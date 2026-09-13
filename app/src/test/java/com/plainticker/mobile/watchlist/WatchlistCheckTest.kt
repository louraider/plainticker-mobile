package com.plainticker.mobile.watchlist

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Forward
import com.plainticker.mobile.data.plainticker.ForwardRaw
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
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

        val text = "1 watched. AAPLx reports in 45 days."
        assertEquals(CheckOutcome.Produced(text), outcome)
        assertEquals(text, digests.record.value.text)
        assertEquals(day, digests.record.value.producedAtMillis)
        assertEquals(day, digests.record.value.lastCheckedAtMillis)
        assertEquals("the notification carries what the screen draws", listOf(text), notifier.posted)
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
                "1 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. NVDAx reports in 66 days.",
            ),
            check.run(),
        )
        assertEquals(2, notifier.posted.size)
    }
}
