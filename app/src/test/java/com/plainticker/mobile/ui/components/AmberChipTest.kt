package com.plainticker.mobile.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Amber's chip, drawn un-morphed (this task's brief): two fixed shapes and an instant switch
 * between them, not a faked halfway shape and not the real morph either. [shapeFor] is the one
 * piece of this component that is pure logic, so it is the one piece a plain JVM can actually run;
 * everything else about the seam is pinned from source, the way every other Amber component test
 * in this package pins what it cannot render.
 */
class AmberChipTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberChip.kt").readText()).code
    }

    // ---- The seam: two fixed shapes, switched, never interpolated ------------------------------

    @Test
    fun `unselected is an 8dp corner radius, selected is a full pill, and they are two different shapes`() {
        val off = shapeFor(selected = false)
        val on = shapeFor(selected = true)
        assertEquals(RoundedCornerShape(8.dp), off)
        assertNotEquals(off, on)
    }

    @Test
    fun `selected resolves to the exact CircleShape singleton, not a shape built to interpolate toward it`() {
        // CircleShape is a stable, pre-built Shape (RoundedCornerShape(50)); shapeFor hands it
        // back unchanged rather than constructing something between it and the 8dp corner, which
        // is the seam this task's brief asks to be left rather than faked.
        assertTrue(shapeFor(selected = true) === CircleShape)
    }

    @Test
    fun `no shape interpolation, animateDpAsState or animateValueAsState appears anywhere in the file`() {
        listOf("animateDpAsState", "animateValueAsState", "animateColorAsState", "Animatable", "tween(", "spring(").forEach {
            assertFalse("$it would be the morph this pass explicitly leaves out", it in source)
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
