package com.plainticker.mobile.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The List's analyzed section chaptered by sector (task A1, docs/plan-monetisation-2026-09-19.md
 * section 1.5): "a heading per sector with its count, sectors ordered by count, rows by composite
 * within". Asserted on [ListRow]/[ListUiState] the way [NextUpModelTest] asserts the strip, so
 * every rule here is a JVM test and not a device walk.
 */
class SectorChaptersTest {

    private fun row(ticker: String, sector: String?, composite: Double) = ListRow(
        ticker = ticker,
        symbol = "${ticker}x",
        mint = "${ticker}Mint".padEnd(44, '1'),
        company = "$ticker Inc.",
        composite = composite,
        state = RowState.FAIR,
        stale = false,
        ageDays = 1,
        priceUsd = null,
        referencePriceUsd = null,
        poolUsd = null,
        analyzed = true,
        sector = sector,
    )

    private fun state(rows: List<ListRow>) = ListUiState(analyzed = rows)

    @Test
    fun `an empty analyzed section is no chapters at all`() {
        assertTrue(state(emptyList()).analyzedChapters.isEmpty())
    }

    @Test
    fun `rows are grouped by sector, and each chapter keeps the list's own order`() {
        // Already in the join's own order, composite descending, the way ListViewModel sorts it:
        // grouping must not re-sort a row, only gather it.
        val rows = listOf(
            row("NVDA", "Information Technology", 90.0),
            row("JPM", "Financials", 85.0),
            row("AAPL", "Information Technology", 80.0),
            row("BAC", "Financials", 70.0),
            row("MSFT", "Information Technology", 60.0),
        )
        val chapters = state(rows).analyzedChapters

        // Three tickers to Information Technology's two, so it leads.
        assertEquals(listOf("Information Technology", "Financials"), chapters.map { it.sector })
        assertEquals(listOf(3, 2), chapters.map { it.rows.size })

        // Within a chapter the rows are exactly the order they arrived in: composite descending.
        assertEquals(listOf("NVDA", "AAPL", "MSFT"), chapters[0].rows.map { it.ticker })
        assertEquals(listOf("JPM", "BAC"), chapters[1].rows.map { it.ticker })
    }

    @Test
    fun `a tie in count is broken by the sector's own name, not by arrival order`() {
        val rows = listOf(
            row("XOM", "Energy", 50.0),
            row("JPM", "Financials", 90.0),
            row("CVX", "Energy", 40.0),
            row("BAC", "Financials", 30.0),
        )
        val chapters = state(rows).analyzedChapters
        assertEquals(listOf(2, 2), chapters.map { it.rows.size })
        assertEquals("Energy sorts before Financials, whichever sector was served first", listOf("Energy", "Financials"), chapters.map { it.sector })
    }

    @Test
    fun `a row without a sector is not dropped, it trails in one chapter of its own`() {
        val rows = listOf(
            row("NVDA", "Information Technology", 90.0),
            row("XYZ", null, 85.0),
            row("AAPL", "Information Technology", 80.0),
            row("ABC", null, 20.0),
        )
        val chapters = state(rows).analyzedChapters

        assertEquals("the sector-less chapter is last", listOf("Information Technology", null), chapters.map { it.sector })
        assertEquals(listOf("XYZ", "ABC"), chapters.last().rows.map { it.ticker })
        assertNull(chapters.last().sector)

        // No row disappears: every ticker put in is a ticker that comes out.
        assertEquals(rows.map { it.ticker }.toSet(), chapters.flatMap { it.rows }.map { it.ticker }.toSet())
    }

    @Test
    fun `the sector-less chapter trails even when it outnumbers every named sector`() {
        val rows = listOf(
            row("A", null, 99.0),
            row("B", null, 90.0),
            row("C", null, 80.0),
            row("JPM", "Financials", 50.0),
        )
        val chapters = state(rows).analyzedChapters
        assertEquals(listOf("Financials", null), chapters.map { it.sector })
        assertEquals(3, chapters.last().rows.size)
    }

    @Test
    fun `one sector is one chapter, whatever the composite spread within it`() {
        val rows = listOf(row("A", "Utilities", 99.0), row("B", "Utilities", 1.0))
        val chapters = state(rows).analyzedChapters
        assertEquals(1, chapters.size)
        assertEquals(listOf("A", "B"), chapters.single().rows.map { it.ticker })
    }
}
