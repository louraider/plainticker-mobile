package com.plainticker.mobile.prefs

import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * This device's own code for Pro entitlement (docs/plan-monetisation-2026-09-19.md section 1.2,
 * task A6).
 *
 * A wallet becomes Pro the way it becomes a voter: by signing a transaction whose memo the tally
 * reads. For a pass or a link, that memo carries the SHA-256 hash of a code, never the code
 * itself, because the memo is public and the preimage is not: a signed transaction on a public
 * chain mints a bearer credential this way, with no signature verification, nonce store or token
 * lifetime anywhere in this repository. [code] is generated once by this device and kept only
 * here. [codeHash] is the only form of it that goes on-chain, in the memo and in `pass/build`;
 * the raw [code] itself rides in the `X-PT-Code` header of every call to PlainTicker's own API
 * (reads, entitlement, sign-in, account, promo), over TLS. That makes the raw code a bearer
 * credential: whoever holds it reads as this device's Pro. So it is kept out of cloud backup and device transfer (res/xml/backup_rules.xml,
 * res/xml/data_extraction_rules.xml exclude this preferences file), and never drawn on a screen
 * (DeviceCodeNeverDrawnTest).
 *
 * **It survives process death.** It belongs to this device, not to a wallet session (which is
 * saved on its own since 2026-09-26, DESIGN.md section 1.2, and cleared on Disconnect), so it
 * never lives there. It is written to `SharedPreferences` the moment it is generated, and every
 * later read returns the same string, cold start after cold start, until the app's data is
 * cleared.
 *
 * **Length.** A new code is [SharedPrefsDevicePassStore.CODE_LENGTH] (26) symbols over a
 * 31-symbol alphabet, about 128.8 bits, drawn with [SecureRandom]. Only the code's unsalted
 * SHA-256 goes on-chain, so the code's own entropy is the only thing between a public memo and an
 * offline preimage search; the earlier 10-symbol code (about 49.5 bits) could be swept in roughly
 * one GPU-day. Codes minted at that earlier length
 * ([SharedPrefsDevicePassStore.LEGACY_CODE_LENGTH]) are still sent exactly as stored until the
 * server has confirmed their replacement: a device that already paid holds one, and its pass is
 * bound to that code's hash.
 *
 * **Replacing a legacy code (rekey, 2026-09-27).** [DeviceCodeRekeyStore] is the one way a stored
 * code ever changes, and only `DeviceRekeyer` drives it, against `POST /api/v1/device/rekey`,
 * which moves every entitlement and binding of the old code onto the new one server side. The
 * order is what keeps a paid credential from being lost:
 * 1. [DeviceCodeRekeyStore.beginRekey] mints the 26-symbol replacement and writes it to
 *    [SharedPrefsDevicePassStore.KEY_PENDING_NEW_CODE] with a synchronous `commit()` BEFORE any
 *    network call, so a crash or a lost answer retries with the very same pair, which the server
 *    answers 200 again (the rekey is idempotent for one old and new pair). The old code stays the
 *    current code throughout.
 * 2. Only a 200 reaches [DeviceCodeRekeyStore.completeRekey], which in ONE `commit()` makes the new
 *    code current and removes the pending slot. The old code is not kept: the server refuses it
 *    everywhere from then on (401 `code_retired`), so on this device it could only ever be a
 *    second copy of a dead credential.
 * 3. Every refusal that means the pair can never be accepted (409 `already_rekeyed`, 409
 *    `new_code_in_use`, 401 `code_retired`) is recorded by [DeviceCodeRekeyStore.markRekeyBlocked]
 *    and deletes nothing: both codes stay on the device, so support can still act on them.
 *
 * All of it lives in the same preferences file as the code, which res/xml/backup_rules.xml and
 * data_extraction_rules.xml exclude whole, so none of the new keys can reach a backup either.
 *
 * **What a wallet change does.** The code belongs to this device, not to whichever wallet
 * happens to be connected when it is minted or presented. Connecting a different wallet and
 * paying, or linking, with it signs this SAME code's hash into that wallet's own memo, so the new
 * wallet becomes bound to the one code this device carries; the code itself is never regenerated
 * for a wallet switch, and switching back to an earlier wallet still finds it. Only
 * clearing the app's data forgets it, and a forgotten code cannot be recovered, because only its
 * hash ever left the device: nothing on the server can hand it back.
 */
interface DevicePassStore {
    /**
     * This device's own code, generated once and stable for the life of the install, except for
     * the one server-confirmed replacement of a legacy code ("Replacing a legacy code" above).
     * Read it fresh for every call; never keep a copy.
     */
    fun code(): String

    /** SHA-256 hex digest of [code], lowercase, the only form that ever leaves the device. */
    fun codeHash(): String
}

/**
 * The rekey half of [SharedPrefsDevicePassStore] (see [DevicePassStore], "Replacing a legacy
 * code"). Separate from [DevicePassStore] so the many readers of the code (every API, every
 * ViewModel) cannot reach the calls that change it; only `DeviceRekeyer` holds this one.
 *
 * Every method does disk I/O (a synchronous `commit()` where order matters), so callers run it
 * off the main thread.
 */
interface DeviceCodeRekeyStore {
    /** The current code, exactly as [DevicePassStore.code] returns it. */
    fun code(): String

    /** Whether the current code is a [SharedPrefsDevicePassStore.LEGACY_CODE_LENGTH]-symbol one. */
    fun isLegacy(): Boolean

    /** The replacement persisted by an earlier [beginRekey] that has not been confirmed, if any. */
    fun pendingNewCode(): String?

    /**
     * The replacement to send: the one an earlier attempt already persisted, or a freshly minted
     * one, committed to disk before this returns. Null when the current code is not legacy, or
     * when the write could not be committed (then nothing may be sent: a replacement the server
     * accepted but this device forgot would orphan the pass).
     */
    fun beginRekey(): String?

    /**
     * The server answered 200 for the current code and [newCode]: make [newCode] current and drop
     * the pending slot in one commit. Refuses (false, nothing written) unless [newCode] is the
     * pending one.
     */
    fun completeRekey(newCode: String): Boolean

    /** The server says the current code needs no rekey (400 `not_legacy`): forget the pending one. */
    fun abandonRekey()

    /** Why a rekey can never finish on this device, or null. Survives restarts; nothing retries it. */
    fun rekeyBlocked(): String?

    /** Records [reason] (the server's error code) and keeps both codes exactly as they are. */
    fun markRekeyBlocked(reason: String)
}

class SharedPrefsDevicePassStore(private val prefs: SharedPreferences) : DevicePassStore, DeviceCodeRekeyStore {

    @Synchronized
    override fun code(): String =
        prefs.getString(KEY_CODE, null) ?: generate().also { prefs.edit().putString(KEY_CODE, it).apply() }

    override fun codeHash(): String = sha256Hex(code())

    @Synchronized
    override fun isLegacy(): Boolean = code().length == LEGACY_CODE_LENGTH

    @Synchronized
    override fun pendingNewCode(): String? = prefs.getString(KEY_PENDING_NEW_CODE, null)?.takeIf(::isNewFormat)

    @Synchronized
    override fun beginRekey(): String? {
        if (!isLegacy()) return null
        pendingNewCode()?.let { return it }
        val minted = generate()
        // commit(), not apply(): the replacement must be on disk before the server can hear of it.
        return if (prefs.edit().putString(KEY_PENDING_NEW_CODE, minted).commit()) minted else null
    }

    @Synchronized
    override fun completeRekey(newCode: String): Boolean {
        if (!isNewFormat(newCode) || pendingNewCode() != newCode) return false
        // One editor, one commit: the new code becomes current and the pending slot goes in the
        // same atomic file write, so no crash can leave a device with neither code, or with two
        // that disagree about which is current.
        return prefs.edit()
            .putString(KEY_CODE, newCode)
            .remove(KEY_PENDING_NEW_CODE)
            .remove(KEY_REKEY_BLOCKED)
            .commit()
    }

    @Synchronized
    override fun abandonRekey() {
        prefs.edit().remove(KEY_PENDING_NEW_CODE).commit()
    }

    @Synchronized
    override fun rekeyBlocked(): String? = prefs.getString(KEY_REKEY_BLOCKED, null)

    @Synchronized
    override fun markRekeyBlocked(reason: String) {
        prefs.edit().putString(KEY_REKEY_BLOCKED, reason).commit()
    }

    private fun generate(): String {
        val random = SecureRandom()
        return buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
    }

    companion object {
        const val KEY_CODE = "device_pass_code"

        /** The minted replacement for a legacy code, persisted before the rekey call is made. */
        const val KEY_PENDING_NEW_CODE = "device_pass_pending_new_code"

        /** The server's error code for a rekey that can never finish on this device. */
        const val KEY_REKEY_BLOCKED = "device_pass_rekey_blocked"

        /** Every key this store writes: all of them live in the one preferences file backups exclude. */
        val ALL_KEYS = setOf(KEY_CODE, KEY_PENDING_NEW_CODE, KEY_REKEY_BLOCKED)

        /** Length of every NEWLY minted code: 26 x log2(31) = 128.8 bits. */
        const val CODE_LENGTH = 26

        /**
         * Length of codes minted before the 26-symbol change. Still read and sent as stored until
         * the server confirms a replacement (`DeviceRekeyer`); never minted again.
         */
        const val LEGACY_CODE_LENGTH = 10

        /** No 0/O or 1/I: a code a person might ever have to read off one screen and type on another. */
        const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        /** A code in the current format: [CODE_LENGTH] symbols, every one from [CODE_ALPHABET]. */
        fun isNewFormat(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET }

        fun sha256Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
