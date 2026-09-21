package com.plainticker.mobile.ui.onboarding

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.ListRow
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.PrimaryButton
import com.plainticker.mobile.ui.components.SheetSurface
import com.plainticker.mobile.ui.components.TodayStrip
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.TopTabs
import com.plainticker.mobile.ui.components.focusOutline
import com.plainticker.mobile.ui.list.RowState
import com.plainticker.mobile.ui.list.label
import com.plainticker.mobile.ui.theme.Accent
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.LineStrong
import com.plainticker.mobile.ui.theme.PlainTickerType
import java.time.Instant

/**
 * The one-time onboarding (DT11; plan section 13 Pass 3, design/canvas/instrument.py
 * screen_onboarding). The first frame is the product, not a form: the List sits behind at 25
 * percent and the panel over it carries the promise, the self-certification and the one action.
 * "Read the list" renders disabled until the box is checked; checking writes nothing, the flag is
 * persisted only when the button is pressed, and AppNavHost then starts at home for good.
 *
 * Insets (ui/components/Insets.kt): the backdrop's TopBar absorbs the status bar and the panel
 * absorbs the navigation bar, so the screen pads nothing at its root and neither edge counts twice.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.completed) {
        if (state.completed) onDone()
    }

    OnboardingContent(
        checked = state.accepted,
        enabled = state.canContinue,
        onCheckedChange = viewModel::setAccepted,
        onContinue = viewModel::confirm,
        modifier = modifier,
    )
}

/** The whole screen without a ViewModel, so the three preview frames can drive both states. */
@Composable
private fun OnboardingContent(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Canvas)) {
        ListBackdrop(
            Modifier
                .matchParentSize()
                .alpha(BackdropAlpha)
                // Decoration, not content: TalkBack reads the panel and nothing else.
                .clearAndSetSemantics {},
        )
        ConsentPanel(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            onContinue = onContinue,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The List screen as a picture of itself (task U3: the backdrop draws the real tabs, the You
 * action and a sector heading, so the first frame stops lying about what the app is). Every piece
 * is built without a click handler, so there is nothing to tap and nothing to focus even before
 * the semantics are cleared: the tabs are decorative (TopTabs with no onSelect), the You action is
 * TopBar's own picture of itself (onAction left null), the strip offers no refresh and the rows do
 * not open.
 */
@Composable
private fun ListBackdrop(modifier: Modifier = Modifier) {
    Column(modifier) {
        TopBar(action = stringResource(R.string.you_action))
        TopTabs(
            items = listOf(
                stringResource(R.string.tab_list),
                stringResource(R.string.tab_vote),
                stringResource(R.string.tab_portfolio),
                stringResource(R.string.tab_watchlist),
            ),
            selected = 0,
            onSelect = null,
        )
        TodayStrip(
            text = pluralStringResource(
                R.plurals.list_today,
                BackdropWatched,
                Fmt.count(BackdropWatched),
                BackdropNextReport.ticker,
                Fmt.monthDay(BackdropNextReport.reportsAt),
            ),
        )
        // The settled List draws a sector chapter heading here, its row count as the meta, never
        // the "Analyzed" heading the skeleton alone uses (ListScreen.kt): the backdrop's six
        // sample rows are one illustrative chapter rather than six real, differently sectored ones.
        Heading(text = BackdropSector, topPadding = 30.dp, meta = Fmt.count(BackdropRows.size))
        BackdropRows.forEachIndexed { index, row ->
            ListRow(
                ticker = row.ticker,
                company = row.company,
                meta = stringResource(
                    R.string.list_row_meta_join,
                    stringResource(R.string.list_row_meta_premium, Fmt.percent(row.premiumPct)),
                    Fmt.daysOld(row.ageDays),
                ),
                valueRight = Fmt.decimal(row.composite, decimals = 0),
                valueSub = stringResource(row.state.label),
                divider = index < BackdropRows.lastIndex,
            )
        }
    }
}

/**
 * The gate: Elevated with the 1dp Line strong top edge (SheetSurface without its handle), the
 * wordmark, the headline, the three paragraphs, the self-certification and the one button.
 *
 * The promise scrolls, the button does not. The panel grows to at most the window height, and
 * when the copy no longer fits (a 360dp frame at font scale 1.3 needs more than a short phone
 * has) the block above scrolls inside the panel while "Read the list" stays on screen. The
 * bottom inset is padded outside that scroll, so the button clears the navigation bar the way
 * every other screen-ending button does (ui/components/Insets.kt).
 */
@Composable
private fun ConsentPanel(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetSurface(modifier = modifier, handle = false) {
        Column(
            modifier = Modifier
                // The navigation bar, or 40dp of canvas, whichever is deeper.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = PanelBottomPadding)))
                .padding(start = PanelSidePadding, end = PanelSidePadding, top = PanelTopPadding),
            verticalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            Column(
                // fill = false: the block keeps its own height while it fits and is capped at
                // what is left over the button once it does not.
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                Text(text = stringResource(R.string.app_name), style = PlainTickerType.wordmark, color = Ink)
                Text(
                    text = stringResource(R.string.onboarding_headline),
                    style = PlainTickerType.onboardingTitle,
                    color = Ink,
                )
                Column(verticalArrangement = Arrangement.spacedBy(BodyGap)) {
                    Text(
                        text = stringResource(R.string.onboarding_body_chain),
                        style = PlainTickerType.body,
                        color = Ink2,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_body_fundamentals),
                        style = PlainTickerType.body,
                        color = Ink2,
                    )
                    // The map (task U3): what each tab is for, and where the wallet, the pass and
                    // this device's record live, since the backdrop behind this panel can only
                    // show that as a picture, never say it.
                    Text(
                        text = stringResource(R.string.onboarding_body_map),
                        style = PlainTickerType.body,
                        color = Ink2,
                    )
                    // The disclaimer is the one paragraph in Ink: it is the sentence that must land.
                    Text(
                        text = stringResource(R.string.onboarding_body_disclaimer),
                        style = PlainTickerType.body,
                        color = Ink,
                    )
                }
                ConsentCheckbox(checked = checked, onCheckedChange = onCheckedChange)
            }
            PrimaryButton(
                label = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                enabled = enabled,
            )
        }
    }
}

/**
 * A 20dp square, 1dp Line strong empty and an Accent fill when checked, with no check glyph: the
 * shape lock holds here too. The whole row is the target (at least 48dp, full width), so a screen
 * reader hears one Checkbox whose label is the sentence and a switch user reaches it once.
 */
@Composable
private fun ConsentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusOutline(interactionSource)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(CheckboxSize)
                .then(if (checked) Modifier.background(Accent) else Modifier.border(1.dp, LineStrong)),
        )
        Text(
            text = stringResource(R.string.onboarding_certify),
            style = PlainTickerType.consent,
            color = Ink2,
        )
    }
}

// ---- Measurements ------------------------------------------------------------------------------

/** Behind the panel the product reads as a watermark, not as a screen someone should try to use. */
private const val BackdropAlpha = 0.25f

private val PanelTopPadding: Dp = 28.dp
private val PanelSidePadding: Dp = 20.dp
private val PanelBottomPadding: Dp = 40.dp

/** The canvas' 20dp between panel blocks and 10dp between the three paragraphs. */
private val PanelGap: Dp = 20.dp
private val BodyGap: Dp = 10.dp
private val CheckboxSize: Dp = 20.dp

// ---- The backdrop snapshot ---------------------------------------------------------------------

/** One analyzed row of the backdrop; the numbers go through Fmt like every other screen. */
private data class BackdropRow(
    val ticker: String,
    val company: String,
    val premiumPct: Double,
    val ageDays: Int,
    val composite: Double,
    val state: RowState,
)

private data class BackdropReport(val ticker: String, val reportsAt: Instant)

/**
 * Illustrative sample data, the six analyzed rows of design/canvas/instrument.py, so the first
 * frame is full before any network call returns. Nothing here is read from the chain or the API,
 * and nothing here is a real holding. The composites are the integer percentile the real List
 * draws (docs/data-map.md, T8), not the 0 to 1 fraction the canvas sample still shows.
 */
private val BackdropRows = listOf(
    BackdropRow("TSLAx", "Tesla, Inc.", 0.09, 2, 71.0, RowState.STRONG),
    BackdropRow("NVDAx", "NVIDIA Corp.", -0.04, 1, 68.0, RowState.STRONG),
    BackdropRow("AAPLx", "Apple Inc.", 0.01, 2, 61.0, RowState.FAIR),
    BackdropRow("MSFTx", "Microsoft Corp.", 0.03, 6, 58.0, RowState.FAIR),
    BackdropRow("AMZNx", "Amazon.com, Inc.", -0.02, 2, 55.0, RowState.FAIR),
    BackdropRow("COINx", "Coinbase Global", 0.08, 3, 47.0, RowState.WEAK),
)

/** One illustrative chapter's worth of sample rows; not a claim that every ticker above is GICS Technology. */
private const val BackdropSector = "Technology"

private const val BackdropWatched = 3
private val BackdropNextReport = BackdropReport("TSLAx", Instant.parse("2026-10-22T20:00:00Z"))

// ---- Previews ----------------------------------------------------------------------------------

/** The canvas frame is 412 by 915; the previews box the screen to that height at every width. */
private val PreviewFrameHeight: Dp = 915.dp

@InstrumentPreviews
@Composable
private fun OnboardingUncheckedPreview() {
    PreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(checked = false, enabled = false, onCheckedChange = {}, onContinue = {})
        }
    }
}

@InstrumentPreviews
@Composable
private fun OnboardingCheckedPreview() {
    PreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(checked = true, enabled = true, onCheckedChange = {}, onContinue = {})
        }
    }
}
