package com.myapp.ui.detail

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.R
import com.myapp.awaitUntil
import com.myapp.core.Clock
import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.MultiplierSource
import com.myapp.data.net.ApiException
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.rpc.DefaultAccountState
import com.myapp.data.rpc.PausableConfig
import com.myapp.data.rpc.PermanentDelegate
import com.myapp.data.rpc.TransferHookConfig
import com.myapp.data.xstocks.Exchange
import com.myapp.data.xstocks.MarketSource
import com.myapp.data.xstocks.MarketState
import com.myapp.data.xstocks.Multiplier
import com.myapp.data.xstocks.PriceLabel
import com.myapp.data.xstocks.Trading
import com.myapp.data.xstocks.TradingPeriod
import com.myapp.prefs.InMemoryWatchlistStore
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakeMintRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeSummaryRepository
import com.myapp.repo.MintReading
import com.myapp.repo.mintFacts
import com.myapp.repo.price
import com.myapp.repo.proofOfReserves
import com.myapp.repo.scaled
import com.myapp.repo.xStock
import com.myapp.repo.xStockTrading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

/**
 * Every state of the Detail data path, from fakes.
 *
 * The shape of these tests is the contract: each source is asserted while another one is dead, so
 * a regression that lets one failed call blank the screen fails here rather than on a demo.
 */
class DetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val aaplMint = "AAPLxMint".padEnd(44, '1')

    /** A Wednesday, 12:00 in New York, so the local schedule reads as a session when it is used. */
    private val now = Instant.parse("2026-09-16T16:00:00Z").toEpochMilli()
    private val clock = Clock { now }

    /** plainticker/analysis-aapl.json: the recorded v1.1 payload for AAPL. */
    private fun analysis(): AnalysisPayload =
        HttpClientFactory.json.decodeFromString(AnalysisPayload.serializer(), Fixtures.read("plainticker/analysis-aapl.json"))

    private fun served() = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(analysis())))

    private val nyseOpen = Trading(
        tradingHoursMode = "Regular",
        currentPeriod = TradingPeriod.MARKET,
        openNow = true,
        exchange = Exchange(mic = "XNYS", abbreviation = "NYSE"),
    )

    private fun catalog(
        trading: Trading? = null,
        reserves: Map<String, Result<com.myapp.data.xstocks.ProofOfReserves?>> = mapOf(
            "AAPLx" to Result.success(proofOfReserves("AAPLx", sharesHeld = "26101", circulatingSupply = "25924")),
        ),
        multiplierRecords: Map<String, Result<Multiplier>> = emptyMap(),
    ) = FakeCatalogRepository(
        assets = Result.success(
            listOf(
                if (trading == null) {
                    xStock("AAPLx", "AAPL", aaplMint, "Apple Inc.")
                } else {
                    xStockTrading("AAPLx", "AAPL", aaplMint, trading, "Apple Inc.")
                },
                xStock("TSLAx", "TSLA", KnownMints.TSLAX),
            ),
        ),
        reserves = reserves,
        multiplierRecords = multiplierRecords,
    )

    /** A readable mint with the shape the live TSLAx capture has: delegate, pausable, empty hook. */
    private fun readableMint(
        scaledUiAmount: com.myapp.data.rpc.ScaledUiAmountConfig? = scaled(1.0),
        slot: Long = 446_503_662L,
    ) = FakeMintRepository(
        Result.success(
            MintReading(
                facts = mintFacts(
                    permanentDelegate = PermanentDelegate("5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq"),
                    pausable = PausableConfig(paused = false, authority = null),
                    scaledUiAmount = scaledUiAmount,
                    transferHook = TransferHookConfig(programId = null, authority = null),
                    defaultAccountState = DefaultAccountState(DefaultAccountState.INITIALIZED),
                ),
                slot = slot,
                readAtMillis = now - 2_000L,
            ),
        ),
    )

    private fun viewModel(
        ticker: String = "AAPL",
        summaries: FakeSummaryRepository = served(),
        catalog: FakeCatalogRepository = catalog(),
        prices: FakePriceRepository = FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
        mints: FakeMintRepository = readableMint(),
        watchlist: InMemoryWatchlistStore = InMemoryWatchlistStore(),
    ) = DetailViewModel(ticker, summaries, catalog, prices, mints, watchlist, clock)

    // ---- Everything present ------------------------------------------------------------------

    @Test
    fun `all present - analysis, catalog, price, mint, reserves and the split all land`() = runTest {
        val prices = FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4, liquidity = 1_300_000.0))))
        val catalog = catalog(trading = nyseOpen)
        val vm = viewModel(ticker = " aapl ", catalog = catalog, prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }

            assertEquals("AAPL", state.ticker)
            assertEquals("AAPLx", state.symbol)
            assertEquals(aaplMint, state.mint)
            assertEquals("AAPL", state.analysis?.ticker)
            assertTrue(state.analysisState is AnalysisState.Served)

            assertEquals(232.5, state.price!!.usdPrice, 0.0)
            assertEquals(232.4, state.price!!.stockData!!.price!!, 0.0)
            assertEquals(listOf(listOf(aaplMint)), prices.requested)

            val chain = (state.chain as Piece.Ready).value
            assertEquals(446_503_662L, chain.slot)
            assertEquals(now - 2_000L, chain.readAtMillis)
            assertNotNull("the trust rows have a delegate to name", chain.facts.permanentDelegate)
            assertFalse(chain.facts.pausable!!.paused)
            assertFalse("the hook extension is present and empty", chain.facts.transferHook!!.runs)
            assertEquals(false, chain.facts.freshAccountFrozen)

            val reserves = (state.reserves as Piece.Ready).value
            assertEquals(26_101.0, reserves.sharesHeld, 0.0)
            assertEquals(25_924.0, reserves.tokensInCirculation, 0.0)
            assertEquals("Alpaca", reserves.custodian)

            val split = (state.split as Piece.Ready).value
            assertEquals(1.0, split.current, 0.0)
            assertEquals("the chain answered, so the chain is cited", MultiplierSource.MINT, split.source)
            assertNull(split.pending)
            assertEquals(1.0, state.multiplier!!, 0.0)

            val market = checkNotNull(state.market)
            assertEquals(MarketSource.VENUE, market.source)
            assertEquals(MarketState.REGULAR, market.state)
            assertEquals(PriceLabel.TRACKING_WITHIN, state.priceLabel)
            assertEquals(0.043029, state.premiumPct!!, 1e-5)

            assertFalse(state.catalogUnavailable)
            assertFalse(state.pricesUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The chain is unreadable ------------------------------------------------------------

    @Test
    fun `chain unreadable - the trust rows say so, they do not vanish and they claim nothing`() = runTest {
        val catalog = catalog()
        val vm = viewModel(catalog = catalog, mints = FakeMintRepository(Result.failure(IOException("forwarder down"))))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }

            assertTrue("failed, so the rows say the chain could not be read", state.chain is Piece.Failed)
            assertFalse("and never absent, which would read as nothing to report", state.chain.isAbsent)
            assertNull(state.chain.valueOrNull)

            // Everything else stands.
            assertNotNull(state.analysis)
            assertNotNull(state.price)
            assertTrue(state.reserves is Piece.Ready)

            // And the multiplier falls back to xStocks, saying so.
            val split = (state.split as Piece.Ready).value
            assertEquals(MultiplierSource.XSTOCKS, split.source)
            assertEquals(1.0, split.current, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mint that is not a Token-2022 mint is unreadable too, never an empty set of facts`() = runTest {
        val unreadable = FakeMintRepository(Result.success(MintReading(facts = null, slot = 446_000_000L, readAtMillis = now)))
        val vm = viewModel(mints = unreadable)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.chain is Piece.Failed)
            assertEquals(MultiplierSource.XSTOCKS, (state.split as Piece.Ready).value.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chain unreadable and xStocks down - the split says nothing rather than one`() = runTest {
        val catalog = catalog(multiplierRecords = mapOf("AAPLx" to Result.failure(IOException("offline"))))
        val vm = viewModel(catalog = catalog, mints = FakeMintRepository(Result.failure(IOException("down"))))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.split is Piece.Failed)
            assertNull("no source answered, so there is no multiplier to draw", state.multiplier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Proof of reserves -------------------------------------------------------------------

    @Test
    fun `proof of reserves unavailable - a JSON null is absent, a failed call is failed`() = runTest {
        val notPublished = viewModel(catalog = catalog(reserves = mapOf("AAPLx" to Result.success(null))))
        notPublished.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue("xStocks publishes none for this symbol", state.reserves is Piece.Absent)
            assertNull(state.reserves.valueOrNull)
            assertNotNull("and the rest of the trust layer is untouched", state.chain.valueOrNull)
            cancelAndIgnoreRemainingEvents()
        }

        val down = viewModel(catalog = catalog(reserves = mapOf("AAPLx" to Result.failure(IOException("timeout")))))
        down.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue("the call failed, which is a different sentence", state.reserves is Piece.Failed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The analysis ------------------------------------------------------------------------

    @Test
    fun `analysis not served - the trust layer renders from the chain and xStocks alone`() = runTest {
        val vm = viewModel(summaries = FakeSummaryRepository())

        vm.state.test {
            val state = awaitUntil { !it.isLoading }

            assertEquals(AnalysisState.NotServed, state.analysisState)
            assertTrue(state.analysisNotServed)
            assertNull(state.analysis)
            assertEquals(R.string.detail_analysis_pending, (state.fundamentalsNotice!!.text as Copy.Words).id)

            assertNotNull("the mint was still read", state.chain.valueOrNull)
            assertTrue(state.reserves is Piece.Ready)
            assertEquals(MultiplierSource.MINT, (state.split as Piece.Ready).value.source)
            assertNotNull(state.price)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an incomplete filer and a transport failure are not the not-served case`() = runTest {
        val incomplete = viewModel(
            summaries = FakeSummaryRepository(
                analyses = mapOf("AAPL" to Result.failure(ApiException(503, "www.plainticker.com/api/v1/AAPL", "insufficient_data", null))),
            ),
        )
        incomplete.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(AnalysisState.Incomplete, state.analysisState)
            assertFalse(state.analysisNotServed)
            assertEquals("AAPLx", state.symbol)
            cancelAndIgnoreRemainingEvents()
        }

        val offline = viewModel(
            summaries = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.failure(IOException("offline")))),
        )
        offline.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(AnalysisState.Unavailable, state.analysisState)
            assertFalse(state.analysisNotServed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the analysis age is measured against the refresh clock and shown only past a day`() = runTest {
        val fresh = Instant.ofEpochMilli(now - 3 * 60 * 60 * 1000L).toString()
        val old = Instant.ofEpochMilli(now - 50 * 60 * 60 * 1000L).toString()

        val young = viewModel(summaries = summaryAsOf(fresh))
        young.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(3 * 60 * 60 * 1000L, state.analysisAgeMillis)
            assertFalse("under a day, so the header says nothing", state.analysisAgeShown)
            cancelAndIgnoreRemainingEvents()
        }

        val stale = viewModel(summaries = summaryAsOf(old))
        stale.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(50 * 60 * 60 * 1000L, state.analysisAgeMillis)
            assertTrue(state.analysisAgeShown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun summaryAsOf(asOf: String) =
        FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(analysis().copy(asOf = asOf))))

    // ---- The price ---------------------------------------------------------------------------

    @Test
    fun `price missing - Jupiter refused, and the rest of the screen stands`() = runTest {
        val vm = viewModel(prices = FakePriceRepository(Result.failure(IOException("429"))))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.quote is Piece.Failed)
            assertTrue(state.pricesUnavailable)
            assertNull(state.price)
            assertNull("no quote, so no tracking question to answer", state.tracking)
            assertNull(state.premiumPct)
            assertNotNull(state.analysis)
            assertNotNull(state.chain.valueOrNull)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mint Jupiter cannot price is absent, which raises no prices banner`() = runTest {
        val vm = viewModel(prices = FakePriceRepository(Result.success(emptyMap())))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.quote is Piece.Absent)
            assertFalse("nothing failed, so nothing is claimed to have", state.pricesUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the price row labels itself by market state, and by the local schedule when there is no block`() = runTest {
        val venueOpen = viewModel(catalog = catalog(trading = nyseOpen))
        venueOpen.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(MarketSource.VENUE, state.market!!.source)
            assertEquals(PriceLabel.TRACKING_WITHIN, state.priceLabel)
            cancelAndIgnoreRemainingEvents()
        }

        val venueClosed = viewModel(
            catalog = catalog(trading = nyseOpen.copy(currentPeriod = TradingPeriod.CLOSED, openNow = false)),
        )
        venueClosed.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(PriceLabel.VS_NYSE_CLOSE, state.priceLabel)
            cancelAndIgnoreRemainingEvents()
        }

        // No trading block at all: the local weekday schedule, and it says which it used.
        val fallback = viewModel(catalog = catalog())
        fallback.state.test {
            val state = awaitUntil { !it.isLoading }
            val market = checkNotNull(state.market)
            assertEquals(MarketSource.LOCAL_SCHEDULE, market.source)
            assertEquals(MarketState.REGULAR, market.state)
            assertEquals(PriceLabel.TRACKING_WITHIN, state.priceLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- A pending split ---------------------------------------------------------------------

    @Test
    fun `a pending split on the mint carries the new multiplier and its activation`() = runTest {
        val pending = readableMint(scaledUiAmount = scaled(1.0, newMultiplier = 4.0, effectiveAtEpochSeconds = 1_789_948_800L))
        val vm = viewModel(mints = pending)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            val split = (state.split as Piece.Ready).value

            assertEquals(1.0, split.current, 0.0)
            assertEquals(MultiplierSource.MINT, split.source)
            val pending = checkNotNull(split.pending)
            assertEquals(4.0, pending.multiplier, 0.0)
            assertEquals(1_789_948_800_000L, pending.activatesAtMillis())
            assertFalse("the refresh clock is before it", pending.activated(state.nowMillis))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a pending split the mint cannot report comes from xStocks, and says so`() = runTest {
        val noScaledAmount = FakeMintRepository(
            Result.success(MintReading(facts = mintFacts(scaledUiAmount = null), slot = 1L, readAtMillis = now)),
        )
        val catalog = catalog(
            multiplierRecords = mapOf(
                "AAPLx" to Result.success(
                    Multiplier(currentMultiplier = 1.0, newMultiplier = 4.0, activationDateTime = 1_789_948_800L, reason = "Split"),
                ),
            ),
        )
        val vm = viewModel(catalog = catalog, mints = noScaledAmount)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            val split = (state.split as Piece.Ready).value

            assertNotNull("the mint itself was read", state.chain.valueOrNull)
            assertEquals(MultiplierSource.XSTOCKS, split.source)
            assertEquals(4.0, split.pending!!.multiplier, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The catalog -------------------------------------------------------------------------

    @Test
    fun `catalog down - the analysis still renders, nothing is priced and nothing is claimed clean`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(catalog = FakeCatalogRepository(Result.failure(IOException("offline"))), prices = prices)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }

            assertTrue(state.catalogUnavailable)
            assertNotNull(state.analysis)
            assertNull(state.mint)
            assertNull(state.price)
            assertNull(state.multiplier)
            assertFalse("Jupiter was never asked, so it never refused", state.pricesUnavailable)
            assertTrue(prices.requested.isEmpty())

            assertTrue("with no mint the chain was not read", state.chain is Piece.Failed)
            assertTrue(state.reserves is Piece.Failed)
            assertTrue(state.split is Piece.Failed)

            assertEquals("and the venue falls back to the schedule", MarketSource.LOCAL_SCHEDULE, state.market!!.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a ticker with no xStock is an absent catalog entry, not a failed catalog`() = runTest {
        val vm = viewModel(ticker = "XYZ", summaries = FakeSummaryRepository())

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.catalogAsset is Piece.Absent)
            assertFalse(state.catalogUnavailable)
            assertNull(state.symbol)
            assertNull(state.mint)
            assertEquals(AnalysisState.NotServed, state.analysisState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Watch -------------------------------------------------------------------------------

    @Test
    fun `the watch state comes from the shared store and the toggle writes back to it`() = runTest {
        val watchlist = InMemoryWatchlistStore(setOf("TSLA"))
        val vm = viewModel(watchlist = watchlist)

        vm.state.test {
            assertFalse(awaitUntil { !it.isLoading }.watched)

            vm.toggleWatch()
            assertTrue(awaitUntil { it.watched }.watched)
            assertEquals(setOf("TSLA", "AAPL"), watchlist.tickers.value)

            vm.toggleWatch()
            assertFalse(awaitUntil { !it.watched }.watched)
            assertEquals(setOf("TSLA"), watchlist.tickers.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a ticker already watched is watched from the first emission`() = runTest {
        val vm = viewModel(watchlist = InMemoryWatchlistStore(setOf("AAPL")))

        vm.state.test {
            assertTrue(awaitItem().watched)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
