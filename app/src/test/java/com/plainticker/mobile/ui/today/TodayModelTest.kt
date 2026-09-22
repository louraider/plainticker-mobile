package com.plainticker.mobile.ui.today

import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketState
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.ui.ShippedCopy
import java.math.BigInteger
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Today's four built blocks say, off the shipped strings.xml the way [ShippedCopy] reads it
 * for every other screen's model test: a plain JVM, no device, and no drift between what a test
 * asserts and what a reader would actually see.
 */
class TodayModelTest {

    private fun status(regular: Boolean, guessed: Boolean) = MarketStatus(
        state = if (regular) MarketState.REGULAR else MarketState.CLOSED,
        source = if (guessed) MarketSource.LOCAL_SCHEDULE else MarketSource.VENUE,
        venueOpen = regular,
        nextChangeAtMillis = null,
    )

    // ---- Block 1: venue and data age -----------------------------------------------------------

    @Test
    fun `the venue sentence is undrawn while no catalog has answered`() {
        assertNull(venueSentence(null))
    }

    @Test
    fun `the venue sentence covers all four states, and reuses the strings Stocks' own banner reads`() {
        assertEquals(
            "The NYSE is open, the price tracks live.",
            ShippedCopy.render(venueSentence(status(regular = true, guessed = false))!!),
        )
        assertEquals(
            "The NYSE is open by the local schedule, the venue did not answer",
            ShippedCopy.render(venueSentence(status(regular = true, guessed = true))!!),
        )
        assertEquals(
            "The NYSE is closed, the reference is the last close",
            ShippedCopy.render(venueSentence(status(regular = false, guessed = false))!!),
        )
        assertEquals(
            "The NYSE is closed by the local schedule, the venue did not answer",
            ShippedCopy.render(venueSentence(status(regular = false, guessed = true))!!),
        )
    }

    @Test
    fun `freshness waits for both ages, true only once both are known`() {
        assertNull("the analysis age alone is not the sentence", freshnessSentence(1_000L, null, 2_000L))
        assertNull("the price age alone is not the sentence", freshnessSentence(null, 1_000L, 2_000L))
        assertNull(freshnessSentence(null, null, 2_000L))
    }

    @Test
    fun `freshness reads both ages once they are both known`() {
        val now = Instant.parse("2026-09-13T12:00:00.000Z").toEpochMilli()
        val analysisAt = Instant.parse("2026-09-12T12:00:00.000Z").toEpochMilli() // 1 day old
        val pricesAt = now - 2_000L // 2 seconds old
        val text = ShippedCopy.render(freshnessSentence(analysisAt, pricesAt, now)!!)
        assertEquals("Analysis 1 d ago, prices 2 s ago.", text)
    }

    // ---- Block 3: Tracked today -----------------------------------------------------------------

    @Test
    fun `the lede is undrawn before the first refresh, when the analyzed total is not yet known`() {
        assertNull("nothing is knowable about coverage before the join has ever run", trackedLede(0, 0))
    }

    @Test
    fun `the lede states coverage, in the exact shape the approved frames draw`() {
        assertEquals("22 of 160 analyzed can be tracked today", ShippedCopy.render(trackedLede(22, 160)!!))
    }

    @Test
    fun `the lede is true with nothing tracked and true with everything tracked, never about movement`() {
        val nothingTracked = ShippedCopy.render(trackedLede(0, 160)!!)
        val everythingTracked = ShippedCopy.render(trackedLede(160, 160)!!)
        assertEquals("0 of 160 analyzed can be tracked today", nothingTracked)
        assertEquals("160 of 160 analyzed can be tracked today", everythingTracked)
        // A sentence about direction ("moved") could not stay true in both of these states without
        // a price history this app does not keep; a sentence about coverage can, and does.
        assertFalse(nothingTracked.contains("moved"))
        assertFalse(everythingTracked.contains("moved"))
    }

    @Test
    fun `the lede holds at the real worst-case universe size, the whole catalog`() {
        // docs/data-map.md: ~830 assets in the catalog today. Not a guess at what "many" looks
        // like, the actual scale this screen will be asked to draw.
        val text = ShippedCopy.render(trackedLede(830, 830)!!)
        assertEquals("830 of 830 analyzed can be tracked today", text)
    }

    @Test
    fun `all-tracked reads the same total the lede and the section head's meta count`() {
        assertEquals("All 22 tracked", ShippedCopy.render(trackedAllCopy(22)))
        assertEquals("All 0 tracked", ShippedCopy.render(trackedAllCopy(0)))
    }

    @Test
    fun `a tracked row without a reference price draws the missing placeholder, not a crash`() {
        val row = TrackedRow(ticker = "AAPL", symbol = "AAPLx", company = "Apple Inc.", premiumPct = null, poolUsd = 250_000.0)
        assertEquals("-", row.figure)
        assertEquals("AAPLx", row.display)
    }

    @Test
    fun `a tracked row with a premium formats it the same way every other screen's premium reads`() {
        val row = TrackedRow(ticker = "TSLA", symbol = "TSLAx", company = "Tesla, Inc.", premiumPct = 0.09, poolUsd = 500_000.0)
        assertEquals("+0.09%", row.figure)
    }

    @Test
    fun `a token the catalog has not named yet falls back to the underlying ticker`() {
        val row = TrackedRow(ticker = "MCD", symbol = null, company = null, premiumPct = -1.01, poolUsd = 26_205.0)
        assertEquals("MCD", row.display)
    }

    // ---- Block 4: Next up ------------------------------------------------------------------------

    @Test
    fun `the round lede is undrawn when there is no round, or the round names no close`() {
        assertNull(nextUpRoundLede(null))
        assertNull(nextUpRoundLede(VoteRound(id = 2, opensAt = "2026-09-21T00:00:00Z", closesAt = "not a date")))
    }

    @Test
    fun `the round lede reads the same round id and close VoteScreen names`() {
        val round = VoteRound(id = 2, opensAt = "2026-09-21T00:00:00Z", closesAt = "2026-09-28T00:00:00.000Z")
        assertEquals("Round 2 closes 28 Sep 2026 00:00 UTC", ShippedCopy.render(nextUpRoundLede(round)!!))
    }

    @Test
    fun `the leader's weight and voters read the same shipped copy the List's own leaders read`() {
        val leader = TodayLeader(
            ticker = "AMD",
            symbol = "AMDx",
            company = "AMD xStock",
            weightRaw = BigInteger.valueOf(6_719_000_000L), // 6 SKR decimals: 6,719 SKR exactly
            voters = 1,
        )
        assertEquals("AMDx", leader.display)
        assertEquals("6,719 SKR", ShippedCopy.render(leader.weight))
        assertEquals("1 voter", ShippedCopy.render(leader.votersContext))

        val many = leader.copy(voters = 3)
        assertEquals("3 voters", ShippedCopy.render(many.votersContext))
    }

    @Test
    fun `a leader the catalog has not named yet falls back to the underlying ticker`() {
        val leader = TodayLeader(ticker = "AMD", symbol = null, company = null, weightRaw = BigInteger.ZERO, voters = 0)
        assertEquals("AMD", leader.display)
    }

    // ---- Block 5: the footer ----------------------------------------------------------------------

    @Test
    fun `the footer is undrawn when neither total is known`() {
        assertNull(footerCopy(0, 0))
    }

    @Test
    fun `the footer reads both totals, in the exact shape the approved frames draw`() {
        assertEquals("160 analyzed, 768 without analysis", ShippedCopy.render(footerCopy(160, 768)!!))
    }

    @Test
    fun `the footer still draws with one total at zero, a count of one phrased as one is not at stake here`() {
        assertEquals("160 analyzed, 0 without analysis", ShippedCopy.render(footerCopy(160, 0)!!))
        assertEquals("0 analyzed, 768 without analysis", ShippedCopy.render(footerCopy(0, 768)!!))
    }

    // ---- The preview constant ---------------------------------------------------------------------

    @Test
    fun `the tracked preview shows the same six rows the approved mockup draws before handing off to Stocks`() {
        assertEquals(6, TrackedPreviewCount)
        assertTrue(TrackedPreviewCount < 830)
    }
}
