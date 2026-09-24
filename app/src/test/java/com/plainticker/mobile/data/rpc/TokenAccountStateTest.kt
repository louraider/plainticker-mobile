package com.plainticker.mobile.data.rpc

import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.repo.ForwarderRpcRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things a swap back to USDC needs from a Token-2022 balance read that the forward swap
 * never did (2026-09-24): whether the account is frozen, and a read no older than a landing.
 *
 * Both are the method the app already uses, `getTokenAccountsByOwner`, which PlainTicker's
 * forwarder allowlists (`lib/rpc/forwarder.ts`, `RPC_ALLOWED_METHODS`) with a `programId` filter,
 * `jsonParsed` and `minContextSlot` all accepted (`ACCOUNT_CONFIG_KEYS`); a live read through
 * `https://www.plainticker.com/api/v1/rpc` with exactly this body answered 200 on 2026-09-24.
 * No server change is needed.
 */
class TokenAccountStateTest {

    private val wallet = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"

    private fun keyed(state: String?) = KeyedAccount(
        pubkey = "acct",
        account = RpcAccount(
            owner = KnownPrograms.TOKEN_2022,
            data = HttpClientFactory.json.parseToJsonElement(
                """{"program":"spl-token-2022","parsed":{"type":"account","info":{"mint":"XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB","owner":"$wallet",""" +
                    (state?.let { """"state":"$it",""" } ?: "") +
                    """"tokenAmount":{"amount":"264600","decimals":8,"uiAmountString":"0.002646"}}}}""",
            ),
        ),
    )

    @Test
    fun `a frozen account says so, an initialized one does not, and a missing state reads as not frozen`() {
        val frozen = requireNotNull(keyed("frozen").toTokenBalance())
        assertEquals("frozen", frozen.state)
        assertTrue(frozen.frozen)
        assertFalse(requireNotNull(keyed("initialized").toTokenBalance()).frozen)
        val unstated = requireNotNull(keyed(null).toTokenBalance())
        assertNull(unstated.state)
        assertFalse(unstated.frozen)
    }

    @Test
    fun `a read after a landing names its slot, on both token programs and the SOL balance`() = runTest {
        val empty = """{"jsonrpc":"2.0","result":{"context":{"slot":450068700},"value":[]},"id":1}"""
        val mock = MockApi { request ->
            val method = HttpClientFactory.json.parseToJsonElement(request.bodyText())
                .jsonObject["method"]!!.jsonPrimitive.content
            if (method == "getBalance") {
                respondJson("""{"jsonrpc":"2.0","result":{"context":{"slot":450068700},"value":96000000},"id":1}""")
            } else {
                respondJson(empty)
            }
        }
        val repo = ForwarderRpcRepository(SolanaRpcApi(mock.client))
        repo.tokenBalances(wallet, minContextSlot = 450_068_700L)
        repo.lamports(wallet, minContextSlot = 450_068_700L)
        val configs = mock.requests.map { r ->
            HttpClientFactory.json.parseToJsonElement(r.bodyText())
                .jsonObject["params"]!!.jsonArray.last().jsonObject
        }
        assertEquals(3, configs.size)
        configs.forEach { assertEquals("450068700", it["minContextSlot"]!!.jsonPrimitive.content) }
    }

    @Test
    fun `an ordinary read names no slot, so it shares the forwarder's cache entry as before`() = runTest {
        val mock = MockApi { respondJson("""{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":[]},"id":1}""") }
        ForwarderRpcRepository(SolanaRpcApi(mock.client)).tokenBalances(wallet)
        mock.requests.forEach { r ->
            val config = HttpClientFactory.json.parseToJsonElement(r.bodyText())
                .jsonObject["params"]!!.jsonArray.last().jsonObject
            assertNull(config["minContextSlot"])
            assertEquals(setOf("commitment", "encoding"), config.keys)
        }
    }
}
