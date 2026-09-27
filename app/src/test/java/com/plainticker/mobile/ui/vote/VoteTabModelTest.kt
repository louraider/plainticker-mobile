package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.plainticker.PreviousRoundStatus
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.data.xstocks.Deployment
import com.plainticker.mobile.data.xstocks.Underlying
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.ui.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * The Vote tab's pure decisions, off a plain JVM, exactly as [VoteSheetModelTest] holds the
 * sheet's: the ballot's search, the leaders' join against it, the previous round's naming, and
 * the two scopes "Your votes" is read through.
 */
class VoteTabModelTest {

    private fun asset(symbol: String, ticker: String, name: String, mint: String? = "$ticker-mint") = XStockAsset(
        name = name,
        symbol = symbol,
        underlying = Underlying(symbol = ticker, type = "Equity", listingCountry = "US"),
        deployments = mint?.let { listOf(Deployment(address = it, network = XStockAsset.NETWORK_SOLANA, supportsAtomicSwaps = true)) }.orEmpty(),
    )

    private fun ballot(vararg entries: Triple<String, String, String>) = entries.map { (ticker, symbol, company) -> BallotEntry(ticker, symbol, company) }

    // ---- The ballot's search --------------------------------------------------------------------

    @Test
    fun `a blank query is every ballot row, in the order it was given`() {
        val rows = ballot(Triple("TSM", "TSMx", "Taiwan Semiconductor"), Triple("ASML", "ASMLx", "ASML Holding"))
        assertEquals(rows, rows.matchingBallot(""))
        assertEquals(rows, rows.matchingBallot("   "))
    }

    @Test
    fun `the search matches the ticker, the symbol or the company, case insensitively`() {
        val rows = ballot(Triple("TSM", "TSMx", "Taiwan Semiconductor"), Triple("ASML", "ASMLx", "ASML Holding"))
        assertEquals(listOf("TSM"), rows.matchingBallot("tsm").map { it.ticker })
        assertEquals(listOf("TSM"), rows.matchingBallot("TSMX").map { it.ticker })
        assertEquals(listOf("ASML"), rows.matchingBallot("holding").map { it.ticker })
        assertTrue(rows.matchingBallot("nvidia").isEmpty())
    }

    // ---- The leaders, joined against the ballot --------------------------------------------------

    @Test
    fun `a leader the ballot can name is drawn, with the ballot's own display and company`() {
        val rows = listOf(NextUpRow(ticker = "tsm", weight = "38406150222", voters = 3))
        val entries = ballot(Triple("TSM", "TSMx", "Taiwan Semiconductor"))
        val leaders = leadersFor(rows, entries)
        assertEquals(1, leaders.size)
        assertEquals("TSM", leaders.single().ticker)
        assertEquals("TSMx", leaders.single().display)
        assertEquals("Taiwan Semiconductor", leaders.single().company)
        assertEquals(BigInteger("38406150222"), leaders.single().weightRaw)
        assertEquals(3, leaders.single().voters)
    }

    @Test
    fun `a leader the ballot cannot name is not drawn, and neither is one with no ballot at all`() {
        val rows = listOf(NextUpRow(ticker = "TSM", weight = "1", voters = 1))
        assertTrue("covered since the tally, or the ballot has not loaded yet", leadersFor(rows, emptyList()).isEmpty())
        assertTrue(leadersFor(rows, ballot(Triple("ASML", "ASMLx", "ASML Holding"))).isEmpty())
    }

    @Test
    fun `a weight that is not a plain number drops the row rather than guessing`() {
        val rows = listOf(NextUpRow(ticker = "TSM", weight = "not-a-number", voters = 1))
        assertTrue(leadersFor(rows, ballot(Triple("TSM", "TSMx", "Taiwan Semiconductor"))).isEmpty())
    }

    @Test
    fun `the leaders section carries no cap, unlike the list's three-row strip`() {
        val rows = (1..20).map { NextUpRow(ticker = "T$it", weight = "$it", voters = 1) }
        val entries = (1..20).map { BallotEntry("T$it", "T${it}x", "Company $it") }
        assertEquals(20, leadersFor(rows, entries).size)
    }

    // ---- The previous round --------------------------------------------------------------------

    @Test
    fun `null previous or no winner draws nothing`() {
        assertNull(previousDisplay(null, emptyMap()))
        assertNull(previousDisplay(PreviousRound(id = 0, winner = null, status = "published"), emptyMap()))
        assertNull(previousDisplay(PreviousRound(id = 0, winner = "  ", status = "closed"), emptyMap()))
    }

    /**
     * Changed 2026-09-26 (audit, item 2): an unrecognized status used to draw nothing, which is
     * how the live server's `closed` hid "Last round" for everyone. It now draws the neutral line.
     */
    @Test
    fun `an unrecognized status still draws the neutral closed line, not nothing`() {
        val display = requireNotNull(previousDisplay(PreviousRound(id = 3, winner = "JEF", status = "in_progress"), emptyMap()))
        assertEquals(PreviousRoundStatus.UNRECOGNIZED, display.status)
        assertEquals(Copy.Words(R.string.vote_tab_last_round_closed, listOf("3", "JEF")), display.sentence)
    }

    @Test
    fun `the live closed round names its number and winner, and claims nothing about coverage`() {
        val catalog = mapOf("JEF" to asset("JEFx", "JEF", "Jefferies Financial Group"))
        // The live previous block, read 2026-09-26.
        val previous = PreviousRound(
            id = 1, winner = "JEF", weight = "878980647", voters = 1,
            closedAt = "2026-09-21T00:00:03.697Z", status = "closed",
        )
        val display = requireNotNull(previousDisplay(previous, catalog))
        assertEquals(PreviousRoundStatus.CLOSED, display.status)
        assertEquals(1, display.roundId)
        assertEquals(Copy.Words(R.string.vote_tab_last_round_closed, listOf("1", "JEFx")), display.sentence)
        assertEquals("21 Sep 2026 00:00 UTC", display.closedAtText)
    }

    @Test
    fun `the three tally words keep their own sentences`() {
        fun sentence(status: String) = requireNotNull(previousDisplay(PreviousRound(id = 1, winner = "JEF", status = status), emptyMap())).sentence
        assertEquals(Copy.Words(R.string.vote_tab_last_round_pending, listOf("JEF")), sentence("pending"))
        assertEquals(Copy.Words(R.string.vote_tab_last_round_published, listOf("JEF")), sentence("published"))
        assertEquals(Copy.Words(R.string.vote_tab_last_round_uncoverable, listOf("JEF")), sentence("uncoverable"))
    }

    @Test
    fun `a resolvable winner is named by its symbol and company, from the catalog and not the ballot`() {
        val catalog = mapOf("JEF" to asset("JEFx", "JEF", "Jefferies Financial Group"))
        val previous = PreviousRound(
            id = 0, winner = "jef", weight = "18500000000", voters = 4,
            closedAt = "2026-09-15T00:00:00.000Z", status = "published",
        )
        val display = requireNotNull(previousDisplay(previous, catalog))
        assertEquals("jef", display.ticker)
        assertEquals("JEFx", display.display)
        assertEquals("Jefferies Financial Group", display.company)
        assertEquals(PreviousRoundStatus.PUBLISHED, display.status)
        assertEquals(BigInteger("18500000000"), display.weightRaw)
        assertEquals(4, display.voters)
        assertEquals("15 Sep 2026 00:00 UTC", display.closedAtText)
    }

    @Test
    fun `a winner the catalog does not carry falls back to the bare ticker`() {
        val previous = PreviousRound(id = 0, winner = "JEF", status = "pending")
        val display = requireNotNull(previousDisplay(previous, emptyMap()))
        assertEquals("JEF", display.display)
        assertNull(display.company)
    }

    // ---- Your votes: scoped by voter, then by round ----------------------------------------------

    private fun receipt(ticker: String, voter: String, round: Int?, landedAtMillis: Long = 1_000L) = VoteReceipt(
        signature = "$ticker-$voter-$round-$landedAtMillis",
        ticker = ticker,
        symbol = "${ticker}x",
        weightRaw = 1L,
        landedAtMillis = landedAtMillis,
        voter = voter,
        round = round,
    )

    private val alice = "alice-wallet"
    private val bob = "bob-wallet"

    @Test
    fun `with a wallet connected, only that wallet's receipts are yours`() {
        val receipts = listOf(receipt("TSM", alice, round = 1), receipt("ASML", bob, round = 1))
        val mine = myVotesFor(receipts, VoteRound(1, "", ""), connectedVoter = alice)
        assertEquals(listOf("TSM"), mine.map { it.ticker })
    }

    @Test
    fun `with no wallet connected, every receipt this device holds stands`() {
        val receipts = listOf(receipt("TSM", alice, round = 1), receipt("ASML", bob, round = 1))
        val mine = myVotesFor(receipts, VoteRound(1, "", ""), connectedVoter = null)
        assertEquals(setOf("TSM", "ASML"), mine.map { it.ticker }.toSet())
    }

    @Test
    fun `the round filter narrows to the round in progress, a different round is not yours here`() {
        val receipts = listOf(
            receipt("TSM", alice, round = 1),
            receipt("ASML", alice, round = 2),
            receipt("NKE", alice, round = null),
        )
        val mine = myVotesFor(receipts, VoteRound(2, "", ""), connectedVoter = alice)
        assertEquals(listOf("ASML"), mine.map { it.ticker })
    }

    @Test
    fun `no round at all is the minimum-slice case, and nothing is scoped to a round that does not exist`() {
        val receipts = listOf(receipt("TSM", alice, round = 1), receipt("ASML", alice, round = null))
        val mine = myVotesFor(receipts, round = null, connectedVoter = alice)
        assertEquals(setOf("TSM", "ASML"), mine.map { it.ticker }.toSet())
    }

    @Test
    fun `your votes read back newest first`() {
        val receipts = listOf(
            receipt("TSM", alice, round = 1, landedAtMillis = 1_000L),
            receipt("ASML", alice, round = 1, landedAtMillis = 3_000L),
            receipt("NKE", alice, round = 1, landedAtMillis = 2_000L),
        )
        val mine = myVotesFor(receipts, VoteRound(1, "", ""), connectedVoter = alice)
        assertEquals(listOf("ASML", "NKE", "TSM"), mine.map { it.ticker })
    }

    @Test
    fun `a ticker this wallet voted for in the open round reads as voted, others and no round do not`() {
        val round = VoteRound(2, "", "")
        val mine = myVotesFor(listOf(receipt("AAL", alice, round = 2)), round, connectedVoter = alice)
        val state = VoteTabUiState(round = round, myVotes = mine)
        assertTrue(state.votedFor("AAL"))
        assertTrue("the ticker match ignores case", state.votedFor("aal"))
        assertFalse(state.votedFor("TSM"))
        assertFalse(
            "with no open round the receipts are not scoped to one, so nothing reads as voted",
            VoteTabUiState(round = null, myVotes = mine).votedFor("AAL"),
        )
    }

    // ---- The state's own computed properties -----------------------------------------------------

    @Test
    fun `the round furniture shows only once settled, open and not failed`() {
        assertTrue(VoteTabUiState().showsRoundFurniture)
        assertTrue(VoteTabUiState(isLoading = false, failed = false, notOpen = false).showsRoundFurniture)
        assertTrue(VoteTabUiState(isLoading = true).showsRoundFurniture.not())
        assertTrue(VoteTabUiState(failed = true).showsRoundFurniture.not())
        assertTrue(VoteTabUiState(notOpen = true).showsRoundFurniture.not())
    }

    @Test
    fun `a search miss needs the ballot to have loaded, a query, and nothing to show for it`() {
        assertTrue(VoteTabUiState(ballotLoaded = true, query = "zzz", ballot = emptyList()).searchMiss)
        assertTrue(VoteTabUiState(ballotLoaded = false, query = "zzz", ballot = emptyList()).searchMiss.not())
        assertTrue(VoteTabUiState(ballotLoaded = true, query = "", ballot = emptyList()).searchMiss.not())
        assertTrue(VoteTabUiState(notOpen = true, ballotLoaded = true, query = "zzz").searchMiss.not())
    }

    // ---- The top card ----------------------------------------------------------------------------

    @Test
    fun `the stake sentence names the figure only when one was read`() {
        fun say(stake: TabStake) = com.plainticker.mobile.ui.ShippedCopy.render(stake.sentence)
        assertEquals("This wallet has 38,406.2 SKR staked. That is the weight each vote from it carries.", say(TabStake.Read(38_406_150_222L)))
        assertEquals("This wallet has no staked SKR, so a vote from it carries no weight.", say(TabStake.Read(0L)))
        assertEquals(
            "Connect a wallet with staked SKR to vote. Your stake is the weight your vote carries.",
            say(TabStake.NoWallet),
        )
        listOf(TabStake.Reading, TabStake.Unread, TabStake.NoWallet).forEach { state ->
            assertTrue("$state prints no figure", say(state).none { it.isDigit() })
        }
    }

    @Test
    fun `the round closes in the reader's own zone and words`() {
        val round = VoteRound(id = 3, opensAt = "2026-09-21T00:00:00.000Z", closesAt = "2026-09-28T00:00:00.000Z")
        val berlin = roundClosesLocal(round, java.time.ZoneId.of("Europe/Berlin"))!!
        assertEquals("Closes Monday 28 Sep at 02:00 your time", com.plainticker.mobile.ui.ShippedCopy.render(berlin))
        val newYork = roundClosesLocal(round, java.time.ZoneId.of("America/New_York"))!!
        assertEquals("Closes Sunday 27 Sep at 20:00 your time", com.plainticker.mobile.ui.ShippedCopy.render(newYork))
        assertEquals(null, roundClosesLocal(round.copy(closesAt = "not-a-date"), java.time.ZoneOffset.UTC))
    }
}
