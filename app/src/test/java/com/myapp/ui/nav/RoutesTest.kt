package com.myapp.ui.nav

import org.junit.Assert.assertEquals
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
    fun `the detail route carries the underlying ticker as a key`() {
        assertEquals("detail/AAPL", Routes.detail("aapl"))
        assertEquals("detail/AAPL", Routes.detail("  AAPL "))
        assertEquals(Routes.DETAIL, "detail/{${Routes.ARG_TICKER}}")
    }
}
