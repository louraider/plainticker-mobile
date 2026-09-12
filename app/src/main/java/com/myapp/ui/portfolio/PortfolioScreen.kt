package com.myapp.ui.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.ui.Fmt

/**
 * Placeholder: connect, then the positions as text. Row anatomy lands with T11/DT8. It draws
 * the [header] the host hands in at the top of its own scroll, and keeps the 20dp side padding
 * of every other screen so no line touches the edge.
 */
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        header()
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            val account = state.account
            if (account == null) {
                Text("Connect your wallet to see the xStocks you own")
                Text("Connect wallet", modifier = Modifier.clickable { viewModel.connect() })
            } else {
                Text("Connected ${Fmt.shortKey(account.address)}" + (account.label?.let { "  ($it)" } ?: ""))
                Text("Disconnect", modifier = Modifier.clickable { viewModel.disconnect() })
                Text("Refresh", modifier = Modifier.clickable { viewModel.refresh() })
                state.lamports?.let { Text("SOL ${Fmt.tokenAmount(it, decimals = 9)}") }

                when {
                    state.isLoading -> Text("Loading positions")
                    state.error != null -> {
                        Text(state.error.orEmpty())
                        Text("Retry", modifier = Modifier.clickable { viewModel.refresh() })
                    }
                    state.isEmpty -> Text("No xStocks in this wallet yet")
                    else -> {
                        Text("Total ${state.totalUsd?.let(Fmt::price) ?: "-"}")
                        if (state.pricesUnavailable) Text("Prices unavailable")
                        state.positions.forEach { position ->
                            Text(
                                "${position.symbol}  ${Fmt.tokenAmount(position.quantity)}  value ${position.valueUsd?.let(Fmt::price) ?: "-"}" +
                                    "  (raw ${Fmt.count(position.amountRaw)} x ${Fmt.decimal(position.multiplier)})",
                                modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(position.ticker) },
                            )
                        }
                        Text("Cost basis is not read from the chain.")
                    }
                }
            }
            state.message?.let { Text(it) }
        }
    }
}
