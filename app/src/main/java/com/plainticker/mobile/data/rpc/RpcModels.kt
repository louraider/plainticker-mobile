package com.plainticker.mobile.data.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/*
 * Solana JSON-RPC 2.0 as it crosses PlainTicker's forwarder. Only the fields the app reads
 * are modelled; unknown keys are ignored by the shared Json configuration.
 */

/** No defaults on `jsonrpc` and `id` on purpose: the shared Json omits default values, and both are required on the wire. */
@Serializable
data class RpcRequest(
    val jsonrpc: String,
    val id: Int,
    val method: String,
    val params: JsonArray,
)

@Serializable
data class RpcError(
    val code: Int = 0,
    val message: String = "",
    val data: JsonElement? = null,
)

@Serializable
data class RpcResponse<T>(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: T? = null,
    val error: RpcError? = null,
)

@Serializable
data class RpcContext(
    val slot: Long = 0L,
    val apiVersion: String? = null,
)

/** The `{context, value}` envelope most account reads come in. `value` is null for a missing account. */
@Serializable
data class ContextValue<T>(
    val context: RpcContext? = null,
    val value: T? = null,
)

@Serializable
data class RpcAccount(
    val lamports: Long = 0L,
    /** Owning program, e.g. the Token-2022 program for an xStock token account. */
    val owner: String = "",
    /** `["<base64>", "base64"]` for base64 reads, an object for jsonParsed reads. */
    val data: JsonElement? = null,
    val executable: Boolean = false,
    /** u64 upstream; rent-exempt accounts report 2^64-1, which is why this is not a Long. */
    val rentEpoch: Double? = null,
    val space: Long? = null,
) {
    /** The base64 payload of a base64-encoded read, or null for jsonParsed data. */
    fun base64Data(): String? {
        val arr = data as? JsonArray ?: return null
        return (arr.getOrNull(0) as? JsonPrimitive)?.contentOrNull
    }

    fun bytes(): ByteArray? = base64Data()?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    /** jsonParsed reads: the `data.program` name, e.g. "spl-token-2022". */
    val parsedProgram: String? get() = ((data as? JsonObject)?.get("program") as? JsonPrimitive)?.contentOrNull

    /** jsonParsed reads: `data.parsed`. */
    val parsed: JsonObject? get() = (data as? JsonObject)?.get("parsed") as? JsonObject

    val parsedType: String? get() = (parsed?.get("type") as? JsonPrimitive)?.contentOrNull

    val parsedInfo: JsonObject? get() = parsed?.get("info") as? JsonObject
}

@Serializable
data class KeyedAccount(
    val pubkey: String = "",
    val account: RpcAccount = RpcAccount(),
)

/** A token account as jsonParsed describes it. [amountRaw] is the on-chain u64; UI amounts are derived. */
data class TokenBalance(
    val tokenAccount: String,
    val mint: String,
    val owner: String,
    val amountRaw: Long,
    val decimals: Int,
    /** The node's own UI string. For scaledUiAmount mints it already includes the multiplier. */
    val uiAmountString: String?,
    /** Token program that owns the account (classic or Token-2022). */
    val programId: String,
    /**
     * The account's own state as jsonParsed names it: "initialized", or "frozen" when the mint's
     * freeze authority has frozen it. A frozen account cannot send, so its balance is held but not
     * spendable. Null when the node did not say, which reads as not frozen: a closed account is
     * never listed at all.
     */
    val state: String? = null,
) {
    /** True when the freeze authority has frozen this account, so nothing in it can be swapped. */
    val frozen: Boolean get() = state == STATE_FROZEN

    /** raw / 10^decimals * [multiplier]; the multiplier is the xStocks scaledUiAmount value (1 unless split). */
    fun quantity(multiplier: Double = 1.0): Double = amountRaw / Math.pow(10.0, decimals.toDouble()) * multiplier
}

fun KeyedAccount.toTokenBalance(): TokenBalance? {
    val info = account.parsedInfo ?: return null
    val amount = info["tokenAmount"] as? JsonObject ?: return null
    val mint = (info["mint"] as? JsonPrimitive)?.contentOrNull ?: return null
    return TokenBalance(
        tokenAccount = pubkey,
        mint = mint,
        owner = (info["owner"] as? JsonPrimitive)?.contentOrNull ?: "",
        amountRaw = (amount["amount"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
        decimals = (amount["decimals"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
        uiAmountString = (amount["uiAmountString"] as? JsonPrimitive)?.contentOrNull,
        programId = account.owner,
        state = (info["state"] as? JsonPrimitive)?.contentOrNull,
    )
}

/** jsonParsed's word for a token account its mint's freeze authority has frozen. */
const val STATE_FROZEN = "frozen"

/** One SKR stake account of a wallet, read through the pinned getProgramAccounts (dataSlice 105/8). */
data class SkrStakeAccount(
    val pubkey: String,
    /** Principal in SKR base units (6 decimals). */
    val principalRaw: Long,
) {
    val principalSkr: Double get() = principalRaw / 1_000_000.0
}

data class SkrStake(val accounts: List<SkrStakeAccount>) {
    val totalRaw: Long get() = accounts.sumOf { it.principalRaw }
    val totalSkr: Double get() = totalRaw / 1_000_000.0

    companion object {
        val NONE = SkrStake(emptyList())
    }
}

/** Decodes the 8-byte little-endian slice the pinned query returns; null when the slice is malformed. */
fun KeyedAccount.toSkrStakeAccount(): SkrStakeAccount? {
    val bytes = account.bytes() ?: return null
    if (bytes.size < SolanaRpcApi.SKR_STAKE_PRINCIPAL_LENGTH) return null
    val raw = ByteBuffer.wrap(bytes, 0, SolanaRpcApi.SKR_STAKE_PRINCIPAL_LENGTH).order(ByteOrder.LITTLE_ENDIAN).long
    return SkrStakeAccount(pubkey, raw)
}

/** The forwarder or the node answered a well-formed request with a JSON-RPC error object. */
class RpcException(
    val code: Int,
    val method: String,
    val detail: String,
) : IOException("$method failed ($code): $detail")
