package com.plainticker.mobile.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan section 13 Pass 2: Material defaults are overridden on purpose. Every slot is read back by
 * reflection so a new slot in a future Material release fails here instead of leaking purple.
 */
class PlainTickerThemeTest {

    private val materialBaseline = Color(0xFF6750A4)

    /** Every zero-argument getter returning a packed Color (a `long` on the JVM). */
    private fun colorSlots(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
            .associate { method ->
                val name = method.name.removePrefix("get").substringBefore('-')
                name to Color((method.invoke(scheme) as Long).toULong())
            }

    /** The public shape slots; the `$material3` internal accessors (expressive slots) are skipped. */
    private fun shapeSlots(shapes: Shapes): Map<String, CornerBasedShape> =
        Shapes::class.java.methods
            .filter {
                it.parameterCount == 0 &&
                    !it.name.contains('$') &&
                    CornerBasedShape::class.java.isAssignableFrom(it.returnType)
            }
            .associate { it.name.removePrefix("get") to it.invoke(shapes) as CornerBasedShape }

    private fun typeSlots(): Map<String, TextStyle> =
        PlainTickerType::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java }
            .associate { it.name.removePrefix("get") to it.invoke(PlainTickerType) as TextStyle }

    @Test
    fun `color scheme exposes every material slot`() {
        // 48 in Material3 1.4.0 (incl. the fixed roles). A larger number means a new slot to map.
        assertEquals(48, colorSlots(PlainTickerColorScheme).size)
    }

    @Test
    fun `no color scheme slot is the material baseline purple`() {
        colorSlots(PlainTickerColorScheme).forEach { (name, color) ->
            assertNotEquals("slot $name is the M3 baseline", materialBaseline, color)
        }
    }

    @Test
    fun `every color scheme slot is one of the nine tokens`() {
        colorSlots(PlainTickerColorScheme).forEach { (name, color) ->
            assertTrue("slot $name = $color is not a token", color in AllTokens)
        }
    }

    @Test
    fun `named slots follow the design system`() {
        val s = PlainTickerColorScheme
        assertEquals(Accent, s.primary)
        assertEquals(Canvas, s.onPrimary)
        assertEquals(Canvas, s.background)
        assertEquals(Canvas, s.surface)
        assertEquals(Elevated, s.surfaceVariant)
        assertEquals(Elevated, s.surfaceContainer)
        assertEquals(Elevated, s.surfaceContainerLow)
        assertEquals(Elevated, s.surfaceContainerHigh)
        assertEquals(Elevated, s.surfaceContainerHighest)
        assertEquals(Ink, s.onBackground)
        assertEquals(Ink, s.onSurface)
        assertEquals(Ink2, s.onSurfaceVariant)
        assertEquals(LineStrong, s.outline)
        assertEquals(Line, s.outlineVariant)
        assertEquals(Caution, s.error)
    }

    @Test
    fun `tokens are the DESIGN md values`() {
        assertEquals(Color(0xFF0B0F14), Canvas)
        assertEquals(Color(0xFF121820), Elevated)
        assertEquals(Color(0xFFE8ECF1), Ink)
        assertEquals(Color(0xFFB4BCC8), Ink2)
        assertEquals(Color(0xFF7F8A99), Muted)
        assertEquals(Color(0xFF5AA9E6), Accent)
        assertEquals(Color(0xFFD9A441), Caution)
        // Alpha is stored in 8 bits, so 0.10 comes back as 26/255.
        assertEquals(0.10f, Line.alpha, 1f / 255f)
        assertEquals(0.22f, LineStrong.alpha, 1f / 255f)
        assertEquals(9, AllTokens.size)
    }

    @Test
    fun `every shape slot is square`() {
        val density = Density(2f)
        val size = Size(200f, 56f)
        val slots = shapeSlots(PlainTickerShapes)
        assertEquals(setOf("ExtraSmall", "Small", "Medium", "Large", "ExtraLarge"), slots.keys)
        slots.forEach { (name, shape) ->
            listOf(shape.topStart, shape.topEnd, shape.bottomStart, shape.bottomEnd).forEach { corner ->
                assertEquals("shape $name has a rounded corner", 0f, corner.toPx(size, density), 0f)
            }
        }
    }

    @Test
    fun `type scale matches DESIGN md section 3`() {
        val t = PlainTickerType
        assertEquals(64.sp, t.heroTicker.fontSize)
        assertEquals(FontWeight.Medium, t.heroTicker.fontWeight)
        assertEquals((-0.035).em, t.heroTicker.letterSpacing)
        assertEquals(40.sp, t.heroPrice.fontSize)
        assertEquals((-0.03).em, t.heroPrice.letterSpacing)
        assertEquals(40.sp, t.bigValue.fontSize)
        assertEquals(20.sp, t.heading.fontSize)
        assertEquals(FontWeight.SemiBold, t.heading.fontWeight)
        assertEquals((-0.01).em, t.heading.letterSpacing)
        assertEquals(22.sp, t.factValueAt(22.sp).fontSize)
        assertEquals(32.sp, t.factValueAt(32.sp).fontSize)
        assertEquals(20.sp, t.trackValue.fontSize)
        assertEquals(18.sp, t.listTicker.fontSize)
        assertEquals(15.sp, t.body.fontSize)
        assertEquals(23.sp, t.body.lineHeight)
        assertEquals(FontWeight.Normal, t.body.fontWeight)
        assertEquals(13.sp, t.label.fontSize)
        assertEquals(FontWeight.Medium, t.label.fontWeight)
        assertEquals(12.sp, t.meta.fontSize)
        assertEquals(16.sp, t.button.fontSize)
        assertEquals(FontWeight.SemiBold, t.button.fontWeight)
        assertEquals(14.sp, t.textAction.fontSize)
        assertEquals(FontWeight.SemiBold, t.textAction.fontWeight)
    }

    /**
     * `Wordmark` is the one deliberate exception, since 2026-09-24 (DESIGN.md section 9, "Two
     * corners, refit"): [PlainTickerType.wordmark] itself moved off Outfit onto the same
     * Bricolage the web's own TopNav wordmark and both OG images already set "PlainTicker" in, so
     * [TopBar] finally draws the same word in the same face the web does. Every other
     * `PlainTickerType` style is untouched; the third-font-family guard below still fires for
     * anything else.
     */
    @Test
    fun `every style is Outfit or tabular JetBrains Mono, except the wordmark's own Bricolage`() {
        val styles = typeSlots()
        assertTrue(styles.size >= 13)
        styles.forEach { (name, style) ->
            when {
                name == "Wordmark" -> {
                    assertTrue("Wordmark must not still be Outfit", style.fontFamily != Outfit)
                    assertTrue("Wordmark must not be mono", style.fontFamily != JetBrainsMono)
                    assertEquals("Wordmark must carry no font feature", null, style.fontFeatureSettings)
                    assertEquals("Wordmark must be Bricolage 700", FontWeight.Bold, style.fontWeight)
                }
                style.fontFamily == JetBrainsMono ->
                    assertEquals("$name lacks tabular numerals", TABULAR_NUMERALS, style.fontFeatureSettings)
                style.fontFamily == Outfit ->
                    assertTrue("$name is a word style with a font feature", style.fontFeatureSettings == null)
                else -> throw AssertionError("$name uses a third font family: ${style.fontFamily}")
            }
        }
        // The numeral styles are mono; the word styles are Outfit, except Wordmark (Bricolage).
        listOf("HeroTicker", "HeroPrice", "BigValue", "FactValue", "TrackValue", "ListTicker", "Meta").forEach {
            assertEquals("$it must be mono", JetBrainsMono, styles.getValue(it).fontFamily)
        }
        listOf("Heading", "Body", "Label", "Button", "TextAction").forEach {
            assertEquals("$it must be Outfit", Outfit, styles.getValue(it).fontFamily)
        }
    }

    @Test
    fun `material typography slots stay inside the two faces, except titleSmall's Bricolage wordmark`() {
        // Touch the scale first: the typography must not depend on initialization order.
        assertEquals(15.sp, PlainTickerType.body.fontSize)
        val typography = PlainTickerTypography
        listOf(
            typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        ).forEach { style ->
            assertTrue(style.fontFamily == Outfit || style.fontFamily == JetBrainsMono)
        }
        // titleSmall carries TopBar's own wordmark (Typography.kt), Bricolage 700 since
        // 2026-09-24 (DESIGN.md section 9, "Two corners, refit"): the one deliberate exception
        // this file's own "every style is Outfit or tabular JetBrains Mono" test documents.
        assertEquals(PlainTickerType.wordmark, typography.titleSmall)
        assertTrue(typography.titleSmall.fontFamily != Outfit && typography.titleSmall.fontFamily != JetBrainsMono)
        assertEquals(PlainTickerType.body, typography.bodyLarge)
        assertEquals(PlainTickerType.button, typography.labelLarge)
    }
}
