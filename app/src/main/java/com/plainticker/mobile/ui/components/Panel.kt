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
import com.plainticker.mobile.ui.theme.Elevated
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Line
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * Elevated with a 1dp Line border and 16dp padding: the digest, the onboarding text. The only
 * card-like container, for one grouped message, never for lists.
 *
 * @param inset side padding outside the panel; 0 when the panel is full-bleed.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    inset: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .background(Elevated)
            .border(1.dp, Line)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@InstrumentPreviews
@Composable
private fun PanelPreview() {
    PreviewCanvas {
        Panel {
            Text(text = "Today 08:00", style = PlainTickerType.meta, color = Muted)
            Text(
                text = "3 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 41 days.",
                style = PlainTickerType.panelBody,
                color = Ink,
            )
        }
    }
}
