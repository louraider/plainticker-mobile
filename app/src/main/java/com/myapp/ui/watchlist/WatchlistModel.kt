package com.myapp.ui.watchlist

import com.myapp.R
import com.myapp.data.jupiter.TrackingQuality
import com.myapp.ui.Copy
import com.myapp.ui.Fmt
import com.myapp.ui.raw
import com.myapp.ui.words
import com.myapp.watchlist.DigestRecord
import com.myapp.watchlist.WatchedTicker
import java.time.Instant

/**
 * What the Watchlist screen says, decided away from the composition (task T12, design task DT8).
 *
 * The same split the List, Detail and Portfolio keep: a model picks the sentence, a composable
 * places it. Three rules it exists to keep:
 *
 * 1. **The row's tracking half is the list's.** It comes from [TrackingQuality] and reads out of
 *    the same string resources, so a pool of $34 says the same thing on every screen it appears on.
 * 2. **A missing report date is a sentence, not a gap.** A payload with no `nextEarningsDate` says
 *    so, and a ticker `/summary` no longer carries says that instead, because those are different
 *    facts and a reader who watched the ticker is owed the difference.
 * 3. **The digest on screen is the digest that was sent.** The Panel draws the stored string as
 *    raw text: the screen never re-derives it, so it cannot say something the notification did not.
 */

// ---- The pieces the screen draws ---------------------------------------------------------------

/** One watched ticker, in the parts a 64dp row draws and a screen reader speaks in order. */
data class WatchRow(
    /** Underlying equity ticker: the Detail route and the unwatch key, never drawn. */
    val ticker: String,
    /** The token symbol, drawn left in mono, or the ticker while the catalog is out. */
    val symbol: String,
    val company: String?,
    /** The first half of the meta line: the next report, or why there is no date to give. */
    val report: Copy,
    /** The second half: the premium, the pool sentence below the floor, or nothing at all. */
    val tracking: Copy?,
)

/** The digest Panel: when it was produced above, what it said below. */
data class DigestPanel(
    /** The mono meta line, or null when there has never been a digest. */
    val producedAt: String?,
    val body: Copy,
)

/** The two lines under the Panel: where a digest is delivered, and when one was last looked for. */
data class DigestFooter(
    val delivery: Copy,
    /** Null until a check has run at all; there is no honest relative time before the first one. */
    val checked: Copy?,
)

// ---- The rules ----------------------------------------------------------------------------------

/**
 * One watched ticker as a row.
 *
 * The report half and the tracking half are independent: a ticker with no date still shows its
 * premium, and a token below the liquidity floor still shows its report date.
 */
fun watchRow(row: WatchedTicker): WatchRow = WatchRow(
    ticker = row.ticker,
    symbol = row.display,
    company = row.company,
    report = when {
        // The leaderboard has dropped it. That is not "no date": it is the analysis being gone,
        // and it is the one thing on this row a reader might act on.
        !row.analyzed -> words(R.string.watchlist_row_unserved)
        row.nextReport != null -> words(R.string.watchlist_row_reports, Fmt.monthDay(row.nextReport))
        else -> words(R.string.watchlist_row_no_date)
    },
    tracking = trackingCopy(row.tracking),
)

/**
 * The quote's half of the meta line, in the list's own words (DESIGN.md section 1.1): the signed
 * premium above the liquidity floor, the pool below it, and the plain statement that Jupiter
 * reported no depth. Null when Jupiter did not price the token at all, which the banner explains.
 */
private fun trackingCopy(quality: TrackingQuality?): Copy? = when (quality) {
    is TrackingQuality.Tracked ->
        quality.premiumPct?.let { words(R.string.list_row_meta_premium, Fmt.percent(it)) }

    is TrackingQuality.Thin ->
        words(R.string.list_row_meta_thin, Fmt.compactMoney(quality.poolUsd, roundDown = true))

    TrackingQuality.Untracked -> words(R.string.list_row_meta_pool_unknown)

    null -> null
}

/**
 * The Panel. A digest that has never been produced is not an empty card: it is one sentence that
 * says when the first one lands, which is the only thing a reader who has just watched their first
 * stock actually wants to know.
 */
fun digestPanel(record: DigestRecord): DigestPanel {
    val text = record.text
    val producedAt = record.producedAtMillis
    if (text == null || producedAt == null) {
        return DigestPanel(producedAt = null, body = words(R.string.watchlist_digest_none))
    }
    return DigestPanel(producedAt = Fmt.utc(producedAt), body = raw(text))
}

/**
 * The lines under the Panel. [notificationsOn] is what this device will actually do with the next
 * digest, not what the app would like it to do: a refused permission says so plainly, once, beside
 * the digest it is still getting.
 */
fun digestFooter(
    record: DigestRecord,
    notificationsOn: Boolean,
    nowMillis: Long,
): DigestFooter = DigestFooter(
    delivery = words(
        if (notificationsOn) R.string.watchlist_notifications_on else R.string.watchlist_notifications_off,
    ),
    checked = record.lastCheckedAtMillis?.let {
        words(R.string.watchlist_checked, Fmt.relativeAgo(Instant.ofEpochMilli(it), Instant.ofEpochMilli(nowMillis)))
    },
)

/** What the one banner slot says. The order of the tiers is [WatchlistUiState.banner]'s. */
fun bannerText(banner: WatchlistBanner): Copy = when (banner) {
    WatchlistBanner.AnalysisUnavailable -> words(R.string.detail_analysis_unavailable)
    WatchlistBanner.CatalogUnavailable -> words(R.string.list_catalog_unavailable)
    WatchlistBanner.PricesUnavailable -> words(R.string.list_prices_unavailable)
}
