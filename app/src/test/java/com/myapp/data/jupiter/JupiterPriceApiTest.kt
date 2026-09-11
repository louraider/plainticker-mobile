package com.myapp.data.jupiter

import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.MockApi
import com.myapp.data.expectThrows
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.net.RateLimitedException
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

class JupiterPriceApiTest {

    private fun api(mock: MockApi) = JupiterPriceApi(mock.client)

    /**
     * jupiter/price-v3.json is the live 2026-09-10 answer for USDC, TSLAx and SKR, with
     * USDC's stockData set to an explicit null and SKR's removed, so both "not an equity"
     * encodings are covered. TSLAx is verbatim.
     */
    @Test
    fun `prices parses the recorded v3 map, stockData null and absent alike`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("jupiter/price-v3.json")) }
        val prices = api(mock).prices(listOf(KnownMints.USDC, KnownMints.TSLAX, KnownMints.SKR))

        assertEquals("https://api.jup.ag/price/v3", mock.lastRequest.url.toString().substringBefore("?"))
        assertEquals("${KnownMints.USDC},${KnownMints.TSLAX},${KnownMints.SKR}", mock.lastRequest.url.parameters["ids"])
        assertEquals(listOf(HttpClientFactory.USER_AGENT), mock.lastRequest.headers.getAll(HttpHeaders.UserAgent))

        assertEquals(3, prices.size)

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
    fun `a mint Jupiter cannot price is dropped, not an error`() = runTest {
        val mock = MockApi {
            respondJson("""{"Unknown111111111111111111111111111111111111": null, "${KnownMints.USDC}": {"usdPrice": 1.0, "decimals": 6}}""")
        }
        val prices = api(mock).prices(listOf("Unknown111111111111111111111111111111111111", KnownMints.USDC))
        assertEquals(setOf(KnownMints.USDC), prices.keys)
        assertEquals(1.0, prices.getValue(KnownMints.USDC).usdPrice, 0.0)
    }

    @Test
    fun `more than 50 mints are fetched in chunks of 50`() = runTest {
        val mock = MockApi { request ->
            val ids = request.url.parameters["ids"]!!.split(",")
            respondJson(ids.joinToString(",", "{", "}") { "\"$it\": {\"usdPrice\": 1.5}" })
        }
        val mints = (1..120).map { "Mint%03d".format(it) }
        val prices = api(mock).prices(mints)

        assertEquals(120, prices.size)
        assertEquals(3, mock.requests.size)
        assertEquals(listOf(50, 50, 20), mock.requests.map { it.url.parameters["ids"]!!.split(",").size })
        assertEquals(mints, prices.keys.toList())
    }

    @Test
    fun `duplicates and blanks are removed before asking`() = runTest {
        val mock = MockApi { respondJson("""{"${KnownMints.USDC}": {"usdPrice": 1.0}}""") }
        api(mock).prices(listOf(KnownMints.USDC, " ", KnownMints.USDC, ""))
        assertEquals(KnownMints.USDC, mock.lastRequest.url.parameters["ids"])
    }

    @Test
    fun `an empty list makes no request`() = runTest {
        val mock = MockApi { respondJson("{}") }
        assertTrue(api(mock).prices(emptyList()).isEmpty())
        assertEquals(0, mock.requests.size)
    }

    @Test
    fun `429 from the keyless bucket is typed`() = runTest {
        val mock = MockApi { respondJson("""{"error":"Rate limit exceeded"}""", HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "2") }
        val e = expectThrows<RateLimitedException> { api(mock).prices(listOf(KnownMints.USDC)) }
        assertEquals(2L, e.retryAfterSeconds)
        assertFalse(e.body.isNullOrBlank())
    }
}
