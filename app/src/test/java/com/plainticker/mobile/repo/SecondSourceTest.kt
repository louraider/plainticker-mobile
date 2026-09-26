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
import com.plainticker.mobile.wallet.TransactionGuard
import io.ktor.client.request.HttpRequestData
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

    // ---- Tolerance: one retry, a relaxed slot, a second public node (audit 2026-09-26, item 6) ----

    private suspend fun method(request: HttpRequestData): String =
        HttpClientFactory.json.parseToJsonElement(request.bodyText()).jsonObject["method"]!!.jsonPrimitive.content

    private suspend fun balanceSlot(request: HttpRequestData): String? =
        HttpClientFactory.json.parseToJsonElement(request.bodyText()).jsonObject["params"]!!.jsonArray[2]
            .jsonObject["minContextSlot"]?.jsonPrimitive?.content

    private val lagging = """{"jsonrpc":"2.0","error":{"code":-32016,"message":"Minimum context slot has not been reached"},"id":1}"""

    @Test
    fun `a rate limit on the first read is retried once after a short pause, with the slot relaxed`() = runTest {
        var balanceReads = 0
        val mock = MockApi { request ->
            when (method(request)) {
                SolanaRpcApi.METHOD_GET_ACCOUNT_INFO -> respondJson(Fixtures.read("rpc/mint-tslax.json"))
                else -> if (balanceReads++ == 0) {
                    respondJson("""{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests"},"id":1}""", HttpStatusCode.TooManyRequests)
                } else {
                    respondJson(Fixtures.read("rpc/token-accounts-by-owner.json"))
                }
            }
        }
        val pauses = mutableListOf<Long>()
        val read = PublicRpcSecondSource(SolanaRpcApi(mock.client, PublicRpcSecondSource.PUBLIC_RPC_URL), Clock { 42L }, pause = { pauses += it })
            .read(wallet, KnownMints.TSLAX, minContextSlot = 445_980_001L)

        assertEquals(137_000_000L, read.spendableRaw)
        assertEquals(listOf(PublicRpcSecondSource.RETRY_BACKOFF_MS), pauses)
        val slots = mock.requests.filter { method(it) == SolanaRpcApi.METHOD_GET_TOKEN_ACCOUNTS_BY_OWNER }.map { balanceSlot(it) }
        assertEquals("the first read asks for the landing's slot, the retry trails it by at most 150", listOf("445980001", "445979851"), slots)
    }

    @Test
    fun `a node behind the forwarder's slot answers the relaxed retry`() = runTest {
        val mock = MockApi { request ->
            when (method(request)) {
                SolanaRpcApi.METHOD_GET_ACCOUNT_INFO -> respondJson(Fixtures.read("rpc/mint-tslax.json"))
                else -> if (balanceSlot(request) == "445980001") respondJson(lagging) else respondJson(Fixtures.read("rpc/token-accounts-by-owner.json"))
            }
        }
        val read = PublicRpcSecondSource(SolanaRpcApi(mock.client, PublicRpcSecondSource.PUBLIC_RPC_URL), Clock { 42L }, pause = {})
            .read(wallet, KnownMints.TSLAX, minContextSlot = 445_980_001L)
        assertEquals(137_000_000L, read.spendableRaw)
        assertNotNull(read.facts)
    }

    @Test
    fun `the relaxed slot trails by the allowance and never goes to zero or below`() {
        assertEquals(850L, PublicRpcSecondSource.relaxedSlot(1_000L))
        assertNull(PublicRpcSecondSource.relaxedSlot(null))
        assertNull(PublicRpcSecondSource.relaxedSlot(PublicRpcSecondSource.SLOT_LAG_ALLOWANCE))
        assertEquals(150L, PublicRpcSecondSource.SLOT_LAG_ALLOWANCE)
    }

    @Test
    fun `the fallback node is a different public operator, and still not PlainTicker`() {
        assertEquals("https://solana-rpc.publicnode.com", PublicRpcSecondSource.FALLBACK_RPC_URL)
        assertNotEquals(PublicRpcSecondSource.PUBLIC_RPC_URL, PublicRpcSecondSource.FALLBACK_RPC_URL)
        assertNotEquals(SolanaRpcApi.BASE_URL, PublicRpcSecondSource.FALLBACK_RPC_URL)
        assertTrue("plainticker" !in PublicRpcSecondSource.FALLBACK_RPC_URL)
    }

    /** A real, valid pubkey, so the associated account can be derived for the fallback read. */
    private val realOwner = "5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq"

    /** getMultipleAccounts over [mint, ata]: the TSLAx mint, then the fixture's TSLAx account, re-owned. */
    private fun multiple(owner: String = realOwner, frozen: Boolean = false): String {
        val mint = HttpClientFactory.json.parseToJsonElement(Fixtures.read("rpc/mint-tslax.json"))
            .jsonObject["result"]!!.jsonObject["value"].toString()
        var token = HttpClientFactory.json.parseToJsonElement(Fixtures.read("rpc/token-accounts-by-owner.json"))
            .jsonObject["result"]!!.jsonObject["value"]!!.jsonArray[0].jsonObject["account"].toString()
            .replace("TestWa11etPubkey1111111111111111111111111111", owner)
        if (frozen) token = token.replace("initialized", "frozen")
        return """{"jsonrpc":"2.0","result":{"context":{"slot":445980002},"value":[$mint,$token]},"id":1}"""
    }

    @Test
    fun `when the first node fails twice, the fallback reads the mint and the associated account in one call`() = runTest {
        val first = MockApi { respondJson("""{"error":"rate limited"}""", HttpStatusCode.TooManyRequests) }
        val fallback = MockApi { respondJson(multiple()) }
        val read = PublicRpcSecondSource(
            api = SolanaRpcApi(first.client, PublicRpcSecondSource.PUBLIC_RPC_URL),
            clock = Clock { 7L },
            fallback = SolanaRpcApi(fallback.client, PublicRpcSecondSource.FALLBACK_RPC_URL),
            pause = {},
        ).read(realOwner, KnownMints.TSLAX, minContextSlot = 445_980_001L)

        assertEquals(MintFacts.XSTOCK_DECIMALS, read.facts!!.decimals)
        assertEquals(137_000_000L, read.spendableRaw)
        assertEquals(7L, read.readAtMillis)
        assertEquals("the first node is asked exactly twice before the fallback", 2, first.requests.size)
        assertEquals(1, fallback.requests.size)
        val call = HttpClientFactory.json.parseToJsonElement(fallback.lastRequest.bodyText()).jsonObject
        assertEquals(SolanaRpcApi.METHOD_GET_MULTIPLE_ACCOUNTS, call["method"]!!.jsonPrimitive.content)
        val keys = call["params"]!!.jsonArray[0].jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf(KnownMints.TSLAX, TransactionGuard.ata(realOwner, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)), keys)
        assertEquals("445979851", call["params"]!!.jsonArray[1].jsonObject["minContextSlot"]!!.jsonPrimitive.content)
        assertTrue(fallback.requests.all { it.url.toString().startsWith(PublicRpcSecondSource.FALLBACK_RPC_URL) })
    }

    @Test
    fun `the fallback counts nothing from a frozen account or one another wallet owns`() = runTest {
        val first = MockApi { respondJson("""{"error":"down"}""", HttpStatusCode.BadGateway) }
        for (answer in listOf(multiple(frozen = true), multiple(owner = "11111111111111111111111111111111"))) {
            val read = PublicRpcSecondSource(
                api = SolanaRpcApi(first.client, PublicRpcSecondSource.PUBLIC_RPC_URL),
                clock = Clock { 0L },
                fallback = SolanaRpcApi(MockApi { respondJson(answer) }.client, PublicRpcSecondSource.FALLBACK_RPC_URL),
                pause = {},
            ).read(realOwner, KnownMints.TSLAX)
            assertEquals(0L, read.spendableRaw)
        }
    }

    @Test
    fun `when every source fails it still throws, so the swap pauses with nothing sent`() = runTest {
        val first = MockApi { respondJson("""{"error":"down"}""", HttpStatusCode.BadGateway) }
        val fallback = MockApi { respondJson("""{"error":"down"}""", HttpStatusCode.ServiceUnavailable) }
        val thrown = runCatching {
            PublicRpcSecondSource(
                api = SolanaRpcApi(first.client, PublicRpcSecondSource.PUBLIC_RPC_URL),
                clock = Clock { 0L },
                fallback = SolanaRpcApi(fallback.client, PublicRpcSecondSource.FALLBACK_RPC_URL),
                pause = {},
            ).read(realOwner, KnownMints.TSLAX, minContextSlot = 1_000L)
        }.exceptionOrNull()
        assertNotNull(thrown)
        assertEquals("both earlier failures ride on the last one", 2, thrown!!.suppressed.size)
        assertEquals(2, first.requests.size)
        assertEquals(1, fallback.requests.size)
    }

    @Test
    fun `without a fallback, a node that fails twice throws after exactly one retry`() = runTest {
        val down = MockApi { respondJson("""{"error":"gateway"}""", HttpStatusCode.BadGateway) }
        val thrown = runCatching { source(down).read(wallet, KnownMints.TSLAX) }.exceptionOrNull()
        assertNotNull(thrown)
        assertEquals("two attempts, each failing on its first call", 2, down.requests.size)
    }
}
