package com.plainticker.mobile.data.rpc

import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.net.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class RpcEncoding(val wire: String) {
    BASE64("base64"),
    JSON_PARSED("jsonParsed"),
}

/**
 * Solana JSON-RPC through PlainTicker's bounded forwarder (plan section 5; the server side is T2).
 *
 * The forwarder allowlists exactly these methods, pins getProgramAccounts to the SKR staking
 * program with the memcmp + dataSlice below, caps body size, rate-limits per IP and caches
 * 60 s per (method, params). Requests are therefore built deterministically: fixed key
 * order, a constant [REQUEST_ID], one commitment level, so identical reads share a cache
 * entry. Every pubkey is checked against the base58 alphabet before it is sent.
 *
 * A non-2xx surfaces as [com.plainticker.mobile.data.net.ApiException]; a 2xx carrying a JSON-RPC
 * `error` object throws [RpcException].
 */
class SolanaRpcApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
) {
    /**
     * @param minContextSlot when given, the node must have seen at least this slot before it
     *   answers. Passed after a swap lands, with the slot the landing reported: it keeps a lagging
     *   node from answering with the balance from before the swap, and because the forwarder keys
     *   its 60 s cache on the params, it also keeps a cached pre-swap answer from being served.
     */
    suspend fun getBalance(pubkey: String, minContextSlot: Long? = null): ContextValue<Long> =
        call(METHOD_GET_BALANCE, buildJsonArray {
            add(pubkey(pubkey))
            add(config(null, minContextSlot))
        })

    suspend fun getAccountInfo(pubkey: String, encoding: RpcEncoding = RpcEncoding.BASE64): ContextValue<RpcAccount> =
        call(METHOD_GET_ACCOUNT_INFO, buildJsonArray {
            add(pubkey(pubkey))
            add(config(encoding))
        })

    /** Up to [MAX_MULTIPLE_ACCOUNTS] accounts; a missing one is a null in the same position. */
    suspend fun getMultipleAccounts(
        pubkeys: List<String>,
        encoding: RpcEncoding = RpcEncoding.BASE64,
        minContextSlot: Long? = null,
    ): ContextValue<List<RpcAccount?>> {
        require(pubkeys.isNotEmpty()) { "no pubkeys" }
        require(pubkeys.size <= MAX_MULTIPLE_ACCOUNTS) { "at most $MAX_MULTIPLE_ACCOUNTS pubkeys per call" }
        return call(METHOD_GET_MULTIPLE_ACCOUNTS, buildJsonArray {
            addJsonArray { pubkeys.forEach { add(pubkey(it)) } }
            add(config(encoding, minContextSlot))
        })
    }

    /**
     * Token accounts of [owner] under one token program, jsonParsed so amounts come typed. For
     * Token-2022 (every xStock) this is the only read that lists the accounts; there is no
     * separate "parsed" method on the wire, `getParsedTokenAccountsByOwner` is a web3.js wrapper
     * around exactly this call with `encoding: jsonParsed`. [minContextSlot] as on [getBalance].
     */
    suspend fun getTokenAccountsByOwner(
        owner: String,
        programId: String,
        minContextSlot: Long? = null,
    ): ContextValue<List<KeyedAccount>> =
        call(METHOD_GET_TOKEN_ACCOUNTS_BY_OWNER, buildJsonArray {
            add(pubkey(owner))
            addJsonObject { put("programId", pubkey(programId)) }
            add(config(RpcEncoding.JSON_PARSED, minContextSlot))
        })

    /**
     * The one getProgramAccounts the forwarder accepts: SKR stake accounts whose staker
     * field (offset [SKR_STAKE_OWNER_OFFSET]) is [wallet], sliced to the 8-byte principal at
     * [SKR_STAKE_PRINCIPAL_OFFSET]. Anything else is refused server-side.
     */
    suspend fun getSkrStakeAccounts(wallet: String): List<KeyedAccount> =
        call(METHOD_GET_PROGRAM_ACCOUNTS, skrStakeParams(wallet))

    private suspend inline fun <reified T> call(method: String, params: JsonArray): T {
        val response: RpcResponse<T> = client.post(baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(RpcRequest(jsonrpc = JSON_RPC_VERSION, id = REQUEST_ID, method = method, params = params))
        }.bodyOrThrow()
        response.error?.let { throw RpcException(it.code, method, it.message) }
        return response.result ?: throw RpcException(0, method, "empty result")
    }

    private fun config(encoding: RpcEncoding?, minContextSlot: Long? = null): JsonObject = buildJsonObject {
        put("commitment", COMMITMENT)
        if (encoding != null) put("encoding", encoding.wire)
        // The forwarder accepts this key on both account reads and getBalance (lib/rpc/forwarder.ts,
        // ACCOUNT_CONFIG_KEYS and BALANCE_CONFIG_KEYS); a non-positive slot says nothing, so it is left out.
        if (minContextSlot != null && minContextSlot > 0L) put("minContextSlot", minContextSlot)
    }

    private fun pubkey(value: String): String = requireBase58(value)

    companion object {
        const val BASE_URL = "https://www.plainticker.com/api/v1/rpc"

        const val JSON_RPC_VERSION = "2.0"

        /** Constant on purpose: the forwarder keys its cache on the body, and ids carry nothing here. */
        const val REQUEST_ID = 1
        const val COMMITMENT = "confirmed"
        const val MAX_MULTIPLE_ACCOUNTS = 100

        const val METHOD_GET_BALANCE = "getBalance"
        const val METHOD_GET_ACCOUNT_INFO = "getAccountInfo"
        const val METHOD_GET_MULTIPLE_ACCOUNTS = "getMultipleAccounts"
        const val METHOD_GET_TOKEN_ACCOUNTS_BY_OWNER = "getTokenAccountsByOwner"
        const val METHOD_GET_PROGRAM_ACCOUNTS = "getProgramAccounts"

        /** SKR stake account layout the pinned query encodes. Mirrored by the forwarder (T2). */
        const val SKR_STAKE_OWNER_OFFSET = 41
        const val SKR_STAKE_PRINCIPAL_OFFSET = 105
        const val SKR_STAKE_PRINCIPAL_LENGTH = 8

        private val BASE58_PUBKEY = Regex("[1-9A-HJ-NP-Za-km-z]{32,44}")

        /** The trimmed value, or an [IllegalArgumentException] when it is not a base58 pubkey. */
        fun requireBase58(value: String): String {
            val trimmed = value.trim()
            require(BASE58_PUBKEY.matches(trimmed)) { "not a base58 pubkey: '$value'" }
            return trimmed
        }

        /** The exact params array of the pinned getProgramAccounts, for the contract test and the forwarder. */
        fun skrStakeParams(wallet: String): JsonArray {
            val staker = requireBase58(wallet)
            return buildJsonArray {
                add(KnownPrograms.SKR_STAKING)
                addJsonObject {
                    put("commitment", COMMITMENT)
                    put("encoding", RpcEncoding.BASE64.wire)
                    putJsonArray("filters") {
                        addJsonObject {
                            putJsonObject("memcmp") {
                                put("offset", SKR_STAKE_OWNER_OFFSET)
                                put("bytes", staker)
                            }
                        }
                    }
                    putJsonObject("dataSlice") {
                        put("offset", SKR_STAKE_PRINCIPAL_OFFSET)
                        put("length", SKR_STAKE_PRINCIPAL_LENGTH)
                    }
                }
            }
        }
    }
}
