package com.myapp.ui.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.R
import com.myapp.data.receipts.SwapReceipt
import com.myapp.ui.Fmt
import com.myapp.ui.components.Banner
import com.myapp.ui.components.Heading
import com.myapp.ui.components.InstrumentPreviews
// The row component and this screen's row model share a name; the anatomy keeps an alias.
import com.myapp.ui.components.ListRow as InstrumentRow
import com.myapp.ui.components.PreviewCanvas
import com.myapp.ui.components.SkeletonRows
import com.myapp.ui.components.TextAction
import com.myapp.ui.components.spoken
import com.myapp.ui.text
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType
import com.myapp.wallet.WalletAccount

/**
 * The Portfolio (T11, DT8; design/canvas/instrument.py screen_portfolio). One scrolling column:
 * the header the host hands in, the one banner slot, "Holdings" with the total and the positions,
 * the cost basis footnote, then "Recent swaps" from this device's own receipts.
 *
 * What the screen decides is nothing: [PortfolioModel] picks every sentence and [PortfolioViewModel]
 * every number, so there is no arithmetic in this file and no premium computed in a composable
 * (the same rule the Detail screen keeps). What it does own is the anatomy: a 64dp row per
 * position, the total as a heading a screen reader can jump to, one spoken sentence per row, and
 * 48dp on every target.
 *
 * The footnote is not decoration. The chain carries no cost basis, so there is no profit, no loss
 * and no change-since figure anywhere on this screen, and the line under the holdings says that
 * plainly rather than leaving a reader to assume the app simply forgot.
 */
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseList: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
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
    )
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
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
            Heading(
                text = stringResource(R.string.portfolio_heading_holdings),
                meta = state.account?.let { Fmt.shortKey(it.address) },
                topPadding = HeadingTopGap,
            )
        }

        when {
            // The wallet's own screen is open. Nothing this app can say is more use than saying so.
            state.phase == WalletPhase.CONNECTING ->
                item(key = "connecting") { EmptyLine(stringResource(R.string.portfolio_connecting)) }

            !state.connected -> item(key = "connect") {
                EmptyLine(
                    text = stringResource(R.string.portfolio_not_connected),
                    action = stringResource(R.string.action_connect_wallet),
                    onAction = onConnect,
                )
            }

            state.isCold -> item(key = "skeleton") { SkeletonRows(count = SkeletonRowCount) }

            // A fresh wallet holds nothing, which is the common case and not an error. It gets a
            // sentence that says what would put something here, and the way to go and read first.
            state.isEmpty -> {
                item(key = "empty") {
                    EmptyLine(
                        text = stringResource(R.string.portfolio_empty),
                        action = onBrowseList?.let { stringResource(R.string.action_browse_analyzed) },
                        onAction = onBrowseList,
                    )
                }
                item(key = "wallet") { WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect) }
            }

            // Connected, nothing drawn, and not an empty wallet either: a source this screen needs
            // did not answer. The banner above carries that sentence and its Retry, so the body
            // adds only the way to ask again rather than repeating it in other words.
            state.positions.isEmpty() ->
                item(key = "wallet") { WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect) }

            else -> {
                item(key = "total") { Total(state) }
                item(key = "wallet") { WalletActions(onRefresh = onRefresh, onDisconnect = onDisconnect) }
                itemsIndexed(state.positions, key = { _, position -> "h:" + position.mint }) { index, position ->
                    Holding(
                        position = position,
                        last = index == state.positions.lastIndex,
                        onOpenDetail = onOpenDetail,
                    )
                }
                item(key = "cost-basis") { Footnote(stringResource(R.string.portfolio_cost_basis)) }
            }
        }

        // This device's own record, and the one part of the screen that needs neither a wallet nor
        // a chain read to draw: a landing this app saw is a landing whether or not it can reach
        // the network now.
        if (state.receipts.isNotEmpty()) {
            item(key = "swaps") {
                Heading(
                    text = stringResource(R.string.portfolio_heading_recent_swaps),
                    topPadding = SectionTopGap,
                )
            }
            itemsIndexed(state.receipts, key = { _, receipt -> "r:" + receipt.signature }) { index, receipt ->
                Swap(receipt = receipt, last = index == state.receipts.lastIndex)
            }
            item(key = "swaps-note") { Footnote(stringResource(R.string.portfolio_receipts_note)) }
        }
    }
}

/**
 * The total, as the canvas draws it: the number alone in mono 40 with one line under it saying
 * what it covers. It is marked as a heading, so a screen reader can jump to the figure the screen
 * exists to show, and it speaks as one sentence rather than as a number and an orphan phrase.
 */
@Composable
private fun Total(state: PortfolioUiState) {
    val block = totalBlock(state)
    // No total at all means nothing on screen could be valued: the neutral placeholder every
    // other screen uses, never a zero, which would read as a wallet that holds nothing.
    val value = block.value ?: stringResource(R.string.value_missing)
    val sub = block.sub.text()
    val spokenTotal = stringResource(R.string.portfolio_total_a11y, spoken(value), sub)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, bottom = 6.dp)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = spokenTotal
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = value,
            style = PlainTickerType.bigValue,
            color = Ink,
            maxLines = 1,
            softWrap = false,
        )
        Text(text = sub, style = PlainTickerType.small, color = Muted)
    }
}

/**
 * One position: the token left, the value right, and a meta line carrying the shares held and
 * what the quote is worth. The tracking half is the list's, from the same rule and the same
 * strings, so a pool below the floor reads identically on both screens.
 */
@Composable
private fun Holding(position: PortfolioPosition, last: Boolean, onOpenDetail: (String) -> Unit) {
    val row = holdingRow(position)
    val quantity = row.quantity.text()
    val tracking = row.tracking?.text()
    val meta = tracking?.let { stringResource(R.string.list_row_meta_join, quantity, it) } ?: quantity
    InstrumentRow(
        ticker = row.symbol,
        company = row.company,
        meta = meta,
        valueRight = row.value,
        divider = !last,
        onClick = { onOpenDetail(row.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, row.symbol),
        description = sentence(row.symbol, row.company, quantity, tracking, row.value),
    )
}

/** One landed swap: what was paid, what came back, the cost actually paid and when. */
@Composable
private fun Swap(receipt: SwapReceipt, last: Boolean) {
    val row = swapRow(receipt)
    val paid = row.paid.text()
    val received = row.received.text()
    val cost = row.cost.text()
    val landed = row.landed.text()
    InstrumentRow(
        ticker = paid,
        company = received,
        // What came back is an amount and a ticker: numerals never sit in the UI face.
        companyMono = true,
        meta = stringResource(R.string.portfolio_swap_row_meta, cost, landed),
        divider = !last,
        description = sentence(paid, received, cost, landed),
    )
}

/** Refresh, and the way back out of the wallet. Two text actions, 48dp each, right aligned. */
@Composable
private fun WalletActions(onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        horizontalArrangement = Arrangement.End,
    ) {
        TextAction(label = stringResource(R.string.action_refresh), onClick = onRefresh)
        TextAction(label = stringResource(R.string.action_disconnect), onClick = onDisconnect)
    }
}

/** One sentence where the rows would be, so no state of this screen is a blank column. */
@Composable
private fun EmptyLine(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = PlainTickerType.body,
            color = Ink2,
            modifier = Modifier.weight(1f).padding(vertical = HeadingTopGap),
        )
        if (action != null && onAction != null) TextAction(label = action, onClick = onAction)
    }
}

/** A closing line under a section: what the screen is not showing, and why. */
@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = PlainTickerType.small,
        color = Muted,
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 16.dp),
    )
}

/** The one slot under the tabs; [PortfolioUiState.banner] has already picked which state wins. */
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
private val HeadingTopGap = 30.dp
private val SectionTopGap = 28.dp
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
    allInCostPct = cost,
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
    PreviewCanvas {
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
    PreviewCanvas {
        PortfolioContent(
            state = PortfolioUiState(receipts = PreviewState.receipts),
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
    PreviewCanvas {
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
    PreviewCanvas {
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
