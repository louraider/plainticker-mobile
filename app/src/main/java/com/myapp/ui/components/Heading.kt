package com.myapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * A section heading, 20 Outfit 600, with an optional mono meta on the right ("composite 0.71").
 * 32dp above and 14dp below by default; sections are separated by space, not rules. Marked as a
 * heading so a screen-reader user can jump between sections.
 */
@Composable
fun Heading(
    text: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    topPadding: Dp = 32.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            style = PlainTickerType.heading,
            color = Ink,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .semantics { heading() },
        )
        if (meta != null) {
            Text(
                text = meta,
                style = PlainTickerType.monoSmall,
                color = Muted,
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun HeadingPreview() {
    PreviewCanvas {
        Column {
            Heading(text = "Backing and controls", topPadding = 28.dp)
            Heading(text = "Against the sector", meta = "composite 0.71")
        }
    }
}
