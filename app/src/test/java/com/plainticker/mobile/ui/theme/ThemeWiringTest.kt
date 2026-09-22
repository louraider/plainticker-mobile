package com.plainticker.mobile.ui.theme

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proof, read from source the way every other component test in this repo is (nothing here can
 * render Compose on a plain JVM): that Amber's light theme is actually wired to the system
 * setting, live, rather than being a palette that exists in [Tokens.kt] and [AmberContrastTest]
 * but that no screen ever reaches for.
 *
 * The fault this pins against, by name: a composable whose `colors: AmberColors` parameter
 * defaults to [AmberDarkColors] and is reached without the argument. [AmberChip.kt] is excluded
 * on purpose (another agent's lane this task was told to stay out of); [Support.kt]'s
 * `AmberPreviewCanvas` is excluded on purpose too, since a design-QA preview helper that shows the
 * dark set unless a caller asks for the light one is the documented, deliberate behaviour of a
 * preview tool, not a screen a reader opens.
 */
class ThemeWiringTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val uiRoot: File = File(module, "src/main/java/com/plainticker/mobile/ui")

    private fun read(relativePath: String): String =
        KotlinScan(File(uiRoot, relativePath).readText()).code

    private fun mainActivitySource(): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/MainActivity.kt").readText()).code

    // ---- The shared component library: no default left dark-only ------------------------------

    @Test
    fun `no component under ui-components still defaults its AmberColors parameter to the fixed-dark set`() {
        val outOfScope = setOf("AmberChip.kt", "Support.kt")
        val componentsDir = File(uiRoot, "components")
        val offenders = componentsDir.listFiles { f -> f.extension == "kt" }
            .orEmpty()
            .filter { it.name !in outOfScope }
            .filter { "AmberColors = AmberDarkColors" in KotlinScan(it.readText()).code }
            .map { it.name }
        assertTrue("still defaults to dark regardless of the system setting: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the bar, the header and the skeleton all default to the system-following palette`() {
        listOf("AmberBottomNav.kt", "TopBar.kt", "Skeleton.kt").forEach { file ->
            val source = read("components/$file")
            assertTrue("$file has no defaultAmberColors() default", "defaultAmberColors()" in source)
        }
    }

    @Test
    fun `the status-clock scrim takes an overridable ground colour, and both live call sites pass the screen's own`() {
        // TopScrim keeps Canvas as its own default for source compatibility with any caller that
        // has not moved to Amber, rather than resolving isSystemInDarkTheme() a second time inside
        // a component that has no text of its own to promote against the tertiary rule; every real
        // screen (HomeScreen, DetailScreen) now passes its own resolved surfaceGround explicitly.
        val insets = read("components/Insets.kt")
        assertTrue("TopScrim(modifier: Modifier = Modifier, groundColor: Color = Canvas)" in insets)
        listOf("home/HomeScreen.kt", "detail/DetailScreen.kt").forEach { file ->
            val source = read(file)
            assertTrue("$file never overrides TopScrim's ground colour", "groundColor = colors.surfaceGround" in source)
        }
    }

    // ---- Money and entitlement: the swap sheet and the pass sheet -----------------------------

    @Test
    fun `the swap sheet reads a theme-following palette, not Instrument's fixed-dark tokens`() {
        val source = read("swap/SwapSheet.kt")
        assertTrue("SwapSheetBody never resolves a theme-following palette", "val colors = defaultAmberColors()" in source)
        listOf("color = Ink", "color = Ink2", "color = Muted", "background(Canvas)", "background(Elevated)").forEach {
            assertFalse("$it survives in SwapSheet.kt, so the sheet is still Instrument-dark there", it in source)
        }
    }

    @Test
    fun `the pass sheet reads a theme-following palette, not Instrument's fixed-dark tokens`() {
        val source = read("pass/PassSheet.kt")
        assertTrue("PassSheetBody never resolves a theme-following palette", "val colors = defaultAmberColors()" in source)
        listOf("color = Ink", "color = Ink2", "surface = Elevated").forEach {
            assertFalse("$it survives in PassSheet.kt, so the sheet is still Instrument-dark there", it in source)
        }
    }

    // ---- The gated classification: Detail --------------------------------------------------------

    @Test
    fun `Detail no longer hardcodes AmberDarkColors anywhere, including the gated verdict block`() {
        val source = read("detail/DetailScreen.kt")
        assertFalse("AmberDarkColors." in source)
        // Every block that draws text needs its own resolved palette; a handful is not proof by
        // itself, but a screen this size drawing every colour from one dark constant and zero
        // defaultAmberColors() calls would be exactly the regression this test exists to catch.
        val occurrences = Regex("defaultAmberColors\\(\\)").findAll(source).count()
        assertTrue("only $occurrences defaultAmberColors() calls in DetailScreen.kt", occurrences >= 10)
    }

    // ---- Vote: the round header, the leaders, the sheet ----------------------------------------

    @Test
    fun `Vote's screen and sheet no longer hardcode AmberDarkColors`() {
        listOf("vote/VoteScreen.kt", "vote/VoteSheet.kt").forEach { file ->
            val source = read(file)
            assertFalse("$file still reads AmberDarkColors directly", "AmberDarkColors." in source)
            assertTrue("$file never resolves a theme-following palette", "defaultAmberColors()" in source)
        }
    }

    // ---- The root: MainActivity wires Amber to the system setting, live -----------------------

    @Test
    fun `MainActivity's content root is Amber, following isSystemInDarkTheme, not the fixed-dark Instrument theme`() {
        val source = mainActivitySource()
        assertTrue("isSystemInDarkTheme()" in source)
        assertTrue("AmberTheme(useDarkTheme = darkTheme)" in source)
        assertFalse("MainActivity still wraps its content in the fixed-dark PlainTickerTheme", "PlainTickerTheme {" in source)
    }

    @Test
    fun `MainActivity restyles the system bars on every recomposition where the theme changes, not only at launch`() {
        val source = mainActivitySource()
        assertTrue("no SideEffect keeps the system bar style in sync with a live theme switch", "SideEffect {" in source)
        assertTrue("SystemBarStyle.dark(Color.TRANSPARENT)" in source)
        assertTrue("no light branch: the bar would stay light-icons-on-dark forever", "SystemBarStyle.light(" in source)
    }

    @Test
    fun `the root surface follows the resolved palette's own ground, not Instrument's fixed Canvas`() {
        val source = mainActivitySource()
        assertTrue("color = colors.surfaceGround" in source)
    }
}
