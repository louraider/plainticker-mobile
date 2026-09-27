package com.plainticker.mobile.watchlist

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Forward
import com.plainticker.mobile.data.plainticker.ForwardRaw
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.xStock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily check, end to end over fakes: no WorkManager, no network and no device.
 *
 * What is pinned here is the four outcomes and, more than any of them, what each one does to the
 * stored digest. The record is the only thing a notification and a screen share, so a run that
 * fails must not touch it, a run with nothing to say must not blank it, and a run that repeats
 * itself must not announce it twice.
 */
class WatchlistCheckTest {

    private val day = Instant.parse("2026-09-13T08:00:00Z").toEpochMilli()
    private val dayAfter = Instant.parse("2026-09-14T08:00:00Z").toEpochMilli()

    private class TestClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private val watchlist = InMemoryWatchlistStore(setOf("AAPL"))
    private val summaries = FakeSummaryRepository()
    private val catalog = FakeCatalogRepository()
    private val prices = FakePriceRepository()
    private val digests = InMemoryDigestStore()
    private val notifier = FakeDigestNotifier()
    private val clock = TestClock(day)

    private val check = WatchlistCheck(
        watchlist = watchlist,
        facts = WatchlistFacts(summaries, catalog, prices),
        digests = digests,
        strings = RealStrings.strings,
        notifier = notifier,
        clock = clock,
    )

    private fun serves(vararg tickers: String) {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-13T08:00:00.000Z",
                rows = tickers.map { SummaryRow(ticker = it, company = "$it Inc.") },
            ),
        )
        catalog.assets = Result.success(tickers.map { xStock("${it}x", it, "mint-$it") })
    }

    private fun reports(ticker: String, on: String?) {
        val payload = AnalysisPayload(
            ticker = ticker,
            company = "$ticker Inc.",
            forward = Forward(ForwardRaw(nextEarningsDate = on)),
        )
        summaries.analyses = summaries.analyses + (ticker to Result.success(payload))
    }

    // ---- Produced -------------------------------------------------------------------------

    @Test
    fun `a run with news writes the digest and posts the same string`() = runTest {
        serves("AAPL")
        reports("AAPL", "2026-10-28")

        val outcome = check.run()

        val text = "AAPLx reports in 45 days."
        assertEquals(CheckOutcome.Produced(text), outcome)
        assertEquals(text, digests.record.value.text)
        assertEquals(day, digests.record.value.producedAtMillis)
        assertEquals(day, digests.record.value.lastCheckedAtMillis)
        // One clause, so the title is that clause without its period and the body is empty;
        // DigestTest covers the title and body splitting a longer digest.
        assertEquals(listOf("AAPLx reports in 45 days"), notifier.posted)
        assertEquals("", notifier.notices.single().body)
        assertEquals("the title names AAPLx, so a tap opens it", "AAPL", notifier.notices.single().ticker)
        assertEquals(WatchedReport("AAPL", "AAPLx", LocalDate.of(2026, 10, 28)), digests.record.value.nextReport)
    }

    // ---- Nothing to say -------------------------------------------------------------------

    @Test
    fun `nothing watched produces no notification and no digest`() = runTest {
        watchlist.remove("AAPL")

        assertEquals(CheckOutcome.NothingToSay, check.run())

        assertTrue(notifier.posted.isEmpty())
        assertNull(digests.record.value.text)
        assertEquals("it still records that it ran", day, digests.record.value.lastCheckedAtMillis)
        assertEquals("and it did not go near the network", 0, summaries.summaryCalls)
    }

    @Test
    fun `a run that finds nothing worth saying sends nothing`() = runTest {
        serves("AAPL")
        reports("AAPL", null)

        assertEquals(CheckOutcome.NothingToSay, check.run())

        assertTrue(notifier.posted.isEmpty())
        assertNull(digests.record.value.text)
    }

    @Test
    fun `a run with nothing to say does not blank the digest it already had`() = runTest {
        serves("AAPL")
        reports("AAPL", "2026-10-28")
        check.run()
        val produced = digests.record.value

        // The next day the provider has dropped the report date and nothing moved.
        reports("AAPL", null)
        clock.now = dayAfter

        assertEquals(CheckOutcome.NothingToSay, check.run())
        assertEquals(produced.text, digests.record.value.text)
        assertEquals(produced.producedAtMillis, digests.record.value.producedAtMillis)
        assertEquals(dayAfter, digests.record.value.lastCheckedAtMillis)
        assertEquals(1, notifier.posted.size)
    }

    // ---- Unchanged ------------------------------------------------------------------------

    @Test
    fun `the same digest twice is announced once`() = runTest {
        serves("AAPL")
        reports("AAPL", "2026-10-28")
        assertTrue(check.run() is CheckOutcome.Produced)

        assertEquals(CheckOutcome.Unchanged, check.run())
        assertEquals(1, notifier.posted.size)
        assertEquals(day, digests.record.value.producedAtMillis)
    }

    // ---- Failed ---------------------------------------------------------------------------

    @Test
    fun `a failed run keeps the last digest and writes nothing`() = runTest {
        serves("AAPL")
        reports("AAPL", "2026-10-28")
        check.run()
        val produced = digests.record.value
        val writes = digests.writes

        summaries.summaryResult =
            Result.failure(ApiException(503, "www.plainticker.com/api/v1/summary", "unavailable", null))
        clock.now = dayAfter

        assertEquals(CheckOutcome.Failed, check.run())
        assertEquals(produced, digests.record.value)
        assertEquals("a failed run writes nothing at all", writes, digests.writes)
        assertEquals(1, notifier.posted.size)
    }

    // ---- The premium baseline -------------------------------------------------------------

    @Test
    fun `a move is measured against what the previous run saw`() = runTest {
        watchlist.remove("AAPL")
        watchlist.add("NVDA")
        serves("NVDA")
        reports("NVDA", "2026-11-19")
        prices.result = Result.success(mapOf("mint-NVDA" to price(usd = 99.96, reference = 100.0)))

        assertTrue(check.run() is CheckOutcome.Produced)
        assertEquals(setOf("NVDA"), digests.record.value.premiums.keys)

        prices.result = Result.success(mapOf("mint-NVDA" to price(usd = 99.39, reference = 100.0)))
        clock.now = dayAfter

        assertEquals(
            CheckOutcome.Produced(
                "NVDAx moved from -0.04% to -0.61% against the NYSE close. NVDAx reports in 66 days.",
            ),
            check.run(),
        )
        assertEquals(2, notifier.posted.size)
    }

    // ---- Covered companies reporting this week, watched or not -----------------------------

    @Test
    fun `a company reporting this week that nobody watches still reaches the digest`() = runTest {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-13T08:00:00.000Z",
                rows = listOf(
                    SummaryRow(ticker = "AAPL", company = "AAPL Inc."),
                    // Not watched, and carries its own report date: the fact this test is for.
                    SummaryRow(ticker = "JEF", company = "Jefferies Financial Group Inc.", nextReportDate = "2026-09-15"),
                ),
            ),
        )
        catalog.assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL")))

        assertEquals(
            CheckOutcome.Produced("1 covered company reports this week."),
            check.run(),
        )
    }

    // ---- The vote ----------------------------------------------------------------------------

    @Test
    fun `the round closing and a live-analysed previous winner reach the digest and the shade`() = runTest {
        serves("AAPL")
        val nextUp = FakeNextUpRepository(
            answer = Result.success(
                NextUpAnswer.Open(
                    rows = emptyList(),
                    round = VoteRound(id = 2, opensAt = "2026-09-07T00:00:00.000Z", closesAt = "2026-09-14T00:00:00.000Z"),
                    previous = PreviousRound(id = 1, winner = "JEF", status = "closed"),
                ),
            ),
        )
        summaries.analyses = summaries.analyses + ("JEF" to Result.success(AnalysisPayload(ticker = "JEF")))
        val votingCheck = WatchlistCheck(
            watchlist = watchlist,
            facts = WatchlistFacts(summaries, catalog, prices),
            digests = digests,
            strings = RealStrings.strings,
            notifier = notifier,
            clock = clock,
            vote = VoteDigestFacts(nextUp, summaries),
        )

        val outcome = votingCheck.run()

        val full = "JEF, last round's winner, is now analyzed. Round 2 closes Monday."
        assertEquals(CheckOutcome.Produced(full), outcome)
        assertEquals(full, digests.record.value.text)
        assertEquals(
            "the shade's title is the most useful line, here the winner, and the round follows it",
            listOf("JEF, last round's winner, is now analyzed"),
            notifier.posted,
        )
    }

    @Test
    fun `a check with no vote collaborator at all carries no vote line, exactly as before this task`() = runTest {
        serves("AAPL")
        reports("AAPL", "2026-10-28")
        assertEquals(
            CheckOutcome.Produced("AAPLx reports in 45 days."),
            check.run(),
        )
    }

    // ---- Closing the loop per voter --------------------------------------------------------------

    private fun votingRound() = FakeNextUpRepository(
        answer = Result.success(
            NextUpAnswer.Open(
                rows = emptyList(),
                round = VoteRound(id = 2, opensAt = "2026-09-07T00:00:00.000Z", closesAt = "2026-09-14T00:00:00.000Z"),
                previous = PreviousRound(id = 1, winner = "JEF", status = "closed"),
            ),
        ),
    )

    private fun receipt(ticker: String, round: Int?) = com.plainticker.mobile.data.receipts.VoteReceipt(
        signature = "sig-$ticker-$round",
        ticker = ticker,
        symbol = "${ticker}x",
        weightRaw = 1_000_000L,
        landedAtMillis = day,
        voter = "voter",
        round = round,
    )

    @Test
    fun `a voter's pick analysed is said personally once, watched first, and a tap opens it`() = runTest {
        // JEF was voted for in round 1 and remembered as pending; overnight it gets analysed.
        serves("AAPL", "JEF")
        summaries.analyses = summaries.analyses + ("JEF" to Result.success(AnalysisPayload(ticker = "JEF")))
        val receipts = com.plainticker.mobile.data.receipts.FakeVoteReceiptStore().apply { record(receipt("JEF", 1)) }
        val pending = InMemoryPendingWatchStore(setOf("JEF"))
        val votingCheck = WatchlistCheck(
            watchlist = watchlist,
            facts = WatchlistFacts(summaries, catalog, prices),
            digests = digests,
            strings = RealStrings.strings,
            notifier = notifier,
            clock = clock,
            vote = VoteDigestFacts(votingRound(), summaries, receipts),
            autoWatch = AutoWatch(watchlist, pending, summaries, catalog),
        )

        val outcome = votingCheck.run()

        assertTrue("the pick is watched before the digest reads the list", "JEF" in watchlist.tickers.value)
        assertTrue("and no longer pending", pending.tickers.isEmpty())
        val text = (outcome as CheckOutcome.Produced).text
        assertTrue(text, text.startsWith("JEF, which you voted for, is now analyzed."))
        assertEquals("JEF, which you voted for, is now analyzed", notifier.notices.single().title)
        assertEquals("JEF", notifier.notices.single().ticker)
        assertEquals("the record keeps that it was said", "JEF", digests.record.value.announcedPick)

        // The next day: nothing else changed, so the pick is not announced a second time.
        clock.now = dayAfter
        votingCheck.run()
        assertEquals(1, notifier.notices.count { it.title.contains("which you voted for") })
    }

    @Test
    fun `a winner this device never voted for is never called the reader's own`() = runTest {
        serves("AAPL")
        summaries.analyses = summaries.analyses + ("JEF" to Result.success(AnalysisPayload(ticker = "JEF")))
        val receipts = com.plainticker.mobile.data.receipts.FakeVoteReceiptStore().apply { record(receipt("TSM", 1)) }
        val votingCheck = WatchlistCheck(
            watchlist = watchlist,
            facts = WatchlistFacts(summaries, catalog, prices),
            digests = digests,
            strings = RealStrings.strings,
            notifier = notifier,
            clock = clock,
            vote = VoteDigestFacts(votingRound(), summaries, receipts),
        )

        val text = (votingCheck.run() as CheckOutcome.Produced).text
        assertTrue(text, text.startsWith("JEF, last round's winner, is now analyzed."))
        assertNull(digests.record.value.announcedPick)
    }
}
