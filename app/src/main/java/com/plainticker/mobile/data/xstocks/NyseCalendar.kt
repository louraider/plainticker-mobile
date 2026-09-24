package com.plainticker.mobile.data.xstocks

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Where the NYSE's own day stands, by the exchange calendar.
 *
 * [PRE_MARKET] and [AFTER_HOURS] are the calendar's windows on either side of a trading day's
 * session (04:00 to the open, the close to 20:00, New York time). The exchange itself is shut in
 * both; the xStocks venue can still take orders, with thinner pools, which is why they are their
 * own phases rather than folded into [CLOSED].
 */
enum class SessionPhase {
    PRE_MARKET,
    REGULAR,
    AFTER_HOURS,
    CLOSED,
}

/**
 * One reading of the calendar at one instant. Every time is epoch millis, computed as a New York
 * wall-clock time first and converted after, so daylight saving and midnight are exact rather
 * than a fixed offset that is right for half the year.
 */
data class NyseSession(
    val phase: SessionPhase,
    /** The next regular open strictly after now, or today's own open while now is before it. */
    val nextOpenMillis: Long,
    /** Today's close while the session is on, null otherwise. */
    val nextCloseMillis: Long?,
    /** The next instant [phase] changes: the pre-market start, the open, the close or 20:00. */
    val nextBoundaryMillis: Long,
    /** Today (New York date) is a weekday the exchange keeps shut. */
    val holiday: Boolean,
    /** Today (New York date) closes at 13:00 instead of 16:00. */
    val earlyClose: Boolean,
)

/**
 * The NYSE holiday and early-close table for 2026 and 2027, from the exchange's own published
 * calendar, and the session arithmetic over it.
 *
 * Outside those two years the table knows no holidays, so a date there reads as an ordinary
 * weekday: the same thing the previous fifteen-line schedule assumed for every date. Extend the
 * two sets below when the exchange publishes the next year; `MarketHoursTest` pins every entry
 * that is here, so a typo cannot hide.
 *
 * Pure java.time: no Android, no clock of its own.
 */
object NyseCalendar {

    val PRE_MARKET_OPEN: LocalTime = LocalTime.of(4, 0)
    val OPEN: LocalTime = LocalTime.of(9, 30)
    val CLOSE: LocalTime = LocalTime.of(16, 0)
    val EARLY_CLOSE: LocalTime = LocalTime.of(13, 0)
    val AFTER_HOURS_END: LocalTime = LocalTime.of(20, 0)

    /** Full-day closures on weekdays. A holiday that falls on a weekend is observed on the date listed. */
    val HOLIDAYS: Set<LocalDate> = setOf(
        // 2026
        LocalDate.of(2026, 1, 1), // New Year's Day
        LocalDate.of(2026, 1, 19), // Martin Luther King Jr. Day
        LocalDate.of(2026, 2, 16), // Washington's Birthday
        LocalDate.of(2026, 4, 3), // Good Friday
        LocalDate.of(2026, 5, 25), // Memorial Day
        LocalDate.of(2026, 6, 19), // Juneteenth
        LocalDate.of(2026, 7, 3), // Independence Day, observed (4 July is a Saturday)
        LocalDate.of(2026, 9, 7), // Labor Day
        LocalDate.of(2026, 11, 26), // Thanksgiving
        LocalDate.of(2026, 12, 25), // Christmas
        // 2027
        LocalDate.of(2027, 1, 1), // New Year's Day
        LocalDate.of(2027, 1, 18), // Martin Luther King Jr. Day
        LocalDate.of(2027, 2, 15), // Washington's Birthday
        LocalDate.of(2027, 3, 26), // Good Friday
        LocalDate.of(2027, 5, 31), // Memorial Day
        LocalDate.of(2027, 6, 18), // Juneteenth, observed (19 June is a Saturday)
        LocalDate.of(2027, 7, 5), // Independence Day, observed (4 July is a Sunday)
        LocalDate.of(2027, 9, 6), // Labor Day
        LocalDate.of(2027, 11, 25), // Thanksgiving
        LocalDate.of(2027, 12, 24), // Christmas, observed (25 December is a Saturday)
    )

    /** Trading days that close at 13:00 New York time. */
    val EARLY_CLOSES: Set<LocalDate> = setOf(
        LocalDate.of(2026, 11, 27), // the day after Thanksgiving
        LocalDate.of(2026, 12, 24), // Christmas Eve
        LocalDate.of(2027, 11, 26), // the day after Thanksgiving
    )

    fun isTradingDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && date !in HOLIDAYS

    fun closeTime(date: LocalDate): LocalTime = if (date in EARLY_CLOSES) EARLY_CLOSE else CLOSE

    /** After-hours runs four hours past the close, so an early-close day's window ends at 17:00. */
    private fun afterHoursEnd(date: LocalDate): LocalTime =
        if (date in EARLY_CLOSES) EARLY_CLOSE.plusHours(4) else AFTER_HOURS_END

    private fun at(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(MarketHours.ZONE).toInstant().toEpochMilli()

    /** The calendar at [nowMillis]. */
    fun session(nowMillis: Long): NyseSession {
        val local: ZonedDateTime = Instant.ofEpochMilli(nowMillis).atZone(MarketHours.ZONE)
        val today = local.toLocalDate()
        val trading = isTradingDay(today)

        val phase = if (!trading) {
            SessionPhase.CLOSED
        } else {
            when {
                nowMillis < at(today, PRE_MARKET_OPEN) -> SessionPhase.CLOSED
                nowMillis < at(today, OPEN) -> SessionPhase.PRE_MARKET
                nowMillis < at(today, closeTime(today)) -> SessionPhase.REGULAR
                nowMillis < at(today, afterHoursEnd(today)) -> SessionPhase.AFTER_HOURS
                else -> SessionPhase.CLOSED
            }
        }

        val nextOpen = if (trading && nowMillis < at(today, OPEN)) at(today, OPEN) else at(nextTradingDay(today), OPEN)
        val nextClose = if (phase == SessionPhase.REGULAR) at(today, closeTime(today)) else null

        val todaysBoundaries = if (trading) {
            listOf(at(today, PRE_MARKET_OPEN), at(today, OPEN), at(today, closeTime(today)), at(today, afterHoursEnd(today)))
        } else {
            emptyList()
        }
        val nextBoundary = todaysBoundaries.firstOrNull { it > nowMillis }
            ?: at(nextTradingDay(today), PRE_MARKET_OPEN)

        return NyseSession(
            phase = phase,
            nextOpenMillis = nextOpen,
            nextCloseMillis = nextClose,
            nextBoundaryMillis = nextBoundary,
            holiday = today in HOLIDAYS,
            earlyClose = trading && today in EARLY_CLOSES,
        )
    }

    /** The first trading day strictly after [date]. A fortnight is far more than any run of closures. */
    private fun nextTradingDay(date: LocalDate): LocalDate {
        var d = date.plusDays(1)
        repeat(14) {
            if (isTradingDay(d)) return d
            d = d.plusDays(1)
        }
        return d
    }
}
