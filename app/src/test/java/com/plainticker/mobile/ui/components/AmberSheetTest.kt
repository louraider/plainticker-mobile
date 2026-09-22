package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet swap, vote and pass all use (docs/design-research-2026-09-21.md section 5.5). Read
 * from source, the same way every other Amber component test in this package is: nothing here can
 * render `ModalBottomSheet` on a plain JVM.
 */
class AmberSheetTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberSheet.kt").readText()).code
    }

    @Test
    fun `the sheet sits on surfaceHigh, not surfaceRaised, per the anatomy table`() {
        assertTrue("containerColor = colors.surfaceHigh" in source)
    }

    @Test
    fun `the handle is amber, not a neutral line`() {
        assertTrue(".background(colors.actionText)" in source)
    }

    @Test
    fun `the top radius is 28dp, matching AmberShapes-large in Theme-kt`() {
        assertTrue("topStart = 28.dp" in source)
        assertTrue("topEnd = 28.dp" in source)
    }

    @Test
    fun `the scrim is derived from surfaceGround by alpha, not a new literal colour`() {
        assertTrue("colors.surfaceGround.copy(alpha = 0.82f)" in source)
    }

    @Test
    fun `it is a real ModalBottomSheet, which is what exposes the dismiss, expand and collapse semantics`() {
        assertTrue("ModalBottomSheet(" in source)
    }

    /**
     * `ModalBottomSheet` wraps whatever `dragHandle` draws in a plain `clickable` `Box` with no
     * minimum size of its own (confirmed against the pinned `material3` 1.4.0 sources), so
     * [AmberSheetHandle]'s own measured height is the drag handle's whole touch target: 23dp
     * above and below the 2dp bar reaches the 48dp floor this task's brief calls a hard rule, the
     * same total `BottomSheetDefaults.DragHandle` reaches with 22dp around its own 4dp bar.
     */
    @Test
    fun `the handle's touch target reaches the 48dp floor, not just its 2dp visible bar`() {
        val fn = source.substring(source.indexOf("fun AmberSheetHandle("), source.indexOf("fun AmberSheetSurface("))
        assertTrue("the bar is still 2dp, research 5.5's own anatomy", "HandleBarHeight = 2.dp" in source)
        assertTrue("23 + 2 + 23 = 48", "HandleTouchPadding = 23.dp" in source)
        assertTrue("the padding actually wraps the bar", "padding(vertical = HandleTouchPadding)" in fn)
        assertTrue("the bar itself reads the height constant", "height = HandleBarHeight" in fn)
    }

    /**
     * The onboarding panel (`OnboardingScreen.kt`'s `ConsentPanel`, migrated off Instrument's
     * retired `SheetSurface`) sits over a 25 percent backdrop and draws no handle, so it needs the
     * same 1dp top edge the retired component fell back to instead of drawing nothing.
     */
    @Test
    fun `AmberSheetSurface without a handle still draws the 1dp top edge, never a bare gap`() {
        val fn = source.substring(source.indexOf("fun AmberSheetSurface("))
        assertTrue("the handle branch is still conditional", "if (handle) {" in fn)
        assertTrue(
            "no handle must still draw a border-token edge, not nothing",
            ".fillMaxWidth().height(1.dp).background(colors.border)" in fn,
        )
    }
}
