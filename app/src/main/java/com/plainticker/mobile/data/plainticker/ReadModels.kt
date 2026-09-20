package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.Serializable

// ---- GET /api/v1/{ticker}/read ---------------------------------------------------------------

/**
 * The peek for everyone, the full content for a caller presenting an entitled device code
 * (server/vote/README.md, task S3). Modelled down to what task A6 draws: [narrative] feeds "The
 * read" and [nextSteps] feeds "What to check next". `sectorTable` and `methodHistory` are on the
 * wire too but no screen in this build reads them, so they are left for `ignoreUnknownKeys` to
 * drop rather than modelled here.
 */
@Serializable
data class TickerReadResponse(
    val ticker: String,
    /** Whether THIS response carries the full half of every block, resolved from the device code. */
    val pro: Boolean = false,
    val narrative: NarrativeRead? = null,
    val nextSteps: NextStepsRead? = null,
)

/**
 * The read, in both locales. This app is English only, so [excerptEn] and [fullEn] are the two
 * fields it ever draws; [excerptUk] and [fullEn] exist so the shape matches the wire exactly.
 * The peek field is always populated when the underlying analysis exists, whether or not [pro] is
 * true; the full field is null unless it is.
 */
@Serializable
data class NarrativeRead(
    val excerptUk: String? = null,
    val excerptEn: String? = null,
    val fullUk: String? = null,
    val fullEn: String? = null,
)

/**
 * One entry of the full "What to check next" text, once entitled (`lib/api/read-payload.ts`'s
 * `ReadNextStep`: `{title, body}`, not a bare string). [title] repeats the titles array's own
 * text for this index; the screen draws [NextStepsRead.titlesEn] for the heading and only reads
 * [body] from here.
 */
@Serializable
data class NextStepDetail(val title: String, val body: String)

/** The three step titles for everyone, the full step text once entitled. */
@Serializable
data class NextStepsRead(
    val titlesUk: List<String>? = null,
    val titlesEn: List<String>? = null,
    val stepsUk: List<NextStepDetail>? = null,
    val stepsEn: List<NextStepDetail>? = null,
)
