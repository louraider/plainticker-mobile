package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.plainticker.PreviousRoundStatus
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.ui.list.NextUpLeader
import com.plainticker.mobile.ui.list.skrWeight
import java.math.BigInteger
import java.time.ZoneId

/**
 * The Vote tab (task A2, docs/plan-monetisation-2026-09-19.md section 1.5): the state and the
 * pure decisions [VoteScreen] draws, kept out of the composition exactly as [VoteSheetModel]
 * keeps the sheet's, so every section this screen can show is assertable on a plain JVM.
 */

/**
 * One token this app has no analysis for, the ballot's own row. A much smaller join than
 * [com.plainticker.mobile.ui.list.ListViewModel] runs for the List: no price, no snapshot, no
 * chaptering, because the ballot's job is to be searched and voted on, not priced.
 */
data class BallotEntry(
    val ticker: String,
    val symbol: String,
    val company: String?,
) {
    val display: String get() = symbol.ifBlank { ticker }
}

/** [BallotEntry.ticker], [BallotEntry.symbol] or [BallotEntry.company] containing [query]. */
fun List<BallotEntry>.matchingBallot(query: String): List<BallotEntry> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { entry ->
        entry.ticker.contains(q, ignoreCase = true) ||
            entry.symbol.contains(q, ignoreCase = true) ||
            entry.company?.contains(q, ignoreCase = true) == true
    }
}

/**
 * Every leader `next-up.rows` names that the ballot can also name, in the server's order. Unlike
 * [com.plainticker.mobile.ui.list.nextUpStrip] this carries no cap: the Vote tab's Leaders section
 * is the whole leaderboard, not a three-row teaser over it.
 *
 * A leader the ballot does not carry (covered since the tally, or the ballot has not loaded yet)
 * is not drawn, and neither is one whose weight is not a plain number: this screen states a figure
 * or it states nothing, never a guess.
 */
fun leadersFor(rows: List<NextUpRow>, ballot: List<BallotEntry>): List<NextUpLeader> {
    if (rows.isEmpty() || ballot.isEmpty()) return emptyList()
    val byTicker = ballot.associateBy { it.ticker.trim().uppercase() } // lint-allow uppercase: map key
    return rows.mapNotNull { row ->
        val entry = byTicker[row.ticker.trim().uppercase()] ?: return@mapNotNull null // lint-allow uppercase: map key
        val raw = row.weightRaw() ?: return@mapNotNull null
        NextUpLeader(ticker = entry.ticker, display = entry.display, company = entry.company, weightRaw = raw, voters = row.voters)
    }.distinctBy { it.ticker }
}

/**
 * The previous round's winner, named against the catalog rather than the ballot: a published
 * winner has an analysis by definition and has therefore left the ballot, so only the catalog (not
 * [BallotEntry]) can still say what to call it. Null only when there is no winner to name. A
 * status this build does not recognize is not a reason to hide the section: it reads as
 * [PreviousRoundStatus.UNRECOGNIZED], drawn as the same neutral "Round N closed" line the live
 * `closed` status draws (see [PreviousRoundStatus.of]).
 */
fun previousDisplay(previous: PreviousRound?, catalogByTicker: Map<String, XStockAsset>): PreviousRoundDisplay? {
    if (previous == null) return null
    val ticker = previous.winner?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val status = PreviousRoundStatus.of(previous.status)
    val asset = catalogByTicker[ticker.uppercase()] // lint-allow uppercase: map key
    return PreviousRoundDisplay(
        roundId = previous.id,
        ticker = ticker,
        display = asset?.symbol?.takeIf { it.isNotBlank() } ?: ticker,
        company = asset?.name,
        status = status,
        weightRaw = previous.weightRaw(),
        voters = previous.voters,
        closedAtText = previous.closedAtInstant()?.let { Fmt.utc(it) },
    )
}

data class PreviousRoundDisplay(
    val roundId: Int,
    val ticker: String,
    val display: String,
    val company: String?,
    val status: PreviousRoundStatus,
    val weightRaw: BigInteger?,
    val voters: Int,
    val closedAtText: String?,
) {
    /**
     * The status sentence. `closed` (the live server's word) and any status this build does not
     * recognize both read "Round 1 closed. JEF had the most stake.", which is true of every previous
     * round; only the three tally words claim anything about coverage.
     */
    val sentence: Copy
        get() = when (status) {
            PreviousRoundStatus.PENDING -> words(R.string.vote_tab_last_round_pending, display)
            PreviousRoundStatus.PUBLISHED -> words(R.string.vote_tab_last_round_published, display)
            PreviousRoundStatus.UNCOVERABLE -> words(R.string.vote_tab_last_round_uncoverable, display)
            PreviousRoundStatus.CLOSED, PreviousRoundStatus.UNRECOGNIZED ->
                words(R.string.vote_tab_last_round_closed, Fmt.count(roundId), display) // lint-allow count: a round's number, not a quantity
        }
}

/**
 * The wallet's own votes for the round in progress: task A3's receipts, scoped twice.
 *
 * By voter first: with a wallet connected, only that wallet's receipts count as "yours" here,
 * because a different wallet's past votes are not this reader's votes to see. With none connected
 * (the MWA session lives in memory alone and answers to no cold start), there is no live voter to
 * narrow against, so every receipt this device holds stands, which is what [VoteReceipt] itself
 * documents as the honest answer for that state.
 *
 * By round second: a round in progress narrows to receipts stamped with it; a server that has not
 * stamped rounds at all ([round] null, the minimum-slice case of section 2) narrows to nothing,
 * because there is no round to have voted "for" yet.
 */
fun myVotesFor(receipts: List<VoteReceipt>, round: VoteRound?, connectedVoter: String?): List<VoteReceipt> {
    val byVoter = if (connectedVoter == null) receipts else receipts.filter { it.voter == connectedVoter }
    val byRound = if (round == null) byVoter else byVoter.filter { it.round == round.id }
    return byRound.sortedByDescending { it.landedAtMillis }
}

/**
 * The connected wallet's own stake, the one figure the Vote tab's top card leads with (judges'
 * round 2: the vote action and the weight it carries belong at the top, not under an explainer).
 * Read the same way the vote sheet reads it ([com.plainticker.mobile.data.rpc.SkrStakeBound]), so
 * the card and the sheet cannot disagree about what a vote from this wallet weighs.
 */
sealed interface TabStake {
    /** No wallet is connected, so there is nothing to read. */
    data object NoWallet : TabStake

    data object Reading : TabStake

    /** The principal the staking program holds for the wallet, in raw units (6 decimals). */
    data class Read(val raw: Long) : TabStake

    /** The read failed or came back outside the bound: no figure rather than a guess. */
    data object Unread : TabStake
}

/** The card's stake sentence, one per state, never a figure the read did not produce. */
val TabStake.sentence: Copy
    get() = when (this) {
        TabStake.NoWallet -> words(R.string.vote_tab_stake_no_wallet)
        TabStake.Reading -> words(R.string.vote_tab_stake_reading)
        TabStake.Unread -> words(R.string.vote_tab_stake_unread)
        is TabStake.Read ->
            if (raw <= 0L) words(R.string.vote_tab_stake_zero)
            else words(R.string.vote_tab_stake, skrWeight(BigInteger.valueOf(raw)))
    }

/**
 * When the round closes, in the reader's own zone and words ("Closes Monday 29 Sep at 02:00 your
 * time"), where the header used to print a UTC stamp nobody converts in their head. Null when the
 * server stamped no close, or one that does not parse.
 */
fun roundClosesLocal(round: VoteRound, zone: ZoneId): Copy? {
    val closes = round.closesAtInstant() ?: return null
    val millis = closes.toEpochMilli()
    val day = closes.atZone(zone).toLocalDate()
    return words(
        R.string.vote_tab_round_closes_local,
        Fmt.weekday(millis, zone),
        Fmt.dayMonth(day),
        Fmt.clock(millis, zone),
    )
}

/**
 * What the tab has to show right now.
 *
 * [failed] is set only for a first fetch that failed before anything was ever answered
 * ([VoteTabViewModel] never lets a later failure clear data a reader is already looking at), so it
 * and [notOpen] are the only two states that replace the round furniture; everywhere else the
 * furniture is whatever has successfully loaded, however partial.
 */
data class VoteTabUiState(
    val isLoading: Boolean = false,
    val failed: Boolean = false,
    val notOpen: Boolean = false,
    val round: VoteRound? = null,
    val previous: PreviousRoundDisplay? = null,
    val leaders: List<NextUpLeader> = emptyList(),
    val myVotes: List<VoteReceipt> = emptyList(),
    val query: String = "",
    /** Already filtered by [query]; the unfiltered set is not this screen's concern. */
    val ballot: List<BallotEntry> = emptyList(),
    val ballotLoaded: Boolean = false,
    /**
     * The catalog-and-summary join that builds the ballot threw, and it has never once
     * succeeded. [VoteTabViewModel.refresh] retries it exactly as it retries `next-up`, so the
     * screen's one Retry banner covers both rather than leaving the ballot on its skeleton
     * forever with nothing offering a second attempt.
     */
    val ballotFailed: Boolean = false,
    /** The connected wallet's stake, for the top card. */
    val stake: TabStake = TabStake.NoWallet,
) {
    /** The explainer is the one thing every state shows; everything below it needs this to be true. */
    val showsRoundFurniture: Boolean get() = !isLoading && !failed && !notOpen

    val searchMiss: Boolean get() = showsRoundFurniture && ballotLoaded && query.isNotBlank() && ballot.isEmpty()
}
