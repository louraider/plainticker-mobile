package com.plainticker.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * One test per Fmt rule (DESIGN.md section 7), with the boundaries the design review asked
 * for: 0.9999 against 1.0, negative zero, very large counts, 59 s against 60 s, 23 h against
 * 24 h. Every screen formats through Fmt, so these strings are the product's numerals.
 */
class FmtTest {

    private val now: Instant = Instant.parse("2026-09-10T14:55:30Z")

    // ---- price ----------------------------------------------------------------------------

    @Test
    fun `price has two decimals from one dollar up`() {
        assertEquals("$366.17", Fmt.price(366.17))
        assertEquals("$1.00", Fmt.price(1.0))
        assertEquals("$1,234.50", Fmt.price(1234.5))
        assertEquals("$26,101.00", Fmt.price(26_101.0))
    }

    @Test
    fun `price has four decimals below one dollar`() {
        assertEquals("$0.4213", Fmt.price(0.4213))
        assertEquals("$0.9999", Fmt.price(0.9999))
        assertEquals("$0.0001", Fmt.price(0.0001))
    }

    @Test
    fun `price decides the decimals after rounding so 0_99995 is one dollar`() {
        assertEquals("$1.00", Fmt.price(0.99995))
        assertEquals("$0.9999", Fmt.price(0.99994))
    }

    @Test
    fun `price puts the sign before the dollar and never on zero`() {
        assertEquals("-$1.50", Fmt.price(-1.5))
        assertEquals("-$0.4213", Fmt.price(-0.4213))
        assertEquals("$0.00", Fmt.price(-0.0))
        assertEquals("$0.00", Fmt.price(-0.00004))
    }

    @Test
    fun `price renders zero with two decimals like any whole amount`() {
        assertEquals("$0.00", Fmt.price(0.0))
        assertEquals("$0.00", Fmt.price(0.00004))
        assertEquals("$0.0001", Fmt.price(0.00005))
    }

    @Test
    fun `price renders a non-finite value as the missing placeholder`() {
        assertEquals("-", Fmt.price(Double.NaN))
        assertEquals("-", Fmt.price(Double.POSITIVE_INFINITY))
        assertEquals("-", Fmt.price(Double.NEGATIVE_INFINITY))
    }

    // ---- compact money ----------------------------------------------------------------------

    @Test
    fun `compact money is whole dollars under a thousand`() {
        // The pools the liquidity floor has to name, as measured on 2026-09-12.
        assertEquals("$34", Fmt.compactMoney(34.0))
        assertEquals("$48", Fmt.compactMoney(48.0))
        assertEquals("$61", Fmt.compactMoney(61.0))
        assertEquals("$80", Fmt.compactMoney(80.0))
        assertEquals("$949", Fmt.compactMoney(948.6))
        assertEquals("$999", Fmt.compactMoney(999.4))
    }

    @Test
    fun `compact money keeps cents only under a dollar`() {
        assertEquals("$0", Fmt.compactMoney(0.0))
        assertEquals("$0.40", Fmt.compactMoney(0.4))
        assertEquals("$0.01", Fmt.compactMoney(0.005))
        assertEquals("$1", Fmt.compactMoney(0.996))
    }

    @Test
    fun `compact money takes a unit from a thousand up`() {
        assertEquals("$1k", Fmt.compactMoney(999.6))
        assertEquals("$10k", Fmt.compactMoney(10_000.0))
        assertEquals("$12.3k", Fmt.compactMoney(12_300.0))
        assertEquals("$12.5k", Fmt.compactMoney(12_500.0))
        assertEquals("$18.8k", Fmt.compactMoney(18_800.0))
        assertEquals("$1.3M", Fmt.compactMoney(1_300_000.0))
        assertEquals("$2.4B", Fmt.compactMoney(2_400_000_000.0))
    }

    @Test
    fun `compact money fills the next unit rather than printing a thousand of the last`() {
        assertEquals("$1M", Fmt.compactMoney(999_960.0))
        assertEquals("$999.9k", Fmt.compactMoney(999_940.0))
        assertEquals("$1B", Fmt.compactMoney(999_999_000.0))
    }

    @Test
    fun `compact money never rounds a pool up across the tracking floor`() {
        // The floor is $10,000. Half-up would print "$10k" for anything from $9,950, so a row
        // would read "Pool holds $10k, too thin to track" beside a token tracked at that size.
        assertEquals("$10k", Fmt.compactMoney(9_960.0))
        assertEquals("$9.9k", Fmt.compactMoney(9_960.0, roundDown = true))
        assertEquals("$9.9k", Fmt.compactMoney(9_999.0, roundDown = true))
        assertEquals("$10k", Fmt.compactMoney(10_000.0, roundDown = true))
        // Truncation changes nothing that was already exact, and keeps small pools whole.
        assertEquals("$34", Fmt.compactMoney(34.0, roundDown = true))
        assertEquals("$948", Fmt.compactMoney(948.6, roundDown = true))
        assertEquals("$12.5k", Fmt.compactMoney(12_500.0, roundDown = true))
        assertEquals("$1.3M", Fmt.compactMoney(1_300_000.0, roundDown = true))
        assertEquals("-$1.2k", Fmt.compactMoney(-1_290.0, roundDown = true))
    }

    @Test
    fun `compact money signs a negative amount before the dollar`() {
        assertEquals("-$1.2k", Fmt.compactMoney(-1_200.0))
        assertEquals("-$34", Fmt.compactMoney(-34.0))
        assertEquals("-$0.40", Fmt.compactMoney(-0.4))
    }

    @Test
    fun `compact money renders a non-finite amount as the neutral placeholder`() {
        assertEquals("-", Fmt.compactMoney(Double.NaN))
        assertEquals("-", Fmt.compactMoney(Double.POSITIVE_INFINITY))
    }

    // ---- percent --------------------------------------------------------------------------

    @Test
    fun `percent is signed with two decimals and zero carries no sign`() {
        assertEquals("+0.09%", Fmt.percent(0.09))
        assertEquals("-0.04%", Fmt.percent(-0.04))
        // Device QA of 1.3.17: zero has no direction, so "0.00%", never "+0.00%".
        assertEquals("0.00%", Fmt.percent(0.0))
        assertEquals("0.00%", Fmt.percent(-0.0))
        assertEquals("+1,234.57%", Fmt.percent(1234.567))
    }

    @Test
    fun `percent that rounds to zero loses its minus`() {
        assertEquals("0.00%", Fmt.percent(-0.004))
        assertEquals("0.00%", Fmt.percent(0.004))
        assertEquals("-0.01%", Fmt.percent(-0.005))
        assertEquals("+0.01%", Fmt.percent(0.005))
    }

    @Test
    fun `percent unsigned drops the plus and keeps the minus`() {
        assertEquals("9.80%", Fmt.percent(9.8, signed = false))
        assertEquals("100.70%", Fmt.percent(100.7, signed = false))
        assertEquals("-0.50%", Fmt.percent(-0.5, signed = false))
        assertEquals("0.00%", Fmt.percent(0.0, signed = false))
        assertEquals("1.4%", Fmt.percent(1.44, signed = false, decimals = 1))
        assertEquals("-", Fmt.percent(Double.NaN))
    }

    // ---- count, slot, decimal -------------------------------------------------------------

    @Test
    fun `count groups thousands with commas`() {
        assertEquals("0", Fmt.count(0))
        assertEquals("999", Fmt.count(999))
        assertEquals("1,000", Fmt.count(1_000))
        assertEquals("26,101", Fmt.count(26_101))
        assertEquals("-26,101", Fmt.count(-26_101))
    }

    @Test
    fun `count survives the extremes of a Long`() {
        assertEquals("9,223,372,036,854,775,807", Fmt.count(Long.MAX_VALUE))
        assertEquals("-9,223,372,036,854,775,808", Fmt.count(Long.MIN_VALUE))
    }

    @Test
    fun `slot is a grouped count`() {
        assertEquals("445,912,118", Fmt.slot(445_912_118L))
        assertEquals("0", Fmt.slot(0L))
    }

    @Test
    fun `decimal keeps exactly the requested decimals`() {
        assertEquals("0.71", Fmt.decimal(0.71))
        assertEquals("1.00", Fmt.decimal(1.0))
        assertEquals("51", Fmt.decimal(51.0, decimals = 0))
        assertEquals("-0.50", Fmt.decimal(-0.5))
        assertEquals("1,234.57", Fmt.decimal(1234.567))
        assertEquals("-", Fmt.decimal(Double.NaN))
    }

    @Test
    fun `plain trims trailing zeros for captions and scales`() {
        assertEquals("0.5", Fmt.plain(0.5))
        assertEquals("1", Fmt.plain(1.0))
        assertEquals("1,000.25", Fmt.plain(1000.25))
        assertEquals("0.000001", Fmt.plain(0.0000014))
        assertEquals("0", Fmt.plain(0.0000004))
        assertEquals("2.5", Fmt.plain(2.4999, maxDecimals = 2))
        assertEquals("-2.5", Fmt.plain(-2.5))
        assertEquals("-", Fmt.plain(Double.NaN))
    }

    // ---- token amounts --------------------------------------------------------------------

    @Test
    fun `token amount trims trailing zeros up to six decimals`() {
        assertEquals("2.01364", Fmt.tokenAmount(BigDecimal("2.013640")))
        assertEquals("2", Fmt.tokenAmount(BigDecimal("2.000000")))
        assertEquals("100", Fmt.tokenAmount(BigDecimal("100.000000")))
        assertEquals("0", Fmt.tokenAmount(BigDecimal.ZERO))
        assertEquals("0.01364", Fmt.tokenAmount(0.01364))
        assertEquals("0.013646", Fmt.tokenAmount(0.0136456))
        assertEquals("1,000.5", Fmt.tokenAmount(1000.5))
        assertEquals("-0.5", Fmt.tokenAmount(-0.5))
        assertEquals("-", Fmt.tokenAmount(Double.NaN))
    }

    @Test
    fun `token amount from base units applies the on-chain decimals`() {
        assertEquals("5", Fmt.tokenAmount(5_000_000L, decimals = 6))
        assertEquals("0.01364", Fmt.tokenAmount(13_640_000L, decimals = 9))
        assertEquals("0.083", Fmt.tokenAmount(83_000_000L, decimals = 9))
        assertEquals("0.012346", Fmt.tokenAmount(1_234_567L, decimals = 8))
        assertEquals("0.01234567", Fmt.tokenAmount(1_234_567L, decimals = 8, maxDecimals = 8))
        assertEquals("0", Fmt.tokenAmount(0L, decimals = 6))
    }

    // ---- absolute time --------------------------------------------------------------------

    @Test
    fun `utc prints day, sentence-case month, year and a zero-padded time`() {
        assertEquals("10 Sep 2026 14:55 UTC", Fmt.utc(now))
        assertEquals("1 Jan 2026 00:05 UTC", Fmt.utc(Instant.parse("2026-01-01T00:05:00Z")))
        assertEquals("31 Dec 2025 23:59 UTC", Fmt.utc(1_767_225_599_000L))
    }

    @Test
    fun `a calendar day prints without a time`() {
        assertEquals("12 Sep 2026", Fmt.day(LocalDate.of(2026, 9, 12)))
        assertEquals("1 Jan 2026", Fmt.day(LocalDate.of(2026, 1, 1)))
        assertEquals("31 Dec 2025", Fmt.day(LocalDate.of(2025, 12, 31)))
    }

    /**
     * Changed 2026-09-26 (audit, item 4): the month-first `monthDay` ("Oct 22") was retired so the
     * app prints one short date format, day first, the one Today's own headings already used
     * ("Monday 28 Sep"). The same dates, the same no-year rule, now day before month.
     */
    @Test
    fun `day month drops the year, day first`() {
        assertEquals("22 Oct", Fmt.dayMonth(LocalDate.of(2026, 10, 22)))
        assertEquals("1 Jan", Fmt.dayMonth(LocalDate.of(2026, 1, 1)))
        assertEquals("31 Dec", Fmt.dayMonth(LocalDate.of(2025, 12, 31)))
    }

    // ---- relative time --------------------------------------------------------------------

    @Test
    fun `relative ago climbs the ladder at exact unit boundaries`() {
        fun ago(seconds: Long) = Fmt.relativeAgo(now.minusSeconds(seconds), now)
        assertEquals("0 s ago", ago(0L))
        assertEquals("2 s ago", ago(2L))
        assertEquals("59 s ago", ago(59L))
        assertEquals("1 min ago", ago(60L))
        assertEquals("5 min ago", ago(5L * 60 + 59))
        assertEquals("59 min ago", ago(3_599L))
        assertEquals("1 h ago", ago(3_600L))
        assertEquals("3 h ago", ago(3L * 3_600 + 1_799))
        assertEquals("23 h ago", ago(86_399L))
        assertEquals("1 d ago", ago(86_400L))
        assertEquals("2 d ago", ago(2L * 86_400 + 3_600))
        assertEquals("1,000 d ago", ago(1_000L * 86_400))
    }

    @Test
    fun `a time after now reads as zero seconds ago`() {
        assertEquals("0 s ago", Fmt.relativeAgo(now.plusSeconds(30), now))
    }

    @Test
    fun `age old uses the same ladder with the old suffix`() {
        assertEquals("2 d old", Fmt.ageOld(now.minusSeconds(2L * 86_400), now))
        assertEquals("3 h old", Fmt.ageOld(now.minusSeconds(3L * 3_600), now))
        assertEquals("45 s old", Fmt.ageOld(now.minusSeconds(45L), now))
    }

    // `days old takes the server count as is` moved to CountCopyTest (2026-09-26): the age is
    // counted copy now ("1 day old", "1,204 days old", list_row_age_days), not an Fmt suffix.

    // ---- durations ------------------------------------------------------------------------

    @Test
    fun `a running phase counts whole seconds, floored and never negative`() {
        assertEquals("0 s", Fmt.seconds(0L))
        assertEquals("a phase 310 ms in has not reached a second", "0 s", Fmt.seconds(310L))
        assertEquals("9 s", Fmt.seconds(9_900L))
        assertEquals("14 s", Fmt.seconds(14_400L))
        assertEquals("a clock read before the phase began is not a negative wait", "0 s", Fmt.seconds(-500L))
        assertEquals("1,000 s", Fmt.seconds(1_000_000L))
    }

    @Test
    fun `a measured phase keeps its tenth`() {
        assertEquals("3.1 s", Fmt.secondsExact(3_100L))
        assertEquals("the wallet round trip, as measured on the Seeker", "12.7 s", Fmt.secondsExact(12_700L))
        assertEquals("14.4 s", Fmt.secondsExact(14_400L))
        assertEquals("a whole second keeps no trailing zero", "3 s", Fmt.secondsExact(3_000L))
        assertEquals("0.3 s", Fmt.secondsExact(310L))
        assertEquals("0 s", Fmt.secondsExact(-500L))
    }

    // ---- keys -----------------------------------------------------------------------------

    @Test
    fun `short key keeps head and tail around one ellipsis character`() {
        val short = Fmt.shortKey("3kF9abcdefghijQm2v")
        assertEquals("3kF9…Qm2v", short)
        assertEquals(9, short.length)
        assertEquals('…', short[4])
        assertEquals("3kF9ab…cdQm2v", Fmt.shortKey("3kF9abXXcdQm2v", head = 6, tail = 6))
    }

    @Test
    fun `short key returns a key no longer than head plus tail whole`() {
        assertEquals("3kF9abcd", Fmt.shortKey("3kF9abcd"))
        assertEquals("abc", Fmt.shortKey("abc"))
        assertEquals("", Fmt.shortKey(""))
    }

    @Test
    fun `short key rejects negative head or tail`() {
        assertThrows(IllegalArgumentException::class.java) { Fmt.shortKey("abcdefghij", head = -1) }
        assertThrows(IllegalArgumentException::class.java) { Fmt.shortKey("abcdefghij", tail = -1) }
    }

    // ---- The reader's own time -------------------------------------------------------------

    @Test
    fun `local date time reads the reader's own zone and carries no UTC label`() {
        val closeInSeptember = Instant.parse("2026-09-24T20:00:00Z").toEpochMilli()
        assertEquals("24 Sep 2026 23:00", Fmt.localDateTime(closeInSeptember, java.time.ZoneId.of("Europe/Kyiv")))
        assertEquals("24 Sep 2026 16:00", Fmt.localDateTime(closeInSeptember, java.time.ZoneId.of("America/New_York")))
        // A zone ahead of UTC can carry the stamp into the next calendar day, the same as utc()
        // would for a different instant; this is the same day/month/year math, zone-shifted.
        val lateUtc = Instant.parse("2026-09-24T23:30:00Z").toEpochMilli()
        assertEquals("25 Sep 2026 08:30", Fmt.localDateTime(lateUtc, java.time.ZoneId.of("+09:00")))
    }

    @Test
    fun `clock reads the reader's own zone, 24-hour, through daylight saving`() {
        val closeInSeptember = Instant.parse("2026-09-24T20:00:00Z").toEpochMilli()
        assertEquals("23:00", Fmt.clock(closeInSeptember, java.time.ZoneId.of("Europe/Kyiv")))
        assertEquals("16:00", Fmt.clock(closeInSeptember, java.time.ZoneId.of("America/New_York")))
        val closeInNovember = Instant.parse("2026-11-02T21:00:00Z").toEpochMilli()
        assertEquals("the same 16:00 close, both zones off daylight saving", "23:00", Fmt.clock(closeInNovember, java.time.ZoneId.of("Europe/Kyiv")))
        assertEquals("09:05", Fmt.clock(Instant.parse("2026-09-24T06:05:00Z").toEpochMilli(), java.time.ZoneId.of("Europe/Kyiv")))
    }

    @Test
    fun `weekday and days ahead are the reader's own calendar, not UTC's`() {
        val kyiv = java.time.ZoneId.of("Europe/Kyiv")
        val lateThursdayUtc = Instant.parse("2026-09-24T22:30:00Z").toEpochMilli() // already Friday in Kyiv
        assertEquals("Friday", Fmt.weekday(lateThursdayUtc, kyiv))
        assertEquals("Thursday", Fmt.weekday(lateThursdayUtc, java.time.ZoneId.of("America/New_York")))
        val mondayOpen = Instant.parse("2026-09-28T13:30:00Z").toEpochMilli()
        assertEquals(3L, Fmt.daysAhead(mondayOpen, lateThursdayUtc, kyiv))
        assertEquals(0L, Fmt.daysAhead(lateThursdayUtc, lateThursdayUtc, kyiv))
        assertEquals(-1L, Fmt.daysAhead(Instant.parse("2026-09-24T12:00:00Z").toEpochMilli(), lateThursdayUtc, kyiv))
    }
}
