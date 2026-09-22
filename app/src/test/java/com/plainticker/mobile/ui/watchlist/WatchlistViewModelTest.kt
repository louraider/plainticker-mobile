package com.plainticker.mobile.ui.watchlist

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Forward
import com.plainticker.mobile.data.plainticker.ForwardRaw
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.repo.xStockTrading
import com.plainticker.mobile.ui.today.TrackedRow
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.FakeDigestNotifier
import com.plainticker.mobile.watchlist.InMemoryDigestStore
import com.plainticker.mobile.watchlist.WatchlistFacts
import com.plainticker.mobile.watchlist.WatchlistScheduler
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Watchlist screen's states: nothing watched, a row per watched ticker, a source that did not
 * answer, and the digest the check stored arriving while the screen is open.
 */
class WatchlistViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val summaries = FakeSummaryRepository()
    private val catalog = FakeCatalogRepository()
    private val prices = FakePriceRepository()
    private val nextUp = FakeNextUpRepository()
    private val digests = InMemoryDigestStore()
    private val notifier = FakeDigestNotifier()
    private val scheduler = RecordingScheduler()
    private val clock = Clock { NOW }

    private class RecordingScheduler : WatchlistScheduler {
        val scheduled = mutableListOf<Duration>()
        var cancels = 0
            private set
        var runs = 0
            private set

        override fun scheduleDaily(initialDelay: Duration) {
            scheduled += initialDelay
        }

        override fun cancel() {
            cancels++
        }

        override fun runNow() {
            runs++
        }
    }

    private fun viewModel(watching: Set<String> = emptySet()) = WatchlistViewModel(
        watchlist = InMemoryWatchlistStore(watching),
        facts = WatchlistFacts(summaries, catalog, prices),
        digests = digests,
        notifier = notifier,
        scheduler = scheduler,
        clock = clock,
        summaries = summaries,
        catalog = catalog,
        prices = prices,
        nextUpRepo = nextUp,
    )

    private fun serving(vararg tickers: String) {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-13T08:00:00.000Z",
                rows = tickers.map { SummaryRow(ticker = it, company = "$it Inc.") },
            ),
        )
        catalog.assets = Result.success(tickers.map { xStock("${it}x", it, "mint-$it") })
        summaries.analyses = tickers.associateWith {
            Result.success(
                AnalysisPayload(ticker = it, forward = Forward(ForwardRaw(nextEarningsDate = "2026-10-28"))),
            )
        }
    }

    @Test
    fun `nothing watched is the empty state, and the watched-ticker join itself asks nothing of the network`() = runTest {
        val vm = viewModel()

        vm.state.test {
            // Today's own join (blocks 1, 3 and 4) always asks once, watched or not; waiting for
            // it to settle is what makes the call count below deterministic rather than a race.
            val state = awaitUntil { !it.todayLoading }
            assertTrue(state.isEmpty)
            assertFalse(state.isCold)
            assertNull(state.banner)
            assertEquals("Today's join asks once; an empty watchlist costs WatchlistFacts nothing beyond it", 1, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a watched ticker becomes a row with its report date and its premium`() = runTest {
        serving("AAPL")
        prices.result = Result.success(mapOf("mint-AAPL" to price(usd = 232.54, reference = 232.52)))
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            val state = awaitUntil { it.rows.isNotEmpty() }
            val row = state.rows.single()
            assertEquals("AAPL", row.ticker)
            assertEquals("AAPLx", row.symbol)
            assertEquals(LocalDate.of(2026, 10, 28), row.nextReport)
            assertEquals(NOW, state.nowMillis)
            assertFalse(state.isEmpty)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unwatching takes the row off the screen`() = runTest {
        serving("AAPL", "TSLA")
        val vm = viewModel(setOf("AAPL", "TSLA"))

        vm.state.test {
            awaitUntil { it.rows.size == 2 }
            vm.unwatch("AAPL")
            val state = awaitUntil { it.rows.size == 1 }
            assertEquals("TSLA", state.rows.single().ticker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unwatching the last one returns the empty state`() = runTest {
        serving("AAPL")
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            awaitUntil { it.rows.isNotEmpty() }
            vm.unwatch("AAPL")
            val state = awaitUntil { it.isEmpty && it.rows.isEmpty() }
            assertTrue(state.rows.isEmpty())
            assertNull("an empty list raises no banner", state.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an analysis that did not answer raises the first banner tier and keeps the row`() = runTest {
        summaries.summaryResult = Result.failure(ApiException(503, "summary", "unavailable", null))
        catalog.assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL")))
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            val state = awaitUntil { it.banner != null }
            assertEquals(WatchlistBanner.AnalysisUnavailable, state.banner)
            assertEquals("AAPL", state.rows.single().ticker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the digest the check stores reaches the open screen`() = runTest {
        serving("AAPL")
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            awaitUntil { it.rows.isNotEmpty() }
            digests.save(DigestRecord(text = "1 stock watched. AAPLx reports in 45 days.", producedAtMillis = NOW))
            val state = awaitUntil { it.digest.text != null }
            assertEquals("1 stock watched. AAPLx reports in 45 days.", state.digest.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the notification setting is read at the start and again when it may have changed`() = runTest {
        notifier.on = false
        val vm = viewModel()

        vm.state.test {
            assertFalse(awaitItem().notificationsOn)
            notifier.on = true
            vm.notificationsChanged()
            assertTrue(awaitUntil { it.notificationsOn }.notificationsOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the debug entry point fires the check through the scheduler`() = runTest {
        val vm = viewModel()

        vm.runCheckNow()

        assertEquals(1, scheduler.runs)
    }

    // ---- Today's other blocks (docs/design-research-2026-09-21.md section 3, blocks 1/3/4/5) ---

    @Test
    fun `today's join reads the wider universe, tracked rows sorted by depth, thin pools excluded`() = runTest {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-13T08:00:00.000Z",
                rows = listOf(
                    SummaryRow(ticker = "AAPL", company = "Apple Inc."),
                    SummaryRow(ticker = "TSLA", company = "Tesla, Inc."),
                    SummaryRow(ticker = "THIN", company = "Thin Pool Co."),
                ),
            ),
        )
        catalog.assets = Result.success(
            listOf(
                xStock("AAPLx", "AAPL", "mint-AAPL"),
                xStock("TSLAx", "TSLA", "mint-TSLA"),
                xStock("THINx", "THIN", "mint-THIN"),
                // Neither in /summary nor priced below: the one row without analysis.
                xStock("NOPRICEx", "NOPRICE", "mint-NOPRICE"),
            ),
        )
        prices.result = Result.success(
            mapOf(
                "mint-AAPL" to price(usd = 232.54, reference = 232.52, liquidity = 250_000.0),
                "mint-TSLA" to price(usd = 366.50, reference = 366.17, liquidity = 500_000.0),
                // Below TrackingQuality.MIN_POOL_USD (4,000): thin, no tracked row anywhere.
                "mint-THIN" to price(usd = 50.0, reference = 49.0, liquidity = 1_000.0),
            ),
        )
        val vm = viewModel()

        vm.state.test {
            val state = awaitUntil { !it.todayLoading }
            assertEquals("AAPL, TSLA and THIN all classify against a matching xStock", 3, state.analyzedTotal)
            assertEquals("NOPRICEx is the one catalog asset with no classification", 1, state.withoutAnalysisTotal)
            assertEquals("THIN's pool is below the floor, so it is not a tracked row", 2, state.tracked.size)
            val tickers: List<String> = state.tracked.map(TrackedRow::ticker)
            assertEquals("deepest pool first: TSLA at 500k, then AAPL at 250k", listOf("TSLA", "AAPL"), tickers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `today's Next up leader is the heaviest uncovered ticker, with the round it belongs to`() = runTest {
        catalog.assets = Result.success(listOf(xStock("AMDx", "AMD", "mint-AMD")))
        nextUp.answer = Result.success(
            NextUpAnswer.Open(
                rows = listOf(NextUpRow(ticker = "AMD", weight = "6719000000", voters = 1)),
                round = VoteRound(id = 2, opensAt = "2026-09-21T00:00:00Z", closesAt = "2026-09-28T00:00:00.000Z"),
                previous = null,
            ),
        )
        val vm = viewModel()

        vm.state.test {
            val state = awaitUntil { !it.todayLoading }
            val leader = requireNotNull(state.nextUpLeader)
            assertEquals("AMDx", leader.display)
            assertEquals("AMD xStock", leader.company)
            assertEquals(2, state.voteRound?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `today's venue reads the catalog's own trading block, and freshness reads both ages`() = runTest {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-12T12:00:00.000Z",
                rows = listOf(SummaryRow(ticker = "AAPL", company = "Apple Inc.")),
            ),
        )
        catalog.assets = Result.success(
            listOf(
                xStockTrading("AAPLx", "AAPL", "mint-AAPL", Trading(currentPeriod = TradingPeriod.MARKET, openNow = true)),
            ),
        )
        prices.result = Result.success(mapOf("mint-AAPL" to price(usd = 232.54, reference = 232.52)))
        val vm = viewModel()

        vm.state.test {
            val state = awaitUntil { !it.todayLoading }
            assertTrue("the venue's own trading block says the session is on", state.market?.regularSession == true)
            assertEquals(MarketSource.VENUE, state.market?.source)
            assertTrue("the analysis age is read off generatedAt", state.analysisGeneratedAtMillis != null)
            assertTrue("the price age is read off when this device asked Jupiter", state.pricesFetchedAtMillis != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry re-runs today's join too`() = runTest {
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.todayLoading }
            val before = summaries.summaryCalls
            vm.refresh()
            awaitUntil { !it.todayLoading && summaries.summaryCalls > before }
            assertEquals(before + 1, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val NOW = 1_789_257_600_000L
    }
}
