package com.plainticker.mobile.data.auth

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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GET /api/v1/account` and `POST /api/v1/account/wallets/unlink` against the contract a web
 * agent is building in parallel: the request shapes (the wallet in unlink's JSON body, the device
 * code in `X-PT-Code` only), the shared 200 shape, and every `error` code either route can answer.
 */
class AccountApiTest {

    private val wallet = "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"

    private val ok = """
        {"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["$wallet"],
         "pro":true,"source":"pass","until":"2026-10-20T00:00:00.000Z"}
    """.trimIndent()

    // ---- GET /account ---------------------------------------------------------------------

    @Test
    fun `a 200 parses the user and the linked wallets`() = runTest {
        val mock = MockApi { respondJson(ok) }
        val answer = AccountApi(mock.client).get("ABCDE12345")

        assertEquals("ann@example.com", answer.user.email)
        assertEquals("Ann", answer.user.name)
        assertEquals(listOf(wallet), answer.linkedWallets)
    }

    /**
     * Every 200 `buildAccountResponse` (web repo, lib/auth/account-response.ts) can produce, as its
     * own route tests pin it: `user.email`/`user.name` null for a wallet-first account, `source`
     * any of `stake`/`pass`/`promo`/`subscription` or null, `until` null for a stake or for no Pro,
     * and `linkedWallets` empty after the last wallet is unlinked. A non-null field or a closed
     * enum here would throw on one of these and the refresh would be swallowed silently.
     */
    @Test
    fun `every 200 shape the server builds decodes`() = runTest {
        val bodies = listOf(
            """{"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["WalletA"],"pro":true,"source":"pass","until":"2026-10-20T00:00:00.000Z"}""",
            """{"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["WalletA"],"pro":true,"source":"stake","until":null}""",
            """{"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["WalletA"],"pro":false,"source":null,"until":null}""",
            """{"user":{"email":null,"name":null},"linkedWallets":[],"pro":true,"source":"promo","until":"2026-11-01T00:00:00.000Z"}""",
            """{"user":{"email":"ann@example.com","name":null},"linkedWallets":["$wallet"],"pro":true,"source":"subscription","until":"2026-12-01T00:00:00.000Z"}""",
        )
        bodies.forEach { body ->
            val answer = AccountApi(MockApi { respondJson(body) }.client).get("K7M9QRSTXYK7M9QRSTXYK7M9QR")
            val parsed = Json.parseToJsonElement(body).jsonObject
            assertEquals(body, parsed.getValue("linkedWallets").jsonArray.map { it.jsonPrimitive.content }, answer.linkedWallets)
            assertEquals(body, parsed.getValue("source").jsonPrimitive.contentOrNull, answer.source)
            assertEquals(body, parsed.getValue("until").jsonPrimitive.contentOrNull, answer.until)
            assertEquals(body, parsed.getValue("pro").jsonPrimitive.content.toBoolean(), answer.pro)
        }
        val walletFirst = AccountApi(MockApi { respondJson(bodies[3]) }.client).get(null)
        assertNull(walletFirst.user.email)
        assertNull(walletFirst.user.name)
        assertEquals("promo", walletFirst.source)
        assertEquals(emptyList<String>(), walletFirst.linkedWallets)
    }

    @Test
    fun `a 200 that cannot be decoded names the decoder, never echoes the account body`() = runTest {
        val mock = MockApi { respondJson("""{"user":{"email":"ann@example.com"},"linkedWallets":"$wallet"}""") }
        val error = expectThrows<AccountApiError.Unavailable> { AccountApi(mock.client).get(null) }
        assertTrue(error.message.orEmpty(), "undecodable" in error.message.orEmpty())
        assertFalse("the email stays out of the message", "ann@example.com" in error.message.orEmpty())
        assertFalse("the wallet stays out of the message", wallet in error.message.orEmpty())
    }

    @Test
    fun `it gets the account route, and the device code rides only in the header`() = runTest {
        val mock = MockApi { respondJson(ok) }
        AccountApi(mock.client).get("K7M9QRSTXYK7M9QRSTXYK7M9QR")

        val request = mock.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://www.plainticker.com/api/v1/account", request.url.toString())
        assertEquals("K7M9QRSTXYK7M9QRSTXYK7M9QR", request.headers[AccountApi.HEADER_CODE])
        assertEquals("X-PT-Code", AccountApi.HEADER_CODE)
        assertFalse("the device code never enters the URL", "K7M9QRSTXY" in request.url.toString())
    }

    @Test
    fun `get sends no header at all with no device code`() = runTest {
        val mock = MockApi { respondJson(ok) }
        AccountApi(mock.client).get(null)
        AccountApi(mock.client).get("  ")

        mock.requests.forEach { assertNull(it.headers[AccountApi.HEADER_CODE]) }
    }

    @Test
    fun `every error code get can answer maps to its own state`() = runTest {
        val table = listOf(
            Triple(401, "not_signed_in", AccountApiError.NotSignedIn::class),
            Triple(400, "bad_request", AccountApiError.BadRequest::class),
            Triple(429, "rate_limited", AccountApiError.RateLimited::class),
        )
        table.forEach { (status, code, expected) ->
            val mock = MockApi { respondJson("""{"error":"$code"}""", HttpStatusCode.fromValue(status)) }
            val error = expectThrows<AccountApiError> { AccountApi(mock.client).get("ABCDE12345") }
            assertEquals(code, expected, error::class)
        }
    }

    @Test
    fun `a 404 keeps the cache by being not open, and a 5xx is unavailable`() = runTest {
        val notDeployed = MockApi { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }
        expectThrows<AccountApiError.NotOpen> { AccountApi(notDeployed.client).get(null) }

        val down = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
        expectThrows<AccountApiError.Unavailable> { AccountApi(down.client).get(null) }

        val gateway = MockApi { respondHtml("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        expectThrows<AccountApiError.Unavailable> { AccountApi(gateway.client).get(null) }
    }

    @Test
    fun `a 200 that is not the contract is unavailable, not a crash`() = runTest {
        val mock = MockApi { respondJson("not json") }
        expectThrows<AccountApiError.Unavailable> { AccountApi(mock.client).get(null) }
    }

    @Test
    fun `a network failure getting the account surfaces as an IOException`() = runTest {
        val mock = MockApi { throw IOException("no route to host") }
        expectThrows<IOException> { AccountApi(mock.client).get(null) }
    }

    // ---- POST /account/wallets/unlink ------------------------------------------------------

    @Test
    fun `a 200 answers the new linked wallets after the drop`() = runTest {
        val mock = MockApi { respondJson("""{"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":[],"pro":false}""") }
        val answer = AccountApi(mock.client).unlinkWallet(wallet, "ABCDE12345")
        assertTrue(answer.linkedWallets.isEmpty())
    }

    @Test
    fun `it posts JSON to wallets unlink, the wallet in the body and nowhere in the URL`() = runTest {
        val mock = MockApi { respondJson(ok) }
        AccountApi(mock.client).unlinkWallet(wallet, "ABCDE12345")

        val request = mock.lastRequest
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://www.plainticker.com/api/v1/account/wallets/unlink", request.url.toString())
        assertTrue(request.body.contentType?.match(ContentType.Application.Json) == true)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals("the body carries wallet and nothing else", setOf("wallet"), body.keys)
        assertEquals(wallet, body.getValue("wallet").jsonPrimitive.content)
    }

    @Test
    fun `the device code rides in the X-PT-Code header of the unlink call, and never in the body or URL`() = runTest {
        val mock = MockApi { respondJson(ok) }
        AccountApi(mock.client).unlinkWallet(wallet, "K7M9QRSTXYK7M9QRSTXYK7M9QR")

        val request = mock.lastRequest
        assertEquals("K7M9QRSTXYK7M9QRSTXYK7M9QR", request.headers[AccountApi.HEADER_CODE])
        assertFalse("the device code never enters the body", "K7M9QRSTXY" in request.bodyText())
        assertFalse("the device code never enters the URL", "K7M9QRSTXY" in request.url.toString())
    }

    @Test
    fun `unlink sends no header at all with no device code`() = runTest {
        val mock = MockApi { respondJson(ok) }
        AccountApi(mock.client).unlinkWallet(wallet, null)
        AccountApi(mock.client).unlinkWallet(wallet, "  ")

        mock.requests.forEach { assertNull(it.headers[AccountApi.HEADER_CODE]) }
    }

    @Test
    fun `every error code unlink can answer maps to its own state`() = runTest {
        val table = listOf(
            Triple(401, "not_signed_in", AccountApiError.NotSignedIn::class),
            Triple(400, "bad_request", AccountApiError.BadRequest::class),
            Triple(400, "not_linked", AccountApiError.NotLinked::class),
            Triple(409, "last_method", AccountApiError.LastMethod::class),
            Triple(429, "rate_limited", AccountApiError.RateLimited::class),
        )
        table.forEach { (status, code, expected) ->
            val mock = MockApi { respondJson("""{"error":"$code"}""", HttpStatusCode.fromValue(status)) }
            val error = expectThrows<AccountApiError> { AccountApi(mock.client).unlinkWallet(wallet, "ABCDE12345") }
            assertEquals(code, expected, error::class)
        }
    }

    @Test
    fun `a 404 on unlink is not open yet, and a 5xx is unavailable`() = runTest {
        val notDeployed = MockApi { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }
        expectThrows<AccountApiError.NotOpen> { AccountApi(notDeployed.client).unlinkWallet(wallet, null) }

        val down = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
        expectThrows<AccountApiError.Unavailable> { AccountApi(down.client).unlinkWallet(wallet, null) }
    }

    @Test
    fun `a network failure unlinking surfaces as an IOException`() = runTest {
        val mock = MockApi { throw IOException("no route to host") }
        expectThrows<IOException> { AccountApi(mock.client).unlinkWallet(wallet, null) }
    }

    @Test
    fun `an error's message never carries the device code`() = runTest {
        val mock = MockApi { respondJson("""{"error":"last_method"}""", HttpStatusCode.Conflict) }
        val error = expectThrows<AccountApiError.LastMethod> {
            AccountApi(mock.client).unlinkWallet(wallet, "K7M9QRSTXYK7M9QRSTXYK7M9QR")
        }
        assertFalse("K7M9QRSTXY" in error.message.orEmpty())
        assertFalse("K7M9QRSTXY" in error.toString())
    }

    // ---- The device-code answers and sign-out (the pack's shared server contract, 2026-09-27) ----

    @Test
    fun `rekey_required and code_retired are their own 401s, never read as not signed in`() = runTest {
        val table = listOf(
            "rekey_required" to AccountApiError.RekeyRequired::class,
            "code_retired" to AccountApiError.CodeRetired::class,
            "not_signed_in" to AccountApiError.NotSignedIn::class,
        )
        table.forEach { (code, expected) ->
            val mock = MockApi { respondJson("""{"error":"$code"}""", HttpStatusCode.Unauthorized) }
            assertEquals(code, expected, expectThrows<AccountApiError> { AccountApi(mock.client).get("ABCDE12345") }::class)
            assertEquals(code, expected, expectThrows<AccountApiError> { AccountApi(mock.client).unlinkWallet(wallet, "ABCDE12345") }::class)
            assertEquals(code, expected, expectThrows<AccountApiError> { AccountApi(mock.client).signOut("ABCDE12345") }::class)
        }
    }

    @Test
    fun `sign out posts to account signout with the device code in the header only`() = runTest {
        val code = "K7M9QRSTXYK7M9QRSTXYK7M9QR"
        val mock = MockApi { respondJson("""{"ok":true}""") }
        AccountApi(mock.client).signOut(code)
        val request = mock.lastRequest
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/account/signout", request.url.encodedPath)
        assertEquals(code, request.headers["X-PT-Code"])
        assertFalse(code in request.url.toString())
        assertFalse(code in request.bodyText())
    }

    @Test
    fun `sign out refusals map like every other account route`() = runTest {
        val table = listOf(
            Triple(429, """{"error":"rate_limited"}""", AccountApiError.RateLimited::class),
            Triple(404, """{"error":"not_found"}""", AccountApiError.NotOpen::class),
            Triple(503, "<html>down</html>", AccountApiError.Unavailable::class),
        )
        table.forEach { (status, body, expected) ->
            val mock = MockApi { respondJson(body, HttpStatusCode.fromValue(status)) }
            assertEquals(body, expected, expectThrows<AccountApiError> { AccountApi(mock.client).signOut("ABCDE12345") }::class)
        }
        val offline = MockApi { throw IOException("offline") }
        expectThrows<IOException> { AccountApi(offline.client).signOut("ABCDE12345") }
    }
}
