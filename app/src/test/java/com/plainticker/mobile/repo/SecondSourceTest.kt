package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The independent read Swap to USDC is checked against (security audit, 2026-09-26): it goes to
 * the public Solana node rather than PlainTicker's forwarder, parses through the same mappers as
 * the forwarder's read, and fails loudly rather than answering with a guess.
 */
class SecondSourceTest {

    private val wallet = "TestWa11etPubkey".padEnd(44, '1')

    private fun node(accountInfo: String = Fixtures.read("rpc/mint-tslax.json")) = MockApi { request ->
        val method = HttpClientFactory.json.parseToJsonElement(request.bodyText()).jsonObject["method"]!!.jsonPrimitive.content
        when (method) {
            SolanaRpcApi.METHOD_GET_ACCOUNT_INFO -> respondJson(accountInfo)
            SolanaRpcApi.METHOD_GET_TOKEN_ACCOUNTS_BY_OWNER -> respondJson(Fixtures.read("rpc/token-accounts-by-owner.json"))
            else -> respondJson("""{"error":"unexpected"}""", HttpStatusCode.BadRequest)
        }
    }

    private fun source(mock: MockApi) =
        PublicRpcSecondSource(SolanaRpcApi(mock.client, PublicRpcSecondSource.PUBLIC_RPC_URL), Clock { 42L })

    @Test
    fun `the second source is the public node, never PlainTicker's forwarder`() {
        assertEquals("https://api.mainnet-beta.solana.com", PublicRpcSecondSource.PUBLIC_RPC_URL)
        assertNotEquals(SolanaRpcApi.BASE_URL, PublicRpcSecondSource.PUBLIC_RPC_URL)
        assertTrue("plainticker" !in PublicRpcSecondSource.PUBLIC_RPC_URL)
    }

    @Test
    fun `it reads the mint and the wallet's spendable balance of it from the node it was given`() = runTest {
        val mock = node()
        val read = source(mock).read(wallet, KnownMints.TSLAX, minContextSlot = 445_980_001L)

        assertNotNull(read.facts)
        assertEquals(MintFacts.XSTOCK_DECIMALS, read.facts!!.decimals)
        assertEquals("only the TSLAx account counts", 137_000_000L, read.spendableRaw)
        assertEquals(42L, read.readAtMillis)
        assertTrue(mock.requests.all { it.url.toString().startsWith(PublicRpcSecondSource.PUBLIC_RPC_URL) })

        val balanceRead = HttpClientFactory.json.parseToJsonElement(mock.requests.last().bodyText()).jsonObject
        val params = balanceRead["params"]!!.jsonArray
        assertEquals(KnownPrograms.TOKEN_2022, params[1].jsonObject["programId"]!!.jsonPrimitive.content)
        assertEquals("445980001", params[2].jsonObject["minContextSlot"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a mint the node reports with other decimals reads as no xStock, not as eight`() = runTest {
        val inflated = Fixtures.read("rpc/mint-tslax.json").replace(""""decimals": 8""", """"decimals": 10""")
        val read = source(node(inflated)).read(wallet, KnownMints.TSLAX)
        assertNull(read.facts)
    }

    @Test
    fun `a node that does not answer throws rather than guessing`() = runTest {
        val down = MockApi { respondJson("""{"error":"gateway"}""", HttpStatusCode.BadGateway) }
        val thrown = runCatching { source(down).read(wallet, KnownMints.TSLAX) }.exceptionOrNull()
        assertNotNull(thrown)
    }
}
