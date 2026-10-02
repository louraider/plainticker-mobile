package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.jupiter.SwapOrder
import java.math.BigInteger

/**
 * The least a swap may deliver before it reverts, computed one way for both the sheet and
 * [TransactionGuard].
 *
 * The sheet shows [shownRaw] as "at least"; the guard reads `route_v2`'s own quoted amount and
 * slippage out of the instruction bytes and requires [of] of those to be no lower (mock judges'
 * review, 2026-09-26). One function for both sides, so the figure the reader sees and the figure
 * the bytes are held to can never drift apart through two roundings.
 */
object SwapFloor {

    /**
     * The floor the sheet displays: Jupiter's `otherAmountThreshold` where the order names one,
     * otherwise the estimate less the order's own slippage. Never the estimate itself, which would
     * put "at least {the estimate}" on the screen, a promise this quote never made.
     */
    fun shownRaw(order: SwapOrder): Long =
        order.otherAmountThreshold?.toLongOrNull() ?: of(order.outAmountRaw, order.slippageBps)

    /**
     * floor([amountRaw] x (10,000 - [slippageBps]) / 10,000), which is how an exact-in threshold is
     * computed upstream: on every real Metis order captured, this of the `route_v2` bytes' quoted
     * amount and slippage is exactly the JSON's `otherAmountThreshold`. Slippage is clamped to
     * 0..10,000 so a nonsense value cannot turn the floor negative.
     */
    fun of(amountRaw: Long, slippageBps: Int): Long {
        val bps = slippageBps.coerceIn(0, 10_000)
        return BigInteger.valueOf(amountRaw)
            .multiply(BigInteger.valueOf(10_000L - bps))
            .divide(BigInteger.valueOf(10_000L))
            .toLong()
    }
}
