package com.plainticker.mobile.ui.swap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The amount, refused before any network call and converted without a float.
 *
 * Every case here is a case the swap machine never sends to Jupiter: the sheet knows the balance
 * because it was read from the chain, so an amount it cannot fund is answered locally.
 */
class SwapAmountTest {

    private val usdcBalance = 20_200_000L // the demo wallet's 20.2 USDC, 6 decimals
    private val tokenBalance = 1_366_141L // 0.01366141 TSLAx, 8 decimals

    private fun usdc(text: String) = SwapAmount.parse(text, decimals = 6, balanceRaw = usdcBalance)

    @Test
    fun `a whole amount converts through the decimals exactly`() {
        val input = usdc("5")
        assertEquals(5_000_000L, input.raw)
        assertNull(input.problem)
        assertTrue(input.isUsable)
    }

    @Test
    fun `a fractional amount a double cannot represent converts exactly`() {
        // 20.2 * 1e6 in binary floating point is 20199999.999999996; this is 20200000.
        assertEquals(20_200_000L, usdc("20.2").raw)
        assertEquals(1_100_000L, usdc("1.1").raw)
        assertEquals(1L, usdc("0.000001").raw)
    }

    @Test
    fun `the whole balance is usable, one base unit more is not`() {
        assertTrue(usdc("20.2").isUsable)
        val over = usdc("20.200001")
        assertEquals(AmountProblem.ABOVE_BALANCE, over.problem)
        assertFalse(over.isUsable)
    }

    @Test
    fun `blank is empty, not an error about numbers`() {
        assertEquals(AmountProblem.EMPTY, usdc("").problem)
        assertEquals(AmountProblem.EMPTY, usdc("   ").problem)
    }

    @Test
    fun `zero and less are refused`() {
        assertEquals(AmountProblem.NOT_ABOVE_ZERO, usdc("0").problem)
        assertEquals(AmountProblem.NOT_ABOVE_ZERO, usdc("0.000000").problem)
        assertEquals(AmountProblem.NOT_ABOVE_ZERO, usdc("-1").problem)
        assertEquals(0L, usdc("0").raw)
    }

    @Test
    fun `more decimals than the token counts is refused, not rounded`() {
        val tooFine = usdc("0.0000001")
        assertEquals(AmountProblem.TOO_PRECISE, tooFine.problem)
        assertEquals(0L, tooFine.raw)
    }

    @Test
    fun `text that is not a number is refused`() {
        listOf("abc", "5x", "1.2.3", "0x10", "five").forEach {
            assertEquals(it, AmountProblem.NOT_A_NUMBER, usdc(it).problem)
        }
    }

    @Test
    fun `grouping and a half typed decimal point are forgiven`() {
        assertEquals(AmountProblem.ABOVE_BALANCE, usdc("1,000").problem)
        assertEquals(1_000_000_000L, usdc("1,000").raw)
        assertEquals(5_000_000L, usdc("5.").raw)
        assertEquals(500_000L, usdc(".5").raw)
    }

    @Test
    fun `an amount past what a long can count reads as over the balance`() {
        val huge = usdc("99999999999999999999")
        assertEquals(AmountProblem.ABOVE_BALANCE, huge.problem)
        assertEquals(0L, huge.raw)
        assertFalse(huge.isUsable)
    }

    @Test
    fun `Max writes the balance so that parsing reads it back to the same base units`() {
        assertEquals("20.2", SwapAmount.maxText(usdcBalance, 6))
        assertEquals(usdcBalance, usdc(SwapAmount.maxText(usdcBalance, 6)).raw)

        val text = SwapAmount.maxText(tokenBalance, 8)
        assertEquals("0.01366141", text)
        assertEquals(tokenBalance, SwapAmount.parse(text, decimals = 8, balanceRaw = tokenBalance).raw)
    }

    @Test
    fun `Max on an empty balance is zero and stays unusable`() {
        assertEquals("0", SwapAmount.maxText(0L, 6))
        assertEquals(AmountProblem.NOT_ABOVE_ZERO, SwapAmount.parse("0", 6, 0L).problem)
    }
}
