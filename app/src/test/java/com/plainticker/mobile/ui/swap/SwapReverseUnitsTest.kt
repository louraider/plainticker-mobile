package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.R
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.scaled
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.detail.ChainRead
import com.plainticker.mobile.ui.detail.DetailUiState
import com.plainticker.mobile.ui.detail.Piece
import com.plainticker.mobile.ui.detail.swapOutLabel
import com.plainticker.mobile.ui.detail.swapToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Raw against UI for a Token-2022 xStock, the one conversion a swap back to USDC can get wrong
 * by a factor of ten (2026-09-24).
 *
 * The numbers are real. Read through PlainTicker's own forwarder that day with the exact request
 * the app sends (`getTokenAccountsByOwner`, Token-2022 program id, `jsonParsed`, and again with
 * `minContextSlot`, which the forwarder accepted), one wallet's NFLXx account answered
 * `amount` 272557048309, `decimals` 8, `uiAmountString` 27255.7048309: raw / 10^8 x 10, the
 * NFLXx mint's effective multiplier (stored 1, a scheduled 10 in force since 2025-11-16). Its TSLAx
 * account answered 532670806939 and 5326.70806939: multiplier 1. The same read showed other
 * xStocks at multipliers that are not whole numbers at all (4048582995951 raw drawn as
 * 40574.44306088, a multiplier of about 1.0022), which is why a typed amount rounds DOWN to a whole
 * base unit rather than demanding an exact one.
 */
class SwapReverseUnitsTest {

    private val nflxMint = "XsEH7wWfJJu2ZT3UCFeVfALnVA6CP5ur7Ee11KmzVpL"
    private val nflx = SwapToken(nflxMint, "NFLXx", 8, BigDecimal.TEN)

    @Test
    fun `the node's own UI amount is raw scaled by the effective multiplier, and so is the app's`() {
        assertEquals(0, BigDecimal("27255.7048309").compareTo(nflx.ui(272_557_048_309L)))
        val tslax = SwapToken(KnownMints.TSLAX, "TSLAx", 8)
        assertEquals(0, BigDecimal("5326.70806939").compareTo(tslax.ui(532_670_806_939L)))
        assertNull("an unknown multiplier is no quantity, never raw alone", tslax.copy(multiplier = null).ui(1L))
    }

    @Test
    fun `NFLXx's scheduled 10 is the multiplier in force, not the 1 the mint still stores`() {
        val nflxConfig = scaled(multiplier = 1.0, newMultiplier = 10.0, effectiveAtEpochSeconds = 1_763_337_300L)
        val split = SplitMultiplier.ofMint(nflxConfig)
        assertEquals(1.0, split.current, 0.0)
        assertEquals(10.0, split.effectiveAt(1_790_208_000_000L), 0.0)
    }

    @Test
    fun `a typed share count becomes base units through the multiplier, never more than was typed`() {
        val balance = 272_557_048_309L
        assertEquals(10_000_000L, SwapAmount.parse("1", 8, balance, BigDecimal.TEN).raw)
        assertEquals(12_345_678L, SwapAmount.parse("1.2345678", 8, balance, BigDecimal.TEN).raw)
        // Past the split's own precision the base units round down: 1.23456789 shares at 10x is
        // 12,345,678.9 raw, and this app sends 12,345,678, never 12,345,679.
        val rounded = SwapAmount.parse("1.23456789", 8, balance, BigDecimal.TEN)
        assertEquals(12_345_678L, rounded.raw)
        assertTrue(rounded.isUsable)
        // Less than one base unit is its own refusal, not "above zero".
        assertEquals(AmountProblem.BELOW_ONE_UNIT, SwapAmount.parse("0.00000001", 8, balance, BigDecimal.TEN).problem)
        assertEquals(AmountProblem.ABOVE_BALANCE, SwapAmount.parse("27255.7048310", 8, balance, BigDecimal.TEN).problem)
    }

    @Test
    fun `Max round-trips to the exact raw balance at every multiplier, whole or not`() {
        val cases = listOf(
            Triple(272_557_048_309L, 8, BigDecimal.TEN),
            Triple(532_670_806_939L, 8, BigDecimal.ONE),
            Triple(4_048_582_995_951L, 8, BigDecimal("1.0021875")),
            Triple(264_600L, 8, BigDecimal.ONE),
        )
        for ((raw, decimals, multiplier) in cases) {
            val text = SwapAmount.maxText(raw, decimals, multiplier)
            val parsed = SwapAmount.parse(text, decimals, raw, multiplier)
            assertEquals("$raw at $multiplier via \"$text\"", raw, parsed.raw)
            assertEquals(null, parsed.problem)
        }
    }

    @Test
    fun `a whole multiplier of one keeps the exact rule every existing swap relies on`() {
        assertEquals(AmountProblem.TOO_PRECISE, SwapAmount.parse("0.000000001", 8, 10_000L).problem)
        assertEquals(1L, SwapAmount.parse("0.00000001", 8, 10_000L).raw)
    }

    // ---- Detail's entry point ----------------------------------------------------------------

    private fun detail(chain: Piece<ChainRead> = Piece.Failed) = DetailUiState(
        ticker = "NFLX",
        catalogAsset = Piece.Ready(xStock("NFLXx", "NFLX", nflxMint)),
        chain = chain,
    )

    @Test
    fun `Detail hands the swap the multiplier its own chain read found in force`() {
        val read = ChainRead(
            facts = mintFacts(decimals = 8, scaledUiAmount = scaled(1.0, 10.0, 1_763_337_300L)),
            slot = 450_076_817L,
            readAtMillis = 1_790_208_000_000L,
        )
        val token = requireNotNull(detail(Piece.Ready(read)).swapToken())
        assertEquals(nflxMint, token.mint)
        assertEquals("NFLXx", token.symbol)
        assertEquals(0, BigDecimal.TEN.compareTo(token.multiplier))
    }

    @Test
    fun `Detail with no chain read leaves the multiplier unknown for the machine to read`() {
        val token = requireNotNull(detail().swapToken())
        assertNull(token.multiplier)
    }

    @Test
    fun `Detail offers Swap to USDC only for a chain-read holding of this very token`() {
        val state = detail()
        val token = requireNotNull(state.swapToken())
        assertNull("no wallet read", state.swapOutLabel(null))
        assertNull("nothing held", state.swapOutLabel(SwapHolding(token, 0L)))
        assertNull("another token's holding", state.swapOutLabel(SwapHolding(token.copy(mint = KnownMints.TSLAX), 5L)))
        val label = requireNotNull(state.swapOutLabel(SwapHolding(token, 272_557_048_309L)))
        assertEquals(R.string.detail_swap_out_button, (label as com.plainticker.mobile.ui.Copy.Words).id)
        assertEquals("Swap NFLXx to USDC", ShippedCopy.render(label))
    }
}
