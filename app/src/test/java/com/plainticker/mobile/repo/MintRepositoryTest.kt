package com.plainticker.mobile.repo

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.RpcEncoding
import com.plainticker.mobile.data.rpc.RpcException
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The whole mint read end to end: the request the forwarder sees, the live answer it returns, and
 * the slot and wall clock the live bar needs from it.
 */
class MintRepositoryTest {

    private var now = 1_757_700_000_000L

    private fun repository(mock: MockApi) =
        ForwarderMintRepository(ForwarderRpcRepository(SolanaRpcApi(mock.client))) { now }

    @Test
    fun `the read asks for jsonParsed and comes back with the slot and the time of the fetch`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("rpc/mint-tslax.json")) }
        val reading = repository(mock).mint(KnownMints.TSLAX)

        val body = HttpClientFactory.json.parseToJsonElement(mock.lastRequest.bodyText()).jsonObject
        assertEquals("getAccountInfo", body["method"]!!.jsonPrimitive.content)
        val params = body["params"]!!.jsonArray
        assertEquals(KnownMints.TSLAX, params[0].jsonPrimitive.content)
        assertEquals(RpcEncoding.JSON_PARSED.wire, params[1].jsonObject["encoding"]!!.jsonPrimitive.content)
        assertEquals(SolanaRpcApi.COMMITMENT, params[1].jsonObject["commitment"]!!.jsonPrimitive.content)

        assertEquals(446_503_662L, reading.slot)
        assertEquals("the wall clock at the read, not at the draw", now, reading.readAtMillis)
        assertTrue(reading.readable)

        val facts = checkNotNull(reading.facts)
        assertEquals(8, facts.decimals)
        assertNotNull(facts.permanentDelegate)
        assertFalse(facts.pausable!!.paused)
    }

    @Test
    fun `an account that is not there is a successful read with nothing to report`() = runTest {
        val mock = MockApi {
            respondJson("""{"jsonrpc":"2.0","id":1,"result":{"context":{"slot":446000000},"value":null}}""")
        }
        val reading = repository(mock).mint(KnownMints.TSLAX)

        assertNull(reading.facts)
        assertFalse("unknown, which the screen renders as a chain it could not read", reading.readable)
        assertEquals(446_000_000L, reading.slot)
    }

    @Test
    fun `a forwarder that refuses throws, so the caller can mark the trust rows unread`() = runTest {
        val refused = MockApi { respondJson("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }
        expectThrows<IOException> { repository(refused).mint(KnownMints.TSLAX) }

        val errored = MockApi {
            respondJson("""{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"Invalid param"}}""")
        }
        expectThrows<RpcException> { repository(errored).mint(KnownMints.TSLAX) }
    }
}
