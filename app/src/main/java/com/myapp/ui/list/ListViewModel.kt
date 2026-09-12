package com.myapp.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.PriceFetch
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
    val analyzed: Boolean,
) {
    /** What the row shows left: the token symbol once the catalog is known, else the ticker. */
    val display: String get() = symbol ?: ticker

    /**
     * The token's premium against the NYSE close, in percent. Null when either side is missing,
     * so a row whose price never arrived loses the premium and keeps everything else.
     */
    val premiumPct: Double?
        get() {
            val price = priceUsd ?: return null
            val reference = referencePriceUsd ?: return null
            if (!price.isFinite() || !reference.isFinite() || reference <= 0.0) return null
            return (price / reference - 1.0) * 100.0
        }
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
    val pricesUnavailable: Boolean = false,
    val pricesPartial: Boolean = false,
    /** The youngest analysis on screen, set only when every analyzed row is stale. */
    val allStaleDays: Int? = null,
) {
    val isEmpty: Boolean get() = !isLoading && !failed && analyzed.isEmpty() && withoutAnalysis.isEmpty()

    /** An empty result the reader asked for: one sentence on screen, not an error. */
    val searchMiss: Boolean get() = isEmpty && query.isNotBlank()

    val banner: ListBanner?
        get() = when {
            failed -> ListBanner.Unavailable
            fromSnapshot -> ListBanner.Snapshot(snapshotCapturedOn)
            allStaleDays != null -> ListBanner.Stale(allStaleDays)
            catalogUnavailable -> ListBanner.CatalogUnavailable
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
                    generatedAt = null,
                    snapshotCapturedOn = snapshot.capturedOn,
                    fromSnapshot = true,
                )
            } else {
                publishRows(
                    rows = summary.getOrNull()?.rows.orEmpty(),
                    assets = assets.getOrNull().orEmpty(),
                    catalogKnown = assets.isSuccess,
                    generatedAt = summary.getOrNull()?.generatedAt,
                    snapshotCapturedOn = null,
                    fromSnapshot = false,
                )
            }

            fetchPrices()
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
        generatedAt: String?,
        snapshotCapturedOn: LocalDate?,
        fromSnapshot: Boolean,
    ) {
        val byTicker = assets
            .filter { it.solanaMint != null }
            .associateBy { it.underlyingTicker.uppercase() } // lint-allow uppercase: map key

        // A summary row whose underlying has no xStock is not an xStock, so it is not a row on
        // this screen: docs/data-map.md defines "Without analysis" as catalog xStocks missing
        // from /summary, which such a row can never be, and the device pass caught BKNG sitting
        // inside Analyzed with no token behind it. While the catalog is unavailable nothing is
        // known about any token, so the rows are kept rather than silently dropped.
        val analyzed = rows
            .mapNotNull { row ->
                val asset = byTicker[row.ticker.uppercase()] // lint-allow uppercase: map key
                if (asset == null && catalogKnown) null else row.toListRow(asset)
            }
            .sortedWith(compareBy<ListRow, Double?>(nullsLast(reverseOrder())) { it.composite }.thenBy { it.ticker })

        val classified = rows.map { it.ticker.uppercase() }.toSet() // lint-allow uppercase: map key
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
        copy(priceUsd = entry?.usdPrice, referencePriceUsd = entry?.stockData?.price)

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
    private fun SummaryRow.toListRow(asset: XStockAsset?) = ListRow(
        ticker = ticker,
        symbol = asset?.symbol,
        mint = asset?.solanaMint,
        company = company ?: asset?.name,
        composite = percentile(composite),
        state = tone.toRowState(),
        stale = stale,
        ageDays = ageDays,
        priceUsd = null,
        referencePriceUsd = null,
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
 * `/summary.composite` as the percentile a row draws. Production serves 0 to 100 (the device
 * pass saw 83.80406, which the placeholder printed raw); the v1 fixture and the design canvas
 * carry the same rank as a 0 to 1 fraction, so a value inside that range is read as the
 * fraction it is. Either shape ends up as "84" on the row.
 */
internal fun percentile(composite: Double?): Double? {
    val value = composite ?: return null
    if (!value.isFinite()) return null
    return if (value >= -1.0 && value <= 1.0) value * 100.0 else value
}

/** docs/data-map.md: positive is strong, caution is fair, danger is weak, nothing is no word. */
internal fun Tone?.toRowState(): RowState? = when (this) {
    Tone.POSITIVE -> RowState.STRONG
    Tone.CAUTION -> RowState.FAIR
    Tone.DANGER -> RowState.WEAK
    null -> null
}
