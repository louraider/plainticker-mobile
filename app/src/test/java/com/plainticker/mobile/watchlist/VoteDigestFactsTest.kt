package com.plainticker.mobile.watchlist

import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.NextUpAnswer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The vote line's own facts, read live rather than off the server's `status` word (which the live
 * contract still sends as `closed` for every previous round): a winner is "analysed" exactly when
 * `/api/v1/{ticker}` serves it, and never when a lookup fails or there is nothing to look up.
 */
class VoteDigestFactsTest {

    private val round = VoteRound(id = 2, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")

    @Test
    fun `a previous winner that answers is the analysed winner`() = runTest {
        val nextUp = FakeNextUpRepository(
            answer = Result.success(
                NextUpAnswer.Open(rows = emptyList(), round = round, previous = PreviousRound(id = 1, winner = "JEF", status = "closed")),
            ),
        )
        val summaries = FakeSummaryRepository(analyses = mapOf("JEF" to Result.success(AnalysisPayload(ticker = "JEF"))))

        val facts = VoteDigestFacts(nextUp, summaries).load()

        assertEquals(round, facts.round)
        assertEquals("JEF", facts.analysedWinner)
    }

    @Test
    fun `a previous winner the analysis endpoint does not yet serve is not named`() = runTest {
        val nextUp = FakeNextUpRepository(
            answer = Result.success(
                NextUpAnswer.Open(rows = emptyList(), round = round, previous = PreviousRound(id = 1, winner = "JEF", status = "closed")),
            ),
        )
        // FakeSummaryRepository throws a 404 for a ticker with no configured analysis, exactly the
        // shape the live analysis route answers with for a ticker it does not yet serve.
        val facts = VoteDigestFacts(nextUp, FakeSummaryRepository()).load()

        assertEquals(round, facts.round)
        assertNull(facts.analysedWinner)
    }

    @Test
    fun `no previous round names no winner, but the round in progress still comes through`() = runTest {
        val nextUp = FakeNextUpRepository(
            answer = Result.success(NextUpAnswer.Open(rows = emptyList(), round = round, previous = null)),
        )
        val facts = VoteDigestFacts(nextUp, FakeSummaryRepository()).load()

        assertEquals(round, facts.round)
        assertNull(facts.analysedWinner)
    }

    @Test
    fun `voting not open at all is the same silence as a source that failed`() = runTest {
        val nextUp = FakeNextUpRepository(answer = Result.success(NextUpAnswer.NotOpen))
        assertEquals(VoteFacts.NONE, VoteDigestFacts(nextUp, FakeSummaryRepository()).load())
    }

    @Test
    fun `a next-up call that fails outright leaves the vote line silent, not the whole check`() = runTest {
        val nextUp = FakeNextUpRepository(answer = Result.failure(IllegalStateException("no network")))
        assertEquals(VoteFacts.NONE, VoteDigestFacts(nextUp, FakeSummaryRepository()).load())
    }
}
