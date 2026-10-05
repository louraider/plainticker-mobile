package com.plainticker.mobile.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app lock's five-minute rule, on an injected clock: time away counts, a wallet round trip or
 * the lock's own prompt does not.
 */
class LockTimerTest {

    private var clock = 1_000_000L
    private val timer = LockTimer(now = { clock })
    private val minute = 60_000L

    @Test
    fun `the limit is five minutes`() {
        assertEquals(5 * minute, LockTimer.RELOCK_AFTER_MILLIS)
        assertEquals(LockTimer.RELOCK_AFTER_MILLIS, timer.relockAfterMillis)
    }

    @Test
    fun `under five minutes away does not lock, five minutes does`() {
        timer.leftApp()
        clock += 5 * minute - 1
        assertFalse(timer.returned())

        timer.leftApp()
        clock += 5 * minute
        assertTrue(timer.returned())
    }

    @Test
    fun `a return with no absence recorded does not lock, and each absence counts on its own`() {
        assertFalse("the first start of a process is not a return", timer.returned())
        timer.leftApp()
        clock += 3 * minute
        assertFalse(timer.returned())
        timer.leftApp()
        clock += 3 * minute
        assertFalse("two short absences do not add up", timer.returned())
    }

    @Test
    fun `a wallet round trip is not time away, however long the wallet took`() {
        timer.excursionStarted() // the swap asks the Seed Vault to sign
        timer.leftApp() // the wallet covers the app
        clock += 20 * minute
        assertFalse("back from the wallet, still mid round trip", timer.returned())
        timer.excursionEnded()
    }

    @Test
    fun `time counts from the moment the wallet answered while the app is still away`() {
        timer.excursionStarted()
        timer.leftApp()
        clock += 10 * minute
        timer.excursionEnded() // the wallet answered; the person stays in another app
        clock += 4 * minute
        assertFalse(timer.returned())

        timer.excursionStarted()
        timer.leftApp()
        clock += 10 * minute
        timer.excursionEnded()
        clock += 5 * minute
        assertTrue(timer.returned())
    }

    @Test
    fun `away time before and after an excursion adds up, the excursion itself does not`() {
        timer.leftApp()
        clock += 3 * minute
        timer.excursionStarted()
        clock += 30 * minute
        timer.excursionEnded()
        clock += 1 * minute
        assertFalse("4 minutes counted", timer.returned())

        timer.leftApp()
        clock += 3 * minute
        timer.excursionStarted()
        clock += 30 * minute
        timer.excursionEnded()
        clock += 2 * minute
        assertTrue("5 minutes counted", timer.returned())
    }

    @Test
    fun `nested excursions count only once all have ended, and a stray end is ignored`() {
        timer.excursionEnded() // nothing out: ignored
        timer.excursionStarted() // wallet
        timer.excursionStarted() // prompt
        timer.leftApp()
        clock += 10 * minute
        timer.excursionEnded()
        clock += 10 * minute
        assertFalse("one excursion still out", timer.returned())
        timer.excursionEnded()
    }

    @Test
    fun `a clock that steps backwards counts nothing rather than a negative`() {
        timer.leftApp()
        clock -= 10 * minute
        assertFalse(timer.returned())
    }
}
