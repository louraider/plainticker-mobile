package com.plainticker.mobile.data.plainticker

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
    /**
     * The next scheduled report as a US Eastern calendar day, e.g. "2026-09-29", or null: absent
     * until the server deploys this field (Today's "Reports this week" block, added alongside it),
     * and possibly still null afterward for a company with no known date. `ignoreUnknownKeys`
     * already covers a server that predates the key entirely; this default covers the same server
     * sending the row without it for one ticker.
     */
    @SerialName("next_report_date") val nextReportDate: String? = null,
    /**
     * Whether [nextReportDate] is confirmed by the company (true), an estimate (false), or unknown
     * (null, read the same as an estimate would be read anywhere the sentence must pick one, but
     * never labelled "estimated" itself: only an explicit false earns that word).
     */
    @SerialName("next_report_confirmed") val nextReportConfirmed: Boolean? = null,
) {
    /**
     * [nextReportDate] parsed, or null when it is absent or is not a calendar day. A bare
     * [LocalDate], never an [Instant]: the server states this as a US Eastern calendar day, and
     * turning it into a moment in time would invite converting it into the reader's own zone next,
     * which is exactly the shift [com.plainticker.mobile.ui.today.reportsThisWeek]'s own doc
     * comment says this app must not do.
     */
    fun nextReportLocalDate(): LocalDate? =
        nextReportDate?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

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
    /**
     * A paid element under the Pro-numbers lock (founder decision 2026-09-23,
     * `lib/api/locked-numbers.ts`): null for a free, non-AAPL caller while it withholds
     * [Axes.valuation]/[Axes.momentum] too, unlocked and unaffected for AAPL or an entitled
     * caller. Carries no `locked` flag of its own on the wire; [DetailModel]'s `compositeMeta`
     * reads the two axes' own [Axis.locked] to tell this apart from a payload that simply has no
     * percentile.
     */
    @SerialName("composite_percentile") val compositePercentile: Double? = null,
    val setup: Setup? = null,
    val forward: Forward? = null,
    val method: Method? = null,
    /**
     * The classification verdict, a paid element (docs/plan-monetisation-2026-09-19.md): every
     * field is nullable and [Verdict.locked] defaults to false, so this whole block is absent
     * without breaking anything for a server that predates it, or one with monetization off. See
     * [Verdict] for what an unentitled caller receives instead of the word itself.
     */
    val verdict: Verdict? = null,
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
 *
 * The Pro-numbers lock (founder decision 2026-09-23) also nulls `forward.score` and four
 * `forward.raw` growth figures (`revenueGrowthTTMYoy`, `epsGrowthTTMYoy`, `epsCagr3y`,
 * `epsGrowthShort`) for a free, non-AAPL caller. None of the five is modelled here, by the same
 * decision recorded above, so none of them is a field this app can find null either way; the lock
 * changes nothing this class parses or a screen draws. `ignoreUnknownKeys` drops all five like any
 * other key this class does not name.
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

/**
 * One stratum of "Against the sector". [value], [position], [state], the labels and [tone] are a
 * paid element for [valuation] and [momentum] under the Pro-numbers lock (founder decision
 * 2026-09-23, `lib/api/locked-numbers.ts`'s `lockAxis`): a free, non-AAPL caller's `valuation`
 * value IS the composite (`computePerspective`), so leaving it free let a caller estimate the
 * withheld verdict closely. [quality] is not locked (it is the F-Score stratum, already public and
 * not one of the verdict's own inputs), so this field defaults to false and only ever turns true on
 * the two axes the server actually withholds.
 *
 * The shape mirrors [Verdict.locked] on purpose: every field above but [scale] and [locked] itself
 * comes back null while [locked] comes back true, [scale] is kept (`lockAxis`'s own comment: "scale
 * kept"), and a server that predates this lock, or has monetization off, never sends the key at
 * all, so [locked] defaults to false and this class draws exactly as it always did.
 */
@Serializable
data class Axis(
    val value: Double? = null,
    /** Human scale of [value], e.g. "0-9", "0-100", "0-1". Kept even when [locked]. */
    val scale: String? = null,
    /** [value] normalised into 0..1 for drawing. */
    val position: Double? = null,
    /** State word the server assigned, e.g. "strong", "fair", "high". */
    val state: String? = null,
    @SerialName("label_en") val labelEn: String? = null,
    val tone: Tone? = null,
    /** True while this axis is withheld by the Pro-numbers lock. Additive; absent decodes false. */
    val locked: Boolean = false,
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

/**
 * The classification verdict on `GET /api/v1/{ticker}` (docs/plan-monetisation-2026-09-19.md): the
 * founder's decision that this word is a paid element, blurred until a caller presents a pass.
 *
 * With a valid `X-PT-Code` header, or a Pro session, the server answers exactly as it always did:
 * [code], [labelUk], [labelEn] and [tone] carry the classification and [locked] is absent from the
 * wire, which decodes to false. Without one, while monetization is on, every field but [locked]
 * comes back null and [locked] comes back true; the real word never reaches this app, so there is
 * nothing here to filter or withhold on the client, only a fact to read off [locked].
 *
 * Every field is nullable and [locked] defaults to false, so a server that predates this block, or
 * one with monetization off, sends no `verdict` key at all and this whole class is never
 * constructed; [AnalysisPayload.verdict] stays null and the screen draws nothing extra.
 */
@Serializable
data class Verdict(
    val code: String? = null,
    @SerialName("label_uk") val labelUk: String? = null,
    @SerialName("label_en") val labelEn: String? = null,
    val tone: Tone? = null,
    val locked: Boolean = false,
)
