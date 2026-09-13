package com.plainticker.mobile.ui.components

import com.plainticker.mobile.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentLogicTest {

    @Test
    fun `gauge places the token tick on the stated scale`() {
        assertEquals(0.59f, gaugePosition(tokenPremiumPct = 0.09, scalePct = 0.5), 1e-6f)
        assertEquals(0.5f, gaugePosition(tokenPremiumPct = 0.0, scalePct = 0.5), 0f)
        assertEquals(0.41f, gaugePosition(tokenPremiumPct = -0.09, scalePct = 0.5), 1e-6f)
        assertEquals(0.75f, gaugePosition(tokenPremiumPct = 0.5, scalePct = 1.0), 1e-6f)
    }

    @Test
    fun `gauge clamps the tick to the track`() {
        assertEquals(1f, gaugePosition(tokenPremiumPct = 5.0, scalePct = 0.5), 0f)
        assertEquals(0f, gaugePosition(tokenPremiumPct = -5.0, scalePct = 0.5), 0f)
        assertEquals(0.5f, gaugePosition(tokenPremiumPct = 0.3, scalePct = 0.0), 0f)
        assertEquals(0.5f, gaugePosition(tokenPremiumPct = Double.NaN, scalePct = 0.5), 0f)
    }

    @Test
    fun `fact grid packs two columns and a span of two takes a row`() {
        val a = FactCell("a", "1", span = 2)
        val b = FactCell("b", "2")
        val c = FactCell("c", "3")
        val d = FactCell("d", "4")
        val e = FactCell("e", "5")
        assertEquals(listOf(listOf(a), listOf(b, c), listOf(d, e)), packRows(listOf(a, b, c, d, e)))
        assertEquals(listOf(listOf(a), listOf(b, c), listOf(d)), packRows(listOf(a, b, c, d)))
        // A span-2 cell after an unpaired cell closes that cell's row first.
        assertEquals(listOf(listOf(b), listOf(a), listOf(c, d)), packRows(listOf(b, a, c, d)))
        assertEquals(emptyList<List<FactCell>>(), packRows(emptyList()))
    }

    @Test
    fun `spoken values read as sentences`() {
        assertEquals("8 of 9", spoken("8/9"))
        assertEquals("plus 0.09 percent", spoken("+0.09%"))
        assertEquals("minus 0.04 percent", spoken("-0.04%"))
        assertEquals("100.7 percent", spoken("100.7%"))
        assertEquals("92.4 times", spoken("92.4x"))
        assertEquals("plus 14.2 pt", spoken("+14.2 pt"))
        assertEquals("TSLAx", spoken("TSLAx"))
        assertEquals("0.01364 TSLAx", spoken("0.01364 TSLAx"))
        assertEquals("\$366.17", spoken("\$366.17"))
        assertEquals("Issuer can move tokens", spoken("Issuer can move tokens"))
    }

    @Test
    fun `gauge caption scale is formatted by Fmt without trailing zeros`() {
        assertEquals("0.5", Fmt.plain(0.5))
        assertEquals("1", Fmt.plain(1.0))
        assertEquals("0.25", Fmt.plain(0.25))
        assertEquals("2", Fmt.plain(2.0))
    }
}
