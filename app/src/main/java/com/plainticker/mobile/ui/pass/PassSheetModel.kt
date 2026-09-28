package com.plainticker.mobile.ui.pass

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.PassSummary
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.refusal
import com.plainticker.mobile.ui.words

/**
 * What the pay sheet says, decided here and drawn by [PassSheet]; the same split
 * [com.plainticker.mobile.ui.vote.VoteSheet] keeps for the vote machine, so every sentence this
 * feature can show is assertable on a plain JVM out of the shipped strings.xml.
 */
data class PassSheetContent(
    val title: Copy,
    val phase: Copy?,
    val bar: PassBar?,
    val cells: List<PassCell>,
    val notice: Copy?,
    val primary: PassAction?,
    val secondary: PassAction?,
    /** True for the signing round-trip and the confirm call: neither is a swipe-away moment. */
    val holdsOpen: Boolean = false,
    /** The landed payment's signature, for "View on Solscan". Landed only. */
    val signature: String? = null,
)

data class PassBar(val label: Copy, val meta: Copy)

data class PassCell(val label: Copy, val value: Copy, val span: Int = 1, val copies: String? = null)

data class PassAction(val label: Copy, val kind: PassActionKind)

enum class PassActionKind {
    Confirm,
    Retry,
    Close,

    /** A phone with no wallet can still get Pro: close the sheet and open You's code field. */
    HaveCode,
}

/** USDC and USDT both carry six decimals. */
private const val USDC_DECIMALS = 6

/** Lamports' own on-chain precision: what the raw integer below is divided by to read as SOL. */
private const val LAMPORT_DECIMALS = 9

/**
 * The fee is shown to six decimals, [Fmt]'s own default for a token quantity ("six by default",
 * `Fmt.kt`) and the precision every other figure in this app keeps, not to all nine of
 * [LAMPORT_DECIMALS]. Nine is what the conversion above needs to read the raw integer correctly;
 * it is not a reason to print all nine digits of a signer's fee. A real fee (base plus whatever
 * priority fee applied) is essentially never a round number of SOL, so the old setting printed up
 * to nine non-zero decimals: "0.123456789 SOL", 15 characters. `FactGrid` gives a span-1 cell
 * about 146.5dp at this style's 18sp (400dp sheet, minus 20dp*2 grid margin, 1dp*2 border and
 * padding, a 1dp row gap split two ways, 16dp*2 cell padding); JetBrains Mono Medium is a true
 * monospace face (every glyph 600 of 1000 units wide, fontTools against
 * `res/font/jetbrains_mono_medium.ttf`), so that is 10.8dp per character at 18sp and about 13
 * characters fit. 15 does not. A lamport is worth about $0.0000002, so nothing past the sixth
 * decimal (a millionth of a SOL) could change what a signer decides: six decimals is both the
 * honest precision for an amount that small and the shorter one, 12 characters at its own worst
 * case ("0.123456 SOL"), a character inside the budget rather than two over it.
 */
private const val LAMPORT_DISPLAY_DECIMALS = 6

fun PassState.sheet(): PassSheetContent? = when (this) {
    is PassState.Closed -> null

    is PassState.Opening -> running(phase = words(phase.text), cells = emptyList())

    is PassState.Building -> running(phase = words(phase.text), cells = emptyList())

    is PassState.Signing ->
        running(phase = words(phase.text), cells = readyCells(build.summary), holdsOpen = true)

    is PassState.Confirming ->
        running(phase = words(phase.text), cells = emptyList(), holdsOpen = true)

    is PassState.Ready -> PassSheetContent(
        title = words(R.string.pass_title),
        phase = null,
        bar = null,
        cells = readyCells(build.summary),
        notice = if (refreshed) words(R.string.pass_refreshed) else words(R.string.pass_lede),
        primary = PassAction(words(R.string.pass_action), PassActionKind.Confirm),
        secondary = PassAction(words(R.string.action_close), PassActionKind.Close),
    )

    is PassState.Landed -> PassSheetContent(
        title = words(R.string.pass_title),
        phase = null,
        bar = PassBar(words(R.string.pass_landed_label), raw(Fmt.shortKey(signature))),
        cells = listOf(
            PassCell(label = words(R.string.receipt_signature), value = raw(Fmt.shortKey(signature)), copies = signature),
        ),
        notice = if (entitlement != null) words(R.string.pass_landed_note) else words(R.string.pass_landed_pending_note),
        primary = null,
        secondary = PassAction(words(R.string.action_close), PassActionKind.Close),
        signature = signature,
    )

    is PassState.Refused -> PassSheetContent(
        title = words(R.string.pass_title),
        phase = null,
        bar = null,
        cells = emptyList(),
        notice = why?.takeIf { reason == PassRefusal.GUARD_REFUSED }?.refusal() ?: words(reason.text),
        // The same two labels the swap and vote sheets use (QA of 1.3.20): "Connect again" when no
        // wallet was connected, "Try again" for every other retry.
        primary = when {
            reason.retryable -> PassAction(words(retryLabel(reason)), PassActionKind.Retry)
            // Fresh-device QA of 1.3.23: "nothing to pay with" was a dead end. A promo code needs
            // no wallet at all, so the one way on from here is the code field.
            reason == PassRefusal.NO_WALLET -> PassAction(words(R.string.promo_action_have_code), PassActionKind.HaveCode)
            else -> null
        },
        secondary = PassAction(words(R.string.action_close), PassActionKind.Close),
    )
}

private fun running(phase: Copy, cells: List<PassCell>, holdsOpen: Boolean = false) = PassSheetContent(
    title = words(R.string.pass_title),
    phase = phase,
    bar = null,
    cells = cells,
    notice = null,
    primary = null,
    secondary = null,
    holdsOpen = holdsOpen,
)

/** The amount, the fee and where it goes: the three figures a signer is owed before approving. */
private fun readyCells(summary: PassSummary) = listOf(
    PassCell(
        label = words(R.string.pass_amount_label),
        value = words(R.string.pass_amount, Fmt.tokenAmount(summary.amount, USDC_DECIMALS), summary.mint),
        span = 2,
    ),
    PassCell(
        label = words(R.string.vote_fee_label),
        value = words(R.string.vote_fee, Fmt.tokenAmount(summary.lamports, LAMPORT_DECIMALS, maxDecimals = LAMPORT_DISPLAY_DECIMALS)),
    ),
    // The guard refuses any destination but this app's own pinned treasury account, so the label
    // says where the address comes from: this app, not the server's answer.
    PassCell(
        label = words(R.string.pass_destination_pinned),
        value = raw(Fmt.shortKey(summary.destination)),
        span = 2,
        copies = summary.destination,
    ),
)

/** The retry label every wallet flow shares: "Connect again" when no wallet was connected. */
@androidx.annotation.StringRes
internal fun retryLabel(reason: PassRefusal): Int =
    if (reason == PassRefusal.NOT_CONNECTED) R.string.action_connect_again else R.string.action_try_again
