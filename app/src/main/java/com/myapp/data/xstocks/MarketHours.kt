package com.myapp.data.xstocks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Which answer said where the venue is, so a screen can tell a fact from a guess. */
enum class MarketSource {
    /** The asset's own `trading` block, which is the issuer's live venue state. */
    VENUE,

    /** The bundled weekday schedule, used only when the asset carries no `trading` block. */
    LOCAL_SCHEDULE,
}

/**
 * Where the underlying venue is right now.
 *
 * [REGULAR] is the exchange's own session, the only period in which the token's quote and the
 * underlying share move together. [EXTENDED] and [OVERNIGHT] are periods the xStocks venue keeps
 * while the exchange itself is shut, so the NYSE close is still the newest reference there.
 */
enum class MarketState {
    REGULAR,
    EXTENDED,
    OVERNIGHT,
    CLOSED,
    HALTED,
}

/** How the price row names the number beside it (docs/data-map.md, Detail gauge). */
enum class PriceLabel {
    /** The exchange is trading: the gauge reads "tracking within {n}%". */
    TRACKING_WITHIN,

    /** The exchange is shut: the gauge reads against the NYSE close. */
    VS_NYSE_CLOSE,
}

/**
 * The venue's state and where the answer came from. Everything the price row needs to label
 * itself, and nothing about how it is worded.
 */
data class MarketStatus(
    val state: MarketState,
    val source: MarketSource,
    /** Whether the venue takes orders now, whatever session it is in. */
    val venueOpen: Boolean,
    /** Unix millis of the next period change, when the venue said so and it parsed. */
    val nextChangeAtMillis: Long?,
) {
    /** The exchange's own session: only then does the token's quote track a live underlying. */
    val regularSession: Boolean get() = state == MarketState.REGULAR

    /** The issuer has stopped trading this asset, which is not the same as the exchange closing. */
    val halted: Boolean get() = state == MarketState.HALTED

    val priceLabel: PriceLabel
        get() = if (regularSession) PriceLabel.TRACKING_WITHIN else PriceLabel.VS_NYSE_CLOSE
}

/**
 * Whether the venue behind an xStock is open, as one pure function over a clock (plan T9: "hours
 * banner live + local fallback").
 *
 * The order is fixed and never inverted. The asset's own `trading` block is the source of truth:
 * it is the issuer's live answer, it knows the holidays, the half days and the halts, and it is
 * what the app must believe whenever it is there. The fifteen-line weekday schedule below is a
 * fallback for one case only, an asset whose catalog entry carries no `trading` block at all, and
 * it never overrides, corrects or second-guesses the block. It knows nothing about holidays, so
 * an answer from it is marked [MarketSource.LOCAL_SCHEDULE] and a surface may say so.
 *
 * Pure: java.time over an epoch-millis clock. No Android, no repository, no formatting.
 */
object MarketHours {

    /** The NYSE's own zone; the schedule follows it through daylight saving. */
    val ZONE: ZoneId = ZoneId.of("America/New_York")

    /** Regular session bounds in [ZONE], inclusive of the open and exclusive of the close. */
    val OPEN: LocalTime = LocalTime.of(9, 30)
    val CLOSE: LocalTime = LocalTime.of(16, 0)

    /** The state of the venue behind [asset] at [nowMillis]. */
    fun of(asset: XStockAsset?, nowMillis: Long): MarketStatus =
        of(asset?.trading, halted = asset?.isTradingHalted == true, nowMillis = nowMillis)

    /**
     * The state from one `trading` block. A null [trading] is the only case that reaches the local
     * schedule; a block that answers is believed even when it disagrees with the calendar.
     */
    fun of(trading: Trading?, halted: Boolean, nowMillis: Long): MarketStatus {
        if (trading == null) {
            val state = localSchedule(nowMillis)
            return MarketStatus(
                state = if (halted) MarketState.HALTED else state,
                source = MarketSource.LOCAL_SCHEDULE,
                venueOpen = !halted && state == MarketState.REGULAR,
                nextChangeAtMillis = null,
            )
        }
        val stopped = halted || trading.isTradingHalted
        return MarketStatus(
            state = when {
                stopped -> MarketState.HALTED
                trading.currentPeriod != null -> trading.currentPeriod.toState()
                // The block is present but silent about the period. It still answered the open
                // question, and the block is the source of truth, so its boolean decides rather
                // than the local calendar.
                trading.openNow -> MarketState.REGULAR
                else -> MarketState.CLOSED
            },
            source = MarketSource.VENUE,
            venueOpen = !stopped && trading.openNow,
            nextChangeAtMillis = trading.nextChangeAt?.let { at ->
                runCatching { Instant.parse(at).toEpochMilli() }.getOrNull()
            },
        )
    }

    /**
     * The fallback: a US equity week with no holiday calendar. Monday to Friday, 09:30 to 16:00 in
     * [ZONE], everything else closed.
     */
    fun localSchedule(nowMillis: Long): MarketState {
        val local = Instant.ofEpochMilli(nowMillis).atZone(ZONE)
        val weekend = local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY
        if (weekend) return MarketState.CLOSED
        val time = local.toLocalTime()
        val trading = !time.isBefore(OPEN) && time.isBefore(CLOSE)
        return if (trading) MarketState.REGULAR else MarketState.CLOSED
    }

    private fun TradingPeriod.toState(): MarketState = when (this) {
        TradingPeriod.MARKET -> MarketState.REGULAR
        TradingPeriod.EXTENDED -> MarketState.EXTENDED
        TradingPeriod.OVERNIGHT -> MarketState.OVERNIGHT
        TradingPeriod.CLOSED -> MarketState.CLOSED
    }
}
