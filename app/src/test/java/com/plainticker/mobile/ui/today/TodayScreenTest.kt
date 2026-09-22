package com.plainticker.mobile.ui.today

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today's four built blocks (docs/design-research-2026-09-21.md section 3), pinned from source the
 * way [com.plainticker.mobile.ui.watchlist.WatchlistScreenTest] and
 * [com.plainticker.mobile.ui.components.AmberTickerRowTest] pin theirs: there is no layout test on a
 * plain JVM that can measure a real render, so what is pinned is that the required components are
 * used, the blocks compose in the research's order, and the two variable slots this file adds
 * (the footer count, the venue card's context) are never squeezed the way the trap this task's
 * brief names by name squeezed "Round" and "no wallet connected".
 */
class TodayScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/today/TodayScreen.kt").readText()).code
    }

    private fun body(function: String, until: String): String {
        val start = source.indexOf(function)
        assertTrue("TodayScreen.kt has no $function", start >= 0)
        val end = source.indexOf(until, start)
        assertTrue("TodayScreen.kt has no $until after $function", end > start)
        return source.substring(start, end)
    }

    // ---- The seams: block 1 before Yours, blocks 3/4/5 after it -------------------------------

    @Test
    fun `block 1 is the before seam, blocks 3, 4 and 5 are the after seam`() {
        val fn = body("fun TodayScreen(", "private fun TodayVenueBlock(")
        assertTrue("block 1 draws through WatchlistContent's beforeContent seam", "beforeContent = { TodayVenueBlock(state) }" in fn)
        assertTrue("blocks 3, 4 and 5 draw through its afterContent seam", "afterContent = {" in fn)
        assertTrue("TodayAfterContent(" in fn)
    }

    @Test
    fun `blocks 3, 4 and 5 compose in the research's order, Tracked today then Next up then the footer`() {
        val fn = body("private fun TodayAfterContent(", "private fun TodayTrackedBlock(")
        val trackedAt = fn.indexOf("TodayTrackedBlock(")
        val nextUpAt = fn.indexOf("TodayNextUpBlock(")
        val footerAt = fn.indexOf("footerCopy(")
        assertTrue("TodayAfterContent never calls TodayTrackedBlock", trackedAt >= 0)
        assertTrue("TodayAfterContent never calls TodayNextUpBlock", nextUpAt >= 0)
        assertTrue("TodayAfterContent never calls footerCopy", footerAt >= 0)
        assertTrue("Tracked today must compose before Next up", trackedAt < nextUpAt)
        assertTrue("Next up must compose before the footer", nextUpAt < footerAt)
    }

    // ---- The five components this task names, used rather than forked -------------------------

    @Test
    fun `every block reuses the named Amber components, never a re-drawn equivalent`() {
        assertTrue("block 1 is AmberFigure's own status card", "AmberFigure(" in source)
        assertTrue("blocks 3 and 4 use the section head", "AmberSectionHead(" in source)
        assertTrue("the tracked rows and the Next up row use the ticker row", "AmberTickerRow(" in source)
        assertTrue("both row lists sit in the tonal group", "AmberTickerRowGroup {" in source)
    }

    // ---- No false claims: every block is gated on the fact it draws being known ----------------

    @Test
    fun `block 1 is undrawn while the venue is not yet known`() {
        val fn = body("private fun TodayVenueBlock(", "private fun TodayAfterContent(")
        assertTrue("no market read, no sentence stated", "venueSentence(state.market) ?: return" in fn)
    }

    @Test
    fun `block 3's meta count is never drawn while still loading, and the section itself is undrawn once settled with nothing analyzed`() {
        val fn = body("private fun TodayTrackedBlock(", "private fun TodayNextUpBlock(")
        assertTrue("no state before the fast half of the join has run", "if (!state.todayLoading && state.analyzedTotal <= 0) return" in fn)
        assertTrue("the count is null, never a guess, while prices are still out", "if (state.trackedLoading) null else Fmt.count(state.tracked.size)" in fn)
    }

    /**
     * The animator-zero stall (docs/qa-checklist.md, 2026-09-22): the block's skeleton-versus-rows
     * choice, and its lede and meta, gate on [state.trackedLoading][com.plainticker.mobile.ui.watchlist.WatchlistUiState.trackedLoading],
     * which only Jupiter's price fetch flips, never on [state.todayLoading][com.plainticker.mobile.ui.watchlist.WatchlistUiState.todayLoading],
     * which the venue line and Next up also read and which settles well before prices do
     * ([WatchlistViewModel.loadToday]'s own doc). Pinning this the other way round, gating the
     * skeleton on `todayLoading`, is exactly the regression that reintroduces the stall.
     */
    @Test
    fun `block 3's skeleton and rows gate on trackedLoading, the price-fetch flag, not todayLoading`() {
        val fn = body("private fun TodayTrackedBlock(", "private fun TodayNextUpBlock(")
        assertTrue("the lede is never a guess while prices are still out", "if (state.trackedLoading) null else trackedLede(" in fn)
        assertTrue("the skeleton shows only while prices are out", "state.trackedLoading -> SkeletonRows(" in fn)
        assertFalse("todayLoading must not gate the skeleton; it settles before prices ever answer", "state.todayLoading -> SkeletonRows(" in fn)
    }

    @Test
    fun `block 4 is undrawn while no leader can be read`() {
        val fn = body("private fun TodayNextUpBlock(", "private fun TodayFooter(")
        assertTrue("state.nextUpLeader ?: return" in fn)
    }

    @Test
    fun `block 5 is undrawn while neither total is known`() {
        assertTrue("footerCopy(state.analyzedTotal, state.withoutAnalysisTotal)?.let { footer ->" in source)
    }

    // ---- The clipping trap this task's brief names by name, twice ------------------------------

    @Test
    fun `the footer count sits beside a fixed four-character label, never a fixed-width column`() {
        val fn = body("private fun TodayFooter(", "private fun AmberTextLink(")
        assertFalse("a fixed-width modifier is exactly the trap this row must not repeat", ".width(" in fn)
        assertTrue("the count is the flexible sibling that absorbs the squeeze", "weight(1f)" in fn)
        assertFalse("the count is never forced to one line", "maxLines" in fn)
    }

    @Test
    fun `the venue card's context line is never forced to one line either`() {
        val fn = body("private fun TodayVenueBlock(", "private fun TodayAfterContent(")
        assertFalse(".width(" in fn)
        assertFalse("AmberFigure's own context slot carries no maxLines; this call adds none either", "maxLines" in fn)
    }

    @Test
    fun `the text link this screen needed beyond the five named components declares a role`() {
        val fn = body("private fun AmberTextLink(", "private const val TrackedSkeletonCount")
        assertTrue("Role.Button" in fn)
    }

    // ---- The orchestrated moment: blocks 1, 3, 4, 5 settle in, block 2 (Yours) stays still ------

    @Test
    fun `blocks 1, 3, 4 and 5 each carry the entrance modifier, staggered in the research's block order`() {
        val block1 = body("private fun TodayVenueBlock(", "private fun TodayAfterContent(")
        val block3 = body("private fun TodayTrackedBlock(", "private fun TodayNextUpBlock(")
        val block4 = body("private fun TodayNextUpBlock(", "private fun TodayFooter(")
        val block5 = body("private fun TodayFooter(", "private fun AmberTextLink(")
        assertTrue("block 1 (venue) settles in", "amberBlockEntrance(step = VenueEntranceStep)" in block1)
        assertTrue("block 3 (tracked) settles in", "amberBlockEntrance(step = TrackedEntranceStep)" in block3)
        assertTrue("block 4 (next up) settles in", "amberBlockEntrance(step = NextUpEntranceStep)" in block4)
        assertTrue("block 5 (footer) settles in", "amberBlockEntrance(step = FooterEntranceStep)" in block5)
        val order = listOf("VenueEntranceStep = 0", "TrackedEntranceStep = 1", "NextUpEntranceStep = 2", "FooterEntranceStep = 3")
            .map { source.indexOf(it) }
        assertTrue("all four stagger steps are declared", order.all { it >= 0 })
        assertEquals(order, order.sorted())
    }

    @Test
    fun `block 2, Yours, is never wrapped in the entrance modifier, TodayScreen itself never calls it`() {
        val fn = body("fun TodayScreen(", "private fun TodayVenueBlock(")
        assertFalse("Yours is WatchlistContent's shared, per-ticker list; a stagger belongs to the block, not the row", "amberBlockEntrance" in fn)
    }

    @Test
    fun `the entrance settles once per block, not once per row, the modifier is called exactly once in Tracked today`() {
        val trackedBlock = body("private fun TodayTrackedBlock(", "private fun TodayNextUpBlock(")
        val calls = Regex("amberBlockEntrance\\(").findAll(trackedBlock).count()
        assertEquals("a per-row stagger inside the tracked rows' forEach is the shape this task's brief warns against", 1, calls)
    }

    @Test
    fun `the entrance is gated by rememberMotionEnabled and snaps instantly when motion is off`() {
        val fn = body("private fun Modifier.amberBlockEntrance(", "private val EntranceSpring")
        assertTrue("rememberMotionEnabled" in fn)
        assertTrue("snap()" in fn)
        assertTrue("EntranceSpring" in fn)
        assertTrue(
            "settled starts false and the coroutine flips it without a user gesture, so it always reaches 1f on its own",
            "targetValue = if (settled) 1f else 0f" in fn,
        )
    }

    @Test
    fun `the settle is a spring with no bounce, so alpha and translation cannot overshoot`() {
        assertTrue("private val EntranceSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy" in source)
    }
}
