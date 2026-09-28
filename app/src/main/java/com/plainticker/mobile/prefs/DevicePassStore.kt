package com.plainticker.mobile.prefs

import android.content.SharedPreferences
import com.plainticker.mobile.wallet.AesGcmSessionCipher
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

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
 *    `new_code_in_use`, 401 `code_retired`, 400 `legacy_sunset`) also goes through
 *    [DeviceCodeRekeyStore.completeRekey]: the pending code is valid on its own (only without the
 *    old code's pass), so it becomes current and [NOTE_REPLACED][SharedPrefsDevicePassStore.NOTE_REPLACED]
 *    is written in the same commit, for You to say where to write.
 *
 * A 200 moves the old code's pass and promo time but not its account binding (web PR #170), so
 * a rekey on a signed-in phone writes [NOTE_SIGN_IN_AGAIN][SharedPrefsDevicePassStore.NOTE_SIGN_IN_AGAIN]
 * in that same commit; the next successful Google sign-in clears it.
 *
 * All of it lives in the same preferences file as the code, which res/xml/backup_rules.xml and
 * data_extraction_rules.xml exclude whole, so none of the new keys can reach a backup either.
 *
 * **Sealed at rest (security review, 2026-09-27).** The current and the pending code used to sit
 * in that file as plain text, readable by anything that can read the app's data directory (a
 * rooted phone, an adb backup on a debuggable build). Both are now sealed with
 * [AesGcmSessionCipher], the same Keystore-backed AES-GCM the wallet session uses, under a key of
 * their own ([SharedPrefsDevicePassStore.KEY_ALIAS]) that never leaves the phone's secure
 * hardware. A code that is lost cannot be recovered and its pass goes with it, so every step is
 * ordered to never lose one:
 * - **Migration** of a plain code: seal it, write the sealed copy beside the plain one, read the
 *   sealed copy back from the file and open it, and only when it opens to the same code remove
 *   the plain one. A Keystore that fails at any step leaves the plain copy where it is, and the
 *   next launch tries again. A crash between two steps leaves either the plain copy alone or both
 *   copies, and the next read finishes the job.
 * - **Reading**: the sealed copy when it opens; the plain copy when it does not and the plain copy
 *   is still there. When neither can be read, [DevicePassStore.code] throws
 *   [DeviceCodeUnreadableException] and You says so: a fresh code is never minted in its place,
 *   because the sealed one may open on the next launch, and a fresh code would orphan the pass
 *   bound to the old one. The sealed copy is never deleted for failing to open.
 * - **A key that is gone for good** (security review M3): when the Keystore no longer holds the
 *   key under [SharedPrefsDevicePassStore.KEY_ALIAS] (a cleared lock screen, a restore onto
 *   another phone, a vendor Keystore wipe), the first read creates a new one, and the sealed copy
 *   can then never open. That is told apart from a Keystore that fails for a moment: a sealed copy
 *   that does not open under a key this process had to create is lost, recorded as such
 *   ([SharedPrefsDevicePassStore.KEY_CODE_KEY_LOST]) so the next launch knows it too, and read as
 *   [DeviceCodeLostException]. Nothing is replaced on its own even then: only the reader, told
 *   what it costs, may start again with a new code ([DeviceCodeRekeyStore.startWithNewCode]).
 * - **Writing** (a first code, a pending code, a completed rekey): the code is sealed and opened
 *   again in memory before anything is written, and written sealed in the same single commit that
 *   removes any plain copy. When the Keystore cannot seal, it is written plain, removing any
 *   sealed copy in that same commit, and migrated on a later launch.
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

    /**
     * [code], or null when this phone cannot read it ([DeviceCodeUnreadableException]). For the
     * reads that work without a code (the summary, a ticker's read) and would rather ask
     * unauthenticated than fail.
     */
    fun codeOrNull(): String? = try {
        code()
    } catch (e: DeviceCodeUnreadableException) {
        null
    }
}

/**
 * This phone holds a sealed device code it cannot open right now, and no plain copy of it (see
 * [DevicePassStore], "Sealed at rest"). An [IOException], so every call that sends the code treats
 * it as a call that could not be made; nothing mints a replacement.
 */
open class DeviceCodeUnreadableException(
    message: String = "the device code is sealed and could not be opened",
) : IOException(message)

/**
 * The sealed code can never be opened again: the Keystore key that sealed it is gone, and the
 * key this phone holds now was created after it ([DevicePassStore], "A key that is gone for
 * good"). Still a [DeviceCodeUnreadableException] to every call that sends the code; only You
 * offers the way out, [DeviceCodeRekeyStore.startWithNewCode], and only when asked.
 */
class DeviceCodeLostException : DeviceCodeUnreadableException("the key that sealed the device code is gone")

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
     * The server answered for the current code and [newCode] (a 200, or a refusal that adopts the
     * fresh code): make [newCode] current, drop the pending slot and write [note] (or remove the
     * note, when null) in one commit. Refuses (false, nothing written) unless [newCode] is the
     * pending one.
     */
    fun completeRekey(newCode: String, note: String? = null): Boolean

    /** The server says the current code needs no rekey (400 `not_legacy`): forget the pending one. */
    fun abandonRekey()

    /**
     * What You should say about the last rekey, or null: [SharedPrefsDevicePassStore.NOTE_SIGN_IN_AGAIN]
     * or [SharedPrefsDevicePassStore.NOTE_REPLACED]. Survives restarts.
     */
    fun rekeyNote(): String?

    /** Replaces the note, or removes it when [note] is null. */
    fun setRekeyNote(note: String?)

    /**
     * True when the current code is sealed under a Keystore key that is gone for good
     * ([DeviceCodeLostException]); false when it opens, or fails only for now.
     */
    fun codeLost(): Boolean = false

    /**
     * The reader's explicit choice on You, and only then: a code that is lost is replaced by a
     * freshly minted 26-symbol one, in one commit that also clears the unreadable slot, any pending
     * rekey code, the rekey note and the lost marker. Refuses (false, nothing written) unless
     * [codeLost]. The old code's Pro does not come with it; Pro on a Google account comes back
     * with the next sign-in.
     */
    fun startWithNewCode(): Boolean = false
}

class SharedPrefsDevicePassStore(
    private val prefs: SharedPreferences,
    /**
     * Seals both codes at rest ([DevicePassStore], "Sealed at rest"). On the phone, an
     * [AesGcmSessionCipher] over the Keystore key [KEY_ALIAS]; null only in tests that exercise
     * the plain layout, where nothing is sealed and nothing is migrated.
     */
    private val cipher: AesGcmSessionCipher? = null,
    /**
     * True once this process had to create the Keystore key [cipher] seals under, because none was
     * there: a sealed copy that then does not open was sealed under a key that is gone
     * ([DeviceCodeLostException]). On the phone, set by [AesGcmSessionCipher.androidKeystoreKey].
     */
    private val keyCreatedThisProcess: () -> Boolean = { false },
) : DevicePassStore, DeviceCodeRekeyStore {

    /** What one slot (the current code, or the pending one) holds, read through the seal. */
    private sealed interface Slot {
        data object Empty : Slot

        data class Value(val code: String) : Slot

        /** A sealed copy that does not open, and no plain copy: never replaced, never deleted. */
        data object Unreadable : Slot

        /** A sealed copy under a key that is gone for good: replaced only by [startWithNewCode]. */
        data object Lost : Slot
    }

    /**
     * The key this process may have created is known to be this install's own: a value was sealed
     * and opened with it ([seal]), or the reader started with a new code under it. A sealed copy
     * that fails to open after that is a Keystore failing for now, not a lost key.
     */
    private var keyCreationSeen = false

    /** Slots whose migration this process already tried; a failed one is tried again next launch. */
    private val migrationTried = mutableSetOf<String>()

    @Synchronized
    override fun code(): String = when (val slot = read(KEY_CODE, KEY_CODE_SEALED)) {
        is Slot.Value -> slot.code
        Slot.Unreadable -> throw DeviceCodeUnreadableException()
        Slot.Lost -> throw DeviceCodeLostException()
        Slot.Empty -> generate().also { minted ->
            // Only a slot with no copy at all, sealed or plain, is ever given a fresh code.
            write(prefs.edit(), KEY_CODE, KEY_CODE_SEALED, minted).commit()
        }
    }

    override fun codeHash(): String = sha256Hex(code())

    @Synchronized
    override fun isLegacy(): Boolean = code().length == LEGACY_CODE_LENGTH

    @Synchronized
    override fun pendingNewCode(): String? =
        (read(KEY_PENDING_NEW_CODE, KEY_PENDING_NEW_CODE_SEALED) as? Slot.Value)?.code?.takeIf(::isNewFormat)

    @Synchronized
    override fun beginRekey(): String? {
        if (!isLegacy()) return null
        when (val pending = read(KEY_PENDING_NEW_CODE, KEY_PENDING_NEW_CODE_SEALED)) {
            is Slot.Value -> if (isNewFormat(pending.code)) return pending.code
            // A replacement the server may already have heard of, that this phone cannot read
            // right now: nothing is sent, and no second replacement is minted over it.
            Slot.Unreadable, Slot.Lost -> return null
            Slot.Empty -> Unit
        }
        val minted = generate()
        // commit(), not apply(): the replacement must be on disk before the server can hear of it.
        val edit = write(prefs.edit(), KEY_PENDING_NEW_CODE, KEY_PENDING_NEW_CODE_SEALED, minted)
        return if (edit.commit()) minted else null
    }

    @Synchronized
    override fun completeRekey(newCode: String, note: String?): Boolean {
        if (!isNewFormat(newCode) || pendingNewCode() != newCode) return false
        // One editor, one commit: the new code becomes current (sealed, or plain when the Keystore
        // cannot seal), the pending slot goes in both forms and the note is written in the same
        // atomic file write, so no crash can leave a device with neither code, with two that
        // disagree about which is current, or with a new code and no note.
        val edit = write(prefs.edit(), KEY_CODE, KEY_CODE_SEALED, newCode)
            .remove(KEY_PENDING_NEW_CODE)
            .remove(KEY_PENDING_NEW_CODE_SEALED)
        if (note != null) edit.putString(KEY_REKEY_NOTE, note) else edit.remove(KEY_REKEY_NOTE)
        return edit.commit()
    }

    @Synchronized
    override fun abandonRekey() {
        prefs.edit().remove(KEY_PENDING_NEW_CODE).remove(KEY_PENDING_NEW_CODE_SEALED).commit()
    }

    @Synchronized
    override fun codeLost(): Boolean = read(KEY_CODE, KEY_CODE_SEALED) == Slot.Lost

    @Synchronized
    override fun startWithNewCode(): Boolean {
        if (read(KEY_CODE, KEY_CODE_SEALED) != Slot.Lost) return false
        val minted = generate()
        // One commit: the new code (sealed under the key this phone holds now, or plain when the
        // Keystore cannot seal), and every trace of the old one and of any rekey gone with it.
        val edit = write(prefs.edit(), KEY_CODE, KEY_CODE_SEALED, minted)
            .remove(KEY_PENDING_NEW_CODE)
            .remove(KEY_PENDING_NEW_CODE_SEALED)
            .remove(KEY_REKEY_NOTE)
            .remove(KEY_CODE_KEY_LOST)
        if (!edit.commit()) return false
        keyCreationSeen = true
        migrationTried.clear()
        return true
    }

    // ---- The seal -------------------------------------------------------------------------

    /**
     * One slot, read through the seal ([DevicePassStore], "Sealed at rest"), finishing or starting
     * the migration of a plain copy on the way.
     */
    private fun read(plainKey: String, sealedKey: String): Slot {
        val plain = prefs.getString(plainKey, null)
        val sealed = prefs.getString(sealedKey, null)
        if (sealed != null) {
            val opened = open(sealed)
            if (opened != null) {
                // Both copies, the same code: a migration stopped after it verified the sealed
                // copy and before it removed the plain one. Finish it.
                if (plain == opened) prefs.edit().remove(plainKey).commit()
                return Slot.Value(opened)
            }
            // The sealed copy does not open. The plain one, while it is still there, is the code;
            // the sealed copy is left in place, and replaced only by a verified seal of that code.
            if (plain == null) return if (keyGone()) Slot.Lost else Slot.Unreadable
            migrate(plainKey, sealedKey, plain)
            return Slot.Value(plain)
        }
        if (plain == null) return Slot.Empty
        migrate(plainKey, sealedKey, plain)
        return Slot.Value(plain)
    }

    /**
     * Whether the key a sealed copy that did not open was sealed under is gone for good: this
     * process had to create the key (so the one before it is not in the Keystore any more), or an
     * earlier launch found that and recorded it. The record is written the first time, so the
     * launch after, whose key the Keystore now holds, still knows.
     */
    private fun keyGone(): Boolean {
        if (prefs.getBoolean(KEY_CODE_KEY_LOST, false)) return true
        if (keyCreationSeen || !keyCreatedThisProcess()) return false
        prefs.edit().putBoolean(KEY_CODE_KEY_LOST, true).commit()
        return true
    }

    /**
     * Seals a plain copy: write the sealed copy beside it, read it back from the file and open it,
     * and remove the plain copy only when that gives the same code back. Once per slot per
     * process; any failure leaves the plain copy, and the next launch tries again.
     */
    private fun migrate(plainKey: String, sealedKey: String, plain: String) {
        if (cipher == null || !migrationTried.add(plainKey)) return
        val blob = seal(plain) ?: return
        if (!prefs.edit().putString(sealedKey, blob).commit()) return
        // Read back from the file and opened by the Keystore itself, never answered from memory:
        // this is the proof the plain copy may go.
        val readBack = prefs.getString(sealedKey, null)?.let { open(it, fromMemory = false) }
        if (readBack != plain) return
        prefs.edit().remove(plainKey).commit()
    }

    /**
     * [value] into [edit] under the slot: sealed (and the plain key removed) when the Keystore
     * seals and opens it again in memory, plain (and the sealed key removed) when it cannot. Either
     * way one copy, written in the caller's one commit.
     */
    private fun write(edit: SharedPreferences.Editor, plainKey: String, sealedKey: String, value: String): SharedPreferences.Editor {
        val blob = seal(value)
        return if (blob != null) {
            edit.putString(sealedKey, blob).remove(plainKey)
        } else {
            edit.putString(plainKey, value).remove(sealedKey)
        }
    }

    /** The sealed form of [value], Base64, or null when the Keystore failed or did not round-trip. */
    private fun seal(value: String): String? {
        val c = cipher ?: return null
        return try {
            val sealed = c.seal(value.toByteArray(Charsets.UTF_8))
            if (c.open(sealed).toString(Charsets.UTF_8) != value) return null
            // This process sealed under the key it holds and opened it again: a key created for
            // this write is this install's own, and a later failure to open is not a lost key.
            if (!prefs.getBoolean(KEY_CODE_KEY_LOST, false)) keyCreationSeen = true
            // Remembered as opened: a Keystore that fails after this write (between a rekey's
            // begin and its completion) cannot make this process lose the code it just wrote.
            Base64.getEncoder().encodeToString(sealed).also { remember(it, value) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The code in [blob], or null when it does not open: another key, a failing Keystore, an edit.
     * A blob this process already opened is answered from memory, so the many reads of the code
     * (one per API call) cost one Keystore round-trip per sealed value, not one each; the plain
     * code lived in the preferences' own memory before, so this keeps nothing new in memory.
     */
    private fun open(blob: String, fromMemory: Boolean = true): String? {
        if (fromMemory) opened[blob]?.let { return it }
        val c = cipher ?: return null
        return try {
            c.open(Base64.getDecoder().decode(blob)).toString(Charsets.UTF_8).takeIf { it.isNotEmpty() }
                ?.also { code -> remember(blob, code) }
        } catch (e: Exception) {
            null
        }
    }

    /** Sealed blobs this process opened or wrote, and the code in each. */
    private val opened = HashMap<String, String>()

    private fun remember(blob: String, code: String) {
        if (opened.size >= 4) opened.clear()
        opened[blob] = code
    }

    @Synchronized
    override fun rekeyNote(): String? = prefs.getString(KEY_REKEY_NOTE, null)

    @Synchronized
    override fun setRekeyNote(note: String?) {
        val edit = prefs.edit()
        if (note != null) edit.putString(KEY_REKEY_NOTE, note) else edit.remove(KEY_REKEY_NOTE)
        edit.commit()
    }

    private fun generate(): String {
        val random = SecureRandom()
        return buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
    }

    companion object {
        const val KEY_CODE = "device_pass_code"

        /** The minted replacement for a legacy code, persisted before the rekey call is made. */
        const val KEY_PENDING_NEW_CODE = "device_pass_pending_new_code"

        /** [KEY_CODE]'s sealed form: Base64 of [AesGcmSessionCipher.seal] over the code's UTF-8. */
        const val KEY_CODE_SEALED = "device_pass_code_sealed"

        /** [KEY_PENDING_NEW_CODE]'s sealed form. */
        const val KEY_PENDING_NEW_CODE_SEALED = "device_pass_pending_new_code_sealed"

        /** The Keystore key both codes are sealed under, apart from the wallet session's. */
        const val KEY_ALIAS = "plainticker.device_code.v1"

        /** What You says about the last rekey: [NOTE_SIGN_IN_AGAIN] or [NOTE_REPLACED]. */
        const val KEY_REKEY_NOTE = "device_pass_rekey_note"

        /**
         * Recorded once a sealed code was found under a Keystore key created after it: the code is
         * lost ([DeviceCodeLostException]) on this launch and every later one, until the reader
         * starts with a new code.
         */
        const val KEY_CODE_KEY_LOST = "device_pass_code_key_lost"

        /** The rekey landed on a signed-in phone; the new code is not bound to the account. */
        const val NOTE_SIGN_IN_AGAIN = "sign_in_again"

        /** The old code could not be moved; the fresh code was adopted without its pass. */
        const val NOTE_REPLACED = "replaced"

        /** Every key this store writes: all of them live in the one preferences file backups exclude. */
        val ALL_KEYS = setOf(
            KEY_CODE,
            KEY_PENDING_NEW_CODE,
            KEY_REKEY_NOTE,
            KEY_CODE_SEALED,
            KEY_PENDING_NEW_CODE_SEALED,
            KEY_CODE_KEY_LOST,
        )

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
