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
