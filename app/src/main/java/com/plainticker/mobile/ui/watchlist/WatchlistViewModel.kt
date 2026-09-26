package com.plainticker.mobile.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.prefs.NotificationPromptStore
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.MarketClock
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.ui.today.ReportRow
import com.plainticker.mobile.ui.today.TodayLeader
import com.plainticker.mobile.watchlist.DigestNotifier
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.DigestStore
import com.plainticker.mobile.watchlist.WatchedTicker
import com.plainticker.mobile.watchlist.WatchlistFacts
import com.plainticker.mobile.watchlist.WatchlistScheduler
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    /**
     * The watched tickers themselves, known before their rows are: "Reports this week" reads this
     * set to mark a row the reader already watches ([com.plainticker.mobile.ui.today.reportRowWatched]),
     * and it is also what lets a watched ticker's own figure be read from Today's one shared price
     * read instead of being priced twice, even while the watched rows are still cold.
     */
    val watchedTickers: Set<String> = emptySet(),
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
    // The venue line, "Reports this week" and Next up read the wider universe Watched never did,
    // off one join over the same repositories Stocks reads, so the two screens' coverage counts
    // cannot disagree. True before that join has ever run: every field below is at a default that
    // draws nothing rather than a guess (com.plainticker.mobile.ui.today.TodayModel.kt's functions
    // all return null for an unknown fact instead of inventing one).
    /**
     * True until `/summary`, the catalog and the leaderboard have all answered once, success or
     * failure alike: the fast half of the join, which blocks 1 (venue), 3 ("Reports this week"), 4
     * (next up) and 5 (the footer) all read and none of which needs a price - "Reports this week"
     * reads `/summary`'s own `next_report_date`/`next_report_confirmed` fields directly, the same
     * call this fast half already makes, unlike the retired Tracked block it replaced (which needed
     * Jupiter's prices to find a pool's own depth). Deliberately independent of [pricesLoading]:
     * the animator-zero stall (docs/qa-checklist.md, 2026-09-22) was these blocks sitting undrawn
     * for as long as Jupiter's own paced fetch took, even though not one of them reads a price. See
     * [WatchlistViewModel.loadToday]'s own doc for the fetch that used to delay them regardless.
     */
    val todayLoading: Boolean = true,
    /**
     * True until Jupiter's prices have answered once, success or failure alike: what the Watched
     * block's own figures need (the one thing here that does read a price) and nothing else on this
     * screen does any more, since the retired Tracked block (renamed [pricesLoading] from
     * `trackedLoading` when it went: task DESIGN.md's own brief, "remove the Tracked block and its
     * now-dead code") was the other reader. Independent of [todayLoading] on purpose; see that
     * field's own doc.
     */
    val pricesLoading: Boolean = true,
    /** Where the venue is right now, or null while no catalog has answered at all. */
    val market: MarketStatus? = null,
    val analysisGeneratedAtMillis: Long? = null,
    /** When this device last asked Jupiter, whether or not anything came back priced. */
    val pricesFetchedAtMillis: Long? = null,
    /** Rows with a PlainTicker classification and a matching xStock, the same count Stocks shows. */
    val analyzedTotal: Int = 0,
    val withoutAnalysisTotal: Int = 0,
    /**
     * "Reports this week"'s own candidates: every covered company with a known next-report date,
     * not yet narrowed to the week or capped ([com.plainticker.mobile.ui.today.reportsThisWeek] does
     * both). Needs no price, so it is on state as soon as [todayLoading] settles.
     */
    val reports: List<ReportRow> = emptyList(),
    /**
     * Whether the server has sent a next-report date for at least one row on `/summary`, anywhere,
     * not only within this week: the one signal this app has for telling "the server has not
     * deployed `next_report_date` yet, every row reads null" apart from "the field is live and a
     * quiet week genuinely has nothing." False draws as though nothing is known, which is also its
     * default before [todayLoading] first settles, since a field that has never answered is exactly
     * the same shape as a field that does not exist yet.
     */
    val reportsKnown: Boolean = false,
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
    // venue line, "Reports this week" and Next up need the wider universe Watched never did.
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
    private val nextUpRepo: NextUpRepository,
    /**
     * Asked by [watch], Today's own Watch on a first open: the same store Detail's Watch asks, so
     * the system dialog comes once, at the first watch, wherever that happens. Null never asks.
     */
    private val prompts: NotificationPromptStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WatchlistUiState(
            watched = watchlist.tickers.value.size,
            watchedTickers = watchlist.tickers.value,
            isLoading = watchlist.tickers.value.isNotEmpty(),
            digest = digests.record.value,
            notificationsOn = notifier.enabled(),
            nowMillis = clock.nowMillis(),
        ),
    )
    val state: StateFlow<WatchlistUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var todayJob: Job? = null

    /**
     * The venue as a clock ([MarketClock] has the stale "Closed" the Seeker drew on 24 Sep 2026):
     * recomputed on every resume and at every boundary while Today is on screen, never only once
     * at load.
     */
    private val marketClock = MarketClock(clock, catalog, viewModelScope) { market ->
        _state.update { it.copy(market = market, nowMillis = clock.nowMillis()) }
    }

    /**
     * The one price read Today draws from. Today's join prices every analyzed token at once; a
     * watched ticker's figure is taken from that same read whenever it covers the ticker's mint,
     * so a watched row and anything else on the screen can never print two different premiums for
     * one token (the Seeker drew METAx at +0.15% and +0.13% on one screen). A watched ticker the
     * join does not cover (not analyzed) keeps the figure [WatchlistFacts] read for it.
     */
    private var sharedPrices: Map<String, PriceEntry> = emptyMap()

    init {
        viewModelScope.launch {
            watchlist.tickers.collect { watched ->
                _state.update { it.copy(watched = watched.size, watchedTickers = watched, isLoading = watched.isNotEmpty()) }
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
     * Today's own Watch, on a "Reports this week" row of a first open. True when the caller should
     * now ask for the notification permission: this was the first ticker ever watched and nothing
     * has asked before, the exact rule Detail's own Watch keeps ([com.plainticker.mobile.ui.detail.DetailViewModel.toggleWatch]),
     * read through the same [NotificationPromptStore], so the dialog comes once, after the first
     * watch, whichever screen that watch happened on.
     */
    fun watch(ticker: String): Boolean {
        val before = watchlist.tickers.value
        if (ticker in before) return false
        watchlist.add(ticker)
        val store = prompts ?: return false
        if (before.isNotEmpty() || store.hasAsked()) return false
        store.setAsked()
        return true
    }

    /**
     * Today came back to the foreground: re-read the notification setting, move the clock, and
     * recompute the venue, then keep it current at every boundary while Today stays resumed.
     */
    fun onResume() {
        notificationsChanged()
        marketClock.onResume()
    }

    /** Today left the foreground: the boundary job stops, nothing runs in the background. */
    fun onPause() = marketClock.onPause()

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
                    rows = loaded.rows.map(::sharedPrice),
                    analysisUnavailable = loaded.analysisUnavailable,
                    catalogUnavailable = loaded.catalogUnavailable,
                    pricesUnavailable = loaded.pricesUnavailable,
                    nowMillis = clock.nowMillis(),
                )
            }
        }
    }

    /**
     * Blocks 1, 3 and 4 (docs/design-research-2026-09-21.md section 3): the venue line, "Reports
     * this week" and Next up. One join, once per load, over `/summary`, the catalog and the
     * leaderboard, the same sources `ListViewModel`'s own join reads, so "22 of 160" here and
     * Stocks' coverage counts can never print two different numbers.
     *
     * **Three network reads, not run one after another.** `/summary`, the catalog and the
     * leaderboard ([nextUpRepo]) answer three unrelated questions and none needs another's result
     * to be *asked*. [summaryDeferred], [catalogDeferred] and [leaderAnswerDeferred] start together
     * with [async], so the network cost is one round trip, not their sum.
     *
     * **Two settles, not one.** The animator-zero stall (docs/qa-checklist.md, 2026-09-22:
     * venue card and the old "Tracked today" undrawn or skeletal for three to four seconds at
     * normal motion, eight to eleven with the animator forced to zero) was traced first to this
     * function, not to motion: what held the venue line, Next up and the footer back was this
     * function publishing every field in one `_state.update` at the very end, after awaiting
     * [prices.pricesFirst] - Jupiter's own paced fetch, several seconds by design
     * ([PriceRepository]'s own doc) - even though none of those three blocks reads a price.
     * [todayLoading] now flips the moment the fast three have answered, in its own update, with
     * [market], [nextUpLeader], [voteRound], the two coverage totals and, since 2026-09-26,
     * ["Reports this week"][reports] itself already on state: `next_report_date` and
     * `next_report_confirmed` are `/summary` row fields, not a price, so the block that replaced
     * Tracked today needs nothing prices answer, unlike the block it replaced. [pricesLoading]
     * (`trackedLoading` before that rename) flips separately once prices have answered, because
     * the Watched block's own figures are the one thing here that still needs one. That closed
     * most of the gap but not all of it: a second stall remained in `amberBlockEntrance` itself
     * (`ui/today/TodayScreen.kt`) - it read this state correctly but still relied on
     * `animateFloatAsState` to *apply* the settled value, which needs a platform frame to fire, and
     * a cold device with every animator scale at 0 sometimes never schedules that frame before a
     * scroll forces one. That function's own doc comment has the fix: motion off now returns the
     * unmodified block, never touching the animation clock at all.
     *
     * Never throws: a source that did not answer costs its own facts (the fields below stay at
     * their [WatchlistUiState] defaults, which is what [com.plainticker.mobile.ui.today.TodayModel.kt]'s
     * functions read as "not yet known") and nothing else on this screen.
     */
    private fun loadToday() {
        todayJob?.cancel()
        _state.update { it.copy(todayLoading = true, pricesLoading = true) }
        todayJob = viewModelScope.launch {
            val summaryDeferred = async { runCatching { summaries.summary() } }
            val catalogDeferred = async {
                runCatching { catalog.catalog() }.getOrNull().orEmpty().filter { it.solanaMint != null }
            }
            val leaderAnswerDeferred = async { runCatching { nextUpRepo.current() }.getOrNull() as? NextUpAnswer.Open }

            val summaryResult = summaryDeferred.await()
            val assets = catalogDeferred.await()
            val rows = summaryResult.getOrNull()?.rows.orEmpty()
                .distinctBy { it.ticker.uppercase() } // lint-allow uppercase: map key
            val byTicker = assets.associateBy { it.underlyingTicker.uppercase() } // lint-allow uppercase: map key

            // A row is "analyzed" the same way List's is: a classification with a matching xStock.
            val analyzed = rows.mapNotNull { row ->
                val asset = byTicker[row.ticker.uppercase()] ?: return@mapNotNull null // lint-allow uppercase: map key
                row to asset
            }

            val analyzedKeys = analyzed.mapTo(HashSet()) { (row, _) -> row.ticker.uppercase() } // lint-allow uppercase: map key
            val withoutAnalysisTotal = assets.count { it.underlyingTicker.uppercase() !in analyzedKeys } // lint-allow uppercase: map key

            // "Reports this week"'s own candidates: every covered company with a known next-report
            // date, not yet narrowed to the week (com.plainticker.mobile.ui.today.reportsThisWeek
            // does that against the reader's own "today", which this join does not have). Needs no
            // price, unlike the Tracked block this replaced.
            val reports = analyzed.mapNotNull { (row, asset) ->
                val date = row.nextReportLocalDate() ?: return@mapNotNull null
                ReportRow(
                    ticker = row.ticker,
                    symbol = asset.symbol,
                    company = row.company ?: asset.name,
                    date = date,
                    confirmed = row.nextReportConfirmed,
                )
            }
            // Whether the server has sent this field at all, read off every /summary row (not only
            // the analyzed ones): a server that predates it, or has not deployed it yet, answers
            // every row with a null date, which is exactly the shape a genuinely quiet dataset would
            // also have. This is this app's one signal for telling the two apart (WatchlistUiState.reportsKnown's own doc).
            val reportsKnown = rows.any { it.nextReportLocalDate() != null }

            val leaderAnswer = leaderAnswerDeferred.await()
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

            // The fast half settles here, before prices are ever asked for: the venue line,
            // "Reports this week", Next up and the footer need nothing below this point.
            _state.update { current ->
                current.copy(
                    todayLoading = false,
                    market = marketClock.setAssets(assets),
                    analysisGeneratedAtMillis = generatedAtMillis,
                    analyzedTotal = analyzed.size,
                    withoutAnalysisTotal = withoutAnalysisTotal,
                    reports = reports,
                    reportsKnown = reportsKnown,
                    nextUpLeader = leaderRow,
                    voteRound = leaderAnswer?.round,
                    nowMillis = clock.nowMillis(),
                )
            }

            val mints = analyzed.mapNotNull { (_, asset) -> asset.solanaMint }.distinct().take(TODAY_PRICE_BUDGET)
            val pricesAskedAt = clock.nowMillis()
            val fetch = if (mints.isEmpty()) null else runCatching { prices.pricesFirst(mints) }.getOrNull()

            val pricesFetchedAtMillis = if (fetch != null) pricesAskedAt else null
            fetch?.priced?.let { sharedPrices = sharedPrices + it }

            _state.update { current ->
                current.copy(
                    pricesLoading = false,
                    pricesFetchedAtMillis = pricesFetchedAtMillis,
                    rows = current.rows.map(::sharedPrice),
                    nowMillis = clock.nowMillis(),
                )
            }
        }
    }

    /** A watched row priced from [sharedPrices] when that read covers its mint; unchanged otherwise. */
    private fun sharedPrice(row: WatchedTicker): WatchedTicker {
        val entry = row.mint?.let { sharedPrices[it] } ?: return row
        return row.copy(priceUsd = entry.usdPrice, referencePriceUsd = entry.stockData?.price, poolUsd = entry.liquidity)
    }

    private companion object {
        /** Same cap `ListViewModel` prices at once: a few paced Jupiter chunks, not the whole catalog. */
        const val TODAY_PRICE_BUDGET = 200
    }
}
