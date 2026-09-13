package com.plainticker.mobile.ui.watchlist

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.Forward
import com.plainticker.mobile.data.plainticker.ForwardRaw
import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.FakeDigestNotifier
import com.plainticker.mobile.watchlist.InMemoryDigestStore
import com.plainticker.mobile.watchlist.WatchlistFacts
import com.plainticker.mobile.watchlist.WatchlistScheduler
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Watchlist screen's states: nothing watched, a row per watched ticker, a source that did not
 * answer, and the digest the check stored arriving while the screen is open.
 */
class WatchlistViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val summaries = FakeSummaryRepository()
    private val catalog = FakeCatalogRepository()
    private val prices = FakePriceRepository()
    private val digests = InMemoryDigestStore()
    private val notifier = FakeDigestNotifier()
    private val scheduler = RecordingScheduler()
    private val clock = Clock { NOW }

    private class RecordingScheduler : WatchlistScheduler {
        val scheduled = mutableListOf<Duration>()
        var cancels = 0
            private set
        var runs = 0
            private set

        override fun scheduleDaily(initialDelay: Duration) {
            scheduled += initialDelay
        }

        override fun cancel() {
            cancels++
        }

        override fun runNow() {
            runs++
        }
    }

    private fun viewModel(watching: Set<String> = emptySet()) = WatchlistViewModel(
        watchlist = InMemoryWatchlistStore(watching),
        facts = WatchlistFacts(summaries, catalog, prices),
        digests = digests,
        notifier = notifier,
        scheduler = scheduler,
        clock = clock,
    )

    private fun serving(vararg tickers: String) {
        summaries.summaryResult = Result.success(
            SummaryResponse(
                schema = "v1.1",
                generatedAt = "2026-09-13T08:00:00.000Z",
                rows = tickers.map { SummaryRow(ticker = it, company = "$it Inc.") },
            ),
        )
        catalog.assets = Result.success(tickers.map { xStock("${it}x", it, "mint-$it") })
        summaries.analyses = tickers.associateWith {
            Result.success(
                AnalysisPayload(ticker = it, forward = Forward(ForwardRaw(nextEarningsDate = "2026-10-28"))),
            )
        }
    }

    @Test
    fun `nothing watched is the empty state and asks nothing of the network`() = runTest {
        val vm = viewModel()

        vm.state.test {
            val state = awaitItem()
            assertTrue(state.isEmpty)
            assertFalse(state.isCold)
            assertNull(state.banner)
            assertEquals(0, summaries.summaryCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a watched ticker becomes a row with its report date and its premium`() = runTest {
        serving("AAPL")
        prices.result = Result.success(mapOf("mint-AAPL" to price(usd = 232.54, reference = 232.52)))
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            val state = awaitUntil { it.rows.isNotEmpty() }
            val row = state.rows.single()
            assertEquals("AAPL", row.ticker)
            assertEquals("AAPLx", row.symbol)
            assertEquals(LocalDate.of(2026, 10, 28), row.nextReport)
            assertEquals(NOW, state.nowMillis)
            assertFalse(state.isEmpty)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unwatching takes the row off the screen`() = runTest {
        serving("AAPL", "TSLA")
        val vm = viewModel(setOf("AAPL", "TSLA"))

        vm.state.test {
            awaitUntil { it.rows.size == 2 }
            vm.unwatch("AAPL")
            val state = awaitUntil { it.rows.size == 1 }
            assertEquals("TSLA", state.rows.single().ticker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unwatching the last one returns the empty state`() = runTest {
        serving("AAPL")
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            awaitUntil { it.rows.isNotEmpty() }
            vm.unwatch("AAPL")
            val state = awaitUntil { it.isEmpty && it.rows.isEmpty() }
            assertTrue(state.rows.isEmpty())
            assertNull("an empty list raises no banner", state.banner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an analysis that did not answer raises the first banner tier and keeps the row`() = runTest {
        summaries.summaryResult = Result.failure(ApiException(503, "summary", "unavailable", null))
        catalog.assets = Result.success(listOf(xStock("AAPLx", "AAPL", "mint-AAPL")))
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            val state = awaitUntil { it.banner != null }
            assertEquals(WatchlistBanner.AnalysisUnavailable, state.banner)
            assertEquals("AAPL", state.rows.single().ticker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the digest the check stores reaches the open screen`() = runTest {
        serving("AAPL")
        val vm = viewModel(setOf("AAPL"))

        vm.state.test {
            awaitUntil { it.rows.isNotEmpty() }
            digests.save(DigestRecord(text = "1 stock watched. AAPLx reports in 45 days.", producedAtMillis = NOW))
            val state = awaitUntil { it.digest.text != null }
            assertEquals("1 stock watched. AAPLx reports in 45 days.", state.digest.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the notification setting is read at the start and again when it may have changed`() = runTest {
        notifier.on = false
        val vm = viewModel()

        vm.state.test {
            assertFalse(awaitItem().notificationsOn)
            notifier.on = true
            vm.notificationsChanged()
            assertTrue(awaitUntil { it.notificationsOn }.notificationsOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the debug entry point fires the check through the scheduler`() = runTest {
        val vm = viewModel()

        vm.runCheckNow()

        assertEquals(1, scheduler.runs)
    }

    private companion object {
        const val NOW = 1_789_257_600_000L
    }
}
