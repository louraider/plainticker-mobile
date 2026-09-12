package com.myapp.data.xstocks

import com.myapp.repo.xStock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The venue's state as one pure function over a clock. The rule under test is the order: the
 * asset's own trading block decides whenever it is there, and the local weekday schedule is only
 * ever reached by an asset that carries no block.
 */
class MarketHoursTest {

    private val mint = "TestMint".padEnd(44, '1')

    /** A wall clock in the NYSE's own zone, so the schedule's boundaries can be named in local time. */
    private fun newYork(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(MarketHours.ZONE).toInstant().toEpochMilli()

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    private fun trading(
        period: TradingPeriod? = null,
        openNow: Boolean = false,
        halted: Boolean = false,
        nextChangeAt: String? = null,
    ) = Trading(
        tradingHoursMode = "Regular",
        isTradingHalted = halted,
        currentPeriod = period,
        openNow = openNow,
        nextChangeAt = nextChangeAt,
        exchange = Exchange(mic = "XNYS", abbreviation = "NYSE", timezone = "America/New_York"),
    )

    // ---- The block is the source of truth ---------------------------------------------------

    @Test
    fun `the venue block decides, even against the local calendar`() {
        // A Wednesday at 11:00 in New York: the local schedule would say the exchange is trading.
        val duringTheLocalSession = newYork(2026, 9, 16, 11, 0)
        assertEquals(MarketState.REGULAR, MarketHours.localSchedule(duringTheLocalSession))

        val holiday = MarketHours.of(
            trading(period = TradingPeriod.CLOSED, openNow = false),
            halted = false,
            nowMillis = duringTheLocalSession,
        )
        assertEquals("the issuer knows about the holiday and the schedule does not", MarketState.CLOSED, holiday.state)
        assertEquals(MarketSource.VENUE, holiday.source)
        assertFalse(holiday.venueOpen)
        assertFalse(holiday.regularSession)
    }

    @Test
    fun `each venue period maps to its own state and only the exchange session tracks live`() {
        val now = newYork(2026, 9, 16, 11, 0)
        val cases = mapOf(
            TradingPeriod.MARKET to MarketState.REGULAR,
            TradingPeriod.EXTENDED to MarketState.EXTENDED,
            TradingPeriod.OVERNIGHT to MarketState.OVERNIGHT,
            TradingPeriod.CLOSED to MarketState.CLOSED,
        )
        cases.forEach { (period, expected) ->
            val status = MarketHours.of(trading(period = period, openNow = true), halted = false, nowMillis = now)
            assertEquals(period.name, expected, status.state)
            assertTrue("the block said the venue takes orders", status.venueOpen)
            assertEquals(period.name, expected == MarketState.REGULAR, status.regularSession)
        }
    }

    @Test
    fun `a halted asset is halted whichever side reported it, and the venue is not open`() {
        val now = newYork(2026, 9, 16, 11, 0)

        val haltedByBlock = MarketHours.of(
            trading(period = TradingPeriod.MARKET, openNow = true, halted = true),
            halted = false,
            nowMillis = now,
        )
        assertEquals(MarketState.HALTED, haltedByBlock.state)
        assertTrue(haltedByBlock.halted)
        assertFalse(haltedByBlock.venueOpen)

        val haltedByAsset = MarketHours.of(
            trading(period = TradingPeriod.MARKET, openNow = true),
            halted = true,
            nowMillis = now,
        )
        assertEquals(MarketState.HALTED, haltedByAsset.state)
    }

    @Test
    fun `a block with no period still answers from its own open flag, never from the calendar`() {
        // A Saturday: the local schedule would say closed, and the block says the venue is open.
        val weekend = newYork(2026, 9, 19, 11, 0)
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(weekend))

        val status = MarketHours.of(trading(period = null, openNow = true), halted = false, nowMillis = weekend)
        assertEquals(MarketState.REGULAR, status.state)
        assertEquals(MarketSource.VENUE, status.source)

        val shut = MarketHours.of(trading(period = null, openNow = false), halted = false, nowMillis = weekend)
        assertEquals(MarketState.CLOSED, shut.state)
        assertEquals(MarketSource.VENUE, shut.source)
    }

    @Test
    fun `the next change is parsed when the venue sends one and never invented when it does not`() {
        val now = newYork(2026, 9, 16, 11, 0)
        val withChange = MarketHours.of(
            trading(period = TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-16T20:00:00.000Z"),
            halted = false,
            nowMillis = now,
        )
        assertEquals(utc("2026-09-16T20:00:00Z"), withChange.nextChangeAtMillis)

        val unparseable = MarketHours.of(
            trading(period = TradingPeriod.MARKET, openNow = true, nextChangeAt = "tomorrow"),
            halted = false,
            nowMillis = now,
        )
        assertNull(unparseable.nextChangeAtMillis)
        assertNull(MarketHours.of(trading(), halted = false, nowMillis = now).nextChangeAtMillis)
    }

    // ---- The fallback -----------------------------------------------------------------------

    @Test
    fun `an asset with no trading block falls back to the local weekday schedule and says so`() {
        val wednesdayNoon = newYork(2026, 9, 16, 12, 0)
        val status = MarketHours.of(xStock("TSLAx", "TSLA", mint), wednesdayNoon)

        assertEquals(MarketSource.LOCAL_SCHEDULE, status.source)
        assertEquals(MarketState.REGULAR, status.state)
        assertTrue(status.venueOpen)
        assertNull("the schedule knows of no scheduled change", status.nextChangeAtMillis)
    }

    @Test
    fun `the local schedule opens at half past nine and closes at four, New York time`() {
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 9, 16, 9, 29)))
        assertEquals(MarketState.REGULAR, MarketHours.localSchedule(newYork(2026, 9, 16, 9, 30)))
        assertEquals(MarketState.REGULAR, MarketHours.localSchedule(newYork(2026, 9, 16, 15, 59)))
        assertEquals("the close itself is shut", MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 9, 16, 16, 0)))
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 9, 16, 3, 0)))
    }

    @Test
    fun `the local schedule is shut all weekend`() {
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 9, 19, 12, 0)))
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 9, 20, 12, 0)))
        assertEquals(MarketState.REGULAR, MarketHours.localSchedule(newYork(2026, 9, 21, 12, 0)))
    }

    @Test
    fun `the schedule follows New York through daylight saving, not a fixed offset`() {
        // 14:00 UTC is 10:00 in New York in September and 09:00 in January. Only one is a session.
        val september = Instant.parse("2026-09-16T14:00:00Z").toEpochMilli()
        val january = Instant.parse("2027-01-13T14:00:00Z").toEpochMilli()

        assertEquals(MarketState.REGULAR, MarketHours.localSchedule(september))
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(january))
        assertEquals(ZoneId.of("America/New_York"), MarketHours.ZONE)
    }

    @Test
    fun `a halted asset with no trading block is halted, not merely closed`() {
        val asset = xStock("TSLAx", "TSLA", mint).copy(isTradingHalted = true)
        val status = MarketHours.of(asset, newYork(2026, 9, 16, 12, 0))

        assertEquals(MarketState.HALTED, status.state)
        assertEquals(MarketSource.LOCAL_SCHEDULE, status.source)
        assertFalse(status.venueOpen)
    }

    @Test
    fun `a missing asset is read from the schedule and never as open by default`() {
        val closed = MarketHours.of(asset = null, nowMillis = newYork(2026, 9, 19, 12, 0))
        assertEquals(MarketState.CLOSED, closed.state)
        assertEquals(MarketSource.LOCAL_SCHEDULE, closed.source)
    }

    // ---- What the price row needs -----------------------------------------------------------

    @Test
    fun `the price row labels itself within a percentage only while the exchange is trading`() {
        val open = MarketHours.of(trading(TradingPeriod.MARKET, openNow = true), false, utc("2026-09-16T14:00:00Z"))
        assertEquals(PriceLabel.TRACKING_WITHIN, open.priceLabel)

        listOf(TradingPeriod.EXTENDED, TradingPeriod.OVERNIGHT, TradingPeriod.CLOSED).forEach { period ->
            val shut = MarketHours.of(trading(period, openNow = true), false, utc("2026-09-16T14:00:00Z"))
            assertEquals(period.name, PriceLabel.VS_NYSE_CLOSE, shut.priceLabel)
        }

        val halted = MarketHours.of(trading(TradingPeriod.MARKET, openNow = true, halted = true), false, utc("2026-09-16T14:00:00Z"))
        assertEquals(PriceLabel.VS_NYSE_CLOSE, halted.priceLabel)
    }

    @Test
    fun `the zone is a real zone and not an offset, so the test clock and the code agree`() {
        val noonUtc = Instant.parse("2026-09-16T16:00:00Z").toEpochMilli()
        assertEquals(12, Instant.ofEpochMilli(noonUtc).atZone(MarketHours.ZONE).hour)
        assertEquals(16, Instant.ofEpochMilli(noonUtc).atZone(ZoneOffset.UTC).hour)
    }
}
