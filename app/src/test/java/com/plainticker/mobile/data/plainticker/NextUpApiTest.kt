package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.net.RateLimitedException
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * The next-up contract, against [MockApi] exactly as `/summary` is tested.
 *
 * The one thing pinned here that nothing else can pin is the weight: the server sends it as a
 * decimal string of base units, and a real account in the staking program decodes to twenty
 * digits, so the string is parsed with a BigInteger and never through a Long or a Double. A
 * weight that is not a number is null, and null is "not a figure", not zero.
 */
class NextUpApiTest {

    /** The README's own 200, field for field. */
    private val golden = """{"schema":"v1.1","generated_at":"2026-09-18T12:34:56.000Z","rows":[""" +
        """{"ticker":"NFLX","weight":"123456000000","voters":3,"last_vote_at":"2026-09-18T12:30:00.000Z"}]}"""

    @Test
    fun `the golden body is exactly the contract in the README, and parses`() = runTest {
        val parsed = HttpClientFactory.json.parseToJsonElement(golden) as JsonObject
        assertEquals(setOf("schema", "generated_at", "rows"), parsed.keys)
        assertEquals(
            setOf("ticker", "weight", "voters", "last_vote_at"),
            parsed["rows"]!!.jsonArray.single().jsonObject.keys,
        )

        val mock = MockApi { respondJson(golden) }
        val answer = NextUpApi(mock.client).getNextUp()

        assertEquals("v1.1", answer.schema)
        assertEquals("2026-09-18T12:34:56.000Z", answer.generatedAt)
        val row = answer.rows.single()
        assertEquals("NFLX", row.ticker)
        assertEquals("the weight stays the server's string", "123456000000", row.weight)
        assertEquals(3, row.voters)
        assertEquals("2026-09-18T12:30:00.000Z", row.lastVoteAt)
        assertEquals(BigInteger("123456000000"), row.weightRaw())
    }

    @Test
    fun `the request is a GET of the next-up route with the app's own agent`() = runTest {
        val mock = MockApi { respondJson(golden) }
        NextUpApi(mock.client).getNextUp()

        val request = mock.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://www.plainticker.com/api/v1/vote/next-up", request.url.toString())
        assertEquals(listOf(HttpClientFactory.USER_AGENT), request.headers.getAll(HttpHeaders.UserAgent))
    }

    @Test
    fun `no rows, or none at all, is an empty list and not a failure`() = runTest {
        val empty = MockApi { respondJson("""{"schema":"v1.1","generated_at":"2026-09-18T12:34:56.000Z","rows":[]}""") }
        assertTrue(NextUpApi(empty.client).getNextUp().rows.isEmpty())

        val absent = MockApi { respondJson("""{"schema":"v1.1","generated_at":"2026-09-18T12:34:56.000Z"}""") }
        assertTrue(NextUpApi(absent.client).getNextUp().rows.isEmpty())
    }

    @Test
    fun `rows keep the server's order and unknown keys are ignored`() = runTest {
        val three = """{"schema":"v1.1","generated_at":"2026-09-18T12:34:56.000Z","transform":"linear","rows":[""" +
            """{"ticker":"NFLX","weight":"3","voters":3,"rank":1},""" +
            """{"ticker":"AMD","weight":"2","voters":1},""" +
            """{"ticker":"TSM","weight":"1","voters":1}]}"""
        val mock = MockApi { respondJson(three) }
        val rows = NextUpApi(mock.client).getNextUp().rows
        assertEquals(listOf("NFLX", "AMD", "TSM"), rows.map { it.ticker })
        assertEquals("a row without last_vote_at parses", null, rows[1].lastVoteAt)
    }

    // ---- The weight ---------------------------------------------------------------------------

    @Test
    fun `the weight is parsed as a big integer, so twenty digits are twenty digits`() {
        assertEquals(BigInteger.ZERO, NextUpRow("A", "0").weightRaw())
        // The account one getProgramAccounts over the staking program actually returned on
        // 2026-09-13, which does not fit a signed 64-bit integer.
        val impossible = NextUpRow("A", "11452317590968446072").weightRaw()!!
        assertEquals(BigInteger("11452317590968446072"), impossible)
        assertTrue("past the Long", impossible > BigInteger.valueOf(Long.MAX_VALUE))
        assertEquals(BigInteger("123456000000"), NextUpRow("A", " 123456000000 ").weightRaw())
    }

    @Test
    fun `a weight that is not a plain non-negative integer is not a figure`() {
        listOf("-5", "1.5", "abc", "", "1e9", "0x10").forEach { weight ->
            assertNull("'$weight' must not read as a number", NextUpRow("A", weight).weightRaw())
        }
    }

    // ---- Refusals -----------------------------------------------------------------------------

    @Test
    fun `429 becomes RateLimitedException and 500 a structured ApiException`() = runTest {
        val limited = MockApi {
            respondJson("""{"error":"rate_limited","code":429}""", HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "30")
        }
        val e = expectThrows<RateLimitedException> { NextUpApi(limited.client).getNextUp() }
        assertEquals(429, e.status)
        assertEquals("rate_limited", e.errorCode)
        assertEquals(30L, e.retryAfterSeconds)

        val down = MockApi { respondJson("""{"error":"internal","code":500}""", HttpStatusCode.InternalServerError) }
        val internal = expectThrows<ApiException> { NextUpApi(down.client).getNextUp() }
        assertEquals(500, internal.status)
        assertEquals("internal", internal.errorCode)

        val gateway = MockApi { respondHtml("<html>502 Bad Gateway</html>", HttpStatusCode.BadGateway) }
        val page = expectThrows<ApiException> { NextUpApi(gateway.client).getNextUp() }
        assertEquals(502, page.status)
        assertNull(page.errorCode)
    }
}
