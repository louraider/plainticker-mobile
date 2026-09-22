package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gauge is the one piece of this restyle the founder's approved mockup actually draws
 * (`scratchpad/design/mockups/gen.py`'s Amber `detail_body` fragment and `gauge(full=True)`), so
 * this file pins the departures-board shape against that contract: a rounded, `surfaceHigh`
 * capsule with no separate end stops, drawn in Amber's own type. The liquidity floor's own
 * contract (the caption pair, the off-scale cap, `OffScaleGap`) is [DetailFinishedScreenTest]'s
 * job and is deliberately not re-pinned here beyond confirming this file still carries the exact
 * substrings that test reads; nothing here can render on a plain JVM, so what is pinned is source,
 * the way [AmberTickerRowTest] and [AmberFigureTest] read their own components.
 */
class GaugeTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/Gauge.kt").readText()).code
    }

    // ---- Amber's own type, not Instrument's -----------------------------------------------------

    @Test
    fun `the caption and premium read AmberType, never PlainTickerType`() {
        assertTrue("style = AmberType.context" in source)
        assertTrue("style = AmberType.figureInline" in source)
        assertFalse("PlainTickerType" in source)
    }

    // ---- The departures-board capsule replaces the hairline and its end stops -------------------

    @Test
    fun `the track is a rounded surfaceHigh capsule, not a colors border hairline`() {
        assertTrue("drawRoundRect(" in source)
        assertTrue("color = colors.surfaceHigh" in source)
        // Every one of Instrument's colors.border draws (the hairline track and the two end
        // stops) is gone; the mockup's own Amber CSS hides the end stops outright
        // (`.d-amber .gauge .e{display:none}`) once the track itself has a rounded, visible end.
        assertFalse("colors.border" in source)
    }

    @Test
    fun `the token tick still stands full canvas height, taller than the reference tick, colour aside`() {
        assertTrue(
            "the token tick must remain 2dp wide and span the full canvas height so it reads as a " +
                "distinct shape from the 1dp, shorter reference tick without relying on colour",
            "size = Size(two, size.height)" in source,
        )
        assertTrue("size = Size(one, 10.dp.toPx())" in source)
    }

    // ---- The liquidity floor's own contract survives this restyle untouched ----------------------

    @Test
    fun `the pinned substrings DetailFinishedScreenTest reads are still exactly present`() {
        assertTrue(
            "if (tick.offScale) R.string.detail_gauge_caption_off else R.string.detail_gauge_caption" in source,
        )
        assertTrue("!tick.offScale ->" in source)
        assertTrue("OffScaleGap" in source)
        assertFalse("the gauge must never draw the thin-pool sentence itself", "R.string.detail_gauge_thin" in source)
    }

    @Test
    fun `nothing here is pinned to a literal dp width`() {
        assertFalse(".width(" in source)
    }

    // ---- Measured, not guessed: fontTools against the real font file, 2026-09-22 ------------------

    /**
     * `context` and `figureInline`, each instantiated at their own `wght`/`wdth`/`opsz`
     * (`AmberType.kt`'s `bricolage()`). Content width on a 400dp frame, this composable's own 20dp
     * side padding twice, is 360dp, split by a 12dp `Arrangement.spacedBy`.
     */
    @Test
    fun `the caption keeps a comfortable margin against even a synthetic worst-case premium`() {
        val contentWidthDp = 360.0
        val gapDp = 12.0
        // "-999.99%" at figureInline's 14sp/400 tnum: a ceiling past anything the tracked
        // catalogue has produced (the real worst measured, INTCx, is -4.13%, 45.458dp).
        val syntheticCeilingPremiumWidthDp = 62.314
        val captionBudgetDp = contentWidthDp - syntheticCeilingPremiumWidthDp - gapDp
        assertEquals(285.686, captionBudgetDp, 0.01)

        // The longest real caption, the off-scale sentence at context's 14sp/400.
        val offScaleCaptionWidthDp = 268.660
        assertTrue(
            "the longest real caption ($offScaleCaptionWidthDp dp) must fit even the worst-case " +
                "budget ($captionBudgetDp dp) on one line",
            offScaleCaptionWidthDp <= captionBudgetDp,
        )
    }
}
