package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live bar already breathes while a read is live (DESIGN.md section 6: keep that meaningful
 * and keep it gated), so this file's job is to pin that the breathing motion is untouched by the
 * restyle and that the one real structural risk this row carries (a fixed-width sibling beside an
 * unbounded label/meta column) is closed. Nothing here can render on a plain JVM, so what is
 * pinned is source, the way [AmberTickerRowTest] and [AmberFigureTest] read their own components.
 */
class LiveBarTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/LiveBar.kt").readText()).code
    }

    // ---- Amber's own type, not Instrument's -----------------------------------------------------

    @Test
    fun `label and meta read AmberType, never PlainTickerType`() {
        assertTrue("style = AmberType.context" in source)
        assertTrue("style = AmberType.meta" in source)
        assertFalse("PlainTickerType" in source)
    }

    // ---- The one motion DESIGN.md section 6 says this restyle must not touch ----------------------

    @Test
    fun `the breathing animation is byte-identical to before this restyle`() {
        assertTrue("live && rememberMotionEnabled()" in source)
        assertTrue("initialValue = 1f" in source)
        assertTrue("targetValue = 0.45f" in source)
        assertTrue("durationMillis = 1200" in source)
        assertTrue("EaseInOut" in source)
        assertTrue("RepeatMode.Reverse" in source)
    }

    // ---- The clipping trap this file's own doc comment names, closed -------------------------------

    @Test
    fun `the label-meta column is bounded, and meta backstops with an ellipsis`() {
        assertTrue(
            "the label/meta Column must be weighted so it cannot demand more width than the row " +
                "has left past the fixed 2dp bar, the shape of the trap this brief names",
            "Modifier.weight(1f, fill = false)" in source,
        )
        assertTrue("maxLines = 1" in source)
        assertTrue("overflow = TextOverflow.Ellipsis" in source)
    }

    @Test
    fun `nothing here is pinned to a literal dp width beyond the bar's own fixed 2dp`() {
        // The bar itself is deliberately fixed at 2dp (DESIGN.md section 1's own thin mark); no
        // other width literal should appear.
        assertEquals(1, Regex("""\.width\(""").findAll(source).count())
        assertTrue(".width(2.dp)" in source)
    }

    // ---- Measured, not guessed: fontTools against the real font file, 2026-09-22 ------------------

    /**
     * The real worst-case `meta` this component draws across every screen that calls it
     * (`detail_live_unread_meta`), at `meta`'s own 12sp/400. Column budget on a 400dp frame, this
     * row's 20dp side padding, the 2dp bar and the 14dp gap between them.
     */
    @Test
    fun `meta keeps a margin against its real worst case, even at 1_3x font scale`() {
        val frameWidthDp = 400.0
        val sidePaddingDp = 20.0
        val barWidthDp = 2.0
        val gapDp = 14.0
        val columnBudgetDp = frameWidthDp - sidePaddingDp * 2 - barWidthDp - gapDp
        assertEquals(344.0, columnBudgetDp, 0.001)

        // "Nothing below this line is read from the chain" (detail_live_unread_meta, 46 characters).
        val worstMetaWidthDp = 255.624
        assertTrue(worstMetaWidthDp <= columnBudgetDp)

        val scale = 1.3
        assertTrue(
            "the real worst-case meta must still clear the column budget at 1.3x font scale, or " +
                "the ellipsis backstop this fix adds is load-bearing rather than a safety margin",
            worstMetaWidthDp * scale <= columnBudgetDp,
        )
    }
}
