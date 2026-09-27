package com.plainticker.mobile.watchlist

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.words
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * The digest: what one daily check has to say about the watched tickers, decided as data and
 * turned into words in exactly one place.
 *
 * The plan asks for a deterministic digest, and this is what that means here. [digest] is a pure
 * function of [DigestInput]: the same rows, the same previous premiums and the same day produce
 * the same lines in the same order, and therefore the same string. Nothing in it reads a clock, a
 * random number or a set whose iteration order is its own business, and every list it builds is
 * sorted by something the input carries. That is what lets the screen and the notification show
 * the same sentence, and what lets a run that would repeat yesterday's sentence stay silent.
 *
 * The words live in strings.xml like every other piece of copy; [DigestStrings] is the seam that
 * resolves them, so the rules can be tested without a device and the wording cannot be spelled in
 * Kotlin.
 */

// ---- The lines ---------------------------------------------------------------------------------

/** One sentence of a digest: a fact first, the copy that states it second. */
sealed interface DigestLine {

    val copy: Copy

    /**
     * Where the notification opens when this line is its title: the stock it names ([ticker]),
     * or the Vote tab ([opensVote]); neither means the Today screen the digest belongs to.
     */
    val ticker: String? get() = null
    val opensVote: Boolean get() = false

    /**
     * The order a reader wants the news in, smallest first: the reader's own pick analysed, then a
     * report today or tomorrow, a round closing today, a move, a report further out, last round's
     * winner, the week's other reports, a round closing later. The first line is the notification's
     * title and the body follows it, so the most useful sentence is always the one the shade shows.
     */
    val rank: Int

    /**
     * A tracked premium that moved against the NYSE close since the previous check. Both ends are
     * stated, because "moved" without the ends is a claim a reader cannot check.
     */
    data class Moved(
        val symbol: String,
        val fromPct: Double,
        val toPct: Double,
        override val ticker: String = symbol,
    ) : DigestLine {
        override val copy: Copy
            get() = words(R.string.digest_moved, symbol, Fmt.percent(fromPct), Fmt.percent(toPct))

        val points: Double get() = abs(toPct - fromPct)

        override val rank: Int get() = 3
    }

    /** The nearest report ahead. [inDays] is whole days from the day the check ran. */
    data class Reports(val symbol: String, val inDays: Long, override val ticker: String = symbol) : DigestLine {
        override val copy: Copy
            get() = when (inDays) {
                0L -> words(R.string.digest_reports_today, symbol)
                1L -> words(R.string.digest_reports_tomorrow, symbol)
                else -> counted(R.plurals.digest_reports_in_days, inDays.toInt(), symbol, Fmt.count(inDays))
            }

        override val rank: Int get() = if (inDays <= 1L) 1 else 4
    }

    /**
     * How many covered companies, watched or not, report within the coming week: a market fact
     * that needs nothing watched to be true, unlike [Reports]'s own nearest watched date. Drawn
     * only above zero; a quiet week says nothing here rather than "0 covered companies".
     */
    data class WeekReports(val count: Int) : DigestLine {
        override val copy: Copy get() = counted(R.plurals.digest_week_reports, count, Fmt.count(count))

        override val rank: Int get() = 6
    }

    /**
     * The vote round in progress: when it closes. Independent of [WinnerAnalysed], because a
     * round can be open with no previous winner yet (before the first round has closed) and a
     * previous winner can still be worth naming after its own round has closed.
     */
    data class RoundCloses(
        val roundId: Int,
        val closesOn: LocalDate,
        /**
         * The close in the reader's own clock ("02:00") when the round closes later on the
         * reader's own today; null otherwise. A round closing tonight is worth the title.
         */
        val todayAt: String? = null,
    ) : DigestLine {
        override val copy: Copy
            // The round's own number, never a quantity: it does not pluralise anything after it.
            get() = if (todayAt != null) {
                words(R.string.digest_vote_round_closes_today, Fmt.count(roundId), todayAt) // lint-allow count: a round's number, not a quantity
            } else {
                words(R.string.digest_vote_round_closes, Fmt.count(roundId), Fmt.weekday(closesOn)) // lint-allow count: a round's number, not a quantity
            }

        override val opensVote: Boolean get() = true

        override val rank: Int get() = if (todayAt != null) 2 else 7
    }

    /**
     * The previous round's winner, once this run has confirmed a live analysis exists for it
     * (`WatchlistCheck`'s own read of `/api/v1/{ticker}`, never the server's own `status` word,
     * which the live contract still sends as `closed` rather than `published`). The loop from a
     * vote to a covered stock, closing in one sentence.
     */
    data class WinnerAnalysed(override val ticker: String, val yours: Boolean = false) : DigestLine {
        override val copy: Copy
            get() = words(if (yours) R.string.digest_vote_pick_analysed else R.string.digest_vote_winner_analysed, ticker)

        override val rank: Int get() = if (yours) 0 else 5
    }
}

/**
 * The one place a digest becomes a string: resources in the app, the same table in a test.
 *
 * [quantity] is the second method because a digest counts out loud and English has to agree with
 * it: "TSLAx reports in 1 day." and "in 4 days." are one resource with two forms, and only the
 * platform knows which one a number selects.
 */
interface DigestStrings {
    fun get(@StringRes id: Int, args: List<String>): String

    fun quantity(@PluralsRes id: Int, quantity: Int, args: List<String>): String
}

/**
 * The nearest report the last check found, which the Today strip on the List reads.
 *
 * It carries the underlying [ticker] as well as the [symbol] it is drawn as, so the strip can
 * check that the ticker it is about to name is still watched: the record is a day old at most,
 * but an hour in which the List names a company the reader has just taken off the list is an hour
 * of the app stating something that is no longer true.
 */
data class WatchedReport(val ticker: String, val symbol: String, val on: LocalDate)

// ---- The rules ---------------------------------------------------------------------------------

data class DigestInput(
    /** The day the check runs, in UTC: report dates are calendar days, not instants. */
    val today: LocalDate,
    val tickers: List<WatchedTicker>,
    /** What the previous check saw, by ticker. The baseline a move is measured against. */
    val previousPremiums: Map<String, Double> = emptyMap(),
    /**
     * `next_report_date` off every company `/summary` covers, watched or not
     * ([WatchedFacts.coveredReportDates]): [DigestLine.WeekReports]'s own candidates, narrowed to
     * the coming week by [digest] itself so this stays as pure an input as [tickers] already is.
     */
    val coveredReportDates: List<LocalDate> = emptyList(),
    /** The vote round in progress, when the server has stamped one (`vote/next-up.round`). */
    val voteRound: VoteRound? = null,
    /**
     * The previous round's winner, already confirmed live-analysed by the caller (`WatchlistCheck`
     * reading `/api/v1/{ticker}`, never a network call [digest] itself could make and stay pure).
     * Null when there is no previous winner, or none this run could confirm is analysed yet.
     */
    val analysedWinner: String? = null,
    /** The reader voted for [analysedWinner] in the round it won (a receipt on this device says so). */
    val votedForWinner: Boolean = false,
    /**
     * The pick an earlier digest already announced personally. Named once, as the title, and then
     * left out: the reader knows, and the stock is on their list by then.
     */
    val announcedPick: String? = null,
    /** When the check runs, for "closes today at 02:00"; null leaves every round line dated. */
    val nowMillis: Long? = null,
    /** The reader's own zone, for the same sentence: a round closes tonight on the reader's clock. */
    val readerZone: ZoneId = ZoneOffset.UTC,
)

/**
 * The notification one produced digest posts: its most useful line as the title (no period, as
 * a title), up to two more as the body, and where a tap goes.
 */
data class DigestNotice(
    val title: String,
    /** Blank when the title is the whole of the news. */
    val body: String,
    /** The stock the title names: a tap opens its page. */
    val ticker: String? = null,
    /** The title is about the vote round: a tap opens Vote. */
    val opensVote: Boolean = false,
)

data class Digest(
    val lines: List<DigestLine>,
    /** The premiums this run saw, by ticker: the baseline the next run compares against. */
    val premiums: Map<String, Double>,
    val nextReport: WatchedReport?,
) {
    /**
     * True when there is any line at all. The count of what is watched is no longer a line (judges'
     * round 2: a digest that opens on "1 stock watched." leads with the one thing the reader
     * already knows), so an empty digest is exactly a day with nothing to say.
     */
    val hasNews: Boolean get() = lines.isNotEmpty()

    /** The reader's own pick, when this digest names it personally: the check records it as said. */
    val personalPick: String?
        get() = lines.firstNotNullOfOrNull { (it as? DigestLine.WinnerAnalysed)?.takeIf { line -> line.yours }?.ticker }

    /** The digest as one paragraph, news first, every applicable clause: what the digest screen draws. */
    fun text(strings: DigestStrings): String = lines.joinToString(" ") { render(it, strings) }

    /**
     * The notification: the first line (the most useful one, see [DigestLine.rank]) as its title,
     * the next two as its body. The full paragraph stays on the digest screen. Null with nothing
     * to say.
     */
    fun notice(strings: DigestStrings): DigestNotice? {
        val top = lines.firstOrNull() ?: return null
        return DigestNotice(
            title = render(top, strings).removeSuffix("."),
            body = lines.drop(1).take(NOTICE_BODY_LINES).joinToString(" ") { render(it, strings) },
            ticker = top.ticker,
            opensVote = top.opensVote,
        )
    }

    private fun render(line: DigestLine, strings: DigestStrings): String =
        when (val copy = line.copy) {
            is Copy.Words -> strings.get(copy.id, copy.args)
            is Copy.Counted -> strings.quantity(copy.id, copy.quantity, copy.args)
            is Copy.Raw -> copy.text
        }

    companion object {
        /** Nothing watched, or nothing that answered: the digest with nothing in it. */
        val NOTHING = Digest(emptyList(), emptyMap(), null)
    }
}

/**
 * What today's check has to say. Pure, total, and sorted at every step.
 *
 * The sentences are ordered by [DigestLine.rank], news first: the reader's own pick analysed, a
 * report today or tomorrow, a round closing today, what moved, the nearest report further out,
 * last round's winner, the week's other reports, a round closing later. Ties keep the order they
 * were built in (the largest move first), so the same input always reads the same way.
 */
fun digest(input: DigestInput): Digest {
    if (input.tickers.isEmpty()) return Digest.NOTHING
    val tickers = input.tickers.sortedBy { it.ticker }
    val premiums = LinkedHashMap<String, Double>(tickers.size)
    tickers.forEach { row -> row.premiumPct?.let { premiums[row.ticker] = it } }
    val report = nearestReport(tickers, input.today)

    val lines = ArrayList<DigestLine>()
    lines += moves(tickers, input.previousPremiums)
    report?.let { lines += DigestLine.Reports(it.symbol, ChronoUnit.DAYS.between(input.today, it.on), it.ticker) }
    weekReportCount(input.coveredReportDates, input.today)
        .takeIf { it > 0 }
        ?.let { lines += DigestLine.WeekReports(it) }
    input.voteRound?.closesAtInstant()?.let { closes ->
        lines += DigestLine.RoundCloses(
            input.voteRound.id,
            LocalDate.ofInstant(closes, ZoneOffset.UTC),
            todayAt = closesLaterToday(closes, input.nowMillis, input.readerZone),
        )
    }
    input.analysedWinner?.let { winner ->
        val yours = input.votedForWinner
        val alreadySaid = yours && input.announcedPick.equals(winner, ignoreCase = true)
        if (!alreadySaid) lines += DigestLine.WinnerAnalysed(winner, yours = yours)
    }
    return Digest(lines = lines.sortedBy { it.rank }, premiums = premiums, nextReport = report)
}

/**
 * The reader's clock time of [closes] when it falls later on the reader's own today, else null.
 * A close that has passed, or one on another day, is not "tonight".
 */
private fun closesLaterToday(closes: Instant, nowMillis: Long?, zone: ZoneId): String? {
    val now = nowMillis ?: return null
    val closesMillis = closes.toEpochMilli()
    if (closesMillis <= now) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    if (closes.atZone(zone).toLocalDate() != today) return null
    return Fmt.clock(closesMillis, zone)
}

/**
 * How many covered companies report from [today] through six days after: a rolling week in the
 * check's own UTC calendar day, not the reader-zone, Monday-to-Sunday week
 * [com.plainticker.mobile.ui.today.reportsThisWeek] draws on Today. That screen's own week needs a
 * weekend exception so it never narrows to two days on a Saturday; a notification sentence has no
 * such display to protect and reads just as honestly either way, so this stays the small, rolling
 * rule the digest already uses for its own dates elsewhere in this file.
 */
private fun weekReportCount(dates: List<LocalDate>, today: LocalDate): Int {
    val end = today.plusDays(6)
    return dates.count { it in today..end }
}

/**
 * The premiums that moved far enough to be worth a sentence, largest move first.
 *
 * [MOVE_POINTS] is half a percentage point, and it comes off the measurement in DESIGN.md section
 * 1.1: every pool at or above $100k tracked the NYSE close within 0.8 percent, so a token can sit
 * anywhere inside that band on any given day without anything having happened to it. Half a point
 * is the smallest day-over-day change that is more than where in the band the quote happened to
 * sit. Below the liquidity floor there is no premium at all, so a dead pool can never move.
 *
 * At most [MAX_MOVES] of them: a digest is read at a glance in a notification shade, and a fourth
 * clause turns it into a paragraph nobody finishes.
 */
private fun moves(tickers: List<WatchedTicker>, previous: Map<String, Double>): List<DigestLine.Moved> =
    tickers
        .mapNotNull { row ->
            val now = row.premiumPct ?: return@mapNotNull null
            val before = previous[row.ticker] ?: return@mapNotNull null
            DigestLine.Moved(row.display, before, now, row.ticker).takeIf { it.points >= MOVE_POINTS }
        }
        .sortedWith(compareByDescending<DigestLine.Moved> { it.points }.thenBy { it.symbol })
        .take(MAX_MOVES)

/**
 * The nearest report ahead, earliest first and the symbol as the tie-break. A date that has
 * already passed is not news: it is a report date the provider has not refreshed yet, and saying
 * a company reports in minus three days would be the app inventing a fact.
 */
private fun nearestReport(tickers: List<WatchedTicker>, today: LocalDate): WatchedReport? = tickers
    .mapNotNull { row -> row.nextReport?.takeUnless { it.isBefore(today) }?.let { WatchedReport(row.ticker, row.display, it) } }
    .minWithOrNull(compareBy({ it.on }, { it.symbol }))

/** Percentage points of movement a premium needs before the digest says it moved. */
const val MOVE_POINTS: Double = 0.5

/** How many moves one digest names. */
const val MAX_MOVES: Int = 2

/** How many lines follow the title in the notification's body. */
const val NOTICE_BODY_LINES: Int = 2
