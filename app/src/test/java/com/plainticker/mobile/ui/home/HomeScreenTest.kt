package com.plainticker.mobile.ui.home

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U9 ("Wallet fragment as the TopBar action when a session is open, 'You' otherwise",
 * docs/plan-app-uiux-2026-09-21.md's after-the-hackathon table), judged against the shell as it
 * stands rather than built as written: moot, for the two reasons [HomeScreen]'s own class doc now
 * states in full. This is the guard, so a later agent cannot quietly put either half back without
 * this test noticing and pointing at the reasoning it would be contradicting.
 */
class HomeScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/home/HomeScreen.kt").readText()).code
    }

    @Test
    fun `the shared header's TopBar carries no action or wallet fragment, on purpose`() {
        val headerStart = source.indexOf("val header: @Composable () -> Unit")
        assertTrue("HomeScreen.kt has no shared header", headerStart >= 0)
        val topBarStart = source.indexOf("TopBar(", headerStart)
        assertTrue("the shared header never calls TopBar", topBarStart >= 0)
        val lineEnd = source.indexOf('\n', topBarStart).let { if (it < 0) source.length else it }
        val call = source.substring(topBarStart, lineEnd)
        assertFalse("U9: the shared header must carry no TopBar action (see HomeScreen's own class doc)", "action =" in call)
        assertFalse("U9: the shared header must carry no wallet fragment (see HomeScreen's own class doc)", "meta =" in call)
    }

    /**
     * The structural fact [com.plainticker.mobile.ui.vote.VoteScreen]'s own U13 measurement rests
     * on: [AmberBottomNav] is a sibling of the weighted `Box` that hosts the selected destination's
     * scrolling content, not a child of it, so switching destinations stays reachable at any scroll
     * depth regardless of what a destination's own scrolled-away header carries.
     */
    @Test
    fun `the bottom bar is a sibling of the scrolled destination content, not nested inside it`() {
        val boxStart = source.indexOf("Box(Modifier.weight(1f)) {")
        assertTrue("HomeScreen.kt has no weighted destination Box", boxStart >= 0)
        val braceOpen = source.indexOf('{', boxStart)
        val braceClose = closingBrace(source, braceOpen)
        val navIndex = source.indexOf("AmberBottomNav(")
        assertTrue("AmberBottomNav must be drawn somewhere in HomeScreen.kt", navIndex >= 0)
        assertTrue(
            "AmberBottomNav must be drawn after the weighted content Box closes, as its sibling, " +
                "never nested inside it",
            navIndex > braceClose,
        )
    }

    private fun closingBrace(code: String, openBraceIndex: Int): Int {
        var depth = 0
        var i = openBraceIndex
        while (i < code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return code.length - 1
    }

    @Test
    fun `a promo request from Detail selects You and hands the request on`() {
        val effect = source.substring(source.indexOf("LaunchedEffect(openPromo)"), source.indexOf("BackHandler("))
        assertTrue("if (openPromo) select(AmberDestination.YOU)" in effect)
        assertTrue("openPromo = openPromo," in source)
        assertTrue("onPromoOpened = onPromoOpened," in source)
    }

    /**
     * Device QA of 1.3.17: with the keyboard up the window panned, the scrim left the screen and
     * text ran under the clock, and "No xStock matches" sat behind the keys. The window resizes now
     * and the shell lays out above the keyboard, with the bar out of the way.
     */
    @Test
    fun `the shell lays out above the keyboard instead of the window panning behind it`() {
        val manifest = File(module, "src/main/AndroidManifest.xml").readText()
        assertTrue("android:windowSoftInputMode=\"adjustResize\"" in manifest)
        assertTrue("Column(Modifier.fillMaxSize().imePadding())" in source)
        assertTrue("if (!imeVisible) AmberBottomNav(" in source)
        assertTrue("the clock's scrim still draws over every destination", "TopScrim(Modifier.align(Alignment.TopCenter)" in source)
        val digest = File(module, "src/main/java/com/plainticker/mobile/ui/you/DigestScreen.kt").readText()
        assertTrue("the digest screen scrolls under the same scrim", "TopScrim(Modifier.align(Alignment.TopCenter)" in digest)
        assertTrue("its card sits on the page gutter, once", "Panel(modifier = Modifier.padding(vertical = 8.dp), inset = 16.dp" in digest)
    }
}
