package com.myapp.watchlist

import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.net.ApiException
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.plainticker.Forward
import com.myapp.data.plainticker.ForwardRaw
import com.myapp.data.plainticker.SummaryResponse
import com.myapp.data.plainticker.SummaryRow
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeSummaryRepository
import com.myapp.repo.price
import com.myapp.repo.xStock
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The join both the screen and the check read: `/summary` for the company, the catalog for the
 * token, the analysis payload for the report date, Jupiter for the premium. What a source that did
 * not answer costs is a field, never the row.
 */
class WatchlistFactsTest {

    private val summaries = FakeSummaryRepository()
    private val catalog = FakeCatalogRepository()
    private val prices = FakePriceRepository()
    private val facts = WatchlistFacts(summaries, catalog, prices)

    private fun serving(vararg tickers: String) {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-13T08:00:00.000Z",
                rows = tickers.map { SummaryRow(ticker = it, company = "$it Inc.") },
            ),
        )
    }

    private fun analysis(ticker: String, reportsOn: String?) {
        summaries.analyses = summaries.analyses + (
            ticker to Result.success(
                AnalysisPayload(ticker = ticker, forward = Forward(ForwardRaw(nextEarningsDate = reportsOn))),
            )
            )
    }

    @Test
    fun `a watched ticker carries its company, its token, its report date and its premium`() = runTest {
        serving("AAPL")
        catalog.assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL", name = "Apple xStock")))
        analysis("AAPL", "2026-10-28")
        prices.result = Result.success(mapOf("mint-AAPL" to price(usd = 232.54, reference = 232.52)))

        val row = facts.load(setOf("aapl")).rows.single()

        assertEquals("AAPL", row.ticker)
        assertEquals("AAPLx", row.symbol)
        assertEquals("AAPL Inc.", row.company)
        assertEquals(LocalDate.of(2026, 10, 28), row.nextReport)
        assertTrue(row.analyzed)
        assertTrue(row.tracking is TrackingQuality.Tracked)
    }

    @Test
    fun `a ticker summary no longer serves keeps its row and is not asked for an analysis`() = runTest {
        serving("AAPL")
        catalog.assets = Result.success(listOf(xStock("MCDx", "MCD", "mint-MCD")))

        val rows = facts.load(setOf("MCD")).rows

        assertEquals(1, rows.size)
        assertFalse("the leaderboard has dropped it", rows.single().analyzed)
        assertNull(rows.single().nextReport)
        assertEquals("MCDx", rows.single().symbol)
    }

    @Test
    fun `a payload with no report date is a row with no report date`() = runTest {
        serving("AAPL")
        catalog.assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL")))
        analysis("AAPL", null)

        assertNull(facts.load(setOf("AAPL")).rows.single().nextReport)
    }

    @Test
    fun `a thin pool withholds the premium exactly as it does on the list`() = runTest {
        serving("APP")
        catalog.assets = Result.success(listOf(xStock("APPx", "APP", "mint-APP")))
        analysis("APP", "2026-10-28")
        prices.result = Result.success(
            mapOf("mint-APP" to price(usd = 1_158.76, reference = 612.0, liquidity = 34.0)),
        )

        val row = facts.load(setOf("APP")).rows.single()

        assertEquals(TrackingQuality.Thin(34.0), row.tracking)
        assertNull(row.premiumPct)
    }

    @Test
    fun `summary out is reported and costs every row its analysis, not its place`() = runTest {
        summaries.summaryResult = Result.failure(ApiException(503, "summary", "unavailable", null))
        catalog.assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL")))

        val loaded = facts.load(setOf("AAPL"))

        assertTrue(loaded.analysisUnavailable)
        assertEquals(1, loaded.rows.size)
        assertEquals("AAPLx", loaded.rows.single().symbol)
        assertFalse(loaded.rows.single().analyzed)
    }

    @Test
    fun `the catalog out leaves the ticker standing with no token`() = runTest {
        serving("AAPL")
        analysis("AAPL", "2026-10-28")
        catalog.assets = Result.failure(ApiException(502, "catalog", "unavailable", null))

        val loaded = facts.load(setOf("AAPL"))

        assertTrue(loaded.catalogUnavailable)
        assertNull(loaded.rows.single().symbol)
        assertEquals("AAPL", loaded.rows.single().display)
        assertEquals(LocalDate.of(2026, 10, 28), loaded.rows.single().nextReport)
    }

    @Test
    fun `nothing watched asks nothing of anybody`() = runTest {
        assertEquals(WatchedFacts(), facts.load(emptySet()))
        assertEquals(0, summaries.summaryCalls)
        assertEquals(0, catalog.catalogCalls)
    }

    @Test
    fun `rows come back in ticker order whatever order they were asked in`() = runTest {
        serving("AAPL", "NVDA", "TSLA")
        catalog.assets = Result.success(
            listOf(
                xStock("AAPLx", "AAPL", "mint-AAPL"),
                xStock("NVDAx", "NVDA", "mint-NVDA"),
                xStock("TSLAx", "TSLA", "mint-TSLA"),
            ),
        )
        listOf("AAPL", "NVDA", "TSLA").forEach { analysis(it, null) }

        assertEquals(
            listOf("AAPL", "NVDA", "TSLA"),
            facts.load(listOf("TSLA", "AAPL", "NVDA")).rows.map { it.ticker },
        )
    }
}
