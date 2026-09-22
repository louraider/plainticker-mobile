package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bar costs in dependencies and what it draws, read from source the way
 * [com.plainticker.mobile.ui.vote.VoteScreenTest] reads its screen: nothing here can render
 * `ShortNavigationBar` on a plain JVM, so what is pinned is the contract instead of a layout.
 */
class AmberBottomNavTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberBottomNav.kt").readText()).code
    }

    // ---- Five destinations, in the order the research names them (section 3) ------------------

    @Test
    fun `there are exactly five destinations, Today Stocks Vote Portfolio You in that order`() {
        assertEquals(
            listOf(
                AmberDestination.TODAY, AmberDestination.STOCKS, AmberDestination.VOTE,
                AmberDestination.PORTFOLIO, AmberDestination.YOU,
            ),
            AmberDestination.entries,
        )
    }

    @Test
    fun `every destination carries a real, distinct icon pair`() {
        val all = AmberDestination.entries
        val everyIcon = all.flatMap { listOf(it.icon, it.iconSelected) }
        assertEquals(
            "a copy-pasted icon reference would collapse two destinations, or a state, onto one glyph",
            everyIcon.size, everyIcon.toSet().size,
        )
        all.forEach { assertTrue("${it.name}'s label is a real string resource", it.label != 0) }
    }

    // ---- What the bar costs, and what it draws (DESIGN.md section 10) -------------------------

    @Test
    fun `the bar is built from the stable, non-experimental ShortNavigationBar, nothing from 1-5-0-alpha`() {
        assertTrue("ShortNavigationBar" in source)
        assertTrue("ShortNavigationBarItem" in source)
        // Absent from material3 1.4.0 (DESIGN.md section 10); reaching for any of them here would
        // be the alpha dependency this task's brief and DESIGN.md both refuse.
        listOf("ButtonGroup", "LoadingIndicator", "FlexibleBottomAppBar").forEach {
            assertFalse("$it is only in 1.5.0-alpha", it in source)
        }
        assertFalse("no opt-in was needed for any of this", "OptIn" in source)
    }

    @Test
    fun `the bar sits on surfaceRaised with textPrimary content, per research 5-3`() {
        assertTrue("containerColor = colors.surfaceRaised" in source)
        assertTrue("contentColor = colors.textPrimary" in source)
    }

    @Test
    fun `the selected item turns actionText amber over a surfaceHigh indicator, never surfaceRaised again`() {
        assertTrue("selectedIconColor = colors.actionText" in source)
        assertTrue("selectedTextColor = colors.actionText" in source)
        assertTrue("selectedIndicatorColor = colors.surfaceHigh" in source)
    }

    @Test
    fun `the icon carries its own null content description because the label beside it already speaks`() {
        assertTrue("contentDescription = null" in source)
    }
}
