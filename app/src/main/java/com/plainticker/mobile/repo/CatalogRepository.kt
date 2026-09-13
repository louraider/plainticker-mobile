package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.xstocks.CatalogCache
import com.plainticker.mobile.data.xstocks.Multiplier
import com.plainticker.mobile.data.xstocks.ProofOfReserves
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.data.xstocks.XStocksApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
     *
     * [userAsked] is a reader who asked the app to look again, rather than a screen opening. A
     * screen opening is served by whatever cache is still inside its window, which is what makes
     * the second launch of the day free. A reader who taps Retry is asking for the network, so
     * both caches are stepped over and the pages are fetched. Without it, a token listed this
     * morning could not be seen until the file on disk aged out, and that window is a day.
     */
    fun catalogUpdates(userAsked: Boolean = false): Flow<CatalogUpdate>

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
 *
 * **The lock covers the caches and never a call.** [mutex] guards the three maps and the ticket
 * below, and every read and write of them is a few instructions long. It used to be taken across
 * the whole of [catalog], so a Detail screen opened during a catalog fetch waited seven seconds
 * for a multiplier that was already on its way; the price layer found and fixed the same shape
 * first, and [CachedPriceRepository] is where the rule is written down. What stops the duplicate
 * fetch the lock used to stop is a ticket instead: the first caller to want a catalog owns one,
 * every caller after it waits on that one rather than sending a second 4.31 MB, and the ticket is
 * handed back even when the caller that owned it walked away. The per-symbol reads carry no
 * ticket, because two screens asking for the same multiplier at the same instant cost two small
 * requests, which is the trade the price layer already made and is nothing like 4.31 MB.
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

    /**
     * What one shared fetch answered with. [assets] is null when the fetch never finished, which
     * is not an answer and only means the next caller takes the turn. [askedTheNetwork] is false
     * for a fetch the file answered inside its window: a fine answer for a screen opening, and
     * not one for a reader who asked the app to look again.
     */
    private class Fetched(val assets: List<XStockAsset>?, val askedTheNetwork: Boolean)

    /** Either an answer this caller may use, or the ticket that makes it the one that fetches. */
    private sealed interface Turn {
        class Answered(val assets: List<XStockAsset>) : Turn
        class Mine(val ticket: CompletableDeferred<Fetched>) : Turn
    }

    private val mutex = Mutex()
    private var catalog: Cached<List<XStockAsset>>? = null

    /** The fetch every other caller waits on while it is out; null when none is. */
    private var inFlight: CompletableDeferred<Fetched>? = null

    private val multipliers = HashMap<String, Cached<Multiplier>>()

    /**
     * A null value is cached like any other: "xStocks publishes no reserves for this symbol" is an
     * answer, and re-asking for it on every open would spend a request to be told the same thing.
     */
    private val reserves = HashMap<String, Cached<ProofOfReserves?>>()

    /**
     * The memory cache if it still answers, otherwise the fetch already out if there is one, and
     * otherwise this caller's own turn. The lock is held across the check and the claim and
     * nothing else.
     */
    private suspend fun takeATurn(userAsked: Boolean): Turn {
        while (true) {
            val waiting = mutex.withLock {
                val now = clock.nowMillis()
                if (!userAsked) {
                    catalog?.takeIf { now - it.at < catalogTtlMillis }?.let { return Turn.Answered(it.value) }
                }
                val out = inFlight
                if (out == null) {
                    val mine = CompletableDeferred<Fetched>()
                    inFlight = mine
                    return Turn.Mine(mine)
                }
                out
            }
            // Someone else is already fetching, so their answer is this caller's answer too. Two
            // cases loop round for a turn instead: a fetch that never finished, and a fetch the
            // file answered under a caller who asked for the network.
            val done = waiting.await()
            val assets = done.assets
            if (assets != null && (done.askedTheNetwork || !userAsked)) return Turn.Answered(assets)
        }
    }

    /**
     * Hands the turn back and publishes what it found. Uncancellable on purpose: a caller that
     * walked away still has to let go of the ticket, or every screen waiting on it waits for ever.
     */
    private suspend fun release(ticket: CompletableDeferred<Fetched>, answer: Fetched) {
        withContext(NonCancellable) {
            mutex.withLock { if (inFlight === ticket) inFlight = null }
            ticket.complete(answer)
        }
    }

    private suspend fun remember(assets: List<XStockAsset>, at: Long) {
        mutex.withLock { catalog = Cached(assets, at) }
    }

    override suspend fun catalog(): List<XStockAsset> {
        val turn = takeATurn(userAsked = false)
        if (turn is Turn.Answered) return turn.assets
        val ticket = (turn as Turn.Mine).ticket
        var answer = Fetched(assets = null, askedTheNetwork = false)
        try {
            val now = clock.nowMillis()
            // A catalog on disk that is still inside its window is the whole answer: the second
            // launch of the day asks the network nothing at all.
            val stored = disk?.read()
            if (stored != null && insideTheWindow(now, stored.capturedAtMillis)) {
                remember(stored.assets, now)
                answer = Fetched(stored.assets, askedTheNetwork = false)
                return stored.assets
            }

            val fresh = api.catalog().solanaAssets()
            // An empty answer is not a catalog. xStocks answering 200 with no Solana nodes is a
            // server with nothing to say, and remembering it as the catalog would tell every screen
            // for the next six hours that no ticker has a token. A stale file is a better answer
            // than none, and neither is worth caching.
            if (fresh.isEmpty()) {
                val fallback = stored?.assets.orEmpty()
                answer = Fetched(fallback, askedTheNetwork = true)
                return fallback
            }
            val at = clock.nowMillis()
            remember(fresh, at)
            disk?.write(fresh, at)
            answer = Fetched(fresh, askedTheNetwork = true)
            return fresh
        } finally {
            release(ticket, answer)
        }
    }

    /**
     * A paging run owns the shared ticket for its seven seconds, which is what keeps a second
     * screen from paying for the same 4.31 MB, but it owns no lock for any of them, so a Detail
     * screen opened during it gets its multiplier and its reserves at once.
     */
    override fun catalogUpdates(userAsked: Boolean): Flow<CatalogUpdate> = flow {
        val turn = takeATurn(userAsked)
        if (turn is Turn.Answered) {
            if (turn.assets.isNotEmpty()) emit(CatalogUpdate(turn.assets, whole = true))
            return@flow
        }
        val ticket = (turn as Turn.Mine).ticket
        var answer = Fetched(assets = null, askedTheNetwork = false)
        try {
            val asked = clock.nowMillis()

            // Whatever is on disk paints first, fresh or not: being past the window is a reason to
            // refresh behind it, never a reason to make the reader wait for the network. A reader
            // who asked to look again gets the same first paint and the network behind it, because
            // walking the list back to skeletons is not what a retry is for.
            val stored = disk?.read()
            val cached = stored?.assets.orEmpty()
            if (cached.isNotEmpty()) emit(CatalogUpdate(cached, whole = true))
            if (!userAsked && stored != null && insideTheWindow(asked, stored.capturedAtMillis)) {
                remember(cached, asked)
                answer = Fetched(cached, askedTheNetwork = false)
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
            if (fresh.isEmpty()) {
                answer = Fetched(cached, askedTheNetwork = true)
                return@flow
            }

            val at = clock.nowMillis()
            remember(fresh, at)
            disk?.write(fresh, at)
            answer = Fetched(fresh, askedTheNetwork = true)
            emit(CatalogUpdate(fresh, whole = true))
        } finally {
            release(ticket, answer)
        }
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

    override suspend fun multiplierRecord(symbol: String): Multiplier {
        val key = symbol.trim()
        val asked = clock.nowMillis()
        mutex.withLock { multipliers[key]?.takeIf { asked - it.at < multiplierTtlMillis } }
            ?.let { return it.value }

        val fresh = api.multiplier(key)
        val at = clock.nowMillis()
        mutex.withLock { multipliers[key] = Cached(fresh, at) }
        return fresh
    }

    override suspend fun proofOfReserves(symbol: String): ProofOfReserves? {
        val key = symbol.trim()
        val asked = clock.nowMillis()
        // The entry's presence decides, never its value: null is the answer for a symbol xStocks
        // publishes nothing for, and an elvis on the value would re-ask for it on every open.
        val cached = mutex.withLock { reserves[key] }
        if (cached != null && asked - cached.at < reservesTtlMillis) return cached.value

        val fresh = api.proofOfReserves(key)
        val at = clock.nowMillis()
        mutex.withLock { reserves[key] = Cached(fresh, at) }
        return fresh
    }

    companion object {
        const val CATALOG_TTL_MS = 6 * 60 * 60 * 1000L
        const val MULTIPLIER_TTL_MS = 30 * 60 * 1000L
        const val RESERVES_TTL_MS = 30 * 60 * 1000L
    }
}
