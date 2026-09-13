package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vote build contract, against [MockApi] exactly as [
 * com.plainticker.mobile.data.jupiter.JupiterSwapApi] is tested.
 *
 * Two things are pinned here that nothing else can pin. The first is that a body which is not the
 * contract is an error and never a crash: this call hands its answer to a wallet, and a
 * serialization exception thrown out of a ViewModel would take a screen with it. The second is
 * that today's answer, a bare 404 from a route nobody has published, arrives as
 * [VoteError.NotOpen] rather than as a failure, so the app can say voting is not open yet and the
 * same path lights up unchanged the day the route answers.
 */
class VoteApiTest {

    /** The public demo wallet. The founder's own address appears in no file in this repository. */
    private val voter = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"

    private val collector = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"

    /** A base64 payload of real bytes, redacted the way the swap fixture redacts its transaction. */
    private val transaction = "UkVEQUNURUQ="

    private fun body(
        transaction: String = this.transaction,
        summary: String = """{"ticker":"NFLX","lamports":5000,"collector":"$collector"}""",
    ) = """{"transaction":"$transaction","summary":$summary}"""

    // ---- The contract, answered -------------------------------------------------------------

    @Test
    fun `a 200 parses into the transaction and the summary the signer is owed`() = runTest {
        val mock = MockApi { respondJson(body()) }
        val build = VoteApi(mock.client).build("NFLX", voter)

        assertEquals(transaction, build.transaction)
        assertEquals("NFLX", build.summary.ticker)
        assertEquals(5_000L, build.summary.lamports)
        assertEquals(collector, build.summary.collector)
        assertNotNull("the transaction must decode to bytes a wallet can be handed", build.transactionBytes())
        assertEquals("REDACTED", build.transactionBytes()!!.decodeToString())
    }

    @Test
    fun `the request is a POST of the ticker and the voter, and carries no weight of its own`() = runTest {
        val mock = MockApi { respondJson(body()) }
        VoteApi(mock.client).build("nflx", voter)

        val request = mock.lastRequest
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://www.plainticker.com/api/v1/vote/build", request.url.toString())
        assertTrue(request.body.contentType!!.match(ContentType.Application.Json))

        val sent = HttpClientFactory.json.parseToJsonElement(request.bodyText()) as JsonObject
        assertEquals("NFLX", sent["ticker"]!!.jsonPrimitive.content)
        assertEquals(voter, sent["voter"]!!.jsonPrimitive.content)
        // The server reads the staked principal itself. A client that could send its own weight
        // could inflate it, so there is no field here to send it in.
        assertEquals("the body carries only the ticker and the voter", setOf("ticker", "voter"), sent.keys)
    }

    @Test
    fun `an answer carrying keys this app does not know still parses`() = runTest {
        val extra = """{"transaction":"$transaction","summary":{"ticker":"NFLX","lamports":5000,""" +
            """"collector":"$collector","memo":"PT-VOTE:NFLX"},"expiresAt":123}"""
        val mock = MockApi { respondJson(extra) }
        val build = VoteApi(mock.client).build("NFLX", voter)
        assertEquals(5_000L, build.summary.lamports)
    }

    // ---- Today's answer ----------------------------------------------------------------------

    @Test
    fun `a 404 is voting not being open yet, whatever the body is`() = runTest {
        // What the live host actually answers on 2026-09-13 for a route nobody has published.
        val page = MockApi { respondHtml("<!DOCTYPE html><html><body>404</body></html>", HttpStatusCode.NotFound) }
        val fromPage = expectThrows<VoteError.NotOpen> { VoteApi(page.client).build("NFLX", voter) }
        assertEquals(404, fromPage.status)

        val explained = MockApi {
            respondJson("""{"error":"Voting opens when the collector is funded.","code":"vote_closed"}""", HttpStatusCode.NotFound)
        }
        val fromBody = expectThrows<VoteError.NotOpen> { VoteApi(explained.client).build("NFLX", voter) }
        assertEquals("Voting opens when the collector is funded.", fromBody.detail)
    }

    // ---- Refusals ----------------------------------------------------------------------------

    @Test
    fun `a 4xx the server explained keeps its slug and its sentence, for the log`() = runTest {
        val mock = MockApi {
            respondJson("""{"error":"NFLX already has an analysis.","code":"already_covered"}""", HttpStatusCode.Conflict)
        }
        val refused = expectThrows<VoteError.Refused> { VoteApi(mock.client).build("NFLX", voter) }
        assertEquals(409, refused.status)
        assertEquals("already_covered", refused.code)
        assertEquals("NFLX already has an analysis.", refused.detail)
    }

    @Test
    fun `a 5xx and a gateway page are both unreadable, and neither is a refusal`() = runTest {
        val server = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
        val fromServer = expectThrows<VoteError.Unreadable> { VoteApi(server.client).build("NFLX", voter) }
        assertEquals(500, fromServer.status)

        val gateway = MockApi { respondHtml("<html>502 Bad Gateway</html>", HttpStatusCode.BadGateway) }
        val fromGateway = expectThrows<VoteError.Unreadable> { VoteApi(gateway.client).build("NFLX", voter) }
        assertEquals(502, fromGateway.status)
        assertTrue("the excerpt is kept for the log", fromGateway.detail!!.contains("502"))
    }

    // ---- A 200 that is not the contract ------------------------------------------------------

    @Test
    fun `a 200 missing the summary is an error and not a crash`() = runTest {
        val mock = MockApi { respondJson("""{"transaction":"$transaction"}""") }
        val error = expectThrows<VoteError.Unreadable> { VoteApi(mock.client).build("NFLX", voter) }
        assertEquals(200, error.status)
    }

    @Test
    fun `a 200 that is not JSON at all is an error and not a crash`() = runTest {
        val mock = MockApi { respondHtml("<html>hello</html>", HttpStatusCode.OK) }
        expectThrows<VoteError.Unreadable> { VoteApi(mock.client).build("NFLX", voter) }
    }

    @Test
    fun `a transaction this app cannot decode is refused before any wallet is opened`() = runTest {
        val mock = MockApi { respondJson(body(transaction = "not base64 at all")) }
        val error = expectThrows<VoteError.Unreadable> { VoteApi(mock.client).build("NFLX", voter) }
        assertTrue(error.detail!!.contains("base64"))

        val empty = MockApi { respondJson(body(transaction = "")) }
        expectThrows<VoteError.Unreadable> { VoteApi(empty.client).build("NFLX", voter) }
    }

    @Test
    fun `a summary whose fields are the wrong type is an error and not a crash`() = runTest {
        val mock = MockApi {
            respondJson(body(summary = """{"ticker":"NFLX","lamports":"five thousand","collector":"$collector"}"""))
        }
        expectThrows<VoteError.Unreadable> { VoteApi(mock.client).build("NFLX", voter) }
    }

    // ---- What never leaves the device --------------------------------------------------------

    @Test
    fun `a ticker or a voter of the wrong shape never becomes a request`() = runTest {
        val mock = MockApi { respondJson(body()) }
        val api = VoteApi(mock.client)
        expectThrows<IllegalArgumentException> { api.build("not a ticker", voter) }
        expectThrows<IllegalArgumentException> { api.build("", voter) }
        expectThrows<IllegalArgumentException> { api.build("NFLX", "0OIl-not-base58") }
        assertTrue("nothing was sent", mock.requests.isEmpty())
    }

    @Test
    fun `the error body reader keeps the server sentence apart from its slug`() {
        val json = HttpClientFactory.json
        val refused = VoteError.fromErrorBody(400, """{"error":"No such ticker.","code":"unknown_ticker"}""", json)
        assertTrue(refused is VoteError.Refused)
        assertEquals("unknown_ticker", refused.code)
        assertEquals("No such ticker.", refused.detail)

        // A 4xx with no body at all is not a refusal this app can read.
        assertTrue(VoteError.fromErrorBody(400, null, json) is VoteError.Unreadable)
        assertNull(VoteError.fromErrorBody(400, null, json).code)
    }

    @Test
    fun `the golden body is exactly the contract in the specification`() = runTest {
        // The shape docs/skr-curation-spec-2026-09-13.md and the founder's contract name, written
        // out field for field, so a later edit to either is caught here rather than on a device.
        val golden = """{"transaction":"$transaction","summary":{"ticker":"NFLX","lamports":5000,"collector":"$collector"}}"""
        val parsed = (HttpClientFactory.json.parseToJsonElement(golden) as JsonObject)
        assertEquals(setOf("transaction", "summary"), parsed.keys)
        assertEquals(setOf("ticker", "lamports", "collector"), parsed["summary"]!!.jsonObject.keys)

        val mock = MockApi { respondJson(golden) }
        assertEquals(5_000L, VoteApi(mock.client).build("NFLX", voter).summary.lamports)
    }
}
