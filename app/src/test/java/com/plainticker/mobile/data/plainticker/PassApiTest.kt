package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `POST /api/v1/pass/build` and `POST /api/v1/pass/confirm` (server/vote/README.md, task S3):
 * paying for Pro the way a vote is built, and confirming the signature rather than waiting for
 * the cron. [PassBuild.transactionBytes] is the envelope parsing task A6's acceptance list names:
 * a base64 transaction the app can actually hand to a wallet.
 */
class PassApiTest {

    private val payer = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"
    private val destination = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
    private val treasury = "E1STBTGEYpHanVG4HWUJHfzEu6eGbnE9mnGX9KnAmJdL"

    /** A base64 payload of real bytes, redacted the way the vote fixture already redacts its own. */
    private val transaction = "UkVEQUNURUQ="

    private fun buildBody(transaction: String = this.transaction) = """
        {"transaction":"$transaction","summary":
          {"mint":"USDC","amount":12000000,"destination":"$destination","treasury":"$treasury","lamports":5000},
         "expiresAt":"2026-09-19T12:00:45.000Z"}
    """.trimIndent()

    // ---- build, and the envelope it hands back --------------------------------------------------

    @Test
    fun `build parses the transaction bytes and the summary a signer is owed`() = runTest {
        val mock = MockApi { respondJson(buildBody()) }
        val build = PassApi(mock.client).build(payer, "USDC", "a".repeat(64))

        assertEquals(transaction, build.transaction)
        assertNotNull("the envelope decodes to bytes a wallet can sign", build.transactionBytes())
        assertEquals(12_000_000L, build.summary.amount)
        assertEquals("USDC", build.summary.mint)
        assertEquals(destination, build.summary.destination)
        assertEquals(treasury, build.summary.treasury)
        assertEquals(5_000L, build.summary.lamports)
        assertEquals(1_789_819_245_000L, build.expiresAtMillis())
    }

    @Test
    fun `build sends the payer, the mint and the code hash, never the code itself`() = runTest {
        val mock = MockApi { respondJson(buildBody()) }
        PassApi(mock.client).build(payer, "USDC", "f".repeat(64))

        val sent = HttpClientFactory.json.parseToJsonElement(mock.lastRequest.bodyText()).jsonObject
        assertEquals(payer, sent["payer"]!!.jsonPrimitive.content)
        assertEquals("USDC", sent["mint"]!!.jsonPrimitive.content)
        assertEquals("f".repeat(64), sent["codeHash"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a transaction this app cannot decode as base64 never reaches a wallet`() = runTest {
        val mock = MockApi { respondJson(buildBody(transaction = "not base64 at all!!")) }
        expectThrows<PassError.Unavailable> { PassApi(mock.client).build(payer, "USDC", "a".repeat(64)) }
    }

    @Test
    fun `503 monetization disabled and pass not configured both read as disabled`() = runTest {
        val flagOff = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        expectThrows<PassError.Disabled> { PassApi(flagOff.client).build(payer, "USDC", "a".repeat(64)) }

        val noTreasury = MockApi { respondJson("""{"error":"x","code":"pass_not_configured"}""", HttpStatusCode.ServiceUnavailable) }
        expectThrows<PassError.Disabled> { PassApi(noTreasury.client).build(payer, "USDC", "a".repeat(64)) }
    }

    @Test
    fun `invalid input and a 429 are refused and rate limited respectively`() = runTest {
        val bad = MockApi { respondJson("""{"error":"x","code":"invalid_input"}""", HttpStatusCode.BadRequest) }
        expectThrows<PassError.Refused> { PassApi(bad.client).build(payer, "EUR", "a".repeat(64)) }

        val limited = MockApi { respondJson("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }
        expectThrows<PassError.RateLimited> { PassApi(limited.client).build(payer, "USDC", "a".repeat(64)) }
    }

    // ---- confirm ---------------------------------------------------------------------------------

    @Test
    fun `confirm answers the same shape entitlement does, for a landed signature`() = runTest {
        val mock = MockApi { respondJson("""{"pro":true,"source":"pass","until":"2026-10-19T12:00:00.000Z"}""") }
        val entitlement = PassApi(mock.client).confirm("4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe")

        assertTrue(entitlement.pro)
        assertEquals(EntitlementSource.PASS, entitlement.sourceKind)

        val sent = HttpClientFactory.json.parseToJsonElement(mock.lastRequest.bodyText()).jsonObject
        assertEquals("4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe", sent["signature"]!!.jsonPrimitive.content)
    }

    @Test
    fun `409 unconfirmed is transient, 422 verification failed is not`() = runTest {
        val notYet = MockApi { respondJson("""{"error":"x","code":"unconfirmed"}""", HttpStatusCode.Conflict) }
        expectThrows<PassError.Unconfirmed> { PassApi(notYet.client).confirm("sig") }

        val rejected = MockApi { respondJson("""{"error":"x","code":"verification_failed"}""", HttpStatusCode.UnprocessableEntity) }
        expectThrows<PassError.VerificationFailed> { PassApi(rejected.client).confirm("sig") }
    }

    @Test
    fun `confirm is gated the same way build is, while monetization stays off`() = runTest {
        val mock = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        expectThrows<PassError.Disabled> { PassApi(mock.client).confirm("sig") }
    }

    @Test
    fun `a 200 that is not the contract never becomes a silent null`() = runTest {
        val garbled = MockApi { respondJson("""{"pro":""") }
        expectThrows<PassError.Unavailable> { PassApi(garbled.client).confirm("sig") }
    }

    /** [assertNull] used once, so a build that sent no expiry at all is read as an unmade promise. */
    @Test
    fun `a build with no expiry makes no promise about staleness`() = runTest {
        val noExpiry = """{"transaction":"$transaction","summary":
            {"mint":"USDC","amount":12000000,"destination":"$destination","treasury":"$treasury","lamports":5000}}"""
        val build = PassApi(MockApi { respondJson(noExpiry) }.client).build(payer, "USDC", "a".repeat(64))
        assertNull(build.expiresAtMillis())
        assertTrue(!build.isExpiredAt(Long.MAX_VALUE))
    }
}
