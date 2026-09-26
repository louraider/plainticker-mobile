package com.plainticker.mobile.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberBottomNav
import com.plainticker.mobile.ui.components.AmberDestination
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberSheetSurface
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.components.focusOutline
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberType
import java.time.LocalDate

/**
 * The one-time onboarding (DT11), rewritten for the app as it ships (the pre-freeze audit,
 * 2026-09-26): the first frame is the product, not a form. A picture of Today sits behind at 25
 * percent, drawn with the real Amber pieces (the top bar, a section head, ticker rows in their
 * tonal group, the five-destination bottom bar), and the panel over it carries the promise, a
 * short map of the five destinations, the self-certification and the one action. "Open Today"
 * renders disabled until the box is checked; checking writes nothing, the flag is persisted only
 * when the button is pressed, and AppNavHost then starts at home, on Today, for good.
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

/** The whole screen without a ViewModel, so the preview frames can drive both states. */
@Composable
private fun OnboardingContent(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onContinue: () -> Unit,
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
 * Today as a picture of itself: the top bar, the venue line, "Reports this week" with four sample
 * rows in [AmberTickerRowGroup] (one marked Watched the way Today marks it), and the bottom bar
 * with Today selected. Every piece is the component the real screen draws; nothing here has a
 * handler of its own, and [swallowTouches] stops the bar's.
 */
@Composable
private fun TodayBackdrop(modifier: Modifier = Modifier) {
    Column(modifier) {
        TopBar()
        Text(
            text = stringResource(R.string.today_status_closed_tomorrow, BackdropOpensAt),
            style = AmberType.body,
            color = defaultAmberColors().textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        AmberSectionHead(
            title = stringResource(R.string.today_heading_reports),
            meta = Fmt.count(BackdropRows.size),
        )
        AmberTickerRowGroup {
            BackdropRows.forEach { row ->
                AmberTickerRow(
                    ticker = row.symbol,
                    company = row.company,
                    figure = if (row.watched) stringResource(R.string.today_reports_watched) else null,
                    context = "${Fmt.weekday(row.reportsOn)} ${Fmt.dayMonth(row.reportsOn)}",
                )
            }
        }
        Spacer(Modifier.weight(1f))
        AmberBottomNav(selected = AmberDestination.TODAY, onSelect = {})
    }
}

/**
 * The gate: [AmberColors.surfaceHigh] with the 1dp [AmberColors.border] top edge
 * ([com.plainticker.mobile.ui.components.AmberSheetSurface] without its handle), the wordmark,
 * the headline, one sentence on what a stock page reads, the map of the five destinations, the
 * disclaimer, the self-certification and the one button. All of it Bricolage ([AmberType]); the
 * panel no longer borrows a single Outfit style.
 *
 * The map is five sentences, each opening with the destination's own bottom-bar label in weight
 * 600 ([MapLine]), so the words a reader learns here are the words under the icons they tap next.
 *
 * The promise scrolls, the button does not. The panel grows to at most the window height, and
 * when the copy no longer fits (a 360dp frame at font scale 1.3 needs more than a short phone
 * has) the block above scrolls inside the panel while "Open Today" stays on screen. The bottom
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
                Column(verticalArrangement = Arrangement.spacedBy(BodyGap)) {
                    MapLine(R.string.nav_today, R.string.onboarding_map_today, colors)
                    MapLine(R.string.nav_stocks, R.string.onboarding_map_stocks, colors)
                    MapLine(R.string.nav_vote, R.string.onboarding_map_vote, colors)
                    MapLine(R.string.nav_portfolio, R.string.onboarding_map_portfolio, colors)
                    MapLine(R.string.nav_you, R.string.onboarding_map_you, colors)
                }
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
                label = stringResource(R.string.onboarding_continue),
                onClick = onContinue,
                enabled = enabled,
                colors = colors,
            )
        }
    }
}

/**
 * One destination of the map: a sentence that opens with the destination's own bottom-bar label,
 * that label drawn in weight 600 and primary text, the rest in secondary. One wrapping [Text], so
 * there is no label column beside a sentence column to starve (DESIGN.md 5.4). If a translation
 * ever stops opening with the label, the sentence still reads whole, only without the emphasis.
 */
@Composable
private fun MapLine(@StringRes destination: Int, @StringRes sentence: Int, colors: AmberColors) {
    val name = stringResource(destination)
    val text = stringResource(sentence)
    val styled = buildAnnotatedString {
        if (text.startsWith(name)) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)) { append(name) }
            append(text.substring(name.length))
        } else {
            append(text)
        }
    }
    Text(text = styled, style = AmberType.body, color = colors.textSecondary)
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

// ---- The backdrop snapshot ---------------------------------------------------------------------

/** One row of the backdrop's "Reports this week"; the date goes through Fmt like Today's own. */
private data class BackdropRow(
    val symbol: String,
    val company: String,
    val reportsOn: LocalDate,
    val watched: Boolean = false,
)

/**
 * Illustrative sample data, so the first frame is full before any network call returns. Nothing
 * here is read from the chain or the API, and nothing here is a real report calendar.
 */
private val BackdropRows = listOf(
    BackdropRow("TSLAx", "Tesla, Inc.", LocalDate.of(2026, 10, 20), watched = true),
    BackdropRow("NVDAx", "NVIDIA Corp.", LocalDate.of(2026, 10, 21)),
    BackdropRow("AAPLx", "Apple Inc.", LocalDate.of(2026, 10, 22)),
    BackdropRow("MSFTx", "Microsoft Corp.", LocalDate.of(2026, 10, 23)),
)

/** The venue line's sample time, in the reader's own clock as Today prints it. */
private const val BackdropOpensAt = "15:30"

// ---- Previews ----------------------------------------------------------------------------------

/** The canvas frame is 412 by 915; the previews box the screen to that height at every width. */
private val PreviewFrameHeight: Dp = 915.dp

@InstrumentPreviews
@Composable
private fun OnboardingUncheckedPreview() {
    AmberPreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(checked = false, enabled = false, onCheckedChange = {}, onContinue = {})
        }
    }
}

@InstrumentPreviews
@Composable
private fun OnboardingCheckedPreview() {
    AmberPreviewCanvas {
        Box(Modifier.height(PreviewFrameHeight)) {
            OnboardingContent(checked = true, enabled = true, onCheckedChange = {}, onContinue = {})
        }
    }
}
