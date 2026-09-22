package com.plainticker.mobile.ui.components

import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.ShippedCopy
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FactGrid]'s own worst recurring defect, re-measured for this pass (see [FactGrid]'s own doc
 * comment for why the anatomy change voids every budget pinned before it).
 *
 * **The geometry**, read off `FactGrid.kt` itself for the Seeker's 400dp frame:
 * - The grid: `.padding(horizontal = 20.dp)` (40dp), the 1dp border trick's own `.padding(1.dp)`
 *   (2dp), leaves 358dp for the row.
 * - A half-width cell ([FactCell.span] 1, the common case, two per row): the row's 358dp minus the
 *   1dp `Arrangement.spacedBy` gap between the pair is 357dp, split two ways, 178.5dp each; each
 *   cell's own `.padding(start = 16.dp, end = 16.dp)` takes 32dp off that for text, leaving
 *   **146.5dp**.
 * - A full-width cell ([FactCell.span] 2, alone in its row): 358dp minus the same 32dp cell padding
 *   leaves **326dp**.
 *
 * **The face**, confirmed 2026-09-22 with fontTools 4.63.0 against `res/font/
 * bricolage_grotesque.ttf` (`unitsPerEm` 1000), instantiated at `wght` 700 `wdth` 100 and `opsz`
 * equal to each style's own point size — the exact coordinates [AmberType.factValueAt] builds for
 * a non-[FactCell.valueMono] value. `tnum` substitutes every digit for a `.tf` glyph (confirmed via
 * the font's own `GSUB`), and all ten are the same width, confirming the tabular claim; comma and
 * period are not substituted and stay narrower than a digit, so a string built from digits plus at
 * most one comma, one period and a short unit tail is the realistic worst shape a value here ever
 * takes (never a sentence: every real value is a percent, a multiplier, a token amount or a short
 * state word).
 *
 * **The five call-site sizes** ([FactCell.valueSize] across Detail, the pass sheet, the swap sheet
 * and the vote sheet) map onto only two of the widths above, because no call site ever pairs a
 * span-2 size with a half-width cell or vice versa:
 * - 18sp half-width (`PassSheet`/`VoteSheet`'s own `span == 1` branch)
 * - 22sp half-width (`SwapSheet`'s `SheetCellSize.Normal`) and 22sp full-width (Gallery's own
 *   span-2 sample)
 * - 24sp half-width (`DetailScreen`'s `CellValueSize`)
 * - 28sp full-width (`PassSheet`/`VoteSheet`'s `span > 1` branch, `SwapSheet`'s
 *   `SheetCellSize.Headline`)
 * - 32sp full-width (`DetailScreen`'s `SpanValueSize`)
 *
 * **The budgets** below are the largest length that stayed clear of the measured clip point by at
 * least one character, found by measuring real candidate strings directly (not a generic
 * "characters this wide" guess): a synthetic all-nines ceiling is not used here the way
 * `YouModelTest` used `999,999`, because unlike a watchlist count this grid's numbers have no
 * single shared domain bound, so every budget is checked instead against the actual formats and
 * fixed words each real call site draws.
 */
class FactGridTest {

    // ---- 18sp half-width: PassSheet's and VoteSheet's `span == 1` branch, 146.5dp available ----

    /**
     * `0.000005 SOL` (12 chars) measures 120.17dp, 26.33dp of headroom; `0.00000523 SOL` (14
     * chars) measures 142.49dp, 4.01dp of headroom; `0.000005237 SOL` (15 chars) measures
     * 153.65dp, 7.15dp *past* the 146.5dp available. 14 is the largest length still measured
     * clear, so the budget below keeps one more character of margin under it, matching the margin
     * [maxFactWordValueLength] in `YouModelTest` keeps under its own computed fit.
     *
     * **This is a real, not synthetic, risk.** `vote_fee_label`'s value is
     * `PassSummary.lamports`/`VoteSummary.lamports` (`PassSheetModel.kt`, `VoteSheetModel.kt`),
     * "the signature fee in lamports, about 5,000 for a one-signature transaction" by that field's
     * own doc comment, formatted with `Fmt.tokenAmount(lamports, 9, maxDecimals = 9)`: a *round*
     * fee like 5,000 trims to "0.000005" (12 chars) and fits with room to spare, but a fee that
     * is not a round number of lamports (any signature fee with a priority tip added, which is
     * ordinary on Solana) keeps every one of its nine decimal places and produces exactly the
     * 15-character shape measured above. That call site is outside this file set
     * (`ui/pass/PassSheetModel.kt`, `ui/vote/VoteSheetModel.kt`), so this file can only measure
     * and record the risk, not fix it; the fix, when someone owns those files, is the same one
     * `Fmt.tokenAmount` already uses elsewhere for a token quantity: a smaller `maxDecimals`.
     * FactGrid's own JetBrains Mono predecessor was worse here, not better — every glyph in that
     * face is a fixed 10.8dp at 18sp regardless of tabular figures, so the same 15-character string
     * measured 162dp, 15.5dp past 146.5dp; this pass narrows the overrun, it does not introduce it.
     */
    private val halfWidth18Budget = 13

    @Test
    fun `the documented signature fee fits its half-width card at 18sp`() {
        // The value PassSheet.kt and VoteSheet.kt both preview: PassSheet.kt's readyCells default
        // and VoteSheet.kt's PreviewLanded, 5_000L lamports, "about 5,000" by the field's own doc.
        val typical = ShippedCopy.string("vote_fee", Fmt.tokenAmount(5_000L, 9, maxDecimals = 9))
        assertTrue(
            "\"$typical\" (${typical.length} chars) exceeds the $halfWidth18Budget-character budget",
            typical.length <= halfWidth18Budget,
        )
        // A fee ten times the documented one, still round, stays short.
        val spiked = ShippedCopy.string("vote_fee", Fmt.tokenAmount(500_000L, 9, maxDecimals = 9))
        assertTrue(spiked.length <= halfWidth18Budget)
    }

    // ---- 22sp half-width: SwapSheet's SheetCellSize.Normal, 146.5dp available -------------------

    /**
     * `0.00203928` (10 chars, the SOL this rent-plus-fee quote needs, unrounded) measures 127.47dp,
     * 19.03dp of headroom; `0.002039285` (11 chars) measures 141.06dp, 5.44dp of headroom;
     * `0.0020392851` (12 chars) measures 154.66dp, 8.16dp past 146.5dp. 11 is the largest length
     * still measured clear, so the budget keeps one character under it. `all_in_cost`'s own value
     * is a percent, always shorter than the SOL figure for any plausible swap, so the SOL figure is
     * this bucket's real worst case.
     */
    private val halfWidth22Budget = 10

    @Test
    fun `the sol-needed figure and the all-in cost percent fit their half-width card at 22sp`() {
        // solCost.totalLamports for a fresh token account: signature fee plus this project's own
        // rent reference (the 2,039,280-lamport figure `SwapSheetModel.kt`'s own comment on
        // `costCells` names, "the classic account's rent").
        val solNeeded = Fmt.tokenAmount(2_039_280L + 5_000L, 9, 9)
        assertTrue(
            "\"$solNeeded\" (${solNeeded.length} chars) exceeds the $halfWidth22Budget-character budget",
            solNeeded.length <= halfWidth22Budget,
        )
        val allInCost = Fmt.percent(1.44, signed = false)
        assertTrue(allInCost.length <= halfWidth22Budget)
    }

    // ---- 24sp half-width: DetailScreen's CellValueSize, 146.5dp available -----------------------

    /**
     * "Unknown" (7 chars) measures 110.95dp, 35.55dp of headroom, the widest of this grid's fixed
     * state words; a split multiplier out to four whole digits with two decimals and a thousands
     * comma, "9,999.00" (8 chars, already generations past any real split this app has priced),
     * measures 98.93dp, 47.57dp of headroom. Both comfortably inside a budget this wide.
     */
    private val halfWidth24Budget = 8

    @Test
    fun `every fixed trust-fact word and a generous split multiplier fit their half-width card at 24sp`() {
        listOf(
            ShippedCopy.strings.getValue("value_yes"),
            ShippedCopy.strings.getValue("value_none"),
            ShippedCopy.strings.getValue("detail_value_unknown"),
            ShippedCopy.strings.getValue("value_missing"),
        ).forEach { text ->
            assertTrue(
                "\"$text\" (${text.length} chars) exceeds the $halfWidth24Budget-character budget",
                text.length <= halfWidth24Budget,
            )
        }
        val generousSplit = Fmt.decimal(9_999.00)
        assertTrue(generousSplit.length <= halfWidth24Budget)
    }

    // ---- Full-width (span 2): 326dp available, every real value here is far short of the edge ---

    /**
     * The widest full-width value any real call site draws is `VoteSheet`'s vote-weight cell
     * (`weightCell`, `VoteSheetModel.kt`) at the worst stake [SkrStakeBound.isPlausible] ever
     * accepts, the same ceiling `YouModelTest`'s own worst-case test uses:
     * `SkrStakeBound.STAKED_SUPPLY_RAW - 1L` formatted at six decimals is
     * "4,389,047,809.999999" (20 chars), which measures 297.78dp at 28sp, 28.22dp of headroom.
     * `SwapSheet`'s "you receive" amount plus symbol ("1,234.567890 TSLAx", 18 chars) measures
     * 272.80dp, 53.20dp of headroom, and a synthetic six-figure token amount with all six decimals
     * populated, "999,999.999999 USDC" (19 chars), measures 300.36dp, 25.64dp of headroom. 326dp
     * is generous enough that the budget stays a full margin under the true boundary (one decimal
     * place further out on the token-amount shape, "9,999,999.999999 USDC", 21 chars, still clears
     * by 3.05dp; 22 chars is the first length this pass found that overruns 326dp) without needing
     * a per-size split the way the half-width cells do.
     */
    private val fullWidthBudget = 20

    @Test
    fun `real full-width values and a six-figure token amount fit the span-2 card`() {
        val stakeWorstCase = Fmt.tokenAmount(SkrStakeBound.STAKED_SUPPLY_RAW - 1L, SkrStakeBound.SKR_DECIMALS)
        assertTrue(
            "\"$stakeWorstCase\" (${stakeWorstCase.length} chars) exceeds the " +
                "$fullWidthBudget-character full-width budget",
            stakeWorstCase.length <= fullWidthBudget,
        )
        val youReceive = "1,234.567890 TSLAx"
        assertTrue(youReceive.length <= fullWidthBudget)
        val sixFigureAmount = "${Fmt.tokenAmount(999_999_999_999L, 6)} USDC"
        assertTrue(
            "\"$sixFigureAmount\" (${sixFigureAmount.length} chars) exceeds the " +
                "$fullWidthBudget-character full-width budget",
            sixFigureAmount.length <= fullWidthBudget,
        )
    }

    // ---- valueMono: unchanged, JetBrains Mono, monospace, no anatomy change to re-measure --------

    /**
     * [FactCell.valueMono] keeps [com.plainticker.mobile.ui.theme.PlainTickerType.factValueAt]
     * (JetBrains Mono) exactly as before this pass, so no budget above applies to it and none needs
     * re-deriving: every glyph in a monospace face is the same width by construction, and
     * [Fmt.shortKey]'s two real head/tail pairs in this grid (4/4 for Detail's transfer-hook
     * program id and the pass/vote destination and collector, 6/6 for the swap receipt's
     * signature) bound the identifier at 9 or 13 characters respectively, both already comfortably
     * inside either half-width budget above at their own, smaller, 18-24sp mono sizes.
     */
    @Test
    fun `every real shortKey length is bounded and short`() {
        assertTrue(Fmt.shortKey("x".repeat(64)).length == 9)
        assertTrue(Fmt.shortKey("x".repeat(88), head = 6, tail = 6).length == 13)
    }

    // ---- packRows stays exactly what the budgets above assume: two columns, a span-2 alone -----

    @Test
    fun `packRows pairs span-1 cells and gives a span-2 cell the row alone`() {
        val a = FactCell("a", "1")
        val b = FactCell("b", "2")
        val c = FactCell("c", "3", span = 2)
        assertTrue(packRows(listOf(a, b, c)) == listOf(listOf(a, b), listOf(c)))
        assertTrue(packRows(listOf(a, c, b)) == listOf(listOf(a), listOf(c), listOf(b)))
    }
}
