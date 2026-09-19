package com.plainticker.mobile.ui.list

import com.plainticker.mobile.R
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.words
import java.math.BigDecimal
import java.math.BigInteger

/**
 * What the "Next up" strip says, decided here and drawn by the List (docs/skr-curation-spec-2026-09-13.md,
 * "App work", step 3, which waited on a server query that now exists).
 *
 * The strip is a function of [ListUiState] and nothing else, so every rule about what it names and
 * when it stays off the screen is a unit test on a plain JVM rather than a device run.
 */

/** The most leaders the strip names. Three is a strip; twenty is a second list. */
const val NEXT_UP_STRIP_SIZE = 3

/**
 * A weight in SKR from its base units: six decimals, at most one of them kept, a trailing zero
 * trimmed, thousands grouped. "0", "1,000", "12,345.7". The raw value is a BigInteger because the
 * server's string can exceed a Long, and nothing here is ever a Double.
 */
fun skrWeight(raw: BigInteger): String =
    Fmt.tokenAmount(BigDecimal(raw).movePointLeft(SkrStakeBound.SKR_DECIMALS), maxDecimals = 1)

/** One leader of the strip, joined to the token row the list already draws under "Without analysis". */
data class NextUpLeader(
    /** The equity ticker: the Detail route and the vote's key. */
    val ticker: String,
    /** What the row calls it, the token symbol ("NFLXx"); the ticker until the catalog names it. */
    val display: String,
    val company: String?,
    val weightRaw: BigInteger,
    val voters: Int,
) {
    /** "31,209.9 SKR" */
    val weight: Copy get() = words(R.string.next_up_weight, skrWeight(weightRaw))

    /** "3 voters", "1 voter": counted copy, so one reads as one. */
    val votersCopy: Copy get() = counted(R.plurals.next_up_voters, voters, Fmt.count(voters))
}

/**
 * The strip: the top [NEXT_UP_STRIP_SIZE] leaders the list can name, in the server's order.
 *
 * Empty, and so undrawn, when the server answered nothing or the call failed, while a search is
 * narrowing the list, and when there is no "Without analysis" section on screen to draw it under.
 * A leader the catalog does not carry is not drawn: the strip names tokens, and it is drawn under
 * a heading of tokens. A leader that has turned up under "Analyzed" has been covered since the
 * tally and is not next up any more, which is the loop closing rather than a fault. A weight that
 * is not a number drops its row rather than printing a guess, and the next leader takes its place.
 *
 * One ticker is one leader. Two rows that normalise onto the same token would be two rows under
 * one key, and the list is keyed by that ticker, so a duplicated ticker is not a repeated row but
 * a crash: `LazyColumn` throws on the second use of a key. The server groups by ticker and should
 * never send two, exactly as `/summary` should never serve one ticker twice, and [ListViewModel]
 * keeps its own guard against that for the same reason. This is that guard for the strip.
 */
val ListUiState.nextUpStrip: List<NextUpLeader>
    get() {
        if (nextUp.isEmpty() || query.isNotBlank() || withoutAnalysis.isEmpty()) return emptyList()
        val uncovered = withoutAnalysis.associateBy { it.ticker.uppercase() } // lint-allow uppercase: map key
        return nextUp.mapNotNull { row ->
            val token = uncovered[row.ticker.trim().uppercase()] ?: return@mapNotNull null // lint-allow uppercase: map key
            val raw = row.weightRaw() ?: return@mapNotNull null
            NextUpLeader(
                ticker = token.ticker,
                display = token.display,
                company = token.company,
                weightRaw = raw,
                voters = row.voters,
            )
        }.distinctBy { it.ticker }.take(NEXT_UP_STRIP_SIZE)
    }

/**
 * What the "Without analysis" section draws under the strip: everything in it the strip is not
 * already naming.
 *
 * A leader is one row on the screen and not two. Drawn twice it is the same ticker, the same
 * company and the same vote a few rows apart under one heading, which reads as a rendering fault
 * rather than as emphasis, and the second row carries a price where the first carries a weight so
 * a reader cannot even tell which is authoritative. The spec asks for a strip of leaders over the
 * uncovered section (docs/skr-curation-spec-2026-09-13.md, step 3) and says nothing about
 * repeating them, so they are lifted out of the list rather than duplicated into it.
 *
 * [leaders] is [nextUpStrip], passed in because the screen has already worked it out and this
 * join runs over every uncovered token on every recomposition.
 */
fun ListUiState.rowsBelowNextUp(leaders: List<NextUpLeader>): List<ListRow> {
    if (leaders.isEmpty()) return withoutAnalysis
    val named = leaders.mapTo(HashSet(leaders.size)) { it.ticker }
    return withoutAnalysis.filterNot { it.ticker in named }
}
