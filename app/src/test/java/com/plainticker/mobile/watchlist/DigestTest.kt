package com.plainticker.mobile.watchlist

import com.plainticker.mobile.data.plainticker.VoteRound
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The digest is a pure function of its inputs (plan T12: "deterministic 12 h release digest").
 * These tests are what that claim means: the same rows twice give the identical string, the order
 * of the input does not reach the output, and nothing in the sentence comes from a clock.
 */
class DigestTest {

    private val today = LocalDate.of(2026, 9, 13)

    private fun text(input: DigestInput) = digest(input).text(RealStrings.strings)

    // ---- Determinism ----------------------------------------------------------------------

    @Test
    fun `the same inputs twice give the identical string`() {
        val rows = listOf(
            watchedAt("TSLA", premiumPct = 0.09, nextReport = LocalDate.of(2026, 10, 24)),
            watchedAt("NVDA", premiumPct = -0.61, nextReport = LocalDate.of(2026, 11, 19)),
            watchedAt("AAPL", premiumPct = 0.01, nextReport = LocalDate.of(2026, 10, 28)),
        )
        val previous = mapOf("NVDA" to -0.04, "TSLA" to 0.08)
        val first = text(DigestInput(today, rows, previous))
        val second = text(DigestInput(today, rows, previous))

        assertEquals(first, second)
        assertEquals(
            "3 stocks watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 41 days.",
            first,
        )
    }

    @Test
    fun `the order the rows arrive in does not reach the digest`() {
        val rows = listOf(
            watchedAt("AAPL", premiumPct = 0.01, nextReport = LocalDate.of(2026, 10, 28)),
            watchedAt("NVDA", premiumPct = -0.61, nextReport = LocalDate.of(2026, 11, 19)),
            watchedAt("TSLA", premiumPct = 0.09, nextReport = LocalDate.of(2026, 10, 24)),
        )
        val previous = mapOf("NVDA" to -0.04, "AAPL" to 0.9)
        assertEquals(
            text(DigestInput(today, rows, previous)),
            text(DigestInput(today, rows.reversed(), previous)),
        )
    }

    @Test
    fun `two moves of the same size are still in one fixed order`() {
        val rows = listOf(watchedAt("AAPL", premiumPct = 1.0), watchedAt("NVDA", premiumPct = 1.0))
        val previous = mapOf("AAPL" to 0.0, "NVDA" to 0.0)
        val lines = digest(DigestInput(today, rows, previous)).lines.filterIsInstance<DigestLine.Moved>()
        assertEquals(listOf("AAPLx", "NVDAx"), lines.map { it.symbol })
        assertEquals(lines, digest(DigestInput(today, rows.reversed(), previous)).lines.filterIsInstance<DigestLine.Moved>())
    }

    // ---- What it says ---------------------------------------------------------------------

    @Test
    fun `nothing watched says nothing at all`() {
        val nothing = digest(DigestInput(today, emptyList()))
        assertFalse(nothing.hasNews)
        assertEquals("", nothing.text(RealStrings.strings))
        assertNull(nothing.nextReport)
    }

    @Test
    fun `watched tickers with no report ahead and no move are not news`() {
        val rows = listOf(watched("AAPL"), watched("TSLA"))
        val result = digest(DigestInput(today, rows))
        assertFalse("the count alone is not worth a notification", result.hasNews)
        assertEquals(listOf(DigestLine.Watched(2)), result.lines)
    }

    @Test
    fun `a report date that has passed is not news`() {
        val rows = listOf(watched("AAPL", nextReport = today.minusDays(1)))
        assertFalse(digest(DigestInput(today, rows)).hasNews)
    }

    @Test
    fun `the nearest report ahead is the one named`() {
        val rows = listOf(
            watched("NVDA", nextReport = LocalDate.of(2026, 11, 19)),
            watched("AAPL", nextReport = LocalDate.of(2026, 9, 16)),
            watched("TSLA", nextReport = null),
        )
        val result = digest(DigestInput(today, rows))
        assertEquals(WatchedReport("AAPL", "AAPLx", LocalDate.of(2026, 9, 16)), result.nextReport)
        assertEquals("3 stocks watched. AAPLx reports in 3 days.", result.text(RealStrings.strings))
    }

    @Test
    fun `today and tomorrow are said in words, never as a count of days`() {
        assertEquals(
            "1 stock watched. AAPLx reports today.",
            text(DigestInput(today, listOf(watched("AAPL", nextReport = today)))),
        )
        assertEquals(
            "1 stock watched. AAPLx reports tomorrow.",
            text(DigestInput(today, listOf(watched("AAPL", nextReport = today.plusDays(1))))),
        )
    }

    @Test
    fun `a move under half a point is inside the band and is not said`() {
        val rows = listOf(watchedAt("AAPL", premiumPct = 0.40))
        assertFalse(digest(DigestInput(today, rows, mapOf("AAPL" to 0.0))).hasNews)
        assertTrue(digest(DigestInput(today, rows, mapOf("AAPL" to -0.11))).hasNews)
    }

    @Test
    fun `a premium the liquidity floor withheld can never move`() {
        // A pool of $34, the APPx case measured on 2026-09-12: the quote exists, the premium does
        // not, so there is nothing to compare and nothing to say.
        val rows = listOf(watchedAt("APP", premiumPct = 89.34, poolUsd = 34.0))
        val result = digest(DigestInput(today, rows, mapOf("APP" to 0.0)))
        assertFalse(result.hasNews)
        assertTrue("a withheld premium is not a baseline either", result.premiums.isEmpty())
    }

    @Test
    fun `a first run has no baseline, so it names no move`() {
        val rows = listOf(watchedAt("NVDA", premiumPct = -0.61, nextReport = LocalDate.of(2026, 11, 19)))
        val result = digest(DigestInput(today, rows))
        assertEquals("1 stock watched. NVDAx reports in 67 days.", result.text(RealStrings.strings))
        assertEquals(setOf("NVDA"), result.premiums.keys)
    }

    @Test
    fun `at most two moves, the largest first`() {
        val rows = listOf(
            watchedAt("AAPL", premiumPct = 1.0),
            watchedAt("NVDA", premiumPct = 3.0),
            watchedAt("TSLA", premiumPct = 2.0),
        )
        val previous = mapOf("AAPL" to 0.0, "NVDA" to 0.0, "TSLA" to 0.0)
        val moved = digest(DigestInput(today, rows, previous)).lines.filterIsInstance<DigestLine.Moved>()
        assertEquals(listOf("NVDAx", "TSLAx"), moved.map { it.symbol })
    }

    @Test
    fun `a ticker with no token reads as its ticker, not as a blank`() {
        val rows = listOf(watched("AAPL", symbol = null, nextReport = today.plusDays(2)))
        assertEquals("1 stock watched. AAPL reports in 2 days.", text(DigestInput(today, rows)))
    }

    @Test
    fun `the baseline it hands on is what it saw, not what it said`() {
        val rows = listOf(watchedAt("AAPL", premiumPct = 0.2), watchedAt("NVDA", premiumPct = -0.4))
        val result = digest(DigestInput(today, rows))
        assertFalse("neither moved far enough to be said", result.hasNews)
        assertEquals(setOf("AAPL", "NVDA"), result.premiums.keys)
    }

    @Test
    fun `every sentence of the shipped copy ends the digest as one paragraph`() {
        val rows = listOf(watchedAt("NVDA", premiumPct = -0.61, nextReport = today.plusDays(3)))
        val line = text(DigestInput(today, rows, mapOf("NVDA" to -0.04)))
        assertTrue("every clause ends in a period", line.endsWith("."))
        assertEquals("three sentences, one space between them", 3, line.split(". ").size)
        assertFalse("no middle dots in a digest", line.contains('\u00B7'))
        assertTrue("one line, never a paragraph break", '\n' !in line)
    }

    // ---- Reports this week among covered companies, watched or not ------------------------

    @Test
    fun `covered companies reporting within the week are said, watched or not`() {
        val rows = listOf(watched("AAPL"))
        val dates = listOf(today.plusDays(1), today.plusDays(6), today.plusDays(9))
        val result = digest(DigestInput(today, rows, coveredReportDates = dates))
        assertEquals(
            "1 stock watched. 2 covered companies report this week.",
            result.text(RealStrings.strings),
        )
    }

    @Test
    fun `a single covered company this week reads as one, not as many`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, coveredReportDates = listOf(today)))
        assertEquals("1 stock watched. 1 covered company reports this week.", result.text(RealStrings.strings))
    }

    @Test
    fun `a report exactly a week and a day out is not this week`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, coveredReportDates = listOf(today.plusDays(7))))
        assertFalse(result.hasNews)
    }

    @Test
    fun `a report the day before today is not this week either`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, coveredReportDates = listOf(today.minusDays(1))))
        assertFalse(result.hasNews)
    }

    // ---- The vote ---------------------------------------------------------------------------

    @Test
    fun `a round in progress is said by its number and when it closes`() {
        val rows = listOf(watched("AAPL"))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val result = digest(DigestInput(today, rows, voteRound = round))
        assertEquals("1 stock watched. Round 3 closes Monday.", result.text(RealStrings.strings))
    }

    @Test
    fun `the previous winner is named once this run has confirmed it is analysed`() {
        val rows = listOf(watched("AAPL"))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val result = digest(DigestInput(today, rows, voteRound = round, analysedWinner = "JEF"))
        assertEquals(
            "1 stock watched. Round 3 closes Monday. JEF, last round's winner, is now analysed.",
            result.text(RealStrings.strings),
        )
    }

    @Test
    fun `a winner named with no round in progress still gets its own sentence`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF"))
        assertEquals("1 stock watched. JEF, last round's winner, is now analysed.", result.text(RealStrings.strings))
    }

    @Test
    fun `an unparsable close date says nothing rather than a guess`() {
        val rows = listOf(watched("AAPL"))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "not-a-date")
        val result = digest(DigestInput(today, rows, voteRound = round))
        assertFalse(result.hasNews)
    }

    // ---- The notification's shorter reading -------------------------------------------------

    @Test
    fun `a quiet day but for the vote still gets a notification worth sending`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF"))
        assertTrue(result.hasNews)
        assertEquals(result.text(RealStrings.strings), result.headline(RealStrings.strings))
    }

    @Test
    fun `the headline keeps the count and the single highest-priority clause, never every one`() {
        val rows = listOf(watchedAt("NVDA", premiumPct = -0.61, nextReport = today.plusDays(3)))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val result = digest(
            DigestInput(
                today, rows, mapOf("NVDA" to -0.04),
                coveredReportDates = listOf(today),
                voteRound = round,
                analysedWinner = "JEF",
            ),
        )
        val full = result.text(RealStrings.strings)
        val headline = result.headline(RealStrings.strings)
        assertEquals("the fuller reading carries all six clauses", 6, full.split(". ").size)
        assertEquals(
            "the count, then the day's biggest move, and nothing past it",
            "1 stock watched. NVDAx moved from -0.04% to -0.61% against the NYSE close.",
            headline,
        )
        assertNotEquals("the two readings really do diverge here", full, headline)
    }
}
