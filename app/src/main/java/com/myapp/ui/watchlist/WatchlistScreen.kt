package com.myapp.ui.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/**
 * Placeholder: watched tickers as text. Events, digest and notifications land with T12. It
 * draws the [header] the host hands in at the top of its own scroll, and keeps the 20dp side
 * padding of every other screen so no line touches the edge.
 */
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel,
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
            if (state.isEmpty) {
                Text("Nothing watched yet. Tap Watch on any stock.")
            } else {
                Text("Watched (${state.tickers.size})")
                state.tickers.forEach { ticker ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(ticker, modifier = Modifier.clickable { onOpenDetail(ticker) })
                        Text("  Unwatch", modifier = Modifier.clickable { viewModel.remove(ticker) })
                    }
                }
            }
        }
    }
}
