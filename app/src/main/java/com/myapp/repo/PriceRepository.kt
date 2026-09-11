package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.jupiter.JupiterPriceApi
import com.myapp.data.jupiter.PriceEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** USD prices by mint from Jupiter Price v3, the app's single price source. */
interface PriceRepository {
    /** Prices for [mints]; a mint Jupiter cannot price is absent, not an error. */
    suspend fun prices(mints: Collection<String>): Map<String, PriceEntry>
}

/**
 * In-memory cache with a 30 s TTL per mint. Jupiter's keyless bucket is 0.5 requests per
 * second, so the list, the detail and the portfolio must share one fetch when they ask for
 * the same mints within a refresh window. Only expired or unseen mints are requested; a
 * mint Jupiter returned nothing for is remembered as a miss for the same TTL so it is not
 * re-asked on every recomposition. A failed fetch throws and keeps whatever was cached.
 */
class CachedPriceRepository(
    private val api: JupiterPriceApi,
    private val clock: Clock,
    private val ttlMillis: Long = TTL_MS,
) : PriceRepository {

    private class Cached(val entry: PriceEntry?, val at: Long)

    private val mutex = Mutex()
    private val cache = HashMap<String, Cached>()

    override suspend fun prices(mints: Collection<String>): Map<String, PriceEntry> = mutex.withLock {
        val wanted = mints.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return@withLock emptyMap()

        val now = clock.nowMillis()
        val missing = wanted.filter { mint -> cache[mint]?.let { now - it.at >= ttlMillis } ?: true }
        if (missing.isNotEmpty()) {
            val fetched = api.prices(missing)
            val at = clock.nowMillis()
            for (mint in missing) cache[mint] = Cached(fetched[mint], at)
        }
        LinkedHashMap<String, PriceEntry>().also { out ->
            for (mint in wanted) cache[mint]?.entry?.let { out[mint] = it }
        }
    }

    companion object {
        const val TTL_MS = 30_000L
    }
}
