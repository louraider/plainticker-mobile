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
}
