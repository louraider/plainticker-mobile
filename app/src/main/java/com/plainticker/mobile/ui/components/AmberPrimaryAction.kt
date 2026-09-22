package com.plainticker.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.theme.PlainTickerRippleAlpha

/**
 * Amber's primary action (docs/design-research-2026-09-21.md section 5.5): 56dp, 16dp radius
 * (matching `AmberShapes.medium` in Theme.kt, a literal here only because a bottom-only or
 * all-corners shape both read as "16dp" and this button needs all four), amber fill, dark text on
 * it. This is the control the swap sheet, vote and pass all reach for, and the rule that survives
 * every pass of this redesign because it was never aesthetic: "no state's only forward action is a
 * text link" (this task's brief; DESIGN.md's Instrument anatomy made the same point with
 * [PrimaryButton]). A real button, not [TextAction], is what makes that rule true here.
 *
 * "Springs on press" (research 5.5) is motion-token work DESIGN.md section 6 defers; what plays
 * today is the theme's own ripple, recoloured so it shows up on an amber fill the way
 * [PrimaryButton]'s Canvas-coloured ripple shows up on Instrument's Accent fill.
 */
@Composable
fun AmberPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: AmberColors = defaultAmberColors(),
) {
    if (!enabled) {
        AmberDisabledAction(label = label, modifier = modifier, colors = colors)
        return
    }
    val ripple = RippleConfiguration(color = colors.actionOnFill, rippleAlpha = PlainTickerRippleAlpha)
    Box(modifier.fillMaxWidth().height(56.dp)) {
        CompositionLocalProvider(LocalRippleConfiguration provides ripple) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                shape = AmberActionShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.actionFill, contentColor = colors.actionOnFill),
                elevation = null,
                contentPadding = AmberActionPadding,
            ) {
                Text(text = label, style = AmberType.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The one state [AmberPrimaryAction] does not fill with amber: transparent with a 1dp
 * [AmberColors.border] and [AmberColors.textTertiary] text, so a disabled forward action still
 * reads as a button and not as the text-link shape this task's brief bans.
 */
@Composable
fun AmberDisabledAction(
    label: String,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Button(
            onClick = {},
            modifier = Modifier.fillMaxSize(),
            enabled = false,
            shape = AmberActionShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Color.Transparent,
                disabledContentColor = colors.textTertiary(AmberSurface.GROUND),
            ),
            elevation = null,
            border = BorderStroke(1.dp, colors.border),
            contentPadding = AmberActionPadding,
        ) {
            Text(text = label, style = AmberType.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val AmberActionShape = RoundedCornerShape(16.dp)
private val AmberActionPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)

@InstrumentPreviews
@Composable
private fun AmberPrimaryActionPreview() {
    AmberPreviewCanvas {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AmberPrimaryAction(label = "Swap USDC to TSLAx", onClick = {})
            AmberDisabledAction(label = "Read the list")
        }
    }
}
