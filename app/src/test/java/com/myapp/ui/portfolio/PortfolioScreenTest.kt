package com.myapp.ui.portfolio

import com.myapp.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DT8's contract for the Portfolio composition, read from source the way [com.myapp.ui.detail.DetailScreenTest]
 * reads Detail's. What the screen *says* is [PortfolioModelTest]'s job; this file pins the things
 * composing it on a device would prove and a later edit could quietly undo:
 *
 * 1. the section order the canvas fixes, top to bottom, which is also the traversal order because
 *    the screen is one list and nothing overlaps;
 * 2. no arithmetic in the composition: quantities, values and premiums arrive decided;
 * 3. the accessibility affordances Pass 6 asks for are actually passed (a spoken sentence per row,
 *    the total as a heading, the click label on each position);
 * 4. no cost basis, no profit and no loss anywhere near this screen, and the footnote that says so.
 */
class PortfolioScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val screenFile = File(module, "src/main/java/com/myapp/ui/portfolio/PortfolioScreen.kt")
    private val modelFile = File(module, "src/main/java/com/myapp/ui/portfolio/PortfolioModel.kt")
    private val viewModelFile = File(module, "src/main/java/com/myapp/ui/portfolio/PortfolioViewModel.kt")

    private val source: String by lazy {
        assertTrue("PortfolioScreen.kt is missing", screenFile.isFile)
        screenFile.readText()
    }

    private val scan: KotlinScan by lazy { KotlinScan(source) }

    /** The first line of the preview block, which is sample data rather than the screen. */
    private val previewsAt: Int by lazy {
        val line = source.lines().indexOfFirst { "---- Previews" in it }
        assertTrue("PortfolioScreen.kt has no previews", line >= 0)
        line + 1
    }

    private val modelScan: KotlinScan by lazy {
        assertTrue("PortfolioModel.kt is missing", modelFile.isFile)
        KotlinScan(modelFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    private fun count(marker: String): Int = scan.code.split(marker).size - 1

    /** One composable's body, so an order is read where the calls happen and not where they live. */
    private fun body(function: String, until: String): String {
        val start = scan.code.indexOf(function)
        assertTrue("PortfolioScreen.kt has no $function", start >= 0)
        val end = scan.code.indexOf(until, start)
        assertTrue("PortfolioScreen.kt has no $until after $function", end > start)
        return scan.code.substring(start, end)
    }

    private fun assertOrder(where: String, source: String, markers: List<String>) {
        val indices = markers.map { marker ->
            val index = source.indexOf(marker)
            assertTrue("$where never calls $marker", index >= 0)
            index
        }
        indices.zipWithNext().forEachIndexed { i, (first, second) ->
            assertTrue("in $where, ${markers[i]} must come before ${markers[i + 1]}", first < second)
        }
    }

    @Test
    fun `the sections are drawn in the order the canvas fixes`() {
        assertOrder(
            "PortfolioContent",
            body("internal fun PortfolioContent(", "private fun Total("),
            listOf(
                "header()",
                "StateBanner(",
                "R.string.portfolio_heading_holdings",
                "Total(state)",
                "Holding(",
                "R.string.portfolio_cost_basis",
                "R.string.portfolio_heading_recent_swaps",
                "Swap(receipt",
                "R.string.portfolio_receipts_note",
            ),
        )
    }

    @Test
    fun `the screen is one list, so the traversal order is the visual order`() {
        assertEquals("exactly one scroll container", 1, count("LazyColumn("))
        assertEquals("nothing is sticky and nothing overlaps", 0, count("stickyHeader"))
        assertEquals(0, count("zIndex("))
        assertEquals("no reordering of the reading order", 0, count("traversalIndex"))
        assertEquals("the navigation inset is part of the scrolled content", 1, count("WindowInsets.navigationBars"))
    }

    @Test
    fun `every state of the screen draws a sentence rather than a blank column`() {
        val content = body("internal fun PortfolioContent(", "private fun Total(")
        listOf(
            "R.string.portfolio_connecting",
            "R.string.portfolio_not_connected",
            "R.string.action_connect_wallet",
            "SkeletonRows(",
            "R.string.portfolio_empty",
            "R.string.action_browse_analyzed",
        ).forEach { assertTrue("the screen has no $it", it in content) }
        // A spinner is never the answer (DESIGN.md section 8).
        assertEquals(0, count("CircularProgressIndicator"))
        assertEquals(0, count("LinearProgressIndicator"))
    }

    @Test
    fun `no quantity, value or premium is worked out in the composition`() {
        listOf("amountRaw", "priceUsd", "multiplier", "decimals", "poolUsd", "referencePriceUsd", "totalUsd")
            .forEach { field ->
                assertEquals(
                    "PortfolioScreen.kt reads $field; the answer belongs in PortfolioModel.kt",
                    0,
                    count("position.$field"),
                )
            }
        // The one rule for the liquidity floor is the data layer's, and the model asks it; a
        // composable never sees a pool size or a premium at all.
        assertEquals("the screen must not reach for the tracking rule itself", 0, count("TrackingQuality"))
        assertTrue("the model is what asks TrackingQuality", "TrackingQuality" in modelScan.code)
    }

    @Test
    fun `the accessibility affordances Pass 6 asks for are passed`() {
        val holding = body("private fun Holding(", "private fun Swap(")
        assertTrue("a position must read as one sentence", "description = sentence(" in holding)
        assertTrue("a position must be labelled as an action", "R.string.action_open_ticker" in holding)

        val swap = body("private fun Swap(", "private fun WalletActions(")
        assertTrue("a swap row must read as one sentence", "description = sentence(" in swap)

        val total = body("private fun Total(", "private fun Holding(")
        assertTrue("the total must be a heading", "heading()" in total)
        assertTrue("the total must read as one sentence", "contentDescription = spokenTotal" in total)

        // Numerals are spoken as words: "+0.09%" reads "plus 0.09 percent".
        assertTrue("the row sentence must speak its numerals", "spoken(" in scan.code)
    }

    @Test
    fun `no cost basis is invented, and the footnote says so`() {
        val viewModelScan = KotlinScan(viewModelFile.readText())
        listOf("costBasis", "profit", "pnl", "gain", "unrealized", "sinceBought").forEach { word ->
            val whole = Regex("""\b""" + word + """\b""", RegexOption.IGNORE_CASE)
            assertEquals("$word has no business on this screen", 0, whole.findAll(scan.code).count())
            assertEquals("$word has no business in the model", 0, whole.findAll(modelScan.code).count())
            assertEquals("$word has no business in the state", 0, whole.findAll(viewModelScan.code).count())
        }
        assertTrue(
            "the cost basis footnote is missing from strings.xml",
            "Cost basis is not read from the chain." in stringsXml,
        )
    }

    @Test
    fun `every sentence on the screen comes from strings xml`() {
        val resourceValues = Regex("""<string\b[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml).map { it.groupValues[1] }.toSet()
        // Literals in the composition are sample data for the previews and formatting glue, never
        // a sentence: anything with a space and a lowercase word would be copy spelled in Kotlin.
        val sentences = scan.literals
            .filter { it.line < previewsAt }
            .map { it.text }
            .filter { it.length > 12 && it.contains(' ') && it.any { c -> c.isLowerCase() } }
            .filterNot { it in resourceValues }
        assertTrue("copy spelled in Kotlin: $sentences", sentences.isEmpty())
    }
}
