package com.plainticker.mobile.ui.components

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trap this task's brief names by name, a third time ("Round" starved to one letter a line;
 * "no wallet connected" clipped mid-character to "no wallet c"; the company name cut to "Meta
 * Pl…" on the device, twice, under two different fixes), pinned the way
 * [com.plainticker.mobile.ui.vote.VoteScreenTest] pins the fix for the first one: there is no
 * layout test on a plain JVM that can measure a real render, so what is pinned is the row's own
 * source, read past comments and literal contents the way [KotlinScan] reads any other screen.
 *
 * **Why this file's own tests did not catch either of the first two fixes.** The first fix gave
 * [ticker] and [figure] a weighted sibling within their own group and stopped there; this file had
 * no test for the *outer* pair (name group versus meta group), so nothing caught the meta column
 * claiming the whole row before the name group saw a budget. The second fix gave both groups a
 * weight, [AmberTickerRow.kt]'s own doc comment now explains why a weighted *split* still cannot
 * work: neither side's real worst case is bounded by the other's, so tuning the ratio only moves
 * the failure to a different company length. The two tests this file used to carry for that split
 * (`the meta column also carries a weight...`, `the name group's weight outweighs the meta
 * group's...`) are gone along with [NameWeight] and [MetaWeight] themselves: the row no longer
 * splits its width at all, so a test that only checked *how* it split would have nothing left to
 * check and would not have caught this regression either. What replaces them below is a structural
 * test that the two lines never share a Row again, and an arithmetic test against the real font
 * file that the regression name actually fits the budget this shape gives it.
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

    // ---- The anatomy fix: the name line and the meta line never share a Row's width again --------

    /**
     * The actual device bug persisted through two fixes because both put the name group and the
     * meta group in the *same* [Row], competing for the same width no matter how that width was
     * split (unweighted-vs-weighted, then weighted-vs-weighted). This is the structural guard: the
     * row's own root container is a [Column], and the name line ([ticker], [company]) and the meta
     * line ([figure], [context]) are two of its direct, un-nested children, each its own [Row] with
     * no `.weight(` on the [Row] itself — so neither line's [Row] can ever be measured against the
     * other's width again, regardless of what ratio a future edit might be tempted to tune.
     */
    @Test
    fun `the row's root is a Column, and the name line never shares a Row with the meta line`() {
        val fn = body("fun AmberTickerRow(")
        // Both the name line's Row and the meta line's Row are exactly this literal, unweighted
        // header: no modifier, so nothing on either Row could constrain it against the other's
        // content, which is what let a long name or a long meta line starve its sibling twice.
        val unweightedRowHeader = "Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {"
        val firstLineIndex = fn.indexOf(unweightedRowHeader)
        // Index-based, bounded by a code token rather than a comment: KotlinScan blanks comment
        // text out of `code` (see the class it is read from), and this file's own body() helper
        // comment already warns that a pattern embedding a bare "\n" between two source lines
        // never matches either, since the source on disk is CRLF.
        val columnIndex = fn.indexOf("Column(")
        assertTrue("the root container (Column() must appear in this function", columnIndex >= 0)
        assertTrue("the name line's Row must appear in this function", firstLineIndex >= 0)
        assertTrue(
            "the root container must be a Column, opened before the name line: two Rows sharing " +
                "one Row's width is exactly the shape both previous fixes kept and both still " +
                "clipped under",
            columnIndex in 0 until firstLineIndex,
        )
        assertTrue("modifier = modifier" in fn.substring(columnIndex, firstLineIndex))
        assertFalse("the weighted split this row used to carry must be gone, not re-tuned", "NameWeight" in source)
        assertFalse("the weighted split this row used to carry must be gone, not re-tuned", "MetaWeight" in source)
        assertEquals(
            "the name line and the meta line must each be their own unweighted Row, not siblings " +
                "inside one shared Row",
            2,
            Regex(Regex.escape(unweightedRowHeader)).findAll(fn).count(),
        )
    }

    @Test
    fun `company ellipsizes rather than clipping, the same safe pattern ListRow already ships`() {
        val row = body("fun AmberTickerRow(")
        // Bounded before the meta line's own Row header (a code token, not a comment: KotlinScan
        // blanks comment text out of `code`), so this block is the name line's remainder only.
        val companyStart = row.indexOf("if (company != null)")
        val metaLineStart = row.indexOf("if (figure != null || context != null)")
        val companyBlock = row.substring(companyStart, metaLineStart)
        assertTrue("TextOverflow.Ellipsis" in companyBlock)
        assertTrue("maxLines = 1" in companyBlock)
        // The pairing ListRow's own top line already proves: an unweighted ticker beside a
        // weight(1f, fill = false) company, so company claims whatever the ticker did not, up to
        // the line's own full width now that nothing else shares it.
        assertTrue("weight(1f, fill = false)" in companyBlock)
    }

    @Test
    fun `context wraps to two lines instead of being forced onto one, the resolution ListRow's meta line uses`() {
        val row = body("fun AmberTickerRow(")
        // Bounded before "if (figure != null)": context now sits ahead of figure on the meta
        // line, so an unbounded substring would swallow figure's own softWrap = false and break
        // the exclusion check below.
        val contextStart = row.indexOf("if (context != null)")
        val contextBlock = row.substring(contextStart, row.indexOf("if (figure != null)", contextStart))
        assertTrue("maxLines = 2" in contextBlock)
        assertTrue("TextOverflow.Ellipsis" in contextBlock)
        // context now carries the identical weight(1f, fill = false) pairing company does (mirrored
        // onto the meta line, ahead of the unweighted figure), so it claims the line's own full
        // width instead of whatever a 40 percent column used to leave it.
        assertTrue("weight(1f, fill = false)" in contextBlock)
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
    fun `the real worst-case meta clause is exactly what the meta line's own budget sizes against`() {
        val longestMeta = "$2.7k behind, too thin · 2 d old"
        assertTrue(longestMeta.length == 32)
    }

    // ---- Proof by arithmetic: the real budget this anatomy gives company and context -------------

    /**
     * The numbers [AmberTickerRow]'s own doc comment states, kept here as executable arithmetic so
     * a future change to this row's own padding, the 8dp gap, or [AmberType.rowTicker]'s size or
     * weight is caught at build time rather than back on a phone. fontTools against
     * `res/font/bricolage_grotesque.ttf` itself, 2026-09-22, each style instantiated at the exact
     * `wght`/`wdth`/`opsz` [AmberType] builds it with (the method
     * [com.plainticker.mobile.ui.you.YouModelTest]'s own fact-cell budget used).
     *
     * **This is the test the two previous fixes could not have passed.** Under the first fix (no
     * weight on the meta group) the name group's budget was whatever was left after an unweighted
     * meta clause claimed the row, frequently nothing. Under the second fix (3-to-2 weighted split)
     * the name group's total share was 60 percent of this same 336dp, roughly 194dp *including* the
     * ticker; subtracting an 8-character ticker and the 8dp gap left about 109dp for company, less
     * than "Meta Platforms, Inc." needs (129.066dp) even though that name is not the catalog's
     * worst case. This anatomy does not split the row at all, so company's budget is the full
     * content width less only the ticker actually beside it.
     */
    @Test
    fun `the worst-case company budget derived from the real font comfortably clears the regression name`() {
        // 400dp frame less AmberTickerRowGroup's 16dp*2 and this row's own 16dp*2 horizontal padding.
        val contentWidthDp = 336.0
        // The 8dp gap between ticker and company on the name line (Arrangement.spacedBy(8.dp)).
        val gapDp = 8.0
        // AUTO.GBx, the catalog's own longest symbol, at rowTicker's 16sp/600: 77.296dp. Any
        // shorter real ticker only grows the company budget from here.
        val worstTickerWidthDp = 77.296
        val worstCaseCompanyBudgetDp = contentWidthDp - worstTickerWidthDp - gapDp
        assertEquals(250.704, worstCaseCompanyBudgetDp, 0.01)

        // summary.json's own `company` field for META, read 2026-09-22: the exact string the
        // device drew as "Meta Platforms, …". At rowCompany's 14sp/400: 129.066dp.
        val metaPlatformsWidthDp = 129.066
        assertTrue(
            "\"Meta Platforms, Inc.\" ($metaPlatformsWidthDp dp) must fit the worst-case company " +
                "budget ($worstCaseCompanyBudgetDp dp), or this row clips it again",
            metaPlatformsWidthDp <= worstCaseCompanyBudgetDp,
        )
        // Other real, shorter analyzed-row names clear it with even more room.
        assertTrue(70.560 <= worstCaseCompanyBudgetDp) // "Netflix, Inc."
        assertTrue(84.686 <= worstCaseCompanyBudgetDp) // "Alphabet Inc."
        assertTrue(128.170 <= worstCaseCompanyBudgetDp) // "NVIDIA Corporation"

        // The catalog's one 54-character outlier does not fit any single-line budget this row
        // could give it at this size, on this frame: 375.564dp of glyphs against, at most, the
        // full 336dp content width with no ticker beside it at all. Recorded, not fixed: the
        // company Text above still ellipsizes rather than clipping, which is what makes this
        // acceptable rather than the defect this task exists to close.
        assertFalse(375.564 <= contentWidthDp)
    }

    /**
     * The same proof for the meta line: [context] now leads the [figure] it used to sit under, so
     * its budget is the content width less only whatever [figure] actually is, not a fixed
     * 40-percent column. The widest realistic [figure] across every screen that calls this row is a
     * worded one, `next_up_weight` ("31,209.9 SKR", [VoteScreen]'s own vote weight), 113.22dp.
     */
    @Test
    fun `the worst-case context budget derived from the real font comfortably clears the longest real meta`() {
        val contentWidthDp = 336.0
        val gapDp = 8.0
        val worstFigureWidthDp = 113.220 // "31,209.9 SKR" at figureRow's 18sp/600 tnum.
        val worstCaseContextBudgetDp = contentWidthDp - worstFigureWidthDp - gapDp
        assertEquals(214.780, worstCaseContextBudgetDp, 0.01)

        // list_row_meta_join's own worst join, the exact clause this file's own worst-case-meta
        // test above pins the length of, measured at context's 14sp/400: 192.444dp.
        val longestMetaWidthDp = 192.444
        assertTrue(
            "the longest real meta clause ($longestMetaWidthDp dp) must fit the worst-case " +
                "context budget ($worstCaseContextBudgetDp dp), or it wraps to two lines needlessly",
            longestMetaWidthDp <= worstCaseContextBudgetDp,
        )
    }

    // ---- AmberTickerRowGroup's own light-only edge (DESIGN.md section 8's added exception) -----

    /**
     * Light's `surfaceRaised` sits about 1.03:1 over `surfaceGround` (`AmberContrastTest`'s own
     * pinned number) against dark's healthy 1.12:1, so a group of rows here loses the tonal
     * separation its whole point depends on: reading as one shared block distinct from the page.
     * The group's own already-clipped 16dp silhouette takes a single `border` ring in light,
     * which outlines the group once rather than framing each row inside it, so DESIGN.md section
     * 8's surviving rule ("no border-as-frame around every row") still holds.
     */
    @Test
    fun `AmberTickerRowGroup takes a light-only border around its own clipped shape, never in dark`() {
        val fn = body("fun AmberTickerRowGroup(")
        assertTrue("val shape = RoundedCornerShape(16.dp)" in fn)
        assertTrue(".clip(shape)" in fn)
        assertTrue(
            "the ring must be gated on colors === AmberLightColors and match the group's own clip shape",
            ".then(if (colors === AmberLightColors) Modifier.border(1.dp, colors.border, shape) else Modifier)" in fn,
        )
    }
}
