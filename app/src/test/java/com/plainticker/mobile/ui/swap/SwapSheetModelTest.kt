package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.R
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.ui.Copy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the swap sheet says, one test per state of plan section 13 Pass 2 (design task DT7).
 *
 * Every figure here is the live order of 2026-09-12 (docs/data-map.md): 5 USDC into TSLAx,
 * outAmount 1,360,437, otherAmountThreshold 1,346,933, signature 5,000 plus rent 1,488,440 plus
 * priority 1,450 lamports, and no `expireAt` field at all. So a regression that reintroduces the
 * 2,039,280 rent constant, or draws a countdown on a Metis quote, fails here with the real
 * numbers beside it rather than with a fixture nobody measured.
 */
class SwapSheetModelTest {

    private val tslax = SwapToken(KnownMints.TSLAX, "TSLAx", 8)
    private val leg = SwapLeg.into(tslax)

    /** The demo wallet on 2026-09-12: 20.2 USDC, 0.096 SOL, no token account for the mint. */
    private val funds = SwapFunds(owner = "owner", lamports = 96_000_000L, usdcRaw = 20_200_000L, tokenRaw = 0L)

    private val quote = SwapQuote(
        requestId = "01a08b00-0000-7000-8000-00000000f00d",
        inAmountRaw = 5_000_000L,
        outAmountRaw = 1_360_437L,
        worstCaseOutRaw = 1_346_933L,
        allInCostPct = 0.586,
        slippageBps = 100,
        route = "Metis",
        swapType = "aggregator",
        gasless = false,
        solCost = SolCost(
            signatureFeeLamports = 5_000L,
            rentFeeLamports = 1_488_440L,
            prioritizationFeeLamports = 1_450L,
        ),
        transaction = "tx",
        expireAtEpochSec = null,
    )

    private val timing = SwapTiming(startedAtMillis = 0L, phaseStartedAtMillis = 0L)

    private fun amount(text: String = "5", balance: Long = funds.usdcRaw) =
        SwapAmount.parse(text, 6, balance)

    private fun SwapState.sheetNow(nowMillis: Long = 9_000L, submitSwaps: Boolean = true) =
        sheet(nowMillis, submitSwaps)

    private fun SwapState.shown(nowMillis: Long = 9_000L, submitSwaps: Boolean = true) =
        requireNotNull(sheetNow(nowMillis, submitSwaps)) { "no sheet for $this" }

    private fun id(copy: Copy?): Int? = (copy as? Copy.Words)?.id

    private fun args(copy: Copy?): List<String> = (copy as? Copy.Words)?.args.orEmpty()

    private fun raw(copy: Copy?): String? = (copy as? Copy.Raw)?.text

    private fun cell(content: SheetContent, label: Int): SheetCell =
        content.cells.single { id(it.label) == label }

    // ---- Idle ---------------------------------------------------------------------------------

    @Test
    fun `a closed machine has no sheet at all`() {
        assertNull(SwapState.Closed().sheetNow())
        assertNull(SwapState.Closed(SwapNote.CANCELLED_IN_WALLET).sheetNow())
    }

    @Test
    fun `the sheet opening states which wallet read it is waiting on`() {
        val content = SwapState.Opening(leg, timing).shown(nowMillis = 2_400L)
        assertEquals(R.string.swap_opening, id(content.phase?.label))
        assertEquals("the elapsed time is whole seconds while it ticks", listOf("2 s"), args(content.phase?.label))
        assertEquals(R.string.swap_a11y_opening, id(content.phase?.announcement))
        assertEquals(true, content.phase?.live)
        assertNull("nothing is typed and nothing is quoted yet", content.field)
        assertTrue(content.cells.isEmpty())
    }

    // ---- The amount step ------------------------------------------------------------------------

    @Test
    fun `the header names the pair and the field names the side being spent`() {
        val content = SwapState.Amount(leg, funds, amount()).shown()
        assertEquals(R.string.swap_direction, id(content.title))
        assertEquals(listOf("USDC", "TSLAx"), args(content.title))
        assertEquals(R.string.swap_amount_label, id(content.field?.label))
        assertEquals(listOf("USDC"), args(content.field?.label))
        assertEquals("the field shows what was typed, not a reformatted number", "5", content.field?.value)
        assertEquals(R.string.action_max, id(content.field?.action))
        assertEquals(R.string.swap_balance, id(content.field?.balance))
        assertEquals(listOf("20.2", "USDC"), args(content.field?.balance))
        assertEquals(R.string.swap_button, id(content.primary?.label))
        assertEquals(SheetActionKind.Submit, content.primary?.kind)
        assertTrue(content.primary!!.enabled)
        assertEquals(R.string.swap_signs_in, id(content.footnote))
    }

    @Test
    fun `the direction is offered only when the wallet holds the token, and carries no verb`() {
        assertNull(
            "a wallet with none of the token cannot send it back",
            SwapState.Amount(leg, funds, amount()).shown().flip,
        )
        val holding = SwapState.Amount(leg, funds.copy(tokenRaw = 1_360_437L), amount()).shown()
        assertEquals(R.string.swap_direction, id(holding.flip))
        assertEquals(listOf("TSLAx", "USDC"), args(holding.flip))
    }

    @Test
    fun `a flipped leg spends the token and prices its own balance`() {
        val flipped = SwapState.Amount(leg.flipped(), funds.copy(tokenRaw = 1_360_437L), AmountInput.EMPTY).shown()
        assertEquals(listOf("TSLAx", "USDC"), args(flipped.title))
        assertEquals(listOf("TSLAx"), args(flipped.field?.label))
        assertEquals("a token quantity is trimmed to six", listOf("0.013604", "TSLAx"), args(flipped.field?.balance))
        assertEquals(listOf("TSLAx", "USDC"), args(flipped.primary?.label))
    }

    @Test
    fun `an empty field asks for an amount and refuses to submit`() {
        val content = SwapState.Amount(leg, funds, AmountInput.EMPTY).shown()
        assertEquals(R.string.swap_enter_amount, id(content.notice))
        assertFalse(content.primary!!.enabled)
    }

    @Test
    fun `every amount problem reaches the notice slot, and none of them can be submitted`() {
        val cases = mapOf(
            "abc" to R.string.swap_amount_not_a_number,
            "0" to R.string.swap_amount_not_above_zero,
            "1.0000001" to R.string.swap_amount_too_precise,
            "25" to R.string.swap_amount_above_balance,
        )
        cases.forEach { (typed, expected) ->
            val content = SwapState.Amount(leg, funds, amount(typed)).shown()
            assertEquals(typed, expected, id(content.notice))
            assertFalse(typed, content.primary!!.enabled)
        }
    }

    @Test
    fun `a wallet with no USDC is told that, not that the amount is too large`() {
        val empty = funds.copy(usdcRaw = 0L)
        val content = SwapState.Amount(leg, empty, amount("5", balance = 0L)).shown()
        assertEquals(R.string.swap_no_usdc, id(content.notice))
    }

    @Test
    fun `the amount step shows no cost cells, and says where the cost comes from instead`() {
        val content = SwapState.Amount(leg, funds, amount()).shown()
        assertEquals(
            "GET /order shares a 0.5 rps bucket with Price v3 and is spent at the tap",
            CostNotice.AtTap,
            content.costNotice,
        )
        assertTrue(content.cells.isEmpty())
        assertEquals(R.string.swap_cost_at_tap, CostNotice.AtTap.text)
    }

    // ---- Cancelled ----------------------------------------------------------------------------

    @Test
    fun `a cancelled approval is a neutral note over the amount that was typed`() {
        val content = SwapState.Amount(leg, funds, amount(), SwapNote.CANCELLED_IN_WALLET).shown()
        assertEquals(R.string.swap_cancelled, id(content.notice))
        assertEquals("nothing was lost, so the amount is still there", "5", content.field?.value)
        assertTrue("and it can be submitted again", content.primary!!.enabled)
    }

    // ---- Quoting and requoting -------------------------------------------------------------------

    @Test
    fun `quoting shows its elapsed time over a skeleton, never a spinner`() {
        val content = SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing).shown(nowMillis = 310L)
        assertEquals(R.string.swap_getting_quote, id(content.phase?.label))
        assertEquals(listOf("0 s"), args(content.phase?.label))
        assertTrue(content.phase!!.live)
        assertEquals(CostNotice.Loading, content.costNotice)
        assertNull(content.notice)
        assertNull("there is nothing to submit while a quote is in flight", content.primary)
    }

    @Test
    fun `the one automatic requote says a second approval is coming`() {
        val quoting = SwapState.Quoting(leg, funds, amount(), requote = true, timing = timing).shown()
        assertEquals(R.string.swap_requote_approval, id(quoting.notice))
        val wallet = SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = true, timing = timing).shown()
        assertEquals(R.string.swap_requote_approval, id(wallet.notice))
    }

    // ---- The cost block --------------------------------------------------------------------------

    @Test
    fun `the cost block carries five facts, and every one is this quote's own field`() {
        val content = SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing).shown()
        val receive = cell(content, R.string.swap_you_receive)
        assertEquals("outAmount, the estimate", listOf("0.013604", "TSLAx"), args(receive.value))
        assertEquals(R.string.swap_worst_case, id(receive.sub))
        assertEquals("otherAmountThreshold, stated beside it", listOf("0.013469", "TSLAx"), args(receive.sub))
        assertEquals(2, receive.span)
        assertEquals(SheetCellSize.Headline, receive.size)

        val cost = cell(content, R.string.swap_all_in_cost)
        assertEquals("0.59%", raw(cost.value))
        assertEquals(R.string.swap_route, id(cost.sub))
        assertEquals(listOf("Metis"), args(cost.sub))

        val sol = cell(content, R.string.swap_sol_label)
        assertEquals("5000 + 1488440 + 1450 lamports", "0.00149489", raw(sol.value))
        assertEquals(R.string.swap_sol_sub_rent, id(sol.sub))
        assertEquals(listOf("0.00148844"), args(sol.sub))
        assertEquals(3, content.cells.size)
    }

    @Test
    fun `the SOL figure is the quote's three fees and never the plan's rent constant`() {
        val constant = 2_039_280L
        val content = SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing).shown()
        val sol = cell(content, R.string.swap_sol_label)
        assertEquals(1_494_890L, quote.solCost.totalLamports)
        assertTrue("the plan's constant is not the rent this quote charges", quote.solCost.rentFeeLamports != constant)
        assertFalse("and it is nowhere on the screen", raw(sol.value)!!.contains("0.00203928"))
    }

    @Test
    fun `a quote that needs no new token account says so instead of naming a rent`() {
        val existing = quote.copy(solCost = quote.solCost.copy(rentFeeLamports = 0L))
        val content = SwapState.AwaitingWallet(leg, funds, amount(), existing, requote = false, timing = timing).shown()
        val sol = cell(content, R.string.swap_sol_label)
        assertEquals(R.string.swap_sol_sub_no_rent, id(sol.sub))
        assertEquals("0.00000645", raw(sol.value))
    }

    // ---- The wallet leg and the countdown ----------------------------------------------------------

    @Test
    fun `awaiting the wallet counts its own seconds and offers no action but closing`() {
        val content = SwapState.AwaitingWallet(
            leg, funds, amount(), quote, requote = false,
            timing = timing.copy(phaseStartedAtMillis = 1_000L),
        ).shown(nowMillis = 13_700L)
        assertEquals(R.string.swap_confirm_in_wallet, id(content.phase?.label))
        assertEquals("the measured round trip, in whole seconds", listOf("12 s"), args(content.phase?.label))
        assertEquals(R.string.swap_a11y_wallet, id(content.phase?.announcement))
        assertNull(content.primary)
        assertEquals(SheetActionKind.Close, content.secondary?.kind)
    }

    @Test
    fun `a Metis quote carries no expiry, so the sheet draws no countdown`() {
        assertFalse(quote.hasExpiry)
        val content = SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing).shown()
        assertNull("absence of expireAt is no expiry, never an expired quote", content.phase?.meta)
    }

    @Test
    fun `a quote that does carry an expiry counts it down`() {
        val rfq = quote.copy(expireAtEpochSec = 34L)
        val content = SwapState.AwaitingWallet(leg, funds, amount(), rfq, requote = false, timing = timing)
            .shown(nowMillis = 0L)
        assertEquals(R.string.swap_quote_valid, id(content.phase?.meta))
        assertEquals(listOf("34 s"), args(content.phase?.meta))
    }

    @Test
    fun `the clock only moves where something is counting`() {
        assertFalse(SwapState.Closed().needsAClock)
        assertFalse(SwapState.Amount(leg, funds, amount()).needsAClock)
        assertTrue(SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing).needsAClock)
        assertTrue(SwapState.AwaitingWallet(leg, funds, amount(), quote, false, timing).needsAClock)
        assertFalse(
            "a landed swap on a Metis quote counts nothing",
            SwapState.Signed(leg, quote, requoted = false, timing = timing).needsAClock,
        )
        assertTrue(
            "an RFQ quote keeps counting even where the machine is not running",
            SwapState.Shortfall(leg, funds, amount(), quote.copy(expireAtEpochSec = 34L), timing).needsAClock,
        )
    }

    // ---- The shortfall ----------------------------------------------------------------------------

    @Test
    fun `a shortfall names the exact gap, before the wallet was ever opened`() {
        val poor = funds.copy(lamports = 1_000_000L)
        val content = SwapState.Shortfall(leg, poor, amount(), quote, timing).shown()
        assertEquals(R.string.swap_sol_short, id(content.notice))
        assertEquals("1,494,890 needed against 1,000,000 held", listOf("0.00049489", "0.001"), args(content.notice))
        assertNull("no phase: nothing is in flight and no approval was asked for", content.phase)
        assertEquals(SheetActionKind.Edit, content.primary?.kind)
        assertEquals(R.string.swap_back_to_amount, id(content.primary?.label))
        assertEquals("and the cost block is still there to explain the number", 3, content.cells.size)
    }

    // ---- Landing ----------------------------------------------------------------------------------

    @Test
    fun `a submission in flight offers nothing to press`() {
        val content = SwapState.Landing(leg, funds, amount(), quote, requoted = false, timing = timing)
            .shown(nowMillis = 3_100L)
        assertEquals(R.string.swap_landing, id(content.phase?.label))
        assertEquals(listOf("3 s"), args(content.phase?.label))
        assertTrue(content.phase!!.live)
        assertNull("POST /execute cannot be taken back", content.primary)
        assertNull(content.secondary)
    }

    // ---- The receipt --------------------------------------------------------------------------------

    private val fill = SwapFill(
        signature = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe",
        inAmountRaw = 5_000_000L,
        outAmountRaw = 1_360_940L,
        slot = 445_912_340L,
    )

    private fun landed(f: SwapFill = fill) = SwapState.Landed(
        leg = leg,
        quote = quote,
        fill = f,
        requoted = false,
        timing = timing.copy(walletMillis = 12_700L, landingMillis = 3_100L),
    )

    @Test
    fun `the sheet becomes the receipt, and the receipt shows the fill and not the estimate`() {
        val content = landed().shown()
        assertTrue(content.isReceipt)
        assertEquals(R.string.receipt_received, id(content.receipt?.label))
        assertEquals("the executed result, not outAmount", "0.013609", raw(content.receipt?.amount))
        assertEquals("TSLAx", content.receipt?.symbol)
        assertNull("nothing on a receipt can be typed into", content.field)
        assertNull("or submitted again", content.primary)
        assertEquals(SheetActionKind.ViewPortfolio, content.secondary?.kind)
        assertEquals(R.string.receipt_view_portfolio, id(content.secondary?.label))
    }

    @Test
    fun `the receipt's live bar is static and says what the landing measured`() {
        val content = landed().shown()
        assertEquals(R.string.receipt_landed, id(content.phase?.label))
        assertEquals(R.string.receipt_confirmed_in, id(content.phase?.meta))
        assertEquals("a measured duration keeps its tenth", listOf("3.1 s"), args(content.phase?.meta))
        assertEquals("DESIGN.md section 6: the receipt's bar never breathes", false, content.phase?.live)
    }

    @Test
    fun `the receipt states the fill against the quote, the cost paid, the signature and the slot`() {
        val content = landed().shown()
        assertEquals(4, content.cells.size)

        assertEquals(listOf("5", "USDC"), args(cell(content, R.string.receipt_paid).value))

        val cost = cell(content, R.string.receipt_cost_paid)
        assertEquals("the quote's 0.586 percent corrected by the fill", "0.55%", raw(cost.value))
        assertEquals(R.string.receipt_cost_sub, id(cost.sub))
        assertEquals("quoted cost, then how the fill beat it", listOf("0.59%", "+0.04%"), args(cost.sub))

        val signature = cell(content, R.string.receipt_signature)
        assertEquals("4xQm7g…5oVtHe", raw(signature.value))
        assertEquals(R.string.receipt_tap_to_copy, id(signature.sub))
        assertEquals("the whole signature is what reaches the clipboard", fill.signature, signature.copies)
        assertEquals(SheetCellSize.Fragment, signature.size)

        assertEquals("445,912,340", raw(cell(content, R.string.receipt_slot).value))
        assertNull("only the signature is a handle", cell(content, R.string.receipt_slot).copies)
    }

    @Test
    fun `an answer that reported no fill draws the missing value, never the estimate`() {
        val content = landed(fill.copy(outAmountRaw = null)).shown()
        assertEquals(
            "the estimate must not stand in for what arrived",
            R.string.value_missing,
            id(content.receipt?.amount),
        )
        val cost = cell(content, R.string.receipt_cost_paid)
        assertEquals(R.string.value_missing, id(cost.value))
        assertNull("there is no fill to compare the quote against", cost.sub)
    }

    @Test
    fun `a quote that priced neither side in dollars leaves the cost unknown, not free`() {
        val content = SwapState.AwaitingWallet(
            leg, funds, amount(), quote.copy(allInCostPct = null), requote = false, timing = timing,
        ).shown()
        val cost = cell(content, R.string.swap_all_in_cost)
        assertEquals("zero percent would read as a swap that cost nothing", R.string.value_missing, id(cost.value))
        assertEquals("the route still names itself", R.string.swap_route, id(cost.sub))
    }

    @Test
    fun `an execute answer with no slot leaves the cell missing rather than showing a zero`() {
        val content = landed(fill.copy(slot = null)).shown()
        assertEquals(R.string.value_missing, id(cell(content, R.string.receipt_slot).value))
    }

    // ---- The debug build ------------------------------------------------------------------------------

    @Test
    fun `a debug build says what it is in every state of the sheet`() {
        listOf(
            SwapState.Opening(leg, timing),
            SwapState.Amount(leg, funds, amount()),
            SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing),
            SwapState.Shortfall(leg, funds, amount(), quote, timing),
            SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing),
            SwapState.Landing(leg, funds, amount(), quote, requoted = false, timing = timing),
            SwapState.Signed(leg, quote, requoted = false, timing = timing),
            SwapState.Failed(leg, funds, amount(), SwapFailure.SWAP_REFUSED),
        ).forEach { state ->
            val content = state.shown(submitSwaps = false)
            assertEquals(state.javaClass.simpleName, R.string.swap_debug_banner, id(content.debug))
        }
    }

    @Test
    fun `a build that submits carries no debug band anywhere`() {
        val content = SwapState.Amount(leg, funds, amount()).shown(submitSwaps = true)
        assertNull(content.debug)
    }

    @Test
    fun `the debug terminal is signed and not submitted, and is not a receipt`() {
        val content = SwapState.Signed(leg, quote, requoted = false, timing = timing.copy(walletMillis = 12_700L))
            .shown(submitSwaps = false)
        assertEquals(R.string.swap_signed, id(content.phase?.label))
        assertEquals(R.string.swap_signed_meta, id(content.phase?.meta))
        assertEquals(listOf("12.7 s"), args(content.phase?.meta))
        assertFalse(content.phase!!.live)
        assertFalse("a debug build has nothing to write a receipt from", content.isReceipt)
        assertNull(content.receipt)
        assertTrue(
            "and nothing on it may read as a landing",
            content.cells.none { id(it.label) == R.string.receipt_signature },
        )
        assertNotNull(content.debug)
    }

    // ---- Failure --------------------------------------------------------------------------------------

    @Test
    fun `every failure reaches the notice slot as this app's own sentence`() {
        SwapFailure.entries.forEach { reason ->
            val content = SwapState.Failed(leg, funds, amount(), reason, quote, false, timing).shown()
            assertEquals(reason.name, reason.text, id(content.notice))
            assertEquals(reason.name, SheetActionKind.Edit, content.primary?.kind)
        }
    }

    @Test
    fun `a failure before the wallet was read has nowhere to go back to`() {
        val content = SwapState.Failed(leg, funds = null, input = null, reason = SwapFailure.NO_WALLET).shown()
        assertEquals(R.string.swap_failed_no_wallet, id(content.notice))
        assertNull(content.primary)
        assertEquals(SheetActionKind.Close, content.secondary?.kind)
        assertTrue("no quote, so no cost block", content.cells.isEmpty())
    }

    @Test
    fun `a failure that had a quote keeps the cost block that explains it`() {
        val content = SwapState.Failed(leg, funds, amount(), SwapFailure.SWAP_REFUSED, quote, false, timing).shown()
        assertEquals(3, content.cells.size)
    }

    // ---- The whole surface ------------------------------------------------------------------------------

    @Test
    fun `there is no slippage control in any state, and no cell invents one`() {
        val states = listOf(
            SwapState.Opening(leg, timing),
            SwapState.Amount(leg, funds, amount()),
            SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing),
            SwapState.Shortfall(leg, funds, amount(), quote, timing),
            SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing),
            SwapState.Landing(leg, funds, amount(), quote, requoted = false, timing = timing),
            SwapState.Signed(leg, quote, requoted = false, timing = timing),
            landed(),
            SwapState.Failed(leg, funds, amount(), SwapFailure.SWAP_REFUSED, quote, false, timing),
        )
        states.forEach { state ->
            val name = state.javaClass.simpleName
            val content = state.shown()
            // Slippage exists on this surface in exactly one place: the worst case, as the sub
            // line under the estimate. It is never a value, and never something a person sets.
            assertTrue(
                "$name puts slippage in a cell value",
                content.cells.none { id(it.value) == R.string.swap_worst_case },
            )
            assertEquals(
                "$name has a field, and only the amount step may",
                state is SwapState.Amount,
                content.field != null,
            )
            assertTrue(
                "$name offers more than the two buttons the sheet has",
                listOfNotNull(content.primary, content.secondary).size <= 2,
            )
        }
    }

    @Test
    fun `the header is the same pair in every state of one attempt`() {
        val states = listOf(
            SwapState.Opening(leg, timing),
            SwapState.Amount(leg, funds, amount()),
            SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing),
            SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing),
            landed(),
        )
        states.forEach {
            assertEquals(listOf("USDC", "TSLAx"), args(it.shown().title))
        }
    }
}
