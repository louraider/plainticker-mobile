package com.plainticker.mobile.ui.list

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stocks' layout, pinned the way [com.plainticker.mobile.ui.components.AmberTickerRowTest] and
 * [com.plainticker.mobile.ui.today.TodayScreenTest] pin their own screens: there is no layout test
 * on a plain JVM that can measure a real render, so what is pinned is the shape of the source.
 *
 * Judges' round 2 (2026-09-27): the chapter jump rail is gone (its "Com", "Dis", "Sta" codes
 * clipped and meant nothing to a beginner, and it narrowed every line beside it, the hours banner
 * included), and the sector chips sit in one horizontally scrolling row instead of wrapping to four.
 */
class ListScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/list/ListScreen.kt").readText()).code
    }

    private fun body(function: String, until: String): String {
        val start = source.indexOf(function)
        assertTrue("ListScreen.kt has no $function", start >= 0)
        val end = source.indexOf(until, start)
        assertTrue("ListScreen.kt has no $until after $function", end > start)
        return source.substring(start, end)
    }

    // ---- No jump rail: the list, and the banner in it, take the full width ----------------------

    @Test
    fun `the jump rail is gone, so nothing narrows the list or the hours banner`() {
        assertFalse("the jump rail came back", "StocksJumpIndex" in source)
        assertFalse("JumpIndexWidth" in source)
        val fn = body("internal fun ListContent(", "private fun StocksChrome(")
        val lazyColumnStart = fn.indexOf("LazyColumn(")
        assertTrue("ListContent has no LazyColumn", lazyColumnStart >= 0)
        val lazyColumnCall = fn.substring(lazyColumnStart, fn.indexOf("contentPadding", lazyColumnStart))
        assertTrue("the list must fill the screen's full width", "modifier.fillMaxSize()" in lazyColumnCall)
        assertFalse("nothing shares the row with the list any more", "weight(1f)" in lazyColumnCall)
    }

    // ---- The filter row: one scrolling line, not four wrapped ones ----------------------------

    @Test
    fun `the sector chips scroll sideways in one row instead of wrapping`() {
        val fn = body("private fun StocksFilterRow(", "private fun groupedRowModifier(")
        assertTrue(".horizontalScroll(rememberScrollState())" in fn)
        assertFalse("a FlowRow wraps the chips to four rows again", "FlowRow(" in fn)
        assertFalse("FlowRow(" in source)
    }

    @Test
    fun `the Deep pool chip is gated on its own count`() {
        val fn = body("private fun StocksFilterRow(", "private fun groupedRowModifier(")
        assertTrue("} else if (showsDeepPoolChip(trackedCount, active)) {" in fn)
    }

    @Test
    fun `the row figure names its scale and the legend says what it ranks against`() {
        val row = body("private fun AnalyzedRow(", "private fun PriceOnlyRow(")
        assertTrue("R.string.list_row_score" in row)
        val chrome = body("private fun StocksChrome(", "private fun SearchField(")
        assertTrue("R.string.list_row_score_legend" in chrome)
    }

    // ---- Stocks' own grouped rows, light-only edge (DESIGN.md section 8's added exception) ------

    /**
     * Stocks is the screen with the most rows to lose the tonal-lift defect on (roughly 830 of
     * them), and it is exactly the screen [AmberTickerRowGroup]'s own single-`Column` border does
     * not fit: one `LazyColumn` item per row for real recycling, not one non-lazy group per
     * chapter (this file's own doc comment on [groupedRowModifier]). So the light-only edge is
     * drawn a row at a time by [groupEdge] instead: every call site must thread [colors] through
     * so the function can gate the edge on the light palette, and the edge itself must never draw
     * a full frame around a middle row, or the seam-between-rows anti-pattern DESIGN.md section 8
     * still bans comes back.
     */
    @Test
    fun `groupedRowModifier takes colors and gates its own edge on the light palette`() {
        val fn = body("private fun groupedRowModifier(", "private fun Modifier.groupEdge(")
        assertTrue(
            "groupedRowModifier must take colors so it can gate the light-only edge",
            fn.startsWith("private fun groupedRowModifier(colors: AmberColors, isFirst: Boolean, isLast: Boolean): Modifier"),
        )
        assertTrue(
            "the edge must be gated on colors === AmberLightColors, not drawn in both themes",
            "if (colors === AmberLightColors) {" in fn,
        )
        assertTrue("Modifier.groupEdge(color = colors.border, corner = corner, isFirst = isFirst, isLast = isLast)" in fn)
    }

    /**
     * [groupEdge] draws the left and right edges unconditionally (every row shares those sides
     * with its neighbours, so they need no `isFirst`/`isLast` gate), but the top edge only under
     * `isFirst` and the bottom edge only under `isLast`, which is the whole mechanism that keeps a
     * middle row from getting a horizontal line of its own, i.e. a frame.
     */
    @Test
    fun `groupEdge draws a top edge only on the first row and a bottom edge only on the last, never a frame per row`() {
        val fn = body("private fun Modifier.groupEdge(", "private val RowGapHeight")
        assertTrue("drawWithContent" in fn)
        assertTrue("drawContent()" in fn)
        val ifFirstIndex = fn.indexOf("if (isFirst) {")
        val ifLastIndex = fn.indexOf("if (isLast) {")
        assertTrue("groupEdge must gate its top edge on isFirst", ifFirstIndex >= 0)
        assertTrue("groupEdge must gate its bottom edge on isLast", ifLastIndex >= 0)
        // The two conditional edges must be the only place drawLine appears past the always-drawn
        // left/right pair: exactly 4 drawLine calls total (left, right, top-if-first, bottom-if-last).
        assertTrue(Regex("drawLine\\(").findAll(fn).count() == 4)
    }

    @Test
    fun `every groupedRowModifier call site threads colors through, all four rows this screen groups`() {
        // "= groupedRowModifier(" excludes the function's own definition ("private fun
        // groupedRowModifier(..."), matching only the four `modifier = groupedRowModifier(...)`
        // call sites below.
        val callSites = Regex("= groupedRowModifier\\(").findAll(source).toList()
        assertTrue(
            "expected the four grouped-row call sites this screen draws (chapter rows, next-up " +
                "leaders, the flat analyzed search results, the flat price-only search results)",
            callSites.size == 4,
        )
        callSites.forEach { call ->
            val afterOpenParen = source.substring(call.range.last + 1).trimStart()
            assertTrue(
                "call site must pass colors as groupedRowModifier's first argument: " +
                    afterOpenParen.take(40),
                afterOpenParen.startsWith("colors,"),
            )
        }
    }

    // ---- Voted, the same everywhere (device QA of 1.3.17) ---------------------------------------

    @Test
    fun `a row this wallet already voted for this round says Voted instead of offering Vote`() {
        val screen = body("fun ListScreen(", "internal fun ListContent(")
        assertTrue("val voted by voteViewModel.votedTickers.collectAsStateWithLifecycle()" in screen)
        assertTrue("votedTickers = voted" in screen)
        val content = body("internal fun ListContent(", "private fun StocksChrome(")
        assertTrue("the Next up strip reads it", "voted = votedTickers.hasVoted(leader.ticker)" in content)
        assertTrue("a search result reads it", "voted = votedTickers.hasVoted(row.ticker)" in content)
        val row = body("private fun VotableAmberRow(", "private fun EmptyLine(")
        val note = row.indexOf("R.string.vote_voted_row")
        assertTrue("the quiet word comes first, in place of the action", note in 0 until row.indexOf("TextAction("))
        assertTrue("if (!voted && onVote != null) {" in row)
    }

    // ---- Cold start and unpriced rows (device QA of 1.3.17) -------------------------------------

    @Test
    fun `a row with no figure says why, and says it is being priced only until the first run settles`() {
        val meta = body("private fun rowMeta(", "private fun StateBanner(")
        assertTrue("row.priceUsd == null && row.quote == RowQuote.ANSWERED -> stringResource(R.string.list_row_meta_unpriced)" in meta)
        assertTrue(
            "row.priceUsd == null && row.quote == RowQuote.PENDING && pricesPending -> stringResource(R.string.list_row_meta_pricing)" in meta,
        )
        assertTrue("val pricesPending = !state.pricesSettled && !state.pricesUnavailable" in source)
    }

    @Test
    fun `the deep pool chip's slot is held while prices load, so the chip row does not jump`() {
        val row = body("private fun StocksFilterRow(", "private fun groupedRowModifier(")
        val slot = row.indexOf("if (holdDeepPoolSlot && !showsDeepPoolChip(trackedCount, active)) {")
        assertTrue("the placeholder comes first, in the chip's own slot", slot >= 0 && slot < row.indexOf("AmberChip("))
        assertTrue("SkeletonChip(width = DeepPoolSlotWidth, colors = colors)" in row)
        assertTrue("holdDeepPoolSlot = pricesPending" in source)
    }
}
