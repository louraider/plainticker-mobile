package com.myapp.data.jupiter

import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.MockApi
import com.myapp.data.net.ApiException
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.net.RateLimitedException
import com.myapp.data.respondJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JupiterPriceApiTest {

    /** Every pause the api asked for, in order, instead of a real one. */
    private val waits = mutableListOf<Long>()

    private fun api(mock: MockApi) = JupiterPriceApi(mock.client, sleep = { millis -> waits += millis })

    private fun mints(count: Int) = (1..count).map { "Mint%03d".format(it) }

    private fun HttpRequestData.ids(): List<String> = url.parameters["ids"]!!.split(",")

    private fun priceAll(ids: List<String>) = ids.joinToString(",", "{", "}") { "\"$it\": {\"usdPrice\": 1.5}" }

    /** The body the keyless gateway actually returns, recorded from the laptop on 2026-09-12. */
    private val gateway429 = """{"code":429,"message":"[API Gateway] Too many requests"}"""

    /**
     * jupiter/price-v3.json is the live 2026-09-10 answer for USDC, TSLAx and SKR, with the
     * USDC stockData set to an explicit null and the SKR one removed, so both "not an equity"
     * encodings are covered. TSLAx is verbatim.
     */
    @Test
    fun `prices parses the recorded v3 map, stockData null and absent alike`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("jupiter/price-v3.json")) }
        val fetch = api(mock).prices(listOf(KnownMints.USDC, KnownMints.TSLAX, KnownMints.SKR))

        assertEquals("https://api.jup.ag/price/v3", mock.lastRequest.url.toString().substringBefore("?"))
        assertEquals("${KnownMints.USDC},${KnownMints.TSLAX},${KnownMints.SKR}", mock.lastRequest.url.parameters["ids"])
        assertEquals(listOf(HttpClientFactory.USER_AGENT), mock.lastRequest.headers.getAll(HttpHeaders.UserAgent))

        val prices = fetch.priced
        assertEquals(3, prices.size)
        assertFalse(fetch.isPartial)
        assertNull(fetch.failure)

        val usdc = prices.getValue(KnownMints.USDC)
        assertEquals(0.9998399064222775, usdc.usdPrice, 1e-15)
        assertEquals(6, usdc.decimals)
        assertEquals(445979827L, usdc.blockId)
        assertNull(usdc.stockData)

        val skr = prices.getValue(KnownMints.SKR)
        assertEquals(0.019214312093881306, skr.usdPrice, 1e-15)
        assertNull(skr.stockData)
        assertTrue(skr.priceChange24h!! < 0)

        val tsla = prices.getValue(KnownMints.TSLAX)
        assertEquals(363.39680127984144, tsla.usdPrice, 1e-9)
        assertEquals(8, tsla.decimals)
        val stock = tsla.stockData
        assertNotNull(stock)
        assertEquals("xstocks", stock!!.id)
        assertEquals(363.88, stock.price!!, 1e-9)
        assertEquals("2026-09-10T20:51:39.029Z", stock.updatedAt)
        assertTrue(stock.mcap!! > 1e12)
    }

    @Test
    fun `a mint Jupiter cannot price is dropped, not an error and not unfetched`() = runTest {
        val unknown = "Unknown111111111111111111111111111111111111"
        val mock = MockApi { respondJson("""{"$unknown": null, "${KnownMints.USDC}": {"usdPrice": 1.0, "decimals": 6}}""") }
        val fetch = api(mock).prices(listOf(unknown, KnownMints.USDC))

        assertEquals(setOf(KnownMints.USDC), fetch.priced.keys)
        assertEquals(1.0, fetch.priced.getValue(KnownMints.USDC).usdPrice, 0.0)
        // Jupiter answered about the unknown mint, so it is absent, never unfetched.
        assertTrue(fetch.unfetched.isEmpty())
        assertFalse(fetch.isPartial)
        assertNull(fetch.failure)
    }

    @Test
    fun `a mint listed with no usdPrice does not take its chunk down with it`() = runTest {
        // The shape production really serves: 39 of 50 xStocks came back like `noPrice` on
        // 2026-09-12, with every field but the price. Decoding the map in one go made the
        // whole chunk a decode failure and the screen showed no prices at all.
        val noPrice = """{"createdAt":"2026-06-25T08:28:11Z","decimals":8,""" +
            """"stockData":{"id":"xstocks","price":253.71,"updatedAt":"2026-09-12T14:51:27.489Z"}}"""
        val quiet = "Quiet1111111111111111111111111111111111111"
        val broken = "Broken111111111111111111111111111111111111"
        val mock = MockApi {
            respondJson(
                """{"$quiet": $noPrice, "$broken": {"usdPrice": "not a number"}, """ +
                    """"${KnownMints.TSLAX}": {"usdPrice": 363.4, "decimals": 8}}""",
            )
        }
        val fetch = api(mock).prices(listOf(quiet, broken, KnownMints.TSLAX))

        assertEquals(setOf(KnownMints.TSLAX), fetch.priced.keys)
        assertEquals(363.4, fetch.priced.getValue(KnownMints.TSLAX).usdPrice, 1e-9)
        // Jupiter answered about all three, so none of them is unfetched and nothing failed.
        assertTrue(fetch.unfetched.isEmpty())
        assertFalse(fetch.isPartial)
        assertNull(fetch.failure)
    }

    @Test
    fun `more than 50 mints are fetched in chunks of 50`() = runTest {
        val mock = MockApi { request -> respondJson(priceAll(request.ids())) }
        val wanted = mints(120)
        val fetch = api(mock).prices(wanted)

        assertEquals(120, fetch.priced.size)
        assertEquals(3, mock.requests.size)
        assertEquals(listOf(50, 50, 20), mock.requests.map { it.ids().size })
        assertEquals(wanted, fetch.priced.keys.toList())
    }

    @Test
    fun `chunks are paced at the keyless budget and the first call is not delayed`() = runTest {
        val mock = MockApi { request -> respondJson(priceAll(request.ids())) }

        api(mock).prices(mints(50))
        assertEquals(1, mock.requests.size)
        assertEquals("the first call goes out at once", emptyList<Long>(), waits)

        waits.clear()
        api(mock).prices(mints(250))
        assertEquals(6, mock.requests.size)
        assertEquals(listOf(2_000L, 2_000L, 2_000L, 2_000L), waits)
    }

    @Test
    fun `duplicates and blanks are removed before asking`() = runTest {
        val mock = MockApi { respondJson("""{"${KnownMints.USDC}": {"usdPrice": 1.0}}""") }
        api(mock).prices(listOf(KnownMints.USDC, " ", KnownMints.USDC, ""))
        assertEquals(KnownMints.USDC, mock.lastRequest.url.parameters["ids"])
        assertEquals(1, mock.requests.size)
    }

    @Test
    fun `an empty list makes no request`() = runTest {
        val mock = MockApi { respondJson("{}") }
        assertEquals(PriceFetch.EMPTY, api(mock).prices(emptyList()))
        assertEquals(0, mock.requests.size)
    }

    @Test
    fun `a chunk refused once is retried after a longer wait and every other chunk survives`() = runTest {
        var refusals = 0
        val mock = MockApi { request ->
            val ids = request.ids()
            if (ids.first() == "Mint101" && refusals++ == 0) respondJson(gateway429, HttpStatusCode.TooManyRequests)
            else respondJson(priceAll(ids))
        }
        val fetch = api(mock).prices(mints(250))

        assertEquals("five chunks plus one retry", 6, mock.requests.size)
        assertEquals(listOf(2_000L, 2_000L, 4_000L, 2_000L, 2_000L), waits)
        assertEquals(250, fetch.priced.size)
        assertTrue(fetch.unfetched.isEmpty())
        assertFalse(fetch.isPartial)
        assertNull(fetch.failure)
    }

    @Test
    fun `a chunk refused twice is reported unfetched and the other chunks are returned`() = runTest {
        val mock = MockApi { request ->
            val ids = request.ids()
            if (ids.first() == "Mint101") respondJson(gateway429, HttpStatusCode.TooManyRequests)
            else respondJson(priceAll(ids))
        }
        val fetch = api(mock).prices(mints(250))

        assertEquals("five chunks plus one retry, never a second retry", 6, mock.requests.size)
        assertEquals(listOf(2_000L, 2_000L, 4_000L, 2_000L, 2_000L), waits)
        assertEquals(200, fetch.priced.size)
        assertEquals(mints(150).drop(100).toSet(), fetch.unfetched)
        assertTrue(fetch.isPartial)
        assertTrue(fetch.wasRateLimited)
        assertTrue(fetch.failure is RateLimitedException)
        // The refused mints are unknown rather than unpriced: absent from priced AND named.
        assertFalse("Mint101" in fetch.priced)
        assertTrue("Mint101" in fetch.unfetched)
        assertTrue("Mint151" in fetch.priced)
    }

    @Test
    fun `429 is typed, retried once, and honours a longer Retry-After`() = runTest {
        val mock = MockApi {
            respondJson(gateway429, HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "6")
        }
        val fetch = api(mock).prices(listOf(KnownMints.USDC))

        assertEquals(2, mock.requests.size)
        assertEquals(listOf(6_000L), waits)
        assertTrue(fetch.priced.isEmpty())
        assertEquals(setOf(KnownMints.USDC), fetch.unfetched)
        val failure = fetch.failure as RateLimitedException
        assertEquals(6L, failure.retryAfterSeconds)
        assertFalse(failure.body.isNullOrBlank())
    }

    @Test
    fun `a non-429 failure is reported, not retried and not read as an unpriced mint`() = runTest {
        val mock = MockApi { request ->
            val ids = request.ids()
            if (ids.first() == "Mint051") respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError)
            else respondJson(priceAll(ids))
        }
        val fetch = api(mock).prices(mints(100))

        assertEquals("two chunks, no retry on a non-429", 2, mock.requests.size)
        assertEquals(listOf(2_000L), waits)
        assertEquals(50, fetch.priced.size)
        assertEquals(mints(100).drop(50).toSet(), fetch.unfetched)
        assertFalse(fetch.wasRateLimited)
        val failure = fetch.failure as ApiException
        assertEquals(500, failure.status)
        assertEquals("internal", failure.errorCode)
    }

    @Test
    fun `a fetch that fails outright prices nothing and keeps the reason`() = runTest {
        val mock = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.BadGateway) }
        val wanted = listOf(KnownMints.USDC, KnownMints.TSLAX, KnownMints.SKR)
        val fetch = api(mock).prices(wanted)

        assertEquals(1, mock.requests.size)
        assertTrue(fetch.priced.isEmpty())
        assertEquals(wanted.toSet(), fetch.unfetched)
        assertEquals(502, (fetch.failure as ApiException).status)
    }
}
