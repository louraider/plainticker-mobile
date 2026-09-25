package com.plainticker.mobile.ui.watchlist

import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.WatchedTicker
import java.time.Instant
import java.time.ZoneId

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
    /**
     * The meta line, in the reader's own zone ([Fmt.localDateTime], not [Fmt.utc]: polish batch,
     * 2026-09-25), or null when there has never been a digest. Drawn in Amber's own type, not
     * mono; the field's own name never said "mono", only the composable that draws it did.
     */
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
 *
 * **QA 2026-09-26, D3.** [WatchedTicker.analyzed] is false in two different situations that used to
 * draw the same sentence: `/summary` answered and genuinely no longer carries this ticker (a real
 * delisting from the analyzed leaderboard, the one thing here a reader might act on), and `/summary`
 * did not answer at all (`WatchedFacts.analysisUnavailable`, `WatchlistFacts.kt`'s own doc comment
 * rule 1: "a source that did not answer is reported as a flag beside the rows"), which leaves every
 * row's `analyzed` false regardless of what the leaderboard actually carries. Offline, that read
 * [watchlist_row_unserved] as "Not in the analysis list," a sentence built for the first case,
 * stated as fact in the second where the app does not know it. [analysisUnavailable] is the flag
 * that tells them apart, threaded in from the same [com.plainticker.mobile.ui.watchlist.WatchlistUiState]
 * field both callers already read; when it is set, the row says the network failed instead of
 * implying the ticker was dropped.
 */
fun watchRow(row: WatchedTicker, analysisUnavailable: Boolean = false): WatchRow = WatchRow(
    ticker = row.ticker,
    symbol = row.display,
    company = row.company,
    report = when {
        // The network failed, not the leaderboard: say so honestly rather than implying a drop
        // this device has no way to have observed.
        !row.analyzed && analysisUnavailable -> words(R.string.watchlist_row_load_failed)
        // /summary did answer, and it genuinely no longer carries this ticker. That is not "no
        // date": it is the analysis being gone, and it is the one thing on this row a reader
        // might act on.
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
 *
 * [zone] is the reader's own, never read here (polish batch, 2026-09-25: this used to print
 * [Fmt.utc], a conversion no other absolute stamp on this screen asks a reader to do; the digest's
 * own successor, [com.plainticker.mobile.ui.you.DigestScreen]'s `digestStamp`, already read the
 * reader's own zone). [zone] is passed in rather than read from the system default here, the same
 * reason [Fmt.clock] and every other reader-zone function in this codebase stays pure.
 */
fun digestPanel(record: DigestRecord, zone: ZoneId): DigestPanel {
    val text = record.text
    val producedAt = record.producedAtMillis
    if (text == null || producedAt == null) {
        return DigestPanel(producedAt = null, body = words(R.string.watchlist_digest_none))
    }
    return DigestPanel(producedAt = Fmt.localDateTime(producedAt, zone), body = raw(text))
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
