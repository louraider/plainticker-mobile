package com.plainticker.mobile.ui.swap

import com.plainticker.mobile.wallet.FakeAdapterOperations
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beeman, judges' review 2026-09-27: sign_transactions is optional in MWA 2.x. */
class SwapWalletCapabilitiesTest {

    @Test
    fun `a wallet that lists sign-only is not refused, as Seed Vault Wallet on the Seeker lists it`() {
        val seedVault = FakeAdapterOperations.capabilities(arrayOf("solana:signTransactions"))
        assertFalse(SwapWalletCapabilities.refusesSignOnly(seedVault))
    }

    @Test
    fun `a wallet whose optional features leave sign-only out is refused`() {
        assertTrue(SwapWalletCapabilities.refusesSignOnly(FakeAdapterOperations.capabilities(emptyArray())))
        assertTrue(SwapWalletCapabilities.refusesSignOnly(FakeAdapterOperations.capabilities(arrayOf("solana:signInWithSolana"))))
    }

    @Test
    fun `a wallet that did not answer, or an MWA 1 wallet read through its legacy flags, is not refused here`() {
        assertFalse(SwapWalletCapabilities.refusesSignOnly(null))
        val legacy = FakeAdapterOperations.capabilities(arrayOf("supports_sign_and_send_transactions"))
        assertFalse(SwapWalletCapabilities.refusesSignOnly(legacy))
    }
}
