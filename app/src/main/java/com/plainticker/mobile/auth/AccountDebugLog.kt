package com.plainticker.mobile.auth

import android.util.Log
import com.plainticker.mobile.BuildConfig

/**
 * The Account section's debug log, shared by [CredentialManagerGoogleSource] and
 * `AccountViewModel` (moved here from `AccountViewModel.kt` so a class in this package can use it
 * too). Handed class names, exception types and failure codes only: never the ID token, never the
 * device code, never an email. `AccountViewModelTest` records every line and asserts the token
 * appears in none of them.
 */
fun interface AccountDebugLog {
    fun raw(line: String)

    companion object {
        val ANDROID = AccountDebugLog { line -> if (BuildConfig.DEBUG) Log.d("Account", line) }
    }
}
