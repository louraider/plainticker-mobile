package com.plainticker.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every contrast ratio docs/design-research-2026-09-21.md section 5.3 states for Amber, and
 * the rule section 4 states across every direction: text.tertiary never sits on surface.high. The
 * WCAG relative-luminance formula below is the one implemented independently in
 * scratchpad/design/contrast.py (the research's own calculator); this test reads Tokens.kt
 * directly, so an edit to a primitive that breaks a stated ratio fails the build instead of
 * waiting for someone to look.
 */
class AmberContrastTest {

    // ---- WCAG 2.x contrast, mirroring scratchpad/design/contrast.py's lum()/cr() -------------

    private fun channel(component: Float): Double {
        val s = component.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** The research states ratios to one decimal; a rounding tolerance, not a slack allowance. */
    private val tolerance = 0.05

    // ---- Section 5.3, "Dark" -------------------------------------------------------------------

    @Test
    fun `amber dark matches every ratio section 5-3 states`() {
        val c = AmberDarkColors
        assertEquals(16.0, contrast(c.textPrimary, c.surfaceGround), tolerance)
        assertEquals(9.8, contrast(c.textSecondary, c.surfaceGround), tolerance)
        assertEquals(5.5, contrast(c.textTertiary(AmberSurface.GROUND), c.surfaceGround), tolerance)
        assertEquals(8.8, contrast(c.actionOnFill, c.actionFill), tolerance)
        assertEquals(11.5, contrast(c.actionText, c.surfaceGround), tolerance)
        assertEquals(6.6, contrast(c.stateCaution, c.surfaceGround), tolerance)
        // action.text and state.live share one value in every set the research drew.
        assertEquals(c.actionText, c.stateLive)
    }

    // ---- Section 5.3, "Light" -------------------------------------------------------------------

    @Test
    fun `amber light matches every ratio section 5-3 states`() {
        val c = AmberLightColors
        assertEquals(16.8, contrast(c.textPrimary, c.surfaceGround), tolerance)
        assertEquals(7.5, contrast(c.textSecondary, c.surfaceGround), tolerance)
        assertEquals(4.7, contrast(c.textTertiary(AmberSurface.GROUND), c.surfaceGround), tolerance)
        // Section 5.3's prose table states 6.7 here; its own calculator (contrast.py) computes
        // 6.65 for this exact pair (#FFFFFF on #7A5600) and is the one this test defers to, per
        // instruction: the calculator wins on a disagreement. Reported in the commit message.
        assertEquals(6.65, contrast(c.actionOnFill, c.actionFill), tolerance)
        assertEquals(6.4, contrast(c.actionText, c.surfaceGround), tolerance)
        assertEquals(6.4, contrast(c.stateCaution, c.surfaceGround), tolerance)
        assertEquals(c.actionText, c.stateLive)
    }

    // ---- Section 4: text.tertiary never sits on surface.high -----------------------------------

    @Test
    fun `tertiary on the highest surface fails AA, in both sets, which is why it must promote`() {
        listOf(AmberDarkColors to "dark", AmberLightColors to "light").forEach { (c, label) ->
            val raw = contrast(c.textTertiary(AmberSurface.GROUND), c.surfaceHigh)
            assertTrue(
                "$label tertiary-on-high measured $raw, expected inside section 4's stated 4.1 to 4.4:1 band",
                raw in 4.05..4.45,
            )
            assertTrue("$label tertiary-on-high ($raw) should fail the 4.5:1 normal-text AA floor", raw < 4.5)
        }
    }

    @Test
    fun `AmberColors refuses tertiary on the highest surface`() {
        listOf(AmberDarkColors to "dark", AmberLightColors to "light").forEach { (c, label) ->
            // The only way to ask this class for a text colour on surface.high is textTertiary(HIGH),
            // and it must hand back textSecondary, not the failing raw tertiary value.
            assertEquals("$label", c.textSecondary, c.textTertiary(AmberSurface.HIGH))
            // The promotion is real, not a coincidence of the two values being equal already.
            assertNotEquals("$label", c.textTertiary(AmberSurface.GROUND), c.textTertiary(AmberSurface.HIGH))
            // Off the highest surface, the rule does not fire.
            assertEquals("$label", c.textTertiary(AmberSurface.GROUND), c.textTertiary(AmberSurface.RAISED))
            // And the promoted value comfortably passes on surface.high (it is >= its own ground ratio
            // for text.secondary, which the two tests above already pin at 9.8:1 dark, 7.5:1 light).
            assertTrue("$label", contrast(c.textTertiary(AmberSurface.HIGH), c.surfaceHigh) >= 4.5)
        }
    }
}
