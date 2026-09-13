@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.DisabledButton
import com.plainticker.mobile.ui.components.FactGrid
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.Gauge
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.ListRow
import com.plainticker.mobile.ui.components.LiveBar
import com.plainticker.mobile.ui.components.Panel
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.PrimaryButton
import com.plainticker.mobile.ui.components.SecondaryButton
import com.plainticker.mobile.ui.components.Sheet
import com.plainticker.mobile.ui.components.SheetSurface
import com.plainticker.mobile.ui.components.SignalRow
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.SkeletonSwitch
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TodayStrip
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.TopTabs
import com.plainticker.mobile.ui.components.Track
import com.plainticker.mobile.ui.swap.SheetContent
import com.plainticker.mobile.ui.swap.SwapActions
import com.plainticker.mobile.ui.swap.SwapAmount
import com.plainticker.mobile.ui.swap.SwapSheetBody
import com.plainticker.mobile.ui.swap.SwapState
import com.plainticker.mobile.ui.swap.sheet
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * Debug builds only: every component with the canvas sample data, in the Detail order, then the
 * pieces of the other screens, so the phone can be compared with the design/canvas artboards.
 * A long press on the wordmark, or "Close" in the banner, leaves.
 */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var amount by rememberSaveable { mutableStateOf("5.00") }
    var query by rememberSaveable { mutableStateOf("") }
    var live by rememberSaveable { mutableStateOf(true) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        TopBar(action = "Watch", onAction = {}, onTitleLongPress = onBack)
        Banner(text = "Debug gallery, sample data from the canvas", action = "Close", onAction = onBack)
        TopTabs(items = GallerySamples.tabs, selected = tab, onSelect = { tab = it })
        TodayStrip(text = GallerySamples.TODAY)

        // Detail: hero, price row, gauge, live bar.
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
            Text(
                text = GallerySamples.TICKER,
                style = PlainTickerType.heroTicker,
                color = Ink,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 64.sp, stepSize = 2.sp),
            )
            Text(
                text = GallerySamples.COMPANY,
                style = PlainTickerType.company,
                color = Ink2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Token price", style = PlainTickerType.label, color = Muted)
                Text(text = GallerySamples.PRICE, style = PlainTickerType.heroPrice, color = Ink, maxLines = 1, softWrap = false)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Text(text = "NYSE close", style = PlainTickerType.label, color = Muted)
                Text(text = GallerySamples.REFERENCE, style = PlainTickerType.referencePrice, color = Ink2, maxLines = 1, softWrap = false)
            }
        }
        Gauge(
            referenceLabel = "Token vs NYSE close",
            tracking = TrackingQuality.Tracked(GallerySamples.PREMIUM_PCT, poolUsd = GallerySamples.POOL_USD),
        )
        Spacer(Modifier.height(28.dp))
        LiveBar(label = "Live from the mint", meta = GallerySamples.LIVE_META, live = live)
        Row(Modifier.padding(start = 4.dp)) {
            TextAction(label = if (live) "Show stale" else "Show live", onClick = { live = !live })
        }

        Heading(text = "Backing and controls", topPadding = 14.dp)
        FactGrid(cells = GallerySamples.backing)

        Heading(text = "Against the sector", meta = GallerySamples.COMPOSITE)
        GallerySamples.tracks.forEach { Track(label = it.label, value = it.value, state = it.state, positionPct = it.positionPct) }
        Spacer(Modifier.height(14.dp))
        FactGrid(cells = GallerySamples.sector, minCellHeight = 88.dp)

        Heading(text = "F-Score")
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "8", style = PlainTickerType.fScoreNumeral, color = Ink, modifier = Modifier.alignByBaseline())
            Text(text = "of 9 signals", style = PlainTickerType.company, color = Muted, modifier = Modifier.alignByBaseline())
        }
        GallerySamples.signals.forEach { (name, ok) -> SignalRow(name = name, ok = ok) }

        Heading(text = "Method")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = GallerySamples.METHOD, style = PlainTickerType.body, color = Ink2)
            Text(text = GallerySamples.SOURCES, style = PlainTickerType.small, color = Muted)
        }
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(label = "Swap USDC to TSLAx", onClick = { sheetOpen = true })
            Text(text = GallerySamples.COST_LINE, style = PlainTickerType.meta, color = Muted)
            SecondaryButton(label = "View in Portfolio", onClick = {})
            DisabledButton(label = "Read the list")
        }

        // List: search field, analyzed rows, price-only rows, with the skeleton in front.
        Heading(text = "Search")
        Field(
            label = "Search",
            value = query,
            onValueChange = { query = it },
            placeholder = "Ticker or company",
            mono = false,
            action = if (query.isNotEmpty()) "Clear" else null,
            onAction = { query = "" },
        )
        Heading(text = "Analyzed", topPadding = 30.dp)
        Row(Modifier.padding(start = 4.dp)) {
            TextAction(label = if (loading) "Show rows" else "Show skeleton", onClick = { loading = !loading })
        }
        SkeletonSwitch(loading = loading, skeleton = { SkeletonRows(count = 6) }) {
            Column {
                GallerySamples.analyzed.forEachIndexed { index, row ->
                    ListRow(
                        ticker = row.ticker,
                        company = row.company,
                        meta = row.meta,
                        valueRight = row.value,
                        valueSub = row.valueSub,
                        divider = index < GallerySamples.analyzed.lastIndex,
                        onClick = {},
                    )
                }
            }
        }
        Heading(text = "Without analysis", topPadding = 28.dp)
        GallerySamples.priceOnly.forEachIndexed { index, row ->
            ListRow(
                ticker = row.ticker,
                company = row.company,
                meta = row.meta,
                valueRight = row.value,
                muted = true,
                divider = index < GallerySamples.priceOnly.lastIndex,
                onClick = {},
            )
        }

        // Portfolio: wallet fragment in the bar, total, holdings.
        Heading(text = "Holdings")
        TopBar(meta = GallerySamples.WALLET, insets = WindowInsets(0))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "\$1,289.01", style = PlainTickerType.bigValue, color = Ink, maxLines = 1, softWrap = false)
            Text(text = "3 xStocks, priced by Jupiter", style = PlainTickerType.small, color = Muted)
        }
        Spacer(Modifier.height(18.dp))
        GallerySamples.holdings.forEachIndexed { index, row ->
            ListRow(
                ticker = row.ticker,
                company = row.company,
                meta = row.meta,
                valueRight = row.value,
                divider = index < GallerySamples.holdings.lastIndex,
                onClick = {},
            )
        }
        Text(
            text = "Cost basis is not read from the chain.",
            style = PlainTickerType.small,
            color = Muted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )

        // Watchlist: rows with a trailing action, the digest panel.
        Heading(text = "Watched", topPadding = 30.dp)
        GallerySamples.watched.forEachIndexed { index, row ->
            ListRow(
                ticker = row.ticker,
                company = row.company,
                meta = row.meta,
                trailingAction = "Unwatch",
                onTrailingAction = {},
                divider = index < GallerySamples.watched.lastIndex,
                onClick = {},
            )
        }
        Heading(text = "Daily digest", topPadding = 30.dp)
        Panel {
            Text(text = GallerySamples.DIGEST_TIME, style = PlainTickerType.meta, color = Muted)
            Text(text = GallerySamples.DIGEST, style = PlainTickerType.panelBody, color = Ink)
        }

        // Swap sheet and receipt on the static surface; the same content opens modally above.
        Heading(text = "Amount field")
        Field(
            label = "Amount, USDC",
            value = amount,
            onValueChange = { amount = it },
            action = "Max",
            onAction = { amount = "10.00" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text(
            text = "Balance 10.00 USDC",
            style = PlainTickerType.meta,
            color = Muted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
        Heading(text = "Receipt, landed")
        SheetSurface { SwapSheetBody(content = receiptContent(), actions = GalleryNoActions, takeFocus = false) }
        Spacer(Modifier.height(48.dp))
    }

    if (sheetOpen) {
        // The real sheet over a real state, so a cell the product drops cannot live on here.
        Sheet(onDismissRequest = { sheetOpen = false }) {
            SwapSheetBody(
                content = amountContent(amount),
                actions = GalleryNoActions.copy(
                    onAmountChanged = { amount = it },
                    onMax = { amount = "20.2" },
                    onSubmit = { sheetOpen = false },
                    onClose = { sheetOpen = false },
                ),
            )
        }
    }
}

/** The amount step of the real machine, typed into. */
private fun amountContent(typed: String): SheetContent = SwapState.Amount(
    leg = GallerySamples.swapLeg,
    funds = GallerySamples.swapFunds.copy(tokenRaw = 1_360_437L),
    input = SwapAmount.parse(typed, USDC_DECIMALS, GallerySamples.swapFunds.usdcRaw),
).sheet(nowMillis = 0L, submitSwaps = true)!!

/** The landed receipt of the real machine, with a fill that beat its quote. */
private fun receiptContent(): SheetContent = SwapState.Landed(
    leg = GallerySamples.swapLeg,
    quote = GallerySamples.swapQuote,
    fill = GallerySamples.swapFill,
    requoted = false,
    timing = GallerySamples.swapTiming,
).sheet(nowMillis = 0L, submitSwaps = true)!!

private const val USDC_DECIMALS = 6

/** The gallery presses nothing; each screen wires these to its own view model. */
private val GalleryNoActions = SwapActions(
    onAmountChanged = {},
    onMax = {},
    onFlip = {},
    onSubmit = {},
    onEdit = {},
    onClose = {},
    onViewPortfolio = {},
)

@InstrumentPreviews
@Composable
private fun GalleryPreview() {
    PreviewCanvas {
        GalleryScreen(onBack = {})
    }
}
