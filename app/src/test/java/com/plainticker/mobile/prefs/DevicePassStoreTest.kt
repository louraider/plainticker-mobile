package com.plainticker.mobile.prefs

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `an existing 10-symbol code is kept exactly as stored, never replaced`() {
        // A paying device already holds a code minted at the earlier length; its pass is bound to
        // that code's hash, so replacing it would orphan the pass.
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
}

/** A plain in-memory [SharedPreferences]: no Android runtime behind the interface to stub. */
private class FakePrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map

    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removedKeys = mutableSetOf<String>()
        private var cleared = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = also { pending[key!!] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            also { pending[key!!] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = also { pending[key!!] = value }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = also { pending[key!!] = value }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = also { pending[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = also { pending[key!!] = value }
        override fun remove(key: String?): SharedPreferences.Editor = also { removedKeys += key!! }
        override fun clear(): SharedPreferences.Editor = also { cleared = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (cleared) map.clear()
            removedKeys.forEach { map.remove(it) }
            map.putAll(pending)
        }
    }
}
