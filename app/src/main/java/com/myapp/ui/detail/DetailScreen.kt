package com.myapp.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.R
import com.myapp.data.KnownMints
import com.myapp.data.MultiplierSource
import com.myapp.data.SplitMultiplier
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.StockData
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.plainticker.Axes
import com.myapp.data.plainticker.Axis
import com.myapp.data.plainticker.FScore
import com.myapp.data.plainticker.Method
import com.myapp.data.rpc.MintFacts
import com.myapp.data.rpc.PausableConfig
import com.myapp.data.rpc.PermanentDelegate
import com.myapp.data.rpc.ScaledUiAmountConfig
import com.myapp.data.rpc.TransferHookConfig
import com.myapp.data.xstocks.Deployment
import com.myapp.data.xstocks.MarketSource
import com.myapp.data.xstocks.MarketState
import com.myapp.data.xstocks.MarketStatus
import com.myapp.data.xstocks.Reserves
import com.myapp.data.xstocks.Underlying
import com.myapp.data.xstocks.XStockAsset
import com.myapp.ui.Copy
import com.myapp.ui.components.Banner
import com.myapp.ui.components.FactCell
import com.myapp.ui.components.FactGrid
import com.myapp.ui.components.FactTone
import com.myapp.ui.components.Gauge
import com.myapp.ui.components.Heading
import com.myapp.ui.components.InstrumentPreviews
import com.myapp.ui.components.LiveBar
import com.myapp.ui.components.PreviewCanvas
import com.myapp.ui.components.PrimaryButton
import com.myapp.ui.components.SignalRow
import com.myapp.ui.components.SkeletonBar
import com.myapp.ui.components.TopBar
import com.myapp.ui.components.Track
import com.myapp.ui.swap.SwapPlaceholder
import com.myapp.ui.text
import com.myapp.ui.swap.SwapState
import com.myapp.ui.swap.SwapToken
import com.myapp.ui.swap.SwapViewModel
import com.myapp.ui.swap.quoteOrNull
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType
import java.math.BigInteger
import java.time.Instant

/**
 * The Detail screen (task T9, design task DT6; design/canvas/instrument.py `screen_detail_full`).
 *
 * One scrolling column in the order DESIGN.md section 5 fixes and never varies: header, hero,
 * price row, the tracking gauge, the live bar, "Backing and controls", "Against the sector",
 * "F-Score", "Method", then the single Swap button with its mono cost line. Nothing is sticky, no
 * section repeats its neighbour's layout, and the first viewport ends inside "Against the sector".
 *
 * The screen decides nothing. Every sentence and every number it draws comes from [DetailModel]
 * over [DetailUiState], so the premium is never computed in a composable (the gauge is handed the
 * one [TrackingQuality] answer and draws or withholds on that), Caution reaches only the value of
 * an issuer control the mint actually carries, and a source that could not be read says so in its
 * own cell instead of vanishing or reading as clean.
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    swapViewModel: SwapViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val swap by swapViewModel.state.collectAsStateWithLifecycle()

    DetailContent(
        state = state,
        swap = swap,
        onToggleWatch = viewModel::toggleWatch,
        onSwap = {
            state.mint?.let { mint ->
                swapViewModel.open(
                    SwapToken(
                        mint = mint,
                        symbol = state.symbol ?: state.ticker,
                        decimals = state.chain.valueOrNull?.facts?.decimals ?: MintFacts.XSTOCK_DECIMALS,
                    )
                )
            }
        },
        onResetSwap = swapViewModel::close,
        modifier = modifier,
    )
}

@Composable
internal fun DetailContent(
    state: DetailUiState,
    swap: SwapState,
    onToggleWatch: () -> Unit,
    onSwap: () -> Unit,
    onResetSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // After the scroll, so the padding is part of the scrolled content (Insets.kt).
            .navigationBarsPadding(),
    ) {
        TopBar(
            action = stringResource(if (state.watched) R.string.action_watching else R.string.action_watch),
            onAction = onToggleWatch,
        )
        state.banner?.let { Banner(text = stringResource(it.text)) }

        Hero(state)

        val tokenNotice = state.tokenNotice
        if (tokenNotice != null) {
            // No mint, so no price, no gauge, no chain and no reserves: one line instead of five
            // blocks that would each have to say the same thing.
            NoticeLine(tokenNotice)
        } else {
            PriceBlock(state)
            state.gauge?.let {
                Gauge(
                    referenceLabel = state.gaugeReference.text(),
                    tracking = it,
                    scalePct = GAUGE_SCALE_PCT,
                )
            }
            Spacer(Modifier.height(LiveGap))
            LiveBlock(state)
            Heading(text = stringResource(R.string.detail_heading_backing), topPadding = BackingGap)
            TrustBlock(state)
        }

        FundamentalsBlock(state)
        SwapBlock(state = state, swap = swap, onSwap = onSwap, onResetSwap = onResetSwap)
        Spacer(Modifier.height(TailGap))
    }
}

// ---- Hero and price ----------------------------------------------------------------------------

/** The 64sp mono ticker with the company under it. One line, never wrapped (DESIGN.md section 3). */
@Composable
private fun Hero(state: DetailUiState) {
    // Merged, so the ticker and the company reach a screen reader as one phrase.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = HeroTop)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = state.heroTicker,
            style = PlainTickerType.heroTicker,
            color = Ink,
            maxLines = 1,
            softWrap = false,
        )
        val company = state.heroCompany
        Spacer(Modifier.height(HeroCompanyGap))
        if (company != null) {
            Text(text = company, style = PlainTickerType.company, color = Ink2, maxLines = 2)
        } else {
            SkeletonBar(width = 160.dp, height = 16.dp)
        }
    }
}

/**
 * Token price left, the reference right. The two read as one sentence to a screen reader, and the
 * right column is labelled by the venue's state: the reference is a close only while the exchange
 * is shut, and the same field is a live price during its session.
 *
 * A missing reference takes the gauge's own slot under the row rather than the narrow right column,
 * because there is no gauge to draw without it and a sentence there would squeeze the price beside
 * it at a large font scale.
 */
@Composable
private fun PriceBlock(state: DetailUiState) {
    val row = state.priceRow
    val pending = state.quote.isLoading
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = PriceTop)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.detail_token_price), style = PlainTickerType.label, color = Muted)
            val price = row.tokenPrice
            when {
                price != null ->
                    Text(text = price, style = PlainTickerType.heroPrice, color = Ink, maxLines = 1, softWrap = false)

                pending -> SkeletonBar(width = 160.dp, height = 40.dp)

                // Jupiter answered and there is no price: a placeholder, never a zero.
                else -> Text(
                    text = stringResource(R.string.value_missing),
                    style = PlainTickerType.heroPrice,
                    color = Muted,
                    maxLines = 1,
                )
            }
            row.tokenNote?.let { Text(text = it.text(), style = PlainTickerType.small, color = Ink2) }
        }
        if (row.referencePrice != null || pending) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = ReferenceLift),
            ) {
                Text(text = row.referenceLabel.text(), style = PlainTickerType.label, color = Muted)
                val reference = row.referencePrice
                if (reference != null) {
                    Text(
                        text = reference,
                        style = PlainTickerType.referencePrice,
                        color = Ink2,
                        maxLines = 1,
                        softWrap = false,
                    )
                } else {
                    SkeletonBar(width = 72.dp, height = 20.dp)
                }
            }
        }
    }
    row.referenceNote?.let {
        Text(
            text = it.text(),
            style = PlainTickerType.small,
            color = Ink2,
            modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = ReferenceNoteTop),
        )
    }
}

// ---- The trust layer ---------------------------------------------------------------------------

/** "Live from the mint, slot N, 2 s ago". Breathing while the read is live, static once it is not. */
@Composable
private fun LiveBlock(state: DetailUiState) {
    val line = state.liveLine
    if (line == null) {
        SkeletonBar(width = 200.dp, height = 20.dp, modifier = Modifier.padding(horizontal = Side))
        return
    }
    LiveBar(
        label = line.label.text(),
        meta = line.meta.text(),
        live = line.live,
        announcement = line.announcement.text(),
    )
}

/** The blueprint grid: five facts, the reserves spanning the first row. */
@Composable
private fun TrustBlock(state: DetailUiState) {
    if (state.trustLoading) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(SkeletonFactRows) { SkeletonBar(width = 320.dp, height = 60.dp) }
        }
        return
    }
    FactGrid(
        cells = state.trustFacts.map { fact ->
            FactCell(
                label = fact.label.text(),
                value = fact.value.text(),
                sub = fact.sub?.text(),
                span = fact.span,
                subMono = fact.subMono,
                tone = if (fact.caution) FactTone.Caution else FactTone.Neutral,
                valueSize = if (fact.span > 1) SpanValueSize else CellValueSize,
            )
        },
    )
}

// ---- The fundamentals --------------------------------------------------------------------------

/**
 * "Against the sector", "F-Score" and "Method", or the one line that replaces all three.
 *
 * The v1.1 payload carries no leaf fundamentals (docs/data-map.md, gap 1), so the sector section is
 * the composite in the heading and three tracks: no grid is invented under them, and no heading is
 * left standing over nothing.
 */
@Composable
private fun FundamentalsBlock(state: DetailUiState) {
    val notice = state.fundamentalsNotice
    if (notice != null) {
        NoticeLine(notice.text, notice.hint)
        return
    }
    if (state.analysis == null) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = SectionGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(SkeletonAnalysisRows) { SkeletonBar(width = 300.dp, height = 20.dp) }
        }
        return
    }

    Heading(text = stringResource(R.string.detail_heading_sector), meta = state.compositeMeta?.text())
    state.tracks.forEach { row ->
        if (row.value != null) {
            Track(
                label = row.label.text(),
                value = row.value,
                state = row.state,
                positionPct = row.positionPct,
            )
        } else {
            AbsentRow(row.label.text())
        }
    }

    Heading(text = stringResource(R.string.detail_heading_fscore))
    state.fScore?.let { FScoreBlock(it) }

    Heading(text = stringResource(R.string.detail_heading_method))
    state.method?.let { MethodBlock(it) }
}

/** The numeral out of nine, then the nine signals in the fixed order of docs/data-map.md. */
@Composable
private fun FScoreBlock(fscore: FScoreContent) {
    if (fscore.unavailable) {
        Text(
            text = stringResource(R.string.detail_not_available_filer),
            style = PlainTickerType.body,
            color = Ink2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        )
        return
    }
    val score = fscore.score
    // Resolved before the semantics lambda, which is not a composable scope.
    val spoken = score?.let { stringResource(R.string.detail_fscore_a11y, it, fscore.outOf) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, bottom = FScoreGap)
            .semantics(mergeDescendants = true) { if (spoken != null) contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = score ?: stringResource(R.string.value_missing),
            style = PlainTickerType.fScoreNumeral,
            color = if (score != null) Ink else Muted,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.detail_fscore_of, fscore.outOf),
            style = PlainTickerType.company,
            color = Muted,
            modifier = Modifier.padding(bottom = FScoreCounterLift),
        )
    }
    fscore.signals.forEach { signal -> SignalRow(name = stringResource(signal.name), ok = signal.ok) }
}

/** The payload's own statement, then the static sources line, with the age above both when old. */
@Composable
private fun MethodBlock(method: MethodContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        method.age?.let { Text(text = it.text(), style = PlainTickerType.small, color = Muted) }
        Text(text = method.statement.text(), style = PlainTickerType.body, color = Ink2)
        Text(text = method.sources.text(), style = PlainTickerType.small, color = Muted)
    }
}

// ---- The swap ----------------------------------------------------------------------------------

/**
 * One button and one mono line. The sheet is T10: the button keeps the placeholder's wiring, so
 * the quote, the signature and the landing still run, and the placeholder shows the phase while
 * the flow is not idle.
 */
@Composable
private fun SwapBlock(
    state: DetailUiState,
    swap: SwapState,
    onSwap: () -> Unit,
    onResetSwap: () -> Unit,
) {
    val label = state.swapLabel ?: return
    val cost = state.costLine(swap.quoteOrNull?.allInCostPct)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = SwapGap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryButton(
            label = label.text(),
            onClick = onSwap,
            enabled = state.mint != null && !swap.isBusy,
        )
        cost?.let { Text(text = it.text(), style = PlainTickerType.meta, color = Muted) }
        if (swap !is SwapState.Closed) {
            SwapPlaceholder(state = swap, enabled = false, onSwap = onSwap, onReset = onResetSwap)
        }
    }
}

// ---- Shared pieces -----------------------------------------------------------------------------

/** One sentence where a block would be, so no state of this screen is a blank column. */
@Composable
private fun NoticeLine(text: Copy, hint: Copy? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = SectionGap)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = text.text(), style = PlainTickerType.body, color = Ink2)
        hint?.let { Text(text = it.text(), style = PlainTickerType.small, color = Muted) }
    }
}

/**
 * An axis the filer does not publish. No marker is drawn: a marker at zero would say the company
 * scored nothing, where the payload only said the SEC filing carries nothing (a foreign 20-F
 * filer, a young one).
 */
@Composable
private fun AbsentRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = 14.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = PlainTickerType.rowLabel, color = Ink2, modifier = Modifier.weight(1f))
        Text(text = stringResource(R.string.detail_not_available_filer), style = PlainTickerType.small, color = Muted)
    }
}

// ---- Measurements ------------------------------------------------------------------------------

private val Side = 20.dp
private val HeroTop = 12.dp
private val HeroCompanyGap = 6.dp
private val PriceTop = 28.dp
private val ReferenceLift = 6.dp
private val ReferenceNoteTop = 18.dp
private val LiveGap = 28.dp
private val BackingGap = 28.dp
private val SectionGap = 28.dp
private val FScoreGap = 6.dp
private val FScoreCounterLift = 4.dp
private val SwapGap = 32.dp
private val TailGap = 48.dp
private val CellValueSize = 24.sp
private val SpanValueSize = 32.sp
private const val SkeletonFactRows = 3
private const val SkeletonAnalysisRows = 4

// ---- Previews ----------------------------------------------------------------------------------

private val PreviewAsset = XStockAsset(
    name = "Tesla, Inc.",
    symbol = "TSLAx",
    underlying = Underlying(symbol = "TSLA", type = "Equity", listingCountry = "US"),
    deployments = listOf(Deployment(address = KnownMints.TSLAX, network = XStockAsset.NETWORK_SOLANA)),
)

private val PreviewNow = Instant.parse("2026-09-12T20:55:00Z").toEpochMilli()

/** TSLAx as it read live on 2026-09-12: a delegate, pausable transfers, no split, an empty hook. */
private val PreviewFacts = MintFacts(
    decimals = 8,
    supplyRaw = BigInteger.valueOf(22_963_733_950_050L),
    mintAuthority = null,
    freezeAuthority = null,
    permanentDelegate = PermanentDelegate(delegate = "5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq"),
    pausable = PausableConfig(paused = false, authority = null),
    scaledUiAmount = ScaledUiAmountConfig(1.0, 1.0, 0L, null),
    transferHook = TransferHookConfig(programId = null, authority = null),
    defaultAccountState = null,
)

private val PreviewState = DetailUiState(
    ticker = "TSLA",
    catalogAsset = Piece.Ready(PreviewAsset),
    analysisState = AnalysisState.Served(
        AnalysisPayload(
            ticker = "TSLA",
            company = "Tesla, Inc.",
            sector = "Consumer Discretionary",
            asOf = "2026-09-10T13:25:30.278Z",
            axes = Axes(
                quality = Axis(value = 8.0, scale = "0-9", position = 0.889, labelEn = "Strong"),
                valuation = Axis(value = 51.0, scale = "0-100", position = 0.51, labelEn = "Moderate"),
                momentum = Axis(value = 0.79, scale = "0-1", position = 0.79, labelEn = "Near 52-week high"),
            ),
            fscore = FScore(
                score = 8,
                scale = "0-9",
                signals = listOf(true, true, true, false, true, true, true, true, true),
            ),
            compositePercentile = 71.0,
            method = Method(statementEn = null, schemaVersion = "v1.1"),
        ),
    ),
    quote = Piece.Ready(PriceEntry(usdPrice = 366.17, liquidity = 1_300_000.0, stockData = StockData(price = 365.84))),
    chain = Piece.Ready(ChainRead(PreviewFacts, slot = 446_503_662L, readAtMillis = PreviewNow - 2_000L)),
    reserves = Piece.Ready(
        Reserves(sharesHeld = 26_101.0, tokensInCirculation = 25_924.0, custodians = listOf("Alpaca"), asOf = null),
    ),
    split = Piece.Ready(SplitMultiplier(current = 1.0, source = MultiplierSource.MINT, pending = null)),
    market = MarketStatus(
        state = MarketState.CLOSED,
        source = MarketSource.VENUE,
        venueOpen = false,
        nextChangeAtMillis = null,
    ),
    nowMillis = PreviewNow,
)

@InstrumentPreviews
@Composable
private fun DetailPreview() {
    PreviewCanvas {
        DetailContent(PreviewState, SwapState.Closed(), onToggleWatch = {}, onSwap = {}, onResetSwap = {})
    }
}

@InstrumentPreviews
@Composable
private fun DetailLoadingPreview() {
    PreviewCanvas {
        DetailContent(
            DetailUiState(ticker = "TSLA", nowMillis = PreviewNow),
            SwapState.Closed(),
            onToggleWatch = {},
            onSwap = {},
            onResetSwap = {},
        )
    }
}

/** The four absences on one frame: no analysis, no mint, no reserves and no reference price. */
@InstrumentPreviews
@Composable
private fun DetailDegradedPreview() {
    PreviewCanvas {
        DetailContent(
            PreviewState.copy(
                analysisState = AnalysisState.NotServed,
                quote = Piece.Ready(PriceEntry(usdPrice = 366.17, liquidity = 1_300_000.0)),
                chain = Piece.Failed,
                reserves = Piece.Absent,
                split = Piece.Failed,
            ),
            SwapState.Closed(),
            onToggleWatch = {},
            onSwap = {},
            onResetSwap = {},
        )
    }
}
