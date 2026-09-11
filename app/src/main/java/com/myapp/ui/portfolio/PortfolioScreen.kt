package com.myapp.ui.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Placeholder: connect, then the positions as text. Row anatomy lands with T11/DT8. */
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val account = state.account
        if (account == null) {
            Text("Connect your wallet to see the xStocks you own")
            Text("Connect wallet", modifier = Modifier.clickable { viewModel.connect() })
        } else {
            Text("Connected ${account.address.take(4)}...${account.address.takeLast(4)}" + (account.label?.let { "  ($it)" } ?: ""))
            Text("Disconnect", modifier = Modifier.clickable { viewModel.disconnect() })
            Text("Refresh", modifier = Modifier.clickable { viewModel.refresh() })
            state.lamports?.let { Text("SOL ${it / 1_000_000_000.0}") }

            when {
                state.isLoading -> Text("Loading positions")
                state.error != null -> {
                    Text(state.error.orEmpty())
                    Text("Retry", modifier = Modifier.clickable { viewModel.refresh() })
                }
                state.isEmpty -> Text("No xStocks in this wallet yet")
                else -> {
                    Text("Total ${state.totalUsd ?: "-"}")
                    if (state.pricesUnavailable) Text("Prices unavailable")
                    state.positions.forEach { position ->
                        Text(
                            "${position.symbol}  ${position.quantity}  value ${position.valueUsd ?: "-"}" +
                                "  (raw ${position.amountRaw} x ${position.multiplier})",
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
