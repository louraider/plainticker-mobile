package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one "already voted this round" rule (device QA of 1.3.17: the Vote tab said Voted for AALx
 * while Stocks and Detail still offered Vote).
 */
class VotedInRoundTest {

    private val round = VoteRound(2, "2026-09-21T00:00:00Z", "2026-09-28T00:00:00Z")
    private val alice = "Alice1111111111111111111111111111111111111"
    private val bob = "Bob11111111111111111111111111111111111111"
    private val inside = Instant.parse("2026-09-27T09:39:00Z").toEpochMilli()
    private val before = Instant.parse("2026-09-20T12:00:00Z").toEpochMilli()

    private fun receipt(ticker: String, voter: String = alice, round: Int? = 2, at: Long = inside) =
        VoteReceipt("sig-$ticker-$voter-$round-$at", ticker, "${ticker}x", 1L, at, voter, round)

    @Test
    fun `a receipt stamped with the open round counts, another round does not`() {
        assertTrue(receipt("AAL").countsIn(round))
        assertFalse(receipt("AAL", round = 1).countsIn(round))
    }

    @Test
    fun `an unstamped receipt counts only when it landed inside the round's window`() {
        assertTrue("cast from Stocks or Detail during the round", receipt("AAL", round = null).countsIn(round))
        assertFalse("cast before the round opened", receipt("AAL", round = null, at = before).countsIn(round))
        assertFalse(
            "a window that cannot be read is never guessed at",
            receipt("AAL", round = null).countsIn(VoteRound(2, "", "")),
        )
    }

    @Test
    fun `voted tickers read the connected wallet's receipts, or every receipt with none connected`() {
        val receipts = listOf(receipt("AAL"), receipt("TSM", voter = bob), receipt("NKE", round = 1))
        assertEquals(setOf("AAL"), votedTickers(receipts, round, connectedVoter = alice))
        assertEquals(setOf("AAL", "TSM"), votedTickers(receipts, round, connectedVoter = null))
        assertTrue("no open round, nothing voted", votedTickers(receipts, null, alice).isEmpty())
    }

    @Test
    fun `the lookup ignores case, the way the server's join key does`() {
        val voted = votedTickers(listOf(receipt("aal")), round, alice)
        assertTrue(voted.hasVoted("AAL"))
        assertTrue(voted.hasVoted(" aal "))
        assertFalse(voted.hasVoted("AA"))
    }

    @Test
    fun `your votes and the voted marks read the same round rule`() {
        val receipts = listOf(receipt("AAL", round = null), receipt("TSM", round = 1))
        assertEquals(listOf("AAL"), myVotesFor(receipts, round, alice).map { it.ticker })
    }
}
