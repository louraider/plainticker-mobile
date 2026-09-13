package com.plainticker.mobile.ui.list

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.net.RateLimitedException
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.data.jupiter.PriceFetch
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.snapshot.SnapshotAsset
import com.plainticker.mobile.data.snapshot.SnapshotRow
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSnapshotRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.Gate
import com.plainticker.mobile.repo.HeldCatalogRepository
import com.plainticker.mobile.repo.HeldPriceRepository
import com.plainticker.mobile.repo.HeldSummaryRepository
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.SnapshotRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.DigestStore
import com.plainticker.mobile.watchlist.InMemoryDigestStore
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.snapshot
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.repo.xStockTrading
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.repo.CatalogUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class ListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    // Synthetic mints: the ViewModel never validates them, only joins on them.
    private val aaplMint = "AAPLxMint".padEnd(44, '1')
    private val jpmMint = "JPMxMint".padEnd(44, '1')
    private val tslaMint = KnownMints.TSLAX

    private val summaryJson: String = Fixtures.read("plainticker/summary.json")

    /** The Ukrainian headline the fixture carries, read from the fixture so no test hardcodes it. */
    private val ukrainianHeadline: String =
        Regex("\"headline\":\\s*\"([^\"]+)\"").find(summaryJson)!!.groupValues[1]

    private val cyrillic = Regex("[\\u0400-\\u04FF]")

    /** plainticker/summary.json: AAPL 0.71, JPM 0.66 (stale, 9 d), XYZ with every field null. */
    private fun summary(): SummaryResponse =
        HttpClientFactory.json.decodeFromString(SummaryResponse.serializer(), summaryJson)

    /**
     * Monday 2026-09-14, 10:00 in New York: the NYSE is open, by the calendar and by the block
     * below alike. Fixed, because the hours banner is a function of the clock and a test that
     * reads the wall clock changes its mind at 09:30 New York every weekday.
     */
    private val marketOpen = Clock { 1_789_394_400_000L }

    /** The same week, 17:00 in New York: the exchange has shut and the close is last night's. */
    private val marketShut = Clock { 1_789_419_600_000L }

    /**
     * The catalog carries a `trading` block on every asset, as production does: the xStocks
     * catalog is filtered to assets that have one (docs/data-map.md, Sources), and that block is
     * the source of truth about the venue. A fixture without one reaches the weekday fallback,
     * which is a state the app only meets when the issuer goes quiet.
     */
    private fun catalog(trading: Trading? = Trading(currentPeriod = TradingPeriod.MARKET, openNow = true)) =
        listOf(
            Triple("TSLAx", "TSLA", tslaMint) to "Tesla xStock",
            Triple("JPMx", "JPM", jpmMint) to "JPMorgan xStock",
            Triple("AAPLx", "AAPL", aaplMint) to "Apple xStock",
        ).map { (id, name) ->
            val (symbol, ticker, mint) = id
            if (trading == null) {
                xStock(symbol, ticker, mint, name)
            } else {
                xStockTrading(symbol, ticker, mint, trading, name)
            }
        }

    /**
     * One catalog asset with the venue block, for the tests that build their own list of them.
     * The block is what production has: the catalog is filtered to assets that carry one, so a
     * fixture without one is testing the fallback rather than the ordinary case.
     */
    private fun openAsset(symbol: String, ticker: String, mint: String): XStockAsset =
        xStockTrading(symbol, ticker, mint, Trading(currentPeriod = TradingPeriod.MARKET, openNow = true))

    private fun viewModel(
        summaries: SummaryRepository = FakeSummaryRepository(Result.success(summary())),
        catalog: CatalogRepository = FakeCatalogRepository(Result.success(catalog())),
        prices: PriceRepository = FakePriceRepository(),
        snapshots: SnapshotRepository = FakeSnapshotRepository(),
        watchlist: WatchlistStore = InMemoryWatchlistStore(),
        digests: DigestStore = InMemoryDigestStore(),
        clock: Clock = marketOpen,
    ) = ListViewModel(summaries, catalog, prices, snapshots, watchlist, digests, clock)

    /** The bundled snapshot as the assets carry it: a whole list, dated, and with no price. */
    private fun bundled() = snapshot(
        capturedOn = LocalDate.of(2026, 9, 12),
        rows = listOf(
            SnapshotRow(ticker = "AAPL", company = "Apple Inc.", composite = 83.80406, tone = Tone.POSITIVE, ageDays = 2),
            SnapshotRow(ticker = "BKNG", company = "Booking Holdings Inc.", composite = 80.28, tone = Tone.CAUTION),
        ),
        assets = listOf(
            SnapshotAsset(symbol = "AAPLx", ticker = "AAPL", name = "Apple xStock", mint = aaplMint),
            SnapshotAsset(symbol = "TSLAx", ticker = "TSLA", name = "Tesla xStock", mint = tslaMint),
        ),
    )

    // ---- The join ----------------------------------------------------------------------

    @Test
    fun `loading, then analyzed rows joined to mints and prices, then rows without analysis`() = runTest {
        val prices = FakePriceRepository(
            Result.success(mapOf(aaplMint to price(232.5, reference = 232.4), jpmMint to price(301.0))),
        )
        val vm = viewModel(prices = prices)

        vm.state.test {
            assertTrue(awaitItem().isLoading)
            val state = awaitUntil { !it.refreshing && it.analyzed.all { row -> row.priceUsd != null } }

            assertNull(state.banner)
            assertFalse(state.catalogUnavailable)
            assertFalse(state.fromSnapshot)
            assertEquals("2026-09-10T18:00:00.000Z", state.generatedAt)

            // Composite descending; XYZ has no xStock and is on no list at all.
            assertEquals(listOf("AAPL", "JPM"), state.analyzed.map { it.ticker })

            val aapl = state.analyzed[0]
            assertEquals("AAPLx", aapl.symbol)
            assertEquals("AAPLx", aapl.display)
            assertEquals(aaplMint, aapl.mint)
            assertEquals("Apple Inc.", aapl.company)
            assertEquals(RowState.FAIR, aapl.state)
            assertEquals(232.5, aapl.priceUsd!!, 0.0)
            assertEquals(232.4, aapl.referencePriceUsd!!, 0.0)
            assertEquals(0.0430, aapl.premiumPct!!, 1e-3)
            assertFalse(aapl.stale)
            assertTrue(aapl.analyzed)

            val jpm = state.analyzed[1]
            assertEquals(RowState.STRONG, jpm.state)
            assertTrue(jpm.stale)
            assertEquals(9, jpm.ageDays)
            assertEquals(301.0, jpm.priceUsd!!, 0.0)
            assertNull(jpm.referencePriceUsd)
            // No reference price, so no premium: the row keeps its age and loses the percent.
            assertNull(jpm.premiumPct)

            // Catalog xStocks PlainTicker has not classified, keyed by the underlying ticker.
            assertEquals(listOf("TSLAx"), state.withoutAnalysis.map { it.symbol })
            assertEquals("TSLA", state.withoutAnalysis[0].ticker)
            assertFalse(state.withoutAnalysis[0].analyzed)

            // Analyzed mints first, then the unanalyzed one; XYZ has no mint to ask about.
            assertEquals(listOf(aaplMint, jpmMint, tslaMint), prices.requested.last())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a classified company with no xStock is in neither section`() = runTest {
        val vm = viewModel()

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertTrue("XYZ has no xStock", state.analyzed.none { it.ticker == "XYZ" })
            assertTrue("and it is not a price-only row either", state.withoutAnalysis.none { it.ticker == "XYZ" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `composite is the integer percentile, whichever scale the server sent`() = runTest {
        // The percentile the device saw raw (83.80406) and the 0 to 1 fraction the v1 fixture
        // uses, both drawn the way the row draws them: as an integer, never as the raw float.
        assertEquals("84", Fmt.decimal(percentile(83.80406, asFraction = false)!!, decimals = 0))
        assertEquals("71", Fmt.decimal(percentile(0.71, asFraction = true)!!, decimals = 0))
        assertEquals("100", Fmt.decimal(percentile(100.0, asFraction = false)!!, decimals = 0))
        assertNull(percentile(null, asFraction = false))

        val vm = viewModel()
        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertEquals(71.0, state.analyzed[0].composite!!, 1e-9)
            assertEquals(66.0, state.analyzed[1].composite!!, 1e-9)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the composite scale is read from the whole payload, not from one row`() = runTest {
        // The fixture and the canvas carry the rank as a 0 to 1 fraction, production as 0 to 100.
        assertTrue(percentilesAreFractions(listOf(0.71, 0.66, 0.02)))
        assertFalse(percentilesAreFractions(listOf(83.8, 12.0)))
        assertFalse("an empty payload has no scale to read", percentilesAreFractions(emptyList()))

        // The bottom of a 179-row leaderboard is legitimately below 1. Judged on its own it
        // would have been multiplied and drawn as "56"; judged with the payload it stays "1".
        val production = summary().let {
            it.copy(
                rows = listOf(
                    it.rows[0].copy(ticker = "AAPL", composite = 83.80406),
                    it.rows[1].copy(ticker = "JPM", composite = 0.56),
                ),
            )
        }
        val vm = viewModel(summaries = FakeSummaryRepository(Result.success(production)))

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertEquals(listOf("84", "1"), state.analyzed.map { Fmt.decimal(it.composite!!, decimals = 0) })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a ticker served twice is one row, not a duplicate key the list would crash on`() = runTest {
        val doubled = summary().let { it.copy(rows = it.rows + it.rows[0].copy(composite = 0.99)) }
        val vm = viewModel(summaries = FakeSummaryRepository(Result.success(doubled)))

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            val tickers = state.analyzed.map { it.ticker }
            assertEquals(tickers.distinct(), tickers)
            // The first row served wins, so the list keeps the server's own ordering.
            assertEquals(71.0, state.analyzed.single { it.ticker == "AAPL" }.composite!!, 1e-9)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no rendered string comes from the Ukrainian headline`() = runTest {
        assertTrue("the fixture carries a Cyrillic headline", cyrillic.containsMatchIn(ukrainianHeadline))
        val vm = viewModel()

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            val rows = state.analyzed + state.withoutAnalysis
            assertTrue(rows.isNotEmpty())
            rows.forEach { row ->
                // A data class prints every property it has, so this fails the moment a field
                // carries the headline again, whatever that field is called.
                val printed = row.toString()
                assertFalse(row.ticker, printed.contains(ukrainianHeadline))
                assertFalse("$row has Cyrillic in it", cyrillic.containsMatchIn(printed))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stale row stays visible with its age and only a wholly stale list raises the banner`() = runTest {
        val vm = viewModel()

        vm.state.test {
            val mixed = awaitUntil { !it.refreshing }
            val jpm = mixed.analyzed.single { it.ticker == "JPM" }
            assertTrue(jpm.stale)
            assertEquals(9, jpm.ageDays)
            assertEquals("the meta line shows the age of a stale row", 9, jpm.ageForMeta)
            assertNull("one stale row out of two raises nothing", mixed.banner)
            cancelAndIgnoreRemainingEvents()
        }

        val allStale = summary().let { it.copy(rows = it.rows.map { row -> row.copy(stale = true) }) }
        val stale = viewModel(summaries = FakeSummaryRepository(Result.success(allStale)))

        stale.state.test {
            val state = awaitUntil { !it.refreshing }
            assertEquals(2, state.analyzed.size)
            assertTrue(state.analyzed.all { it.stale })
            assertEquals(ListBanner.Stale(2), state.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Sources down ------------------------------------------------------------------

    @Test
    fun `summary down, catalog up - rows without analysis still render`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.failure(ApiException(502, "www.plainticker.com/api/v1/summary", null, null))),
            prices = prices,
        )

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            // The analysis is the product: a list that quietly turns into a price-only catalog
            // has to say so, and offer the retry, rather than look like a catalog of nothing.
            assertTrue(state.analysisUnavailable)
            assertEquals(ListBanner.AnalysisUnavailable, state.banner)
            assertTrue(state.analyzed.isEmpty())
            assertEquals(listOf("AAPLx", "JPMx", "TSLAx"), state.withoutAnalysis.map { it.symbol })
            assertFalse(state.isEmpty)
            assertEquals(listOf(aaplMint, jpmMint, tslaMint).sorted(), prices.requested.last().sorted())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both sources answer with nothing - one sentence, never a blank column`() = runTest {
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.success(SummaryResponse("v1.1", "2026-09-12T00:00:00.000Z"))),
            catalog = FakeCatalogRepository(Result.success(emptyList())),
        )

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertFalse("nothing failed, so this is not an outage", state.failed)
            assertNull(state.banner)
            assertTrue(state.isEmpty)
            assertFalse("and it is not an empty search either", state.searchMiss)
            assertTrue("the screen has a sentence to draw", state.emptyResult)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `catalog down, summary up - analyzed rows render without mints and the banner names it`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(catalog = FakeCatalogRepository(Result.failure(IOException("offline"))), prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertTrue(state.catalogUnavailable)
            assertEquals(ListBanner.CatalogUnavailable, state.banner)
            // Nothing is known about tokens, so every classified row is kept rather than dropped.
            assertEquals(3, state.analyzed.size)
            assertTrue(state.analyzed.all { it.mint == null && it.priceUsd == null })
            assertEquals("AAPL", state.analyzed[0].display)
            assertTrue(state.withoutAnalysis.isEmpty())
            assertTrue("no mint, so nothing to price", prices.requested.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both sources down - the bundled snapshot draws the list and names its date`() = runTest {
        val snapshots = FakeSnapshotRepository(
            snapshot(
                capturedOn = LocalDate.of(2026, 9, 12),
                rows = listOf(
                    SnapshotRow(ticker = "AAPL", company = "Apple Inc.", composite = 83.80406, tone = Tone.POSITIVE, ageDays = 2),
                    SnapshotRow(ticker = "BKNG", company = "Booking Holdings Inc.", composite = 80.28, tone = Tone.CAUTION),
                ),
                assets = listOf(
                    SnapshotAsset(symbol = "AAPLx", ticker = "AAPL", name = "Apple xStock", mint = aaplMint),
                    SnapshotAsset(symbol = "TSLAx", ticker = "TSLA", name = "Tesla xStock", mint = tslaMint),
                ),
            ),
        )
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.failure(IOException("offline"))),
            catalog = FakeCatalogRepository(Result.failure(IOException("offline"))),
            prices = FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
            snapshots = snapshots,
        )

        vm.state.test {
            // The snapshot now paints before the network is asked, so this waits for the
            // network to have settled as a failure: that is the state this test is about.
            val state = awaitUntil { !it.refreshing && it.analyzed.any { row -> row.priceUsd != null } }
            assertEquals(1, snapshots.calls)
            assertTrue(state.fromSnapshot)
            assertFalse(state.failed)
            assertEquals(ListBanner.Snapshot(LocalDate.of(2026, 9, 12)), state.banner)

            // The snapshot obeys the same rules as the live join: BKNG has no xStock, so it is
            // not a row, and the composite is carried as the percentile.
            assertEquals(listOf("AAPLx"), state.analyzed.map { it.symbol })
            assertEquals(83.80406, state.analyzed[0].composite!!, 1e-9)
            assertEquals(232.5, state.analyzed[0].priceUsd!!, 0.0)
            assertEquals(listOf("TSLAx"), state.withoutAnalysis.map { it.symbol })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both sources down with no snapshot - one line and one retry`() = runTest {
        val summaries = FakeSummaryRepository(Result.failure(IOException("offline")))
        val vm = viewModel(
            summaries = summaries,
            catalog = FakeCatalogRepository(Result.failure(IOException("offline"))),
        )

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertTrue(state.failed)
            assertEquals(ListBanner.Unavailable, state.banner)
            assertTrue(state.analyzed.isEmpty())
            assertTrue(state.withoutAnalysis.isEmpty())
            assertFalse("an outage is not an empty search", state.searchMiss)

            vm.refresh()
            awaitUntil { it.isLoading }
            awaitUntil { !it.refreshing }
            assertEquals("Retry asks the sources again", 2, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The bundled snapshot paints first -----------------------------------------------

    @Test
    fun `the bundled snapshot paints before any network answer, with no price and a dated banner`() = runTest {
        val network = Gate()
        val summaries = HeldSummaryRepository(network, FakeSummaryRepository(Result.success(summary())))
        val assets = HeldCatalogRepository(network, FakeCatalogRepository(Result.success(catalog())))
        val jupiter = HeldPriceRepository(
            Gate(),
            FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
        )
        val vm = viewModel(
            summaries = summaries,
            catalog = assets,
            prices = jupiter,
            snapshots = FakeSnapshotRepository(bundled()),
        )

        vm.state.test {
            val painted = awaitUntil { it.analyzed.isNotEmpty() }

            assertEquals("no source answered, and the list is drawn anyway", 0, summaries.summaryCalls)
            assertFalse("a drawn list is not loading", painted.isLoading)
            assertTrue(painted.fromSnapshot)
            assertTrue(painted.refreshing)
            assertNull("a line that would go again in half a second is a jump, not a warning", painted.banner)

            // The snapshot obeys the same join rules as the live sources: BKNG has no xStock, so
            // it is not a row, and the composite is the percentile.
            assertEquals(listOf("AAPLx"), painted.analyzed.map { it.symbol })
            assertEquals(83.80406, painted.analyzed[0].composite!!, 1e-9)
            assertEquals(listOf("TSLAx"), painted.withoutAnalysis.map { it.symbol })

            // The snapshot carries no price, so a snapshot row shows its analysis and nothing
            // where the premium goes. It is never a stale quote.
            val rows = painted.analyzed + painted.withoutAnalysis
            assertTrue(rows.all { it.priceUsd == null })
            assertTrue(rows.all { it.premiumPct == null })
            assertTrue(rows.all { it.tracking == null })

            // The refresh is still out once the grace has passed, so the reader is told what the
            // list is: a capture of a named day, being replaced in place.
            val named = awaitUntil { it.banner != null }
            assertEquals(ListBanner.SnapshotRefreshing(LocalDate.of(2026, 9, 12)), named.banner)
            assertEquals("and still no source has answered", 0, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a live answer replaces the snapshot in place and the banner goes`() = runTest {
        val network = Gate()
        val summaries = HeldSummaryRepository(network, FakeSummaryRepository(Result.success(summary())))
        val assets = HeldCatalogRepository(network, FakeCatalogRepository(Result.success(catalog())))
        val vm = viewModel(
            summaries = summaries,
            catalog = assets,
            prices = FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
            snapshots = FakeSnapshotRepository(bundled()),
        )

        vm.state.test {
            val painted = awaitUntil { it.fromSnapshot }
            assertEquals(listOf("AAPL"), painted.analyzed.map { it.ticker })

            network.release()
            val live = awaitUntil { !it.fromSnapshot && !it.refreshing }

            assertNull("nothing on screen is a snapshot any more, so no banner", live.banner)
            assertNull(live.snapshotCapturedOn)
            assertFalse(live.isLoading)
            assertFalse(live.failed)

            // The live summary is on screen, not the snapshot's two rows.
            assertEquals(listOf("AAPL", "JPM"), live.analyzed.map { it.ticker })
            assertEquals(71.0, live.analyzed[0].composite!!, 1e-9)
            assertEquals(listOf("TSLAx"), live.withoutAnalysis.map { it.symbol })
            assertEquals("2026-09-10T18:00:00.000Z", live.generatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed network keeps the snapshot on screen and offers the retry`() = runTest {
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.failure(IOException("offline"))),
            catalog = FakeCatalogRepository(Result.failure(IOException("offline"))),
            snapshots = FakeSnapshotRepository(bundled()),
        )

        vm.state.test {
            val settled = awaitUntil { !it.refreshing }

            assertTrue("the outage path still draws the snapshot", settled.fromSnapshot)
            assertFalse(settled.failed)
            // Settled, so this is the banner that offers a retry, not the one that says a
            // refresh is already running.
            assertEquals(ListBanner.Snapshot(LocalDate.of(2026, 9, 12)), settled.banner)
            assertEquals(listOf("AAPLx"), settled.analyzed.map { it.symbol })
            assertEquals(listOf("TSLAx"), settled.withoutAnalysis.map { it.symbol })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a source landing mid-run never starts a second price fetch`() = runTest {
        val jupiter = Gate()
        val network = Gate()
        val prices = HeldPriceRepository(jupiter, FakePriceRepository(Result.success(emptyMap())))
        val vm = viewModel(
            summaries = HeldSummaryRepository(network, FakeSummaryRepository(Result.success(summary()))),
            catalog = HeldCatalogRepository(network, FakeCatalogRepository(Result.success(catalog()))),
            prices = prices,
            snapshots = FakeSnapshotRepository(bundled()),
        )
        advanceUntilIdle()

        assertTrue(vm.state.value.fromSnapshot)
        assertEquals("the snapshot's mints are out with Jupiter", 1, prices.calls)

        // Both live sources land while that run is still out. Neither may start another.
        network.release()
        advanceUntilIdle()
        assertEquals("a source landing mid-run does not restart the price fetch", 1, prices.calls)

        jupiter.release()
        advanceUntilIdle()
        assertEquals("one run at a time, start to finish", 1, prices.maxInFlight)
        // The queued follow-up prices the live mint set the held run never saw.
        assertEquals(listOf(aaplMint, jpmMint, tslaMint), prices.asked.last())
    }

    // ---- The catalog arrives page by page --------------------------------------------------

    /** Every distinct list the screen drew, in order, so a test can look at the whole sequence. */
    private fun CoroutineScope.record(vm: ListViewModel, of: (ListUiState) -> List<String>): Pair<MutableList<List<String>>, Job> {
        val drawn = mutableListOf<List<String>>()
        val watcher = launch(UnconfinedTestDispatcher(coroutineContext[kotlinx.coroutines.test.TestCoroutineScheduler]!!)) {
            vm.state.collect { state ->
                val row = of(state)
                if (row.isNotEmpty() && drawn.lastOrNull() != row) drawn += row
            }
        }
        return drawn to watcher
    }

    @Test
    fun `the catalog publishes page by page, and no page reorders what is already drawn`() = runTest {
        // Nine xStocks arriving in three pages, in an order that is not the order they are drawn
        // in, so a page that simply appended would show up as a reordering.
        val arrival = listOf("D", "A", "G", "C", "I", "B", "F", "H", "E")
        val assets = arrival.map { openAsset("${it}x", it, "Mint$it".padEnd(44, 'z')) }
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.success(SummaryResponse("v1.1", "2026-09-12T00:00:00.000Z"))),
            catalog = FakeCatalogRepository(Result.success(assets), pageSize = 3),
        )

        val (drawn, watcher) = record(vm) { state -> state.withoutAnalysis.mapNotNull { it.symbol } }
        advanceUntilIdle()
        watcher.cancel()

        assertEquals("one publish per page, not one at the end", 3, drawn.size)
        assertEquals(listOf(3, 6, 9), drawn.map { it.size })
        assertEquals(arrival.sorted().map { "${it}x" }, drawn.last())
        assertEquals(9, vm.state.value.withoutAnalysis.size)

        // Nothing already on screen moves: every publish keeps the previous one in its order.
        drawn.zipWithNext { earlier, later ->
            assertEquals("a page reordered rows the reader was already looking at", earlier, later.filter { it in earlier })
        }
    }

    @Test
    fun `pages landing over a painted snapshot refine it and never empty the list`() = runTest {
        val assets = (1..9).map { openAsset("T${it}x", "T$it", "Mint$it".padEnd(44, 'z')) }
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.success(SummaryResponse("v1.1", "2026-09-12T00:00:00.000Z"))),
            catalog = FakeCatalogRepository(Result.success(assets), pageSize = 3),
            snapshots = FakeSnapshotRepository(bundled()),
        )

        val (drawn, watcher) = record(vm) { state ->
            (state.analyzed + state.withoutAnalysis).mapNotNull { it.symbol }
        }
        advanceUntilIdle()
        watcher.cancel()

        // The snapshot's two tokens are the first thing drawn, and the list only ever grows
        // from there: a page refines what is on screen, it does not replace it.
        assertEquals(listOf("AAPLx", "TSLAx"), drawn.first().sorted())
        assertTrue("the list never shrinks mid-paging", drawn.zipWithNext().all { (a, b) -> b.size >= a.size })
        assertEquals("the whole live catalog, once it is whole", 9, vm.state.value.withoutAnalysis.size)
        assertNull("nothing on screen is a snapshot any more", vm.state.value.banner)
    }

    @Test
    fun `pages landing during a price run never start a second one`() = runTest {
        val jupiter = Gate()
        val prices = HeldPriceRepository(jupiter, FakePriceRepository(Result.success(emptyMap())))
        val mints = (1..9).map { "Mint$it".padEnd(44, 'z') }
        val assets = mints.mapIndexed { index, mint -> openAsset("T${index}x", "T$index", mint) }
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.success(SummaryResponse("v1.1", "2026-09-12T00:00:00.000Z"))),
            catalog = FakeCatalogRepository(Result.success(assets), pageSize = 3),
            prices = prices,
        )
        advanceUntilIdle()

        assertEquals("the first page's mints are out with Jupiter", 1, prices.calls)
        assertEquals(mints.take(3), prices.asked.single())

        jupiter.release()
        advanceUntilIdle()

        assertEquals("one run at a time, however many pages landed during it", 1, prices.maxInFlight)
        assertEquals("and the follow-up prices the whole catalog", mints, prices.asked.last())
        assertEquals("two runs for three pages, not three", 2, prices.calls)
        assertEquals(9, vm.state.value.withoutAnalysis.size)
    }

    /**
     * Jupiter that answers the visible window and then refuses the rest of a run, which is the
     * shape the rule below is about: what a run draws, the next run's window must not take away.
     */
    private class WindowOnlyPriceRepository(
        private val inner: FakePriceRepository,
    ) : PriceRepository by inner {
        var refuseTheRest = false

        override suspend fun pricesFirst(mints: List<String>, limit: Int): PriceFetch {
            if (limit < 0 && refuseTheRest) throw IOException("Jupiter refused the rest")
            return inner.pricesFirst(mints, limit)
        }
    }

    @Test
    fun `a second price run's first window does not blank the prices already drawn`() = runTest {
        val mints = (1..20).map { "Mint$it".padEnd(44, 'z') }
        val jupiter = WindowOnlyPriceRepository(
            FakePriceRepository(Result.success(mints.associateWith { price(100.0, reference = 100.0) })),
        )
        val network = Gate()
        val bundledAssets = mints.mapIndexed { index, mint ->
            SnapshotAsset(symbol = "T${index}x", ticker = "T$index", name = "T$index xStock", mint = mint)
        }
        val live = mints.mapIndexed { index, mint -> openAsset("T${index}x", "T$index", mint) } +
            openAsset("NEWx", "NEW", "MintNew".padEnd(44, 'z'))
        val empty = SummaryResponse("v1.1", "2026-09-12T00:00:00.000Z")

        val vm = viewModel(
            summaries = HeldSummaryRepository(network, FakeSummaryRepository(Result.success(empty))),
            catalog = HeldCatalogRepository(network, FakeCatalogRepository(Result.success(live))),
            prices = jupiter,
            snapshots = FakeSnapshotRepository(snapshot(assets = bundledAssets)),
        )
        advanceUntilIdle()

        assertEquals("the snapshot's tokens are priced", 20, vm.state.value.withoutAnalysis.count { it.priceUsd != null })

        // The live catalog lands with one more token, so a second run starts. Its first call
        // covers the visible window only, and Jupiter then refuses the rest of that run.
        jupiter.refuseTheRest = true
        network.release()
        advanceUntilIdle()

        val rows = vm.state.value.withoutAnalysis
        assertEquals(21, rows.size)
        assertEquals(
            "a row outside the second run's window keeps the quote it was drawn with",
            20,
            rows.count { it.priceUsd != null },
        )
    }

    // ---- Prices ------------------------------------------------------------------------

    @Test
    fun `the first screenful is priced before the rest`() = runTest {
        val mints = (1..40).map { "Mint$it".padEnd(44, 'z') }
        val prices = FakePriceRepository(Result.success(mints.associateWith { price(10.0, reference = 10.0) }))
        val vm = wide(mints, prices)
        advanceUntilIdle()

        assertEquals("two calls, the window then the rest", 2, prices.requested.size)
        assertEquals(mints.take(ListViewModel.FIRST_SCREENFUL), prices.requested[0])
        assertEquals(mints, prices.requested[1])
        assertTrue(vm.state.value.analyzed.all { it.priceUsd != null })
    }

    @Test
    fun `a refused chunk costs only its own rows their price`() = runTest {
        val mints = (1..40).map { "Mint$it".padEnd(44, 'z') }
        val refused = mints.takeLast(20).toSet()
        val prices = FakePriceRepository(
            result = Result.success((mints - refused).associateWith { price(10.0, reference = 10.0) }),
            unfetched = refused,
        )
        val vm = wide(mints, prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing && it.pricesPartial }
            assertEquals(ListBanner.PricesPartial, state.banner)
            val rows = state.analyzed + state.withoutAnalysis
            assertEquals(20, rows.count { it.priceUsd != null })
            assertEquals(20, rows.count { it.priceUsd == null })
            assertTrue("what was priced keeps its premium", rows.filter { it.priceUsd != null }.all { it.premiumPct != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nothing priced at all raises the prices banner and the rows stay`() = runTest {
        val mints = (1..40).map { "Mint$it".padEnd(44, 'z') }
        val prices = FakePriceRepository(
            result = Result.failure(RateLimitedException("lite-api.jup.ag/price/v3", "429", null, null)),
            unfetched = mints.toSet(),
        )
        val vm = wide(mints, prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing && it.pricesUnavailable }
            assertEquals(ListBanner.PricesUnavailable, state.banner)
            assertFalse(state.pricesPartial)
            assertEquals(40, (state.analyzed + state.withoutAnalysis).size)
            assertTrue((state.analyzed + state.withoutAnalysis).all { it.priceUsd == null })
            assertTrue("the analysis is still on screen", state.analyzed.all { it.composite != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mint Jupiter has no price for is not a failure`() = runTest {
        val prices = FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5))))
        val vm = viewModel(prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing && it.analyzed.any { row -> row.priceUsd != null } }
            assertNull("an answered but unpriced mint raises no banner", state.banner)
            assertFalse(state.pricesPartial)
            assertFalse(state.pricesUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the price run is capped so one refresh cannot spend a minute on paced calls`() = runTest {
        val mints = (1..400).map { "Mint$it".padEnd(44, 'z') }
        val prices = FakePriceRepository(Result.success(emptyMap()))
        val vm = wide(mints, prices)
        advanceUntilIdle()

        assertEquals(2, prices.requested.size)
        assertEquals(ListViewModel.PRICE_BUDGET, prices.requested[1].size)
        assertEquals(400, vm.state.value.analyzed.size + vm.state.value.withoutAnalysis.size)
    }

    // ---- The liquidity floor -------------------------------------------------------------

    @Test
    fun `a pool under the floor publishes no premium and carries the pool instead`() = runTest {
        val prices = FakePriceRepository(
            Result.success(
                mapOf(
                    // APPx read +89.34 percent off a pool of $34 live on 2026-09-12.
                    aaplMint to price(189.34, reference = 100.0, liquidity = 34.0),
                    jpmMint to price(301.0, reference = 300.0),
                ),
            ),
        )
        val vm = viewModel(prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing && it.analyzed.all { row -> row.priceUsd != null } }
            val aapl = state.analyzed.first { it.ticker == "AAPL" }

            assertNull("a premium off a dead pool never reaches the row", aapl.premiumPct)
            assertEquals(TrackingQuality.Thin(34.0), aapl.tracking)
            assertEquals("the row states what the pool is worth", 34.0, aapl.tracking!!.poolUsd!!, 0.0)

            // Everything else about the row is untouched: the floor withholds one number.
            assertEquals(189.34, aapl.priceUsd!!, 0.0)
            assertEquals(100.0, aapl.referencePriceUsd!!, 0.0)
            assertEquals(71.0, aapl.composite!!, 1e-9)
            assertEquals(RowState.FAIR, aapl.state)

            // Disclosure, not curation: same sections, same order, no banner, nothing filtered.
            assertEquals(listOf("AAPL", "JPM"), state.analyzed.map { it.ticker })
            assertEquals(listOf("TSLAx"), state.withoutAnalysis.map { it.symbol })
            assertNull(state.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a deep pool is untouched and the floor itself is deep enough`() = runTest {
        val prices = FakePriceRepository(
            Result.success(
                mapOf(
                    aaplMint to price(232.5, reference = 232.4, liquidity = 250_000.0),
                    jpmMint to price(301.0, reference = 300.0, liquidity = TrackingQuality.MIN_POOL_USD),
                ),
            ),
        )
        val vm = viewModel(prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing && it.analyzed.all { row -> row.priceUsd != null } }

            val aapl = state.analyzed.first { it.ticker == "AAPL" }
            assertTrue(aapl.tracking is TrackingQuality.Tracked)
            assertEquals(0.0430, aapl.premiumPct!!, 1e-3)
            assertEquals(250_000.0, aapl.tracking!!.poolUsd!!, 0.0)

            val jpm = state.analyzed.first { it.ticker == "JPM" }
            assertTrue("a pool exactly at the floor keeps its premium", jpm.tracking is TrackingQuality.Tracked)
            assertEquals(0.3333, jpm.premiumPct!!, 1e-3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a quote with no depth reported publishes no premium and no pool`() = runTest {
        val prices = FakePriceRepository(
            Result.success(mapOf(aaplMint to price(232.5, reference = 232.4, liquidity = null))),
        )
        val vm = viewModel(prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.refreshing && it.analyzed.any { row -> row.priceUsd != null } }
            val aapl = state.analyzed.first { it.ticker == "AAPL" }

            assertEquals(TrackingQuality.Untracked, aapl.tracking)
            assertNull("unknown depth is not deep", aapl.premiumPct)
            assertNull("and there is no pool to state", aapl.tracking!!.poolUsd)
            assertEquals(232.5, aapl.priceUsd!!, 0.0)
            assertEquals("the row keeps its place in Analyzed", listOf("AAPL", "JPM"), state.analyzed.map { it.ticker })
            assertNull("a missing depth is not an error state", state.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Search ------------------------------------------------------------------------

    @Test
    fun `search filters by ticker, symbol or company, says so when it misses, and clears`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(prices = prices)

        vm.state.test {
            awaitUntil { !it.refreshing }
            val calls = prices.requested.size

            vm.search("jp")
            val jp = awaitUntil { it.query == "jp" }
            assertEquals(listOf("JPM"), jp.analyzed.map { it.ticker })
            assertTrue(jp.withoutAnalysis.isEmpty())

            vm.search("tesla")
            val tesla = awaitUntil { it.query == "tesla" }
            assertTrue(tesla.analyzed.isEmpty())
            assertEquals(listOf("TSLAx"), tesla.withoutAnalysis.map { it.symbol })

            vm.search("zzz")
            val miss = awaitUntil { it.query == "zzz" }
            assertTrue(miss.isEmpty)
            assertTrue(miss.searchMiss)

            vm.clearSearch()
            val all = awaitUntil { it.query == "" }
            assertEquals(2, all.analyzed.size)
            assertEquals(1, all.withoutAnalysis.size)
            assertFalse(all.searchMiss)

            assertEquals("searching never asks for a price", calls, prices.requested.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The banner slot ---------------------------------------------------------------

    @Test
    fun `one slot, in the order offline then stale then device`() {
        val stale = ListUiState(allStaleDays = 3, pricesPartial = true, catalogUnavailable = true)
        assertEquals(ListBanner.Stale(3), stale.banner)

        val snapshot = stale.copy(fromSnapshot = true, snapshotCapturedOn = LocalDate.of(2026, 9, 12))
        assertEquals(ListBanner.Snapshot(LocalDate.of(2026, 9, 12)), snapshot.banner)

        assertEquals(ListBanner.Unavailable, snapshot.copy(failed = true).banner)
        assertEquals(
            ListBanner.CatalogUnavailable,
            ListUiState(catalogUnavailable = true, analysisUnavailable = true, pricesPartial = true).banner,
        )
        assertEquals(
            ListBanner.AnalysisUnavailable,
            ListUiState(analysisUnavailable = true, pricesUnavailable = true).banner,
        )
        assertEquals(ListBanner.PricesUnavailable, ListUiState(pricesUnavailable = true, pricesPartial = true).banner)
        assertEquals(ListBanner.PricesPartial, ListUiState(pricesPartial = true).banner)
        assertNull(ListUiState().banner)
    }

    // ---- The Today strip ---------------------------------------------------------------

    @Test
    fun `the today strip counts what is watched and follows the store`() = runTest {
        val watchlist = InMemoryWatchlistStore(setOf("AAPL", "TSLA"))
        val vm = viewModel(watchlist = watchlist)

        vm.state.test {
            assertEquals(2, awaitUntil { !it.refreshing }.watched)
            watchlist.add("NVDA")
            assertEquals(3, awaitUntil { it.watched == 3 }.watched)
            watchlist.remove("AAPL")
            assertEquals(2, awaitUntil { it.watched == 2 }.watched)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The strip names the nearest report out of the digest the daily check stored, which can be a
     * day old, so it names it only while that ticker is still watched. Without the guard the List
     * would go on naming a company for up to a day after the reader took it off the watchlist.
     */
    @Test
    fun `the today strip names the nearest report only while its ticker is watched`() = runTest {
        val watchlist = InMemoryWatchlistStore(setOf("AAPL"))
        val digests = InMemoryDigestStore(
            DigestRecord(
                text = "1 stock watched. AAPLx reports in 45 days.",
                producedAtMillis = 1_789_257_600_000L,
                nextReportTicker = "AAPL",
                nextReportSymbol = "AAPLx",
                nextReportOn = "2026-10-28",
            ),
        )
        val vm = viewModel(watchlist = watchlist, digests = digests)

        vm.state.test {
            val named = awaitUntil { it.nextReport != null }
            assertEquals("AAPLx", named.nextReport?.symbol)
            assertEquals(LocalDate.of(2026, 10, 28), named.nextReport?.on)

            watchlist.remove("AAPL")
            watchlist.add("TSLA")
            val other = awaitUntil { it.nextReport == null }
            assertEquals("the count stands, only the name goes", 1, other.watched)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the today strip has no report to name before the first check has run`() = runTest {
        val vm = viewModel(watchlist = InMemoryWatchlistStore(setOf("AAPL")))

        vm.state.test {
            val state = awaitUntil { !it.refreshing }
            assertEquals(1, state.watched)
            assertNull(state.nextReport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh asks the sources again`() = runTest {
        val summaries = FakeSummaryRepository(Result.success(summary()))
        val vm = viewModel(summaries = summaries)

        vm.state.test {
            awaitUntil { !it.refreshing }
            assertEquals(1, summaries.summaryCalls)
            vm.refresh()
            awaitUntil { it.refreshing }
            awaitUntil { !it.refreshing }
            assertEquals(2, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- A retry never walks the screen backwards -------------------------------------------

    /** The catalog, answering at once until a test sets [wait]: the retry is what it wants to see. */
    private class HoldableCatalog(private val inner: FakeCatalogRepository) : CatalogRepository by inner {
        var wait: CompletableDeferred<Unit>? = null
        val userAsked: List<Boolean> get() = inner.userAsked

        /** Every ask after this one fails, which is the retry that lands on nothing. */
        fun goesOffline() {
            inner.assets = Result.failure(IOException("offline"))
        }

        override fun catalogUpdates(userAsked: Boolean): Flow<CatalogUpdate> = flow {
            wait?.await()
            inner.catalogUpdates(userAsked).collect { emit(it) }
        }
    }

    /** The analysis, answering at once until a test sets [wait]. */
    private class HoldableSummary(private val inner: FakeSummaryRepository) : SummaryRepository by inner {
        var wait: CompletableDeferred<Unit>? = null

        fun goesOffline() {
            inner.summaryResult = Result.failure(IOException("offline"))
        }

        override suspend fun summary(): SummaryResponse {
            wait?.await()
            return inner.summary()
        }
    }

    @Test
    fun `a retry keeps the live list on screen instead of putting the snapshot back`() = runTest {
        // A token listed after the snapshot was captured: it is on the live catalog and not on the
        // bundled one, so it is exactly the row a retry that walked backwards would take away.
        val live = catalog() + xStock("NEWx", "NEW", "MintNew".padEnd(44, 'z'))
        val assets = HoldableCatalog(FakeCatalogRepository(Result.success(live)))
        val summaries = HoldableSummary(FakeSummaryRepository(Result.success(summary())))
        val vm = viewModel(summaries = summaries, catalog = assets, snapshots = FakeSnapshotRepository(bundled()))
        advanceUntilIdle()

        val settled = vm.state.value
        assertFalse("the live sources are what is drawn", settled.fromSnapshot)
        assertEquals(listOf("AAPL", "JPM"), settled.analyzed.map { it.ticker })
        assertEquals(listOf("NEWx", "TSLAx"), settled.withoutAnalysis.map { it.symbol })

        // Retry, with both sources still out. Nothing better has arrived, so nothing may change.
        assets.wait = CompletableDeferred()
        summaries.wait = CompletableDeferred()
        val (drawn, watcher) = record(vm) { state -> (state.analyzed + state.withoutAnalysis).mapNotNull { it.symbol } }
        vm.refresh()
        advanceUntilIdle()

        assertEquals("a retry drew the bundled snapshot over a live list", 1, drawn.size)
        val retrying = vm.state.value
        assertTrue("the refresh is running", retrying.refreshing)
        assertFalse("the snapshot never comes back over a live list", retrying.fromSnapshot)
        assertFalse("and a drawn list never goes back to skeletons", retrying.isLoading)
        assertEquals(listOf("NEWx", "TSLAx"), retrying.withoutAnalysis.map { it.symbol })

        assets.wait!!.complete(Unit)
        summaries.wait!!.complete(Unit)
        advanceUntilIdle()
        watcher.cancel()

        assertEquals(1, drawn.size)
        assertEquals(listOf("NEWx", "TSLAx"), vm.state.value.withoutAnalysis.map { it.symbol })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `a retry whose sources fail keeps what was on screen rather than the snapshot`() = runTest {
        val assets = HoldableCatalog(FakeCatalogRepository(Result.success(catalog())))
        val summaries = HoldableSummary(FakeSummaryRepository(Result.success(summary())))
        val vm = viewModel(summaries = summaries, catalog = assets, snapshots = FakeSnapshotRepository(bundled()))
        advanceUntilIdle()
        assertEquals(listOf("AAPL", "JPM"), vm.state.value.analyzed.map { it.ticker })

        summaries.goesOffline()
        assets.goesOffline()
        vm.refresh()
        advanceUntilIdle()

        val after = vm.state.value
        assertFalse("a failed retry is not a reason to draw the snapshot", after.fromSnapshot)
        assertFalse(after.failed)
        assertEquals("what was on screen is still on screen", listOf("AAPL", "JPM"), after.analyzed.map { it.ticker })
        assertEquals(listOf("TSLAx"), after.withoutAnalysis.map { it.symbol })
    }

    /**
     * The other half of the same rule, on the first load rather than on a retry. A ticker listed
     * after the bundled snapshot was captured is withheld until its page lands, because only a
     * whole catalog may say a ticker has no xStock, and it is then inserted. What it must never
     * do is appear, go, and come back: the review read the flicker it saw as that, and the one
     * path that produced it was the retry above. This is the guard that says so.
     */
    @Test
    fun `a token listed after the capture is never drawn and then taken away`() = runTest {
        val live = catalog() + xStock("NEWx", "NEW", "MintNew".padEnd(44, 'z'))
        val classified = summary().let { it.copy(rows = it.rows + it.rows[0].copy(ticker = "NEW", composite = 0.9)) }
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.success(classified)),
            catalog = FakeCatalogRepository(Result.success(live), pageSize = 2),
            snapshots = FakeSnapshotRepository(bundled()),
        )

        val (drawn, watcher) = record(vm) { state -> (state.analyzed + state.withoutAnalysis).mapNotNull { it.symbol } }
        advanceUntilIdle()
        watcher.cancel()

        assertTrue("the snapshot painted first", drawn.size > 1)
        drawn.zipWithNext { earlier, later ->
            assertTrue("a row was drawn and then taken away: ${earlier - later.toSet()}", later.containsAll(earlier))
        }
        assertTrue("the token listed today is on the list", vm.state.value.analyzed.any { it.symbol == "NEWx" })
    }

    // ---- A reader who asks the app to look again ---------------------------------------------

    @Test
    fun `a screen opening may use the caches and a retry asks the network`() = runTest {
        val assets = FakeCatalogRepository(Result.success(catalog()))
        val vm = viewModel(catalog = assets)
        advanceUntilIdle()
        assertEquals("the first load is a screen opening", listOf(false), assets.userAsked)

        vm.refresh()
        advanceUntilIdle()
        assertEquals("and a retry the reader tapped asks the network", listOf(false, true), assets.userAsked)
    }

    // ---- The snapshot banner does not flash --------------------------------------------------

    /** Sources that answer after a stretch of the test clock, so a test can time the banner. */
    private class SlowCatalog(
        private val inner: FakeCatalogRepository,
        private val afterMillis: Long,
    ) : CatalogRepository by inner {
        override fun catalogUpdates(userAsked: Boolean): Flow<CatalogUpdate> = flow {
            delay(afterMillis)
            inner.catalogUpdates(userAsked).collect { emit(it) }
        }
    }

    private class SlowSummary(
        private val inner: FakeSummaryRepository,
        private val afterMillis: Long,
    ) : SummaryRepository by inner {
        override suspend fun summary(): SummaryResponse {
            delay(afterMillis)
            return inner.summary()
        }
    }

    private fun slowly(afterMillis: Long) = viewModel(
        summaries = SlowSummary(FakeSummaryRepository(Result.success(summary())), afterMillis),
        catalog = SlowCatalog(FakeCatalogRepository(Result.success(catalog())), afterMillis),
        snapshots = FakeSnapshotRepository(bundled()),
    )

    /** Every distinct banner the screen drew, in order, nulls included. */
    private fun CoroutineScope.recordBanners(vm: ListViewModel): Pair<MutableList<ListBanner?>, Job> {
        val seen = mutableListOf<ListBanner?>()
        val watcher = launch(UnconfinedTestDispatcher(coroutineContext[kotlinx.coroutines.test.TestCoroutineScheduler]!!)) {
            vm.state.collect { if (seen.isEmpty() || seen.last() != it.banner) seen += it.banner }
        }
        return seen to watcher
    }

    @Test
    fun `a refresh that settles quickly never flashes the snapshot banner`() = runTest {
        // 0.70 s is what the warm launch on the Seeker measured: the line came up at 3.53 s and
        // went at 4.20 s, moving the whole list down by its height and back (docs/data-map.md).
        val vm = slowly(afterMillis = 700)
        val (seen, watcher) = recordBanners(vm)
        advanceUntilIdle()
        watcher.cancel()

        assertEquals("the live list is what settled", listOf("AAPL", "JPM"), vm.state.value.analyzed.map { it.ticker })
        assertEquals("the banner appeared and went, moving the list under the reader", listOf<ListBanner?>(null), seen)
    }

    @Test
    fun `a refresh the reader is left waiting on still says the list is a snapshot`() = runTest {
        val vm = slowly(afterMillis = 5_000)

        advanceTimeBy(800)
        assertTrue("the snapshot is what is drawn", vm.state.value.fromSnapshot)
        assertNull("and nothing flashed in the first second", vm.state.value.banner)

        advanceTimeBy(3_000)
        assertEquals(
            "a refresh this long is worth a line",
            ListBanner.SnapshotRefreshing(LocalDate.of(2026, 9, 12)),
            vm.state.value.banner,
        )

        advanceUntilIdle()
        assertNull("and it goes when the live list lands", vm.state.value.banner)
        assertFalse(vm.state.value.fromSnapshot)
    }

    // ---- Support -----------------------------------------------------------------------

    /** A list wide enough to have a window and a rest: half analyzed, half catalog only. */
    private fun wide(mints: List<String>, prices: FakePriceRepository): ListViewModel {
        val analyzed = mints.take(mints.size / 2)
        val assets = mints.mapIndexed { index, mint -> openAsset("T${index}x", "T$index", mint) }
        val rows = analyzed.mapIndexed { index, _ ->
            SummaryRow(
                ticker = "T$index",
                company = "Company $index",
                tone = Tone.POSITIVE,
                composite = 90.0 - index,
                ageDays = 1,
            )
        }
        return viewModel(
            summaries = FakeSummaryRepository(Result.success(SummaryResponse("v1.1", "2026-09-12T00:00:00.000Z", rows))),
            catalog = FakeCatalogRepository(Result.success(assets)),
            prices = prices,
        )
    }
}
