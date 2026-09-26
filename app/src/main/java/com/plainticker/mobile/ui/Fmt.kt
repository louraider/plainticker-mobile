package com.plainticker.mobile.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The one number and time formatter (DESIGN.md section 7, plan section 13 Pass 7). Every
 * number on every screen passes through it.
 *
 * en-US fixed: comma thousands, period decimal, month names in sentence case, times in UTC
 * except where a function says it reads the reader's own zone ([clock], [weekday]).
 * Pure JVM (java.math, java.time), no Android dependency, so it runs in plain unit tests.
 *
 * Rounding is half-up on the shortest decimal representation of the double (what a person
 * would type), so 0.005 becomes 0.01 and no value ever renders with a minus sign on zero.
 * A non-finite double renders as "-", the same neutral placeholder the screens use for a
 * missing value, instead of crashing the composition.
 */
object Fmt {

    /** U+2026, one character, never three periods. */
    private const val ELLIPSIS = '…'
    /** The missing-value placeholder, the same one value_missing draws. */
    const val MISSING = "-"

    private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val WEEKDAYS = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** Compact money units, largest first; [compactMoney] takes the first that the amount fills. */
    private val MONEY_UNITS = listOf(
        1_000_000_000_000L to "T",
        1_000_000_000L to "B",
        1_000_000L to "M",
        1_000L to "k",
    )

    private val THOUSAND = BigDecimal.valueOf(1_000L)

    // ---- Money ----------------------------------------------------------------------------

    /**
     * A USD amount: "$366.17" with two decimals, four when the rounded absolute value is
     * below one dollar ("$0.4213"). Zero is "$0.00": it has no sub-cent digits to keep, and a
     * zero total reads as money, not as a bug. Negative amounts carry the sign first ("-$1.50").
     */
    fun price(usd: Double): String {
        if (!usd.isFinite()) return MISSING
        val exact = BigDecimal.valueOf(usd)
        val fine = exact.setScale(4, RoundingMode.HALF_UP)
        val scaled = if (fine.signum() != 0 && fine.abs() < BigDecimal.ONE) fine else exact.setScale(2, RoundingMode.HALF_UP)
        return sign(scaled) + "$" + grouped(scaled)
    }

    /**
     * A USD amount at the width a meta line can spare, so a pool size fits beside the rest of a
     * row: whole dollars under a thousand ("$34", "$949"), then one decimal and a unit above it
     * ("$12.5k", "$18k", "$1.3M", "$2.4B") with a trailing ".0" trimmed. Under a dollar it keeps
     * two decimals ("$0.40") instead of rounding a real pool away to "$0"; exactly zero is "$0".
     * Negative amounts carry the sign first ("-$1.2k").
     *
     * [price] stays the form for a price a person compares (four decimals under a dollar, cents
     * above); this is the form for a magnitude a person only needs the size of.
     *
     * [roundDown] truncates instead of rounding to nearest, and exists for one reason: a pool of
     * $9,960 rounds to "$10k", which is the exact value of the tracking floor, so a row would read
     * "Pool holds $10k, too thin to track" beside another token tracked at the same stated size.
     * Naming a pool smaller than it is can only understate the depth; rounding it up across the
     * floor contradicts the sentence it sits in.
     */
    fun compactMoney(usd: Double, roundDown: Boolean = false): String {
        if (!usd.isFinite()) return MISSING
        val mode = if (roundDown) RoundingMode.DOWN else RoundingMode.HALF_UP
        val exact = BigDecimal.valueOf(usd)
        val whole = exact.setScale(0, mode)
        if (whole.signum() == 0) {
            if (exact.signum() == 0) return "$0"
            return sign(exact) + "$" + grouped(exact.setScale(2, mode))
        }
        var unit = MONEY_UNITS.indexOfFirst { whole.abs() >= BigDecimal.valueOf(it.first) }
        if (unit < 0) return sign(whole) + "$" + grouped(whole)
        var scaled = whole.divide(BigDecimal.valueOf(MONEY_UNITS[unit].first), 1, mode)
        // Rounding up can fill the next unit: 999,960 is "$1M", never "$1000k".
        if (scaled.abs() >= THOUSAND && unit > 0) {
            unit -= 1
            scaled = whole.divide(BigDecimal.valueOf(MONEY_UNITS[unit].first), 1, mode)
        }
        return sign(scaled) + "$" + grouped(scaled.stripTrailingZeros()) + MONEY_UNITS[unit].second
    }

    // ---- Percentages ----------------------------------------------------------------------

    /**
     * A percentage with a fixed number of decimals, two by default: "+0.09%", "-0.04%".
     * With [signed] the sign is explicit, and a value that rounds to zero is "+0.00%".
     * Unsigned ("9.8%", "100.7%") is for magnitudes that have no direction.
     */
    fun percent(value: Double, signed: Boolean = true, decimals: Int = 2): String {
        if (!value.isFinite()) return MISSING
        val scaled = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        val sign = when {
            scaled.signum() < 0 -> "-"
            signed -> "+"
            else -> ""
        }
        return sign + grouped(scaled) + "%"
    }

    // ---- Plain numbers --------------------------------------------------------------------

    /** A whole number with comma thousands: "26,101". */
    fun count(value: Long): String {
        val digits = value.toString()
        return if (value < 0) "-" + groupDigits(digits.substring(1)) else groupDigits(digits)
    }

    fun count(value: Int): String = count(value.toLong())

    /** A Solana slot: "445,912,118". */
    fun slot(slot: Long): String = count(slot)

    /** A number with exactly [decimals] decimals and comma thousands: "0.71", "1.00", "51". */
    fun decimal(value: Double, decimals: Int = 2): String {
        if (!value.isFinite()) return MISSING
        val scaled = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        return sign(scaled) + grouped(scaled)
    }

    /**
     * A number with up to [maxDecimals] decimals, six by default, trailing zeros trimmed, for
     * captions and stated scales: "0.5" (the gauge scale), "1", "1,000.25". Token quantities have
     * the same shape and their own name, [tokenAmount].
     */
    fun plain(value: Double, maxDecimals: Int = 6): String =
        if (value.isFinite()) trimmed(BigDecimal.valueOf(value), maxDecimals) else MISSING

    // ---- Token amounts --------------------------------------------------------------------

    /**
     * A token quantity with up to [maxDecimals] decimals, six by default, trailing zeros
     * trimmed: "2.01364", "2", "0.01364".
     */
    fun tokenAmount(amount: BigDecimal, maxDecimals: Int = 6): String = trimmed(amount, maxDecimals)

    fun tokenAmount(amount: Double, maxDecimals: Int = 6): String =
        if (amount.isFinite()) trimmed(BigDecimal.valueOf(amount), maxDecimals) else MISSING

    /**
     * A token quantity from its base units: [raw] with [decimals] on-chain decimals, so
     * 5_000_000 USDC base units (6 decimals) is "5" and 13_640_000 lamports (9) is "0.01364".
     */
    fun tokenAmount(raw: Long, decimals: Int, maxDecimals: Int = 6): String =
        tokenAmount(BigDecimal.valueOf(raw).movePointLeft(decimals), maxDecimals)

    // ---- Absolute time --------------------------------------------------------------------

    /** An absolute time in UTC, month in sentence case: "10 Sep 2026 14:55 UTC". */
    fun utc(instant: Instant): String {
        val t = instant.atOffset(ZoneOffset.UTC)
        return "${t.dayOfMonth} ${MONTHS[t.monthValue - 1]} ${t.year} ${two(t.hour)}:${two(t.minute)} UTC"
    }

    fun utc(epochMillis: Long): String = utc(Instant.ofEpochMilli(epochMillis))

    // ---- The reader's own time ------------------------------------------------------------

    /**
     * A time of day in the reader's own zone, 24-hour: "16:30". For a surface a beginner reads at
     * a glance (Today's venue line, the digest's stamp), where a UTC time is a conversion the
     * reader should not have to do. [zone] is passed in, never read here, so this stays pure.
     */
    fun clock(epochMillis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "${two(t.hour)}:${two(t.minute)}"
    }

    /**
     * An absolute time in the reader's own zone, the same shape [utc] prints but with no "UTC"
     * label, since it is not one: "10 Sep 2026 14:55". For a surface that needs a full date and
     * not only a time of day ([clock]) and reads on the reader's own clock rather than a fixed
     * one, the same reason [clock] exists.
     */
    fun localDateTime(epochMillis: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "${t.dayOfMonth} ${MONTHS[t.monthValue - 1]} ${t.year} ${two(t.hour)}:${two(t.minute)}"
    }

    /** The weekday in the reader's own zone, in sentence case: "Monday". */
    fun weekday(epochMillis: Long, zone: ZoneId): String =
        WEEKDAYS[Instant.ofEpochMilli(epochMillis).atZone(zone).dayOfWeek.value - 1]

    /**
     * The weekday for a calendar day that is already the right calendar day, no zone conversion:
     * for a date a payload sent as a bare day (Today's "Reports this week"), never an instant, so
     * there is nothing here to re-zone in the first place.
     */
    fun weekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    /** The same calendar day [monthDay] prints, day before month, no year: "29 Sep". */
    fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"

    /** How many calendar days, in the reader's own zone, lie between [nowMillis] and [thenMillis]. */
    fun daysAhead(thenMillis: Long, nowMillis: Long, zone: ZoneId): Long {
        val then = Instant.ofEpochMilli(thenMillis).atZone(zone).toLocalDate()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(now, then)
    }

    /** A calendar day in UTC without the year, for report dates and history rows: "Oct 22". */
    fun monthDay(instant: Instant): String {
        val t = instant.atOffset(ZoneOffset.UTC)
        return "${MONTHS[t.monthValue - 1]} ${t.dayOfMonth}"
    }

    /** The same day without the year, for a report date the payload sends as a calendar day. */
    fun monthDay(date: LocalDate): String = "${MONTHS[date.monthValue - 1]} ${date.dayOfMonth}"

    /** A calendar day, for a stamp that carries no time of day: "12 Sep 2026". */
    fun day(date: LocalDate): String = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]} ${date.year}"

    // ---- Relative time --------------------------------------------------------------------

    /**
     * How long ago an event happened: "2 s ago", "5 min ago", "3 h ago", "2 d ago". Whole
     * units, truncated: 59 s stays seconds, 60 s is "1 min ago", 24 h is "1 d ago". A [then]
     * after [now] reads "0 s ago".
     */
    fun relativeAgo(then: Instant, now: Instant): String = elapsed(then, now) + " ago"

    /** The age of an analysis, same ladder as [relativeAgo] with the "old" suffix: "2 d old". */
    fun ageOld(then: Instant, now: Instant): String = elapsed(then, now) + " old"

    /** The age of an analysis when the server already counted the days: "2 d old". */
    fun daysOld(days: Int): String = count(days) + " d old"

    private fun elapsed(then: Instant, now: Instant): String {
        val seconds = Duration.between(then, now).seconds.coerceAtLeast(0L)
        return when {
            seconds < 60L -> "$seconds s"
            seconds < 3_600L -> "${seconds / 60L} min"
            seconds < 86_400L -> "${seconds / 3_600L} h"
            else -> "${count(seconds / 86_400L)} d"
        }
    }

    // ---- Durations ------------------------------------------------------------------------

    /**
     * A duration in whole seconds, floored and never negative: "0 s", "9 s", "14 s". For a
     * phase that is still running and for a countdown, both of which are redrawn once a second:
     * a tenth there would claim a precision the refresh does not have.
     */
    fun seconds(millis: Long): String = count(millis.coerceAtLeast(0L) / 1_000L) + " s"

    /**
     * A finished duration to a tenth: "3.1 s", "14.4 s". For a phase that has been measured and
     * will not change again, where the tenth is real. The wallet round trip measured 12.7 to
     * 14.4 s on the Seeker, which is the number this product rests on stating honestly.
     */
    fun secondsExact(millis: Long): String =
        plain(millis.coerceAtLeast(0L) / 1_000.0, maxDecimals = 1) + " s"

    // ---- Keys and signatures --------------------------------------------------------------

    /**
     * A wallet address or transaction signature as a fragment: the first [head] and last
     * [tail] characters around one ellipsis character, "3kF9…Qm2v". A key no longer than
     * head plus tail is returned whole.
     */
    fun shortKey(key: String, head: Int = 4, tail: Int = 4): String {
        require(head >= 0 && tail >= 0) { "head and tail must not be negative" }
        if (key.length <= head + tail) return key
        return key.take(head) + ELLIPSIS + key.takeLast(tail)
    }

    // ---- Internals ------------------------------------------------------------------------

    private fun sign(value: BigDecimal): String = if (value.signum() < 0) "-" else ""

    /** Rounded half-up to [maxDecimals], trailing zeros trimmed, comma thousands. */
    private fun trimmed(value: BigDecimal, maxDecimals: Int): String {
        val scaled = value.setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        return sign(scaled) + grouped(scaled)
    }

    /** The absolute value with comma thousands in the integer part; the scale is kept as is. */
    private fun grouped(value: BigDecimal): String {
        val plain = value.abs().toPlainString()
        val dot = plain.indexOf('.')
        return if (dot < 0) groupDigits(plain) else groupDigits(plain.substring(0, dot)) + plain.substring(dot)
    }

    private fun groupDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val out = StringBuilder(digits.length + digits.length / 3)
        val lead = digits.length % 3
        if (lead > 0) out.append(digits, 0, lead)
        var i = lead
        while (i < digits.length) {
            if (out.isNotEmpty()) out.append(',')
            out.append(digits, i, i + 3)
            i += 3
        }
        return out.toString()
    }

    private fun two(n: Int): String = if (n < 10) "0$n" else n.toString()
}
