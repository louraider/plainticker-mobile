package com.plainticker.mobile.ui.today

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today in direction A, "One line, then yours", pinned from source the way
 * [com.plainticker.mobile.ui.watchlist.WatchlistScreenTest] and
 * [com.plainticker.mobile.ui.components.AmberTickerRowTest] pin theirs: no plain-JVM layout test
 * can measure a real render, so what is pinned is the block order in both states, what each block
 * is gated on, which Amber components draw it, that no new slot is squeezed (DESIGN.md 5.4), and
 * that the venue is recomputed on resume and stopped on pause.
 */
class TodayScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val raw: String by lazy {
        File(module, "src/main/java/com/plainticker/mobile/ui/today/TodayScreen.kt").readText()
    }

    private val source: String by lazy { KotlinScan(raw).code }

    private fun body(function: String, until: String): String {
        val start = source.indexOf(function)
        assertTrue("TodayScreen.kt has no $function", start >= 0)
        val end = source.indexOf(until, start)
        assertTrue("TodayScreen.kt has no $until after $function", end > start)
        return source.substring(start, end)
    }

    private val content: String get() = body("internal fun TodayContent(", "private fun TodayStatusLine(")

    // ---- The order, returning and first open ----------------------------------------------------------

    @Test
    fun `returning, the blocks run status, watched, digest, reports this week, next up`() {
        val order = listOf("TodayStatusLine(", "TodayWatchedBlock(", "TodayDigestLine(", "TodayReportsBlock(", "TodayNextUpBlock(")
            .map { content.indexOf(it) }
        assertTrue("every block is drawn from TodayContent: $order", order.all { it >= 0 })
        assertEquals("in the order direction A draws", order.sorted(), order)
    }

    @Test
    fun `first open swaps Watched and the digest for the start block, and drops Next up`() {
        val branch = content.substring(content.indexOf("if (firstOpen) {"), content.indexOf("TodayReportsBlock("))
        assertTrue("the start block is the first-open branch", "TodayStartBlock(" in branch.substringBefore("} else {"))
        assertFalse("no Watched block on a first open", "TodayWatchedBlock(" in branch.substringBefore("} else {"))
        assertFalse("no digest line on a first open", "TodayDigestLine(" in branch.substringBefore("} else {"))
        assertTrue("Next up is gated on not being a first open", "if (!firstOpen) {" in content)
        val nextUpGate = content.indexOf("if (!firstOpen) {")
        assertTrue(nextUpGate < content.indexOf("TodayNextUpBlock("))
    }

    @Test
    fun `Watch on a report row exists only on a first open, and never alongside the watched marker`() {
        assertTrue("onWatch = if (firstOpen) onWatch else null" in content)
        val reports = body("private fun TodayReportsBlock(", "private fun TodayNextUpBlock(")
        assertTrue(
            "the action is AmberTickerRow's own trailing action, the pairing its budget proves",
            "trailingAction = onWatch?.takeIf { !watched }?.let" in reports,
        )
        assertTrue(
            "a watched ticker never gets a Watch action: the two are mutually exclusive by construction",
            "onTrailingAction = onWatch?.takeIf { !watched }?.let" in reports,
        )
    }

    @Test
    fun `the start block offers one primary action and nothing else to press`() {
        val start = body("private fun TodayStartBlock(", "private fun TodayWatchedBlock(")
        assertEquals(1, Regex("AmberPrimaryAction\\(").findAll(start).count())
        assertFalse("TextAction(" in start)
        assertFalse("AmberSecondaryAction(" in start)
    }

    // ---- What was removed stays removed ----------------------------------------------------------------

    @Test
    fun `no footer count, no venue card, no per-row NYSE close, no notifications line on Today`() {
        assertFalse("the footer count left Today; Stocks' own segments carry it", "footerCopy" in source || "today_footer_count" in raw)
        assertFalse("the 120dp status card is gone", "AmberFigure(" in source)
        assertFalse("the figure's meaning is said once, not per row", "today_tracked_context" in raw || "list_row_meta_premium" in raw)
        assertFalse("the notifications line lives in You and on the digest screen", "watchlist_notifications" in raw || "action_enable" in raw)
        assertFalse("Today draws its own list now, not the Watchlist screen's content", "WatchlistContent(" in source)
    }

    // ---- Gates: nothing is said before it is known -------------------------------------------------------

    @Test
    fun `the status line is undrawn while the venue is not yet known`() {
        val fn = body("private fun TodayStatusLine(", "private fun TodayStartBlock(")
        assertTrue("statusLine(state.market, state.nowMillis, zone) ?: return" in fn)
    }

    /**
     * "Reports this week" replaced "Tracked today" (founder's pick of option B, 2026-09-26; see
     * TodayScreen.kt's own class doc and DESIGN.md 5.3). Unlike the retired block, this one reads
     * `/summary`'s own `next_report_date`/`next_report_confirmed` fields directly and needs no
     * price at all, so it gates on [state.todayLoading] and [state.reportsKnown] rather than on a
     * price fetch: the old test's premise ("gate on the price fetch, not todayLoading") no longer
     * holds because there is no price-gated half left for this block to wait behind. Reused from
     * the retired test: the block is undrawn (not merely empty) when nothing is known at all, and
     * the count beside the heading is never a guess while still loading.
     */
    @Test
    fun `reports this week is undrawn while the server field is unknown, and the skeleton gates on todayLoading only`() {
        val fn = body("private fun TodayReportsBlock(", "private fun TodayNextUpBlock(")
        assertTrue("reportsThisWeek(state.reports, today)" in fn)
        assertTrue(
            "undrawn entirely before the server has sent a date for anyone: showing an empty state " +
                "here could be a false 'quiet week' rather than an undeployed field",
            "if (!state.todayLoading && !state.reportsKnown) return" in fn,
        )
        assertTrue("the count is null, never a guess, while still loading", "if (state.todayLoading) null else Fmt.count(thisWeek.size)" in fn)
        assertTrue("the skeleton shows only while todayLoading is out", "if (state.todayLoading) {" in fn && "SkeletonTickerRows(" in fn)
        assertTrue(
            "the rest is handed to Stocks through the honest, argument-less link: Stocks cannot " +
                "sort or filter by report date, so it never claims a filtered count",
            "label = reportsAllCopy().text()" in fn,
        )
    }

    @Test
    fun `a watched ticker is marked in the figure slot, and the empty week's message replaces the lede`() {
        val fn = body("private fun TodayReportsBlock(", "private fun TodayNextUpBlock(")
        assertTrue("a watched ticker is marked, never dropped, unlike the retired Tracked block", "reportRowWatched(row.ticker, state.watchedTickers)" in fn)
        assertTrue("the marker draws in the figure slot", "figure = if (watched) stringResource(R.string.today_reports_watched) else null" in fn)
        assertTrue("estimated joins the date with one middle dot, list_row_meta_join's own pattern", "list_row_meta_join" in fn)
        assertTrue(
            "a quiet week's message replaces the lede rather than adding a second sentence",
            "reportsEmptyCopy(nextReportAfterThisWeek(state.reports, today), nextWeek)" in fn,
        )
    }

    @Test
    fun `the weekend moves the reports block to next week, heading and empty state alike`() {
        val fn = body("private fun TodayReportsBlock(", "private fun TodayNextUpBlock(")
        assertTrue("the window itself moves on the reader's own Saturday or Sunday", "val nextWeek = reportsIsNextWeek(today)" in fn)
        assertTrue(
            "the heading follows the same switch, never a separate, driftable condition",
            "title = stringResource(if (nextWeek) R.string.today_heading_reports_next else R.string.today_heading_reports)" in fn,
        )
    }

    @Test
    fun `watched rows carry one figure each, and a thin pool's note instead of a figure`() {
        val fn = body("private fun TodayWatchedBlock(", "private fun TodayDigestLine(")
        assertTrue("todayWatchRow(" in fn)
        assertTrue("figure = row.figure" in fn)
        assertTrue("the pool note joins the report line", "row.poolNote?.let" in fn)
        assertTrue("the figure's meaning is the section's lede, said once", "lede = figureMeaning(state.market).text()" in fn)
        assertFalse("no Unwatch on Today: it is on the stock's own page", "trailingAction" in fn)
    }

    @Test
    fun `next up is undrawn only with no round and no leader, and names the round in the reader's time`() {
        val fn = body("private fun TodayNextUpBlock(", "private fun TodayHoursSheet(")
        assertTrue("if (leader == null && round == null) return" in fn)
        assertTrue("nextUpLede(round, zone)" in fn)
        // An open round nobody voted in still draws (judges' round 2), with the way to Vote.
        val empty = fn.substringAfter("} else if (round != null) {")
        assertTrue("the empty-round sentence", "nextUpEmpty(round)" in empty)
        assertTrue("and the action to Vote", "onClick = onOpenVote" in empty)
    }

    @Test
    fun `the digest line links to the digest only when there is one to read`() {
        val fn = body("private fun TodayDigestLine(", "private fun TodayReportsBlock(")
        assertTrue("onOpenDigest != null && digestReadable(state.digest)" in fn)
    }

    // ---- The clock: recomputed on resume, stopped on pause ------------------------------------------------

    @Test
    fun `the venue is recomputed on every resume and the boundary job stops on pause`() {
        val fn = body("fun TodayScreen(", "internal fun TodayContent(")
        assertTrue("LifecycleResumeEffect(watchlistViewModel)" in fn)
        assertTrue("watchlistViewModel.onResume()" in fn)
        assertTrue("onPauseOrDispose { watchlistViewModel.onPause() }" in fn)
    }

    @Test
    fun `a pull refreshes Today through the ViewModel, and a banner Retry does not draw the pull`() {
        val fn = body("fun TodayScreen(", "internal fun TodayContent(")
        assertTrue("onRefresh = { watchlistViewModel.refresh(userAsked = true) }" in fn)
        assertTrue("onRetry = { watchlistViewModel.refresh() }" in fn)
        assertTrue("PullToRefreshBox(" in content)
        assertTrue("isRefreshing = state.refreshing" in content)
    }

    @Test
    fun `the first watch asks for notifications once, the same way Detail's Watch does`() {
        val fn = body("fun TodayScreen(", "internal fun TodayContent(")
        assertTrue("val ask = watchlistViewModel.watch(ticker)" in fn)
        assertTrue("askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)" in fn)
    }

    @Test
    fun `the status line opens the market hours sheet, and the sheet carries the ages and the calendar note`() {
        val status = body("private fun TodayStatusLine(", "private fun TodayStartBlock(")
        assertTrue("clickable(role = Role.Button" in status)
        val sheet = body("private fun TodayHoursSheet(", "private val Side")
        assertTrue("AmberSheet(" in sheet)
        assertTrue("freshnessSentence(" in sheet)
        assertTrue("if (statusFromCalendar(state.market))" in sheet)
    }

    // ---- The clipping rule (DESIGN.md 5.4) on every new slot ----------------------------------------------

    @Test
    fun `the status line owns its row and wraps, never forced to one line or a fixed width`() {
        val fn = body("private fun TodayStatusLine(", "private fun TodayStartBlock(")
        assertFalse("maxLines" in fn)
        assertTrue("the text is the flexible sibling of a fixed 3dp bar", "modifier = Modifier.weight(1f)" in fn)
        assertFalse("no width but the bar's own 3dp", Regex("\\.width\\((?!3\\.dp|10\\.dp)").containsMatchIn(fn))
    }

    @Test
    fun `the digest sentence is the weighted sibling of a short fixed action`() {
        val fn = body("private fun TodayDigestLine(", "private fun TodayReportsBlock(")
        assertTrue("modifier = Modifier.weight(1f)" in fn)
        assertFalse("maxLines" in fn)
        assertFalse(".width(" in fn)
    }

    @Test
    fun `every block reuses the Amber components, never a re-drawn equivalent`() {
        assertTrue("AmberSectionHead(" in source)
        assertTrue("AmberTickerRow(" in source)
        assertTrue("AmberTickerRowGroup(" in source)
        assertTrue("AmberPrimaryAction(" in source)
        assertTrue("AmberSheet(" in source)
    }

    // ---- The orchestrated moment ----------------------------------------------------------------------------

    @Test
    fun `status, reports this week and next up settle in, staggered in block order`() {
        assertTrue("amberBlockEntrance(step = StatusEntranceStep)" in body("private fun TodayStatusLine(", "private fun TodayStartBlock("))
        assertTrue("amberBlockEntrance(step = ReportsEntranceStep)" in body("private fun TodayReportsBlock(", "private fun TodayNextUpBlock("))
        assertTrue("amberBlockEntrance(step = NextUpEntranceStep)" in body("private fun TodayNextUpBlock(", "private fun TodayHoursSheet("))
        val order = listOf("StatusEntranceStep = 0", "ReportsEntranceStep = 1", "NextUpEntranceStep = 2").map { source.indexOf(it) }
        assertTrue(order.all { it >= 0 })
        assertEquals(order, order.sorted())
    }

    @Test
    fun `the reader's own rows and the start block never stagger`() {
        assertFalse("amberBlockEntrance" in body("private fun TodayWatchedBlock(", "private fun TodayDigestLine("))
        assertFalse("amberBlockEntrance" in body("private fun TodayStartBlock(", "private fun TodayWatchedBlock("))
        assertFalse("amberBlockEntrance" in content)
    }

    @Test
    fun `the entrance settles once per block, not once per row`() {
        val reports = body("private fun TodayReportsBlock(", "private fun TodayNextUpBlock(")
        assertEquals(1, Regex("amberBlockEntrance\\(").findAll(reports).count())
    }

    @Test
    fun `the entrance is gated by rememberMotionEnabled and springs in when motion is on`() {
        val fn = body("private fun Modifier.amberBlockEntrance(", "private val EntranceSpring")
        assertTrue("rememberMotionEnabled" in fn)
        assertTrue("EntranceSpring" in fn)
        assertTrue("targetValue = if (settled) 1f else 0f" in fn)
    }

    /**
     * The animator-zero stall's second half (docs/qa-checklist.md, 2026-09-22; the ViewModel-level
     * half is [com.plainticker.mobile.ui.watchlist.WatchlistViewModelTest]'s own regression test).
     * `animateFloatAsState` still needs a platform frame to apply even a `snap()` spec, and a cold
     * device with every animator scale at 0 does not reliably deliver one before a scroll forces
     * it, so motion off must never enter that machinery at all: the modifier has to come back
     * unchanged, before `remember` or `LaunchedEffect` run, so the block is at its settled state in
     * the same composition pass that first draws it rather than one the animation clock owes it
     * later.
     */
    @Test
    fun `motion off returns the block unchanged, before any remember, effect or animation runs`() {
        val fn = body("private fun Modifier.amberBlockEntrance(", "private val EntranceSpring")
        val functionOpenBrace = fn.indexOf('{')
        val guard = fn.indexOf("if (!motionEnabled) return this")
        assertTrue("the guard exists", guard >= 0)
        assertTrue(
            "nothing but whitespace sits between the function's opening brace and the guard",
            fn.substring(functionOpenBrace + 1, guard).isBlank(),
        )
        val remember = fn.indexOf("remember {")
        val launchedEffect = fn.indexOf("LaunchedEffect(")
        val animateFloatAsState = fn.indexOf("animateFloatAsState(")
        assertTrue("no remembered state is created before the motion-off guard", guard in 0..<remember)
        assertTrue("no effect runs before the motion-off guard", guard in 0..<launchedEffect)
        assertTrue("no animation is started before the motion-off guard", guard in 0..<animateFloatAsState)
        assertFalse("motion off must not fall back to snap() on the animation clock", "snap()" in fn)
    }

    @Test
    fun `the settle is a spring with no bounce, so alpha and translation cannot overshoot`() {
        assertTrue("private val EntranceSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy" in source)
    }

    // ---- Device QA of 1.3.18 --------------------------------------------------------------------

    @Test
    fun `next up's row reads Voted through the shared rule, off the home entry's vote model`() {
        val block = body("private fun TodayNextUpBlock(", "private fun TodayHoursSheet(")
        assertTrue("the shared round rule decides", "nextUpVoted(leader, votedTickers)" in block)
        assertTrue("the same quiet word the Vote tab draws", "R.string.vote_voted_row" in block)
        assertTrue("Today reads the vote model's own set", "voteViewModel?.votedTickers" in source)
        val home = KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/home/HomeScreen.kt").readText()).code
        val today = home.substring(home.indexOf("TodayScreen("), home.indexOf("AmberDestination.STOCKS ->"))
        assertTrue("HomeScreen hands Today the home entry's VoteViewModel", "voteViewModel = viewModel(factory = factory)" in today)
    }

    @Test
    fun `reports and watched skeletons stand inside the card the rows will fill`() {
        assertTrue("SkeletonTickerRows(count = ReportsPreviewCount" in source)
        assertTrue("SkeletonTickerRows(count = minOf(state.watched, ColdRowCap)" in source)
        assertFalse("no bare skeleton rows outside a card", "SkeletonRows(" in source)
    }
}
