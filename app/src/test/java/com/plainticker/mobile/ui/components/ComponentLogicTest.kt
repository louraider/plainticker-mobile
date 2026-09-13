package com.plainticker.mobile.ui.components

import com.plainticker.mobile.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentLogicTest {

    @Test
    fun `gauge places the token tick on the stated scale`() {
        assertEquals(0.518f, gaugeTick(tokenPremiumPct = 0.09, scalePct = 2.5).fraction, 1e-6f)
        assertEquals(0.31f, gaugeTick(tokenPremiumPct = -0.95, scalePct = 2.5).fraction, 1e-6f)
        assertEquals(0.5f, gaugeTick(tokenPremiumPct = 0.0, scalePct = 2.5).fraction, 0f)
        assertEquals(0.59f, gaugeTick(tokenPremiumPct = 0.09, scalePct = 0.5).fraction, 1e-6f)
        assertEquals(0.41f, gaugeTick(tokenPremiumPct = -0.09, scalePct = 0.5).fraction, 1e-6f)
        assertEquals(0.75f, gaugeTick(tokenPremiumPct = 0.5, scalePct = 1.0).fraction, 1e-6f)
        listOf(0.09, 0.0, -0.09).forEach {
            assertFalse(it.toString(), gaugeTick(tokenPremiumPct = it, scalePct = 0.5).offScale)
        }
    }

    @Test
    fun `gauge marks a tick past the scale instead of resting it on the end`() {
        // Clamped, because the drawing needs somewhere to put it, and flagged, because clamped is
        // not a reading: the caption and the drawing both branch on this one answer.
        val over = gaugeTick(tokenPremiumPct = 5.0, scalePct = 0.5)
        assertEquals(1f, over.fraction, 0f)
        assertTrue(over.offScale)
        val under = gaugeTick(tokenPremiumPct = -5.0, scalePct = 0.5)
        assertEquals(0f, under.fraction, 0f)
        assertTrue(under.offScale)
        // The end of the scale is still on the scale.
        assertFalse(gaugeTick(tokenPremiumPct = 0.5, scalePct = 0.5).offScale)
        assertFalse(gaugeTick(tokenPremiumPct = -0.5, scalePct = 0.5).offScale)
        // Nothing to place claims nothing: the tick sits on the reference and is not off any scale.
        assertEquals(0.5f, gaugeTick(tokenPremiumPct = 0.3, scalePct = 0.0).fraction, 0f)
        assertFalse(gaugeTick(tokenPremiumPct = 0.3, scalePct = 0.0).offScale)
        assertEquals(0.5f, gaugeTick(tokenPremiumPct = Double.NaN, scalePct = 0.5).fraction, 0f)
        assertFalse(gaugeTick(tokenPremiumPct = Double.NaN, scalePct = 0.5).offScale)
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
        assertEquals("2.5", Fmt.plain(2.5))
        assertEquals("0.5", Fmt.plain(0.5))
        assertEquals("1", Fmt.plain(1.0))
        assertEquals("0.25", Fmt.plain(0.25))
        assertEquals("2", Fmt.plain(2.0))
    }
}
