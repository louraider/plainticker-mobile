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
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Line
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * One line of Outfit 13/500 Ink 2 under the tabs ("Today: 3 watched, next report TSLAx on Oct 22").
 * The caller hides it when nothing is watched. Tappable only when it offers a refresh.
 */
@Composable
fun TodayStrip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
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
            Text(text = text, style = PlainTickerType.label, color = Ink2)
        }
        HorizontalDivider(thickness = 1.dp, color = Line)
    }
}

@InstrumentPreviews
@Composable
private fun TodayStripPreview() {
    PreviewCanvas {
        TodayStrip(text = "Today: 3 watched, next report TSLAx on Oct 22")
    }
}
