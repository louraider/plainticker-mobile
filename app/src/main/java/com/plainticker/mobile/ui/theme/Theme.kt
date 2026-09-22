@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.lightColorScheme
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

/** Pressed state: a ripple at 10 percent (DESIGN.md section 6). */
val PlainTickerRippleAlpha: RippleAlpha = RippleAlpha(
    draggedAlpha = 0.10f,
    focusedAlpha = 0.10f,
    hoveredAlpha = 0.06f,
    pressedAlpha = 0.10f,
)

/** The theme ripple is Accent; a surface filled with Accent (PrimaryButton) ripples in Canvas instead. */
private val PlainTickerRipple = RippleConfiguration(color = Accent, rippleAlpha = PlainTickerRippleAlpha)

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

// ============================================================================================
// Amber (docs/design-research-2026-09-21.md section 5.3). Additive, like Tokens.kt's semantic
// layer: [AmberTheme] exists and is fully wired, but MainActivity still calls [PlainTickerTheme].
// Swapping the app over is restyle-phase work across every screen (the calendar in section 7
// treats "restyle ListRow, Heading, Buttons, Sheet, FactGrid, Track, Gauge" as its own line), not
// foundation work, and it cannot be checked without a device this task was told not to touch.
//
// Material3 1.4.0 (the version this app compiles against; there is no 1.5.0-alpha in this
// machine's Gradle cache) was unpacked and read directly to answer two questions before writing
// this. First, the one the research already answered and this file re-checked the same way:
// `ShortNavigationBarKt.class` and `MaterialThemeKt.class` (MaterialExpressiveTheme) carry no
// ExperimentalMaterial3ExpressiveApi or ExperimentalMaterial3Api annotation, and
// `ButtonGroupKt.class`, `LoadingIndicatorKt.class`, `FlexibleBottomAppBar` and any shape-morphing
// class are genuinely absent from the jar, confirmed by class listing, not by trusting the
// research doc. Second, a question the research did not ask: `ColorSchemeKt.class` exposes
// `expressiveLightColorScheme()` but no `expressiveDarkColorScheme()` at all, and the one
// function that does exist takes zero parameters, so it cannot carry Amber's own palette even
// for the light set. Amber's colour schemes below are built the same way [PlainTickerColorScheme]
// already is, with the stable, non-expressive [darkColorScheme] and [lightColorScheme]
// constructors, every slot mapped by hand: no new dependency, nothing experimental, and nothing
// this codebase was not already doing.
// ============================================================================================

/** Every Material slot mapped from [c], the same way [PlainTickerColorScheme] is from Instrument. */
private fun amberColorScheme(c: AmberColors, dark: Boolean): ColorScheme {
    val onLow = c.surfaceGround
    val tertiary = c.textTertiary(AmberSurface.GROUND)
    return if (dark) {
        darkColorScheme(
            primary = c.actionFill, onPrimary = c.actionOnFill,
            primaryContainer = c.surfaceRaised, onPrimaryContainer = c.textPrimary,
            inversePrimary = c.actionFill,
            secondary = c.textSecondary, onSecondary = onLow,
            secondaryContainer = c.surfaceRaised, onSecondaryContainer = c.textPrimary,
            tertiary = tertiary, onTertiary = onLow,
            tertiaryContainer = c.surfaceRaised, onTertiaryContainer = c.textPrimary,
            background = c.surfaceGround, onBackground = c.textPrimary,
            surface = c.surfaceGround, onSurface = c.textPrimary,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary,
            surfaceTint = c.surfaceGround,
            inverseSurface = c.textPrimary, inverseOnSurface = c.surfaceGround,
            error = c.stateCaution, onError = onLow,
            errorContainer = c.surfaceRaised, onErrorContainer = c.stateCaution,
            outline = c.border, outlineVariant = c.border,
            scrim = c.surfaceGround,
            surfaceBright = c.surfaceRaised, surfaceDim = c.surfaceGround,
            surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceHigh, surfaceContainerHighest = c.surfaceHigh,
            surfaceContainerLow = c.surfaceRaised, surfaceContainerLowest = c.surfaceGround,
            primaryFixed = c.actionFill, primaryFixedDim = c.actionFill,
            onPrimaryFixed = onLow, onPrimaryFixedVariant = onLow,
            secondaryFixed = c.surfaceRaised, secondaryFixedDim = c.surfaceRaised,
            onSecondaryFixed = c.textPrimary, onSecondaryFixedVariant = c.textSecondary,
            tertiaryFixed = c.surfaceRaised, tertiaryFixedDim = c.surfaceRaised,
            onTertiaryFixed = c.textPrimary, onTertiaryFixedVariant = c.textSecondary,
        )
    } else {
        lightColorScheme(
            primary = c.actionFill, onPrimary = c.actionOnFill,
            primaryContainer = c.surfaceRaised, onPrimaryContainer = c.textPrimary,
            inversePrimary = c.actionFill,
            secondary = c.textSecondary, onSecondary = onLow,
            secondaryContainer = c.surfaceRaised, onSecondaryContainer = c.textPrimary,
            tertiary = tertiary, onTertiary = onLow,
            tertiaryContainer = c.surfaceRaised, onTertiaryContainer = c.textPrimary,
            background = c.surfaceGround, onBackground = c.textPrimary,
            surface = c.surfaceGround, onSurface = c.textPrimary,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary,
            surfaceTint = c.surfaceGround,
            inverseSurface = c.textPrimary, inverseOnSurface = c.surfaceGround,
            error = c.stateCaution, onError = onLow,
            errorContainer = c.surfaceRaised, onErrorContainer = c.stateCaution,
            outline = c.border, outlineVariant = c.border,
            scrim = c.surfaceGround,
            surfaceBright = c.surfaceRaised, surfaceDim = c.surfaceGround,
            surfaceContainer = c.surfaceRaised,
            surfaceContainerHigh = c.surfaceHigh, surfaceContainerHighest = c.surfaceHigh,
            surfaceContainerLow = c.surfaceRaised, surfaceContainerLowest = c.surfaceGround,
            primaryFixed = c.actionFill, primaryFixedDim = c.actionFill,
            onPrimaryFixed = onLow, onPrimaryFixedVariant = onLow,
            secondaryFixed = c.surfaceRaised, secondaryFixedDim = c.surfaceRaised,
            onSecondaryFixed = c.textPrimary, onSecondaryFixedVariant = c.textSecondary,
            tertiaryFixed = c.surfaceRaised, tertiaryFixedDim = c.surfaceRaised,
            onTertiaryFixed = c.textPrimary, onTertiaryFixedVariant = c.textSecondary,
        )
    }
}

/** Amber, dark set (docs/design-research-2026-09-21.md section 5.3, "Dark"). */
val AmberDarkColorScheme: ColorScheme = amberColorScheme(AmberDarkColors, dark = true)

/** Amber, light set (section 5.3, "Light"); in scope because the founder cut nothing. */
val AmberLightColorScheme: ColorScheme = amberColorScheme(AmberLightColors, dark = false)

/**
 * Radii by hierarchy (section 5.3's shape line): 8dp chips, 16dp list containers, 28dp for the
 * status card and a sheet's top radius. A chip morphing from 8dp to full radius on selection is
 * runtime, per-component shape animation, not a static token, so it is not built here; it is
 * restyle-phase work once there is a chip component to animate.
 */
val AmberShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Amber, dark and light, built the same way [PlainTickerTheme] is: every Material slot set on
 * purpose so nothing baseline can leak through. Not yet the app's active theme; see this file's
 * Amber section comment above for why.
 */
@Composable
fun AmberTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) AmberDarkColorScheme else AmberLightColorScheme,
        typography = AmberTypography,
        shapes = AmberShapes,
        content = content,
    )
}
