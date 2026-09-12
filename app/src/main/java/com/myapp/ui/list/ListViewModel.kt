package com.myapp.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.PriceFetch
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.plainticker.SummaryRow
import com.myapp.data.plainticker.Tone
import com.myapp.data.snapshot.toSummaryRow
import com.myapp.data.snapshot.toXStockAsset
import com.myapp.data.xstocks.XStockAsset
import com.myapp.prefs.WatchlistStore
import com.myapp.repo.CatalogRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.SnapshotRepository
import com.myapp.repo.SummaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The one word right of the composite. Never a verdict: it describes the classification. */
enum class RowState { STRONG, FAIR, WEAK }

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
 * device. The List has no market-hours banner yet (T9 owns the trading calendar), so what is
 * left is the offline tier (the list could not be loaded at all, or came from the bundled
 * snapshot), the stale tier (nothing on screen is a fresh analysis) and the device tier (one
 * source the rows depend on is missing while the rest of the screen stands).
 */
sealed interface ListBanner {
    /** Offline tier: nothing to draw, the user gets one Retry. */
    data object Unavailable : ListBanner

    /** Offline tier: both routes failed and the rows came from the bundled snapshot. */
    data class Snapshot(val capturedOn: LocalDate?) : ListBanner

    /** Stale tier: every analysis on screen is old; [newestDays] is the youngest of them. */
    data class Stale(val newestDays: Int) : ListBanner

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
    /** How many tickers are watched, for the Today strip; the strip is hidden at zero. */
    val watched: Int = 0,
    val generatedAt: String? = null,
    /** The day the bundled snapshot was captured, when the rows came from it. */
    val snapshotCapturedOn: LocalDate? = null,
    /** Both sources failed and there was no snapshot to fall back to. */
    val failed: Boolean = false,
    val fromSnapshot: Boolean = false,
    val catalogUnavailable: Boolean = false,
    /** `/summary` did not answer while the catalog did, so no row on screen has an analysis. */
    val analysisUnavailable: Boolean = false,
    val pricesUnavailable: Boolean = false,
    val pricesPartial: Boolean = false,
    /** The youngest analysis on screen, set only when every analyzed row is stale. */
    val allStaleDays: Int? = null,
) {
    val isEmpty: Boolean get() = !isLoading && !failed && analyzed.isEmpty() && withoutAnalysis.isEmpty()

    /** An empty result the reader asked for: one sentence on screen, not an error. */
    val searchMiss: Boolean get() = isEmpty && query.isNotBlank()

    /** Both sources answered and neither had anything. One sentence, so the screen is never blank. */
    val emptyResult: Boolean get() = isEmpty && query.isBlank()

    val banner: ListBanner?
        get() = when {
            failed -> ListBanner.Unavailable
            fromSnapshot -> ListBanner.Snapshot(snapshotCapturedOn)
            allStaleDays != null -> ListBanner.Stale(allStaleDays)
            catalogUnavailable -> ListBanner.CatalogUnavailable
            analysisUnavailable -> ListBanner.AnalysisUnavailable
            pricesUnavailable -> ListBanner.PricesUnavailable
            pricesPartial -> ListBanner.PricesPartial
            else -> null
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
    private val watchlist: WatchlistStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ListUiState(isLoading = true, watched = watchlist.tickers.value.size))
    val state: StateFlow<ListUiState> = _state.asStateFlow()

    private var allAnalyzed: List<ListRow> = emptyList()
    private var allWithoutAnalysis: List<ListRow> = emptyList()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            watchlist.tickers.collect { watched -> _state.update { it.copy(watched = watched.size) } }
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, failed = false) }

            val summary = runCatching { summaries.summary() }
            val assets = runCatching { catalog.catalog() }

            if (summary.isFailure && assets.isFailure) {
                val snapshot = runCatching { snapshots.listSnapshot() }.getOrNull()
                if (snapshot == null || snapshot.rows.isEmpty()) {
                    publishNothing()
                    return@launch
                }
                publishRows(
                    rows = snapshot.rows.map { it.toSummaryRow() },
                    assets = snapshot.assets.map { it.toXStockAsset() },
                    catalogKnown = snapshot.assets.isNotEmpty(),
                    analysisKnown = snapshot.rows.isNotEmpty(),
                    generatedAt = null,
                    snapshotCapturedOn = snapshot.capturedOn,
                    fromSnapshot = true,
                )
            } else {
                publishRows(
                    rows = summary.getOrNull()?.rows.orEmpty(),
                    assets = assets.getOrNull().orEmpty(),
                    catalogKnown = assets.isSuccess,
                    // A failed /summary used to pass silently: the catalog drew 672 price-only
                    // rows and nothing on screen said the analysis, which is the product, was
                    // missing rather than absent for those tickers.
                    analysisKnown = summary.isSuccess,
                    generatedAt = summary.getOrNull()?.generatedAt,
                    snapshotCapturedOn = null,
                    fromSnapshot = false,
                )
            }

            // The repository contract is that pricing never throws, but a broken contract must
            // cost the prices, not the screen: this runs after the rows are already published.
            try {
                fetchPrices()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                _state.update { it.copy(pricesUnavailable = true, pricesPartial = false) }
            }
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

    private fun publishRows(
        rows: List<SummaryRow>,
        assets: List<XStockAsset>,
        catalogKnown: Boolean,
        analysisKnown: Boolean,
        generatedAt: String?,
        snapshotCapturedOn: LocalDate?,
        fromSnapshot: Boolean,
    ) {
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
        val analyzed = unique
            .mapNotNull { row ->
                val asset = byTicker[row.ticker.uppercase()] // lint-allow uppercase: map key
                if (asset == null && catalogKnown) null else row.toListRow(asset, asFraction)
            }
            .sortedWith(compareBy<ListRow, Double?>(nullsLast(reverseOrder())) { it.composite }.thenBy { it.ticker })

        val classified = unique.map { it.ticker.uppercase() }.toSet() // lint-allow uppercase: map key
        val withoutAnalysis = byTicker.values
            .filter { it.underlyingTicker.uppercase() !in classified } // lint-allow uppercase: map key
            .map { it.toPriceOnlyRow() }
            .sortedBy { it.symbol }

        allAnalyzed = analyzed
        allWithoutAnalysis = withoutAnalysis

        _state.update {
            it.copy(
                isLoading = false,
                failed = false,
                fromSnapshot = fromSnapshot,
                snapshotCapturedOn = snapshotCapturedOn,
                analyzed = analyzed.matching(it.query),
                withoutAnalysis = withoutAnalysis.matching(it.query),
                catalogUnavailable = !catalogKnown,
                analysisUnavailable = !analysisKnown,
                pricesUnavailable = false,
                pricesPartial = false,
                allStaleDays = staleDays(analyzed),
                generatedAt = generatedAt,
            )
        }
    }

    private fun publishNothing() {
        allAnalyzed = emptyList()
        allWithoutAnalysis = emptyList()
        _state.update {
            it.copy(
                isLoading = false,
                failed = true,
                fromSnapshot = false,
                snapshotCapturedOn = null,
                analyzed = emptyList(),
                withoutAnalysis = emptyList(),
                catalogUnavailable = true,
                analysisUnavailable = true,
                pricesUnavailable = false,
                pricesPartial = false,
                allStaleDays = null,
                generatedAt = null,
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

    /** The visible window first, then the rest; the repository serves the window from cache. */
    private suspend fun fetchPrices() {
        val mints = (allAnalyzed.mapNotNull { it.mint } + allWithoutAnalysis.mapNotNull { it.mint })
            .distinct()
            .take(PRICE_BUDGET)
        if (mints.isEmpty()) return

        val window = prices.pricesFirst(mints, FIRST_SCREENFUL)
        applyPrices(window)
        if (mints.size > FIRST_SCREENFUL) {
            applyPrices(window.mergedWith(prices.pricesFirst(mints)))
        }
    }

    /**
     * A refused chunk costs its own rows their price and nothing else: what Jupiter answered is
     * drawn, and the banner says the rest is missing. A mint Jupiter answered about but cannot
     * price is not a failure at all, so it raises no banner.
     */
    private fun applyPrices(fetch: PriceFetch) {
        allAnalyzed = allAnalyzed.map { it.withPrice(fetch.priced[it.mint]) }
        allWithoutAnalysis = allWithoutAnalysis.map { it.withPrice(fetch.priced[it.mint]) }
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

    private fun ListRow.withPrice(entry: PriceEntry?): ListRow =
        copy(
            priceUsd = entry?.usdPrice,
            referencePriceUsd = entry?.stockData?.price,
            poolUsd = entry?.liquidity,
        )

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
    private fun SummaryRow.toListRow(asset: XStockAsset?, asFraction: Boolean) = ListRow(
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
    }
}

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
