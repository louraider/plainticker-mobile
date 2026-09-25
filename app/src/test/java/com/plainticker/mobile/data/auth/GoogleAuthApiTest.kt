package com.plainticker.mobile.data.auth

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
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
 * `POST /api/v1/auth/google` against the web repo's server/auth/README.md, section 1: the request
 * shape (the token in the JSON body only, the device code in `X-PT-Code` only), the 200 shape, and
 * every error code in the README's table.
 */
class GoogleAuthApiTest {

    private val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.SIGNATURE-must-never-leak"

    private val ok = """
        {"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"],
         "pro":true,"source":"pass","until":"2026-10-20T00:00:00.000Z"}
    """.trimIndent()

    @Test
    fun `a 200 parses the user, the linked wallets and the pro fields`() = runTest {
        val mock = MockApi { respondJson(ok) }
        val answer = GoogleAuthApi(mock.client).signIn(token, "ABCDE12345")

        assertEquals("ann@example.com", answer.user.email)
        assertEquals("Ann", answer.user.name)
        assertEquals(listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"), answer.linkedWallets)
        assertTrue(answer.pro)
        assertEquals("pass", answer.source)
        assertEquals("2026-10-20T00:00:00.000Z", answer.until)
    }

    @Test
    fun `null email and name, and no wallets, are a valid answer`() = runTest {
        val mock = MockApi { respondJson("""{"user":{"email":null,"name":null},"linkedWallets":[],"pro":false,"source":null,"until":null}""") }
        val answer = GoogleAuthApi(mock.client).signIn(token, null)

        assertNull(answer.user.email)
        assertNull(answer.user.name)
        assertTrue(answer.linkedWallets.isEmpty())
        assertFalse(answer.pro)
    }

    @Test
    fun `it posts JSON to the auth google route, with the token in the body and nowhere in the URL`() = runTest {
        val mock = MockApi { respondJson(ok) }
        GoogleAuthApi(mock.client).signIn(token, "ABCDE12345")

        val request = mock.lastRequest
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://www.plainticker.com/api/v1/auth/google", request.url.toString())
        assertFalse("the token must never ride in the URL", token in request.url.toString())
        assertTrue(request.body.contentType?.match(ContentType.Application.Json) == true)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals("the body carries idToken and nothing else", setOf("idToken"), body.keys)
        assertEquals(token, body.getValue("idToken").jsonPrimitive.content)
    }

    @Test
    fun `the device code rides in the X-PT-Code header in the clear, and never in the body`() = runTest {
        val mock = MockApi { respondJson(ok) }
        GoogleAuthApi(mock.client).signIn(token, "K7M9QRSTXYK7M9QRSTXYK7M9QR")

        val request = mock.lastRequest
        assertEquals("K7M9QRSTXYK7M9QRSTXYK7M9QR", request.headers["X-PT-Code"])
        assertEquals("X-PT-Code", GoogleAuthApi.HEADER_CODE)
        assertFalse("the device code never enters the body", "K7M9QRSTXY" in request.bodyText())
        assertFalse("the device code never enters the URL", "K7M9QRSTXY" in request.url.toString())
    }

    @Test
    fun `no device code sends no header at all`() = runTest {
        val mock = MockApi { respondJson(ok) }
        GoogleAuthApi(mock.client).signIn(token, null)
        GoogleAuthApi(mock.client).signIn(token, "  ")

        mock.requests.forEach { assertNull(it.headers["X-PT-Code"]) }
    }

    /** The README's error table, row by row: status, `error` code, and the constant it maps to. */
    private val readmeErrors = listOf(
        Triple(400, "bad_request", GoogleAuthFailure.BAD_REQUEST),
        Triple(400, "bad_device_code", GoogleAuthFailure.BAD_DEVICE_CODE),
        Triple(401, "invalid_token", GoogleAuthFailure.INVALID_TOKEN),
        Triple(401, "expired_token", GoogleAuthFailure.EXPIRED_TOKEN),
        Triple(401, "wrong_audience", GoogleAuthFailure.WRONG_AUDIENCE),
        Triple(403, "email_not_verified", GoogleAuthFailure.EMAIL_NOT_VERIFIED),
        Triple(429, "rate_limited", GoogleAuthFailure.RATE_LIMITED),
        Triple(500, "internal", GoogleAuthFailure.INTERNAL),
        Triple(503, "auth_disabled", GoogleAuthFailure.AUTH_DISABLED),
        Triple(503, "not_configured", GoogleAuthFailure.NOT_CONFIGURED),
        Triple(503, "jwks_unavailable", GoogleAuthFailure.JWKS_UNAVAILABLE),
    )

    @Test
    fun `every error code in the README maps to its own failure`() = runTest {
        readmeErrors.forEach { (status, code, expected) ->
            val mock = MockApi { respondJson("""{"error":"$code","code":$status}""", HttpStatusCode.fromValue(status)) }
            val error = expectThrows<GoogleAuthError> { GoogleAuthApi(mock.client).signIn(token, "ABCDE12345") }
            assertEquals(code, expected, error.failure)
            assertEquals(code, status, error.status)
        }
        assertEquals(
            "every wire code has a row above",
            GoogleAuthFailure.entries.mapNotNull { it.wire }.toSet(),
            readmeErrors.map { it.second }.toSet(),
        )
    }

    @Test
    fun `a 404 means the route is not deployed yet, and an unknown answer is unknown`() = runTest {
        val missing = MockApi { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }
        assertEquals(GoogleAuthFailure.NOT_OPEN, expectThrows<GoogleAuthError> { GoogleAuthApi(missing.client).signIn(token, null) }.failure)

        val teapot = MockApi { respondJson("""{"error":"something_new"}""", HttpStatusCode.fromValue(418)) }
        assertEquals(GoogleAuthFailure.UNKNOWN, expectThrows<GoogleAuthError> { GoogleAuthApi(teapot.client).signIn(token, null) }.failure)

        val gateway = MockApi { respondJson("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        assertEquals(GoogleAuthFailure.UNKNOWN, expectThrows<GoogleAuthError> { GoogleAuthApi(gateway.client).signIn(token, null) }.failure)

        val limitedNoBody = MockApi { respondJson("", HttpStatusCode.TooManyRequests) }
        assertEquals(
            GoogleAuthFailure.RATE_LIMITED,
            expectThrows<GoogleAuthError> { GoogleAuthApi(limitedNoBody.client).signIn(token, null) }.failure,
        )
    }

    @Test
    fun `a 200 that is not the contract is an unknown failure, not a crash`() = runTest {
        val mock = MockApi { respondJson("not json") }
        val error = expectThrows<GoogleAuthError> { GoogleAuthApi(mock.client).signIn(token, null) }
        assertEquals(GoogleAuthFailure.UNKNOWN, error.failure)
    }

    @Test
    fun `a network failure surfaces as an IOException`() = runTest {
        val mock = MockApi { throw IOException("no route to host") }
        expectThrows<IOException> { GoogleAuthApi(mock.client).signIn(token, null) }
    }

    @Test
    fun `an error's message carries the code and status, never the token`() = runTest {
        readmeErrors.forEach { (status, code, _) ->
            val mock = MockApi { respondJson("""{"error":"$code","code":$status,"echo":"$token"}""", HttpStatusCode.fromValue(status)) }
            val error = expectThrows<GoogleAuthError> { GoogleAuthApi(mock.client).signIn(token, "ABCDE12345") }
            assertFalse(code, token in error.message.orEmpty())
            assertFalse(code, "ABCDE12345" in error.message.orEmpty())
            assertFalse(code, token in error.toString())
        }
    }

    @Test
    fun `the request model never prints its token`() {
        assertFalse(token in GoogleAuthRequest(token).toString())
    }
}
