package com.plainticker.mobile.ui.vote

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.FakeVoteReceiptStore
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.wallet.FakeWalletSession
import com.plainticker.mobile.wallet.WalletAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The Vote tab's ViewModel: what each section shows, the not-open state (task A2, both HTTP
 * shapes read as one by the time they reach here), the ballot's own search, and the two scopes
 * "Your votes" (task A3) is read through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteTabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun catalogWith(vararg entries: Triple<String, String, String>) = FakeCatalogRepository(
        assets = Result.success(entries.map { (symbol, ticker, name) -> xStock(symbol, ticker, mint = "$ticker-mint", name = name) }),
    )

    private fun summaryWith(vararg tickers: String) = FakeSummaryRepository(
        summaryResult = Result.success(SummaryResponse("v1.1", "2026-09-18T00:00:00.000Z", rows = tickers.map { SummaryRow(ticker = it) })),
    )

    private val defaultCatalog = catalogWith(Triple("TSMx", "TSM", "Taiwan Semiconductor"), Triple("ASMLx", "ASML", "ASML Holding"))

    private fun viewModel(
        nextUp: FakeNextUpRepository = FakeNextUpRepository(),
        catalog: FakeCatalogRepository = defaultCatalog,
        summaries: FakeSummaryRepository = summaryWith(),
        receipts: FakeVoteReceiptStore = FakeVoteReceiptStore(),
        wallet: FakeWalletSession = FakeWalletSession(),
    ) = VoteTabViewModel(nextUp, catalog, summaries, receipts, wallet)

    // ---- State per section ------------------------------------------------------------------

    @Test
    fun `open with a round, leaders and a previous winner fills every section`() = runTest {
        val nextUp = FakeNextUpRepository(
            answer = Result.success(
                NextUpAnswer.Open(
                    rows = listOf(NextUpRow(ticker = "TSM", weight = "31209870777", voters = 3)),
                    round = VoteRound(2, "2026-09-15T00:00:00.000Z", "2026-09-22T00:00:00.000Z"),
                    previous = PreviousRound(id = 1, winner = "JEF", weight = "1", voters = 1, status = "published"),
                ),
            ),
        )
        val catalog = catalogWith(
            Triple("TSMx", "TSM", "Taiwan Semiconductor"),
            Triple("ASMLx", "ASML", "ASML Holding"),
            Triple("JEFx", "JEF", "Jefferies Financial Group"),
        )
        val model = viewModel(nextUp = nextUp, catalog = catalog, summaries = summaryWith("JEF"))

        model.state.test {
            awaitUntil { it.ballotLoaded && it.leaders.isNotEmpty() && it.previous != null }
            cancelAndIgnoreRemainingEvents()
        }

        val state = model.state.value
        assertFalse(state.isLoading)
        assertFalse(state.notOpen)
        assertEquals(2, state.round?.id)
        assertEquals(listOf("TSM"), state.leaders.map { it.ticker })
        assertEquals("JEFx", state.previous?.display)
        // JEF is covered (the summary now carries it), so it has left the ballot; TSM leads the
        // vote but has no analysis yet either, so it is still on the ballot beside ASML.
        assertEquals(setOf("ASML", "TSM"), state.ballot.map { it.ticker }.toSet())
    }

    @Test
    fun `not open replaces the round furniture, whichever HTTP shape it came from`() = runTest {
        val model = viewModel(nextUp = FakeNextUpRepository(answer = Result.success(NextUpAnswer.NotOpen)))
        model.state.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        // Read straight off the ViewModel rather than off what Turbine last delivered: a fast
        // burst of updates from independent sources can settle after the last delivered snapshot.
        val settled = model.state.value
        assertTrue(settled.notOpen)
        assertFalse(settled.showsRoundFurniture)
        assertEquals(null, settled.round)
        assertTrue(settled.leaders.isEmpty())
        assertTrue(settled.myVotes.isEmpty())
    }

    @Test
    fun `a first fetch that fails is a failed banner, not a blank screen`() = runTest {
        val model = viewModel(nextUp = FakeNextUpRepository(answer = Result.failure(IOException("down"))))
        model.state.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }
        val settled = model.state.value
        assertTrue(settled.failed)
        assertFalse(settled.showsRoundFurniture)
    }

    @Test
    fun `once something has loaded, a later failure keeps it rather than clearing it`() = runTest {
        val nextUp = FakeNextUpRepository(
            answer = Result.success(NextUpAnswer.Open(rows = emptyList(), round = VoteRound(1, "", ""), previous = null)),
        )
        val model = viewModel(nextUp = nextUp)
        model.state.test {
            awaitUntil { !it.isLoading }
            cancelAndIgnoreRemainingEvents()
        }

        nextUp.answer = Result.failure(IOException("down"))
        model.refresh()
        advanceUntilIdle()

        assertEquals(1, model.state.value.round?.id)
        assertFalse(model.state.value.failed)
    }

    // ---- The ballot's search ------------------------------------------------------------------

    @Test
    fun `the ballot's search narrows to the ticker, the symbol or the company`() = runTest {
        val model = viewModel()
        model.state.test {
            awaitUntil { it.ballotLoaded }
            model.search("asml")
            val searched = awaitUntil { it.query == "asml" }
            assertEquals(listOf("ASML"), searched.ballot.map { it.ticker })

            model.clearSearch()
            val cleared = awaitUntil { it.query.isEmpty() }
            assertEquals(setOf("TSM", "ASML"), cleared.ballot.map { it.ticker }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a search with no match is a search miss, once the ballot has loaded`() = runTest {
        val model = viewModel()
        model.state.test {
            awaitUntil { it.ballotLoaded }
            model.search("nvidia")
            val searched = awaitUntil { it.query == "nvidia" }
            assertTrue(searched.searchMiss)
            assertTrue(searched.ballot.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The round filter on "Your votes" (task A3) ------------------------------------------

    @Test
    fun `your votes narrows to the round in progress and to the connected wallet`() = runTest {
        val voter = WalletAccount(ByteArray(32) { 7 }, "Seeker")
        val wallet = FakeWalletSession().apply { connectedAs(voter) }
        val receipts = FakeVoteReceiptStore().apply {
            record(VoteReceipt("sig-1", "TSM", "TSMx", 1L, 1_000L, voter = voter.address, round = 2))
            record(VoteReceipt("sig-2", "ASML", "ASMLx", 1L, 2_000L, voter = voter.address, round = 1))
            record(VoteReceipt("sig-3", "NKE", "NKEx", 1L, 3_000L, voter = "someone-elses-wallet", round = 2))
        }
        val nextUp = FakeNextUpRepository(
            answer = Result.success(NextUpAnswer.Open(rows = emptyList(), round = VoteRound(2, "", ""), previous = null)),
        )
        val model = viewModel(nextUp = nextUp, receipts = receipts, wallet = wallet)

        model.state.test {
            // The round arrives with `refresh()`'s own answer; wait for it rather than only for
            // isLoading, since the receipts and wallet flows can settle "your votes" once before
            // the round is known and Turbine's last delivered value can lag the ViewModel's own.
            awaitUntil { !it.isLoading && it.round != null }
            cancelAndIgnoreRemainingEvents()
        }
        // Round 1's vote and a different wallet's round-2 vote are both excluded.
        assertEquals(listOf("TSM"), model.state.value.myVotes.map { it.ticker })
    }

    @Test
    fun `with no round known, every voter's own votes stand unscoped by round`() = runTest {
        val voter = WalletAccount(ByteArray(32) { 7 }, "Seeker")
        val wallet = FakeWalletSession().apply { connectedAs(voter) }
        val receipts = FakeVoteReceiptStore().apply {
            record(VoteReceipt("sig-1", "TSM", "TSMx", 1L, 1_000L, voter = voter.address, round = 1))
            record(VoteReceipt("sig-2", "ASML", "ASMLx", 1L, 2_000L, voter = voter.address, round = null))
        }
        // The minimum-slice case (plan section 2): the server sends no round at all.
        val nextUp = FakeNextUpRepository(
            answer = Result.success(NextUpAnswer.Open(rows = emptyList(), round = null, previous = null)),
        )
        val model = viewModel(nextUp = nextUp, receipts = receipts, wallet = wallet)

        model.state.test {
            awaitUntil { !it.isLoading && it.myVotes.size == 2 }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(setOf("TSM", "ASML"), model.state.value.myVotes.map { it.ticker }.toSet())
    }

    @Test
    fun `disconnecting the wallet widens your votes back to every receipt this device holds`() = runTest {
        val voter = WalletAccount(ByteArray(32) { 7 }, "Seeker")
        val wallet = FakeWalletSession().apply { connectedAs(voter) }
        val receipts = FakeVoteReceiptStore().apply {
            record(VoteReceipt("sig-1", "TSM", "TSMx", 1L, 1_000L, voter = voter.address, round = 1))
            record(VoteReceipt("sig-2", "ASML", "ASMLx", 1L, 2_000L, voter = "someone-elses-wallet", round = 1))
        }
        val nextUp = FakeNextUpRepository(
            answer = Result.success(NextUpAnswer.Open(rows = emptyList(), round = VoteRound(1, "", ""), previous = null)),
        )
        val model = viewModel(nextUp = nextUp, receipts = receipts, wallet = wallet)

        model.state.test {
            awaitUntil { !it.isLoading && it.myVotes.size == 1 }
            wallet.connectedAs(null)
            val widened = awaitUntil { it.myVotes.size == 2 }
            assertEquals(setOf("TSM", "ASML"), widened.myVotes.map { it.ticker }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
