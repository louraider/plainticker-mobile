package com.plainticker.mobile.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.data.receipts.ReceiptStore
import com.plainticker.mobile.data.receipts.VoteReceiptStore
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.wallet.WalletAccount
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.WalletSession
import kotlinx.coroutines.Job
import com.plainticker.mobile.watchlist.DigestNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * You's own state (docs/plan-app-uiux-2026-09-21.md, task U1): the wallet session every screen
 * shares, and this device's own record of what it did, counted rather than listed, because the
 * rows themselves already live on their own tabs and each fact here only opens the one that
 * carries them.
 */
data class YouUiState(
    val account: WalletAccount? = null,
    val swapsRecorded: Int = 0,
    val votesCast: Int = 0,
    val stocksWatched: Int = 0,
    val notificationsOn: Boolean = false,
    /**
     * The last Connect found no wallet app on this phone (fresh-device QA of 1.3.23: both Connect
     * actions on You did nothing on a phone without one), and where it was tapped, so the one
     * shared sentence stands under that action. Null once a wallet connects or nothing was found
     * wanting.
     */
    val noWalletAt: ConnectPlace? = null,
)

/** Where on You a Connect was tapped: the hero's text action, or the Wallet group's row. */
enum class ConnectPlace { HERO, WALLET }

/**
 * The identity summary and this device's own counts (task U1). The entitlement, the stake and
 * the pay machine stay [com.plainticker.mobile.ui.pass.PassViewModel]'s, scoped to the same home
 * back-stack entry as every other tab's ViewModel, so a payment mid flight survives a tab switch
 * exactly as it does today: this class exists only for the parts that were nobody's before.
 */
class YouViewModel(
    private val wallet: WalletSession,
    private val receiptStore: ReceiptStore,
    private val voteReceiptStore: VoteReceiptStore,
    private val watchlistStore: WatchlistStore,
    private val notifier: DigestNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow(
        YouUiState(
            account = wallet.account.value,
            swapsRecorded = receiptStore.receipts.value.size,
            votesCast = voteReceiptStore.receipts.value.size,
            stocksWatched = watchlistStore.tickers.value.size,
            notificationsOn = notifier.enabled(),
        ),
    )
    val state: StateFlow<YouUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                wallet.account,
                receiptStore.receipts,
                voteReceiptStore.receipts,
                watchlistStore.tickers,
            ) { account, swaps, votes, watched -> Counts(account, swaps.size, votes.size, watched.size) }
                .collect { counts ->
                    _state.update {
                        it.copy(
                            account = counts.account,
                            swapsRecorded = counts.swaps,
                            votesCast = counts.votes,
                            stocksWatched = counts.watched,
                        )
                    }
                }
        }
    }

    private data class Counts(val account: WalletAccount?, val swaps: Int, val votes: Int, val watched: Int)

    /**
     * Authorize only; the wallet session is shared, so Portfolio and Pro read the same account.
     * A phone with no wallet app answers at once, and says so under the action tapped, in the
     * same sentence the swap, vote and pay sheets use; a cancel is the reader's own answer and
     * says nothing.
     */
    fun connect(place: ConnectPlace = ConnectPlace.WALLET) {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            when (wallet.connect()) {
                is WalletOutcome.NoWallet -> _state.update { it.copy(noWalletAt = place) }
                is WalletOutcome.Success -> _state.update { it.copy(noWalletAt = null) }
                is WalletOutcome.Cancelled, is WalletOutcome.Error -> Unit
            }
        }
    }

    private var connectJob: Job? = null

    fun disconnect() {
        viewModelScope.launch { wallet.disconnect() }
    }

    /**
     * Notifications can change while the app is in the background; re-read on resume, the same
     * rule [com.plainticker.mobile.ui.watchlist.WatchlistViewModel.notificationsChanged] keeps.
     */
    fun notificationsChanged() {
        _state.update { it.copy(notificationsOn = notifier.enabled()) }
    }
}
