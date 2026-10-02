package com.plainticker.mobile.ui.portfolio

import com.plainticker.mobile.ui.share.rememberSwapShare
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.ui.Explorer
import com.plainticker.mobile.ui.rememberExplorerOpener
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberFigure
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.rememberMotionEnabled
import com.plainticker.mobile.ui.components.spoken
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.theme.JetBrainsMono
import com.plainticker.mobile.ui.swap.SwapActions
import com.plainticker.mobile.ui.swap.SwapSheet
import com.plainticker.mobile.ui.swap.SwapToken
import com.plainticker.mobile.ui.swap.SwapViewModel
import com.plainticker.mobile.wallet.WalletAccount

/**
 * The Portfolio (T11, DT8; restyled to Amber, docs/design-research-2026-09-21.md section 5.3/5.5).
 * One scrolling column: the header the host hands in, the one banner slot, "Holdings" with the
 * total and the positions, the cost basis footnote, then "Recent swaps" from this device's own
 * receipts. What changed in this pass is what every row and figure is drawn with, not what the
 * screen says or in what order: [PortfolioModel] still picks every sentence and
 * [PortfolioViewModel] still picks every number, so there is still no arithmetic in this file.
 *
 * **Amber components used.** The total is [AmberFigure] (the "number with its context" component,
 * this screen's whole argument); every position, recorded holding and landed swap is
 * [AmberTickerRow]; both headings are [AmberSectionHead]; the one forward action a state ever
 * offers (Connect wallet, Browse analyzed stocks) is [AmberPrimaryAction], never a text link (the
 * rule that survives every pass of this redesign).
 *
 * **The receipt that must keep drawing.** The real mainnet swap recorded 13 September and every
 * cold open since (docs/design-research-2026-09-13 review finding 9; [PortfolioColdOpenTest])
 * still lands in the same place, under the same lede, before the same figures: nothing about
 * which sentence wins or in what order changed, only the row anatomy under it.
 *
 * **The 16dp tonal container without a second LazyColumn.** Amber's own anatomy puts a run of
 * ticker rows inside one 16dp rounded, gapped tonal container ([com.plainticker.mobile.ui.components.AmberTickerRowGroup]).
 * That component wraps a plain [Column], which does not compose with three separate
 * `itemsIndexed` lists inside one [LazyColumn] the way this screen's pinned tests
 * ([PortfolioScreenTest], [PortfolioColdOpenTest]) read the composition (`itemsIndexed(state.recorded,
 * key = ...)` is itself a pinned substring). [AmberRowFrame] gets the same look a different way:
 * each row still is its own lazy item, but only the first and last row of a run round their outer
 * corners, and every row but the last leaves a 1dp [AmberColors.surfaceGround] seam below it, the
 * same colour the group's own background would show through. The visual result is identical; the
 * list structure PortfolioColdOpenTest reads is untouched.
 *
 * **Components that stay Instrument for this pass, on purpose.** [Banner] (the one state-banner
 * slot) and [SkeletonRows] (the cold-open loading placeholder) take no colour parameter and are
 * not in this task's file set (the shared `ui/components` package); Amber has not restyled them
 * yet either (DESIGN.md section 4 lists them as "not yet restyled"). Both are peripheral,
 * transient states, not the money-carrying content this pass exists to restyle, so they are left
 * exactly as they are rather than forked quietly into a second, parallel implementation.
 *
 * The wallet's own staked SKR and this device's Pro entitlement left this screen for You
 * (docs/plan-app-uiux-2026-09-21.md, task U1); [PassViewModel] and its sheet went with them.
 */
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseList: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
    /**
     * The swap machine this screen's "Swap to USDC" opens, scoped to the home entry like every
     * other ViewModel there, so a swap in flight survives a destination switch. Null in previews.
     */
    swapViewModel: SwapViewModel? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PortfolioContent(
        state = state,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onRefresh = viewModel::refresh,
        onOpenDetail = onOpenDetail,
        onBrowseList = onBrowseList,
        modifier = modifier,
        header = header,
        onSwapOut = swapViewModel?.let { swap -> { token: SwapToken -> swap.openOut(token) } },
    )
    if (swapViewModel != null) {
        val swap by swapViewModel.state.collectAsStateWithLifecycle()
        // A modal surface in its own window, so where it sits here does not matter. The receipt's
        // "View in Portfolio" is this screen, so it only closes the sheet; the balance behind it
        // is read again by the landing itself (PortfolioViewModel watches the receipts).
        SwapSheet(
            state = swap,
            actions = SwapActions(
                onAmountChanged = swapViewModel::amountChanged,
                onMax = swapViewModel::useMax,
                onFlip = swapViewModel::flip,
                onSubmit = swapViewModel::submit,
                onEdit = swapViewModel::edit,
                onClose = swapViewModel::close,
                onViewPortfolio = swapViewModel::close,
                onRetry = swapViewModel::retry,
                onSwapBack = swapViewModel::swapBack,
                onContinue = swapViewModel::continueToWallet,
                onShare = rememberSwapShare(swap),
            ),
        )
    }
}

@Composable
internal fun PortfolioContent(
    state: PortfolioUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBrowseList: (() -> Unit)?,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    /** "Swap to USDC" for a holding the chain read found; null where no swap can be opened. */
    onSwapOut: ((SwapToken) -> Unit)? = null,
) {
    val colors = amberColors()
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.surfaceGround),
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "chrome") {
            Column(Modifier.fillMaxWidth()) {
                state.banner?.let { StateBanner(banner = it, onRetry = onRefresh) }
            }
        }
        item(key = "holdings") {
            Column(Modifier.fillMaxWidth()) {
                AmberSectionHead(title = stringResource(R.string.portfolio_heading_holdings), colors = colors)
                state.account?.let { WalletKeyLine(key = Fmt.shortKey(it.address), colors = colors) }
            }
        }

        when {
            // The wallet's own screen is open. Nothing this app can say is more use than saying so.
            state.phase == WalletPhase.CONNECTING ->
                item(key = "connecting") { EmptyLine(stringResource(R.string.portfolio_connecting), colors) }

            // The app's own record, standing where the chain's answer would be (design review
            // 2026-09-13, finding 9 and its related note). The order is DESIGN.md section 1.1's,
            // learned on Detail below the liquidity floor: the sentence that says what a number
            // is stands above the number, never under it, so a reader cannot take the quantity
            // for a balance and be corrected afterwards.
            state.showsRecorded -> {
                item(key = "recorded-lede") { Lede(stringResource(R.string.portfolio_recorded_lede), colors) }
                item(key = "recorded-actions") {
                    if (state.connected) {
                        WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect, colors = colors)
                    } else {
                        ConnectAction(onConnect = onConnect, colors = colors)
                    }
                }
                itemsIndexed(state.recorded, key = { _, holding -> "rec:" + holding.mint }) { index, holding ->
                    Recorded(
                        holding = holding,
                        first = index == 0,
                        last = index == state.recorded.lastIndex,
                        colors = colors,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            // The one forward action this state offers, so it is a 56dp button rather than a
            // text link (U2: no state's only forward action is a TextAction).
            !state.connected -> {
                item(key = "connect") { EmptyLine(stringResource(R.string.portfolio_not_connected), colors) }
                item(key = "connect-action") {
                    AmberPrimaryAction(
                        label = stringResource(R.string.action_connect_wallet),
                        onClick = onConnect,
                        colors = colors,
                        modifier = Modifier.padding(horizontal = Side, vertical = ButtonTop),
                    )
                }
            }

            state.isCold -> item(key = "skeleton") { SkeletonRows(count = SkeletonRowCount) }

            // A fresh wallet holds nothing, which is the common case and not an error. It gets a
            // sentence that says what would put something here, and the way to go and read first,
            // as a 56dp button (U2), the same rule the state above keeps.
            state.isEmpty -> {
                item(key = "empty") { EmptyLine(stringResource(R.string.portfolio_empty), colors) }
                if (onBrowseList != null) {
                    item(key = "browse-action") {
                        AmberPrimaryAction(
                            label = stringResource(R.string.action_browse_analyzed),
                            onClick = onBrowseList,
                            colors = colors,
                            modifier = Modifier.padding(horizontal = Side, vertical = ButtonTop),
                        )
                    }
                }
                item(key = "wallet") { WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect, colors = colors) }
            }

            // Connected, nothing drawn, and not an empty wallet either: a source this screen needs
            // did not answer. The banner above carries that sentence and its Retry, so the body
            // adds only the way to ask again rather than repeating it in other words.
            state.positions.isEmpty() ->
                item(key = "wallet") { WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect, colors = colors) }

            else -> {
                item(key = "total") { Total(state) }
                item(key = "wallet") { WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect, colors = colors) }
                itemsIndexed(state.positions, key = { _, position -> "h:" + position.mint }) { index, position ->
                    Holding(
                        position = position,
                        first = index == 0,
                        last = index == state.positions.lastIndex,
                        colors = colors,
                        onOpenDetail = onOpenDetail,
                        swapOut = onSwapOut?.let { open -> swapOutToken(position, state)?.let { token -> { open(token) } } },
                    )
                }
                item(key = "cost-basis") { Footnote(stringResource(R.string.portfolio_cost_basis), colors) }
            }
        }

        // This device's own record, and the one part of the screen that needs neither a wallet nor
        // a chain read to draw: a landing this app saw is a landing whether or not it can reach
        // the network now.
        if (state.receipts.isNotEmpty()) {
            item(key = "swaps") { AmberSectionHead(title = stringResource(R.string.portfolio_heading_recent_swaps), colors = colors) }
            itemsIndexed(state.receipts, key = { _, receipt -> "r:" + receipt.signature }) { index, receipt ->
                Swap(receipt = receipt, first = index == 0, last = index == state.receipts.lastIndex, colors = colors)
            }
            item(key = "swaps-note") { Footnote(stringResource(R.string.portfolio_receipts_note), colors) }
        }
    }
}

/** Dark by default, light when the system asks for it: Amber ships both (DESIGN.md section 2). */
@Composable
private fun amberColors(): AmberColors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors

/**
 * The total, as [AmberFigure] draws a card's headline figure: 34/700 tnum amber, the sentence that
 * says what it covers underneath in [AmberColors.textSecondary]. Kept to the exact call shape
 * `Total(state)` (PortfolioScreenTest pins the substring), so colours are resolved inside rather
 * than threaded in as a parameter.
 *
 * Marked as a heading, so a screen reader can jump to the figure the screen exists to show, and it
 * speaks as one sentence rather than as a number and an orphan phrase.
 *
 * **Motion.** This is the one figure the whole screen argues from, so it is the one place on this
 * screen the research's "quick, 150ms fade" (docs/design-research-2026-09-21.md section 4) means
 * something rather than decorating a row a reader would read the same way either way. Gated by
 * [rememberMotionEnabled]: at animator scale 0 (the smoke script's own setting) the fade snaps to
 * fully opaque on the first frame, so nothing here is ever readable only because it finished
 * animating.
 */
@Composable
private fun Total(state: PortfolioUiState) {
    val colors = amberColors()
    val block = totalBlock(state)
    // No total at all means nothing on screen could be valued: the neutral placeholder every
    // other screen uses, never a zero, which would read as a wallet that holds nothing.
    val value = block.value ?: stringResource(R.string.value_missing)
    val sub = block.sub.text()
    val spokenTotal = stringResource(R.string.portfolio_total_a11y, spoken(value), sub)
    val motion = rememberMotionEnabled()
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = if (motion) tween(durationMillis = 150, easing = LinearOutSlowInEasing) else snap(),
        label = "portfolio-total-reveal",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer { this.alpha = alpha }
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = spokenTotal
            },
    ) {
        AmberFigure(figure = value, context = sub, colors = colors)
    }
}

/**
 * One row's own share of Amber's 16dp tonal container (docs/design-research-2026-09-21.md section
 * 5.5, "rows in a 16dp tonal container, 1dp gap"): only the first and last row of a run round
 * their outer corners; every row but the last leaves a 1dp [AmberColors.surfaceGround] seam below
 * it. See this file's own top doc comment for why this is built per row instead of by wrapping the
 * whole run in [com.plainticker.mobile.ui.components.AmberTickerRowGroup].
 */
@Composable
private fun AmberRowFrame(
    first: Boolean,
    last: Boolean,
    colors: AmberColors,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 16.dp else 0.dp,
        topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp,
        bottomEnd = if (last) 16.dp else 0.dp,
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(Modifier.clip(shape)) { content() }
        if (!last) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.surfaceGround))
        }
    }
}

/**
 * One position: the dollar value as the row's figure, the shares held and the tracking half as its
 * context line. The tracking half is the list's, from the same rule and the same strings, so a
 * pool below the floor reads identically on both screens.
 */
@Composable
private fun Holding(
    position: PortfolioPosition,
    first: Boolean,
    last: Boolean,
    colors: AmberColors,
    onOpenDetail: (String) -> Unit,
    /** Opens "Swap to USDC" for this holding; null when the row must not offer it. */
    swapOut: (() -> Unit)? = null,
) {
    val row = holdingRow(position)
    val quantity = row.quantity.text()
    val tracking = row.tracking?.text()
    val meta = tracking?.let { stringResource(R.string.list_row_meta_join, quantity, it) } ?: quantity
    AmberRowFrame(first = first, last = last, colors = colors) {
        Column(Modifier.fillMaxWidth().background(colors.surfaceRaised)) {
            AmberTickerRow(
                ticker = row.symbol,
                company = row.company,
                figure = row.value,
                context = meta,
                colors = colors,
                onClick = { onOpenDetail(row.ticker) },
                onClickLabel = stringResource(R.string.action_open_ticker, row.symbol),
                description = sentence(row.symbol, row.company, quantity, tracking, row.value),
            )
            if (swapOut != null) SwapOutLine(symbol = row.symbol, onClick = swapOut, colors = colors)
        }
    }
}

/**
 * "Swap to USDC", on a line of its own under the holding it acts on, never on the row's meta line.
 * Measured against the real fonts (2026-09-24, SwapResultFitTest): on the meta line, beside
 * a value like "$12,345.67", the action would leave the quantity 119.55dp at 1.0x and 61.82dp at
 * 1.3x, and "1.37 TSLAx, +0.09%" already needs 124.73dp and 162.14dp, so the quantity itself would
 * ellipsize at 1.3x. On its own line the label (96.73dp, 125.74dp at 1.3x, Bricolage 600 opsz 14) has
 * the row's whole 336dp, and the row above keeps every budget AmberTickerRow already proves.
 */
@Composable
private fun SwapOutLine(symbol: String, onClick: () -> Unit, colors: AmberColors) {
    val spoken = stringResource(R.string.portfolio_swap_to_usdc_a11y, symbol)
    Row(Modifier.fillMaxWidth()) {
        TextAction(
            label = stringResource(R.string.portfolio_swap_to_usdc),
            onClick = onClick,
            color = colors.actionText,
            contentPadding = SwapOutPadding,
            modifier = Modifier.semantics { contentDescription = spoken },
        )
    }
}

/** 2dp under the row above and 14dp below; TextAction's own 48dp minimum makes the target. */
private val SwapOutPadding = PaddingValues(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 14.dp)

/**
 * One recorded holding: the quantity as the row's figure (the app's own record carries no value),
 * the symbol and company on the left, when the swap behind it landed as the context line.
 *
 * No value and no premium, because neither was read: what this row claims is exactly what the app
 * wrote down. The row opens Detail only once the catalog has named the underlying ticker; without a
 * name there is no route, and a dead tap is worse than a row that does not offer one.
 */
@Composable
private fun Recorded(
    holding: RecordedHolding,
    first: Boolean,
    last: Boolean,
    colors: AmberColors,
    onOpenDetail: (String) -> Unit,
) {
    val row = recordedRow(holding)
    val meta = row.meta.text()
    AmberRowFrame(first = first, last = last, colors = colors) {
        AmberTickerRow(
            ticker = row.symbol,
            company = row.company,
            figure = row.quantity,
            context = meta,
            colors = colors,
            onClick = row.ticker?.let { ticker -> { onOpenDetail(ticker) } },
            onClickLabel = stringResource(R.string.action_open_ticker, row.symbol),
            description = sentence(row.symbol, row.company, row.quantity, meta),
        )
    }
}

/**
 * One landed swap: what was paid on the left, what came back beside it, the cost and when it
 * landed as the context line. DESIGN.md section 3, under Amber: a number lives in Bricolage with
 * tabular figures, and JetBrains Mono is for on-chain identifiers only, so the received amount no
 * longer needs Instrument's `companyMono` escape hatch into the numeral face; it reads as the
 * ordinary prose sentence the model already composed it as ("to 0.01364 TSLAx").
 */
@Composable
private fun Swap(receipt: SwapReceipt, first: Boolean, last: Boolean, colors: AmberColors) {
    val row = swapRow(receipt)
    val paid = row.paid.text()
    val received = row.received.text()
    val cost = row.cost.text()
    val landed = row.landed.text()
    // The landed transaction on a public explorer (mock judges' review, 2026-09-27), as the row's
    // trailing action: a fixed short word, the width AmberTickerRow's meta line budgets for.
    val explorer = Explorer.transaction(receipt.signature)
    val open = rememberExplorerOpener()
    AmberRowFrame(first = first, last = last, colors = colors) {
        AmberTickerRow(
            ticker = paid,
            company = received,
            context = stringResource(R.string.portfolio_swap_row_meta, cost, landed),
            trailingAction = explorer?.let { stringResource(R.string.action_solscan) },
            onTrailingAction = explorer?.let { url -> { open(url) } },
            colors = colors,
            // Device QA of 1.3.16: a one-line meta let Solscan follow the text while two-line rows
            // pushed it right, so the column of actions zigzagged; and "UTC" wrapped onto a line
            // of its own. The cost and the time now take a line each and the action sits at the end.
            trailingActionAtEnd = true,
            description = sentence(paid, received, cost, landed),
        )
    }
}

/** Refresh, and the way back out of the wallet. Two text actions, 48dp each, right aligned. */
@Composable
private fun WalletActions(onRefresh: () -> Unit, onDisconnect: () -> Unit, colors: AmberColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        horizontalArrangement = Arrangement.End,
    ) {
        TextAction(label = stringResource(R.string.action_refresh), onClick = onRefresh, color = colors.actionText)
        TextAction(label = stringResource(R.string.action_disconnect), onClick = onDisconnect, color = colors.actionText)
    }
}

/**
 * The way into the wallet, under the sentence that explains why it is offered: [AmberPrimaryAction]
 * (U2), the one forward action this state exists to offer, never a text link.
 */
@Composable
private fun ConnectAction(onConnect: () -> Unit, colors: AmberColors) {
    AmberPrimaryAction(
        label = stringResource(R.string.action_connect_wallet),
        onClick = onConnect,
        colors = colors,
        modifier = Modifier.padding(horizontal = Side, vertical = ButtonTop),
    )
}

/**
 * The wallet's own short key, in the mono face DESIGN.md section 3 reserves for on-chain
 * identifiers under Amber, beside the Holdings heading it names (the same rule You's own
 * WalletBlock already keeps for this exact string).
 */
@Composable
private fun WalletKeyLine(key: String, colors: AmberColors) {
    Text(
        text = key,
        style = WalletKeyStyle,
        color = colors.textSecondary,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

private val WalletKeyStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 18.sp)

/**
 * The sentence that qualifies the figures below it: full width, body in [AmberColors.textPrimary],
 * above them.
 *
 * Not a [Footnote] and not an [EmptyLine]. A footnote is muted and arrives after the number, and
 * the whole finding this screen was changed for is that a caveat printed under a figure is read
 * after the reader has already believed the figure (DESIGN.md section 1.1).
 */
@Composable
private fun Lede(text: String, colors: AmberColors) {
    Text(
        text = text,
        style = AmberType.body,
        color = colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = 4.dp, bottom = 14.dp),
    )
}

/**
 * One sentence where the rows would be, so no state of this screen is a blank column. The forward
 * action that answers it, when there is one, is its own [AmberPrimaryAction] below (U2), never
 * drawn inline as a trailing text link.
 */
@Composable
private fun EmptyLine(text: String, colors: AmberColors) {
    Text(
        text = text,
        style = AmberType.body,
        color = colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side, vertical = EmptyLineGap),
    )
}

/** A closing line under a section: what the screen is not showing, and why. */
@Composable
private fun Footnote(text: String, colors: AmberColors) {
    Text(
        text = text,
        style = AmberType.meta,
        color = colors.textTertiary(AmberSurface.GROUND),
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 16.dp),
    )
}

/**
 * The one slot under the tabs; [PortfolioUiState.banner] has already picked which state wins.
 * [Banner] itself is unchanged Instrument (see this file's top doc comment): it takes no colour
 * parameter and is not in this task's file set.
 */
@Composable
private fun StateBanner(banner: PortfolioBanner, onRetry: () -> Unit) {
    val retry = stringResource(R.string.action_retry)
    val text = bannerText(banner).text()
    if (bannerRetries(banner)) {
        Banner(text = text, action = retry, onAction = onRetry)
    } else {
        Banner(text = text)
    }
}

/**
 * The parts of a row as one spoken phrase, in the order they are drawn, each numeral turned into
 * words ("+0.09%" reads "plus 0.09 percent"). A part that is not on the row is not spoken.
 */
private fun sentence(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(", ") { spoken(it.orEmpty()) }

private val Side = 20.dp
/** Vertical centering for an EmptyLine's sentence; unrelated to Heading's own rhythm (U6). */
private val EmptyLineGap = 30.dp
private val ButtonTop = 8.dp
private const val SkeletonRowCount = 3

// ---- Previews ------------------------------------------------------------------------------

private fun samplePosition(
    symbol: String,
    ticker: String,
    company: String,
    amountRaw: Long,
    price: Double?,
    reference: Double? = null,
    multiplier: Double? = 1.0,
    decimals: Int? = 8,
    poolUsd: Double? = 250_000.0,
) = PortfolioPosition(
    symbol = symbol,
    ticker = ticker,
    company = company,
    mint = ticker,
    amountRaw = amountRaw,
    decimals = decimals,
    multiplier = multiplier,
    priceUsd = price,
    referencePriceUsd = reference,
    poolUsd = poolUsd,
)

private fun sampleReceipt(
    signature: String,
    paidRaw: Long,
    receivedRaw: Long?,
    symbol: String,
    cost: Double?,
    landedAtMillis: Long,
) = SwapReceipt(
    signature = signature,
    inputMint = "usdc",
    inputSymbol = "USDC",
    inputAmountRaw = paidRaw,
    inputDecimals = 6,
    outputMint = symbol,
    outputSymbol = symbol,
    outputAmountRaw = receivedRaw,
    outputDecimals = 8,
    routeCostPct = cost,
    route = "Metis",
    landedAtMillis = landedAtMillis,
)

private val PreviewAccount = WalletAccount(publicKey = ByteArray(32) { 7 }, label = "Seed Vault Wallet")

private val PreviewState = PortfolioUiState(
    phase = WalletPhase.CONNECTED,
    account = PreviewAccount,
    settled = true,
    positions = listOf(
        samplePosition("TSLAx", "TSLA", "Tesla, Inc.", 201_364_000L, 366.17, 365.84),
        samplePosition("NVDAx", "NVDA", "NVIDIA Corp.", 210_000_000L, 182.11, 182.18),
        // A mint the forwarder could not read: no quantity, no value, and it says so.
        samplePosition("AAPLx", "AAPL", "Apple Inc.", 80_000_000L, 232.54, 232.52, multiplier = null, decimals = null),
        // Below the liquidity floor, as APPx read live on 2026-09-12: a pool of $34.
        samplePosition("APPx", "APP", "AppLovin Corp.", 50_000_000L, 1_158.76, 612.00, poolUsd = 34.0),
        // Jupiter prices nothing for it: the quantity stands, the value does not.
        samplePosition("MCDx", "MCD", "McDonald Corp.", 120_000_000L, null),
    ),
    // The three positions that could be valued: 737.33 plus 382.43 plus 579.38.
    totalUsd = 1_699.14,
    receipts = listOf(
        sampleReceipt("5xY1", 5_000_000L, 1_364_000L, "TSLAx", 0.09, 1_789_045_020_000L),
        sampleReceipt("9pLd", 20_000_000L, null, "NVDAx", null, 1_788_440_220_000L),
    ),
)

@InstrumentPreviews
@Composable
private fun PortfolioPreview() {
    AmberPreviewCanvas {
        PortfolioContent(
            state = PreviewState,
            onConnect = {},
            onDisconnect = {},
            onRefresh = {},
            onOpenDetail = {},
            onBrowseList = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun PortfolioDisconnectedPreview() {
    AmberPreviewCanvas {
        PortfolioContent(
            state = PortfolioUiState(
                receipts = PreviewState.receipts,
                recorded = listOf(
                    RecordedHolding(
                        mint = "TSLAx",
                        symbol = "TSLAx",
                        ticker = "TSLA",
                        company = "Tesla, Inc.",
                        amountRaw = 1_364_000L,
                        decimals = 8,
                        landedAtMillis = 1_789_045_020_000L,
                    ),
                ),
            ),
            onConnect = {},
            onDisconnect = {},
            onRefresh = {},
            onOpenDetail = {},
            onBrowseList = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun PortfolioEmptyPreview() {
    AmberPreviewCanvas {
        PortfolioContent(
            state = PortfolioUiState(
                phase = WalletPhase.CONNECTED,
                account = PreviewAccount,
                settled = true,
            ),
            onConnect = {},
            onDisconnect = {},
            onRefresh = {},
            onOpenDetail = {},
            onBrowseList = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun PortfolioChainUnavailablePreview() {
    AmberPreviewCanvas {
        PortfolioContent(
            state = PortfolioUiState(
                phase = WalletPhase.CONNECTED,
                account = PreviewAccount,
                settled = true,
                chainUnavailable = true,
            ),
            onConnect = {},
            onDisconnect = {},
            onRefresh = {},
            onOpenDetail = {},
            onBrowseList = {},
        )
    }
}
