package com.plainticker.mobile.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.xstocks.MarketHours
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.ui.today.TodayLeader
import com.plainticker.mobile.ui.today.TrackedRow
import com.plainticker.mobile.watchlist.DigestNotifier
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.DigestStore
import com.plainticker.mobile.watchlist.WatchedTicker
import com.plainticker.mobile.watchlist.WatchlistFacts
import com.plainticker.mobile.watchlist.WatchlistScheduler
import java.time.Instant
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
    // ---- Today's other blocks (docs/design-research-2026-09-21.md section 3, blocks 1/3/4/5) ----
    // The venue line, Tracked today and Next up read the wider universe Watched never did, off one
    // join over the same repositories Stocks reads, so the two screens' coverage counts cannot
    // disagree. True before that join has ever run: every field below is at a default that draws
    // nothing rather than a guess (com.plainticker.mobile.ui.today.TodayModel.kt's functions all
    // return null for an unknown fact instead of inventing one).
    /** True until the join behind blocks 1, 3 and 4 has completed once, success or failure alike. */
    val todayLoading: Boolean = true,
    /** Where the venue is right now, or null while no catalog has answered at all. */
    val market: MarketStatus? = null,
    val analysisGeneratedAtMillis: Long? = null,
    /** When this device last asked Jupiter, whether or not anything came back priced. */
    val pricesFetchedAtMillis: Long? = null,
    /** Rows with a PlainTicker classification and a matching xStock, the same count Stocks shows. */
    val analyzedTotal: Int = 0,
    val withoutAnalysisTotal: Int = 0,
    /** The tracked rows, deepest pool first, whether or not the screen draws all of them. */
    val tracked: List<TrackedRow> = emptyList(),
    /** The vote leader Next up names, or null when nothing staked SKR chose can be read. */
    val nextUpLeader: TodayLeader? = null,
    val voteRound: VoteRound? = null,
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
 * The Watchlist screen's state (task T12), now also Today's (docs/design-research-2026-09-21.md
 * section 3): [com.plainticker.mobile.ui.today.TodayScreen] hosts this same instance so watching a
 * ticker, the daily digest and Today's other blocks all move off one ViewModel rather than two that
 * would have to be kept in step by hand.
 *
 * It owns none of the rules: the watched rows come from [WatchlistFacts], which the daily check
 * reads through the same class, and the digest comes from the store the check wrote it to. So the
 * screen cannot draw a premium the notification would disagree with, and it cannot re-derive a
 * digest that was never sent. Today's other blocks read [summaries], [catalog], [prices] and
 * [nextUpRepo] directly, the same repositories `ListViewModel` (Stocks) joins against, so "22 of
 * 160" here and Stocks' own coverage counts are read off one join and cannot disagree.
 *
 * A change to the watched set reloads: taking a ticker off the list has to take its row off the
 * screen, and putting one on has to fetch its report date. The load is one job at a time, so a
 * quick unwatch of three rows costs one pass rather than three. Today's join is a second, unrelated
 * job: the watched set changing does not re-run it, and it does not re-run when a ticker is watched
 * or unwatched.
 */
class WatchlistViewModel(
    private val watchlist: WatchlistStore,
    private val facts: WatchlistFacts,
    private val digests: DigestStore,
    private val notifier: DigestNotifier,
    private val scheduler: WatchlistScheduler,
    private val clock: Clock,
    // Today's other blocks (docs/design-research-2026-09-21.md section 3, blocks 1/3/4/5): the
    // venue line, Tracked today and Next up need the wider universe Watched never did.
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
    private val nextUpRepo: NextUpRepository,
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
    private var todayJob: Job? = null

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
        // Today's other blocks: a second, unrelated join that the watched set does not drive and
        // does not re-run for.
        loadToday()
    }

    /** The Retry a banner offers. Asks every source again and re-reads the notification setting. */
    fun refresh() {
        notificationsChanged()
        load(watchlist.tickers.value)
        loadToday()
    }

    /** Takes one ticker off the list. The rows follow from the store, so nothing is removed here. */
    fun unwatch(ticker: String) = watchlist.remove(ticker)

    /**
     * Fires the daily check now. Debug builds only (the screen offers no way to call it otherwise),
     * and deliberately through WorkManager rather than straight into [com.plainticker.mobile.watchlist.WatchlistCheck]:
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

    /**
     * Blocks 1, 3 and 4 (docs/design-research-2026-09-21.md section 3): the venue line, Tracked
     * today and Next up. One join, once per load, over `/summary`, the catalog and Jupiter's
     * prices, the same three sources `ListViewModel`'s own join reads, so "22 of 160" here and
     * Stocks' coverage counts can never print two different numbers.
     *
     * Never throws: a source that did not answer costs its own facts (the fields below stay at
     * their [WatchlistUiState] defaults, which is what [com.plainticker.mobile.ui.today.TodayModel.kt]'s
     * functions read as "not yet known") and nothing else on this screen.
     */
    private fun loadToday() {
        todayJob?.cancel()
        _state.update { it.copy(todayLoading = true) }
        todayJob = viewModelScope.launch {
            val summaryResult = runCatching { summaries.summary() }
            val assets = runCatching { catalog.catalog() }.getOrNull().orEmpty()
                .filter { it.solanaMint != null }
            val rows = summaryResult.getOrNull()?.rows.orEmpty()
                .distinctBy { it.ticker.uppercase() } // lint-allow uppercase: map key
            val byTicker = assets.associateBy { it.underlyingTicker.uppercase() } // lint-allow uppercase: map key

            // A row is "analyzed" the same way List's is: a classification with a matching xStock.
            val analyzed = rows.mapNotNull { row ->
                val asset = byTicker[row.ticker.uppercase()] ?: return@mapNotNull null // lint-allow uppercase: map key
                row to asset
            }

            val mints = analyzed.mapNotNull { (_, asset) -> asset.solanaMint }.distinct().take(TODAY_PRICE_BUDGET)
            val pricesAskedAt = clock.nowMillis()
            val fetch = if (mints.isEmpty()) null else runCatching { prices.pricesFirst(mints) }.getOrNull()

            val tracked = analyzed.mapNotNull { (row, asset) ->
                val mint = asset.solanaMint ?: return@mapNotNull null
                val entry = fetch?.priced?.get(mint) ?: return@mapNotNull null
                val quality = TrackingQuality.of(entry.usdPrice, entry.stockData?.price, entry.liquidity)
                if (quality !is TrackingQuality.Tracked) return@mapNotNull null
                TrackedRow(
                    ticker = row.ticker,
                    symbol = asset.symbol,
                    company = row.company ?: asset.name,
                    premiumPct = quality.premiumPct,
                    poolUsd = quality.poolUsd,
                )
            }.sortedByDescending { it.poolUsd }

            val analyzedKeys = analyzed.mapTo(HashSet()) { (row, _) -> row.ticker.uppercase() } // lint-allow uppercase: map key
            val withoutAnalysisTotal = assets.count { it.underlyingTicker.uppercase() !in analyzedKeys } // lint-allow uppercase: map key

            val leaderAnswer = runCatching { nextUpRepo.current() }.getOrNull() as? NextUpAnswer.Open
            val leaderRow = leaderAnswer?.rows?.firstOrNull()?.let { row ->
                val raw = row.weightRaw() ?: return@let null
                val asset = byTicker[row.ticker.trim().uppercase()] // lint-allow uppercase: map key
                TodayLeader(
                    ticker = row.ticker,
                    symbol = asset?.symbol,
                    company = asset?.name,
                    weightRaw = raw,
                    voters = row.voters,
                )
            }

            val generatedAtMillis = summaryResult.getOrNull()?.generatedAt?.let { stamp ->
                runCatching { Instant.parse(stamp).toEpochMilli() }.getOrNull()
            }
            val pricesFetchedAtMillis = if (fetch != null) pricesAskedAt else null

            _state.update { current ->
                current.copy(
                    todayLoading = false,
                    market = MarketHours.ofCatalog(assets, clock.nowMillis()),
                    analysisGeneratedAtMillis = generatedAtMillis,
                    pricesFetchedAtMillis = pricesFetchedAtMillis,
                    analyzedTotal = analyzed.size,
                    withoutAnalysisTotal = withoutAnalysisTotal,
                    tracked = tracked,
                    nextUpLeader = leaderRow,
                    voteRound = leaderAnswer?.round,
                    nowMillis = clock.nowMillis(),
                )
            }
        }
    }

    private companion object {
        /** Same cap `ListViewModel` prices at once: a few paced Jupiter chunks, not the whole catalog. */
        const val TODAY_PRICE_BUDGET = 200
    }
}
