@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.Accent
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Line
import com.plainticker.mobile.ui.theme.LineStrong
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerRippleAlpha
import com.plainticker.mobile.ui.theme.PlainTickerType

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)

/** The theme ripple is Accent, invisible on an Accent fill; the primary button ripples in Canvas. */
private val PrimaryRipple = RippleConfiguration(color = Canvas, rippleAlpha = PlainTickerRippleAlpha)

/** 56dp, Accent fill, Canvas text 16/600, radius 0. Renders as [DisabledButton] when not enabled. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) {
        DisabledButton(label = label, modifier = modifier)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        ButtonFrame(modifier = modifier, interactionSource = interactionSource) {
            CompositionLocalProvider(LocalRippleConfiguration provides PrimaryRipple) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
                    elevation = null,
                    contentPadding = ButtonPadding,
                    interactionSource = interactionSource,
                ) {
                    Text(text = label, style = PlainTickerType.button, maxLines = 1)
                }
            }
        }
    }
}

/** 56dp, transparent, 1dp Line strong border, Ink text. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    ButtonFrame(modifier = modifier, interactionSource = interactionSource) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Ink),
            border = BorderStroke(1.dp, LineStrong),
            contentPadding = ButtonPadding,
            interactionSource = interactionSource,
        ) {
            Text(text = label, style = PlainTickerType.button, maxLines = 1)
        }
    }
}

/** 56dp, transparent, 1dp Line border, Muted text. Not a target. */
@Composable
fun DisabledButton(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Button(
            onClick = {},
            modifier = Modifier.fillMaxSize(),
            enabled = false,
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Muted,
            ),
            elevation = null,
            border = BorderStroke(1.dp, Line),
            contentPadding = ButtonPadding,
        ) {
            Text(text = label, style = PlainTickerType.button, maxLines = 1)
        }
    }
}

/**
 * 56dp frame. While focused it draws the 2dp Accent outline with a 2dp Canvas gap, so the ring
 * reads on a filled button as well as on a bordered one.
 */
@Composable
private fun ButtonFrame(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    content: @Composable () -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (focused) Modifier.border(2.dp, Accent).padding(4.dp) else Modifier),
    ) {
        content()
    }
}

@InstrumentPreviews
@Composable
private fun ButtonsPreview() {
    PreviewCanvas {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(label = "Swap USDC to TSLAx", onClick = {})
            SecondaryButton(label = "View in Portfolio", onClick = {})
            DisabledButton(label = "Read the list")
        }
    }
}
