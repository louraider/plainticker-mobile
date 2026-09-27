@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.pass

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementResponse
import com.plainticker.mobile.data.plainticker.PassBuild
import com.plainticker.mobile.data.plainticker.PassSummary
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
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.SolscanAction
import com.plainticker.mobile.ui.text
import kotlinx.coroutines.launch

/**
 * The pay sheet: the same [AmberSheet] surface the vote and the swap use, drawing whatever
 * [PassSheetContent] the machine's state resolved to (task A6). It decides nothing and computes
 * nothing, exactly as [com.plainticker.mobile.ui.vote.VoteSheet] is split.
 *
 * Only the signing round-trip and the confirm call keep the sheet open: the wallet has the
 * transfer and the call cannot be taken back once it is sent, so a swipe there would hide the
 * receipt for a payment that did land. Every other state dismisses normally.
 */
data class PassActions(
    val onConfirm: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
fun PassSheet(state: PassState, actions: PassActions, modifier: Modifier = Modifier) {
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
        PassSheetBody(content = content, actions = actions)
    }
}

@Composable
internal fun ColumnScope.PassSheetBody(
    content: PassSheetContent,
    actions: PassActions,
    takeFocus: Boolean = true,
) {
    // This is money and entitlement, the other flow DESIGN.md section 1's "must keep working
    // identically in both themes" line names: paying for the pass this device's Pro state comes
    // from. A theme-following AmberColors replaces the fixed-dark Ink/Ink2/Elevated this sheet
    // drew unconditionally before this fix.
    val colors = defaultAmberColors()
    ConfirmOnSent(content.bar != null)
    val lead = leadFocus(takeFocus)

    content.bar?.let {
        LiveBar(label = it.label.text(), meta = it.meta.text(), live = false, modifier = lead.padding(top = BarTop))
    }

    Text(
        text = content.title.text(),
        // "Pay for Pro", AmberType.sectionHead (22/700, opsz 22): 118.25dp at 1.0x, 153.73dp at
        // 1.3x, inside the sheet's 360dp content width either way (fontTools, 2026-09-26).
        style = AmberType.sectionHead,
        color = colors.textPrimary,
        maxLines = 1,
        softWrap = false,
        modifier = (if (content.bar == null) lead else Modifier)
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = TitleTop)
            .semantics { heading() },
    )

    content.phase?.let { Sentence(it.text(), colors.textSecondary, PhaseTop) }

    if (content.cells.isNotEmpty()) {
        Spacer(Modifier.height(GridTop))
        FactGrid(
            cells = content.cells.map { it.factCell() },
            colors = colors,
            minCellHeight = CellHeight,
        )
    }

    content.notice?.let { Sentence(it.text(), colors.textSecondary, NoticeTop) }

    // The landed payment on a public explorer (judges' review, 2026-09-27).
    content.signature?.let {
        SolscanAction(signature = it, color = colors.actionText, modifier = Modifier.padding(start = Side, top = NoticeTop))
    }

    Column(
        modifier = Modifier.padding(start = Side, end = Side, top = ActionsTop, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        content.primary?.let { AmberPrimaryAction(label = it.label.text(), onClick = actions.of(it.kind)) }
        content.secondary?.let { AmberSecondaryAction(label = it.label.text(), onClick = actions.of(it.kind)) }
    }
}

private fun PassActions.of(kind: PassActionKind): () -> Unit = when (kind) {
    PassActionKind.Confirm -> onConfirm
    PassActionKind.Retry -> onRetry
    PassActionKind.Close -> onClose
}

@Composable
private fun Sentence(text: String, color: Color, top: Dp) {
    Text(
        text = text,
        style = AmberType.body,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = top),
    )
}

@Composable
private fun PassCell.factCell(): FactCell {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.receipt_copy_signature)
    val copied = copies
    return FactCell(
        label = label.text(),
        value = value.text(),
        span = span,
        valueSize = if (span > 1) 28.sp else 18.sp,
        // Every PassCell that carries something to copy (the signature, the destination) is an
        // on-chain identifier drawn from Fmt.shortKey; every one that carries nothing to copy
        // (the amount, the fee) is a number. The two happen to coincide exactly for this screen's
        // cells, so [copied] doubles as the signal FactCell.valueMono needs.
        valueMono = copied != null,
        onTap = if (copied == null) null else {
            { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, copied))) } }
        },
        tapLabel = if (copied == null) null else copyLabel,
    )
}

@Composable
private fun ConfirmOnSent(sent: Boolean) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(sent) {
        if (sent) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

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
private val ActionsTop = 24.dp
private val ActionGap = 10.dp
private val SheetBottom = 40.dp
private val CellHeight = 84.dp

// ---- Previews ------------------------------------------------------------------------------

private const val PreviewPayer = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"
private const val PreviewTreasuryAta = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
private const val PreviewTreasury = "E1STBTGEYpHanVG4HWUJHfzEu6eGbnE9mnGX9KnAmJdL"

private val PreviewBuild = PassBuild(
    transaction = "UkVEQUNURUQ=",
    summary = PassSummary(
        mint = "USDC",
        amount = 12_000_000L,
        destination = PreviewTreasuryAta,
        treasury = PreviewTreasury,
        lamports = 5_000L,
    ),
)

private val PreviewStates: List<PassState> = listOf(
    PassState.Opening(),
    PassState.Ready(PreviewPayer, PreviewBuild),
    PassState.Ready(PreviewPayer, PreviewBuild, refreshed = true),
    PassState.Landed("4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe", EntitlementResponse(pro = true, source = "pass")),
    PassState.Landed("4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe", entitlement = null),
    PassState.Refused(PassRefusal.NOT_OPEN),
)

private val PreviewActions = PassActions(onConfirm = {}, onRetry = {}, onClose = {})

@InstrumentPreviews
@Composable
private fun PassSheetPreview() {
    PreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PreviewStates.forEach { state ->
                AmberSheetSurface {
                    state.sheet()?.let { PassSheetBody(content = it, actions = PreviewActions, takeFocus = false) }
                }
            }
        }
    }
}
