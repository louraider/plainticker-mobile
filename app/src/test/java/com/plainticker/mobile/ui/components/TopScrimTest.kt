package com.plainticker.mobile.ui.components

import androidx.compose.ui.unit.dp
import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hero and the system clock, which is a property of a whole screen in a whole window.
 *
 * `Insets.kt` decided that content scrolls under a transparent status bar and nothing is sticky,
 * and every component on the way passed its own review, because none of them is wrong. What was
 * never looked at is the result: every canvas artboard is an 890dp content frame with no system
 * bars drawn, so nobody saw the 64sp Ink ticker and the white clock land in the same pixels. On
 * the Seeker on 2026-09-13 that read "NVDAx" over "11:40".
 *
 * These tests pin the fix and, more importantly, pin the two things about it that a later edit
 * would get wrong: that the scrim covers the whole band the clock is drawn in, and that it never
 * becomes a header.
 */
class TopScrimTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(path: String): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/" + path).readText()).code

    private fun count(source: String, marker: String): Int = source.split(marker).size - 1

    /**
     * The Seeker's own status bar, measured from a uiautomator dump on 2026-09-13: the tab labels
     * begin at y314 of 2670 physical pixels at 480dpi, 56dp of TopBar below the inset, which puts
     * the inset at 36dp. The clock inside it spans y38 to y70 physical, which is 12.7dp to 23.3dp.
     */
    private val seekerInset = 36.dp
    private val clockTop = 12.7.dp
    private val clockBottom = 23.3.dp

    // ---- The band the clock is drawn in ---------------------------------------------------------

    @Test
    fun `the scrim is fully opaque everywhere the clock is drawn`() {
        val band = seekerInset + ScrimFade
        val opaqueTo = scrimOpaqueFraction(seekerInset)
        assertEquals(36f / 52f, opaqueTo, 1e-6f)

        // The whole clock, top and bottom, lies inside the opaque part rather than in the fade.
        // This is the assertion the finding is about: a fade that starts too high leaves the
        // hero showing through the clock, which is the state that shipped.
        assertTrue("the clock starts inside the fade", clockTop / band < opaqueTo)
        assertTrue("the clock ends inside the fade", clockBottom / band < opaqueTo)

        // And the scrim ends: it is a band, not a background. A phone with no status bar gets
        // the fade alone rather than a gradient with two stops in the same place.
        assertEquals(0f, scrimOpaqueFraction(0.dp), 0f)
        // The ceiling is the wordmark, not taste. TopBar is 56dp after the inset with its 15sp
        // label centred, so the topmost ink of "PlainTicker" is about 18dp into it. A fade that
        // reached it would tint the wordmark at rest, and at rest this band must be invisible.
        assertTrue("a softer fade would tint the wordmark at rest", ScrimFade < 18.dp)
        assertTrue(ScrimFade > 0.dp)
    }

    @Test
    fun `the scrim is Canvas over Canvas, so at rest it changes nothing`() {
        val insets = source("ui/components/Insets.kt")
        val scrim = insets.substring(insets.indexOf("fun TopScrim("), insets.indexOf("val ScrimFade"))

        // One colour, the page background, falling to nothing. Anything else would be a bar: an
        // Elevated band, a Line edge or a second colour all read as chrome the canvas never drew.
        assertTrue("the scrim must paint the page background", "Canvas" in scrim)
        // Twice: the band with a status bar under it, and the one without. Both end at nothing,
        // so neither can leave a hard edge where the scrim stops.
        assertEquals("every branch must fall to nothing", 2, count(scrim, "Color.Transparent"))
        listOf("Elevated", "LineStrong", "Ink", "Accent", "alpha =").forEach {
            assertTrue("the scrim must not paint $it", it !in scrim)
        }
    }

    // ---- It is a scrim, and never a header --------------------------------------------------------

    @Test
    fun `the scrim holds nothing, so nothing became sticky`() {
        val insets = source("ui/components/Insets.kt")
        val scrim = insets.substring(insets.indexOf("fun TopScrim("), insets.indexOf("val ScrimFade"))

        // DESIGN.md sections 4 and 5: the header scrolls away, nothing is sticky, no bottom bar.
        // A band that draws no text, takes no touch and says nothing to a screen reader is not a
        // header however long it stays on screen.
        listOf("Text(", "TopBar(", "clickable", "TextAction", "semantics", "pointerInput").forEach {
            assertTrue("a scrim that draws $it is a header", it !in scrim)
        }
    }

    @Test
    fun `every surface that scrolls under the status bar draws exactly one scrim`() {
        // The five bar destinations share one host, so the host owns the scrim once and the five
        // cannot drift apart. Detail is its own surface and owns its own.
        listOf("ui/home/HomeScreen.kt", "ui/detail/DetailScreen.kt").forEach { path ->
            assertEquals("$path draws no scrim over its scroll", 1, count(source(path), "TopScrim("))
        }
        listOf(
            "ui/list/ListScreen.kt",
            "ui/vote/VoteScreen.kt",
            "ui/portfolio/PortfolioScreen.kt",
            "ui/watchlist/WatchlistScreen.kt",
            "ui/today/TodayScreen.kt",
            "ui/stocks/StocksScreen.kt",
        ).forEach { path ->
            assertEquals("$path must leave the scrim to its host", 0, count(source(path), "TopScrim("))
        }

        // Nothing else moved: the header is still inside the scroll on both surfaces, and neither
        // gained a sticky item or a z-order.
        listOf("ui/home/HomeScreen.kt", "ui/detail/DetailScreen.kt").forEach { path ->
            val screen = source(path)
            assertEquals("$path must not stack anything", 0, count(screen, "zIndex("))
            assertEquals("$path must not pin a header", 0, count(screen, "stickyHeader"))
        }
        assertEquals("Detail still has one scroll container", 1, count(source("ui/detail/DetailScreen.kt"), "verticalScroll("))
    }
}
