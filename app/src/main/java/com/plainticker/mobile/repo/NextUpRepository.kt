package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.plainticker.NextUpApi
import com.plainticker.mobile.data.plainticker.NextUpNotOpenException
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.plainticker.VoteRound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What `next-up` answered, for the caller that needs the whole thing rather than only the
 * leaders: the Vote tab (task A2), which draws a round header only when [Open.round] is present
 * and replaces its round furniture with one line when the answer is [NotOpen].
 */
sealed interface NextUpAnswer {
    data class Open(
        val rows: List<NextUpRow>,
        val round: VoteRound?,
        val previous: PreviousRound?,
    ) : NextUpAnswer

    /** HTTP 404, or 503 `vote_not_configured`: the route exists but voting has not started. */
    data object NotOpen : NextUpAnswer
}

/** The leaders of SKR-weighted coverage curation: which uncovered tickers staked SKR has chosen. */
interface NextUpRepository {
    /** The leaders in the server's order, heaviest first. Throws when nothing can be answered. */
    suspend fun nextUp(): List<NextUpRow>

    /** The whole current-round answer: the leaders, the round header and the previous round. */
    suspend fun current(): NextUpAnswer
}

/**
 * One answer, kept for the edge's own five minutes so the List and a Detail opened from it share
 * one request rather than each asking the same cache. [current] is the whole answer the Vote tab
 * needs; [nextUp] is [NextUpAnswer.Open.rows] read off the very same cached fetch, so the List's
 * strip and the Vote tab's leaders can never see two different answers a moment apart.
 *
 * The mutex is held across the fetch on purpose, which is the opposite of what
 * [CachedPriceRepository] does. That one pays for a paced, multi-second run over two hundred
 * mints and must not make a second screen wait behind it; this one is a single small edge-cached
 * GET, and two screens asking at the same instant should cost one call rather than two.
 *
 * A fetch that fails while an older answer is held returns the older answer: the leaders move
 * slowly (the tally runs every ten minutes) and a stale strip is better than a strip that blinks
 * out because one request was refused. With nothing held it throws, and the caller draws nothing.
 * Voting not being open is not a failure of this cache: [NextUpNotOpenException] is cached as
 * [NextUpAnswer.NotOpen] exactly like a normal answer, rather than being treated as a fetch that
 * failed, so the not-open state is not forgotten the moment a stale-but-open answer would have won.
 */
class CachedNextUpRepository(
    private val api: NextUpApi,
    private val clock: Clock,
    private val ttlMillis: Long = TTL_MS,
) : NextUpRepository {

    private class Cached(val answer: NextUpAnswer, val at: Long)

    private val mutex = Mutex()
    private var cached: Cached? = null

    override suspend fun nextUp(): List<NextUpRow> = when (val answer = current()) {
        is NextUpAnswer.Open -> answer.rows
        NextUpAnswer.NotOpen -> emptyList()
    }

    override suspend fun current(): NextUpAnswer = mutex.withLock {
        val now = clock.nowMillis()
        val held = cached
        if (held != null && now - held.at < ttlMillis) return held.answer
        try {
            val fresh = api.getNextUp()
            val answer = NextUpAnswer.Open(fresh.rows, fresh.round, fresh.previous)
            cached = Cached(answer, clock.nowMillis())
            answer
        } catch (e: NextUpNotOpenException) {
            val answer = NextUpAnswer.NotOpen
            cached = Cached(answer, clock.nowMillis())
            answer
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            held?.answer ?: throw e
        }
    }

    companion object {
        /** The edge's `s-maxage`, so a second ask inside it could only get the same bytes back. */
        const val TTL_MS = 300_000L
    }
}
