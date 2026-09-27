package com.plainticker.mobile.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation graph itself needs a device, but the two decisions it makes from data do not:
 * where the app opens (DT11, onboarding once) and how a ticker becomes a detail route.
 */
class RoutesTest {

    @Test
    fun `the first launch starts at onboarding and every later one at home`() {
        assertEquals(Routes.ONBOARDING, Routes.start(onboarded = false))
        assertEquals(Routes.HOME, Routes.start(onboarded = true))
    }

    @Test
    fun `home names a tab as an optional argument, so the start destination is unchanged`() {
        assertEquals("home?tab={tab}", Routes.HOME_TAB)
        assertEquals("home?tab=1", Routes.home(1))
        // The receipt's "View in Portfolio" is the one caller; plain "home" still matches the
        // same destination because the argument carries a default.
        assertTrue(Routes.HOME_TAB.startsWith(Routes.HOME))
        assertTrue(Routes.home(0).startsWith(Routes.HOME))
    }

    @Test
    fun `the detail route carries the underlying ticker as a key`() {
        assertEquals("detail/AAPL", Routes.detail("aapl"))
        assertEquals("detail/AAPL", Routes.detail("  AAPL "))
        assertEquals(Routes.DETAIL, "detail/{${Routes.ARG_TICKER}}")
    }

    @Test
    fun `the promo request is a saved-state flag, never part of a route`() {
        assertEquals("open_promo", Routes.KEY_OPEN_PROMO)
        assertTrue(Routes.KEY_OPEN_PROMO !in Routes.HOME_TAB)
        assertTrue(Routes.KEY_OPEN_PROMO !in Routes.DETAIL)
    }
}
