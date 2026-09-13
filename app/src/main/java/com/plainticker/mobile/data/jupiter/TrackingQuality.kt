package com.plainticker.mobile.data.jupiter

/**
 * How far one xStock's Jupiter quote can be trusted as a tracking figure, read from the pool
 * behind it. The one rule; every surface that would draw a premium or a tracking gauge asks this
 * and nothing else, so the list row, the gauge and the Detail screen cannot disagree.
 *
 * Three answers, because the live data has three shapes (docs/data-map.md, "The liquidity floor,
 * measured 2026-09-12"):
 *
 * - [Tracked]: the pool is at or above [MIN_POOL_USD], so the quote describes the market and the
 *   premium may be drawn.
 * - [Thin]: Jupiter reported a pool below the floor. The premium off such a pool is arithmetic,
 *   not a price, so no surface draws it; the pool is stated instead, which is a fact about the
 *   token and not a risk flag (DESIGN.md section 2 keeps Caution for issuer control).
 * - [Untracked]: Jupiter priced the token but did not say how deep the pool is. The honest
 *   reading of a missing depth is unknown, never deep, so the premium is withheld here too.
 *
 * Pure: no Android, no formatting, no copy. The surfaces own the words and [com.plainticker.mobile.ui.Fmt]
 * owns the numerals.
 */
sealed interface TrackingQuality {

    /** The premium against the NYSE close, in percent, when a surface may draw it. */
    val premiumPct: Double?

    /** The pool behind the quote in USD, when Jupiter reported one. */
    val poolUsd: Double?

    /**
     * A pool deep enough for its quote to mean something. [premiumPct] is null only when Jupiter
     * sent no reference price for the underlying, which is a missing NYSE close rather than a
     * tracking failure, and the surface says so in its own words.
     */
    data class Tracked(override val premiumPct: Double?, override val poolUsd: Double) : TrackingQuality

    /** A pool below the floor: [poolUsd] is what the surface states in place of the premium. */
    data class Thin(override val poolUsd: Double) : TrackingQuality {
        override val premiumPct: Double? get() = null
    }

    /** Jupiter answered without a depth, so nothing about the quote can be vouched for. */
    data object Untracked : TrackingQuality {
        override val premiumPct: Double? get() = null
        override val poolUsd: Double? get() = null
    }

    companion object {

        /**
         * The shallowest pool whose quote this app will present as a tracking figure, in USD.
         *
         * Re-measured live on 2026-09-13 after the founder asked how liquidity is checked at all.
         * That check found the figure is not the pool it is documented as (docs/data-map.md), so
         * the floor is now read from what the quote does rather than from what the depth claims.
         * Every analyzed xStock Jupiter reports a depth for, sorted by that depth:
         *
         * | floor | rows that keep a premium | widest premium in the set |
         * |---|---|---|
         * | $10,000 | 19 | INTCx -4.13% |
         * | **$4,000** | **22** | **INTCx -4.13%** |
         * | $2,500 | 25 | Vx +6.64% |
         * | $1,000 | 29 | JPMx +37.98% |
         *
         * **$4,000 buys three readable rows for nothing.** NFLXx at $9,370 reads -1.95, PEPx at
         * $5,351 reads +0.11 and ORCLx at $4,444 reads -0.78, and the widest premium in the whole
         * tracked set does not move, because it already belonged to INTCx at $26,205, far above
         * either floor. The next step down is where it breaks: Vx at $2,513 prints +6.64 and JPMx
         * at $2,002 prints +37.98, which are pool arithmetic and not prices.
         *
         * Below it the number stops describing anything at all. UBERx read +152.13 percent on $80,
         * APPx +89.34 on $34, CRWDx -42.15 on $48. Those are not understated depths: on 2026-09-13
         * DexScreener indexed no trading pair for any of them, GeckoTerminal agreed, and a $100
         * order into UBERx came back at -64.2 percent price impact.
         *
         * The floor is deliberately not $100k, and no longer $10k. Both withhold rows whose
         * numbers a reader can use, in exchange for a precision the row never claims: it prints
         * two decimals of a percent, not a tracking error budget.
         */
        const val MIN_POOL_USD = 4_000.0

        /**
         * The widest deviation from the NYSE close a pool above [MIN_POOL_USD] produced when the
         * catalogue was measured, in percent, and therefore the narrowest scale a gauge over the
         * tracked set may be drawn on.
         *
         * Re-measured 2026-09-13 against the live catalogue, over the 22 rows the floor above
         * now tracks: the widest is INTCx at -4.13 percent on $26,205, then HOODx -3.93 on
         * $358,520 and XOMx -3.18 on $17,019. All three sit far above even the old $10k floor, so
         * this number was already too narrow for the set it describes and lowering the floor did
         * not make it so: at either floor the widest premium is the same INTCx reading.
         *
         * **4.5** covers it with headroom. The previous value, 2.5, was read on 2026-09-12 when
         * the widest tracked premium was NFLXx at -2.34 percent, and one day later three of the
         * 22 tracked rows ran past it. A gauge that leaves its scale for one row in seven is
         * reporting the scale badly, not reporting an exception.
         *
         * The cost is accepted rather than hidden: the deepest pools read within 1 percent, so on
         * a 4.5 scale they sit close to the reference and the gauge says "on the close" about
         * them, which is the true story. The exact premium is printed in mono beside the track
         * either way. It lives here rather than in the UI because it is a fact about the market,
         * read from the same rows the floor was read from.
         */
        const val TRACKED_SPREAD_PCT = 4.5

        /**
         * The rule. Null when Jupiter did not price the token at all: with no quote there is no
         * tracking question to answer, and the screen says the prices are missing on its own.
         */
        fun of(priceUsd: Double?, referenceUsd: Double?, poolUsd: Double?): TrackingQuality? {
            val price = priceUsd ?: return null
            if (!price.isFinite()) return null
            // A depth Jupiter never sent, or one that is not a number of dollars, is unknown.
            if (poolUsd == null || !poolUsd.isFinite() || poolUsd < 0.0) return Untracked
            if (poolUsd < MIN_POOL_USD) return Thin(poolUsd)
            return Tracked(premiumPct = premiumPct(price, referenceUsd), poolUsd = poolUsd)
        }

        /** The same rule from a Price v3 entry, which is where all three inputs come from. */
        fun of(entry: PriceEntry?): TrackingQuality? =
            of(entry?.usdPrice, entry?.stockData?.price, entry?.liquidity)

        /**
         * The token's premium over the NYSE close, in percent, or null when either side is
         * missing. Deliberately not public as a shortcut around [of]: a premium that no one may
         * draw is still computed here, and only [of] decides whether it reaches a screen.
         */
        private fun premiumPct(priceUsd: Double?, referenceUsd: Double?): Double? {
            val price = priceUsd ?: return null
            val reference = referenceUsd ?: return null
            if (!price.isFinite() || !reference.isFinite() || reference <= 0.0) return null
            return (price / reference - 1.0) * 100.0
        }
    }
}
