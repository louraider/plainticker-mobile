package com.plainticker.mobile.watchlist

import com.plainticker.mobile.data.plainticker.VoteRound
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "NVDAx moved from -0.04% to -0.61% against its share's US price. TSLAx reports in 41 days.",
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
        assertFalse("the watchlist merely existing is not worth a notification", result.hasNews)
        assertEquals(emptyList<DigestLine>(), result.lines)
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
        assertEquals("AAPLx reports in 3 days.", result.text(RealStrings.strings))
    }

    @Test
    fun `today and tomorrow are said in words, never as a count of days`() {
        assertEquals(
            "AAPLx reports today.",
            text(DigestInput(today, listOf(watched("AAPL", nextReport = today)))),
        )
        assertEquals(
            "AAPLx reports tomorrow.",
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
        assertEquals("NVDAx reports in 67 days.", result.text(RealStrings.strings))
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
        assertEquals("AAPL reports in 2 days.", text(DigestInput(today, rows)))
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
        assertEquals("two sentences, one space between them", 2, line.split(". ").size)
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
            "2 covered companies report this week.",
            result.text(RealStrings.strings),
        )
    }

    @Test
    fun `a single covered company this week reads as one, not as many`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, coveredReportDates = listOf(today)))
        assertEquals("1 covered company reports this week.", result.text(RealStrings.strings))
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
        assertEquals("Round 3 closes Monday 28 Sep at 00:00 your time.", result.text(RealStrings.strings))
    }

    /** QA of 1.3.20: on Monday 28 Sep, the day round 3 opened, the digest said "Round 3 closes Monday." */
    @Test
    fun `a round closing a week out names its date and the reader's clock, by the shared round rule`() {
        val rows = listOf(watched("AAPL"))
        val round = VoteRound(id = 3, opensAt = "2026-09-28T00:00:00.000Z", closesAt = "2026-10-05T00:00:00.000Z")
        val now = java.time.Instant.parse("2026-09-28T08:34:00Z").toEpochMilli()
        val kyiv = java.time.ZoneId.of("Europe/Kyiv")
        val result = digest(DigestInput(LocalDate.of(2026, 9, 28), rows, voteRound = round, nowMillis = now, readerZone = kyiv))
        assertEquals("Round 3 closes Monday 5 Oct at 03:00 your time.", result.text(RealStrings.strings))
    }

    @Test
    fun `the previous winner is named once this run has confirmed it is analysed`() {
        val rows = listOf(watched("AAPL"))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val result = digest(DigestInput(today, rows, voteRound = round, analysedWinner = "JEF"))
        assertEquals(
            "JEF, last round's winner, is now analyzed. Round 3 closes Monday 28 Sep at 00:00 your time.",
            result.text(RealStrings.strings),
        )
    }

    @Test
    fun `a winner named with no round in progress still gets its own sentence`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF"))
        assertEquals("JEF, last round's winner, is now analyzed.", result.text(RealStrings.strings))
    }

    @Test
    fun `an unparsable close date says nothing rather than a guess`() {
        val rows = listOf(watched("AAPL"))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "not-a-date")
        val result = digest(DigestInput(today, rows, voteRound = round))
        assertFalse(result.hasNews)
    }

    // ---- The notification: the most useful line as its title ------------------------------------

    @Test
    fun `a quiet day but for the vote still gets a notification, titled with it and opening the stock`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF"))
        assertTrue(result.hasNews)
        val notice = result.notice(RealStrings.strings)!!
        assertEquals("JEF, last round's winner, is now analyzed", notice.title)
        assertEquals("the title is the whole of the news", "", notice.body)
        assertEquals("JEF", notice.ticker)
        assertFalse(notice.opensVote)
    }

    @Test
    fun `the title is the most useful line and the body the next two, never every clause`() {
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
        assertEquals("the fuller reading carries all five clauses", 5, full.split(". ").size)
        val notice = result.notice(RealStrings.strings)!!
        assertEquals("NVDAx moved from -0.04% to -0.61% against its share's US price", notice.title)
        assertEquals("NVDAx reports in 3 days. JEF, last round's winner, is now analyzed.", notice.body)
        assertEquals("a move opens the stock that moved", "NVDA", notice.ticker)
        assertFalse("no digest ever opens on a count of what is watched", full.contains("watched"))
    }

    @Test
    fun `nothing to say posts nothing`() {
        assertNull(digest(DigestInput(today, listOf(watched("AAPL")))).notice(RealStrings.strings))
    }

    @Test
    fun `a report tomorrow outranks a move, and opens that stock`() {
        val rows = listOf(
            watchedAt("NVDA", premiumPct = 3.0),
            watched("AAPL", nextReport = today.plusDays(1)),
        )
        val result = digest(DigestInput(today, rows, mapOf("NVDA" to 0.0)))
        val notice = result.notice(RealStrings.strings)!!
        assertEquals("AAPLx reports tomorrow", notice.title)
        assertEquals("AAPL", notice.ticker)
        assertEquals("NVDAx moved from 0.00% to +3.00% against its share's US price.", notice.body)
    }

    // ---- The reader's own pick ----------------------------------------------------------------

    @Test
    fun `the reader's own pick analysed is said personally, first, and opens the stock`() {
        val rows = listOf(watched("AAPL", nextReport = today.plusDays(1)))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF", votedForWinner = true))
        assertEquals(
            "JEF, which you voted for, is now analyzed. AAPLx reports tomorrow.",
            result.text(RealStrings.strings),
        )
        val notice = result.notice(RealStrings.strings)!!
        assertEquals("JEF, which you voted for, is now analyzed", notice.title)
        assertEquals("JEF", notice.ticker)
        assertEquals("JEF", result.personalPick)
    }

    @Test
    fun `a pick already announced personally is not said again`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF", votedForWinner = true, announcedPick = "JEF"))
        assertFalse("the reader was told, and the stock is on their list by now", result.hasNews)
        assertNull(result.personalPick)
    }

    @Test
    fun `a winner the reader did not vote for is named the ordinary way, never as theirs`() {
        val rows = listOf(watched("AAPL"))
        val result = digest(DigestInput(today, rows, analysedWinner = "JEF", votedForWinner = false))
        assertEquals("JEF, last round's winner, is now analyzed.", result.text(RealStrings.strings))
        assertNull(result.personalPick)
    }

    // ---- A round closing tonight on the reader's clock ----------------------------------------

    @Test
    fun `a round closing later today in the reader's zone leads, with the reader's clock time, and opens Vote`() {
        val rows = listOf(watchedAt("NVDA", premiumPct = 3.0))
        // 20:00 in New York on Sunday 27 September is 00:00 UTC on Monday 28 September.
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val now = java.time.Instant.parse("2026-09-27T20:00:00Z").toEpochMilli()
        val result = digest(
            DigestInput(
                LocalDate.of(2026, 9, 27), rows, mapOf("NVDA" to 0.0),
                voteRound = round,
                nowMillis = now,
                readerZone = java.time.ZoneId.of("America/New_York"),
            ),
        )
        val notice = result.notice(RealStrings.strings)!!
        assertEquals("Round 3 closes today at 20:00 your time", notice.title)
        assertTrue(notice.opensVote)
        assertNull(notice.ticker)
    }

    @Test
    fun `the same round closing on another day of the reader's calendar keeps its dated line, last`() {
        val rows = listOf(watchedAt("NVDA", premiumPct = 3.0))
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val now = java.time.Instant.parse("2026-09-27T20:00:00Z").toEpochMilli()
        val result = digest(
            DigestInput(
                LocalDate.of(2026, 9, 27), rows, mapOf("NVDA" to 0.0),
                voteRound = round,
                nowMillis = now,
                readerZone = java.time.ZoneId.of("Europe/Berlin"),
            ),
        )
        assertEquals(
            "NVDAx moved from 0.00% to +3.00% against its share's US price. Round 3 closes Monday at 02:00 your time.",
            result.text(RealStrings.strings),
        )
    }
}
