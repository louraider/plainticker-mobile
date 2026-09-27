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
}
