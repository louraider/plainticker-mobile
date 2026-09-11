package com.myapp.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Placeholder: unstyled text per row. The row anatomy lands with T7/T8. */
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text("Search")
        BasicTextField(
            value = state.query,
            onValueChange = viewModel::search,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        state.generatedAt?.let { Text("Analysis generated $it") }
        if (state.pricesUnavailable) Text("Prices unavailable")
        if (state.catalogUnavailable) Text("xStocks catalog unavailable")

        when {
            state.isLoading -> Text("Loading analysis list")
            state.error != null -> {
                Text(state.error.orEmpty())
                Text("Retry", modifier = Modifier.clickable { viewModel.refresh() })
            }
            state.isEmpty -> Text("No xStock matches '${state.query}'")
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { Text("Analyzed (${state.analyzed.size})") }
                items(state.analyzed, key = { "a:" + it.ticker }) { row -> ListRowText(row, onOpenDetail) }
                item { Text("Without analysis (${state.withoutAnalysis.size})") }
                items(state.withoutAnalysis, key = { "p:" + it.ticker }) { row -> ListRowText(row, onOpenDetail) }
            }
        }
    }
}

@Composable
private fun ListRowText(row: ListRow, onOpenDetail: (String) -> Unit) {
    val text = buildString {
        append(row.symbol ?: row.ticker)
        row.company?.let { append("  ").append(it) }
        row.composite?.let { append("  composite ").append(it) }
        row.priceUsd?.let { append("  price ").append(it) }
        if (row.stale) append("  analysis ").append(row.ageDays ?: "?").append(" d old")
        row.headline?.let { append("  ").append(it) }
    }
    Text(text, modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(row.ticker) })
}
