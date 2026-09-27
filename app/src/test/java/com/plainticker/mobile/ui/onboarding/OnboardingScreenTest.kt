package com.plainticker.mobile.ui.onboarding

import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DT11's contract for the screen itself, read from source the way ManifestTest, BrandAssetsTest
 * and CopyLintTest read theirs. Composing the screen needs a device, but the three things a
 * later edit could quietly break do not:
 *
 * 1. every sentence comes from strings.xml, never from a Kotlin literal;
 * 2. the self-certification is one Checkbox target of at least 48dp;
 * 3. the picture of Today behind the panel is decoration, dimmed, cleared from the semantics tree
 *    and blind to touch, so the button is the only thing on the screen a finger or a switch can reach;
 * 4. (2026-09-26) the screen describes the app that ships: the five bottom-bar destinations, drawn
 *    with the real Amber components, never the retired List, four text tabs or Watchlist.
 *
 * The copy itself is CopyLintTest's job; this file only pins where it lives and what it does.
 */
class OnboardingScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val screenFile = File(module, "src/main/java/com/plainticker/mobile/ui/onboarding/OnboardingScreen.kt")

    private val scan: KotlinScan by lazy {
        assertTrue("OnboardingScreen.kt is missing", screenFile.isFile)
        KotlinScan(screenFile.readText())
    }

    private val stringsXml: String by lazy { File(module, "src/main/res/values/strings.xml").readText() }

    private val resourceValues: Set<String> by lazy {
        Regex("""<string\b[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml).map { it.groupValues[1] }.toSet()
    }

    /**
     * Changed 2026-09-26 (audit, item 1): the resource list used to pin the four retired tab labels,
     * the You action and the List's counted Today strip, because the backdrop drew the old shell.
     * It now pins what the rewritten screen reads: the five bottom-bar labels, the map sentence for
     * each, Today's own venue line, section head and Watched marker for the backdrop.
     */
    @Test
    fun `every sentence on the screen comes from strings xml`() {
        listOf(
            "app_name", "onboarding_headline", "onboarding_body_reads",
            "onboarding_body_disclaimer", "onboarding_certify", "onboarding_consent_continue",
            "onboarding_continue", "onboarding_pick_title", "onboarding_pick_body",
            "onboarding_pick_skip_example", "onboarding_pick_skip",
            "today_status_closed_tomorrow", "today_heading_reports", "today_reports_watched",
        ).forEach { name ->
            assertTrue("$name is not declared in strings.xml", """name="$name"""" in stringsXml)
            assertTrue("OnboardingScreen.kt does not read R.string.$name", "R.string.$name" in scan.code)
        }
        listOf("onboarding_body_chain", "onboarding_body_fundamentals", "onboarding_body_map").forEach { name ->
            assertTrue("$name described the retired app and should be gone", """name="$name"""" !in stringsXml)
        }
        // What is left in Kotlin is sample data, not copy: tickers, company names and the state
        // word the server assigns. Nothing that a translator would ever be handed.
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

    /**
     * Replaces "the map sentence names all four tabs and You" (2026-09-26, audit item 1): that test
     * pinned List and Watchlist, two destinations the app no longer has. The map is now one
     * sentence per bottom-bar destination, and each must open with that destination's own label,
     * which is what the panel sets in weight 600 and what the reader then sees under the icon.
     */
    @Test
    fun `the tab map is gone, and the second step picks stocks to watch from real analysed chips`() {
        fun string(name: String) = Regex("""<string name="$name">(.*?)</string>""").find(stringsXml)?.groupValues?.get(1)
        listOf("today", "stocks", "vote", "portfolio", "you").forEach { key ->
            assertTrue("onboarding_map_$key ended onboarding on a list of tabs", string("onboarding_map_$key") == null)
        }
        assertEquals("Continue", string("onboarding_consent_continue"))
        assertEquals("Open Today", string("onboarding_continue"))
        assertEquals("Pick stocks to watch", string("onboarding_pick_title"))
        assertEquals("Skip and read %1\$s", string("onboarding_pick_skip_example"))

        val pick = scan.code.substringAfter("private fun PickPanel(").substringBefore("\n}\n")
        assertTrue("the chips are the state's suggestions, never literals", "state.suggestions.forEach" in pick)
        assertTrue("each chip is an Amber chip", "AmberChip(" in pick)
        assertTrue("the primary action is live once something is picked", "enabled = state.canFinish" in pick)
        assertTrue("skip is always offered", "onClick = actions.onSkip" in pick)
        assertTrue("skip names the worked example when it is on offer", "R.string.onboarding_pick_skip_example" in pick)
    }

    @Test
    fun `the backdrop is drawn with Amber components in Bricolage, never Instrument's`() {
        listOf("AmberBottomNav(", "AmberDestination.TODAY", "AmberTickerRow(", "AmberTickerRowGroup", "TopBar(").forEach {
            assertTrue("the backdrop does not draw $it", it in scan.code)
        }
        listOf("TopTabs", "TodayStrip", "PlainTickerType", "Outfit").forEach {
            assertTrue("OnboardingScreen.kt still reaches for $it", it !in scan.code)
        }
        // Whole words: ConsentPanel( is this screen's own gate, not Instrument's Panel.
        listOf("ListRow", "Panel").forEach {
            assertTrue("OnboardingScreen.kt still draws $it", !Regex("""\b$it\(""").containsMatchIn(scan.code))
        }
    }

    @Test
    fun `the self certification is one checkbox target of at least 48dp`() {
        assertEquals("exactly one toggle on the screen", 1, Regex("""\.toggleable\(""").findAll(scan.code).count())
        assertTrue("the toggle does not name Role.Checkbox", "role = Role.Checkbox" in scan.code)
        assertTrue("the consent row is smaller than a 48dp target", "defaultMinSize(minHeight = 48.dp)" in scan.code)
        // The sentence sits inside the toggled row, so the merged node reads it as the label.
        assertTrue("the consent sentence is not on the screen", "R.string.onboarding_certify" in scan.code)
    }

    @Test
    fun `the list behind the panel is decoration and not a target`() {
        assertTrue("the backdrop is not dimmed", "alpha(BackdropAlpha)" in scan.code)
        assertTrue("the backdrop is not the canvas' 25 percent", "BackdropAlpha = 0.25f" in scan.code)
        assertTrue("the backdrop is not cleared from the semantics tree", "clearAndSetSemantics {}" in scan.code)
        // The backdrop's bottom bar is a real AmberBottomNav, which needs a select handler; the
        // picture swallows every pointer event on the initial pass so the bar can never be reached
        // (replaces the old "onSelect = null" pin on the retired TopTabs, 2026-09-26).
        assertTrue("the bottom bar behind the panel does nothing when selected", "onSelect = {}" in scan.code)
        assertTrue("the backdrop does not swallow touches", ".swallowTouches()" in scan.code)
        assertTrue("touches are not consumed before the children see them", "PointerEventPass.Initial" in scan.code)
        val handlers = Regex("""\bonClick\s*=\s*(\w+)""").findAll(scan.code).map { it.groupValues[1] }.toList()
        assertEquals(
            "the consent button, then the pick step's finish and skip, are the only named click handlers",
            listOf("onContinue", "actions", "actions"),
            handlers,
        )
    }
}
