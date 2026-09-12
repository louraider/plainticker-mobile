package com.myapp.ui.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.ui.theme.LineStrong

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
 */

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
                    PrimaryButton(label = "Swap USDC to TSLAx", onClick = {})
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
