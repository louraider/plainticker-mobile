package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.wallet.TransactionGuard
import com.plainticker.mobile.R
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.words
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
        routeCostPct = 0.586,
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
            "25" to R.string.swap_amount_above_balance,
        )
        cases.forEach { (typed, expected) ->
            val content = SwapState.Amount(leg, funds, amount(typed)).shown()
            assertEquals(typed, expected, id(content.notice))
            assertFalse(typed, content.primary!!.enabled)
        }
        // An amount finer than the token counts says how many decimals it counts, which is a
        // number spoken out loud, so the notice is counted copy and carries the count itself.
        val tooPrecise = SwapState.Amount(leg, funds, amount("1.0000001")).shown()
        assertEquals(
            Copy.Counted(R.plurals.swap_amount_too_precise, 6, listOf("USDC", "6")),
            tooPrecise.notice,
        )
        assertFalse("1.0000001", tooPrecise.primary!!.enabled)
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

    // ---- Not approved -------------------------------------------------------------------------

    /**
     * The sentence a session that ended with no signature gets, and the state it gets it in.
     *
     * The Seeker drew "The swap did not land. Nothing was swapped." on 2026-09-13 when the wallet
     * sheet was closed without an approval: true on chain and the wrong sentence, because nothing
     * failed. Mobile Wallet Adapter cannot tell a decline from a sheet that went away, so this one
     * names no fault, and the state is the amount step with the amount intact rather than a
     * terminal screen with a Close button.
     */
    @Test
    fun `an approval that came back with no signature is a neutral note over the typed amount`() {
        val content = SwapState.Amount(leg, funds, amount(), SwapNote.NOT_APPROVED).shown()
        assertEquals(R.string.swap_not_approved, id(content.notice))
        assertEquals(
            "No signature came back, so nothing was sent. The amount is still here.",
            ShippedCopy.render(requireNotNull(content.notice)),
        )
        assertEquals("nothing was lost, so the amount is still there", "5", content.field?.value)
        assertEquals(SheetActionKind.Submit, content.primary?.kind)
        assertTrue("and it can be submitted again", content.primary!!.enabled)
        assertFalse("it is not a dead end", content.isReceipt)
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

        // No SOL price in this quote, so the figure is the route's and is called that.
        val cost = cell(content, R.string.swap_route_cost)
        assertEquals("0.59%", raw(cost.value))
        assertEquals(R.string.swap_route_cost_sub, id(cost.sub))
        assertEquals(listOf("Metis"), args(cost.sub))

        val sol = cell(content, R.string.swap_sol_label)
        assertEquals("5000 + 1488440 + 1450 lamports", "0.00149489", raw(sol.value))
        assertEquals(R.string.swap_sol_sub_rent, id(sol.sub))
        assertEquals(listOf("0.00148844"), args(sol.sub))
        assertEquals(3, content.cells.size)
    }

    /**
     * Judges' review, 2026-09-27: "All-in cost" excluded the SOL. With a SOL price it now adds the
     * signature fee, the priority fee and the rent this wallet pays, and names the SOL share.
     */
    @Test
    fun `with a SOL price the cost is all-in, the SOL share named under it`() {
        val priced = quote.copy(inUsdValue = 5.0, solUsd = 200.0)
        val content = SwapState.AwaitingWallet(leg, funds, amount(), priced, requote = false, timing = timing).shown()
        val cost = cell(content, R.string.swap_all_in_cost)
        // 0.586 percent route, plus 1,494,890 lamports at 200 dollars (0.298978) over 5 dollars in.
        assertEquals(0.586 + 0.298978 / 5.0 * 100.0, priced.allInCostPct!!, 1e-9)
        assertEquals("6.57%", raw(cost.value))
        assertEquals(R.string.swap_all_in_sub, id(cost.sub))
        assertEquals(listOf("Metis", "$0.2990"), args(cost.sub))
        assertEquals("the rent within it is named apart", 0.297688, priced.rentUsd!!, 1e-9)
        assertTrue("no Route cost cell beside it", content.cells.none { id(it.label) == R.string.swap_route_cost })
    }

    @Test
    fun `a gasless order counts only the SOL the wallet itself pays`() {
        val order = com.plainticker.mobile.data.jupiter.SwapOrder(
            requestId = "r",
            gasless = true,
            taker = "taker",
            inUsdValue = 5.0,
            outUsdValue = 4.97,
            signatureFeeLamports = 10_000L,
            signatureFeePayer = "maker",
            prioritizationFeeLamports = 4_245L,
            prioritizationFeePayer = "maker",
            rentFeeLamports = 1_488_440L,
            rentFeePayer = "taker",
        )
        val q = SwapQuote.from(order).copy(solUsd = 200.0)
        assertEquals(SolCost(0L, 1_488_440L, 0L), q.paidSol)
        assertEquals(1_488_440L / 1e9 * 200.0, q.solCostUsd!!, 1e-9)
        // Not gasless: the taker pays all three, whatever the payer fields say.
        val paying = SwapQuote.from(order.copy(gasless = false))
        assertEquals(paying.solCost, paying.paidSol)
    }

    /** Voynich, judges' review 2026-09-27: show the guard's result on screen before signing. */
    @Test
    fun `while the wallet is open the sheet says what this phone checked in the bytes`() {
        val content = SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing).shown()
        assertEquals(R.string.swap_guard_checked, id(content.checked))
        assertEquals(listOf("5", "USDC", "TSLAx", "0.013469"), args(content.checked))
        assertEquals(
            "Checked on this phone: spends 5 USDC from your account, pays into your own TSLAx account, at least 0.013469 TSLAx.",
            ShippedCopy.render(requireNotNull(content.checked)),
        )
        // Only there: before the guard has read anything, and after the wallet, there is no such line.
        assertNull(SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing).shown().checked)
        assertNull(SwapState.Landing(leg, funds, amount(), quote, requoted = false, timing = timing).shown().checked)
        assertNull(SwapState.Failed(leg, funds, amount(), SwapFailure.GUARD_REFUSED, quote, false, timing).shown().checked)
    }

    @Test
    fun `a wallet that only signs by sending is named, with nothing to retry`() {
        val content = SwapState.Failed(leg, funds, amount(), SwapFailure.SIGN_ONLY_UNSUPPORTED, quote, false, timing).shown()
        assertEquals(
            "This wallet only signs by sending, and a swap needs it to sign first. Use Seed Vault Wallet. Nothing was signed.",
            ShippedCopy.render(requireNotNull(content.notice)),
        )
        assertNull(content.primary)
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
        assertEquals(5, content.cells.size)

        assertEquals(listOf("5", "USDC"), args(cell(content, R.string.receipt_paid).value))

        // No SOL price with this quote: the route's cost paid, named as such.
        val cost = cell(content, R.string.receipt_route_cost_paid)
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
        // A real signature is 86 to 88 characters; this fixture's short one makes no link at all.
        assertNull(content.explorerUrl)
        val real = "5".repeat(88)
        assertEquals("the receipt links the transaction on Solscan", "https://solscan.io/tx/$real", landed(fill.copy(signature = real)).shown().explorerUrl)

        // What the wallet paid in SOL, the deposit named as coming back.
        val sol = cell(content, R.string.receipt_sol_paid)
        assertEquals("0.00149489", raw(sol.value))
        assertEquals(R.string.receipt_sol_paid_rent, id(sol.sub))
        assertEquals(listOf("0.00148844"), args(sol.sub))
    }

    @Test
    fun `with a SOL price the receipt states the all-in cost paid, quote against fill in the same measure`() {
        val priced = quote.copy(inUsdValue = 5.0, solUsd = 200.0)
        val content = SwapState.Landed(leg, priced, fill, requoted = false, timing = timing).shown()
        val cost = cell(content, R.string.receipt_cost_paid)
        val solShare = 0.298978 / 5.0 * 100.0
        assertEquals(fill.routeCostPaidPct(priced)!! + solShare, fill.allInCostPaidPct(priced)!!, 1e-9)
        assertEquals(Fmt.percent(fill.allInCostPaidPct(priced)!!, signed = false), raw(cost.value))
        assertEquals(listOf(Fmt.percent(priced.allInCostPct!!, signed = false), "+0.04%"), args(cost.sub))
    }

    @Test
    fun `an answer that reported no fill draws the missing value, never the estimate`() {
        val content = landed(fill.copy(outAmountRaw = null)).shown()
        assertEquals(
            "the estimate must not stand in for what arrived",
            R.string.value_missing,
            id(content.receipt?.amount),
        )
        val cost = cell(content, R.string.receipt_route_cost_paid)
        assertEquals(R.string.value_missing, id(cost.value))
        assertNull("there is no fill to compare the quote against", cost.sub)
    }

    @Test
    fun `a quote that priced neither side in dollars leaves the cost unknown, not free`() {
        val content = SwapState.AwaitingWallet(
            leg, funds, amount(), quote.copy(routeCostPct = null), requote = false, timing = timing,
        ).shown()
        val cost = cell(content, R.string.swap_route_cost)
        assertEquals("zero percent would read as a swap that cost nothing", R.string.value_missing, id(cost.value))
        assertEquals("the route still names itself", R.string.swap_route_cost_sub, id(cost.sub))
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
            // Every failure now offers the one way on its own outcome allows (2026-09-24), where
            // it used to offer "Back to the amount" for all of them, including the one failure
            // where going back and swapping again could be a second swap over a first that landed.
            val expected = when (reason.next) {
                FailureNext.NONE -> null
                FailureNext.EDIT -> SheetActionKind.Edit
                FailureNext.RETRY, FailureNext.NEW_QUOTE -> SheetActionKind.Retry
                FailureNext.PORTFOLIO -> SheetActionKind.ViewPortfolio
            }
            assertEquals(reason.name, expected, content.primary?.kind)
            assertEquals(reason.name, SheetActionKind.Close, content.secondary?.kind)
        }
    }

    @Test
    fun `an outcome nobody has reported never offers a second attempt`() {
        val unknown = SwapFailure.entries.filter { it.outcome == FailureOutcome.UNKNOWN }
        assertEquals(listOf(SwapFailure.SUBMIT_UNAVAILABLE), unknown)
        val content = SwapState.Failed(leg, funds, amount(), SwapFailure.SUBMIT_UNAVAILABLE, quote, false, timing).shown()
        val offered = listOfNotNull(content.primary, content.secondary, content.extra).map { it.kind }
        assertFalse("a retry over a swap that may have landed is two swaps", SheetActionKind.Retry in offered)
        assertFalse("and so is going back to submit the same amount", SheetActionKind.Edit in offered)
        assertEquals(SheetActionKind.ViewPortfolio, content.primary?.kind)
        assertEquals(ResultTone.Pending, content.result?.tone)
        assertEquals(R.string.result_pending, id(content.result?.headline))
        assertEquals("Sent, not confirmed yet", ShippedCopy.render(content.result!!.headline))
    }

    @Test
    fun `a failure before the wallet was read has nowhere to go back to`() {
        val content = SwapState.Failed(leg, funds = null, input = null, reason = SwapFailure.NO_WALLET).shown()
        assertEquals(R.string.swap_failed_no_wallet, id(content.notice))
        assertNull(content.primary)
        assertEquals(SheetActionKind.Close, content.secondary?.kind)
        assertTrue("no quote, so no cost block", content.cells.isEmpty())
    }

    /**
     * The Swap to USDC checks (security audit, 2026-09-26) are refusals before the wallet, so each
     * says nothing was sent. The one for a second source that did not answer is honest about what
     * it costs: the swap is paused, viewing is not.
     */
    @Test
    fun `the Swap to USDC checks say plainly why the swap stopped and that nothing was sent`() {
        fun text(reason: SwapFailure) = ShippedCopy.render(words(reason.text))
        val checks = listOf(
            SwapFailure.SECOND_SOURCE_UNREACHABLE,
            SwapFailure.SECOND_SOURCE_MISMATCH,
            SwapFailure.VALUE_MISMATCH,
            SwapFailure.VALUE_UNCHECKED,
        )
        checks.forEach {
            assertEquals(it.name, FailureOutcome.NOTHING_SENT, it.outcome)
            assertFalse(it.name, '!' in text(it))
        }
        assertEquals(
            "A second check of this token did not answer, so Swap to USDC is paused. Nothing was sent, and your holding still shows.",
            text(SwapFailure.SECOND_SOURCE_UNREACHABLE),
        )
        assertTrue("never reached the wallet" in text(SwapFailure.VALUE_MISMATCH))
        assertTrue("Nothing was sent" in text(SwapFailure.SECOND_SOURCE_MISMATCH))
        // Refused while opening, there is no amount to go back to and nothing to retry with.
        val opened = SwapState.Failed(leg, funds = null, input = null, reason = SwapFailure.SECOND_SOURCE_UNREACHABLE).shown()
        assertNull(opened.primary)
        assertEquals(SheetActionKind.Close, opened.secondary?.kind)
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
                listOfNotNull(content.primary, content.secondary, content.extra).size <= 2,
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

    // ---- The result, 2026-09-24 -----------------------------------------------------------------

    @Test
    fun `a landing is its own moment, the headline, the fill as the hero, what arrived and how fast`() {
        val content = landed().shown()
        val result = requireNotNull(content.result) { "a landing must lead with its result" }
        assertEquals(ResultTone.Landed, result.tone)
        assertEquals("Swap landed", ShippedCopy.render(result.headline))
        assertEquals("the executed fill, never the estimate", "0.013609", raw(result.figure))
        assertEquals(R.string.result_received_in, id(result.detail))
        assertEquals("TSLAx received, confirmed in 3.1 s", ShippedCopy.render(requireNotNull(result.detail)))
        assertEquals(
            "TalkBack hears the headline and what arrived, once",
            listOf("Swap landed", "Received 0.013609 TSLAx"),
            result.announcement.map { ShippedCopy.render(it) },
        )
    }

    @Test
    fun `a landing whose fill was not reported says so in the result, and claims no amount`() {
        val result = requireNotNull(landed(fill.copy(outAmountRaw = null)).shown().result)
        assertEquals(R.string.value_missing, id(result.figure))
        assertEquals(
            listOf("Swap landed", "The amount of TSLAx received was not reported"),
            result.announcement.map { ShippedCopy.render(it) },
        )
    }

    @Test
    fun `a fresh receipt offers the way back, worded as a direction and never a verb`() {
        val content = landed().shown()
        assertNull("still no primary on a receipt", content.primary)
        assertEquals(SheetActionKind.ViewPortfolio, content.secondary?.kind)
        assertEquals(SheetActionKind.SwapBack, content.extra?.kind)
        assertEquals("Swap back to USDC", ShippedCopy.render(requireNotNull(content.extra).label))
    }

    @Test
    fun `each failure's headline says what is certain about the money, in the failure tone`() {
        SwapFailure.entries.forEach { reason ->
            val result = requireNotNull(SwapState.Failed(leg, funds, amount(), reason, quote, false, timing).shown().result)
            val (tone, headline) = when (reason.outcome) {
                FailureOutcome.NOTHING_SENT -> ResultTone.Failed to "Nothing was swapped"
                FailureOutcome.NOT_LANDED -> ResultTone.Failed to "The swap did not land"
                FailureOutcome.UNKNOWN -> ResultTone.Pending to "Sent, not confirmed yet"
            }
            assertEquals(reason.name, tone, result.tone)
            assertEquals(reason.name, headline, ShippedCopy.render(result.headline))
            assertEquals("the reason is the failure's own one line", reason.text, id(result.detail))
            assertNull("a failure has no hero figure", result.figure)
            assertEquals(2, result.announcement.size)
        }
    }

    @Test
    fun `the failures a person meets map to plain reasons and the right way on`() {
        fun failed(reason: SwapFailure) = SwapState.Failed(leg, funds, amount(), reason, quote, false, timing).shown()
        // Expired quote, slippage: a fresh quote, worded as one.
        listOf(SwapFailure.QUOTE_EXPIRED, SwapFailure.SLIPPAGE, SwapFailure.QUOTE_GONE).forEach {
            assertEquals(it.name, "Get a new quote", ShippedCopy.render(requireNotNull(failed(it).primary).label))
        }
        // Network: try again. Guard refusal: try again, and the reason names what happened.
        assertEquals("Try again", ShippedCopy.render(requireNotNull(failed(SwapFailure.QUOTE_UNAVAILABLE).primary).label))
        assertEquals(
            "The transaction did not match this swap, so it never reached the wallet",
            ShippedCopy.render(requireNotNull(failed(SwapFailure.GUARD_REFUSED).result?.detail)),
        )
        // With the guard's plain reason (judges' review, 2026-09-27), the sheet states it.
        val withWhy = SwapState.Failed(leg, funds, amount(), SwapFailure.GUARD_REFUSED, quote, false, timing, TransactionGuard.Why.WRONG_RECIPIENT).shown()
        val sentence = "This phone refused the transaction before your wallet saw it: it pays somewhere other than the destination shown. Nothing was signed or sent."
        assertEquals(sentence, ShippedCopy.render(requireNotNull(withWhy.result?.detail)))
        assertEquals(sentence, ShippedCopy.render(requireNotNull(withWhy.notice)))
        // Dust: back to the amount, because the same amount will return nothing again.
        assertEquals(SheetActionKind.Edit, failed(SwapFailure.QUOTE_DUST).primary?.kind)
        // Wallet changed: nothing to retry with, only Close.
        assertNull(failed(SwapFailure.WALLET_CHANGED).primary)
        // No failure's copy uses the one word the sheet reserves for the worst-case line.
        SwapFailure.entries.forEach {
            assertFalse(it.name, "slippage" in ShippedCopy.render(words(it.text)).lowercase())
        }
    }

    @Test
    fun `a sheet still in progress has no result, and landing says what is true right now`() {
        listOf(
            SwapState.Opening(leg, timing),
            SwapState.Amount(leg, funds, amount()),
            SwapState.Quoting(leg, funds, amount(), requote = false, timing = timing),
            SwapState.AwaitingWallet(leg, funds, amount(), quote, requote = false, timing = timing),
            SwapState.Landing(leg, funds, amount(), quote, requoted = false, timing = timing),
        ).forEach { assertNull(it.javaClass.simpleName, it.shown().result) }
        val landing = SwapState.Landing(leg, funds, amount(), quote, requoted = false, timing = timing).shown()
        assertEquals("Signed and sent. Waiting for the network to confirm it.", ShippedCopy.render(requireNotNull(landing.notice)))
    }

    // ---- The reverse direction and the Token-2022 multiplier ------------------------------------

    private val out = SwapLeg.outOf(tslax)
    private val holding = funds.copy(tokenRaw = 264_600L)

    @Test
    fun `swap to USDC names the pair the other way and spends the xStock balance`() {
        val content = SwapState.Amount(out, holding, SwapAmount.parse("", 8, holding.tokenRaw)).shown()
        assertEquals(listOf("TSLAx", "USDC"), args(content.title))
        assertEquals(listOf("TSLAx"), args(content.field?.label))
        assertEquals(listOf("0.002646", "TSLAx"), args(content.field?.balance))
        assertEquals(listOf("TSLAx", "USDC"), args(content.primary?.label))
    }

    @Test
    fun `a wallet with none of the xStock is told that before any amount is typed`() {
        val content = SwapState.Amount(out, funds, SwapAmount.parse("", 8, 0L)).shown()
        assertEquals("No TSLAx in this wallet", ShippedCopy.render(requireNotNull(content.notice)))
        assertFalse(content.primary!!.enabled)
    }

    @Test
    fun `a frozen account says it is frozen, not empty, and cannot be submitted`() {
        val frozen = funds.copy(tokenRaw = 0L, frozenMints = setOf(KnownMints.TSLAX))
        val state = SwapState.Amount(out, frozen, SwapAmount.parse("1", 8, 0L))
        val content = state.shown()
        assertEquals(
            "The TSLAx account in this wallet is frozen by its issuer, so it cannot be swapped",
            ShippedCopy.render(requireNotNull(content.notice)),
        )
        assertFalse(state.canSubmit)
        assertFalse(content.primary!!.enabled)
    }

    @Test
    fun `a split xStock is shown as the wallet shows it, raw times the multiplier`() {
        // NFLXx, read 2026-09-24: raw 272,557,048,309 at 8 decimals and an effective multiplier
        // of 10, which the node itself renders as 27255.7048309.
        val nflx = SwapToken("XsEH7wWfJJu2ZT3UCFeVfALnVA6CP5ur7Ee11KmzVpL", "NFLXx", 8, java.math.BigDecimal.TEN)
        val split = SwapLeg.outOf(nflx)
        val wallet = funds.copy(tokenRaw = 272_557_048_309L)
        val content = SwapState.Amount(split, wallet, AmountInput.EMPTY).shown()
        assertEquals(listOf("27,255.704831", "NFLXx"), args(content.field?.balance))

        // And the forward estimate of a split token is scaled the same way: 1,360,437 raw is
        // 0.1360437 shares at a multiplier of 10, not 0.013604.
        val forward = SwapState.AwaitingWallet(SwapLeg.into(nflx), funds, amount(), quote, requote = false, timing = timing).shown()
        assertEquals(listOf("0.136044", "NFLXx"), args(cell(forward, R.string.swap_you_receive).value))
    }

    @Test
    fun `an unknown multiplier states no quantity at all rather than guessing one`() {
        val unknown = SwapToken(KnownMints.TSLAX, "TSLAx", 8, multiplier = null)
        val content = SwapState.Amount(SwapLeg.outOf(unknown), holding, AmountInput.EMPTY).shown()
        assertEquals(listOf("-", "TSLAx"), args(content.field?.balance))
    }

    @Test
    fun `the reverse receipt shows USDC received and the xStock paid, and offers the way back`() {
        val reverseQuote = quote.copy(inAmountRaw = 264_600L, outAmountRaw = 996_503L, worstCaseOutRaw = 986_438L)
        val reverseFill = fill.copy(inAmountRaw = 264_600L, outAmountRaw = 996_812L)
        val content = SwapState.Landed(out, reverseQuote, reverseFill, false, timing.copy(landingMillis = 500L)).shown()
        assertEquals("0.996812", raw(content.result?.figure))
        assertEquals("USDC received, confirmed in 0.5 s", ShippedCopy.render(requireNotNull(content.result?.detail)))
        assertEquals(listOf("0.002646", "TSLAx"), args(cell(content, R.string.receipt_paid).value))
        assertEquals("Swap back to TSLAx", ShippedCopy.render(requireNotNull(content.extra).label))
    }
}
