package com.myapp.ui.detail

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.awaitUntil
import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.net.ApiException
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeSummaryRepository
import com.myapp.repo.price
import com.myapp.repo.xStock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class DetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val aaplMint = "AAPLxMint".padEnd(44, '1')

    /** plainticker/analysis-aapl.json: the recorded v1.1 payload for AAPL. */
    private fun analysis(): AnalysisPayload =
        HttpClientFactory.json.decodeFromString(AnalysisPayload.serializer(), Fixtures.read("plainticker/analysis-aapl.json"))

    private fun served() = FakeSummaryRepository(analyses = mapOf("AAPL" to Result.success(analysis())))

    private fun catalog() = FakeCatalogRepository(
        Result.success(listOf(xStock("AAPLx", "AAPL", aaplMint, "Apple xStock"), xStock("TSLAx", "TSLA", KnownMints.TSLAX))),
        multipliers = mapOf("AAPLx" to 1.0),
    )

    @Test
    fun `joins the analysis, the catalog asset, the price and the multiplier for one ticker`() = runTest {
        val prices = FakePriceRepository(Result.success(mapOf(aaplMint to price(232.5, reference = 232.4))))
        val vm = DetailViewModel(" aapl ", served(), catalog(), prices)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals("AAPL", state.ticker)
            assertEquals("AAPLx", state.symbol)
            assertEquals(aaplMint, state.mint)
            assertEquals("AAPL", state.analysis?.ticker)
            assertNull(state.analysisUnavailable)
            assertEquals(232.5, state.price!!.usdPrice, 0.0)
            assertEquals(232.4, state.price!!.stockData!!.price!!, 0.0)
            assertEquals(1.0, state.multiplier!!, 0.0)
            assertFalse(state.catalogUnavailable)
            assertFalse(state.pricesUnavailable)
            assertEquals(listOf(listOf(aaplMint)), prices.requested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unserved ticker, an incomplete filer and a transport failure read differently`() = runTest {
        val unserved = DetailViewModel("XYZ", FakeSummaryRepository(), catalog(), FakePriceRepository())
        unserved.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals("Analysis not yet available", state.analysisUnavailable)
            assertNull(state.analysis)
            assertNull(state.symbol)
            assertNull(state.mint)
            cancelAndIgnoreRemainingEvents()
        }

        val incomplete = DetailViewModel(
            "AAPL",
            FakeSummaryRepository(analyses = mapOf("AAPL" to Result.failure(ApiException(503, "www.plainticker.com/api/v1/AAPL", "incomplete", null)))),
            catalog(),
            FakePriceRepository(),
        )
        incomplete.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals("Analysis incomplete for this filer", state.analysisUnavailable)
            assertEquals("AAPLx", state.symbol) // the catalog side still renders
            cancelAndIgnoreRemainingEvents()
        }

        val offline = DetailViewModel(
            "AAPL",
            FakeSummaryRepository(analyses = mapOf("AAPL" to Result.failure(IOException("offline")))),
            catalog(),
            FakePriceRepository(),
        )
        offline.state.test {
            assertEquals("Analysis unavailable", awaitUntil { !it.isLoading }.analysisUnavailable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `catalog down - the analysis still renders and nothing is priced`() = runTest {
        val prices = FakePriceRepository()
        val vm = DetailViewModel("AAPL", served(), FakeCatalogRepository(Result.failure(IOException("offline"))), prices)

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.catalogUnavailable)
            assertNotNull(state.analysis)
            assertNull(state.mint)
            assertNull(state.price)
            assertNull(state.multiplier)
            assertFalse(state.pricesUnavailable)
            assertTrue(prices.requested.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prices down - flagged, the rest stays`() = runTest {
        val vm = DetailViewModel("AAPL", served(), catalog(), FakePriceRepository(Result.failure(IOException("429"))))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertTrue(state.pricesUnavailable)
            assertNull(state.price)
            assertEquals(aaplMint, state.mint)
            assertNotNull(state.analysis)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
