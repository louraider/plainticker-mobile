package com.plainticker.mobile.ui.list

import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.ui.ShippedCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task A1 (docs/plan-monetisation-2026-09-19.md section 1.5): "search spans both sets", as one
 * flat list, and "selecting an uncovered result still opens its Detail, where the vote action
 * already is". Read from source the way [ListFinishedScreenTest] and
 * [com.plainticker.mobile.ui.vote.VoteScreenTest] read theirs: this build has no device and no
 * Compose test host, so a screen's wiring is proved by scanning it rather than by composing it.
 *
 * [ListViewModelTest] already proves the data half, that a query narrows `analyzed` and
 * `withoutAnalysis` together (`search filters by ticker, symbol or company...`). What that test
 * cannot see is what the screen does with an uncovered match once it has one; this does.
 */
class ListSearchScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(path: String): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/$path").readText()).code

    private val listScreen by lazy { source("ui/list/ListScreen.kt") }

    /** Everything from [function] to the line that closes it at column zero. */
    private fun body(source: String, function: String): String {
        val start = source.indexOf(function)
        assertTrue("no $function in the source", start >= 0)
        val end = source.indexOf("\n}", start)
        assertTrue("$function never closes", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `an uncovered row opens Detail by ticker, the same call an analyzed row makes`() {
        val priceOnly = body(listScreen, "private fun PriceOnlyRow(")
        val analyzed = body(listScreen, "private fun AnalyzedRow(")
        assertTrue("the uncovered row opens Detail", "onClick = { onOpenDetail(row.ticker) }" in priceOnly)
        assertTrue("an analyzed row opens the same way", "onClick = { onOpenDetail(row.ticker) }" in analyzed)
    }

    @Test
    fun `searching draws both sets as one flat list, with no sector chapter and no sticky heading`() {
        val content = body(listScreen, "internal fun ListContent(")

        // Amber's sticky chapters (task: sticky sector chapters, a jump index) are read off
        // `visibleChapters`, built once, above the `when` that branches on the query; browsing
        // and searching then read that value (or ignore it) rather than each computing their own.
        assertTrue("Stocks chapters by sector somewhere in the browse path", "chapteredBySector()" in content)

        // The browse branch (blank query) is the only place a sticky chapter heading is drawn;
        // the search branch is the `else` of the same `when` and draws nothing between them.
        val browseStart = content.indexOf("state.query.isBlank() ->")
        val searchStart = content.indexOf("else -> {", browseStart)
        assertTrue("both branches of the when are in the source", browseStart >= 0 && searchStart > browseStart)
        val browse = content.substring(browseStart, searchStart)
        val searching = content.substring(searchStart)

        assertTrue("browsing walks the chapters the query-blank branch built", "visibleChapters.forEach" in browse)
        assertTrue("browsing pins each chapter head as a stickyHeader", "stickyHeader(" in browse)
        assertTrue("the chapter head is Amber's own, not Instrument's Heading", "AmberSectionHead(" in browse)

        assertTrue("no chapter heading while searching", "AmberSectionHead(" !in searching)
        assertTrue("no stickyHeader while searching", "stickyHeader(" !in searching)
        assertTrue("no sector read while searching", "chapteredBySector" !in searching)

        // Both sets, drawn with their own existing row (composite for an analyzed match, price
        // plus the vote action for an uncovered one) rather than a shape of its own.
        assertTrue("analyzed matches are drawn", "itemsIndexed(state.analyzed" in searching)
        assertTrue("uncovered matches are drawn", "itemsIndexed(state.withoutAnalysis" in searching)
        assertTrue("an uncovered match keeps its row, price and vote action included", "PriceOnlyRow(" in searching)
    }

    @Test
    fun `every sector chapter reads the count off the same rows it draws`() {
        val content = body(listScreen, "internal fun ListContent(")
        val browse = content.substring(content.indexOf("state.query.isBlank() ->"))
        assertTrue(
            "the heading's meta is the chapter's own row count, not a guess",
            "meta = Fmt.count(chapter.rows.size)" in browse,
        )
    }

    @Test
    fun `strings xml carries the trailing chapter's heading`() {
        assertEquals("No sector", ShippedCopy.strings["list_heading_no_sector"])
    }

    // ---- Device QA of 1.3.18 --------------------------------------------------------------------

    @Test
    fun `a search result stands clear of the field's underline`() {
        val gap = listScreen.indexOf("Spacer(Modifier.height(SearchResultsGap))")
        val flat = listScreen.indexOf("isLast = index == state.analyzed.lastIndex && state.withoutAnalysis.isEmpty()")
        assertTrue("the gap leads the flat search list", gap in 0 until flat)
    }

    @Test
    fun `a voted row is one tap target, the Voted word included`() {
        val row = body(listScreen, "private fun VotableAmberRow(")
        assertTrue("Voted is drawn by the row itself", "trailingNote = if (voted) stringResource(R.string.vote_voted_row) else null" in row)
        assertTrue("pinned to the end edge", "trailingActionAtEnd = voted" in row)
        assertEquals("no second, untappable Voted beside the row", 1, row.split("R.string.vote_voted_row").size - 1)
    }

    @Test
    fun `the cold chip slots are outlines, never filled empty boxes`() {
        assertTrue("SkeletonChip(width = DeepPoolSlotWidth" in listScreen)
        assertTrue("the filter row holds its place while cold", "if (sectors.isEmpty() && cold) ColdFilterRow(" in listScreen)
        assertTrue("the cold list sits in the card its rows will fill", "SkeletonTickerRows(count = SkeletonRowCount" in listScreen)
        assertTrue("no filled bar stands for a chip", "SkeletonBar(" !in listScreen)
    }
}
