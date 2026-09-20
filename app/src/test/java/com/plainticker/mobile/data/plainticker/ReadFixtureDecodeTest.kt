package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.net.HttpClientFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * The cheapest decisive test for the v0.8.0 "no read section at all" defect: decode a byte-exact
 * capture of the live `GET /api/v1/AAPL/read` response with the app's own [HttpClientFactory.json]
 * and [TickerReadResponse.serializer], with no ktor, no mock engine and no ViewModel in the way.
 *
 * `plainticker/read-aapl.json` is `curl`'d straight from production
 * (`curl -sS --compressed https://www.plainticker.com/api/v1/AAPL/read`, captured 2026-09-20) and
 * checked in byte-for-byte, Ukrainian text included, so a change here should only ever come from
 * re-capturing the live response.
 */
class ReadFixtureDecodeTest {

    private val fixture: String
        get() = Fixtures.read("plainticker/read-aapl.json")

    @Test
    fun `the live AAPL read response decodes with the app's own json config`() {
        val payload = HttpClientFactory.json.decodeFromString(TickerReadResponse.serializer(), fixture)

        assertEquals("AAPL", payload.ticker)
        assertNotNull("the peek must survive decoding for the free-stays-free path", payload.narrative?.excerptEn)
        assertNotNull(payload.nextSteps?.titlesEn)
    }
}
