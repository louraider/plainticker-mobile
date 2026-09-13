package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why the List and Detail print two composites for one ticker, pinned so nobody repairs it here.
 *
 * On the device on 2026-09-13 the List drew NVDAx as "79" with "Analysis 7 d old", and one tap
 * later Detail drew "composite 77" and "Analysis from 2 d ago". Both screens read their own
 * source correctly. The repair is upstream and the client must not paper over it, because the
 * obvious client-side repairs are all wrong: rescaling one number into the other assumes they
 * measure different things, and they do not, and fetching the per-ticker payload for all 157 rows
 * to agree with Detail would put 157 round trips in front of the first row.
 *
 * What these tests carry is the measurement that settles which of those it is, taken live against
 * production on 2026-09-13 and written up in docs/data-map.md. The two fields are the same
 * quantity: wherever both payloads come out of one extraction run they agree exactly, and
 * wherever they disagree the per-ticker payload was extracted later than the summary row. The
 * summary table is a materialized snapshot that is not rewritten when a ticker is re-extracted.
 *
 * If this file ever goes red after a server change, the server has changed what these fields
 * mean, and the screens need reading again rather than the test relaxing.
 */
class TwoPipelinesTest {

    /**
     * One ticker as both pipelines answered for it, read from `/api/v1/summary` and
     * `/api/v1/{TICKER}` within the same minute on 2026-09-13.
     */
    private data class Reading(
        val ticker: String,
        /** `/summary.composite`, which the List draws. */
        val summaryComposite: Double,
        /** `/summary.computed_at`, which `/summary.age_days` is counted from. */
        val summaryComputedAt: String,
        /** `composite_percentile` from the per-ticker payload, which Detail draws. */
        val detailPercentile: Int,
        /** `as_of` from that payload, which Detail's analysis age is counted from. */
        val detailAsOf: String,
    ) {
        /**
         * Both payloads came out of one extraction run: same day and same hour. A run writes the
         * summary row and the ticker payload within a minute or two of each other, so the hour is
         * a wide enough window to be safe and a narrow enough one to separate NEM, whose payload
         * was rewritten seventeen hours after its summary row.
         */
        val oneRun: Boolean get() = summaryComputedAt.take(13) == detailAsOf.take(13)
    }

    private val readings = listOf(
        // Four tickers re-extracted the morning they were read: both pipelines, one run.
        Reading("USB", 61.094208, "2026-09-13T03:05:50.837Z", 61, "2026-09-13T03:04:43.388Z"),
        Reading("CHTR", 59.916653, "2026-09-13T03:04:42.053Z", 60, "2026-09-13T03:03:57.490Z"),
        Reading("AMT", 57.81229, "2026-09-13T03:06:45.600Z", 58, "2026-09-13T03:05:51.948Z"),
        Reading("VZ", 51.838345, "2026-09-13T03:00:36.663Z", 52, "2026-09-13T03:00:16.460Z"),
        // The two that disagree, including the one the review walked into.
        Reading("NVDA", 79.072655, "2026-09-06T03:01:30.607Z", 77, "2026-09-11T03:13:41.765Z"),
        Reading("NEM", 83.80406, "2026-09-06T03:01:11.572Z", 82, "2026-09-06T20:24:52.227Z"),
        // Re-extracted five days later and unchanged: a later run does not have to move a number.
        Reading("BKNG", 80.2809, "2026-09-06T03:01:45.467Z", 80, "2026-09-11T11:13:25.991Z"),
    )

    @Test
    fun `the two fields are one quantity, so neither screen may convert the other's`() {
        // Both screens format with the same call, so a pair from one run must render identically.
        // This is the assertion that stops the plausible wrong repair: treating "composite" as a
        // raw score to be ranked into a percentile, or the percentile as something to rescale.
        readings.filter { it.oneRun }.forEach {
            assertEquals(
                "${it.ticker}: one extraction run must give one number on both screens",
                it.detailPercentile.toString(),
                Fmt.decimal(it.summaryComposite, decimals = 0),
            )
        }
        assertEquals("four same-run pairs were measured", 4, readings.count { it.oneRun })
    }

    @Test
    fun `every disagreement is the summary row lagging, never the client`() {
        val disagreeing = readings.filterNot {
            Fmt.decimal(it.summaryComposite, decimals = 0) == it.detailPercentile.toString()
        }
        assertEquals(listOf("NVDA", "NEM"), disagreeing.map { it.ticker })

        // In both, the per-ticker payload was extracted after the summary row was written. That
        // is the whole of the upstream bug: the summary table is materialized at extraction time
        // and is not rewritten when one ticker is re-extracted, so its composite and its
        // computed_at, and therefore its age_days, can lag that ticker's own payload for days.
        disagreeing.forEach {
            assertTrue(
                "${it.ticker}: a disagreement with no later re-extract behind it is a client bug",
                it.detailAsOf > it.summaryComputedAt,
            )
        }

        // NVDA is the pair the review read on the phone: 79 against 77, 7 days against 2.
        val nvda = readings.single { it.ticker == "NVDA" }
        assertEquals("79", Fmt.decimal(nvda.summaryComposite, decimals = 0))
        assertEquals(77, nvda.detailPercentile)
        assertEquals("the summary row is five days behind the payload", "2026-09-06", nvda.summaryComputedAt.take(10))
        assertEquals("2026-09-11", nvda.detailAsOf.take(10))
    }

    @Test
    fun `a later run is free to leave a number where it was`() {
        // BKNG was re-extracted five days after its summary row and still reads 80 on both, so
        // "the timestamps differ" is not on its own a prediction that the numbers will.
        val bkng = readings.single { it.ticker == "BKNG" }
        assertEquals("80", Fmt.decimal(bkng.summaryComposite, decimals = 0))
        assertEquals(80, bkng.detailPercentile)
        assertTrue(bkng.detailAsOf > bkng.summaryComputedAt)
    }
}
