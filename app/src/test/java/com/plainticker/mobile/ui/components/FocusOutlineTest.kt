package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `Modifier.focusOutline` drew a hard-coded Instrument `Accent` (polish batch, 2026-09-25): a blue
 * with no relationship to Amber's palette and no contrast ratio anyone had measured for it,
 * regardless of which theme was drawing the rest of the screen it sat on. The fix reads
 * `AmberColors.actionText` instead, which `AmberContrastTest` already pins at 11.5:1 dark and
 * 6.4:1 light against `surfaceGround`, comfortably above the 3:1 WCAG floor for a non-text focus
 * indicator, in both themes.
 *
 * This scans the source rather than rendering it, the same reason every other Compose-behavior
 * test in this module does (`TopTabsTest`'s own doc comment): there is no Robolectric here and no
 * instrumentation runs from this gate.
 */
class FocusOutlineTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun sourceOf(relativePath: String): String {
        val file = File(module, "src/main/java/com/plainticker/mobile/$relativePath")
        assertTrue("$relativePath is missing", file.isFile)
        return KotlinScan(file.readText()).code
    }

    private val supportSource by lazy { sourceOf("ui/components/Support.kt") }

    private val focusOutlineFn: String by lazy {
        val start = supportSource.indexOf("fun Modifier.focusOutline(")
        assertTrue("Support.kt has no Modifier.focusOutline", start >= 0)
        val end = supportSource.indexOf("fun rememberMotionEnabled(", start)
        assertTrue("Support.kt has no rememberMotionEnabled after focusOutline", end > start)
        supportSource.substring(start, end)
    }

    @Test
    fun `focusOutline draws the theme's own action colour, never a hard-coded Accent`() {
        assertTrue(
            "takes AmberColors, defaulted the way every other shared component in this package is",
            "colors: AmberColors = defaultAmberColors()" in focusOutlineFn,
        )
        assertTrue("the border reads the theme's action colour", "this.border(2.dp, colors.actionText)" in focusOutlineFn)
        assertFalse("no hard-coded Instrument Accent left in the outline itself", "border(2.dp, Accent)" in focusOutlineFn)
    }

    /**
     * Every call site the task's own grep turned up, named here so a future rename, or a ninth
     * call site added elsewhere, is not silently missed by this test: each must still resolve to
     * the shared, theme-following modifier above rather than growing its own copy.
     */
    @Test
    fun `every known focusOutline call site still calls the shared modifier`() {
        val callSites = listOf(
            "ui/components/AmberChip.kt",
            "ui/components/AmberFactRow.kt",
            "ui/components/AmberTickerRow.kt",
            "ui/components/ListRow.kt",
            "ui/components/TextAction.kt",
            "ui/components/TopTabs.kt",
            "ui/onboarding/OnboardingScreen.kt",
            "ui/you/YouScreen.kt",
        )
        callSites.forEach { relativePath ->
            assertTrue("$relativePath no longer calls focusOutline", "focusOutline(" in sourceOf(relativePath))
        }
    }

    /**
     * The six call sites that already compute their own [com.plainticker.mobile.ui.theme.AmberColors]
     * thread it straight through rather than letting `focusOutline` recompute
     * `defaultAmberColors()` a second time; `ListRow` and `TextAction` are still Instrument's own
     * anatomy (DESIGN.md section 4) with no local `AmberColors` to pass, so they fall back to the
     * default, which is still theme-correct on its own.
     */
    @Test
    fun `call sites that already have AmberColors pass it through instead of recomputing it`() {
        val threaded = listOf(
            "ui/components/AmberChip.kt",
            "ui/components/AmberFactRow.kt",
            "ui/components/AmberTickerRow.kt",
            "ui/components/TopTabs.kt",
            "ui/onboarding/OnboardingScreen.kt",
            "ui/you/YouScreen.kt",
        )
        threaded.forEach { relativePath ->
            assertTrue("$relativePath should pass colors to focusOutline", "focusOutline(interactionSource, colors)" in sourceOf(relativePath))
        }
    }
}
