package com.myapp.data.plainticker

import com.myapp.data.Fixtures
import com.myapp.data.MockApi
import com.myapp.data.expectThrows
import com.myapp.data.net.ApiException
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.net.RateLimitedException
import com.myapp.data.respondHtml
import com.myapp.data.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainTickerApiTest {

    private fun api(handler: MockApi) = PlainTickerApi(handler.client)

    // ---- /summary -------------------------------------------------------------------------

    @Test
    fun `getSummary parses the contract fixture`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("plainticker/summary.json")) }
        val summary = api(mock).getSummary()

        assertEquals("https://www.plainticker.com/api/v1/summary", mock.lastRequest.url.toString())
        assertEquals(listOf(HttpClientFactory.USER_AGENT), mock.lastRequest.headers.getAll(HttpHeaders.UserAgent))

        assertEquals("v1.1", summary.schema)
        assertEquals("2026-09-10T18:00:00.000Z", summary.generatedAt)
        assertEquals(3, summary.rows.size)

        val aapl = summary.rows[0]
        assertEquals("AAPL", aapl.ticker)
        assertEquals("Apple Inc.", aapl.company)
        assertEquals(Tone.CAUTION, aapl.tone)
        assertEquals(3, aapl.setupScore)
        assertEquals(0.71, aapl.composite!!, 1e-9)
        assertEquals(8, aapl.fscore)
        assertEquals("Information Technology", aapl.sector)
        assertEquals("2026-09-08T13:25:30.278Z", aapl.computedAt)
        assertFalse(aapl.stale)
        assertEquals(2, aapl.ageDays)

        val jpm = summary.rows[1]
        assertTrue(jpm.stale)
        assertEquals(9, jpm.ageDays)
        assertEquals(Tone.POSITIVE, jpm.tone)

        val bare = summary.rows[2]
        assertEquals("XYZ", bare.ticker)
        assertNull(bare.company)
        assertNull(bare.headline)
        assertNull(bare.tone)
        assertNull(bare.setupScore)
        assertNull(bare.composite)
        assertNull(bare.fscore)
        assertNull(bare.sector)
        assertEquals(1, bare.ageDays)
    }

    @Test
    fun `an unknown tone decodes as null rather than failing the row`() {
        val row = HttpClientFactory.json.decodeFromString(
            SummaryRow.serializer(),
            """{"ticker":"T","tone":"neutral","stale":false}""",
        )
        assertNull(row.tone)
    }

    @Test
    fun `429 becomes RateLimitedException with Retry-After`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"error":"rate_limited","code":429}""",
                HttpStatusCode.TooManyRequests,
                HttpHeaders.RetryAfter to "30",
            )
        }
        val e = expectThrows<RateLimitedException> { api(mock).getSummary() }
        assertEquals(429, e.status)
        assertEquals("rate_limited", e.errorCode)
        assertEquals(30L, e.retryAfterSeconds)
        assertEquals("www.plainticker.com/api/v1/summary", e.endpoint)
    }

    @Test
    fun `500 becomes a structured ApiException`() = runTest {
        val mock = MockApi { respondJson("""{"error":"internal","code":500}""", HttpStatusCode.InternalServerError) }
        val e = expectThrows<ApiException> { api(mock).getSummary() }
        assertEquals(500, e.status)
        assertEquals("internal", e.errorCode)
        assertFalse(e is RateLimitedException)
    }

    @Test
    fun `a gateway HTML page still becomes an ApiException, with no error code`() = runTest {
        val mock = MockApi { respondHtml("<html><body>Bad gateway</body></html>", HttpStatusCode.BadGateway) }
        val e = expectThrows<ApiException> { api(mock).getSummary() }
        assertEquals(502, e.status)
        assertNull(e.errorCode)
        assertTrue(e.body!!.contains("<html>"))
    }

    // ---- /{TICKER} ------------------------------------------------------------------------

    @Test
    fun `getAnalysis parses the live v1_1 payload`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("plainticker/analysis-aapl.json")) }
        val payload = api(mock).getAnalysis("aapl")

        assertEquals("https://www.plainticker.com/api/v1/AAPL", mock.lastRequest.url.toString())

        assertEquals("AAPL", payload.ticker)
        assertEquals("Apple Inc.", payload.company)
        assertEquals("Information Technology", payload.sector)
        assertEquals("2026-09-08T13:25:30.278Z", payload.asOf)
        assertEquals("v1.1", payload.schemaVersion)

        val quality = payload.axes.quality!!
        assertEquals(8.0, quality.value!!, 1e-9)
        assertEquals("0-9", quality.scale)
        assertEquals(8.0 / 9.0, quality.position!!, 1e-9)
        assertEquals("strong", quality.state)
        assertEquals("Strong", quality.labelEn)
        assertEquals(Tone.POSITIVE, quality.tone)

        val valuation = payload.axes.valuation!!
        assertEquals("fair", valuation.state)
        assertEquals("Moderate", valuation.labelEn)
        assertEquals(Tone.CAUTION, valuation.tone)
        assertEquals("0-100", valuation.scale)

        val momentum = payload.axes.momentum!!
        assertEquals("high", momentum.state)
        assertEquals("Near 52-week high", momentum.labelEn)
        assertEquals(0.7926150733434499, momentum.position!!, 1e-12)

        val fscore = payload.fscore!!
        assertEquals(8, fscore.score)
        assertEquals(9, fscore.signals.size)
        assertEquals(8, fscore.passedCount)
        assertEquals(listOf(true, true, true, false, true, true, true, true, true), fscore.signals)
        assertEquals(3, fscore.breakdown!!.profitability)
        assertEquals(3, fscore.breakdown.leverageLiquidity)
        assertEquals(2, fscore.breakdown.efficiency)

        assertEquals(51.0, payload.compositePercentile!!, 1e-9)

        val setup = payload.setup!!
        assertEquals(3, setup.score)
        assertEquals("0-5", setup.scale)
        assertEquals(5, setup.criteria.size)
        assertEquals(listOf("quality", "growing", "cheaper", "fcfMargin", "notTopped"), setup.criteria.map { it.key })
        assertFalse(setup.criteria.first { it.key == "cheaper" }.passed)

        val method = payload.method!!
        assertFalse(method.isPrediction)
        assertEquals("classification", method.kind)
        assertTrue(method.statementEn!!.startsWith("Rule-based classification of fundamentals"))
        assertTrue(method.statementEn.contains("not investment advice"))
    }

    @Test
    fun `ageDays counts whole days since as_of`() {
        val payload = AnalysisPayload(ticker = "T", asOf = "2026-09-08T13:25:30.278Z")
        val asOf = payload.asOfEpochMillis()
        assertNotNull(asOf)
        assertEquals(0L, payload.ageDays(asOf!!))
        assertEquals(2L, payload.ageDays(asOf + 2 * 86_400_000L + 5_000L))
        assertEquals(0L, payload.ageDays(asOf - 60_000L))
        assertNull(AnalysisPayload(ticker = "T", asOf = "not a date").ageDays(asOf))
        assertNull(AnalysisPayload(ticker = "T").ageDays(asOf))
    }

    @Test
    fun `getAnalysis refuses anything that is not a ticker`() = runTest {
        val mock = MockApi { respondJson("{}") }
        expectThrows<IllegalArgumentException> { api(mock).getAnalysis("../summary") }
        expectThrows<IllegalArgumentException> { api(mock).getAnalysis("") }
        assertEquals(0, mock.requests.size)
    }

    @Test
    fun `getAnalysis 429 is typed too`() = runTest {
        val mock = MockApi { respondJson("""{"error":"rate_limited","code":429}""", HttpStatusCode.TooManyRequests) }
        val e = expectThrows<RateLimitedException> { api(mock).getAnalysis("JPM") }
        assertNull(e.retryAfterSeconds)
    }

    @Test
    fun `a null Piotroski signal is kept as unknown and not counted as passed`() {
        val fscore = HttpClientFactory.json.decodeFromString(
            FScore.serializer(),
            """{"score":7,"scale":"0-9","signals":[true,true,null,false,true,true,true,true,true]}""",
        )
        assertEquals(9, fscore.signals.size)
        assertNull(fscore.signals[2])
        assertEquals(7, fscore.passedCount)
    }
}
