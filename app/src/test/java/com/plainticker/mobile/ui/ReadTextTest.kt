package com.plainticker.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The display-side pass over the server's prose (device QA of 1.3.17, AAPLx's and JEFx's read and
 * ABBVx's "What to check next"). The inputs are the device's own text, with the dash written as a
 * character code so this file carries none itself.
 */
class ReadTextTest {

    private val em = Char(0x2014).toString()
    private val en = Char(0x2013).toString()

    private fun norm(text: String, year: Int = 2026) = ReadText.normalize(text, year)

    @Test
    fun `a lone dash before a clause introduces it, so it becomes a colon`() {
        assertEquals(
            "leaves one signal unlit: check which metric closes or widens that gap.",
            norm("leaves one signal unlit $em check which metric closes or widens that gap."),
        )
        assertEquals(
            "the comparison is yours to draw: place the P/E against a sector median of 35.8x.",
            norm("the comparison is yours to draw $em place the P/E against a sector median of 35.8x."),
        )
    }

    @Test
    fun `a lone dash before a joining word becomes a comma`() {
        assertEquals(
            "beat estimates in 6 of 6 recent quarters, and the Forward-axis score of 45/100 aligns.",
            norm("beat estimates in 6 of 6 recent quarters $em and the Forward-axis score of 45/100 aligns."),
        )
    }

    /**
     * Device QA of 1.3.18, ABBVx's read: the pair around "AbbVie Inc. beat estimates in 6 of 6 recent
     * quarters" was split at the stop in "Inc.", and the screen read "quarters: and the Forward-axis".
     */
    @Test
    fun `a pair of dashes is found across an abbreviation, so ABBVx's read gets two commas`() {
        assertEquals(
            "Consensus has been well-calibrated, AbbVie Inc. beat estimates in 6 of 6 recent quarters, " +
                "and the Forward-axis score of 45/100 aligns with what the realized record supports.",
            norm(
                "Consensus has been well-calibrated $em AbbVie Inc. beat estimates in 6 of 6 recent quarters $em " +
                    "and the Forward-axis score of 45/100 aligns with what the realized record supports.",
            ),
        )
    }

    @Test
    fun `a colon an older pass put before a joining word goes back to a comma`() {
        // The exact text the device drew.
        assertEquals(
            "AbbVie Inc. beat estimates in 6 of 6 recent quarters, and the Forward-axis score of 45/100 aligns",
            norm("AbbVie Inc. beat estimates in 6 of 6 recent quarters: and the Forward-axis score of 45/100 aligns"),
        )
        assertEquals("The one bright spot in valuation is P/B: at 0.9x", norm("The one bright spot in valuation is P/B: at 0.9x"))
    }

    @Test
    fun `references to sections and tables the app does not have are rewritten`() {
        assertEquals(
            "From the sector list in Stocks, pick two or three companies yourself and line them up by F-Score, ROE, and Op. margin.",
            norm("From the same-sector table in section 06, pick two or three companies yourself and line them up by F-Score, ROE, and Op. margin."),
        )
        assertEquals(
            "In the sector list in Stocks, pick two or three companies and line them up by ROE.",
            norm("In the same-sector table, pick two or three companies and line them up by ROE."),
        )
        assertEquals("Compare the margins with the median.", norm("Compare the margins (see section 06) with the median."))
        assertEquals("Same-sector peers stay as they are.", norm("Same-sector peers stay as they are."))
    }

    @Test
    fun `step titles in Title Case read in sentence case, names and acronyms kept`() {
        assertEquals(
            "Compare Jefferies against sector peers by ROE and operating margin",
            ReadText.sentenceCase("Compare Jefferies Against Sector Peers by ROE and Operating Margin", names = listOf("Jefferies Financial Group Inc.")),
        )
        assertEquals(
            "Watch the September 2026 earnings for ROE recovery",
            ReadText.sentenceCase("Watch the September 2026 Earnings for ROE Recovery"),
        )
        assertEquals(
            "Read the 10-K risk factors and management discussion on capex",
            ReadText.sentenceCase("Read the 10-K Risk Factors and Management Discussion on Capex"),
        )
        assertEquals(
            "Compare sector peers by F-Score, ROE, and operating margin",
            ReadText.sentenceCase("Compare Sector Peers by F-Score, ROE, and Operating Margin"),
        )
        // A word the prose writes with a capital mid-sentence is a name and keeps it.
        assertEquals(
            "Read the 10-K risk factors and Management Discussion",
            ReadText.sentenceCase(
                "Read the 10-K Risk Factors and Management Discussion",
                evidence = "Pull the most recent 10-K and focus on the Management's Discussion section.",
            ),
        )
    }

    @Test
    fun `a title already in sentence case is left alone, names included`() {
        listOf(
            "Watch the October 2026 earnings report closely",
            "Compare peers in the sector table by F-Score and operating margin",
            "Compare Apple with its sector on margin",
        ).forEach { assertEquals(it, ReadText.sentenceCase(it)) }
    }

    @Test
    fun `a pair of dashes in one sentence frames an aside, so both become commas`() {
        assertEquals(
            "EPS growth of -5.4% puts Jefferies at the bottom of the Financials sector, the 7th percentile, while ROE of 6.1% trails.",
            norm("EPS growth of -5.4% puts Jefferies at the bottom of the Financials sector $em the 7th percentile $em while ROE of 6.1% trails."),
        )
        // A decimal point is not a sentence end, so the pair is still found across "8.6%" and "2.7".
        assertEquals(
            "Revenue grew 8.6% year over year, outpacing the sector median by 2.7 pp, yet EPS changed -1.3%.",
            norm("Revenue grew 8.6% year over year $em outpacing the sector median by 2.7 pp $em yet EPS changed -1.3%."),
        )
    }

    @Test
    fun `unspaced dashes, spaced en dashes and spaced hyphens are punctuation too`() {
        assertEquals("well contained: Debt/EBITDA of 0.7x.", norm("well contained${em}Debt/EBITDA of 0.7x."))
        assertEquals("well contained: Debt/EBITDA of 0.7x.", norm("well contained $en Debt/EBITDA of 0.7x."))
        assertEquals("well contained: Debt/EBITDA of 0.7x.", norm("well contained - Debt/EBITDA of 0.7x."))
    }

    @Test
    fun `hyphens inside words and before numbers, and number ranges, are left as words`() {
        assertEquals("the Forward-axis score and a median of -4.2%", norm("the Forward-axis score and a median of -4.2%"))
        assertEquals("fiscal 2024 to 2025", norm("fiscal 2024${en}2025"))
    }

    @Test
    fun `an ISO date reads the app's own way, with the year only when it is not this year`() {
        assertEquals("When Apple reports on 28 Oct, track revenue.", norm("When Apple reports on 2026-10-28, track revenue."))
        assertEquals("reports on 3 Feb 2027", norm("reports on 2027-02-03"))
        assertEquals("not a day: 2026-13-45", norm("not a day: 2026-13-45"))
    }

    @Test
    fun `the device's own AAPLx read comes out with no dash at all`() {
        val read = "The F-Score of 8/9 confirms the underlying strength, with leverage well contained $em " +
            "Debt/EBITDA of 0.7x sits below the sector median by 9%. Revenue grew 6.4% year over year, and FCF " +
            "changed -9.2% year over year $em a gap of 34.9 pp below the median. The company has beat consensus " +
            "in 7 of 7 recent quarters, yet consensus models faster EPS growth than the record shows $em a " +
            "mismatch the Forward-axis score of 34/100 reflects."
        val out = norm(read)
        assertFalse(em in out)
        assertFalse(en in out)
        assertFalse(" - " in out)
        assertFalse("no doubled punctuation", ",," in out || ": ," in out || "  " in out)
    }

    @Test
    fun `text with nothing to rewrite comes back unchanged`() {
        val plain = "ROIC of 82.3% runs above the sector median by 62.4 pp."
        assertEquals(plain, norm(plain))
    }

    /** Final QA of 1.3.19 (aapl_p6, aapl_p7, abbv_p7, jef_p7): the exact strings the device drew. */
    @Test
    fun `every name the web gives its sector table points at the sector list in Stocks`() {
        assertEquals(
            "Compare peers in the sector list in Stocks by ROE and operating margin",
            norm("Compare peers in the sector table by ROE and operating margin"),
        )
        assertEquals(
            "Compare Jefferies against peers in the sector list in Stocks by ROE and operating margin",
            norm("Compare Jefferies against peers in the sector table by ROE and operating margin"),
        )
        assertEquals(
            "Compare peers from the sector list in Stocks by F-Score, ROE, and operating margin",
            norm("Compare peers from the sector table by F-Score, ROE, and operating margin"),
        )
        assertEquals(
            "Pick two or three companies from the Information Technology list in Stocks and line them up by ROE and Op. margin. " +
                "Apple's ROE of 171.4% stands 140.9 pp above the sector median, so the comparison will show how far that spread extends across the peer set.",
            norm(
                "Pick two or three companies from the Information Technology table and line them up by ROE and Op. margin. " +
                    "Apple's ROE of 171.4% stands 140.9 pp above the sector median, so the comparison will show how far that spread extends across the peer set.",
            ),
        )
        assertEquals(
            "Jefferies' P/B of 0.9x against a sector median of 2.6x suggests the market is pricing in lower returns, " +
                "and the sector list in Stocks can show whether that gap is sector-wide or specific to this firm.",
            norm(
                "Jefferies' P/B of 0.9x against a sector median of 2.6x suggests the market is pricing in lower returns, " +
                    "and the table comparison can show whether that gap is sector-wide or specific to this firm.",
            ),
        )
        assertEquals("Sector list in Stocks first.", norm("Sector table first."))
        // QA of 1.3.20: the app has no "sector comparison" section, so no rewrite may name one.
        assertFalse("sector comparison" in norm("Compare peers in the sector table by ROE and operating margin"))
        // Nothing else about a table moves.
        assertEquals("The table below is not drawn here.", norm("The table below is not drawn here."))
    }

    @Test
    fun `has beat reads has beaten, and nothing else moves`() {
        assertEquals(
            "The company has beaten consensus in 7 of 7 recent quarters, yet consensus models faster EPS growth.",
            norm("The company has beat consensus in 7 of 7 recent quarters, yet consensus models faster EPS growth."),
        )
        assertEquals("It has beaten estimates.", norm("It has beaten estimates."))
        assertEquals("AbbVie beat estimates in 6 of 6 quarters.", norm("AbbVie beat estimates in 6 of 6 quarters."))
    }
}
