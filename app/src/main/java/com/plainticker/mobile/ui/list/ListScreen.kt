package com.plainticker.mobile.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
// The row component and this package's row model share a name; the anatomy keeps an alias.
import com.plainticker.mobile.ui.components.ListRow as InstrumentRow
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TodayStrip
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType
import com.plainticker.mobile.ui.vote.VoteActions
import com.plainticker.mobile.ui.vote.VoteSheet
import com.plainticker.mobile.ui.vote.VoteViewModel
import java.time.LocalDate

/**
 * The List (T8, DT8; design/canvas/instrument.py screen_list). One scrolling column: the header
 * the host hands in (the TopBar and the tabs, so they scroll away and nothing is sticky), the
 * Today strip while something is watched, the one banner slot, the search field, then the
 * analyzed rows, chaptered by sector (task A1, docs/plan-monetisation-2026-09-19.md section 1.5).
 *
 * The roughly 672 uncovered rows no longer tail the list: they leave for a Vote tab in a later
 * task, so nothing here draws them as a section of their own any more. Their data stays reachable
 * two ways. Search spans both sets at once, as one flat list, unchaptered ([ListContent]'s search
 * branch): typing a query narrows [ListUiState.analyzed] and [ListUiState.withoutAnalysis]
 * together, and an uncovered hit opens the same Detail an analyzed one does. And the "Next up"
 * strip still leads where "Without analysis" used to: the three uncovered tokens staked SKR has
 * voted to cover next, with the weight behind each (docs/skr-curation-spec-2026-09-13.md, step 3).
 * It is decided by [nextUpStrip] and drawn here, and when there is nothing to draw it is not
 * there: no heading, no banner, no empty section.
 *
 * Every number goes through [Fmt]: the composite as an integer, the premium as a signed percent,
 * the age as "2 d old", a chapter's count as a plain integer. The meta line carries at most one
 * middle dot, and the Ukrainian headline of `/summary` is not a field a row even has (see
 * [ListViewModel]).
 */
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    voteViewModel: VoteViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vote by voteViewModel.state.collectAsStateWithLifecycle()
    // The sheet is a modal surface and draws in its own window, so it costs this Box no layout
    // and takes none from the list. It is a sibling of the LazyColumn rather than an item in it:
    // an item is disposed when it scrolls out, and a vote that was mid-flight would go with it.
    Box(modifier.fillMaxSize()) {
        ListContent(
            state = state,
            onQueryChange = viewModel::search,
            onClearSearch = viewModel::clearSearch,
            onRetry = viewModel::refresh,
            onOpenDetail = onOpenDetail,
            onVote = { ticker, symbol -> voteViewModel.vote(ticker, symbol) },
            header = header,
        )
        VoteSheet(
            state = vote,
            actions = VoteActions(
                onConfirm = voteViewModel::confirm,
                onRetry = voteViewModel::retry,
                onClose = voteViewModel::close,
            ),
        )
    }
}

@Composable
internal fun ListContent(
    state: ListUiState,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The vote a row under "Without analysis" offers, and a leader of the "Next up" strip too: the
     * equity ticker the server joins on and the symbol a reader calls it. Null in the previews and
     * the gallery, where there is no wallet to take it anywhere; the rows then draw exactly as they
     * did before.
     */
    onVote: ((ticker: String, symbol: String) -> Unit)? = null,
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
                if (state.watched > 0) TodayStrip(text = todayText(state))
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

            // A token listed this morning is on neither the bundled snapshot nor the catalog kept
            // on disk for the day, and the reader who searched for it is the one person who knows
            // to look. This action is why a settled list has a way to reach the network at all:
            // it goes to the same [ListViewModel.refresh] the banners offer, which asks the
            // catalog for the network rather than for whichever cache still answers.
            state.searchMiss -> item(key = "miss") {
                EmptyLine(
                    text = stringResource(R.string.list_search_empty, state.query),
                    action = stringResource(R.string.list_search_look_again),
                    onAction = onRetry,
                )
            }

            // Both sources answered and neither had a row. Rare, but the screen was otherwise
            // a wordmark, a search field and nothing else, with no banner to explain it.
            state.emptyResult -> item(key = "empty") { EmptyLine(stringResource(R.string.list_empty)) }

            // Browsing: analyzed rows chaptered by sector, then the Next up strip. Search draws a
            // different shape (see below), so this branch runs only while the query is blank.
            state.query.isBlank() -> {
                val chapters = state.analyzedChapters
                chapters.forEachIndexed { chapterIndex, chapter ->
                    item(key = "chapter:${chapter.sector ?: NoSectorKey}") {
                        Heading(
                            text = chapter.sector ?: stringResource(R.string.list_heading_no_sector),
                            topPadding = if (chapterIndex == 0) HeadingTopGap else SectionTopGap,
                            meta = Fmt.count(chapter.rows.size),
                        )
                    }
                    itemsIndexed(chapter.rows, key = { _, row -> "a:" + row.ticker }) { index, row ->
                        AnalyzedRow(row = row, last = index == chapter.rows.lastIndex, onOpenDetail = onOpenDetail)
                    }
                }

                // The uncovered rows themselves no longer tail the list (task A1): the roughly 672
                // of them leave for a Vote tab in a later task. Only the leaders staked SKR has
                // voted to cover next still lead here, exactly where "Without analysis" used to.
                val leaders = state.nextUpStrip
                if (leaders.isNotEmpty()) {
                    item(key = "without") {
                        Heading(
                            text = stringResource(R.string.list_heading_without_analysis),
                            topPadding = SectionTopGap,
                        )
                    }
                    item(key = "next-up-label") { NextUpLabel() }
                    itemsIndexed(leaders, key = { _, leader -> "n:" + leader.ticker }) { index, leader ->
                        NextUpLeaderRow(
                            leader = leader,
                            last = index == leaders.lastIndex,
                            onOpenDetail = onOpenDetail,
                            onVote = onVote,
                        )
                    }
                }
            }

            // Searching: one flat list across both sets, unchaptered. An uncovered hit draws
            // exactly as it does under "Without analysis", price and vote action included, and
            // opens the same Detail an analyzed hit does.
            else -> {
                itemsIndexed(state.analyzed, key = { _, row -> "a:" + row.ticker }) { index, row ->
                    AnalyzedRow(
                        row = row,
                        last = index == state.analyzed.lastIndex && state.withoutAnalysis.isEmpty(),
                        onOpenDetail = onOpenDetail,
                    )
                }
                itemsIndexed(state.withoutAnalysis, key = { _, row -> "p:" + row.ticker }) { index, row ->
                    PriceOnlyRow(
                        row = row,
                        last = index == state.withoutAnalysis.lastIndex,
                        onOpenDetail = onOpenDetail,
                        onVote = onVote,
                    )
                }
            }
        }
    }
}

/**
 * One sentence where the rows would be, so no state of this screen is a blank column, with the
 * one text action that state can offer beside it.
 */
@Composable
private fun EmptyLine(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = PlainTickerType.body,
            color = Ink2,
            modifier = Modifier.weight(1f).padding(vertical = HeadingTopGap),
        )
        if (action != null && onAction != null) TextAction(label = action, onClick = onAction)
    }
}

/**
 * The Today strip (docs/data-map.md, List (T8)): how many are watched, and the next report among
 * them when the daily check has found one. The count alone stands until it has.
 */
@Composable
private fun todayText(state: ListUiState): String {
    val report = state.nextReport
        ?: return pluralStringResource(R.plurals.list_today_watched, state.watched, Fmt.count(state.watched))
    return pluralStringResource(
        R.plurals.list_today,
        state.watched,
        Fmt.count(state.watched),
        report.symbol,
        Fmt.monthDay(report.on),
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
        // Every analyzed row keeps the word's column open, including the row that has no word,
        // because the composites under this heading are one column to the reader scanning them.
        reserveValueSub = true,
        divider = !last,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
    )
}

/**
 * An xStock PlainTicker has not classified: everything Muted, the price as the value.
 *
 * This is the section the curation loop acts on. 672 of the 832 tokenized stocks have no analysis
 * at all (docs/skr-curation-spec-2026-09-13.md), and until now a row here offered a price and
 * nothing else. The trailing action votes with the weight of the reader's staked SKR for this one
 * to be covered next: the same [TextAction] the watchlist row already uses, one word wide because
 * the 64dp row still has to carry a ticker, a company and a price beside it. The sentence the word
 * is short for is on the sheet it opens, which leads with "Vote to cover NFLXx".
 */
@Composable
private fun PriceOnlyRow(
    row: ListRow,
    last: Boolean,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
) {
    InstrumentRow(
        ticker = row.display,
        company = row.company,
        meta = rowMeta(row),
        valueRight = row.priceUsd?.let { Fmt.price(it) },
        trailingAction = if (onVote == null) null else stringResource(R.string.vote_action_row),
        onTrailingAction = if (onVote == null) null else ({ onVote(row.ticker, row.display) }),
        muted = true,
        divider = !last,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
    )
}

/**
 * "Next up, by staked SKR": the label over the leaders, 13 Outfit 500 Muted, the face a fact
 * grid labels its cells in. It sits under the section heading rather than being one, because
 * the leaders are still tokens without analysis and the strip is the front of that section.
 */
@Composable
private fun NextUpLabel() {
    Text(
        text = stringResource(R.string.next_up_label),
        style = PlainTickerType.label,
        color = Muted,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = NextUpLabelGap),
    )
}

/**
 * One leader of the "Next up" strip: the token and its company left, how many wallets voted on
 * the meta line, the staked SKR behind it as the value, and the same one-word vote the rows
 * below it carry. Every figure is in the numeral face because it is a number. The row is not
 * muted the way a price-only row is: the value on its right is a fact this app stands behind,
 * where a price-only row's muting marks the absence of an analysis. Tapping it opens the same
 * Detail the row further down would, so a leader is never a second way to reach a token.
 */
@Composable
private fun NextUpLeaderRow(
    leader: NextUpLeader,
    last: Boolean,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
) {
    InstrumentRow(
        ticker = leader.display,
        company = leader.company,
        meta = leader.votersCopy.text(),
        valueRight = leader.weight.text(),
        trailingAction = if (onVote == null) null else stringResource(R.string.vote_action_row),
        onTrailingAction = if (onVote == null) null else ({ onVote(leader.ticker, leader.display) }),
        divider = !last,
        onClick = { onOpenDetail(leader.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, leader.display),
    )
}

/**
 * What the quote is worth, then the age of the analysis, one middle dot between them.
 *
 * Above the liquidity floor the first half is the premium against the NYSE close and nothing
 * here changed. Below it [TrackingQuality] withholds the premium, so the row states what stands
 * behind the price instead (docs/data-map.md): $34 makes a quoted premium arithmetic rather than
 * a price, and the reader is owed the reason. The figure is Jupiter's and the row does not call
 * it the pool, because measured against DexScreener and GeckoTerminal on 2026-09-13 it runs at
 * 0.21 to 0.54 of what they count. The sentence is a fact about the token and not a risk flag, so
 * it stays in the Muted meta line and never takes Caution, which DESIGN.md section 2 keeps for
 * issuer control. It is deliberately no longer than the string it replaced: the meta line is one
 * ellipsized line and it still has to carry the analysis age after a middle dot.
 *
 * Either half can be missing: an unpriced row keeps its age, an analysis from today prints no
 * age at all, and a row with neither has no meta line. It is one line in every case, so no row
 * grows taller than the 64dp the list is drawn on.
 */
@Composable
private fun rowMeta(row: ListRow): String? {
    val quote = when (val tracking = row.tracking) {
        is TrackingQuality.Tracked ->
            tracking.premiumPct?.let { stringResource(R.string.list_row_meta_premium, Fmt.percent(it)) }

        is TrackingQuality.Thin ->
            stringResource(R.string.list_row_meta_thin, Fmt.compactMoney(tracking.poolUsd, roundDown = true))

        TrackingQuality.Untracked -> stringResource(R.string.list_row_meta_pool_unknown)

        null -> null
    }
    val age = row.ageForMeta?.let { Fmt.daysOld(it) }
    return when {
        quote != null && age != null -> stringResource(R.string.list_row_meta_join, quote, age)
        quote != null -> quote
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

        // The refresh is already running, so this one carries no Retry: the only thing a tap
        // could do is start again what is already in flight.
        is ListBanner.SnapshotRefreshing -> Banner(
            text = banner.capturedOn
                ?.let { stringResource(R.string.list_snapshot_refreshing, Fmt.day(it)) }
                ?: stringResource(R.string.list_snapshot_refreshing_undated),
        )

        is ListBanner.Snapshot -> Banner(
            text = banner.capturedOn
                ?.let { stringResource(R.string.list_snapshot_banner, Fmt.day(it)) }
                ?: stringResource(R.string.list_snapshot_banner_undated),
            action = retry,
            onAction = onRetry,
        )

        is ListBanner.Stale ->
            Banner(text = stringResource(R.string.list_stale_banner, Fmt.daysOld(banner.newestDays)))

        // The hours tier, in Detail's own words out of Detail's own strings: the caveat a reader
        // meets one tap away must not be worded differently on the screen they came from.
        ListBanner.MarketClosed -> Banner(text = stringResource(R.string.banner_market_closed))

        ListBanner.MarketClosedLocal -> Banner(text = stringResource(R.string.banner_market_closed_local))

        ListBanner.MarketOpenLocal -> Banner(text = stringResource(R.string.banner_market_open_local))

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

/** Under the strip's label. */
private val NextUpLabelGap = 6.dp
private const val SkeletonRowCount = 6

/** LazyColumn item key for the trailing chapter of rows `/summary` sent no sector for. */
private const val NoSectorKey = "no-sector-chapter"

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
    poolUsd: Double? = 250_000.0,
    sector: String? = null,
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
    poolUsd = poolUsd,
    analyzed = true,
    sector = sector,
)

private fun samplePriceOnlyRow(
    ticker: String,
    symbol: String,
    company: String,
    price: Double,
    reference: Double,
    poolUsd: Double? = 250_000.0,
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
    poolUsd = poolUsd,
    analyzed = false,
)

private val PreviewState = ListUiState(
    watched = 3,
    analyzed = listOf(
        sampleRow("TSLA", "TSLAx", "Tesla, Inc.", 71.0, RowState.STRONG, 366.17, 365.84, 2, sector = "Consumer Discretionary"),
        sampleRow("NVDA", "NVDAx", "NVIDIA Corp.", 68.0, RowState.STRONG, 182.11, 182.18, 1, sector = "Information Technology"),
        sampleRow("AAPL", "AAPLx", "Apple Inc.", 61.0, RowState.FAIR, 232.54, 232.52, 2, sector = "Information Technology"),
        // Below the floor, as APPx read live on 2026-09-12: +89.34% quoted off a pool of $34.
        sampleRow(
            "APP", "APPx", "AppLovin Corp.", 58.0, RowState.FAIR, 1_158.76, 612.00, 2,
            poolUsd = 34.0, sector = "Communication Services",
        ),
        // Priced, with no depth reported: unknown, which is not the same as deep.
        sampleRow(
            "UNH", "UNHx", "UnitedHealth Group", 52.0, RowState.FAIR, 331.20, 338.38, 1,
            poolUsd = null, sector = "Health Care",
        ),
        sampleRow("COIN", "COINx", "Coinbase Global", 47.0, RowState.WEAK, 301.08, 300.84, 3, sector = "Financials"),
        // /summary sent no sector for this one: the trailing chapter, not a dropped row.
        sampleRow("XOM", "XOMx", "Exxon Mobil Corp.", 44.0, RowState.WEAK, 118.20, 117.90, 3, sector = null),
    ),
    withoutAnalysis = listOf(
        samplePriceOnlyRow("TSM", "TSMx", "Taiwan Semiconductor", 264.10, 263.97),
        samplePriceOnlyRow("ASML", "ASMLx", "ASML Holding", 1_059.61, 812.48, poolUsd = 61.0),
    ),
    // The strip as the leaders would read: the measured stake of 2026-09-13 and the median stake.
    nextUp = listOf(NextUpRow("TSM", "31209870777", 3), NextUpRow("ASML", "6719000000", 1)),
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
private fun ListSearchMissPreview() {
    PreviewCanvas {
        ListContent(
            state = PreviewState.copy(query = "RBLX", analyzed = emptyList(), withoutAnalysis = emptyList()),
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
