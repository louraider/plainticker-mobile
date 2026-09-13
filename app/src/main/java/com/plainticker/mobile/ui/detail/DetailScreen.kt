package com.plainticker.mobile.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.MultiplierSource
import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.StockData
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Axes
import com.plainticker.mobile.data.plainticker.Axis
import com.plainticker.mobile.data.plainticker.FScore
import com.plainticker.mobile.data.plainticker.Method
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.data.rpc.PausableConfig
import com.plainticker.mobile.data.rpc.PermanentDelegate
import com.plainticker.mobile.data.rpc.ScaledUiAmountConfig
import com.plainticker.mobile.data.rpc.TransferHookConfig
import com.plainticker.mobile.data.xstocks.Deployment
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketState
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.Reserves
import com.plainticker.mobile.data.xstocks.Underlying
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactGrid
import com.plainticker.mobile.ui.components.FactTone
import com.plainticker.mobile.ui.components.Gauge
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.LiveBar
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.PrimaryButton
import com.plainticker.mobile.ui.components.SignalRow
import com.plainticker.mobile.ui.components.SkeletonBar
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.TopScrim
import com.plainticker.mobile.ui.components.Track
import com.plainticker.mobile.ui.swap.SwapActions
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.swap.SwapSheet
import com.plainticker.mobile.ui.swap.SwapState
import com.plainticker.mobile.ui.swap.SwapToken
import com.plainticker.mobile.ui.swap.SwapViewModel
import com.plainticker.mobile.ui.swap.quoteOrNull
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType
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
    onViewPortfolio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val swap by swapViewModel.state.collectAsStateWithLifecycle()

    // The one permission this app asks for, at the one moment it means anything: the tap that puts
    // the first ticker on the watchlist, which is the tap that creates something to notify about.
    // Never at launch. The answer is not acted on here at all: a refusal leaves the digest on the
    // Watchlist screen, which is where it is either way, and nothing asks again.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    DetailContent(
        state = state,
        swap = swap,
        swapActions = SwapActions(
            onAmountChanged = swapViewModel::amountChanged,
            onMax = swapViewModel::useMax,
            onFlip = swapViewModel::flip,
            onSubmit = swapViewModel::submit,
            onEdit = swapViewModel::edit,
            onClose = swapViewModel::close,
            // The receipt leaves the sheet behind: the holding it created is on the other screen.
            onViewPortfolio = {
                swapViewModel.close()
                onViewPortfolio()
            },
        ),
        onToggleWatch = {
            val ask = viewModel.toggleWatch()
            if (ask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
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
        modifier = modifier,
    )
}

@Composable
internal fun DetailContent(
    state: DetailUiState,
    swap: SwapState,
    swapActions: SwapActions,
    onToggleWatch: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The only Box on Detail, and the only thing in it that does not scroll is the scrim: the
    // 64sp hero used to draw in the same pixels as the white system clock, because the content
    // scrolls under a transparent status bar and nothing stood between them (Insets.kt). Nothing
    // here is sticky and the section order below is unchanged.
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
            SwapBlock(state = state, swap = swap, onSwap = onSwap)
            Spacer(Modifier.height(TailGap))

            // The sheet is a modal surface and draws in its own window, so where it sits in this
            // column does not matter. What matters is that it is not inside SwapBlock: that block
            // returns early when the catalog has not named a symbol, and a sheet that vanished
            // mid-landing would take a landed swap's receipt with it.
            SwapSheet(state = swap, actions = swapActions)
        }
        TopScrim(Modifier.align(Alignment.TopCenter))
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
 * How the two figures of the price row are set. One decision, taken once, so the two figures
 * cannot be styled apart by two separate edits.
 */
internal data class PriceFigureType(
    val token: TextStyle,
    val reference: TextStyle,
    /** The skeleton stands in for the token figure, so it is the height of whatever that is. */
    val skeletonHeight: Dp,
    /** Lifts the smaller figure's baseline onto the larger one's. Nothing to lift when they match. */
    val referenceLift: Dp,
)

/**
 * The type of the price pair, which the liquidity floor decides and the screen only obeys.
 *
 * Above the floor the pair is asymmetric on purpose: the token's own price leads at 40sp Ink and
 * the reference sits beside it at 20sp Ink 2, so a reader knows which is which without being told.
 *
 * Below the floor that same asymmetry is a trap, and it is the one this screen shipped with. The
 * screen has just said the pool is too thin to track the NYSE close, and then set the two numbers
 * that withheld premium is computed from on one baseline, the larger of them the largest true
 * thing on the screen. On APPx, $611.56 over $323.00: any reader subtracts them and gets the
 * +89.34 percent the floor exists to suppress. So below the floor neither figure leads, both are
 * set at the reference's size, and the sentence is read before either of them.
 */
internal fun priceFigureType(comparable: Boolean): PriceFigureType = if (comparable) {
    PriceFigureType(
        token = PlainTickerType.heroPrice,
        reference = PlainTickerType.referencePrice,
        skeletonHeight = 40.dp,
        referenceLift = ReferenceLift,
    )
} else {
    PriceFigureType(
        token = PlainTickerType.referencePrice,
        reference = PlainTickerType.referencePrice,
        skeletonHeight = 20.dp,
        referenceLift = 0.dp,
    )
}

/**
 * The token's own figure left, the reference right, and above them the one sentence the liquidity
 * floor has to say. The pair reads as one sentence to a screen reader, and the right column is
 * labelled by the venue's state: the reference is a close only while the exchange is shut, and the
 * same field is a live price during its session.
 *
 * The lead sentence is first for a reason. It used to stand under the pair, in the gauge's slot at
 * 13sp, below a 40sp figure and a 20sp one it was there to disqualify, and it lost: the reader had
 * already read the two operands and done the subtraction the app refused to do. A caveat that
 * arrives after the number it qualifies is not a caveat.
 *
 * A missing reference takes the slot under the row rather than the narrow right column, because
 * there is no gauge to draw without it and a sentence there would squeeze the price beside it at a
 * large font scale.
 */
@Composable
private fun PriceBlock(state: DetailUiState) {
    val row = state.priceRow
    val pending = state.quote.isLoading
    val type = priceFigureType(row.comparable)
    row.lead?.let {
        Text(
            text = it.text(),
            style = PlainTickerType.body,
            color = Ink,
            modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = PriceTop),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = if (row.lead == null) PriceTop else LeadGap)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = row.tokenLabel.text(), style = PlainTickerType.label, color = Muted)
            val price = row.tokenPrice
            when {
                price != null ->
                    Text(text = price, style = type.token, color = Ink, maxLines = 1, softWrap = false)

                pending -> SkeletonBar(width = 160.dp, height = type.skeletonHeight)

                // Jupiter answered and there is no price: a placeholder, never a zero.
                else -> Text(
                    text = stringResource(R.string.value_missing),
                    style = type.token,
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
                modifier = Modifier.padding(bottom = type.referenceLift),
            ) {
                Text(text = row.referenceLabel.text(), style = PlainTickerType.label, color = Muted)
                val reference = row.referencePrice
                if (reference != null) {
                    Text(
                        text = reference,
                        style = type.reference,
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
 * One button and one mono line. The sheet it opens is drawn by [DetailContent], not here: this
 * block has nothing to draw until the catalog names a symbol, and the sheet must outlive that.
 */
@Composable
private fun SwapBlock(
    state: DetailUiState,
    swap: SwapState,
    onSwap: () -> Unit,
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
/** Between the floor's sentence and the pair it governs: close enough to read as one block. */
private val LeadGap = 14.dp
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

/** The sheet is closed on every Detail preview; its own states preview in SwapSheet.kt. */
private val PreviewSwapActions = SwapActions(
    onAmountChanged = {},
    onMax = {},
    onFlip = {},
    onSubmit = {},
    onEdit = {},
    onClose = {},
    onViewPortfolio = {},
)

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
        DetailContent(PreviewState, SwapState.Closed(), PreviewSwapActions, onToggleWatch = {}, onSwap = {})
    }
}

@InstrumentPreviews
@Composable
private fun DetailLoadingPreview() {
    PreviewCanvas {
        DetailContent(
            DetailUiState(ticker = "TSLA", nowMillis = PreviewNow),
            SwapState.Closed(),
            PreviewSwapActions,
            onToggleWatch = {},
            onSwap = {},
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
            PreviewSwapActions,
            onToggleWatch = {},
            onSwap = {},
        )
    }
}
