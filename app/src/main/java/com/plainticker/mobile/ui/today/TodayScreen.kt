package com.plainticker.mobile.ui.today

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberSheet
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.rememberMotionEnabled
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.watchlist.WatchlistUiState
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel
import com.plainticker.mobile.ui.watchlist.bannerText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Today, direction A, "One line, then yours" (the founder's pick of three, 2026-09-24; the
 * comparison page and its diagnosis sit with that decision). A tester called the previous Today
 * cluttered, unclear, stale and dominated by a huge banner; every one of those was true, and each
 * block below answers one of them.
 *
 * Top to bottom, returning:
 * 1. **The status line** ([TodayStatusLine]): the venue in one wrapping line, in the reader's own
 *    time, replacing a 120dp card with a 34sp word in it. Tap it for the market hours sheet
 *    ([TodayHoursSheet]), where the data ages and the calendar note live.
 * 2. **Watched** ([TodayWatchedBlock]): the reader's own stocks first, one figure each, the
 *    figure's meaning said once in the lede instead of "vs NYSE close" on every row. A thin pool
 *    states itself instead of a figure. Then the digest as one line with "Read it"
 *    ([TodayDigestLine]), which opens the digest screen under You.
 * 3. **Reports this week** ([TodayReportsBlock], the founder's pick of option B off the designer's
 *    page, 2026-09-26, replacing "Tracked today": a beginner reading NVDAx at +0.21% had no way to
 *    tell what "tracked" meant or why the list was there): up to [ReportsPreviewCount] covered
 *    companies whose next report falls this week, soonest first, a watched ticker marked rather
 *    than left out, then a plain pointer to Stocks (which cannot sort or filter by report date, so
 *    the link says so by not promising a filtered count). Undrawn entirely before the server has
 *    sent a report date for anyone at all, never a broken list ([WatchlistUiState.reportsKnown]'s
 *    own doc has the reasoning). On the reader's own Saturday or Sunday the window and the heading
 *    both move to next week ([reportsIsNextWeek], caught on the real device on Saturday 26 Sep
 *    2026: "today through the coming Sunday" is only Saturday and Sunday themselves on a Saturday,
 *    empty every single weekend).
 * 4. **Next up** ([TodayNextUpBlock]): one row, opening Vote.
 *
 * First open (nothing watched) swaps block 2 for [TodayStartBlock], one primary action, "Find a
 * stock", and gives every report row a Watch (never true at once with the "already watched" marker,
 * since a first open's watched set is always empty), so the first real step can happen on this
 * screen. There is no digest line, no notifications line and no Next up on a first open: one goal
 * per session. The notification permission is asked once, right after the first watch, the same
 * rule Detail's Watch keeps; the setting itself lives in You and on the digest screen.
 *
 * **The status is a clock.** [WatchlistViewModel.onResume] recomputes it every time Today comes
 * back and keeps it current at every open and close while Today stays on screen;
 * [WatchlistViewModel.onPause] stops that. [com.plainticker.mobile.repo.MarketClock] has the bug
 * this replaces.
 *
 * **Motion.** One orchestrated moment, as before: the status line, "Reports this week" and Next up
 * settle in with the no-bounce spring, staggered 40ms ([amberBlockEntrance]). The reader's own rows
 * and the start block stay still: a stagger belongs to a block, never to a per-ticker list, and
 * nothing here needs motion to be legible (the smoke script runs at animator scale 0).
 */
@Composable
fun TodayScreen(
    watchlistViewModel: WatchlistViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseStocks: (() -> Unit)? = null,
    onRunCheck: (() -> Unit)? = null,
    /** Next up's row opens Vote. Null draws the row without a click target. */
    onOpenVote: (() -> Unit)? = null,
    /** The digest line's "Read it": the digest screen under You. Null draws the line without it. */
    onOpenDigest: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val state by watchlistViewModel.state.collectAsStateWithLifecycle()
    var hoursOpen by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    // The same one-time ask Detail's Watch makes: after the first watch, and never again.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Resumed: the venue is recomputed now and at every boundary; paused: nothing runs.
    LifecycleResumeEffect(watchlistViewModel) {
        watchlistViewModel.onResume()
        onPauseOrDispose { watchlistViewModel.onPause() }
    }

    TodayContent(
        state = state,
        zone = zone,
        onOpenDetail = onOpenDetail,
        onOpenHours = { hoursOpen = true },
        onFindStock = onBrowseStocks,
        onWatch = { ticker ->
            val ask = watchlistViewModel.watch(ticker)
            if (ask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRetry = watchlistViewModel::refresh,
        onOpenVote = onOpenVote,
        onOpenDigest = onOpenDigest,
        onRunCheck = onRunCheck,
        modifier = modifier,
        header = header,
    )

    if (hoursOpen) {
        TodayHoursSheet(state = state, onDismiss = { hoursOpen = false })
    }
}

/** The whole screen, one list: header, status, then either the start block or Watched, Reports this week, Next up. */
@Composable
internal fun TodayContent(
    state: WatchlistUiState,
    zone: ZoneId,
    onOpenDetail: (String) -> Unit,
    onOpenHours: () -> Unit,
    onFindStock: (() -> Unit)?,
    onWatch: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenVote: (() -> Unit)?,
    onOpenDigest: (() -> Unit)?,
    onRunCheck: (() -> Unit)?,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val colors = defaultAmberColors()
    val firstOpen = state.isEmpty
    // The reader's own calendar day, off the same nowMillis/zone every other reader-time sentence
    // on this screen reads: "Reports this week"'s own window is the reader's local week, never New
    // York's (com.plainticker.mobile.ui.today.reportsThisWeek's own doc comment has the rule).
    val today = remember(state.nowMillis, zone) { Instant.ofEpochMilli(state.nowMillis).atZone(zone).toLocalDate() }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.surfaceGround),
        // The content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "status") { TodayStatusLine(state = state, zone = zone, onOpenHours = onOpenHours, colors = colors) }
        state.banner?.let { banner ->
            item(key = "banner") {
                Banner(
                    text = bannerText(banner).text(),
                    action = stringResource(R.string.action_retry),
                    onAction = onRetry,
                    modifier = Modifier.padding(top = BlockGap),
                    colors = colors,
                )
            }
        }
        if (firstOpen) {
            item(key = "start") { TodayStartBlock(onFindStock = onFindStock, colors = colors) }
        } else {
            item(key = "watched") {
                TodayWatchedBlock(state = state, onOpenDetail = onOpenDetail, colors = colors)
            }
            item(key = "digest") { TodayDigestLine(state = state, zone = zone, onOpenDigest = onOpenDigest, colors = colors) }
        }
        item(key = "reports") {
            TodayReportsBlock(
                state = state,
                today = today,
                onOpenDetail = onOpenDetail,
                onBrowseStocks = onFindStock,
                onWatch = if (firstOpen) onWatch else null,
                colors = colors,
            )
        }
        if (!firstOpen) {
            item(key = "next-up") { TodayNextUpBlock(state = state, zone = zone, onOpenVote = onOpenVote) }
        }
        // Debug builds only, behind the same gate as the component gallery.
        onRunCheck?.let { run ->
            item(key = "debug-run") {
                Row(Modifier.fillMaxWidth().padding(horizontal = Side), horizontalArrangement = Arrangement.End) {
                    TextAction(label = stringResource(R.string.debug_run_watchlist_check), onClick = run, color = colors.actionText)
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(EndGap)) }
    }
}

/**
 * The venue, one line. A 3dp bar leads it, lit (actionText) only while the exchange's own session
 * is on; the state phrase, everything up to the first period, is set in weight 600 so it reads
 * first. The line wraps rather than clipping: it owns its whole row and has no sibling to share a
 * budget with (DESIGN.md section 5.4), which is why it carries no `maxLines`. Undrawn until a
 * catalog has answered: no sentence about a venue this screen has not read.
 */
@Composable
private fun TodayStatusLine(state: WatchlistUiState, zone: ZoneId, onOpenHours: () -> Unit, colors: AmberColors) {
    val copy = statusLine(state.market, state.nowMillis, zone) ?: return
    val text = copy.text()
    val cut = text.indexOf('.').let { if (it < 0) text.length else it + 1 }
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold)) { append(text.substring(0, cut)) }
        append(text.substring(cut))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .amberBlockEntrance(step = StatusEntranceStep)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.today_hours_open_label), onClick = onOpenHours)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = Side, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 3.dp)
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (statusLive(state.market)) colors.actionText else colors.textTertiary(AmberSurface.GROUND)),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = line, style = AmberType.context, color = colors.textSecondary, modifier = Modifier.weight(1f))
    }
}

/** First open: what watching gives, and the one step to take. */
@Composable
private fun TodayStartBlock(onFindStock: (() -> Unit)?, colors: AmberColors) {
    Column(Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 28.dp)) {
        Text(text = stringResource(R.string.today_start_title), style = AmberType.sectionHead, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.today_start_body), style = AmberType.body, color = colors.textSecondary)
        if (onFindStock != null) {
            Spacer(Modifier.height(20.dp))
            AmberPrimaryAction(label = stringResource(R.string.action_find_stock), onClick = onFindStock, colors = colors)
        }
    }
}

/** Watched: the reader's own stocks, one figure each, the figure's meaning said once. Still, never staggered. */
@Composable
private fun TodayWatchedBlock(state: WatchlistUiState, onOpenDetail: (String) -> Unit, colors: AmberColors) {
    Column(Modifier.fillMaxWidth().padding(top = SectionGap)) {
        AmberSectionHead(
            title = stringResource(R.string.watchlist_heading_watched),
            meta = Fmt.count(state.watched),
            lede = figureMeaning(state.market).text(),
            colors = colors,
        )
        if (state.isCold) {
            SkeletonRows(count = minOf(state.watched, ColdRowCap), colors = colors)
        } else {
            AmberTickerRowGroup(colors = colors) {
                state.rows.forEach { ticker ->
                    val row = todayWatchRow(ticker, state.analysisUnavailable)
                    val report = row.report.text()
                    val context = row.poolNote?.let { stringResource(R.string.list_row_meta_join, report, it.text()) } ?: report
                    AmberTickerRow(
                        ticker = row.symbol,
                        company = row.company,
                        figure = row.figure,
                        context = context,
                        colors = colors,
                        onClick = { onOpenDetail(row.ticker) },
                        onClickLabel = stringResource(R.string.action_open_ticker, row.symbol),
                    )
                }
            }
        }
    }
}

/**
 * The digest, folded to one line: when the last one landed, in the reader's own time, and "Read
 * it". The sentence is the weighted, wrapping sibling; "Read it" is short and fixed, so it can
 * never starve the sentence (DESIGN.md section 5.4).
 */
@Composable
private fun TodayDigestLine(state: WatchlistUiState, zone: ZoneId, onOpenDigest: (() -> Unit)?, colors: AmberColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = digestLink(state.digest, state.nowMillis, zone).text(),
            style = AmberType.context,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (onOpenDigest != null && digestReadable(state.digest)) {
            TextAction(label = stringResource(R.string.action_read_it), onClick = onOpenDigest, color = colors.actionText)
        }
    }
}

/**
 * "Reports this week" (the founder's pick of option B off the designer's page, 2026-09-26,
 * replacing "Tracked today"): up to [ReportsPreviewCount] covered companies whose next report
 * falls this week ([reportsThisWeek]), soonest first, a watched ticker marked in the [figure] slot
 * rather than left out, then a plain pointer to Stocks (which cannot sort or filter by report date,
 * so the link never claims a filtered count).
 *
 * **Undrawn entirely**, header included, once settled with the server field not yet known
 * ([WatchlistUiState.reportsKnown] false): this app cannot tell "no covered company reports this
 * week" apart from "`next_report_date` has not deployed, every row is null," and showing an empty
 * state that might be a false "quiet week" is worse than showing nothing (task brief: "do not show
 * a broken list"). A skeleton draws only while `todayLoading` is still out, the same gate the
 * retired Tracked block's own `SkeletonRows` used, now correct rather than coincidental: this block
 * needs nothing prices answer at all (`next_report_date`/`next_report_confirmed` ride on
 * `/summary`, the fast half's own call), so there is no second, price-gated loading state left to
 * confuse it with (the animator-zero stall, docs/qa-checklist.md 2026-09-22, is exactly that
 * confusion and must not recur).
 *
 * **The empty state lives in the lede**, not a second sentence below it: a quiet week says so in
 * place of the usual "Companies publish results..." line, with the next known date after this week
 * closes when one exists ([reportsEmptyCopy]), matching the designer's own page ("No covered
 * company reports this week. The next one is Micron, on Wednesday 7 Oct.") exactly.
 *
 * **Saturday and Sunday move the whole block to next week**, heading and empty state alike
 * ([reportsIsNextWeek], [reportsThisWeek]'s own doc comment has the reasoning off the real device
 * that caught this: a "this week" window that is only ever Saturday and Sunday themselves read
 * empty every weekend, exactly when a reader has time to look). The row list itself keeps every
 * other rule unchanged: the same cap, sort, estimated marker, watched marker and Watch action, just
 * over next week's seven days instead of this week's.
 *
 * On a first open each row carries Watch instead of the watched marker (the two are never true at
 * once: a first open's watched set is always empty): the row's meta line holds the context and the
 * action, the pairing [AmberTickerRow]'s own budget proves for Vote's leader row, and
 * [TodayModelTest] re-proves for this one.
 */
@Composable
private fun TodayReportsBlock(
    state: WatchlistUiState,
    today: LocalDate,
    onOpenDetail: (String) -> Unit,
    onBrowseStocks: (() -> Unit)?,
    onWatch: ((String) -> Unit)?,
    colors: AmberColors,
) {
    if (!state.todayLoading && !state.reportsKnown) return
    val nextWeek = reportsIsNextWeek(today)
    val thisWeek = reportsThisWeek(state.reports, today)
    val empty = !state.todayLoading && thisWeek.isEmpty()
    val lede = if (empty) {
        reportsEmptyCopy(nextReportAfterThisWeek(state.reports, today), nextWeek).text()
    } else {
        stringResource(R.string.today_reports_lede)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = SectionGap).amberBlockEntrance(step = ReportsEntranceStep)) {
        AmberSectionHead(
            title = stringResource(if (nextWeek) R.string.today_heading_reports_next else R.string.today_heading_reports),
            meta = if (state.todayLoading) null else Fmt.count(thisWeek.size),
            lede = lede,
            colors = colors,
        )
        if (state.todayLoading) {
            SkeletonRows(count = ReportsPreviewCount, colors = colors)
        } else if (!empty) {
            AmberTickerRowGroup(colors = colors) {
                thisWeek.take(ReportsPreviewCount).forEach { row ->
                    val watched = reportRowWatched(row.ticker, state.watchedTickers)
                    val dateText = row.dateLabel
                    val context = if (row.estimated) {
                        stringResource(R.string.list_row_meta_join, dateText, stringResource(R.string.today_reports_estimated))
                    } else {
                        dateText
                    }
                    AmberTickerRow(
                        ticker = row.display,
                        company = row.company,
                        context = context,
                        figure = if (watched) stringResource(R.string.today_reports_watched) else null,
                        trailingAction = onWatch?.takeIf { !watched }?.let { stringResource(R.string.action_watch) },
                        onTrailingAction = onWatch?.takeIf { !watched }?.let { watch -> { watch(row.ticker) } },
                        colors = colors,
                        onClick = { onOpenDetail(row.ticker) },
                        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
                    )
                }
            }
            if (onBrowseStocks != null) {
                TextAction(
                    label = reportsAllCopy().text(),
                    onClick = onBrowseStocks,
                    color = colors.actionText,
                    modifier = Modifier.padding(start = Side - LinkInset),
                )
            }
        }
    }
}

/** Next up: one row, the vote leader, the round's close in the reader's own time. Undrawn with no leader. */
@Composable
private fun TodayNextUpBlock(state: WatchlistUiState, zone: ZoneId, onOpenVote: (() -> Unit)?) {
    val leader = state.nextUpLeader ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).amberBlockEntrance(step = NextUpEntranceStep)) {
        AmberSectionHead(
            title = stringResource(R.string.today_next_up_title),
            lede = nextUpLede(state.voteRound, zone)?.text(),
        )
        AmberTickerRowGroup {
            AmberTickerRow(
                ticker = leader.display,
                company = leader.company,
                figure = leader.weight.text(),
                context = leader.votersContext.text(),
                onClick = onOpenVote,
                onClickLabel = stringResource(R.string.action_open_ticker, leader.display),
            )
        }
    }
}

/**
 * The market hours sheet the status line opens: what the hours mean for a token, how old the data
 * is, and, only while the line reads the calendar, why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayHoursSheet(state: WatchlistUiState, onDismiss: () -> Unit) {
    val colors = defaultAmberColors()
    AmberSheet(onDismissRequest = onDismiss, colors = colors) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.today_hours_title), style = AmberType.sectionHead, color = colors.textPrimary)
            Text(text = stringResource(R.string.today_hours_body), style = AmberType.body, color = colors.textSecondary)
            freshnessSentence(state.analysisGeneratedAtMillis, state.pricesFetchedAtMillis, state.nowMillis)?.let {
                Text(text = it.text(), style = AmberType.context, color = colors.textSecondary)
            }
            if (statusFromCalendar(state.market)) {
                Text(text = stringResource(R.string.today_hours_calendar), style = AmberType.context, color = colors.textSecondary)
            }
        }
    }
}

private val Side = 16.dp
private val SectionGap = 8.dp
private val BlockGap = 8.dp
private val EndGap = 24.dp

/** TextAction's own 16dp start padding, taken back so "All N in Stocks" lines up with the section head. */
private val LinkInset = 16.dp
private const val ColdRowCap = 3

/**
 * The settle this screen's class doc names: a block fades in and rises [EntranceRise] into place,
 * once, the first time it has something to draw, staggered by [step] positions of
 * [StaggerStepMillis] each. Damping is [Spring.DampingRatioNoBouncy] on purpose: a settle, not a
 * bounce, since a bouncy alpha can overshoot past fully opaque and read as a flicker.
 *
 * **Motion off returns [this] untouched, before `remember` or `LaunchedEffect` ever run.** The
 * animator-zero stall (docs/qa-checklist.md, 2026-09-22) was traced past the data-loading fix in
 * [com.plainticker.mobile.ui.watchlist.WatchlistViewModel.loadToday] to this function itself: even
 * with `animationSpec = snap()`, [animateFloatAsState] still routes through [Animatable]'s frame
 * clock, so the settled value only lands "on the next frame" the platform delivers one, and with
 * all three animator scales at 0 a cold Seeker sometimes does not schedule that frame until a
 * scroll or other input forces one, leaving the block on its unsettled first value (alpha 0,
 * translated) indefinitely. No animation object, no coroutine and no frame pulse is on this path
 * when motion is off, so the block is drawn at its settled state in the very same composition pass
 * that first draws it, the same guarantee every other read on this screen already has.
 */
@Composable
private fun Modifier.amberBlockEntrance(step: Int, motionEnabled: Boolean = rememberMotionEnabled()): Modifier {
    if (!motionEnabled) return this
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(step * StaggerStepMillis)
        settled = true
    }
    val progress by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = EntranceSpring,
        label = "today-block-entrance-$step",
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * EntranceRise.toPx()
    }
}

private val EntranceSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private const val StaggerStepMillis = 40L
private val EntranceRise = 8.dp

/** Stagger order: the status line, Reports this week, Next up. The reader's own rows stay still. */
private const val StatusEntranceStep = 0
private const val ReportsEntranceStep = 1
private const val NextUpEntranceStep = 2
