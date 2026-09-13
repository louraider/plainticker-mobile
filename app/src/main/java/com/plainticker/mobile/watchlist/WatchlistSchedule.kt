package com.plainticker.mobile.watchlist

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.plainticker.mobile.prefs.WatchlistStore
import java.time.Duration
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the app can ask of the background scheduler. One periodic job, and a way to fire it now. */
interface WatchlistScheduler {

    /** One run a day, starting [initialDelay] from now. Idempotent: an existing job is kept. */
    fun scheduleDaily(initialDelay: Duration)

    /** Stops the daily job. Nothing is watched, so there is nothing for it to look at. */
    fun cancel()

    /** Runs the check once, now. Debug builds only: this is the fire-now entry point. */
    fun runNow()
}

/**
 * Keeps the daily check in step with the watchlist.
 *
 * The plan asks for the first digest twelve hours after the first ticker is watched rather than at
 * some hour the app picked, and this is where that lives. Watching the first ticker schedules the
 * job with a twelve hour initial delay; watching the second, third and tenth changes nothing,
 * because the schedule already exists and the policy keeps it rather than pushing the first digest
 * another twelve hours out every time a reader adds a row. Taking the last ticker off cancels it.
 *
 * Twelve hours, and not twenty-four, because a person who has just watched their first stock is
 * owed some evidence that watching does something before the next day; and not one hour, because
 * a digest that arrives while the reader is still on the screen that produced it is not news.
 *
 * It observes the store rather than being called from the toggle, so every path that can change
 * the watchlist goes through it: Detail's Watch action, the Unwatch on the Watchlist row, and a
 * process that starts with tickers already stored.
 */
class WatchlistSchedule(
    private val watchlist: WatchlistStore,
    private val scheduler: WatchlistScheduler,
) {

    /** Collects for as long as the process lives. Never returns. */
    suspend fun keepInStep() {
        watchlist.tickers
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
            .collect { watching -> if (watching) scheduler.scheduleDaily(FIRST_DIGEST_DELAY) else scheduler.cancel() }
    }

    companion object {
        /** How long after the first ticker is watched the first digest is produced. */
        val FIRST_DIGEST_DELAY: Duration = Duration.ofHours(12)

        /** One run a day after that. */
        val PERIOD: Duration = Duration.ofDays(1)
    }
}

/**
 * The scheduler on a real device.
 *
 * The job is unique and periodic, constrained to a network because the check is four calls and
 * nothing else, and enqueued with KEEP so that the delay to the first digest is set once and not
 * reset by the next thing the reader watches. The one-time run the debug entry point uses is its
 * own unique job under its own name, so firing it by hand never disturbs the daily one.
 */
class WorkManagerWatchlistScheduler(context: Context) : WatchlistScheduler {

    private val app: Context = context.applicationContext

    private val onANetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun scheduleDaily(initialDelay: Duration) {
        val request = PeriodicWorkRequestBuilder<WatchlistWorker>(WatchlistSchedule.PERIOD)
            .setInitialDelay(initialDelay)
            .setConstraints(onANetwork)
            .build()
        WorkManager.getInstance(app)
            .enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(app).cancelUniqueWork(DAILY_WORK)
    }

    override fun runNow() {
        val request = OneTimeWorkRequestBuilder<WatchlistWorker>().setConstraints(onANetwork).build()
        WorkManager.getInstance(app).enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val DAILY_WORK = "watchlist-daily"
        const val NOW_WORK = "watchlist-now"
    }
}
