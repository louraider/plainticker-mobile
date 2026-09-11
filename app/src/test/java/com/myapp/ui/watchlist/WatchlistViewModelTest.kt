package com.myapp.ui.watchlist

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.awaitUntil
import com.myapp.prefs.InMemoryWatchlistStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WatchlistViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `mirrors the store, sorted, and toggles`() = runTest {
        val store = InMemoryWatchlistStore(setOf("TSLA"))
        val vm = WatchlistViewModel(store)

        vm.state.test {
            assertEquals(listOf("TSLA"), awaitItem().tickers)

            vm.toggle("aapl")
            assertEquals(listOf("AAPL", "TSLA"), awaitUntil { it.tickers.size == 2 }.tickers)

            vm.toggle("TSLA")
            assertEquals(listOf("AAPL"), awaitUntil { it.tickers.size == 1 }.tickers)

            vm.remove("AAPL")
            assertTrue(awaitUntil { it.tickers.isEmpty() }.isEmpty)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
