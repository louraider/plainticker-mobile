package com.plainticker.mobile.ui.list

import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.data.xstocks.MarketHours
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketState
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.TradingPeriod
import com.plainticker.mobile.lint.KotlinScan
import com.plainticker.mobile.repo.xStock
import com.plainticker.mobile.repo.xStockTrading
import com.plainticker.mobile.ui.components.ValueSubWidth
import com.plainticker.mobile.ui.components.valueSubWidth
import com.plainticker.mobile.ui.detail.DetailBanner
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things the finished List gets wrong that no row and no banner could get wrong alone.
 *
 * `ListRow` is right about right-aligning its value group, and `ListViewModel` is right about
 * every banner it knows. What neither can see is the column of numerals the rows make together,
 * and what is missing from a slot whose contents are defined elsewhere. Both were found by
 * reading the whole screen on a phone (docs/design-review-2026-09-13.md, findings 5 and 8).
 */
class ListFinishedScreenTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun source(path: String): String =
        KotlinScan(File(module, "src/main/java/com/plainticker/mobile/" + path).readText()).code

    private val nowMillis = 1_789_419_600_000L

    // ---- Finding 5: the column of numerals ------------------------------------------------------

    /**
     * The three state words as the Seeker drew them, measured from a uiautomator dump of the
     * settled list on 2026-09-13 at 480dpi: "strong" 112 physical pixels, "weak" 92, "fair" 63.
     */
    private val wordWidths = mapOf("strong" to 37.33.dp, "weak" to 30.67.dp, "fair" to 21.0.dp)

    @Test
    fun `the state word no longer decides where the number begins`() {
        // What the review measured. The word and the number were right-aligned as one group, so
        // the number's right edge moved by the difference between the widest word and the
        // narrowest: "84" ended at x1004, "79" at x1053, "72" at x1024, a 49px jog down the one
        // column a reader scans, in the app whose type system was chosen for tabular numerals.
        val spread = wordWidths.values.max() - wordWidths.values.min()
        assertEquals("the jog the review measured, in dp", 16.33f, spread.value, 0.01f)

        // Every word now sits in one column, so the number's right edge is one number. The column
        // has to carry the widest of them, or the fix trades a jog for a clipped word.
        wordWidths.forEach { (word, width) ->
            assertTrue("$word does not fit the column and would clip", width <= ValueSubWidth)
        }
        assertTrue("the column must not be wider than it needs", ValueSubWidth <= 48.dp)
    }

    @Test
    fun `the column grows with the reader's type and never shrinks below it`() {
        assertEquals(ValueSubWidth, valueSubWidth(1f))
        // Android 14 grows 13sp by about 1.19 at a scale of 1.3, so scaling by the raw factor
        // leaves room rather than clipping (docs/data-map.md, the device smoke walk).
        assertTrue("1.3 must leave room for the widest word", wordWidths.values.max() * 1.19f <= valueSubWidth(1.3f))
        // A reader who shrinks the type does not need the column to shrink with them, and the
        // largest scale must not be allowed to eat the ticker beside it.
        assertEquals(ValueSubWidth, valueSubWidth(0.85f))
        assertEquals(ValueSubWidth * 2f, valueSubWidth(3f))
    }

    @Test
    fun `the row gives the number its own alignment and the list keeps it open`() {
        val row = source("ui/components/ListRow.kt")
        assertTrue("the word must sit in a column of its own", ".width(valueSubWidth(" in row)
        assertTrue("the column must follow the reader's font scale", "LocalDensity.current.fontScale" in row)
        assertTrue("a row with no word must be able to keep the column", "reserveValueSub" in row)

        // Every analyzed row keeps it open, including one with no state word, because the
        // composites under that heading are one column to the reader scanning them.
        val screen = source("ui/list/ListScreen.kt")
        val analyzed = screen.substring(
            screen.indexOf("private fun AnalyzedRow("),
            screen.indexOf("private fun PriceOnlyRow("),
        )
        assertTrue("an analyzed row must reserve the word's column", "reserveValueSub = true" in analyzed)

        // The untracked tail is its own section of prices with no word at all, so it stays flush
        // right rather than carrying an empty column it never fills.
        val priceOnly = screen.substring(screen.indexOf("private fun PriceOnlyRow("))
        assertTrue(
            "a price-only row must not reserve a column it never fills",
            "reserveValueSub" !in priceOnly.substringBefore("private fun "),
        )
    }

    // ---- Finding 8: the caveat Detail pays and the List did not ---------------------------------

    private fun list(market: MarketStatus?) = ListUiState(market = market)

    private fun venue(state: MarketState) = MarketStatus(
        state = state,
        source = MarketSource.VENUE,
        venueOpen = state == MarketState.REGULAR,
        nextChangeAtMillis = null,
    )

    private fun guessed(state: MarketState) = venue(state).copy(source = MarketSource.LOCAL_SCHEDULE)

    @Test
    fun `the List raises the same hours banner Detail does`() {
        assertEquals(ListBanner.MarketClosed, list(venue(MarketState.CLOSED)).banner)
        assertEquals(ListBanner.MarketClosedLocal, list(guessed(MarketState.CLOSED)).banner)
        assertEquals(ListBanner.MarketOpenLocal, list(guessed(MarketState.REGULAR)).banner)

        // An open exchange the venue itself reported needs no sentence: the reference the rows
        // quote is simply the live one, and there is nothing to caveat.
        assertNull(list(venue(MarketState.REGULAR)).banner)
        // No catalog has been read, so nothing is known about the venue and nothing is claimed.
        assertNull(list(null).banner)

        // The overnight and extended sessions are the venue trading while the exchange is shut,
        // so the NYSE close is still the newest reference and the caveat is still owed.
        assertEquals(ListBanner.MarketClosed, list(venue(MarketState.EXTENDED)).banner)
        assertEquals(ListBanner.MarketClosed, list(venue(MarketState.OVERNIGHT)).banner)
    }

    @Test
    fun `the words are Detail's own, out of the same strings`() {
        // The point of the finding is that a reader meets this caveat on one screen and not the
        // other. Two screens wording it differently would be the same finding wearing a hat.
        assertEquals(R.string.banner_market_closed, DetailBanner.CLOSED.text)
        assertEquals(R.string.banner_market_closed_local, DetailBanner.CLOSED_LOCAL.text)
        assertEquals(R.string.banner_market_open_local, DetailBanner.OPEN_LOCAL.text)

        val screen = source("ui/list/ListScreen.kt")
        listOf("banner_market_closed", "banner_market_closed_local", "banner_market_open_local")
            .forEach { assertTrue("the List must draw R.string.$it", "R.string.$it" in screen) }
        // A halt is one issuer stopping one asset. A list of 157 rows never says it.
        assertTrue("a halt is Detail's to state", "banner_market_halted" !in screen)
    }

    @Test
    fun `the hours tier sits where DESIGN section 4 puts it`() {
        val shut = venue(MarketState.CLOSED)

        // offline > stale > hours > device.
        assertEquals(ListBanner.Unavailable, list(shut).copy(failed = true).banner)
        assertEquals(
            ListBanner.Snapshot(null),
            list(shut).copy(fromSnapshot = true, snapshotBannerDue = true).banner,
        )
        assertEquals(ListBanner.Stale(7), list(shut).copy(allStaleDays = 7).banner)
        assertEquals(ListBanner.MarketClosed, list(shut).copy(catalogUnavailable = true).banner)
        assertEquals(ListBanner.MarketClosed, list(shut).copy(pricesPartial = true).banner)
        // With the exchange open the device tier gets the slot back.
        assertEquals(
            ListBanner.PricesPartial,
            list(venue(MarketState.REGULAR)).copy(pricesPartial = true).banner,
        )
    }

    @Test
    fun `the venue behind a catalog is read from the catalog, and a halt is not the exchange`() {
        val open = Trading(currentPeriod = TradingPeriod.MARKET, openNow = true)
        val shut = Trading(currentPeriod = TradingPeriod.CLOSED, openNow = false)

        // The first block that answers answers for all of them: they describe one exchange.
        val assets = listOf(xStockTrading("AAPLx", "AAPL", "m1", open), xStockTrading("TSLAx", "TSLA", "m2", shut))
        assertEquals(MarketState.REGULAR, MarketHours.ofCatalog(assets, nowMillis)!!.state)
        assertEquals(MarketSource.VENUE, MarketHours.ofCatalog(assets, nowMillis)!!.source)

        // A halted asset must not tell 157 rows the market is shut: the halt is the issuer
        // stopping one token, and the block it carries still describes the exchange.
        val halted = xStockTrading("MSTRx", "MSTR", "m3", open.copy(isTradingHalted = true))
        assertEquals(MarketState.REGULAR, MarketHours.ofCatalog(listOf(halted), nowMillis)!!.state)

        // No block anywhere: the weekday schedule decides and says that it did. 2026-09-14 at
        // 17:00 in New York is a Monday after the close.
        val silent = listOf(xStock("AAPLx", "AAPL", "m1"))
        val fallback = MarketHours.ofCatalog(silent, nowMillis)!!
        assertEquals(MarketSource.LOCAL_SCHEDULE, fallback.source)
        assertEquals(MarketState.CLOSED, fallback.state)

        // No catalog at all is not an answer about the venue.
        assertNull(MarketHours.ofCatalog(emptyList(), nowMillis))
    }
}
