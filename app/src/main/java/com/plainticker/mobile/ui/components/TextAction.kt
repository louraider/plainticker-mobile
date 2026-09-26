package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.Accent
import com.plainticker.mobile.ui.theme.Elevated
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.AmberType

/** 14dp vertical padding makes a 48dp target; 16dp start keeps the label flush with the right edge. */
val TextActionPadding: PaddingValues = PaddingValues(start = 16.dp, top = 14.dp, end = 0.dp, bottom = 14.dp)

/**
 * The only kind of secondary action: Bricolage 14/600 ([AmberType.textAction], off Outfit since
 * 2026-09-26) in the caller's colour, role Button, a 48dp minimum target, Elevated while pressed
 * plus the theme's ripple, a 2dp outline while focused.
 */
@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Accent,
    contentPadding: PaddingValues = TextActionPadding,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .focusOutline(interactionSource)
            .background(if (pressed) Elevated else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(contentPadding),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = label,
            style = AmberType.textAction,
            color = if (enabled) color else Muted,
            maxLines = 1,
        )
    }
}

@InstrumentPreviews
@Composable
private fun TextActionPreview() {
    PreviewCanvas {
        Row {
            TextAction(label = "Watch", onClick = {})
            TextAction(label = "Unwatch", onClick = {}, enabled = false)
        }
    }
}
