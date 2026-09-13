package com.plainticker.mobile.prefs

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Tickers the user watches. The set is small, so it lives in memory and mirrors to storage. */
interface WatchlistStore {
    val tickers: StateFlow<Set<String>>
    fun add(ticker: String)
    fun remove(ticker: String)
    fun toggle(ticker: String) {
        if (ticker.trim().uppercase() in tickers.value) remove(ticker) else add(ticker)
    }
}

class SharedPrefsWatchlistStore(private val prefs: SharedPreferences) : WatchlistStore {
    private val _tickers = MutableStateFlow(prefs.getStringSet(KEY_WATCHLIST, null)?.toSet() ?: emptySet())
    override val tickers: StateFlow<Set<String>> = _tickers.asStateFlow()

    override fun add(ticker: String) = mutate { it + normalize(ticker) }

    override fun remove(ticker: String) = mutate { it - normalize(ticker) }

    private fun mutate(change: (Set<String>) -> Set<String>) {
        _tickers.update(change)
        // A fresh HashSet: SharedPreferences must never be handed the instance it returned.
        prefs.edit().putStringSet(KEY_WATCHLIST, HashSet(_tickers.value)).apply()
    }

    private fun normalize(ticker: String) = ticker.trim().uppercase()

    companion object {
        const val KEY_WATCHLIST = "watchlist"
    }
}
