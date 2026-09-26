package com.plainticker.mobile.watchlist

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.prefs.WatchlistStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** What one run of the daily check did. */
sealed interface CheckOutcome {

    /** A new digest: persisted, and posted as a notification if this device allows one. */
    data class Produced(val text: String) : CheckOutcome

    /** The check ran and would have said exactly what it said last time, so it said nothing. */
    data object Unchanged : CheckOutcome

    /** The check ran and found nothing worth a sentence. */
    data object NothingToSay : CheckOutcome

    /** A source the digest is made of did not answer. The last digest stands untouched. */
    data object Failed : CheckOutcome
}

/**
 * The daily check: read the watched tickers, produce the digest, keep it, and say it once.
 *
 * The worker is a thin shell over this, and so is the debug action, so the whole run is testable
 * without WorkManager, without a device and without the network. Four outcomes, and the difference
 * between them is the product:
 *
 * - **Produced.** There is news and it is not yesterday's news. The record is replaced with every
 *   applicable clause; the notification carries [Digest.headline]'s own shorter reading of the
 *   same facts, one to two sentences rather than the screen's fuller paragraph.
 * - **Unchanged.** The digest came out identical to the last one. Sending it again would be the
 *   app telling a person something they were already told, so nothing is posted and the stored
 *   digest, timestamp included, stays as it was. This is what makes determinism load-bearing
 *   rather than decorative: identical inputs produce an identical string, and an identical string
 *   is silence.
 * - **NothingToSay.** Nothing is watched, or nothing that answered has a report ahead or a premium
 *   that moved. No empty notification, and no empty digest overwriting a real one.
 * - **Failed.** `/summary` did not answer, so there is nothing to judge. Nothing is written: the
 *   last digest is still the last true thing this app knew, and the worker asks to be retried.
 *
 * Every completed run records what it saw (the premiums and the nearest report) whether or not it
 * said anything, so tomorrow's comparison is against yesterday's observation rather than against
 * the last thing that was announced.
 */
class WatchlistCheck(
    private val watchlist: WatchlistStore,
    private val facts: WatchlistFacts,
    private val digests: DigestStore,
    private val strings: DigestStrings,
    private val notifier: DigestNotifier,
    private val clock: Clock,
    /** Report dates are calendar days in the filing's own calendar, so the day is read in UTC. */
    private val zone: ZoneId = ZoneOffset.UTC,
    /**
     * The vote round and the previous round's winner, once confirmed live-analysed. Null for a
     * caller that does not need the vote line at all (every existing test before this field
     * existed, among them): no lookup runs and the digest simply carries no vote clause, the same
     * silence a source [facts] itself could not reach would leave behind.
     */
    private val vote: VoteDigestFacts? = null,
) {

    suspend fun run(): CheckOutcome {
        val now = clock.nowMillis()
        val before = digests.record.value
        val watched = watchlist.tickers.value

        if (watched.isEmpty()) {
            digests.save(before.observing(now, emptyMap(), null))
            return CheckOutcome.NothingToSay
        }

        val loaded = facts.load(watched)
        // The analysis is what the digest is made of: without it there is no report date, no
        // company and no way to tell a dropped ticker from a silent one.
        if (loaded.analysisUnavailable) return CheckOutcome.Failed

        val voteFacts = vote?.load() ?: VoteFacts.NONE

        val digest = digest(
            DigestInput(
                today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone),
                tickers = loaded.rows,
                previousPremiums = before.premiums,
                coveredReportDates = loaded.coveredReportDates,
                voteRound = voteFacts.round,
                analysedWinner = voteFacts.analysedWinner,
            ),
        )
        val observed = before.observing(now, digest.premiums, digest.nextReport)

        if (!digest.hasNews) {
            digests.save(observed)
            return CheckOutcome.NothingToSay
        }

        val text = digest.text(strings)
        if (text == before.text) {
            digests.save(observed)
            return CheckOutcome.Unchanged
        }

        digests.save(observed.copy(text = text, producedAtMillis = now))
        // Last, and never before the record is written: a notification the screen cannot show
        // something true of would be the one thing this feature is supposed to make impossible.
        // The shade gets the short reading; the record above, and the screen that draws it, get
        // every clause.
        notifier.post(digest.headline(strings))
        return CheckOutcome.Produced(text)
    }
}
