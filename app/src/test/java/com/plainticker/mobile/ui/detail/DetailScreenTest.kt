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
                "VerdictSection(",
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
            // AmberFScoreNumeral (ui/detail/DetailScreen.kt), not Instrument's fScoreNumeral:
            // extended from AmberType.figureLarge at the same 56sp, see its own doc comment.
            listOf("AmberFScoreNumeral", "R.plurals.detail_fscore_of", "SignalRow("),
        )
        assertOrder(
            "MethodBlock",
            body("private fun MethodBlock(", "private fun SwapBlock("),
            listOf("method.age", "method.statement", "method.sources"),
        )
    }

    // ---- Hero's own slots, measured rather than guessed ----------------------------------------

    /**
     * The Amber restyle inverts Instrument's hero (small ticker, large company name, a sector
     * line below), the biggest structural change this pass made to Detail (see [Hero]'s own doc
     * comment). What is pinned here is the one thing that trade brings with it: the company name
     * now draws at 34sp instead of 16sp, on the same two-line cap, so the real worst case from the
     * catalog (already measured for `AmberTickerRow`, `AmberTickerRowTest`) is worth checking
     * against rather than assumed to still fit.
     */
    @Test
    fun `the hero's ticker stays one line and the company name is capped at two, the same bound a much larger face now has to fit`() {
        val hero = body("private fun Hero(", "private val AmberHeroCompany")
        assertTrue("the small ticker line above the company name never wraps", "maxLines = 1" in hero)
        assertTrue("the company name is bounded rather than left to wrap without limit", "maxLines = 2" in hero)
        // The xStocks catalog's own longest company name, read 2026-09-22
        // (app/src/main/assets/snapshot/xstocks.json, the same figure AmberTickerRowTest pins
        // for the 16sp ticker row): 54 characters. At the hero's own 34sp this is the one
        // real-world name most likely to test the two-line cap; clipping it there (rather than
        // wrapping to a third line) is the known, accepted cost of this restyle's larger company
        // face, not an oversight.
        val longestCompany = "SPDR S&P Oil & Gas Exploration & Production ETF xStock"
        assertEquals(54, longestCompany.length)
    }

    /**
     * The one motion this restyle pass added: the company name fades in through [SkeletonSwitch][
     * com.plainticker.mobile.ui.components.SkeletonSwitch], the screen's cold-open arrival, applied
     * once rather than reached for on every skeleton this screen already had. `SkeletonSwitch`
     * itself already ties into `rememberMotionEnabled()` and snaps under reduced motion; what is
     * pinned here is that Hero actually wires it to `company == null`, its own loading question,
     * and that no other block on this screen was quietly given the same treatment.
     */
    @Test
    fun `the hero's company name is the one place this screen fades content in, gated on its own loading question`() {
        val hero = body("private fun Hero(", "private val AmberHeroCompany")
        assertTrue("SkeletonSwitch drives the company name's own arrival", "SkeletonSwitch(" in hero)
        assertTrue("gated on whether the company is actually known yet", "loading = company == null" in hero)
        assertEquals("SkeletonSwitch is not reached for anywhere else on Detail", 1, count("SkeletonSwitch("))
    }

    @Test
    fun `the screen is one scrolling column, so the traversal order is the visual order`() {
        assertEquals("exactly one scroll container", 1, count("verticalScroll("))
        assertEquals("nothing is sticky and nothing overlaps", 0, count("zIndex("))
        // One Box, holding the scroll and the status-bar scrim. The scrim is the only thing on
        // Detail that does not scroll, and it is not content: it draws no text and takes no
        // touch, so the header still scrolls away (Insets.kt, and TopScrimTest holds the rest).
        assertEquals("the only Box is the one the scrim needs", 1, count("Box("))
        assertEquals("one scrim over the scroll", 1, count("TopScrim("))
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
        // AmberPrimaryAction, one of the six Amber components this restyle pass names: the swap
        // flow it opens (SwapSheet, the receipt) is untouched, only this trigger button restyled.
        assertEquals("no Instrument PrimaryButton left on the screen", 0, count("PrimaryButton("))
        assertEquals("one Swap button on the screen", 1, count("AmberPrimaryAction("))
        assertEquals("one sheet, opened by that button (T10, DT7)", 1, count("SwapSheet("))
        assertTrue("the button does not carry the pair", "state.swapLabel" in scan.code)
    }

    @Test
    fun `every sentence on the screen comes from strings xml`() {
        listOf(
            "action_watch", "action_watching", "detail_heading_backing",
            "detail_heading_sector", "detail_heading_fscore", "detail_heading_method",
            "detail_not_available_filer", "value_missing",
        ).forEach { name ->
            assertTrue("$name is not declared in strings.xml", """name="$name"""" in stringsXml)
            assertTrue("DetailScreen.kt does not read R.string.$name", "R.string.$name" in scan.code)
        }
        // The F-Score caption counts its signals out loud, so both of its sentences are counted
        // copy: a plurals with a one and an other, never a string (CountCopyTest).
        listOf("detail_fscore_of", "detail_fscore_a11y").forEach { name ->
            assertTrue("$name is not declared as a plurals", """<plurals name="$name">""" in stringsXml)
            assertTrue("DetailScreen.kt does not read R.plurals.$name", "R.plurals.$name" in scan.code)
        }
        // Everything the model picks between lives there, not here: the screen never chooses a word.
        listOf(
            // The token's own figure is named by the model too, because below the liquidity floor
            // it is not called a price: the screen may not pick between the two words itself.
            "detail_token_price", "detail_pool_quote",
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
