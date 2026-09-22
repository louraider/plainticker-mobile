package com.plainticker.mobile.ui.theme

import androidx.compose.material3.Typography

/**
 * Material slots mapped onto [AmberType], mirroring [PlainTickerTypography]'s pattern and, for
 * the same reason, kept out of Type.kt: an [AmberType] initializer that read this back would form
 * a class-initialization cycle and see null styles. Screens are meant to use [AmberType] directly.
 */
val AmberTypography: Typography = Typography(
    displayLarge = AmberType.figureLarge,
    displayMedium = AmberType.figureRow,
    displaySmall = AmberType.figureInline,
    headlineLarge = AmberType.sectionHead,
    headlineMedium = AmberType.sectionHead,
    headlineSmall = AmberType.sectionHead,
    titleLarge = AmberType.rowTicker,
    titleMedium = AmberType.rowTicker,
    titleSmall = AmberType.rowCompany,
    bodyLarge = AmberType.body,
    bodyMedium = AmberType.context,
    bodySmall = AmberType.meta,
    labelLarge = AmberType.button,
    labelMedium = AmberType.context,
    labelSmall = AmberType.meta,
)
