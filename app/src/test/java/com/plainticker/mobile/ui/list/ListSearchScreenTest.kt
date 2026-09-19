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
    fun `searching draws both sets as one flat list, with no sector chapter and no heading`() {
        val content = body(listScreen, "internal fun ListContent(")

        // The browse branch (blank query) is the only place a chapter heading is drawn; the
        // search branch is the `else` of the same `when` and draws nothing between them.
        val browseStart = content.indexOf("state.query.isBlank() ->")
        val searchStart = content.indexOf("else -> {", browseStart)
        assertTrue("both branches of the when are in the source", browseStart >= 0 && searchStart > browseStart)
        val browse = content.substring(browseStart, searchStart)
        val searching = content.substring(searchStart)

        assertTrue("browsing chapters by sector", "state.analyzedChapters" in browse)
        assertTrue("browsing draws a heading", "Heading(" in browse)

        assertTrue("no chapter heading while searching", "Heading(" !in searching)
        assertTrue("no sector read while searching", "analyzedChapters" !in searching)

        // Both sets, drawn with their own existing row (composite plus state for an analyzed
        // match, price plus the vote action for an uncovered one) rather than a shape of its own.
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
}
