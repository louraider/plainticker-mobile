package com.plainticker.mobile.ui.home

import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.ui.components.AmberDestination
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The translation from a [HomeTab] ordinal, the namespace every existing deep link and saved
 * state path was already written against, to the [AmberDestination] the Amber shell actually
 * draws (docs/design-research-2026-09-21.md section 3). [HomeTabTest] pins the ordinals
 * themselves and must stay untouched; this file pins what each of them now resolves to, and that
 * the three places that still hand HomeScreen a raw ordinal instead of an [AmberDestination] (the
 * digest notification's `EXTRA_TAB`, a landed swap's "View in Portfolio", and You's own
 * device-fact cells) are still wired through the translation rather than around it.
 */
class HomeTabDestinationTest {

    @Test
    fun `every HomeTab ordinal resolves to the AmberDestination the research maps it to`() {
        assertEquals(AmberDestination.TODAY, HomeTab.LIST.toAmberDestination())
        assertEquals(AmberDestination.VOTE, HomeTab.VOTE.toAmberDestination())
        assertEquals(AmberDestination.PORTFOLIO, HomeTab.PORTFOLIO.toAmberDestination())
        assertEquals(AmberDestination.TODAY, HomeTab.WATCHLIST.toAmberDestination())
        assertEquals(AmberDestination.YOU, HomeTab.YOU.toAmberDestination())
    }

    /**
     * The one ordinal the research names explicitly: "HomeTab.WATCHLIST stays in the enum so the
     * digest notification's EXTRA_TAB still resolves; it is translated to Today scrolled to
     * Yours." Asserted by value, at the ordinal the notification actually stores, so a silent
     * renumbering of either enum cannot slide this past the test by coincidence.
     */
    @Test
    fun `the digest notification's stored ordinal opens Today, not a removed Watchlist destination`() {
        assertEquals(3, HomeTab.WATCHLIST.ordinal)
        assertEquals(AmberDestination.TODAY, HomeTab.entries[HomeTab.WATCHLIST.ordinal].toAmberDestination())
    }

    /** A landed swap's "View in Portfolio" (AppNavHost.kt) still names `HomeTab.PORTFOLIO`. */
    @Test
    fun `the swap receipt's stored ordinal still opens Portfolio`() {
        assertEquals(2, HomeTab.PORTFOLIO.ordinal)
        assertEquals(AmberDestination.PORTFOLIO, HomeTab.entries[HomeTab.PORTFOLIO.ordinal].toAmberDestination())
    }

    /**
     * An ordinal this build does not recognise (a future build's stored extra opened by an older
     * one, or a stale saved instance state) falls back to the same "no tab named" default the nav
     * graph itself declares, rather than throwing.
     */
    @Test
    fun `an out-of-range ordinal falls back to Today rather than crashing`() {
        assertEquals(AmberDestination.TODAY, HomeTab.entries.getOrElse(-1) { HomeTab.LIST }.toAmberDestination())
        assertEquals(AmberDestination.TODAY, HomeTab.entries.getOrElse(99) { HomeTab.LIST }.toAmberDestination())
    }

    // ---- HomeScreen.kt actually wires the translation, at every known ordinal source ------------

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(path: String): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/$path").readText()).code

    @Test
    fun `HomeScreen translates every incoming ordinal instead of indexing AmberDestination by it directly`() {
        val home = source("ui/home/HomeScreen.kt")
        assertTrue(
            "the start destination must go through the translation, not AmberDestination.entries[initialTab]",
            "homeTabFrom(initialTab).toAmberDestination()" in home,
        )
        assertTrue(
            "You's device-fact cells hand back a HomeTab ordinal (YouScreen.kt) and must be translated the same way",
            "homeTabFrom(tabOrdinal).toAmberDestination()" in home,
        )
    }

    @Test
    fun `the notification and the swap receipt still address HomeScreen by a HomeTab ordinal`() {
        assertTrue(
            "WatchlistNotifications must still store HomeTab.WATCHLIST.ordinal, not an AmberDestination one",
            "HomeTab.WATCHLIST.ordinal" in source("watchlist/WatchlistNotifications.kt"),
        )
        assertTrue(
            "AppNavHost's View in Portfolio must still name HomeTab.PORTFOLIO.ordinal",
            "HomeTab.PORTFOLIO.ordinal" in source("ui/nav/AppNavHost.kt"),
        )
    }
}
