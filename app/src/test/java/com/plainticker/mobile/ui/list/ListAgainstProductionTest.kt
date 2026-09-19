package com.plainticker.mobile.ui.list

import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.data.snapshot.ListSnapshot
import com.plainticker.mobile.prefs.InMemoryWatchlistStore
import com.plainticker.mobile.watchlist.InMemoryDigestStore
import com.plainticker.mobile.repo.AssetSource
import com.plainticker.mobile.repo.BundledSnapshotRepository
import com.plainticker.mobile.repo.FakeCatalogRepository
import com.plainticker.mobile.repo.FakeNextUpRepository
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeSummaryRepository
import com.plainticker.mobile.repo.SnapshotRepository
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.ui.Fmt
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
 * capture of production (179 classified rows, each carrying its sector since task A1's follow-up,
 * and 928 Solana xStocks, captured 2026-09-19), so running the ViewModel over it exercises the
 * join at production size and shape.
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
        FakeNextUpRepository(),
        InMemoryWatchlistStore(),
        InMemoryDigestStore(),
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

    /**
     * The bundled snapshot now carries `sector` (task A1 follow-up, capture-list-snapshot.mjs),
     * so chaptering is correct from the very first frame rather than falling back to one "No
     * sector" chapter until the network answers. Run over the real capture rather than hand-built
     * rows, the way the rest of this file exercises the join at production size and shape.
     */
    @Test
    fun `the bundled snapshot itself chapters correctly, before any network answer`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val chapters = vm.state.value.analyzedChapters

        assertTrue("more than one chapter, or this is not chaptering", chapters.size > 1)
        // Alphabetical among the named sectors; a trailing chapter, if any, is the null one.
        val named = chapters.mapNotNull { it.sector }
        assertEquals(named.sorted(), named)
        assertTrue("at most the trailing chapter has no sector", chapters.dropLast(1).all { it.sector != null })

        // No row is lost or duplicated by grouping, and every row sits under its own sector.
        assertEquals(vm.state.value.analyzed.map { it.ticker }.toSet(), chapters.flatMap { it.rows }.map { it.ticker }.toSet())
        chapters.forEach { chapter -> assertTrue(chapter.rows.all { it.sector == chapter.sector }) }

        // The capture taken for this task classified every row, so there is no trailing "no
        // sector" chapter today; the path is still exercised by SectorChaptersTest's synthetic
        // rows, since a future capture can carry a row /summary sent no sector for.
        assertTrue("today's capture has a sector for every analyzed row", chapters.none { it.sector == null })
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
