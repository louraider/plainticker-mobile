package com.plainticker.mobile.data.receipts

import kotlinx.serialization.SerialName
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
 * counted in, never a float of tokens. [routeCostPct] is the route cost actually paid, computed from
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
     * The route's cost of this fill in percent, positive when the swap cost the taker value. Null
     * when the fill was not reported, or when the order priced neither side in dollars, which
     * is not the same fact as zero. Stored under its old name, `allInCostPct`, so receipts
     * written before 2026-09-27 still read: it never counted the SOL the wallet paid, which is
     * why it is no longer called all-in (judges' review, 2026-09-27).
     */
    @SerialName("allInCostPct")
    val routeCostPct: Double? = null,
    /**
     * The SOL this wallet paid for the swap (signature fee, priority fee, token account rent) in
     * dollars at the SOL price read with the quote. Null on receipts written before it was kept,
     * or when no SOL price came back.
     */
    val solCostUsd: Double? = null,
    /** The token account rent within [solCostUsd]: it comes back if the account is later closed. */
    val rentUsd: Double? = null,
    /** Jupiter's dollar value of what went in, the base [solCostUsd] is a share of. */
    val inputUsd: Double? = null,
    /** The router that filled it, as the quote named it: "Metis". */
    val route: String,
    /** Wall clock when the swap landed, epoch millis. */
    val landedAtMillis: Long,
    /** The slot the execute answer reported, when it reported one. */
    val slot: Long? = null,
    /**
     * The Token-2022 scaled UI multiplier each side was drawn with when it landed: one for USDC
     * and for an unsplit xStock. The raw amounts above stay raw; these are what turn them into
     * the figure a wallet shows (raw / 10^decimals x multiplier). Receipts written before this
     * field existed read as one, which is what every xStock this app had swapped then carried.
     */
    val inputMultiplier: Double = 1.0,
    val outputMultiplier: Double = 1.0,
) {
    /**
     * All-in cost paid in percent: [routeCostPct] plus [solCostUsd] as a share of [inputUsd].
     * Null when any of them is missing, and Portfolio then says "route cost" for what it has.
     */
    val allInCostPct: Double?
        get() {
            val route = routeCostPct ?: return null
            val sol = solCostUsd?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
            val base = inputUsd?.takeIf { it.isFinite() && it > 0.0 } ?: return null
            return route + sol / base * 100.0
        }

    /** What a wallet shows for the input side. */
    fun inputUi(): java.math.BigDecimal = uiOf(inputAmountRaw, inputDecimals, inputMultiplier)

    /** What a wallet shows for the output side, or null when the fill was not reported. */
    fun outputUi(): java.math.BigDecimal? = outputAmountRaw?.let { uiOf(it, outputDecimals, outputMultiplier) }

    private fun uiOf(raw: Long, decimals: Int, multiplier: Double): java.math.BigDecimal =
        java.math.BigDecimal.valueOf(raw).movePointLeft(decimals)
            .multiply(java.math.BigDecimal.valueOf(if (multiplier.isFinite() && multiplier > 0.0) multiplier else 1.0))
}
