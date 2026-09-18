package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.PriceLabel
import com.plainticker.mobile.data.xstocks.Reserves
import com.plainticker.mobile.data.xstocks.XStockAsset

/**
 * One independently loaded piece of the Detail screen.
 *
 * Detail joins six sources and no two of them fail together. A piece therefore carries its own
 * outcome, so one dead call costs exactly its own rows and never blanks the screen, and so a row
 * can say what happened to it instead of disappearing. [Absent] and [Failed] are kept apart on
 * purpose: "the source answered and has nothing for this token" and "we could not read it" look
 * the same in a null and mean opposite things to a reader deciding whether to swap.
 */
sealed interface Piece<out T> {
    /** Still in flight. The screen draws a skeleton, never a spinner and never a zero. */
    data object Loading : Piece<Nothing>

    data class Ready<T>(val value: T) : Piece<T>

    /** The source answered, and it publishes nothing for this token. */
    data object Absent : Piece<Nothing>

    /** The call failed, or what came back could not be read. */
    data object Failed : Piece<Nothing>

    /** The loaded value, or null in every other state. Named apart from [Ready.value] on purpose. */
    val valueOrNull: T? get() = (this as? Ready)?.value
    val isLoading: Boolean get() = this is Loading
    val isFailed: Boolean get() = this is Failed
    val isAbsent: Boolean get() = this is Absent
}

/** Why the fundamentals half of the screen is or is not there. */
sealed interface AnalysisState {
    data object Loading : AnalysisState

    data class Served(val payload: AnalysisPayload) : AnalysisState

    /**
     * PlainTicker does not classify this ticker: 404 `unsupported_ticker` or `not_available`. The
     * trust layer still renders in full from the chain and xStocks; only the fundamentals are
     * replaced by one row (docs/data-map.md, Detail "Not served").
     */
    data object NotServed : AnalysisState

    /** 503 `insufficient_data`: the filer is known and the classification could not be completed. */
    data object Incomplete : AnalysisState

    /** The call itself failed. Nothing is known either way. */
    data object Unavailable : AnalysisState
}

/** The mint read behind the trust rows and the live bar. */
data class ChainRead(
    val facts: MintFacts,
    /** The slot the node answered at, for "slot N". */
    val slot: Long,
    /** Wall clock at the read, for "2 s ago". */
    val readAtMillis: Long,
)

/**
 * Everything the Detail screen draws, with every source carried as its own piece.
 *
 * The trust layer (chain facts, proof of reserves, split multiplier) does not depend on the
 * analysis: when PlainTicker serves nothing for a ticker the screen still reads the mint and the
 * issuer's reserves, which is the part of the product that has to work for a token nobody has
 * classified yet.
 *
 * The flat fields below [asset] are the shorthands the screen and [DetailModel] read; each one is
 * derived from a piece and none of them hides which piece answered.
 */
data class DetailUiState(
    /** The underlying equity ticker, uppercased. The route key and the PlainTicker join key. */
    val ticker: String,
    val catalogAsset: Piece<XStockAsset> = Piece.Loading,
    val analysisState: AnalysisState = AnalysisState.Loading,
    val quote: Piece<PriceEntry> = Piece.Loading,
    /** The mint read. [Piece.Failed] means the chain could not be read, never that it is clean. */
    val chain: Piece<ChainRead> = Piece.Loading,
    /** [Piece.Absent] means xStocks publishes no reserves for this symbol. */
    val reserves: Piece<Reserves> = Piece.Loading,
    val split: Piece<SplitMultiplier> = Piece.Loading,
    /**
     * The leaders of SKR-weighted coverage curation, asked for only when PlainTicker classifies
     * nothing for this ticker, because only then does its standing among them mean anything.
     * Empty when the call failed or the list is empty; the screen then states no standing.
     */
    val nextUp: List<NextUpRow> = emptyList(),
    /** Where the venue is, from the asset's trading block or the local weekday schedule. */
    val market: MarketStatus? = null,
    val watched: Boolean = false,
    /** Wall clock of the last refresh, so ages and countdowns are read against one instant. */
    val nowMillis: Long = 0L,
) {
    val asset: XStockAsset? get() = catalogAsset.valueOrNull
    val symbol: String? get() = asset?.symbol
    val mint: String? get() = asset?.solanaMint
    val catalogUnavailable: Boolean get() = catalogAsset.isFailed

    val price: PriceEntry? get() = quote.valueOrNull
    val pricesUnavailable: Boolean get() = quote.isFailed

    val analysis: AnalysisPayload? get() = (analysisState as? AnalysisState.Served)?.payload

    /** The multiplier a balance is scaled by, whichever source answered. Null until one does. */
    val multiplier: Double? get() = split.valueOrNull?.current

    /** True while any piece is still in flight. */
    val isLoading: Boolean
        get() = catalogAsset.isLoading || analysisState is AnalysisState.Loading || quote.isLoading ||
            chain.isLoading || reserves.isLoading || split.isLoading

    /**
     * How far the quote can be trusted as a tracking figure, from the one rule in [TrackingQuality].
     * Null when Jupiter has not priced the token: no quote, no tracking question.
     */
    val tracking: TrackingQuality? get() = TrackingQuality.of(price)

    /** The signed premium against the NYSE close the gauge may draw, or null where it may not. */
    val premiumPct: Double? get() = tracking?.premiumPct

    /**
     * How the price row names itself: within a percentage while the exchange is trading, against
     * the NYSE close while it is shut. Unknown market state reads as shut, which is the older and
     * more careful of the two claims.
     */
    val priceLabel: PriceLabel get() = market?.priceLabel ?: PriceLabel.VS_NYSE_CLOSE

    /** Age of the analysis in millis at [nowMillis], or null when it carries no usable `as_of`. */
    val analysisAgeMillis: Long?
        get() = analysis?.asOfEpochMillis()?.let { (nowMillis - it).coerceAtLeast(0L) }

    /** The header states the age only past a day (docs/data-map.md, Detail "Analysis age"). */
    val analysisAgeShown: Boolean get() = (analysisAgeMillis ?: 0L) > DAY_MILLIS

    /** True for the 404 case: the trust layer renders alone and one row explains the rest. */
    val analysisNotServed: Boolean get() = analysisState is AnalysisState.NotServed

    companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
