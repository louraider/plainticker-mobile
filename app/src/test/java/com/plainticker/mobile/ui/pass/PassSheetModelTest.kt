package com.plainticker.mobile.ui.pass

import com.plainticker.mobile.data.plainticker.PassBuild
import com.plainticker.mobile.data.plainticker.PassSummary
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.refusal
import com.plainticker.mobile.wallet.TransactionGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the pay sheet's fact grid says, the same pure-function split
 * [com.plainticker.mobile.ui.vote.VoteSheetModelTest] pins for the vote sheet
 * [readyCells] shares a shape with.
 *
 * The one thing this file exists to pin: the signature fee prints to six decimals, not to all
 * nine of lamports' own on-chain precision ([LAMPORT_DISPLAY_DECIMALS]'s own doc comment has the
 * arithmetic against the real font and the real `FactGrid` cell this value sits in). A fee that
 * happens to be a round number of SOL, like the 5,000-lamport fee this file's own preview data
 * used, never exercised the bug; a fee that is not is the regression case below.
 */
class PassSheetModelTest {

    private val payer = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"
    private val destination = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
    private val treasury = "E1STBTGEYpHanVG4HWUJHfzEu6eGbnE9mnGX9KnAmJdL"

    private fun build(lamports: Long) = PassBuild(
        transaction = "UkVEQUNURUQ=",
        summary = PassSummary(
            mint = "USDC",
            amount = 12_000_000L,
            destination = destination,
            treasury = treasury,
            lamports = lamports,
        ),
    )

    private fun render(copy: Copy?): String? = copy?.let { ShippedCopy.render(it) }

    private fun feeCellOf(lamports: Long): PassCell {
        val content = requireNotNull(PassState.Ready(payer, build(lamports)).sheet()) { "Ready draws no sheet" }
        return content.cells[1]
    }

    @Test
    fun `a round fee prints exactly, which is why this case alone never caught the clip`() {
        assertEquals("Signature fee", render(feeCellOf(5_000L).label))
        assertEquals("0.000005 SOL", render(feeCellOf(5_000L).value))
    }

    /**
     * A fee that is not a round number of SOL (a base fee plus whatever priority fee applied, the
     * common case) used to print all nine of lamports' own decimals: "0.123456789 SOL", 15
     * characters, wide enough to clip the two-across `FactGrid` cell it sits in beside the
     * destination cell. Rounded half-up to six decimals instead: "0.123457 SOL", 12 characters,
     * inside the budget the doc comment on `LAMPORT_DISPLAY_DECIMALS` derives from the real font.
     */
    @Test
    fun `an unrounded fee is shown to six decimals, not to all nine of lamports' own precision`() {
        assertEquals(
            "half-up at six decimals, not the nine-decimal exact reading (\"0.123456789 SOL\") that " +
                "used to clip the fact cell",
            "0.123457 SOL",
            render(feeCellOf(123_456_789L).value),
        )
    }

    @Test
    fun `the destination is labelled as pinned in this app, across the whole row`() {
        val cell = requireNotNull(PassState.Ready(payer, build(5_000L)).sheet()).cells[2]
        assertEquals("Sent to, pinned in this app", render(cell.label))
        assertEquals(2, cell.span)
        assertEquals(destination, cell.copies)
    }

    /**
     * Judges' review, 2026-09-27: a transfer this phone refused read "The server did not build this
     * payment". The server did build it; the sentence now says who refused and why, in plain words.
     */
    @Test
    fun `a guard refusal says this phone refused it, with the plain reason, and offers a retry`() {
        val refused = requireNotNull(PassState.Refused(PassRefusal.GUARD_REFUSED, TransactionGuard.Why.WRONG_RECIPIENT).sheet())
        assertEquals(
            "This phone refused the transaction before your wallet saw it: it pays somewhere other than the destination shown. Nothing was signed or sent.",
            render(refused.notice),
        )
        assertEquals(PassActionKind.Retry, refused.primary?.kind)
        assertEquals("the retry wording every wallet flow shares", "Try again", render(refused.primary?.label))
        val price = requireNotNull(PassState.Refused(PassRefusal.GUARD_REFUSED, TransactionGuard.Why.ABOVE_PASS_PRICE).sheet())
        assertEquals(
            "This phone refused the transaction before your wallet saw it: it asks more than 12 USDC, the most a pass costs in this app. Nothing was signed or sent.",
            render(price.notice),
        )
        // The server not answering is still its own sentence.
        val down = requireNotNull(PassState.Refused(PassRefusal.UNAVAILABLE).sheet())
        assertEquals("The server did not build this payment, so nothing was signed and nothing was sent.", render(down.notice))
    }

    @Test
    fun `every plain reason has its own shipped sentence`() {
        val sentences = TransactionGuard.Why.entries.map { ShippedCopy.render(it.refusal()) }
        assertEquals(sentences.size, sentences.toSet().size)
        sentences.forEach { assertTrue(it, it.startsWith("This phone refused the transaction before your wallet saw it: ")) }
    }

    @Test
    fun `a landed payment carries its signature for View on Solscan, and nothing before it does`() {
        val sig = "5".repeat(88)
        assertEquals(sig, requireNotNull(PassState.Landed(sig, null).sheet()).signature)
        assertEquals(null, requireNotNull(PassState.Ready(payer, build(5_000L)).sheet()).signature)
    }

    /** QA of 1.3.20: swap said "Connect again", pass and vote said "Retry" for the same moment. */
    @Test
    fun `a connect that brought no wallet offers Connect again, as the swap and vote sheets do`() {
        val content = requireNotNull(PassState.Refused(PassRefusal.NOT_CONNECTED).sheet())
        assertEquals(PassActionKind.Retry, content.primary?.kind)
        assertEquals("Connect again", render(content.primary?.label))
    }
}
