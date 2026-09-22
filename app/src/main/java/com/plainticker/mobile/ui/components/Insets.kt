package com.plainticker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.LineStrong
import com.plainticker.mobile.ui.theme.AmberColors

/*
 * Edge to edge (plan section 13 Pass 6, DT5). MainActivity draws under both system bars, which are
 * transparent with light icons, and pads nothing at the root. Each edge of a screen absorbs its
 * inset exactly once; windowInsetsPadding consumes what it applies, so nesting never pads twice.
 *
 * - Top: TopBar pads by WindowInsets.statusBars inside the scrolling content (56dp after the
 *   inset, the 24dp placeholder of the canvas), so the wordmark clears the status bar and the
 *   content scrolls under it. Nothing is sticky.
 * - Bottom: whatever ends a screen pads by WindowInsets.navigationBars: the column that ends in
 *   the Swap button, the tab content under the TopTabs, the onboarding panel. On a scrolling
 *   column the padding comes after verticalScroll, so it is part of the scrolled content.
 * - Sheet: Material pads the sheet content by SheetInsets and consumes the top inset by the
 *   sheet offset, so the top only counts once the sheet is dragged up to the status bar.
 * - Over the top of every scrolling surface, and nowhere else: [TopScrim].
 */

/**
 * The band the system clock sits in, painted in Canvas and faded out under it.
 *
 * Scrolling under a transparent status bar was decided deliberately above, and on the device on
 * 2026-09-13 it put the 64sp Ink hero and the white system clock in the same pixels, "NVDAx" over
 * "11:40". The state was never looked at because every canvas artboard is an 890dp content frame
 * with no system bars drawn. This is the fix the review asked for, and it costs the decision
 * nothing: the header still scrolls away, nothing is sticky, and no content moves.
 *
 * Three properties make it a scrim rather than a bar.
 *
 * 1. **It is Canvas over Canvas, so at rest it is invisible.** The page background already fills
 *    this band. The scrim only becomes visible when something that is not the background passes
 *    under it, which is exactly when it is needed.
 * 2. **It holds nothing.** No text, no action, no semantics and no pointer input: it is one Box
 *    with a gradient, so a touch goes through it and a screen reader never meets it. A sticky
 *    header is a header that stays; this stays and is not a header.
 * 3. **It is opaque only where the clock is.** Full ground colour across the status bar inset, then
 *    a [ScrimFade] fall to nothing, so content dissolves as it leaves rather than being cut off by
 *    a hard edge. A hard edge is what a status-bar background looks like, and it would make the
 *    890dp artboards wrong in the other direction.
 *
 * [groundColor] defaults to Instrument's [Canvas] for source compatibility with callers that have
 * not moved to Amber, but every live call site (`HomeScreen`, `DetailScreen`) now passes the
 * screen's own [AmberColors.surfaceGround] instead: a scrim hard-coded to Instrument's near-black
 * painted an opaque dark band across the top of every Amber screen regardless of theme, which on
 * Amber's light ground read as a black bar under the clock rather than an invisible one.
 */
@Composable
fun TopScrim(modifier: Modifier = Modifier, groundColor: Color = Canvas) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val opaqueTo = scrimOpaqueFraction(inset)
    val stops = if (opaqueTo > 0f) {
        arrayOf(0f to groundColor, opaqueTo to groundColor, 1f to Color.Transparent)
    } else {
        arrayOf(0f to groundColor, 1f to Color.Transparent)
    }
    Box(modifier.fillMaxWidth().height(inset + ScrimFade).background(Brush.verticalGradient(*stops)))
}

/**
 * How far past the status bar the scrim falls to nothing.
 *
 * It has a ceiling, and the ceiling is the wordmark. [TopBar] is 56dp after the inset with its
 * 15sp label centred, so the topmost ink of "PlainTicker" sits about 18dp into that bar. A fade
 * that reached it would tint the wordmark at rest, which is the one thing this band must never do:
 * at rest it is Canvas over Canvas and therefore invisible. 16dp stops about 3dp above it.
 *
 * So a later edit that wants a softer fall has to move the wordmark first, not this number.
 */
val ScrimFade: Dp = 16.dp

/**
 * Where the fade begins, as a fraction of the whole scrim band: the bottom edge of the status
 * bar. Everything above it is full Canvas, which is the half of this that matters, because that
 * is the band the clock is drawn in.
 *
 * A window with no status bar at all (a preview, a desktop frame) has nothing to cover and gets
 * the fade alone, rather than a gradient with two stops in the same place.
 */
internal fun scrimOpaqueFraction(inset: Dp, fade: Dp = ScrimFade): Float =
    (inset / (inset + fade)).coerceIn(0f, 1f)

/**
 * What the sheet content sits above: the navigation bar, plus the keyboard while the amount field
 * has it open, plus the status bar once the sheet reaches it. This is Material's own default for
 * the modal sheet, named here so the choice is visible where the Sheet is built.
 */
val SheetInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)

/**
 * The system bars simulated as 24dp bands, since a preview has no window and the real insets are
 * zero: the TopBar clears the top band, the bottom button clears the bottom one, and the Canvas
 * runs under both.
 */
@InstrumentPreviews
@Composable
private fun InsetsPreview() {
    val statusBar = WindowInsets(top = 24.dp)
    val navigationBar = WindowInsets(bottom = 24.dp)
    PreviewCanvas {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                TopBar(action = "Watch", onAction = {}, insets = statusBar)
                TopTabs(items = listOf("List", "Portfolio", "Watchlist"), selected = 0, onSelect = {})
                Spacer(Modifier.height(64.dp))
                Column(Modifier.padding(horizontal = 20.dp).windowInsetsPadding(navigationBar)) {
                    AmberPrimaryAction(label = "Swap USDC to TSLAx", onClick = {})
                }
            }
            SystemBarBand(Modifier.align(Alignment.TopCenter).windowInsetsTopHeight(statusBar))
            SystemBarBand(Modifier.align(Alignment.BottomCenter).windowInsetsBottomHeight(navigationBar))
        }
    }
}

@Composable
private fun SystemBarBand(modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(LineStrong))
}
