package com.plainticker.mobile.ui.stocks

import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.list.ListRow
import com.plainticker.mobile.ui.list.RowState
import com.plainticker.mobile.ui.list.SectorChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wrapping filter row and the chapter jump index, both kept as plain functions
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

    // ---- sectorChipRow ---------------------------------------------------------------------

    @Test
    fun `the first six sectors show, the rest fall into overflow`() {
        val sectors = (1..8).map { "Sector $it" }
        val chips = sectorChipRow(sectors)
        assertEquals(sectors.take(6), chips.shown)
        assertEquals(sectors.drop(6), chips.overflow)
        assertTrue(chips.hasOverflow)
    }

    @Test
    fun `eight or fewer sectors never overflow`() {
        val sectors = (1..6).map { "Sector $it" }
        val chips = sectorChipRow(sectors)
        assertEquals(sectors, chips.shown)
        assertFalse(chips.hasOverflow)
    }

    // ---- jumpAbbreviationRes -----------------------------------------------------------------

    @Test
    fun `every GICS sector Stocks chapters by has a jump abbreviation`() {
        val sectors = listOf(
            "Consumer Discretionary", "Information Technology", "Communication Services",
            "Consumer Staples", "Health Care", "Industrials", "Real Estate",
            "Financials", "Materials", "Utilities", "Energy",
        )
        sectors.forEach { assertTrue("$it has no jump abbreviation", jumpAbbreviationRes(it) != null) }
        // Eleven distinct resources: no two sectors read the same short code.
        assertEquals(11, sectors.mapNotNull { jumpAbbreviationRes(it) }.toSet().size)
    }

    @Test
    fun `the trailing no-sector chapter reads its own dedicated label, not the sector fallback`() {
        assertEquals(com.plainticker.mobile.R.string.stocks_jump_no_sector, jumpAbbreviationRes(null))
    }

    @Test
    fun `a sector outside the known eleven falls back to the caller reading the sector name itself`() {
        assertNull(jumpAbbreviationRes("A Newly Split Sector"))
    }

    /**
     * The measurement behind [com.plainticker.mobile.ui.list.ListScreen]'s `JumpIndexWidth`
     * (28dp): every authored abbreviation the jump index can show is short enough to sit in one
     * narrow column without wrapping or clipping, the same "measure the longest and pin it" rule
     * this task's brief applies to the row's own longest sector and company name
     * ([com.plainticker.mobile.ui.components.AmberSectionHeadTest],
     * [com.plainticker.mobile.ui.components.AmberTickerRowTest]), read here off the eleven jump
     * strings this pass itself authored rather than off catalog data.
     */
    @Test
    fun `every jump index label is short enough for the rail's fixed width`() {
        val names = listOf(
            "stocks_jump_communication_services", "stocks_jump_consumer_discretionary",
            "stocks_jump_consumer_staples", "stocks_jump_energy", "stocks_jump_financials",
            "stocks_jump_health_care", "stocks_jump_industrials", "stocks_jump_information_technology",
            "stocks_jump_materials", "stocks_jump_real_estate", "stocks_jump_utilities", "stocks_jump_no_sector",
        )
        names.forEach { name ->
            val label = ShippedCopy.strings.getValue(name)
            assertTrue("$name (\"$label\") is longer than the rail was sized for", label.length <= 3)
        }
    }

    // ---- chapterStartIndices and activeChapterAt ---------------------------------------------

    private fun chapter(sector: String?, rowCount: Int) =
        SectorChapter(sector, List(rowCount) { row("T$it", sector = sector) })

    @Test
    fun `each chapter's start is one stickyHeader item past the previous chapter's last row`() {
        val chapters = listOf(chapter("Energy", 3), chapter("Financials", 1), chapter(null, 2))
        val starts = chapterStartIndices(chapters, itemsBeforeChapters = 2)
        // 2 fixed items, then Energy's header at 2, its 3 rows at 3..5, Financials' header at 6,
        // its 1 row at 7, the trailing chapter's header at 8, its 2 rows at 9..10.
        assertEquals(mapOf("Energy" to 2, "Financials" to 6, null to 8), starts)
    }

    @Test
    fun `an empty chapter list has no start at all`() {
        assertTrue(chapterStartIndices(emptyList(), itemsBeforeChapters = 2).isEmpty())
    }

    @Test
    fun `activeChapterAt finds the latest chapter reached, never a chapter still ahead`() {
        val starts = mapOf("Energy" to 2, "Financials" to 6, null to 8)
        assertNull("still on the chrome above the first chapter", activeChapterAt(0, starts))
        assertEquals(ActiveChapter("Energy"), activeChapterAt(2, starts))
        assertEquals(ActiveChapter("Energy"), activeChapterAt(5, starts))
        assertEquals(ActiveChapter("Financials"), activeChapterAt(6, starts))
        // The trailing chapter's own sector is null; this must read as a definite hit on it, not
        // as the same null the chrome-above-the-first-chapter case returns.
        assertEquals(ActiveChapter(null), activeChapterAt(9, starts))
        assertTrue(activeChapterAt(9, starts) != null)
    }
}
