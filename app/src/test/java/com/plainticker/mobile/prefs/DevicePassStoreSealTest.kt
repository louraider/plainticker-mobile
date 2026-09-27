package com.plainticker.mobile.prefs

import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore.Companion.KEY_CODE
import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore.Companion.KEY_CODE_SEALED
import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore.Companion.KEY_PENDING_NEW_CODE
import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore.Companion.KEY_PENDING_NEW_CODE_SEALED
import com.plainticker.mobile.wallet.AesGcmSessionCipher
import java.security.KeyStoreException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The device code sealed at rest (security review, 2026-09-27; [DevicePassStore], "Sealed at
 * rest"), over the same AES-256-GCM the phone runs on its Keystore key, here on an in-memory key.
 *
 * The rule every case below pins: a code is never lost and never replaced because a seal failed.
 * Migration writes the sealed copy, reads it back, and only then removes the plain one; a Keystore
 * that fails leaves the plain copy for the next launch; a sealed copy that does not open falls
 * back to the plain one while it exists, and otherwise the store says so and mints nothing. Every
 * crash point is a state of the file, so each is set up as that state and read by a fresh store,
 * which is exactly what the next launch is.
 */
class DevicePassStoreSealTest {

    private val legacy = "K7M9QRSTXY"
    private val current = "23456789ABCDEFGHJKMNPQRSTU"

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()

    /** A cipher over [k]; [broken] makes every Keystore call throw, as a failing Keystore does. */
    private fun cipher(k: SecretKey = key, broken: () -> Boolean = { false }) =
        AesGcmSessionCipher { if (broken()) throw KeyStoreException("keystore unavailable") else k }

    private fun store(prefs: FakePrefs, c: AesGcmSessionCipher? = cipher()) = SharedPrefsDevicePassStore(prefs, c)

    private fun sealedFor(code: String, k: SecretKey = key): String =
        Base64.getEncoder().encodeToString(cipher(k).seal(code.toByteArray()))

    private fun FakePrefs.put(vararg pairs: Pair<String, String>) = apply {
        val e = edit()
        pairs.forEach { (k, v) -> e.putString(k, v) }
        e.commit()
        writes.clear()
    }

    /** No write this file ever saw carries [code] in the clear. */
    private fun assertNeverPlain(prefs: FakePrefs, code: String) {
        prefs.writes.forEach { w -> assertFalse("the code was written in the clear: ${w.keys}", w.values.any { it == code }) }
        assertFalse(prefs.all.values.any { it == code })
    }

    private fun assertUnreadable(block: () -> Unit) {
        try {
            block()
            fail("expected DeviceCodeUnreadableException")
        } catch (e: DeviceCodeUnreadableException) {
            // expected
        }
    }

    // ---- A fresh install ------------------------------------------------------------------

    @Test
    fun `a fresh code is written sealed only, and a cold start opens the same one`() {
        val prefs = FakePrefs()
        val code = store(prefs).code()
        assertTrue(SharedPrefsDevicePassStore.isNewFormat(code))
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
        assertNeverPlain(prefs, code)
        assertEquals("a cold start opens the same code", code, store(prefs).code())
        assertEquals(SharedPrefsDevicePassStore.sha256Hex(code), store(prefs).codeHash())
    }

    @Test
    fun `a fresh install on a failing Keystore writes the code plain, and a later launch seals it`() {
        val prefs = FakePrefs()
        var broken = true
        val code = store(prefs, cipher { broken }).code()
        assertEquals(mapOf(KEY_CODE to code), prefs.all)

        broken = false
        assertEquals(code, store(prefs, cipher { broken }).code())
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
        assertEquals(code, store(prefs).code())
    }

    // ---- Migration of a plain code -------------------------------------------------------

    @Test
    fun `migration writes the sealed copy beside the plain one, then removes the plain one`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        val store = store(prefs)
        assertEquals(legacy, store.code())
        assertEquals("sealed, then plain removed: two writes", 2, prefs.writes.size)
        val first = prefs.writes[0]
        assertEquals("the plain copy is still there while the sealed one is verified", legacy, first[KEY_CODE])
        assertNotNull(first[KEY_CODE_SEALED])
        assertEquals(setOf(KEY_CODE_SEALED), prefs.writes[1].keys)
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
        // Nothing changed about the code itself: a legacy code stays legacy, and stays the one sent.
        assertTrue(store.isLegacy())
        assertEquals(legacy, store(prefs).code())
        assertEquals(SharedPrefsDevicePassStore.sha256Hex(legacy), store(prefs).codeHash())
    }

    @Test
    fun `a migration stopped after the sealed write is finished by the next launch`() {
        // The crash point: sealed copy written and good, plain copy not removed yet.
        val prefs = FakePrefs().put(KEY_CODE to current, KEY_CODE_SEALED to sealedFor(current))
        assertEquals(current, store(prefs).code())
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
    }

    @Test
    fun `a migration stopped before its read back, with a sealed copy that does not open, keeps the plain code and seals again`() {
        val prefs = FakePrefs().put(KEY_CODE to current, KEY_CODE_SEALED to "not a sealed blob")
        assertEquals(current, store(prefs).code())
        assertEquals("sealed again, verified, and only then the plain copy removed", setOf(KEY_CODE_SEALED), prefs.all.keys)
        assertEquals(current, store(prefs).code())
    }

    @Test
    fun `a Keystore that fails during migration leaves the plain code, and the next launch tries again`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        assertEquals(legacy, store(prefs, cipher { true }).code())
        assertEquals(mapOf(KEY_CODE to legacy), prefs.all)
        assertTrue("nothing was written", prefs.writes.isEmpty())

        assertEquals(legacy, store(prefs).code())
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
    }

    @Test
    fun `a Keystore that seals but cannot open the copy back never removes the plain code`() {
        // Seals on the first Keystore call, fails on every one after: the in-memory round trip
        // inside seal() already fails, so nothing is written at all.
        var calls = 0
        val flaky = AesGcmSessionCipher { if (calls++ == 0) key else throw KeyStoreException("gone") }
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        assertEquals(legacy, store(prefs, flaky).code())
        assertEquals(legacy, prefs.getString(KEY_CODE, null))
    }

    @Test
    fun `a read back that does not open keeps the plain code`() {
        // Seal and its in-memory check pass; the Keystore fails before the read back from the file.
        var calls = 0
        val failsOnReadBack = AesGcmSessionCipher { if (calls++ < 2) key else throw KeyStoreException("gone") }
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        assertEquals(legacy, store(prefs, failsOnReadBack).code())
        assertEquals("the plain copy is only removed after the read back opens", legacy, prefs.getString(KEY_CODE, null))
        // And the next launch, with a working Keystore, reads the sealed copy (the same code) and finishes.
        assertEquals(legacy, store(prefs).code())
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
    }

    @Test
    fun `a disk that refuses the sealed write keeps the plain code`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        prefs.failCommits = true
        assertEquals(legacy, store(prefs).code())
        assertEquals(mapOf(KEY_CODE to legacy), prefs.all)
    }

    // ---- A sealed copy that does not open ------------------------------------------------

    @Test
    fun `a corrupt sealed code with no plain copy is reported, never replaced, never deleted`() {
        val prefs = FakePrefs().put(KEY_CODE_SEALED to "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")
        val store = store(prefs)
        assertUnreadable { store.code() }
        assertUnreadable { store.codeHash() }
        assertUnreadable { store.isLegacy() }
        assertNull(store.codeOrNull())
        assertTrue("nothing was written: no fresh code was minted over it", prefs.writes.isEmpty())
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
    }

    @Test
    fun `a code sealed under a key this phone cannot use right now opens again once it can`() {
        // Another key stands for a Keystore that is failing or answering with the wrong key: the
        // code is unreadable, and the moment the right key answers, it is the same code.
        val prefs = FakePrefs().put(KEY_CODE_SEALED to sealedFor(current))
        assertUnreadable { store(prefs, cipher(newKey())).code() }
        assertUnreadable { store(prefs, cipher { true }).code() }
        assertTrue(prefs.writes.isEmpty())
        assertEquals(current, store(prefs).code())
    }

    @Test
    fun `a sealed code that does not open falls back to the plain copy while it exists`() {
        val prefs = FakePrefs().put(KEY_CODE to current, KEY_CODE_SEALED to sealedFor(current, newKey()))
        assertEquals(current, store(prefs, cipher { true }).code())
        assertEquals("the plain copy stays while it is the only one that reads", current, prefs.getString(KEY_CODE, null))
    }

    // ---- The rekey, sealed ---------------------------------------------------------------

    @Test
    fun `the pending replacement is written sealed only, and survives a cold start`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        val store = store(prefs)
        val pending = store.beginRekey()!!
        assertEquals(setOf(KEY_CODE_SEALED, KEY_PENDING_NEW_CODE_SEALED), prefs.all.keys)
        assertNeverPlain(prefs, pending)
        val cold = store(prefs)
        assertEquals(pending, cold.pendingNewCode())
        assertEquals("the very same replacement, never a second one", pending, cold.beginRekey())
        assertEquals(legacy, cold.code())
    }

    @Test
    fun `completeRekey makes the sealed replacement current and drops both pending forms in one write`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        val store = store(prefs)
        val pending = store.beginRekey()!!
        prefs.writes.clear()
        assertTrue(store.completeRekey(pending, SharedPrefsDevicePassStore.NOTE_SIGN_IN_AGAIN))
        assertEquals("one atomic write", 1, prefs.writes.size)
        assertEquals(setOf(KEY_CODE_SEALED, SharedPrefsDevicePassStore.KEY_REKEY_NOTE), prefs.all.keys)
        assertNeverPlain(prefs, pending)
        val cold = store(prefs)
        assertEquals(pending, cold.code())
        assertFalse(cold.isLegacy())
        assertNull(cold.pendingNewCode())
    }

    @Test
    fun `completeRekey on a failing Keystore writes the new code plain and removes the old sealed one in the same write`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        var broken = false
        val store = store(prefs, cipher { broken })
        val pending = store.beginRekey()!!
        broken = true
        prefs.writes.clear()
        assertTrue(store.completeRekey(pending))
        assertEquals(1, prefs.writes.size)
        assertEquals(mapOf(KEY_CODE to pending), prefs.all)
        assertEquals(pending, store(prefs, cipher { true }).code())
        // A later launch with the Keystore back seals it.
        assertEquals(pending, store(prefs).code())
        assertEquals(setOf(KEY_CODE_SEALED), prefs.all.keys)
    }

    @Test
    fun `a pending replacement written plain by an earlier version is migrated too`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy, KEY_PENDING_NEW_CODE to current)
        val store = store(prefs)
        assertEquals(current, store.pendingNewCode())
        assertEquals(legacy, store.code())
        assertEquals(setOf(KEY_CODE_SEALED, KEY_PENDING_NEW_CODE_SEALED), prefs.all.keys)
        assertEquals(current, store(prefs).beginRekey())
    }

    @Test
    fun `a pending replacement that does not open is never replaced by a second one`() {
        val otherKey = newKey()
        val blob = sealedFor(current, otherKey)
        val prefs = FakePrefs().put(KEY_CODE to legacy, KEY_PENDING_NEW_CODE_SEALED to blob)
        val store = store(prefs)
        assertNull(store.pendingNewCode())
        assertNull("nothing to send, and nothing minted over a replacement the server may know", store.beginRekey())
        assertEquals("the unreadable replacement is kept as it was", blob, prefs.getString(KEY_PENDING_NEW_CODE_SEALED, null))
        assertFalse(store.completeRekey(current))
        assertEquals(legacy, store.code())
        // The key comes back: the same replacement, still the one to send.
        assertEquals(current, store(prefs, cipher(otherKey)).pendingNewCode())
    }

    @Test
    fun `abandonRekey drops both pending forms`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy, KEY_PENDING_NEW_CODE to current, KEY_PENDING_NEW_CODE_SEALED to sealedFor(current))
        store(prefs).abandonRekey()
        assertFalse(prefs.all.containsKey(KEY_PENDING_NEW_CODE))
        assertFalse(prefs.all.containsKey(KEY_PENDING_NEW_CODE_SEALED))
    }

    @Test
    fun `every key the sealed store writes is one of the keys the backup rules exclude with its file`() {
        val prefs = FakePrefs().put(KEY_CODE to legacy)
        val store = store(prefs)
        store.beginRekey()
        store.setRekeyNote(SharedPrefsDevicePassStore.NOTE_SIGN_IN_AGAIN)
        assertTrue(SharedPrefsDevicePassStore.ALL_KEYS.containsAll(prefs.all.keys))
        prefs.writes.forEach { assertTrue(SharedPrefsDevicePassStore.ALL_KEYS.containsAll(it.keys)) }
    }
}
