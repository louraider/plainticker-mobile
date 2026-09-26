package com.plainticker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * [AmberColors.surfaceRaised] with a 1dp [AmberColors.border] border and 16dp padding: the digest,
 * the onboarding text. The only card-like container, for one grouped message, never for lists.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark `Elevated`/`Line`: the one call site ([com.plainticker.mobile.ui.watchlist.WatchlistScreen]'s
 * `Digest`) draws on Today's Yours block, the first thing a reader sees, so a fixed-dark surface
 * here would paint a near-black box on Amber's light ground rather than resolving with the rest of
 * the screen. The border is drawn unconditionally, not gated to light only like
 * [AmberChip]/[SkeletonBar]'s ring, because Instrument's own Panel always carried one; light's own
 * `surfaceRaised` is close to invisible over `surfaceGround` on its own (AmberContrastTest), and
 * this border is what keeps the panel legible as a surface there too.
 *
 * @param inset side padding outside the panel; 0 when the panel is full-bleed.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    inset: Dp = 20.dp,
    colors: AmberColors = defaultAmberColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@InstrumentPreviews
@Composable
private fun PanelPreview() {
    AmberPreviewCanvas {
        val colors = defaultAmberColors()
        Panel(colors = colors) {
            Text(text = "Today 08:00", style = AmberType.meta, color = colors.textTertiary(AmberSurface.RAISED))
            Text(
                text = "3 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 41 days.",
                style = AmberType.body,
                color = colors.textPrimary,
            )
        }
    }
}
