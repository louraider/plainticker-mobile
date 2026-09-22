package com.plainticker.mobile.ui.theme

import androidx.compose.ui.graphics.Color

// DESIGN.md section 2. These nine are the whole palette; nothing else may appear on a surface.
// Exactly one accent. No green or red for price direction anywhere. No gradients, shadows or glass.

/** Page background. Cool near-black, never pure black. */
val Canvas: Color = Color(0xFF0B0F14)

/** Sheets, the onboarding panel, the digest panel. The only second surface. */
val Elevated: Color = Color(0xFF121820)

/** Primary text and numerals. */
val Ink: Color = Color(0xFFE8ECF1)

/** Secondary text, labels of facts, body copy. */
val Ink2: Color = Color(0xFFB4BCC8)

/** Metadata, placeholders, disabled text, inactive tabs. */
val Muted: Color = Color(0xFF7F8A99)

/** Hairlines, grid gaps, row dividers. Ink at 10 percent. */
val Line: Color = Ink.copy(alpha = 0.10f)

/** Field underlines, sheet top edge, secondary button border, gauge track. Ink at 22 percent. */
val LineStrong: Color = Ink.copy(alpha = 0.22f)

/**
 * Every interactive text action, the active tab indicator, the primary button fill, the live bar,
 * the token tick on the gauge. Nothing else.
 */
val Accent: Color = Color(0xFF5AA9E6)

/**
 * Only the value of an explicit issuer-control risk (permanent delegate present, transfers
 * pausable). Never on prices, premiums, scores or list rows.
 */
val Caution: Color = Color(0xFFD9A441)

/** The nine tokens, for tests and lint. */
val AllTokens: Set<Color> = setOf(Canvas, Elevated, Ink, Ink2, Muted, Line, LineStrong, Accent, Caution)

// ============================================================================================
// Amber (docs/design-research-2026-09-21.md section 5.3), the founder's pick for the redesign.
// The new DESIGN.md documents this set. The nine values above are Instrument's and are left
// exactly as they are: BrandAssetsTest pins the launcher icon to them, PlainTickerThemeTest pins
// PlainTickerColorScheme to them, and every existing composable still reads them, so repainting
// them here would be a silent, untested restyle of the whole app and the icon it ships today.
// Moving a composable over to the roles below is restyle-phase work, not foundation work.
//
// Two tiers, which is what the research says the old Tokens.kt lacked: nine primitives named
// for their colour and read directly by more than sixty call sites, which is how the old Accent
// ended up meaning "tab indicator" and "button fill" and "live bar" and "gauge tick" all at
// once. AmberPrimitive below is the raw palette; nothing outside this file reads it. AmberColors
// is the semantic tier: every property is named for the role it plays (surface.ground,
// text.secondary, action.fill...), so a caller reaches for what a thing IS, never for what
// colour it happens to be right now.
//
// Values below are transcribed from docs/design-research-2026-09-21.md section 5.3, cross-
// checked on 2026-09-22 against the "amber" dict in the mockup generator
// (scratchpad/design/mockups/gen.py, the file the approved canvas was drawn from) and against
// contrast.py's own "Amber" dict. All three agree on every hex value; there is nothing to report
// as a disagreement.
// ============================================================================================

/** Raw hex only. Read by [AmberDarkColors] and [AmberLightColors]; nothing else may reference it. */
private object AmberPrimitive {
    // Dark, section 5.3's "Dark" column.
    val groundDark: Color = Color(0xFF16130D)
    val raisedDark: Color = Color(0xFF221E15)
    val highDark: Color = Color(0xFF2E281C)
    val text1Dark: Color = Color(0xFFF5EEDD)
    val text2Dark: Color = Color(0xFFC6BCA4)
    val text3Dark: Color = Color(0xFF948B74)
    val borderDark: Color = Color(0xFF3A3324)
    val fillDark: Color = Color(0xFFFFC247)
    val onFillDark: Color = Color(0xFF3B2800)
    val cautionDark: Color = Color(0xFFFF6B57)

    // Light, section 5.3's "Light" column. The founder cut nothing from the redesign's scope, so
    // this set ships even though the research calls Amber dark-first and only drew dark mockups.
    val groundLight: Color = Color(0xFFFFFBF2)
    val raisedLight: Color = Color(0xFFFFFFFF)
    val highLight: Color = Color(0xFFF3EBD6)
    val text1Light: Color = Color(0xFF1F1A0E)
    val text2Light: Color = Color(0xFF5A5240)
    val text3Light: Color = Color(0xFF7A7059)
    val borderLight: Color = Color(0xFFE2D9C2)
    val fillLight: Color = Color(0xFF7A5600)
    val cautionLight: Color = Color(0xFFB4220C)
    /** The research states this pair shares one value with [raisedLight]; not a second colour. */
    val onFillLight: Color = raisedLight
}

/** The three surfaces a role can be drawn on. See [AmberColors.textTertiary]. */
enum class AmberSurface { GROUND, RAISED, HIGH }

/**
 * Amber's semantic roles: one instance per theme ([AmberDarkColors], [AmberLightColors]).
 * Composables are meant to read this class, never [AmberPrimitive], so a surface can never be
 * reached for as if it were an accent.
 */
class AmberColors(
    /** Page background. */
    val surfaceGround: Color,
    /** Cards, tonal containers, the status card. */
    val surfaceRaised: Color,
    /** The highest surface: a selected chip, a sheet. [textTertiary] refuses to sit here. */
    val surfaceHigh: Color,
    /** Primary text and figures. */
    val textPrimary: Color,
    /** Secondary text, context lines, row captions. */
    val textSecondary: Color,
    private val textTertiaryOnLow: Color,
    /** Hairlines, the one border an active chip carries. */
    val border: Color,
    /** Primary button fill; the selected chip once it morphs to full radius. */
    val actionFill: Color,
    /** Text and icon drawn on [actionFill]. */
    val actionOnFill: Color,
    /** Every interactive text action and the active tab's glyph and label. */
    val actionText: Color,
    /** The live bar and the token tick on the gauge; equal to [actionText] in every set drawn. */
    val stateLive: Color,
    /** The only red-orange anywhere; never on a number (section 5.3). */
    val stateCaution: Color,
) {
    /**
     * text.tertiary never sits on surface.high (docs/design-research-2026-09-21.md section 4:
     * "it measures 4.1 to 4.4:1 there in every set"). Amber's own pair, computed the same way as
     * contrast.py, is 4.32:1 dark and 4.12:1 light: inside that band and under the 4.5:1 normal-
     * text AA floor, so drawing it there is a real accessibility failure, not a rounding error.
     *
     * Asking for tertiary text on the highest surface promotes to [textSecondary] instead of
     * handing back the failing colour, so the rule holds even where a call site forgets it:
     * there is no other way to read [textTertiaryOnLow] out of this class. AmberContrastTest
     * pins both the raw ratio and this promotion.
     */
    fun textTertiary(on: AmberSurface): Color = if (on == AmberSurface.HIGH) textSecondary else textTertiaryOnLow
}

/**
 * Ratios are WCAG contrast against [AmberColors.surfaceGround] (against [AmberColors.actionFill]
 * for [AmberColors.actionOnFill]), from docs/design-research-2026-09-21.md section 5.3 and pinned
 * by AmberContrastTest.
 */
val AmberDarkColors: AmberColors = AmberColors(
    surfaceGround = AmberPrimitive.groundDark,
    surfaceRaised = AmberPrimitive.raisedDark,
    surfaceHigh = AmberPrimitive.highDark,
    textPrimary = AmberPrimitive.text1Dark, // 16.0:1
    textSecondary = AmberPrimitive.text2Dark, // 9.8:1
    textTertiaryOnLow = AmberPrimitive.text3Dark, // 5.5:1
    border = AmberPrimitive.borderDark,
    actionFill = AmberPrimitive.fillDark,
    actionOnFill = AmberPrimitive.onFillDark, // 8.8:1 on actionFill
    actionText = AmberPrimitive.fillDark, // 11.5:1
    stateLive = AmberPrimitive.fillDark,
    stateCaution = AmberPrimitive.cautionDark, // 6.6:1
)

/** See [AmberDarkColors]; same source, the "Light" column. */
val AmberLightColors: AmberColors = AmberColors(
    surfaceGround = AmberPrimitive.groundLight,
    surfaceRaised = AmberPrimitive.raisedLight,
    surfaceHigh = AmberPrimitive.highLight,
    textPrimary = AmberPrimitive.text1Light, // 16.8:1
    textSecondary = AmberPrimitive.text2Light, // 7.5:1
    textTertiaryOnLow = AmberPrimitive.text3Light, // 4.7:1
    border = AmberPrimitive.borderLight,
    actionFill = AmberPrimitive.fillLight,
    // Section 5.3's prose table states 6.7:1; contrast.py, its own calculator, computes 6.65:1
    // for this exact pair. AmberContrastTest defers to the calculator; see its comment.
    actionOnFill = AmberPrimitive.onFillLight, // 6.65:1 on actionFill
    actionText = AmberPrimitive.fillLight, // 6.4:1
    stateLive = AmberPrimitive.fillLight,
    stateCaution = AmberPrimitive.cautionLight, // 6.4:1
)
