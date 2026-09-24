package com.plainticker.mobile.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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
    /**
     * TopBar's own word style: Bricolage 700, opsz 15, replacing Outfit SemiBold 15sp on
     * 2026-09-24 (DESIGN.md section 9, "Two corners, refit"). The one deliberate exception
     * [PlainTickerThemeTest]'s own "every style is Outfit or tabular JetBrains Mono" test carries
     * by name: the web's own TopNav wordmark and both OG images already set "PlainTicker" in
     * Bricolage Bold, so this closes the one place the app and the web drew the same word in two
     * different faces. Same 15sp point size, no manual tracking (Bricolage's own spacing, the
     * same choice every [AmberType] style below makes; Outfit's negative-tracking-on-headings
     * habit never applied to Bricolage).
     * [com.plainticker.mobile.ui.components.TopBarTest] proves this still clears the one-line
     * clipping rule against the widest real action label beside it, at 1.0x and 1.3x.
     */
    val wordmark: TextStyle = bricolage(15.sp, FontWeight.Bold, 20.sp)
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

// =================================================================================================
// Amber (docs/design-research-2026-09-21.md section 5.3): Bricolage Grotesque, a real variable font
// (fvar axes opsz 12-96, wght 200-800, wdth 75-100; confirmed 2026-09-22 with fontTools against
// res/font/bricolage_grotesque.ttf, the actual variable instance from google/fonts, not the static
// Regular-weight file the mockup canvas used to draw the approved artboards). Its GSUB carries
// `tnum`, also confirmed with fontTools, so numbers stay in Bricolage; the research's Manrope
// fallback is not needed. JetBrains Mono above remains for on-chain identifiers only (section 4);
// numbers never fall back to it under Amber.
// =================================================================================================

/**
 * One physical font, referenced once per call with its own [FontVariation.Settings]: width held
 * at 100 (no research finding calls for a width change), weight at whatever [weight] is, and
 * optical size set to the style's own point size, which is the actual point of bundling a
 * variable font ("optical size doing the work between 34 and 14sp", section 5.3), rather than a
 * handful of fixed static weights.
 *
 * `tnum` is a font feature, not a variation axis, and this face widens the comma and the period
 * under it (section 5.3's type note, confirmed on the mockup canvas with fontTools), so
 * [tabularNumerals] defaults to false and only a number style below turns it on.
 */
private fun bricolage(
    size: TextUnit,
    weight: FontWeight,
    lineHeight: TextUnit,
    tracking: TextUnit = TextUnit.Unspecified,
    tabularNumerals: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = FontFamily(
        Font(
            R.font.bricolage_grotesque,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.width(100f),
                FontVariation.opticalSizing(size),
            ),
        ),
    ),
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    fontFeatureSettings = if (tabularNumerals) TABULAR_NUMERALS else null,
    platformStyle = NoFontPadding,
    lineHeightStyle = CenteredLines,
)

/**
 * Amber's type scale. Sizes and weights are the ones docs/design-research-2026-09-21.md section
 * 5.5 states for the anatomy it measured (the ticker row, a number with its context line, the
 * section head). [body], [button] and [meta] are not individually sized by the research; they
 * use the sizes every direction in section 5.5 converges on. Colour is applied at the call site,
 * same as [PlainTickerType]; no style here carries one.
 */
object AmberType {
    // Words: tnum stays off. Section 5.3's type note is explicit that the feature belongs on
    // number styles only, because it widens the comma and the period in this face.
    /** Section head: 22/700, opsz 22 (5.5, "Section head"). */
    val sectionHead: TextStyle = bricolage(22.sp, FontWeight.Bold, 27.sp)
    /** Ticker row's ticker: 16/600, opsz 16 (5.5, "Ticker row"). */
    val rowTicker: TextStyle = bricolage(16.sp, FontWeight.SemiBold, 20.sp)
    /** Ticker row's company name: 14/400 (5.5, "company 14"). */
    val rowCompany: TextStyle = bricolage(14.sp, FontWeight.Normal, 18.sp)
    /** A figure's supporting line ("vs NYSE close", "of 100, fair"): 14/400 secondary (5.5). */
    val context: TextStyle = bricolage(14.sp, FontWeight.Normal, 18.sp)
    /** Prose: Method, disclaimers. Held at the 15sp body every direction in 5.5 shares. */
    val body: TextStyle = bricolage(15.sp, FontWeight.Normal, 22.sp)
    /** Primary action label. */
    val button: TextStyle = bricolage(16.sp, FontWeight.SemiBold, 20.sp)
    /** Smallest supporting text: state words, timestamps written as words. */
    val meta: TextStyle = bricolage(12.sp, FontWeight.Normal, 16.sp)

    // Numbers: tnum on. Colour (amber action.text/state.live) is applied at the call site, per
    // the foundation rule in section 4: direction is never a second colour.
    /** A card's headline figure: 34/700 tnum, opsz 34 (5.5, "Number with context"). */
    val figureLarge: TextStyle = bricolage(34.sp, FontWeight.Bold, 38.sp, tabularNumerals = true)
    /** Ticker row's right-hand figure: 18/600 tnum, opsz 18 (5.5, "figure 18 amber tnum"). */
    val figureRow: TextStyle = bricolage(18.sp, FontWeight.SemiBold, 22.sp, tabularNumerals = true)
    /** A numeral set inside a sentence ("22 of 160", "+0.62%"): 14/400 tnum, opsz 14. */
    val figureInline: TextStyle = bricolage(14.sp, FontWeight.Normal, 18.sp, tabularNumerals = true)

    /**
     * A [com.plainticker.mobile.ui.components.FactGrid] cell's value, 22 to 32sp depending on the
     * cell's own prominence ([com.plainticker.mobile.ui.components.FactCell.valueSize]), tnum on
     * (every real value is a percent, a multiplier, a count or a short state word, never a
     * sentence). No single fixed size in this object covers that range the way [figureLarge] and
     * [figureRow] cover their own one anatomy each, so this is built the same way
     * [PlainTickerType.factValueAt] was: one function, optical size doing the work per call the
     * way every other Amber style already does. Bold throughout, matching [figureLarge] rather
     * than [figureRow]'s SemiBold: a fact grid's value is the one thing each cell states, the same
     * role a card's headline figure plays, and every call site's own size (22 to 32) sits closer
     * to figureLarge's 34 than to figureRow's 18.
     */
    fun factValueAt(size: TextUnit): TextStyle = bricolage(size, FontWeight.Bold, size * 1.1f, tabularNumerals = true)
}
