package com.plainticker.mobile.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.PriceFetch
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.data.snapshot.toSummaryRow
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock
import com.plainticker.mobile.data.snapshot.toXStockAsset
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.Coverage
import com.plainticker.mobile.repo.MarketClock
import com.plainticker.mobile.repo.CatalogUpdate
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.SnapshotRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.watchlist.DigestStore
import com.plainticker.mobile.watchlist.WatchedReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** The one word right of the composite. Never a verdict: it describes the classification. */
enum class RowState { STRONG, FAIR, WEAK }

/**
 * Where a row's quote stands with Jupiter, so a row with no figure can say why (device QA of
 * 1.3.17: JEFx's row sat with no gap and no depth and nothing saying Jupiter has no price for it,
 * and a cold start drew every row bare for seconds with no sign anything was coming).
 */
enum class RowQuote {
    /** Not asked yet, or the ask is still out. */
    PENDING,

    /** Jupiter answered about this mint, with a price or without one. */
    ANSWERED,

    /** The ask never landed (a refused chunk): the banner says the prices are missing. */
    UNREACHED,
}

/** One row of the list: an analyzed xStock, or a catalog xStock PlainTicker has not classified. */
data class ListRow(
    /** Underlying equity ticker, e.g. "AAPL". The join key to PlainTicker and the Detail route. */
    val ticker: String,
    /** xStock token symbol, e.g. "AAPLx"; null only while the catalog is unavailable. */
    val symbol: String?,
    val mint: String?,
    val company: String?,
    /** Percentile 0 to 100. The row draws it as an integer, never as the raw float. */
    val composite: Double?,
    val state: RowState?,
    val stale: Boolean,
    val ageDays: Int?,
    val priceUsd: Double?,
    /** The underlying share's reference price from Price v3 `stockData`, when Jupiter has one. */
    val referencePriceUsd: Double?,
    /** The pool behind the quote in USD, from Price v3 `liquidity`. Jupiter may omit it. */
    val poolUsd: Double?,
    val analyzed: Boolean,
    /**
     * `/summary.sector` (PlainTickerModels.kt), read only for an analyzed row: the classification
     * is against a sector, and this is what chapters the List by one (task A1, `analyzedChapters`
     * in this package). Null for every price-only row, and for an analyzed row `/summary` sent no
     * sector for, which is grouped under a trailing chapter rather than dropped.
     */
    val sector: String? = null,
    /**
     * True when [composite] came back null under the Pro-numbers lock (founder decision
     * 2026-09-23) rather than because this row has no analysis: `/summary` lists only classified
     * tickers, so an analyzed row's own composite is never null for any other reason
     * ([SummaryRow.toListRow]'s own doc comment has the exact signal). Always false on a
     * price-only row, where [composite] is null because there is no analysis to withhold in the
     * first place. Reused everywhere [ListRow] itself is: a row rebuilt by [withPrice] or
     * [carryingPriceFrom] keeps whatever this field already was, the same way it keeps [analyzed].
     */
    val locked: Boolean = false,
    /**
     * True for a covered company `/summary` sent no row for ([com.plainticker.mobile.repo.Coverage]
     * added it bare): it has a full page but no classification against its sector (QA of 1.3.20:
     * ABBVx, CMCSAx, MAx, NKEx and Vx, `class_state` "unavailable", thin cohort). There is no score
     * to show and none to lock, so the row says so in neutral words instead of "Pro", which it drew
     * even for a Pro reader. Never true together with [locked].
     */
    val unclassified: Boolean = false,
    /**
     * False for a price-only row whose underlying is not US-listed ([XStockAsset.isUsUnderlying]):
     * the server refuses a vote for it, so the row offers no Vote action. True on every analyzed
     * row, which offers no vote anyway.
     */
    val votable: Boolean = true,
    /** Where this row's quote stands with Jupiter; see [RowQuote]. */
    val quote: RowQuote = RowQuote.PENDING,
) {
    /** What the row shows left: the token symbol once the catalog is known, else the ticker. */
    val display: String get() = symbol ?: ticker

    /**
     * The age the meta line shows, in whole days: an analysis younger than a day has no age
     * worth printing, the same rule Detail keeps for its header ("only when more than 24 h",
     * docs/data-map.md). It would otherwise read "Analysis 0 d old" on the freshest rows.
     */
    val ageForMeta: Int? get() = ageDays?.takeIf { it >= 1 }

    /**
     * How far this row's quote can be trusted, from the one rule in [TrackingQuality]. Null while
     * Jupiter has not priced the row at all: no quote, so no tracking question, and the banner
     * already says the prices are missing.
     */
    val tracking: TrackingQuality? get() = TrackingQuality.of(priceUsd, referencePriceUsd, poolUsd)

    /**
     * The premium against the NYSE close the row may draw, in percent. Null when either side is
     * missing, so a row whose price never arrived keeps everything else, and null below the
     * liquidity floor, where the number would be arithmetic off a dead pool rather than a price.
     */
    val premiumPct: Double? get() = tracking?.premiumPct
}

/**
 * The one banner slot, in DESIGN.md section 4 order: offline, then stale, then hours, then
 * device. The offline tier is a list that could not be loaded at all or came from the bundled
 * snapshot, the stale tier is a list on which no analysis is fresh, the hours tier is where the
 * exchange the premiums are measured against is, and the device tier is one source the rows
 * depend on being missing while the rest of the screen stands.
 *
 * The hours tier was empty until 2026-09-13 and the omission was visible on the device: every
 * tracked row prints a premium "vs NYSE close" against a last close, and Detail carried "The
 * NYSE is closed, the reference is the last close" one tap away while the List carried nothing.
 * The same caveat is owed on both surfaces or on neither, so both now read it out of the same
 * strings and decide it from the same [MarketHours].
 */
sealed interface ListBanner {
    /** Offline tier: nothing to draw, the user gets one Retry. */
    data object Unavailable : ListBanner

    /**
     * Offline tier: the bundled snapshot is on screen and the network is still being asked.
     * This is the cold-start banner and the only honest thing to say while snapshot rows are
     * drawn: they are a capture of a named day, not the live list, and they are about to be
     * replaced. It carries no Retry, because a retry is exactly what is already running.
     */
    data class SnapshotRefreshing(val capturedOn: LocalDate?) : ListBanner

    /** Offline tier: the network has settled and the rows still come from the bundled snapshot. */
    data class Snapshot(val capturedOn: LocalDate?) : ListBanner

    /** Stale tier: every analysis on screen is old; [newestDays] is the youngest of them. */
    data class Stale(val newestDays: Int) : ListBanner

    /** Hours tier: the exchange is shut, so every premium on screen is against a last close. */
    data object MarketClosed : ListBanner

    /** Hours tier: shut by the bundled weekday schedule, because no venue block answered. */
    data object MarketClosedLocal : ListBanner

    /** Hours tier: open by that same schedule, which knows no holidays and says so. */
    data object MarketOpenLocal : ListBanner

    /** Device tier: the xStocks catalog did not answer, so no row carries a token or a price. */
    data object CatalogUnavailable : ListBanner

    /** Device tier: `/summary` did not answer, so the catalog is on screen with no analysis. */
    data object AnalysisUnavailable : ListBanner

    /** Device tier: Jupiter refused every chunk. */
    data object PricesUnavailable : ListBanner

    /** Device tier: Jupiter refused some chunks; the rows it priced keep their prices. */
    data object PricesPartial : ListBanner
}

data class ListUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    /** Rows with a PlainTicker classification, composite descending. */
    val analyzed: List<ListRow> = emptyList(),
    /** Catalog xStocks PlainTicker has not classified, symbol ascending. */
    val withoutAnalysis: List<ListRow> = emptyList(),
    /**
     * The leaders of SKR-weighted coverage curation as the server sent them, heaviest first:
     * the uncovered tickers staked SKR has voted to cover next. Empty when the call failed or
     * answered nothing, and the screen raises no banner for it either way; the strip that draws
     * them ([nextUpStrip]) is simply not there. A quiet source, on purpose: the list stands
     * without it.
     */
    val nextUp: List<NextUpRow> = emptyList(),
    /** How many tickers are watched, for the Watched chip. */
    val watched: Int = 0,
    /**
     * The watched tickers themselves, uppercase (the same normal form [WatchlistStore] keeps),
     * for the Stocks screen's Watched filter chip
     * ([com.plainticker.mobile.ui.stocks.StocksFilter.Watched]): a chip needs to ask "is this
     * row's own ticker in the set" and [watched] only ever answers "how many". Read from the same
     * flow as [watched] so the two can never name a different count, and left off outside
     * ui/stocks and this class on purpose: nothing else on this screen filters by it.
     */
    val watchedTickers: Set<String> = emptySet(),
    /**
     * The nearest report among the watched tickers, as the last daily check found it. It comes
     * from the digest the check stored rather than from a call of this screen's own: the report
     * date lives in the per-ticker analysis payload, and fetching one per watched ticker on the
     * path to the first row would put a second network round trip in front of the list.
     *
     * Null when no check has run yet, when nothing it saw reports ahead, and when the ticker it
     * named is no longer watched: the record can be a day old, and an hour of the strip naming a
     * company the reader has just taken off the list is an hour of the app stating what is no
     * longer true.
     */
    val nextReport: WatchedReport? = null,
    val generatedAt: String? = null,
    /** The day the bundled snapshot was captured, when the rows came from it. */
    val snapshotCapturedOn: LocalDate? = null,
    /** Both sources failed and there was no snapshot to fall back to. */
    val failed: Boolean = false,
    val fromSnapshot: Boolean = false,
    /**
     * A source is still being asked, so what is on screen may still be replaced. It is what
     * separates the two snapshot banners: refreshing while the network is still out, settled
     * once every source has either answered or failed.
     */
    val refreshing: Boolean = false,
    val catalogUnavailable: Boolean = false,
    /** `/summary` did not answer while the catalog did, so no row on screen has an analysis. */
    val analysisUnavailable: Boolean = false,
    val pricesUnavailable: Boolean = false,
    val pricesPartial: Boolean = false,
    /**
     * True once a price run has finished, whatever it found. Until then a row with a mint and no
     * figure is still being priced, and says so quietly, and the Deep pool chip's slot is held
     * (device QA of 1.3.17: the chip arrived seconds in and shoved the whole chip row sideways).
     */
    val pricesSettled: Boolean = false,
    /** The youngest analysis on screen, set only when every analyzed row is stale. */
    val allStaleDays: Int? = null,
    /**
     * Whether the refresh has been out long enough for the snapshot line to be worth its space.
     *
     * The line itself is honest and stays: what it says about the rows is true for as long as
     * [fromSnapshot] is. What it is not worth is a warm launch, where the Seeker measured it up
     * at 3.53 s and gone at 4.20 s, 0.70 s in which the whole list moved down by the height of a
     * banner and back (docs/data-map.md). So it waits out [ListViewModel.SNAPSHOT_BANNER_GRACE_MS]
     * before it may be drawn, and a refresh that finishes inside that never draws it at all.
     * During the grace the slot keeps the hours line it has held since the first frame (device QA
     * of 1.3.18) and falls through to nothing lower, because a lower tier would be a different
     * sentence arriving and leaving in the same second.
     */
    val snapshotBannerDue: Boolean = false,
    /**
     * The analysis on screen is the bundled snapshot's (the live `/summary` has not answered). Only
     * then does a refresh raise the snapshot line: once the analysis is live and only the token
     * catalog is still the bundled one, the rows say nothing a live catalog would change, and the
     * line arriving and leaving would move the list twice for nothing (device QA of 1.3.18).
     */
    val analysisFromSnapshot: Boolean = false,
    /**
     * No live catalog has answered yet, so the venue comes from the exchange calendar alone. A
     * closed exchange is then said the plain way, not as "its live hours did not load", which is not
     * yet known to be true and is a longer line that would change height when the hours land.
     */
    val hoursPending: Boolean = false,
    /**
     * Where the exchange behind these rows is, or null while no catalog has been read. Every
     * tracked row measures its premium against the NYSE close, so this is what says whether that
     * close is a live price or last night's.
     */
    val market: MarketStatus? = null,
    /**
     * A pull to refresh is running: the indicator stands until the analysis, the catalog and the
     * first screenful of fresh prices have answered ([ListViewModel.pull]).
     */
    val pulling: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && !failed && analyzed.isEmpty() && withoutAnalysis.isEmpty()

    /** An empty result the reader asked for: one sentence on screen, not an error. */
    val searchMiss: Boolean get() = isEmpty && query.isNotBlank()

    /** Both sources answered and neither had anything. One sentence, so the screen is never blank. */
    val emptyResult: Boolean get() = isEmpty && query.isBlank()

    val banner: ListBanner?
        get() = when {
            failed -> ListBanner.Unavailable
            fromSnapshot && refreshing && analysisFromSnapshot && snapshotBannerDue ->
                ListBanner.SnapshotRefreshing(snapshotCapturedOn)

            // The grace, or live analysis on the bundled catalog: the slot keeps the venue line it
            // has held since the first frame, so nothing arrives and leaves under the reader.
            fromSnapshot && refreshing -> hours

            fromSnapshot -> ListBanner.Snapshot(snapshotCapturedOn)
            allStaleDays != null -> ListBanner.Stale(allStaleDays)
            hours != null -> hours
            catalogUnavailable -> ListBanner.CatalogUnavailable
            analysisUnavailable -> ListBanner.AnalysisUnavailable
            pricesUnavailable -> ListBanner.PricesUnavailable
            pricesPartial -> ListBanner.PricesPartial
            else -> null
        }

    /**
     * The hours tier, decided exactly as Detail decides it (`DetailUiState.banner`) minus the
     * halt, which is one asset's fact and not a list's. An open exchange says nothing, because
     * then the reference the rows quote is simply the live price and there is no caveat to make.
     */
    private val hours: ListBanner?
        get() {
            val market = market ?: return null
            // While the live hours are still on their way the calendar is all there is, and it is
            // not yet a failure to load them: said as the venue would say it.
            val guessed = market.source == MarketSource.LOCAL_SCHEDULE && !hoursPending
            return when {
                market.regularSession && guessed -> ListBanner.MarketOpenLocal
                market.regularSession -> null
                guessed -> ListBanner.MarketClosedLocal
                else -> ListBanner.MarketClosed
            }
        }
}

/**
 * summary joined with the catalog joined with prices (plan T8, docs/data-map.md "List (T8)").
 *
 * Four rules the first device pass caught are enforced here rather than in the screen: the
 * Ukrainian `headline` never reaches a [ListRow]; `composite` is carried as the percentile the
 * row draws as an integer, not as a raw float; an analyzed company with no xStock is not an
 * xStock row, so it is in neither section; and the meta line's premium comes from Price v3.
 *
 * **The bundled snapshot draws first.** docs/data-map.md timed a cold start at seventeen seconds
 * to the first row, nearly all of it the 4.31 MB xStocks catalog, which a row cannot do without
 * because it carries the symbol and the mint. The snapshot is that catalog and that analysis,
 * already on the device, so it is read before any network call is made. It is drawn when the live
 * analysis has not answered within [SNAPSHOT_BANNER_GRACE_MS] (device QA of 1.3.18: drawn at once,
 * another day's coverage was reordered under the reader seconds later), and the network then
 * replaces it in place. Three rules keep that honest:
 *
 * 1. A snapshot row carries no price, because the snapshot carries none. It shows its analysis
 *    and nothing where the premium goes. Prices come from Jupiter, live, or not at all.
 * 2. The reader is told, in the one banner slot, that the list is a bundled snapshot of a named
 *    day and is being refreshed. The banner stands for exactly as long as any part of what is on
 *    screen still comes from the snapshot, and goes when nothing does.
 * 3. The replacement is a data swap. Rows are rebuilt from the newer source in the same sort
 *    order, and nothing about the arrival is animated, so live data landing is never a jump.
 *
 * Prices are asked for on refresh and never per recomposition: the first screenful so the top
 * of the list draws with numbers, then the rest, which the repository serves from its cache for
 * the window already fetched. Jupiter's keyless budget is 0.5 requests per second, so the run
 * is capped at [PRICE_BUDGET] mints; past that a row shows its analysis without a premium
 * instead of the screen spending half a minute on tokens nobody has scrolled to.
 */
class ListViewModel(
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
    private val snapshots: SnapshotRepository,
    private val nextUp: NextUpRepository,
    private val watchlist: WatchlistStore,
    private val digests: DigestStore,
    private val clock: Clock = WallClock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ListUiState(
            isLoading = true,
            watched = watchlist.tickers.value.size,
            watchedTickers = watchlist.tickers.value,
            hoursPending = true,
        ),
    )
    val state: StateFlow<ListUiState> = _state.asStateFlow()

    // ---- What each source has contributed so far ----------------------------------------
    // The screen is a function of these, recomputed by [republish] whenever one of them moves.
    // Keeping the sources apart rather than only their joined result is what lets a later
    // source replace an earlier one in place, without either having to know about the other.

    private var snapshotRows: List<SummaryRow> = emptyList()

    /** Covered tickers with no row of their own, added bare by [Coverage.rows], per source. */
    private var snapshotBare: Set<String> = emptySet()
    private var liveBare: Set<String> = emptySet()
    private var snapshotAssets: List<XStockAsset> = emptyList()
    private var snapshotCapturedOn: LocalDate? = null
    private var haveSnapshot = false

    /** `/summary` rows, null until it answers; [summarySettled] separates "not yet" from "never". */
    private var liveRows: List<SummaryRow>? = null
    private var summarySettled = false
    private var generatedAt: String? = null

    private var liveAssets: List<XStockAsset> = emptyList()

    /** True when [liveAssets] is a whole catalog rather than the first pages of one. */
    private var catalogWhole = false
    private var catalogSettled = false

    private var allAnalyzed: List<ListRow> = emptyList()
    private var allWithoutAnalysis: List<ListRow> = emptyList()
    private var refreshJob: Job? = null
    private var priceJob: Job? = null
    private var bannerJob: Job? = null
    private var repriceJob: Job? = null

    /** When the last price run finished, by [clock]; null until one has. */
    private var pricedAtMillis: Long? = null

    /**
     * The venue behind the hours banner, as a clock rather than a photograph: the same
     * [MarketClock] Today runs, so Stocks and Today can never disagree about where the NYSE is,
     * and neither keeps showing the status the catalog cache was fetched under.
     */
    private val marketClock = MarketClock(clock, catalog, viewModelScope, localUntilKnown = true) { market ->
        _state.update { it.copy(market = market, hoursPending = hoursPending()) }
    }

    /**
     * The live hours are still on their way: the catalog has not settled, or a live block is being
     * read (by this screen or Today). Never "did not load" while a load is in flight (final QA of
     * 1.3.19: the line appeared mid-load, while Today already said "Opens today at 16:30").
     */
    private fun hoursPending(): Boolean = !catalogSettled || marketClock.loading

    /**
     * True while a first load holds the bundled snapshot back for the live analysis (device QA of
     * 1.3.18). The snapshot is a capture of another day's coverage: drawn first, it was replaced
     * seconds later by a list in a different order with different sectors (Communication Services
     * 11, then 5), which read as the list jumping under the reader. `/summary` answers in well under
     * a second, so the first draw now waits for it, up to [SNAPSHOT_BANNER_GRACE_MS], and the
     * snapshot is drawn only when it has not answered by then or has failed.
     */
    private var holdingSnapshot = false
    private var bannerGracePassed = false
    private var pricesQueued = false
    private var pricedMints: List<String>? = null

    init {
        viewModelScope.launch {
            combine(watchlist.tickers, digests.record) { watched, digest ->
                watched to digest.nextReport?.takeIf { it.ticker in watched }
            }.collect { (watched, report) ->
                _state.update { it.copy(watched = watched.size, nextReport = report, watchedTickers = watched) }
            }
        }
        // The one price source every screen observes (PriceRepository.latest): a quote Today or
        // Detail fetched redraws the same mint here at once, so no row keeps a private stale copy.
        viewModelScope.launch {
            prices.latest.collect(::applyLatest)
        }
        // The venue from the first frame, off the exchange calendar until a catalog answers, so
        // the hours banner holds its slot instead of arriving late and pushing the list down.
        marketClock.tick()
        load(userAsked = false)
    }

    /**
     * The Retry the reader taps, wherever the screen offers it. It is the one path that may step
     * over the caches: the catalog is kept on disk for a day, and without this a token listed
     * this morning could not be seen at all until tomorrow, whatever the reader did.
     */
    fun refresh() = load(userAsked = true)

    /** Counts the first-screenful price answers, so a pull knows when fresh figures are drawn. */
    private val windowsPriced = MutableStateFlow(0)

    /**
     * Pull to refresh (final QA of 1.3.19: Stocks had none, and a figure never changed). Asks the
     * analysis and the catalog again the way a load does, and Jupiter past the 30 s price cache,
     * with the indicator up until the first screenful of new figures is drawn (at least
     * [PULL_MIN_MS], at most [PULL_MAX_MS]). The catalog is not fetched whole again: its hours are
     * the part that goes stale, and [MarketClock] reads those live on its own.
     */
    fun pull() {
        if (_state.value.pulling) return
        _state.update { it.copy(pulling = true) }
        viewModelScope.launch {
            val least = launch { delay(PULL_MIN_MS) }
            try {
                prices.forget()
                val before = windowsPriced.value
                load(userAsked = false)
                refreshJob?.join()
                if (priceableMints().isNotEmpty()) {
                    withTimeoutOrNull(PULL_MAX_MS) { windowsPriced.first { it > before } }
                }
                least.join()
            } finally {
                _state.update { it.copy(pulling = false) }
            }
        }
    }

    /**
     * Stocks came back to the foreground: recompute the venue now and at every boundary after, and
     * keep the prices as fresh as the cache Detail reads from ([REPRICE_MS]).
     *
     * **Why the prices refresh while Stocks is shown** (device QA of 1.3.16). The list priced its
     * rows once per load and never again, while Detail asks [PriceRepository] on open and gets a
     * fresh quote once the 30 s cache has expired. Both go through the same rule
     * ([com.plainticker.mobile.data.jupiter.TrackingQuality]) against the same NYSE close from the
     * same Jupiter Price v3 entry, so the two figures only differed by when the token price was
     * read: AAPLx read -0.40% on the list, from a quote minutes old, and -0.68% on Detail seconds
     * later, from a new one. Re-pricing on the cache's own window while the list is on screen, and
     * once on coming back to it, means a row and the Detail it opens read the same cached quote.
     */
    fun onResume() {
        marketClock.onResume()
        repriceJob?.cancel()
        repriceJob = viewModelScope.launch {
            while (isActive) {
                repriceIfStale()
                delay(REPRICE_MS)
            }
        }
    }

    /** Stocks left the foreground: the boundary job and the re-pricing stop. */
    fun onPause() {
        marketClock.onPause()
        repriceJob?.cancel()
        repriceJob = null
    }

    /**
     * Asks for the same mints again once the last run is older than [REPRICE_MS]. Before any run
     * has finished there is nothing to refresh: the load's own run owns the first pricing.
     */
    private fun repriceIfStale() {
        val at = pricedAtMillis ?: return
        if (clock.nowMillis() - at < REPRICE_MS) return
        pricedMints = null
        schedulePrices()
    }

    /**
     * Asks every source again and draws whatever comes back.
     *
     * Nothing already drawn is taken away first. A retry used to forget the live sources and
     * republish the bundled snapshot, so the list a reader was looking at went back to the
     * capture of an older day, lost any token listed since it, and then grew back as the pages
     * landed. What is on screen now stays on screen until something better arrives: a source
     * that fails leaves the last good answer alone, and a catalog that is still paging lands on
     * top of the whole one rather than replacing it with page zero.
     */
    private fun load(userAsked: Boolean) {
        refreshJob?.cancel()
        priceJob?.cancel()
        forgetWhatTheSourcesSaid()

        // Skeletons are for a screen with nothing on it. A retry over a drawn list keeps the list.
        val nothingDrawn = allAnalyzed.isEmpty() && allWithoutAnalysis.isEmpty()
        _state.update { it.copy(isLoading = nothingDrawn, failed = false, refreshing = true) }
        holdingSnapshot = nothingDrawn

        bannerJob?.cancel()
        bannerGracePassed = false
        bannerJob = viewModelScope.launch {
            delay(SNAPSHOT_BANNER_GRACE_MS)
            bannerGracePassed = true
            holdingSnapshot = false
            republish()
            schedulePrices()
        }

        refreshJob = viewModelScope.launch {
            // The bundled snapshot, before a single network call. It is a whole list that is
            // already on the device, so there is no reason for the reader to watch skeletons
            // while the catalog is paid for.
            runCatching { snapshots.listSnapshot() }.getOrNull()?.takeUnless { it.isEmpty }?.let { snapshot ->
                // No per-row age: the banner names the day the snapshot was captured, and a row
                // saying "3 days old" beside "snapshot of 19 Sep" counted from a day the reader
                // cannot see (device QA of 1.3.18). One age statement, the banner's.
                val bundledRows = snapshot.rows.map { it.toSummaryRow().copy(ageDays = null) }
                snapshotRows = Coverage.rows(bundledRows)
                snapshotBare = Coverage.bare(bundledRows)
                snapshotAssets = snapshot.assets.map { it.toXStockAsset() }
                snapshotCapturedOn = snapshot.capturedOn
                haveSnapshot = true
                republish()
                schedulePrices()
            }

            // Both halves at once. They used to run one after the other, which put the 378 ms
            // analysis behind the seven-second catalog for no reason at all.
            val summary = launch {
                val answer = runCatching { summaries.summary() }
                // A refresh that failed takes nothing away: the rows it could not replace are
                // still the best answer this screen has.
                answer.getOrNull()?.let {
                    // Every covered company, a row or not (QA of 1.3.19: ABBV has a page and no row).
                    liveRows = Coverage.rows(it)
                    liveBare = Coverage.bare(it)
                    generatedAt = it.generatedAt
                }
                summarySettled = true
                holdingSnapshot = false
                republish()
                schedulePrices()
            }
            // The catalog arrives page by page. Each one refines what is already drawn: the
            // sort keys a row is placed by (its composite, or its symbol) do not change when a
            // page lands, so nothing the reader is looking at moves.
            val assets = launch {
                runCatching { catalog.catalogUpdates(userAsked).collect(::onCatalog) }
                catalogSettled = true
                republish()
                schedulePrices()
            }
            // The leaders staked SKR chose (docs/skr-curation-spec-2026-09-13.md, step 3). A quiet
            // source: it settles nothing about the list, is not waited for, raises no banner, and a
            // failure simply leaves the strip undrawn. It is a child of this refresh, so a retry
            // asks for it again and a cancelled refresh drops it with the rest.
            launch {
                val leaders = try {
                    nextUp.nextUp()
                } catch (cancelled: CancellationException) {
                    // A cancelled call is not an answer, and this is the one exception that must
                    // not become an empty list: a refresh that replaced this run owns the screen
                    // now, and a cancelled child writing its own emptiness over the leaders the
                    // newer run published would blank the strip under the reader.
                    throw cancelled
                } catch (failed: Exception) {
                    emptyList()
                }
                _state.update { it.copy(nextUp = leaders) }
            }
            joinAll(summary, assets)
            republish()
        }
    }

    fun search(query: String) {
        _state.update {
            it.copy(
                query = query,
                analyzed = allAnalyzed.matching(query),
                withoutAnalysis = allWithoutAnalysis.matching(query),
            )
        }
    }

    fun clearSearch() = search("")

    // ---- Join ---------------------------------------------------------------------------

    /**
     * What a new run has to forget, which is only what it is about to ask again: whether each
     * source has settled, and which mints have been priced. Everything a source actually said is
     * kept, because a run that has not answered yet is not a reason to draw less than before.
     */
    private fun forgetWhatTheSourcesSaid() {
        summarySettled = false
        catalogSettled = false
        pricesQueued = false
        pricedMints = null
    }

    /**
     * One catalog emission. A whole catalog replaces what was there; the first pages of one land
     * on top of it, keyed by token symbol, so a refresh that is still paging never takes a row off
     * a list that already had it. Whole is sticky for the same reason: a screen that has been
     * shown a whole catalog is not walked back to a partial one by the next refresh.
     */
    private fun onCatalog(update: CatalogUpdate) {
        liveAssets = when {
            update.whole || liveAssets.isEmpty() -> update.assets
            else -> {
                val merged = LinkedHashMap<String, XStockAsset>(liveAssets.size + update.assets.size)
                liveAssets.forEach { merged[it.symbol] = it }
                update.assets.forEach { merged[it.symbol] = it }
                merged.values.toList()
            }
        }
        catalogWhole = catalogWhole || update.whole
        republish()
        schedulePrices()
    }

    /**
     * The catalog the screen joins against. While the live catalog is still arriving it does not
     * replace the snapshot wholesale: it lands on top of it, keyed by ticker, so a row the reader
     * is already looking at is refined in place rather than vanishing and coming back.
     */
    private fun assetsOnScreen(): List<XStockAsset> = when {
        catalogWhole -> liveAssets
        liveAssets.isEmpty() -> snapshotAssets
        !haveSnapshot -> liveAssets
        else -> {
            val merged = LinkedHashMap<String, XStockAsset>(snapshotAssets.size + liveAssets.size)
            snapshotAssets.forEach { merged[it.underlyingTicker.uppercase()] = it } // lint-allow uppercase: map key
            liveAssets.forEach { merged[it.underlyingTicker.uppercase()] = it } // lint-allow uppercase: map key
            merged.values.toList()
        }
    }

    /** Draws the screen from whatever the sources have so far. Never animates: this is a swap. */
    private fun republish() {
        // The first draw waits for the live analysis while it may still arrive in time; the
        // skeletons stand meanwhile, under a venue line that is already in its place.
        if (holdingSnapshot && haveSnapshot && liveRows == null && !summarySettled) return
        val rows = liveRows ?: snapshotRows
        val assets = assetsOnScreen()

        // Only a whole catalog may be used to decide that a ticker has no xStock. The bundled
        // snapshot is one, so the list has its final shape from the first frame and no row is
        // dropped and then re-added as the live pages land.
        val catalogKnown = catalogWhole || haveSnapshot

        // A failed /summary used to pass silently: the catalog drew 672 price-only rows and
        // nothing on screen said the analysis, which is the product, was missing rather than
        // absent for those tickers. It counts as unavailable only once it has actually failed
        // and the snapshot has no rows to stand in for it.
        val analysisKnown = liveRows != null || snapshotRows.isNotEmpty() || !summarySettled
        val catalogDown = catalogSettled && !catalogWhole && liveAssets.isEmpty() && !haveSnapshot
        val analysisDown = summarySettled && liveRows == null && snapshotRows.isEmpty()
        val failed = analysisDown && catalogDown

        // The snapshot is on screen while it is still supplying either half. The moment the
        // analysis is live and the catalog is whole, nothing drawn comes from it.
        val fromSnapshot = !failed && haveSnapshot && (liveRows == null || !catalogWhole)
        val refreshing = !(summarySettled && catalogSettled)
        val nothingOnScreen = !haveSnapshot && liveRows == null && liveAssets.isEmpty()

        // A catalog that settled with nothing, and no snapshot to name the venue: nothing is known
        // about it, so the calendar stops standing in and no hours line is raised at all.
        if (catalogSettled && assets.isEmpty()) marketClock.calendarUntilKnown = false

        if (failed) {
            allAnalyzed = emptyList()
            allWithoutAnalysis = emptyList()
            _state.update {
                it.copy(
                    isLoading = false,
                    failed = true,
                    fromSnapshot = false,
                    refreshing = refreshing,
                    snapshotCapturedOn = null,
                    analyzed = emptyList(),
                    withoutAnalysis = emptyList(),
                    catalogUnavailable = true,
                    analysisUnavailable = true,
                    pricesUnavailable = false,
                    pricesPartial = false,
                    allStaleDays = null,
                    generatedAt = null,
                    snapshotBannerDue = bannerGracePassed,
                    analysisFromSnapshot = false,
                    hoursPending = false,
                )
            }
            return
        }

        val byTicker = assets
            .filter { it.solanaMint != null }
            .associateBy { it.underlyingTicker.uppercase() } // lint-allow uppercase: map key

        // One row per ticker. A duplicated ticker is a server bug, but it used to be this
        // screen's crash: a LazyColumn keyed by ticker throws on the second one.
        val unique = rows.distinctBy { it.ticker.uppercase() } // lint-allow uppercase: map key

        // Production serves the composite as a percentile 0 to 100; the v1 fixture and the design
        // canvas carry the same rank as a 0 to 1 fraction. The scale is a property of the payload,
        // not of a row, so it is read once from the whole list: the bottom of a 179-row leaderboard
        // can legitimately be 0.56, and judging that row on its own would draw it as "56".
        val asFraction = percentilesAreFractions(unique.mapNotNull { it.composite })

        // A summary row whose underlying has no xStock is not an xStock, so it is not a row on
        // this screen: docs/data-map.md defines "Without analysis" as catalog xStocks missing
        // from /summary, which such a row can never be, and the device pass caught BKNG sitting
        // inside Analyzed with no token behind it. While the catalog is unavailable nothing is
        // known about any token, so the rows are kept rather than silently dropped.
        // A covered company added bare (no row of its own) is listed only once its token is known:
        // with the catalog down there is nothing to say about it beyond its name.
        val bare = if (liveRows != null) liveBare else snapshotBare
        val analyzed = unique
            .mapNotNull { row ->
                val asset = byTicker[row.ticker.uppercase()] // lint-allow uppercase: map key
                val isBare = row.ticker.uppercase() in bare // lint-allow uppercase: map key
                if (asset == null && (catalogKnown || isBare)) null else row.toListRow(asset, asFraction, isBare)
            }
            .sortedWith(compareBy<ListRow, Double?>(nullsLast(reverseOrder())) { it.composite }.thenBy { it.ticker })

        val classified = unique.map { it.ticker.uppercase() }.toSet() // lint-allow uppercase: map key
        val withoutAnalysis = byTicker.values
            .filter { it.underlyingTicker.uppercase() !in classified } // lint-allow uppercase: map key
            .map { it.toPriceOnlyRow() }
            .sortedBy { it.symbol }

        // Prices already on screen survive the swap: a row keeps the quote Jupiter gave it when a
        // later source refines its analysis or its symbol, so the numbers never blink out.
        val priced = (allAnalyzed + allWithoutAnalysis).mapNotNull { row -> row.mint?.let { it to row } }.toMap()
        val latest = prices.latest.value
        allAnalyzed = analyzed.map { it.carryingPriceFrom(priced).withLatest(latest) }
        allWithoutAnalysis = withoutAnalysis.map { it.carryingPriceFrom(priced).withLatest(latest) }

        _state.update {
            it.copy(
                isLoading = nothingOnScreen && refreshing,
                failed = false,
                fromSnapshot = fromSnapshot,
                refreshing = refreshing,
                snapshotCapturedOn = snapshotCapturedOn.takeIf { _ -> fromSnapshot },
                analyzed = allAnalyzed.matching(it.query),
                withoutAnalysis = allWithoutAnalysis.matching(it.query),
                catalogUnavailable = catalogDown,
                analysisUnavailable = !analysisKnown,
                allStaleDays = staleDays(allAnalyzed),
                // Read off the same catalog the rows were joined against, so the banner cannot
                // describe a venue the screen is not showing.
                market = marketClock.setAssets(assets),
                generatedAt = generatedAt,
                snapshotBannerDue = bannerGracePassed,
                analysisFromSnapshot = haveSnapshot && liveRows == null,
                hoursPending = hoursPending(),
            )
        }
    }

    /**
     * The youngest analysis when every analyzed row is stale, null otherwise: a stale row keeps
     * its place with its age in the meta line, and only a wholly stale list raises the banner.
     * A list that reports no age at all raises none, since the banner exists to name the age.
     */
    private fun staleDays(rows: List<ListRow>): Int? {
        if (rows.isEmpty() || !rows.all { it.stale }) return null
        return rows.mapNotNull { it.ageDays }.minOrNull()
    }

    // ---- Prices -------------------------------------------------------------------------

    /**
     * Asks for the prices of whatever is on screen now. Two rules, both of them about not
     * spending Jupiter's 0.5 requests per second twice on the same thing:
     *
     * - One run at a time. A source landing while a run is out does not restart it; it queues a
     *   single follow-up for when the current run finishes, so the newest set of mints is always
     *   the one that ends up priced and this screen never has two runs in flight.
     * - A set of mints that has not changed is not asked about again, so a refinement that
     *   leaves the tokens alone (a company name, an analysis) costs nothing.
     */
    private fun schedulePrices() {
        val mints = priceableMints()
        if (mints.isEmpty() || mints == pricedMints) return
        if (priceJob?.isActive == true) {
            pricesQueued = true
            return
        }
        priceJob = viewModelScope.launch {
            do {
                pricesQueued = false
                val wanted = priceableMints()
                if (wanted.isNotEmpty() && wanted != pricedMints) {
                    pricedMints = wanted
                    // The repository contract is that pricing never throws, but a broken contract
                    // must cost the prices, not the screen: the rows are already published.
                    try {
                        fetchPrices(wanted)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failed: Exception) {
                        _state.update { it.copy(pricesUnavailable = true, pricesPartial = false) }
                    }
                    pricedAtMillis = clock.nowMillis()
                    _state.update { it.copy(pricesSettled = true) }
                }
            } while (pricesQueued)
        }
    }

    private fun priceableMints(): List<String> =
        (allAnalyzed.mapNotNull { it.mint } + allWithoutAnalysis.mapNotNull { it.mint })
            .distinct()
            .take(PRICE_BUDGET)

    /** The visible window first, then the rest; the repository serves the window from cache. */
    private suspend fun fetchPrices(mints: List<String>) {
        val window = prices.pricesFirst(mints, FIRST_SCREENFUL)
        applyPrices(window, asked = mints.take(FIRST_SCREENFUL))
        windowsPriced.update { it + 1 }
        if (mints.size > FIRST_SCREENFUL) {
            applyPrices(window.mergedWith(prices.pricesFirst(mints)), asked = mints)
        }
    }

    /**
     * A refused chunk costs its own rows their price and nothing else: what Jupiter answered is
     * drawn, and the banner says the rest is missing. A mint Jupiter answered about but cannot
     * price is not a failure at all, so it raises no banner.
     *
     * Only the mints in [asked] are rewritten. A run's first call covers the visible window and
     * nothing else, so rewriting every row would blank the premiums further down that the run
     * before it had already drawn: on a cold start the snapshot's mints are priced first, and
     * the live set then starts a second run whose window would otherwise empty the rest of the
     * list for the seconds Jupiter's pacing costs. A row nobody asked about keeps what it had.
     */
    private fun applyPrices(fetch: PriceFetch, asked: Collection<String>) {
        val covered = asked.toHashSet()
        allAnalyzed = allAnalyzed.map { if (it.mint in covered) it.withPrice(fetch) else it }
        allWithoutAnalysis = allWithoutAnalysis.map { if (it.mint in covered) it.withPrice(fetch) else it }
        val nothingPriced = fetch.priced.isEmpty()
        _state.update {
            it.copy(
                analyzed = allAnalyzed.matching(it.query),
                withoutAnalysis = allWithoutAnalysis.matching(it.query),
                pricesUnavailable = fetch.isPartial && nothingPriced,
                pricesPartial = fetch.isPartial && !nothingPriced,
            )
        }
    }

    /**
     * A quote landed in the shared source, from this screen or any other: every row showing one of
     * those mints takes it. Rows whose quote did not change are left as they are, and nothing is
     * published when no row moved.
     */
    private fun applyLatest(latest: Map<String, PriceEntry>) {
        if (latest.isEmpty()) return
        val analyzed = allAnalyzed.map { it.withLatest(latest) }
        val without = allWithoutAnalysis.map { it.withLatest(latest) }
        if (analyzed == allAnalyzed && without == allWithoutAnalysis) return
        allAnalyzed = analyzed
        allWithoutAnalysis = without
        _state.update {
            it.copy(
                analyzed = allAnalyzed.matching(it.query),
                withoutAnalysis = allWithoutAnalysis.matching(it.query),
            )
        }
    }

    /** This row priced from the shared source when it holds the row's mint; unchanged otherwise. */
    private fun ListRow.withLatest(latest: Map<String, PriceEntry>): ListRow {
        val entry = mint?.let { latest[it] } ?: return this
        val next = copy(
            priceUsd = entry.usdPrice,
            referencePriceUsd = entry.stockData?.price,
            poolUsd = entry.liquidity,
            quote = RowQuote.ANSWERED,
        )
        return if (next == this) this else next
    }

    private fun PriceFetch.mergedWith(next: PriceFetch): PriceFetch {
        val merged = LinkedHashMap<String, PriceEntry>(priced.size + next.priced.size)
        merged += priced
        merged += next.priced
        return PriceFetch(
            priced = merged,
            unfetched = (unfetched + next.unfetched) - merged.keys,
            failure = next.failure ?: failure,
        )
    }

    private fun ListRow.withPrice(fetch: PriceFetch): ListRow {
        val entry = mint?.let { fetch.priced[it] }
        return copy(
            priceUsd = entry?.usdPrice,
            referencePriceUsd = entry?.stockData?.price,
            poolUsd = entry?.liquidity,
            quote = if (entry == null && mint in fetch.unfetched) RowQuote.UNREACHED else RowQuote.ANSWERED,
        )
    }

    /** The quote this row's mint already had, if any. A rebuilt row must not lose its price. */
    private fun ListRow.carryingPriceFrom(already: Map<String, ListRow>): ListRow {
        val previous = mint?.let { already[it] } ?: return this
        return copy(
            priceUsd = previous.priceUsd,
            referencePriceUsd = previous.referencePriceUsd,
            poolUsd = previous.poolUsd,
            quote = previous.quote,
        )
    }

    // ---- Search -------------------------------------------------------------------------

    private fun List<ListRow>.matching(query: String): List<ListRow> {
        val q = query.trim()
        if (q.isEmpty()) return this
        return filter { row ->
            row.ticker.contains(q, ignoreCase = true) ||
                row.symbol?.contains(q, ignoreCase = true) == true ||
                row.company?.contains(q, ignoreCase = true) == true
        }
    }

    // ---- Mapping ------------------------------------------------------------------------

    // `headline` is deliberately never read: it is Ukrainian, and docs/data-map.md says it is
    // not rendered in the app. Nothing on a row can carry it because no row field holds it.
    private fun SummaryRow.toListRow(asset: XStockAsset?, asFraction: Boolean, bare: Boolean = false) = ListRow(
        ticker = ticker,
        symbol = asset?.symbol,
        mint = asset?.solanaMint,
        company = company ?: asset?.name,
        composite = percentile(composite, asFraction),
        state = tone.toRowState(),
        stale = stale,
        ageDays = ageDays,
        priceUsd = null,
        referencePriceUsd = null,
        poolUsd = null,
        analyzed = true,
        sector = sector,
        // A bare row carries no composite because the company has no row on /summary, not because
        // the Pro lock withheld one: there is nothing to unlock (QA of 1.3.20).
        locked = !bare && isProLocked(),
        unclassified = bare && composite == null,
    )

    private fun XStockAsset.toPriceOnlyRow() = ListRow(
        ticker = underlyingTicker,
        symbol = symbol,
        mint = solanaMint,
        company = name,
        composite = null,
        state = null,
        stale = false,
        ageDays = null,
        priceUsd = null,
        referencePriceUsd = null,
        poolUsd = null,
        analyzed = false,
        sector = null,
        votable = isUsUnderlying,
    )

    companion object {
        /** Rows an 890dp frame shows at 64dp each; they get their prices before the rest. */
        const val FIRST_SCREENFUL = 12

        /**
         * The most mints one refresh will price. Four chunks of 50 paced at Jupiter's documented
         * 0.5 requests per second cost about eight seconds, inside the repository's 30 s cache
         * window; the whole catalog (830 tokens today) would cost half a minute of paced calls
         * for rows nobody has scrolled to.
         */
        const val PRICE_BUDGET = 200

        /**
         * How long a refresh may run before the snapshot line is allowed on screen.
         *
         * The line is true the moment the snapshot paints, but on a warm launch it is true for
         * less than a second: docs/data-map.md measured the Seeker settling at 3.5 s with the
         * first row at 2.78 s, and the review measured the line itself up at 3.53 s and gone at
         * 4.20 s. 0.70 s of banner is not a warning, it is the list jumping down by 40dp and back
         * while the reader is reading it. This is twice the 0.72 s that whole warm window takes,
         * so no refresh that behaves like a warm launch can reach it, and it costs the honest
         * case almost nothing: the first ever launch settles at 12.4 s, so the line still stands
         * for about eleven of them.
         *
         * 1.5 s until 1.3.20. Final QA of 1.3.19 caught a cold start whose `/summary` answered
         * just past it: the snapshot of 19 Sep was drawn (Communication Services 11, METAx 66),
         * then replaced by the live list (5, METAx 59) and a two-line banner, a jump of about
         * 50 px. The snapshot is for offline and slow networks; 4 s holds the skeletons through
         * a cold TLS start and still draws the snapshot early on a network that is really slow.
         */
        const val SNAPSHOT_BANNER_GRACE_MS = 4_000L

        /** The pull indicator's least and most stay (see [pull]). */
        const val PULL_MIN_MS = 600L
        const val PULL_MAX_MS = 15_000L

        /**
         * How old the list's prices may get while Stocks is on screen: the price cache's own
         * window ([com.plainticker.mobile.repo.CachedPriceRepository.TTL_MS]), so the quote a row
         * shows is the one Detail finds in the cache when that row is opened. [PRICE_BUDGET] mints
         * are four paced calls, about 0.13 requests per second at this cadence, a quarter of
         * Jupiter's keyless 0.5, and nothing runs while Stocks is not in the foreground.
         */
        const val REPRICE_MS = com.plainticker.mobile.repo.CachedPriceRepository.TTL_MS
    }
}

/**
 * The one ticker `/summary` never locks (`lib/billing/gate.ts`'s `OPEN_EXAMPLE_TICKER`, the same
 * name and the same value): the founder's permanent, fully open example, so a reader always has
 * one real composite to look at even with no code presented.
 */
internal const val OPEN_EXAMPLE_TICKER = "AAPL"

/**
 * True when [SummaryRow.composite] came back null because the Pro-numbers lock withheld it, not
 * because this ticker carries no analysis: `/summary` (`SummaryResponse`'s own doc comment) lists
 * only tickers PlainTicker has classified, so a served row's composite is otherwise never null.
 * The server nulls `composite`, `tone`, `headline` and `setup_score` together on every locked row
 * and leaves [OPEN_EXAMPLE_TICKER] (AAPL) and a Pro caller's own response untouched, so this same
 * combination cannot arise any other way; the app has no separate signal for "is this device Pro"
 * to check instead, and does not need one.
 */
internal fun SummaryRow.isProLocked(): Boolean =
    composite == null && (tone == null || headline == null) && !ticker.equals(OPEN_EXAMPLE_TICKER, ignoreCase = true)

/**
 * True when a whole payload's composites are the 0 to 1 fraction the v1 fixture and the design
 * canvas carry, rather than the 0 to 100 percentile production serves (the device pass saw
 * 83.80406, which the placeholder printed raw). The scale belongs to the payload, so it is read
 * from every value at once: one production row at 0.56 is the bottom of the leaderboard, not a
 * fraction, and reading it on its own would draw it as "56".
 */
internal fun percentilesAreFractions(composites: List<Double>): Boolean {
    val usable = composites.filter { it.isFinite() }
    return usable.isNotEmpty() && usable.all { it >= -1.0 && it <= 1.0 }
}

/** `/summary.composite` as the percentile a row draws. Either shape ends up as "84" on the row. */
internal fun percentile(composite: Double?, asFraction: Boolean): Double? {
    val value = composite ?: return null
    if (!value.isFinite()) return null
    return if (asFraction) value * 100.0 else value
}

/** docs/data-map.md: positive is strong, caution is fair, danger is weak, nothing is no word. */
internal fun Tone?.toRowState(): RowState? = when (this) {
    Tone.POSITIVE -> RowState.STRONG
    Tone.CAUTION -> RowState.FAIR
    Tone.DANGER -> RowState.WEAK
    null -> null
}
