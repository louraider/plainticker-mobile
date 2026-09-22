package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one state slot under the TopBar (this task's brief), read from source the way every other
 * component test in this package is: nothing here can render Compose on a plain JVM. Before this
 * pass `Banner` had no `colors` parameter at all and drew Instrument's fixed dark `Elevated` and
 * `Ink2` unconditionally, worse than the other five components this task covers (they at least had
 * a `colors: AmberColors` default that only defaulted dark); [ThemeWiringTest] now also holds this
 * file to the same `defaultAmberColors()` rule as `Skeleton.kt` and `TopBar.kt`.
 */
class BannerTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/Banner.kt").readText()).code
    }

    @Test
    fun `every colour is a theme-following AmberColors role, none of Instrument's fixed tokens survive`() {
        assertTrue("colors: AmberColors = defaultAmberColors()" in source)
        assertTrue("the surface is colors own, not a fixed Elevated", "colors.surfaceRaised" in source)
        assertTrue("the text is colors own, not a fixed Ink2", "colors.textSecondary" in source)
        assertTrue("the action reads colors own actionText, not TextAction's fixed Accent default", "color = colors.actionText" in source)
        listOf("Elevated", "Ink2", "Accent").forEach {
            assertFalse("$it survives as a fixed token in Banner.kt", it in source)
        }
    }

    /**
     * The clip trap this task's brief names by name: a fixed-width sibling (the Retry
     * [TextAction]) beside a value with no room to wrap clips it. [Banner]'s text carries no
     * `maxLines`, so it wraps under a long banner instead; this pins that no line limit was added
     * back by a later edit.
     */
    @Test
    fun `the message wraps beside the fixed-width action, it is never limited to one line`() {
        val start = source.indexOf("text = text,")
        assertTrue("Banner.kt has no Text(text = text, ...) call to check", start >= 0)
        val end = source.indexOf("if (action != null", start)
        assertTrue("the action gate does not follow the message Text(...) call", end > start)
        val messageText = source.substring(start, end)
        assertFalse("a maxLines here would silently clip a long banner beside Retry", "maxLines" in messageText)
        assertTrue("the text takes the flexible slot, the action stays its own fixed width", "Modifier.weight(1f)" in messageText)
    }

    @Test
    fun `the optional action is still a real 48dp TextAction, never a bare Text`() {
        assertTrue("TextAction(" in source)
        assertTrue("the action is gated on both action and onAction being present", "action != null && onAction != null" in source)
    }

    @Test
    fun `the banner is a polite live region, announced once per change`() {
        assertTrue("liveRegion = LiveRegionMode.Polite" in source)
        assertTrue("mergeDescendants = true" in source)
    }
}
