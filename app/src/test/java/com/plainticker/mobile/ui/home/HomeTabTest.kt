package com.plainticker.mobile.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The digest notification's `EXTRA_TAB` is a raw ordinal (AppNavHost.kt,
 * com.plainticker.mobile.watchlist.WatchlistNotifications), so a reordering of [HomeTab] that
 * nobody remembers to update would silently send a stored deep link to the wrong screen.
 *
 * You is appended after Watchlist and stays outside the tab row (docs/plan-app-uiux-2026-09-21.md,
 * task U1): pinned here so `WATCHLIST.ordinal == 3` never moves under a later edit, and so a new
 * tab is caught the moment it lands anywhere but last.
 */
class HomeTabTest {

    @Test
    fun `the four tab ordinals are unmoved, and You is appended after Watchlist`() {
        assertEquals(0, HomeTab.LIST.ordinal)
        assertEquals(1, HomeTab.VOTE.ordinal)
        assertEquals(2, HomeTab.PORTFOLIO.ordinal)
        assertEquals(3, HomeTab.WATCHLIST.ordinal)
        assertEquals(4, HomeTab.YOU.ordinal)
    }

    @Test
    fun `You is the last entry, so appending anything else again does not silently insert before it`() {
        assertEquals(HomeTab.YOU, HomeTab.entries.last())
        assertEquals(5, HomeTab.entries.size)
    }
}
