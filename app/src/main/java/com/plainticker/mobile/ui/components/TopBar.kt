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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
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
 * fault this pass exists to find. The wordmark moved to Bricolage 700 on 2026-09-24 (DESIGN.md
 * section 9, "Two corners, refit"), so the app draws "PlainTicker" in the same face the web's
 * TopNav lockup does, and the action followed on 2026-09-26 ([AmberType.textAction], off Outfit):
 * every word on this bar is Bricolage now, and only a wallet's short key stays in JetBrains Mono.
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
        // The lockup: the two-corners mark, cropped tight to its own block
        // (ic_brand_mark_tight, design/brand/glyph.py) so the glyph is the box, sized to the
        // wordmark's cap height and tinted with actionText so it follows dark and light. The
        // size is in sp, so it grows with font scale exactly as the wordmark beside it does;
        // TopBarTest proves the pair plus the widest trailing action still fit at 1.0x and 1.3x.
        // Decorative: the wordmark beside it already names the app, so it says nothing to a
        // screen reader.
        val markSize = with(LocalDensity.current) { WordmarkCapHeight.toDp() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = titleModifier) {
            Icon(
                painter = painterResource(R.drawable.ic_brand_mark_tight),
                contentDescription = null,
                tint = colors.actionText,
                modifier = Modifier.size(markSize),
            )
            Spacer(Modifier.width(MarkGap))
            Text(
                text = title,
                style = AmberType.wordmark,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
        when {
            action != null && onAction != null ->
                TextAction(label = action, onClick = onAction, color = colors.actionText)
            // A picture of the action, not the action itself: same action-text colour, no click target.
            action != null ->
                Text(text = action, style = AmberType.textAction, color = colors.actionText, maxLines = 1)
            meta != null ->
                // A wallet's short key: an on-chain identifier, the one thing JetBrains Mono is kept for.
                Text(
                    text = meta,
                    style = PlainTickerType.meta,
                    color = colors.textTertiary(AmberSurface.GROUND),
                    maxLines = 1,
                )
        }
    }
}

/**
 * The wordmark's cap height: [AmberType.wordmark] is Bricolage 700 at 15sp, and the bundled
 * variable font's OS/2 `sCapHeight` is 660 of 1000 units at that instance (fontTools, 2026-09-24),
 * so 9.9sp. The mark is drawn exactly that tall, so it stands level with the "P".
 */
internal val WordmarkCapHeight = (15f * 660f / 1000f).sp

/** Between the mark and the wordmark: a little over half a cap height, read as one lockup. */
internal val MarkGap = 6.dp

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
