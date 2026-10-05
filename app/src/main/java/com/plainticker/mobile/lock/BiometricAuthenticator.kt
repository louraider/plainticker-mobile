package com.plainticker.mobile.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.plainticker.mobile.R
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What the phone can confirm its owner with, read the way BiometricPrompt will use it. */
object PhoneLockAvailability {
    fun read(context: Context): LockAvailability {
        val biometrics = BiometricManager.from(context)
        val strong = runCatching {
            biometrics.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
        // DEVICE_CREDENTIAL on its own is a combination androidx.biometric supports from Android 11;
        // below that the keyguard answers whether a PIN, pattern or password is set.
        val credential = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                biometrics.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
            }.getOrDefault(false)
        } else {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }
        return LockAvailability.of(strong, credential)
    }
}

/**
 * The real [Authenticator]: androidx.biometric's BiometricPrompt over the one Activity. Built in
 * onCreate, because both the prompt and the screen-lock launcher must exist before the Activity
 * starts.
 *
 * - **Android 11 and later**: one prompt allowing BIOMETRIC_STRONG or DEVICE_CREDENTIAL, so the
 *   fingerprint and the PIN, pattern or password share it and the system offers the screen lock
 *   itself (after too many attempts too). The library forbids a negative button here.
 * - **Android 10 and lower**: the library does not support BIOMETRIC_STRONG with DEVICE_CREDENTIAL
 *   (or the credential alone) there, so the prompt allows the fingerprint only and its negative
 *   button reads "Use screen lock", which opens the keyguard's own confirmation as an Activity. A
 *   phone with a screen lock and no fingerprint goes straight to it, and so does a fingerprint
 *   sensor locked out by too many attempts.
 *
 * A cancel ends as [AuthResult.Cancelled], never as a second prompt: [AppLock] decides whether to
 * ask again, and it never does on its own.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) : Authenticator {

    private var pending: CancellableContinuation<AuthResult>? = null
    private var pendingTitle: String = ""

    /** The keyguard's confirmation is on screen; the Activity's stop is the prompt's own. */
    var awayForScreenLock: Boolean = false
        private set

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                finish(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    // Below Android 11 only: the prompt carries the button, and a sensor that is
                    // locked out, unenrolled since or busy does not fall back to the screen lock
                    // by itself.
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) askScreenLock() else finish(failure(errString))

                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_TIMEOUT,
                    -> finish(AuthResult.Cancelled)

                    else -> finish(failure(errString))
                }
            }

            // onAuthenticationFailed is one fingerprint not recognised: the prompt stays up and
            // says so itself, so nothing here ends.
        },
    )

    private val screenLock = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        awayForScreenLock = false
        finish(if (result.resultCode == Activity.RESULT_OK) AuthResult.Success else AuthResult.Cancelled)
    }

    override suspend fun authenticate(purpose: AuthPurpose, availability: LockAvailability): AuthResult =
        suspendCancellableCoroutine { continuation ->
            finish(AuthResult.Cancelled) // a prompt left over from before ends first; AppLock asks one at a time
            pending = continuation
            pendingTitle = activity.getString(
                when (purpose) {
                    AuthPurpose.OPEN -> R.string.lock_prompt_open
                    AuthPurpose.TURN_ON -> R.string.lock_prompt_turn_on
                    AuthPurpose.TURN_OFF -> R.string.lock_prompt_turn_off
                },
            )
            continuation.invokeOnCancellation { activity.runOnUiThread { if (pending === continuation) cancel() } }
            // A prompt asked for while the Activity is not on screen would never answer: the
            // library drops it after the state is saved.
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                activity.supportFragmentManager.isStateSaved
            ) {
                finish(AuthResult.NoScreen)
                return@suspendCancellableCoroutine
            }
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> show(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(pendingTitle)
                        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                        .setConfirmationRequired(false)
                        .build(),
                )
                availability == LockAvailability.BIOMETRIC -> show(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(pendingTitle)
                        .setAllowedAuthenticators(BIOMETRIC_STRONG)
                        .setNegativeButtonText(activity.getString(R.string.lock_prompt_screen_lock))
                        .setConfirmationRequired(false)
                        .build(),
                )
                else -> askScreenLock()
            }
        }

    /**
     * The Activity stopped. A BiometricPrompt does not outlive it (the library dismisses it, and
     * not every version says so), so it ends here as a cancel rather than leaving a prompt that
     * never answers. The keyguard's own confirmation is exactly what stopped it, so that one stays.
     */
    fun onStop() {
        if (pending != null && !awayForScreenLock) {
            prompt.cancelAuthentication()
            finish(AuthResult.Cancelled)
        }
    }

    /** The Activity is going away: nothing is left waiting on it. */
    fun close() {
        cancel()
    }

    private fun show(info: BiometricPrompt.PromptInfo) {
        runCatching { prompt.authenticate(info) }.onFailure { finish(AuthResult.Cancelled) }
    }

    @Suppress("DEPRECATION") // The keyguard's confirmation is the screen lock below Android 11.
    private fun askScreenLock() {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(pendingTitle, null)
        if (intent == null) {
            finish(AuthResult.Cancelled)
            return
        }
        awayForScreenLock = true
        runCatching { screenLock.launch(intent) }.onFailure {
            awayForScreenLock = false
            finish(AuthResult.Cancelled)
        }
    }

    private fun cancel() {
        if (pending == null) return
        if (!awayForScreenLock) runCatching { prompt.cancelAuthentication() }
        awayForScreenLock = false
        finish(AuthResult.Cancelled)
    }

    private fun failure(errString: CharSequence): AuthResult =
        AuthResult.Failed(errString.toString().takeIf { it.isNotBlank() } ?: activity.getString(R.string.lock_failed))

    private fun finish(result: AuthResult) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(result)
    }
}
