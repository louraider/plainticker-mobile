package com.plainticker.mobile.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** QA of 1.3.22: an exact ticker ranks first, then a ticker prefix, then a name. */
class SearchRankTest {

    private fun row(ticker: String, company: String, analyzed: Boolean = true) = ListRow(
        ticker = ticker,
        symbol = "${ticker}x",
        mint = null,
        company = company,
        composite = null,
        state = null,
        stale = false,
        ageDays = null,
        priceUsd = null,
        referencePriceUsd = null,
        poolUsd = null,
        analyzed = analyzed,
    )

    @Test
    fun `the tiers`() {
        assertEquals(SEARCH_EXACT, searchTier("MA", "MA", "MAx", "Mastercard Incorporated"))
        assertEquals("the token symbol is exact too", SEARCH_EXACT, searchTier("max", "MA", "MAx", "Mastercard"))
        assertEquals("case and spaces do not matter", SEARCH_EXACT, searchTier("  v ", "V", "Vx", "Visa Inc."))
        assertEquals(SEARCH_PREFIX, searchTier("MA", "MARA", "MARAx", "MARA Holdings"))
        assertEquals(SEARCH_PREFIX, searchTier("V", "VZ", "VZx", "Verizon"))
        assertEquals("inside a ticker is a name-tier match", SEARCH_NAME, searchTier("V", "NVDA", "NVDAx", "NVIDIA"))
        assertEquals(SEARCH_NAME, searchTier("ma", "GS", "GSx", "The Goldman Sachs Group, Inc."))
        assertNull(searchTier("zzz", "GS", "GSx", "The Goldman Sachs Group, Inc."))
        assertNull("no symbol and no company, and the ticker does not hold it", searchTier("MA", "GS", null, null))
    }

    @Test
    fun `MA puts MAx first, across both sets, and keeps list order inside a tier`() {
        val analyzed = listOf(
            row("TSM", "Taiwan Semiconductor Manufacturing Company Limited"),
            row("GS", "The Goldman Sachs Group, Inc."),
            row("MA", "Mastercard Incorporated"),
        )
        val uncovered = listOf(row("AMAT", "Applied Materials", analyzed = false), row("MARA", "MARA Holdings", analyzed = false))
        assertEquals(
            listOf("MA", "MARA", "TSM", "GS", "AMAT"),
            searchResults(analyzed, uncovered, "MA").map { it.ticker },
        )
    }

    @Test
    fun `an exact uncovered ticker leads analyzed name matches`() {
        val analyzed = listOf(row("NVDA", "NVIDIA Corporation"))
        val uncovered = listOf(row("V", "Visa Inc.", analyzed = false))
        assertEquals(listOf("V", "NVDA"), searchResults(analyzed, uncovered, "v").map { it.ticker })
    }

    @Test
    fun `a blank query keeps both sets as they were`() {
        val analyzed = listOf(row("GS", "Goldman"), row("MA", "Mastercard"))
        val uncovered = listOf(row("MARA", "MARA", analyzed = false))
        assertEquals(listOf("GS", "MA", "MARA"), searchResults(analyzed, uncovered, " ").map { it.ticker })
    }
}
