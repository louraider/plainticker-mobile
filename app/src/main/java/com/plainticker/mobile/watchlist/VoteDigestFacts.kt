package com.plainticker.mobile.watchlist

import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.SummaryRepository
import kotlinx.coroutines.CancellationException

/**
 * What the vote line of the daily digest needs: the round in progress, and, once this run has
 * confirmed it live, the previous round's winner.
 *
 * "Confirmed it live" is the whole reason this exists rather than reading [VoteRound] and
 * [com.plainticker.mobile.data.plainticker.PreviousRound.status] alone: the live server still
 * sends `closed` for every previous round (`PreviousRoundStatus`'s own doc, read 2026-09-26), never
 * `published`, so a winner that already has an analysis would otherwise never be told apart from
 * one still waiting for it. This asks the one question the status word cannot answer: does
 * `/api/v1/{ticker}` serve that ticker today. It never throws, the same rule [WatchlistFacts]
 * follows for its own sources, because the vote line is the one clause in the digest that is
 * allowed to simply be absent.
 */
data class VoteFacts(
    val round: VoteRound? = null,
    /** The previous round's winner, or null when there is none, or none this run could confirm. */
    val analysedWinner: String? = null,
) {
    companion object {
        val NONE = VoteFacts()
    }
}

class VoteDigestFacts(
    private val nextUp: NextUpRepository,
    private val summaries: SummaryRepository,
) {
    suspend fun load(): VoteFacts {
        val answer = try {
            nextUp.current() as? NextUpAnswer.Open ?: return VoteFacts.NONE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return VoteFacts.NONE
        }
        val winner = answer.previous?.winner?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } // lint-allow uppercase: map key
        val analysed = winner?.takeIf { ticker ->
            try {
                summaries.analysis(ticker)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                false
            }
        }
        return VoteFacts(round = answer.round, analysedWinner = analysed)
    }
}
