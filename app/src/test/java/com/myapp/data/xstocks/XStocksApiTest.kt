package com.myapp.data.xstocks

import app.cash.turbine.test
import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.MockApi
import com.myapp.data.expectThrows
import com.myapp.data.net.ApiException
import com.myapp.data.net.HttpClientFactory
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

class XStocksApiTest {

    private fun api(mock: MockApi) = XStocksApi(mock.client)

    /** Routes the recorded pages by the `page` query parameter, like the real API. */
    private fun catalogHandler() = MockApi { request: HttpRequestData ->
        when (request.url.parameters["page"]) {
            "0" -> respondJson(Fixtures.read("xstocks/assets-page-0.json"))
            "1" -> respondJson(Fixtures.read("xstocks/assets-page-1.json"))
            else -> respondJson("""{"error":"no such page"}""", HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `a catalog page parses, including a node with a field the client has never seen`() = runTest {
        val mock = catalogHandler()
        val page = api(mock).assetsPage(page = 0)

        assertEquals(2, page.nodes.size)
        assertEquals(0, page.page?.currentPage)
        assertEquals(true, page.page?.hasNextPage)

        val xrx = page.nodes[0]
        assertEquals("XRXx", xrx.symbol)
        assertEquals("XRX", xrx.underlyingTicker)
        assertEquals("XsensupeZBdHxZtdnLptf1UfWpVyancWcit7qWFYZrJ", xrx.solanaMint)
        assertNull(xrx.trading?.exchange)
        assertEquals(TradingPeriod.EXTENDED, xrx.trading?.currentPeriod)

        val tsla = page.nodes[1]
        assertEquals("TSLAx", tsla.symbol)
        assertEquals("Tesla xStock", tsla.name)
        assertEquals("TSLA", tsla.underlyingTicker)
        assertEquals("US", tsla.underlying?.listingCountry)
        assertEquals(KnownMints.TSLAX, tsla.solanaMint)
        assertEquals(2, tsla.deployments.size)
        assertTrue(tsla.solanaDeployment!!.supportsAtomicSwaps)
        assertFalse(tsla.isTradingHalted)

        val trading = tsla.trading!!
        assertEquals("USD", trading.currency)
        assertEquals("TwentyFourFive", trading.tradingHoursMode)
        assertTrue(trading.openNow)
        assertEquals("2026-09-11T00:00:00.000Z", trading.nextChangeAt)
        assertEquals("XNAS", trading.exchange?.mic)
        assertEquals("America/New_York", trading.exchange?.timezone)
        assertEquals(1000.0, trading.limitsPerPeriod?.market?.minOrderFiatValue!!, 0.0)
        assertEquals(0.0, trading.limitsPerPeriod.closed?.maxOrderFiatValue!!, 0.0)

        val usdc = tsla.solanaDeployment!!.stablecoins.first { it.symbol == "USDC" }
        assertEquals(KnownMints.USDC, usdc.address)
        assertEquals(6, usdc.decimals)
        assertEquals("TokenProgram", usdc.solanaTokenProgram)
        assertTrue(usdc.issuance && usdc.redemption)
    }

    @Test
    fun `catalog follows hasNextPage across pages with the right query`() = runTest {
        val mock = catalogHandler()
        val all = api(mock).catalog()

        assertEquals(3, all.size)
        assertEquals(listOf("XRXx", "TSLAx"), all.take(2).map { it.symbol })
        assertTrue(all.all { it.solanaMint != null })

        assertEquals(2, mock.requests.size)
        mock.requests.forEachIndexed { index, request ->
            assertEquals("/api/v2/public/assets", request.url.encodedPath)
            assertEquals("Solana", request.url.parameters["network"])
            assertEquals("100", request.url.parameters["pageSize"])
            assertEquals(index.toString(), request.url.parameters["page"])
        }
    }

    @Test
    fun `catalogPages streams each page as it lands`() = runTest {
        val mock = catalogHandler()
        api(mock).catalogPages().test {
            val first = awaitItem()
            assertEquals(0, first.page?.currentPage)
            assertEquals(2, first.nodes.size)
            val second = awaitItem()
            assertEquals(1, second.page?.currentPage)
            assertEquals(false, second.page?.hasNextPage)
            awaitComplete()
        }
    }

    @Test
    fun `xStocks requests carry the browser user agent and nothing else in that header`() = runTest {
        val mock = catalogHandler()
        api(mock).assetsPage(page = 0)
        assertEquals(listOf(HttpClientFactory.BROWSER_USER_AGENT), mock.lastRequest.headers.getAll(HttpHeaders.UserAgent))
    }

    @Test
    fun `multiplier parses for a split token and an untouched one`() = runTest {
        val mock = MockApi { request ->
            when {
                request.url.encodedPath.endsWith("/assets/NFLXx/multiplier") -> respondJson(Fixtures.read("xstocks/multiplier-nflxx.json"))
                request.url.encodedPath.endsWith("/assets/TSLAx/multiplier") -> respondJson(Fixtures.read("xstocks/multiplier-tslax.json"))
                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }
        val nflx = api(mock).multiplier("NFLXx")
        assertEquals(10.0, nflx.currentMultiplier, 0.0)
        assertFalse(nflx.hasScheduledChange)
        assertEquals("Solana", mock.lastRequest.url.parameters["network"])

        val tsla = api(mock).multiplier("TSLAx")
        assertEquals(1.0, tsla.currentMultiplier, 0.0)
        assertEquals(0.0, tsla.newMultiplier, 0.0)
        assertEquals(0L, tsla.activationDateTime)
        assertNull(tsla.reason)
    }

    @Test
    fun `proof of reserves for one symbol parses with a coverage ratio`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("xstocks/por-tslax.json")) }
        val por = checkNotNull(api(mock).proofOfReserves("TSLAx"))

        assertEquals("/api/v2/public/proof-of-reserves/TSLAx", mock.lastRequest.url.encodedPath)
        assertEquals("TSLAx", por.symbol)
        assertEquals("196869", por.sharesHeld)
        assertEquals("196340.26085951860836", por.circulatingSupply)
        assertEquals(196869.0, por.sharesHeldValue!!, 0.0)
        assertEquals(1.0026927, por.coverageRatio!!, 1e-6)
        assertEquals(1, por.holdings.size)
        assertEquals("Alpaca", por.holdings[0].provider)
        assertEquals("TSLA", por.holdings[0].symbol)
        assertNotNull(por.timestamp)
    }

    @Test
    fun `coverage ratio is null when either side is missing or supply is zero`() {
        assertNull(ProofOfReserves(symbol = "A", sharesHeld = null, circulatingSupply = "1").coverageRatio)
        assertNull(ProofOfReserves(symbol = "A", sharesHeld = "1", circulatingSupply = null).coverageRatio)
        assertNull(ProofOfReserves(symbol = "A", sharesHeld = "1", circulatingSupply = "0").coverageRatio)
        assertEquals(2.0, ProofOfReserves(symbol = "A", sharesHeld = "2", circulatingSupply = "1").coverageRatio!!, 0.0)
    }

    @Test
    fun `proof of reserves list follows pagination`() = runTest {
        val mock = MockApi { request ->
            when (request.url.parameters["page"]) {
                "0" -> respondJson(Fixtures.read("xstocks/por-page-0.json"))
                "1" -> respondJson("""{"nodes":[{"symbol":"LASTx","sharesHeld":"1","circulatingSupply":"1","holdings":[]}],"page":{"currentPage":1,"hasNextPage":false}}""")
                else -> respondJson("{}", HttpStatusCode.NotFound)
            }
        }
        val all = api(mock).proofOfReserves()
        assertEquals(listOf("XRXx", "FLNCx", "LASTx"), all.map { it.symbol })
        assertEquals(2, mock.requests.size)

        val page0 = HttpClientFactory.json.decodeFromString(ProofOfReservesPage.serializer(), Fixtures.read("xstocks/por-page-0.json"))
        assertEquals(830, page0.page?.totalNodes)
        assertEquals(9, page0.page?.totalPages)
        assertEquals(100, page0.page?.pageSize)
    }

    @Test
    fun `a validation error surfaces as ApiException 400`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"error":"Validation error","details":[{"field":"network","message":"Required"}]}""",
                HttpStatusCode.BadRequest,
            )
        }
        val e = expectThrows<ApiException> { api(mock).multiplier("TSLAx") }
        assertEquals(400, e.status)
        assertEquals("Validation error", e.errorCode)
    }

    @Test
    fun `symbols are validated before they reach a path`() = runTest {
        val mock = MockApi { respondJson("{}") }
        expectThrows<IllegalArgumentException> { api(mock).asset("../proof-of-reserves") }
        expectThrows<IllegalArgumentException> { api(mock).multiplier("TSLAx?network=Ethereum") }
        expectThrows<IllegalArgumentException> { api(mock).proofOfReserves("") }
        expectThrows<IllegalArgumentException> { api(mock).asset("TSLAx/multiplier") }
        assertEquals(0, mock.requests.size)

        api(mock).asset(" TSLAx ")
        assertEquals("/api/v2/public/assets/TSLAx", mock.lastRequest.url.encodedPath)
    }

    @Test
    fun `proof of reserves for a symbol without data is null on a 200 null body`() = runTest {
        val mock = MockApi { respondJson("null") }
        assertNull(api(mock).proofOfReserves("NOPEx"))
        assertEquals("/api/v2/public/proof-of-reserves/NOPEx", mock.lastRequest.url.encodedPath)
    }

    @Test
    fun `multiplier activation time accepts a float literal or a numeric string`() {
        val json = HttpClientFactory.json
        val scheduled = json.decodeFromString(
            Multiplier.serializer(),
            """{"currentMultiplier":1,"newMultiplier":2,"activationDateTime":1767225600.0,"reason":"Split"}""",
        )
        assertEquals(1767225600L, scheduled.activationDateTime)
        assertEquals(2.0, scheduled.newMultiplier, 0.0)
        assertTrue(scheduled.hasScheduledChange)
        assertEquals("Split", scheduled.reason)

        val idle = json.decodeFromString(
            Multiplier.serializer(),
            """{"currentMultiplier":1,"newMultiplier":0,"activationDateTime":"0","reason":null}""",
        )
        assertEquals(0L, idle.activationDateTime)
        assertFalse(idle.hasScheduledChange)
    }

    @Test
    fun `underlying ticker prefers underlying_symbol and never returns the blank deprecated field`() {
        assertEquals("TSLA", XStockAsset(symbol = "TSLAx", underlyingSymbol = "", underlying = Underlying(symbol = "TSLA")).underlyingTicker)
        assertEquals("XRX", XStockAsset(symbol = "XRXx", underlyingSymbol = "XRX", underlying = null).underlyingTicker)
        assertEquals("ABC", XStockAsset(symbol = "ABCx", underlyingSymbol = "", underlying = Underlying(symbol = "")).underlyingTicker)
        assertEquals("2888", XStockAsset(symbol = "2888x", underlyingSymbol = "OLD", underlying = Underlying(symbol = "2888")).underlyingTicker)
    }
}
