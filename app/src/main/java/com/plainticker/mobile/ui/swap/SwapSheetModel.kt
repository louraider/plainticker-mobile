package com.plainticker.mobile.ui.swap

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Explorer
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.words

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
enum class SheetActionKind { Submit, Edit, Close, ViewPortfolio, Retry, SwapBack, Continue }

/** Which of the three results a finished attempt is. Each has its own mark, colour and words. */
enum class ResultTone {
    /** It landed. The amber ring closes around the figure that arrived. */
    Landed,

    /** It did not, and what that means for the money is certain. The caution colour. */
    Failed,

    /** It was signed and handed on and nobody has said whether it landed. Amber, open. */
    Pending,
}

/**
 * The result an attempt ended in, drawn as its own moment at the top of the sheet rather than
 * as one more line in it (2026-09-24: the founder's first real receipt read "Landed / confirmed in
 * 0.5 s" in a small amber line, and whether the swap had worked was not clear).
 *
 * [headline] is the plain answer in the largest words on the sheet. [figure] is the hero number,
 * only for a landing, and only the executed fill. [detail] says the one thing a person needs next:
 * what arrived and how fast, or why it failed. [announcement] is what TalkBack reads once, as a
 * polite live region, when the result appears: headline and detail together, no seconds ticking.
 */
data class SheetResult(
    val tone: ResultTone,
    val headline: Copy,
    val figure: Copy? = null,
    val detail: Copy?,
    val announcement: List<Copy>,
)

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
    /** The finished attempt's own moment: landed, failed or not yet known. Null while it runs. */
    val result: SheetResult? = null,
    /**
     * A second action in the secondary style, under [secondary]: "Swap back" on a fresh receipt.
     * Never a second primary: a sheet asks for one decision at a time.
     */
    val extra: SheetAction? = null,
    /**
     * What [com.plainticker.mobile.wallet.TransactionGuard] checked on this phone (judges' review,
     * 2026-09-27): what the bytes spend, where they pay, and the least they accept. On
     * [SwapState.Review], before the wallet opens, and on [SwapState.AwaitingWallet] while it is
     * open; the guard's Allow precedes both.
     */
    val checked: Copy? = null,
    /** Solscan's page for the landed transaction, drawn as "View on Solscan". Receipt only. */
    val explorerUrl: String? = null,
) {
    /** The label [explorerUrl] is drawn with, decided here like every other word on the sheet. */
    val explorerLabel: Copy? get() = explorerUrl?.let { words(R.string.action_view_on_solscan) }

    /** The receipt replaces the sheet's own anatomy rather than being appended to it. */
    val isReceipt: Boolean get() = receipt != null

    /** True when the sheet leads with a result instead of the pair it is about. */
    val leadsWithResult: Boolean get() = result != null
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
        result: SheetResult? = null,
        extra: SheetAction? = null,
        checked: Copy? = null,
        explorerUrl: String? = null,
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
        result = result,
        extra = extra,
        checked = checked,
        explorerUrl = explorerUrl,
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
                // What the wallet shows: raw scaled by the token's multiplier, never raw alone.
                balance = words(R.string.swap_balance, leg.input.shown(balanceRaw), leg.input.symbol),
            ),
            costNotice = CostNotice.AtTap,
            notice = amountNotice(),
            primary = SheetAction(
                // A token Jupiter has no reference price for is asked about, not promised: the
                // quote answers whether a route exists (judges' review, 2026-09-27).
                label = if (leg.unpriced && leg.intoToken) {
                    words(R.string.swap_check_availability)
                } else {
                    words(R.string.swap_button, leg.input.symbol, leg.output.symbol)
                },
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

        // Everything the bytes do, before the wallet opens (judges' review, 2026-09-27). One
        // decision: continue to the wallet, or cancel with nothing signed.
        is SwapState.Review -> base(
            phase = SheetPhase(
                label = words(R.string.swap_review),
                // Only an RFQ quote carries an expiry; a Metis order counts down nothing.
                meta = quote.secondsLeft(nowMillis / 1_000L)
                    ?.let { words(R.string.swap_quote_valid, Fmt.seconds(it.coerceAtLeast(0L) * 1_000L)) },
                live = false,
                announcement = words(R.string.swap_review),
            ),
            cells = reviewCells(leg, quote),
            notice = reviewNotice(),
            primary = SheetAction(words(R.string.swap_continue_to_wallet), SheetActionKind.Continue),
            secondary = SheetAction(words(R.string.action_cancel), SheetActionKind.Close),
            checked = checkedLine(leg, quote),
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
            checked = checkedLine(leg, quote),
        )

        // No action at all: POST /execute is in flight and there is nothing to take back. The
        // notice says what is true right now, signed and sent, and claims nothing about landing.
        is SwapState.Landing -> base(
            phase = running(R.string.swap_landing, R.string.swap_a11y_landing, timing, nowMillis),
            cells = costCells(leg, quote),
            notice = words(R.string.swap_landing_note),
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

        is SwapState.Landed -> {
            val phase = SheetPhase(
                label = words(R.string.receipt_landed),
                meta = timing.landingMillis?.let { words(R.string.receipt_confirmed_in, Fmt.secondsExact(it)) },
                // DESIGN.md section 6: the receipt's bar is static. Nothing here is still live.
                live = false,
                announcement = words(R.string.receipt_landed),
            )
            // An answer that reported no fill leaves this unknown. The estimate standing in for
            // it would put a quantity nobody received under the word "You received".
            val amount = fill.outAmountRaw?.let { raw(leg.output.shown(it)) } ?: words(R.string.value_missing)
            base(
                phase = phase,
                cells = receiptCells(),
                receipt = SheetReceipt(
                    label = words(R.string.receipt_received),
                    amount = amount,
                    symbol = leg.output.symbol,
                    signature = fill.signature,
                ),
                secondary = SheetAction(words(R.string.receipt_view_portfolio), SheetActionKind.ViewPortfolio),
                result = landedResult(amount),
                // The other direction, straight from the receipt: the token that just arrived,
                // back the way it came. Worded as a direction and never as a trading verb.
                extra = SheetAction(
                    words(R.string.receipt_swap_back, leg.input.symbol),
                    SheetActionKind.SwapBack,
                ),
                explorerUrl = Explorer.transaction(fill.signature),
            )
        }

        is SwapState.Failed -> base(
            cells = quote?.let { costCells(leg, it) }.orEmpty(),
            notice = sentence,
            primary = failurePrimary(),
            secondary = close,
            result = failedResult(),
        )
    }
}

/**
 * The guard read the bytes before the wallet opened, and required exactly these: the amount
 * spent from the wallet's own account, the proceeds into its own account for the output, and a
 * floor no lower than the one shown.
 */
private fun checkedLine(leg: SwapLeg, quote: SwapQuote): Copy = words(
    R.string.swap_guard_checked,
    leg.input.shown(quote.inAmountRaw),
    leg.input.symbol,
    leg.output.symbol,
    leg.output.shown(quote.worstCaseOutRaw),
)

/**
 * The Review step's one notice, most important first: a quote fetched again because the first
 * ran out, the automatic requote, and the limit of the value check on a token Jupiter has no
 * reference price for.
 */
private fun SwapState.Review.reviewNotice(): Copy? = when {
    refreshed -> words(R.string.swap_review_refreshed)
    requote -> words(R.string.swap_requote_approval)
    leg.unpriced -> words(R.string.swap_review_unpriced)
    else -> null
}

/**
 * The Review step's cells (judges' review, 2026-09-27): what arrives and the least that may,
 * what is spent, the all-in cost, the network fee the bytes charge (signature and priority, both
 * read from the message), and the deposit at its upper bound, named as a deposit that comes back.
 */
private fun reviewCells(leg: SwapLeg, quote: SwapQuote): List<SheetCell> {
    val paid = quote.paidSol
    return listOfNotNull(
        receiveCell(leg, quote),
        SheetCell(
            label = words(R.string.swap_review_spend),
            value = words(R.string.swap_amount_symbol, leg.input.shown(quote.inAmountRaw), leg.input.symbol),
        ),
        costCell(quote),
        SheetCell(
            label = words(R.string.swap_review_network_fee),
            value = raw(Fmt.tokenAmount(paid.networkFeeLamports, LAMPORT_DECIMALS, SOL_DECIMALS)),
            sub = if (paid.networkFeeLamports > 0L) {
                words(
                    R.string.swap_review_network_fee_sub,
                    Fmt.tokenAmount(paid.signatureFeeLamports, LAMPORT_DECIMALS, SOL_DECIMALS),
                    Fmt.tokenAmount(paid.prioritizationFeeLamports, LAMPORT_DECIMALS, SOL_DECIMALS),
                )
            } else {
                words(R.string.swap_review_network_fee_none)
            },
            subMono = paid.networkFeeLamports > 0L,
        ),
        paid.rentFeeLamports.takeIf { it > 0L }?.let { rent ->
            SheetCell(
                label = words(R.string.swap_review_deposit),
                value = words(R.string.swap_review_deposit_value, Fmt.tokenAmount(rent, LAMPORT_DECIMALS, SOL_DECIMALS)),
                sub = words(R.string.swap_review_deposit_sub),
            )
        },
    )
}

/**
 * The one forward action a failure offers. Nothing to go back to when the failure happened
 * before the wallet was read, and never a second attempt when the first one may have landed:
 * [SwapFailure.SUBMIT_UNAVAILABLE] offers Portfolio, where the answer will be.
 */
private fun SwapState.Failed.failurePrimary(): SheetAction? {
    // Before any funds were read, and still the one way on: nothing was connected yet.
    if (reason.next == FailureNext.CONNECT) return SheetAction(words(R.string.action_connect_again), SheetActionKind.Retry)
    if (funds == null) return null
    return when (reason.next) {
        FailureNext.NONE -> null
        FailureNext.EDIT -> SheetAction(words(R.string.swap_back_to_amount), SheetActionKind.Edit)
        FailureNext.RETRY ->
            if (input?.isUsable == true) SheetAction(words(R.string.action_try_again), SheetActionKind.Retry)
            else SheetAction(words(R.string.swap_back_to_amount), SheetActionKind.Edit)
        FailureNext.NEW_QUOTE ->
            if (input?.isUsable == true) SheetAction(words(R.string.swap_new_quote), SheetActionKind.Retry)
            else SheetAction(words(R.string.swap_back_to_amount), SheetActionKind.Edit)
        FailureNext.PORTFOLIO -> SheetAction(words(R.string.receipt_view_portfolio), SheetActionKind.ViewPortfolio)
        FailureNext.CONNECT -> SheetAction(words(R.string.action_connect_again), SheetActionKind.Retry)
    }
}

/** The landing, as its own moment: "Swap landed", the fill as the hero, what arrived and how fast. */
private fun SwapState.Landed.landedResult(amount: Copy): SheetResult {
    val headline = words(R.string.result_landed)
    val detail = timing.landingMillis
        ?.let { words(R.string.result_received_in, leg.output.symbol, Fmt.secondsExact(it)) }
        ?: words(R.string.result_received, leg.output.symbol)
    val spoken = fill.outAmountRaw
        ?.let { words(R.string.result_landed_a11y, leg.output.shown(it), leg.output.symbol) }
        ?: words(R.string.result_landed_unreported_a11y, leg.output.symbol)
    return SheetResult(
        tone = ResultTone.Landed,
        headline = headline,
        figure = amount,
        detail = detail,
        announcement = listOf(headline, spoken),
    )
}

/**
 * A failure, as its own moment. The headline says what is certain about the money, picked by the
 * failure's [FailureOutcome]; the detail is the failure's own one-line reason. Not knowing is its
 * own tone, not a failure: [ResultTone.Pending].
 */
private fun SwapState.Failed.failedResult(): SheetResult {
    val (tone, headline) = when (reason.outcome) {
        FailureOutcome.NOTHING_SENT -> ResultTone.Failed to words(R.string.result_nothing_swapped)
        FailureOutcome.NOT_LANDED -> ResultTone.Failed to words(R.string.result_not_landed)
        FailureOutcome.UNKNOWN -> ResultTone.Pending to words(R.string.result_pending)
        FailureOutcome.NOT_CONNECTED -> ResultTone.Failed to words(R.string.result_not_connected)
    }
    val detail = sentence
    return SheetResult(tone = tone, headline = headline, detail = detail, announcement = listOf(headline, detail))
}

/**
 * A quantity of this token as its wallet shows it: raw scaled by the token's multiplier. The
 * missing value, never a guess, while the multiplier is unknown ([SwapToken.ui]).
 */
internal fun SwapToken.shown(raw: Long): String = ui(raw)?.let { Fmt.tokenAmount(it) } ?: Fmt.MISSING

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
    // A frozen account first: "no TSLAx" would be false, the wallet has it and cannot move it.
    spendFrozen -> words(R.string.swap_account_frozen, leg.input.symbol)
    leg.intoToken && balanceRaw == 0L -> words(R.string.swap_no_usdc)
    // The exit from a holding with nothing in it: said plainly, before any amount is typed.
    !leg.intoToken && balanceRaw == 0L -> words(R.string.swap_no_balance, leg.input.symbol)
    note != null -> words(note.text)
    else -> input.problem?.let { problem ->
        when (problem) {
            AmountProblem.EMPTY -> words(R.string.swap_enter_amount)
            AmountProblem.NOT_A_NUMBER -> words(R.string.swap_amount_not_a_number)
            AmountProblem.NOT_ABOVE_ZERO -> words(R.string.swap_amount_not_above_zero)
            AmountProblem.ABOVE_BALANCE -> words(R.string.swap_amount_above_balance, leg.input.symbol)
            AmountProblem.BELOW_ONE_UNIT -> words(R.string.swap_amount_below_unit, leg.input.symbol)
            // The one amount problem that counts out loud, so the one that has to agree with it.
            AmountProblem.TOO_PRECISE -> counted(
                R.plurals.swap_amount_too_precise,
                leg.input.decimals,
                leg.input.symbol,
                Fmt.count(leg.input.decimals),
            )
        }
    }
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
    receiveCell(leg, quote),
    costCell(quote),
    SheetCell(
        label = words(R.string.swap_sol_label),
        // What this wallet can be charged: the bytes' own fees and the deposit at its upper bound
        // once the guard has read them (security review, 2026-09-27), never the JSON summed.
        value = raw(Fmt.tokenAmount(quote.paidSol.totalLamports, LAMPORT_DECIMALS, SOL_DECIMALS)),
        // The rent is most of it and it is charged once, for the first account of this mint, so
        // the sub says which of the two this quote is: a new account, or one that already exists.
        // The rent is a deposit held in the new account, not a fee: it comes back if the account
        // is closed, and the sub says so (judges' review, 2026-09-27).
        sub = if (quote.paidSol.rentFeeLamports > 0L) {
            words(
                R.string.swap_sol_sub_rent,
                Fmt.tokenAmount(quote.paidSol.rentFeeLamports, LAMPORT_DECIMALS, SOL_DECIMALS),
            )
        } else {
            words(R.string.swap_sol_sub_no_rent)
        },
    ),
)

/**
 * What arrives, as the headline, with otherAmountThreshold beneath it: the least this swap may
 * deliver before it reverts, stated beside the estimate rather than hidden behind a slippage
 * control the sheet does not have.
 */
private fun receiveCell(leg: SwapLeg, quote: SwapQuote): SheetCell = SheetCell(
    label = words(R.string.swap_you_receive),
    value = words(
        R.string.swap_amount_symbol,
        leg.output.shown(quote.outAmountRaw),
        leg.output.symbol,
    ),
    sub = words(
        R.string.swap_worst_case,
        leg.output.shown(quote.worstCaseOutRaw),
        leg.output.symbol,
    ),
    span = 2,
    subMono = true,
    size = SheetCellSize.Headline,
)

/**
 * The cost cell (judges' review, 2026-09-27). "All-in" only when it is: the route's cost plus the
 * signature fee, the priority fee and the token account rent this wallet pays, priced at the SOL
 * price read with the quote, with the SOL share named under it. Without a SOL price the figure is
 * the route's alone, so it is called "Route cost", and the sub points at the SOL line beneath,
 * where the SOL costs stand on their own.
 */
private fun costCell(quote: SwapQuote): SheetCell {
    val allIn = quote.allInCostPct
    val sol = quote.solCostUsd
    return if (allIn != null && sol != null) {
        SheetCell(
            label = words(R.string.swap_all_in_cost),
            value = raw(Fmt.percent(allIn, signed = false)),
            sub = words(R.string.swap_all_in_sub, quote.route, Fmt.price(sol)),
        )
    } else {
        SheetCell(
            label = words(R.string.swap_route_cost),
            // An order that priced neither side in dollars leaves this unknown, and unknown is
            // drawn as the missing value. Zero here would read as a swap that cost nothing.
            value = quote.routeCostPct?.let { raw(Fmt.percent(it, signed = false)) }
                ?: words(R.string.value_missing),
            sub = words(R.string.swap_route_cost_sub, quote.route),
        )
    }
}

/**
 * The receipt's cells: what was paid, what it cost against what it was quoted at, and the two
 * things that let a person check this swap on any explorer.
 *
 * The fill is the executed result throughout. A landed swap on 2026-09-10 beat its quote by
 * 0.037 percent, so repeating the estimate here would print a number nobody was charged.
 */
private fun SwapState.Landed.quotedAgainstFill(): Copy? {
    // The quote's figure in the same measure as the one above it: all-in where that is known.
    val quoted = (if (allInCostPaidPct != null) quote.allInCostPct else quote.routeCostPct) ?: return null
    val delta = fillDeltaPct ?: return null
    return words(R.string.receipt_cost_sub, Fmt.percent(quoted, signed = false), Fmt.percent(delta))
}

/**
 * The SOL this swap cost the wallet, with the refundable rent named, or null when it cost none.
 * Labelled an estimate: the figures are the quote's (the fees the bytes set, the deposit at its
 * upper bound), and the app does not read the landed transaction's own fee (judges' review,
 * 2026-09-27; the forwarder allowlists no method that returns a transaction's meta).
 */
private fun SwapState.Landed.solPaidCell(): SheetCell? {
    val paid = quote.paidSol
    if (paid.totalLamports <= 0L) return null
    return SheetCell(
        label = words(R.string.receipt_sol_paid),
        value = raw(Fmt.tokenAmount(paid.totalLamports, LAMPORT_DECIMALS, SOL_DECIMALS)),
        sub = if (paid.rentFeeLamports > 0L) {
            words(R.string.receipt_sol_paid_rent, Fmt.tokenAmount(paid.rentFeeLamports, LAMPORT_DECIMALS, SOL_DECIMALS))
        } else {
            words(R.string.receipt_sol_paid_no_rent)
        },
    )
}

private fun SwapState.Landed.receiptCells(): List<SheetCell> = listOfNotNull(
    SheetCell(
        label = words(R.string.receipt_paid),
        value = words(
            R.string.swap_amount_symbol,
            leg.input.shown(fill.inAmountRaw),
            leg.input.symbol,
        ),
    ),
    SheetCell(
        // "All-in" only when the SOL was priced; otherwise the route's own cost, named as such.
        label = words(if (allInCostPaidPct != null) R.string.receipt_cost_paid else R.string.receipt_route_cost_paid),
        value = (allInCostPaidPct ?: routeCostPaidPct)?.let { raw(Fmt.percent(it, signed = false)) }
            ?: words(R.string.value_missing),
        // The sub line compares the quote against the fill, so it exists only when both do.
        sub = quotedAgainstFill(),
        subMono = true,
    ),
    // What the wallet was estimated to pay in SOL, the deposit named apart because it comes back.
    solPaidCell(),
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
