package com.plainticker.mobile.ui.pass

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.PassSummary
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
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
)

data class PassBar(val label: Copy, val meta: Copy)

data class PassCell(val label: Copy, val value: Copy, val span: Int = 1, val copies: String? = null)

data class PassAction(val label: Copy, val kind: PassActionKind)

enum class PassActionKind { Confirm, Retry, Close }

/** USDC and USDT both carry six decimals. */
private const val USDC_DECIMALS = 6
private const val LAMPORT_DECIMALS = 9

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
    )

    is PassState.Refused -> PassSheetContent(
        title = words(R.string.pass_title),
        phase = null,
        bar = null,
        cells = emptyList(),
        notice = words(reason.text),
        primary = if (reason.retryable) PassAction(words(R.string.action_retry), PassActionKind.Retry) else null,
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
        value = words(R.string.vote_fee, Fmt.tokenAmount(summary.lamports, LAMPORT_DECIMALS, maxDecimals = LAMPORT_DECIMALS)),
    ),
    PassCell(
        label = words(R.string.pass_destination_label),
        value = raw(Fmt.shortKey(summary.destination)),
        copies = summary.destination,
    ),
)
