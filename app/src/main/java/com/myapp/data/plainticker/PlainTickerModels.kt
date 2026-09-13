package com.myapp.data.plainticker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/** Colour hint PlainTicker attaches to a headline or an axis. Unknown values decode as null. */
@Serializable
enum class Tone {
    @SerialName("positive") POSITIVE,
    @SerialName("caution") CAUTION,
    @SerialName("danger") DANGER,
}

// ---- GET /api/v1/summary -------------------------------------------------------------

/** One row per ticker PlainTicker has classified, sorted by composite desc. */
@Serializable
data class SummaryResponse(
    val schema: String,
    @SerialName("generated_at") val generatedAt: String,
    val rows: List<SummaryRow> = emptyList(),
)

@Serializable
data class SummaryRow(
    val ticker: String,
    val company: String? = null,
    val headline: String? = null,
    val tone: Tone? = null,
    @SerialName("setup_score") val setupScore: Int? = null,
    val composite: Double? = null,
    val fscore: Int? = null,
    val sector: String? = null,
    @SerialName("computed_at") val computedAt: String? = null,
    /** True when the server's cache for this ticker has expired. The row is still served. */
    val stale: Boolean = false,
    @SerialName("age_days") val ageDays: Int? = null,
)

// ---- GET /api/v1/{TICKER}  (schema v1.1) -----------------------------------------------

/**
 * The classification payload for one ticker, modelled down to what the app renders.
 * Unknown keys are ignored on purpose: the server adds fields without a schema bump.
 */
@Serializable
data class AnalysisPayload(
    val ticker: String,
    val company: String? = null,
    val sector: String? = null,
    @SerialName("as_of") val asOf: String? = null,
    val axes: Axes = Axes(),
    val fscore: FScore? = null,
    @SerialName("composite_percentile") val compositePercentile: Double? = null,
    val setup: Setup? = null,
    val forward: Forward? = null,
    val method: Method? = null,
) {
    /** Epoch millis of `as_of`, or null when missing or not ISO-8601. */
    fun asOfEpochMillis(): Long? =
        asOf?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    /** Whole days between `as_of` and [nowEpochMillis]; null when `as_of` is unusable. */
    fun ageDays(nowEpochMillis: Long = System.currentTimeMillis()): Long? =
        asOfEpochMillis()?.let { (nowEpochMillis - it).coerceAtLeast(0L) / 86_400_000L }

    /**
     * The next report date as a calendar day, or null when the provider sent none and null when
     * it sent something that is not a date. The Watchlist is the one screen that reads it: a
     * watched company's next report is the reason a person watches, and it is the only field of
     * `forward` this build renders (docs/data-map.md, "Watchlist (T12)").
     */
    fun nextReportDate(): LocalDate? =
        forward?.raw?.nextEarningsDate?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val schemaVersion: String? get() = method?.schemaVersion
}

/**
 * The forward block, modelled down to the one field this app draws.
 *
 * The rest of `forward.raw` (the 3Y EPS CAGR, the forward multiples, the beat history) stays off
 * the hackathon build by decision, and `forward.score` never reaches a screen at all; both are
 * recorded in docs/data-map.md. `raw` itself is null when the provider call failed, and every
 * field inside it may be null on its own, so nothing here has a default that invents a date.
 */
@Serializable
data class Forward(
    val raw: ForwardRaw? = null,
)

@Serializable
data class ForwardRaw(
    /** The next scheduled report as an ISO calendar day, e.g. "2026-10-28". */
    val nextEarningsDate: String? = null,
)

/** The three strata the detail screen draws: quality, valuation, momentum. */
@Serializable
data class Axes(
    val quality: Axis? = null,
    val valuation: Axis? = null,
    val momentum: Axis? = null,
)

@Serializable
data class Axis(
    val value: Double? = null,
    /** Human scale of [value], e.g. "0-9", "0-100", "0-1". */
    val scale: String? = null,
    /** [value] normalised into 0..1 for drawing. */
    val position: Double? = null,
    /** State word the server assigned, e.g. "strong", "fair", "high". */
    val state: String? = null,
    @SerialName("label_en") val labelEn: String? = null,
    val tone: Tone? = null,
)

/**
 * Piotroski F-score. `signals` holds the nine checks in fixed order: true = passed,
 * false = evaluated and failed, null = unknown (the server widened this from boolean on
 * 2026-06-16 when the data behind a check is missing, e.g. a bank with no COGS).
 * `score` counts only the true ones; so does [passedCount].
 */
@Serializable
data class FScore(
    val score: Int? = null,
    val scale: String? = null,
    val breakdown: FScoreBreakdown? = null,
    val signals: List<Boolean?> = emptyList(),
) {
    val passedCount: Int get() = signals.count { it == true }
}

@Serializable
data class FScoreBreakdown(
    val profitability: Int? = null,
    val leverageLiquidity: Int? = null,
    val efficiency: Int? = null,
)

@Serializable
data class Setup(
    val score: Int? = null,
    val scale: String? = null,
    val criteria: List<SetupCriterion> = emptyList(),
)

@Serializable
data class SetupCriterion(
    val key: String,
    val passed: Boolean = false,
)

/** The trust block: what this number is and is not. Shown before anything else. */
@Serializable
data class Method(
    @SerialName("is_prediction") val isPrediction: Boolean = false,
    val kind: String? = null,
    @SerialName("statement_en") val statementEn: String? = null,
    @SerialName("schema_version") val schemaVersion: String? = null,
)
