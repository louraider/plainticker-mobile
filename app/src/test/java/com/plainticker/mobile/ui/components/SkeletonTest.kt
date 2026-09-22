package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The skeleton fill, checked specifically (this task's own brief): it is the placeholder a reader
 * sees on a cold open before any row has loaded, and it is also how `DetailScreen.kt`'s
 * `VerdictBlock.Locked` draws a gated classification's locked state (`SkeletonBar` there, no text
 * behind it). Light's `surfaceRaised` sits about 1.03:1 over `surfaceGround`
 * (`AmberContrastTest`'s own pinned number), so before this fix the fill read as a blank gap on
 * white, and a locked classification read as an empty gap rather than a shape standing in for a
 * word. Pinned the way every other Amber component test in this package pins what a plain JVM
 * test cannot render: the source itself.
 */
class SkeletonTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/Skeleton.kt").readText()).code
    }

    private fun body(function: String, until: String): String {
        val start = source.indexOf(function)
        assertTrue("Skeleton.kt has no $function", start >= 0)
        val end = source.indexOf(until, start)
        assertTrue("Skeleton.kt has no $until after $function", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `SkeletonBar still fills with surfaceRaised, following defaultAmberColors like every other shared piece`() {
        val fn = body("fun SkeletonBar(", "fun SkeletonRows(")
        assertTrue(".background(colors.surfaceRaised)" in fn)
        assertTrue("colors: AmberColors = defaultAmberColors()" in fn)
    }

    /**
     * The edge itself: gated on the light palette rather than drawn in both themes, and drawn
     * with the plain-rectangle default shape (no `shape` argument), since a skeleton bar is never
     * clipped to a rounded corner. Dark must keep drawing the plain, unringed fill it always has.
     */
    @Test
    fun `SkeletonBar takes a light-only border, never in dark`() {
        val fn = body("fun SkeletonBar(", "fun SkeletonRows(")
        assertTrue(
            "the edge must be gated on colors === AmberLightColors",
            ".then(if (colors === AmberLightColors) Modifier.border(1.dp, colors.border) else Modifier)" in fn,
        )
    }

    @Test
    fun `SkeletonRows still announces itself once and separates placeholder rows with the border token`() {
        val fn = body("fun SkeletonRows(", "fun SkeletonSwitch(")
        // KotlinScan's own `code` blanks string-literal contents (it collects them separately in
        // `literals`, for tests that check copy), so the announcement is checked by its code shape
        // (one semantics block wrapping the whole column) rather than by the word inside the string.
        assertTrue("semantics { contentDescription = " in fn)
        assertTrue("HorizontalDivider(thickness = 1.dp, color = colors.border)" in fn)
    }

    @Test
    fun `SkeletonSwitch never draws a spinner, per DESIGN-md section 8's still-banned list`() {
        val fn = body("fun SkeletonSwitch(", "@InstrumentPreviews")
        assertTrue(!fn.contains("CircularProgressIndicator") && !fn.contains("Spinner"))
    }
}
