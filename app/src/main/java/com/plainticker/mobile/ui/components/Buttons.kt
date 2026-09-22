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
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerRippleAlpha
import com.plainticker.mobile.ui.theme.PlainTickerType

private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)

/**
 * 56dp, filled with [AmberColors.actionFill], radius 0. Renders as [DisabledButton] when not
 * enabled. [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark Accent/Canvas: this button and [SecondaryButton] are what the swap sheet and the
 * pass sheet still reach for (money and entitlement, DESIGN.md section 1), so a button that stayed
 * Instrument-dark on an Amber-light sheet is exactly the fault this pass exists to close.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: AmberColors = defaultAmberColors(),
) {
    if (!enabled) {
        DisabledButton(label = label, modifier = modifier, colors = colors)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val ripple = RippleConfiguration(color = colors.actionOnFill, rippleAlpha = PlainTickerRippleAlpha)
        ButtonFrame(modifier = modifier, interactionSource = interactionSource, focusColor = colors.actionText) {
            CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.actionFill,
                        contentColor = colors.actionOnFill,
                    ),
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

/** 56dp, transparent, 1dp bordered, [AmberColors.textPrimary] text. See [PrimaryButton] on [colors]. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    ButtonFrame(modifier = modifier, interactionSource = interactionSource, focusColor = colors.actionText) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = colors.textPrimary,
            ),
            border = BorderStroke(1.dp, colors.border),
            contentPadding = ButtonPadding,
            interactionSource = interactionSource,
        ) {
            Text(text = label, style = PlainTickerType.button, maxLines = 1)
        }
    }
}

/** 56dp, transparent, 1dp bordered, tertiary text. Not a target. See [PrimaryButton] on [colors]. */
@Composable
fun DisabledButton(
    label: String,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Button(
            onClick = {},
            modifier = Modifier.fillMaxSize(),
            enabled = false,
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.textTertiary(AmberSurface.GROUND),
            ),
            elevation = null,
            border = BorderStroke(1.dp, colors.border),
            contentPadding = ButtonPadding,
        ) {
            Text(text = label, style = PlainTickerType.button, maxLines = 1)
        }
    }
}

/**
 * 56dp frame. While focused it draws a 2dp [focusColor] outline with a 2dp gap, so the ring reads
 * on a filled button as well as on a bordered one.
 */
@Composable
private fun ButtonFrame(
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    focusColor: Color,
    content: @Composable () -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (focused) Modifier.border(2.dp, focusColor).padding(4.dp) else Modifier),
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
