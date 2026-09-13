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
import com.plainticker.mobile.ui.theme.Elevated
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * The one state slot under the TopBar: Elevated, Outfit 13/500, one optional text action
 * (Retry). Priority is the caller's: offline, then stale, then hours, then device.
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Elevated)
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            style = PlainTickerType.label,
            color = Ink2,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        if (action != null && onAction != null) {
            TextAction(
                label = action,
                onClick = onAction,
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 0.dp, bottom = 12.dp),
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun BannerPreview() {
    PreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Banner(text = "Offline, data as of 14:55")
            Banner(text = "Analysis list unavailable", action = "Retry", onAction = {})
        }
    }
}
