package com.plainticker.mobile.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberChip
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TodayStrip
import com.plainticker.mobile.ui.components.rememberMotionEnabled
import com.plainticker.mobile.ui.stocks.ActiveChapter
import com.plainticker.mobile.ui.stocks.SectorChipRow
import com.plainticker.mobile.ui.stocks.StocksFilter
import com.plainticker.mobile.ui.stocks.activeChapterAt
import com.plainticker.mobile.ui.stocks.chapterStartIndices
import com.plainticker.mobile.ui.stocks.jumpAbbreviationRes
import com.plainticker.mobile.ui.stocks.matchesStocksFilter
import com.plainticker.mobile.ui.stocks.sectorChipRow
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
import com.plainticker.mobile.ui.vote.VoteSheet
import com.plainticker.mobile.ui.vote.VoteViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Stocks (docs/design-research-2026-09-21.md section 3): the founder's original complaint, a
 * ticker list scrolled forever, given a top a reader can move through with intent instead. Search
 * at the top, then sticky sector chapters with a chapter jump index and a wrapping filter row
 * (Tracked, Watched, sector), roughly 160 analyzed rows chaptered and roughly 670 uncovered ones
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
 *   block and a Watched filter chip here. [CopyLintTest]'s `CountCopyTest` pins two literal
 *   `pluralStringResource` calls to this exact file, and this pass could not confirm from inside
 *   an isolated worktree whether Today's own screen (built in parallel) already carries the
 *   content those pins describe, so [TodayStrip] stays rather than risk breaking a lint gate
 *   shared by every screen over a duplication this pass could not verify was resolved. It now
 *   sits beside the Watched chip, which reads the same count.
 *
 * **Performance, for roughly 830 rows under sticky headers:** every row is its own `LazyColumn`
 * item with its own stable key (`"a:" + ticker`, `"p:" + ticker`, `"n:" + ticker`, unchanged from
 * before this pass), never a chapter composed as one non-lazy block; see [groupedRowModifier]'s
 * own doc comment for why [com.plainticker.mobile.ui.components.AmberTickerRowGroup] does not fit
 * here even though it is the component the anatomy names for this exact tonal container. The
 * filtered chapters, the chip row's sector list, the per-chapter jump target indices and the
 * tracked count are each behind their own `remember` keyed to only the state slice that can change
 * them, so retyping a search character or a chip toggling does not recompute the others, and the
 * jump index's own highlighted entry is read through `derivedStateOf` so the rail recomposes only
 * when the highlighted chapter changes, not on every pixel `LazyListState.firstVisibleItemIndex`
 * reports while scrolling.
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
    val colors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors
    Box(modifier.fillMaxSize()) {
        // AmberTheme wraps only the restyled content, not VoteSheet: VoteSheet is Instrument's own
        // component (ui/vote, another agent's lane), untouched by this pass, and keeping it outside
        // this boundary means it goes on reading whatever theme the host (still PlainTickerTheme,
        // DESIGN.md section 11) already provides, exactly as it did before this file changed.
        AmberTheme(useDarkTheme = colors === AmberDarkColors) {
            ListContent(
                state = state,
                onQueryChange = viewModel::search,
                onClearSearch = viewModel::clearSearch,
                onRetry = viewModel::refresh,
                onOpenDetail = onOpenDetail,
                onVote = { ticker, symbol -> voteViewModel.vote(ticker, symbol) },
                header = header,
                colors = colors,
            )
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

/** `item(...)` calls before the first chapter's `stickyHeader`: `"header"`, `"chrome"`. */
private const val ChromeItemCount = 2

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
    header: @Composable () -> Unit = {},
    colors: AmberColors = defaultAmberColors(),
) {
    val cold = state.isLoading && state.analyzed.isEmpty() && state.withoutAnalysis.isEmpty()

    // The chip row's own sector list is read off every analyzed row, not off whatever a filter
    // has already narrowed the chapters to, so choosing "Tracked" does not make a sector's own
    // chip disappear because that sector has no tracked row left (StocksFilter.kt's SectorChipRow
    // doc comment).
    val allSectors = remember(state.analyzed) {
        state.analyzed.chapteredBySector().mapNotNull { it.sector }
    }
    val chipRow = remember(allSectors) { sectorChipRow(allSectors) }
    val trackedCount = remember(state.analyzed) { state.analyzed.count { it.tracking is TrackingQuality.Tracked } }

    var filterKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeFilter = stocksFilterFromSaveKey(filterKey)
    var sectorsExpanded by rememberSaveable { mutableStateOf(false) }

    // What the chapters actually draw. state.analyzed already carries the search narrowing
    // (ListViewModel.search), so a chip on top of a query narrows what the query already
    // narrowed; a query itself switches to the flat branch below and this value goes unused.
    val visibleChapters = remember(state.analyzed, state.watchedTickers, activeFilter) {
        state.analyzed.filter { it.matchesStocksFilter(activeFilter, state.watchedTickers) }.chapteredBySector()
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motionEnabled = rememberMotionEnabled()
    val chapterStarts = remember(visibleChapters) { chapterStartIndices(visibleChapters, ChromeItemCount) }
    val activeChapter by remember(chapterStarts) {
        derivedStateOf { activeChapterAt(listState.firstVisibleItemIndex, chapterStarts) }
    }

    fun jumpTo(sector: String?) {
        val index = chapterStarts[sector] ?: return
        scope.launch { if (motionEnabled) listState.animateScrollToItem(index) else listState.scrollToItem(index) }
    }

    // A Row, not a Box with the rail overlaid: an overlay never takes width away from what sits
    // under it, which is exactly how the rail used to end up drawn over the search field, the
    // filter chips and the sticky sector heading rather than beside them (this task's brief, part
    // 2). Reserving the rail's own [JumpIndexWidth] as a real sibling column means the chrome and
    // every chapter narrow to make room for it instead, on every frame the rail is shown, not just
    // where a chapter happens to end short of the rail's height.
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            state = listState,
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
                    chipRow = chipRow,
                    sectorsExpanded = sectorsExpanded,
                    onToggleSectorsExpanded = { sectorsExpanded = !sectorsExpanded },
                    activeFilter = activeFilter,
                    onFilterSelect = { tapped -> filterKey = if (activeFilter == tapped) null else tapped.toSaveKey() },
                )
            }

            when {
                cold -> item(key = "skeleton") {
                    Column(Modifier.fillMaxWidth()) {
                        AmberSectionHead(title = stringResource(R.string.list_heading_analyzed), colors = colors)
                        SkeletonRows(count = SkeletonRowCount)
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
                            )
                        }
                    }
                }

                // Searching: one flat list across both sets, unchaptered. An uncovered hit draws
                // exactly as it does under "Without analysis", price and vote action included, and
                // opens the same Detail an analyzed hit does. After this pass, search is the only
                // way to reach an uncovered ticker that is not one of the Next-up leaders.
                else -> {
                    itemsIndexed(state.analyzed, key = { _, row -> "a:" + row.ticker }) { index, row ->
                        AnalyzedRow(
                            row = row,
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
                            modifier = groupedRowModifier(
                                colors,
                                isFirst = index == 0 && state.analyzed.isEmpty(),
                                isLast = index == state.withoutAnalysis.lastIndex,
                            ),
                            colors = colors,
                            onOpenDetail = onOpenDetail,
                            onVote = onVote,
                        )
                    }
                }
            }
        }

        if (state.query.isBlank() && visibleChapters.isNotEmpty()) {
            StocksJumpIndex(
                chapters = visibleChapters,
                active = activeChapter,
                onJump = ::jumpTo,
                colors = colors,
                // No BoxScope.align: the outer Row's own verticalAlignment (CenterVertically)
                // centers this sibling column the same way Alignment.CenterEnd used to center it
                // inside the Box, minus the overlap. The rail's own width comes from its
                // [JumpIndexWidth] modifier, unchanged.
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
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
    chipRow: SectorChipRow,
    sectorsExpanded: Boolean,
    onToggleSectorsExpanded: () -> Unit,
    activeFilter: StocksFilter?,
    onFilterSelect: (StocksFilter) -> Unit,
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
        if (state.watched > 0) TodayStrip(text = todayText(state), colors = colors)
        state.banner?.let { StateBanner(banner = it, onRetry = onRetry) }
        Spacer(Modifier.height(SearchTopGap))
        SearchField(query = state.query, onQueryChange = onQueryChange, onClearSearch = onClearSearch)
        if (chipRow.shown.isNotEmpty()) {
            StocksFilterRow(
                trackedCount = trackedCount,
                watchedCount = state.watched,
                chipRow = chipRow,
                expanded = sectorsExpanded,
                onToggleExpanded = onToggleSectorsExpanded,
                active = activeFilter,
                onSelect = onFilterSelect,
                colors = colors,
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
 * The wrapping filter row (docs/design-research-2026-09-21.md section 3): Tracked and Watched
 * first, then up to six sectors, then a disclosure chip past that
 * ([com.plainticker.mobile.ui.stocks.sectorChipRow]'s own default). [FlowRow] rather than a
 * horizontally scrolling [Row] because a horizontally scrolling chip row hides how many sectors
 * exist at all behind an edge a reader has to discover, and because a scrolling row is exactly
 * the "fights the reader's own scroll gesture" trap this task's brief names for the jump index;
 * wrapping keeps every shown chip on screen at once, at the cost of height instead of a hidden
 * edge. [FlowRow]'s own line-wrapping is what keeps this correct at a 1.3x font scale: each
 * [AmberChip] grows with the reader's font size and the row gains a line rather than clipping or
 * scrolling one chip out of reach.
 */
@Composable
private fun StocksFilterRow(
    trackedCount: Int,
    watchedCount: Int,
    chipRow: SectorChipRow,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    active: StocksFilter?,
    onSelect: (StocksFilter) -> Unit,
    colors: AmberColors,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AmberChip(
            // Tracked and Watched read as a label plus a count, not a sentence a plural has to
            // agree with ("Tracked 22", never "22 tracked stocks"), so the count stays a plain
            // string; strings.xml's own comment on stocks_filter_tracked says the same thing.
            label = stringResource(R.string.stocks_filter_tracked, Fmt.count(trackedCount)), // lint-allow count: no noun follows it
            selected = active == StocksFilter.Tracked,
            onClick = { onSelect(StocksFilter.Tracked) },
            colors = colors,
        )
        AmberChip(
            label = stringResource(R.string.stocks_filter_watched, Fmt.count(watchedCount)), // lint-allow count: no noun follows it
            selected = active == StocksFilter.Watched,
            onClick = { onSelect(StocksFilter.Watched) },
            colors = colors,
        )
        val sectors = if (expanded) chipRow.shown + chipRow.overflow else chipRow.shown
        sectors.forEach { sector ->
            AmberChip(
                label = sector,
                selected = active == StocksFilter.Sector(sector),
                onClick = { onSelect(StocksFilter.Sector(sector)) },
                colors = colors,
            )
        }
        if (chipRow.hasOverflow) {
            AmberChip(
                label = if (expanded) {
                    stringResource(R.string.stocks_filter_fewer_sectors)
                } else {
                    pluralStringResource(
                        R.plurals.stocks_filter_more_sectors,
                        chipRow.overflow.size,
                        Fmt.count(chipRow.overflow.size),
                    )
                },
                selected = false,
                onClick = onToggleExpanded,
                colors = colors,
            )
        }
    }
}

// ---- The chapter jump index --------------------------------------------------------------------

/**
 * The chapter jump index (docs/design-research-2026-09-21.md section 3), read live off
 * [chapters] rather than a fixed eleven so a sector with zero rows this week is simply not a
 * target this week (`StocksFilter.kt`'s own doc comment: "a rotating set of sectors" is the whole
 * reason [jumpAbbreviationRes] is a lookup and not an enum ordinal).
 *
 * **What it does, decided rather than guessed.** A tap scrolls to that chapter's own `stickyHeader`
 * ([jumpTo] in the caller); it does not drag-scroll the list the way a fast-scroll letter index
 * on Contacts does, because building a continuous drag gesture across eleven short-lived targets
 * is a second, much larger interaction to get right for a list this much shorter than a phone's
 * contact book, and a tap is the gesture [LazyListState.animateScrollToItem] already exists for.
 * Each entry speaks the sector's own full name as its click label
 * ([R.string.stocks_jump_action]), so the two or three letters on screen are a label for someone
 * who is already looking at the screen, never the only account of what the target is.
 *
 * **It does not compete with a system scrollbar because there is not one to compete with.**
 * Compose's `LazyColumn` draws no OS-style scrollbar or fast-scroll thumb of its own, so the
 * column of taps is not sharing the same 22 to 28dp of screen edge with anything else; it stays
 * a narrow, fixed-width column anyway (rather than growing into a wider drag track) so it keeps
 * reading as a set of discrete labels and not as a slider a reader could mistake for one.
 *
 * **It does compete with the chrome, and used to draw over it.** This composable was, until the
 * device found it, a `Box` overlay: positioned on top of the same-height `LazyColumn` rather than
 * beside it, so it drew over the search field at the top, the filter chips below it and every
 * sticky sector heading, instead of leaving each its own width. The caller ([ListContent]) now
 * lays this out as a real [Row] sibling next to the `LazyColumn`, which the [LazyColumn] gives a
 * `weight(1f)` so the chrome and every chapter narrow by this column's own width whenever it is
 * shown, rather than a fixed width nobody but this composable knows to leave clear.
 *
 * **Whether the rail and [StocksFilterRow]'s sector chips are the same act of navigation twice.**
 * They read a reader's intent differently, not just their pixels differently, and
 * `StocksFilter.kt`'s own doc comment on [StocksFilter.Sector] already draws the line: "the jump
 * index already reaches a sector without narrowing anything (it scrolls, a chip filters)... a
 * sector chip's own job is the thing the jump index cannot do: keep one sector still while the
 * rest fall away." Concretely: the rail is for a reader moving *through* the full order (browsing
 * Health Care, then continuing into Industrials next), where a tap only relocates the scroll
 * position and every other chapter stays one flick away; a chip is for a reader who wants to *stop
 * seeing* every sector but one (narrowing to only Health Care, Tracked, or Watched, and staying
 * there while they read). Eleven rail entries plus up to eleven chips is real interface weight for
 * one screen, but it is two different questions ("where in the order am I" versus "show me only
 * this"), not one question asked twice, so both stay; the fix here is the overlap, not the count.
 *
 * **Touch targets: the device measured this rail at 38 by 32dp, under the app's 48dp floor, and
 * the "eleven 48dp entries would run to roughly 530dp" reasoning a previous pass used to justify
 * that shortcut was wrong about what the rail's own height actually competes with.** This
 * `Column` is a sibling of the full-height `LazyColumn` in the `Row` above ([ListContent]), not
 * nested inside whatever the list's own scrolled content happens to draw, so its natural height
 * is the screen's own content area, not "whatever's left of the list." The device the QA
 * screenshots for this task were taken on is 400 by 890dp; eleven entries at the literal 48dp
 * floor plus ten 4dp gaps between them come to 568dp, which leaves 322dp of that device's own
 * height free even before subtracting a status bar and a navigation bar, both together typically
 * well under 150dp on Android. The premise that eleven 48dp entries do not fit is not true on the
 * device that found this bug, so there is nothing left to trade away: [JumpIndexWidth] and
 * [JumpEntryMinHeight] are both the real, undiminished 48dp floor, the same
 * `minimumInteractiveComponentSize()` every other tap target on this screen already keeps,
 * instead of a smaller carve-out only this rail used to get.
 *
 * **Why this, and not the other three shapes this task's brief names.** A drag-to-scrub target
 * covering the whole rail was not built: the tap-only interaction already reads a reader's intent
 * correctly (see above), a continuous drag gesture is a second interaction to get right with no
 * device here to check it against, and it would trade eleven distinct accessible nodes (each
 * speaking its own sector's full name) for one node that would need custom accessibility actions
 * to say the same thing. Showing fewer entries when there is not room was not built either: the
 * arithmetic above says there is room, so shedding sectors would be giving up reach the frame
 * does not actually require giving up. Removing the rail for the sector chips was not built for
 * the reason [ListContent]'s own doc comment already gives: the rail relocates without narrowing
 * and a chip narrows without relocating, two different reader intents, not one asked twice. So
 * the fix is the plainest one available: the rail was simply drawn smaller than the floor it was
 * always supposed to clear, and it now clears it.
 *
 * **The same width fix closes the font-clipping defect too.** Widening [JumpIndexWidth] from
 * 28dp to 48dp for the touch-target floor happens to give every abbreviation, measured against
 * the real font this rail draws with, comfortable clearance at 1.3x scale as well (see
 * [com.plainticker.mobile.ui.stocks.StocksFilterTest]'s own fontTools measurement against
 * `res/font/bricolage_grotesque.ttf`): "Com", the widest of the eleven, needs 33.790dp at 1.3x,
 * 14.210dp inside the 48dp box. `overflow = TextOverflow.Ellipsis` below is a second, independent
 * backstop for the one case the eleven authored strings cannot cover: an unmapped sector's name
 * taken to three characters by [jumpAbbreviationRes]'s own fallback, which this measurement was
 * never run against. A silent clip is worse than an ellipsis (this task's brief, the fifth
 * clipping-class defect this project has hit); now a label that somehow still does not fit says
 * so instead of dropping a character with nothing to show for it.
 */
@Composable
private fun StocksJumpIndex(
    chapters: List<SectorChapter>,
    active: ActiveChapter?,
    onJump: (String?) -> Unit,
    colors: AmberColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(JumpIndexWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chapters.forEach { chapter ->
            val resId = jumpAbbreviationRes(chapter.sector)
            // A sector outside the known eleven (StocksFilter.kt's own doc comment on
            // jumpAbbreviationRes) still gets a short, if imperfect, label rather than the rail
            // growing to fit its full name.
            val label = if (resId != null) stringResource(resId) else chapter.sector.orEmpty().take(3)
            val fullName = chapter.sector ?: stringResource(R.string.list_heading_no_sector)
            val isActive = active != null && active.sector == chapter.sector
            Text(
                text = label,
                style = AmberType.meta,
                color = if (isActive) colors.textPrimary else colors.textTertiary(AmberSurface.GROUND),
                maxLines = 1,
                // A defensive backstop only, not this pass's real fix (the doc comment above):
                // every authored abbreviation already clears JumpIndexWidth with margin at 1.3x,
                // so this only ever fires for jumpAbbreviationRes's own unmapped-sector fallback.
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = JumpEntryMinHeight)
                    .clickable(
                        onClickLabel = stringResource(R.string.stocks_jump_action, fullName),
                        role = Role.Button,
                        onClick = { onJump(chapter.sector) },
                    )
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * The app's real 48dp touch-target floor, matching every other tap target on this screen; see
 * [StocksJumpIndex]'s own doc comment for why this pass stopped carving out a smaller exception
 * for this rail.
 */
private val JumpIndexWidth = 48.dp
private val JumpEntryMinHeight = 48.dp

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

/** Ticker and company left, the composite as an integer with the row's disclosure line right. */
@Composable
private fun AnalyzedRow(row: ListRow, modifier: Modifier = Modifier, colors: AmberColors, onOpenDetail: (String) -> Unit) {
    AmberTickerRow(
        ticker = row.display,
        company = row.company,
        figure = row.composite?.let { Fmt.decimal(it, decimals = 0) },
        context = rowMeta(row),
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
) {
    VotableAmberRow(
        ticker = row.display,
        company = row.company,
        figure = row.priceUsd?.let { Fmt.price(it) },
        context = rowMeta(row),
        colors = colors,
        modifier = modifier,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
        onVote = if (onVote == null) null else ({ onVote(row.ticker, row.display) }),
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
) {
    Row(
        modifier = modifier.background(colors.surfaceRaised),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmberTickerRow(
            ticker = ticker,
            company = company,
            figure = figure,
            context = context,
            colors = colors,
            onClick = onClick,
            onClickLabel = onClickLabel,
            modifier = Modifier.weight(1f),
        )
        if (onVote != null) {
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
    // The strip as the leaders would read: the measured stake of 2026-09-13 and the median stake.
    nextUp = listOf(NextUpRow("TSM", "31209870777", 3), NextUpRow("ASML", "6719000000", 1)),
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
