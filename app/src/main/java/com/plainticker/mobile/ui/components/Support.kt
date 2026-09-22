package com.plainticker.mobile.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.Accent
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberTheme
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.PlainTickerTheme

/** The three preview frames from plan section 13 Pass 6: 360, 412 and 412 at 1.3x font scale. */
@Preview(name = "360", widthDp = 360, showBackground = true, backgroundColor = 0xFF0B0F14)
@Preview(name = "412", widthDp = 412, showBackground = true, backgroundColor = 0xFF0B0F14)
@Preview(name = "412 at 1.3x", widthDp = 412, fontScale = 1.3f, showBackground = true, backgroundColor = 0xFF0B0F14)
annotation class InstrumentPreviews

/** Every component preview renders inside the theme on a Canvas background. */
@Composable
fun PreviewCanvas(content: @Composable () -> Unit) {
    PlainTickerTheme {
        Box(Modifier.fillMaxWidth().background(Canvas)) { content() }
    }
}

/**
 * Every Amber component preview renders inside [AmberTheme] on its own [AmberColors.surfaceGround],
 * not Instrument's [Canvas]: Amber's ground is a warm near-black, not Instrument's cool one
 * (DESIGN.md section 2), and previewing a new component against the palette it was not drawn from
 * would hide a contrast problem [AmberContrastTest] cannot see from a colour pair alone.
 */
@Composable
fun AmberPreviewCanvas(colors: AmberColors = AmberDarkColors, content: @Composable () -> Unit) {
    AmberTheme(useDarkTheme = colors === AmberDarkColors) {
        Box(Modifier.fillMaxWidth().background(colors.surfaceGround)) { content() }
    }
}

/**
 * The theme-following [AmberColors] every shared component reaches for when a call site does not
 * pick one itself: [AmberDarkColors] or [AmberLightColors] by the system setting, recomposing live
 * when that setting changes, the same [isSystemInDarkTheme] primitive `ListScreen`, `PortfolioScreen`
 * and `YouScreen` already compute their own copy of. Named as a default-parameter expression
 * (`colors: AmberColors = defaultAmberColors()`) rather than a `CompositionLocal`: nothing in this
 * codebase wraps every one of these components in one shared `AmberTheme` before reaching them (a
 * component composed straight off `HomeScreen`, `DetailScreen` or the swap and pass sheets has no
 * such ancestor), so a default has to derive the answer the same way a screen's own root does
 * rather than assume one was already provided above it. Kept in `ui/components/` so every shared
 * component in this package can default to it without a new import cycle.
 */
@Composable
fun defaultAmberColors(): AmberColors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors

/** Keyboard or switch-access focus: a 2dp Accent outline, never removed (DESIGN.md section 6). */
@Composable
fun Modifier.focusOutline(interactionSource: InteractionSource): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (focused) this.border(2.dp, Accent) else this
}

/**
 * False when the system animator duration scale is 0 (reduced motion): the live bar stops
 * breathing and the track marker moves instantly. Previews always animate.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

private val Fraction = Regex("(?<=\\d)/(?=\\d)")
private val Multiple = Regex("(?<=\\d)x\\b")
private val LeadingPlus = Regex("^\\+(?=[\\d\\$])")
private val LeadingMinus = Regex("^-(?=[\\d\\$])")

/**
 * One spoken phrase for a value (plan section 13 Pass 6): "8/9" reads "8 of 9", "+0.09%" reads
 * "plus 0.09 percent", "92.4x" reads "92.4 times". Words and tickers pass through unchanged.
 */
internal fun spoken(value: String): String = value
    .replace(Fraction, " of ")
    .replace("%", " percent")
    .replace(Multiple, " times")
    .replace(LeadingPlus, "plus ")
    .replace(LeadingMinus, "minus ")
