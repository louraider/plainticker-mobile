package com.plainticker.mobile.lock

/**
 * How long PlainTicker has really been away, for the app lock's five-minute rule ([AppLock]).
 *
 * Time away counts from the moment the app leaves the screen until it comes back, except while an
 * excursion is out: a wallet round trip (the Seed Vault is on screen because this app asked it to
 * sign) or the lock's own prompt (on Android 9 and lower the screen lock is its own Activity). An
 * excursion stops the count, and the count picks up again only when the excursion has ended with
 * the app still away. So approving a swap in the Seed Vault never reads as five minutes in the
 * background, however long the wallet took, while a person who walks away after the wallet has
 * answered is counted from the moment it answered.
 *
 * Pure: [now] is injected (the phone's elapsed-realtime clock in the app, so changing the wall
 * clock cannot shorten the wait), and nothing here knows about Android. Confined to one thread,
 * the main one in the app.
 */
class LockTimer(
    private val now: () -> Long,
    val relockAfterMillis: Long = RELOCK_AFTER_MILLIS,
) {
    private var away = false
    private var excursions = 0

    /** When the current stretch of counted time started, or null while nothing is being counted. */
    private var countingSince: Long? = null

    /** Counted time from earlier stretches of this same absence. */
    private var counted = 0L

    /** The app left the screen (the Activity stopped). */
    fun leftApp() {
        if (away) return
        away = true
        counted = 0L
        countingSince = if (excursions == 0) now() else null
    }

    /** A wallet round trip or the lock's own prompt started. Nesting is allowed. */
    fun excursionStarted() {
        excursions++
        if (excursions == 1) pause()
    }

    /** One excursion ended. Counting resumes once none is left and the app is still away. */
    fun excursionEnded() {
        if (excursions == 0) return
        excursions--
        if (excursions == 0 && away && countingSince == null) countingSince = now()
    }

    /**
     * The app is back on screen. True when the time counted for this absence reached
     * [relockAfterMillis]; false for a return with no absence recorded.
     */
    fun returned(): Boolean {
        if (!away) return false
        pause()
        away = false
        val total = counted
        counted = 0L
        return total >= relockAfterMillis
    }

    private fun pause() {
        countingSince?.let { counted += (now() - it).coerceAtLeast(0L) }
        countingSince = null
    }

    companion object {
        /** Five minutes away, wallet round trips not counted, locks the app again. */
        const val RELOCK_AFTER_MILLIS: Long = 5 * 60 * 1_000L
    }
}
