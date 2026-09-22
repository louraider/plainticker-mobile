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

    // ---- The light `surfaceRaised` tonal-lift defect, and the `border` edge that closes it ------

    /**
     * The defect a previous pass measured and refused to fix alone: light's `surfaceRaised` over
     * `surfaceGround` is nearly 1:1 (`#FFFFFF` on `#FFFBF2`), while dark's own step is a real,
     * legible lift. Every tonal container that depends on this step (a grouped row, an unselected
     * chip, a skeleton bar's fill) is close to invisible in light. Pinned here so a future edit to
     * either primitive cannot silently regress the number this task's fix (`border`, light only)
     * was measured against.
     */
    @Test
    fun `light surfaceRaised over surfaceGround is nearly 1 to 1, the defect the light border edge closes`() {
        val raisedOverGround = contrast(AmberLightColors.surfaceRaised, AmberLightColors.surfaceGround)
        assertTrue("measured $raisedOverGround, expected close to 1:1 per this task's brief", raisedOverGround in 1.0..1.08)
        // surfaceHigh's own step is healthy by comparison: the fault is specific to surfaceRaised.
        val highOverGround = contrast(AmberLightColors.surfaceHigh, AmberLightColors.surfaceGround)
        assertTrue("measured $highOverGround, expected the healthy ~1.15:1 the brief states", highOverGround in 1.1..1.2)
    }

    /**
     * Dark's own `surfaceRaised` step, pinned so a future change that "fixes" light by accident
     * changing a shared code path cannot quietly move dark's already-healthy 1.12:1 lift too.
     */
    @Test
    fun `dark surfaceRaised over surfaceGround is a real lift, unlike light's`() {
        val raisedOverGround = contrast(AmberDarkColors.surfaceRaised, AmberDarkColors.surfaceGround)
        assertTrue("measured $raisedOverGround, expected dark's healthy ~1.12:1", raisedOverGround in 1.08..1.16)
    }

    /**
     * The fix itself, measured rather than asserted: `border` against both the container it
     * outlines (`surfaceRaised`) and the ground behind it, in light. Both clear the ~1.03:1 defect
     * above by a wide margin and land close to dark's own `border`-against-`surfaceRaised` step
     * (1.33:1), so the edge reads as real structure, not another near-invisible tone.
     */
    @Test
    fun `light border reads clearly against both surfaceRaised and surfaceGround, unlike the tonal step alone`() {
        val borderOverRaised = contrast(AmberLightColors.border, AmberLightColors.surfaceRaised)
        val borderOverGround = contrast(AmberLightColors.border, AmberLightColors.surfaceGround)
        assertTrue("measured $borderOverRaised", borderOverRaised in 1.35..1.45)
        assertTrue("measured $borderOverGround", borderOverGround in 1.30..1.40)
        val raisedOverGround = contrast(AmberLightColors.surfaceRaised, AmberLightColors.surfaceGround)
        assertTrue("the edge must read more clearly than the tonal step it stands in for", borderOverRaised > raisedOverGround * 1.25)
        assertTrue("the edge must read more clearly than the tonal step it stands in for", borderOverGround > raisedOverGround * 1.25)
    }

    /**
     * Dark's own primitives, byte-identical to before this task's fix (Tokens.kt's `AmberPrimitive`
     * is untouched; only light-gated `border` usages were added in the components that draw on
     * `surfaceRaised`). A change to any of these values would mean the "leave dark exactly as the
     * founder approved it" constraint was broken.
     */
    @Test
    fun `dark primitives are untouched by the light-only border fix`() {
        assertEquals(Color(0xFF16130D), AmberDarkColors.surfaceGround)
        assertEquals(Color(0xFF221E15), AmberDarkColors.surfaceRaised)
        assertEquals(Color(0xFF2E281C), AmberDarkColors.surfaceHigh)
        assertEquals(Color(0xFF3A3324), AmberDarkColors.border)
    }
}
