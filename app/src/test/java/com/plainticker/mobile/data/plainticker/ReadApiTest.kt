package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/v1/{ticker}/read` (server/vote/README.md, task S3): the peek for everyone, the full
 * content once a device presents an entitled code. Task A6's Detail model only ever reads
 * [TickerReadResponse.narrative] and [TickerReadResponse.nextSteps], so those are the two fields
 * this file pins the shape of; `sectorTable` and `methodHistory` are left for `ignoreUnknownKeys`.
 */
class ReadApiTest {

    private val body = """
        {
          "ticker": "AAPL",
          "pro": false,
          "narrative": {"excerptUk": "П.", "excerptEn": "First sentence.", "fullUk": null, "fullEn": null},
          "sectorTable": {"own": null, "peers": null},
          "methodHistory": {"latest": null, "history": null},
          "nextSteps": {"titlesUk": ["А"], "titlesEn": ["Check margins"], "stepsUk": null, "stepsEn": null}
        }
    """.trimIndent()

    @Test
    fun `a 200 parses the peek, and unknown fields on the wire do not break it`() = runTest {
        val mock = MockApi { respondJson(body) }
        val read = ReadApi(mock.client).get("AAPL", code = "ABCDE12345")

        assertEquals("AAPL", read.ticker)
        assertEquals(false, read.pro)
        assertEquals("First sentence.", read.narrative!!.excerptEn)
        assertNull("the full field is null unless pro", read.narrative.fullEn)
        assertEquals(listOf("Check margins"), read.nextSteps!!.titlesEn)
        assertNull(read.nextSteps.stepsEn)
        assertEquals("ABCDE12345", mock.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }

    @Test
    fun `an entitled code answers the full text too`() = runTest {
        val full = """
            {"ticker":"AAPL","pro":true,
             "narrative":{"excerptEn":"First sentence.","fullEn":"First sentence. Second one."},
             "nextSteps":{"titlesEn":["Check margins"],"stepsEn":["Margins rose."]}}
        """.trimIndent()
        val read = ReadApi(MockApi { respondJson(full) }.client).get("AAPL", "code")

        assertTrue(read.pro)
        assertEquals("First sentence. Second one.", read.narrative!!.fullEn)
        assertEquals(listOf("Margins rose."), read.nextSteps!!.stepsEn)
    }

    @Test
    fun `no code asks for the peek alone, and carries no header`() = runTest {
        val mock = MockApi { respondJson(body) }
        ReadApi(mock.client).get("AAPL")
        assertNull(mock.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }

    @Test
    fun `503 monetization disabled reads as disabled, never as an error banner`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        expectThrows<ReadError.Disabled> { ReadApi(mock.client).get("AAPL") }
    }

    @Test
    fun `422 unsupported ticker and 503 not available are both not served, and never a 404`() = runTest {
        val unsupported = MockApi { respondJson("""{"error":"x","code":"unsupported_ticker"}""", HttpStatusCode.UnprocessableEntity) }
        expectThrows<ReadError.NotServed> { ReadApi(unsupported.client).get("ZZZZ") }

        val notReady = MockApi { respondJson("""{"error":"x","code":"not_available"}""", HttpStatusCode.ServiceUnavailable) }
        expectThrows<ReadError.NotServed> { ReadApi(notReady.client).get("AAPL") }
    }

    @Test
    fun `a 429 is rate limited, anything else unreadable is unavailable`() = runTest {
        val limited = MockApi { respondJson("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }
        expectThrows<ReadError.RateLimited> { ReadApi(limited.client).get("AAPL") }

        val garbled = MockApi { respondJson("""{"ticker":""") }
        expectThrows<ReadError.Unavailable> { ReadApi(garbled.client).get("AAPL") }
    }
}
