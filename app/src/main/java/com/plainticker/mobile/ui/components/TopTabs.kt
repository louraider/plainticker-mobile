package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * List, Vote, Portfolio, Watchlist as text tabs: 14sp Outfit, the selected one in
 * [AmberColors.textPrimary] with a 2dp [AmberColors.actionText] underline, the rest
 * [AmberColors.textTertiary], a [AmberColors.border] hairline below. No icons, no bottom bar.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark `Ink`/`Accent`/`Muted`/`Line`: the onboarding backdrop below draws this live, over
 * that screen's own [AmberColors.surfaceGround], so a fixed-dark colour here drew near-invisible
 * text on Amber's light ground. [com.plainticker.mobile.ui.gallery.GalleryScreen] (debug builds
 * only) passes the fixed [com.plainticker.mobile.ui.theme.AmberDarkColors] instead, matching its
 * own permanently-dark canvas comparison rather than following the live system setting.
 *
 * Four labels at DESIGN.md's 1.3x font scale ceiling is untested by a layout test: this module's
 * unit tests run on a plain JVM with no Robolectric and no instrumentation, so nothing here can
 * actually measure text at a font scale. The row scrolls horizontally instead of assuming the
 * four labels always fit the 400dp Seeker frame, so a scale this build cannot check for itself
 * degrades into a scroll rather than clipping "Watchlist" off the edge.
 *
 * @param onSelect null draws the row as a picture of itself: no target, no focus, no ripple. The
 * onboarding backdrop (DT11) shows the List tab that way.
 */
@Composable
fun TopTabs(
    items: List<String>,
    selected: Int,
    onSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    Column(modifier.fillMaxWidth()) {
        // 8dp row inset plus 12dp per tab puts the first label at 20dp and 24dp between labels.
        // defaultMinSize comes before width(IntrinsicSize.Max): the intrinsic width fixes the
        // constraints, so a minimum applied after it would be ignored and a short label could
        // fall under the 48dp target. horizontalScroll costs nothing when the row already fits
        // (its content is no wider than the viewport, so there is nothing to scroll) and is the
        // difference between a clipped label and a reachable one when it does not.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val on = index == selected
                val interactionSource = remember { MutableInteractionSource() }
                val target = if (onSelect == null) {
                    Modifier
                } else {
                    Modifier
                        .focusOutline(interactionSource, colors)
                        .selectable(
                            selected = on,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                }
                Column(
                    modifier = Modifier
                        .then(target)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .width(IntrinsicSize.Max)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = item,
                        style = if (on) PlainTickerType.tabSelected else PlainTickerType.tab,
                        color = if (on) colors.textPrimary else colors.textTertiary(AmberSurface.GROUND),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (on) colors.actionText else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

@InstrumentPreviews
@Composable
private fun TopTabsPreview() {
    AmberPreviewCanvas {
        var selected by remember { mutableIntStateOf(0) }
        Column {
            TopTabs(items = listOf("List", "Vote", "Portfolio", "Watchlist"), selected = selected, onSelect = { selected = it })
            TopTabs(items = listOf("List", "Vote", "Portfolio", "Watchlist"), selected = 0, onSelect = null)
        }
    }
}
