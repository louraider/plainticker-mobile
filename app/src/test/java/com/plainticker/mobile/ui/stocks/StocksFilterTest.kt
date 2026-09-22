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
     * Necessary, not sufficient: every authored abbreviation is three characters or fewer. A
     * previous pass treated this as the whole justification for `JumpIndexWidth`, but three
     * characters of Bricolage Grotesque at 1.3x font scale do not all fit a 28dp column (the
     * device found "Com" rendering as "Co", silently, with no ellipsis) — a character count is
     * not a rendered width. The real guarantee is the fontTools-measured width test below (`the
     * widest jump abbreviation, measured against the real font, clears the rail's width with
     * margin at both scales`); this test stays only as the cheap, always-true precondition that
     * measurement assumes.
     */
    @Test
    fun `every jump index label is three characters or fewer, the precondition the width test below assumes`() {
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

    /**
     * The real guarantee the test above cannot give: every jump abbreviation's own rendered width,
     * measured against `res/font/bricolage_grotesque.ttf` itself with fontTools (2026-09-22),
     * instantiated at the exact variation coordinates [com.plainticker.mobile.ui.theme.AmberType.meta]
     * builds ([com.plainticker.mobile.ui.list.ListScreen]'s `StocksJumpIndex` draws every label in
     * that style) — `wght` 400, `wdth` 100, `opsz` 12 — summing each glyph's own `hmtx` advance
     * width and scaling by the 12sp point size, the same method
     * [com.plainticker.mobile.ui.components.AmberTickerRowTest] and
     * [com.plainticker.mobile.ui.you.YouModelTest] already use and that this file's own numbers
     * were cross-checked against (`outfit_semibold.ttf` "Vote" at 14sp reproduces
     * [com.plainticker.mobile.ui.components.AmberTickerRowTest]'s own pinned 30.856dp exactly).
     *
     * At 1.3x, sp text scales by the raw factor and the box's own dp width does not (the same
     * conservative assumption [com.plainticker.mobile.ui.components.AmberTickerRowTest]'s
     * trailing-action budgets use), so 1.0x widths below are multiplied by 1.3 rather than
     * re-instantiated at a different `opsz`: [com.plainticker.mobile.ui.theme.Type.bricolage] locks
     * `opsz` to the style's own fixed point size regardless of the reader's font scale setting, so
     * the real device never re-optically-sizes this glyph at 1.3x either.
     *
     * "Com" is the widest of the twelve at both scales. `JumpIndexWidth` (48dp, widened from 28dp
     * for the touch-target floor, [com.plainticker.mobile.ui.list.ListScreen]'s own doc comment on
     * `StocksJumpIndex`) clears it with 22.008dp to spare at 1.0x and 14.210dp at 1.3x: comfortable
     * margin at both, not a bare pass.
     */
    @Test
    fun `the widest jump abbreviation, measured against the real font, clears the rail's width with margin at both scales`() {
        val railWidthDp = 48.0
        // label to (1.0x width, 1.3x width), fontTools against bricolage_grotesque.ttf at
        // wght=400/wdth=100/opsz=12 (AmberType.meta), 2026-09-22.
        val widths = mapOf(
            "Com" to (25.992 to 33.790),
            "Dis" to (17.652 to 22.948),
            "Sta" to (18.816 to 24.461),
            "Ene" to (21.024 to 27.331),
            "Fin" to (17.064 to 22.183),
            "Hea" to (21.924 to 28.501),
            "Ind" to (17.676 to 22.979),
            "IT" to (9.744 to 12.667),
            "Mat" to (21.924 to 28.501),
            "RE" to (14.892 to 19.360),
            "Uti" to (16.080 to 20.904),
            "No" to (16.032 to 20.842),
        )
        val (widestLabel, widestPair) = widths.entries.maxBy { it.value.second }
        assertEquals("\"Com\" is expected to be the widest jump label at 1.3x", "Com", widestLabel)

        widths.forEach { (label, pair) ->
            val (at1x, at13x) = pair
            assertTrue(
                "\"$label\" ($at1x dp) must fit the rail's own $railWidthDp dp width at 1.0x, with margin",
                at1x <= railWidthDp - 10.0,
            )
            assertTrue(
                "\"$label\" ($at13x dp) must fit the rail's own $railWidthDp dp width at 1.3x, with margin, " +
                    "or a character silently clips the way \"Com\" clipped to \"Co\" on the device",
                at13x <= railWidthDp - 10.0,
            )
        }

        // The binding case, spelled out rather than left inside the loop above.
        val (widest1x, widest13x) = widestPair
        assertEquals(22.008, railWidthDp - widest1x, 0.01)
        assertEquals(14.210, railWidthDp - widest13x, 0.01)
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
