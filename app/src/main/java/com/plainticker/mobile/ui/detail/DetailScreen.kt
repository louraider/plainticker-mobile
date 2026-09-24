package com.plainticker.mobile.ui.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.plainticker.mobile.data.plainticker.NextUpRow
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
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSecondaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactGrid
import com.plainticker.mobile.ui.components.FactTone
import com.plainticker.mobile.ui.components.Gauge
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.LiveBar
import com.plainticker.mobile.ui.components.SignalRow
import com.plainticker.mobile.ui.components.SkeletonBar
import com.plainticker.mobile.ui.components.SkeletonSwitch
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.TopScrim
import com.plainticker.mobile.ui.components.Track
import com.plainticker.mobile.ui.swap.SwapActions
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.swap.SwapSheet
import com.plainticker.mobile.ui.swap.SwapState
import com.plainticker.mobile.ui.swap.SwapHolding
import com.plainticker.mobile.ui.swap.SwapViewModel
import com.plainticker.mobile.ui.swap.quoteOrNull
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.vote.VoteActions
import com.plainticker.mobile.ui.vote.VoteSheet
import com.plainticker.mobile.ui.vote.VoteState
import com.plainticker.mobile.ui.vote.VoteViewModel
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
    voteViewModel: VoteViewModel,
    onViewPortfolio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val swap by swapViewModel.state.collectAsStateWithLifecycle()
    val holding by swapViewModel.holding.collectAsStateWithLifecycle()
    val vote by voteViewModel.state.collectAsStateWithLifecycle()

    // What the connected wallet holds of this token, read from the chain, is what decides whether
    // "Swap to USDC" is offered here. Keyed on the token, so a chain read that adds the multiplier
    // later hands the machine the better answer.
    val swapToken = state.swapToken()
    LaunchedEffect(swapToken) { swapToken?.let(swapViewModel::watchHolding) }

    // The one permission this app asks for, at the one moment it means anything: the tap that puts
    // the first ticker on the watchlist, which is the tap that creates something to notify about.
    // Never at launch. The answer is not acted on here at all: a refusal leaves the digest on the
    // Watchlist screen, which is where it is either way, and nothing asks again.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    DetailContent(
        state = state,
        swap = swap,
        vote = vote,
        voteActions = VoteActions(
            onConfirm = voteViewModel::confirm,
            onRetry = voteViewModel::retry,
            onClose = voteViewModel::close,
        ),
        // The vote names the token the way this screen does, so the sheet it opens says NFLXx
        // where the hero says NFLXx, and the ticker under it is the key the server joins on.
        onVote = { voteViewModel.vote(state.ticker, state.symbol ?: state.ticker) },
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
            onRetry = swapViewModel::retry,
            onSwapBack = swapViewModel::swapBack,
        ),
        holding = holding,
        onSwapOut = { swapToken?.let(swapViewModel::openOut) },
        onToggleWatch = {
            val ask = viewModel.toggleWatch()
            if (ask && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onSwap = { swapToken?.let(swapViewModel::open) },
        // The peek's own way to Pro, beside the honest short form rather than in place of it
        // (task A6): the same destination the swap receipt already leads to.
        onViewPortfolio = onViewPortfolio,
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
    vote: VoteState = VoteState.Closed,
    voteActions: VoteActions = NoVoteActions,
    /** Null in the previews and the gallery, where no wallet can be reached. */
    onVote: (() -> Unit)? = null,
    /** Null in the previews and the gallery; the peek's own way to Pro when it is not (task A6). */
    onViewPortfolio: (() -> Unit)? = null,
    /** The connected wallet's chain-read balance of this token; null with no wallet or no read. */
    holding: SwapHolding? = null,
    /** "Swap to USDC"; null in the previews and the gallery. */
    onSwapOut: (() -> Unit)? = null,
) {
    val colors = defaultAmberColors()
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
                colors = colors,
            )
            state.banner?.let { Banner(text = stringResource(it.text)) }

            Hero(state)
            VerdictSection(state = state, onViewPortfolio = onViewPortfolio)

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
                AmberSectionHead(title = stringResource(R.string.detail_heading_backing))
                TrustBlock(state)
            }

            FundamentalsBlock(state)
            ReadSection(state = state, onViewPortfolio = onViewPortfolio)
            NextStepsSection(state)
            state.readNotice?.let { NoticeLine(it) }
            NextUpBlock(state)
            VoteBlock(state = state, onVote = onVote)
            SwapBlock(state = state, swap = swap, onSwap = onSwap, holding = holding, onSwapOut = onSwapOut)
            Spacer(Modifier.height(TailGap))

            // The sheet is a modal surface and draws in its own window, so where it sits in this
            // column does not matter. What matters is that it is not inside SwapBlock: that block
            // returns early when the catalog has not named a symbol, and a sheet that vanished
            // mid-landing would take a landed swap's receipt with it.
            SwapSheet(state = swap, actions = swapActions)
            VoteSheet(state = vote, actions = voteActions)
        }
        TopScrim(Modifier.align(Alignment.TopCenter), groundColor = colors.surfaceGround)
    }
}

// ---- The verdict (task app-verdict) -------------------------------------------------------------

/**
 * Directly under the hero and above the gauge, the slot the web gives its verdict band
 * (components/ticker/VerdictBand.tsx in the FinanceAnalyst repo). This app draws it its own way
 * rather than copying the web's boxed, tone-coloured band: a caption over a value, the same shape
 * [PriceBlock] already uses for the token's own figure, because DESIGN.md section 2 keeps colour
 * for direction and risk, never for a classification.
 *
 * [DetailUiState.verdictBlock] is null in every state this screen already drew nothing extra in: no
 * analysis served, or a served payload with no `verdict` block at all. [VerdictBlock.Loading] draws
 * a skeleton the way [Hero]'s own company line does. [VerdictBlock.Locked] draws a placeholder
 * shape with no text behind it, because the real word never reached this app to blur, plus the one
 * sentence this screen already uses lower down for the same reason, pointing at the same place.
 */
@Composable
private fun VerdictSection(state: DetailUiState, onViewPortfolio: (() -> Unit)?) {
    val block = state.verdictBlock ?: return
    val colors = defaultAmberColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = VerdictTop)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_verdict_label),
            style = AmberType.meta,
            color = colors.textTertiary(AmberSurface.GROUND),
        )
        when (block) {
            VerdictBlock.Loading -> SkeletonBar(width = VerdictPlaceholderWidth, height = VerdictPlaceholderHeight)

            is VerdictBlock.Unlocked ->
                // A word, never a colour: DESIGN.md section 7 keeps colour for direction and
                // risk, never for a classification, so this carries no more emphasis than
                // colors.textPrimary gives every other primary word on the screen.
                Text(text = block.label.text(), style = AmberType.sectionHead, color = colors.textPrimary)

            VerdictBlock.Locked -> {
                // The placeholder shape carries no text at all, amber or otherwise: the real
                // word never reached this app to draw, so there is nothing here to restyle
                // into something that reads as the word behind a filter.
                SkeletonBar(width = VerdictPlaceholderWidth, height = VerdictPlaceholderHeight)
                Text(
                    text = stringResource(R.string.detail_pro_peek_note),
                    style = AmberType.context,
                    color = colors.textSecondary,
                )
                if (onViewPortfolio != null) {
                    TextAction(
                        label = stringResource(R.string.receipt_view_portfolio),
                        onClick = onViewPortfolio,
                        color = colors.actionText,
                    )
                }
            }
        }
    }
}

// ---- Hero and price ----------------------------------------------------------------------------

/**
 * The Amber Detail frame's own hero anatomy (`scratchpad/design/mockups/gen.py`'s `detail_body`,
 * the `.hero` block, "amber" dict): the ticker small and secondary above, the company name the
 * large lead, the sector a third, quieter line below it. This inverts Instrument's hero, which led
 * with a 64sp mono ticker and gave the company a small caption underneath; there is no Amber type
 * scale entry that draws a bare ticker symbol at hero size, and the approved frame draws the
 * company as the lead identity instead, which is what a reader actually recognizes a stock by.
 * [AmberHeroCompany] extends [AmberType.sectionHead] (22/700) up to the frame's own 34sp rather
 * than declaring a new style in `Type.kt` (`ui/theme/`, out of this task's `ui/detail/` lane); it
 * stays a word style (no `tnum`) because a company name is words, not a numeral.
 *
 * **Motion, applied where it means something rather than everywhere.** The company name is the
 * one thing on this screen that genuinely arrives, once, on a cold open: null until the catalog
 * or the analysis answers, non-null for the rest of the screen's life. [SkeletonSwitch] (`ui/
 * components/Skeleton.kt`) is the existing, already reduced-motion-aware primitive for exactly
 * this shape ("skeleton while loading, then the content fading in over 200ms ease-out, instant
 * under reduced motion, never a spinner"); reused here rather than forked, and not reached for
 * anywhere else on this screen, because nowhere else on Detail is a single value's first arrival
 * the whole story the way the reader's own recognition of the company is.
 *
 * Merged, so the ticker, the company and the sector reach a screen reader as one phrase.
 */
@Composable
private fun Hero(state: DetailUiState) {
    val colors = defaultAmberColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = HeroTop)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = state.heroTicker,
            style = AmberType.context,
            color = colors.textSecondary,
            maxLines = 1,
            softWrap = false,
        )
        val company = state.heroCompany
        Spacer(Modifier.height(HeroTickerGap))
        SkeletonSwitch(
            loading = company == null,
            skeleton = { SkeletonBar(width = 200.dp, height = 30.dp) },
            content = {
                Text(text = company.orEmpty(), style = AmberHeroCompany, color = colors.textPrimary, maxLines = 2)
            },
        )
        val sector = state.heroSector
        if (sector != null) {
            Spacer(Modifier.height(HeroCompanyGap))
            Text(text = sector, style = AmberType.meta, color = colors.textTertiary(AmberSurface.GROUND))
        }
    }
}

/** See [Hero]'s own doc comment for why this extends [AmberType.sectionHead] rather than [AmberType.figureLarge]. */
private val AmberHeroCompany: TextStyle =
    AmberType.sectionHead.copy(fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em)

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
 * Above the floor the pair is asymmetric on purpose: the token's own price leads at 40sp amber
 * ([colors.actionText]) and the reference sits beside it at 20sp
 * [colors.textSecondary], so a reader knows which is which without being told.
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
        token = AmberPriceLead,
        reference = AmberPriceSmall,
        skeletonHeight = 40.dp,
        referenceLift = ReferenceLift,
    )
} else {
    PriceFigureType(
        token = AmberPriceSmall,
        reference = AmberPriceSmall,
        skeletonHeight = 20.dp,
        referenceLift = 0.dp,
    )
}

/**
 * Amber-styled equivalents of Instrument's `heroPrice` (40sp) and `referencePrice` (20sp),
 * extended from the closest [AmberType] number styles rather than declared fresh in `Type.kt`
 * (`ui/theme/`, out of this task's `ui/detail/` lane): Bricolage, tabular, at the exact point
 * sizes `DetailFinishedScreenTest` already pins for the liquidity floor's "neither figure leads"
 * contract, so that contract keeps holding without the test needing to change. The `opsz` axis
 * stays pinned at each base style's own point size (34 and 18) rather than tracking 40 and 20
 * exactly, a cosmetic nit rather than a functional one.
 */
private val AmberPriceLead: TextStyle =
    AmberType.figureLarge.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em)
private val AmberPriceSmall: TextStyle = AmberType.figureRow.copy(fontSize = 20.sp, lineHeight = 24.sp)

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
    val colors = defaultAmberColors()
    val row = state.priceRow
    val pending = state.quote.isLoading
    val type = priceFigureType(row.comparable)
    row.lead?.let {
        Text(
            text = it.text(),
            style = AmberType.body,
            color = colors.textPrimary,
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
            Text(
                text = row.tokenLabel.text(),
                style = AmberType.meta,
                color = colors.textTertiary(AmberSurface.GROUND),
            )
            val price = row.tokenPrice
            when {
                // Colour separates the two figures at every size (DESIGN.md section 1.1): the
                // token's own figure is always colors.actionText, whether or not it
                // leads on size, never a second colour for direction.
                price != null ->
                    Text(text = price, style = type.token, color = colors.actionText, maxLines = 1, softWrap = false)

                pending -> SkeletonBar(width = 160.dp, height = type.skeletonHeight)

                // Jupiter answered and there is no price: a placeholder, never a zero.
                else -> Text(
                    text = stringResource(R.string.value_missing),
                    style = type.token,
                    color = colors.textTertiary(AmberSurface.GROUND),
                    maxLines = 1,
                )
            }
            row.tokenNote?.let {
                Text(text = it.text(), style = AmberType.context, color = colors.textSecondary)
            }
        }
        if (row.referencePrice != null || pending) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = type.referenceLift),
            ) {
                Text(
                    text = row.referenceLabel.text(),
                    style = AmberType.meta,
                    color = colors.textTertiary(AmberSurface.GROUND),
                )
                val reference = row.referencePrice
                if (reference != null) {
                    Text(
                        text = reference,
                        style = type.reference,
                        color = colors.textSecondary,
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
            style = AmberType.context,
            color = colors.textSecondary,
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
    val hookLabel = stringResource(R.string.detail_fact_hook)
    FactGrid(
        cells = state.trustFacts.map { fact ->
            val labelText = fact.label.text()
            FactCell(
                label = labelText,
                value = fact.value.text(),
                sub = fact.sub?.text(),
                span = fact.span,
                subMono = fact.subMono,
                tone = if (fact.caution) FactTone.Caution else FactTone.Neutral,
                valueSize = if (fact.span > 1) SpanValueSize else CellValueSize,
                // The transfer hook's program id (DetailModel.kt's hookCell, Fmt.shortKey(program))
                // is the one TrustFact value that is an on-chain identifier rather than a number or
                // a state word; every other trust fact is one of those two. TrustFact itself carries
                // no field to say so (DetailModel.kt is outside this file set), so the one cell is
                // picked out by its own label, which is unique among the five trust facts and is
                // already resolved above for the row itself.
                valueMono = labelText == hookLabel,
            )
        },
        // FactGrid now reads a theme-following AmberColors by default for both its surface and
        // its label/value/sub text (ui/components/FactGrid.kt), so no override is needed here;
        // previously only `surface` was settable from a call site and it was pinned to a literal
        // dark colour regardless of the system setting, while the cell text stayed Instrument's
        // fixed-dark Muted/Ink/Ink2 underneath whatever surface was passed in.
    )
}

// ---- The fundamentals --------------------------------------------------------------------------

/**
 * "Against the sector", "F-Score" and "Method", or the one line that replaces all three.
 *
 * The v1.1 payload carries no leaf fundamentals (docs/data-map.md, gap 1), so the sector section is
 * the composite in the heading and three tracks: no grid is invented under them, and no heading is
 * left standing over nothing.
 *
 * **Where the approved frame and the real payload disagree.** `scratchpad/design/mockups/gen.py`'s
 * Amber Detail fragment additionally draws the composite as its own big [AmberFigure][
 * com.plainticker.mobile.ui.components.AmberFigure] cell ("50, of 100, sector median 55") beside a
 * "Sector rank" fact cell ("15 of 23, Information Technology, by composite") in a two-column facts
 * row. Neither figure is data this screen has: there is no sector-median or sector-rank field on
 * [AnalysisPayload][com.plainticker.mobile.data.plainticker.AnalysisPayload], only
 * `compositePercentile`, which is exactly the one number already drawn above, in the section
 * head's own meta slot. Building either cell would mean inventing the second operand of a
 * comparison this screen has no standing to state, the same failure mode DESIGN.md section 1
 * exists to keep off the liquidity floor. So this section stays composite-in-the-heading plus
 * three tracks, restyled but not restructured, and `DetailScreenTest`'s existing
 * `detail_fact_sector_rank` / `detail_fact_sector_median` assertions (nothing invented under the
 * three tracks) keep pinning exactly that.
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

    AmberSectionHead(title = stringResource(R.string.detail_heading_sector), meta = state.compositeMeta?.text())
    state.tracks.forEach { row ->
        when {
            row.locked -> LockedRow(row.label.text())
            row.value != null -> Track(
                label = row.label.text(),
                value = row.value,
                state = row.state,
                positionPct = row.positionPct,
            )

            else -> AbsentRow(row.label.text())
        }
    }

    AmberSectionHead(title = stringResource(R.string.detail_heading_fscore))
    state.fScore?.let { FScoreBlock(it) }

    AmberSectionHead(title = stringResource(R.string.detail_heading_method))
    state.method?.let { MethodBlock(it) }
}

/** The numeral out of nine, then the nine signals in the fixed order of docs/data-map.md. */
@Composable
private fun FScoreBlock(fscore: FScoreContent) {
    val colors = defaultAmberColors()
    if (fscore.unavailable) {
        Text(
            text = stringResource(R.string.detail_not_available_filer),
            style = AmberType.body,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        )
        return
    }
    val score = fscore.score
    // Resolved before the semantics lambda, which is not a composable scope.
    val spoken = score?.let {
        pluralStringResource(R.plurals.detail_fscore_a11y, fscore.outOf, it, Fmt.count(fscore.outOf))
    }
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
            style = AmberFScoreNumeral,
            color = if (score != null) colors.actionText else colors.textTertiary(AmberSurface.GROUND),
            maxLines = 1,
        )
        Text(
            text = pluralStringResource(R.plurals.detail_fscore_of, fscore.outOf, Fmt.count(fscore.outOf)),
            style = AmberType.context,
            color = colors.textTertiary(AmberSurface.GROUND),
            modifier = Modifier.padding(bottom = FScoreCounterLift),
        )
    }
    fscore.signals.forEach { signal -> SignalRow(name = stringResource(signal.name), ok = signal.ok) }
}

/** Instrument's `fScoreNumeral` was 56sp mono; extended from [AmberType.figureLarge] the same way [AmberPriceLead] is. */
private val AmberFScoreNumeral: TextStyle =
    AmberType.figureLarge.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.03).em)

/** The payload's own statement, then the static sources line, with the age above both when old. */
@Composable
private fun MethodBlock(method: MethodContent) {
    val colors = defaultAmberColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        method.age?.let {
            Text(text = it.text(), style = AmberType.context, color = colors.textTertiary(AmberSurface.GROUND))
        }
        Text(text = method.statement.text(), style = AmberType.body, color = colors.textSecondary)
        Text(
            text = method.sources.text(),
            style = AmberType.context,
            color = colors.textTertiary(AmberSurface.GROUND),
        )
    }
}

// ---- The read and What to check next (task A6) --------------------------------------------------

/**
 * "The read": the full narrative for an entitled wallet, the server's own honest excerpt
 * otherwise. Drawn only where [DetailUiState.readNarrative] found something to say, so a server
 * that has not turned this on, or has nothing cached yet, leaves the screen exactly as it drew
 * before task A6 (the free-stays-free invariant this screen keeps).
 */
@Composable
private fun ReadSection(state: DetailUiState, onViewPortfolio: (() -> Unit)?) {
    val block = state.readNarrative ?: return
    val colors = defaultAmberColors()
    AmberSectionHead(title = stringResource(R.string.detail_heading_read))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = block.text.text(), style = AmberType.body, color = colors.textSecondary)
        if (!block.full) {
            Text(
                text = stringResource(R.string.detail_pro_peek_note),
                style = AmberType.context,
                color = colors.textTertiary(AmberSurface.GROUND),
            )
            if (onViewPortfolio != null) {
                TextAction(
                    label = stringResource(R.string.receipt_view_portfolio),
                    onClick = onViewPortfolio,
                    color = colors.actionText,
                )
            }
        }
    }
}

/**
 * "What to check next": the three step titles for everyone, the full step text beside each title
 * once entitled. The same withholding rule as [ReadSection]: nothing to show draws nothing.
 */
@Composable
private fun NextStepsSection(state: DetailUiState) {
    val block = state.nextStepsBlock ?: return
    val colors = defaultAmberColors()
    AmberSectionHead(
        title = stringResource(R.string.detail_heading_next_steps),
        meta = Fmt.count(block.items.size),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        block.items.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = row.title, style = AmberType.body, color = colors.textSecondary)
                row.detail?.let { Text(text = it, style = AmberType.context, color = colors.textSecondary) }
            }
        }
        if (!block.full) {
            Text(
                text = stringResource(R.string.detail_pro_peek_note),
                style = AmberType.context,
                color = colors.textTertiary(AmberSurface.GROUND),
            )
        }
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
    holding: SwapHolding? = null,
    onSwapOut: (() -> Unit)? = null,
) {
    val label = state.swapLabel ?: return
    val cost = state.costLine(swap.quoteOrNull?.allInCostPct)
    val colors = defaultAmberColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = SwapGap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The swap flow itself (its sheet, its receipt) is untouched: only this trigger button
        // is restyled, to Amber's own primary action, one of the six named components.
        AmberPrimaryAction(
            label = label.text(),
            onClick = onSwap,
            enabled = state.mint != null && !swap.isBusy,
        )
        // The exit, for a wallet the chain says holds some: the same machine the other way round,
        // one step quieter than the swap in, because it is the second thing this screen offers.
        val out = state.swapOutLabel(holding)
        if (out != null && onSwapOut != null) {
            AmberSecondaryAction(label = out.text(), onClick = onSwapOut)
        }
        cost?.let {
            Text(text = it.text(), style = AmberType.meta, color = colors.textTertiary(AmberSurface.GROUND))
        }
    }
}

/**
 * "Vote to cover next", under the line that says there is no analysis to show.
 *
 * It is offered for exactly one state of this screen, [AnalysisState.NotServed]: PlainTicker
 * answered and classifies nothing for this ticker, which is the only case where a vote would
 * change anything. A call that failed leaves it unknown whether the ticker is covered at all
 * ([AnalysisState.Unavailable]), and voting to cover something that may already be covered is
 * exactly the kind of guess this screen refuses everywhere else.
 *
 * A text action and not a button: DESIGN.md gives this screen one primary control, the swap, and
 * a second filled button under it would say the two carry the same weight. They do not.
 */
@Composable
private fun VoteBlock(state: DetailUiState, onVote: (() -> Unit)?) {
    if (!state.analysisNotServed || onVote == null) return
    val colors = defaultAmberColors()
    Row(modifier = Modifier.fillMaxWidth().padding(start = Side, end = Side, top = VoteGap)) {
        TextAction(
            label = stringResource(R.string.vote_action),
            onClick = onVote,
            color = colors.actionText,
            contentPadding = VoteActionPadding,
        )
    }
}

/**
 * Where this ticker stands in the next-up list, under the line that says there is no analysis
 * yet and above the action that votes for one (docs/skr-curation-spec-2026-09-13.md, step 3).
 * Two lines: the rank in the body face, the figure behind it in the numeral face, the way the
 * cost line sits under the swap button. Drawn only where [DetailModel] has a standing to state,
 * which is never for a covered ticker and never for one nobody has voted for.
 */
@Composable
private fun NextUpBlock(state: DetailUiState) {
    val line = state.nextUpLine ?: return
    val colors = defaultAmberColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = NextUpTop)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = line.rank.text(), style = AmberType.body, color = colors.textSecondary)
        Text(text = line.weight.text(), style = AmberType.meta, color = colors.textTertiary(AmberSurface.GROUND))
    }
}

// ---- Shared pieces -----------------------------------------------------------------------------

/** One sentence where a block would be, so no state of this screen is a blank column. */
@Composable
private fun NoticeLine(text: Copy, hint: Copy? = null) {
    val colors = defaultAmberColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = SectionGap)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = text.text(), style = AmberType.body, color = colors.textSecondary)
        hint?.let {
            Text(text = it.text(), style = AmberType.context, color = colors.textTertiary(AmberSurface.GROUND))
        }
    }
}

/**
 * An axis the filer does not publish. No marker is drawn: a marker at zero would say the company
 * scored nothing, where the payload only said the SEC filing carries nothing (a foreign 20-F
 * filer, a young one).
 */
@Composable
private fun AbsentRow(label: String) {
    val colors = defaultAmberColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = 14.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = AmberType.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.detail_not_available_filer),
            style = AmberType.context,
            color = colors.textTertiary(AmberSurface.GROUND),
        )
    }
}

/**
 * An axis the Pro-numbers lock withheld (founder decision 2026-09-23): the same anatomy as
 * [AbsentRow], on purpose, because both are "a track with no marker to draw," but never its
 * sentence. [R.string.pro_locked_value] says entitlement, not absence, so a reader who already
 * saw "not available for this filer" on quality (an unlikely, foreign-filer case) never mistakes
 * a withheld valuation or momentum axis for the same fact about the company.
 */
@Composable
private fun LockedRow(label: String) {
    val colors = defaultAmberColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Side, end = Side, top = 14.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = label, style = AmberType.body, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.pro_locked_value),
            style = AmberType.context,
            color = colors.textTertiary(AmberSurface.GROUND),
        )
    }
}

// ---- Measurements ------------------------------------------------------------------------------

private val Side = 20.dp
private val HeroTop = 12.dp
/** Between the small ticker line and the hero company name below it. */
private val HeroTickerGap = 4.dp
/** Between the hero company name and the sector line below it. */
private val HeroCompanyGap = 6.dp
/** Between the verdict block and the hero above it, the same gap [PriceTop] used before it moved down. */
private val VerdictTop = 28.dp
/** The placeholder that stands in for a locked or not-yet-loaded classification, never real text. */
private val VerdictPlaceholderWidth = 120.dp
private val VerdictPlaceholderHeight = 22.dp
private val PriceTop = 28.dp
/** Between the floor's sentence and the pair it governs: close enough to read as one block. */
private val LeadGap = 14.dp
private val ReferenceLift = 6.dp
private val ReferenceNoteTop = 18.dp
private val LiveGap = 28.dp
private val SectionGap = 28.dp
private val FScoreGap = 6.dp
private val FScoreCounterLift = 4.dp
private val SwapGap = 32.dp

/** Under the sentence that says nothing is classified here, and above the vote it leads to. */
private val NextUpTop = 16.dp

/** Close under the sentence that says nothing is classified here, which is what it answers. */
private val VoteGap = 10.dp

/** The action leads the row, so its 48dp target starts at the same edge as every other block. */
private val VoteActionPadding = PaddingValues(start = 0.dp, top = 14.dp, end = 0.dp, bottom = 14.dp)

/** Detail's previews and the component gallery reach no wallet, so the vote sheet is never up. */
private val NoVoteActions = VoteActions(onConfirm = {}, onRetry = {}, onClose = {})
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
    AmberPreviewCanvas {
        DetailContent(PreviewState, SwapState.Closed(), PreviewSwapActions, onToggleWatch = {}, onSwap = {})
    }
}

@InstrumentPreviews
@Composable
private fun DetailLoadingPreview() {
    AmberPreviewCanvas {
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
    AmberPreviewCanvas {
        DetailContent(
            PreviewState.copy(
                analysisState = AnalysisState.NotServed,
                nextUp = listOf(NextUpRow("AAPL", "12345678901", 2), NextUpRow("TSLA", "31209870777", 3)),
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
