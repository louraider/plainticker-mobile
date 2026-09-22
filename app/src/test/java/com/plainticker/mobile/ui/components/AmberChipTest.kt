package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amber's chip, morphed (a later pass than the one that left the seam: the founder decided the
 * calendar had room and asked for [shapeFor] built rather than left un-morphed). [shapeFor] is now
 * `@Composable` (it calls `animateDpAsState`), so a plain JVM test can no longer call it directly the
 * way the un-morphed pure function let it; what is pinned instead is the same thing every other
 * Amber component test in this package pins what it cannot render: the source itself, so a change
 * to the animation choices below has to change this test on purpose.
 */
class AmberChipTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberChip.kt").readText()).code
    }

    private fun body(function: String, until: String): String {
        val start = source.indexOf(function)
        assertTrue("AmberChip.kt has no $function", start >= 0)
        val end = source.indexOf(until, start)
        assertTrue("AmberChip.kt has no $until after $function", end > start)
        return source.substring(start, end)
    }

    // ---- The seam, filled in: one Dp interpolated with a spring, not two shapes switched -------

    @Test
    fun `shapeFor is Composable and animates a corner radius with animateDpAsState, not a plain if between two shapes`() {
        assertTrue(
            "shapeFor must be @Composable directly above its declaration to call animateDpAsState",
            Regex("@Composable\\s+internal fun shapeFor\\(").containsMatchIn(source),
        )
        val fn = body("internal fun shapeFor(", "private val ChipHeight")
        assertTrue("animateDpAsState(" in fn)
        assertTrue("RoundedCornerShape(radius)" in fn)
    }

    @Test
    fun `the two ends are 8dp unselected and half the chip height selected, the same radius CircleShape drew before`() {
        assertTrue("private val AmberChipCornerRadius = 8.dp" in source)
        assertTrue("private val AmberChipFullRadius = ChipHeight / 2" in source)
    }

    @Test
    fun `the morph is a spring, gated to snap at animator scale 0 so it is never mid-morph when motion is off`() {
        val fn = body("internal fun shapeFor(", "private val ChipHeight")
        assertTrue("rememberMotionEnabled" in fn)
        assertTrue("AmberChipMorphSpring" in fn)
        assertTrue("snap()" in fn)
        assertTrue("private val AmberChipMorphSpring = spring<Dp>(" in source)
    }

    @Test
    fun `no material3 1_5_0-alpha shape-morphing API is reached for, per DESIGN-md section 10's refusal`() {
        // The real class names live only in an artifact this build does not depend on, so this
        // guards the intent (no alpha reach) rather than a class this module cannot even resolve.
        listOf("Morph(", "RoundedPolygon", "MaterialShapes", "ExperimentalMaterial3ExpressiveApi").forEach {
            assertFalse("$it would be the alpha dependency section 10 and 5.3 both say to refuse here", it in source)
        }
    }

    // ---- The unselected chip matches the mockup's own un-morphed set --------------------------

    @Test
    fun `unselected reads surfaceRaised and textPrimary, exactly the mockup's un-morphed chip`() {
        assertTrue("colors.surfaceRaised" in source)
        assertTrue("color = colors.textPrimary" in source)
    }

    @Test
    fun `selected takes a border, unselected does not, per DESIGN-md section 2's a selected chip carries a border`() {
        assertTrue("border(1.dp, colors.border, shape)" in source)
    }

    // ---- The light-only edge on an unselected chip (DESIGN.md section 8's added exception) -----

    /**
     * Light's `surfaceRaised` (`#FFFFFF`) sits about 1.03:1 over `surfaceGround` (`#FFFBF2`,
     * `AmberContrastTest`'s own pinned number), so an unselected chip drawn on the ground it sits
     * on was reading as nearly invisible in light while dark's 1.12:1 step already worked. The
     * same ring [selected] already carries now also draws unselected, gated on the light palette
     * only: dark must keep drawing the plain, unringed chip it always has.
     */
    @Test
    fun `an unselected chip also takes the border in light, gated on the light palette rather than a repaint`() {
        assertTrue(
            "the unselected ring must be gated on colors === AmberLightColors, not drawn unconditionally",
            "selected || colors === AmberLightColors) Modifier.border(1.dp, colors.border, shape) else Modifier" in source,
        )
    }

    // ---- The 48dp touch target on a 32dp visual chip -------------------------------------------

    @Test
    fun `the 32dp visual height keeps a 48dp touch target via minimumInteractiveComponentSize, ordered before the size modifiers`() {
        val order = listOf("minimumInteractiveComponentSize()", "height(ChipHeight)").map { source.indexOf(it) }
        assertTrue("all of the ordering markers were found", order.all { it >= 0 })
        assertTrue(
            "minimumInteractiveComponentSize must come before any size modifier that could limit its constraints, per its own doc",
            order[0] < order[1],
        )
    }

    @Test
    fun `the chip labels itself selected for a screen reader, and its click carries a role`() {
        assertTrue("this.selected = isSelected" in source)
        assertTrue("role = Role.Button" in source)
    }
}
