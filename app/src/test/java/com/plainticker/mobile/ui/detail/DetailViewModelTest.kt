package com.plainticker.mobile.ui.detail

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.R
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.MultiplierSource
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import com.plainticker.mobile.data.plainticker.ReadApi
import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.data.plainticker.Verdict
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.DefaultAccountState
import com.plainticker.mobile.data.rpc.PausableConfig
import com.plainticker.mobile.data.rpc.PermanentDelegate
import com.plainticker.mobile.data.rpc.TransferHookConfig
import com.plainticker.mobile.data.xstocks.Exchange
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketState
import com.plainticker.mobile.data.xstocks.Multiplier
import com.plainticker.mobile.data.xstocks.PriceLabel
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.prefs.InMemoryDevicePassStore
import com.plainticker.mobile.prefs.InMemoryNotificationPromptStore
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeMintRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.MintReading
import com.plainticker.mobile.repo.PlainTickerSummaryRepository
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.proofOfReserves
import com.plainticker.mobile.repo.scaled
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.repo.xStockTrading
import com.plainticker.mobile.ui.Copy
import io.ktor.http.HttpStatusCode
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
        reserves: Map<String, Result<com.plainticker.mobile.data.xstocks.ProofOfReserves?>> = mapOf(
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
        scaledUiAmount: com.plainticker.mobile.data.rpc.ScaledUiAmountConfig? = scaled(1.0),
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
        nextUp: FakeNextUpRepository = FakeNextUpRepository(),
        watchlist: InMemoryWatchlistStore = InMemoryWatchlistStore(),
        prompts: InMemoryNotificationPromptStore = InMemoryNotificationPromptStore(),
        readApi: ReadApi? = null,
        devicePassStore: InMemoryDevicePassStore = InMemoryDevicePassStore(),
    ) = DetailViewModel(ticker, summaries, catalog, prices, mints, nextUp, watchlist, prompts, clock, readApi, devicePassStore)

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
    fun `a ticker PlainTicker does not serve asks where it stands, and a served one does not`() = runTest {
        val leaders = FakeNextUpRepository(
            Result.success(listOf(NextUpRow("NFLX", "123456000000", 3), NextUpRow("AAPL", "38406150222", 2))),
        )
        val unserved = viewModel(summaries = FakeSummaryRepository(), nextUp = leaders)
        unserved.state.test {
            val state = awaitUntil { !it.isLoading && it.nextUp.isNotEmpty() }
            assertEquals(1, leaders.calls)
            assertEquals(listOf("NFLX", "AAPL"), state.nextUp.map { it.ticker })
            assertEquals("second of two", listOf("2", "2"), (state.nextUpLine!!.rank as Copy.Words).args)
            cancelAndIgnoreRemainingEvents()
        }

        val served = FakeNextUpRepository(Result.success(listOf(NextUpRow("AAPL", "38406150222", 2))))
        val covered = viewModel(nextUp = served)
        covered.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals("a covered ticker has no standing to ask about", 0, served.calls)
            assertTrue(state.nextUp.isEmpty())
            assertNull(state.nextUpLine)
            cancelAndIgnoreRemainingEvents()
        }

        val down = viewModel(
            summaries = FakeSummaryRepository(),
            nextUp = FakeNextUpRepository(Result.failure(IOException("offline"))),
        )
        down.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue("a failed call states no standing", state.nextUp.isEmpty())
            assertNull(state.nextUpLine)
            assertTrue("and the rest of the not-served screen stands", state.analysisNotServed)
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

    // ---- The verdict (task app-verdict) --------------------------------------------------------

    @Test
    fun `the ticker call carries this device's own code, the same header the read call sends`() = runTest {
        val store = InMemoryDevicePassStore("ABCDE12345")
        val repo = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(analysis())))
        val vm = viewModel(summaries = repo, devicePassStore = store)

        vm.state.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("ABCDE12345", repo.lastAnalysisCode)
    }

    /**
     * The same header, this time through the real repository and the real client rather than the
     * fake, so a defect in the wiring between [DetailViewModel], [PlainTickerSummaryRepository]
     * and [PlainTickerApi] cannot hide behind a fake that never touches the wire. [FakeSummaryRepository]
     * cannot stand in for [PlainTickerSummaryRepository] here, so this one builds the view model
     * directly rather than through this file's [viewModel] helper.
     */
    @Test
    fun `the code reaches the wire, end to end, through the real client`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("plainticker/analysis-aapl.json")) }
        val vm = DetailViewModel(
            "AAPL",
            PlainTickerSummaryRepository(PlainTickerApi(mock.client)),
            catalog(),
            FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
            readableMint(),
            FakeNextUpRepository(),
            InMemoryWatchlistStore(),
            InMemoryNotificationPromptStore(),
            clock,
            null,
            InMemoryDevicePassStore("ABCDE12345"),
        )

        vm.state.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("ABCDE12345", mock.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }

    @Test
    fun `an unlocked verdict lands as the payload's own label`() = runTest {
        val verdict = Verdict(code = "quality_compounder", labelEn = "Quality compounder", tone = Tone.POSITIVE)
        val vm = viewModel(
            summaries = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(analysis().copy(verdict = verdict)))),
        )
        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            val block = state.verdictBlock
            assertTrue(block is VerdictBlock.Unlocked)
            assertEquals("Quality compounder", ((block as VerdictBlock.Unlocked).label as Copy.Raw).text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a locked verdict lands as locked, and the rest of the screen is unaffected`() = runTest {
        val vm = viewModel(
            summaries = FakeSummaryRepository(
                analyses = mapOf("AAPL" to Result.success(analysis().copy(verdict = Verdict(locked = true)))),
            ),
        )
        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(VerdictBlock.Locked, state.verdictBlock)
            assertNotNull("the trust layer is unaffected by a locked verdict", state.chain.valueOrNull)
            cancelAndIgnoreRemainingEvents()
        }
    }

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

    /**
     * Plan section 13 Pass 2 and Pass 7: the notification permission is requested at the moment
     * the first ticker is watched, never at launch, and a refusal is an answer rather than a state
     * to work around. The ViewModel decides the moment; the screen owns the request itself.
     */
    @Test
    fun `the permission is asked for on the first ticker ever watched and never again`() = runTest {
        val watchlist = InMemoryWatchlistStore()
        val prompts = InMemoryNotificationPromptStore()
        val first = viewModel(ticker = "AAPL", watchlist = watchlist, prompts = prompts)

        assertTrue("the first ticker on an empty watchlist asks", first.toggleWatch())
        assertTrue(prompts.hasAsked())

        val second = viewModel(ticker = "TSLA", watchlist = watchlist, prompts = prompts)
        assertFalse("the second ticker does not ask again", second.toggleWatch())

        // Unwatching everything and starting over is still not a reason to ask a second time.
        first.toggleWatch()
        second.toggleWatch()
        assertTrue(watchlist.tickers.value.isEmpty())
        assertFalse(first.toggleWatch())
        assertEquals("the flag is written once", 1, prompts.writes)
    }

    @Test
    fun `unwatching never asks for the permission`() = runTest {
        val watchlist = InMemoryWatchlistStore(setOf("AAPL"))
        val prompts = InMemoryNotificationPromptStore()
        val vm = viewModel(watchlist = watchlist, prompts = prompts)

        assertFalse(vm.toggleWatch())
        assertTrue(watchlist.tickers.value.isEmpty())
        assertFalse("and it was never asked", prompts.hasAsked())
    }

    @Test
    fun `a device that has already answered is not asked when it watches its first ticker`() = runTest {
        val prompts = InMemoryNotificationPromptStore(asked = true)
        val vm = viewModel(watchlist = InMemoryWatchlistStore(), prompts = prompts)

        assertFalse(vm.toggleWatch())
        assertEquals(0, prompts.writes)
    }

    @Test
    fun `a ticker already watched is watched from the first emission`() = runTest {
        val vm = viewModel(watchlist = InMemoryWatchlistStore(setOf("AAPL")))

        vm.state.test {
            assertTrue(awaitItem().watched)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The clock ---------------------------------------------------------------------------

    /**
     * Every age on the screen is measured against the state's own clock, so the state's clock has
     * to move while the screen is open. Taken once at the refresh it is older than the reads it is
     * compared against, and the live bar would breathe for ever over a meta line stuck at "0 s ago".
     */
    @Test
    fun `the clock keeps moving while the screen is watched, so the live bar goes static`() = runTest {
        var moment = now
        val vm = DetailViewModel(
            "AAPL",
            served(),
            catalog(),
            FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
            readableMint(),
            FakeNextUpRepository(),
            InMemoryWatchlistStore(),
            InMemoryNotificationPromptStore(),
            { moment },
        )

        vm.state.test {
            val landed = awaitUntil { !it.isLoading }
            assertEquals(now, landed.nowMillis)
            assertTrue("the read is two seconds old", landed.liveLine!!.live)

            moment = now + LIVE_WINDOW_MILLIS + DetailViewModel.TICK_MILLIS
            val later = awaitUntil { it.nowMillis > landed.nowMillis }

            assertEquals(moment, later.nowMillis)
            assertFalse("past the forwarder's cache window the bar stops breathing", later.liveLine!!.live)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The read and What to check next (task A6) ---------------------------------------

    @Test
    fun `the read call carries the ticker and this device's own code, in the clear`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"ticker":"AAPL","pro":false,"narrative":{"excerptEn":"First sentence."}}""",
            )
        }
        val store = InMemoryDevicePassStore("ABCDE12345")
        val vm = viewModel(readApi = ReadApi(mock.client), devicePassStore = store)

        vm.state.test {
            val state = awaitUntil { it.read is ReadState.Ready }
            assertEquals("First sentence.", (state.readNarrative!!.text as Copy.Raw).text)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(mock.lastRequest.url.encodedPath.endsWith("/AAPL/read"))
        assertEquals("ABCDE12345", mock.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }

    @Test
    fun `no read api wired leaves the read at loading, and never blocks the rest of the screen`() = runTest {
        val vm = viewModel(readApi = null)
        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals(ReadState.Loading, state.read)
            assertNotNull("the free sources are unaffected", state.analysis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The byte-exact live production response (`plainticker/read-aapl.json`, see
     * [com.plainticker.mobile.data.plainticker.ReadFixtureDecodeTest]), through the whole path a
     * phone actually takes: [ReadApi] over a mock engine standing in for the real client, and
     * [DetailViewModel.refresh] driving [DetailViewModel.loadRead] the same way it does on device.
     * This is the test the v0.8.0 "no read section at all" regression needed and did not have:
     * every other read-related test hand-writes its own body that already matches the model
     * exactly, so none of them could have caught a defect that only the real payload triggers.
     */
    @Test
    fun `the live AAPL response lands as a Ready read with a non-null readNarrative, end to end`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("plainticker/read-aapl.json")) }
        val vm = viewModel(readApi = ReadApi(mock.client))

        vm.state.test {
            val state = awaitUntil { it.read is ReadState.Ready }
            assertNotNull("the peek must render for an unentitled reader against the live shape", state.readNarrative)
            assertFalse(state.readNarrative!!.full)
            assertNotNull(state.nextStepsBlock)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The shape a phone holding a paid device pass actually receives, never exercised by
     * [`the live AAPL response...`][com.plainticker.mobile.ui.detail.DetailViewModelTest] above:
     * that fixture is `pro: false` because it was captured with no code presented. This is the v1
     * "no read section at all" regression on a release build (`ReadState.Failed` behind a 200):
     * `nextSteps.stepsEn` on the wire is a list of `{title, body}` objects, and a model that
     * declared it `List<String>` threw on exactly this shape while decoding the peek fine, so the
     * defect only ever showed up once a device sent `X-PT-Code` for real.
     */
    @Test
    fun `the entitled AAPL response lands as a Ready read with the full text, end to end`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("plainticker/read-aapl-entitled.json")) }
        val store = InMemoryDevicePassStore("ABCDE12345")
        val vm = viewModel(readApi = ReadApi(mock.client), devicePassStore = store)

        vm.state.test {
            val state = awaitUntil { it.read is ReadState.Ready }
            val narrative = state.readNarrative
            assertNotNull("an entitled reader must still get a read block, not ReadState.Failed", narrative)
            assertTrue("the full text must be used, not the excerpt", narrative!!.full)
            val payload = (state.read as ReadState.Ready).payload
            assertEquals(payload.narrative!!.fullEn, (narrative.text as Copy.Raw).text)

            val steps = state.nextStepsBlock
            assertNotNull(steps)
            assertTrue(steps!!.full)
            assertEquals(payload.nextSteps!!.stepsEn!!.map { it.body }, steps.items.map { it.detail })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Considered and ruled out: [DevicePassStore.code] is called inside [DetailViewModel]'s own
     * try block for both the read call (`ReadState.Ready(api.get(ticker, devicePassStore?.code()))`)
     * and, since task app-verdict, the ticker call itself
     * (`summaries.analysis(ticker, devicePassStore?.code())`), so a store that throws must settle
     * each to its own failure state exactly like a failed network call, and never crash or leave
     * either stuck at Loading.
     *
     * Updated for task app-verdict: before it, the analysis call never touched the device pass
     * store, so this test asserted `state.analysis` stood unaffected. Now the verdict needs the
     * same code the read call needs, so a throwing store is a transport failure for the analysis
     * too and it settles to [AnalysisState.Unavailable], the same outcome an offline analysis call
     * already reads as ([DetailModelTest]'s `classify`). What stays true, and is asserted here
     * instead, is that the trust layer, which never reads the device pass store, is unaffected.
     */
    @Test
    fun `a device pass store that throws settles the read and the analysis to their own failure, never the trust layer`() = runTest {
        val throwingStore = object : DevicePassStore {
            override fun code(): String = throw IllegalStateException("prefs unavailable")
            override fun codeHash(): String = throw IllegalStateException("prefs unavailable")
        }
        val vm = DetailViewModel(
            "AAPL",
            served(),
            catalog(),
            FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4)))),
            readableMint(),
            FakeNextUpRepository(),
            InMemoryWatchlistStore(),
            InMemoryNotificationPromptStore(),
            clock,
            ReadApi(MockApi { respondJson("""{"ticker":"AAPL","pro":false}""") }.client),
            throwingStore,
        )

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals("a throwing store must not crash the read into staying Loading", ReadState.Failed, state.read)
            assertEquals(
                "the ticker call needs the same code the read call needs (task app-verdict)",
                AnalysisState.Unavailable,
                state.analysisState,
            )
            assertNotNull("the trust layer never reads the device pass store, so it is unaffected", state.chain.valueOrNull)
            assertTrue(state.reserves is Piece.Ready)
            assertNotNull(state.price)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `503 monetization disabled settles the read to disabled, drawing neither new block`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        val vm = viewModel(readApi = ReadApi(mock.client))
        vm.state.test {
            val state = awaitUntil { it.read is ReadState.Disabled }
            assertNull(state.readNarrative)
            assertNull(state.nextStepsBlock)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
