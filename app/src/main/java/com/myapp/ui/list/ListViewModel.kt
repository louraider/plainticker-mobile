package com.myapp.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.data.plainticker.SummaryRow
import com.myapp.data.plainticker.Tone
import com.myapp.data.xstocks.XStockAsset
import com.myapp.repo.CatalogRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.SummaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of the list: an analyzed xStock, or a catalog asset PlainTicker has not classified. */
data class ListRow(
    /** Underlying equity ticker, e.g. "AAPL". The join key to PlainTicker. */
    val ticker: String,
    /** xStock token symbol, e.g. "AAPLx"; null when the catalog has no Solana token for [ticker]. */
    val symbol: String?,
    val mint: String?,
    val company: String?,
    val headline: String?,
    val composite: Double?,
    val tone: Tone?,
    val stale: Boolean,
    val ageDays: Int?,
    val priceUsd: Double?,
    /** The underlying share's reference price from Price v3 `stockData`, when Jupiter has one. */
    val referencePriceUsd: Double?,
) {
    val hasAnalysis: Boolean get() = composite != null || headline != null
}

data class ListUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    /** Rows with a PlainTicker classification, composite descending. */
    val analyzed: List<ListRow> = emptyList(),
    /** Catalog assets PlainTicker has not classified, symbol ascending. */
    val withoutAnalysis: List<ListRow> = emptyList(),
    /** Set only when neither source answered: there is nothing to draw. */
    val error: String? = null,
    val catalogUnavailable: Boolean = false,
    val pricesUnavailable: Boolean = false,
    val generatedAt: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && error == null && analyzed.isEmpty() && withoutAnalysis.isEmpty()
}

/**
 * summary joined with the catalog joined with prices (plan T8). The skeleton prices only
 * the analyzed rows: one Price v3 call for up to 50 mints keeps the list inside Jupiter's
 * keyless bucket; pricing the whole catalog is T8's problem together with the bundled
 * snapshot fallback.
 */
class ListViewModel(
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ListUiState(isLoading = true))
    val state: StateFlow<ListUiState> = _state.asStateFlow()

    private var allAnalyzed: List<ListRow> = emptyList()
    private var allWithoutAnalysis: List<ListRow> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val summary = runCatching { summaries.summary() }
            val assets = runCatching { catalog.catalog() }
            if (summary.isFailure && assets.isFailure) {
                _state.update {
                    it.copy(isLoading = false, error = "Analysis list unavailable", catalogUnavailable = true)
                }
                return@launch
            }

            val rows = summary.getOrNull()?.rows.orEmpty()
            val byTicker = assets.getOrNull().orEmpty()
                .filter { it.solanaMint != null }
                .associateBy { it.underlyingTicker.uppercase() }

            val analyzed = rows.map { row -> row.toListRow(byTicker[row.ticker.uppercase()]) }
                .sortedWith(compareBy<ListRow, Double?>(nullsLast(reverseOrder())) { it.composite }.thenBy { it.ticker })
            val analyzedTickers = analyzed.map { it.ticker.uppercase() }.toSet()
            val withoutAnalysis = byTicker.values
                .filter { it.underlyingTicker.uppercase() !in analyzedTickers }
                .map { it.toPriceOnlyRow() }
                .sortedBy { it.symbol }

            val mints = analyzed.mapNotNull { it.mint }
            val priced = runCatching { if (mints.isEmpty()) emptyMap() else prices.prices(mints) }
            val priceMap = priced.getOrNull().orEmpty()

            allAnalyzed = analyzed.map { row ->
                val entry = row.mint?.let(priceMap::get)
                row.copy(priceUsd = entry?.usdPrice, referencePriceUsd = entry?.stockData?.price)
            }
            allWithoutAnalysis = withoutAnalysis

            _state.update {
                it.copy(
                    isLoading = false,
                    error = null,
                    analyzed = allAnalyzed.matching(it.query),
                    withoutAnalysis = allWithoutAnalysis.matching(it.query),
                    catalogUnavailable = assets.isFailure,
                    pricesUnavailable = priced.isFailure,
                    generatedAt = summary.getOrNull()?.generatedAt,
                )
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

    private fun List<ListRow>.matching(query: String): List<ListRow> {
        val q = query.trim()
        if (q.isEmpty()) return this
        return filter { row ->
            row.ticker.contains(q, ignoreCase = true) ||
                row.symbol?.contains(q, ignoreCase = true) == true ||
                row.company?.contains(q, ignoreCase = true) == true
        }
    }

    private fun SummaryRow.toListRow(asset: XStockAsset?) = ListRow(
        ticker = ticker,
        symbol = asset?.symbol,
        mint = asset?.solanaMint,
        company = company ?: asset?.name,
        headline = headline,
        composite = composite,
        tone = tone,
        stale = stale,
        ageDays = ageDays,
        priceUsd = null,
        referencePriceUsd = null,
    )

    private fun XStockAsset.toPriceOnlyRow() = ListRow(
        ticker = underlyingTicker,
        symbol = symbol,
        mint = solanaMint,
        company = name,
        headline = null,
        composite = null,
        tone = null,
        stale = false,
        ageDays = null,
        priceUsd = null,
        referencePriceUsd = null,
    )
}
