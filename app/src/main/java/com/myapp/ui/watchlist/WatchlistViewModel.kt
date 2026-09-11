package com.myapp.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.prefs.WatchlistStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WatchlistUiState(
    val tickers: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = tickers.isEmpty()
}

/** The watched tickers, sorted. Events, digests and notifications arrive with T12. */
class WatchlistViewModel(
    private val store: WatchlistStore,
) : ViewModel() {

    val state: StateFlow<WatchlistUiState> = store.tickers
        .map { WatchlistUiState(it.sorted()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WatchlistUiState(store.tickers.value.sorted()))

    fun toggle(ticker: String) = store.toggle(ticker)

    fun remove(ticker: String) = store.remove(ticker)
}
