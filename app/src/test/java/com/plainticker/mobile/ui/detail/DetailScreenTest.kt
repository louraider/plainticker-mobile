package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DT6's contract for the composition itself, read from source the way OnboardingScreenTest,
 * ManifestTest and CopyLintTest read theirs. What the screen *says* is [DetailModelTest]'s job;
 * this file pins the four things composing it on a device would prove and a later edit could
 * quietly undo:
 *
 * 1. the section order DESIGN.md section 5 fixes, top to bottom, which is also the traversal order
 *    because the screen is one Column and nothing overlaps;
 * 2. no premium and no arithmetic in the composition: the gauge is handed the one tracking answer;
 * 3. no fact grid invented under the three tracks, since the v1.1 payload carries no leaf
 *    fundamentals (docs/data-map.md, gap 1);
 * 4. every sentence comes from strings.xml, and the accessibility affordances the components need
 *    are actually passed.
 */
class DetailScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val screenFile = File(module, "src/main/java/com/plainticker/mobile/ui/detail/DetailScreen.kt")
    private val modelFile = File(module, "src/main/java/com/plainticker/mobile/ui/detail/DetailModel.kt")

    private val scan: KotlinScan by lazy {
        assertTrue("DetailScreen.kt is missing", screenFile.isFile)
        KotlinScan(screenFile.readText())
    }

    private val modelScan: KotlinScan by lazy {
        assertTrue("DetailModel.kt is missing", modelFile.isFile)
        KotlinScan(modelFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    private val resourceValues: Set<String> by lazy {
        Regex("""<string\b[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml).map { it.groupValues[1] }.toSet()
    }

    private fun count(marker: String): Int = scan.code.split(marker).size - 1

    /** One composable's body, so an order is read where the calls happen and not where they live. */
    private fun body(function: String, until: String): String {
        val start = scan.code.indexOf(function)
        assertTrue("DetailScreen.kt has no $function", start >= 0)
        val end = scan.code.indexOf(until, start)
        assertTrue("DetailScreen.kt has no $until after $function", end > start)
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
    fun `the sections are drawn in the order DESIGN section 5 fixes`() {
        assertOrder(
            "DetailContent",
            body("internal fun DetailContent(", "private fun Hero("),
            listOf(
                "TopBar(",
                "Banner(",
                "Hero(state)",
                "PriceBlock(state)",
                "Gauge(",
                "LiveBlock(state)",
                "R.string.detail_heading_backing",
                "TrustBlock(state)",
                "FundamentalsBlock(state)",
                "SwapBlock(state = state",
                "SwapSheet(state = swap",
            ),
        )
    }

    @Test
    fun `the fundamentals are the sector, then the F-Score, then the method`() {
        assertOrder(
            "FundamentalsBlock",
            body("private fun FundamentalsBlock(", "private fun FScoreBlock("),
            listOf(
                "R.string.detail_heading_sector",
                "Track(",
                "R.string.detail_heading_fscore",
                "FScoreBlock(it)",
                "R.string.detail_heading_method",
                "MethodBlock(it)",
            ),
        )
        assertOrder(
            "FScoreBlock",
            body("private fun FScoreBlock(", "private fun MethodBlock("),
            listOf("fScoreNumeral", "R.string.detail_fscore_of", "SignalRow("),
        )
        assertOrder(
            "MethodBlock",
            body("private fun MethodBlock(", "private fun SwapBlock("),
            listOf("method.age", "method.statement", "method.sources"),
        )
    }

    @Test
    fun `the screen is one scrolling column, so the traversal order is the visual order`() {
        assertEquals("exactly one scroll container", 1, count("verticalScroll("))
        assertEquals("nothing is sticky and nothing overlaps", 0, count("zIndex("))
        assertEquals(0, count("Box("))
        assertEquals("the navigation inset is part of the scrolled content", 1, count("navigationBarsPadding()"))
    }

    @Test
    fun `no premium and no price arithmetic is done in the composition`() {
        listOf("premiumPct", "usdPrice", "stockData", "liquidity", "supplyRaw", "coverage")
            .forEach { field ->
                assertEquals(
                    "DetailScreen.kt reads $field; the answer belongs in DetailModel.kt",
                    0,
                    count("state.$field"),
                )
            }
        // The gauge takes the one TrackingQuality answer and decides nothing else.
        assertTrue("the gauge is not handed the tracking answer", "tracking = it" in scan.code)
        assertTrue("the gauge scale is not the one DESIGN.md fixes", "scalePct = GAUGE_SCALE_PCT" in scan.code)
    }

    @Test
    fun `there is one fact grid, and nothing is invented under the three tracks`() {
        assertEquals("Backing and controls is the only grid on Detail", 1, count("FactGrid("))
        listOf(
            "detail_fact_sector_rank", "detail_fact_return_on_capital", "detail_fact_gross_margin",
            "detail_fact_price_to_earnings", "detail_fact_ev_to_sales", "detail_fact_52_week",
            "detail_fact_six_months", "detail_fact_sector_median",
        ).forEach { name ->
            assertEquals(
                "$name is a canvas placeholder; the v1.1 payload has no leaf fundamentals",
                0,
                count("R.string.$name"),
            )
        }
    }

    @Test
    fun `caution reaches the value of a fact cell and nothing else on the screen`() {
        assertEquals("one tone decision, taken from the model's own flag", 1, count("FactTone.Caution"))
        assertTrue("the tone is not read off the model", "if (fact.caution)" in scan.code)
        assertEquals("Caution is never imported as a colour here", 0, count("theme.Caution"))
        // And the model gives it to the two issuer controls only.
        assertEquals("only two cells may carry caution", 2, modelScan.code.split("caution = true").size - 1)
    }

    @Test
    fun `the live bar is announced by something that does not tick`() {
        assertTrue("the live bar takes no stable announcement", "announcement = line.announcement.text()" in scan.code)
        assertTrue("the announcement is not the slot", "R.string.detail_live_a11y" in modelScan.code)
        assertTrue("the bar is not told whether the read is live", "live = line.live" in scan.code)
    }

    @Test
    fun `the one banner slot and the single Swap button`() {
        assertEquals("one banner slot", 1, count("Banner("))
        assertEquals("one Swap button on the screen", 1, count("PrimaryButton("))
        assertEquals("one sheet, opened by that button (T10, DT7)", 1, count("SwapSheet("))
        assertTrue("the button does not carry the pair", "state.swapLabel" in scan.code)
    }

    @Test
    fun `every sentence on the screen comes from strings xml`() {
        listOf(
            "action_watch", "action_watching", "detail_token_price", "detail_heading_backing",
            "detail_heading_sector", "detail_heading_fscore", "detail_heading_method", "detail_fscore_of",
            "detail_fscore_a11y", "detail_not_available_filer", "value_missing",
        ).forEach { name ->
            assertTrue("$name is not declared in strings.xml", """name="$name"""" in stringsXml)
            assertTrue("DetailScreen.kt does not read R.string.$name", "R.string.$name" in scan.code)
        }
        // Everything the model picks between lives there, not here: the screen never chooses a word.
        listOf(
            "detail_nyse_close", "detail_nyse_price", "detail_gauge_reference_close",
            "detail_gauge_reference_live", "detail_value_unknown", "detail_chain_unread_sub",
            "value_none", "value_yes", "detail_analysis_pending", "detail_no_xstock",
        ).forEach { name ->
            assertTrue("$name is not declared in strings.xml", """name="$name"""" in stringsXml)
            assertTrue("DetailModel.kt does not name R.string.$name", "R.string.$name" in modelScan.code)
            assertTrue("the screen picks $name itself; that belongs in DetailModel.kt", "R.string.$name" !in scan.code)
        }
        // What is left in Kotlin is preview sample data: tickers, company names, the state words
        // the server assigns and one public mint. Nothing a translator would ever be handed.
        scan.literals.forEach { literal ->
            assertTrue(
                "\"${literal.text}\" is a resource value hard coded at line ${literal.line}",
                literal.text !in resourceValues,
            )
            assertTrue(
                "\"${literal.text}\" at line ${literal.line} reads like copy; put it in strings.xml",
                literal.text.trim().split(Regex("\\s+")).size <= 3,
            )
        }
    }

    @Test
    fun `every string the model names is declared, and the nine signals are in the data map's order`() {
        Regex("""R\.string\.(\w+)""").findAll(modelScan.code + scan.code).map { it.groupValues[1] }.distinct()
            .forEach { name ->
                assertTrue("R.string.$name is not declared in strings.xml", """name="$name"""" in stringsXml)
            }
        val signals = Regex("""R\.string\.(signal_\w+)""").findAll(modelScan.code).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "signal_roa_positive", "signal_cfo_positive", "signal_roa_improving", "signal_accruals",
                "signal_leverage_falling", "signal_liquidity_improving", "signal_no_new_shares",
                "signal_gross_margin_improving", "signal_asset_turnover_improving",
            ),
            signals,
        )
    }

    @Test
    fun `the screen previews at the three frames the design pass reads`() {
        assertTrue("no preview on the finished screen", "@InstrumentPreviews" in scan.code)
        assertTrue("the loading state has no preview", "DetailLoadingPreview" in scan.code)
        assertTrue("the degraded state has no preview", "DetailDegradedPreview" in scan.code)
    }
}
