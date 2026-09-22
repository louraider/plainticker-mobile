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
}
