package com.myapp.ui.list

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.awaitUntil
import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.net.ApiException
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.plainticker.SummaryResponse
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeSummaryRepository
import com.myapp.repo.price
import com.myapp.repo.xStock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    // Synthetic mints: the ViewModel never validates them, only joins on them.
    private val aaplMint = "AAPLxMint".padEnd(44, '1')
    private val jpmMint = "JPMxMint".padEnd(44, '1')
    private val tslaMint = KnownMints.TSLAX

    /** plainticker/summary.json: AAPL 0.71, JPM 0.66 (stale, 9 d), XYZ with every optional field null. */
    private fun summary(): SummaryResponse =
        HttpClientFactory.json.decodeFromString(SummaryResponse.serializer(), Fixtures.read("plainticker/summary.json"))

    private fun catalog() = listOf(
        xStock("TSLAx", "TSLA", tslaMint, "Tesla xStock"),
        xStock("JPMx", "JPM", jpmMint, "JPMorgan xStock"),
        xStock("AAPLx", "AAPL", aaplMint, "Apple xStock"),
    )

    private fun viewModel(
        summaries: FakeSummaryRepository = FakeSummaryRepository(Result.success(summary())),
        catalog: FakeCatalogRepository = FakeCatalogRepository(Result.success(catalog())),
        prices: FakePriceRepository = FakePriceRepository(),
    ) = ListViewModel(summaries, catalog, prices)

    @Test
    fun `loading, then analyzed rows joined to mints and prices, then rows without analysis`() = runTest {
        val prices = FakePriceRepository(
            Result.success(mapOf(aaplMint to price(232.5, reference = 232.4), jpmMint to price(301.0))),
        )
        val vm = viewModel(prices = prices)

        vm.state.test {
            assertTrue(awaitItem().isLoading)
            val state = awaitUntil { !it.isLoading }

            assertNull(state.error)
            assertFalse(state.catalogUnavailable)
            assertFalse(state.pricesUnavailable)
            assertEquals("2026-09-10T18:00:00.000Z", state.generatedAt)

            // Composite descending, the row without a composite last.
            assertEquals(listOf("AAPL", "JPM", "XYZ"), state.analyzed.map { it.ticker })

            val aapl = state.analyzed[0]
            assertEquals("AAPLx", aapl.symbol)
            assertEquals(aaplMint, aapl.mint)
            assertEquals("Apple Inc.", aapl.company)
            assertEquals(232.5, aapl.priceUsd!!, 0.0)
            assertEquals(232.4, aapl.referencePriceUsd!!, 0.0)
            assertFalse(aapl.stale)
            assertTrue(aapl.hasAnalysis)

            val jpm = state.analyzed[1]
            assertTrue(jpm.stale)
            assertEquals(9, jpm.ageDays)
            assertEquals(301.0, jpm.priceUsd!!, 0.0)
            assertNull(jpm.referencePriceUsd)

            val xyz = state.analyzed[2]
            assertNull(xyz.symbol)
            assertNull(xyz.mint)
            assertNull(xyz.priceUsd)
            assertNull(xyz.composite)

            // Catalog assets PlainTicker has not classified, keyed by the underlying ticker.
            assertEquals(listOf("TSLAx"), state.withoutAnalysis.map { it.symbol })
            assertEquals("TSLA", state.withoutAnalysis[0].ticker)
            assertFalse(state.withoutAnalysis[0].hasAnalysis)

            // One price call, analyzed mints only: not XYZ (no mint), not the unanalyzed catalog.
            assertEquals(listOf(listOf(aaplMint, jpmMint)), prices.requested)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `summary down, catalog up - rows without analysis still render and no price is asked`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.failure(ApiException(502, "www.plainticker.com/api/v1/summary", null, null))),
            prices = prices,
        )

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertNull(state.error)
            assertTrue(state.analyzed.isEmpty())
            assertEquals(listOf("AAPLx", "JPMx", "TSLAx"), state.withoutAnalysis.map { it.symbol })
            assertTrue(prices.requested.isEmpty())
            assertFalse(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `catalog down, summary up - analyzed rows render without mints`() = runTest {
        val vm = viewModel(catalog = FakeCatalogRepository(Result.failure(IOException("offline"))))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertNull(state.error)
            assertTrue(state.catalogUnavailable)
            assertEquals(3, state.analyzed.size)
            assertTrue(state.analyzed.all { it.mint == null && it.priceUsd == null })
            assertTrue(state.withoutAnalysis.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both sources down - one error, nothing to draw`() = runTest {
        val vm = viewModel(
            summaries = FakeSummaryRepository(Result.failure(IOException("offline"))),
            catalog = FakeCatalogRepository(Result.failure(IOException("offline"))),
        )

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertEquals("Analysis list unavailable", state.error)
            assertTrue(state.analyzed.isEmpty())
            assertTrue(state.withoutAnalysis.isEmpty())
            assertFalse(state.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prices down - rows keep rendering with a partial flag`() = runTest {
        val vm = viewModel(prices = FakePriceRepository(Result.failure(IOException("429"))))

        vm.state.test {
            val state = awaitUntil { !it.isLoading }
            assertNull(state.error)
            assertTrue(state.pricesUnavailable)
            assertEquals(3, state.analyzed.size)
            assertTrue(state.analyzed.all { it.priceUsd == null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search filters by ticker, symbol or company, and clears`() = runTest {
        val vm = viewModel()

        vm.state.test {
            awaitUntil { !it.isLoading }

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

            vm.search("")
            val all = awaitUntil { it.query == "" }
            assertEquals(3, all.analyzed.size)
            assertEquals(1, all.withoutAnalysis.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh asks the sources again`() = runTest {
        val summaries = FakeSummaryRepository(Result.success(summary()))
        val vm = viewModel(summaries = summaries)

        vm.state.test {
            awaitUntil { !it.isLoading }
            assertEquals(1, summaries.summaryCalls)
            vm.refresh()
            awaitUntil { it.isLoading }
            awaitUntil { !it.isLoading }
            assertEquals(2, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
