package com.myapp.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.net.ApiException
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.xstocks.XStockAsset
import com.myapp.repo.CatalogRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.SummaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val ticker: String,
    val isLoading: Boolean = true,
    val analysis: AnalysisPayload? = null,
    /** Why there is no analysis: an unserved ticker (404), incomplete data (503), or a transport failure. */
    val analysisUnavailable: String? = null,
    val asset: XStockAsset? = null,
    val catalogUnavailable: Boolean = false,
    val price: PriceEntry? = null,
    val pricesUnavailable: Boolean = false,
    /** xStocks scaledUiAmount multiplier for the token; null until read. */
    val multiplier: Double? = null,
) {
    val symbol: String? get() = asset?.symbol
    val mint: String? get() = asset?.solanaMint
}

class DetailViewModel(
    ticker: String,
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
) : ViewModel() {

    private val ticker = ticker.trim().uppercase()

    private val _state = MutableStateFlow(DetailUiState(ticker = this.ticker))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val asset = runCatching {
                catalog.catalog().firstOrNull { it.underlyingTicker.equals(ticker, ignoreCase = true) }
            }
            val analysis = runCatching { summaries.analysis(ticker) }
            val mint = asset.getOrNull()?.solanaMint
            val price = runCatching { mint?.let { prices.prices(listOf(it))[it] } }
            val multiplier = asset.getOrNull()?.symbol?.let { symbol -> runCatching { catalog.multiplier(symbol) }.getOrNull() }

            _state.update {
                it.copy(
                    isLoading = false,
                    analysis = analysis.getOrNull(),
                    analysisUnavailable = analysis.exceptionOrNull()?.let(::describe),
                    asset = asset.getOrNull(),
                    catalogUnavailable = asset.isFailure,
                    price = price.getOrNull(),
                    pricesUnavailable = price.isFailure,
                    multiplier = multiplier,
                )
            }
        }
    }

    private fun describe(e: Throwable): String = when {
        e is ApiException && e.status == 404 -> "Analysis not yet available"
        e is ApiException && e.status == 503 -> "Analysis incomplete for this filer"
        else -> "Analysis unavailable"
    }
}
