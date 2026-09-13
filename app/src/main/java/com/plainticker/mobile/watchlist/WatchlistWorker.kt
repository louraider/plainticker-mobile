package com.plainticker.mobile.watchlist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.plainticker.mobile.appContainer
import kotlinx.coroutines.CancellationException

/**
 * The daily check as WorkManager sees it: a shell over [WatchlistCheck] and nothing else.
 *
 * Everything worth testing is in the check, which runs without a device, and the one decision left
 * here is in [decide], which runs without one either. The check writes the digest before it posts
 * anything, so a retried run cannot produce two notifications for one day's news: the second
 * attempt reads the digest the first one stored and finds it unchanged.
 */
class WatchlistWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = decide { applicationContext.appContainer.watchlistCheck.run() }

    companion object {

        /**
         * The whole of the worker, with the check handed in: what WorkManager is told after one
         * run. It is separate from [doWork] for one reason, which is that [doWork] cannot be
         * called without WorkManager and a device, and the three answers below are worth more
         * than the shell around them.
         *
         * A run that had nothing to say **succeeded**: saying nothing is the correct outcome and
         * a retry would only say nothing again, on a schedule of its own. A run whose analysis
         * did not answer asks to be **retried**, because the next attempt costs four calls and
         * the reader is owed a digest today. A run that threw is a retry as well rather than a
         * success: a crash that reported success would cost the reader the whole day silently.
         * A cancellation is neither, and is rethrown: it is WorkManager stopping the run, not the
         * run failing, and swallowing it would turn a stop into a retry storm.
         */
        internal suspend fun decide(check: suspend () -> CheckOutcome): Result = try {
            if (check() is CheckOutcome.Failed) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            Result.retry()
        }
    }
}
