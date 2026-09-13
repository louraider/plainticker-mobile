package com.plainticker.mobile.data.jupiter

import com.plainticker.mobile.data.jupiter.TrackingQuality.Companion.MIN_POOL_USD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The liquidity floor, the one rule every surface asks before it draws a premium or a gauge
 * (docs/data-map.md, "The liquidity floor, measured 2026-09-12"). Both boundaries, a missing
 * depth, and the four pools the live measurement named.
 */
class TrackingQualityTest {

    private fun entry(usd: Double, reference: Double? = null, liquidity: Double? = null) = PriceEntry(
        usdPrice = usd,
        liquidity = liquidity,
        stockData = reference?.let { StockData(id = "xstocks", price = it) },
    )

    /** A quote whose premium over the NYSE close is [pct] percent, off a pool of [pool] dollars. */
    private fun quote(pct: Double, pool: Double?): TrackingQuality? =
        TrackingQuality.of(entry(usd = 100.0 * (1.0 + pct / 100.0), reference = 100.0, liquidity = pool))

    // ---- The floor ------------------------------------------------------------------------

    @Test
    fun `the floor is four thousand dollars`() {
        assertEquals(4_000.0, MIN_POOL_USD, 0.0)
    }

    @Test
    fun `a pool at the floor is tracked and a pool one cent under it is thin`() {
        val at = TrackingQuality.of(priceUsd = 100.5, referenceUsd = 100.0, poolUsd = MIN_POOL_USD)
        assertTrue("a pool exactly at the floor is deep enough", at is TrackingQuality.Tracked)
        assertEquals(0.5, at!!.premiumPct!!, 1e-9)
        assertEquals(MIN_POOL_USD, at.poolUsd!!, 0.0)

        val under = TrackingQuality.of(priceUsd = 100.5, referenceUsd = 100.0, poolUsd = MIN_POOL_USD - 0.01)
        assertEquals(TrackingQuality.Thin(MIN_POOL_USD - 0.01), under)
        assertNull("no premium below the floor", under!!.premiumPct)
        assertEquals(MIN_POOL_USD - 0.01, under.poolUsd!!, 0.0)

        val over = TrackingQuality.of(priceUsd = 100.5, referenceUsd = 100.0, poolUsd = MIN_POOL_USD + 0.01)
        assertTrue(over is TrackingQuality.Tracked)
        assertEquals(0.5, over!!.premiumPct!!, 1e-9)
    }

    @Test
    fun `a pool of nothing is thin, not unknown`() {
        val empty = TrackingQuality.of(priceUsd = 100.5, referenceUsd = 100.0, poolUsd = 0.0)
        assertEquals(TrackingQuality.Thin(0.0), empty)
    }

    // ---- A depth Jupiter did not send -------------------------------------------------------

    @Test
    fun `a null liquidity is unknown, never deep`() {
        val unknown = TrackingQuality.of(priceUsd = 100.5, referenceUsd = 100.0, poolUsd = null)
        assertEquals(TrackingQuality.Untracked, unknown)
        assertNull("the premium is withheld when the depth is unknown", unknown!!.premiumPct)
        assertNull("there is no pool to state", unknown.poolUsd)
    }

    @Test
    fun `a liquidity that is not a number of dollars is unknown`() {
        assertEquals(TrackingQuality.Untracked, TrackingQuality.of(100.5, 100.0, Double.NaN))
        assertEquals(TrackingQuality.Untracked, TrackingQuality.of(100.5, 100.0, Double.POSITIVE_INFINITY))
        assertEquals(TrackingQuality.Untracked, TrackingQuality.of(100.5, 100.0, -1.0))
    }

    // ---- No quote at all ---------------------------------------------------------------------

    @Test
    fun `no price entry is no tracking question`() {
        assertNull(TrackingQuality.of(entry = null))
        assertNull(TrackingQuality.of(priceUsd = null, referenceUsd = 100.0, poolUsd = 250_000.0))
        assertNull(TrackingQuality.of(priceUsd = Double.NaN, referenceUsd = 100.0, poolUsd = 250_000.0))
    }

    @Test
    fun `a deep pool with no reference price is tracked with nothing to draw`() {
        val noClose = TrackingQuality.of(entry(usd = 232.54, liquidity = 250_000.0))
        assertEquals(TrackingQuality.Tracked(null, 250_000.0), noClose)
        assertNull("no NYSE close means no premium, which is not a tracking failure", noClose!!.premiumPct)
        assertEquals(250_000.0, noClose.poolUsd!!, 0.0)
    }

    @Test
    fun `a reference of zero yields no premium`() {
        assertNull(TrackingQuality.of(priceUsd = 100.0, referenceUsd = 0.0, poolUsd = 250_000.0)!!.premiumPct)
    }

    // ---- The live measurement, 2026-09-12 ---------------------------------------------------

    @Test
    fun `the four pools the measurement named are all thin and state their pool`() {
        // UBERx +152.13% on $80, APPx +89.34% on $34, CRWDx -42.15% on $48, ASMLx +30.42% on $61.
        val measured = listOf(152.13 to 80.0, 89.34 to 34.0, -42.15 to 48.0, 30.42 to 61.0)
        measured.forEach { (pct, pool) ->
            val quality = quote(pct, pool)
            assertEquals("a pool of $pool is below the floor", TrackingQuality.Thin(pool), quality)
            assertNull("$pct percent off $pool dollars never reaches a screen", quality!!.premiumPct)
            assertEquals(pool, quality.poolUsd!!, 0.0)
        }
    }

    @Test
    fun `the plausible band between ten thousand and a hundred thousand keeps its premium`() {
        // NFLXx -2.34% on $12.5k, UNHx -2.12% on $12.3k, XOMx -1.58% on $18.8k.
        val band = listOf(-2.34 to 12_500.0, -2.12 to 12_300.0, -1.58 to 18_800.0)
        band.forEach { (pct, pool) ->
            val quality = quote(pct, pool)
            assertTrue("a pool of $pool is above the floor", quality is TrackingQuality.Tracked)
            assertEquals(pct, quality!!.premiumPct!!, 1e-9)
        }
    }

    @Test
    fun `the deepest pools keep their premium`() {
        // The thirteen deepest all tracked within 0.8 percent; NVDAx and TSLAx stand for them.
        val nvda = TrackingQuality.of(entry(usd = 182.11, reference = 182.18, liquidity = 1_300_000.0))
        assertTrue(nvda is TrackingQuality.Tracked)
        assertEquals(-0.0384, nvda!!.premiumPct!!, 1e-4)

        val tsla = TrackingQuality.of(entry(usd = 366.17, reference = 365.84, liquidity = 184_000.0))
        assertEquals(0.0902, tsla!!.premiumPct!!, 1e-4)
    }

    // ---- Arithmetic --------------------------------------------------------------------------

    @Test
    fun `the premium is the token over the reference, in percent`() {
        assertEquals(0.5, TrackingQuality.of(100.5, 100.0, 250_000.0)!!.premiumPct!!, 1e-9)
        assertEquals(-0.5, TrackingQuality.of(99.5, 100.0, 250_000.0)!!.premiumPct!!, 1e-9)
        assertEquals(0.0, TrackingQuality.of(100.0, 100.0, 250_000.0)!!.premiumPct!!, 1e-9)
        assertEquals(152.13, TrackingQuality.of(252.13, 100.0, 250_000.0)!!.premiumPct!!, 1e-9)
    }
}
