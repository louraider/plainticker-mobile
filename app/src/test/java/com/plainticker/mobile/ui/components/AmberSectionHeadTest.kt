package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The section head, including the sticky sector chapter Stocks needs (docs/design-research-2026-
 * 09-21.md section 3). Pinned the same way [com.plainticker.mobile.ui.vote.VoteScreenTest] pinned
 * `RoundHeader`'s own fix for the exact same class of bug: a long meta starving a `weight(1f)`
 * title. This head chose the other resolution (let the title wrap), so what is pinned here is that
 * choice, not a re-run of the layout that produced it, which a plain JVM cannot do.
 */
class AmberSectionHeadTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberSectionHead.kt").readText()).code
    }

    private fun body(function: String): String {
        val start = source.indexOf(function)
        assertTrue("no $function in the source", start >= 0)
        val end = source.indexOf("\n}", start)
        assertTrue("$function never closes", end > start)
        return source.substring(start, end)
    }

    // ---- The starved-title trap RoundHeader already found, resolved by wrap here --------------

    @Test
    fun `the title wraps to two lines rather than being forced to a single starved one`() {
        val fn = body("fun AmberSectionHead(")
        val titleCall = fn.substring(fn.indexOf("text = title,"), fn.indexOf("if (meta != null)"))
        assertTrue("maxLines = 2" in titleCall)
        assertTrue("weight(1f)" in titleCall)
    }

    @Test
    fun `meta stays a short, single-line count next to the title, never a full clause`() {
        val fn = body("fun AmberSectionHead(")
        val metaCall = fn.substring(fn.indexOf("if (meta != null)"))
        assertTrue("maxLines = 1" in metaCall)
    }

    // ---- The real worst case this head is measured against ------------------------------------

    /**
     * The GICS sectors Stocks groups xStocks into (`app/src/main/assets/snapshot/summary.json`,
     * read 2026-09-22): three names tie for longest at 22 characters. [LongestSectorName] is one
     * of them.
     */
    @Test
    fun `the longest real sector name is 22 characters, and LongestSectorName is one of the three tied for it`() {
        val sectors = listOf(
            "Consumer Discretionary", "Information Technology", "Communication Services",
            "Consumer Staples", "Health Care", "Industrials", "Real Estate",
            "Financials", "Materials", "Utilities", "Energy",
        )
        assertEquals(22, sectors.maxOf { it.length })
        assertEquals(22, LongestSectorName.length)
        assertTrue(LongestSectorName in sectors)
    }

    // ---- The sticky chapter head paints its own background --------------------------------

    @Test
    fun `the head always paints an opaque background of its own, which is what a sticky header needs while pinned`() {
        val fn = body("fun AmberSectionHead(")
        assertTrue(".background(background)" in fn)
        assertTrue("background: Color = colors.surfaceGround" in source)
    }

    @Test
    fun `the title still carries the heading semantics a screen reader jumps between sections with`() {
        assertTrue("semantics { heading() }" in source)
    }
}
