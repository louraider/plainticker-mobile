package com.myapp.ui.list

import com.myapp.MainDispatcherRule
import com.myapp.data.snapshot.ListSnapshot
import com.myapp.prefs.InMemoryWatchlistStore
import com.myapp.repo.AssetSource
import com.myapp.repo.BundledSnapshotRepository
import com.myapp.repo.FakeCatalogRepository
import com.myapp.repo.FakePriceRepository
import com.myapp.repo.FakeSummaryRepository
import com.myapp.repo.SnapshotRepository
import com.myapp.repo.price
import com.myapp.ui.Fmt
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The List against the real payloads, not against hand-built rows: the bundled snapshot is a
 * capture of production (179 classified rows and 832 Solana xStocks on 2026-09-12), so running
 * the ViewModel over it exercises the join at production size and shape.
 *
 * This is what the first device pass had to catch by eye. It cannot replace a device pass (no
 * layout, no Jupiter, no scrolling), but the four data rules it broke are checked here against
 * the same data the device was showing.
 */
class ListAgainstProductionTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    // The read stays on the test dispatcher: virtual time then covers the whole refresh.
    private val snapshots: SnapshotRepository = BundledSnapshotRepository(
        assets = AssetSource { path -> File(module, "src/main/assets/$path").takeIf { it.isFile }?.inputStream() },
        readContext = EmptyCoroutineContext,
    )

    private val cyrillic = Regex("[\\u0400-\\u04FF]")

    /** Both live sources down, so the rows come from the bundled capture of production. */
    private fun viewModel(prices: FakePriceRepository = FakePriceRepository()) = ListViewModel(
        FakeSummaryRepository(Result.failure(IOException("offline"))),
        FakeCatalogRepository(Result.failure(IOException("offline"))),
        prices,
        snapshots,
        InMemoryWatchlistStore(),
    )

    private suspend fun snapshot(): ListSnapshot = checkNotNull(snapshots.listSnapshot())

    @Test
    fun `the production join puts every row in the right section`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value
        val snapshot = snapshot()

        val tokens = snapshot.assets.map { it.ticker.uppercase() }.toSet()
        val classified = snapshot.rows.map { it.ticker.uppercase() }.toSet()
        val expectedAnalyzed = snapshot.rows.count { it.ticker.uppercase() in tokens }
        val expectedWithout = snapshot.assets.count { it.ticker.uppercase() !in classified }

        assertEquals("analyzed rows", expectedAnalyzed, state.analyzed.size)
        assertEquals("price-only rows", expectedWithout, state.withoutAnalysis.size)
        assertTrue("the capture has rows with no xStock to leave out", expectedAnalyzed < snapshot.rows.size)

        // Every analyzed row is a token the user can actually open, and BKNG and its kind are
        // in neither section (the device pass found BKNG sitting inside Analyzed).
        assertTrue(state.analyzed.all { it.symbol != null && it.mint != null })
        assertTrue(state.analyzed.none { it.ticker.equals("BKNG", ignoreCase = true) })
        assertTrue(state.withoutAnalysis.none { it.ticker.equals("BKNG", ignoreCase = true) })
        assertTrue("no token is in both sections", state.analyzed.map { it.ticker }.intersect(state.withoutAnalysis.map { it.ticker }.toSet()).isEmpty())

        // Composite descending, the way the leaderboard reads.
        val composites = state.analyzed.mapNotNull { it.composite }
        assertEquals(composites.sortedDescending(), composites)
    }

    @Test
    fun `every string a production row would render is clean and formatted`() = runTest {
        val vm = viewModel(FakePriceRepository(Result.success(mapOf())))
        advanceUntilIdle()
        val state = vm.state.value
        val rows = state.analyzed + state.withoutAnalysis
        assertTrue(rows.size > 500)

        rows.forEach { row ->
            assertFalse("Cyrillic in $row", cyrillic.containsMatchIn(row.toString()))

            // What the screen draws right: an integer for a composite, a price for a token.
            row.composite?.let {
                val drawn = Fmt.decimal(it, decimals = 0)
                assertFalse("$drawn is not an integer", drawn.contains('.'))
                assertTrue("composite off scale: $drawn", drawn.toInt() in 0..100)
            }
            row.ageForMeta?.let { assertTrue(Fmt.daysOld(it).endsWith(" d old")) }
            // An analysis from today prints no age, the way Detail does not print one under 24 h.
            if (row.ageDays == 0) assertNull(row.ageForMeta)
        }
    }

    @Test
    fun `the visible window is priced first, then the rest, inside the budget`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(prices)
        advanceUntilIdle()

        assertEquals("one window call and one for the rest", 2, prices.requested.size)
        assertEquals(ListViewModel.FIRST_SCREENFUL, prices.requested[0].size)
        assertEquals(ListViewModel.PRICE_BUDGET, prices.requested[1].size)
        assertEquals("the window is the head of the run", prices.requested[0], prices.requested[1].take(ListViewModel.FIRST_SCREENFUL))

        // The analyzed rows are priced before any row without analysis.
        val analyzedMints = vm.state.value.analyzed.mapNotNull { it.mint }
        assertTrue(prices.requested[1].take(analyzedMints.size.coerceAtMost(ListViewModel.PRICE_BUDGET)).all { it in analyzedMints })
    }

    @Test
    fun `a production sized refresh with prices keeps the premium on the rows that got one`() = runTest {
        val prices = FakePriceRepository()
        val vm = viewModel(prices)
        advanceUntilIdle()

        val priced = vm.state.value.analyzed.take(5).mapNotNull { it.mint }
        // The same ViewModel is refreshed, so this is the state transition and not a second
        // cold start wearing its name.
        prices.result = Result.success(priced.associateWith { price(100.0, reference = 99.5) })
        prices.unfetched = vm.state.value.analyzed.drop(5).mapNotNull { it.mint }.toSet()
        val callsBefore = prices.requested.size

        vm.refresh()
        advanceUntilIdle()
        assertTrue("the refresh asked for prices again", prices.requested.size > callsBefore)

        val state = vm.state.value
        assertTrue("some prices are missing", state.pricesPartial)
        assertFalse("but not all of them", state.pricesUnavailable)
        // The offline tier outranks the device tier: these rows came from the snapshot, so that
        // is what the one slot says, and the partial prices stay a flag until it clears.
        assertTrue(state.banner is ListBanner.Snapshot)
        assertEquals(5, state.analyzed.count { it.priceUsd != null })
        state.analyzed.filter { it.priceUsd != null }.forEach {
            assertEquals("+0.50%", Fmt.percent(it.premiumPct!!))
        }
        assertTrue("the rest keep their analysis", state.analyzed.all { it.composite != null })
    }
}
