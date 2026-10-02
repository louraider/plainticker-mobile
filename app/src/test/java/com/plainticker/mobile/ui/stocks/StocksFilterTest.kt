package com.plainticker.mobile.ui.stocks

import com.plainticker.mobile.ui.list.ListRow
import com.plainticker.mobile.ui.list.RowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter row's rules, kept as plain functions
 * ([StocksFilter.kt]'s own doc comment says why) so every rule here runs on a plain JVM, the way
 * [com.plainticker.mobile.ui.list.SectorChaptersTest] already proves `chapteredBySector()` without
 * a device.
 */
class StocksFilterTest {

    private fun row(
        ticker: String,
        sector: String? = "Information Technology",
        priceUsd: Double? = 100.0,
        referencePriceUsd: Double? = 100.0,
        poolUsd: Double? = 250_000.0,
    ) = ListRow(
        ticker = ticker,
        symbol = "${ticker}x",
        mint = ticker,
        company = "$ticker Inc.",
        composite = 50.0,
        state = RowState.FAIR,
        stale = false,
        ageDays = 1,
        priceUsd = priceUsd,
        referencePriceUsd = referencePriceUsd,
        poolUsd = poolUsd,
        analyzed = true,
        sector = sector,
    )

    // ---- matchesStocksFilter --------------------------------------------------------------

    @Test
    fun `a null filter passes every row`() {
        val r = row("AAPL")
        assertTrue(r.matchesStocksFilter(null, emptySet()))
        assertTrue(row("AAPL", poolUsd = 1.0).matchesStocksFilter(null, emptySet()))
    }

    @Test
    fun `Tracked matches only a pool at or above the liquidity floor`() {
        val deep = row("NVDA", poolUsd = 250_000.0)
        val thin = row("APP", poolUsd = 34.0)
        val unknown = row("UNH", poolUsd = null)
        assertTrue(deep.matchesStocksFilter(StocksFilter.Tracked, emptySet()))
        assertFalse(thin.matchesStocksFilter(StocksFilter.Tracked, emptySet()))
        assertFalse(unknown.matchesStocksFilter(StocksFilter.Tracked, emptySet()))
    }

    @Test
    fun `Watched matches by the row's own ticker against the watched set`() {
        val watched = setOf("AAPL", "TSLA")
        assertTrue(row("AAPL").matchesStocksFilter(StocksFilter.Watched, watched))
        assertFalse(row("NVDA").matchesStocksFilter(StocksFilter.Watched, watched))
        assertFalse(row("AAPL").matchesStocksFilter(StocksFilter.Watched, emptySet()))
    }

    @Test
    fun `a Sector filter matches the row's own sector by exact name, including the trailing null chapter`() {
        val sector = StocksFilter.Sector("Health Care")
        assertTrue(row("UNH", sector = "Health Care").matchesStocksFilter(sector, emptySet()))
        assertFalse(row("AAPL", sector = "Information Technology").matchesStocksFilter(sector, emptySet()))
        // The trailing chapter's rows carry a null sector; no Sector value can name it, on purpose
        // (StocksFilter.kt: the filter chips only ever offer a real sector, never "no sector").
        assertFalse(row("XOM", sector = null).matchesStocksFilter(sector, emptySet()))
    }

    // ---- toSaveKey / stocksFilterFromSaveKey (rememberSaveable's own String? support) --------

    @Test
    fun `every filter round-trips through its save key`() {
        val filters = listOf(null, StocksFilter.Tracked, StocksFilter.Watched, StocksFilter.Sector("Energy"))
        filters.forEach { assertEquals(it, stocksFilterFromSaveKey(it.toSaveKey())) }
    }

    @Test
    fun `a sector named exactly Tracked or Watched still reads back as that sector, not the fixed chip`() {
        // The fixed keys are NUL-prefixed precisely so a real sector name, however it reads,
        // can never collide with them.
        assertEquals(StocksFilter.Sector("Tracked"), stocksFilterFromSaveKey("Tracked"))
        assertEquals(StocksFilter.Sector("Watched"), stocksFilterFromSaveKey("Watched"))
    }

    // ---- showsDeepPoolChip (mock judges' round 2: hide "Deep pool" under five) ------------------

    @Test
    fun `the Deep pool chip is hidden while fewer than five rows have a deep pool`() {
        assertEquals(5, DEEP_POOL_CHIP_MIN)
        (0..4).forEach { assertFalse("Deep pool $it must not be drawn", showsDeepPoolChip(it, active = null)) }
        assertTrue(showsDeepPoolChip(5, active = null))
        assertTrue(showsDeepPoolChip(22, active = StocksFilter.Watched))
    }

    @Test
    fun `the Deep pool chip stays while it is the active filter, so it can always be cleared`() {
        assertTrue(showsDeepPoolChip(0, active = StocksFilter.Tracked))
        assertTrue(showsDeepPoolChip(3, active = StocksFilter.Tracked))
        assertFalse(showsDeepPoolChip(3, active = StocksFilter.Sector("Energy")))
    }
}
