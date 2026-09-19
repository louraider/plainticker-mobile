package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TopTabs carries four labels now that task A2 added Vote, and DESIGN.md commits to honoring the
 * font scale up to 1.3x. Nothing in this module's plain-JVM unit tests can actually lay out text
 * and measure it: there is no Robolectric here and no instrumentation is run from this gate, so
 * whether "Watchlist" still fits the 400dp Seeker frame at that scale is not something a unit
 * test can answer. What is pinned instead is the mitigation: the row scrolls rather than clips
 * when four labels do not fit at whatever scale is in force, so a later edit cannot quietly drop
 * that and reintroduce a clipped tab believing three labels never needed it.
 */
class TopTabsTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/TopTabs.kt").readText()).code
    }

    @Test
    fun `the tab row scrolls horizontally rather than clipping a label that does not fit`() {
        assertTrue(
            "a row of tabs with no way to reach an overflowing label clips it silently",
            "horizontalScroll(" in source,
        )
    }
}
