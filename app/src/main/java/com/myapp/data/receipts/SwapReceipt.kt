package com.myapp.data.receipts

import kotlinx.serialization.Serializable

/**
 * One swap this app landed, as this app saw it.
 *
 * The chain carries no cost basis: a token account knows a balance and nothing about what was
 * paid for it, and the swap that filled it is one instruction among others in a transaction that
 * the wallet, not this app, may have batched. So the honest record of what a person paid is the
 * app's own, written at the moment the swap landed, and marked as such wherever it is shown
 * (plan section 13, Pass 8: Portfolio's "Cost basis is not read from the chain.").
 *
 * Every number is stored the way it came off the wire: base units plus the decimals they are
 * counted in, never a float of tokens. [allInCostPct] is the cost actually paid, computed from
 * the executed fill rather than from the quote, because the two differ (a landed swap on
 * 2026-09-10 beat its quote by 0.037 percent).
 */
@Serializable
data class SwapReceipt(
    /** The transaction signature. Also the identity of the receipt: one landing, one row. */
    val signature: String,
    val inputMint: String,
    val inputSymbol: String,
    val inputAmountRaw: Long,
    val inputDecimals: Int,
    val outputMint: String,
    val outputSymbol: String,
    /**
     * What the wallet actually received, from the executed result and never from the quote.
     * Null when the answer did not report it: an unknown fill is recorded as unknown, because a
     * cost basis built from an estimate is a wrong number that reads as a measured one.
     */
    val outputAmountRaw: Long? = null,
    val outputDecimals: Int,
    /**
     * All-in cost of this fill in percent, positive when the swap cost the taker value. Null
     * when the fill was not reported, or when the order priced neither side in dollars, which
     * is not the same fact as zero.
     */
    val allInCostPct: Double? = null,
    /** The router that filled it, as the quote named it: "Metis". */
    val route: String,
    /** Wall clock when the swap landed, epoch millis. */
    val landedAtMillis: Long,
    /** The slot the execute answer reported, when it reported one. */
    val slot: Long? = null,
)
