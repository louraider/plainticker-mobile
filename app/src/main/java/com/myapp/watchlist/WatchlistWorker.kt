package com.myapp.watchlist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myapp.appContainer
import kotlinx.coroutines.CancellationException

/**
 * The daily check as WorkManager sees it: a shell over [WatchlistCheck] and nothing else.
 *
 * Everything worth testing is in the check, which runs without a device, so this file has one
 * decision in it. A run whose analysis did not answer asks to be retried, because the next attempt
 * costs four calls and the reader is owed a digest today; a run that had nothing to say succeeded,
 * because saying nothing is the correct outcome and retrying it would only say nothing again.
 *
 * A crash is a retry as well. The check writes the digest before it posts anything, so a retried
 * run cannot produce two notifications for one day's news: the second attempt reads the digest the
 * first one stored and finds it unchanged.
 */
class WatchlistWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = try {
            applicationContext.appContainer.watchlistCheck.run()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return Result.retry()
        }
        return if (outcome is CheckOutcome.Failed) Result.retry() else Result.success()
    }
}
