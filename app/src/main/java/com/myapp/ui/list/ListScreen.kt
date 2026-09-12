package com.myapp.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.R
import com.myapp.ui.Fmt
import com.myapp.ui.components.Banner
import com.myapp.ui.components.Field
import com.myapp.ui.components.Heading
import com.myapp.ui.components.InstrumentPreviews
// The row component and this package's row model share a name; the anatomy keeps an alias.
import com.myapp.ui.components.ListRow as InstrumentRow
import com.myapp.ui.components.PreviewCanvas
import com.myapp.ui.components.SkeletonRows
import com.myapp.ui.components.TodayStrip
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.PlainTickerType
import java.time.LocalDate

/**
 * The List (T8, DT8; design/canvas/instrument.py screen_list). One scrolling column: the header
 * the host hands in (the TopBar and the tabs, so they scroll away and nothing is sticky), the
 * Today strip while something is watched, the one banner slot, the search field, "Analyzed" with
 * the classified xStocks and "Without analysis" with the muted price-only rows.
 *
 * Every number goes through [Fmt]: the composite as an integer, the premium as a signed percent,
 * the age as "2 d old". The meta line carries at most one middle dot, and the Ukrainian headline
 * of `/summary` is not a field a row even has (see [ListViewModel]).
 */
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListContent(
        state = state,
        onQueryChange = viewModel::search,
        onClearSearch = viewModel::clearSearch,
        onRetry = viewModel::refresh,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
        header = header,
    )
}

@Composable
internal fun ListContent(
    state: ListUiState,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val cold = state.isLoading && state.analyzed.isEmpty() && state.withoutAnalysis.isEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "chrome") {
            Column(Modifier.fillMaxWidth()) {
                if (state.watched > 0) {
                    TodayStrip(text = stringResource(R.string.list_today_watched, Fmt.count(state.watched)))
                }
                state.banner?.let { StateBanner(banner = it, onRetry = onRetry) }
                Spacer(Modifier.height(SearchTopGap))
                SearchField(query = state.query, onQueryChange = onQueryChange, onClearSearch = onClearSearch)
            }
        }

        when {
            cold -> item(key = "skeleton") {
                Column(Modifier.fillMaxWidth()) {
                    Heading(text = stringResource(R.string.list_heading_analyzed), topPadding = HeadingTopGap)
                    SkeletonRows(count = SkeletonRowCount)
                }
            }

            state.searchMiss -> item(key = "miss") { EmptyLine(stringResource(R.string.list_search_empty, state.query)) }

            // Both sources answered and neither had a row. Rare, but the screen was otherwise
            // a wordmark, a search field and nothing else, with no banner to explain it.
            state.emptyResult -> item(key = "empty") { EmptyLine(stringResource(R.string.list_empty)) }

            else -> {
                if (state.analyzed.isNotEmpty()) {
                    item(key = "analyzed") {
                        Heading(text = stringResource(R.string.list_heading_analyzed), topPadding = HeadingTopGap)
                    }
                    itemsIndexed(state.analyzed, key = { _, row -> "a:" + row.ticker }) { index, row ->
                        AnalyzedRow(
                            row = row,
                            last = index == state.analyzed.lastIndex,
                            onOpenDetail = onOpenDetail,
                        )
                    }
                }
                if (state.withoutAnalysis.isNotEmpty()) {
                    item(key = "without") {
                        Heading(
                            text = stringResource(R.string.list_heading_without_analysis),
                            topPadding = SectionTopGap,
                        )
                    }
                    itemsIndexed(state.withoutAnalysis, key = { _, row -> "p:" + row.ticker }) { index, row ->
                        PriceOnlyRow(
                            row = row,
                            last = index == state.withoutAnalysis.lastIndex,
                            onOpenDetail = onOpenDetail,
                        )
                    }
                }
            }
        }
    }
}

/** One sentence where the rows would be, so no state of this screen is a blank column. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = PlainTickerType.body,
        color = Ink2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = HeadingTopGap),
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClearSearch: () -> Unit) {
    Field(
        label = stringResource(R.string.list_search_label),
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.list_search_placeholder),
        mono = false,
        action = if (query.isEmpty()) null else stringResource(R.string.action_clear),
        onAction = onClearSearch,
    )
}

/** Ticker and company left, the composite as an integer with its state word right. */
@Composable
private fun AnalyzedRow(row: ListRow, last: Boolean, onOpenDetail: (String) -> Unit) {
    InstrumentRow(
        ticker = row.display,
        company = row.company,
        meta = rowMeta(row),
        valueRight = row.composite?.let { Fmt.decimal(it, decimals = 0) },
        valueSub = row.state?.let { stringResource(it.label) },
        divider = !last,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
    )
}

/** An xStock PlainTicker has not classified: everything Muted, the price as the value. */
@Composable
private fun PriceOnlyRow(row: ListRow, last: Boolean, onOpenDetail: (String) -> Unit) {
    InstrumentRow(
        ticker = row.display,
        company = row.company,
        meta = rowMeta(row),
        valueRight = row.priceUsd?.let { Fmt.price(it) },
        muted = true,
        divider = !last,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
    )
}

/**
 * The premium against the NYSE close and the age of the analysis, one middle dot between them.
 * Either half can be missing: an unpriced row keeps its age, an unanalyzed one keeps its premium,
 * an analysis from today prints no age at all, and a row with neither has no meta line.
 */
@Composable
private fun rowMeta(row: ListRow): String? {
    val premium = row.premiumPct?.let { Fmt.percent(it) }
    val age = row.ageForMeta?.let { Fmt.daysOld(it) }
    return when {
        premium != null && age != null -> stringResource(R.string.list_row_meta, premium, age)
        premium != null -> stringResource(R.string.list_row_meta_price_only, premium)
        age != null -> stringResource(R.string.list_row_meta_age, age)
        else -> null
    }
}

/** The one slot under the tabs; [ListBanner] has already picked which state wins. */
@Composable
private fun StateBanner(banner: ListBanner, onRetry: () -> Unit) {
    val retry = stringResource(R.string.action_retry)
    when (banner) {
        ListBanner.Unavailable ->
            Banner(text = stringResource(R.string.list_error_unavailable), action = retry, onAction = onRetry)

        is ListBanner.Snapshot -> Banner(
            text = banner.capturedOn
                ?.let { stringResource(R.string.list_snapshot_banner, Fmt.day(it)) }
                ?: stringResource(R.string.list_snapshot_banner_undated),
            action = retry,
            onAction = onRetry,
        )

        is ListBanner.Stale ->
            Banner(text = stringResource(R.string.list_stale_banner, Fmt.daysOld(banner.newestDays)))

        ListBanner.CatalogUnavailable ->
            Banner(text = stringResource(R.string.list_catalog_unavailable), action = retry, onAction = onRetry)

        ListBanner.AnalysisUnavailable ->
            Banner(text = stringResource(R.string.list_analysis_unavailable), action = retry, onAction = onRetry)

        ListBanner.PricesUnavailable ->
            Banner(text = stringResource(R.string.list_prices_unavailable), action = retry, onAction = onRetry)

        ListBanner.PricesPartial ->
            Banner(text = stringResource(R.string.list_prices_partial), action = retry, onAction = onRetry)
    }
}

/** The state word a row shows beside its composite; shared with the onboarding backdrop. */
internal val RowState.label: Int
    get() = when (this) {
        RowState.STRONG -> R.string.list_state_strong
        RowState.FAIR -> R.string.list_state_fair
        RowState.WEAK -> R.string.list_state_weak
    }

private val SearchTopGap = 22.dp
private val HeadingTopGap = 30.dp
private val SectionTopGap = 28.dp
private const val SkeletonRowCount = 6

// ---- Previews ------------------------------------------------------------------------------

private fun sampleRow(
    ticker: String,
    symbol: String,
    company: String,
    composite: Double,
    state: RowState,
    price: Double,
    reference: Double,
    ageDays: Int,
) = ListRow(
    ticker = ticker,
    symbol = symbol,
    mint = ticker,
    company = company,
    composite = composite,
    state = state,
    stale = false,
    ageDays = ageDays,
    priceUsd = price,
    referencePriceUsd = reference,
    analyzed = true,
)

private fun samplePriceOnlyRow(
    ticker: String,
    symbol: String,
    company: String,
    price: Double,
    reference: Double,
) = ListRow(
    ticker = ticker,
    symbol = symbol,
    mint = ticker,
    company = company,
    composite = null,
    state = null,
    stale = false,
    ageDays = null,
    priceUsd = price,
    referencePriceUsd = reference,
    analyzed = false,
)

private val PreviewState = ListUiState(
    watched = 3,
    analyzed = listOf(
        sampleRow("TSLA", "TSLAx", "Tesla, Inc.", 71.0, RowState.STRONG, 366.17, 365.84, 2),
        sampleRow("NVDA", "NVDAx", "NVIDIA Corp.", 68.0, RowState.STRONG, 182.11, 182.18, 1),
        sampleRow("AAPL", "AAPLx", "Apple Inc.", 61.0, RowState.FAIR, 232.54, 232.52, 2),
        sampleRow("COIN", "COINx", "Coinbase Global", 47.0, RowState.WEAK, 301.08, 300.84, 3),
    ),
    withoutAnalysis = listOf(
        samplePriceOnlyRow("TSM", "TSMx", "Taiwan Semiconductor", 264.10, 263.97),
        samplePriceOnlyRow("ASML", "ASMLx", "ASML Holding", 812.40, 812.48),
    ),
)

@InstrumentPreviews
@Composable
private fun ListPreview() {
    PreviewCanvas {
        ListContent(
            state = PreviewState,
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun ListSnapshotPreview() {
    PreviewCanvas {
        ListContent(
            state = PreviewState.copy(
                fromSnapshot = true,
                snapshotCapturedOn = LocalDate.of(2026, 9, 12),
            ),
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun ListLoadingPreview() {
    PreviewCanvas {
        ListContent(
            state = ListUiState(isLoading = true),
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
        )
    }
}
