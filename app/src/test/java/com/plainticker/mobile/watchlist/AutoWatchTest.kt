package com.plainticker.mobile.watchlist

import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.receipts.FakeReceiptStore
import com.plainticker.mobile.data.receipts.FakeVoteReceiptStore
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.xStock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoWatchTest {

    private val watchlist = InMemoryWatchlistStore()
    private val pending = InMemoryPendingWatchStore()
    private val summaries = FakeSummaryRepository()
    private val catalog = FakeCatalogRepository(
        assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL"), xStock("JEFx", "JEF", "mint-JEF"))),
    )
    private val auto = AutoWatch(watchlist, pending, summaries, catalog)

    private fun analyses(vararg tickers: String) {
        summaries.summaryResult = Result.success(
            SummaryResponse(schema = "v1.1", generatedAt = "2026-09-27T08:00:00.000Z", rows = tickers.map { SummaryRow(ticker = it) }),
        )
    }

    private fun vote(ticker: String, signature: String = "sig-$ticker") = VoteReceipt(
        signature = signature,
        ticker = ticker,
        symbol = "${ticker}x",
        weightRaw = 1_000_000L,
        landedAtMillis = 1L,
        voter = "voter",
        round = 3,
    )

    private fun swap(outputMint: String, signature: String) = SwapReceipt(
        signature = signature,
        inputMint = "usdc",
        inputSymbol = "USDC",
        inputAmountRaw = 1_000_000L,
        inputDecimals = 6,
        outputMint = outputMint,
        outputSymbol = "x",
        outputDecimals = 8,
        route = "jupiter",
        landedAtMillis = 1L,
    )

    @Test
    fun `a vote for an analysed stock watches it at once`() = runTest {
        analyses("AAPL")
        auto.voted("aapl")
        assertEquals(setOf("AAPL"), watchlist.tickers.value)
        assertTrue(pending.tickers.isEmpty())
    }

    @Test
    fun `a vote for a stock without analysis is remembered, and watched the day it is analysed`() = runTest {
        analyses("AAPL")
        auto.voted("JEF")
        assertTrue("not watched yet: there is nothing to read about it", watchlist.tickers.value.isEmpty())
        assertEquals(setOf("JEF"), pending.tickers)

        assertEquals("still not analysed, nothing changes", emptyList<String>(), auto.resolvePending())
        assertEquals(setOf("JEF"), pending.tickers)

        analyses("AAPL", "JEF")
        assertEquals(listOf("JEF"), auto.resolvePending())
        assertEquals(setOf("JEF"), watchlist.tickers.value)
        assertTrue(pending.tickers.isEmpty())
    }

    @Test
    fun `a summary that does not answer keeps the pick remembered rather than dropping it`() = runTest {
        summaries.summaryResult = Result.failure(ApiException(503, "summary", "unavailable", null))
        auto.voted("JEF")
        assertEquals(setOf("JEF"), pending.tickers)
        assertEquals(emptyList<String>(), auto.resolvePending())
        assertEquals(setOf("JEF"), pending.tickers)
    }

    @Test
    fun `a swap into a stock token watches that stock, and a swap back to USDC watches nothing`() = runTest {
        auto.swappedInto("mint-JEF")
        assertEquals(setOf("JEF"), watchlist.tickers.value)
        auto.swappedInto("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
        assertEquals(setOf("JEF"), watchlist.tickers.value)
    }

    @Test
    fun `only receipts recorded after the app started are acted on, so an old one is never re-watched`() = runTest(UnconfinedTestDispatcher()) {
        analyses("AAPL", "JEF")
        val votes = FakeVoteReceiptStore().apply { record(vote("AAPL", "old")) }
        val swaps = FakeReceiptStore().apply { record(swap("mint-AAPL", "old-swap")) }
        val followers = listOf(
            launch { auto.followVotes(votes.receipts) },
            launch { auto.followSwaps(swaps.receipts) },
        )
        assertTrue("a reader who took AAPL off the list keeps it off", watchlist.tickers.value.isEmpty())

        votes.record(vote("JEF", "new"))
        assertEquals(setOf("JEF"), watchlist.tickers.value)

        swaps.record(swap("mint-AAPL", "new-swap"))
        assertEquals(setOf("AAPL", "JEF"), watchlist.tickers.value)
        followers.forEach { it.cancel() }
    }

    @Test
    fun `nothing the reader watched is ever removed`() = runTest {
        watchlist.add("NVDA")
        analyses("AAPL")
        auto.voted("AAPL")
        auto.swappedInto("mint-JEF")
        assertEquals(setOf("NVDA", "AAPL", "JEF"), watchlist.tickers.value)
    }
}
