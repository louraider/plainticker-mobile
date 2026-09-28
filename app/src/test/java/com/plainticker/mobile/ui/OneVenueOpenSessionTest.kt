package com.plainticker.mobile.ui

import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.R
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.PriceLabel
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.prefs.InMemoryNotificationPromptStore
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeMintRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSnapshotRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.VenueHours
import com.plainticker.mobile.repo.xStockTrading
import com.plainticker.mobile.ui.detail.DetailBanner
import com.plainticker.mobile.ui.detail.DetailUiState
import com.plainticker.mobile.ui.detail.DetailViewModel
import com.plainticker.mobile.ui.detail.banner
import com.plainticker.mobile.ui.detail.gaugeReference
import com.plainticker.mobile.ui.detail.priceRow
import com.plainticker.mobile.ui.list.ListViewModel
import com.plainticker.mobile.ui.today.statusLine
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel
import com.plainticker.mobile.watchlist.FakeDigestNotifier
import com.plainticker.mobile.watchlist.InMemoryDigestStore
import com.plainticker.mobile.watchlist.WatchlistFacts
import com.plainticker.mobile.watchlist.WatchlistScheduler
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * QA of 1.3.21: at 13:32 UTC on a trading day, Detail (METAx, GOOGLx) said "The NYSE is closed,
 * the reference is the last US price" while Today said "NYSE open. Closes at 23:00 your time".
 * Detail took its venue from the catalog's cached block, which still said CLOSED past its own
 * `nextChangeAt`; Today and Stocks read the shared [VenueHours] through their clocks. These run
 * the three screens on one shared state, inside the regular session, and hold them to one answer.
 */
class OneVenueOpenSessionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Tuesday 29 Sep 2026, 13:32 UTC: 09:32 in New York, two minutes into the regular session. */
    private val now = Instant.parse("2026-09-29T13:32:00Z").toEpochMilli()
    private val clock = Clock { now }
    private val kyiv = ZoneId.of("Europe/Kyiv")

    private val metaMint = "METAxMint".padEnd(44, '1')
    private val googlMint = "GOOGLxMint".padEnd(44, '1')

    /** What the catalog cached before the open: closed, with a change at 13:30 that has passed. */
    private val cachedBeforeOpen = Trading(currentPeriod = TradingPeriod.CLOSED, openNow = false, nextChangeAt = "2026-09-29T13:30:00Z")

    /** The live block the venue answers with once asked: the session, until the 16:00 close. */
    private val liveOpen = Trading(currentPeriod = TradingPeriod.MARKET, openNow = true, nextChangeAt = "2026-09-29T20:00:00Z")

    private fun catalog(shared: VenueHours, live: Trading?): CatalogRepository {
        val inner = FakeCatalogRepository(
            Result.success(
                listOf(
                    xStockTrading("METAx", "META", metaMint, cachedBeforeOpen, "Meta Platforms xStock"),
                    xStockTrading("GOOGLx", "GOOGL", googlMint, cachedBeforeOpen, "Alphabet xStock"),
                ),
            ),
        )
        return object : CatalogRepository by inner {
            override val venueHours: VenueHours = shared
            override suspend fun liveTrading(symbol: String): Trading? = live
        }
    }

    private fun detail(ticker: String, catalog: CatalogRepository) = DetailViewModel(
        ticker, FakeSummaryRepository(), catalog, FakePriceRepository(), FakeMintRepository(), FakeNextUpRepository(),
        InMemoryWatchlistStore(), InMemoryNotificationPromptStore(), clock,
    )

    private fun stocks(catalog: CatalogRepository) = ListViewModel(
        FakeSummaryRepository(), catalog, FakePriceRepository(), FakeSnapshotRepository(), FakeNextUpRepository(),
        InMemoryWatchlistStore(), InMemoryDigestStore(), clock,
    )

    private fun today(catalog: CatalogRepository): WatchlistViewModel {
        val summaries = FakeSummaryRepository()
        val prices = FakePriceRepository()
        return WatchlistViewModel(
            watchlist = InMemoryWatchlistStore(),
            facts = WatchlistFacts(summaries, catalog, prices),
            digests = InMemoryDigestStore(),
            notifier = FakeDigestNotifier(),
            scheduler = object : WatchlistScheduler {
                override fun scheduleDaily(initialDelay: Duration) = Unit
                override fun cancel() = Unit
                override fun runNow() = Unit
            },
            clock = clock,
            summaries = summaries,
            catalog = catalog,
            prices = prices,
            nextUpRepo = FakeNextUpRepository(),
        )
    }

    /** Every open-state claim Detail makes: the state, the banner, and both labels. */
    private fun assertDetailOpen(state: DetailUiState, who: String) {
        assertNotNull("$who has a venue", state.market)
        val market = state.market!!
        assertTrue("$who reads the session", market.regularSession)
        assertEquals("$who labels the reference live", PriceLabel.TRACKING_WITHIN, state.priceLabel)
        assertNull("$who draws no closed banner", state.banner)
        assertEquals(R.string.detail_gauge_reference_live, (state.gaugeReference as Copy.Words).id)
        assertEquals(R.string.detail_nyse_price, (state.priceRow.referenceLabel as Copy.Words).id)
    }

    @Test
    fun `inside the session Detail, Today and Stocks read one open venue from the shared hours`() = runTest {
        val shared = VenueHours()
        val catalog = catalog(shared, live = liveOpen)
        val todayVm = today(catalog)
        val stocksVm = stocks(catalog)
        val metaVm = detail("META", catalog)
        val googlVm = detail("GOOGL", catalog)
        advanceUntilIdle()

        val todayMarket = todayVm.state.value.market
        assertEquals(MarketSource.VENUE, todayMarket?.source)
        assertEquals("NYSE open. Closes at 23:00 your time", ShippedCopy.render(statusLine(todayMarket, now, kyiv)!!))

        val stocksState = stocksVm.state.value
        assertTrue(stocksState.market!!.regularSession)
        assertNull("Stocks says nothing about an open NYSE", stocksState.banner)

        assertDetailOpen(metaVm.state.value, "METAx")
        assertDetailOpen(googlVm.state.value, "GOOGLx")
        // One state, not three that happen to agree.
        assertEquals(todayMarket?.state, metaVm.state.value.market?.state)
        assertEquals(todayMarket?.nextChangeAtMillis, metaVm.state.value.market?.nextChangeAtMillis)
        assertEquals(todayMarket?.state, stocksState.market?.state)
    }

    @Test
    fun `Detail never reports closed while the shared state is open, even on a cached closed block`() = runTest {
        val shared = VenueHours()
        // Today already read the live hours; Detail's own live read finds nothing.
        shared.offer(liveOpen)
        val catalog = catalog(shared, live = null)

        val vm = detail("META", catalog)
        val seen = mutableListOf<DetailUiState>()
        // Bounded time only: while observed, Detail re-reads the wall clock every second, and a
        // resumed clock sleeps to the next boundary for ever, so advanceUntilIdle would never end.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        advanceTimeBy(3_000L)
        runCurrent()
        vm.onResume()
        advanceTimeBy(3_000L)
        runCurrent()
        vm.onPause()
        vm.refresh() // a refresh rebuilds the state and keeps the shared venue
        runCurrent()
        vm.pull()
        advanceTimeBy(3_000L)
        runCurrent()

        assertTrue(seen.isNotEmpty())
        seen.forEach { state ->
            assertTrue("never a closed banner", state.banner != DetailBanner.CLOSED && state.banner != DetailBanner.CLOSED_LOCAL)
            assertDetailOpen(state, "every Detail frame")
        }
        assertEquals(MarketSource.VENUE, vm.state.value.market?.source)
    }

    @Test
    fun `a cold start inside the session reads open from the first frame, before any live hours land`() = runTest {
        val vm = detail("META", catalog(VenueHours(), live = null))
        // The calendar stands in from construction; the catalog's stale CLOSED never wins.
        assertDetailOpen(vm.state.value, "the first frame")
        assertEquals(MarketSource.LOCAL_SCHEDULE, vm.state.value.market?.source)
        advanceUntilIdle()
        assertTrue(vm.state.value.market!!.regularSession)
        assertEquals(PriceLabel.TRACKING_WITHIN, vm.state.value.priceLabel)
        assertTrue("never the closed banner", vm.state.value.banner != DetailBanner.CLOSED)
    }
}
