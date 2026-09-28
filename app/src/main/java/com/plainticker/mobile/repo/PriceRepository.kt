package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.jupiter.JupiterPriceApi
import com.plainticker.mobile.data.jupiter.PriceEntry
import com.plainticker.mobile.data.jupiter.PriceFetch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /**
     * The newest quote this app holds for each mint, whichever screen asked for it: the one price
     * source Today, Stocks and Detail all observe (device QA of 1.3.18).
     *
     * **Why a flow and not only the cache.** Each screen used to copy the quotes it fetched into
     * its own rows and keep them there. Today's pull to refresh read METAx at +0.10% while Stocks
     * and Detail kept the +0.30% they had copied earlier; a Stocks refresh then left Today on the
     * older figure. The cache was shared, the copies were not. Every fetch now lands here as well,
     * and every screen redraws the mints it shows from it, so one refresh anywhere moves every
     * screen at once and no screen keeps a private stale copy.
     *
     * A mint Jupiter answered about without a price is taken out; a mint whose request never landed
     * keeps what it had. The default is a flow that never moves, for a test double that does not
     * care.
     */
    val latest: StateFlow<Map<String, PriceEntry>> get() = NO_QUOTES

    /**
     * Drops the cached answers for [mints], or for every mint when null, so the next ask goes to
     * Jupiter: a pull to refresh is a reader asking for a new figure, and the 30 s window would
     * otherwise hand back the one already on screen (final QA of 1.3.19: a pull on Stocks left
     * every figure where it was). [latest] keeps what it holds until the new answer replaces it.
     */
    suspend fun forget(mints: Collection<String>? = null) {}

    companion object {
        /** Price every mint given, with no leading window. */
        const val NO_LIMIT = -1

        /** A [latest] that never holds anything. */
        val NO_QUOTES: StateFlow<Map<String, PriceEntry>> = MutableStateFlow<Map<String, PriceEntry>>(emptyMap()).asStateFlow()
    }
}

/**
 * In-memory cache with a 30 s TTL per mint. Jupiter's keyless bucket is 0.5 requests per
 * second, so the list, the detail and the portfolio must share what has already been fetched
 * rather than each ask again: only expired or unseen mints are requested.
 *
 * A mint Jupiter answered about is cached for the TTL, whether it came back with a price or
 * without one, so an unpriceable mint is not re-asked on every recomposition. A mint whose
 * request never landed (a 429, or any other transport failure) is deliberately NOT cached:
 * it carries no answer, and remembering it as a miss would leave the row blank for the whole
 * TTL and make the retry pointless.
 *
 * The mutex guards the cache and nothing else. A paced run over the List's 200 mints spends
 * seconds inside [JupiterPriceApi.prices], and holding the lock across that made every other
 * screen wait for the List: a Detail opened during a refresh sat without a price until the
 * whole run finished. The fetch therefore happens outside the lock. The price of that is that
 * two screens asking for the same expired mint at the same instant can each send one request,
 * which the pacing and the backoff already tolerate; the price of the alternative was a frozen
 * screen on the demo path.
 */
class CachedPriceRepository(
    private val api: JupiterPriceApi,
    private val clock: Clock,
    private val ttlMillis: Long = TTL_MS,
) : PriceRepository {

    /** [at] is when the answer arrived (the TTL runs from it). */
    private class Cached(val entry: PriceEntry?, val at: Long)

    private val mutex = Mutex()
    private val cache = HashMap<String, Cached>()
    private val _latest = MutableStateFlow<Map<String, PriceEntry>>(emptyMap())

    /**
     * Request order, independent of the cache (security review L6). Each fetch takes the next
     * number when it leaves; [landed] holds, per mint, the number of the request whose answer is
     * the one on screen. [forget] empties the cache but never this, so an older request still in
     * flight when a pull to refresh cleared the cache cannot land last and overwrite the newer
     * quote. Both, and [_latest], change only under [mutex].
     */
    private var sequence = 0L
    private val landed = HashMap<String, Long>()

    override val latest: StateFlow<Map<String, PriceEntry>> = _latest.asStateFlow()

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

        val asked = clock.nowMillis()
        val (missing, request) = mutex.withLock {
            window.filter { expiredAt(asked, it) } to ++sequence
        }

        // Outside the lock: this is the paced, retried, multi-second part, and no other screen
        // may be made to wait behind it.
        var fetched = PriceFetch.EMPTY
        if (missing.isNotEmpty()) {
            fetched = api.prices(missing)
            val at = clock.nowMillis()
            // Only the mints this answer is the newest for (QA of 1.3.21: Stocks and Detail lagged
            // Today by one refresh). A paced fetch takes seconds; a pull on another screen that
            // left after it and landed before it is the newer quote, and the older answer landing
            // last used to overwrite it in the cache and in [latest], so every screen but the one
            // that pulled drew the quote from one refresh before.
            mutex.withLock {
                val newest = missing.filter { mint ->
                    (mint !in fetched.unfetched && (landed[mint] ?: 0L) < request).also { write ->
                        if (write) {
                            cache[mint] = Cached(fetched.priced[mint], at)
                            landed[mint] = request
                        }
                    }
                }.toSet()
                // Every screen observes this: what one screen fetched, all of them draw. Under the
                // same lock as the choice above, so two answers cannot publish out of order.
                val priced = fetched.priced.filterKeys { it in newest }
                val answeredWithoutPrice = newest - priced.keys
                if (priced.isNotEmpty() || answeredWithoutPrice.isNotEmpty()) {
                    _latest.update { current -> (current - answeredWithoutPrice) + priced }
                }
            }
        }

        val out = LinkedHashMap<String, PriceEntry>(window.size)
        mutex.withLock {
            for (mint in window) {
                val hit = cache[mint] ?: continue
                // Freshness is judged at [asked], the same instant that chose [missing]: anything
                // this call refreshed is newer than that, and anything still expired against it is
                // a mint the fetch could not reach. Pacing 149 mints takes seconds, and judging at
                // the end would drop prices that were perfectly fresh when the screen asked.
                if (expiredAt(asked, mint)) continue
                hit.entry?.let { out[mint] = it }
            }
        }
        // A mint another screen priced while this fetch was out is priced, not unknown: no mint
        // may be in both halves of the answer.
        return PriceFetch(out, fetched.unfetched - out.keys, fetched.failure)
    }

    override suspend fun forget(mints: Collection<String>?) {
        mutex.withLock {
            if (mints == null) cache.clear() else mints.forEach { cache.remove(it.trim()) }
        }
    }

    /** Caller must own [mutex]. True when [mint] carries no answer newer than [asked]. */
    private fun expiredAt(asked: Long, mint: String): Boolean =
        cache[mint]?.let { asked - it.at >= ttlMillis } ?: true

    companion object {
        const val TTL_MS = 30_000L
    }
}
