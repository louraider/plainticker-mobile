package com.myapp.ui.swap

import androidx.annotation.StringRes
import com.myapp.R
import com.myapp.ui.Copy
import com.myapp.ui.Fmt
import com.myapp.ui.raw
import com.myapp.ui.words

/**
 * What the swap sheet says, decided away from the composition (task T10, design task DT7).
 *
 * The sheet is one surface with nine states behind it, and every one of them is a claim about
 * money: what will arrive, what it costs, what SOL the wallet needs, whether anything was sent.
 * So the rules that matter are the ones that pick a sentence and format a number, not the ones
 * that place a pixel. They live here as a pure function of [SwapState] and the wall clock, which
 * makes every state in plan section 13 Pass 2 a unit test instead of a device run, and makes the
 * one unforgivable regression, a debug build that reads as a landed swap, fail the gate.
 *
 * Three rules are enforced here and nowhere else.
 *
 * 1. **No number is computed in a composable.** Every figure below is a [Fmt] call over a field
 *    the quote or the executed result carried. The composable receives strings.
 * 2. **The countdown exists only where the quote carries an expiry.** The 2026-09-12 Metis order
 *    has no `expireAt` field at all (docs/data-map.md): it is bounded by `lastValidBlockHeight`
 *    and slippage, so absence means no expiry and never "expired". A countdown drawn there would
 *    be invented.
 * 3. **A debug build says so from the moment the sheet opens**, and its terminal state is
 *    "Signed, not submitted" with no receipt, no fill and no signature. [SwapState.Landed] is
 *    unreachable when SUBMIT_SWAPS is false, and the sheet must not imply otherwise.
 *
 * The cost cells appear from [SwapState.Quoting] onward and not on the amount step. GET /order
 * shares a 0.5 rps bucket with Price v3 (docs/data-map.md) and is spent at the tap, so a preview
 * estimate would either be stale or cost the quote the swap itself needs. The canvas drew "You
 * receive" and "All-in cost" on the amount step; this is DT7 deciding against it, in the open,
 * and saying so on the screen with [R.string.swap_cost_at_tap] rather than leaving a blank.
 */

/** SOL counts nine decimals, so a lamport figure reads as SOL through the one formatter. */
internal const val LAMPORT_DECIMALS = 9

/**
 * SOL figures keep all nine decimals rather than the six a token quantity is trimmed to.
 * A fee of 6,450 lamports trimmed to six reads "0.000006", which is not what is charged, and a
 * shortfall a person has to top up is a number they will compare against a wallet that shows
 * every lamport. Nothing else on any screen asks for more than six.
 */
private const val SOL_DECIMALS = LAMPORT_DECIMALS

/** How large a cell's value is drawn; DESIGN.md section 4 puts fact values between 22 and 32. */
enum class SheetCellSize {
    /** The one number the block is about: what arrives, what was received. */
    Headline,
    Normal,

    /** A signature or a slot: a key, not a quantity. */
    Fragment,
}

/** One cell of the sheet's blueprint grid, before it meets a composable. */
data class SheetCell(
    val label: Copy,
    val value: Copy,
    val sub: Copy? = null,
    val span: Int = 1,
    /** True when the sub line carries numbers, which are never set in the words face. */
    val subMono: Boolean = false,
    val size: SheetCellSize = SheetCellSize.Normal,
    /**
     * The exact text this cell puts on the clipboard when it is tapped, for a fact that is also
     * a handle. Only the receipt's signature is one: it is the string an explorer takes, and a
     * person cannot retype 88 base58 characters from a screen.
     */
    val copies: String? = null,
)

/** The amount step's field. [value] is the text as typed, never reformatted under a cursor. */
data class SheetField(
    val label: Copy,
    val value: String,
    val action: Copy,
    val balance: Copy,
)

/** The phase line, drawn as the live bar. */
data class SheetPhase(
    val label: Copy,
    /** The countdown, or what a finished phase measured. Null when there is nothing to add. */
    val meta: Copy?,
    /** The bar breathes only while a call is in flight. On the receipt it is static. */
    val live: Boolean,
    /** Stable on purpose: a polite live region must not be re-announced every second. */
    val announcement: Copy,
)

/**
 * The landed receipt's headline, and the signature its copy action needs in full.
 *
 * [amount] and [symbol] are apart because the headline is the one 40sp numeral on the surface and
 * a ticker set at 40sp pushes it off a 360dp screen at a 1.3x font scale. [symbol] is what the
 * chain calls the token; [amount] is a numeral [Fmt] produced, or the missing-value placeholder
 * when the executed answer reported no fill, which is never filled in from the estimate.
 */
data class SheetReceipt(
    val label: Copy,
    /** The fill, from the executed result and never from the quote. */
    val amount: Copy,
    val symbol: String,
    val signature: String,
)

/** What a button does. The sheet maps each to one [SwapViewModel] method and decides nothing. */
enum class SheetActionKind { Submit, Edit, Close, ViewPortfolio }

/** One button. */
data class SheetAction(val label: Copy, val kind: SheetActionKind, val enabled: Boolean = true)

/** What stands where the cost cells go while there is no quote to fill them. */
enum class CostNotice(@StringRes val text: Int) {
    /** The amount step: the quote is spent at the tap, so there is nothing honest to draw yet. */
    AtTap(R.string.swap_cost_at_tap),

    /** The quote is in flight: skeleton bars, never a spinner (plan section 13 Pass 2). */
    Loading(R.string.state_loading),
}

/**
 * Everything the sheet draws, in the slots it draws them in. A null field is an absent slot and
 * not an empty one: a sheet with no phase draws no live bar at all.
 */
data class SheetContent(
    /** "USDC to TSLAx", whichever way the leg points. */
    val title: Copy,
    /** The direction text action, "TSLAx to USDC". Null unless the wallet holds the token. */
    val flip: Copy?,
    val field: SheetField?,
    val phase: SheetPhase?,
    val cells: List<SheetCell>,
    val costNotice: CostNotice?,
    /** The one notice slot: an amount problem, a cancellation, a shortfall, a failure. */
    val notice: Copy?,
    /** Present in every state of a build that signs and does not submit. */
    val debug: Copy?,
    /** Non-null only once the swap has landed, which is when the sheet becomes the receipt. */
    val receipt: SheetReceipt?,
    val primary: SheetAction?,
    val secondary: SheetAction?,
    val footnote: Copy?,
) {
    /** The receipt replaces the sheet's own anatomy rather than being appended to it. */
    val isReceipt: Boolean get() = receipt != null
}

/**
 * The sheet for this state, or null when there is no sheet ([SwapState.Closed]).
 *
 * @param nowMillis the wall clock, re-read once a second while a phase is in flight.
 * @param submitSwaps BuildConfig.SUBMIT_SWAPS, which is false in every debug build.
 */
fun SwapState.sheet(nowMillis: Long, submitSwaps: Boolean): SheetContent? {
    if (this !is SwapState.OnLeg) return null
    val title = words(R.string.swap_direction, leg.input.symbol, leg.output.symbol)
    val debugLine = if (submitSwaps) null else words(R.string.swap_debug_banner)
    val close = SheetAction(words(R.string.action_close), SheetActionKind.Close)

    fun base(
        flip: Copy? = null,
        field: SheetField? = null,
        phase: SheetPhase? = null,
        cells: List<SheetCell> = emptyList(),
        costNotice: CostNotice? = null,
        notice: Copy? = null,
        receipt: SheetReceipt? = null,
        primary: SheetAction? = null,
        secondary: SheetAction? = null,
        footnote: Copy? = null,
    ) = SheetContent(
        title = title,
        flip = flip,
        field = field,
        phase = phase,
        cells = cells,
        costNotice = costNotice,
        notice = notice,
        debug = debugLine,
        receipt = receipt,
        primary = primary,
        secondary = secondary,
        footnote = footnote,
    )

    return when (this) {
        is SwapState.Opening -> base(
            phase = running(R.string.swap_opening, R.string.swap_a11y_opening, timing, nowMillis),
            secondary = close,
        )

        is SwapState.Amount -> base(
            // The flip is a direction, not a verdict: "TSLAx to USDC", and never a verb.
            flip = if (canFlip) words(R.string.swap_direction, leg.output.symbol, leg.input.symbol) else null,
            field = SheetField(
                label = words(R.string.swap_amount_label, leg.input.symbol),
                value = input.text,
                action = words(R.string.action_max),
                balance = words(
                    R.string.swap_balance,
                    Fmt.tokenAmount(balanceRaw, leg.input.decimals),
                    leg.input.symbol,
                ),
            ),
            costNotice = CostNotice.AtTap,
            notice = amountNotice(),
            primary = SheetAction(
                label = words(R.string.swap_button, leg.input.symbol, leg.output.symbol),
                kind = SheetActionKind.Submit,
                enabled = canSubmit,
            ),
            footnote = words(R.string.swap_signs_in),
        )

        is SwapState.Quoting -> base(
            phase = running(R.string.swap_getting_quote, R.string.swap_a11y_quoting, timing, nowMillis),
            costNotice = CostNotice.Loading,
            notice = if (requote) words(R.string.swap_requote_approval) else null,
            secondary = close,
        )

        // The gap is stated in the unit it is short of, and the wallet was never opened.
        is SwapState.Shortfall -> base(
            cells = costCells(leg, quote),
            notice = words(
                R.string.swap_sol_short,
                Fmt.tokenAmount(missingLamports, LAMPORT_DECIMALS, SOL_DECIMALS),
                Fmt.tokenAmount(haveLamports, LAMPORT_DECIMALS, SOL_DECIMALS),
            ),
            primary = SheetAction(words(R.string.swap_back_to_amount), SheetActionKind.Edit),
            secondary = close,
        )

        is SwapState.AwaitingWallet -> base(
            phase = running(
                label = R.string.swap_confirm_in_wallet,
                announcement = R.string.swap_a11y_wallet,
                timing = timing,
                nowMillis = nowMillis,
                // Only an RFQ quote carries an expiry; a Metis order counts down nothing.
                meta = quote.secondsLeft(nowMillis / 1_000L)
                    ?.let { words(R.string.swap_quote_valid, Fmt.seconds(it * 1_000L)) },
            ),
            cells = costCells(leg, quote),
            notice = if (requote) words(R.string.swap_requote_approval) else null,
            secondary = close,
        )

        // No action at all: POST /execute is in flight and there is nothing to take back.
        is SwapState.Landing -> base(
            phase = running(R.string.swap_landing, R.string.swap_a11y_landing, timing, nowMillis),
            cells = costCells(leg, quote),
        )

        // The debug terminal. Signed, nothing sent, no signature to show and no fill to claim.
        is SwapState.Signed -> base(
            phase = SheetPhase(
                label = words(R.string.swap_signed),
                meta = timing.walletMillis?.let { words(R.string.swap_signed_meta, Fmt.secondsExact(it)) },
                live = false,
                announcement = words(R.string.swap_signed),
            ),
            cells = costCells(leg, quote),
            secondary = close,
        )

        is SwapState.Landed -> base(
            phase = SheetPhase(
                label = words(R.string.receipt_landed),
                meta = timing.landingMillis?.let { words(R.string.receipt_confirmed_in, Fmt.secondsExact(it)) },
                // DESIGN.md section 6: the receipt's bar is static. Nothing here is still live.
                live = false,
                announcement = words(R.string.receipt_landed),
            ),
            cells = receiptCells(),
            receipt = SheetReceipt(
                label = words(R.string.receipt_received),
                // An answer that reported no fill leaves this unknown. The estimate standing in
                // for it would put a quantity nobody received under the word "You received".
                amount = fill.outAmountRaw
                    ?.let { raw(Fmt.tokenAmount(it, leg.output.decimals)) }
                    ?: words(R.string.value_missing),
                symbol = leg.output.symbol,
                signature = fill.signature,
            ),
            secondary = SheetAction(words(R.string.receipt_view_portfolio), SheetActionKind.ViewPortfolio),
        )

        is SwapState.Failed -> base(
            cells = quote?.let { costCells(leg, it) }.orEmpty(),
            notice = words(reason.text),
            // Nothing to go back to when the failure happened before the wallet was read.
            primary = if (funds == null) null else SheetAction(words(R.string.swap_back_to_amount), SheetActionKind.Edit),
            secondary = close,
        )
    }
}

/** True while something on the sheet is counting: a phase in flight, or a quote with an expiry. */
val SwapState.needsAClock: Boolean
    get() = this is SwapState.Running || quoteOrNull?.hasExpiry == true

// ---- The pieces ---------------------------------------------------------------------------------

/**
 * A phase still in flight: its own elapsed time in whole seconds, and a stable announcement.
 *
 * The elapsed figure is the phase's, not the attempt's, so a requoted attempt says how long this
 * approval has taken rather than how long the whole thing has. What the attempt cost in total is
 * the receipt's line.
 */
private fun running(
    label: Int,
    announcement: Int,
    timing: SwapTiming,
    nowMillis: Long,
    meta: Copy? = null,
): SheetPhase = SheetPhase(
    label = words(label, Fmt.seconds(timing.phaseElapsedMillis(nowMillis))),
    meta = meta,
    live = true,
    announcement = words(announcement),
)

/**
 * One line under the amount field. A wallet with no USDC at all is told that plainly rather than
 * through "more than the USDC in this wallet", which reads as a typo and not as an empty wallet.
 */
private fun SwapState.Amount.amountNotice(): Copy? = when {
    leg.intoToken && balanceRaw == 0L -> words(R.string.swap_no_usdc)
    note != null -> words(note.text)
    input.problem != null -> words(
        input.problem.text,
        leg.input.symbol,
        Fmt.count(leg.input.decimals),
    )
    else -> null
}

/**
 * The cost block: the estimate, the worst case beneath it, the all-in percent with the route that
 * quoted it, and the SOL this wallet needs.
 *
 * Every one of the five is this quote's own field. The all-in cost is per quote because it moves:
 * the same pair measured 0.08 percent and then 1.44 percent fifteen minutes apart on 2026-09-10,
 * so a fixed disclaimer would be wrong most of the time. The SOL figure is the order's
 * signatureFee, rentFee and prioritizationFee added up, never the 2,039,280 lamport constant task
 * T10 names, which is a classic account's rent and not what this quote charges.
 */
private fun costCells(leg: SwapLeg, quote: SwapQuote): List<SheetCell> = listOf(
    SheetCell(
        label = words(R.string.swap_you_receive),
        value = words(
            R.string.swap_amount_symbol,
            Fmt.tokenAmount(quote.outAmountRaw, leg.output.decimals),
            leg.output.symbol,
        ),
        // otherAmountThreshold: the least this swap may deliver before it reverts, stated beside
        // the estimate rather than hidden behind a slippage control the sheet does not have.
        sub = words(
            R.string.swap_worst_case,
            Fmt.tokenAmount(quote.worstCaseOutRaw, leg.output.decimals),
            leg.output.symbol,
        ),
        span = 2,
        subMono = true,
        size = SheetCellSize.Headline,
    ),
    SheetCell(
        label = words(R.string.swap_all_in_cost),
        // An order that priced neither side in dollars leaves this unknown, and unknown is drawn
        // as the missing value. Zero here would read as a swap that cost nothing.
        value = quote.allInCostPct?.let { raw(Fmt.percent(it, signed = false)) }
            ?: words(R.string.value_missing),
        sub = words(R.string.swap_route, quote.route),
    ),
    SheetCell(
        label = words(R.string.swap_sol_label),
        value = raw(Fmt.tokenAmount(quote.solCost.totalLamports, LAMPORT_DECIMALS, SOL_DECIMALS)),
        // The rent is most of it and it is charged once, for the first account of this mint, so
        // the sub says which of the two this quote is: a new account, or one that already exists.
        sub = if (quote.solCost.rentFeeLamports > 0L) {
            words(
                R.string.swap_sol_sub_rent,
                Fmt.tokenAmount(quote.solCost.rentFeeLamports, LAMPORT_DECIMALS, SOL_DECIMALS),
            )
        } else {
            words(R.string.swap_sol_sub_no_rent)
        },
    ),
)

/**
 * The receipt's cells: what was paid, what it cost against what it was quoted at, and the two
 * things that let a person check this swap on any explorer.
 *
 * The fill is the executed result throughout. A landed swap on 2026-09-10 beat its quote by
 * 0.037 percent, so repeating the estimate here would print a number nobody was charged.
 */
private fun SwapState.Landed.quotedAgainstFill(): Copy? {
    val quoted = quote.allInCostPct ?: return null
    val delta = fillDeltaPct ?: return null
    return words(R.string.receipt_cost_sub, Fmt.percent(quoted, signed = false), Fmt.percent(delta))
}

private fun SwapState.Landed.receiptCells(): List<SheetCell> = listOf(
    SheetCell(
        label = words(R.string.receipt_paid),
        value = words(
            R.string.swap_amount_symbol,
            Fmt.tokenAmount(fill.inAmountRaw, leg.input.decimals),
            leg.input.symbol,
        ),
    ),
    SheetCell(
        label = words(R.string.receipt_cost_paid),
        value = allInCostPaidPct?.let { raw(Fmt.percent(it, signed = false)) }
            ?: words(R.string.value_missing),
        // The sub line compares the quote against the fill, so it exists only when both do.
        sub = quotedAgainstFill(),
        subMono = true,
    ),
    SheetCell(
        label = words(R.string.receipt_signature),
        value = raw(Fmt.shortKey(fill.signature, head = 6, tail = 6)),
        sub = words(R.string.receipt_tap_to_copy),
        span = 2,
        size = SheetCellSize.Fragment,
        copies = fill.signature,
    ),
    SheetCell(
        label = words(R.string.receipt_slot),
        value = fill.slot?.let { raw(Fmt.slot(it)) } ?: words(R.string.value_missing),
        span = 2,
        size = SheetCellSize.Fragment,
    ),
)
