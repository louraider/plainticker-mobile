package com.plainticker.mobile.ui.you

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U1's contract for the You composition, read from source the way PortfolioScreenTest and
 * WatchlistScreenTest read theirs. What the screen *says* is [YouModelTest]'s job; this file pins
 * what a device walk would otherwise have to prove and a later edit could quietly undo:
 *
 * 1. the section order the plan fixes, top to bottom, which is also the traversal order because
 *    the screen is one list and nothing overlaps;
 * 2. no arithmetic in the composition: every numeral and every sentence arrives decided;
 * 3. every "On this device" fact opens the tab it belongs to, and is labelled for a screen reader;
 * 4. the device's own code never reaches this file at all, not even a hash of it drawn as text.
 */
class YouScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val screenFile = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouScreen.kt")
    private val modelFile = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouModel.kt")
    private val viewModelFile = File(module, "src/main/java/com/plainticker/mobile/ui/you/YouViewModel.kt")

    private val source: String by lazy {
        assertTrue("YouScreen.kt is missing", screenFile.isFile)
        screenFile.readText()
    }

    private val scan: KotlinScan by lazy { KotlinScan(source) }

    private val modelScan: KotlinScan by lazy {
        assertTrue("YouModel.kt is missing", modelFile.isFile)
        KotlinScan(modelFile.readText())
    }

    private val viewModelScan: KotlinScan by lazy {
        assertTrue("YouViewModel.kt is missing", viewModelFile.isFile)
        KotlinScan(viewModelFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    /** The first line of the preview block, which is sample data rather than the screen. */
    private val previewsAt: Int by lazy {
        val line = source.lines().indexOfFirst { "---- Previews" in it }
        assertTrue("YouScreen.kt has no previews", line >= 0)
        line + 1
    }

    private fun count(marker: String): Int = scan.code.split(marker).size - 1

    /** One composable's body, so an order is read where the calls happen and not where they live. */
    private fun body(function: String, until: String): String {
        val start = scan.code.indexOf(function)
        assertTrue("YouScreen.kt has no $function", start >= 0)
        val end = scan.code.indexOf(until, start)
        assertTrue("YouScreen.kt has no $until after $function", end > start)
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
    fun `the sections are drawn in the order the plan fixes`() {
        // KotlinScan blanks the contents of every string literal (including item()'s own "key"
        // argument) out of `code`, so the order is read off the calls each item wraps, the same
        // way PortfolioScreenTest and WatchlistScreenTest read theirs.
        assertOrder(
            "YouContent",
            body("internal fun YouContent(", "private fun YouAction.handler("),
            listOf(
                "header()",
                "R.string.you_heading)",
                "WalletBlock(",
                "FactGrid(cells = listOf(proCell",
                "ActionButtons(",
                "R.string.you_heading_device",
                "FactGrid(cells = deviceCells",
                "NotificationsLine(",
                "Footer()",
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
    fun `the device code never reaches this file`() {
        assertEquals("YouScreen.kt must never read the device's own code", 0, count(".code("))
        assertEquals("YouModel.kt must never read the device's own code", 0, modelScan.code.split(".code(").size - 1)
        assertEquals("YouViewModel.kt must never read the device's own code", 0, viewModelScan.code.split(".code(").size - 1)
        assertEquals(0, count("DevicePassStore"))
    }

    @Test
    fun `every on this device fact opens the tab it belongs to, and is labelled`() {
        listOf("R.string.you_fact_swaps", "R.string.you_fact_votes", "R.string.you_fact_watched").forEach {
            assertTrue("the screen has no $it", it in scan.code)
        }
        assertTrue("a tapped fact must open a tab", "onTap = { onOpenTab(" in scan.code)
        assertTrue("a tapped fact must be labelled for a screen reader", "tapLabel = " in scan.code)
        assertTrue("R.string.you_open_tab" in scan.code)
    }

    @Test
    fun `the button matrix never draws two accent fills`() {
        val actions = body("private fun ActionButtons(", "private fun WalletBlock(")
        // Amber restyle: AmberPrimaryAction and AmberSecondaryAction, both now the shared
        // components in ui/components/AmberPrimaryAction.kt (AmberSecondaryAction was a private
        // copy living only in this file until Instrument's PrimaryButton and SecondaryButton
        // retired), keeping the same one-fill, never-a-text-link rule (U2).
        assertTrue("the primary slot is an AmberPrimaryAction", "AmberPrimaryAction(label = it.label.text()" in actions)
        assertTrue(
            "the secondary slot is an AmberSecondaryAction, never a text action",
            "AmberSecondaryAction(label = it.label.text()" in actions,
        )
        assertEquals("Pay for Pro is never drawn as a text action", 0, count("TextAction(label = it.label"))
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
