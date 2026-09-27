package com.plainticker.mobile.prefs

import android.content.SharedPreferences

/**
 * Whether a sign-out cleared this phone's account locally but has not yet been confirmed by
 * `POST /api/v1/account/signout` (AccountSignOut). One flag, no credential: it lives in the same
 * preferences file as the device code, which backups exclude whole, only because that file is
 * already there.
 */
interface PendingSignOutStore {
    fun isPending(): Boolean

    fun setPending(pending: Boolean)
}

class SharedPrefsPendingSignOutStore(private val prefs: SharedPreferences) : PendingSignOutStore {
    override fun isPending(): Boolean = prefs.getBoolean(KEY_PENDING, false)

    override fun setPending(pending: Boolean) {
        val edit = prefs.edit()
        if (pending) edit.putBoolean(KEY_PENDING, true) else edit.remove(KEY_PENDING)
        edit.commit()
    }

    companion object {
        const val KEY_PENDING = "account_signout_pending"
    }
}

class InMemoryPendingSignOutStore(private var pending: Boolean = false) : PendingSignOutStore {
    override fun isPending(): Boolean = pending

    override fun setPending(pending: Boolean) {
        this.pending = pending
    }
}
