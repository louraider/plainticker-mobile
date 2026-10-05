package com.plainticker.mobile.prefs

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Whether the optional app lock is on (You, Security; [com.plainticker.mobile.lock.AppLock]). Off
 * until the person turns it on, and turned on or off only after the phone has confirmed its owner.
 *
 * It lives in the shared preferences file the backup rules exclude whole (res/xml/backup_rules.xml,
 * BackupRulesTest), beside the device code: a security setting restored onto another phone, or onto
 * this one after its screen lock changed, would be a lock nobody chose there.
 */
interface AppLockStore {
    fun isEnabled(): Boolean
    fun setEnabled(value: Boolean)
}

class SharedPrefsAppLockStore(private val prefs: SharedPreferences) : AppLockStore {

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(value: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, value) }
    }

    companion object {
        const val KEY_ENABLED = "app_lock_enabled"
    }
}
