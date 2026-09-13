package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.StockData
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.components.gaugeTick
import com.plainticker.mobile.ui.theme.PlainTickerType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two properties of the finished Detail screen that every per-component review passed.
 *
 * Both blockers in docs/design-review-2026-09-13.md are properties of a whole screen, so neither
 * could fail a test of a piece of it. `TrackingQuality` was right about the floor, `PriceBlock` was
 * right about its two figures, `Gauge` was right about placing a tick on a stated scale, and the
 * screen those three compose into was wrong: it withheld a premium and then printed both operands
 * at two sizes with the caveat between them, and it drew a tick pinned at the end of a scale the
 * live market leaves on most days. This file asserts the composed screen, on the numbers the
 * device actually read, which is the only level at which either of them is visible.
 *
 * What the screen *says* stays in [DetailModelTest]; where the screen *puts* things stays in
 * [DetailScreenTest]. What is here is neither: it is what a reader ends up able to do.
 */
class DetailFinishedScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(path: String): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/$path").readText()).code

    /** One Detail screen, built from the three Price v3 fields every finding here turns on. */
    private fun detail(ticker: String, token: Double, reference: Double?, pool: Double?) = DetailUiState(
        ticker = ticker,
        quote = Piece.Ready(
            PriceEntry(
                usdPrice = token,
                liquidity = pool,
                stockData = reference?.let { StockData(price = it) },
            ),
        ),
    )

    /** A screen whose token stands [premiumPct] off its reference, on a pool deep enough to track. */
    private fun tracking(premiumPct: Double) =
        detail("X", token = 100.0 + premiumPct, reference = 100.0, pool = 1_900_000.0)

    private fun words(copy: Copy?): Copy.Words = copy as Copy.Words

    // ---- Blocker 1: the floor and the composition above it -------------------------------------

    @Test
    fun `below the floor the screen hands the reader no arithmetic to do`() {
        // APPx exactly as the signed v0.2.0 drew it on the Seeker: pool $34, token $611.56,
        // NYSE close $323.00. The two of them are +89.34 percent apart, which is the number the
        // liquidity floor exists to keep off the screen (docs/data-map.md).
        val appx = detail("APP", token = 611.56, reference = 323.00, pool = 34.0)
        val row = appx.priceRow

        // The floor is disclosure, not curation: both figures are still printed, and the derived
        // number is still withheld by the one rule.
        assertEquals("\$611.56", row.tokenPrice)
        assertEquals("\$323.00", row.referencePrice)
        assertEquals(TrackingQuality.Thin(34.0), appx.tracking)
        assertNull(appx.tracking?.premiumPct)

        // 1. The sentence that disqualifies the comparison is part of the price block, and it is
        //    read before either figure rather than under both of them.
        assertFalse("the two figures are staged as a comparison the screen just refused", row.comparable)
        assertEquals(R.string.detail_gauge_thin, words(row.lead).id)
        assertEquals(listOf("\$34"), words(row.lead).args)
        val block = source("ui/detail/DetailScreen.kt")
            .substringAfter("private fun PriceBlock(")
            .substringBefore("private fun LiveBlock(")
        assertTrue("PriceBlock never draws the pool sentence", "row.lead" in block)
        assertTrue(
            "the pool sentence is drawn after the figures it disqualifies",
            block.indexOf("row.lead") < block.indexOf("row.tokenPrice"),
        )

        // 2. Neither figure leads. One size for both, and not the hero size: by the floor's own
        //    argument a quote off a $34 pool is not a price, so it may not be the largest true
        //    thing on the screen.
        val withheld = priceFigureType(row.comparable)
        assertEquals(
            "two operands at two sizes is the invitation to subtract them",
            withheld.reference.fontSize.value,
            withheld.token.fontSize.value,
            0f,
        )
        assertTrue(
            "a figure the screen just disqualified is still the largest thing on it",
            withheld.token.fontSize.value < PlainTickerType.heroPrice.fontSize.value,
        )
        assertEquals("nothing to lift when the two figures match", 0f, withheld.referenceLift.value, 0f)

        // 3. And the figure is named for what it is: a reading off the pool the sentence above it
        //    just measured, not a price of AppLovin.
        assertEquals(R.string.detail_pool_quote, words(row.tokenLabel).id)

        // 4. The pool is stated once. Below the floor the gauge draws nothing at all, so the
        //    sentence cannot drift back under the pair by being owned in two places.
        assertTrue(appx.gauge !is TrackingQuality.Tracked)
        val gauge = source("ui/components/Gauge.kt")
        assertFalse("the gauge still states the pool under the pair", "R.string.detail_gauge_thin" in gauge)

        // Above the floor nothing moves: NVDAx keeps the asymmetric pair the review called the
        // best thing in the app, and says nothing about its pool.
        val nvdax = detail("NVDA", token = 216.18, reference = 218.26, pool = 1_900_000.0)
        val deep = nvdax.priceRow
        assertNull(deep.lead)
        assertTrue(deep.comparable)
        assertEquals(R.string.detail_token_price, words(deep.tokenLabel).id)
        val tracked = priceFigureType(deep.comparable)
        assertEquals(PlainTickerType.heroPrice.fontSize.value, tracked.token.fontSize.value, 0f)
        assertEquals(PlainTickerType.referencePrice.fontSize.value, tracked.reference.fontSize.value, 0f)
    }

    // ---- Blocker 2: the gauge on real data -----------------------------------------------------

    @Test
    fun `the gauge never draws a pinned tick as if it were a value`() {
        // Every premium a pool above the floor produced when the catalogue was measured on
        // 2026-09-12 (docs/data-map.md), plus both readings the Seeker gave for NVDAx on
        // 2026-09-13. The deepest pool in the catalogue is in here twice because it is the one
        // that pinned, and it pinned at two different values hours apart.
        val measured = mapOf(
            "NVDAx as the review read it" to -1.01,
            "NVDAx a few hours later" to -0.95,
            "TSLAx" to 0.09,
            "AAPLx" to 0.12,
            "the widest of the 13 deepest" to 0.8,
            "XOMx on \$18.8k" to -1.58,
            "UNHx on \$12.3k" to -2.12,
            "NFLXx on \$12.5k" to -2.34,
        )
        measured.forEach { (name, premiumPct) ->
            val state = tracking(premiumPct)
            val premium = (state.gauge as TrackingQuality.Tracked).premiumPct!!
            assertEquals(name, premiumPct, premium, 1e-9)
            val tick = gaugeTick(premium, GAUGE_SCALE_PCT)
            assertFalse("$name runs off the scale the screen states", tick.offScale)
            assertTrue("$name pins against the end of the track", tick.fraction > 0f)
            assertTrue("$name pins against the end of the track", tick.fraction < 1f)
        }

        // No tracked token was measured past the scale, and that is exactly why the cap has to
        // exist: a scale wide enough for today is not a promise about tomorrow, and a tick that
        // stops at the end while the caption still names the scale reports a number it does not
        // have. Past the scale the tick is a cap, on both sides, and it is clamped only so the
        // drawing has somewhere to put it.
        listOf(-6.2, -2.51, 2.51, 89.34).forEach { premiumPct ->
            val tick = gaugeTick(premiumPct, GAUGE_SCALE_PCT)
            assertTrue("$premiumPct percent is drawn as a position on a 2.5 percent scale", tick.offScale)
            assertEquals(if (premiumPct < 0) 0f else 1f, tick.fraction, 0f)
        }
        // The end of the scale is on the scale; one step past it is not.
        assertFalse(gaugeTick(GAUGE_SCALE_PCT, GAUGE_SCALE_PCT).offScale)
        assertFalse(gaugeTick(-GAUGE_SCALE_PCT, GAUGE_SCALE_PCT).offScale)

        // The scale is the measured spread of the tracked set, not a number the canvas liked.
        assertEquals(TrackingQuality.TRACKED_SPREAD_PCT, GAUGE_SCALE_PCT, 0.0)

        // And the caption says it, so a reader who does not read the geometry is still told.
        val gauge = source("ui/components/Gauge.kt")
        assertTrue(
            "an off-scale tick keeps the caption of an on-scale one",
            "if (tick.offScale) R.string.detail_gauge_caption_off else R.string.detail_gauge_caption" in gauge,
        )
        assertTrue("the drawing does not know the tick is off the scale", "!tick.offScale ->" in gauge)
        assertTrue("there is no room off the track for the tick to stand in", "OffScaleGap" in gauge)
    }
}
