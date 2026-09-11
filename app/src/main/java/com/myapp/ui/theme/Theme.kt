@file:OptIn(ExperimentalMaterial3Api::class)

package com.myapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * Every Material slot set from the nine tokens (DESIGN.md section 2, plan section 13 Pass 2), so
 * nothing baseline-purple can leak through an M3 component. Dark only; never dynamic color.
 */
val PlainTickerColorScheme: ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Canvas,
    primaryContainer = Elevated,
    onPrimaryContainer = Ink,
    inversePrimary = Accent,
    secondary = Ink2,
    onSecondary = Canvas,
    secondaryContainer = Elevated,
    onSecondaryContainer = Ink,
    tertiary = Muted,
    onTertiary = Canvas,
    tertiaryContainer = Elevated,
    onTertiaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = Elevated,
    onSurfaceVariant = Ink2,
    // Tinting the surface with its own color keeps tonal elevation a no-op.
    surfaceTint = Canvas,
    inverseSurface = Ink,
    inverseOnSurface = Canvas,
    error = Caution,
    onError = Canvas,
    errorContainer = Elevated,
    onErrorContainer = Caution,
    outline = LineStrong,
    outlineVariant = Line,
    scrim = Canvas,
    surfaceBright = Elevated,
    surfaceDim = Canvas,
    surfaceContainer = Elevated,
    surfaceContainerHigh = Elevated,
    surfaceContainerHighest = Elevated,
    surfaceContainerLow = Elevated,
    surfaceContainerLowest = Canvas,
    primaryFixed = Accent,
    primaryFixedDim = Accent,
    onPrimaryFixed = Canvas,
    onPrimaryFixedVariant = Canvas,
    secondaryFixed = Elevated,
    secondaryFixedDim = Elevated,
    onSecondaryFixed = Ink,
    onSecondaryFixedVariant = Ink2,
    tertiaryFixed = Elevated,
    tertiaryFixedDim = Elevated,
    onTertiaryFixed = Ink,
    onTertiaryFixedVariant = Ink2,
)

private val Sharp = RoundedCornerShape(0.dp)

/**
 * Shape lock: radius 0 on every public slot. Material3 1.4.0 keeps the three expressive slots
 * (largeIncreased, extraLargeIncreased, extraExtraLarge) internal; only expressive components
 * read them and none is used here.
 */
val PlainTickerShapes: Shapes = Shapes(
    extraSmall = Sharp,
    small = Sharp,
    medium = Sharp,
    large = Sharp,
    extraLarge = Sharp,
)

/** Pressed state: Accent ripple at 10 percent (DESIGN.md section 6). */
private val PlainTickerRipple = RippleConfiguration(
    color = Accent,
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.10f,
        focusedAlpha = 0.10f,
        hoveredAlpha = 0.06f,
        pressedAlpha = 0.10f,
    ),
)

private val PlainTickerSelection = TextSelectionColors(
    handleColor = Accent,
    backgroundColor = Accent.copy(alpha = 0.30f),
)

/**
 * The one theme. Dark regardless of the system setting (a light variant is deferred, see
 * DESIGN.md section 2); dynamic color is never consulted.
 */
@Composable
fun PlainTickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlainTickerColorScheme,
        typography = PlainTickerTypography,
        shapes = PlainTickerShapes,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides PlainTickerRipple,
            LocalTextSelectionColors provides PlainTickerSelection,
            LocalContentColor provides Ink,
            content = content,
        )
    }
}
