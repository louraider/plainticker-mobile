package com.plainticker.mobile.watchlist

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * What the worker tells WorkManager after one run.
 *
 * The rest of the daily check is covered where it lives, in [WatchlistCheckTest]; this file exists
 * because the shell around it had no test at all, and a shell is exactly where a wrong answer
 * hides. Every case below is a day of the reader's digest: a retry that should have been a success
 * is a job that runs again and again saying nothing, and a success that should have been a retry
 * is a day that silently never happened.
 */
class WatchlistWorkerTest {

    @Test
    fun `a run whose analysis did not answer asks to be retried`() = runTest {
        assertEquals(Result.retry(), WatchlistWorker.decide { CheckOutcome.Failed })
    }

    @Test
    fun `a run with nothing to say succeeded, so it is not tried again today`() = runTest {
        assertEquals(Result.success(), WatchlistWorker.decide { CheckOutcome.NothingToSay })
    }

    @Test
    fun `a run that repeated itself succeeded, so silence is not retried into noise`() = runTest {
        assertEquals(Result.success(), WatchlistWorker.decide { CheckOutcome.Unchanged })
    }

    @Test
    fun `a produced digest succeeded`() = runTest {
        assertEquals(Result.success(), WatchlistWorker.decide { CheckOutcome.Produced("1 stock watched.") })
    }

    @Test
    fun `a run that threw is a retry rather than a silent success`() = runTest {
        assertEquals(Result.retry(), WatchlistWorker.decide { error("the network went away mid run") })
    }

    @Test
    fun `the check is asked for exactly once`() = runTest {
        var runs = 0
        WatchlistWorker.decide {
            runs++
            CheckOutcome.NothingToSay
        }
        assertEquals(1, runs)
    }

    /**
     * WorkManager stopping the run is not the run failing. Swallowing it into a retry would turn
     * every system stop into another attempt, which is the one way a daily job becomes a loop.
     */
    @Test
    fun `a cancellation is rethrown, never turned into a retry`() {
        try {
            runBlocking { WatchlistWorker.decide { throw CancellationException("stopped") } }
            fail("the cancellation was swallowed")
        } catch (cancelled: CancellationException) {
            assertTrue(cancelled.message == "stopped")
        }
    }
}
