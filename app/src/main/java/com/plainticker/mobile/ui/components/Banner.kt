package com.plainticker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberType

/**
 * The one state slot under the TopBar: [AmberColors.surfaceRaised] (Instrument's fixed
 * [com.plainticker.mobile.ui.theme.Elevated] before this fix; the same class of fault this pass
 * looks for in every one of its six components,
 * this one just had no `colors` parameter at all rather than one that defaulted dark), Bricolage
 * 13/500 ([AmberType.label]) in [AmberColors.textSecondary], one optional text action (Retry) in
 * [AmberColors.actionText]. Priority is the caller's: offline, then stale, then hours, then device.
 * [colors] defaults to the system-following [defaultAmberColors] like every other shared component.
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    colors: AmberColors = defaultAmberColors(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            style = AmberType.label,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (action != null && onAction != null) {
            TextAction(
                label = action,
                onClick = onAction,
                color = colors.actionText,
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 0.dp, bottom = 12.dp),
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun BannerPreview() {
    AmberPreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Banner(text = "Offline, data as of 14:55")
            Banner(text = "Analysis list unavailable", action = "Retry", onAction = {})
        }
    }
}
