@file:OptIn(ExperimentalMaterial3Api::class)

package com.myapp.ui.swap

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.BuildConfig
import com.myapp.R
import com.myapp.ui.Copy
import com.myapp.ui.components.FactCell
import com.myapp.ui.components.FactGrid
import com.myapp.ui.components.Field
import com.myapp.ui.components.InstrumentPreviews
import com.myapp.ui.components.LiveBar
import com.myapp.ui.components.PreviewCanvas
import com.myapp.ui.components.PrimaryButton
import com.myapp.ui.components.SecondaryButton
import com.myapp.ui.components.Sheet
import com.myapp.ui.components.SheetSurface
import com.myapp.ui.components.SkeletonBar
import com.myapp.ui.components.TextAction
import com.myapp.ui.text
import com.myapp.ui.theme.Canvas
import com.myapp.ui.theme.Elevated
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The swap sheet and the receipt (task T10, design task DT7), on the one [Sheet] surface.
 *
 * This file draws and nothing else. Every sentence and every number it renders was decided by
 * [SheetContent]; what is left here is anatomy, focus and one haptic. Four things are worth
 * knowing before editing it.
 *
 * **There is no slippage control, and there never was one.** Jupiter sets slippage dynamically
 * and the order comes back with `otherAmountThreshold` already in it, so the honest way to show
 * it is the worst case beside the estimate, which the cost block does. A reading tool's checkout
 * does not ask a person to tune basis points.
 *
 * **The receipt replaces the sheet rather than extending it.** Once the swap lands, the amount
 * field, the direction and the primary button are gone: nothing on that surface can be acted on
 * twice. Its live bar is static, because nothing there is still live (DESIGN.md section 6).
 *
 * **A debug build states what it is in every state of the sheet.** BuildConfig.SUBMIT_SWAPS is
 * false in debug, the machine stops at [SwapState.Signed], and the band under the handle says so
 * from the moment the sheet opens. This is the one screen where a comforting lie would be
 * unforgivable, so a debug build never draws a receipt at all: it has nothing to draw one from.
 *
 * **The sheet takes focus when it opens** and the phase is a polite live region, so a screen
 * reader lands on the sheet rather than on the screen behind it and then hears each phase once
 * (plan section 13 Pass 6). Every target here is a component that is already 48dp. Only the
 * component gallery, which draws the receipt inline beside everything else, asks for no focus.
 */

/** What the sheet can ask the machine to do. Every one of them is a [SwapViewModel] method. */
data class SwapActions(
    val onAmountChanged: (String) -> Unit,
    val onMax: () -> Unit,
    val onFlip: () -> Unit,
    val onSubmit: () -> Unit,
    val onEdit: () -> Unit,
    val onClose: () -> Unit,
    val onViewPortfolio: () -> Unit,
)

@Composable
fun SwapSheet(
    state: SwapState,
    actions: SwapActions,
    modifier: Modifier = Modifier,
    submitSwaps: Boolean = BuildConfig.SUBMIT_SWAPS,
) {
    val content = state.sheet(swapClock(state), submitSwaps) ?: return
    // A modal sheet hides FIRST and asks afterwards, so refusing the request is not enough on its
    // own: a swipe or a back press during POST /execute would leave the sheet hidden with the
    // machine still running, and open() refuses to reopen a busy machine, so the receipt for a
    // swap that did land would never be seen. The drag and the back press are refused instead.
    val landing = rememberUpdatedState(state is SwapState.Landing)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = remember { { target: SheetValue -> target != SheetValue.Hidden || !landing.value } },
    )
    Sheet(
        // A submission in flight cannot be taken back, and close() would cancel the call that is
        // carrying it. While it is landing the sheet stays put; every other state dismisses.
        onDismissRequest = { if (state !is SwapState.Landing) actions.onClose() },
        modifier = modifier,
        sheetState = sheetState,
    ) {
        SwapSheetBody(content = content, actions = actions)
    }
}

/**
 * The sheet's own anatomy, without the modal surface around it. Internal so the component
 * gallery can compose the real thing over a real state instead of keeping a second copy of the
 * layout that would drift the first time a cell changes.
 */
@Composable
internal fun ColumnScope.SwapSheetBody(
    content: SheetContent,
    actions: SwapActions,
    takeFocus: Boolean = true,
) {
    ConfirmOnLanded(content.receipt?.signature)
    val lead = leadFocus(takeFocus)

    content.debug?.let { DebugBand(it) }

    if (content.isReceipt) {
        // The receipt leads with what happened, not with what the sheet was for.
        content.phase?.let { Phase(it, lead) }
        content.receipt?.let { Received(it) }
    } else {
        Title(content, lead, actions)
        content.phase?.let { Phase(it, Modifier) }
    }

    content.field?.let { field ->
        Spacer(Modifier.height(FieldTop))
        Field(
            label = field.label.text(),
            value = field.value,
            onValueChange = actions.onAmountChanged,
            action = field.action.text(),
            onAction = actions.onMax,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Sentence(field.balance, PlainTickerType.meta, Muted, BalanceTop)
    }

    content.notice?.let { Sentence(it, PlainTickerType.small, Ink2, NoticeTop) }

    Spacer(Modifier.height(BlockTop))
    when (content.costNotice) {
        CostNotice.AtTap -> Text(
            text = stringResource(CostNotice.AtTap.text),
            style = PlainTickerType.small,
            color = Muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        )

        CostNotice.Loading -> CostSkeleton()
        null -> FactGrid(cells = content.cells.map { it.factCell() }, surface = Elevated, minCellHeight = CellHeight)
    }

    Column(
        modifier = Modifier.padding(start = Side, end = Side, top = ActionsTop, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        content.primary?.let {
            PrimaryButton(label = it.label.text(), onClick = actions.of(it.kind), enabled = it.enabled)
        }
        content.secondary?.let {
            SecondaryButton(label = it.label.text(), onClick = actions.of(it.kind))
        }
        content.footnote?.let { Text(text = it.text(), style = PlainTickerType.small, color = Muted) }
    }
}

/** The machine's method for a button the model already chose. The sheet decides nothing here. */
private fun SwapActions.of(kind: SheetActionKind): () -> Unit = when (kind) {
    SheetActionKind.Submit -> onSubmit
    SheetActionKind.Edit -> onEdit
    SheetActionKind.Close -> onClose
    SheetActionKind.ViewPortfolio -> onViewPortfolio
}

// ---- The pieces ---------------------------------------------------------------------------------

/**
 * "USDC to TSLAx" with the other direction named in full beneath it. The flip is a text action
 * and not a control with an icon, which is also why no verb appears on it: "TSLAx to USDC" is a
 * direction, and this product has one trading verb.
 */
@Composable
private fun Title(content: SheetContent, lead: Modifier, actions: SwapActions) {
    Column(Modifier.fillMaxWidth().padding(start = Side, end = Side, top = TitleTop)) {
        Text(
            text = content.title.text(),
            style = PlainTickerType.sheetTitle,
            color = Ink,
            maxLines = 1,
            softWrap = false,
            modifier = lead.semantics { heading() },
        )
        content.flip?.let {
            TextAction(label = it.text(), onClick = actions.onFlip, contentPadding = FlipPadding)
        }
    }
}

/** The phase, as the live bar: breathing while a call is in flight, static once it is not. */
@Composable
private fun Phase(phase: SheetPhase, lead: Modifier) {
    LiveBar(
        label = phase.label.text(),
        meta = phase.meta?.text().orEmpty(),
        live = phase.live,
        // The ticking seconds are in the label; the region is announced by what phase it is, so
        // a reader is not interrupted once a second (plan section 13 Pass 6).
        announcement = phase.announcement.text(),
        modifier = lead.padding(top = PhaseTop),
    )
}

/**
 * The receipt's headline: what actually arrived. The one 40sp numeral on this surface, with the
 * ticker beside it on the same baseline at the fact size, so the pair stays on one line at 1.3x.
 */
@Composable
private fun Received(receipt: SheetReceipt) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = ReceivedTop)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(ReceivedGap),
    ) {
        Text(text = receipt.label.text(), style = PlainTickerType.label, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(ReceivedGap), verticalAlignment = Alignment.Bottom) {
            Text(
                text = receipt.amount.text(),
                style = PlainTickerType.bigValue,
                color = Ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = receipt.symbol,
                style = PlainTickerType.trackValue,
                color = Ink2,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * What a debug build is, said in every state of the sheet. Canvas inside an Elevated sheet, so it
 * reads as a band the page shows through rather than as one more sentence about the swap.
 */
@Composable
private fun DebugBand(text: Copy) {
    Text(
        text = text.text(),
        style = PlainTickerType.label,
        color = Ink2,
        modifier = Modifier
            .fillMaxWidth()
            .background(Canvas)
            .padding(horizontal = Side, vertical = BandPadding),
    )
}

/** The cost block while the order is in flight: bars, never a spinner (DESIGN.md section 4). */
@Composable
private fun CostSkeleton() {
    val loading = stringResource(CostNotice.Loading.text)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Side)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(SkeletonGap),
    ) {
        SkeletonBar(width = 180.dp, height = 28.dp)
        SkeletonBar(width = 120.dp)
        SkeletonBar(width = 150.dp)
    }
}

/** One line of the sheet, in the style and colour its slot fixes. */
@Composable
private fun Sentence(text: Copy, style: TextStyle, color: Color, top: Dp) {
    Text(
        text = text.text(),
        style = style,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = top),
    )
}

/**
 * One Confirm haptic when the swap lands, and exactly one: the effect is keyed on the signature,
 * so a recomposition, a rotation or a second collection of the same state does not buzz again.
 * Nothing else on this screen has a haptic, and nothing at all has a sound (DESIGN.md section 6).
 */
@Composable
private fun ConfirmOnLanded(signature: String?) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(signature) {
        if (signature != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

/**
 * The modifier for whatever the sheet leads with, already asked for focus (Pass 6). Focus is an
 * affordance and not a state of the swap: a requester whose node has gone is not worth crashing
 * a screen that may have money on it, so a failed request is dropped.
 */
@Composable
private fun leadFocus(takeFocus: Boolean): Modifier {
    val opened = remember { FocusRequester() }
    LaunchedEffect(takeFocus) { if (takeFocus) runCatching { opened.requestFocus() } }
    return Modifier.focusRequester(opened).focusable()
}

/**
 * A [SheetCell] as the grid draws it. A cell that names what it copies becomes a handle: the
 * receipt's signature is the one, with no confirmation of its own, because the platform shows
 * its own whenever something reaches the clipboard.
 */
@Composable
private fun SheetCell.factCell(): FactCell {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.receipt_copy_signature)
    val copied = copies
    return FactCell(
        label = label.text(),
        value = value.text(),
        sub = sub?.text(),
        span = span,
        subMono = subMono,
        valueSize = size.valueSize(),
        onTap = if (copied == null) {
            null
        } else {
            { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, copied))) } }
        },
        tapLabel = if (copied == null) null else copyLabel,
    )
}

private fun SheetCellSize.valueSize(): TextUnit = when (this) {
    SheetCellSize.Headline -> 28.sp
    SheetCellSize.Normal -> 22.sp
    SheetCellSize.Fragment -> 18.sp
}

/**
 * The wall clock, re-read once a second while a phase is in flight or a quote is counting down.
 *
 * It lives in the composition rather than in [SwapViewModel] because it is a property of drawing
 * a running state, not of the machine: the machine measures each phase when that phase ends, and
 * a sheet nobody is looking at costs nothing. Read once, the elapsed line would stick at "0 s".
 */
@Composable
private fun swapClock(state: SwapState): Long {
    val moving = state.needsAClock
    val now by produceState(System.currentTimeMillis(), state, moving) {
        while (moving) {
            value = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    return now
}

// ---- Measurements -------------------------------------------------------------------------------

private const val TICK_MILLIS = 1_000L

private val Side = 20.dp
private val TitleTop = 8.dp
private val PhaseTop = 8.dp
private val FieldTop = 14.dp
private val BalanceTop = 10.dp
private val NoticeTop = 12.dp
private val BlockTop = 22.dp
private val ActionsTop = 24.dp
private val ActionGap = 10.dp
private val SheetBottom = 40.dp
private val ReceivedTop = 22.dp
private val ReceivedGap = 6.dp
private val BandPadding = 10.dp
private val SkeletonGap = 12.dp
private val CellHeight = 84.dp
private val FlipPadding = PaddingValues(start = 0.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)

// ---- Previews -----------------------------------------------------------------------------------

private val PreviewActions = SwapActions(
    onAmountChanged = {},
    onMax = {},
    onFlip = {},
    onSubmit = {},
    onEdit = {},
    onClose = {},
    onViewPortfolio = {},
)

private val PreviewLeg = SwapLeg.into(SwapToken("XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB", "TSLAx", 8))

/** The demo wallet as measured on 2026-09-12: 20.2 USDC, 0.096 SOL, no account for the mint. */
private val PreviewFunds = SwapFunds(owner = "owner", lamports = 96_000_000L, usdcRaw = 20_200_000L, tokenRaw = 0L)

/** The 2026-09-12 order, field for field: 5 USDC in, no expiry, rent for a first token account. */
private val PreviewQuote = SwapQuote(
    requestId = "preview",
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

private val PreviewTiming = SwapTiming(
    startedAtMillis = 0L,
    phaseStartedAtMillis = 0L,
    quotingMillis = 310L,
    walletMillis = 12_700L,
    landingMillis = 3_100L,
)

private fun previewInput() = SwapAmount.parse("5", 6, PreviewFunds.usdcRaw)

@Composable
private fun SheetPreview(state: SwapState, submitSwaps: Boolean = true) {
    PreviewCanvas {
        SheetSurface {
            SwapSheetBody(
                content = state.sheet(nowMillis = 9_000L, submitSwaps = submitSwaps)!!,
                actions = PreviewActions,
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun SwapAmountPreview() {
    SheetPreview(
        SwapState.Amount(
            leg = PreviewLeg,
            funds = PreviewFunds.copy(tokenRaw = 1_360_437L),
            input = previewInput(),
        )
    )
}

@InstrumentPreviews
@Composable
private fun SwapWalletPreview() {
    SheetPreview(
        SwapState.AwaitingWallet(
            leg = PreviewLeg,
            funds = PreviewFunds,
            input = previewInput(),
            quote = PreviewQuote,
            requote = false,
            timing = PreviewTiming,
        )
    )
}

@InstrumentPreviews
@Composable
private fun SwapShortfallPreview() {
    SheetPreview(
        SwapState.Shortfall(
            leg = PreviewLeg,
            funds = PreviewFunds.copy(lamports = 1_000_000L),
            input = previewInput(),
            quote = PreviewQuote,
            timing = PreviewTiming,
        )
    )
}

@InstrumentPreviews
@Composable
private fun SwapReceiptPreview() {
    SheetPreview(
        SwapState.Landed(
            leg = PreviewLeg,
            quote = PreviewQuote,
            fill = SwapFill(
                signature = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe",
                inAmountRaw = 5_000_000L,
                outAmountRaw = 1_360_940L,
                slot = 445_912_340L,
            ),
            requoted = false,
            timing = PreviewTiming,
        )
    )
}

@InstrumentPreviews
@Composable
private fun SwapDebugSignedPreview() {
    SheetPreview(
        SwapState.Signed(leg = PreviewLeg, quote = PreviewQuote, requoted = false, timing = PreviewTiming),
        submitSwaps = false,
    )
}
