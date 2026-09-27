package com.plainticker.mobile.ui.today

import com.plainticker.mobile.R
import com.plainticker.mobile.data.jupiter.PriceEntry
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What Today says, in direction A, "One line, then yours" (the founder's pick, 2026-09-24): the
 * venue as one line in the reader's own time, the watched stocks first with one figure each and
 * that figure's meaning stated once, the digest as a single link, "Reports this week" (the
 * founder's pick of option B, 2026-09-26, replacing "Tracked today") marking a watched ticker
 * rather than hiding it, and Next up as one row. No footer count: Stocks' own segments carry it.
 *
 * The same split every other screen's model keeps: [com.plainticker.mobile.ui.watchlist.WatchlistViewModel]
 * decides the numbers, this file decides the sentence. Every function here is pure and total; the
 * reader's zone is passed in, never read, so a test can pin Kyiv, New York and Tokyo alike. A fact
 * that is not yet known returns null rather than a guess.
 */

/**
 * One row of "Reports this week" (the founder's pick of option B off the designer's page, dated
 * 2026-09-26, replacing "Tracked today": a beginner reading NVDAx at +0.21% had no way to tell
 * what "tracked" meant or why the list was there, and a report date is a plain fact instead of a
 * number that reads like a tip). A covered company whose next report [date] the server sent on
 * `/summary`, joined against the catalog the same way the retired Tracked row was.
 */
data class ReportRow(
    val ticker: String,
    val symbol: String?,
    val company: String?,
    /** A US Eastern calendar day, never re-zoned; see [reportsThisWeek]'s own doc comment. */
    val date: LocalDate,
    /** true confirmed, false estimated, null unknown. Only an explicit false reads as "estimated". */
    val confirmed: Boolean?,
) {
    val display: String get() = symbol ?: ticker

    /** Only an explicit false earns the word: an unknown confirmation is not stated as a guess. */
    val estimated: Boolean get() = confirmed == false

    /** "Tuesday 29 Sep": the weekday and the bare calendar day, both read off [date] itself. */
    val dateLabel: String get() = "${Fmt.weekday(date)} ${Fmt.dayMonth(date)}"
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
 * Rows "Reports this week" draws inline before the link to Stocks hands the rest over: about
 * five, so the first viewport holds the reader's own stocks and a taste of the week rather than a
 * second long list.
 */
const val ReportsPreviewCount: Int = 5

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

// ---- Reports this week -------------------------------------------------------------------------

/**
 * The coming Sunday, inclusive of [today] itself when [today] already is one: the last day
 * "this week" reaches ([reportsThisWeek]'s own doc comment has the rule this closes over).
 */
fun weekEnd(today: LocalDate): LocalDate = today.plusDays(((7 - today.dayOfWeek.value) % 7).toLong())

/**
 * "Reports this week": [rows] (already joined against the catalog, one per covered company that
 * carries a next-report date) narrowed to today through the coming Sunday, in the reader's own
 * local week, sorted by date then ticker.
 *
 * **Two deliberate choices, both stated once because getting either wrong reads as a bug on a
 * calendar screen.**
 *
 * **The report date is never re-zoned.** `next_report_date` is a US Eastern calendar day the
 * server sends as a bare string ("2026-09-29"), not a moment in time, and [SummaryRow.nextReportLocalDate]
 * parses it as a [LocalDate] for exactly that reason. Converting it into an [Instant] and back out
 * in the reader's own zone would be the one operation guaranteed to move a date that already is
 * what it is: a Friday report reread near midnight in a zone hours off New York could print as
 * Thursday or Saturday depending on which side of that gap the reader stands, and this app has no
 * way to tell a genuine change of day from an artifact of the conversion. So this function compares
 * one bare [LocalDate] against another and nothing here ever touches a clock.
 *
 * **"This week" is the reader's own local week, not New York's.** [today] is the reader's own
 * calendar day (their zone, not US Eastern), because a reader plans a week by their own calendar,
 * the same reason [Fmt.weekday] and every other reader-zone function in this file take the zone as
 * a parameter rather than assuming one. The gap this leaves is real and is left open on purpose,
 * not closed: a report New York dates the coming Monday can still read as "next week" here for a
 * reader whose own Sunday has not yet turned, and the reverse near the boundary for a reader far
 * ahead of New York. Closing it would require deciding whose midnight a bare calendar day belongs
 * to, which is exactly the re-zoning the rule above refuses to do; the honest position is a plain
 * date compared against a plain date, stated as what it is instead of quietly guessed at.
 *
 * **No late-Friday roll-forward, but a real one across the weekend.** The window never reaches
 * into next week early on a weekday, even late on a Friday: "late" has no single answer once a
 * reader's zone and New York's trading day are two different clocks, and rolling a weekday forward
 * would trade an honest empty state for a guess. **Saturday and Sunday are the one deliberate
 * exception**, added after the real device caught it: on Saturday 26 Sep 2026 the window was
 * "today through the coming Sunday," which on a Saturday is only Saturday and Sunday themselves,
 * so the block read "No covered company reports this week" with an empty list every single
 * weekend, exactly when a reader has time to look. [reportsWindowStart] moves the window's start
 * to the coming Monday on those two days only ([reportsIsNextWeek]), so it reaches next Monday
 * through next Sunday instead, and `TodayReportsBlock` (`TodayScreen.kt`) swaps the heading to
 * "Reports next week" to match. Monday through Friday are untouched: the window is still today
 * through the coming Sunday, read by this same function.
 */
fun reportsThisWeek(rows: List<ReportRow>, today: LocalDate): List<ReportRow> {
    val start = reportsWindowStart(today)
    val end = weekEnd(start)
    return rows.filter { it.date in start..end }.sortedWith(compareBy({ it.date }, { it.ticker }))
}

/**
 * Whether [today] itself is the reader's own Saturday or Sunday: the one condition that moves
 * [reportsThisWeek]'s window to next Monday through next Sunday and swaps the section's heading
 * from "Reports this week" to "Reports next week" ([reportsThisWeek]'s own doc comment has the
 * full reasoning, off the real device that caught the bug this closes).
 */
fun reportsIsNextWeek(today: LocalDate): Boolean =
    today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY

/**
 * The first day [reportsThisWeek]'s window reaches: [today] itself Monday through Friday, or the
 * coming Monday when [today] is the reader's own Saturday or Sunday ([reportsIsNextWeek]). Never
 * re-zoned, the same as every other date this file compares: [today] is already the reader's own
 * local calendar day by the time it reaches here.
 */
private fun reportsWindowStart(today: LocalDate): LocalDate = when (today.dayOfWeek) {
    DayOfWeek.SATURDAY -> today.plusDays(2)
    DayOfWeek.SUNDAY -> today.plusDays(1)
    else -> today
}

/**
 * The soonest known report after this week (or, on a weekend, next week) closes, read off every
 * candidate [rows] carries (not only the ones [reportsThisWeek] kept), for the empty state's own
 * second sentence: a quiet week still says when the next one is, if this app knows.
 */
fun nextReportAfterThisWeek(rows: List<ReportRow>, today: LocalDate): ReportRow? {
    val end = weekEnd(reportsWindowStart(today))
    return rows.filter { it.date > end }.minWithOrNull(compareBy({ it.date }, { it.ticker }))
}

/**
 * The empty state: a quiet week says so plainly, with the next known report after it when this
 * app has one. [next] is null either because nothing further is known yet, or because the caller
 * has already decided the block should not draw at all (every date null, the server field not yet
 * deployed): this function does not tell those two apart, which is why that decision is made
 * before this one is ever reached ([WatchlistUiState.reportsKnown]).
 *
 * [isNextWeek] follows the heading ([reportsIsNextWeek]): a quiet weekend says "next week," not
 * "this week," the same swap the heading makes, so the empty sentence never contradicts the title
 * sitting right above it. Defaulted to false so every Monday-through-Friday caller, including every
 * test written before the weekend fix, keeps reading exactly as it did.
 */
fun reportsEmptyCopy(next: ReportRow?, isNextWeek: Boolean = false): Copy = if (isNextWeek) {
    if (next == null) words(R.string.today_reports_empty_next_week)
    else words(R.string.today_reports_empty_next_week_next, next.company ?: next.display, next.dateLabel)
} else {
    if (next == null) words(R.string.today_reports_empty)
    else words(R.string.today_reports_empty_next, next.company ?: next.display, next.dateLabel)
}

/**
 * Whether [ticker] is one the reader already watches, the same case- and whitespace-tolerant key
 * every set membership check in this file reads by. A watched ticker's report row is marked, never
 * dropped: unlike the retired Tracked block (which left a watched ticker out because its figure
 * already sat in Watched), a report date is worth seeing beside the ticker whether or not the
 * reader is already watching it, so [reportsThisWeek] keeps every row and this is the one thing
 * that changes about how it draws.
 */
fun reportRowWatched(ticker: String, watched: Set<String>): Boolean {
    val keys = watched.mapTo(HashSet()) { it.trim().uppercase() } // lint-allow uppercase: map key
    return ticker.trim().uppercase() in keys // lint-allow uppercase: map key
}

/** "See every covered company in Stocks": the link under the week's rows, honest that Stocks does
 * not sort or filter by report date, so it is a plain pointer rather than a promise of the same
 * filtered set (task brief: "if it can't, link to Stocks and say so"). */
fun reportsAllCopy(): Copy = words(R.string.today_reports_all)

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

/**
 * An open round with nothing voted yet: "Round 3 is open. No votes yet: one vote decides it." The
 * round's own number, never a quantity.
 */
fun nextUpEmpty(round: VoteRound): Copy =
    words(R.string.today_next_up_empty, Fmt.count(round.id)) // lint-allow count: a round's number, not a quantity

// ---- While New York is closed ------------------------------------------------------------------

/** One covered token priced against the last NYSE close while the exchange is shut. */
data class ClosedMover(
    val ticker: String,
    val symbol: String,
    val company: String?,
    val premiumPct: Double,
    val poolUsd: Double,
)

/** A covered token and the quote Today's join already fetched for it, the input [closedMovers] ranks. */
data class CoveredQuote(val ticker: String, val symbol: String, val company: String?, val price: PriceEntry?)

/**
 * The covered tokens whose onchain price sits furthest from the last NYSE close, at most [max],
 * furthest first (the symbol breaks a tie). Only a [TrackingQuality.Tracked] reading counts: a
 * pool under the liquidity floor, or one whose depth Jupiter did not report, prints no premium
 * anywhere in this app and is not a mover here either. A gap under [minPoints] (the digest's own
 * half point, inside the band a deep pool tracks within) is not a move.
 */
fun closedMovers(
    quotes: List<CoveredQuote>,
    max: Int = ClosedMoversCount,
    minPoints: Double = com.plainticker.mobile.watchlist.MOVE_POINTS,
): List<ClosedMover> = quotes
    .mapNotNull { quote ->
        val tracked = TrackingQuality.of(quote.price) as? TrackingQuality.Tracked ?: return@mapNotNull null
        val premium = tracked.premiumPct ?: return@mapNotNull null
        if (!premium.isFinite() || kotlin.math.abs(premium) < minPoints) return@mapNotNull null
        ClosedMover(quote.ticker, quote.symbol, quote.company, premium, tracked.poolUsd)
    }
    .distinctBy { it.ticker }
    .sortedWith(compareByDescending<ClosedMover> { kotlin.math.abs(it.premiumPct) }.thenBy { it.symbol })
    .take(max)

/** How many rows "While New York is closed" draws. */
const val ClosedMoversCount: Int = 3

/**
 * The block draws only while the NYSE is known to be closed: an unknown venue is not a closed one,
 * and during the session the watched rows already carry the live figure.
 */
fun closedMoversShown(market: MarketStatus?, movers: List<ClosedMover>): Boolean =
    market != null && !market.regularSession && movers.isNotEmpty()
