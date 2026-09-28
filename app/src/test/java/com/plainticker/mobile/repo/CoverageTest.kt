package com.plainticker.mobile.repo

import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {

    private fun summary(rows: List<SummaryRow>, covered: List<String>? = null) =
        SummaryResponse(schema = "v1", generatedAt = "2026-09-28T00:00:00Z", rows = rows, covered = covered)

    @Test
    fun `a covered company's bundled symbol is the one the bundled catalog lists for it`() {
        // Fresh-device QA of 1.3.24 (B7): Detail's hero names the xStock before the catalog
        // answers, so the rule ticker plus "x" must be true of every covered company the bundled
        // catalog carries (a London listing like AAFL is AAFx, which is why it is pinned here).
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }
        val catalog = File(module, "src/main/assets/snapshot/xstocks.json").readText()
        val symbols = Regex(""""symbol":\s*"([^"]+)",\s*"ticker":\s*"([^"]+)"""").findAll(catalog)
            .associate { it.groupValues[2] to it.groupValues[1] }
        assertTrue("the bundled catalog was not read", symbols.size > 500)
        val checked = Coverage.BUNDLED.filter { it.ticker in symbols }
        assertTrue("most covered companies are in the bundled catalog", checked.size >= 50)
        checked.forEach { assertEquals(it.ticker, symbols.getValue(it.ticker), it.symbol) }
        assertEquals("AAPLx", Coverage.company("aapl")?.symbol)
    }

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
