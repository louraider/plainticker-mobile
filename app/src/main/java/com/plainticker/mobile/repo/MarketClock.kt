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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
 *
 * **One venue for every screen** (final QA of 1.3.19: Today had the live hours while Stocks, on
 * the same launch, said they did not load). Every block a clock reads, from a catalog or a live
 * refresh, is offered to [hours], shared app-wide through the catalog repository; every clock
 * reads the freshest block there, and ticks when it changes. [loading] says a live read is still
 * out anywhere, so no screen calls the hours missing while they are on their way.
 */
class MarketClock(
    private val clock: Clock,
    private val catalog: CatalogRepository,
    private val scope: CoroutineScope,
    /**
     * Until a catalog answers, read the exchange calendar instead of saying nothing. Stocks sets it
     * (device QA of 1.3.18): its hours banner arrived with the live catalog seconds after the first
     * frame and pushed the chips and the list down by its own height. The calendar is on the
     * device, so the venue line can stand from the first frame; the status says it came from the
     * calendar ([com.plainticker.mobile.data.xstocks.MarketSource.LOCAL_SCHEDULE]). The screen
     * turns it off ([calendarUntilKnown]) once the catalog has settled with nothing: then nothing
     * is known about the venue, and the old rule, no status at all, applies again.
     */
    localUntilKnown: Boolean = false,
    private val hours: VenueHours = catalog.venueHours ?: VenueHours(),
    private val publish: (MarketStatus?) -> Unit,
) {
    /** See the constructor's `localUntilKnown`. */
    var calendarUntilKnown: Boolean = localUntilKnown

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

    /** A live block is being asked for, by this clock or another screen's. */
    val loading: Boolean get() = hours.loading.value

    init {
        // Another screen's read lands here too: a fresher block, or a read starting or ending.
        scope.launch {
            combine(hours.block, hours.loading) { block, loading -> block to loading }
                .drop(1)
                .collect { tick() }
        }
    }

    /** A catalog arrived: keep its block (not a finished status) and publish a fresh reading. */
    fun setAssets(assets: List<XStockAsset>): MarketStatus? {
        if (assets.isEmpty()) return if (known) status else recompute()
        known = true
        val block = MarketHours.snapshotOf(assets)
        // The same block again (Stocks republishes on every catalog page) keeps its one refresh:
        // a live read that failed is not asked again per page, only at the next tick or resume.
        if (block != snapshot) refreshedFor = null
        snapshot = block
        hours.offer(snapshot)
        refreshSymbol = assets.firstOrNull { it.trading != null }?.symbol ?: assets.first().symbol
        // A block already past its own change (a catalog from disk, the bundled snapshot) asks for
        // the live one now, not at the next tick: until it lands the hours are loading, not lost.
        refreshIfExpired()
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
        val now = clock.nowMillis()
        val block = effective(now)
        if (!known && block?.let { VenueHours.isFresh(it, now) } != true) {
            if (!calendarUntilKnown) return null.also { status = null }
            return MarketHours.sessionAt(now, null).also { status = it }
        }
        return MarketHours.sessionAt(now, block).also { status = it }
    }

    /** This clock's own block, or the shared one when that is fresh and at least as new. */
    private fun effective(nowMillis: Long): Trading? {
        val own = snapshot
        val shared = hours.block.value ?: return own
        if (!VenueHours.isFresh(shared, nowMillis)) return own
        if (own == null || !VenueHours.isFresh(own, nowMillis)) return shared
        return if (VenueHours.changeAt(shared) >= VenueHours.changeAt(own)) shared else own
    }

    private fun refreshIfExpired() {
        val symbol = refreshSymbol ?: return
        val held = effective(clock.nowMillis()) ?: return
        val changeAt = held.nextChangeAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return
        if (clock.nowMillis() < changeAt || refreshedFor == changeAt || refreshJob?.isActive == true) return
        refreshedFor = changeAt
        refreshJob = scope.launch {
            val live = hours.read { catalog.liveTrading(symbol) }
            if (live != null) snapshot = live.copy(isTradingHalted = false)
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
