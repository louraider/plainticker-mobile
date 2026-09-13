@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.vote

import android.content.ClipData
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.VoteBuild
import com.plainticker.mobile.data.plainticker.VoteSummary
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactGrid
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.LiveBar
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.PrimaryButton
import com.plainticker.mobile.ui.components.SecondaryButton
import com.plainticker.mobile.ui.components.Sheet
import com.plainticker.mobile.ui.components.SheetSurface
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.Elevated
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType
import kotlinx.coroutines.launch

/**
 * The vote sheet: the same [Sheet] surface the swap uses, drawing whatever [VoteSheetContent] the
 * machine's state resolved to. It decides nothing, computes nothing, and states no figure the
 * model did not hand it.
 *
 * There is no new component family here. The action that opens it is a [TextAction][
 * com.plainticker.mobile.ui.components.TextAction] on a row and on Detail, and what it opens is
 * the bar, the grid and the two buttons every other wallet flow in this app is made of.
 *
 * Only the signing round-trip holds the sheet open. The wallet has the transaction and the call
 * cannot be taken back, so a swipe or a back press during it would hide the receipt for a vote
 * that did land. Every other state, a connect and a server call included, dismisses normally.
 */
data class VoteActions(
    val onConfirm: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
fun VoteSheet(state: VoteState, actions: VoteActions, modifier: Modifier = Modifier) {
    val content = state.sheet() ?: return
    val held = rememberUpdatedState(content.holdsOpen)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = remember { { target: SheetValue -> target != SheetValue.Hidden || !held.value } },
    )
    Sheet(
        onDismissRequest = { if (!content.holdsOpen) actions.onClose() },
        modifier = modifier,
        sheetState = sheetState,
    ) {
        VoteSheetBody(content = content, actions = actions)
    }
}

/**
 * The sheet's anatomy without the modal around it, so the component gallery and the previews
 * compose the real thing over a real state instead of keeping a second copy of the layout.
 */
@Composable
internal fun ColumnScope.VoteSheetBody(
    content: VoteSheetContent,
    actions: VoteActions,
    takeFocus: Boolean = true,
) {
    ConfirmOnSent(content.bar != null)
    val lead = leadFocus(takeFocus)

    content.bar?.let {
        // Static, like the swap receipt's bar: the vote is sent and nothing is still moving.
        LiveBar(
            label = it.label.text(),
            meta = it.meta.text(),
            live = false,
            modifier = lead.padding(top = BarTop),
        )
    }

    Text(
        text = content.title.text(),
        style = PlainTickerType.sheetTitle,
        color = Ink,
        maxLines = 1,
        softWrap = false,
        modifier = (if (content.bar == null) lead else Modifier)
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = TitleTop)
            .semantics { heading() },
    )

    // What is in flight, always as a sentence. This app draws no spinners (DESIGN.md section 8),
    // so the phase is the only thing that says a round-trip is happening, and it has to say which.
    content.phase?.let { Sentence(it.text(), PlainTickerType.body, Ink2, PhaseTop) }

    if (content.cells.isNotEmpty()) {
        Spacer(Modifier.height(GridTop))
        FactGrid(
            cells = content.cells.map { it.factCell() },
            surface = Elevated,
            minCellHeight = CellHeight,
        )
    }

    content.notice?.let { Sentence(it.text(), PlainTickerType.body, Ink2, NoticeTop) }

    // The weakness of a balance-weighted vote, set in the metadata face under the figure it is
    // about. It is Muted and never Caution: DESIGN.md section 2 keeps that colour for an issuer
    // control the mint actually carries, and this is a property of the mechanism, not a flag.
    content.disclosure?.let { Sentence(it.text(), PlainTickerType.small, Muted, DisclosureTop) }

    Column(
        modifier = Modifier.padding(start = Side, end = Side, top = ActionsTop, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        content.primary?.let {
            PrimaryButton(label = it.label.text(), onClick = actions.of(it.kind))
        }
        content.secondary?.let {
            SecondaryButton(label = it.label.text(), onClick = actions.of(it.kind))
        }
    }
}

/** The machine's method for a button the model already chose. The sheet decides nothing here. */
private fun VoteActions.of(kind: VoteActionKind): () -> Unit = when (kind) {
    VoteActionKind.Confirm -> onConfirm
    VoteActionKind.Retry -> onRetry
    VoteActionKind.Close -> onClose
}

@Composable
private fun Sentence(text: String, style: TextStyle, color: Color, top: Dp) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = top),
    )
}

/**
 * A [VoteCell] as the grid draws it. A cell that names what it copies becomes a handle: the
 * signature and the collector are both worth carrying off this screen, and neither gets a
 * confirmation of its own because the platform shows one whenever something reaches the clipboard.
 */
@Composable
private fun VoteCell.factCell(): FactCell {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.receipt_copy_signature)
    val copied = copies
    return FactCell(
        label = label.text(),
        value = value.text(),
        span = span,
        valueSize = if (span > 1) 28.sp else 18.sp,
        onTap = if (copied == null) {
            null
        } else {
            { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, copied))) } }
        },
        tapLabel = if (copied == null) null else copyLabel,
    )
}

/** One Confirm haptic when the vote lands, and exactly one: the effect is keyed on [sent]. */
@Composable
private fun ConfirmOnSent(sent: Boolean) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(sent) {
        if (sent) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

/**
 * The modifier for whatever the sheet leads with, already asked for focus. Focus is an affordance
 * and not a state of the vote, so a requester whose node has gone is dropped rather than thrown.
 */
@Composable
private fun leadFocus(takeFocus: Boolean): Modifier {
    val opened = remember { FocusRequester() }
    LaunchedEffect(takeFocus) { if (takeFocus) runCatching { opened.requestFocus() } }
    return Modifier.focusRequester(opened).focusable()
}

// ---- Measurements ------------------------------------------------------------------------------

private val Side = 20.dp
private val BarTop = 8.dp
private val TitleTop = 14.dp
private val PhaseTop = 12.dp
private val GridTop = 20.dp
private val NoticeTop = 16.dp
private val DisclosureTop = 12.dp
private val ActionsTop = 24.dp
private val ActionGap = 10.dp
private val SheetBottom = 40.dp
private val CellHeight = 84.dp

/**
 * The measured stake of the wallet the SKR read was proved with on 2026-09-13: 31,209.870777 SKR
 * of principal, which is exactly what the forwarder returned through its 8-byte slice.
 */
private const val PreviewStakeRaw = 31_209_870_777L

/** The public demo wallet. The founder's own address appears in no file in this repository. */
private const val PreviewCollector = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"

private val PreviewBuild = VoteBuild(
    transaction = "UkVEQUNURUQ=",
    summary = VoteSummary(ticker = "NFLX", lamports = 5_000L, collector = PreviewCollector),
)

private val PreviewStates: List<VoteState> = listOf(
    VoteState.Opening("NFLX", "NFLXx", VotePhase.CONNECTING),
    VoteState.Ready("NFLX", "NFLXx", PreviewCollector, PreviewStakeRaw, PreviewBuild),
    VoteState.Landed("NFLX", "NFLXx", PreviewStakeRaw, "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe"),
    VoteState.Refused("NFLX", "NFLXx", VoteRefusal.NOT_OPEN),
)

private val PreviewActions = VoteActions(onConfirm = {}, onRetry = {}, onClose = {})

@InstrumentPreviews
@Composable
private fun VoteSheetPreview() {
    PreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PreviewStates.forEach { state ->
                SheetSurface {
                    state.sheet()?.let { VoteSheetBody(content = it, actions = PreviewActions, takeFocus = false) }
                }
            }
        }
    }
}
