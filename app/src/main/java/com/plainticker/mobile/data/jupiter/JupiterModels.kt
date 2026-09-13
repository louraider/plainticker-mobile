package com.plainticker.mobile.data.jupiter

import com.plainticker.mobile.data.net.LenientDoubleSerializer
import com.plainticker.mobile.data.net.LenientLongSerializer
import kotlinx.serialization.Serializable

// ---- Price v3 ---------------------------------------------------------------------------

/** One entry of GET /price/v3. The response is a map keyed by mint; unpriced mints are null or absent. */
@Serializable
data class PriceEntry(
    val usdPrice: Double,
    val blockId: Long? = null,
    val decimals: Int? = null,
    val priceChange24h: Double? = null,
    val liquidity: Double? = null,
    val createdAt: String? = null,
    /** Present only for tokenised equities; carries the underlying's reference price. */
    val stockData: StockData? = null,
)

@Serializable
data class StockData(
    /** Issuer id, e.g. "xstocks". */
    val id: String? = null,
    /** Reference price of the underlying share in USD. */
    val price: Double? = null,
    val mcap: Double? = null,
    val updatedAt: String? = null,
)

// ---- Swap v2 (Ultra): GET /order -------------------------------------------------------

/**
 * A GET /order answer: a quote and, when a `taker` was given, the transaction to sign.
 *
 * Amounts stay as the decimal strings Jupiter sends (u64-safe); the `...Raw` accessors
 * give them as Long. [allInCostPct], [hasExpiry] and [secondsLeft] carry over from the
 * spike unchanged.
 */
@Serializable
data class SwapOrder(
    val requestId: String,
    /** "aggregator" (Metis routing) or "rfq". */
    val swapType: String = "",
    /** "metis", "jupiterz", ... The RFQ routers put an expiry on the quote; Metis does not. */
    val router: String = "",
    val mode: String? = null,
    val inputMint: String = "",
    val outputMint: String = "",
    val inAmount: String = "0",
    val outAmount: String = "0",
    val otherAmountThreshold: String? = null,
    val swapMode: String? = null,
    val slippageBps: Int = 0,
    @Serializable(with = LenientDoubleSerializer::class) val priceImpactPct: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class) val priceImpact: Double? = null,
    val feeBps: Int = 0,
    val feeMint: String? = null,
    val platformFee: PlatformFee? = null,
    val gasless: Boolean = false,
    val jitOptimized: Boolean = false,
    val guaranteedPrice: Boolean = false,
    val taker: String? = null,
    /** Base64 of the unsigned transaction. Null on a quote-only order (no taker). */
    val transaction: String? = null,
    /** Epoch seconds. RFQ quotes only; absent on Metis orders. */
    @Serializable(with = LenientLongSerializer::class) val expireAt: Long? = null,
    @Serializable(with = LenientLongSerializer::class) val lastValidBlockHeight: Long? = null,
    @Serializable(with = LenientDoubleSerializer::class) val inUsdValue: Double = 0.0,
    @Serializable(with = LenientDoubleSerializer::class) val outUsdValue: Double = 0.0,
    @Serializable(with = LenientDoubleSerializer::class) val swapUsdValue: Double? = null,
    val signatureFeeLamports: Long = 0L,
    val signatureFeePayer: String? = null,
    val prioritizationFeeLamports: Long = 0L,
    val prioritizationFeePayer: String? = null,
    val rentFeeLamports: Long = 0L,
    val rentFeePayer: String? = null,
    val routePlan: List<RoutePlanStep> = emptyList(),
    val totalTime: Long? = null,
) {
    val inAmountRaw: Long get() = inAmount.toLongOrNull() ?: 0L
    val outAmountRaw: Long get() = outAmount.toLongOrNull() ?: 0L
    val platformFeeBps: Int get() = platformFee?.feeBps ?: 0

    /** All-in cost, the number worth showing a user: what went in vs what came out. */
    val allInCostPct: Double
        get() = if (inUsdValue > 0) (inUsdValue - outUsdValue) / inUsdValue * 100.0 else 0.0

    /** True only for RFQ quotes. Metis orders carry no expireAt at all. */
    val hasExpiry: Boolean get() = (expireAt ?: 0L) > 0L

    /** Seconds left, or null when the order has no expiry (a Metis route). */
    fun secondsLeft(nowEpochSec: Long): Long? =
        if (!hasExpiry) null else (expireAt ?: 0L) - nowEpochSec

    /** True when this order can be signed: it was built for a taker and carries bytes. */
    val isSignable: Boolean get() = !transaction.isNullOrBlank()
}

@Serializable
data class PlatformFee(
    val feeBps: Int = 0,
    val feeMint: String? = null,
)

@Serializable
data class RoutePlanStep(
    val percent: Int? = null,
    val bps: Int? = null,
    val usdValue: Double? = null,
    val swapInfo: SwapInfo? = null,
)

@Serializable
data class SwapInfo(
    val ammKey: String? = null,
    /** Venue name, e.g. "Whirlpool". */
    val label: String? = null,
    val inputMint: String? = null,
    val outputMint: String? = null,
    val inAmount: String? = null,
    val outAmount: String? = null,
)

// ---- Swap v2 (Ultra): POST /execute ----------------------------------------------------

@Serializable
data class ExecuteRequest(
    val signedTransaction: String,
    val requestId: String,
)

/**
 * A 2xx answer from POST /execute. `status` is "Success" or "Failed"; on failure [code]
 * and [error] say why, and [errorOrNull] maps them onto [SwapError].
 */
@Serializable
data class ExecuteResult(
    val status: String = "",
    val signature: String? = null,
    val code: Int? = null,
    val error: String? = null,
    val slot: String? = null,
    val inputAmountResult: String? = null,
    val outputAmountResult: String? = null,
) {
    val isSuccess: Boolean get() = status.equals("Success", ignoreCase = true)

    fun errorOrNull(): SwapError? =
        if (isSuccess) null else SwapError.fromCode(code, error, SwapError.Stage.EXECUTE)
}
