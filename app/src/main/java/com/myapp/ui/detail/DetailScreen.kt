package com.myapp.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.ui.swap.SwapPlaceholder
import com.myapp.ui.swap.SwapViewModel
import com.myapp.ui.watchlist.WatchlistViewModel

/** Placeholder: the state as text. The trust layer and sections land with T9/DT6. */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    swapViewModel: SwapViewModel,
    watchlistViewModel: WatchlistViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val swap by swapViewModel.state.collectAsStateWithLifecycle()
    val watchlist by watchlistViewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Back", modifier = Modifier.clickable(onClick = onBack))
        Text((state.symbol ?: state.ticker) + (state.asset?.name?.let { "  $it" } ?: ""))
        Text(
            if (state.ticker in watchlist.tickers) "Unwatch" else "Watch",
            modifier = Modifier.clickable { watchlistViewModel.toggle(state.ticker) },
        )

        if (state.isLoading) Text("Loading")
        if (state.catalogUnavailable) Text("xStocks catalog unavailable")
        state.mint?.let { Text("mint $it") }
        state.multiplier?.let { Text("multiplier $it") }
        state.price?.let { Text("price ${it.usdPrice}  reference ${it.stockData?.price ?: "unavailable"}") }
        if (state.pricesUnavailable) Text("Prices unavailable")
        state.asset?.trading?.let { Text("venue ${it.currentPeriod ?: "unknown"}  open now ${it.openNow}") }

        val analysis = state.analysis
        if (analysis != null) {
            Text("as of ${analysis.asOf ?: "unknown"}  schema ${analysis.schemaVersion ?: "?"}")
            Text("quality ${analysis.axes.quality?.labelEn ?: "-"}")
            Text("valuation ${analysis.axes.valuation?.labelEn ?: "-"}")
            Text("momentum ${analysis.axes.momentum?.labelEn ?: "-"}")
            Text("F-Score ${analysis.fscore?.score ?: "-"} of 9")
            Text("composite percentile ${analysis.compositePercentile ?: "-"}")
            analysis.method?.statementEn?.let { Text(it) }
        } else {
            state.analysisUnavailable?.let { Text(it) }
        }

        SwapPlaceholder(
            state = swap,
            enabled = state.mint != null && !state.isLoading,
            onSwap = { state.mint?.let { mint -> swapViewModel.start(mint, state.symbol ?: state.ticker, DEMO_USDC_RAW) } },
            onReset = swapViewModel::reset,
        )
    }
}

/** 5 USDC (6 decimals), the spike's amount, until the sheet has an amount field (T10). */
private const val DEMO_USDC_RAW = 5_000_000L
