package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.xstocks.MarketHours
import com.plainticker.mobile.data.xstocks.MarketStatus
import com.plainticker.mobile.data.xstocks.Trading
import com.plainticker.mobile.data.xstocks.XStockAsset
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The venue's status as a clock rather than a photograph: the fix for Today's "Closed" while the
 * NYSE was trading (24 Sep 2026, 15:22 New York time, on the Seeker).
 *
 * That bug had three layers, and this class answers each. The status was computed once, when a
 * screen first loaded, and never again: [tick] recomputes it from the clock, and a screen calls
 * [onResume] every time it comes back. The computation ignored the clock whenever a trading block
 * existed: [MarketHours.sessionAt] believes a block only until its own `nextChangeAt`. And the
 * block itself came out of a catalog cached for up to a day: once it expires, [onResume] and the
 * boundary job ask for one asset's live block ([CatalogRepository.liveTrading]), a few hundred
 * bytes, instead of the 4.31 MB catalog.
 *
 * **The boundary job.** While a screen is resumed, one coroutine sleeps until the status's own
 * next change (the venue's `nextChangeAt`, or the calendar's next boundary: the pre-market start,
 * the open, the close, 20:00), then ticks. [onPause] cancels it, so nothing runs while the app is
 * in the background; the next [onResume] catches up with a tick of its own.
 *
 * Owned by a ViewModel and run on its scope; [publish] is how each ViewModel writes the status
 * into its own state.
 */
class MarketClock(
    private val clock: Clock,
    private val catalog: CatalogRepository,
    private val scope: CoroutineScope,
    private val publish: (MarketStatus?) -> Unit,
) {
    /** The block every status is read from, and the asset to ask when it expires. */
    private var snapshot: Trading? = null
    private var refreshSymbol: String? = null

    /** False until a catalog has answered at all: "the NYSE is closed" is not said of a venue never read. */
    private var known = false

    /** The expiry a refresh was last asked for, so one expired block costs one request, not one per tick. */
    private var refreshedFor: Long? = null

    private var boundaryJob: Job? = null
    private var refreshJob: Job? = null

    /** The status as of now; null until a catalog has answered. */
    var status: MarketStatus? = null
        private set

    /** A catalog arrived: keep its block (not a finished status) and publish a fresh reading. */
    fun setAssets(assets: List<XStockAsset>): MarketStatus? {
        if (assets.isEmpty()) return status
        known = true
        snapshot = MarketHours.snapshotOf(assets)
        refreshSymbol = assets.firstOrNull { it.trading != null }?.symbol ?: assets.first().symbol
        refreshedFor = null
        return recompute()
    }

    /** Recompute from the clock and publish. Cheap: no network. */
    fun tick(): MarketStatus? {
        val next = recompute()
        publish(next)
        return next
    }

    /**
     * The screen came back: tick, ask for a live block if the one held has expired, and sleep
     * until the next boundary while the screen stays resumed.
     */
    fun onResume() {
        tick()
        refreshIfExpired()
        boundaryJob?.cancel()
        boundaryJob = scope.launch {
            while (isActive) {
                val now = clock.nowMillis()
                val next = status?.nextChangeAtMillis
                // A boundary already behind the clock (the refresh has not landed yet, or a
                // venue that answered with a past change) is polled, never spun on.
                val wait = if (next == null || next <= now) POLL_MILLIS else next - now + SETTLE_MILLIS
                delay(wait)
                tick()
                refreshIfExpired()
            }
        }
    }

    /** The screen left: nothing runs in the background. */
    fun onPause() {
        boundaryJob?.cancel()
        boundaryJob = null
    }

    private fun recompute(): MarketStatus? {
        if (!known) return null
        return MarketHours.sessionAt(clock.nowMillis(), snapshot).also { status = it }
    }

    private fun refreshIfExpired() {
        val symbol = refreshSymbol ?: return
        val held = snapshot ?: return
        val changeAt = held.nextChangeAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return
        if (clock.nowMillis() < changeAt || refreshedFor == changeAt || refreshJob?.isActive == true) return
        refreshedFor = changeAt
        refreshJob = scope.launch {
            val live = catalog.liveTrading(symbol) ?: return@launch
            snapshot = live.copy(isTradingHalted = false)
            tick()
        }
    }

    private companion object {
        /** A minute: how often to look again when no future boundary is known. */
        const val POLL_MILLIS = 60_000L

        /** A second past the boundary, so the tick lands on the far side of it. */
        const val SETTLE_MILLIS = 1_000L
    }
}
