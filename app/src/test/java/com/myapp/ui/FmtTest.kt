package com.myapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

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

    // ---- percent --------------------------------------------------------------------------

    @Test
    fun `percent is signed with two decimals and zero is positive`() {
        assertEquals("+0.09%", Fmt.percent(0.09))
        assertEquals("-0.04%", Fmt.percent(-0.04))
        assertEquals("+0.00%", Fmt.percent(0.0))
        assertEquals("+0.00%", Fmt.percent(-0.0))
        assertEquals("+1,234.57%", Fmt.percent(1234.567))
    }

    @Test
    fun `percent that rounds to zero loses its minus`() {
        assertEquals("+0.00%", Fmt.percent(-0.004))
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
        assertEquals("0.013666", Fmt.tokenAmount(1_366_647L, decimals = 8))
        assertEquals("0.01366647", Fmt.tokenAmount(1_366_647L, decimals = 8, maxDecimals = 8))
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
    fun `month day drops the year`() {
        assertEquals("Oct 22", Fmt.monthDay(Instant.parse("2026-10-22T20:00:00Z")))
        assertEquals("Jan 1", Fmt.monthDay(Instant.parse("2026-01-01T00:00:00Z")))
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

    @Test
    fun `days old takes the server count as is`() {
        assertEquals("2 d old", Fmt.daysOld(2))
        assertEquals("0 d old", Fmt.daysOld(0))
        assertEquals("1,204 d old", Fmt.daysOld(1_204))
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
}
