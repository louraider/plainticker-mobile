package com.plainticker.mobile.ui.you

import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.data.receipts.FakeReceiptStore
import com.plainticker.mobile.data.receipts.FakeVoteReceiptStore
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.wallet.FakeWalletSession
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.testAccount
import com.plainticker.mobile.watchlist.FakeDigestNotifier
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Fresh-device QA of 1.3.23 (33, 33b, 44): both Connect actions on You did nothing on a phone with
 * no wallet app, the `solana-wallet:` intent had nothing to open and the answer was dropped. The
 * answer is now kept, with the action it came from, so You says so right there.
 */
class YouViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun viewModel(wallet: FakeWalletSession) =
        YouViewModel(wallet, FakeReceiptStore(), FakeVoteReceiptStore(), InMemoryWatchlistStore(), FakeDigestNotifier())

    @Test
    fun `a connect with no wallet app on the phone is said under the action that was tapped`() = runTest {
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.NoWallet, WalletOutcome.NoWallet) }
        val vm = viewModel(wallet)

        vm.connect(ConnectPlace.HERO)
        runCurrent()
        assertEquals(ConnectPlace.HERO, vm.state.value.noWalletAt)

        vm.connect(ConnectPlace.WALLET)
        runCurrent()
        assertEquals("the line moves to the action tapped last", ConnectPlace.WALLET, vm.state.value.noWalletAt)
        assertEquals(2, wallet.connectCount)
    }

    @Test
    fun `a wallet that connects clears the line, and a cancel says nothing`() = runTest {
        val account = testAccount()
        val wallet = FakeWalletSession().apply {
            enqueue(WalletOutcome.Cancelled, WalletOutcome.NoWallet, WalletOutcome.Success(account))
        }
        val vm = viewModel(wallet)

        vm.connect(ConnectPlace.WALLET)
        runCurrent()
        assertNull("backing out of the wallet is the reader's own answer", vm.state.value.noWalletAt)

        vm.connect(ConnectPlace.WALLET)
        runCurrent()
        assertEquals(ConnectPlace.WALLET, vm.state.value.noWalletAt)

        vm.connect(ConnectPlace.WALLET)
        runCurrent()
        assertNull(vm.state.value.noWalletAt)
        assertEquals(account, vm.state.value.account)
    }
}
