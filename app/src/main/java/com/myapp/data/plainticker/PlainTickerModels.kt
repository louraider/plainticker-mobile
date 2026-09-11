package com.myapp.data.plainticker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

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
    val method: Method? = null,
) {
    /** Epoch millis of `as_of`, or null when missing or not ISO-8601. */
    fun asOfEpochMillis(): Long? =
        asOf?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    /** Whole days between `as_of` and [nowEpochMillis]; null when `as_of` is unusable. */
    fun ageDays(nowEpochMillis: Long = System.currentTimeMillis()): Long? =
        asOfEpochMillis()?.let { (nowEpochMillis - it).coerceAtLeast(0L) / 86_400_000L }

    val schemaVersion: String? get() = method?.schemaVersion
}

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

/** Piotroski F-score: nine boolean signals, summed. */
@Serializable
data class FScore(
    val score: Int? = null,
    val scale: String? = null,
    val breakdown: FScoreBreakdown? = null,
    val signals: List<Boolean> = emptyList(),
) {
    val passedCount: Int get() = signals.count { it }
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
