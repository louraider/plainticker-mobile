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
import com.plainticker.mobile.repo.Gate
import com.plainticker.mobile.repo.HeldPriceRepository
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
            // The tracked rows are what prices answers, so this waits for trackedLoading, not
            // todayLoading: todayLoading now settles before prices are even asked (see
            // WatchlistViewModel.loadToday's own doc), and state.tracked would still be empty at
            // that point.
            val state = awaitUntil { !it.trackedLoading }
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
            // Next up reads no price, so it is on state as soon as todayLoading settles, not
            // trackedLoading: this is the fast half of the join.
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
            // pricesFetchedAtMillis is trackedLoading's own field, published once prices answer,
            // not todayLoading's: waiting for the fuller settle is what this assertion on both
            // ages actually needs.
            val state = awaitUntil { !it.trackedLoading }
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

    /**
     * The animator-zero stall (docs/qa-checklist.md, 2026-09-22): Today's venue card and "Tracked
     * today" sat undrawn or skeletal for three to four seconds after launch at normal motion, eight
     * to eleven with every animator scale forced to zero. A motion agent's `amberBlockEntrance` was
     * innocent - it already snaps to its settled state on the next frame when motion is off, which
     * `TodayScreenTest` pins directly - so animation being off could not be *why* the screen took
     * longer to read. The actual cause was here, one step further than the fix this test used to
     * pin: [WatchlistViewModel.loadToday] already asked `/summary`, the catalog and the leaderboard
     * concurrently, but it published every field from the join, including the venue line and Next
     * up, in the one `_state.update` at the very end, after awaiting Jupiter's own paced price
     * fetch (several seconds by [PriceRepository]'s own design), even though neither block reads a
     * price. This test holds prices open and checks that the fast half - [todayLoading], [market]
     * (by way of the catalog), [nextUpLeader] - is already on state regardless: in the coupled
     * shape this test used to pin, [todayLoading] stayed true until the held gate released, which
     * is the bug reproduced in a unit test rather than on a phone. Only [trackedLoading] (Tracked
     * today's own rows, the one thing that does need a price) may still be true here.
     */
    @Test
    fun `today's venue and Next up settle while prices are still out, not after them`() = runTest {
        serving("AAPL")
        nextUp.answer = Result.success(
            NextUpAnswer.Open(rows = listOf(NextUpRow(ticker = "AAPL", weight = "1", voters = 1)), round = null, previous = null),
        )
        val jupiterGate = Gate()
        val heldPrices = HeldPriceRepository(
            jupiterGate,
            FakePriceRepository(Result.success(mapOf("mint-AAPL" to price(usd = 232.54, reference = 232.52)))),
        )
        val vm = WatchlistViewModel(
            watchlist = InMemoryWatchlistStore(),
            facts = WatchlistFacts(summaries, catalog, prices),
            digests = digests,
            notifier = notifier,
            scheduler = scheduler,
            clock = clock,
            summaries = summaries,
            catalog = catalog,
            prices = heldPrices,
            nextUpRepo = nextUp,
        )

        advanceUntilIdle()

        assertEquals("the price fetch is out but held", 1, heldPrices.calls)
        val holding = vm.state.value
        assertFalse("the venue and Next up half does not wait behind prices", holding.todayLoading)
        assertEquals("AAPLx", holding.nextUpLeader?.display)
        assertTrue("the venue reads a market the catalog alone already answered", holding.market != null)
        assertTrue("Tracked today's own rows are the one thing still out", holding.trackedLoading)
        assertTrue("nothing is tracked yet, because prices have not answered", holding.tracked.isEmpty())
        assertEquals(
            "the leaderboard does not depend on prices and must not wait behind them",
            1,
            nextUp.currentCalls,
        )

        jupiterGate.release()
        advanceUntilIdle()

        val settled = vm.state.value
        assertFalse(settled.todayLoading)
        assertFalse("prices have now answered too", settled.trackedLoading)
        assertEquals("AAPLx", settled.nextUpLeader?.display)
    }

    // ---- The venue as a clock (Today direction A, the stale "Closed" of 24 Sep 2026) ---------------

    /** A clock the test moves, the way the phone's own clock moves while Today sits in the background. */
    private class MovingClock(var now: Long) : Clock {
        override fun nowMillis(): Long = now
    }

    private fun utc(text: String): Long = java.time.Instant.parse(text).toEpochMilli()

    /** A catalog whose one live block answers [live], counting who asked. */
    private class LiveCatalog(
        private val inner: FakeCatalogRepository,
        var live: Trading? = null,
    ) : com.plainticker.mobile.repo.CatalogRepository by inner {
        val asked = mutableListOf<String>()
        override suspend fun liveTrading(symbol: String): Trading? {
            asked += symbol
            return live
        }
    }

    private fun clockedViewModel(clock: Clock, catalogRepo: com.plainticker.mobile.repo.CatalogRepository) = WatchlistViewModel(
        watchlist = InMemoryWatchlistStore(),
        facts = WatchlistFacts(summaries, catalogRepo, prices),
        digests = digests,
        notifier = notifier,
        scheduler = scheduler,
        clock = clock,
        summaries = summaries,
        catalog = catalogRepo,
        prices = prices,
        nextUpRepo = nextUp,
    )

    private fun cachedBlock(period: TradingPeriod, openNow: Boolean, nextChangeAt: String) {
        catalog.assets = Result.success(
            listOf(xStockTrading("AAPLx", "AAPL", "mint-AAPL", Trading(currentPeriod = period, openNow = openNow, nextChangeAt = nextChangeAt))),
        )
    }

    /**
     * The Seeker's own bug as a unit test. The catalog was cached before the open, when the venue
     * said "closed, changing at 13:30 UTC". Today was left in the background and brought back at
     * 19:22 UTC, mid-session. Before the fix, nothing recomputed the venue on resume and the block
     * was replayed verbatim: "Closed". Now the resume ticks, the expired block stops answering, and
     * the calendar says the session is on.
     */
    @Test
    fun `resume after the snapshot's nextChangeAt flips a stale closed venue to open`() = runTest {
        cachedBlock(TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-24T13:30:00Z")
        val clock = MovingClock(utc("2026-09-24T12:00:00Z"))
        val vm = clockedViewModel(clock, catalog)
        advanceUntilIdle()
        val before = vm.state.value.market!!
        assertFalse("before the open the venue's block is fresh and says closed", before.regularSession)
        assertEquals(MarketSource.VENUE, before.source)

        clock.now = utc("2026-09-24T19:22:00Z")
        try {
            vm.onResume()
            val after = vm.state.value.market!!
            assertTrue("the resume recomputed the venue: the session is on", after.regularSession)
            assertEquals("and it says the calendar answered, not the venue", MarketSource.LOCAL_SCHEDULE, after.source)
            assertEquals("the next change is the close", utc("2026-09-24T20:00:00Z"), after.nextChangeAtMillis)
        } finally {
            vm.onPause()
        }
    }

    @Test
    fun `an expired block is refreshed with one asset's live block, once`() = runTest {
        cachedBlock(TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-24T13:30:00Z")
        val live = LiveCatalog(catalog, Trading(currentPeriod = TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-24T20:00:00Z"))
        val clock = MovingClock(utc("2026-09-24T19:22:00Z"))
        val vm = clockedViewModel(clock, live)
        advanceUntilIdle()
        try {
            vm.onResume()
            runCurrent()
            val market = vm.state.value.market!!
            assertEquals("the live block answers once it lands", MarketSource.VENUE, market.source)
            assertTrue(market.regularSession)
            assertEquals(listOf("AAPLx"), live.asked)
            vm.onPause()
            vm.onResume()
            runCurrent()
            assertEquals("a fresh block is not asked for again", listOf("AAPLx"), live.asked)
        } finally {
            vm.onPause()
        }
    }

    @Test
    fun `while resumed the boundary job ticks at the close, and paused nothing ticks`() = runTest {
        cachedBlock(TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-24T20:00:00Z")
        val clock = MovingClock(utc("2026-09-24T19:59:00Z"))
        val vm = clockedViewModel(clock, catalog)
        advanceUntilIdle()
        try {
            vm.onResume()
            assertTrue(vm.state.value.market!!.regularSession)

            // Resumed: the job sleeps until a second past the close, then ticks.
            clock.now = utc("2026-09-24T20:00:01Z")
            advanceTimeBy(61_001)
            runCurrent()
            assertFalse("the boundary job flipped it at the close, with no resume", vm.state.value.market!!.regularSession)

            // Paused: the clock passes the next boundary and nothing recomputes.
            vm.onPause()
            clock.now = utc("2026-09-25T13:31:00Z")
            advanceTimeBy(24 * 60 * 60 * 1_000L)
            runCurrent()
            assertFalse("paused, the venue is not recomputed in the background", vm.state.value.market!!.regularSession)

            // And the next resume catches up.
            vm.onResume()
            assertTrue(vm.state.value.market!!.regularSession)
        } finally {
            vm.onPause()
        }
    }

    /**
     * One price read: the Seeker drew METAx at +0.15% in Watched and +0.13% in Tracked on one
     * screen, because each block priced it separately. A watched row now takes its figure from
     * Today's own read whenever that read covers it, whichever of the two loads lands first.
     */
    @Test
    fun `a watched row is priced from today's own read, so two blocks never disagree`() = runTest {
        serving("AAPL", "TSLA")
        val split = object : com.plainticker.mobile.repo.PriceRepository {
            override suspend fun prices(mints: Collection<String>) = pricesFirst(mints.toList()).priced
            override suspend fun pricesFirst(mints: List<String>, limit: Int): com.plainticker.mobile.data.jupiter.PriceFetch {
                // Today's join asks for every analyzed mint; the watched load for its one.
                val aapl = if (mints.size > 1) price(usd = 101.0, reference = 100.0) else price(usd = 102.0, reference = 100.0)
                return com.plainticker.mobile.data.jupiter.PriceFetch(
                    priced = mapOf("mint-AAPL" to aapl, "mint-TSLA" to price(usd = 100.0, reference = 100.0)).filterKeys { it in mints },
                )
            }
        }
        val vm = WatchlistViewModel(
            watchlist = InMemoryWatchlistStore(setOf("AAPL")),
            facts = WatchlistFacts(summaries, catalog, split),
            digests = digests,
            notifier = notifier,
            scheduler = scheduler,
            clock = clock,
            summaries = summaries,
            catalog = catalog,
            prices = split,
            nextUpRepo = nextUp,
        )
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.trackedLoading)
        val row = state.rows.single()
        assertEquals("the watched row reads today's own price", 1.0, row.premiumPct!!, 1e-9)
        assertEquals(setOf("AAPL"), state.watchedTickers)
    }

    @Test
    fun `watch from Today asks for notifications once, after the first watch, and never again`() = runTest {
        val prompts = com.plainticker.mobile.prefs.InMemoryNotificationPromptStore()
        val store = InMemoryWatchlistStore()
        val vm = WatchlistViewModel(
            watchlist = store,
            facts = WatchlistFacts(summaries, catalog, prices),
            digests = digests,
            notifier = notifier,
            scheduler = scheduler,
            clock = clock,
            summaries = summaries,
            catalog = catalog,
            prices = prices,
            nextUpRepo = nextUp,
            prompts = prompts,
        )
        assertTrue("the first watch asks", vm.watch("NVDA"))
        assertEquals(setOf("NVDA"), store.tickers.value)
        assertFalse("a second watch does not ask again", vm.watch("COIN"))
        assertFalse("a ticker already watched is not watched twice, and asks nothing", vm.watch("NVDA"))
        store.remove("NVDA")
        store.remove("COIN")
        assertFalse("a refusal is an answer: emptying the list and watching again does not re-ask", vm.watch("TSLA"))
    }

    private companion object {
        const val NOW = 1_789_257_600_000L
    }
}
