package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `POST /api/v1/promo/redeem` against the contract the web agent is building in parallel: the
 * request shape (the promo code in the JSON body, the device code in `X-PT-Code` only), the 200
 * shape (the same one [EntitlementResponse] carries), and every `code` in the errors table.
 */
class PromoApiTest {

    private val ok = """{"pro":true,"source":"promo","until":"2026-10-26T00:00:00.000Z"}"""

    @Test
    fun `a 200 parses pro, its source and when it ends`() = runTest {
        val mock = MockApi { respondJson(ok) }
        val answer = PromoApi(mock.client).redeem("PTAAAABBBBCCCC", "ABCDE12345")

        assertTrue(answer.pro)
        assertEquals("promo", answer.source)
        assertEquals(1_792_972_800_000L, answer.untilEpochMillis())
    }

    @Test
    fun `it posts JSON to promo redeem, the code in the body and nowhere in the URL`() = runTest {
        val mock = MockApi { respondJson(ok) }
        PromoApi(mock.client).redeem("PTAAAABBBBCCCC", "ABCDE12345")

        val request = mock.lastRequest
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://www.plainticker.com/api/v1/promo/redeem", request.url.toString())
        assertTrue(request.body.contentType?.match(ContentType.Application.Json) == true)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals("the body carries code and nothing else", setOf("code"), body.keys)
        assertEquals("PTAAAABBBBCCCC", body.getValue("code").jsonPrimitive.content)
        assertFalse("the promo code is not a secret, but it is still not a URL parameter", "PTAAAABBBBCCCC" in request.url.toString())
    }

    // ---- The device code: header only, never body, URL or log ---------------------------------

    @Test
    fun `the device code rides in the X-PT-Code header in the clear, and never in the body`() = runTest {
        val mock = MockApi { respondJson(ok) }
        PromoApi(mock.client).redeem("PTAAAABBBBCCCC", "K7M9QRSTXYK7M9QRSTXYK7M9QR")

        val request = mock.lastRequest
        assertEquals("K7M9QRSTXYK7M9QRSTXYK7M9QR", request.headers[PromoApi.HEADER_CODE])
        assertEquals("X-PT-Code", PromoApi.HEADER_CODE)
        assertFalse("the device code never enters the body", "K7M9QRSTXY" in request.bodyText())
        assertFalse("the device code never enters the URL", "K7M9QRSTXY" in request.url.toString())
    }

    @Test
    fun `no device code sends no header at all`() = runTest {
        val mock = MockApi { respondJson(ok) }
        PromoApi(mock.client).redeem("PTAAAABBBBCCCC", null)
        PromoApi(mock.client).redeem("PTAAAABBBBCCCC", "  ")

        mock.requests.forEach { assertNull(it.headers[PromoApi.HEADER_CODE]) }
    }

    @Test
    fun `an error's message never carries the device code`() = runTest {
        val mock = MockApi { respondJson("""{"error":"invalid_code","code":"invalid_code"}""", HttpStatusCode.BadRequest) }
        val error = expectThrows<PromoError.InvalidCode> {
            PromoApi(mock.client).redeem("PTAAAABBBBCCCC", "K7M9QRSTXYK7M9QRSTXYK7M9QR")
        }
        assertFalse("K7M9QRSTXY" in error.message.orEmpty())
        assertFalse("K7M9QRSTXY" in error.toString())
    }

    // ---- Every response the contract names -----------------------------------------------------

    @Test
    fun `every error code in the contract maps to its own state`() = runTest {
        val table = listOf(
            Triple(400, "bad_request", PromoError.BadRequest::class),
            Triple(400, "invalid_code", PromoError.InvalidCode::class),
            Triple(410, "expired_code", PromoError.ExpiredCode::class),
            Triple(409, "already_redeemed", PromoError.AlreadyRedeemed::class),
            Triple(409, "already_applied", PromoError.AlreadyApplied::class),
            Triple(429, "rate_limited", PromoError.RateLimited::class),
        )
        table.forEach { (status, code, expected) ->
            val mock = MockApi { respondJson("""{"error":"That code","code":"$code"}""", HttpStatusCode.fromValue(status)) }
            val error = expectThrows<PromoError> { PromoApi(mock.client).redeem("PTAAAABBBBCCCC", "ABCDE12345") }
            assertEquals(code, expected, error::class)
            assertEquals(code, status, error.status)
            assertEquals(code, code, error.code)
        }
    }

    @Test
    fun `a 404 means not open yet, the existing convention, and a 5xx is unavailable`() = runTest {
        val notDeployed = MockApi { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }
        expectThrows<PromoError.NotOpen> { PromoApi(notDeployed.client).redeem("PTAAAABBBBCCCC", null) }

        val down = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
        expectThrows<PromoError.Unavailable> { PromoApi(down.client).redeem("PTAAAABBBBCCCC", null) }

        val gateway = MockApi { respondHtml("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        expectThrows<PromoError.Unavailable> { PromoApi(gateway.client).redeem("PTAAAABBBBCCCC", null) }
    }

    @Test
    fun `a 200 that is not the contract is unavailable, not a crash`() = runTest {
        val mock = MockApi { respondJson("not json") }
        expectThrows<PromoError.Unavailable> { PromoApi(mock.client).redeem("PTAAAABBBBCCCC", null) }
    }

    @Test
    fun `a network failure surfaces as an IOException`() = runTest {
        val mock = MockApi { throw IOException("no route to host") }
        expectThrows<IOException> { PromoApi(mock.client).redeem("PTAAAABBBBCCCC", null) }
    }

    @Test
    fun `a 400 without the invalid_code marker reads as a bad request`() = runTest {
        val mock = MockApi { respondJson("""{"error":"missing code"}""", HttpStatusCode.BadRequest) }
        expectThrows<PromoError.BadRequest> { PromoApi(mock.client).redeem("", "ABCDE12345") }
    }

    @Test
    fun `a 429 with no body is still rate limited`() = runTest {
        val mock = MockApi { respondJson("", HttpStatusCode.TooManyRequests) }
        expectThrows<PromoError.RateLimited> { PromoApi(mock.client).redeem("PTAAAABBBBCCCC", null) }
    }

    // ---- Normalization: uppercase, no spaces, no dashes ----------------------------------------

    @Test
    fun `normalize uppercases and strips spaces and dashes`() {
        assertEquals("PTAAAABBBBCCCC", PromoApi.normalize("PT-AAAA-BBBB-CCCC"))
        assertEquals("PTAAAABBBBCCCC", PromoApi.normalize("pt-aaaa-bbbb-cccc"))
        assertEquals("PTAAAABBBBCCCC", PromoApi.normalize("pt aaaa bbbb cccc"))
        assertEquals("PTAAAABBBBCCCC", PromoApi.normalize(" pt-aaaa bbbb-cccc "))
        assertEquals("PTAAAABBBBCCCC", PromoApi.normalize("PTAAAABBBBCCCC"))
        assertEquals("", PromoApi.normalize(""))
        assertEquals("", PromoApi.normalize("   -- -- "))
    }
}
