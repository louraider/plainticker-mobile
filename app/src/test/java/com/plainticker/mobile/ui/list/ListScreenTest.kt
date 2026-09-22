package com.plainticker.mobile.ui.list

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The jump index overlap the device found (this task's brief, part 2): the rail drawn over the
 * search field, the filter chips and the sticky sector heading instead of beside them. Pinned the
 * way [com.plainticker.mobile.ui.components.AmberTickerRowTest] and
 * [com.plainticker.mobile.ui.today.TodayScreenTest] pin their own screens: there is no layout test
 * on a plain JVM that can measure a real render or catch a real overlap, so what is pinned is that
 * [ListContent] lays the rail out as a genuine sibling column beside a narrowed `LazyColumn`
 * rather than as a `Box` overlay drawn on top of a full-width one.
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

    // ---- The device bug: an overlay draws over the chrome instead of reserving its own width -----

    @Test
    fun `the list and the jump index are Row siblings, not a Box with the rail overlaid on top`() {
        val fn = body("internal fun ListContent(", "private fun StocksChrome(")
        assertFalse(
            "a Box lets the rail draw over the search field, the chips and the sticky heading " +
                "instead of taking its own width, which is exactly the device bug",
            "Box(modifier.fillMaxSize())" in fn,
        )
        assertTrue("the list and the rail must be laid out side by side", "Row(modifier.fillMaxSize()" in fn)
    }

    @Test
    fun `the list narrows to leave room for the rail instead of the rail floating over a full-width list`() {
        val fn = body("internal fun ListContent(", "private fun StocksChrome(")
        val lazyColumnStart = fn.indexOf("LazyColumn(")
        assertTrue("ListContent has no LazyColumn", lazyColumnStart >= 0)
        val lazyColumnCall = fn.substring(lazyColumnStart, fn.indexOf("state = listState", lazyColumnStart))
        assertTrue(
            "the list must share the row's width with the rail (weight), not claim all of it " +
                "(fillMaxSize), or the rail has nowhere of its own to sit",
            "weight(1f)" in lazyColumnCall,
        )
        assertFalse("fillMaxSize" in lazyColumnCall)
    }

    @Test
    fun `the jump index call carries no Box-only alignment, since it is a Row sibling now`() {
        val fn = body("internal fun ListContent(", "private fun StocksChrome(")
        val jumpCallStart = fn.indexOf("StocksJumpIndex(")
        assertTrue("ListContent never calls StocksJumpIndex", jumpCallStart >= 0)
        val jumpCall = fn.substring(jumpCallStart, fn.indexOf("\n        }", jumpCallStart))
        // BoxScope.align(Alignment.CenterEnd) is exactly the old overlay's own positioning; a Row
        // sibling needs none, since the Row's own verticalAlignment centers it.
        assertFalse(".align(" in jumpCall)
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
}
