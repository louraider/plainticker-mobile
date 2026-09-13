package com.plainticker.mobile.watchlist

import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The twelve hour rule, over a fake scheduler: no WorkManager is touched here, which is the point.
 * What is asserted is when the first digest is due and how the schedule follows the watchlist;
 * whether WorkManager honours a periodic request is WorkManager's business.
 */
class WatchlistScheduleTest {

    private class FakeScheduler : WatchlistScheduler {
        val scheduled = mutableListOf<Duration>()
        var cancels = 0
            private set

        override fun scheduleDaily(initialDelay: Duration) {
            scheduled += initialDelay
        }

        override fun cancel() {
            cancels++
        }

        override fun runNow() = Unit
    }

    private fun CoroutineScope.keepInStep(watchlist: InMemoryWatchlistStore, scheduler: WatchlistScheduler) =
        launch { WatchlistSchedule(watchlist, scheduler).keepInStep() }

    @Test
    fun `the first digest is due twelve hours after the first ticker is watched`() = runTest {
        val watchlist = InMemoryWatchlistStore()
        val scheduler = FakeScheduler()
        val job = keepInStep(watchlist, scheduler)
        advanceUntilIdle()

        assertEquals("nothing watched, nothing scheduled", emptyList<Duration>(), scheduler.scheduled)

        watchlist.add("AAPL")
        advanceUntilIdle()

        assertEquals(listOf(Duration.ofHours(12)), scheduler.scheduled)
        assertEquals(Duration.ofHours(12), WatchlistSchedule.FIRST_DIGEST_DELAY)
        assertEquals("and one run a day after that", Duration.ofDays(1), WatchlistSchedule.PERIOD)
        job.cancel()
    }

    @Test
    fun `watching a second ticker does not push the first digest another twelve hours out`() = runTest {
        val watchlist = InMemoryWatchlistStore()
        val scheduler = FakeScheduler()
        val job = keepInStep(watchlist, scheduler)
        advanceUntilIdle()

        watchlist.add("AAPL")
        advanceUntilIdle()
        watchlist.add("TSLA")
        watchlist.add("NVDA")
        advanceUntilIdle()

        assertEquals("scheduled once, when the list stopped being empty", 1, scheduler.scheduled.size)
        assertEquals("the empty list it started from was cancelled once, and never again", 1, scheduler.cancels)
        job.cancel()
    }

    @Test
    fun `taking the last ticker off cancels the daily job`() = runTest {
        val watchlist = InMemoryWatchlistStore(setOf("AAPL", "TSLA"))
        val scheduler = FakeScheduler()
        val job = keepInStep(watchlist, scheduler)
        advanceUntilIdle()

        assertEquals("a process that starts with tickers stored is already scheduled", 1, scheduler.scheduled.size)

        watchlist.remove("AAPL")
        advanceUntilIdle()
        assertEquals("one is still watched", 0, scheduler.cancels)

        watchlist.remove("TSLA")
        advanceUntilIdle()
        assertEquals(1, scheduler.cancels)
        job.cancel()
    }

    @Test
    fun `watching again after an empty list starts the twelve hours over`() = runTest {
        val watchlist = InMemoryWatchlistStore(setOf("AAPL"))
        val scheduler = FakeScheduler()
        val job = keepInStep(watchlist, scheduler)
        advanceUntilIdle()

        watchlist.remove("AAPL")
        advanceUntilIdle()
        watchlist.add("TSLA")
        advanceUntilIdle()

        assertEquals(listOf(Duration.ofHours(12), Duration.ofHours(12)), scheduler.scheduled)
        assertEquals(1, scheduler.cancels)
        job.cancel()
    }
}
