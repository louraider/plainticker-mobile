package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.repo.agreeingSecondRead
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.scaled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The pure checks between the forwarder's word and what the wallet is asked to approve (security
 * audit, 2026-09-26). Each refusal the audit asked for is one case here; the view model tests
 * prove the machine acts on them.
 */
class SwapTrustTest {

    private val tslax = SwapToken(KnownMints.TSLAX, "TSLAx", 8)

    private fun assertMismatch(verdict: SwapTrust.ScaleVerdict) =
        assertTrue("expected a mismatch, got $verdict", verdict is SwapTrust.ScaleVerdict.Mismatch)

    // ---- Decimals ------------------------------------------------------------------------------

    @Test
    fun `only eight decimals are an xStock's, and nothing else is corrected into eight`() {
        assertTrue(SwapTrust.decimalsPinned(8))
        listOf(0, 6, 7, 9, 10, 18).forEach { assertTrue("$it", !SwapTrust.decimalsPinned(it)) }
        // A token the sheet holds with 10 decimals is refused even when the node reads 8.
        assertMismatch(SwapTrust.confirmScale(tslax.copy(decimals = 10), 264_600L, agreeingSecondRead()))
        // And a node reading 10 is refused even when the sheet holds 8.
        assertMismatch(SwapTrust.confirmScale(tslax, 264_600L, agreeingSecondRead(mintFacts(decimals = 10))))
    }

    // ---- Multiplier ----------------------------------------------------------------------------

    @Test
    fun `agreeing reads confirm, and the cap is the forwarder's own balance when it is the smaller`() {
        assertEquals(
            SwapTrust.ScaleVerdict.Confirmed(264_600L),
            SwapTrust.confirmScale(tslax, 264_600L, agreeingSecondRead(spendableRaw = 300_000L)),
        )
    }

    @Test
    fun `a multiplier the second source does not report is a mismatch, however close to one`() {
        // STRCx's real multiplier on 2026-09-26, dropped to 1 by the forwarder: 8.6 percent more.
        val strcx = agreeingSecondRead(mintFacts(scaledUiAmount = scaled(1.0863570205637327)))
        assertMismatch(SwapTrust.confirmScale(tslax, 1L, strcx))
        // A tiny forged multiplier: 100 times the base units for every typed share.
        assertMismatch(SwapTrust.confirmScale(tslax.copy(multiplier = BigDecimal("0.01")), 1L, agreeingSecondRead()))
        // One part in ten million is still a mismatch; the tolerance only absorbs float rounding.
        assertMismatch(SwapTrust.confirmScale(tslax.copy(multiplier = BigDecimal("1.0000001")), 1L, agreeingSecondRead()))
        // No multiplier known at all is not "one".
        assertMismatch(SwapTrust.confirmScale(tslax.copy(multiplier = null), 1L, agreeingSecondRead()))
        // A node that does not read the account as an xStock mint confirms nothing.
        assertMismatch(SwapTrust.confirmScale(tslax, 1L, agreeingSecondRead(facts = null)))
    }

    @Test
    fun `the same multiplier from both reads confirms, including a scheduled one now in force`() {
        val strcx = 1.0863570205637327
        assertEquals(
            SwapTrust.ScaleVerdict.Confirmed(1L),
            SwapTrust.confirmScale(
                tslax.copy(multiplier = BigDecimal.valueOf(strcx)),
                1L,
                agreeingSecondRead(mintFacts(scaledUiAmount = scaled(strcx))),
            ),
        )
        // NFLXx: stored 1, a scheduled 10 in force since 2025-11-16, read on 2026-09-26.
        val nflxx = agreeingSecondRead(
            mintFacts(scaledUiAmount = scaled(1.0, newMultiplier = 10.0, effectiveAtEpochSeconds = 1_763_337_300L)),
            readAtMillis = 1_790_373_650_000L,
        )
        assertEquals(SwapTrust.ScaleVerdict.Confirmed(1L), SwapTrust.confirmScale(tslax.copy(multiplier = BigDecimal.TEN), 1L, nflxx))
        assertMismatch(SwapTrust.confirmScale(tslax, 1L, nflxx))
    }

    // ---- Balance -------------------------------------------------------------------------------

    @Test
    fun `a forged balance is capped at what the second source shows`() {
        assertEquals(
            SwapTrust.ScaleVerdict.Confirmed(264_600L),
            SwapTrust.confirmScale(tslax, 26_460_000_000L, agreeingSecondRead(spendableRaw = 264_600L)),
        )
        assertEquals(
            "a node showing none leaves nothing to spend",
            SwapTrust.ScaleVerdict.Confirmed(0L),
            SwapTrust.confirmScale(tslax, 264_600L, agreeingSecondRead(spendableRaw = 0L)),
        )
    }

    // ---- Value ---------------------------------------------------------------------------------

    private val typed = BigDecimal("0.002646")
    private val price = 376.45

    @Test
    fun `the real reverse order is consistent with its typed quantity at the price it implies`() {
        val order = HttpClientFactory.json.decodeFromString(
            SwapOrder.serializer(),
            Fixtures.read("jupiter/order-tslax-usdc-default-metis.json"),
        )
        val ui = tslax.ui(order.inAmountRaw)
        assertEquals(0, typed.compareTo(ui))
        assertEquals(SwapTrust.ValueVerdict.Consistent, SwapTrust.checkValue(ui, price, order.inUsdValue))
    }

    @Test
    fun `the bound is five percent either way`() {
        val expected = typed.toDouble() * price
        listOf(1.0, 1.049, 0.951, 1.03, 0.97).forEach { ratio ->
            assertEquals("ratio $ratio", SwapTrust.ValueVerdict.Consistent, SwapTrust.checkValue(typed, price, expected * ratio))
        }
        listOf(1.051, 0.949, 1.10, 0.90, 100.0, 0.01).forEach { ratio ->
            val verdict = SwapTrust.checkValue(typed, price, expected * ratio)
            assertTrue("ratio $ratio gave $verdict", verdict is SwapTrust.ValueVerdict.Mismatch)
        }
        assertEquals(0.05, SwapTrust.VALUE_BOUND, 0.0)
    }

    @Test
    fun `a value check with a missing figure is unchecked, never consistent`() {
        val expected = typed.toDouble() * price
        listOf(
            SwapTrust.checkValue(typed, null, expected),
            SwapTrust.checkValue(typed, 0.0, expected),
            SwapTrust.checkValue(typed, Double.NaN, expected),
            SwapTrust.checkValue(typed, price, 0.0),
            SwapTrust.checkValue(typed, price, Double.POSITIVE_INFINITY),
            SwapTrust.checkValue(null, price, expected),
            SwapTrust.checkValue(BigDecimal.ZERO, price, expected),
        ).forEach { assertTrue("got $it", it is SwapTrust.ValueVerdict.Unchecked) }
    }
}
