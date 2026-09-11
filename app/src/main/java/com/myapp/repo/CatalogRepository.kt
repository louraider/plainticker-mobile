package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.xstocks.XStockAsset
import com.myapp.data.xstocks.XStocksApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The xStocks catalog (symbols, mints, trading state) and the per-symbol scaledUiAmount multiplier. */
interface CatalogRepository {
    /** Every asset with a Solana deployment. */
    suspend fun catalog(): List<XStockAsset>

    /** Current scaledUiAmount multiplier for one token symbol, e.g. "TSLAx". 1.0 until a split lands. */
    suspend fun multiplier(symbol: String): Double
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
) : CatalogRepository {

    private class Cached<T>(val value: T, val at: Long)

    private val mutex = Mutex()
    private var catalog: Cached<List<XStockAsset>>? = null
    private val multipliers = HashMap<String, Cached<Double>>()

    override suspend fun catalog(): List<XStockAsset> = mutex.withLock {
        val now = clock.nowMillis()
        catalog?.takeIf { now - it.at < catalogTtlMillis }?.value ?: run {
            val fresh = api.catalog().filter { it.solanaMint != null }
            catalog = Cached(fresh, now)
            fresh
        }
    }

    override suspend fun multiplier(symbol: String): Double = mutex.withLock {
        val now = clock.nowMillis()
        val key = symbol.trim()
        multipliers[key]?.takeIf { now - it.at < multiplierTtlMillis }?.value ?: run {
            val fresh = api.multiplier(key).currentMultiplier.takeIf { it > 0.0 } ?: 1.0
            multipliers[key] = Cached(fresh, now)
            fresh
        }
    }

    companion object {
        const val CATALOG_TTL_MS = 6 * 60 * 60 * 1000L
        const val MULTIPLIER_TTL_MS = 30 * 60 * 1000L
    }
}
