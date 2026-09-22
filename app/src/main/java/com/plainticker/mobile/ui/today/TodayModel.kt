package com.plainticker.mobile.ui.today

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.words
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

/**
 * What Today's four built blocks say (docs/design-research-2026-09-21.md section 3): the venue and
 * data age line, Tracked today's lede, Next up's single row, and the footer count. Block 2, Yours,
 * is the kept Watched section (com.plainticker.mobile.ui.watchlist.WatchlistModel) already draws;
 * nothing here duplicates it.
 *
 * The same split every other screen's model keeps: [com.plainticker.mobile.ui.watchlist.WatchlistViewModel]
 * decides the numbers, off one join over the same repositories Stocks reads so the two screens'
 * coverage counts cannot disagree; this file decides the sentence. Every function here is pure and
 * total, and a fact that is not yet known (no catalog read, no round) returns null rather than a
 * guess, so a caller never has to invent a sentence to fill a slot before the day's first refresh.
 */

/** One row of "Tracked today": an analyzed xStock whose pool clears the liquidity floor. */
data class TrackedRow(
    val ticker: String,
    val symbol: String?,
    val company: String?,
    /** Null only when Jupiter never sent a reference price; the pool still clears the floor. */
    val premiumPct: Double?,
    val poolUsd: Double,
) {
    val display: String get() = symbol ?: ticker

    /** The formatted premium, or the placeholder [Fmt] draws for any other missing figure. */
    val figure: String get() = premiumPct?.let { Fmt.percent(it) } ?: "-"
}

/** The single "Next up" row: the heaviest-staked ticker staked SKR has chosen to cover next. */
data class TodayLeader(
    val ticker: String,
    val symbol: String?,
    val company: String?,
    val weightRaw: BigInteger,
    val voters: Int,
) {
    val display: String get() = symbol ?: ticker

    /** "6,719 SKR", the same string and the same rounding the List's own leaders read. */
    val weight: Copy get() = words(R.string.next_up_weight, skrAmount(weightRaw))

    /** "1 voter", "3 voters": the same counted copy the List's leaders read, reused rather than forked. */
    val votersContext: Copy get() = counted(R.plurals.next_up_voters, voters, Fmt.count(voters))
}

/** A weight in SKR from its base units, the same six-decimal, one-kept-decimal rule List reads by. */
private fun skrAmount(raw: BigInteger): String =
    Fmt.tokenAmount(BigDecimal(raw).movePointLeft(SkrStakeBound.SKR_DECIMALS), maxDecimals = 1)

/** Rows Today draws inline before "All N tracked" hands the rest to Stocks. */
const val TrackedPreviewCount: Int = 6

// ---- Block 1: venue and data age ---------------------------------------------------------------

/**
 * The one line of facts (research section 3, block 1): where the venue is right now. Null while no
 * catalog has answered at all (a null [MarketStatus] is `MarketHours.ofCatalog`'s own reading of an
 * empty catalog), because "the NYSE is closed" is not a sentence this screen can state about a
 * venue it has not read yet.
 *
 * The four-way branch mirrors `ListUiState.hours` exactly, so Today and Stocks can never disagree
 * about where the venue is; the one difference is that this line is a standing fact rather than a
 * caveat, so unlike Stocks' banner it is drawn in the open, venue-sourced case too.
 */
fun venueSentence(market: MarketStatus?): Copy? {
    val m = market ?: return null
    val guessed = m.source == MarketSource.LOCAL_SCHEDULE
    return when {
        m.regularSession && guessed -> words(R.string.banner_market_open_local)
        m.regularSession -> words(R.string.today_venue_open)
        guessed -> words(R.string.banner_market_closed_local)
        else -> words(R.string.banner_market_closed)
    }
}

/**
 * How old the analysis and the prices are, read together or not at all: naming one age and staying
 * silent about the other would read as the other one being unknown, which is not always true, so
 * the line waits for both. True before the first refresh of the day the same way the lede below is:
 * not yet drawn, never drawn wrong.
 */
fun freshnessSentence(analysisAtMillis: Long?, pricesAtMillis: Long?, nowMillis: Long): Copy? {
    val analysisAt = analysisAtMillis ?: return null
    val pricesAt = pricesAtMillis ?: return null
    return words(
        R.string.today_venue_freshness,
        Fmt.relativeAgo(Instant.ofEpochMilli(analysisAt), Instant.ofEpochMilli(nowMillis)),
        Fmt.relativeAgo(Instant.ofEpochMilli(pricesAt), Instant.ofEpochMilli(nowMillis)),
    )
}

// ---- Block 3: Tracked today ---------------------------------------------------------------------

/**
 * The lede (research section 3): a fact about how far the product's coverage reaches today, not
 * about how many stocks moved. That is the one shape of sentence this block can state truthfully in
 * every state: true when nothing has moved and true when everything has, because tracking is a fact
 * about pool depth and never about direction; true before the first refresh of the day because it
 * is simply not drawn then ([analyzedTotal] is 0 until the join has run once), which is exactly the
 * state a sentence about "how many moved" could not stay honest in without a price history this
 * app does not keep.
 */
fun trackedLede(trackedCount: Int, analyzedTotal: Int): Copy? {
    if (analyzedTotal <= 0) return null
    return words(R.string.today_tracked_lede, Fmt.count(trackedCount), Fmt.count(analyzedTotal)) // lint-allow count: a coverage ratio, no noun agreement
}

/** "All 22 tracked", the footer of the Tracked block that hands the rest to Stocks. */
fun trackedAllCopy(trackedCount: Int): Copy =
    words(R.string.today_tracked_all, Fmt.count(trackedCount)) // lint-allow count: a total, no noun agreement

// ---- Block 4: Next up ----------------------------------------------------------------------------

/** "Round 2 closes 28 Sep 2026 00:00 UTC", the same round com.plainticker.mobile.ui.vote.VoteScreen names. */
fun nextUpRoundLede(round: VoteRound?): Copy? {
    if (round == null) return null
    val closesAt = round.closesAtInstant() ?: return null
    return words(R.string.today_next_up_round_closes, Fmt.count(round.id), Fmt.utc(closesAt)) // lint-allow count: a round number, no noun follows
}

// ---- Block 5: the footer -------------------------------------------------------------------------

/** "160 analyzed, 768 without analysis", the same two totals Stocks' own segmented control counts. */
fun footerCopy(analyzedTotal: Int, withoutAnalysisTotal: Int): Copy? {
    if (analyzedTotal <= 0 && withoutAnalysisTotal <= 0) return null
    return words(R.string.today_footer_count, Fmt.count(analyzedTotal), Fmt.count(withoutAnalysisTotal)) // lint-allow count: two totals, no noun agreement
}
