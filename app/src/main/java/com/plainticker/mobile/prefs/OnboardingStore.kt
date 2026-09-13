package com.plainticker.mobile.prefs

import android.content.SharedPreferences

/** Whether the user has passed the one-time onboarding (self-certification) screen. */
interface OnboardingStore {
    fun isOnboarded(): Boolean
    fun setOnboarded(value: Boolean)
}

class SharedPrefsOnboardingStore(private val prefs: SharedPreferences) : OnboardingStore {
    override fun isOnboarded(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)

    override fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    companion object {
        const val KEY_ONBOARDED = "onboarded"
    }
}
