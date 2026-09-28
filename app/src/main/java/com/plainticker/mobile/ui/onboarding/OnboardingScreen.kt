package com.plainticker.mobile.ui.onboarding

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.AmberBottomNav
import com.plainticker.mobile.ui.components.AmberChip
import com.plainticker.mobile.ui.components.AmberDestination
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSheetSurface
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.SkeletonBar
import com.plainticker.mobile.ui.components.SkeletonTickerRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.focusOutline
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberType

/**
 * The one-time onboarding (DT11), rewritten for the app as it ships (the pre-freeze audit,
 * 2026-09-26): the first frame is the product, not a form. A picture of Today sits behind at 25
 * percent, drawn with the real Amber pieces (the top bar, skeleton rows in their tonal group, the
 * five-destination bottom bar) and no sample ticker or market claim, and the panel over it
 * carries the promise, the self-certification and the one action. "Continue" renders disabled
 * until the box is checked; checking writes nothing, the flag is persisted only when the button
 * is pressed, and AppNavHost then starts at home, on Today, for good.
 *
 * Step two (judges' round 2) replaced the five-line tab map that ended on an empty Today: the
 * reader picks stocks to watch from real analysed chips and lands on a Today that already has
 * them, or skips to the AAPLx page, the worked example whose every figure is open.
 *
 * The version before this one described a dead app: a List to start on, four text tabs, a
 * Watchlist that sent the digest, and You "top right". Every one of those moved when the shell
 * became the bottom bar, and a reader who trusted the first screen learned the wrong navigation.
 *
 * Insets (ui/components/Insets.kt): the backdrop's TopBar absorbs the status bar and the panel
 * absorbs the navigation bar, so the screen pads nothing at its root and neither edge counts twice.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onDone: (OnboardingExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.completed) {
        if (state.completed) onDone(state.exit)
    }

    OnboardingContent(
        state = state,
        onCheckedChange = viewModel::setAccepted,
        onContinue = viewModel::confirm,
        picks = PickActions(onToggle = viewModel::togglePick, onFinish = viewModel::finish, onSkip = viewModel::skip),
        modifier = modifier,
    )
}

/** The pick step's three handlers, grouped so the content signature stays readable. */
internal data class PickActions(
    val onToggle: (String) -> Unit,
    val onFinish: () -> Unit,
    val onSkip: () -> Unit,
)

private val NoPickActions = PickActions(onToggle = {}, onFinish = {}, onSkip = {})

/** The whole screen without a ViewModel, so the preview frames can drive both states. */
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    picks: PickActions,
    modifier: Modifier = Modifier,
) {
    val colors = defaultAmberColors()
    Box(modifier.fillMaxSize().background(colors.surfaceGround)) {
        TodayBackdrop(
            Modifier
                .matchParentSize()
                .alpha(BackdropAlpha)
                // Decoration, not content: TalkBack reads the panel and nothing else, and no touch
                // reaches the picture's own bottom bar either.
                .clearAndSetSemantics {}
                .swallowTouches(),
        )
        when (state.step) {
            OnboardingStep.CONSENT -> ConsentPanel(
                checked = state.accepted,
                enabled = state.canContinue,
                onCheckedChange = onCheckedChange,
                onContinue = onContinue,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            OnboardingStep.PICK -> PickPanel(
                state = state,
                actions = picks,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Consumes every pointer event on the way down, before any child sees it, so the backdrop's bottom
 * bar (a real [AmberBottomNav], which cannot be built without a select handler) can never be tapped
 * or show a ripple through the 25 percent picture.
 */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

/**
 * Today's shape, with nothing in it a reader could take for a fact: the top bar, a bar where the
 * venue line goes, one where a section head goes, four skeleton rows in their tonal group
 * ([SkeletonTickerRows], the same placeholder Today draws while it loads), and the bottom bar with
 * Today selected. Fresh-device QA of 1.3.24 (B6): the picture used to carry sample rows, TSLAx
 * "Watched" and "NYSE closed. Opens tomorrow", which a new reader could read as their own list and
 * a real market claim before anything had been read. Nothing here has a handler of its own, and
 * [swallowTouches] stops the bar's.
 */
@Composable
private fun TodayBackdrop(modifier: Modifier = Modifier) {
    val colors = defaultAmberColors()
    Column(modifier) {
        TopBar()
        // Where the venue line and the section head stand on Today, with nothing written in either.
        SkeletonBar(
            width = 240.dp,
            height = 16.dp,
            colors = colors,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        SkeletonBar(
            width = 180.dp,
            height = 24.dp,
            colors = colors,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        )
        SkeletonTickerRows(count = BackdropRowCount, colors = colors)
        Spacer(Modifier.weight(1f))
        AmberBottomNav(selected = AmberDestination.TODAY, onSelect = {})
    }
}

/**
 * The gate: [AmberColors.surfaceHigh] with the 1dp [AmberColors.border] top edge
 * ([com.plainticker.mobile.ui.components.AmberSheetSurface] without its handle), the wordmark,
 * the headline, one sentence on what a stock page reads, the disclaimer, the self-certification
 * and the one button. All of it Bricolage ([AmberType]).
 *
 * The promise scrolls, the button does not. The panel grows to at most the window height, and
 * when the copy no longer fits (a 360dp frame at font scale 1.3 needs more than a short phone
 * has) the block above scrolls inside the panel while "Continue" stays on screen. The bottom
 * inset is padded outside that scroll, so the button clears the navigation bar the way every other
 * screen-ending button does (ui/components/Insets.kt).
 */
@Composable
private fun ConsentPanel(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The one real, always-opaque content on this screen (the backdrop behind it is a decorative,
    // 25-percent watermark), so it reads the theme-following palette.
    val colors = defaultAmberColors()
    AmberSheetSurface(modifier = modifier, handle = false, colors = colors) {
        Column(
            modifier = Modifier
                // The navigation bar, or 40dp of ground, whichever is deeper.
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
                Text(text = stringResource(R.string.app_name), style = AmberType.wordmark, color = colors.textPrimary)
                Text(
                    text = stringResource(R.string.onboarding_headline),
                    style = AmberType.screenTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.onboarding_body_reads),
                    style = AmberType.body,
                    color = colors.textSecondary,
                )
                // The disclaimer is the one paragraph in primary text: it is the sentence that
                // must land.
                Text(
                    text = stringResource(R.string.onboarding_body_disclaimer),
                    style = AmberType.body,
                    color = colors.textPrimary,
                )
                ConsentCheckbox(checked = checked, onCheckedChange = onCheckedChange, colors = colors)
            }
            AmberPrimaryAction(
                label = stringResource(R.string.onboarding_consent_continue),
                onClick = onContinue,
                enabled = enabled,
                colors = colors,
            )
        }
    }
}

/**
 * Step two (judges' round 2): pick stocks to watch, so onboarding ends on a Today that already has
 * the reader's own rows, or, skipped, on the AAPLx page whose every figure is open. The chips are
 * real analysed stocks ([onboardingPicks]), never copy. The primary action is live once one is
 * picked; Skip is always there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickPanel(state: OnboardingUiState, actions: PickActions, modifier: Modifier = Modifier) {
    val colors = defaultAmberColors()
    AmberSheetSurface(modifier = modifier, handle = false, colors = colors) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets(bottom = PanelBottomPadding)))
                .padding(start = PanelSidePadding, end = PanelSidePadding, top = PanelTopPadding),
            verticalArrangement = Arrangement.spacedBy(PanelGap),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_pick_title),
                    style = AmberType.screenTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.onboarding_pick_body),
                    style = AmberType.body,
                    color = colors.textSecondary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(BodyGap),
                    verticalArrangement = Arrangement.spacedBy(BodyGap),
                ) {
                    state.suggestions.forEach { chip ->
                        AmberChip(
                            label = chip.symbol,
                            selected = chip.ticker in state.picked,
                            onClick = { actions.onToggle(chip.ticker) },
                            colors = colors,
                        )
                    }
                }
            }
            AmberPrimaryAction(
                label = stringResource(R.string.onboarding_continue),
                onClick = actions.onFinish,
                enabled = state.canFinish,
                colors = colors,
            )
            val example = state.example
            TextAction(
                label = if (example != null) {
                    stringResource(R.string.onboarding_pick_skip_example, example.symbol)
                } else {
                    stringResource(R.string.onboarding_pick_skip)
                },
                onClick = actions.onSkip,
                color = colors.actionText,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * A 20dp square, bordered and empty or filled in the action colour when checked, with no check
 * glyph: the shape lock holds here too. The whole row is the target (at least 48dp, full width),
 * so a screen reader hears one Checkbox whose label is the sentence and a switch user reaches it
 * once.
 */
@Composable
private fun ConsentCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusOutline(interactionSource, colors)
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
                .then(if (checked) Modifier.background(colors.actionFill) else Modifier.border(1.dp, colors.border)),
        )
        Text(
            text = stringResource(R.string.onboarding_certify),
            style = AmberType.context,
            color = colors.textSecondary,
        )
    }
}

// ---- Measurements ------------------------------------------------------------------------------

/** Behind the panel the product reads as a watermark, not as a screen someone should try to use. */
private const val BackdropAlpha = 0.25f

private val PanelTopPadding: Dp = 28.dp
private val PanelSidePadding: Dp = 20.dp
private val PanelBottomPadding: Dp = 40.dp

/** 20dp between panel blocks, 10dp between the five lines of the map. */
private val PanelGap: Dp = 20.dp
private val BodyGap: Dp = 10.dp
private val CheckboxSize: Dp = 20.dp

/** How many skeleton rows the backdrop draws: Today's first screenful, and no ticker in any. */
private const val BackdropRowCount = 4

// ---- Previews ----------------------------------------------------------------------------------

/** The canvas frame is 412 by 915; the previews box the screen to that height at every width. */
private val PreviewFrameHeight: Dp = 915.dp

@InstrumentPreviews
@Composable
private fun OnboardingUncheckedPreview() {
    AmberPreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(state = OnboardingUiState(), onCheckedChange = {}, onContinue = {}, picks = NoPickActions)
        }
    }
}

@InstrumentPreviews
@Composable
private fun OnboardingCheckedPreview() {
    AmberPreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(state = OnboardingUiState(accepted = true), onCheckedChange = {}, onContinue = {}, picks = NoPickActions)
        }
    }
}

@InstrumentPreviews
@Composable
private fun OnboardingPickPreview() {
    AmberPreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(
                state = OnboardingUiState(
                    accepted = true,
                    step = OnboardingStep.PICK,
                    suggestions = listOf(PickChip("AAPL", "AAPLx"), PickChip("TSLA", "TSLAx"), PickChip("NVDA", "NVDAx")),
                    picked = setOf("TSLA"),
                ),
                onCheckedChange = {},
                onContinue = {},
                picks = NoPickActions,
            )
        }
    }
}
