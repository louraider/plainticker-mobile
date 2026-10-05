package com.plainticker.mobile.lock

import com.plainticker.mobile.prefs.AppLockStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What this phone can confirm its owner with, read from BiometricManager and the keyguard. */
enum class LockAvailability {
    /** A strong biometric (the Seeker's fingerprint) is enrolled; a screen lock is then set too. */
    BIOMETRIC,

    /** A PIN, pattern or password is set, and no strong biometric is enrolled. */
    SCREEN_LOCK,

    /** Neither: there is nothing to confirm with, so the lock is not offered. */
    NONE,
    ;

    val canLock: Boolean get() = this != NONE

    companion object {
        fun of(strongBiometric: Boolean, deviceCredential: Boolean): LockAvailability = when {
            strongBiometric -> BIOMETRIC
            deviceCredential -> SCREEN_LOCK
            else -> NONE
        }
    }
}

/** Why the phone is asked to confirm its owner; the prompt's title follows it. */
enum class AuthPurpose { OPEN, TURN_ON, TURN_OFF }

/** How one prompt ended. */
sealed interface AuthResult {
    /** The fingerprint or the screen lock was confirmed. */
    data object Success : AuthResult

    /** Closed by the person, or by the system (the app left the screen, the screen went off). */
    data object Cancelled : AuthResult

    /** Ended in an error the system named (too many attempts, the sensor busy), in its own words. */
    data class Failed(val message: String) : AuthResult

    /** No screen is up to draw a prompt over. */
    data object NoScreen : AuthResult
}

/** Shows one prompt and answers how it ended. The Activity binds the real one. */
fun interface Authenticator {
    suspend fun authenticate(purpose: AuthPurpose, availability: LockAvailability): AuthResult
}

data class AppLockState(
    val availability: LockAvailability = LockAvailability.NONE,
    /** The setting: the lock is on. Only ever true while [availability] can lock. */
    val enabled: Boolean = false,
    /** The lock screen is up and hides everything until a prompt succeeds. */
    val locked: Boolean = false,
    /** A prompt is on screen (to open the app, or to turn the setting on or off). */
    val authenticating: Boolean = false,
    /** Why the last prompt ended without confirming, in the system's words; null after a plain cancel. */
    val message: String? = null,
)

/**
 * The optional app lock (1.3.28). Off by default; when the person turns it on, PlainTicker asks for
 * the fingerprint or the screen lock when it opens and whenever it comes back after five minutes
 * away. It guards what this app shows; it signs nothing, and the Seed Vault still asks for every
 * transaction itself.
 *
 * - **Cold start.** A process that has not drawn this app yet starts locked when the lock is on:
 *   [state] is built locked, so the lock screen is the first frame, before any content.
 * - **Coming back.** [leftApp] and [returned] follow the Activity's stop and start; [LockTimer]
 *   decides whether the time away reached five minutes. A wallet round trip ([walletBusy]) and
 *   this lock's own prompt are excursions the timer does not count, so returning from the Seed
 *   Vault never locks the app.
 * - **The setting is changed only after a prompt succeeds**, in both directions, so a mistaken tap
 *   can neither lock a person out (turning on proves the phone can confirm them first) nor take the
 *   lock away from a phone someone else is holding (turning off asks too).
 * - **No prompt loops.** One automatic prompt per lock and per return to the screen ([takeAutoPrompt]);
 *   a cancel or an error leaves the lock screen up with its Open action, and nothing asks again
 *   until the person does. Coming back from the prompt itself (the screen lock is an Activity on
 *   Android 9 and lower) is not a return that asks again.
 * - **A phone that lost its screen lock** cannot confirm anyone, so a lock left on would lock the
 *   person out for good: the setting turns itself off and the app opens ([returned]).
 *
 * Main thread only in the app: [scope] runs on the main dispatcher, and the Activity's lifecycle
 * calls arrive there too.
 */
class AppLock(
    private val store: AppLockStore,
    private val availability: () -> LockAvailability,
    private val scope: CoroutineScope,
    now: () -> Long,
    walletBusy: Flow<Boolean>,
    relockAfterMillis: Long = LockTimer.RELOCK_AFTER_MILLIS,
) {
    private val timer = LockTimer(now, relockAfterMillis)

    private val _state: MutableStateFlow<AppLockState>
    val state: StateFlow<AppLockState>

    /** The Activity's prompt, while one is bound. */
    private var authenticator: Authenticator? = null

    /** One automatic prompt is owed: the lock engaged, or the app came back to a locked screen. */
    private var autoPrompt = false

    /** The app left the screen while a prompt was up: the return is the prompt's own, not the person's. */
    private var leftDuringPrompt = false

    private var walletOut = false

    init {
        val available = availability()
        val enabled = settingFor(available)
        _state = MutableStateFlow(AppLockState(availability = available, enabled = enabled, locked = enabled))
        state = _state.asStateFlow()
        autoPrompt = enabled
        scope.launch {
            walletBusy.collect { busy ->
                if (busy && !walletOut) timer.excursionStarted()
                if (!busy && walletOut) timer.excursionEnded()
                walletOut = busy
            }
        }
    }

    fun bind(authenticator: Authenticator) {
        this.authenticator = authenticator
    }

    fun unbind(authenticator: Authenticator) {
        if (this.authenticator === authenticator) this.authenticator = null
    }

    /**
     * The Activity stopped: the app is off the screen. [forPrompt] is true when the stop is the
     * prompt's own (the screen lock opened as an Activity, Android 9 and lower), so the return from
     * it is not a return that asks again.
     */
    fun leftApp(forPrompt: Boolean = false) {
        leftDuringPrompt = forPrompt
        timer.leftApp()
    }

    /** The Activity started again: lock if five counted minutes went by, and owe a prompt if locked. */
    fun returned() {
        val expired = timer.returned()
        val available = availability()
        val enabled = settingFor(available)
        _state.update {
            val locks = enabled && !it.locked && expired
            it.copy(
                availability = available,
                enabled = enabled,
                locked = enabled && (it.locked || expired),
                // A fresh lock screen does not carry the last prompt's error.
                message = if (locks) null else it.message,
            )
        }
        val now = _state.value
        if (now.locked && !now.authenticating && !leftDuringPrompt) autoPrompt = true
        leftDuringPrompt = false
    }

    /**
     * True once per owed automatic prompt, and only while the lock screen is up with no prompt on
     * it. The Activity asks on every resume, where a prompt can be shown.
     */
    fun takeAutoPrompt(): Boolean {
        val take = autoPrompt && _state.value.locked && !_state.value.authenticating
        autoPrompt = false
        return take
    }

    /** The lock screen's Open, or the automatic prompt: one prompt, and the app opens on success. */
    fun open() {
        if (!_state.value.locked) return
        prompt(AuthPurpose.OPEN) { _state.update { it.copy(locked = false) } }
    }

    /**
     * You's switch. Nothing changes until the prompt succeeds; a phone with nothing to confirm with
     * cannot turn the lock on at all.
     */
    fun setEnabled(on: Boolean) {
        val current = _state.value
        if (on == current.enabled || current.locked) return
        if (on && !current.availability.canLock) return
        prompt(if (on) AuthPurpose.TURN_ON else AuthPurpose.TURN_OFF) {
            store.setEnabled(on)
            _state.update { it.copy(enabled = on) }
        }
    }

    private fun prompt(purpose: AuthPurpose, onSuccess: () -> Unit) {
        if (_state.value.authenticating) return
        // Marked before the coroutine starts, so a second tap in the same frame finds a prompt up.
        _state.update { it.copy(authenticating = true, message = null) }
        timer.excursionStarted()
        scope.launch {
            val result = try {
                authenticator?.authenticate(purpose, _state.value.availability) ?: AuthResult.NoScreen
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthResult.Cancelled
            } finally {
                timer.excursionEnded()
                _state.update { it.copy(authenticating = false) }
            }
            when (result) {
                AuthResult.Success -> onSuccess()
                is AuthResult.Failed -> _state.update { it.copy(message = result.message) }
                AuthResult.Cancelled, AuthResult.NoScreen -> Unit
            }
        }
    }

    /**
     * The stored setting, unless the phone can no longer confirm anyone: then it is turned off for
     * good rather than left to lock a person out of their own app.
     */
    private fun settingFor(available: LockAvailability): Boolean {
        val stored = store.isEnabled()
        if (stored && !available.canLock) {
            store.setEnabled(false)
            return false
        }
        return stored
    }
}
