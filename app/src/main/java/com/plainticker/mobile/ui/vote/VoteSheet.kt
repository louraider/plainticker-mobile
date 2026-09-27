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
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSecondaryAction
import com.plainticker.mobile.ui.components.AmberSheet
import com.plainticker.mobile.ui.components.AmberSheetSurface
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactGrid
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.LiveBar
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.rememberShareText
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import kotlinx.coroutines.launch

/**
 * The vote sheet: the same [AmberSheet] surface the swap and pass now also reach for, drawing
 * whatever [VoteSheetContent] the machine's state resolved to. It decides nothing, computes
 * nothing, and states no figure the model did not hand it.
 *
 * There is no new component family here. The action that opens it is a [TextAction][
 * com.plainticker.mobile.ui.components.TextAction] on a row and on Detail, and what it opens is
 * Amber's sheet, [FactGrid] (still Instrument's, see [VoteSheetBody]'s own note) and
 * [AmberPrimaryAction].
 *
 * Only the signing round-trip holds the sheet open. The wallet has the transaction and the call
 * cannot be taken back, so a swipe or a back press during it would hide the receipt for a vote
 * that did land. Every other state, a connect and a server call included, dismisses normally.
 */
data class VoteActions(
    val onConfirm: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
    /**
     * Where the landed vote's plain sentence goes. Null, every host's default, is the system share
     * sheet ([com.plainticker.mobile.ui.shareText]); a preview or a test hands its own.
     */
    val onShare: ((String) -> Unit)? = null,
)

@Composable
fun VoteSheet(state: VoteState, actions: VoteActions, modifier: Modifier = Modifier) {
    val content = state.sheet() ?: return
    val held = rememberUpdatedState(content.holdsOpen)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = remember { { target: SheetValue -> target != SheetValue.Hidden || !held.value } },
    )
    AmberSheet(
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
 *
 * **[FactGrid] stays Instrument's own component, not restyled here.** It is not one of the six
 * Amber components this task's brief names (the ticker row, `AmberFigure`, the section head, the
 * primary action, the sheet, chips). Its surface and its cell text now both read a theme-following
 * `AmberColors` by default (`ui/components/FactGrid.kt`), and this call site still pins its own
 * `surface` explicitly to [colors]' own `surfaceHigh`, the same fill [AmberSheet] gives the sheet
 * around them, rather than Instrument's Elevated. [LiveBar] and [AmberSecondaryAction] read the
 * same theme-following default. [AmberSecondaryAction] itself used to be a private copy in
 * `YouScreen.kt`; the "Close" button below now reaches for the shared one rather than Instrument's
 * retired `SecondaryButton` or a forked one-off.
 */
@Composable
internal fun ColumnScope.VoteSheetBody(
    content: VoteSheetContent,
    actions: VoteActions,
    takeFocus: Boolean = true,
) {
    val colors = defaultAmberColors()
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
        style = AmberSheetTitle,
        color = colors.textPrimary,
        maxLines = 1,
        softWrap = false,
        modifier = (if (content.bar == null) lead else Modifier)
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = TitleTop)
            .semantics { heading() },
    )

    // What is in flight, always as a sentence. This app draws no spinners (DESIGN.md section 8),
    // so the phase is the only thing that says a round-trip is happening, and it has to say which.
    content.phase?.let { Sentence(it.text(), AmberType.body, colors.textSecondary, PhaseTop) }

    if (content.cells.isNotEmpty()) {
        Spacer(Modifier.height(GridTop))
        FactGrid(
            cells = content.cells.map { it.factCell() },
            colors = colors,
            surface = colors.surfaceHigh,
            minCellHeight = CellHeight,
        )
    }

    content.notice?.let { Sentence(it.text(), AmberType.body, colors.textSecondary, NoticeTop) }

    // The weakness of a balance-weighted vote, set in the metadata face under the figure it is
    // about. It is never [colors.stateCaution]: DESIGN.md section 7 keeps that colour
    // for an issuer control the mint actually carries, and this is a property of the mechanism,
    // not a flag. `textTertiary(HIGH)` promotes to `textSecondary` on this sheet's own surface,
    // so the contrast rule holds without the call site having to know that.
    content.disclosure?.let {
        Sentence(it.text(), AmberType.meta, colors.textTertiary(AmberSurface.HIGH), DisclosureTop)
    }

    Column(
        modifier = Modifier.padding(start = Side, end = Side, top = ActionsTop, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        val share = content.shareText?.text()
        val systemShare = rememberShareText()
        val onShare = actions.onShare ?: systemShare
        content.primary?.let {
            AmberPrimaryAction(label = it.label.text(), onClick = actions.of(it.kind, share, onShare))
        }
        content.secondary?.let {
            AmberSecondaryAction(label = it.label.text(), onClick = actions.of(it.kind, share, onShare))
        }
    }
}

/** The machine's method for a button the model already chose. The sheet decides nothing here. */
private fun VoteActions.of(kind: VoteActionKind, share: String?, onShare: (String) -> Unit): () -> Unit = when (kind) {
    VoteActionKind.Confirm -> onConfirm
    VoteActionKind.Retry -> onRetry
    VoteActionKind.Close -> onClose
    VoteActionKind.Share -> ({ share?.let(onShare) })
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
        // Every VoteCell that carries something to copy (the signature, the collector) is an
        // on-chain identifier drawn from Fmt.shortKey; the weight and the fee, which never carry
        // one, are numbers. [copied] already distinguishes exactly this for the screen's own tap
        // handling, so it doubles as the signal FactCell.valueMono needs.
        valueMono = copied != null,
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
 * The sheet title ("Vote to cover NFLXx"), the size Instrument's own `sheetTitle` (22sp) drew it
 * at: a word style, not [com.plainticker.mobile.ui.components.AmberFigure]'s tabular
 * [AmberType.figureLarge], because a ticker beside ordinary words is a phrase, not a number.
 * `AmberType.sectionHead` is 22sp already; reused as is rather than declaring a near-duplicate.
 */
private val AmberSheetTitle: TextStyle = AmberType.sectionHead

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
    VoteState.Ready("NFLX", "NFLXx", PreviewCollector, PreviewStakeRaw, PreviewBuild, refreshed = true),
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
                AmberSheetSurface {
                    state.sheet()?.let { VoteSheetBody(content = it, actions = PreviewActions, takeFocus = false) }
                }
            }
        }
    }
}
