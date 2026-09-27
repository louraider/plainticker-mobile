package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import java.time.Instant

/**
 * The one rule for "this device already voted for this ticker in the round that is open", read by
 * every surface that offers a Vote: the Vote tab's leaders and ballot, Stocks' rows (a search
 * result, the "Without analysis" section, the Next up strip) and Detail's "Vote to cover next".
 *
 * **Why one rule** (device QA of 1.3.17). The Vote tab drew a quiet "Voted" for AALx while Stocks'
 * search row, the "Without analysis" section and AALx's own Detail still offered Vote, which the
 * server would only refuse (one vote per wallet per token per round). Each surface decided on its
 * own, and only the Vote tab read the receipts at all.
 *
 * A receipt counts for [round] when it was stamped with that round's id. A receipt with no round
 * (a vote cast from a surface that did not know the round, before [VoteViewModel] learned to stamp
 * it) counts when it landed inside the round's own window, and never when the window cannot be
 * read: a guess about which round a vote belonged to is not a fact to draw.
 */
fun VoteReceipt.countsIn(round: VoteRound): Boolean {
    if (this.round != null) return this.round == round.id
    val opens = parseInstant(round.opensAt) ?: return false
    val closes = parseInstant(round.closesAt)
    if (landedAtMillis < opens.toEpochMilli()) return false
    return closes == null || landedAtMillis < closes.toEpochMilli()
}

/**
 * The tickers, uppercase, that this device's receipts say were voted for in [round]. Empty with no
 * open round. With a wallet connected only that wallet's receipts count; with none connected
 * (every cold start, until the reader reconnects) every receipt this device holds does, the same
 * scoping "Your votes" keeps ([myVotesFor]).
 */
fun votedTickers(receipts: List<VoteReceipt>, round: VoteRound?, connectedVoter: String?): Set<String> {
    if (round == null) return emptySet()
    return receipts.asSequence()
        .filter { connectedVoter == null || it.voter == connectedVoter }
        .filter { it.countsIn(round) }
        .map { it.ticker.trim().uppercase() } // lint-allow uppercase: set key
        .toSet()
}

/** Whether [ticker] is in a set [votedTickers] built, ignoring case. */
fun Set<String>.hasVoted(ticker: String): Boolean = ticker.trim().uppercase() in this // lint-allow uppercase: set key

private fun parseInstant(text: String): Instant? =
    if (text.isBlank()) null else runCatching { Instant.parse(text) }.getOrNull()
