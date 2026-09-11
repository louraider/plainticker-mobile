package com.myapp.ui.components

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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * 56dp after the status inset: the wordmark left, one text action right (Watch) or one static
 * mono fragment (the wallet). Lives inside the scrolling content, so it scrolls away, never sticky.
 *
 * @param insets the inset the bar absorbs; pass `WindowInsets(0)` when a parent already pads it.
 * @param onTitleLongPress debug builds only: a long press on the wordmark opens the gallery.
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
        val titleModifier = if (onTitleLongPress != null) {
            Modifier.combinedClickable(onLongClick = onTitleLongPress, onClick = {})
        } else {
            Modifier
        }
        // The wordmark is decorative for a screen reader; the content speaks for itself.
        Text(
            text = title,
            style = PlainTickerType.wordmark,
            color = Ink,
            maxLines = 1,
            modifier = titleModifier.semantics { hideFromAccessibility() },
        )
        when {
            action != null && onAction != null -> TextAction(label = action, onClick = onAction)
            meta != null -> Text(text = meta, style = PlainTickerType.meta, color = Muted, maxLines = 1)
        }
    }
}

@InstrumentPreviews
@Composable
private fun TopBarPreview() {
    PreviewCanvas {
        Column {
            TopBar(action = "Watch", onAction = {}, insets = WindowInsets(0))
            TopBar(meta = "3kF9…Qm2v", insets = WindowInsets(0))
            TopBar(insets = WindowInsets(0))
        }
    }
}
