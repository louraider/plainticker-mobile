package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.jupiter.JupiterPriceApi
import com.myapp.data.jupiter.PriceEntry
import com.myapp.data.jupiter.PriceFetch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** USD prices by mint from Jupiter Price v3, the app's single price source. */
interface PriceRepository {
    /**
     * Prices for [mints]; a mint Jupiter cannot price is absent, not an error. Throws only
     * when there is nothing at all to draw: some mints priced and some not is a result, not
     * a failure. A caller that has to explain a gap on screen wants [pricesFirst] instead.
     */
    suspend fun prices(mints: Collection<String>): Map<String, PriceEntry>

    /**
     * Prices the first [limit] of [mints] in the order given, and reports what it could not
     * fetch. Jupiter's keyless budget is 0.5 requests per second, so pricing 149 mints costs
     * three paced calls and several seconds: a screen prices the rows a reader can actually
     * see, draws them, then calls again with [NO_LIMIT] for the rest. The first window is
     * cached by then, so the second call pays only for what is left. Never throws.
     */
    suspend fun pricesFirst(mints: List<String>, limit: Int = NO_LIMIT): PriceFetch

    companion object {
        /** Price every mint given, with no leading window. */
        const val NO_LIMIT = -1
    }
}

/**
 * In-memory cache with a 30 s TTL per mint. Jupiter's keyless bucket is 0.5 requests per
 * second, so the list, the detail and the portfolio must share one fetch when they ask for
 * the same mints within a refresh window. Only expired or unseen mints are requested.
 *
 * A mint Jupiter answered about is cached for the TTL, whether it came back with a price or
 * without one, so an unpriceable mint is not re-asked on every recomposition. A mint whose
 * request never landed (a 429, or any other transport failure) is deliberately NOT cached:
 * it carries no answer, and remembering it as a miss would leave the row blank for the whole
 * TTL and make the retry pointless.
 */
class CachedPriceRepository(
    private val api: JupiterPriceApi,
    private val clock: Clock,
    private val ttlMillis: Long = TTL_MS,
) : PriceRepository {

    private class Cached(val entry: PriceEntry?, val at: Long)

    private val mutex = Mutex()
    private val cache = HashMap<String, Cached>()

    override suspend fun prices(mints: Collection<String>): Map<String, PriceEntry> {
        val fetch = pricesFirst(mints.toList(), PriceRepository.NO_LIMIT)
        val cause = fetch.failure
        // Nothing came back and something failed: the caller has no numbers to draw, so it
        // gets the reason rather than an empty map it cannot tell apart from "no prices".
        if (cause != null && fetch.priced.isEmpty()) throw cause
        return fetch.priced
    }

    override suspend fun pricesFirst(mints: List<String>, limit: Int): PriceFetch {
        val wanted = mints.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val window = if (limit >= 0) wanted.take(limit) else wanted
        if (window.isEmpty()) return PriceFetch.EMPTY
        return mutex.withLock { fetchLocked(window) }
    }

    /** Caller must own [mutex]: one fetch per refresh window, shared by every screen. */
    private suspend fun fetchLocked(window: List<String>): PriceFetch {
        val asked = clock.nowMillis()
        val missing = window.filter { mint -> cache[mint]?.let { asked - it.at >= ttlMillis } ?: true }

        var fetched = PriceFetch.EMPTY
        if (missing.isNotEmpty()) {
            fetched = api.prices(missing)
            val at = clock.nowMillis()
            for (mint in missing) {
                if (mint in fetched.unfetched) continue
                cache[mint] = Cached(fetched.priced[mint], at)
            }
        }

        val out = LinkedHashMap<String, PriceEntry>(window.size)
        for (mint in window) {
            val hit = cache[mint] ?: continue
            // Freshness is judged at [asked], the same instant that chose [missing]: anything
            // this call refreshed is newer than that, and anything still expired against it is
            // a mint the fetch could not reach. Pacing 149 mints takes seconds, and judging at
            // the end would drop prices that were perfectly fresh when the screen asked.
            if (asked - hit.at >= ttlMillis) continue
            hit.entry?.let { out[mint] = it }
        }
        return PriceFetch(out, fetched.unfetched, fetched.failure)
    }

    companion object {
        const val TTL_MS = 30_000L
    }
}
