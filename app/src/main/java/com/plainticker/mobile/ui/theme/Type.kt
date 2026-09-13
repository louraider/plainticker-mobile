package com.plainticker.mobile.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.R

/** Words, labels, buttons, headings: Outfit 400, 500, 600 (DESIGN.md section 3). Bundled, see docs/fonts.md. */
val Outfit: FontFamily = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

/**
 * Every number, ticker symbol, wallet or signature fragment and timestamp: JetBrains Mono 400, 500
 * with tabular numerals. Numbers never appear in Outfit.
 */
val JetBrainsMono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/** OpenType feature tag for tabular numerals; set on every mono style. */
const val TABULAR_NUMERALS: String = "tnum"

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)
private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun ui(
    size: TextUnit,
    weight: FontWeight,
    lineHeight: TextUnit,
    tracking: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = Outfit,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    platformStyle = NoFontPadding,
    lineHeightStyle = CenteredLines,
)

private fun mono(
    size: TextUnit,
    weight: FontWeight,
    lineHeight: TextUnit,
    tracking: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    fontFeatureSettings = TABULAR_NUMERALS,
    platformStyle = NoFontPadding,
    lineHeightStyle = CenteredLines,
)

/**
 * The type scale from DESIGN.md section 3 (sp), plus the supporting styles the canvas measures.
 * Colors are applied at the call site; a style never carries one.
 */
object PlainTickerType {
    /** 64 mono 500, tracking -0.035em. Autosizes down on the hero row only. */
    val heroTicker: TextStyle = mono(64.sp, FontWeight.Medium, 64.sp, (-0.035).em)
    val heroPrice: TextStyle = mono(40.sp, FontWeight.Medium, 44.sp, (-0.03).em)
    /** Total, received. */
    val bigValue: TextStyle = mono(40.sp, FontWeight.Medium, 44.sp, (-0.03).em)
    val fScoreNumeral: TextStyle = mono(56.sp, FontWeight.Medium, 60.sp, (-0.03).em)
    /** Section heading 20 Outfit 600, tracking -0.01em. */
    val heading: TextStyle = ui(20.sp, FontWeight.SemiBold, 26.sp, (-0.01).em)
    /** Fact value default 24 mono 500; cells choose 22 to 32 via [factValueAt]. */
    val factValue: TextStyle = mono(24.sp, FontWeight.Medium, 26.sp, (-0.01).em)
    val trackValue: TextStyle = mono(20.sp, FontWeight.Medium, 24.sp)
    val listTicker: TextStyle = mono(18.sp, FontWeight.Medium, 22.sp)
    /** Body 15 Outfit 400, line height 23. */
    val body: TextStyle = ui(15.sp, FontWeight.Normal, 23.sp)
    /** Label 13 Outfit 500, Muted at the call site. */
    val label: TextStyle = ui(13.sp, FontWeight.Medium, 18.sp)
    /** Meta 12 mono 400, Muted at the call site. */
    val meta: TextStyle = mono(12.sp, FontWeight.Normal, 18.sp)
    val button: TextStyle = ui(16.sp, FontWeight.SemiBold, 20.sp)
    /** Text action 14 Outfit 600, Accent at the call site. */
    val textAction: TextStyle = ui(14.sp, FontWeight.SemiBold, 20.sp)

    // Supporting styles, one per canvas measurement.
    val wordmark: TextStyle = ui(15.sp, FontWeight.SemiBold, 20.sp, (-0.01).em)
    val tab: TextStyle = ui(14.sp, FontWeight.Medium, 20.sp)
    val tabSelected: TextStyle = ui(14.sp, FontWeight.SemiBold, 20.sp)
    /** Sub lines, state words, captions, banners: 13 Outfit 400. */
    val small: TextStyle = ui(13.sp, FontWeight.Normal, 18.sp)
    /** Signal names, row copy: 15 Outfit 400, line height 20. */
    val rowText: TextStyle = ui(15.sp, FontWeight.Normal, 20.sp)
    /** Track label: 15 Outfit 500. */
    val rowLabel: TextStyle = ui(15.sp, FontWeight.Medium, 20.sp)
    /** Company under the hero ticker, the "of 9 signals" counter. */
    val company: TextStyle = ui(16.sp, FontWeight.Normal, 22.sp)
    val panelBody: TextStyle = ui(15.sp, FontWeight.Normal, 22.sp)
    /** Field value when it is words (search). */
    val fieldText: TextStyle = ui(16.sp, FontWeight.Normal, 24.sp)
    val onboardingTitle: TextStyle = ui(26.sp, FontWeight.SemiBold, 32.sp, (-0.02).em)
    /** The onboarding consent sentence beside the checkbox: 14 Outfit 400, line height 20. */
    val consent: TextStyle = ui(14.sp, FontWeight.Normal, 20.sp)
    /** Gauge value, heading meta: 13 mono 500. */
    val monoSmall: TextStyle = mono(13.sp, FontWeight.Medium, 18.sp)
    /** "yes" or "no" beside a signal: 14 mono 500. */
    val monoRow: TextStyle = mono(14.sp, FontWeight.Medium, 20.sp)
    /** Field unit: 14 mono 400. */
    val monoUnit: TextStyle = mono(14.sp, FontWeight.Normal, 20.sp)
    /** NYSE close beside the token price: 20 mono 400. */
    val referencePrice: TextStyle = mono(20.sp, FontWeight.Normal, 24.sp)
    /** Right value on a muted (price-only) list row: 15 mono 400. */
    val listValueMuted: TextStyle = mono(15.sp, FontWeight.Normal, 22.sp)
    /** Field value when it is a number: 36 mono 500, tracking -0.02em. */
    val fieldValue: TextStyle = mono(36.sp, FontWeight.Medium, 40.sp, (-0.02).em)
    /** Swap sheet title "USDC to TSLAx": 22 mono 500. */
    val sheetTitle: TextStyle = mono(22.sp, FontWeight.Medium, 28.sp)

    /** Fact values run 22 to 32 sp depending on the cell; line height is 1.1 times the size. */
    fun factValueAt(size: TextUnit): TextStyle = mono(size, FontWeight.Medium, size * 1.1f, (-0.01).em)
}
