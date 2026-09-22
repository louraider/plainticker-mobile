package com.plainticker.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * One line of Outfit 13/500 under the tabs ("Today: 3 watched, next report TSLAx on Oct 22"). The
 * caller hides it when nothing is watched. Tappable only when it offers a refresh.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark `Ink2`/`Line`: [com.plainticker.mobile.ui.list.ListScreen]'s own Stocks chrome draws
 * this live, on [AmberColors.surfaceGround], whenever a reader has watched a ticker, so a
 * fixed-dark colour here drew near-invisible text on Amber's light ground rather than resolving
 * with the rest of that screen. [com.plainticker.mobile.ui.gallery.GalleryScreen] (debug builds
 * only) passes the fixed [com.plainticker.mobile.ui.theme.AmberDarkColors] instead, matching its
 * own permanently-dark canvas comparison rather than following the live system setting.
 */
@Composable
fun TodayStrip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: AmberColors = defaultAmberColors(),
) {
    Column(modifier.fillMaxWidth()) {
        val tappable = if (onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = onClick).defaultMinSize(minHeight = 48.dp)
        } else {
            Modifier
        }
        Box(
            modifier = Modifier.fillMaxWidth().then(tappable).padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = text, style = PlainTickerType.label, color = colors.textSecondary)
        }
        HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
}

@InstrumentPreviews
@Composable
private fun TodayStripPreview() {
    AmberPreviewCanvas {
        TodayStrip(text = "Today: 3 watched, next report TSLAx on Oct 22")
    }
}
