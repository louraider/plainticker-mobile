package com.plainticker.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amber's theme (docs/design-research-2026-09-21.md section 5.3), checked the same way
 * PlainTickerThemeTest checks Instrument's: every Material slot read back by reflection, so a new
 * slot in a future Material release fails here instead of leaking purple; every style in
 * [AmberType] read back the same way, so a future style that forgets tnum or picks a third font
 * family fails here instead of on a screen.
 */
class AmberThemeTest {

    private val materialBaseline = Color(0xFF6750A4)

    private fun colorSlots(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE }
            .associate { method ->
                val name = method.name.removePrefix("get").substringBefore('-')
                name to Color((method.invoke(scheme) as Long).toULong())
            }

    private fun typeSlots(): Map<String, TextStyle> =
        AmberType::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java }
            .associate { it.name.removePrefix("get") to it.invoke(AmberType) as TextStyle }

    /** Every colour a theme's own [AmberColors] instance can hand out, dark or light. */
    private fun ownColors(c: AmberColors): Set<Color> = setOf(
        c.surfaceGround, c.surfaceRaised, c.surfaceHigh,
        c.textPrimary, c.textSecondary,
        c.textTertiary(AmberSurface.GROUND), c.textTertiary(AmberSurface.HIGH),
        c.border, c.actionFill, c.actionOnFill, c.actionText, c.stateLive, c.stateCaution,
    )

    @Test
    fun `amber color schemes expose the same 48 slots Instrument's does`() {
        assertEquals(48, colorSlots(AmberDarkColorScheme).size)
        assertEquals(48, colorSlots(AmberLightColorScheme).size)
    }

    @Test
    fun `no amber color scheme slot is the material baseline purple, dark or light`() {
        (colorSlots(AmberDarkColorScheme) + colorSlots(AmberLightColorScheme)).forEach { (name, color) ->
            assertNotEquals("slot $name is the M3 baseline", materialBaseline, color)
        }
    }

    @Test
    fun `every amber dark scheme slot is one of AmberDarkColors' own colors`() {
        val allowed = ownColors(AmberDarkColors)
        colorSlots(AmberDarkColorScheme).forEach { (name, color) ->
            assertTrue("slot $name = $color is not one of AmberDarkColors' colors", color in allowed)
        }
    }

    @Test
    fun `every amber light scheme slot is one of AmberLightColors' own colors`() {
        val allowed = ownColors(AmberLightColors)
        colorSlots(AmberLightColorScheme).forEach { (name, color) ->
            assertTrue("slot $name = $color is not one of AmberLightColors' colors", color in allowed)
        }
    }

    @Test
    fun `named slots follow the semantic mapping, dark and light`() {
        listOf(AmberDarkColorScheme to AmberDarkColors, AmberLightColorScheme to AmberLightColors)
            .forEach { (s, c) ->
                assertEquals(c.actionFill, s.primary)
                assertEquals(c.actionOnFill, s.onPrimary)
                assertEquals(c.surfaceGround, s.background)
                assertEquals(c.surfaceGround, s.surface)
                assertEquals(c.textPrimary, s.onBackground)
                assertEquals(c.textPrimary, s.onSurface)
                assertEquals(c.surfaceRaised, s.surfaceVariant)
                assertEquals(c.textSecondary, s.onSurfaceVariant)
                assertEquals(c.surfaceHigh, s.surfaceContainerHighest)
                assertEquals(c.border, s.outline)
                assertEquals(c.stateCaution, s.error)
            }
    }

    @Test
    fun `amber type scale matches section 5-5`() {
        assertEquals(22.sp, AmberType.sectionHead.fontSize)
        assertEquals(FontWeight.Bold, AmberType.sectionHead.fontWeight)
        assertEquals(16.sp, AmberType.rowTicker.fontSize)
        assertEquals(FontWeight.SemiBold, AmberType.rowTicker.fontWeight)
        assertEquals(14.sp, AmberType.rowCompany.fontSize)
        assertEquals(34.sp, AmberType.figureLarge.fontSize)
        assertEquals(FontWeight.Bold, AmberType.figureLarge.fontWeight)
        assertEquals(18.sp, AmberType.figureRow.fontSize)
        assertEquals(FontWeight.SemiBold, AmberType.figureRow.fontWeight)
    }

    @Test
    fun `tnum sits on number styles only, never on a word style`() {
        val styles = typeSlots()
        assertTrue(styles.size >= 10)
        val numberStyles = setOf("FigureLarge", "FigureRow", "FigureInline")
        styles.forEach { (name, style) ->
            if (name in numberStyles) {
                assertEquals("$name must carry tnum", TABULAR_NUMERALS, style.fontFeatureSettings)
            } else {
                assertTrue("$name is a word style but carries a font feature", style.fontFeatureSettings == null)
            }
        }
    }

    @Test
    fun `every amber style is Bricolage Grotesque, not Instrument's faces`() {
        typeSlots().values.forEach { style ->
            assertNotEquals(Outfit, style.fontFamily)
            assertNotEquals(JetBrainsMono, style.fontFamily)
        }
    }
}
