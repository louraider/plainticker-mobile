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
 * 3. the List behind the panel is decoration, dimmed and cleared from the semantics tree, so the
 *    button is the only thing on the screen a finger or a switch can reach.
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

    @Test
    fun `every sentence on the screen comes from strings xml`() {
        listOf(
            "app_name", "onboarding_headline", "onboarding_body_chain", "onboarding_body_fundamentals",
            "onboarding_body_disclaimer", "onboarding_certify", "onboarding_continue",
            "tab_list", "tab_portfolio", "tab_watchlist",
            "list_heading_analyzed", "list_row_meta_premium", "list_row_meta_join",
        ).forEach { name ->
            assertTrue("$name is not declared in strings.xml", """name="$name"""" in stringsXml)
            assertTrue("OnboardingScreen.kt does not read R.string.$name", "R.string.$name" in scan.code)
        }
        // The backdrop's Today strip counts what is watched, so it reads the List's counted copy.
        assertTrue("list_today is not declared as a plurals", """<plurals name="list_today">""" in stringsXml)
        assertTrue("OnboardingScreen.kt does not read R.plurals.list_today", "R.plurals.list_today" in scan.code)
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
        assertTrue("the tab row behind the panel is still selectable", "onSelect = null" in scan.code)
        val handlers = Regex("""\bonClick\s*=\s*(\w+)""").findAll(scan.code).map { it.groupValues[1] }.toList()
        assertEquals("the button is the only click handler on the screen", listOf("onContinue"), handlers)
    }
}
