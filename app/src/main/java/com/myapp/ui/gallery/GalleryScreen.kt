@file:OptIn(ExperimentalMaterial3Api::class)

package com.myapp.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import com.myapp.ui.components.Banner
import com.myapp.ui.components.DisabledButton
import com.myapp.ui.components.FactGrid
import com.myapp.ui.components.Field
import com.myapp.ui.components.Gauge
import com.myapp.ui.components.Heading
import com.myapp.ui.components.InstrumentPreviews
import com.myapp.ui.components.ListRow
import com.myapp.ui.components.LiveBar
import com.myapp.ui.components.Panel
import com.myapp.ui.components.PreviewCanvas
import com.myapp.ui.components.PrimaryButton
import com.myapp.ui.components.SecondaryButton
import com.myapp.ui.components.Sheet
import com.myapp.ui.components.SheetSurface
import com.myapp.ui.components.SignalRow
import com.myapp.ui.components.SkeletonRows
import com.myapp.ui.components.SkeletonSwitch
import com.myapp.ui.components.TextAction
import com.myapp.ui.components.TodayStrip
import com.myapp.ui.components.TopBar
import com.myapp.ui.components.TopTabs
import com.myapp.ui.components.Track
import com.myapp.ui.theme.Canvas
import com.myapp.ui.theme.Elevated
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

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
            tokenPremiumPct = GallerySamples.PREMIUM_PCT,
            premiumText = GallerySamples.PREMIUM_TEXT,
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
        SheetSurface { ReceiptSample() }
        Spacer(Modifier.height(48.dp))
    }

    if (sheetOpen) {
        Sheet(onDismissRequest = { sheetOpen = false }) {
            SwapSheetSample(amount = amount, onAmount = { amount = it }, onSwap = { sheetOpen = false })
        }
    }
}

@Composable
private fun ColumnScope.SwapSheetSample(
    amount: String,
    onAmount: (String) -> Unit,
    onSwap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "USDC to TSLAx", style = PlainTickerType.sheetTitle, color = Ink)
        TextAction(
            label = "TSLAx to USDC",
            onClick = {},
            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 0.dp, bottom = 10.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Field(
        label = "Amount, USDC",
        value = amount,
        onValueChange = onAmount,
        action = "Max",
        onAction = { onAmount("10.00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    Text(
        text = "Balance 10.00 USDC",
        style = PlainTickerType.meta,
        color = Muted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
    )
    Spacer(Modifier.height(22.dp))
    FactGrid(cells = GallerySamples.swapQuote, surface = Elevated, minCellHeight = 0.dp)
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryButton(label = "Swap USDC to TSLAx", onClick = onSwap)
        Text(text = "Signs in Seed Vault Wallet. Not investment advice.", style = PlainTickerType.small, color = Muted)
    }
}

@Composable
private fun ColumnScope.ReceiptSample() {
    Box(Modifier.padding(top = 8.dp)) {
        LiveBar(label = "Landed", meta = "confirmed in 3.1 s", live = false)
    }
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "Received", style = PlainTickerType.label, color = Muted)
        Text(text = "0.01364 TSLAx", style = PlainTickerType.bigValue, color = Ink, maxLines = 1, softWrap = false)
    }
    Spacer(Modifier.height(22.dp))
    FactGrid(cells = GallerySamples.receipt, surface = Elevated, minCellHeight = 0.dp)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp)) {
        SecondaryButton(label = "View in Portfolio", onClick = {})
    }
}

@InstrumentPreviews
@Composable
private fun GalleryPreview() {
    PreviewCanvas {
        GalleryScreen(onBack = {})
    }
}
