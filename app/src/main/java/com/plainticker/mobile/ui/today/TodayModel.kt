package com.plainticker.mobile.ui.today

import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.NyseCalendar
import com.plainticker.mobile.data.xstocks.SessionPhase
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.watchlist.watchRow
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.watchlist.DigestRecord
import com.plainticker.mobile.watchlist.WatchedTicker
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId

/**
 * What Today says, in direction A, "One line, then yours" (the founder's pick, 2026-09-24): the
 * venue as one line in the reader's own time, the watched stocks first with one figure each and
 * that figure's meaning stated once, the digest as a single link, three Tracked rows that never
 * repeat a watched ticker, and Next up as one row. No footer count: Stocks' own segments carry it.
 *
 * The same split every other screen's model keeps: [com.plainticker.mobile.ui.watchlist.WatchlistViewModel]
 * decides the numbers, this file decides the sentence. Every function here is pure and total; the
 * reader's zone is passed in, never read, so a test can pin Kyiv, New York and Tokyo alike. A fact
 * that is not yet known returns null rather than a guess.
 */

/** One row of "Tracked today": an analyzed xStock whose pool clears the liquidity floor. */
data class TrackedRow(
    val ticker: String,
    val symbol: String?,
    val company: String?,
    /** Null only when Jupiter never sent a reference price; the pool still clears the floor. */
    val premiumPct: Double?,
    val poolUsd: Double,
) {
    val display: String get() = symbol ?: ticker

    /** The formatted premium, or the placeholder [Fmt] draws for any other missing figure. */
    val figure: String get() = premiumPct?.let { Fmt.percent(it) } ?: "-"
}

/** The single "Next up" row: the heaviest-staked ticker staked SKR has chosen to cover next. */
data class TodayLeader(
    val ticker: String,
    val symbol: String?,
    val company: String?,
    val weightRaw: BigInteger,
    val voters: Int,
) {
    val display: String get() = symbol ?: ticker

    /** "6,719 SKR", the same string and the same rounding the List's own leaders read. */
    val weight: Copy get() = words(R.string.next_up_weight, skrAmount(weightRaw))

    /** "1 voter", "3 voters": the same counted copy the List's leaders read, reused rather than forked. */
    val votersContext: Copy get() = counted(R.plurals.next_up_voters, voters, Fmt.count(voters))
}

/** A weight in SKR from its base units, the same six-decimal, one-kept-decimal rule List reads by. */
private fun skrAmount(raw: BigInteger): String =
    Fmt.tokenAmount(BigDecimal(raw).movePointLeft(SkrStakeBound.SKR_DECIMALS), maxDecimals = 1)

/**
 * Rows Today draws inline before "All N in Stocks" hands the rest over: three, down from six, so the
 * first viewport holds the reader's own stocks and a taste of the thesis rather than a second list.
 */
const val TrackedPreviewCount: Int = 3

// ---- The status line --------------------------------------------------------------------------

/**
 * The venue in one line, in the reader's own time: "NYSE open. Closes at 23:00 your time",
 * "NYSE closed. Opens Monday at 16:30 your time". Null while no catalog has answered at all:
 * "the NYSE is closed" is not a sentence this screen can state about a venue it has not read.
 *
 * The state comes from [market] (the venue's block while it is fresh, the calendar after it
 * expires, [com.plainticker.mobile.data.xstocks.MarketHours.sessionAt]); which side of the
 * session a closed venue is on, and when it next opens, comes from the calendar reading it
 * carries. Pre-market and after hours are their own sentences and say that tokens still trade on
 * thinner pools, because a beginner looking at a moving token price at 20:00 New York time is
 * owed the reason it may sit further from the share.
 */
fun statusLine(market: MarketStatus?, nowMillis: Long, zone: ZoneId): Copy? {
    val m = market ?: return null
    val session = m.session ?: NyseCalendar.session(nowMillis)
    if (m.regularSession) {
        val venueClose = m.nextChangeAtMillis.takeIf { m.source == MarketSource.VENUE }
        val closeAt = venueClose ?: session.nextCloseMillis ?: return words(R.string.today_status_open_untimed)
        val time = Fmt.clock(closeAt, zone)
        return when {
            m.source == MarketSource.LOCAL_SCHEDULE -> words(R.string.today_status_open_calendar, time)
            session.earlyClose -> words(R.string.today_status_open_short_day, time)
            else -> words(R.string.today_status_open, time)
        }
    }
    val openAt = session.nextOpenMillis
    val time = Fmt.clock(openAt, zone)
    val days = Fmt.daysAhead(openAt, nowMillis, zone)
    val weekday = Fmt.weekday(openAt, zone)
    return when {
        session.phase == SessionPhase.PRE_MARKET -> words(R.string.today_status_pre, time)
        session.phase == SessionPhase.AFTER_HOURS -> dayed(
            days, time, weekday,
            R.string.today_status_after_today, R.string.today_status_after_tomorrow, R.string.today_status_after_day,
        )
        session.holiday -> dayed(
            days, time, weekday,
            R.string.today_status_holiday_today, R.string.today_status_holiday_tomorrow, R.string.today_status_holiday_day,
        )
        else -> dayed(
            days, time, weekday,
            R.string.today_status_closed_today, R.string.today_status_closed_tomorrow, R.string.today_status_closed_day,
        )
    }
}

/** The same sentence in its today, tomorrow or weekday form, by calendar days in the reader's zone. */
private fun dayed(days: Long, time: String, weekday: String, today: Int, tomorrow: Int, other: Int): Copy = when {
    days <= 0L -> words(today, time)
    days == 1L -> words(tomorrow, time)
    else -> words(other, weekday, time)
}

/** Whether the status line is lit: only while the exchange's own session is on. */
fun statusLive(market: MarketStatus?): Boolean = market?.regularSession == true

/**
 * The market hours sheet's source note: drawn only while the line reads the calendar because the
 * venue's block has expired or never came, never when the venue itself answered.
 */
fun statusFromCalendar(market: MarketStatus?): Boolean = market?.source == MarketSource.LOCAL_SCHEDULE

/**
 * How old the analysis and the prices are, read together or not at all: naming one age and staying
 * silent about the other would read as the other one being unknown. Drawn in the market hours
 * sheet, off the first viewport.
 */
fun freshnessSentence(analysisAtMillis: Long?, pricesAtMillis: Long?, nowMillis: Long): Copy? {
    val analysisAt = analysisAtMillis ?: return null
    val pricesAt = pricesAtMillis ?: return null
    return words(
        R.string.today_venue_freshness,
        Fmt.relativeAgo(Instant.ofEpochMilli(analysisAt), Instant.ofEpochMilli(nowMillis)),
        Fmt.relativeAgo(Instant.ofEpochMilli(pricesAt), Instant.ofEpochMilli(nowMillis)),
    )
}

// ---- Watched ----------------------------------------------------------------------------------

/**
 * What every figure on Today means, said once above the watched rows instead of "vs NYSE close"
 * on every row: against the share price now while the exchange trades, against the last close
 * otherwise (DESIGN.md's price label, `MarketStatus.priceLabel`, in a sentence).
 */
fun figureMeaning(market: MarketStatus?): Copy =
    words(if (market?.regularSession == true) R.string.today_figure_live else R.string.today_figure_close)

/**
 * One watched ticker on Today: one figure, the premium, and only above the liquidity floor. Below
 * it, or with no depth reported, the pool states itself beside the report date instead of a figure
 * (DESIGN.md section 1.1), in the list's own words.
 */
data class TodayWatchRow(
    val ticker: String,
    val symbol: String,
    val company: String?,
    /** The signed premium, or null: a thin or unpriced pool draws no figure at all. */
    val figure: String?,
    /** The next report, or why there is no date. */
    val report: Copy,
    /** The pool's own sentence when there is no figure to draw; null otherwise. */
    val poolNote: Copy?,
)

fun todayWatchRow(row: WatchedTicker, analysisUnavailable: Boolean = false): TodayWatchRow {
    val base = watchRow(row, analysisUnavailable)
    val quality = row.tracking
    val premium = (quality as? TrackingQuality.Tracked)?.premiumPct
    return TodayWatchRow(
        ticker = row.ticker,
        symbol = base.symbol,
        company = base.company,
        figure = premium?.let { Fmt.percent(it) },
        report = base.report,
        poolNote = if (premium != null) null else base.tracking,
    )
}

// ---- Digest -----------------------------------------------------------------------------------

/**
 * The digest as one line and a link: "Last digest today at 09:49." with "Read it" beside it, in
 * the reader's own time. Before the first digest, the sentence that says when it lands, and no
 * link, because there is nothing yet to read.
 */
fun digestLink(record: DigestRecord, nowMillis: Long, zone: ZoneId): Copy {
    val at = record.producedAtMillis
    if (record.text == null || at == null) return words(R.string.today_digest_none)
    val time = Fmt.clock(at, zone)
    return if (Fmt.daysAhead(at, nowMillis, zone) == 0L) {
        words(R.string.today_digest_link_today, time)
    } else {
        words(R.string.today_digest_link_day, Fmt.weekday(at, zone), time)
    }
}

/** Whether the digest line carries its "Read it" link: only once there is a digest to read. */
fun digestReadable(record: DigestRecord): Boolean = record.text != null && record.producedAtMillis != null

// ---- Tracked today ----------------------------------------------------------------------------

/**
 * The Tracked rows Today draws: deepest pool first, never a ticker the reader already watches (that
 * one has its figure in Watched, and one ticker is priced once per screen), at most
 * [TrackedPreviewCount] of them.
 */
fun trackedPreview(tracked: List<TrackedRow>, watched: Set<String>): List<TrackedRow> {
    val keys = watched.mapTo(HashSet()) { it.trim().uppercase() } // lint-allow uppercase: map key
    return tracked.filter { it.ticker.trim().uppercase() !in keys }.take(TrackedPreviewCount) // lint-allow uppercase: map key
}

/** "All 20 in Stocks", the link under Tracked that hands the rest to Stocks. */
fun trackedAllCopy(trackedCount: Int): Copy =
    words(R.string.today_tracked_all, Fmt.count(trackedCount)) // lint-allow count: a total, no noun agreement

// ---- Next up ----------------------------------------------------------------------------------

/**
 * "Chosen by staked SKR. Round closes Monday at 03:00 your time.", the same round
 * com.plainticker.mobile.ui.vote.VoteScreen names, in the reader's own time rather than UTC.
 */
fun nextUpLede(round: VoteRound?, zone: ZoneId): Copy? {
    if (round == null) return null
    val closesAt = round.closesAtInstant() ?: return null
    val millis = closesAt.toEpochMilli()
    return words(R.string.today_next_up_lede, Fmt.weekday(millis, zone), Fmt.clock(millis, zone))
}
