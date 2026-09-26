package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.time.Instant

// ---- GET /api/v1/vote/next-up ------------------------------------------------------------

/**
 * The uncovered tickers staked SKR has chosen, heaviest first: the server's `SUM(weight)` over
 * verified votes, grouped by ticker, with the tickers that already have an analysis dropped and
 * the top twenty kept. Cached five minutes at the edge, the same way `/summary` is.
 *
 * This is the server query docs/skr-curation-spec-2026-09-13.md said did not exist when the app
 * half was built ("App work", step 3). It exists now, so the strip it feeds is app work.
 *
 * [round] and [previous] are the addition docs/plan-monetisation-2026-09-19.md section 1.4 names
 * (task S1): additive, scoped to the current round, and null on a server that has not stamped
 * rounds yet, which is why the Vote tab (task A2) draws the round header only when [round] is not
 * null rather than assuming the field exists.
 */
@Serializable
data class NextUpResponse(
    val schema: String,
    @SerialName("generated_at") val generatedAt: String,
    val rows: List<NextUpRow> = emptyList(),
    /** The round in progress, or null on a server that has not stamped rounds yet (task S1). */
    val round: VoteRound? = null,
    /**
     * The round that closed most recently, or null before the first round has closed. Present
     * once, not four deep: the live contract (read 2026-09-19) sends exactly one, so "Last round"
     * is drawn singular rather than as the four-deep history section 1.4 describes for later.
     */
    val previous: PreviousRound? = null,
)

/** `next-up.round`: the round in progress, so the Vote tab can name it and say when it closes. */
@Serializable
data class VoteRound(
    val id: Int,
    @SerialName("opens_at") val opensAt: String,
    @SerialName("closes_at") val closesAt: String,
) {
    /** [closesAt] parsed, or null when it is not ISO-8601: an unparsed close is not stated at all. */
    fun closesAtInstant(): Instant? = runCatching { Instant.parse(closesAt) }.getOrNull()
}

/**
 * `next-up.previous`: the round that closed most recently. [status] is the round row's own word.
 * The live server sends `closed` (read 2026-09-26: `lib/vote/rounds.ts` knows only `open` and
 * `closed`, and `previous` is by definition a closed round); `pending`, `published` and
 * `uncoverable` are the tally words the monetisation plan named for later and are kept so a server
 * that starts sending them reads correctly. Any other word ([PreviousRoundStatus.of]) degrades to
 * the same neutral "Round N closed" line `closed` draws, because that much is true of every
 * `previous` whatever its status says, and hiding the section taught readers the round had vanished.
 */
@Serializable
data class PreviousRound(
    val id: Int,
    val winner: String? = null,
    /** Decimal string of SKR base units, exactly as [NextUpRow.weight] is. */
    val weight: String? = null,
    val voters: Int = 0,
    @SerialName("closed_at") val closedAt: String? = null,
    val status: String? = null,
) {
    /** [weight] as base units, or null when it is not a plain non-negative integer string. */
    fun weightRaw(): BigInteger? =
        weight?.trim()?.takeIf { NON_NEGATIVE_INTEGER.matches(it) }?.let { BigInteger(it) }

    /** [closedAt] parsed, or null when absent or not ISO-8601. */
    fun closedAtInstant(): Instant? = closedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }

    companion object {
        private val NON_NEGATIVE_INTEGER = Regex("[0-9]{1,40}")
    }
}

/** The states a closed round's winner can be in, read from [PreviousRound.status]. */
enum class PreviousRoundStatus {
    /** The round closed and the tally named a winner; nothing more is claimed. The live server's word. */
    CLOSED,

    /** The winner has not been analyzed yet: a founder's line and a deploy are still owed. */
    PENDING,

    /** The winner's analysis is live; its Detail is the loop closing. */
    PUBLISHED,

    /** The winner could not be covered. The contract carries no reason, so none is invented. */
    UNCOVERABLE,

    /**
     * A blank, missing or unrecognized status. Drawn exactly as [CLOSED] is: a `previous` round has
     * closed whatever else its status says, so the neutral line is still true, and a sentence that
     * claimed more (covered, being covered) would be a guess wearing the server's certainty.
     */
    UNRECOGNIZED;

    companion object {
        fun of(raw: String?): PreviousRoundStatus = when (raw?.trim()?.lowercase()) {
            "closed" -> CLOSED
            "pending" -> PENDING
            "published" -> PUBLISHED
            "uncoverable" -> UNCOVERABLE
            else -> UNRECOGNIZED
        }
    }
}

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
