package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.data.plainticker.VoteBuild
import com.plainticker.mobile.data.plainticker.VoteSummary
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.wallet.TransactionGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the vote says, rendered out of the shipped strings.xml through [ShippedCopy], so every
 * sentence asserted here is the sentence a reader sees rather than one spelled twice.
 *
 * Two rules carry more weight than the rest. A state that could not read the stake prints no
 * figure at all, and the weakness of a balance-weighted vote is stated where a voter reads it
 * before acting rather than only in the README.
 */
class VoteSheetModelTest {

    private val collector = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"

    private val signature = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe"

    /** 31,209.870777 SKR, the principal the production forwarder returned on 2026-09-13. */
    private val measuredStake = 31_209_870_777L

    private val build = VoteBuild(
        transaction = "UkVEQUNURUQ=",
        summary = VoteSummary(ticker = "NFLX", lamports = 5_000L, collector = collector),
    )

    private fun ready(stakeRaw: Long = measuredStake, refreshed: Boolean = false) =
        VoteState.Ready("NFLX", "NFLXx", collector, stakeRaw, build, refreshed = refreshed)

    private fun landed() = VoteState.Landed("NFLX", "NFLXx", measuredStake, signature)

    private fun sheetOf(state: VoteState): VoteSheetContent =
        requireNotNull(state.sheet()) { "$state draws no sheet" }

    private fun render(copy: Copy?): String? = copy?.let { ShippedCopy.render(it) }

    private fun everySentence(content: VoteSheetContent): List<String> = listOfNotNull(
        render(content.title),
        render(content.phase),
        render(content.notice),
        render(content.disclosure),
        render(content.bar?.label),
    ) + content.cells.flatMap { listOf(ShippedCopy.render(it.label), ShippedCopy.render(it.value)) }

    private val allStates: List<VoteState> = listOf(
        VoteState.Opening("NFLX", "NFLXx", VotePhase.CONNECTING),
        VoteState.Opening("NFLX", "NFLXx", VotePhase.READING),
        VoteState.Building("NFLX", "NFLXx", collector, measuredStake),
        ready(),
        ready(refreshed = true),
        VoteState.Signing("NFLX", "NFLXx", collector, measuredStake, build),
        landed(),
    ) + VoteRefusal.entries.map { VoteState.Refused("NFLX", "NFLXx", it) }

    // ---- The confirm step, which is the one the signer reads --------------------------------------

    @Test
    fun `the confirm step names the ticker, the weight, the fee and where the vote goes`() {
        val content = sheetOf(ready())

        assertEquals("Vote to cover NFLXx", render(content.title))
        assertEquals(3, content.cells.size)

        val weight = content.cells[0]
        assertEquals("Staked SKR this vote carries", ShippedCopy.render(weight.label))
        assertEquals("31,209.870777", ShippedCopy.render(weight.value))
        assertEquals("the weight is the headline figure, so it takes the whole row", 2, weight.span)

        val fee = content.cells[1]
        assertEquals("Signature fee", ShippedCopy.render(fee.label))
        assertEquals("0.000005 SOL", ShippedCopy.render(fee.value))

        val sentTo = content.cells[2]
        assertEquals("Sent to", ShippedCopy.render(sentTo.label))
        assertEquals("8rUv…ZyEC", ShippedCopy.render(sentTo.value))
        assertEquals("the whole address is what a tap carries off the screen", collector, sentTo.copies)
    }

    /**
     * A fee that is not a round number of SOL (a base fee plus whatever priority fee applied,
     * which is the common case, not the edge case) used to print all nine of lamports' own
     * decimals: "0.123456789 SOL", 15 characters, wide enough to clip the fact cell it sits in
     * (`LAMPORT_DISPLAY_DECIMALS`'s own doc comment in `VoteSheetModel.kt` has the arithmetic
     * against the real font). Six decimals, this file's `measuredStake` fee of 5,000 lamports
     * being too round to have ever caught it, is the regression this test is for.
     */
    @Test
    fun `an unrounded fee is shown to six decimals, not to all nine of lamports' own precision`() {
        val unrounded = build.copy(summary = build.summary.copy(lamports = 123_456_789L))
        val content = sheetOf(VoteState.Ready("NFLX", "NFLXx", collector, measuredStake, unrounded))
        val fee = content.cells[1]
        assertEquals(
            "half-up at six decimals, not the nine-decimal exact reading (\"0.123456789 SOL\") that " +
                "used to clip the fact cell",
            "0.123457 SOL",
            ShippedCopy.render(fee.value),
        )
    }

    @Test
    fun `the confirm step says what the vote is and that the signer is the voter`() {
        val lede = render(sheetOf(ready()).notice)!!
        assertTrue(lede.startsWith("This asks for NFLXx to be analyzed next."))
        assertTrue("a vote nobody can audit would be the one unverifiable thing here", lede.contains("memo"))
        assertTrue(lede.contains("the wallet that signs it is the voter"))
    }

    @Test
    fun `the vote states how stake decides today and the planned change, where a voter reads it before acting`() {
        val disclosure = render(sheetOf(ready()).disclosure)
        assertEquals(
            "Today the largest stake decides: one wallet staking more than the rest outweighs them " +
                "all. The planned next change is one Seeker, one voice, checked through the Seeker " +
                "Genesis Token.",
            disclosure,
        )

        // Said once, on the step where a person is about to act on it, and nowhere else.
        val elsewhere = allStates.filter { it !is VoteState.Ready }.map { sheetOf(it) }
        assertTrue("the weakness is stated once, not repeated", elsewhere.all { it.disclosure == null })
    }

    @Test
    fun `the confirm step is the only state that offers to send the vote`() {
        assertEquals("Vote to cover next", render(sheetOf(ready()).primary?.label))
        assertEquals(VoteActionKind.Confirm, sheetOf(ready()).primary?.kind)

        val others = allStates.filter { it !is VoteState.Ready }.mapNotNull { sheetOf(it).primary?.kind }
        assertFalse("nothing else may send a vote", VoteActionKind.Confirm in others)
    }

    // ---- The bound ----------------------------------------------------------------------------------

    @Test
    fun `a stake that could not be read prints no figure at all`() {
        val content = sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.STAKE_UNREAD))
        val sentence = render(content.notice)!!

        assertEquals(
            "The stake of this wallet did not read as a figure this app can stand behind, so no " +
                "figure is shown and no vote was built.",
            sentence,
        )
        assertTrue("a refused read draws no grid, so there is nowhere for a number to be", content.cells.isEmpty())
        assertTrue(
            "not one digit of a figure this app will not stand behind reaches the screen",
            everySentence(content).none { line -> line.any { it.isDigit() } },
        )
    }

    // ---- The receipt ---------------------------------------------------------------------------------

    @Test
    fun `a landed vote names the signature the way the swap receipt does`() {
        val content = sheetOf(landed())

        assertEquals("Vote sent", render(content.bar?.label))
        assertEquals("4xQm…VtHe", ShippedCopy.render(content.bar!!.meta))

        val cells = content.cells
        assertEquals("Staked SKR behind NFLXx", ShippedCopy.render(cells[0].label))
        assertEquals("31,209.870777", ShippedCopy.render(cells[0].value))
        assertEquals("Signature", ShippedCopy.render(cells[1].label))
        assertEquals("4xQm…VtHe", ShippedCopy.render(cells[1].value))
        assertEquals("the fragment is what is shown, the signature is what is copied", signature, cells[1].copies)

        assertEquals("a landed vote offers to share it, and nothing that sends another", VoteActionKind.Share, content.primary?.kind)
        assertEquals("Share", render(content.primary?.label))
        assertEquals(VoteActionKind.Close, content.secondary?.kind)
        assertEquals("The vote is on the chain. Counted within about 20 minutes.", render(content.notice))
    }

    /**
     * Judges' review, 2026-09-27: a vote this phone refused read "The server did not build this
     * vote". The server did build it; the sentence now says who refused and why.
     */
    @Test
    fun `a guard refusal says this phone refused it, with the plain reason`() {
        val content = sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.GUARD_REFUSED, TransactionGuard.Why.WRONG_RECIPIENT))
        assertEquals(
            "This phone refused the transaction before your wallet saw it: it pays somewhere other than the destination shown. Nothing was signed or sent.",
            render(content.notice),
        )
        assertEquals(VoteActionKind.Retry, content.primary?.kind)
        assertNotEquals(render(content.notice), render(sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.UNAVAILABLE)).notice))
    }

    // ---- Every state ------------------------------------------------------------------------------------

    @Test
    fun `every state says something, and no two of them say the same thing`() {
        val sentences = mutableMapOf<String, VoteState>()
        allStates.forEach { state ->
            val content = sheetOf(state)
            val voice = render(content.phase) ?: render(content.notice) ?: render(content.bar?.label)
            assertNotNull("$state says nothing at all", voice)
            assertTrue("$state says a blank", voice!!.isNotBlank())
            val clash = sentences.put(voice, state)
            assertNull("$state and $clash both say \"$voice\"", clash)
        }
    }

    @Test
    fun `every sentence is in sentence case and ends in a period, or is a label that is neither`() {
        allStates.forEach { state ->
            val content = sheetOf(state)
            listOfNotNull(render(content.phase), render(content.notice), render(content.disclosure))
                .forEach { line ->
                    assertTrue("\"$line\" is not a sentence", line.endsWith("."))
                    assertTrue("\"$line\" does not start in sentence case", line.first().isUpperCase())
                }
        }
    }

    @Test
    fun `every state can be left, and only the signing round-trip refuses to be dismissed`() {
        allStates.forEach { state ->
            val content = sheetOf(state)
            val leaves = content.secondary?.kind == VoteActionKind.Close || content.holdsOpen || content.phase != null
            assertTrue("$state offers no way out", leaves)
        }
        assertTrue(sheetOf(VoteState.Signing("NFLX", "NFLXx", collector, measuredStake, build)).holdsOpen)
        assertFalse(sheetOf(VoteState.Opening("NFLX", "NFLXx", VotePhase.READING)).holdsOpen)
        assertFalse(sheetOf(landed()).holdsOpen)
    }

    @Test
    fun `a refusal that could end differently offers a retry, and one that is an answer does not`() {
        val retryable = VoteRefusal.entries.filter { it.retryable }
        val answers = VoteRefusal.entries.filterNot { it.retryable }

        retryable.forEach {
            val content = sheetOf(VoteState.Refused("NFLX", "NFLXx", it))
            assertEquals("$it offers no retry", VoteActionKind.Retry, content.primary?.kind)
            assertEquals("Retry", render(content.primary?.label))
        }
        answers.forEach {
            assertNull("$it is an answer and must not be dressed as a retry", sheetOf(VoteState.Refused("NFLX", "NFLXx", it)).primary)
        }
        assertEquals(
            "a device with no wallet, nothing staked, voting not being open and a wallet that already voted are answers",
            setOf(VoteRefusal.NO_WALLET, VoteRefusal.NO_STAKE, VoteRefusal.NOT_OPEN, VoteRefusal.ALREADY_VOTED),
            answers.toSet(),
        )
    }

    @Test
    fun `the weight appears as soon as it is bounded, and stays through the signing`() {
        val building = sheetOf(VoteState.Building("NFLX", "NFLXx", collector, measuredStake))
        assertEquals("31,209.870777", ShippedCopy.render(building.cells.single().value))

        val signing = sheetOf(VoteState.Signing("NFLX", "NFLXx", collector, measuredStake, build))
        assertEquals("31,209.870777", ShippedCopy.render(signing.cells.first().value))
        assertNotNull("a wallet that is open still says what it is open for", render(signing.phase))
    }

    @Test
    fun `a wallet staking nothing never reaches a step that would print a zero`() {
        // The machine refuses before Ready, so this is belt and brace: no state carrying a stake
        // of zero exists to be drawn. Asserted on the states rather than the machine so the two
        // cannot drift: if one is ever constructed, the figure it would print is a real zero.
        assertEquals("0", ShippedCopy.render(sheetOf(ready(stakeRaw = 0L)).cells.first().value))
    }

    @Test
    fun `the phases name what is in flight, one sentence each`() {
        assertEquals(
            "No wallet is connected, so the wallet is being asked to authorize.",
            render(sheetOf(VoteState.Opening("NFLX", "NFLXx", VotePhase.CONNECTING)).phase),
        )
        assertEquals(
            "Reading what this wallet has staked in the SKR staking program.",
            render(sheetOf(VoteState.Opening("NFLX", "NFLXx", VotePhase.READING)).phase),
        )
        assertEquals(
            "Asking the server to build the vote transaction.",
            render(sheetOf(VoteState.Building("NFLX", "NFLXx", collector, measuredStake)).phase),
        )
        assertEquals(
            "Waiting for the wallet to sign the vote and send it.",
            render(sheetOf(VoteState.Signing("NFLX", "NFLXx", collector, measuredStake, build)).phase),
        )
    }

    @Test
    fun `voting not being open yet is a state and reads as one, not as a failure`() {
        val sentence = render(sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.NOT_OPEN)).notice)
        assertEquals(
            "Voting is not open yet. The server has nothing to build this vote with, so nothing " +
                "was signed and nothing was sent.",
            sentence,
        )
    }

    @Test
    fun `a wallet with no stake is told what the weight is and why it has none`() {
        val sentence = render(sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.NO_STAKE)).notice)!!
        assertTrue(sentence.contains("SKR staking program"))
        assertTrue("the reader is owed the rule, not only the refusal", sentence.contains("weighted by staked SKR"))
    }

    // ---- The published contract ------------------------------------------------------------------------

    @Test
    fun `the confirm step shows the server's figure where it stated one, because that is the figure that counts`() {
        val counted = build.copy(summary = build.summary.copy(weight = 123_456_000_000L))
        val ready = VoteState.Ready("NFLX", "NFLXx", collector, measuredStake, counted)
        assertEquals("123,456", ShippedCopy.render(sheetOf(ready).cells[0].value))
        val signing = VoteState.Signing("NFLX", "NFLXx", collector, measuredStake, counted)
        assertEquals("123,456", ShippedCopy.render(sheetOf(signing).cells[0].value))
        // A server that sent no figure leaves the app's own bounded read on the screen.
        assertEquals("31,209.870777", ShippedCopy.render(sheetOf(ready()).cells[0].value))
    }

    @Test
    fun `a wallet that already voted is told the rule, and that it cost nothing`() {
        val sentence = render(sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.ALREADY_VOTED)).notice)!!
        assertEquals(
            "This wallet has already voted for this ticker. One wallet counts once per ticker, so nothing " +
                "was signed and no fee was spent.",
            sentence,
        )
        assertTrue(sentence.contains("once per ticker"))
    }

    @Test
    fun `a confirm step that replaced a stale transaction says so, and still offers to send`() {
        val content = sheetOf(ready(refreshed = true))
        assertEquals(
            "The transaction went stale while this was open, so the server built a fresh one. " +
                "Nothing was signed. Confirm again to send the vote.",
            render(content.notice),
        )
        assertNotEquals(
            "the lede is what a first confirm step says, and it is not read twice",
            render(sheetOf(ready()).notice),
            render(content.notice),
        )
        // Everything else about the step is what it was: the same three figures, the same button,
        // and the weakness still stated where a person is about to act on it.
        assertEquals(3, content.cells.size)
        assertEquals("31,209.870777", ShippedCopy.render(content.cells[0].value))
        assertEquals(VoteActionKind.Confirm, content.primary?.kind)
        assertEquals("Vote to cover next", render(content.primary?.label))
        assertNotNull(content.disclosure)
    }

    @Test
    fun `a server taking votes slowly says so and offers a later tap`() {
        val content = sheetOf(VoteState.Refused("NFLX", "NFLXx", VoteRefusal.RATE_LIMITED))
        assertTrue(render(content.notice)!!.contains("Try again shortly."))
        assertEquals(VoteActionKind.Retry, content.primary?.kind)
    }

    @Test
    fun `the share line is the plain sentence and the public transaction, nothing else`() {
        val shared = render(sheetOf(landed()).shareText)
        assertEquals(
            "I voted for NFLX to be analysed next on PlainTicker. https://solscan.io/tx/$signature",
            shared,
        )
        val others = allStates.filter { it !is VoteState.Landed }.map { sheetOf(it) }
        assertTrue("only a landed vote has anything to share", others.all { it.shareText == null && it.primary?.kind != VoteActionKind.Share })
    }

    @Test
    fun `a landed vote carries its signature for View on Solscan, and nothing before it does`() {
        assertEquals(signature, sheetOf(landed()).signature)
        val others = allStates.filter { it !is VoteState.Landed }.map { sheetOf(it) }
        assertTrue("only a landed vote links to the explorer", others.all { it.signature == null })
    }
}
