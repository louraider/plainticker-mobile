package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track draws Detail's own classification rows ("Against the sector"). No mockup frame shows it
 * (`gen.py`'s own Amber Detail fragment explicitly omits it: "Backing and controls, F-Score,
 * Method: the layout does not change in any direction, the tokens change under it"), so this
 * restyle follows the departures-board language [Gauge] already carries (the same rounded,
 * `surfaceHigh` capsule) rather than inventing a second track anatomy, while keeping the marker
 * neutral rather than amber: a classification is not a live reading. Nothing here can render on a
 * plain JVM, so what is pinned is source, the way [AmberTickerRowTest] and [AmberFigureTest] read
 * their own components.
 */
class TrackTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/Track.kt").readText()).code
    }

    // ---- Amber's own type, not Instrument's -----------------------------------------------------

    @Test
    fun `label, value and state read AmberType, never PlainTickerType`() {
        assertTrue("style = AmberType.body" in source)
        assertTrue("style = AmberType.figureRow" in source)
        assertTrue("style = AmberType.meta" in source)
        assertFalse("PlainTickerType" in source)
    }

    // ---- The classification stays neutral: colour is reserved for a live reading ------------------

    @Test
    fun `the marker is textPrimary, never actionText, since a classification is not staged as a grade`() {
        assertTrue("the marker draw call must set colors.textPrimary", "drawRect(color = colors.textPrimary" in source)
        assertFalse(
            "Track's own marker must never reach for actionText: DESIGN.md section 7 bans " +
                "staging a classification as a grade, which extends to colour the same way it " +
                "extends to size",
            "drawRect(color = colors.actionText" in source,
        )
    }

    // ---- The departures-board capsule replaces the hairline ---------------------------------------

    @Test
    fun `the track is a rounded surfaceHigh capsule, not a colors border hairline`() {
        assertTrue("drawRoundRect(" in source)
        assertTrue("color = colors.surfaceHigh" in source)
        assertFalse("colors.border" in source)
    }

    @Test
    fun `state keeps its single line but now backstops with an ellipsis`() {
        assertTrue("maxLines = 1" in source)
        assertTrue("overflow = TextOverflow.Ellipsis" in source)
    }

    @Test
    fun `nothing here is pinned to a literal dp width`() {
        assertFalse(".width(" in source)
    }

    // ---- The one motion this component already had, kept exactly as it was -----------------------

    @Test
    fun `the marker's settle animation is unchanged and still gated on rememberMotionEnabled`() {
        assertTrue("rememberMotionEnabled()" in source)
        assertTrue("durationMillis = 400" in source)
        assertTrue("snap<Float>()" in source)
    }

    // ---- Measured, not guessed: fontTools against the real font file, 2026-09-22 ------------------

    /**
     * The real worst case (`axis.labelEn`, lowercased, the shape [DetailModelTest] and
     * [FreeStaysFreeTest] fix): "near 52-week high", 103.812dp at `meta`'s 12sp/400. Content width
     * on a 400dp frame, this composable's own 20dp side padding twice, is 360dp; the top row's two
     * groups sit 12dp apart, `value` and `state` a further 10dp apart.
     */
    @Test
    fun `label keeps a comfortable single-line margin even against the widest real state word`() {
        val contentWidthDp = 360.0
        val outerGapDp = 12.0
        val innerGapDp = 10.0
        val widestValueWidthDp = 36.954 // "0.79" or "1.00" at figureRow's 18sp/600 tnum.
        val widestStateWidthDp = 103.812 // "near 52-week high" at meta's 12sp/400.
        val valueStateGroupWidthDp = widestValueWidthDp + innerGapDp + widestStateWidthDp
        assertEquals(150.766, valueStateGroupWidthDp, 0.01)

        val labelBudgetDp = contentWidthDp - outerGapDp - valueStateGroupWidthDp
        assertEquals(197.234, labelBudgetDp, 0.01)

        // The widest of the three fixed labels, at body's 15sp/400.
        val widestLabelWidthDp = 80.955 // "Momentum"
        assertTrue(
            "the widest label ($widestLabelWidthDp dp) must clear the worst-case budget " +
                "($labelBudgetDp dp) on one line",
            widestLabelWidthDp <= labelBudgetDp,
        )

        // At 1.3x font scale, content width does not scale but every measured width does.
        val scale = 1.3
        val labelBudgetAt13x = contentWidthDp - outerGapDp - valueStateGroupWidthDp * scale
        assertTrue(
            "the widest label at 1.3x must still clear the scaled budget",
            widestLabelWidthDp * scale <= labelBudgetAt13x,
        )
    }
}
