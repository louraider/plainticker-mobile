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
 * here; [codeHash] is the one form of it that ever leaves, into a memo or the `X-PT-Code` header
 * a read carries.
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
 * ([SharedPrefsDevicePassStore.LEGACY_CODE_LENGTH]) are still honoured exactly as stored: a device
 * that already paid holds one, its paid pass is bound to that code's hash, and replacing it would
 * orphan the pass. So an existing code is never regenerated, lengthened or rewritten, whatever its
 * length; only a device with no code at all mints the longer one.
 *
 * **What a wallet change does.** The code belongs to this device, not to whichever wallet
 * happens to be connected when it is minted or presented. Connecting a different wallet and
 * paying, or linking, with it signs this SAME code's hash into that wallet's own memo, so the new
 * wallet becomes bound to the one code this device has always carried; the code itself is never
 * regenerated for a wallet switch, and switching back to an earlier wallet still finds it. Only
 * clearing the app's data forgets it, and a forgotten code cannot be recovered, because only its
 * hash ever left the device: nothing on the server can hand it back.
 */
interface DevicePassStore {
    /** This device's own code, generated once and stable for the life of the install. */
    fun code(): String

    /** SHA-256 hex digest of [code], lowercase, the only form that ever leaves the device. */
    fun codeHash(): String
}

class SharedPrefsDevicePassStore(private val prefs: SharedPreferences) : DevicePassStore {

    @Synchronized
    override fun code(): String =
        prefs.getString(KEY_CODE, null) ?: generate().also { prefs.edit().putString(KEY_CODE, it).apply() }

    override fun codeHash(): String = sha256Hex(code())

    private fun generate(): String {
        val random = SecureRandom()
        return buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }
    }

    companion object {
        const val KEY_CODE = "device_pass_code"
        /** Length of every NEWLY minted code: 26 x log2(31) = 128.8 bits. */
        const val CODE_LENGTH = 26

        /**
         * Length of codes minted before the 26-symbol change. Still read, sent and honoured as
         * stored; never minted again. The server accepts both lengths.
         */
        const val LEGACY_CODE_LENGTH = 10

        /** No 0/O or 1/I: a code a person might ever have to read off one screen and type on another. */
        const val CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

        fun sha256Hex(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
