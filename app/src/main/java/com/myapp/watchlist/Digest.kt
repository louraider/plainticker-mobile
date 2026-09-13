package com.myapp.watchlist

import androidx.annotation.StringRes
import com.myapp.R
import com.myapp.ui.Copy
import com.myapp.ui.Fmt
import com.myapp.ui.words
import java.time.LocalDate
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

    /** How many tickers the check looked at. Never news on its own: it is the opening clause. */
    data class Watched(val count: Int) : DigestLine {
        override val copy: Copy get() = words(R.string.digest_watched, Fmt.count(count))
    }

    /**
     * A tracked premium that moved against the NYSE close since the previous check. Both ends are
     * stated, because "moved" without the ends is a claim a reader cannot check.
     */
    data class Moved(val symbol: String, val fromPct: Double, val toPct: Double) : DigestLine {
        override val copy: Copy
            get() = words(R.string.digest_moved, symbol, Fmt.percent(fromPct), Fmt.percent(toPct))

        val points: Double get() = abs(toPct - fromPct)
    }

    /** The nearest report ahead. [inDays] is whole days from the day the check ran. */
    data class Reports(val symbol: String, val inDays: Long) : DigestLine {
        override val copy: Copy
            get() = when (inDays) {
                0L -> words(R.string.digest_reports_today, symbol)
                1L -> words(R.string.digest_reports_tomorrow, symbol)
                else -> words(R.string.digest_reports_in_days, symbol, Fmt.count(inDays))
            }
    }
}

/** The one place a digest becomes a string: resources in the app, the same table in a test. */
fun interface DigestStrings {
    fun get(@StringRes id: Int, args: List<String>): String
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
)

data class Digest(
    val lines: List<DigestLine>,
    /** The premiums this run saw, by ticker: the baseline the next run compares against. */
    val premiums: Map<String, Double>,
    val nextReport: WatchedReport?,
) {
    /**
     * True when a line says something that is not simply the count of what is watched. A check
     * that finds only the count says nothing at all rather than sending a notification whose
     * whole content is that the watchlist still exists.
     */
    val hasNews: Boolean get() = lines.any { it !is DigestLine.Watched }

    /** The digest as one paragraph, which is what the Panel draws and the notification carries. */
    fun text(strings: DigestStrings): String =
        lines.joinToString(" ") { line ->
            when (val copy = line.copy) {
                is Copy.Words -> strings.get(copy.id, copy.args)
                is Copy.Raw -> copy.text
            }
        }

    companion object {
        /** Nothing watched, or nothing that answered: the digest with nothing in it. */
        val NOTHING = Digest(emptyList(), emptyMap(), null)
    }
}

/**
 * What today's check has to say. Pure, total, and sorted at every step.
 *
 * The order of the sentences is fixed: how many are watched, then what moved, then what reports
 * next. It is the canvas order, and it is also the order of decreasing volatility, so the part of
 * the digest that changes daily sits where a reader's eye lands after the count.
 */
fun digest(input: DigestInput): Digest {
    if (input.tickers.isEmpty()) return Digest.NOTHING
    val tickers = input.tickers.sortedBy { it.ticker }
    val premiums = LinkedHashMap<String, Double>(tickers.size)
    tickers.forEach { row -> row.premiumPct?.let { premiums[row.ticker] = it } }
    val report = nearestReport(tickers, input.today)

    val lines = ArrayList<DigestLine>()
    lines += DigestLine.Watched(tickers.size)
    lines += moves(tickers, input.previousPremiums)
    report?.let { lines += DigestLine.Reports(it.symbol, ChronoUnit.DAYS.between(input.today, it.on)) }
    return Digest(lines = lines, premiums = premiums, nextReport = report)
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
            DigestLine.Moved(row.display, before, now).takeIf { it.points >= MOVE_POINTS }
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
