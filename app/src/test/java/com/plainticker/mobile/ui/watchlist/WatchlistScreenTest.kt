package com.plainticker.mobile.ui.watchlist

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DT8's contract for the Watchlist composition, read from source the way the List and the
 * Portfolio read theirs. What the screen *says* is [WatchlistModelTest]'s job; this file pins the
 * things composing it on a device would prove and a later edit could quietly undo:
 *
 * 1. the section order the canvas fixes, top to bottom, which is also the traversal order;
 * 2. no arithmetic in the composition: report dates, premiums and the digest arrive decided;
 * 3. the accessibility affordances Pass 6 asks for (one spoken sentence per row, a click label,
 *    Unwatch as its own target);
 * 4. the digest on screen is the stored one, never re-derived from the rows.
 */
class WatchlistScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val screenFile = File(module, "src/main/java/com/plainticker/mobile/ui/watchlist/WatchlistScreen.kt")
    private val modelFile = File(module, "src/main/java/com/plainticker/mobile/ui/watchlist/WatchlistModel.kt")

    private val source: String by lazy {
        assertTrue("WatchlistScreen.kt is missing", screenFile.isFile)
        screenFile.readText()
    }

    private val scan: KotlinScan by lazy { KotlinScan(source) }

    private val modelScan: KotlinScan by lazy {
        assertTrue("WatchlistModel.kt is missing", modelFile.isFile)
        KotlinScan(modelFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    /** The first line of the preview block, which is sample data rather than the screen. */
    private val previewsAt: Int by lazy {
        val line = source.lines().indexOfFirst { "---- Previews" in it }
        assertTrue("WatchlistScreen.kt has no previews", line >= 0)
        line + 1
    }

    private fun count(marker: String): Int = scan.code.split(marker).size - 1

    /** One composable's body, so an order is read where the calls happen and not where they live. */
    private fun body(function: String, until: String): String {
        val start = scan.code.indexOf(function)
        assertTrue("WatchlistScreen.kt has no $function", start >= 0)
        val end = scan.code.indexOf(until, start)
        assertTrue("WatchlistScreen.kt has no $until after $function", end > start)
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
            "WatchlistContent",
            body("internal fun WatchlistContent(", "private fun Watched("),
            listOf(
                "header()",
                "Banner(",
                "R.string.watchlist_heading_watched",
                "R.string.watchlist_empty",
                "SkeletonRows(",
                "Watched(",
                "R.string.watchlist_heading_digest",
                "Digest(state.digest)",
                "Footer(",
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
    fun `the empty state is a real sentence with the one action that answers it`() {
        val content = body("internal fun WatchlistContent(", "private fun Watched(")
        assertTrue("nothing watched must say what watching is for", "R.string.watchlist_empty" in content)
        assertTrue("and offer the place to do it from", "R.string.action_browse_analyzed" in content)
        // A spinner is never the answer (DESIGN.md section 8).
        assertEquals(0, count("CircularProgressIndicator"))
        assertEquals(0, count("LinearProgressIndicator"))
    }

    @Test
    fun `no report date or premium is worked out in the composition`() {
        listOf("nextReport", "priceUsd", "referencePriceUsd", "poolUsd", "premiumPct").forEach { field ->
            assertEquals(
                "WatchlistScreen.kt reads row.$field; the answer belongs in WatchlistModel.kt",
                0,
                count("row.$field"),
            )
        }
        assertEquals("the screen must not reach for the tracking rule itself", 0, count("TrackingQuality"))
        assertTrue("the model is what asks TrackingQuality", "TrackingQuality" in modelScan.code)
        assertEquals("the report date is formatted once, in the model", 0, count("Fmt."))
        assertTrue("and the model is where Fmt lives", "Fmt.dayMonth" in modelScan.code)
    }

    @Test
    fun `the digest on screen is the one that was stored`() {
        val panel = body("private fun Digest(", "private fun Footer(")
        // "Panel(colors = colors) {" since the fix for the light-theme Digest leak (Digest now
        // resolves defaultAmberColors and hands it to Panel instead of Panel reading Instrument's
        // fixed-dark Elevated/Line on its own); still the digest's one container either way.
        assertTrue("the Panel is the digest container", "Panel(" in panel)
        assertTrue("it draws what the model read out of the record, stamped in the reader's own zone", "digestPanel(record, zone)" in panel)
        assertEquals("the screen must not assemble a digest", 0, count("digest("))
        assertTrue("the model reads the stored text raw", "raw(text)" in modelScan.code)
    }

    /**
     * Polish batch, 2026-09-25: the stamp used to print Instrument's mono `PlainTickerType.meta`
     * off [Fmt.utc][com.plainticker.mobile.ui.Fmt.utc], the pre-Amber shape a screenshot of the
     * old card showed (a monospace UTC timestamp in a bordered box). The box is Amber's own, kept
     * on purpose for light-theme contrast (`Panel`'s own doc comment, DESIGN.md section 4's
     * `Panel` row); only the type and the zone were still pre-Amber, and this pins both fixed.
     */
    @Test
    fun `the digest stamp is drawn in Amber's own type, in the reader's own zone, never Instrument's mono UTC`() {
        val panel = body("private fun Digest(", "private fun Footer(")
        assertTrue("the stamp is Amber's own meta style", "style = AmberType.meta" in panel)
        assertTrue("the body is Amber's own body style", "style = AmberType.body" in panel)
        assertFalse("no more Instrument type on this panel", "PlainTickerType" in panel)
        assertTrue("the zone is read once and passed to the model, the same as TodayScreen and DigestScreen", "remember { ZoneId.systemDefault() }" in panel)
        assertFalse("the model must not still be asked for a UTC stamp", "Fmt.utc" in modelScan.code)
        assertTrue("the model reads the reader's own zone instead", "Fmt.localDateTime" in modelScan.code)
    }

    @Test
    fun `the accessibility affordances Pass 6 asks for are passed`() {
        val row = body("private fun Watched(", "private fun Digest(")
        assertTrue("a watched row must read as one sentence", "description = sentence(" in row)
        assertTrue("a watched row must be labelled as an action", "R.string.action_open_ticker" in row)
        assertTrue("Unwatch is the trailing action, and its own target", "trailingAction = " in row)
        assertTrue("R.string.action_unwatch" in row)
        assertEquals("no swipe to unwatch (Pass 2 rejected it)", 0, count("swipe"))
        assertTrue("the row sentence must speak its numerals", "spoken(" in scan.code)
    }

    @Test
    fun `a device that will not show notifications says so once, with a way to change it`() {
        val footer = body("private fun Footer(", "private fun EmptyLine(")
        assertTrue("R.string.action_enable" in footer)
        assertTrue("the line is drawn from the model", "digestFooter(" in footer)
        assertTrue(
            "the off sentence must say the digest is still here",
            "the digest stays on this screen" in stringsXml,
        )
    }

    @Test
    fun `every sentence on the screen comes from strings xml`() {
        val resourceValues = Regex("""<string\b[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml).map { it.groupValues[1] }.toSet()
        val sentences = scan.literals
            .filter { it.line < previewsAt }
            .map { it.text }
            .filter { it.length > 12 && it.contains(' ') && it.any { c -> c.isLowerCase() } }
            .filterNot { it in resourceValues }
        assertTrue("copy spelled in Kotlin: $sentences", sentences.isEmpty())
    }
}
