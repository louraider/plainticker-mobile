package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.xstocks.CatalogCache
import com.myapp.data.xstocks.Multiplier
import com.myapp.data.xstocks.ProofOfReserves
import com.myapp.data.xstocks.XStockAsset
import com.myapp.data.xstocks.XStocksApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The xStocks catalog (symbols, mints, trading state), the per-symbol scaledUiAmount multiplier and
 * the per-symbol proof of reserves.
 */
interface CatalogRepository {
    /** Every asset with a Solana deployment. */
    suspend fun catalog(): List<XStockAsset>

    /** Current scaledUiAmount multiplier for one token symbol, e.g. "TSLAx". 1.0 until a split lands. */
    suspend fun multiplier(symbol: String): Double

    /**
     * The whole multiplier record for one symbol, including any scheduled change. The Detail screen
     * needs the pending pair, not just today's value, so it can say what a balance becomes and when.
     */
    suspend fun multiplierRecord(symbol: String): Multiplier

    /**
     * Proof of reserves for one token symbol, or null when xStocks publishes none for it: the API
     * answers 200 with a JSON `null`, which is an absence and never a zero.
     */
    suspend fun proofOfReserves(symbol: String): ProofOfReserves?
}

/**
 * The catalog is ~830 assets over nine pages, so it is fetched once per TTL and shared by
 * every screen. Multipliers change on corporate actions, so they get a shorter TTL.
 *
 * Two caches sit in front of the network, and the in-memory one stays in front of the file.
 * Memory answers within a process and dies with it; [disk] is what makes the second launch
 * free, because a process that has just started has no memory cache and used to pay the whole
 * 4.31 MB again. Both keep the catalog trimmed to its Solana deployment
 * ([XStockAsset.solanaOnly]), which is all this app has ever read of it.
 */
class CachedCatalogRepository(
    private val api: XStocksApi,
    private val clock: Clock,
    /** The catalog across process death. Null is "this build keeps no file", not an error. */
    private val disk: CatalogCache? = null,
    private val catalogTtlMillis: Long = CATALOG_TTL_MS,
    private val diskTtlMillis: Long = CatalogCache.TTL_MS,
    private val multiplierTtlMillis: Long = MULTIPLIER_TTL_MS,
    private val reservesTtlMillis: Long = RESERVES_TTL_MS,
) : CatalogRepository {

    private class Cached<T>(val value: T, val at: Long)

    private val mutex = Mutex()
    private var catalog: Cached<List<XStockAsset>>? = null
    private val multipliers = HashMap<String, Cached<Multiplier>>()

    /**
     * A null value is cached like any other: "xStocks publishes no reserves for this symbol" is an
     * answer, and re-asking for it on every open would spend a request to be told the same thing.
     */
    private val reserves = HashMap<String, Cached<ProofOfReserves?>>()

    override suspend fun catalog(): List<XStockAsset> = mutex.withLock {
        val now = clock.nowMillis()
        catalog?.takeIf { now - it.at < catalogTtlMillis }?.let { return@withLock it.value }

        // A catalog on disk that is still inside its window is the whole answer: the second
        // launch of the day asks the network nothing at all.
        val stored = disk?.read()
        if (stored != null && now - stored.capturedAtMillis < diskTtlMillis) {
            catalog = Cached(stored.assets, now)
            return@withLock stored.assets
        }

        val fresh = api.catalog().filter { it.solanaMint != null }.map { it.solanaOnly() }
        catalog = Cached(fresh, now)
        disk?.write(fresh, now)
        fresh
    }

    override suspend fun multiplier(symbol: String): Double =
        multiplierRecord(symbol).currentMultiplier.takeIf { it > 0.0 } ?: 1.0

    override suspend fun multiplierRecord(symbol: String): Multiplier = mutex.withLock {
        val now = clock.nowMillis()
        val key = symbol.trim()
        multipliers[key]?.takeIf { now - it.at < multiplierTtlMillis }?.value ?: run {
            val fresh = api.multiplier(key)
            multipliers[key] = Cached(fresh, now)
            fresh
        }
    }

    override suspend fun proofOfReserves(symbol: String): ProofOfReserves? = mutex.withLock {
        val now = clock.nowMillis()
        val key = symbol.trim()
        // The entry's presence decides, never its value: null is the answer for a symbol xStocks
        // publishes nothing for, and an elvis on the value would re-ask for it on every open.
        val cached = reserves[key]
        if (cached != null && now - cached.at < reservesTtlMillis) return@withLock cached.value
        val fresh = api.proofOfReserves(key)
        reserves[key] = Cached(fresh, now)
        fresh
    }

    companion object {
        const val CATALOG_TTL_MS = 6 * 60 * 60 * 1000L
        const val MULTIPLIER_TTL_MS = 30 * 60 * 1000L
        const val RESERVES_TTL_MS = 30 * 60 * 1000L
    }
}
