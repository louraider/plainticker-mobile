package com.plainticker.mobile.prefs

import android.content.SharedPreferences

/** A plain in-memory [SharedPreferences]: no Android runtime behind the interface to stub. */
class FakePrefs : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    /**
     * When true, commit() writes nothing and returns false, the way a full disk answers; apply()
     * is unaffected. Real SharedPreferences update memory before the disk write, but a store that
     * refuses to act on a false commit must be proven against the harsher case.
     */
    var failCommits = false

    /** Every commit() and apply(), in order: what a test asserts about write ordering. */
    val writes = mutableListOf<Map<String, Any?>>()

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
            if (failCommits) return false
            apply()
            return true
        }

        override fun apply() {
            if (cleared) map.clear()
            removedKeys.forEach { map.remove(it) }
            map.putAll(pending)
            writes += map.toMap()
        }
    }
}
