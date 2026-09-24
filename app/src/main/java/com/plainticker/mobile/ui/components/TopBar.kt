package com.plainticker.mobile.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * 56dp after the status inset: the wordmark left, one text action right (Watch, You) or one
 * static mono fragment (the wallet). Lives inside the scrolling content, so it scrolls away,
 * never sticky.
 *
 * Every screen this bar sits on top of is Amber's now (`HomeScreen`'s shared `header`, Detail's
 * own instance, the onboarding backdrop's picture of the old List), so [colors] defaults to the
 * system-following [defaultAmberColors] rather than Instrument's fixed-dark [Ink]/[Accent]/[Muted]:
 * a bar that stayed dark on top of a light Stocks or Detail screen is exactly the "assumes dark"
 * fault this pass exists to find. [PlainTickerType.wordmark] itself moved on 2026-09-24 (DESIGN.md
 * section 9, "Two corners, refit"): Bricolage 700 replacing Outfit SemiBold, the one deliberate
 * exception to "Instrument's word style is unchanged by the redesign" the rest of this bar still
 * is, so the app finally draws "PlainTicker" in the same face the web's TopNav lockup does.
 * [TopBarTest] proves the wider glyphs still clear this row's own one-line clipping budget.
 *
 * @param insets the inset the bar absorbs; pass `WindowInsets(0)` when a parent already pads it.
 * @param onTitleLongPress debug builds only: a long press on the wordmark opens the gallery.
 * @param onAction null with [action] non-null draws the action as a picture of itself, in the
 * same action-text style but with no click target: the onboarding backdrop (task U3) shows the
 * You action this way, the same rule [TopTabs]'s own `onSelect = null` already keeps for its tabs.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String = "PlainTicker",
    action: String? = null,
    onAction: (() -> Unit)? = null,
    meta: String? = null,
    insets: WindowInsets = WindowInsets.statusBars,
    onTitleLongPress: (() -> Unit)? = null,
    colors: AmberColors = defaultAmberColors(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets)
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // The wordmark is decorative for a screen reader; the content speaks for itself. When a
        // debug long press hangs on it, it stays reachable as a labelled button instead of being
        // a hidden target.
        val titleModifier = if (onTitleLongPress != null) {
            Modifier.combinedClickable(
                role = Role.Button,
                onLongClickLabel = "Open the component gallery",
                onLongClick = onTitleLongPress,
                onClick = {},
            )
        } else {
            Modifier.semantics { hideFromAccessibility() }
        }
        Text(
            text = title,
            style = PlainTickerType.wordmark,
            color = colors.textPrimary,
            maxLines = 1,
            modifier = titleModifier,
        )
        when {
            action != null && onAction != null ->
                TextAction(label = action, onClick = onAction, color = colors.actionText)
            // A picture of the action, not the action itself: same action-text colour, no click target.
            action != null ->
                Text(text = action, style = PlainTickerType.textAction, color = colors.actionText, maxLines = 1)
            meta != null ->
                Text(
                    text = meta,
                    style = PlainTickerType.meta,
                    color = colors.textTertiary(AmberSurface.GROUND),
                    maxLines = 1,
                )
        }
    }
}

@InstrumentPreviews
@Composable
private fun TopBarPreview() {
    PreviewCanvas {
        Column {
            TopBar(action = "Watch", onAction = {}, insets = WindowInsets(0))
            // The onboarding backdrop's own case: an action with no handler, drawn as a picture.
            TopBar(action = "You", insets = WindowInsets(0))
            TopBar(meta = "3kF9…Qm2v", insets = WindowInsets(0))
            TopBar(insets = WindowInsets(0))
        }
    }
}
