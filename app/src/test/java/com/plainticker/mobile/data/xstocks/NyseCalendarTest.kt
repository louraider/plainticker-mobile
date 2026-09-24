package com.plainticker.mobile.data.xstocks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The venue as a clock: the exchange calendar ([NyseCalendar]) and the snapshot rule
 * ([MarketHours.sessionAt]) that together replace a status computed once from a catalog cached
 * for up to a day. The bug they fix was measured on the Seeker on 24 Sep 2026 at 19:22 UTC (15:22
 * in New York, a Thursday): the venue's own feed said "market, open, next change 20:00 UTC" and
 * Today said "Closed".
 */
class NyseCalendarTest {

    private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

    private fun newYork(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(MarketHours.ZONE).toInstant().toEpochMilli()

    private fun block(period: TradingPeriod, openNow: Boolean, nextChangeAt: String?) =
        Trading(currentPeriod = period, openNow = openNow, nextChangeAt = nextChangeAt)

    // ---- The phases of one ordinary day ---------------------------------------------------------

    @Test
    fun `an ordinary trading day runs closed, pre-market, regular, after hours, closed`() {
        // Thursday 24 Sep 2026, EDT (UTC-4).
        assertEquals(SessionPhase.CLOSED, NyseCalendar.session(newYork(2026, 9, 24, 3, 59)).phase)
        assertEquals(SessionPhase.PRE_MARKET, NyseCalendar.session(newYork(2026, 9, 24, 4, 0)).phase)
        assertEquals(SessionPhase.PRE_MARKET, NyseCalendar.session(newYork(2026, 9, 24, 9, 29)).phase)
        assertEquals(SessionPhase.REGULAR, NyseCalendar.session(newYork(2026, 9, 24, 9, 30)).phase)
        assertEquals(SessionPhase.REGULAR, NyseCalendar.session(newYork(2026, 9, 24, 15, 59)).phase)
        assertEquals(SessionPhase.AFTER_HOURS, NyseCalendar.session(newYork(2026, 9, 24, 16, 0)).phase)
        assertEquals(SessionPhase.AFTER_HOURS, NyseCalendar.session(newYork(2026, 9, 24, 19, 59)).phase)
        assertEquals(SessionPhase.CLOSED, NyseCalendar.session(newYork(2026, 9, 24, 20, 0)).phase)
    }

    @Test
    fun `the seeker's own moment reads as the session it was, with its close and next boundary`() {
        val s = NyseCalendar.session(utc("2026-09-24T19:22:00Z"))
        assertEquals(SessionPhase.REGULAR, s.phase)
        assertEquals(utc("2026-09-24T20:00:00Z"), s.nextCloseMillis)
        assertEquals("the close is the next boundary", utc("2026-09-24T20:00:00Z"), s.nextBoundaryMillis)
        assertEquals("tomorrow's open is the next open once today's has passed", utc("2026-09-25T13:30:00Z"), s.nextOpenMillis)
        assertFalse(s.holiday)
        assertFalse(s.earlyClose)
    }

    @Test
    fun `each boundary of a day is the next one in turn, and the last hands over to tomorrow's pre-market`() {
        assertEquals(newYork(2026, 9, 24, 4, 0), NyseCalendar.session(newYork(2026, 9, 24, 1, 0)).nextBoundaryMillis)
        assertEquals(newYork(2026, 9, 24, 9, 30), NyseCalendar.session(newYork(2026, 9, 24, 5, 0)).nextBoundaryMillis)
        assertEquals(newYork(2026, 9, 24, 20, 0), NyseCalendar.session(newYork(2026, 9, 24, 17, 0)).nextBoundaryMillis)
        assertEquals(newYork(2026, 9, 25, 4, 0), NyseCalendar.session(newYork(2026, 9, 24, 21, 0)).nextBoundaryMillis)
    }

    // ---- Weekend ----------------------------------------------------------------------------------

    @Test
    fun `a weekend is closed throughout and the next open is Monday's`() {
        val friday = NyseCalendar.session(newYork(2026, 9, 25, 21, 0))
        val saturday = NyseCalendar.session(newYork(2026, 9, 26, 12, 0))
        val sunday = NyseCalendar.session(newYork(2026, 9, 27, 23, 59))
        listOf(friday, saturday, sunday).forEach {
            assertEquals(SessionPhase.CLOSED, it.phase)
            assertEquals(newYork(2026, 9, 28, 9, 30), it.nextOpenMillis)
            assertEquals(newYork(2026, 9, 28, 4, 0), it.nextBoundaryMillis)
            assertNull(it.nextCloseMillis)
            assertFalse("a weekend is not a holiday", it.holiday)
        }
    }

    // ---- Daylight saving ---------------------------------------------------------------------------

    @Test
    fun `the spring switch moves the open from 14 30 to 13 30 UTC, and a boundary across it lands in the new offset`() {
        // DST begins Sunday 8 Mar 2026. Friday opens at 09:30 EST (14:30 UTC), Monday at 09:30 EDT (13:30 UTC).
        assertEquals(SessionPhase.PRE_MARKET, NyseCalendar.session(utc("2026-03-06T14:29:00Z")).phase)
        assertEquals(SessionPhase.REGULAR, NyseCalendar.session(utc("2026-03-06T14:30:00Z")).phase)
        assertEquals(SessionPhase.PRE_MARKET, NyseCalendar.session(utc("2026-03-09T13:29:00Z")).phase)
        assertEquals(SessionPhase.REGULAR, NyseCalendar.session(utc("2026-03-09T13:30:00Z")).phase)
        val fridayNight = NyseCalendar.session(utc("2026-03-07T02:00:00Z")) // Friday 21:00 EST
        assertEquals("Monday's pre-market, 04:00 EDT", utc("2026-03-09T08:00:00Z"), fridayNight.nextBoundaryMillis)
        assertEquals("Monday's open, 09:30 EDT", utc("2026-03-09T13:30:00Z"), fridayNight.nextOpenMillis)
    }

    @Test
    fun `the autumn switch moves the close from 20 00 to 21 00 UTC, and the weekend's next open is in EST`() {
        // DST ends Sunday 1 Nov 2026. Friday 30 Oct closes 16:00 EDT (20:00 UTC); Monday 2 Nov at 16:00 EST (21:00 UTC).
        assertEquals(utc("2026-10-30T20:00:00Z"), NyseCalendar.session(utc("2026-10-30T19:00:00Z")).nextCloseMillis)
        assertEquals(utc("2026-11-02T21:00:00Z"), NyseCalendar.session(utc("2026-11-02T19:00:00Z")).nextCloseMillis)
        assertEquals(SessionPhase.REGULAR, NyseCalendar.session(utc("2026-11-02T20:30:00Z")).phase)
        val sunday = NyseCalendar.session(utc("2026-11-01T12:00:00Z"))
        assertEquals(utc("2026-11-02T14:30:00Z"), sunday.nextOpenMillis)
    }

    // ---- Holidays and early closes --------------------------------------------------------------------

    @Test
    fun `thanksgiving is a holiday, closed all day, and the day after opens`() {
        val thanksgiving = NyseCalendar.session(newYork(2026, 11, 26, 11, 0))
        assertEquals(SessionPhase.CLOSED, thanksgiving.phase)
        assertTrue(thanksgiving.holiday)
        assertEquals(newYork(2026, 11, 27, 9, 30), thanksgiving.nextOpenMillis)
        assertEquals("and the old weekday schedule's own answer follows the calendar", MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 11, 26, 11, 0)))
    }

    @Test
    fun `the day after thanksgiving closes at 13 00, and its after-hours window ends at 17 00`() {
        val midday = NyseCalendar.session(newYork(2026, 11, 27, 12, 59))
        assertEquals(SessionPhase.REGULAR, midday.phase)
        assertTrue(midday.earlyClose)
        assertEquals(newYork(2026, 11, 27, 13, 0), midday.nextCloseMillis)
        assertEquals(SessionPhase.AFTER_HOURS, NyseCalendar.session(newYork(2026, 11, 27, 13, 0)).phase)
        assertEquals(SessionPhase.AFTER_HOURS, NyseCalendar.session(newYork(2026, 11, 27, 16, 59)).phase)
        val evening = NyseCalendar.session(newYork(2026, 11, 27, 17, 0))
        assertEquals(SessionPhase.CLOSED, evening.phase)
        assertEquals("Monday 30 Nov", newYork(2026, 11, 30, 9, 30), evening.nextOpenMillis)
        assertEquals(MarketState.CLOSED, MarketHours.localSchedule(newYork(2026, 11, 27, 14, 0)))
    }

    @Test
    fun `every holiday in the table is a weekday, and every early close is a trading day`() {
        NyseCalendar.HOLIDAYS.forEach {
            assertTrue("$it falls on a weekend, so it is not an observed date", it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY)
            assertFalse(NyseCalendar.isTradingDay(it))
        }
        NyseCalendar.EARLY_CLOSES.forEach { assertTrue("$it must be a trading day", NyseCalendar.isTradingDay(it)) }
        assertEquals("ten full closures a year, two years", 20, NyseCalendar.HOLIDAYS.size)
    }

    @Test
    fun `a run of closures, christmas observed on a friday, still finds the next open`() {
        // Friday 24 Dec 2027 is Christmas observed; the next open is Monday 27 Dec.
        val s = NyseCalendar.session(newYork(2027, 12, 24, 12, 0))
        assertTrue(s.holiday)
        assertEquals(newYork(2027, 12, 27, 9, 30), s.nextOpenMillis)
    }

    // ---- The snapshot is believed only until its own next change ------------------------------------------

    @Test
    fun `the seeker's bug, a closed snapshot past its nextChangeAt no longer answers`() {
        // A catalog cached before the open: the venue said closed, changing at the open, 13:30 UTC.
        val stale = block(TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-24T13:30:00Z")
        val status = MarketHours.sessionAt(utc("2026-09-24T19:22:00Z"), stale)
        assertEquals("the calendar answers once the snapshot has expired", MarketState.REGULAR, status.state)
        assertTrue(status.regularSession)
        assertEquals(MarketSource.LOCAL_SCHEDULE, status.source)
        assertEquals("and the next change is the calendar's close", utc("2026-09-24T20:00:00Z"), status.nextChangeAtMillis)
    }

    @Test
    fun `the opposite bug, an open snapshot past the close reads as after hours`() {
        // v131: an open snapshot still shown at 17:31 in New York.
        val stale = block(TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-22T20:00:00Z")
        val status = MarketHours.sessionAt(utc("2026-09-22T21:31:00Z"), stale)
        assertEquals(MarketState.CLOSED, status.state)
        assertEquals(SessionPhase.AFTER_HOURS, status.session?.phase)
    }

    @Test
    fun `a snapshot before its nextChangeAt is the venue's answer, even against the calendar`() {
        // The venue knows about an unscheduled closure the calendar cannot: it is believed.
        val fresh = block(TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-25T13:30:00Z")
        val status = MarketHours.sessionAt(utc("2026-09-24T19:22:00Z"), fresh)
        assertEquals(MarketState.CLOSED, status.state)
        assertEquals(MarketSource.VENUE, status.source)
        assertEquals(utc("2026-09-25T13:30:00Z"), status.nextChangeAtMillis)
        assertEquals("the calendar still rides along for the copy", SessionPhase.REGULAR, status.session?.phase)
    }

    @Test
    fun `a snapshot exactly at its nextChangeAt has expired`() {
        val edge = block(TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-24T13:30:00Z")
        assertEquals(MarketSource.LOCAL_SCHEDULE, MarketHours.sessionAt(utc("2026-09-24T13:30:00Z"), edge).source)
    }

    @Test
    fun `a snapshot with no nextChangeAt is believed, and no snapshot at all reads the calendar`() {
        val silent = block(TradingPeriod.EXTENDED, openNow = true, nextChangeAt = null)
        val status = MarketHours.sessionAt(utc("2026-09-24T19:22:00Z"), silent)
        assertEquals(MarketState.EXTENDED, status.state)
        assertEquals(MarketSource.VENUE, status.source)

        val none = MarketHours.sessionAt(utc("2026-09-26T12:00:00Z"), null)
        assertEquals(MarketState.CLOSED, none.state)
        assertEquals(MarketSource.LOCAL_SCHEDULE, none.source)
        assertEquals("Monday's pre-market is the next change", newYork(2026, 9, 28, 4, 0), none.nextChangeAtMillis)
    }

    @Test
    fun `the calendar never claims the venue's own extended or overnight periods`() {
        val preMarket = MarketHours.sessionAt(newYork(2026, 9, 24, 8, 0), null)
        assertEquals(MarketState.CLOSED, preMarket.state)
        assertEquals(SessionPhase.PRE_MARKET, preMarket.session?.phase)
        assertFalse(preMarket.venueOpen)
    }
}
