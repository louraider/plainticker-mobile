package com.plainticker.mobile.ui.theme

import androidx.compose.material3.Typography

/**
 * Material slots mapped onto the scale so any M3 component that reads [Typography] stays in the
 * two faces. Screens use [PlainTickerType] directly.
 *
 * Kept in its own file on purpose: [PlainTickerType]'s initializer calls helpers in Type.kt, so a
 * Type.kt-level value that read [PlainTickerType] back would form a class-initialization cycle
 * and see null styles.
 */
val PlainTickerTypography: Typography = Typography(
    displayLarge = PlainTickerType.heroTicker,
    displayMedium = PlainTickerType.heroPrice,
    displaySmall = PlainTickerType.fScoreNumeral,
    headlineLarge = PlainTickerType.onboardingTitle,
    headlineMedium = PlainTickerType.heading,
    headlineSmall = PlainTickerType.heading,
    titleLarge = PlainTickerType.sheetTitle,
    titleMedium = PlainTickerType.rowLabel,
    titleSmall = PlainTickerType.wordmark,
    bodyLarge = PlainTickerType.body,
    bodyMedium = PlainTickerType.rowText,
    bodySmall = PlainTickerType.small,
    labelLarge = PlainTickerType.button,
    labelMedium = PlainTickerType.textAction,
    labelSmall = PlainTickerType.label,
)
