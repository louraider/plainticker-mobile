package com.myapp.data.jupiter

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
 * Pure: no Android, no formatting, no copy. The surfaces own the words and [com.myapp.ui.Fmt]
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
         * Measured live on 2026-09-12 against production (docs/data-map.md): of the 157 analyzed
         * xStocks that reach the Analyzed section Jupiter priced 55. The 13 pools at or above
         * $100k all tracked the NYSE close within 0.8 percent. The 6 pools between $10k and $100k
         * deviated plausibly rather than absurdly: NFLXx -2.34 percent on $12.5k, UNHx -2.12
         * percent on $12.3k, XOMx -1.58 percent on $18.8k, which is a real spread on a shallow
         * venue and still a description of the market. Under $10k the number stops describing
         * anything: UBERx read +152.13 percent on a pool of $80, APPx +89.34 percent on $34,
         * CRWDx -42.15 percent on $48, ASMLx +30.42 percent on $61, and 32 of the 55 priced
         * tokens sat in that range.
         *
         * $10k is therefore the lowest depth at which the quote still moved with the underlying
         * instead of with the pool. The floor is deliberately not $100k: that would take the
         * premium off six rows whose numbers are readable, in exchange for a precision the row
         * never claims (it prints two decimals of a percent, not a tracking error budget).
         */
        const val MIN_POOL_USD = 10_000.0

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
