package com.plainticker.mobile.ui.portfolio

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.pass.ProUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Portfolio's Pro block says, for each of the three entitlement sources and for none
 * (task A6's acceptance list: "entitlement states for each source and for none"), and the rule
 * that a stake is stated as a figure and never as a verdict about the wallet carrying it.
 */
class ProModelTest {

    private fun words(copy: Copy): Int = (copy as Copy.Words).id

    // ---- Entitlement, one state at a time --------------------------------------------------

    @Test
    fun `loading reads as loading, before anything else is asked`() {
        assertEquals(R.string.pro_entitlement_loading, words(entitlementLine(ProUiState(entitlementLoading = true))))
    }

    @Test
    fun `disabled is an honest unavailable state, never an error`() {
        val state = ProUiState(entitlementLoading = false, entitlementDisabled = true)
        assertEquals(R.string.pro_entitlement_disabled, words(entitlementLine(state)))
        assertTrue("disabled offers no retry", !entitlementRetries(state))
    }

    @Test
    fun `a failed read is retryable, and reads apart from disabled`() {
        val state = ProUiState(entitlementLoading = false, entitlementFailed = true)
        assertEquals(R.string.pro_entitlement_failed, words(entitlementLine(state)))
        assertTrue(entitlementRetries(state))
    }

    @Test
    fun `pass names the source and the date it ends`() {
        val until = 1_792_368_000_000L // 2026-10-19T00:00:00Z
        val state = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.PASS, untilMillis = until)
        val line = entitlementLine(state) as Copy.Words
        assertEquals(R.string.pro_entitlement_pass_until, line.id)
        assertEquals(listOf("19 Oct 2026 00:00 UTC"), line.args)
    }

    @Test
    fun `a pass with no date still says which source it is`() {
        val state = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.PASS, untilMillis = null)
        assertEquals(R.string.pro_entitlement_pass, words(entitlementLine(state)))
    }

    @Test
    fun `stake carries no date, because it is re-evaluated rather than granted once`() {
        val state = ProUiState(
            entitlementLoading = false,
            pro = true,
            source = EntitlementSource.STAKE,
            // A date would be a lie for a source that is never granted with an expiry; passing one
            // anyway must not leak into the sentence.
            untilMillis = 1_760_832_000_000L,
        )
        assertEquals(R.string.pro_entitlement_stake, words(entitlementLine(state)))
    }

    @Test
    fun `subscription names the source and the date it ends`() {
        val state = ProUiState(
            entitlementLoading = false,
            pro = true,
            source = EntitlementSource.SUBSCRIPTION,
            untilMillis = 1_760_832_000_000L,
        )
        val line = entitlementLine(state) as Copy.Words
        assertEquals(R.string.pro_entitlement_subscription_until, line.id)
    }

    @Test
    fun `pro true with no recognized source still says the wallet is pro, never a guess at which`() {
        val state = ProUiState(entitlementLoading = false, pro = true, source = null)
        assertEquals(R.string.pro_entitlement_active, words(entitlementLine(state)))
    }

    @Test
    fun `none of the three sources reads as not pro, plainly`() {
        val state = ProUiState(entitlementLoading = false, pro = false, source = null)
        assertEquals(R.string.pro_entitlement_none, words(entitlementLine(state)))
    }

    // ---- The stake: a figure, never a verdict ------------------------------------------------

    @Test
    fun `no wallet connected asks to connect one, and states no figure at all`() {
        val state = ProUiState(walletConnected = false)
        assertEquals(R.string.pro_stake_disconnected, words(stakeLine(state)!!))
    }

    @Test
    fun `a connected wallet whose stake could not be read says so, never a zero`() {
        val state = ProUiState(walletConnected = true, stakeUnread = true, stakeRaw = null)
        assertEquals(R.string.pro_stake_unread, words(stakeLine(state)!!))
    }

    @Test
    fun `a stake still in flight draws nothing, never a placeholder`() {
        val state = ProUiState(walletConnected = true, stakeUnread = false, stakeRaw = null)
        assertNull(stakeLine(state))
    }

    @Test
    fun `a stake below the entitlement threshold is stated as a figure, with no verdict word near it`() {
        // 3,200 SKR: below the 10,000 SKR threshold (docs/plan-monetisation-2026-09-19.md section
        // 1.2). The sentence states it and stops; no word here says whether that is enough.
        val state = ProUiState(walletConnected = true, stakeRaw = 3_200_000_000L)
        val line = stakeLine(state) as Copy.Words
        assertEquals(R.string.pro_stake_read, line.id)
        assertEquals(listOf("3,200"), line.args)
    }

    @Test
    fun `a stake above the threshold is stated the same honest way, with no verdict either`() {
        val state = ProUiState(walletConnected = true, stakeRaw = 31_209_870_777L)
        val line = stakeLine(state) as Copy.Words
        assertEquals(R.string.pro_stake_read, line.id)
        assertEquals(listOf("31,209.870777"), line.args)
    }

    // ---- A pending payment (task A6 review) --------------------------------------------------

    @Test
    fun `no pending payment draws no sentence at all`() {
        assertNull(pendingPaymentLine(ProUiState(pendingSignature = null)))
    }

    @Test
    fun `a pending payment states so, in place of the Pay action rather than beside it`() {
        val line = pendingPaymentLine(ProUiState(pendingSignature = "sig")) as Copy.Words
        assertEquals(R.string.pro_payment_pending, line.id)
    }

    // ---- The action matrix (v0.7.0 review: Pay must never be reachable from a refused state) ----

    /**
     * Which of the two actions the block offers, for every state that matters: refused by the
     * server, a read that merely failed, no wallet, a wallet with nothing, a wallet already Pro,
     * and a pending payment. Pinned as one table so the next line added to either predicate has
     * to answer for all six at once, rather than for whichever one prompted the change.
     */
    private data class Offered(val pay: Boolean, val retry: Boolean)

    private fun offered(state: ProUiState) = Offered(payOffered(state), entitlementRetries(state))

    @Test
    fun `refused by the server offers neither action`() {
        // 503 monetization_disabled (or the entitlement route's own 404): pass-build is gated on
        // the same flag, so Pay could only end in the same refusal, after first spending a wallet
        // approval on a connect for nothing (the v0.7.0 finding this matrix exists to pin).
        val state = ProUiState(entitlementLoading = false, entitlementDisabled = true)
        assertEquals(Offered(pay = false, retry = false), offered(state))
    }

    @Test
    fun `an entitlement read that merely failed still offers Pay, because pass-build is its own call`() {
        // A failure is not a refusal: retrying entitlement can plausibly answer differently, and
        // paying was never told no by anyone, so neither action is withheld for this reason.
        val state = ProUiState(entitlementLoading = false, entitlementFailed = true)
        assertEquals(Offered(pay = true, retry = true), offered(state))
    }

    @Test
    fun `no wallet connected still offers Pay, since paying is what connects one`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = false, pro = false)
        assertEquals(Offered(pay = true, retry = false), offered(state))
    }

    @Test
    fun `a connected wallet with no entitlement offers Pay`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = true, pro = false, stakeRaw = 0L)
        assertEquals(Offered(pay = true, retry = false), offered(state))
    }

    @Test
    fun `a connected wallet already Pro still offers Pay, since a later payment replaces the pass`() {
        val state = ProUiState(
            entitlementLoading = false,
            walletConnected = true,
            pro = true,
            source = EntitlementSource.PASS,
            untilMillis = 1_792_368_000_000L,
        )
        assertEquals(Offered(pay = true, retry = false), offered(state))
    }

    @Test
    fun `a payment already pending offers neither action, so a second payment is never asked for`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = true, pro = false, pendingSignature = "sig")
        assertEquals(Offered(pay = false, retry = false), offered(state))
    }
}
