package com.plainticker.mobile.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device code (task A6): it survives process death, and its hash is what a memo or an
 * `X-PT-Code` header ever carries.
 *
 * [FakePrefs] is a plain in-memory [SharedPreferences], not a mock and not Robolectric: the
 * interface has no Android runtime behind it to stub, so implementing it directly is enough to
 * prove [SharedPrefsDevicePassStore] against something that persists exactly the way the real
 * one does, including across a second instance built on the same backing map, which is what a
 * process death and a cold start actually are from this class's point of view.
 */
class DevicePassStoreTest {

    @Test
    fun `the code is stable across repeated reads on the same instance`() {
        val store = SharedPrefsDevicePassStore(FakePrefs())
        val first = store.code()
        val second = store.code()
        assertEquals(first, second)
    }

    @Test
    fun `the code survives process death - a fresh store on the same backing storage finds it`() {
        val backing = FakePrefs()
        val beforeDeath = SharedPrefsDevicePassStore(backing).code()

        // A new instance, exactly as a cold start after process death builds a new
        // SharedPrefsDevicePassStore over the same file: nothing here is carried in memory.
        val afterDeath = SharedPrefsDevicePassStore(backing).code()

        assertEquals("process death must not mint a second code", beforeDeath, afterDeath)
    }

    @Test
    fun `a wallet change does not touch the code`() {
        // The store knows nothing about which wallet is connected; this is the whole of the
        // proof that a wallet switch cannot affect it, because there is no wallet in this class
        // at all for one to change. See the class doc for the decision this states.
        val store = SharedPrefsDevicePassStore(FakePrefs())
        val before = store.code()
        // Nothing simulates "connect a different wallet" here on purpose: reading the code twice
        // with no such concept in scope is the test.
        val after = store.code()
        assertEquals(before, after)
    }

    @Test
    fun `two devices, two backing stores, mint two different codes`() {
        val a = SharedPrefsDevicePassStore(FakePrefs()).code()
        val b = SharedPrefsDevicePassStore(FakePrefs()).code()
        assertNotEquals("a shared code would let one device read as another", a, b)
    }

    @Test
    fun `the code is drawn from an alphabet with no 0-O or 1-I confusion, at the stated length`() {
        val code = SharedPrefsDevicePassStore(FakePrefs()).code()
        assertEquals(SharedPrefsDevicePassStore.CODE_LENGTH, code.length)
        assertTrue(code.all { it in SharedPrefsDevicePassStore.CODE_ALPHABET })
        assertTrue('0' !in code && 'O' !in code && '1' !in code && 'I' !in code)
    }

    @Test
    fun `a new code carries at least 128 bits of entropy`() {
        val bits = SharedPrefsDevicePassStore.CODE_LENGTH *
            (Math.log(SharedPrefsDevicePassStore.CODE_ALPHABET.length.toDouble()) / Math.log(2.0))
        assertTrue("a new code carries only $bits bits", bits >= 128.0)
        assertEquals(26, SharedPrefsDevicePassStore.CODE_LENGTH)
        assertEquals(26, SharedPrefsDevicePassStore(FakePrefs()).code().length)
    }

    @Test
    fun `an existing 10-symbol code is kept exactly as stored, and reading it never replaces it`() {
        // A paying device already holds a code minted at the earlier length; its pass is bound to
        // that code's hash, so only a server-confirmed rekey may replace it (tests below).
        val legacy = "K7M9QRSTXY"
        assertEquals(SharedPrefsDevicePassStore.LEGACY_CODE_LENGTH, legacy.length)
        val backing = FakePrefs()
        backing.edit().putString(SharedPrefsDevicePassStore.KEY_CODE, legacy).apply()

        val store = SharedPrefsDevicePassStore(backing)
        assertEquals(legacy, store.code())
        assertEquals(SharedPrefsDevicePassStore.sha256Hex(legacy), store.codeHash())
        // A cold start over the same storage still finds the same legacy code.
        assertEquals(legacy, SharedPrefsDevicePassStore(backing).code())
        assertEquals(legacy, backing.getString(SharedPrefsDevicePassStore.KEY_CODE, null))
    }

    @Test
    fun `the hash is the code's own SHA-256, lowercase hex, and never the code itself`() {
        val store = SharedPrefsDevicePassStore(FakePrefs())
        val code = store.code()
        val hash = store.codeHash()

        assertEquals(64, hash.length)
        assertTrue(hash.all { it.isDigit() || it in 'a'..'f' })
        assertNotEquals("the hash must not be readable back to the code", code, hash)
        assertEquals(hash, SharedPrefsDevicePassStore.sha256Hex(code))
    }

    /** The standard test vector, so the digest itself is pinned and not only its shape. */
    @Test
    fun `sha256Hex matches the standard test vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SharedPrefsDevicePassStore.sha256Hex("abc"),
        )
    }

    // ---- Rekeying a legacy code (DeviceCodeRekeyStore) --------------------------------------

    private val legacy = "K7M9QRSTXY"

    private fun legacyStore(backing: FakePrefs = FakePrefs()): Pair<FakePrefs, SharedPrefsDevicePassStore> {
        backing.edit().putString(SharedPrefsDevicePassStore.KEY_CODE, legacy).commit()
        return backing to SharedPrefsDevicePassStore(backing)
    }

    @Test
    fun `beginRekey commits a 26-symbol replacement before returning, and the legacy code stays current`() {
        val (backing, store) = legacyStore()
        assertTrue(store.isLegacy())
        val pending = store.beginRekey()!!
        assertTrue(SharedPrefsDevicePassStore.isNewFormat(pending))
        assertEquals("the old code is still the one sent", legacy, store.code())
        assertEquals("on disk before the server can hear of it", pending, backing.getString(SharedPrefsDevicePassStore.KEY_PENDING_NEW_CODE, null))
    }

    @Test
    fun `beginRekey after a crash returns the very same replacement, never a second one`() {
        val (backing, first) = legacyStore()
        val pending = first.beginRekey()!!
        // A cold start: a new instance over the same file.
        val afterCrash = SharedPrefsDevicePassStore(backing)
        assertEquals(pending, afterCrash.pendingNewCode())
        assertEquals(pending, afterCrash.beginRekey())
        assertEquals(legacy, afterCrash.code())
    }

    @Test
    fun `beginRekey refuses to hand out a replacement it could not commit`() {
        val (backing, store) = legacyStore()
        backing.failCommits = true
        assertNull(store.beginRekey())
        assertNull(store.pendingNewCode())
        assertEquals(legacy, store.code())
    }

    @Test
    fun `beginRekey does nothing for a current-format code`() {
        val store = SharedPrefsDevicePassStore(FakePrefs())
        val code = store.code()
        assertFalse(store.isLegacy())
        assertNull(store.beginRekey())
        assertEquals(code, store.code())
    }

    @Test
    fun `completeRekey swaps the code and drops the pending slot in one write`() {
        val (backing, store) = legacyStore()
        val pending = store.beginRekey()!!
        val writesBefore = backing.writes.size
        assertTrue(store.completeRekey(pending))
        assertEquals("one atomic write", writesBefore + 1, backing.writes.size)
        val written = backing.writes.last()
        assertEquals(pending, written[SharedPrefsDevicePassStore.KEY_CODE])
        assertFalse(written.containsKey(SharedPrefsDevicePassStore.KEY_PENDING_NEW_CODE))
        assertEquals(pending, SharedPrefsDevicePassStore(backing).code())
        assertFalse(SharedPrefsDevicePassStore(backing).isLegacy())
        assertEquals(SharedPrefsDevicePassStore.sha256Hex(pending), store.codeHash())
    }

    @Test
    fun `completeRekey refuses any code but the pending one, and keeps the legacy code`() {
        val (_, store) = legacyStore()
        store.beginRekey()!!
        assertFalse(store.completeRekey("23456789ABCDEFGHJKMNPQRSTU"))
        assertFalse(store.completeRekey("short"))
        assertEquals(legacy, store.code())
    }

    @Test
    fun `abandonRekey drops only the pending code`() {
        val (_, store) = legacyStore()
        store.beginRekey()!!
        store.abandonRekey()
        assertNull(store.pendingNewCode())
        assertEquals(legacy, store.code())
    }

    @Test
    fun `a rekey note is written in the same commit as the new code and survives a restart`() {
        val (backing, store) = legacyStore()
        val pending = store.beginRekey()!!
        val writesBefore = backing.writes.size
        assertTrue(store.completeRekey(pending, SharedPrefsDevicePassStore.NOTE_REPLACED))
        assertEquals("one atomic write", writesBefore + 1, backing.writes.size)
        assertEquals(SharedPrefsDevicePassStore.NOTE_REPLACED, backing.writes.last()[SharedPrefsDevicePassStore.KEY_REKEY_NOTE])
        val cold = SharedPrefsDevicePassStore(backing)
        assertEquals(SharedPrefsDevicePassStore.NOTE_REPLACED, cold.rekeyNote())
        assertEquals(pending, cold.code())
        assertNull(cold.pendingNewCode())

        cold.setRekeyNote(null)
        assertNull(SharedPrefsDevicePassStore(backing).rekeyNote())
    }

    @Test
    fun `every key the store writes lands in the one preferences file it was given`() {
        // backup_rules.xml excludes that file whole (BackupRulesTest), so no key here can reach a
        // backup: this pins that the store has no second file and no key outside ALL_KEYS.
        val (backing, store) = legacyStore()
        store.beginRekey()
        store.setRekeyNote(SharedPrefsDevicePassStore.NOTE_SIGN_IN_AGAIN)
        assertEquals(SharedPrefsDevicePassStore.ALL_KEYS, backing.all.keys)
    }
}
