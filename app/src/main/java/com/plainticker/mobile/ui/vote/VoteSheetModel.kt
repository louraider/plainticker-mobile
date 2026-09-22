package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.VoteSummary
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.words

/**
 * What the vote sheet says, decided here and drawn by [VoteSheet].
 *
 * The composition renders and decides nothing, exactly as the swap sheet is split, so every
 * sentence this feature can show is assertable on a plain JVM out of the shipped strings.xml
 * rather than on a device.
 */
data class VoteSheetContent(
    val title: Copy,
    /** What is in flight, as a sentence. Never a spinner and never a bare label. */
    val phase: Copy?,
    /** The static bar a landed vote leads with, the way a landed swap does. */
    val bar: VoteBar?,
    /** The figures the sheet states: the weight, the fee and the collector, then the signature. */
    val cells: List<VoteCell>,
    /** The lede on the confirm step, or the sentence a refusal ends on. */
    val notice: Copy?,
    /**
     * The one weakness of a balance-weighted vote, stated where a voter can read it before acting
     * rather than only in the README. It appears on the confirm step and nowhere else: said once,
     * at the moment it is about to matter.
     */
    val disclosure: Copy?,
    val primary: VoteAction?,
    val secondary: VoteAction?,
    /**
     * Whether the sheet refuses to be dismissed.
     *
     * True for the signing round-trip alone. The wallet has the transaction and the call cannot be
     * taken back, so a swipe or a back press there would hide the receipt for a vote that landed.
     * A connect or a server call is cancelled by closing and costs nothing, so neither holds.
     */
    val holdsOpen: Boolean = false,
)

/** The label and the mono fragment of the bar over a landed vote. */
data class VoteBar(val label: Copy, val meta: Copy)

/** One cell of the fact grid. [copies] is the full text a tap puts on the clipboard. */
data class VoteCell(
    val label: Copy,
    val value: Copy,
    val span: Int = 1,
    val copies: String? = null,
)

data class VoteAction(val label: Copy, val kind: VoteActionKind)

enum class VoteActionKind { Confirm, Retry, Close }

/** Lamports' own on-chain precision: what the raw integer below is divided by to read as SOL. */
private const val LAMPORT_DECIMALS = 9

/**
 * The fee is shown to six decimals, not to all nine of [LAMPORT_DECIMALS]: see
 * `com.plainticker.mobile.ui.pass.PassSheetModel`'s own constant of the same name for the
 * arithmetic (`FactGrid`'s span-1 cell budget against the real `jetbrains_mono_medium.ttf` glyph
 * widths) behind why nine digits clips this same cell ("0.123456789 SOL", 15 characters, against
 * about 13 that fit) and six does not (12 characters at its own worst case). The two sheets share
 * the shape because they share the component, not by copying a number without checking it.
 */
private const val LAMPORT_DISPLAY_DECIMALS = 6

/**
 * The sheet for one state, or null when there is no sheet.
 *
 * Every state answers with a sentence. A running state answers with the sentence for what is
 * running, a refusal with its own, and the confirm step with the figure the signer is about to
 * approve. None of them answers with nothing.
 */
fun VoteState.sheet(): VoteSheetContent? = when (this) {
    is VoteState.Closed -> null

    is VoteState.Opening -> running(phase = words(phase.text), cells = emptyList())

    is VoteState.Building -> running(phase = words(phase.text), cells = listOf(weightCell(stakeRaw)))

    is VoteState.Signing ->
        running(phase = words(phase.text), cells = readyCells(stakeRaw, build.summary), holdsOpen = true)

    is VoteState.Ready -> VoteSheetContent(
        title = words(R.string.vote_title, symbol),
        phase = null,
        bar = null,
        cells = readyCells(stakeRaw, build.summary),
        // A confirm step that replaced a stale one leads with what happened to the tap that made
        // it, because the reader asked to send and is looking at the same button again. The lede
        // is what a first confirm step says, and it has been read by the time this one is drawn.
        notice = if (refreshed) words(R.string.vote_refreshed) else words(R.string.vote_lede, symbol),
        disclosure = words(R.string.vote_gameable),
        primary = VoteAction(words(R.string.vote_action), VoteActionKind.Confirm),
        secondary = VoteAction(words(R.string.action_close), VoteActionKind.Close),
    )

    is VoteState.Landed -> VoteSheetContent(
        title = words(R.string.vote_title, symbol),
        phase = null,
        bar = VoteBar(words(R.string.vote_landed_label), raw(Fmt.shortKey(signature))),
        cells = listOf(
            VoteCell(
                label = words(R.string.vote_weight_landed_label, symbol),
                value = raw(skr(stakeRaw)),
                span = 2,
            ),
            VoteCell(
                label = words(R.string.receipt_signature),
                value = raw(Fmt.shortKey(signature)),
                copies = signature,
            ),
        ),
        notice = words(R.string.vote_landed_note),
        disclosure = null,
        primary = null,
        secondary = VoteAction(words(R.string.action_close), VoteActionKind.Close),
    )

    is VoteState.Refused -> VoteSheetContent(
        title = words(R.string.vote_title, symbol),
        phase = null,
        bar = null,
        cells = emptyList(),
        notice = words(reason.text),
        disclosure = null,
        primary = if (reason.retryable) {
            VoteAction(words(R.string.action_retry), VoteActionKind.Retry)
        } else {
            null
        },
        secondary = VoteAction(words(R.string.action_close), VoteActionKind.Close),
    )
}

private fun VoteState.OnTicker.running(
    phase: Copy,
    cells: List<VoteCell>,
    holdsOpen: Boolean = false,
) = VoteSheetContent(
    title = words(R.string.vote_title, symbol),
    phase = phase,
    bar = null,
    cells = cells,
    notice = null,
    disclosure = null,
    primary = null,
    secondary = null,
    holdsOpen = holdsOpen,
)

/**
 * The three figures a signer is owed: what weight the vote carries, what the signature costs, and
 * where it goes. The collector is on the screen because a vote is only auditable if the address
 * its memos are counted from is named.
 *
 * The weight is the server's own figure wherever the server stated one, because that is the
 * figure the vote is counted at and the figure a person signs for has to be the one that counts
 * (docs/skr-curation-spec-2026-09-13.md, gap 3). The app's own bounded read stands in only where
 * the server has not spoken: the Building step, and a server that sent no `weight`.
 */
private fun readyCells(stakeRaw: Long, summary: VoteSummary) = listOf(
    weightCell(summary.weight ?: stakeRaw),
    VoteCell(
        label = words(R.string.vote_fee_label),
        value = words(
            R.string.vote_fee,
            Fmt.tokenAmount(summary.lamports, LAMPORT_DECIMALS, maxDecimals = LAMPORT_DISPLAY_DECIMALS),
        ),
    ),
    VoteCell(
        label = words(R.string.vote_collector_label),
        value = raw(Fmt.shortKey(summary.collector)),
        copies = summary.collector,
    ),
)

private fun weightCell(stakeRaw: Long) =
    VoteCell(label = words(R.string.vote_weight_label), value = raw(skr(stakeRaw)), span = 2)

/** The principal, exactly as the staking program counts it: six decimals, trimmed, grouped. */
private fun skr(stakeRaw: Long): String = Fmt.tokenAmount(stakeRaw, SkrStakeBound.SKR_DECIMALS)
