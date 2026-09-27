package com.plainticker.mobile

import android.app.Application
import android.content.Context
import com.plainticker.mobile.watchlist.WatchlistSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlainTickerApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Lives as long as the process; the one thing on it never completes. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        // The daily check follows the watchlist rather than the toggle that changed it, so every
        // path that can empty or fill the list is covered: the Watch action, the Unwatch on the
        // row, and a process that starts with tickers already stored.
        scope.launch { WatchlistSchedule(container.watchlistStore, container.watchlistScheduler).keepInStep() }

        // The wallet connected before this process started, put back for display and reads. No
        // wallet opens here; the first request that needs one reauthorizes with the saved token.
        scope.launch { container.walletSession.restore() }

        // Closing the loop per reader: a vote's pick and a swap's stock token are watched for
        // them. Followed from here, not from the flows that record them, so the vote and swap
        // machines are untouched; a pick analysed since the last run is watched at start too.
        scope.launch { container.autoWatch.followVotes(container.voteReceiptStore.receipts) }
        scope.launch { container.autoWatch.followSwaps(container.receiptStore.receipts) }
        scope.launch { runCatching { container.autoWatch.resolvePending() } }
    }
}

/** The process-wide dependency graph, from any Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as PlainTickerApp).container
