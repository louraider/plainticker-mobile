package com.plainticker.mobile.data.jupiter

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JupiterSwapApiTest {

    private val placeholderTaker = "11111111111111111111111111111111"

    private fun api(mock: MockApi) = JupiterSwapApi(mock.client)

    // ---- GOLDEN: the real 2026-09-10 /order shape ------------------------------------------

    @Test
    fun `golden - the recorded 5 USDC to TSLAx Metis order parses with the spike's semantics`() = runTest {
        val text = Fixtures.read("jupiter/order-usdc-tslax-5.json")
        // Fixture integrity: a Metis order has no expireAt at all, and nothing identifying.
        val topLevelKeys = (HttpClientFactory.json.parseToJsonElement(text) as JsonObject).keys
        assertFalse("golden must not carry expireAt", "expireAt" in topLevelKeys)
        assertTrue(text.contains("\"taker\": \"$placeholderTaker\""))
        assertTrue(text.contains("\"transaction\": \"UkVEQUNURUQ=\""))

        val mock = MockApi { respondJson(text) }
        val order = api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L, placeholderTaker)

        assertEquals("5000000", order.inAmount)
        assertEquals(5_000_000L, order.inAmountRaw)
        assertEquals("1366141", order.outAmount)
        assertEquals(1_366_141L, order.outAmountRaw)
        assertEquals("metis", order.router)
        assertEquals("aggregator", order.swapType)
        assertFalse(order.gasless)

        assertNull(order.expireAt)
        assertFalse(order.hasExpiry)
        assertNull(order.secondsLeft(System.currentTimeMillis() / 1000))

        // The spike's formula, applied to the fixture's own USD values.
        val expected = (4.999342 - 4.99462) / 4.999342 * 100.0
        assertEquals(expected, order.allInCostPct, 1e-9)
        assertTrue(order.allInCostPct > 0.0 && order.allInCostPct < 1.0)

        assertEquals(KnownMints.USDC, order.inputMint)
        assertEquals(KnownMints.TSLAX, order.outputMint)
        assertEquals("ExactIn", order.swapMode)
        assertEquals(100, order.slippageBps)
        assertEquals("1352479", order.otherAmountThreshold)
        assertEquals(11, order.feeBps)
        assertEquals(10, order.platformFeeBps)
        assertEquals(KnownMints.USDC, order.platformFee?.feeMint)
        assertEquals(-0.00046103, order.priceImpactPct!!, 1e-12)
        assertEquals("ultra", order.mode)
        assertFalse(order.guaranteedPrice)
        assertEquals(placeholderTaker, order.taker)
        assertEquals("UkVEQUNURUQ=", order.transaction)
        assertTrue(order.isSignable)
        assertEquals(423986571L, order.lastValidBlockHeight)
        assertEquals(5000L, order.signatureFeeLamports)
        assertEquals(placeholderTaker, order.signatureFeePayer)
        assertNull(order.rentFeePayer)
        assertEquals(1, order.routePlan.size)
        assertEquals("Whirlpool", order.routePlan[0].swapInfo?.label)
        assertEquals(100, order.routePlan[0].percent)
    }

    @Test
    fun `order sends the four query parameters and omits taker for a quote`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("jupiter/order-usdc-tslax-5.json")) }
        api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L, placeholderTaker)
        with(mock.lastRequest) {
            assertEquals(HttpMethod.Get, method)
            assertEquals("/swap/v2/order", url.encodedPath)
            assertEquals(KnownMints.USDC, url.parameters["inputMint"])
            assertEquals(KnownMints.TSLAX, url.parameters["outputMint"])
            assertEquals("5000000", url.parameters["amount"])
            assertEquals(placeholderTaker, url.parameters["taker"])
        }

        api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L)
        assertNull(mock.lastRequest.url.parameters["taker"])
    }

    @Test
    fun `a quote-only order has a null transaction and is not signable`() = runTest {
        val mock = MockApi {
            respondJson("""{"requestId":"q","swapType":"aggregator","router":"metis","inAmount":"5000000","outAmount":"1","transaction":null,"taker":null}""")
        }
        val order = api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L)
        assertNull(order.transaction)
        assertFalse(order.isSignable)
        assertEquals(0.0, order.allInCostPct, 0.0)
    }

    @Test
    fun `an RFQ order carries an expiry as string or number`() = runTest {
        val asString = """{"requestId":"r","swapType":"rfq","router":"jupiterz","inAmount":"5000000","outAmount":"1366141","expireAt":"1757534400","inUsdValue":5.0,"outUsdValue":4.99}"""
        val asNumber = asString.replace("\"1757534400\"", "1757534400")
        for (body in listOf(asString, asNumber)) {
            val mock = MockApi { respondJson(body) }
            val order = api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L)
            assertTrue(order.hasExpiry)
            assertEquals(1757534400L, order.expireAt)
            assertEquals(10L, order.secondsLeft(1757534390L))
            assertEquals(-5L, order.secondsLeft(1757534405L))
            assertEquals(0.2, order.allInCostPct, 1e-9)
        }
    }

    // ---- GOLDEN: the 2026-09-12 /order that corrects task T10 ------------------------------

    @Test
    fun `golden - the 2026-09-12 order names three SOL costs, and the rent is not the plan's constant`() = runTest {
        val text = Fixtures.read("jupiter/order-usdc-tslax-5-rent.json")
        val topLevelKeys = (HttpClientFactory.json.parseToJsonElement(text) as JsonObject).keys
        assertFalse("a Metis order carries no expireAt", "expireAt" in topLevelKeys)
        assertTrue(text.contains("\"taker\": \"$placeholderTaker\""))
        assertTrue(text.contains("\"transaction\": \"UkVEQUNURUQ=\""))

        val mock = MockApi { respondJson(text) }
        val order = api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L, placeholderTaker)

        // Every cost is its own field with its own payer, so nothing is inferred and nothing is
        // a constant. The SOL the wallet needs is these three added up.
        assertEquals(5_000L, order.signatureFeeLamports)
        assertEquals(1_488_440L, order.rentFeeLamports)
        assertEquals(1_450L, order.prioritizationFeeLamports)
        assertEquals(1_494_890L, order.signatureFeeLamports + order.rentFeeLamports + order.prioritizationFeeLamports)
        assertEquals(placeholderTaker, order.signatureFeePayer)
        assertEquals(placeholderTaker, order.rentFeePayer)
        assertEquals(placeholderTaker, order.prioritizationFeePayer)

        // Task T10 says to add the 165-byte classic token account rent as a constant. This quote
        // charges a Token-2022 account instead, and it knows whether the account already exists.
        assertTrue("the plan's constant is not what this route charges", order.rentFeeLamports != 2_039_280L)

        // The worst case can be stated beside the estimate, from the order's own threshold.
        assertEquals(1_360_437L, order.outAmountRaw)
        assertEquals("1346933", order.otherAmountThreshold)
        assertEquals(100, order.slippageBps)
        assertEquals(10, order.feeBps)
        assertEquals(10, order.platformFeeBps)

        // Liquid names route this way and the taker pays, so no SOL is never the pitch.
        assertEquals("metis", order.router)
        assertEquals("aggregator", order.swapType)
        assertEquals("ultra", order.mode)
        assertFalse(order.gasless)

        assertNull(order.expireAt)
        assertFalse(order.hasExpiry)
        assertNull("no expiry is never an expired quote", order.secondsLeft(System.currentTimeMillis() / 1000))

        assertEquals(0.586, order.allInCostPct, 1e-9)
    }

    @Test
    fun `the three codes task T10 names are the requotable ones`() {
        val requotable = listOf(
            SwapError.CODE_NOT_FULLY_SIGNED,
            SwapError.CODE_QUOTE_EXPIRED,
            SwapError.CODE_REJECTED_BY_MAKER,
        )
        requotable.forEach { code ->
            assertTrue("$code should requote", SwapError.fromCode(code, null, SwapError.Stage.EXECUTE).requotable)
        }
        // needsFreshOrder stays the narrower fact: the quote itself went.
        assertFalse(SwapError.fromCode(SwapError.CODE_NOT_FULLY_SIGNED, null, SwapError.Stage.EXECUTE).needsFreshOrder)
        listOf(-2, -4, null).forEach { code ->
            assertFalse("$code should not requote", SwapError.fromCode(code, null, SwapError.Stage.EXECUTE).requotable)
        }
        assertFalse(SwapError.Http(SwapError.Stage.EXECUTE, 502, null).requotable)
    }

    // ---- error mapping ----------------------------------------------------------------------

    @Test
    fun `order 400 with an error body is OrderRejected`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("jupiter/order-error-400.json"), HttpStatusCode.BadRequest) }
        val e = expectThrows<SwapError.OrderRejected> { api(mock).order(KnownMints.USDC, "NOTAMINT", 5_000_000L) }
        assertEquals("Invalid outputMint", e.detail)
        assertNull(e.code)
        assertEquals(SwapError.Stage.ORDER, e.stage)
        assertFalse(e.needsFreshOrder)
    }

    @Test
    fun `order 200 that still carries an error field is OrderRejected`() = runTest {
        val mock = MockApi { respondJson("""{"error":"No route found","errorCode":-4}""") }
        val e = expectThrows<SwapError.OrderRejected> { api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 1L) }
        assertEquals("No route found", e.detail)
        assertEquals(-4, e.code)
    }

    @Test
    fun `order behind a gateway page is SwapError_Http with the status`() = runTest {
        val mock = MockApi { respondHtml("<html>502</html>", HttpStatusCode.BadGateway) }
        val e = expectThrows<SwapError.Http> { api(mock).order(KnownMints.USDC, KnownMints.TSLAX, 5_000_000L) }
        assertEquals(502, e.status)
        assertEquals(SwapError.Stage.ORDER, e.stage)
        assertTrue(e.detail!!.contains("502"))
    }

    @Test
    fun `execute posts JSON with signedTransaction and requestId and parses a landed answer`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("jupiter/execute-success.json")) }
        val result = api(mock).execute("UkVEQUNURUQ=", "01a08b00-0000-7000-8000-00000000cafe")

        with(mock.lastRequest) {
            assertEquals(HttpMethod.Post, method)
            assertEquals("/swap/v2/execute", url.encodedPath)
            assertTrue(body.contentType!!.match(ContentType.Application.Json))
            val sent = HttpClientFactory.json.parseToJsonElement(bodyText()).jsonObject
            assertEquals("UkVEQUNURUQ=", sent.getValue("signedTransaction").jsonPrimitive.content)
            assertEquals("01a08b00-0000-7000-8000-00000000cafe", sent.getValue("requestId").jsonPrimitive.content)
            assertEquals(setOf("signedTransaction", "requestId"), sent.keys)
        }

        assertTrue(result.isSuccess)
        assertEquals("Success", result.status)
        assertEquals("1".repeat(88), result.signature)
        assertEquals(0, result.code)
        assertNull(result.error)
        assertEquals("367000000", result.slot)
        assertEquals("1366141", result.outputAmountResult)
        assertNull(result.errorOrNull())
    }

    @Test
    fun `execute 400 with code -2 is ExecuteFailed carrying the code`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("jupiter/execute-error-400.json"), HttpStatusCode.BadRequest) }
        val e = expectThrows<SwapError.ExecuteFailed> { api(mock).execute("AA==", "r") }
        assertEquals(-2, e.code)
        assertEquals("Failed to decode signed transaction", e.detail)
        assertEquals(SwapError.Stage.EXECUTE, e.stage)
        assertFalse(e.needsFreshOrder)
    }

    @Test
    fun `execute failures with the three special codes map to their own types`() = runTest {
        suspend fun failedWith(code: Int, error: String): ExecuteResult {
            val mock = MockApi { respondJson("""{"status":"Failed","code":$code,"error":"$error","signature":null}""") }
            return api(mock).execute("AA==", "r")
        }

        val expired = failedWith(-2003, "Quote expired")
        assertFalse(expired.isSuccess)
        val expiredError = expired.errorOrNull()
        assertTrue(expiredError is SwapError.QuoteExpired)
        assertTrue(expiredError!!.needsFreshOrder)
        assertEquals(-2003, expiredError.code)
        assertEquals("Quote expired", expiredError.detail)

        val rejected = failedWith(-2004, "Swap rejected by market maker").errorOrNull()
        assertTrue(rejected is SwapError.RejectedByMaker)
        assertTrue(rejected!!.needsFreshOrder)

        val unsigned = failedWith(-1003, "Transaction is not fully signed").errorOrNull()
        assertTrue(unsigned is SwapError.NotFullySigned)
        assertFalse(unsigned!!.needsFreshOrder)

        val other = failedWith(-1000, "Failed to land").errorOrNull()
        assertTrue(other is SwapError.ExecuteFailed)
        assertEquals(-1000, other!!.code)
        assertFalse(other.needsFreshOrder)
    }

    @Test
    fun `a non-2xx execute with a special code throws that type`() = runTest {
        val mock = MockApi { respondJson("""{"status":"Failed","code":-2003,"error":"Quote expired"}""", HttpStatusCode.BadRequest) }
        val e = expectThrows<SwapError.QuoteExpired> { api(mock).execute("AA==", "r") }
        assertTrue(e.needsFreshOrder)
    }

    @Test
    fun `fromErrorBody falls back to Http when the body has no structure`() {
        val json = HttpClientFactory.json
        val empty = SwapError.fromErrorBody(503, "", SwapError.Stage.EXECUTE, json)
        assertTrue(empty is SwapError.Http)
        assertNull(empty.detail)

        val unrelated = SwapError.fromErrorBody(500, """{"something":"else"}""", SwapError.Stage.ORDER, json)
        assertTrue(unrelated is SwapError.Http)
        assertEquals(500, (unrelated as SwapError.Http).status)
    }

    @Test
    fun `the golden fixture's unknown top-level note key is really ignored`() {
        val element = HttpClientFactory.json.parseToJsonElement(Fixtures.read("jupiter/order-usdc-tslax-5.json")) as JsonObject
        assertTrue(element["_fixture_note"] is JsonPrimitive)
        val order = HttpClientFactory.json.decodeFromJsonElement(SwapOrder.serializer(), element)
        assertEquals("01a08b00-0000-7000-8000-00000000cafe", order.requestId)
    }
}
