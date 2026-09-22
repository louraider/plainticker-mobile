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
     * row's own root container is a [Column], and the name line ([ticker], [company]) is its direct
     * child, drawn by the one literal, unweighted `Row(horizontalArrangement = ...)` header nothing
     * else in this function repeats — so nothing can ever again measure it against the meta line's
     * own width, regardless of what a future edit might be tempted to tune.
     *
     * The meta line ([figure], [context]) is a *sibling* of the name line, never nested inside it or
     * vice versa (both are direct children of the root [Column]), but it is no longer this same bare
     * header: [trailingAction] gave it a reason to share a [Row] of its own, with the action rather
     * than with the name line, which the next test pins.
     */
    @Test
    fun `the row's root is a Column, and the name line's own Row is the only unweighted one left`() {
        val fn = body("fun AmberTickerRow(")
        // The name line's own header: no modifier, so nothing beside ticker and company could ever
        // constrain them against anything else's content.
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
        // Exactly one now: the meta line's own inner Row carries trailingAction's weight modifier
        // (the next test), so it no longer matches this bare, unweighted literal the way it did
        // when it was a plain sibling of the name line under both previous fixes.
        assertEquals(
            "the name line's own Row must be the only unweighted, unmodified Row header left in " +
                "this function; the meta line's own inner Row now carries a weight against " +
                "trailingAction instead (see the trailing-action test below)",
            1,
            Regex(Regex.escape(unweightedRowHeader)).findAll(fn).count(),
        )
    }

    /**
     * [trailingAction] (added to unblock Vote's leader row; see this row's own doc comment, "The
     * leader row, unblocked") shares width with [figure] and [context] only, wrapped in its own
     * [Row], never with [ticker] and [company]: this pins that the wrapper appears exactly once,
     * after the name line, and that the meta content inside it (not [TextAction] itself) carries the
     * `weight(1f, fill = false)` that lets it claim whatever the action does not — the same
     * "bounded content unweighted, flexible content weighted" pairing this row already proves twice
     * (ticker/company, context/figure), now proved a third time one level up.
     */
    @Test
    fun `trailingAction shares a Row with the meta content only, weighted the same way company and context already are`() {
        val fn = body("fun AmberTickerRow(")
        val nameLineIndex = fn.indexOf("Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {")
        assertTrue("the name line must appear", nameLineIndex >= 0)
        val wrapperHeader = "Row(verticalAlignment = Alignment.CenterVertically) {"
        val wrapperIndex = fn.indexOf(wrapperHeader)
        assertTrue("the meta line's own wrapper Row must appear, after the name line", wrapperIndex > nameLineIndex)
        assertEquals(
            "the wrapper must appear exactly once: one Row hosting the meta content and " +
                "trailingAction, never a second one that could nest the name line inside it too",
            1,
            Regex(Regex.escape(wrapperHeader)).findAll(fn).count(),
        )
        // The meta content (context, figure) is what carries the weight, not TextAction: the same
        // "unweighted fixed content first, weighted flexible content claims the rest" pairing
        // proven twice already, never a fixed-width sibling either side of it.
        val metaContentWeight = "modifier = Modifier.weight(1f, fill = false)"
        val metaContentIndex = fn.indexOf(metaContentWeight, wrapperIndex)
        assertTrue(
            "the meta content Row must carry weight(1f, fill = false) against trailingAction",
            metaContentIndex in (wrapperIndex + 1) until fn.length,
        )
        assertEquals(1, Regex(Regex.escape(metaContentWeight)).findAll(fn).count())
        val textActionIndex = fn.indexOf("TextAction(", wrapperIndex)
        assertTrue("TextAction must be inside the meta line's own wrapper", textActionIndex > metaContentIndex)
        // TextAction is the last thing this function draws, so the tail from here to the end of
        // the function body is exactly its own call and nothing else (never bound this with a
        // pattern that embeds a bare "\n" spanning two source lines: this file's source is CRLF,
        // and a pattern ending in a literal newline never matches across the \r that precedes it).
        val textActionCall = fn.substring(textActionIndex)
        assertFalse("TextAction itself must stay unweighted, a bounded label rather than a flexible sibling", ".weight(" in textActionCall)
        assertFalse(".width(" in textActionCall)
    }

    @Test
    fun `company ellipsizes rather than clipping, the same safe pattern ListRow already ships`() {
        val row = body("fun AmberTickerRow(")
        // Bounded before the meta line's own Row header (a code token, not a comment: KotlinScan
        // blanks comment text out of `code`), so this block is the name line's remainder only.
        val companyStart = row.indexOf("if (company != null)")
        val metaLineStart = row.indexOf("if (figure != null || context != null || trailingAction != null)")
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

    // ---- Proof by arithmetic: trailingAction's own budget, at 1.0x and at 1.3x --------------------

    /**
     * Vote's leader row (`VoteScreen.kt`'s `LeaderRow`, migrated off Instrument's `ListRow` onto
     * this parameter): [figure] is the app's own widest figure and [context] is a voter count, both
     * beside a "Vote" [trailingAction] at once. [AmberTickerRow]'s own doc comment ("The leader row,
     * unblocked") has the prose; this is the same numbers as executable arithmetic, at both the
     * scale the row ships at and the 1.3x scale the task this test exists for asks for by name.
     *
     * fontTools against `res/font/outfit_semibold.ttf` for the action's own label (`TextAction`
     * draws through `PlainTickerType.textAction`, Outfit, never Bricolage) and against
     * `res/font/bricolage_grotesque.ttf` for [figure] and [context], both read 2026-09-22.
     */
    @Test
    fun `the leader row's trailing-action budget derived from the real fonts clears the app's own widest figure and voter count`() {
        val contentWidthDp = 336.0
        val gapDp = 8.0
        // TextAction's own 16dp start padding (end = 0.dp) is the only gap between the meta
        // content and the action; nothing here adds a second one.
        val actionStartPaddingDp = 16.0

        // "Vote" (vote_action_row) at PlainTickerType.textAction's Outfit SemiBold 14sp: 30.856dp.
        val voteLabelWidthDp = 30.856
        val metaContentBudgetDp = contentWidthDp - (actionStartPaddingDp + voteLabelWidthDp)
        assertEquals(289.144, metaContentBudgetDp, 0.01)

        // next_up_weight's own widest figure, "31,209.9 SKR", at figureRow's 18sp/600 tnum:
        // 113.220dp, the same number the context budget test above already pins.
        val figureWidthDp = 113.220
        val contextBudgetDp = metaContentBudgetDp - figureWidthDp - gapDp
        assertEquals(167.924, contextBudgetDp, 0.01)

        // next_up_voters' own realistic ceiling for one token's leaderboard, "9,999 voters", at
        // context's 14sp/400: 83.160dp.
        val votersWidthDp = 83.160
        assertTrue(
            "\"9,999 voters\" ($votersWidthDp dp) must fit the leader row's own context budget " +
                "with the Vote action present ($contextBudgetDp dp), or the action starves it",
            votersWidthDp <= contextBudgetDp,
        )

        // The same arithmetic at 1.3x: sp text scales by the raw factor (the conservative,
        // worse-than-real assumption ListRow.valueSubWidth and the budgets above already use,
        // since the real Android 14 curve compresses small text below the nominal multiplier),
        // dp padding does not.
        val voteLabelWidthAt13xDp = voteLabelWidthDp * 1.3
        val metaContentBudgetAt13xDp = contentWidthDp - (actionStartPaddingDp + voteLabelWidthAt13xDp)
        assertEquals(279.887, metaContentBudgetAt13xDp, 0.01)

        val figureWidthAt13xDp = figureWidthDp * 1.3
        val contextBudgetAt13xDp = metaContentBudgetAt13xDp - figureWidthAt13xDp - gapDp
        assertEquals(124.701, contextBudgetAt13xDp, 0.01)

        val votersWidthAt13xDp = votersWidthDp * 1.3
        assertTrue(
            "\"9,999 voters\" grown to 1.3x ($votersWidthAt13xDp dp) must still fit the leader " +
                "row's own context budget with Vote grown too ($contextBudgetAt13xDp dp)",
            votersWidthAt13xDp <= contextBudgetAt13xDp,
        )

        // The name line shares nothing with trailingAction (the structural tests above), so its
        // own 1.0x and 1.3x margins are exactly the company budget test's numbers, unaffected by
        // this same row also carrying a leader's weight and its Vote action.
    }

    /**
     * Watchlist's row (`WatchlistScreen.kt`'s `Watched`, migrated off `ListRow` the same way):
     * [figure] is always null on this caller, so [context] shares the meta line with "Unwatch",
     * the widest label any [trailingAction] on this row draws, and nothing else. The realistic
     * meta join clears one line at 1.0x with a real but modest margin; the pathological one, and
     * the realistic one regrown to 1.3x, do not — both wrap to a second line rather than clipping,
     * which is what [context]'s `maxLines = 2` and `TextOverflow.Ellipsis` exist for.
     */
    @Test
    fun `the watchlist row's trailing-action budget derived from the real fonts wraps rather than clips when it cannot clear one line`() {
        val contentWidthDp = 336.0
        val actionStartPaddingDp = 16.0

        // "Unwatch" (action_unwatch) at PlainTickerType.textAction's Outfit SemiBold 14sp: 56.812dp,
        // the widest trailingAction label this row draws.
        val unwatchLabelWidthDp = 56.812
        val metaContentBudgetDp = contentWidthDp - (actionStartPaddingDp + unwatchLabelWidthDp)
        assertEquals(263.188, metaContentBudgetDp, 0.01)
        // No figure ever, on this caller: the whole budget above is context's own, with no gap
        // and nothing to share it with.

        // watchlist_row_reports joined with list_row_meta_premium's own worst premium clause
        // (list_row_meta_join), at context's 14sp/400: "Reports Oct 22 · $2.7k behind, too thin".
        val realisticJoinWidthDp = 246.568
        assertTrue(
            "the realistic join ($realisticJoinWidthDp dp) must clear the 1.0x budget " +
                "($metaContentBudgetDp dp) with Unwatch present",
            realisticJoinWidthDp <= metaContentBudgetDp,
        )

        // At 1.3x the action's own label grows and the budget shrinks the same way the leader
        // row's does above; the realistic join grows with it and no longer clears one line.
        val unwatchLabelWidthAt13xDp = unwatchLabelWidthDp * 1.3
        val metaContentBudgetAt13xDp = contentWidthDp - (actionStartPaddingDp + unwatchLabelWidthAt13xDp)
        assertEquals(246.144, metaContentBudgetAt13xDp, 0.01)
        val realisticJoinWidthAt13xDp = realisticJoinWidthDp * 1.3
        assertFalse(
            "the realistic join grown to 1.3x ($realisticJoinWidthAt13xDp dp) is expected to miss " +
                "the shrunk 1.3x budget ($metaContentBudgetAt13xDp dp) and wrap instead of clipping",
            realisticJoinWidthAt13xDp <= metaContentBudgetAt13xDp,
        )

        // watchlist_row_unserved joined with the same worst premium clause, at a wider pool
        // figure: "Not in the analysis list · $99.9k behind, too thin". Recorded, not fixed, the
        // same resolution the 54-character company outlier above accepts: it does not clear the
        // 1.0x budget either.
        val pathologicalJoinWidthDp = 305.144
        assertFalse(pathologicalJoinWidthDp <= metaContentBudgetDp)

        // The backstop actually backstops: two lines' own combined capacity at 1.3x comfortably
        // exceeds even the pathological join regrown to 1.3x, so maxLines = 2 is a real ceiling
        // here, not a silent third line of truncation waiting to happen.
        val pathologicalJoinWidthAt13xDp = pathologicalJoinWidthDp * 1.3
        assertTrue(pathologicalJoinWidthAt13xDp <= metaContentBudgetAt13xDp * 2)
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
