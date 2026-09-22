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
}
