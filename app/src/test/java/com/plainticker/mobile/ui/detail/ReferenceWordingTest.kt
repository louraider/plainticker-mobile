package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.ui.ShippedCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QA of 1.3.22: Method said "the share's last US price" while the NYSE was open, and the Stocks
 * row said "vs US price" while Detail said "NYSE price". One wording now holds in both states:
 * Jupiter's `stockData` is the share's latest US trade, which is neither only a close nor an NYSE
 * print. Only the shut state keeps "last", where it is true.
 */
class ReferenceWordingTest {

    private val strings = ShippedCopy.strings

    @Test
    fun `Method names the reference in words true whether the NYSE is open or shut`() {
        val sources = strings.getValue("detail_method_sources")
        assertTrue(sources, "latest US price" in sources)
        assertFalse(sources, "last US price" in sources)
        assertFalse(sources, "NYSE" in sources)
    }

    @Test
    fun `the row and Detail name the open-session reference the same way`() {
        assertEquals("%1\$s vs US price", strings["list_row_meta_premium"])
        assertEquals("US price", strings["detail_nyse_price"])
        assertEquals("Token vs US price", strings["detail_gauge_reference_live"])
        // Shut, the reference is the last trade, and Detail and the hours banner say so.
        assertEquals("Last US price", strings["detail_nyse_close"])
        assertEquals("Token vs last US price", strings["detail_gauge_reference_close"])
    }
}
