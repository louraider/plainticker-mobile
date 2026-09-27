package com.plainticker.mobile.watchlist

import android.content.SharedPreferences
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.SummaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * The tickers a vote asked for that had no analysis yet when the vote landed: remembered so they
 * can be watched the day they are analysed. Small (one per vote, capped), so it lives beside the
 * watchlist in the same preferences file.
 */
interface PendingWatchStore {
    val tickers: Set<String>
    fun add(ticker: String)
    fun remove(ticker: String)
}

class SharedPrefsPendingWatchStore(private val prefs: SharedPreferences) : PendingWatchStore {
    override val tickers: Set<String>
        get() = prefs.getStringSet(KEY_PENDING, null)?.toSet() ?: emptySet()

    override fun add(ticker: String) {
        val current = tickers
        if (ticker in current || current.size >= AutoWatch.MAX_PENDING) return
        prefs.edit().putStringSet(KEY_PENDING, HashSet(current + ticker)).apply()
    }

    override fun remove(ticker: String) {
        prefs.edit().putStringSet(KEY_PENDING, HashSet(tickers - ticker)).apply()
    }

    companion object {
        const val KEY_PENDING = "watch_pending_votes"
    }
}

/**
 * Closing the loop per reader (judges' round 2): a stock the reader acted on is one they want to
 * hear about, so the app watches it for them instead of waiting to be asked.
 *
 * - **A vote that lands** watches its ticker at once when PlainTicker already analyses it, and
 *   otherwise remembers it ([PendingWatchStore]) and watches it the first time a check finds it
 *   analysed ([resolvePending]), which is the day the digest can say "JEF, which you voted for,
 *   is now analyzed" about a row the reader already has.
 * - **A swap that lands** watches the stock token it went into. A swap back to USDC names no
 *   stock and watches nothing.
 *
 * Both follow the receipt stores rather than hooking the flows that write them, so the swap and
 * vote machines are untouched: a receipt recorded after the first emission is new, and one that
 * was already on disk at start is not re-announced (a reader who took it off the list later
 * keeps it off). Only additions ever happen here; nothing the reader watched is removed.
 */
class AutoWatch(
    private val watchlist: WatchlistStore,
    private val pending: PendingWatchStore,
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
) {

    /** A vote just landed for [ticker]: watched now when analysed, remembered otherwise. */
    suspend fun voted(ticker: String) {
        val key = normalize(ticker) ?: return
        if (key in watchlist.tickers.value) return
        val analysed = analysedTickers()
        if (analysed != null && key in analysed) {
            watchlist.add(key)
            pending.remove(key)
        } else {
            pending.add(key)
        }
    }

    /** A swap just landed into [outputMint]: the stock it names, if any, is watched. */
    suspend fun swappedInto(outputMint: String) {
        val assets = try {
            catalog.catalog()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            return
        }
        val ticker = assets.firstOrNull { it.solanaMint == outputMint }?.underlyingTicker ?: return
        val key = normalize(ticker) ?: return
        if (key !in watchlist.tickers.value) watchlist.add(key)
    }

    /**
     * Every remembered pick that has since been analysed is watched now and forgotten as pending.
     * Returns the tickers it watched, in order. A summary that does not answer changes nothing:
     * the picks stay remembered for the next attempt.
     */
    suspend fun resolvePending(): List<String> {
        val waiting = pending.tickers
        if (waiting.isEmpty()) return emptyList()
        val analysed = analysedTickers() ?: return emptyList()
        val ready = waiting.filter { it in analysed }.sorted()
        ready.forEach { ticker ->
            if (ticker !in watchlist.tickers.value) watchlist.add(ticker)
            pending.remove(ticker)
        }
        return ready
    }

    /** Hands every vote receipt recorded after the first emission to [voted]. Runs until cancelled. */
    suspend fun followVotes(receipts: StateFlow<List<VoteReceipt>>) {
        var seen = receipts.value.mapTo(HashSet()) { it.signature }
        receipts.collect { list ->
            val fresh = list.filter { it.signature !in seen }
            seen = (seen + fresh.map { it.signature }).toHashSet()
            fresh.forEach { voted(it.ticker) }
        }
    }

    /** Hands every swap receipt recorded after the first emission to [swappedInto]. Runs until cancelled. */
    suspend fun followSwaps(receipts: StateFlow<List<SwapReceipt>>) {
        var seen = receipts.value.mapTo(HashSet()) { it.signature }
        receipts.collect { list ->
            val fresh = list.filter { it.signature !in seen }
            seen = (seen + fresh.map { it.signature }).toHashSet()
            fresh.forEach { swappedInto(it.outputMint) }
        }
    }

    private suspend fun analysedTickers(): Set<String>? = try {
        summaries.summary().rows.mapNotNull { normalize(it.ticker) }.toSet()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    }

    private fun normalize(ticker: String): String? =
        ticker.trim().uppercase().takeIf { it.isNotEmpty() } // lint-allow uppercase: map key

    companion object {
        /** A reader who votes every week for a year still fits; past it, a new pick is not remembered. */
        const val MAX_PENDING = 64
    }
}
