package com.plainticker.mobile.ui.swap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN.md section 4's clipping rule, for everything the 2026-09-24 swap work draws: the result
 * block, the receipt's Amber rows, the new buttons and Portfolio's "Swap to USDC" line.
 *
 * **Method, the same as AmberTickerRowTest's and never a uiautomator dump.** fontTools against the
 * bundled `res/font/bricolage_grotesque.ttf`, each style instantiated at the exact `wght` / `wdth`
 * 100 / `opsz` [com.plainticker.mobile.ui.theme.AmberType] builds it with, the `tnum` single
 * substitution applied on number styles, advances summed from `hmtx` (GPOS kerning left out, which
 * only narrows these pairs, so every width here is an upper bound). The same script reproduces
 * AmberTickerRowTest's own pinned widths to the thousandth ("AUTO.GBx" 77.296dp, "Meta Platforms,
 * Inc." 129.066dp, "38,406.2 SKR" 113.220dp, "Vote" in Outfit 30.856dp), so the numbers below are
 * measured the way that file's are. 1.3x is the flat, worse-than-real scaling that file uses: sp
 * text grows by the raw factor, dp padding does not.
 *
 * Frame: 400dp. The sheet's side inset is 20dp, so a full-width block has 360dp; a button's label
 * has 360 - 2 x 24 (Material's own content padding) - 2 x 4 (the focus ring's padding) = 304dp; an
 * Amber row's content has 400 - 2 x 20 - 2 x 16 = 328dp.
 */
class SwapResultFitTest {

    private fun at13(dp: Double) = dp * 1.3

    @Test
    fun `the hero figure stays on one line at 1_0x and 1_3x`() {
        // figureLarge, 34sp / 700 / tnum. The widest realistic fill: 99,999.999999 (USDC's six
        // decimals behind five integer digits). The founder's real fill, 0.002646, for scale.
        val budget = 360.0
        val worst = 242.896
        assertEquals(315.765, at13(worst), 0.01)
        assertTrue("hero at 1.0x", worst <= budget)
        assertTrue("hero at 1.3x: ${budget - at13(worst)}dp of margin", at13(worst) <= budget)
        assertTrue(153.272 <= budget) // "0.002646"
    }

    @Test
    fun `the headline wraps beside the mark rather than clipping, and the landing one fits on one line`() {
        // sectionHead, 22sp / 700, beside a 40dp mark and a 12dp gap: 308dp, weight(1f), no line limit.
        val budget = 360.0 - 40.0 - 12.0
        val landed = 137.566
        assertTrue("\"Swap landed\" on one line at 1.3x", at13(landed) <= budget)
        assertTrue("\"Nothing was swapped\" on one line at 1.3x", at13(236.698) <= budget)
        assertTrue("\"The swap did not land\" on one line at 1.3x", at13(232.056) <= budget)
        // "Sent, not confirmed yet" goes to a second line at 1.3x, which the weighted, unlimited
        // headline allows: two lines carry it with room to spare.
        val pending = 249.414
        assertFalse(at13(pending) <= budget)
        assertTrue(at13(pending) <= 2 * budget)
    }

    @Test
    fun `the sheet title is one line and clears its full width at 1_3x`() {
        // sectionHead, maxLines 1, softWrap false: the one slot on the sheet that may not wrap.
        val budget = 360.0
        val worst = 199.386 // "AUTO.GBx to USDC" and "USDC to AUTO.GBx", the catalog's longest symbol.
        assertEquals(259.202, at13(worst), 0.01)
        assertTrue(at13(worst) <= budget)
    }

    @Test
    fun `an Amber row's figure never clips, and its label wraps before the figure gives way`() {
        // AmberFactRows: the value is unweighted and one line (figureRow 18sp / 600 / tnum); the
        // label beside it is weight(1f) and wraps.
        val budget = 328.0
        val worstValue = 194.184 // "123.456789 AUTO.GBx"
        assertEquals(252.439, at13(worstValue), 0.01)
        assertTrue("worst value at 1.3x", at13(worstValue) <= budget)
        assertTrue("\"99,999.999999 USDC\" at 1.3x", at13(181.8) <= budget)
        assertTrue("the founder's own \"0.002646 TSLAx\" at 1.3x", at13(138.24) <= budget)
        assertTrue("slot \"450,068,679\" at 1.3x", at13(105.786) <= budget)
        // The signature fragment in JetBrains Mono 16sp, the one identifier.
        assertTrue(at13(124.8) <= budget)
        // "You receive" (context 14sp / 400, 76.216dp) beside the worst value at 1.3x: the label
        // gets 328 - 12 - 252.439 = 63.561dp and wraps to two lines. It wraps; nothing clips.
        val labelRoom = budget - 12.0 - at13(worstValue)
        assertEquals(63.561, labelRoom, 0.01)
        assertTrue(at13(76.216) <= 2 * labelRoom)
    }

    @Test
    fun `every new button label clears its budget at 1_3x`() {
        val budget = 304.0
        mapOf(
            "Swap AUTO.GBx to USDC" to 191.312,
            "Swap back to AUTO.GBx" to 184.928,
            "Swap back to USDC" to 151.36,
            "Get a new quote" to 124.752,
            "View in Portfolio" to 126.128,
            "Try again" to 71.04,
        ).forEach { (label, width) ->
            assertTrue("$label at 1.3x (${at13(width)}dp of $budget)", at13(width) <= budget)
        }
    }

    @Test
    fun `Portfolio's Swap to USDC line has the row's whole width, and the meta line could not have held it`() {
        // "Swap to USDC" at TextAction's own face, Bricolage 600 opsz 14 (AmberType.textAction,
        // re-measured 2026-09-26; 91.84dp in Outfit): 96.726dp, 125.744dp at 1.3x, plus 16dp start.
        val action = 96.726
        assertTrue(16.0 + at13(action) <= 336.0)
        // The rejected shape: on the meta line beside "$12,345.67" (figureRow, 95.724dp), the
        // context would keep 336 - (16 + 96.726) - 95.724 - 8 = 119.550dp at 1.0x and 61.815dp at
        // 1.3x, less than "1.37 TSLAx · +0.09%" needs (124.726dp; 162.144dp at 1.3x), so the
        // quantity itself would have ellipsized.
        val metaRoom10 = 336.0 - (16.0 + action) - 95.724 - 8.0
        val metaRoom13 = 336.0 - (16.0 + at13(action)) - at13(95.724) - 8.0
        assertEquals(119.550, metaRoom10, 0.01)
        assertEquals(61.815, metaRoom13, 0.01)
        assertFalse(124.726 <= metaRoom10)
        assertFalse(at13(124.726) <= 2 * metaRoom13)
    }
}
