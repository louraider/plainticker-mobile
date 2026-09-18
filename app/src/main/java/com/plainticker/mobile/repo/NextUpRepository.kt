package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.plainticker.NextUpApi
import com.plainticker.mobile.data.plainticker.NextUpRow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The leaders of SKR-weighted coverage curation: which uncovered tickers staked SKR has chosen. */
interface NextUpRepository {
    /** The leaders in the server's order, heaviest first. Throws when nothing can be answered. */
    suspend fun nextUp(): List<NextUpRow>
}

/**
 * One answer, kept for the edge's own five minutes so the List and a Detail opened from it share
 * one request rather than each asking the same cache.
 *
 * The mutex is held across the fetch on purpose, which is the opposite of what
 * [CachedPriceRepository] does. That one pays for a paced, multi-second run over two hundred
 * mints and must not make a second screen wait behind it; this one is a single small edge-cached
 * GET, and two screens asking at the same instant should cost one call rather than two.
 *
 * A fetch that fails while an older answer is held returns the older answer: the leaders move
 * slowly (the tally runs every ten minutes) and a stale strip is better than a strip that blinks
 * out because one request was refused. With nothing held it throws, and the caller draws nothing.
 */
class CachedNextUpRepository(
    private val api: NextUpApi,
    private val clock: Clock,
    private val ttlMillis: Long = TTL_MS,
) : NextUpRepository {

    private class Cached(val rows: List<NextUpRow>, val at: Long)

    private val mutex = Mutex()
    private var cached: Cached? = null

    override suspend fun nextUp(): List<NextUpRow> = mutex.withLock {
        val now = clock.nowMillis()
        val held = cached
        if (held != null && now - held.at < ttlMillis) return held.rows
        try {
            val fresh = api.getNextUp().rows
            cached = Cached(fresh, clock.nowMillis())
            fresh
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            held?.rows ?: throw e
        }
    }

    companion object {
        /** The edge's `s-maxage`, so a second ask inside it could only get the same bytes back. */
        const val TTL_MS = 300_000L
    }
}
