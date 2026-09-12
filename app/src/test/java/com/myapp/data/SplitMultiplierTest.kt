package com.myapp.data

import com.myapp.data.rpc.ScaledUiAmountConfig
import com.myapp.data.xstocks.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Two sources for one number, and the answer has to say which of them spoke. */
class SplitMultiplierTest {

    private fun chain(
        multiplier: Double,
        newMultiplier: Double = multiplier,
        effectiveAt: Long = 0L,
    ) = ScaledUiAmountConfig(multiplier, newMultiplier, effectiveAt, authority = null)

    @Test
    fun `the mint's own extension is reported as coming from the mint`() {
        val split = SplitMultiplier.ofMint(chain(1.0))

        assertEquals(1.0, split.current, 0.0)
        assertEquals(MultiplierSource.MINT, split.source)
        assertNull("nothing is scheduled", split.pending)
    }

    @Test
    fun `a scheduled change on the mint carries the new value and the second it lands`() {
        val split = SplitMultiplier.ofMint(chain(1.0, newMultiplier = 4.0, effectiveAt = 1_789_948_800L))

        assertEquals(1.0, split.current, 0.0)
        val pending = checkNotNull(split.pending)
        assertEquals(4.0, pending.multiplier, 0.0)
        assertEquals(1_789_948_800L, pending.activatesAtEpochSeconds)
        assertEquals(1_789_948_800_000L, pending.activatesAtMillis())
        assertFalse(pending.activated(1_789_948_799_000L))
        assertTrue(pending.activated(1_789_948_800_000L))
    }

    @Test
    fun `a timestamp with no change, and a change with no timestamp, are both no pending split`() {
        assertNull(SplitMultiplier.ofMint(chain(1.0, newMultiplier = 1.0, effectiveAt = 1_789_948_800L)).pending)
        assertNull(SplitMultiplier.ofMint(chain(1.0, newMultiplier = 4.0, effectiveAt = 0L)).pending)
    }

    @Test
    fun `the xStocks endpoint is the fallback and is reported as such`() {
        val split = SplitMultiplier.ofXStocks(Multiplier(currentMultiplier = 10.0))

        assertEquals(10.0, split.current, 0.0)
        assertEquals(MultiplierSource.XSTOCKS, split.source)
        assertNull(split.pending)
    }

    @Test
    fun `a scheduled change from xStocks needs both halves, as its own model says`() {
        val scheduled = SplitMultiplier.ofXStocks(
            Multiplier(currentMultiplier = 1.0, newMultiplier = 4.0, activationDateTime = 1_789_948_800L, reason = "Split"),
        )
        val pending = checkNotNull(scheduled.pending)
        assertEquals(4.0, pending.multiplier, 0.0)
        assertEquals(1_789_948_800L, pending.activatesAtEpochSeconds)

        assertNull(
            "a new value with no activation is not scheduled",
            SplitMultiplier.ofXStocks(Multiplier(currentMultiplier = 1.0, newMultiplier = 4.0)).pending,
        )
        assertNull(
            "an activation with no new value is not scheduled",
            SplitMultiplier.ofXStocks(Multiplier(currentMultiplier = 1.0, activationDateTime = 1_789_948_800L)).pending,
        )
    }

    @Test
    fun `an xStocks activation sent in milliseconds is read as the same instant`() {
        val millis = SplitMultiplier.ofXStocks(
            Multiplier(currentMultiplier = 1.0, newMultiplier = 4.0, activationDateTime = 1_789_948_800_000L),
        )
        assertEquals(1_789_948_800L, millis.pending!!.activatesAtEpochSeconds)
    }

    @Test
    fun `a multiplier that is missing, zero or not a number is no rescaling at all`() {
        assertEquals(SplitMultiplier.NONE, SplitMultiplier.ofMint(chain(0.0)).current, 0.0)
        assertEquals(SplitMultiplier.NONE, SplitMultiplier.ofMint(chain(Double.NaN)).current, 0.0)
        assertEquals(SplitMultiplier.NONE, SplitMultiplier.ofXStocks(Multiplier(currentMultiplier = -1.0)).current, 0.0)
    }
}
