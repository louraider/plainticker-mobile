package com.plainticker.mobile.repo

import com.plainticker.mobile.data.xstocks.Trading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.time.Instant

/**
 * The venue's trading block as the newest read of any screen left it, shared app-wide (final QA
 * of 1.3.19: Today said "Opens today at 16:30" while Stocks, on the same launch, said "The NYSE
 * looks closed by its usual hours. Its live hours did not load."). Each [MarketClock] used to keep
 * its own block, from its own catalog read, so the two screens could keep blocks of different
 * ages. Now every block any clock reads is [offer]ed here, and every clock reads the freshest one
 * there is, so if one screen has the live hours every screen has them.
 *
 * [loading] is true while any clock is asking for a live block. A clock does not say the live
 * hours "did not load" while one is still on its way.
 */
class VenueHours {

    private val _block = MutableStateFlow<Trading?>(null)

    /** The freshest block offered so far: the one whose own `nextChangeAt` is latest. */
    val block: StateFlow<Trading?> = _block.asStateFlow()

    private val inFlight = MutableStateFlow(0)
    private val _loading = MutableStateFlow(false)

    /** A live block is being asked for by some screen. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Keeps [trading] when it is at least as fresh as the block held. The halt is never shared. */
    fun offer(trading: Trading?) {
        val offered = trading?.copy(isTradingHalted = false) ?: return
        _block.update { held -> if (held == null || changeAt(offered) >= changeAt(held)) offered else held }
    }

    /** Runs one live read with [loading] raised for its duration, and offers what it found. */
    suspend fun read(fetch: suspend () -> Trading?): Trading? {
        inFlight.update { it + 1 }
        _loading.value = true
        try {
            return fetch().also(::offer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } finally {
            val left = inFlight.updateAndGet { it - 1 }
            _loading.value = left > 0
        }
    }

    companion object {
        /** A block's own `nextChangeAt`, or the earliest instant for one that states none. */
        fun changeAt(trading: Trading): Long =
            trading.nextChangeAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: Long.MIN_VALUE

        /** Believed until its own `nextChangeAt`, the rule [com.plainticker.mobile.data.xstocks.MarketHours.sessionAt] keeps. */
        fun isFresh(trading: Trading, nowMillis: Long): Boolean {
            val at = trading.nextChangeAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            return at == null || nowMillis < at
        }
    }
}
