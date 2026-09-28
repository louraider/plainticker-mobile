package com.plainticker.mobile.repo

import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {

    private fun summary(rows: List<SummaryRow>, covered: List<String>? = null) =
        SummaryResponse(schema = "v1", generatedAt = "2026-09-28T00:00:00Z", rows = rows, covered = covered)

    /**
     * QA of 1.3.20: the web covers BABA, but xStocks issues no BABAx on any network (the asset list
     * of 28 Sep 2026 has no Alibaba entry and `/public/assets/BABAx` answers 500), so the bundled
     * lists must not name it: a bare row for it could never be joined to a token.
     */
    @Test
    fun `the bundled lists name no company xStocks has no token for`() {
        assertTrue(Coverage.BUNDLED.none { it.ticker == "BABA" })
        assertFalse("BABA" in Coverage.WITHOUT_ROW)
        assertNull(Coverage.company("BABA"))
        assertEquals(56, Coverage.BUNDLED.size)
        assertEquals("every ticker is listed once", Coverage.BUNDLED.size, Coverage.BUNDLED.map { it.ticker }.toSet().size)
    }

    @Test
    fun `every stand-in without a row is a bundled company, so it lands in its own sector`() {
        Coverage.WITHOUT_ROW.forEach { ticker ->
            assertTrue("$ticker has no bundled company", Coverage.company(ticker) != null)
        }
    }

    @Test
    fun `with no covered list from the server, the stand-ins are added bare and BABA is not`() {
        val rows = listOf(SummaryRow(ticker = "AAPL", composite = 46.0))
        val all = Coverage.rows(summary(rows))
        assertEquals(listOf("AAPL", "ABBV", "CMCSA", "MA", "NKE", "V"), all.map { it.ticker })
        assertEquals(setOf("ABBV", "CMCSA", "MA", "NKE", "V"), Coverage.bare(summary(rows)))
        assertEquals("Health Care", all.single { it.ticker == "ABBV" }.sector)
        assertNull("a bare row claims no composite", all.single { it.ticker == "ABBV" }.composite)
    }

    @Test
    fun `the server's own covered list still wins, BABA included, should xStocks ever list it`() {
        val rows = listOf(SummaryRow(ticker = "AAPL", composite = 46.0))
        assertEquals(setOf("BABA"), Coverage.bare(summary(rows, covered = listOf("AAPL", "BABA"))))
    }
}
