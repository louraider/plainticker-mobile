package com.plainticker.mobile.ui.list

import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.ui.ShippedCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * What the "Next up" strip names and when it stays off the screen, asserted on [ListUiState] out
 * of the shipped strings.xml, so every rule is a JVM test and not a device walk.
 */
class NextUpModelTest {

    private fun priceOnly(ticker: String, symbol: String, company: String) = ListRow(
        ticker = ticker,
        symbol = symbol,
        mint = "${ticker}Mint".padEnd(44, '1'),
        company = company,
        composite = null,
        state = null,
        stale = false,
        ageDays = null,
        priceUsd = null,
        referencePriceUsd = null,
        poolUsd = null,
        analyzed = false,
    )

    private val uncovered = listOf(
        priceOnly("AMD", "AMDx", "Advanced Micro Devices"),
        priceOnly("ASML", "ASMLx", "ASML Holding"),
        priceOnly("NFLX", "NFLXx", "Netflix, Inc."),
        priceOnly("TSM", "TSMx", "Taiwan Semiconductor"),
        priceOnly("UBER", "UBERx", "Uber Technologies"),
    )

    /** Five leaders in the server's order, heaviest first. */
    private val leaders = listOf(
        NextUpRow("NFLX", "31209870777", 3),
        NextUpRow("TSM", "12345678901", 2),
        NextUpRow("AMD", "6719000000", 1),
        NextUpRow("UBER", "1000000000", 1),
        NextUpRow("ASML", "5", 1),
    )

    private fun state(
        nextUp: List<NextUpRow> = leaders,
        withoutAnalysis: List<ListRow> = uncovered,
        analyzed: List<ListRow> = emptyList(),
        query: String = "",
    ) = ListUiState(nextUp = nextUp, withoutAnalysis = withoutAnalysis, analyzed = analyzed, query = query)

    // ---- The figure ---------------------------------------------------------------------------

    @Test
    fun `the weight is SKR with six decimals, at most one of them kept`() {
        assertEquals("0", skrWeight(BigInteger.ZERO))
        assertEquals("1,000", skrWeight(BigInteger.valueOf(1_000_000_000L)))
        assertEquals("12,345.7", skrWeight(BigInteger.valueOf(12_345_678_901L)))
        // The measured stake of 2026-09-13, 31,209.870777 SKR, at the strip's precision.
        assertEquals("31,209.9", skrWeight(BigInteger.valueOf(31_209_870_777L)))
        // Twenty digits: the value the impossible account decodes to, which no Long carries.
        assertEquals("11,452,317,590,968.4", skrWeight(BigInteger("11452317590968446072")))
        assertEquals("a whole number keeps no trailing zero", "6,719", skrWeight(BigInteger.valueOf(6_719_000_000L)))
    }

    // ---- What the strip names ---------------------------------------------------------------------

    @Test
    fun `the strip is the top three, in the server's order, named as the list names them`() {
        val strip = state().nextUpStrip
        assertEquals(NEXT_UP_STRIP_SIZE, strip.size)
        assertEquals(listOf("NFLX", "TSM", "AMD"), strip.map { it.ticker })
        assertEquals("the token symbol, as the row under it reads", listOf("NFLXx", "TSMx", "AMDx"), strip.map { it.display })
        assertEquals(listOf("Netflix, Inc.", "Taiwan Semiconductor", "Advanced Micro Devices"), strip.map { it.company })
        assertEquals(listOf(3, 2, 1), strip.map { it.voters })
        assertEquals(BigInteger.valueOf(31_209_870_777L), strip.first().weightRaw)
    }

    @Test
    fun `the strip reads its figures out of the shipped copy`() {
        val leader = state().nextUpStrip.first()
        assertEquals("31,209.9 SKR", ShippedCopy.render(leader.weight))
        assertEquals("3 voters", ShippedCopy.render(leader.votersCopy))
        assertEquals("one reads as one", "1 voter", ShippedCopy.render(state().nextUpStrip[2].votersCopy))
    }

    // ---- When there is nothing to draw ---------------------------------------------------------

    @Test
    fun `no leaders, a search in progress, or no section to draw under is no strip`() {
        assertTrue("the call failed or answered nothing", state(nextUp = emptyList()).nextUpStrip.isEmpty())
        assertTrue("a search narrows the list, and the strip is not a search result", state(query = "nf").nextUpStrip.isEmpty())
        assertTrue("no section, nothing to lead it", state(withoutAnalysis = emptyList()).nextUpStrip.isEmpty())
    }

    @Test
    fun `a leader that has been covered since the tally is not next up any more`() {
        val covered = uncovered.first { it.ticker == "NFLX" }.copy(analyzed = true, composite = 71.0)
        val strip = state(withoutAnalysis = uncovered.filter { it.ticker != "NFLX" }, analyzed = listOf(covered)).nextUpStrip
        assertEquals(listOf("TSM", "AMD", "UBER"), strip.map { it.ticker })
    }

    @Test
    fun `a leader the catalog does not carry is not drawn, and the next one takes its place`() {
        val strip = state(withoutAnalysis = uncovered.filter { it.ticker != "TSM" }).nextUpStrip
        assertEquals(listOf("NFLX", "AMD", "UBER"), strip.map { it.ticker })
    }

    @Test
    fun `a weight that is not a number drops its row rather than printing a guess`() {
        val garbled = leaders.map { if (it.ticker == "TSM") it.copy(weight = "n/a") else it }
        val strip = state(nextUp = garbled).nextUpStrip
        assertEquals(listOf("NFLX", "AMD", "UBER"), strip.map { it.ticker })
    }

    @Test
    fun `one ticker is one leader, whatever the server sent twice`() {
        // The list is keyed by this ticker, so a second row carrying it is not a repeated row,
        // it is a LazyColumn throwing on a key it has already used.
        val doubled = listOf(
            NextUpRow("NFLX", "31209870777", 3),
            NextUpRow(" nflx ", "12345678901", 2),
            NextUpRow("TSM", "6719000000", 1),
        )
        val strip = state(nextUp = doubled).nextUpStrip
        assertEquals(listOf("NFLX", "TSM"), strip.map { it.ticker })
        assertEquals("the row the server served first wins, which is the heavier", "31,209.9 SKR", ShippedCopy.render(strip.first().weight))
        assertEquals("no key is used twice", strip.map { it.ticker }.distinct(), strip.map { it.ticker })
    }

    @Test
    fun `a duplicate never costs the strip one of its three places`() {
        val doubled = listOf(
            NextUpRow("NFLX", "9000000", 2),
            NextUpRow("NFLX", "8000000", 1),
            NextUpRow("TSM", "7000000", 1),
            NextUpRow("AMD", "6000000", 1),
            NextUpRow("UBER", "5000000", 1),
        )
        assertEquals(listOf("NFLX", "TSM", "AMD"), state(nextUp = doubled).nextUpStrip.map { it.ticker })
    }

    // ---- The rows under the strip ----------------------------------------------------------------

    @Test
    fun `a leader is drawn once, in the strip, and not again in the rows below it`() {
        val drawn = state()
        val leaders = drawn.nextUpStrip
        val rows = drawn.rowsBelowNextUp(leaders)

        assertEquals(listOf("NFLX", "TSM", "AMD"), leaders.map { it.ticker })
        assertEquals("the section keeps everything the strip is not naming", listOf("ASML", "UBER"), rows.map { it.ticker })
        assertTrue(
            "the same ticker twice under one heading reads as a fault, not as emphasis",
            rows.none { row -> leaders.any { it.ticker == row.ticker } },
        )
    }

    @Test
    fun `with no strip every uncovered row stands, and a strip naming them all leaves none`() {
        assertEquals(uncovered, state(nextUp = emptyList()).rowsBelowNextUp(emptyList()))

        val onlyLeaders = state(withoutAnalysis = uncovered.filter { it.ticker in setOf("NFLX", "TSM", "AMD") })
        assertEquals(3, onlyLeaders.nextUpStrip.size)
        assertTrue(onlyLeaders.rowsBelowNextUp(onlyLeaders.nextUpStrip).isEmpty())
    }

    @Test
    fun `the join is by ticker whatever the case, and fewer than three leaders is fewer rows`() {
        val strip = state(nextUp = listOf(NextUpRow(" nflx ", "1000000", 1))).nextUpStrip
        assertEquals(listOf("NFLXx"), strip.map { it.display })
        assertEquals("1 voter", ShippedCopy.render(strip.single().votersCopy))
    }
}
