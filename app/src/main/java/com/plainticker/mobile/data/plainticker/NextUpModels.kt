package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

// ---- GET /api/v1/vote/next-up ------------------------------------------------------------

/**
 * The uncovered tickers staked SKR has chosen, heaviest first: the server's `SUM(weight)` over
 * verified votes, grouped by ticker, with the tickers that already have an analysis dropped and
 * the top twenty kept. Cached five minutes at the edge, the same way `/summary` is.
 *
 * This is the server query docs/skr-curation-spec-2026-09-13.md said did not exist when the app
 * half was built ("App work", step 3). It exists now, so the strip it feeds is app work.
 */
@Serializable
data class NextUpResponse(
    val schema: String,
    @SerialName("generated_at") val generatedAt: String,
    val rows: List<NextUpRow> = emptyList(),
)

/**
 * One leader. [weight] is kept exactly as the server sends it: a decimal string of base units,
 * six decimals, and a value that can exceed a Long, because one account in the staking program
 * decodes to twenty digits and the sum over many wallets is not bounded by one wallet's ceiling.
 * It is never read through `toLong()` or a Double; [weightRaw] parses it with a BigInteger.
 */
@Serializable
data class NextUpRow(
    val ticker: String,
    val weight: String,
    /** How many wallets carry a counted vote for this ticker. */
    val voters: Int = 0,
    @SerialName("last_vote_at") val lastVoteAt: String? = null,
) {
    /**
     * The weight in base units, or null when [weight] is not a plain non-negative integer string.
     * Null is "not a figure", and a row without a figure is not drawn rather than drawn as a guess.
     */
    fun weightRaw(): BigInteger? =
        weight.trim().takeIf { NON_NEGATIVE_INTEGER.matches(it) }?.let { BigInteger(it) }

    companion object {
        private val NON_NEGATIVE_INTEGER = Regex("[0-9]{1,40}")
    }
}
