package com.myapp.ui.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.myapp.ui.theme.Accent
import com.myapp.ui.theme.Canvas
import com.myapp.ui.theme.PlainTickerTheme

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
