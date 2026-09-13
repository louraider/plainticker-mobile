package com.myapp.prefs

import android.content.SharedPreferences

/**
 * Whether the app has already asked this device for permission to send notifications.
 *
 * It exists so the app asks once. The plan is explicit that the request belongs at the moment the
 * first ticker is watched and nowhere else (section 13 Pass 2 and Pass 7), and the corollary is
 * that a refusal is an answer: a reader who said no is not asked again by watching a second stock,
 * or by watching one, unwatching it and watching another. The digest is on the Watchlist screen
 * either way, and the only other way back to the system dialog is the Enable action beside the
 * line that says notifications are off.
 */
interface NotificationPromptStore {
    fun hasAsked(): Boolean
    fun setAsked()
}

class SharedPrefsNotificationPromptStore(private val prefs: SharedPreferences) : NotificationPromptStore {

    override fun hasAsked(): Boolean = prefs.getBoolean(KEY_ASKED, false)

    override fun setAsked() {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
    }

    companion object {
        const val KEY_ASKED = "notifications_asked"
    }
}
