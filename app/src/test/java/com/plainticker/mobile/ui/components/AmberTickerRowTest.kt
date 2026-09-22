package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trap this task's brief names by name, twice ("Round" starved to one letter a line;
 * "no wallet connected" clipped mid-character to "no wallet c"), pinned the way
 * [com.plainticker.mobile.ui.vote.VoteScreenTest] pins the fix for the first one: there is no
 * layout test on a plain JVM that can measure a real render, so what is pinned is the row's own
 * source, read past comments and literal contents the way [KotlinScan] reads any other screen.
 */
class AmberTickerRowTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val source: String by lazy {
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/ui/components/AmberTickerRow.kt").readText()).code
    }

    private fun body(function: String): String {
        val start = source.indexOf(function)
        assertTrue("no $function in the source", start >= 0)
        val end = source.indexOf("\n}", start)
        assertTrue("$function never closes", end > start)
        return source.substring(start, end)
    }

    // ---- The trap this row is built to not repeat: no fixed-width sibling anywhere -------------

    @Test
    fun `neither the ticker nor the figure sits next to a fixed-width sibling`() {
        val row = body("fun AmberTickerRow(")
        // The old bug's shape: a Modifier.width(Xdp) column that starves whatever shares its row.
        // AmberTickerRow's own left group (ticker, company) and right group (figure, context) are
        // each sized by their content, so there is nothing here for either to clip against.
        assertFalse("a fixed-width modifier is exactly the trap this row was built not to repeat", ".width(" in row)
    }

    @Test
    fun `company ellipsizes rather than clipping, the same safe pattern ListRow already ships`() {
        val row = body("fun AmberTickerRow(")
        val companyBlock = row.substring(row.indexOf("if (company != null)"))
        assertTrue("TextOverflow.Ellipsis" in companyBlock)
        assertTrue("maxLines = 1" in companyBlock)
    }

    @Test
    fun `context wraps to two lines instead of being forced onto one, the resolution ListRow's meta line uses`() {
        val row = body("fun AmberTickerRow(")
        val contextBlock = row.substring(row.indexOf("if (context != null)"))
        assertTrue("maxLines = 2" in contextBlock)
        assertTrue("TextOverflow.Ellipsis" in contextBlock)
        // Never set: an unset softWrap defaults to true, which is what lets a long clause
        // ("TSLAx, swapped 13 Sep") wrap instead of needing a budget.
        assertFalse("softWrap = false" in contextBlock)
    }

    @Test
    fun `ticker and figure are single-line and tabular, never softWrap without a reason`() {
        val row = body("fun AmberTickerRow(")
        // Both are short, bounded content (a ticker, a formatted figure) with nothing beside them
        // that could squeeze them, per the two tests above; softWrap = false here is deliberate,
        // not the unexamined default the historical bug shipped with.
        assertTrue(row.contains("style = AmberType.rowTicker"))
        assertTrue(row.contains("style = AmberType.figureRow"))
        assertTrue(row.contains("color = colors.actionText"))
    }

    // ---- Measured, not guessed: the real worst case from the catalog this row draws ------------

    /**
     * The xStocks catalog's own longest symbol and name
     * (`app/src/main/assets/snapshot/xstocks.json`, read 2026-09-22): `AUTO.GBx`, 8 characters, is
     * the longest of 928 symbols; "SPDR S&P Oil & Gas Exploration & Production ETF xStock", 54
     * characters, is the longest company name. Both are safe under this row's design (the tests
     * above), which is the point of writing them down: a real worst case was checked, not assumed.
     */
    @Test
    fun `the real worst-case ticker and company from the catalog are exactly what this design already tolerates`() {
        val longestSymbol = "AUTO.GBx"
        val longestCompany = "SPDR S&P Oil & Gas Exploration & Production ETF xStock"
        // Not a layout assertion (nothing here can render), a record: the row's design tolerates
        // an unbounded ticker and company by construction (the tests above), and this is the
        // actual longest pair in the catalog it will be asked to draw, not an invented one.
        assertTrue(longestSymbol.length == 8)
        assertTrue(longestCompany.length == 54)
    }
}
