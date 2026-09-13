package com.myapp.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.Clock
import com.myapp.prefs.WatchlistStore
import com.myapp.watchlist.DigestNotifier
import com.myapp.watchlist.DigestRecord
import com.myapp.watchlist.DigestStore
import com.myapp.watchlist.WatchedTicker
import com.myapp.watchlist.WatchlistFacts
import com.myapp.watchlist.WatchlistScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one banner slot, in the DESIGN.md section 4 order: what could not be read at all first, then
 * what is only partly missing.
 */
sealed interface WatchlistBanner {
    /** `/summary` did not answer, so no row carries a company, a report date or an analysis. */
    data object AnalysisUnavailable : WatchlistBanner

    /** The xStocks catalog did not answer, so no row carries a token symbol or a price. */
    data object CatalogUnavailable : WatchlistBanner

    /** Jupiter refused, so no row carries a premium. */
    data object PricesUnavailable : WatchlistBanner
}

data class WatchlistUiState(
    /** How many tickers are watched. The rows follow; this is what decides the empty state. */
    val watched: Int = 0,
    val isLoading: Boolean = false,
    val rows: List<WatchedTicker> = emptyList(),
    /** The last digest the daily check produced, which is what the Panel draws. */
    val digest: DigestRecord = DigestRecord.NONE,
    /** Whether this device will actually show the next digest as a notification. */
    val notificationsOn: Boolean = false,
    val analysisUnavailable: Boolean = false,
    val catalogUnavailable: Boolean = false,
    val pricesUnavailable: Boolean = false,
    /** Read when the screen loads and when it resumes; the "checked 3 h ago" line rests on it. */
    val nowMillis: Long = 0L,
) {
    /** Nothing is watched. The common first state, and the one that gets a sentence. */
    val isEmpty: Boolean get() = watched == 0

    /** Something is watched and nothing has been drawn about it yet: skeletons, never a spinner. */
    val isCold: Boolean get() = watched > 0 && rows.isEmpty()

    val banner: WatchlistBanner?
        get() = when {
            watched == 0 -> null
            analysisUnavailable -> WatchlistBanner.AnalysisUnavailable
            catalogUnavailable -> WatchlistBanner.CatalogUnavailable
            pricesUnavailable -> WatchlistBanner.PricesUnavailable
            else -> null
        }
}

/**
 * The Watchlist screen's state (task T12).
 *
 * It owns none of the rules: the rows come from [WatchlistFacts], which the daily check reads
 * through the same class, and the digest comes from the store the check wrote it to. So the screen
 * cannot draw a premium the notification would disagree with, and it cannot re-derive a digest
 * that was never sent.
 *
 * A change to the watched set reloads: taking a ticker off the list has to take its row off the
 * screen, and putting one on has to fetch its report date. The load is one job at a time, so a
 * quick unwatch of three rows costs one pass rather than three.
 */
class WatchlistViewModel(
    private val watchlist: WatchlistStore,
    private val facts: WatchlistFacts,
    private val digests: DigestStore,
    private val notifier: DigestNotifier,
    private val scheduler: WatchlistScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WatchlistUiState(
            watched = watchlist.tickers.value.size,
            isLoading = watchlist.tickers.value.isNotEmpty(),
            digest = digests.record.value,
            notificationsOn = notifier.enabled(),
            nowMillis = clock.nowMillis(),
        ),
    )
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            watchlist.tickers.collect { watched ->
                _state.update { it.copy(watched = watched.size, isLoading = watched.isNotEmpty()) }
                load(watched)
            }
        }
        // The worker writes the digest from another coroutine in this same process, so the Panel
        // updates the moment a check produces one, without the screen asking.
        viewModelScope.launch {
            digests.record.collect { record -> _state.update { it.copy(digest = record) } }
        }
    }

    /** The Retry a banner offers. Asks every source again and re-reads the notification setting. */
    fun refresh() {
        notificationsChanged()
        load(watchlist.tickers.value)
    }

    /** Takes one ticker off the list. The rows follow from the store, so nothing is removed here. */
    fun unwatch(ticker: String) = watchlist.remove(ticker)

    /**
     * Fires the daily check now. Debug builds only (the screen offers no way to call it otherwise),
     * and deliberately through WorkManager rather than straight into [com.myapp.watchlist.WatchlistCheck]:
     * what needs testing without waiting a day is the whole path, the worker and its network
     * constraint included, and not just the part of it that a unit test already covers.
     *
     * Nothing is returned. The digest lands in the store, the store reaches this screen, and the
     * Panel redraws itself, which is exactly what happens when the daily run produces one.
     */
    fun runCheckNow() = scheduler.runNow()

    /**
     * Re-reads whether notifications are allowed, which is the one piece of this screen's state
     * that can change while the app is in the background: the reader may have gone to settings and
     * come back. Cheap, so it also moves the clock the "checked" line is read against.
     */
    fun notificationsChanged() {
        _state.update { it.copy(notificationsOn = notifier.enabled(), nowMillis = clock.nowMillis()) }
    }

    private fun load(watched: Set<String>) {
        loadJob?.cancel()
        if (watched.isEmpty()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    rows = emptyList(),
                    analysisUnavailable = false,
                    catalogUnavailable = false,
                    pricesUnavailable = false,
                    nowMillis = clock.nowMillis(),
                )
            }
            return
        }
        loadJob = viewModelScope.launch {
            val loaded = facts.load(watched)
            _state.update {
                it.copy(
                    isLoading = false,
                    rows = loaded.rows,
                    analysisUnavailable = loaded.analysisUnavailable,
                    catalogUnavailable = loaded.catalogUnavailable,
                    pricesUnavailable = loaded.pricesUnavailable,
                    nowMillis = clock.nowMillis(),
                )
            }
        }
    }
}
