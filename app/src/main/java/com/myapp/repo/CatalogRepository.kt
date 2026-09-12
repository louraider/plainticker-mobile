package com.myapp.repo

import com.myapp.core.Clock
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
 */
class CachedCatalogRepository(
    private val api: XStocksApi,
    private val clock: Clock,
    private val catalogTtlMillis: Long = CATALOG_TTL_MS,
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
        catalog?.takeIf { now - it.at < catalogTtlMillis }?.value ?: run {
            val fresh = api.catalog().filter { it.solanaMint != null }
            catalog = Cached(fresh, now)
            fresh
        }
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
