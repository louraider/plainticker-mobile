package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.xstocks.CatalogCache
import com.myapp.data.xstocks.Multiplier
import com.myapp.data.xstocks.ProofOfReserves
import com.myapp.data.xstocks.XStockAsset
import com.myapp.data.xstocks.XStocksApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The xStocks catalog (symbols, mints, trading state), the per-symbol scaledUiAmount multiplier and
 * the per-symbol proof of reserves.
 */
/**
 * Everything known about the catalog at one moment, and whether that is all of it.
 *
 * [whole] is the only thing a caller has to think about: a whole catalog may be used to decide
 * that a ticker has no xStock, the first pages of one may not. It is true for a catalog that came
 * off disk, true for the last emission of a paging run, and true while live pages are landing on
 * top of a cached catalog, because what is on screen is still a whole catalog being refined.
 */
data class CatalogUpdate(
    /** Every Solana asset known so far. */
    val assets: List<XStockAsset>,
    val whole: Boolean,
)

interface CatalogRepository {
    /** Every asset with a Solana deployment. */
    suspend fun catalog(): List<XStockAsset>

    /**
     * The catalog as it arrives, rather than only once all of it has.
     *
     * The catalog is ~830 assets over eight pages and 7.5 s, and page zero alone is enough to
     * draw the top of a list, so this publishes after every page instead of after the last one.
     * While pages are landing the set only grows: a page refines it rather than replacing it. A
     * catalog on disk is emitted first, whether or not it is still inside its window, so a stale
     * cache paints immediately and the network refreshes behind it.
     *
     * The last emission is the live catalog alone, in its own page order, so a token xStocks has
     * since dropped leaves the list with it. That is the one emission that may be smaller than
     * the one before it, and callers must not assume the sequence is monotonic. Order is not a
     * promise either: every caller sorts what it draws.
     */
    fun catalogUpdates(): Flow<CatalogUpdate>

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
        if (stored != null && insideTheWindow(now, stored.capturedAtMillis)) {
            catalog = Cached(stored.assets, now)
            return@withLock stored.assets
        }

        val fresh = api.catalog().solanaAssets()
        // An empty answer is not a catalog. xStocks answering 200 with no Solana nodes is a
        // server with nothing to say, and remembering it as the catalog would tell every screen
        // for the next six hours that no ticker has a token. A stale file is a better answer
        // than none, and neither is worth caching.
        if (fresh.isEmpty()) return@withLock stored?.assets.orEmpty()
        val at = clock.nowMillis()
        catalog = Cached(fresh, at)
        disk?.write(fresh, at)
        fresh
    }

    /**
     * Nothing here is under [mutex]. The lock exists so two screens do not fetch the same catalog
     * twice, and a paging run would own it for seven seconds, which is exactly the wait this
     * whole change is about. The List calls this once per refresh; a [catalog] call that arrives
     * while a run is in flight can still pay for its own fetch, and the cost of that is one extra
     * download on the first launch of a build, not a wrong answer.
     */
    override fun catalogUpdates(): Flow<CatalogUpdate> = flow {
        val asked = clock.nowMillis()
        val remembered = mutex.withLock { catalog?.takeIf { asked - it.at < catalogTtlMillis }?.value }
        if (remembered != null) {
            emit(CatalogUpdate(remembered, whole = true))
            return@flow
        }

        // Whatever is on disk paints first, fresh or not: being past the window is a reason to
        // refresh behind it, never a reason to make the reader wait for the network.
        val stored = disk?.read()
        val cached = stored?.assets.orEmpty()
        if (cached.isNotEmpty()) emit(CatalogUpdate(cached, whole = true))
        if (stored != null && insideTheWindow(asked, stored.capturedAtMillis)) {
            mutex.withLock { catalog = Cached(cached, asked) }
            return@flow
        }

        // Page zero is enough to draw the top of the list, so nothing waits for page seven.
        val merged = LinkedHashMap<String, XStockAsset>()
        cached.forEach { merged[it.symbol] = it }
        val fresh = ArrayList<XStockAsset>(cached.size)
        api.catalogPages().collect { page ->
            val solana = page.nodes.solanaAssets()
            if (solana.isEmpty()) return@collect
            fresh += solana
            // Keyed by token symbol, so a live page refines the row a cached one put there
            // rather than adding a second copy of it.
            solana.forEach { merged[it.symbol] = it }
            // Still a whole catalog while a cached one is underneath: the pages are refining it.
            emit(CatalogUpdate(merged.values.toList(), whole = cached.isNotEmpty()))
        }

        // The same rule as [catalog]: an empty answer never becomes the whole catalog. Emitting
        // it with `whole = true` would blank a list that had the bundled snapshot on it, because
        // a whole catalog is what the screen decides "this ticker has no xStock" from. Ending
        // here instead keeps what is drawn and leaves the banner to say the catalog is missing.
        if (fresh.isEmpty()) return@flow

        val at = clock.nowMillis()
        mutex.withLock { catalog = Cached(fresh, at) }
        disk?.write(fresh, at)
        emit(CatalogUpdate(fresh, whole = true))
    }

    /**
     * Whether a catalog captured at [capturedAtMillis] is still inside its window.
     *
     * The capture time is a wall clock, because the file outlives the process and nothing else
     * survives a reboot to compare it against. The cost of that is a device whose clock moves:
     * a file stamped in the future would read as fresh until the clock caught up, which on a
     * file with a day-long window is unbounded. A negative age is therefore a moved clock and
     * not a fresh file, and it counts as expired, which costs one refetch and nothing else.
     */
    private fun insideTheWindow(now: Long, capturedAtMillis: Long): Boolean =
        now - capturedAtMillis in 0 until diskTtlMillis

    /** What this app keeps of a catalog page: Solana assets, trimmed to their Solana deployment. */
    private fun List<XStockAsset>.solanaAssets(): List<XStockAsset> =
        filter { it.symbol.isNotBlank() && it.solanaMint != null }.map { it.solanaOnly() }

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
