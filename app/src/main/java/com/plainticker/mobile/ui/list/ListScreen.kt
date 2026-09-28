package com.plainticker.mobile.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberChip
import com.plainticker.mobile.ui.components.SkeletonChip
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.SkeletonTickerRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.stocks.StocksFilter
import com.plainticker.mobile.ui.stocks.matchesStocksFilter
import com.plainticker.mobile.ui.stocks.showsDeepPoolChip
import com.plainticker.mobile.ui.stocks.stocksFilterFromSaveKey
import com.plainticker.mobile.ui.stocks.toSaveKey
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberTheme
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.vote.VoteActions
import com.plainticker.mobile.ui.vote.hasVoted
import com.plainticker.mobile.ui.vote.VoteSheet
import com.plainticker.mobile.ui.vote.VoteViewModel
import java.time.LocalDate

/**
 * Stocks (docs/design-research-2026-09-21.md section 3): the founder's original complaint, a
 * ticker list scrolled forever, given a top a reader can move through with intent instead. Search
 * at the top, then sticky sector chapters under one horizontally scrolling filter row (Deep pool,
 * Watched, sector; the chapter jump rail was removed in the judges' round 2), roughly 160 analyzed rows chaptered and roughly 670 uncovered ones
 * reachable only by search, exactly as the research draws it (section 3, "The list becomes
 * Stocks"). This file is still named `ListScreen` because [com.plainticker.mobile.ui.stocks.StocksScreen]
 * mounts it unchanged as the seam a shell pass left behind; nothing else composes it.
 *
 * **Where this reading of the approved Amber Stocks frame (`gen.py`'s "amber" Stocks board)
 * departs from it, and why:**
 *
 * - The frame's row context reads "of 100, fair" (the composite's classification word). DESIGN.md
 *   section 1 is explicit that the liquidity floor's disclosure is content and legal copy the
 *   redesign does not touch, "nothing about the redesign touches it," and that it belongs "on the
 *   row's single meta line." [AmberTickerRow] has exactly one context slot, and the two contents
 *   cannot share it without either a second middle dot (a copy rule) or two unrelated lines forced
 *   into one wrapping sentence, so the disclosure wins: [rowMeta] is unchanged from before this
 *   pass, and the classification word is dropped from the row (the composite figure, and Detail,
 *   still carry it).
 * - The frame draws a segmented "Analyzed 160 / Without analysis 768" control. Task A1 already
 *   retired full browsing of the uncovered set before this pass started (this file's own header
 *   comment on the search branch below), so there is no second browsable list for that control to
 *   switch to; building the control would be a working toggle over a section that draws nothing.
 *   It is not built. Search and the Next-up strip stay the only two routes to an uncovered row.
 * - The frame's Today strip (watched count, next report) does not appear on the Stocks board at
 *   all; the research's information architecture (section 3) folds it into Today's own "Yours"
 *   block and a Watched filter chip here. An earlier pass kept a Today strip on this screen
 *   anyway, unsure whether Today already carried it; it did, and the audit of 2026-09-26 found
 *   "Today: 1 stock watched" drawn above this screen's own banner as a pure repeat, so the strip
 *   and its two counted strings are gone from here. The Watched chip still reads the count.
 *
 * **Performance, for roughly 830 rows under sticky headers:** every row is its own `LazyColumn`
 * item with its own stable key (`"a:" + ticker`, `"p:" + ticker`, `"n:" + ticker`, unchanged from
 * before this pass), never a chapter composed as one non-lazy block; see [groupedRowModifier]'s
 * own doc comment for why [com.plainticker.mobile.ui.components.AmberTickerRowGroup] does not fit
 * here even though it is the component the anatomy names for this exact tonal container. The
 * filtered chapters, the chip row's sector list and the
 * tracked count are each behind their own `remember` keyed to only the state slice that can change
 * them, so retyping a search character or a chip toggling does not recompute the others.
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
    val voted by voteViewModel.votedTickers.collectAsStateWithLifecycle()
    val colors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors
    Box(modifier.fillMaxSize()) {
        // AmberTheme wraps only the restyled content, not VoteSheet: VoteSheet is Instrument's own
        // component (ui/vote, another agent's lane), untouched by this pass, and keeping it outside
        // this boundary means it goes on reading whatever theme the host (still PlainTickerTheme,
        // DESIGN.md section 11) already provides, exactly as it did before this file changed.
        AmberTheme(useDarkTheme = colors === AmberDarkColors) {
            // Pull to refresh (final QA of 1.3.19: a pull on Stocks drew nothing and changed no
            // figure). The indicator stands until fresh figures are drawn (ListViewModel.pull).
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.pulling,
                onRefresh = viewModel::pull,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = state.pulling,
                        // Below the clock, where the list's own viewport starts.
                        modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars),
                        containerColor = colors.surfaceHigh,
                        color = colors.actionText,
                    )
                },
            ) {
                ListContent(
                    state = state,
                    onQueryChange = viewModel::search,
                    onClearSearch = viewModel::clearSearch,
                    onRetry = viewModel::refresh,
                    onOpenDetail = onOpenDetail,
                    onVote = { ticker, symbol -> voteViewModel.vote(ticker, symbol) },
                    votedTickers = voted,
                    header = header,
                    colors = colors,
                )
            }
        }
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
     * What a merged screen reader item says instead of its parts read end to end; see [ListRow].
     * Null in the previews and the gallery, where there is no wallet to take it anywhere; the rows
     * then draw exactly as they did before.
     */
    onVote: ((ticker: String, symbol: String) -> Unit)? = null,
    /**
     * Tickers this wallet already voted for in the open round ([VoteViewModel.votedTickers], the
     * one rule every Vote surface reads): their rows draw a quiet "Voted" instead of Vote.
     */
    votedTickers: Set<String> = emptySet(),
    header: @Composable () -> Unit = {},
    colors: AmberColors = defaultAmberColors(),
) {
    val cold = state.isLoading && state.analyzed.isEmpty() && state.withoutAnalysis.isEmpty()

    // The chip row's own sector list is read off every analyzed row, not off whatever a filter
    // has already narrowed the chapters to, so choosing "Tracked" does not make a sector's own
    // chip disappear because that sector has no tracked row left (the
    // row keeps every sector while another filter is active).
    val allSectors = remember(state.analyzed) {
        state.analyzed.chapteredBySector().mapNotNull { it.sector }
    }
    val trackedCount = remember(state.analyzed) { state.analyzed.count { it.tracking is TrackingQuality.Tracked } }
    // No price run has finished yet: rows say they are being priced and the Deep pool chip's slot
    // is held, so the figures and the chip arrive without shoving anything (device QA of 1.3.17).
    val pricesPending = !state.pricesSettled && !state.pricesUnavailable

    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeFilter = stocksFilterFromSaveKey(filterKey)

    // What the chapters actually draw. state.analyzed already carries the search narrowing
    // (ListViewModel.search), so a chip on top of a query narrows what the query already
    // narrowed; a query itself switches to the flat branch below and this value goes unused.
    val visibleChapters = remember(state.analyzed, state.watchedTickers, activeFilter) {
        state.analyzed.filter { it.matchesStocksFilter(activeFilter, state.watchedTickers) }.chapteredBySector()
    }

    // No jump rail beside the list any more (judges' round 2, 2026-09-27): its "Com", "Dis", "Sta"
    // codes clipped and meant nothing to a first-time reader, and it took 48dp off every line,
    // the hours banner included. The list takes the full width; a sector chip narrows to one sector.
    LazyColumn(
        // The viewport starts below the status bar (final QA of 1.3.19): a pinned sector head
        // sticks to the top of the viewport whatever the content padding says, and with the
        // viewport under the clock it sat behind the scrim with its title cut off. Consuming the
        // inset here also zeroes the TopBar's own status-bar padding in the header item, so the
        // first frame is where it was. The scrim still fades the rows as they leave.
        modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)),
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "chrome") {
            StocksChrome(
                state = state,
                onQueryChange = onQueryChange,
                onClearSearch = onClearSearch,
                onRetry = onRetry,
                colors = colors,
                trackedCount = trackedCount,
                sectors = allSectors,
                activeFilter = activeFilter,
                onFilterSelect = { tapped -> filterKey = if (activeFilter == tapped) null else tapped.toSaveKey() },
                pricesPending = pricesPending,
                cold = cold,
            )
        }

        when {
            cold -> item(key = "skeleton") {
                Column(Modifier.fillMaxWidth()) {
                    AmberSectionHead(title = stringResource(R.string.list_heading_analyzed), colors = colors)
                    SkeletonTickerRows(count = SkeletonRowCount, colors = colors)
                }
            }

            // A token listed this morning is on neither the bundled snapshot nor the catalog
            // kept on disk for the day, and the reader who searched for it is the one person
            // who knows to look. This action is why a settled list has a way to reach the
            // network at all: it goes to the same [ListViewModel.refresh] the banners offer,
            // which asks the catalog for the network rather than for whichever cache still
            // answers.
            state.searchMiss -> item(key = "miss") {
                EmptyLine(
                    text = stringResource(R.string.list_search_empty, state.query),
                    action = stringResource(R.string.list_search_look_again),
                    onAction = onRetry,
                    colors = colors,
                )
            }

            // Both sources answered and neither had a row. Rare, but the screen was otherwise
            // a wordmark, a search field and nothing else, with no banner to explain it.
            state.emptyResult -> item(key = "empty") { EmptyLine(stringResource(R.string.list_empty), colors = colors) }

            // Browsing: analyzed rows chaptered by sector as sticky headers, then the Next up
            // strip. Search draws a different shape (see below), so this branch runs only
            // while the query is blank.
            state.query.isBlank() -> {
                visibleChapters.forEach { chapter ->
                    stickyHeader(key = "chapter:${chapter.sector ?: NoSectorKey}") {
                        AmberSectionHead(
                            title = chapter.sector ?: stringResource(R.string.list_heading_no_sector),
                            meta = Fmt.count(chapter.rows.size),
                            colors = colors,
                        )
                    }
                    itemsIndexed(chapter.rows, key = { _, row -> "a:" + row.ticker }) { index, row ->
                        AnalyzedRow(
                            row = row,
                            pricesPending = pricesPending,
                            modifier = groupedRowModifier(colors, isFirst = index == 0, isLast = index == chapter.rows.lastIndex),
                            colors = colors,
                            onOpenDetail = onOpenDetail,
                        )
                    }
                }

                // The roughly 672 uncovered rows no longer tail the list (task A1): they left
                // for a Vote tab in an earlier pass. Only the leaders staked SKR has voted to
                // cover next still lead here, exactly where "Without analysis" used to.
                val leaders = state.nextUpStrip
                if (leaders.isNotEmpty()) {
                    item(key = "without") {
                        AmberSectionHead(title = stringResource(R.string.list_heading_without_analysis), colors = colors)
                    }
                    item(key = "next-up-label") { NextUpLabel(colors = colors) }
                    itemsIndexed(leaders, key = { _, leader -> "n:" + leader.ticker }) { index, leader ->
                        NextUpLeaderRow(
                            leader = leader,
                            modifier = groupedRowModifier(colors, isFirst = index == 0, isLast = index == leaders.lastIndex),
                            colors = colors,
                            onOpenDetail = onOpenDetail,
                            onVote = onVote,
                            voted = votedTickers.hasVoted(leader.ticker),
                        )
                    }
                }
            }

            // Searching: one flat list across both sets, unchaptered. An uncovered hit draws
            // exactly as it does under "Without analysis", price and vote action included, and
            // opens the same Detail an analyzed hit does. After this pass, search is the only
            // way to reach an uncovered ticker that is not one of the Next-up leaders.
            else -> {
                // Clear of the search field's underline (device QA of 1.3.18: the first result sat
                // on it when no chip row stood between them).
                item(key = "search-gap") { Spacer(Modifier.height(SearchResultsGap)) }
                itemsIndexed(state.analyzed, key = { _, row -> "a:" + row.ticker }) { index, row ->
                    AnalyzedRow(
                        row = row,
                        pricesPending = pricesPending,
                        modifier = groupedRowModifier(
                            colors,
                            isFirst = index == 0,
                            isLast = index == state.analyzed.lastIndex && state.withoutAnalysis.isEmpty(),
                        ),
                        colors = colors,
                        onOpenDetail = onOpenDetail,
                    )
                }
                itemsIndexed(state.withoutAnalysis, key = { _, row -> "p:" + row.ticker }) { index, row ->
                    PriceOnlyRow(
                        row = row,
                        pricesPending = pricesPending,
                        modifier = groupedRowModifier(
                            colors,
                            isFirst = index == 0 && state.analyzed.isEmpty(),
                            isLast = index == state.withoutAnalysis.lastIndex,
                        ),
                        colors = colors,
                        onOpenDetail = onOpenDetail,
                        onVote = onVote,
                        voted = votedTickers.hasVoted(row.ticker),
                    )
                }
            }
        }
    }
}

// ---- Chrome: title, banner, search, filter row -----------------------------------------------

@Composable
private fun StocksChrome(
    state: ListUiState,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRetry: () -> Unit,
    colors: AmberColors,
    trackedCount: Int,
    sectors: List<String>,
    activeFilter: StocksFilter?,
    onFilterSelect: (StocksFilter) -> Unit,
    pricesPending: Boolean = false,
    /**
     * Nothing drawn yet: the chip row and the legend keep their places with outlines, so the rows
     * landing do not push the list down by a chip row's height (device QA of 1.3.18).
     */
    cold: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().background(colors.surfaceGround)) {
        Text(
            text = stringResource(R.string.nav_stocks),
            style = AmberType.sectionHead,
            color = colors.textPrimary,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        // No Today strip here any more (audit 2026-09-26): "Today: 1 stock watched" repeated
        // Today's own screen one tab away, above this screen's own banner.
        state.banner?.let { StateBanner(banner = it, onRetry = onRetry) }
        Spacer(Modifier.height(SearchTopGap))
        SearchField(query = state.query, onQueryChange = onQueryChange, onClearSearch = onClearSearch)
        if (sectors.isNotEmpty()) {
            StocksFilterRow(
                trackedCount = trackedCount,
                watchedCount = state.watched,
                sectors = sectors,
                active = activeFilter,
                onSelect = onFilterSelect,
                colors = colors,
                holdDeepPoolSlot = pricesPending,
            )
            if (activeFilter == StocksFilter.Tracked) {
                Text(
                    text = stringResource(R.string.stocks_filter_tracked_note, Fmt.compactMoney(TrackingQuality.MIN_POOL_USD)),
                    style = AmberType.meta,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        if (sectors.isEmpty() && cold) ColdFilterRow(colors = colors)
        // What the number on every analyzed row means, said once above the rows rather than
        // squeezed beside each one (judges' round 2: a bare "score 62" told a beginner nothing).
        if (state.query.isBlank() && (state.analyzed.isNotEmpty() || cold)) {
            Text(
                text = stringResource(R.string.list_row_score_legend),
                style = AmberType.meta,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClearSearch: () -> Unit) {
    // Field is Instrument's own text input (ui/components, not restyled by this pass, DESIGN.md
    // section 4). Re-implementing its focus, cursor and IME handling here to chase the approved
    // frame's pill-shaped search field risked a real input bug this pass has no device to catch,
    // for a visual gain a screen-local wrapper cannot make up for without also wrapping its
    // internals; it keeps working exactly as it did on List, at the cost of being the one piece
    // of Stocks' chrome that does not yet read Amber's tokens.
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

/**
 * The filter row's place while nothing is drawn yet: three chip outlines at the row's own height
 * and padding, so the real chips replace them where they stand.
 */
@Composable
private fun ColdFilterRow(colors: AmberColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColdChipWidths.forEach { width -> SkeletonChip(width = width, colors = colors) }
    }
}

/**
 * The filter row (docs/design-research-2026-09-21.md section 3): Deep pool and Watched first, then
 * every sector, in one horizontally scrolling row. It used to be a [androidx.compose.foundation.layout.FlowRow]
 * that wrapped to four rows of chips and pushed the first stock below the fold (judges' round 2,
 * 2026-09-27); a row the reader can swipe costs one line of height, and the chip cut off at the
 * screen edge is itself the sign that more follow. The padding sits inside the scroll, so the
 * first chip lines up with the search field and the last one can scroll clear of the edge.
 *
 * Deep pool is drawn only from [com.plainticker.mobile.ui.stocks.DEEP_POOL_CHIP_MIN] deep rows up
 * ([showsDeepPoolChip]); a chip reading "Deep pool 1" filtered the list to next to nothing.
 */
@Composable
private fun StocksFilterRow(
    trackedCount: Int,
    watchedCount: Int,
    sectors: List<String>,
    active: StocksFilter?,
    onSelect: (StocksFilter) -> Unit,
    colors: AmberColors,
    /**
     * Prices have not answered yet, so the deep-pool count is not known: a quiet placeholder the
     * chip's size holds its slot, and the chip replaces it in place rather than arriving seconds
     * later and pushing Watched and every sector sideways (device QA of 1.3.17).
     */
    holdDeepPoolSlot: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (holdDeepPoolSlot && !showsDeepPoolChip(trackedCount, active)) {
            SkeletonChip(width = DeepPoolSlotWidth, colors = colors)
        } else if (showsDeepPoolChip(trackedCount, active)) {
            AmberChip(
                // Deep pool and Watched read as a label plus a count, not a sentence a plural has
                // to agree with ("Deep pool 22", never "22 deep pool stocks"), so the count stays a
                // plain string; strings.xml's own comment on stocks_filter_tracked says the same.
                label = stringResource(R.string.stocks_filter_tracked, Fmt.count(trackedCount)), // lint-allow count: no noun follows it
                selected = active == StocksFilter.Tracked,
                onClick = { onSelect(StocksFilter.Tracked) },
                colors = colors,
            )
        }
        AmberChip(
            label = stringResource(R.string.stocks_filter_watched, Fmt.count(watchedCount)), // lint-allow count: no noun follows it
            selected = active == StocksFilter.Watched,
            onClick = { onSelect(StocksFilter.Watched) },
            colors = colors,
        )
        sectors.forEach { sector ->
            AmberChip(
                label = sector,
                selected = active == StocksFilter.Sector(sector),
                onClick = { onSelect(StocksFilter.Sector(sector)) },
                colors = colors,
            )
        }
    }
}

// ---- Rows ---------------------------------------------------------------------------------

/**
 * Groups consecutive rows into one 16dp tonal container the way
 * [com.plainticker.mobile.ui.components.AmberTickerRowGroup] draws it, one [LazyColumn] item at a
 * time instead of one non-lazy [Column] per chapter.
 *
 * **Why [AmberTickerRowGroup] does not fit here, said once rather than at every call site.**
 * [AmberTickerRowGroup]'s own content lambda is a plain, non-lazy `Column`: every row inside it
 * composes and measures together as one unit whenever the group is on screen or in the prefetch
 * window. That is exactly right for Today's own blocks (a handful of rows each) and exactly wrong
 * for Stocks, where a sector chapter can carry several dozen of the roughly 830 rows this screen
 * has: handing `LazyColumn` one giant composable per chapter throws away per-row recycling for
 * the chapters that most need it, which is the stutter this task's brief warns 830 rows under
 * sticky headers is the size where a naive list starts to show. This function draws the same
 * result, a 16dp radius on the group's own top and bottom row and a 1dp seam of the page's own
 * ground colour between rows, while keeping every row its own keyed `itemsIndexed` item.
 *
 * **Light theme's own edge, carried over from [AmberTickerRowGroup]'s own doc comment.**
 * `surfaceRaised` over `surfaceGround` is about 1.03:1 in light (`#FFFFFF` on `#FFFBF2`,
 * Tokens.kt) against 1.12:1 in dark, so a chapter of rows here loses the same structure
 * [AmberTickerRowGroup] would, and for the same reason: Stocks is the screen with the most rows
 * to lose it on. [AmberTickerRowGroup] draws one ring around one `Column`; there is no single
 * `Column` here to ring, one `LazyColumn` item per row, so [groupEdge] draws the group's own
 * outline a row at a time instead: a straight edge down each side of every row, and a straight
 * edge across the top of the first row and the bottom of the last, never a seam between two rows
 * (DESIGN.md section 8's surviving rule: "no border-as-frame around every row"). Every edge is
 * inset by [corner] on whichever end this row rounds, so it lands inside the row's own clipped
 * silhouette rather than crossing the curve as a straight chord; the last few pixels of each
 * rounded corner are left unstroked rather than hand-rolled as an arc this task has no device to
 * check pixel by pixel. Dark keeps the plain, unringed rows it always drew.
 */
private fun groupedRowModifier(colors: AmberColors, isFirst: Boolean, isLast: Boolean): Modifier {
    val corner = 16.dp
    val top = if (isFirst) corner else 0.dp
    val bottom = if (isLast) corner else 0.dp
    return Modifier
        .padding(horizontal = 16.dp)
        .padding(bottom = if (isLast) 0.dp else RowGapHeight)
        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        .then(
            if (colors === AmberLightColors) {
                Modifier.groupEdge(color = colors.border, corner = corner, isFirst = isFirst, isLast = isLast)
            } else {
                Modifier
            },
        )
}

/**
 * Draws [groupedRowModifier]'s light-only edge on top of whatever this row already painted (a
 * plain `drawWithContent` always draws after its own `drawContent()` call, regardless of where a
 * later `.background()` sits in the caller's own modifier chain), positioned by hand rather than
 * by re-walking the row's own clip shape: left and right run the row's full height except where
 * [corner] itself rounds a corner, top runs only on [isFirst] and bottom only on [isLast], each
 * inset by [corner] so it stops short of the curve instead of crossing it.
 */
private fun Modifier.groupEdge(color: Color, corner: Dp, isFirst: Boolean, isLast: Boolean): Modifier =
    drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val half = stroke / 2f
        val cornerPx = corner.toPx()
        val top = if (isFirst) cornerPx else 0f
        val bottom = if (isLast) size.height - cornerPx else size.height
        drawLine(color, Offset(half, top), Offset(half, bottom), strokeWidth = stroke)
        drawLine(color, Offset(size.width - half, top), Offset(size.width - half, bottom), strokeWidth = stroke)
        if (isFirst) {
            drawLine(color, Offset(cornerPx, half), Offset(size.width - cornerPx, half), strokeWidth = stroke)
        }
        if (isLast) {
            drawLine(
                color,
                Offset(cornerPx, size.height - half),
                Offset(size.width - cornerPx, size.height - half),
                strokeWidth = stroke,
            )
        }
    }

private val RowGapHeight = 1.dp

/**
 * Ticker and company left, the composite as an integer with the row's disclosure line right.
 *
 * [ListRow.locked] draws [R.string.pro_locked_value] in the figure slot instead: the composite
 * `/summary` withheld under the Pro-numbers lock never reaches this app to round or print, so
 * there is no number behind this word, the same "nothing here to blur" [row.composite] already
 * being null gives the unlocked branch when there is genuinely no analysis. Measured against the
 * real font, this word is the shortest content this slot ever draws
 * (`AmberTickerRowTest`'s own "Pro" arithmetic), so it always clears the figure budget the row's
 * own worst-case figure ("38,406.2 SKR") already proves.
 */
@Composable
private fun AnalyzedRow(
    row: ListRow,
    modifier: Modifier = Modifier,
    colors: AmberColors,
    onOpenDetail: (String) -> Unit,
    pricesPending: Boolean = false,
) {
    // "62 of 100", not "score 62" (judges' round 2, 2026-09-27): a bare "score" named no scale.
    // What the scale ranks against, the sector, is said once above the rows
    // (list_row_score_legend). Widest real value "100 of 100", figureRow 18sp/600 tnum: 92.538dp
    // at 1.0x, 120.299dp at 1.3x (fontTools, AmberTickerRowTest), still under the price figure.
    val figure = if (row.locked) {
        stringResource(R.string.pro_locked_value)
    } else {
        row.composite?.let { stringResource(R.string.list_row_score, Fmt.decimal(it, decimals = 0)) }
    }
    AmberTickerRow(
        ticker = row.display,
        company = row.company,
        figure = figure,
        context = rowMeta(row, pricesPending),
        colors = colors,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
        modifier = modifier,
    )
}

/**
 * An xStock PlainTicker has not classified: the price as the figure, the row's disclosure line
 * unchanged, and the same one-word vote action price-only rows have carried since the curation
 * loop needed a place to cast one from.
 */
@Composable
private fun PriceOnlyRow(
    row: ListRow,
    modifier: Modifier = Modifier,
    colors: AmberColors,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
    voted: Boolean = false,
    pricesPending: Boolean = false,
) {
    VotableAmberRow(
        ticker = row.display,
        company = row.company,
        figure = row.priceUsd?.let { Fmt.price(it) },
        context = rowMeta(row, pricesPending),
        colors = colors,
        modifier = modifier,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
        onVote = if (onVote == null || !row.votable) null else ({ onVote(row.ticker, row.display) }),
        voted = voted && row.votable,
    )
}

/** One leader of the "Next up" strip: the weight as the figure, the voter count as context. */
@Composable
private fun NextUpLeaderRow(
    leader: NextUpLeader,
    modifier: Modifier = Modifier,
    colors: AmberColors,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
    voted: Boolean = false,
) {
    VotableAmberRow(
        ticker = leader.display,
        company = leader.company,
        figure = leader.weight.text(),
        context = leader.votersCopy.text(),
        colors = colors,
        modifier = modifier,
        onClick = { onOpenDetail(leader.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, leader.display),
        onVote = if (onVote == null) null else ({ onVote(leader.ticker, leader.display) }),
        voted = voted,
    )
}

/**
 * [AmberTickerRow] with a trailing vote action beside it, for the two rows that need one and that
 * [AmberTickerRow] itself has no slot for (its anatomy is ticker, company, figure, context; a
 * trailing action is Instrument [ListRow]'s own fifth slot, not carried over). A sibling [Row]
 * rather than a change to [AmberTickerRow] itself, which is out of this task's files: the two
 * targets stay independently tappable, [AmberTickerRow] keeps its own click merged into one
 * spoken sentence, and [TextAction] keeps its.
 */
@Composable
private fun VotableAmberRow(
    ticker: String,
    company: String?,
    figure: String?,
    context: String?,
    colors: AmberColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClickLabel: String,
    onVote: (() -> Unit)?,
    /**
     * This wallet already voted for the row's token in the open round: a quiet "Voted" in the
     * action's place, the same word and colour the Vote tab draws (device QA of 1.3.17).
     */
    voted: Boolean = false,
) {
    Row(
        modifier = modifier.background(colors.surfaceRaised),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A voted row's quiet "Voted" is part of the row itself, pinned to its end edge, so the
        // whole width opens Detail (device QA of 1.3.18: the tap area stopped where the word began
        // and the "Voted" side did nothing).
        AmberTickerRow(
            ticker = ticker,
            company = company,
            figure = figure,
            context = context,
            trailingNote = if (voted) stringResource(R.string.vote_voted_row) else null,
            trailingActionAtEnd = voted,
            colors = colors,
            onClick = onClick,
            onClickLabel = onClickLabel,
            modifier = Modifier.weight(1f),
        )
        if (!voted && onVote != null) {
            TextAction(
                label = stringResource(R.string.vote_action_row),
                onClick = onVote,
                color = colors.actionText,
                contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * One sentence where the rows would be, so no state of this screen is a blank column, with the
 * one text action that state can offer beside it.
 */
@Composable
private fun EmptyLine(text: String, action: String? = null, onAction: (() -> Unit)? = null, colors: AmberColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = AmberType.body,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f).padding(vertical = EmptyLineGap),
        )
        if (action != null && onAction != null) TextAction(label = action, onClick = onAction, color = colors.actionText)
    }
}

/**
 * "Next up, by staked SKR": the label over the leaders, the face a fact grid labels its cells in.
 * It sits under the section heading rather than being one, because the leaders are still tokens
 * without analysis and the strip is the front of that section.
 */
@Composable
private fun NextUpLabel(colors: AmberColors) {
    Text(
        text = stringResource(R.string.next_up_label),
        style = AmberType.meta,
        color = colors.textTertiary(AmberSurface.GROUND),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = NextUpLabelGap),
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
 * it stays in a secondary role and never the caution colour, which DESIGN.md section 2 keeps for
 * issuer control. It is deliberately no longer than the string it replaced: the meta line is one
 * to two lines ([AmberTickerRow]'s own `context` wraps rather than clips) and it still has to
 * carry the analysis age after a middle dot.
 *
 * Either half can be missing: an unpriced row keeps its age, an analysis from today prints no
 * age at all, and a row with neither has no meta line.
 */
@Composable
private fun rowMeta(row: ListRow, pricesPending: Boolean = false): String? {
    val quote = when (val tracking = row.tracking) {
        is TrackingQuality.Tracked ->
            tracking.premiumPct?.let { stringResource(R.string.list_row_meta_premium, Fmt.percent(it)) }

        is TrackingQuality.Thin ->
            stringResource(R.string.list_row_meta_thin, Fmt.compactMoney(tracking.poolUsd, roundDown = true))

        TrackingQuality.Untracked -> stringResource(R.string.list_row_meta_pool_unknown)

        // No figure: say why rather than leave the line bare (device QA of 1.3.17). Still being
        // priced on a cold start; answered by Jupiter with no price (JEFx); a refused chunk says
        // nothing here, because the banner already does.
        null -> when {
            row.mint == null -> null
            row.priceUsd == null && row.quote == RowQuote.ANSWERED -> stringResource(R.string.list_row_meta_unpriced)
            row.priceUsd == null && row.quote == RowQuote.PENDING && pricesPending -> stringResource(R.string.list_row_meta_pricing)
            else -> null
        }
    }
    val age = row.ageForMeta?.let { pluralStringResource(R.plurals.list_row_age_days, it, Fmt.count(it)) }
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
            Banner(
                text = stringResource(
                    R.string.list_stale_banner,
                    pluralStringResource(R.plurals.list_row_age_days, banner.newestDays, Fmt.count(banner.newestDays)),
                ),
            )

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

/** Between the search field and the first result. */
private val SearchResultsGap = 12.dp

/** The cold filter row's three outlines: about Deep pool, Watched and one sector wide. */
private val ColdChipWidths = listOf(112.dp, 104.dp, 180.dp)

/** Vertical centering for an EmptyLine's sentence; unrelated to AmberSectionHead's own rhythm. */
private val EmptyLineGap = 30.dp

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
    // The strip as the leaders would read: a large example stake and the median stake.
    nextUp = listOf(NextUpRow("TSM", "38406150222", 3), NextUpRow("ASML", "6719000000", 1)),
)

@InstrumentPreviews
@Composable
private fun ListPreview() {
    AmberPreviewCanvas {
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
    AmberPreviewCanvas {
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
    AmberPreviewCanvas {
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
    AmberPreviewCanvas {
        ListContent(
            state = ListUiState(isLoading = true),
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
        )
    }
}

/** The Deep pool chip's own width at its usual count ("Deep pool 17"), held while prices load. */
private val DeepPoolSlotWidth = 112.dp
