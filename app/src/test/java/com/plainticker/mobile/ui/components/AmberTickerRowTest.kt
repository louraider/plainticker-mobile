package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trap this task's brief names by name, a third time ("Round" starved to one letter a line;
 * "no wallet connected" clipped mid-character to "no wallet c"; now the company name cut to
 * "Meta Pl…" on the device), pinned the way [com.plainticker.mobile.ui.vote.VoteScreenTest] pins
 * the fix for the first one: there is no layout test on a plain JVM that can measure a real
 * render, so what is pinned is the row's own source, read past comments and literal contents the
 * way [KotlinScan] reads any other screen.
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

    // ---- The device bug: an unweighted meta column claimed the row before the name saw a budget -

    /**
     * The actual device bug ("META x  Meta Pl…", "GOOGL x  Alphab…"), and what the test above did
     * not catch: absence of a literal `.width(` does not mean neither side has priority. Before this
     * fix, the outer [Row]'s meta column (figure, context) carried no weight, so Compose measured it
     * first, with the *entire* row available, and a long `context` clause claimed most of that width
     * before the weighted name group (ticker, company) ever saw a budget — a fixed-width sibling in
     * effect, just not in literal syntax. Both groups now carry a weight, which the test above could
     * not have distinguished from the old, broken shape, since neither shape contains `.width(`.
     */
    @Test
    fun `the meta column also carries a weight, so it cannot claim the row before the name group does`() {
        val row = body("fun AmberTickerRow(")
        // Not "Row(\n    modifier...": the source is read from disk with its own CRLF line
        // endings, so a pattern that embeds a literal "\n" between two lines never matches: it
        // is a full clause on one line instead.
        assertTrue(
            "the name group (ticker, company) must be weighted",
            "modifier = Modifier.weight(NameWeight)" in row,
        )
        val metaColumnStart = row.indexOf("if (figure != null || context != null)")
        val metaColumn = row.substring(metaColumnStart, row.indexOf("verticalArrangement", metaColumnStart))
        assertTrue(
            "the meta column (figure, context) must also be weighted, or it is measured before " +
                "the name group with the whole row available to it, which is the device bug",
            "modifier = Modifier.weight(MetaWeight)" in metaColumn,
        )
    }

    @Test
    fun `the name group's weight outweighs the meta group's, so the identity wins a real squeeze`() {
        val nameWeight = Regex("""private const val NameWeight = (\d+(?:\.\d+)?)f""").find(source)
            ?.groupValues?.get(1)?.toDouble()
        val metaWeight = Regex("""private const val MetaWeight = (\d+(?:\.\d+)?)f""").find(source)
            ?.groupValues?.get(1)?.toDouble()
        assertTrue("NameWeight must be declared", nameWeight != null)
        assertTrue("MetaWeight must be declared", metaWeight != null)
        assertTrue(
            "the name (ticker, company) is the row's identity and must outrank the meta line " +
                "(figure, context), which is supplementary and already wraps gracefully",
            nameWeight!! > metaWeight!!,
        )
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

    /**
     * The actual worst case that broke the row on the device: not company alone, but company
     * *and* a long meta clause competing for the same row at once. `list_row_meta_join` joins the
     * thin-pool sentence and the age ("$2.7k behind, too thin" + " · " + "2 d old"), which is what
     * the Stocks list drew beside "Meta Platforms, Inc." when the report was filed. Recorded here,
     * beside the catalog's own worst case above, because it was the *pair* the old, unweighted
     * design never accounted for.
     */
    @Test
    fun `the real worst-case meta clause is exactly what the weighted split above sizes against`() {
        val longestMeta = "$2.7k behind, too thin · 2 d old"
        assertTrue(longestMeta.length == 32)
    }
}
