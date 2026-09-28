package com.plainticker.mobile.ui.watchlist

import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.watched
import com.plainticker.mobile.watchlist.watchedAt
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every Pass 2 state of the Watchlist row and of the digest Panel, as sentences rather than as
 * pixels. What the screen places is [com.plainticker.mobile.ui.watchlist.WatchlistScreenTest]'s job.
 */
class WatchlistModelTest {

    private fun words(id: Int, vararg args: String) = Copy.Words(id, args.toList())

    // ---- The row --------------------------------------------------------------------------

    @Test
    fun `a watched ticker with a report date and a deep pool says both`() {
        val row = watchRow(
            watchedAt("TSLA", premiumPct = 0.09, nextReport = LocalDate.of(2026, 10, 22)),
        )

        assertEquals("TSLAx", row.symbol)
        assertEquals("TSLA", row.ticker)
        // Day first since 2026-09-26 (Fmt.dayMonth): one short date format app-wide.
        assertEquals(words(R.string.watchlist_row_reports, "22 Oct"), row.report)
        assertEquals(words(R.string.list_row_meta_premium, "+0.09%"), row.tracking)
    }

    @Test
    fun `no report date is a sentence, not a gap`() {
        val row = watchRow(watchedAt("AAPL", premiumPct = 0.01, nextReport = null))

        assertEquals(words(R.string.watchlist_row_no_date), row.report)
        assertEquals(words(R.string.list_row_meta_premium, "+0.01%"), row.tracking)
    }

    @Test
    fun `a ticker the analysis list no longer serves says that instead`() {
        val row = watchRow(watched("MCD", analyzed = false, nextReport = null))

        assertEquals(words(R.string.watchlist_row_unserved), row.report)
    }

    /**
     * QA 2026-09-26, D3. `WatchedTicker.analyzed` is false both when `/summary` genuinely no longer
     * carries a ticker and when `/summary` did not answer at all (`WatchlistFacts.kt`'s own rule:
     * a source that failed leaves every row's `analyzed` false, not just the ones actually dropped).
     * `analysisUnavailable` is the flag that tells the two apart; without it, an offline reader was
     * told a stock had been removed from coverage rather than that the app could not check.
     */
    @Test
    fun `offline, the same unanalyzed shape says the network failed, not that the ticker was dropped`() {
        val row = watchRow(watched("MCD", analyzed = false, nextReport = null), analysisUnavailable = true)

        assertEquals(words(R.string.watchlist_row_load_failed), row.report)
    }

    @Test
    fun `once summary answers again, the unanalyzed shape reads as a real drop`() {
        val row = watchRow(watched("MCD", analyzed = false, nextReport = null), analysisUnavailable = false)

        assertEquals(words(R.string.watchlist_row_unserved), row.report)
    }

    @Test
    fun `a thin pool states the pool where the premium would be`() {
        // APPx as measured on 2026-09-12: a pool of $34 behind an +89 percent quote.
        val row = watchRow(watchedAt("APP", premiumPct = 89.34, poolUsd = 34.0))

        assertEquals(words(R.string.list_row_meta_thin, "$34"), row.tracking)
    }

    @Test
    fun `a pool Jupiter did not report is unknown, never deep`() {
        val row = watchRow(watched("NVDA", priceUsd = 182.11, referencePriceUsd = 182.18, poolUsd = null))

        assertEquals(words(R.string.list_row_meta_pool_unknown), row.tracking)
    }

    @Test
    fun `a row Jupiter never priced has no tracking half at all`() {
        val row = watchRow(watched("AAPL", nextReport = LocalDate.of(2026, 10, 28)))

        assertNull(row.tracking)
        assertEquals(words(R.string.watchlist_row_reports, "28 Oct"), row.report)
    }

    @Test
    fun `a row with no token reads as its ticker`() {
        assertEquals("AAPL", watchRow(watched("AAPL", symbol = null)).symbol)
    }

    // ---- The digest Panel -----------------------------------------------------------------

    private val kyiv: java.time.ZoneId = java.time.ZoneId.of("Europe/Kyiv")

    @Test
    fun `no digest yet is one sentence that says when the first one lands`() {
        val panel = digestPanel(DigestRecord.NONE, kyiv)

        assertNull("nothing was produced, so there is no time to print", panel.producedAt)
        assertEquals(words(R.string.watchlist_digest_none), panel.body)
    }

    @Test
    fun `the Panel draws the stored digest as it was sent, stamped in the reader's own zone`() {
        val text = "3 stocks watched. NVDAx moved from -0.04% to -0.61% against its share's US price. TSLAx reports in 41 days."
        val record = DigestRecord(text = text, producedAtMillis = Instant.parse("2026-09-13T08:00:00Z").toEpochMilli())

        val panel = digestPanel(record, kyiv)
        assertEquals("13 Sep 2026 11:00", panel.producedAt)
        assertEquals(Copy.Raw(text), panel.body)

        // A different reader, a different stamp of the same instant: not silently still UTC.
        val newYork = digestPanel(record, java.time.ZoneId.of("America/New_York"))
        assertEquals("13 Sep 2026 04:00", newYork.producedAt)
    }

    @Test
    fun `a stored time with no text is still no digest`() {
        assertEquals(
            words(R.string.watchlist_digest_none),
            digestPanel(DigestRecord(producedAtMillis = 1L), kyiv).body,
        )
    }

    // ---- The footer -----------------------------------------------------------------------

    @Test
    fun `the footer says where the digest goes and when it was last looked for`() {
        val checked = Instant.parse("2026-09-13T05:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-09-13T08:00:00Z").toEpochMilli()

        val on = digestFooter(DigestRecord(lastCheckedAtMillis = checked), notificationsOn = true, nowMillis = now)
        assertEquals(words(R.string.watchlist_notifications_on), on.delivery)
        assertEquals(words(R.string.watchlist_checked, "3 h ago"), on.checked)

        val off = digestFooter(DigestRecord(lastCheckedAtMillis = checked), notificationsOn = false, nowMillis = now)
        assertEquals(words(R.string.watchlist_notifications_off), off.delivery)
    }

    @Test
    fun `before the first check there is no relative time to print`() {
        assertNull(digestFooter(DigestRecord.NONE, notificationsOn = true, nowMillis = 1L).checked)
    }

    // ---- The banner -----------------------------------------------------------------------

    @Test
    fun `each banner tier has its own sentence`() {
        assertEquals(words(R.string.detail_analysis_unavailable), bannerText(WatchlistBanner.AnalysisUnavailable))
        assertEquals(words(R.string.list_catalog_unavailable), bannerText(WatchlistBanner.CatalogUnavailable))
        assertEquals(words(R.string.list_prices_unavailable), bannerText(WatchlistBanner.PricesUnavailable))
    }
}
