package com.plainticker.mobile.watchlist

import com.plainticker.mobile.data.jupiter.TrackingQuality
import java.time.LocalDate

/**
 * One watched ticker, as both the Watchlist screen and the daily check read it.
 *
 * There is one of these rather than one per surface for the reason the liquidity floor lives in
 * one place: the notification and the screen must not be able to disagree about what a token did.
 * The digest is computed from exactly the rows the screen draws.
 */
data class WatchedTicker(
    /** Underlying equity ticker, e.g. "AAPL": the watchlist key and the Detail route. */
    val ticker: String,
    /** The xStock token symbol, e.g. "AAPLx"; null while the catalog is unavailable. */
    val symbol: String?,
    val company: String?,
    val mint: String?,
    /**
     * False when `/summary` no longer carries this ticker. The row stays: the reader asked for it
     * and only the reader takes it off the list, so a ticker PlainTicker has stopped classifying
     * says so rather than quietly disappearing.
     */
    val analyzed: Boolean,
    /** `forward.raw.nextEarningsDate`, the reason a person watches. Null when there is none. */
    val nextReport: LocalDate?,
    val priceUsd: Double?,
    /** The underlying share's reference price from Price v3 `stockData`, when Jupiter has one. */
    val referencePriceUsd: Double?,
    /** The pool behind the quote in USD, from Price v3 `liquidity`. Jupiter may omit it. */
    val poolUsd: Double?,
) {
    /** What the row shows left: the token symbol once the catalog is known, else the ticker. */
    val display: String get() = symbol ?: ticker

    /**
     * How far this row's quote can be trusted, from the one rule in [TrackingQuality]. The floor
     * is the list's floor and the gauge's floor: a premium off a dead pool is not drawn here
     * either, and is not what the digest calls a move.
     */
    val tracking: TrackingQuality? get() = TrackingQuality.of(priceUsd, referencePriceUsd, poolUsd)

    /** The premium against the NYSE close this row may draw, in percent, or null. */
    val premiumPct: Double? get() = tracking?.premiumPct
}

/** What one load of the watched tickers found, and which of its sources did not answer. */
data class WatchedFacts(
    /** One row per watched ticker, in ticker order, whether or not anything answered about it. */
    val rows: List<WatchedTicker> = emptyList(),
    /** `/summary` did not answer, so no row on screen carries an analysis or a report date. */
    val analysisUnavailable: Boolean = false,
    /** The xStocks catalog did not answer, so no row carries a token symbol or a price. */
    val catalogUnavailable: Boolean = false,
    /** Jupiter refused, so no row carries a premium. */
    val pricesUnavailable: Boolean = false,
)
