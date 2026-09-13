package com.plainticker.mobile.data

import com.plainticker.mobile.data.rpc.ScaledUiAmountConfig
import com.plainticker.mobile.data.xstocks.Multiplier

/** Where a split multiplier was read from, so the screen can say which answer it is showing. */
enum class MultiplierSource {
    /** The `scaledUiAmountConfig` extension on the Token-2022 mint. Preferred: it is the chain. */
    MINT,

    /** The xStocks multiplier endpoint. The fallback, used when the mint could not be read. */
    XSTOCKS,
}

/** A scheduled change of the scaled UI amount: the value it becomes, and when. */
data class PendingMultiplier(
    val multiplier: Double,
    /** Unix seconds at which [multiplier] takes effect. */
    val activatesAtEpochSeconds: Long,
) {
    fun activatesAtMillis(): Long = activatesAtEpochSeconds * 1_000L

    /** True once the clock has passed the activation, so a screen can stop calling it pending. */
    fun activated(nowMillis: Long): Boolean = nowMillis >= activatesAtMillis()
}

/**
 * The multiplier a raw balance is scaled by, and the split still to come.
 *
 * Two sources answer the same question and they can disagree: the mint is the chain and the
 * xStocks endpoint is the issuer's description of it. The chain wins whenever it was read, and the
 * answer carries its [source] so the screen never presents an issuer statement as an on-chain fact
 * (docs/data-map.md, Detail "Split multiplier": mint extension, fallback xStocks multiplier).
 */
data class SplitMultiplier(
    val current: Double,
    val source: MultiplierSource,
    /** The change still scheduled, or null when the mint has none. */
    val pending: PendingMultiplier?,
) {
    /**
     * The multiplier actually in force at [nowMillis], which is what a balance is scaled by.
     *
     * Token-2022 keeps the scheduled value beside the old one and switches at the timestamp by
     * itself: `ScaledUiAmountConfig::total_multiplier` answers `new_multiplier` from
     * `new_multiplier_effective_timestamp` onwards, and the stored `multiplier` field is only
     * rewritten the next time the authority updates the extension. So between a split taking
     * effect and the issuer's next write, [current] is last week's number and the chain is
     * already applying the other one. Anything that multiplies a raw balance to a share count
     * asks this; [current] is only what the mint has stored.
     */
    fun effectiveAt(nowMillis: Long): Double =
        pending?.takeIf { it.activated(nowMillis) }?.multiplier ?: current

    companion object {
        /** A multiplier that is missing or not a positive number means no rescaling, which is 1. */
        const val NONE = 1.0

        /**
         * xStocks types the activation as a plain JSON number and the live value is 0, so the unit
         * is not pinned by the contract. A value past this is milliseconds (as seconds it would be
         * the year 5138), anything below it is seconds.
         */
        private const val MILLIS_ABOVE = 100_000_000_000L

        /** From the mint's own extension. A scheduled change must differ and must carry a time. */
        fun ofMint(config: ScaledUiAmountConfig): SplitMultiplier = SplitMultiplier(
            current = usable(config.multiplier),
            source = MultiplierSource.MINT,
            pending = config
                .takeIf { it.newMultiplierEffectiveAtEpochSeconds > 0L && it.newMultiplier != it.multiplier }
                ?.let { PendingMultiplier(usable(it.newMultiplier), it.newMultiplierEffectiveAtEpochSeconds) },
        )

        /** From the xStocks endpoint, the fallback when the mint could not be read. */
        fun ofXStocks(multiplier: Multiplier): SplitMultiplier = SplitMultiplier(
            current = usable(multiplier.currentMultiplier),
            source = MultiplierSource.XSTOCKS,
            pending = multiplier
                .takeIf { it.hasScheduledChange }
                ?.let { PendingMultiplier(usable(it.newMultiplier), epochSeconds(it.activationDateTime)) },
        )

        private fun usable(value: Double): Double = if (value.isFinite() && value > 0.0) value else NONE

        private fun epochSeconds(raw: Long): Long = if (raw > MILLIS_ABOVE) raw / 1_000L else raw
    }
}
