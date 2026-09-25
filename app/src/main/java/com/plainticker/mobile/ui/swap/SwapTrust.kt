package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.repo.SecondRead
import java.math.BigDecimal
import java.math.MathContext

/**
 * The checks that stand between a forwarder's word and the amount a wallet is asked to approve
 * when the xStock is the side being spent (security audit, 2026-09-26). Pure: no network, no
 * clock, no Android, so every refusal is a unit test.
 *
 * Three layers, each of which alone stops the audit's example (a server answering `decimals: 10`
 * so that 1 TSLAx typed becomes the base units of 100):
 *
 * 1. **Decimals are pinned.** Every xStock mint carries [MintFacts.XSTOCK_DECIMALS]; checked for
 *    all 1124 Solana mints in the catalog on 2026-09-26. Any other value is refused, never
 *    replaced by the constant.
 * 2. **The scale and the balance agree with a second source** ([confirmScale]). The multiplier the
 *    sheet types in must equal, within [MULTIPLIER_TOLERANCE], the one a public node reports in
 *    force, and the balance the amount is capped at is never more than that node shows.
 * 3. **The quote's dollar value matches what was typed** ([checkValue]). Jupiter prices the order
 *    it built; that value must sit within [VALUE_BOUND] of the typed quantity at the price the app
 *    shows. This one does not depend on any chain read at all.
 */
object SwapTrust {

    /**
     * How far the forwarder's multiplier may sit from the second source's, relative. Both are
     * parsed from the same decimal string by the same code, so an honest pair is equal to the last
     * bit; the tolerance only absorbs the Double to BigDecimal round trip. 89 of the 1124 catalog
     * mints carry a multiplier other than 1 today (dividends paid in shares, up to about 1.09, and
     * NFLXx at 10), so a check that treated "close to 1" as fine would miss a forged 1.
     */
    const val MULTIPLIER_TOLERANCE = 1e-9

    /**
     * How far Jupiter's own dollar value of the order may sit from the typed quantity times the
     * price the app shows, relative, before the wallet is not opened: 5 percent.
     *
     * Why 5. Both figures are Jupiter's: the price is Price v3, cached for at most 30 seconds
     * ([com.plainticker.mobile.repo.CachedPriceRepository.TTL_MS]), and `inUsdValue` is the same
     * pricing at the moment of the quote, so an honest gap is price movement over half a minute
     * plus rounding, well under 1 percent even in thin extended hours (the widest all-in cost
     * measured was 1.98 percent, and that is cost, not valuation). On the other side, the smallest
     * forgery this layer has to catch alone is a forwarder dropping a real multiplier back to 1:
     * STRCx at 1.086 turns into 8.6 percent more base units, above the bound. A forged scale inside
     * 5 percent is what [confirmScale] catches exactly, so the two layers cover each other.
     */
    const val VALUE_BOUND = 0.05

    sealed interface ScaleVerdict {
        /** The scale agrees. [capRaw] is the balance the amount may be checked against. */
        data class Confirmed(val capRaw: Long) : ScaleVerdict

        /** The reads disagree, or one of them is not an xStock this app can read. Never swap. */
        data class Mismatch(val reason: String) : ScaleVerdict
    }

    sealed interface ValueVerdict {
        data object Consistent : ValueVerdict

        /** The two dollar figures are further apart than [VALUE_BOUND]. */
        data class Mismatch(val reason: String) : ValueVerdict

        /** One of the two figures is missing, so the check cannot be made. Refused, not waved on. */
        data class Unchecked(val reason: String) : ValueVerdict
    }

    /** True when [decimals] is what every xStock mint carries. */
    fun decimalsPinned(decimals: Int): Boolean = decimals == MintFacts.XSTOCK_DECIMALS

    /**
     * Whether [token], as the sheet holds it, and [forwarderRaw], the balance the forwarder
     * reported, agree with [second].
     *
     * The cap is the smaller of the two balances. The forwarder caches for 60 seconds, so right
     * after a swap either read can trail the other; the smaller one is always money the wallet has.
     */
    fun confirmScale(token: SwapToken, forwarderRaw: Long, second: SecondRead): ScaleVerdict {
        if (!decimalsPinned(token.decimals)) {
            return ScaleVerdict.Mismatch("the sheet holds ${token.decimals} decimals, not ${MintFacts.XSTOCK_DECIMALS}")
        }
        val facts = second.facts ?: return ScaleVerdict.Mismatch("the second source does not read this as an xStock mint")
        if (!decimalsPinned(facts.decimals)) {
            return ScaleVerdict.Mismatch("the second source reports ${facts.decimals} decimals")
        }
        val held = token.multiplier ?: return ScaleVerdict.Mismatch("the multiplier is not known")
        val independent = facts.scaledUiAmount?.let { SplitMultiplier.ofMint(it).effectiveAt(second.readAtMillis) }
            ?: SplitMultiplier.NONE
        if (!sameMultiplier(held, independent)) {
            return ScaleVerdict.Mismatch("multiplier ${held.toPlainString()} against $independent from the second source")
        }
        return ScaleVerdict.Confirmed(capRaw = minOf(forwarderRaw, second.spendableRaw).coerceAtLeast(0L))
    }

    /**
     * Whether Jupiter's dollar value of the order, [inUsdValue], agrees with [typedUi] (the
     * quantity as the wallet shows it) times [priceUsd] (the price the app shows for the token).
     */
    fun checkValue(typedUi: BigDecimal?, priceUsd: Double?, inUsdValue: Double): ValueVerdict {
        if (typedUi == null || typedUi.signum() <= 0) return ValueVerdict.Unchecked("no typed quantity")
        if (priceUsd == null || !priceUsd.isFinite() || priceUsd <= 0.0) return ValueVerdict.Unchecked("no price to check against")
        if (!inUsdValue.isFinite() || inUsdValue <= 0.0) return ValueVerdict.Unchecked("the order carried no dollar value")
        val expected = typedUi.multiply(BigDecimal.valueOf(priceUsd), MathContext.DECIMAL64)
        if (expected.signum() <= 0) return ValueVerdict.Unchecked("the typed value rounds to nothing")
        val ratio = BigDecimal.valueOf(inUsdValue).divide(expected, MathContext.DECIMAL64)
        val gap = ratio.subtract(BigDecimal.ONE).abs()
        return if (gap > BigDecimal.valueOf(VALUE_BOUND)) {
            ValueVerdict.Mismatch("order worth $inUsdValue, typed quantity worth ${expected.toPlainString()}")
        } else {
            ValueVerdict.Consistent
        }
    }

    private fun sameMultiplier(held: BigDecimal, independent: Double): Boolean {
        if (!independent.isFinite() || independent <= 0.0 || held.signum() <= 0) return false
        val ratio = held.toDouble() / independent
        return kotlin.math.abs(ratio - 1.0) <= MULTIPLIER_TOLERANCE
    }
}
