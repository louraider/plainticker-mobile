@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.swap

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.rememberExplorerOpener
import com.plainticker.mobile.ui.components.AmberFact
import com.plainticker.mobile.ui.components.AmberFactRows
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSecondaryAction
import com.plainticker.mobile.ui.components.AmberSheet
import com.plainticker.mobile.ui.components.AmberSheetSurface
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.LiveBar
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.ResultHero
import com.plainticker.mobile.ui.components.SkeletonBar
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.resultAnnouncement
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The swap sheet and the receipt (task T10, design task DT7), on the one [AmberSheet] surface.
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
 * twice. Every finished attempt, landed, failed or not yet known, leads with [ResultBlock], and
 * the facts under it are Amber rows ([AmberFactRows]), not Instrument's bordered grid.
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
    /** The same amount again with a fresh quote, after a failure that offers it. */
    val onRetry: () -> Unit = {},
    /** From a fresh receipt, the other direction. */
    val onSwapBack: () -> Unit = {},
    /** From the Review step, the one way to the wallet. */
    val onContinue: () -> Unit = {},
    /**
     * Share, from a landed receipt. Null, every host's default today, draws no Share action at
     * all; a host that passes one (a picture of the receipt, say) gets the action beside "View on
     * Solscan" with nothing else on the sheet changing.
     */
    val onShare: (() -> Unit)? = null,
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
    AmberSheet(
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
    // This is money: the swap sheet and its receipt, DESIGN.md section 1's "must keep working
    // identically in both themes" line. Every colour below now reads a theme-following AmberColors
    // rather than the fixed-dark Ink/Ink2/Muted/Canvas/Elevated this sheet drew unconditionally
    // before this fix, which left the sheet dark regardless of the system setting.
    val colors = defaultAmberColors()
    val lead = leadFocus(takeFocus)

    content.debug?.let { DebugBand(it, colors) }

    val result = content.result
    if (result != null) {
        // A finished attempt leads with what happened, not with what the sheet was for: landed,
        // failed, or not known yet, each as its own moment, and each announced once.
        ResultBlock(result = result, pair = content.title, signature = content.receipt?.signature, lead = lead, colors = colors)
    } else {
        Title(content, lead, actions, colors)
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
        Sentence(field.balance, AmberType.meta, colors.textTertiary(AmberSurface.HIGH), BalanceTop)
    }

    // A result carries its own reason inside it; only a sheet still in progress draws the notice
    // on its own line.
    if (result == null) {
        content.notice?.let { Sentence(it, AmberType.context, colors.textSecondary, NoticeTop) }
    }

    Spacer(Modifier.height(BlockTop))
    when (content.costNotice) {
        CostNotice.AtTap -> Text(
            text = stringResource(CostNotice.AtTap.text),
            style = AmberType.meta,
            color = colors.textTertiary(AmberSurface.HIGH),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        )

        CostNotice.Loading -> CostSkeleton()
        // The group's own 16dp inset plus this 4dp is the sheet's 20dp side, so the rows line up
        // with every other block on the sheet (AmberFactRows measures its budget on that).
        null -> if (content.cells.isNotEmpty()) {
            AmberFactRows(
                facts = content.cells.map { it.fact() },
                colors = colors,
                modifier = Modifier.padding(horizontal = RowsInset),
            )
        }
    }

    // What this phone checked in the bytes, on the Review step and while the wallet is open (2026-09-27).
    content.checked?.let { Sentence(it, AmberType.context, colors.textSecondary, NoticeTop) }
    // The receipt's way to check it on the chain, under the signature and the slot, and beside it
    // the Share slot: drawn only when the model offers it (a landing) and a host passed
    // [SwapActions.onShare], which is where a share of the receipt plugs in.
    val explorerLabel = content.explorerLabel
    val share = content.share?.takeIf { actions.onShare != null }
    if (content.explorerUrl != null || share != null) {
        Row(modifier = Modifier.padding(start = Side, top = NoticeTop), horizontalArrangement = Arrangement.spacedBy(LinkGap)) {
            content.explorerUrl?.let { url -> if (explorerLabel != null) ExplorerLink(url, explorerLabel, colors) }
            share?.let {
                TextAction(label = it.label.text(), onClick = actions.of(it.kind), color = colors.actionText, contentPadding = FlipPadding)
            }
        }
    }

    Column(
        modifier = Modifier.padding(start = Side, end = Side, top = ActionsTop, bottom = SheetBottom),
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        content.primary?.let {
            AmberPrimaryAction(label = it.label.text(), onClick = actions.of(it.kind), enabled = it.enabled)
        }
        // "Swap back" sits under "View in Portfolio" on a fresh receipt, in the same secondary
        // style: two ways on, neither louder than the other.
        listOfNotNull(content.secondary, content.extra).forEach {
            AmberSecondaryAction(label = it.label.text(), onClick = actions.of(it.kind))
        }
        content.footnote?.let {
            Text(text = it.text(), style = AmberType.meta, color = colors.textTertiary(AmberSurface.HIGH))
        }
    }
}

/** The machine's method for a button the model already chose. The sheet decides nothing here. */
private fun SwapActions.of(kind: SheetActionKind): () -> Unit = when (kind) {
    SheetActionKind.Submit -> onSubmit
    SheetActionKind.Edit -> onEdit
    SheetActionKind.Close -> onClose
    SheetActionKind.ViewPortfolio -> onViewPortfolio
    SheetActionKind.Retry -> onRetry
    SheetActionKind.SwapBack -> onSwapBack
    SheetActionKind.Continue -> onContinue
    SheetActionKind.Share -> onShare ?: {}
}

// ---- The pieces ---------------------------------------------------------------------------------

/**
 * "USDC to TSLAx" with the other direction named in full beneath it. The flip is a text action
 * and not a control with an icon, which is also why no verb appears on it: "TSLAx to USDC" is a
 * direction, and this product has one trading verb.
 */
@Composable
private fun Title(content: SheetContent, lead: Modifier, actions: SwapActions, colors: AmberColors) {
    Column(Modifier.fillMaxWidth().padding(start = Side, end = Side, top = TitleTop)) {
        Text(
            text = content.title.text(),
            style = AmberType.sectionHead,
            color = colors.textPrimary,
            maxLines = 1,
            softWrap = false,
            modifier = lead.semantics { heading() },
        )
        content.flip?.let {
            TextAction(label = it.text(), onClick = actions.onFlip, color = colors.actionText, contentPadding = FlipPadding)
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
 * The finished attempt, as its own moment: the shared [ResultHero] (2026-09-29), the one the vote
 * sheet leads with too, so both results read the same way. The founder's first real receipt
 * (2026-09-24) put the outcome in a small amber "Landed" line; the Seeker report of 2026-09-29
 * found the replacement still too quiet (a 40dp ring beside a 22sp line). Now:
 *
 * - **Landed.** A 72dp amber disc whose ring closes and whose check strokes in, "Swap landed" in
 *   the largest words on the sheet, the fill as the hero figure, what arrived and how fast, and
 *   where it is now. One Confirm haptic, keyed on the signature.
 * - **Failed.** The closed caution ring with its sign, "Swap failed" in the caution colour, and
 *   the reason, which says what is certain about the money.
 * - **Pending.** The broken amber ring, static once drawn, "Sent, not confirmed yet", and what to
 *   do.
 *
 * Motion, haptic and accessibility live in [ResultHero] and are pinned there; this block only
 * hands it the model's words.
 */
@Composable
private fun ResultBlock(result: SheetResult, pair: Copy, signature: String?, lead: Modifier, colors: AmberColors) {
    val headline = result.headline.text()
    val sentences = listOfNotNull(result.detail, result.next).map { it.text() }
    val spoken = result.announcement.map { it.text() }
    ResultHero(
        tone = result.tone.mark,
        headline = headline,
        sentences = sentences,
        announcement = resultAnnouncement(spoken.first(), spoken.drop(1)),
        modifier = lead,
        eyebrow = pair.text(),
        figure = result.figure?.text(),
        hapticKey = signature,
        colors = colors,
    )
}

/**
 * What a debug build is, said in every state of the sheet. The page ground inside the sheet's own
 * surface, so it reads as a band the page shows through rather than as one more sentence about the
 * swap.
 */
@Composable
private fun DebugBand(text: Copy, colors: AmberColors) {
    Text(
        text = text.text(),
        style = AmberType.context,
        color = colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceGround)
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

/** "View on Solscan" under the receipt's cells: the landed transaction on a public explorer. */
@Composable
private fun ExplorerLink(url: String, label: Copy, colors: AmberColors) {
    val open = rememberExplorerOpener()
    TextAction(
        label = label.text(),
        onClick = { open(url) },
        color = colors.actionText,
        contentPadding = FlipPadding,
    )
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
 * A [SheetCell] as an Amber row. A cell that names what it copies becomes a handle: the receipt's
 * signature is the one, with no confirmation of its own, because the platform shows its own
 * whenever something reaches the clipboard. The signature fragment is the one value set in
 * JetBrains Mono, because it is an on-chain key; the slot is a number and reads in Bricolage with
 * every other figure (DESIGN.md section 3).
 */
@Composable
private fun SheetCell.fact(): AmberFact {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.receipt_copy_signature)
    val copied = copies
    return AmberFact(
        label = label.text(),
        value = value.text(),
        sub = sub?.text(),
        identifier = copied != null,
        subNumeric = subMono,
        onTap = if (copied == null) {
            null
        } else {
            { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, copied))) } }
        },
        tapLabel = if (copied == null) null else copyLabel,
    )
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
private val LinkGap = 8.dp
private val RowsInset = 4.dp
private val BandPadding = 10.dp
private val SkeletonGap = 12.dp
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
        AmberSheetSurface {
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
