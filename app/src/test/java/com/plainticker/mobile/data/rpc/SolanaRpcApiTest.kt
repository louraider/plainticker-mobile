package com.plainticker.mobile.data.rpc

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.net.RateLimitedException
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.repo.ForwarderRpcRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaRpcApiTest {

    private fun api(mock: MockApi) = SolanaRpcApi(mock.client)

    /** Synthetic, base58-valid, 44 chars. Never a real wallet. */
    private val wallet = "TestWa11etPubkey".padEnd(44, '1')

    private suspend fun MockApi.lastBody(): JsonElement = HttpClientFactory.json.parseToJsonElement(lastRequest.bodyText())

    private fun json(text: String): JsonElement = HttpClientFactory.json.parseToJsonElement(text)

    // ---- the contract T2 mirrors ---------------------------------------------------------

    @Test
    fun `the pinned SKR stake query is exactly the forwarder contract, and the slice decodes`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("rpc/gpa-skr-stake.json")) }
        val accounts = api(mock).getSkrStakeAccounts(wallet)

        val request = mock.lastRequest
        assertEquals("https://www.plainticker.com/api/v1/rpc", request.url.toString())
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())

        val expected = json(
            """
            {"jsonrpc":"2.0","id":1,"method":"getProgramAccounts","params":[
              "${KnownPrograms.SKR_STAKING}",
              {"commitment":"confirmed","encoding":"base64",
               "filters":[{"memcmp":{"offset":41,"bytes":"$wallet"}}],
               "dataSlice":{"offset":105,"length":8}}
            ]}
            """,
        )
        assertEquals(expected, mock.lastBody())

        val stake = SkrStake(accounts.mapNotNull { it.toSkrStakeAccount() })
        assertEquals(2, stake.accounts.size)
        assertEquals(100_000_000L, stake.accounts[0].principalRaw)
        assertEquals(100.0, stake.accounts[0].principalSkr, 0.0)
        assertEquals(25_500_000L, stake.accounts[1].principalRaw)
        assertEquals(125_500_000L, stake.totalRaw)
        assertEquals(125.5, stake.totalSkr, 1e-9)
    }

    @Test
    fun `the stake params helper carries the pinned offsets`() {
        val params = SolanaRpcApi.skrStakeParams(wallet).jsonArray
        assertEquals(KnownPrograms.SKR_STAKING, params[0].jsonPrimitive.content)
        val config = params[1].jsonObject
        val memcmp = config["filters"]!!.jsonArray[0].jsonObject["memcmp"]!!.jsonObject
        assertEquals(41, memcmp["offset"]!!.jsonPrimitive.content.toInt())
        assertEquals(wallet, memcmp["bytes"]!!.jsonPrimitive.content)
        val slice = config["dataSlice"]!!.jsonObject
        assertEquals(105, slice["offset"]!!.jsonPrimitive.content.toInt())
        assertEquals(8, slice["length"]!!.jsonPrimitive.content.toInt())
    }

    // ---- the other allowlisted methods ---------------------------------------------------

    @Test
    fun `getTokenAccountsByOwner is jsonParsed and types the amounts`() = runTest {
        val mock = MockApi { respondJson(Fixtures.read("rpc/token-accounts-by-owner.json")) }
        val result = api(mock).getTokenAccountsByOwner(wallet, KnownPrograms.TOKEN_2022)

        assertEquals(
            json("""{"jsonrpc":"2.0","id":1,"method":"getTokenAccountsByOwner","params":["$wallet",{"programId":"${KnownPrograms.TOKEN_2022}"},{"commitment":"confirmed","encoding":"jsonParsed"}]}"""),
            mock.lastBody(),
        )

        assertEquals(445980001L, result.context?.slot)
        val balances = result.value!!.mapNotNull { it.toTokenBalance() }
        assertEquals(2, balances.size)

        val tsla = balances[0]
        assertEquals(KnownMints.TSLAX, tsla.mint)
        assertEquals(137_000_000L, tsla.amountRaw)
        assertEquals(8, tsla.decimals)
        assertEquals("1.37", tsla.uiAmountString)
        assertEquals(KnownPrograms.TOKEN_2022, tsla.programId)
        assertEquals(1.37, tsla.quantity(), 1e-9)
        assertEquals(13.7, tsla.quantity(multiplier = 10.0), 1e-9)
        assertEquals("spl-token-2022", result.value!![0].account.parsedProgram)
        assertEquals("account", result.value!![0].account.parsedType)

        assertEquals(0L, balances[1].amountRaw)
    }

    @Test
    fun `the repository reads both token programs and drops empty accounts`() = runTest {
        val mock = MockApi { request ->
            val programId = HttpClientFactory.json.parseToJsonElement(request.bodyText())
                .jsonObject["params"]!!.jsonArray[1].jsonObject["programId"]!!.jsonPrimitive.content
            when (programId) {
                KnownPrograms.TOKEN_2022 -> respondJson(Fixtures.read("rpc/token-accounts-by-owner.json"))
                KnownPrograms.TOKEN -> respondJson("""{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":[]},"id":1}""")
                else -> respondJson("""{"jsonrpc":"2.0","error":{"code":-32602,"message":"unexpected program"},"id":1}""")
            }
        }
        val balances = ForwarderRpcRepository(api(mock)).tokenBalances(wallet)

        assertEquals(2, mock.requests.size)
        assertEquals(listOf(KnownMints.TSLAX), balances.map { it.mint })
    }

    @Test
    fun `getBalance returns lamports`() = runTest {
        val mock = MockApi { respondJson("""{"jsonrpc":"2.0","result":{"context":{"slot":445980002},"value":2039280},"id":1}""") }
        val result = api(mock).getBalance(wallet)

        assertEquals(json("""{"jsonrpc":"2.0","id":1,"method":"getBalance","params":["$wallet",{"commitment":"confirmed"}]}"""), mock.lastBody())
        assertEquals(2039280L, result.value)
        assertEquals(2039280L, ForwarderRpcRepository(api(mock)).lamports(wallet))
    }

    @Test
    fun `getAccountInfo base64 decodes bytes and a missing account is null`() = runTest {
        val mock = MockApi { request ->
            val encoding = HttpClientFactory.json.parseToJsonElement(request.bodyText())
                .jsonObject["params"]!!.jsonArray[1].jsonObject["encoding"]!!.jsonPrimitive.content
            assertEquals("base64", encoding)
            respondJson(
                """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":{"lamports":1,"owner":"${KnownPrograms.SKR_STAKING}","data":["AOH1BQAAAAA=","base64"],"executable":false,"rentEpoch":18446744073709551615,"space":8}},"id":1}""",
            )
        }
        val account = api(mock).getAccountInfo(wallet).value
        assertNotNull(account)
        assertEquals(KnownPrograms.SKR_STAKING, account!!.owner)
        assertEquals("AOH1BQAAAAA=", account.base64Data())
        assertEquals(8, account.bytes()!!.size)
        assertNull(account.parsed)

        val missing = MockApi { respondJson("""{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":null},"id":1}""") }
        assertNull(api(missing).getAccountInfo(wallet).value)
        assertNull(ForwarderRpcRepository(api(missing)).accountInfo(wallet))
    }

    @Test
    fun `getMultipleAccounts keeps positions, jsonParsed asks for jsonParsed`() = runTest {
        val mock = MockApi { request ->
            val params = HttpClientFactory.json.parseToJsonElement(request.bodyText()).jsonObject["params"]!!.jsonArray
            assertEquals(2, params[0].jsonArray.size)
            assertEquals("jsonParsed", params[1].jsonObject["encoding"]!!.jsonPrimitive.content)
            respondJson("""{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":[null,{"lamports":5,"owner":"${KnownPrograms.TOKEN}","data":{"program":"spl-token","parsed":{"type":"mint","info":{"decimals":6}},"space":82}}]},"id":1}""")
        }
        val accounts = api(mock).getMultipleAccounts(listOf(KnownMints.USDC, KnownMints.TSLAX), RpcEncoding.JSON_PARSED).value!!
        assertEquals(2, accounts.size)
        assertNull(accounts[0])
        assertEquals("mint", accounts[1]!!.parsedType)
        assertEquals("6", accounts[1]!!.parsedInfo!!["decimals"]!!.jsonPrimitive.content)
    }

    // ---- failure shapes -----------------------------------------------------------------

    @Test
    fun `a JSON-RPC error object becomes RpcException`() = runTest {
        val mock = MockApi { respondJson("""{"jsonrpc":"2.0","error":{"code":-32601,"message":"method not allowed"},"id":1}""") }
        val e = expectThrows<RpcException> { api(mock).getBalance(wallet) }
        assertEquals(-32601, e.code)
        assertEquals("getBalance", e.method)
        assertTrue(e.message!!.contains("method not allowed"))
    }

    @Test
    fun `the forwarder's 429 is typed like every other API`() = runTest {
        val mock = MockApi { respondJson("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }
        val e = expectThrows<RateLimitedException> { api(mock).getBalance(wallet) }
        assertEquals("rate_limited", e.errorCode)
    }

    @Test
    fun `a pubkey that is not base58 is refused before any request`() = runTest {
        val mock = MockApi { respondJson("{}") }
        expectThrows<IllegalArgumentException> { api(mock).getBalance("not a key") }
        expectThrows<IllegalArgumentException> { api(mock).getSkrStakeAccounts("0OIl".padEnd(44, '1')) }
        expectThrows<IllegalArgumentException> { api(mock).getTokenAccountsByOwner(wallet, "../etc") }
        expectThrows<IllegalArgumentException> { api(mock).getMultipleAccounts(emptyList()) }
        assertEquals(0, mock.requests.size)
        assertFalse(mock.requests.isNotEmpty())
    }
}
