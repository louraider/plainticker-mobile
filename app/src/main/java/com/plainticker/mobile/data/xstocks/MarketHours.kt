package com.plainticker.mobile.data.xstocks

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
    /**
     * Unix millis of the next period change: the venue's own when it said so and it parsed, the
     * calendar's next boundary when [MarketHours.sessionAt] had to fall back to the calendar.
     */
    val nextChangeAtMillis: Long?,
    /**
     * The exchange calendar at the same instant: which side of the session a closed venue is on
     * (before the open, after the close, a holiday) and when it next opens or closes. Null only
     * for the per-asset reading ([MarketHours.of]), which Detail labels by [state] alone.
     */
    val session: NyseSession? = null,
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
     * The exchange behind a whole catalog, for a screen that draws many tickers at once.
     *
     * Every asset here is a US equity on one exchange, and every `trading` block describes that
     * one exchange, so the first block that answers answers for all of them. A halt is excluded
     * deliberately: it is one issuer stopping one asset, and a list of 157 rows must not tell a
     * reader the market is shut because the first row in catalog order is halted. The per-asset
     * halt is Detail's to state, on the screen that is about that asset.
     *
     * Null for an empty catalog, which is not an answer about the venue: with no assets at all
     * there is nothing to read, and a screen with no rows on it has no premium to caveat.
     */
    fun ofCatalog(assets: List<XStockAsset>, nowMillis: Long): MarketStatus? {
        if (assets.isEmpty()) return null
        return sessionAt(nowMillis, snapshotOf(assets))
    }

    /**
     * The one trading block a whole catalog is read by: the first that answers, with its halt
     * cleared. The halt is cleared on both levels, the asset's and the block's own flag, because
     * [of] folds the two together and either one would answer HALTED for the whole list. The
     * period and the open flag still come from the block: it is only the halt that is about one
     * token rather than about the exchange behind all of them.
     */
    fun snapshotOf(assets: List<XStockAsset>): Trading? =
        assets.firstNotNullOfOrNull { it.trading }?.copy(isTradingHalted = false)

    /**
     * The venue at [nowMillis], from a [snapshot] of its trading block that may be hours old.
     *
     * The snapshot is a photograph: `currentPeriod` and `openNow` describe the moment the catalog
     * was fetched, and the catalog is cached for up to a day. So it is believed only until its own
     * `nextChangeAt`, the instant the venue itself said the period would change. Past that, or
     * with no snapshot at all, the exchange calendar ([NyseCalendar]) answers and the status says
     * so ([MarketSource.LOCAL_SCHEDULE]) until a fresh block lands. Whichever answers, [session]
     * carries the calendar's reading, so a screen can say which side of the session a closed venue
     * is on and when it next opens, in the reader's own time.
     *
     * The calendar never claims [MarketState.EXTENDED] or [MarketState.OVERNIGHT]: those are the
     * xStocks venue's own periods and only its block can report them. The calendar answers the
     * exchange's question, open or not, and [NyseSession.phase] carries the rest.
     */
    fun sessionAt(nowMillis: Long, snapshot: Trading?): MarketStatus {
        val session = NyseCalendar.session(nowMillis)
        val changeAt = snapshot?.nextChangeAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        val fresh = snapshot != null && (changeAt == null || nowMillis < changeAt)
        if (fresh) {
            return of(snapshot, halted = false, nowMillis = nowMillis).copy(session = session)
        }
        val regular = session.phase == SessionPhase.REGULAR
        return MarketStatus(
            state = if (regular) MarketState.REGULAR else MarketState.CLOSED,
            source = MarketSource.LOCAL_SCHEDULE,
            venueOpen = regular,
            nextChangeAtMillis = session.nextBoundaryMillis,
            session = session,
        )
    }

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
     * The fallback: the exchange calendar ([NyseCalendar]), open only during a trading day's
     * regular session. Weekends, the holidays in its table and the hours after a 13:00 early close
     * are all closed.
     */
    fun localSchedule(nowMillis: Long): MarketState =
        if (NyseCalendar.session(nowMillis).phase == SessionPhase.REGULAR) MarketState.REGULAR else MarketState.CLOSED

    private fun TradingPeriod.toState(): MarketState = when (this) {
        TradingPeriod.MARKET -> MarketState.REGULAR
        TradingPeriod.EXTENDED -> MarketState.EXTENDED
        TradingPeriod.OVERNIGHT -> MarketState.OVERNIGHT
        TradingPeriod.CLOSED -> MarketState.CLOSED
    }
}
