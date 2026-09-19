package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/v1/entitlement` (server/vote/README.md), including the state a fresh, un-flagged
 * server is in today: a 503 `monetization_disabled` (or a 404, which predates the flag) reads as
 * [EntitlementError.Disabled] rather than as a failure, so the Portfolio's Pro block can say Pro
 * is not offered yet instead of raising an error banner (task A6's acceptance list).
 */
class EntitlementApiTest {

    @Test
    fun `a 200 parses pro, its source and when it ends`() = runTest {
        val mock = MockApi { respondJson("""{"pro":true,"source":"pass","until":"2026-10-19T00:00:00.000Z"}""") }
        val answer = EntitlementApi(mock.client).get("ABCDE12345")

        assertEquals(true, answer.pro)
        assertEquals(EntitlementSource.PASS, answer.sourceKind)
        assertEquals(1_792_368_000_000L, answer.untilEpochMillis())
    }

    @Test
    fun `no code and no entitlement is a plain 200, never an error`() = runTest {
        val mock = MockApi { respondJson("""{"pro":false,"source":null,"until":null}""") }
        val answer = EntitlementApi(mock.client).get(code = null)

        assertEquals(false, answer.pro)
        assertNull(answer.sourceKind)
        assertNull(answer.untilEpochMillis())
        assertTrue("no header at all when there is no code", mock.lastRequest.headers[EntitlementApi.HEADER_CODE] == null)
    }

    @Test
    fun `the code reaches the header in the clear, never hashed`() = runTest {
        val mock = MockApi { respondJson("""{"pro":false}""") }
        EntitlementApi(mock.client).get("K7M9QRSTXY")

        assertEquals("K7M9QRSTXY", mock.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }

    @Test
    fun `503 monetization disabled reads as disabled, not as a failure`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        val error = expectThrows<EntitlementError.Disabled> { EntitlementApi(mock.client).get() }
        assertEquals(503, error.status)
    }

    @Test
    fun `a 404 reads the same way, since it predates the flag`() = runTest {
        val mock = MockApi { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }
        expectThrows<EntitlementError.Disabled> { EntitlementApi(mock.client).get() }
    }

    @Test
    fun `a 429 is rate limited, and anything else this app cannot act on is unavailable`() = runTest {
        val limited = MockApi { respondJson("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }
        expectThrows<EntitlementError.RateLimited> { EntitlementApi(limited.client).get() }

        val down = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
        expectThrows<EntitlementError.Unavailable> { EntitlementApi(down.client).get() }
    }

    /** So a private header value from a wallet's own code never rides on a request it did not ask for. */
    @Test
    fun `every request carries the standard user agent and nothing extra by accident`() = runTest {
        val mock = MockApi { respondJson("""{"pro":false}""") }
        EntitlementApi(mock.client).get("code")
        assertTrue(mock.lastRequest.headers[HttpHeaders.UserAgent]!!.isNotBlank())
    }
}
