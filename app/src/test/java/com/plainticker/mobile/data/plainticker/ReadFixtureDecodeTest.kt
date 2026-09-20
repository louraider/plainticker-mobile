package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.net.HttpClientFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    /**
     * `plainticker/read-aapl-entitled.json`: the shape this same route answers with once a
     * caller presents an entitled device code (`pro: true`), so `fullUk`/`fullEn`,
     * `sectorTable.peers`, `methodHistory.history` and `nextSteps.stepsUk`/`stepsEn` are all
     * populated rather than null. Unlike [fixture] this is not a `curl` capture — reaching
     * production with an entitled code is not available from here — it is built field for field
     * from `lib/api/read-payload.ts`'s `ReadPayloadV1`/`buildReadPayload` (the server's own
     * payload shaper) and `lib/prompts/next-steps.v1.ts`'s `NextStep` (`{title, body}`), the
     * source of truth this app's model has to match. Every value that `buildReadPayload` leaves
     * unchanged between `pro: false` and `pro: true` (`ticker`, `excerptUk`/`excerptEn`,
     * `sectorTable.own`, `methodHistory.latest`, `nextSteps.titlesUk`/`titlesEn`) is carried over
     * byte for byte from [fixture], the real production capture, so only the newly-populated
     * full-half fields are hand-built.
     */
    private val entitledFixture: String
        get() = Fixtures.read("plainticker/read-aapl-entitled.json")

    @Test
    fun `the live AAPL read response decodes with the app's own json config`() {
        val payload = HttpClientFactory.json.decodeFromString(TickerReadResponse.serializer(), fixture)

        assertEquals("AAPL", payload.ticker)
        assertNotNull("the peek must survive decoding for the free-stays-free path", payload.narrative?.excerptEn)
        assertNotNull(payload.nextSteps?.titlesEn)
    }

    /**
     * The regression this app shipped with no test for: every other captured or hand-written
     * body in this repo carries `pro: false`, so `stepsEn` was always null and never exercised
     * [NextStepDetail] against the wire. The server sends `stepsUk`/`stepsEn` as objects
     * (`{title, body}`, `lib/api/read-payload.ts`'s `ReadNextStep`), not bare strings; a model
     * that declared them as `List<String>` decoded the peek fine and threw the moment `pro` was
     * true, which is exactly the failure a phone with a paid device code hit on a release build
     * while every log showed a 200.
     */
    @Test
    fun `the entitled AAPL read response decodes too, full text and all`() {
        val payload = HttpClientFactory.json.decodeFromString(TickerReadResponse.serializer(), entitledFixture)

        assertEquals("AAPL", payload.ticker)
        assertTrue(payload.pro)
        assertNotNull("the full narrative must survive decoding once entitled", payload.narrative?.fullEn)
        assertNotNull(payload.nextSteps?.titlesEn)
        val stepsEn = payload.nextSteps?.stepsEn
        assertNotNull("the full step detail must survive decoding once entitled", stepsEn)
        assertEquals(3, stepsEn!!.size)
        assertTrue("each entry is a title/body object, not a bare string", stepsEn.all { it.body.isNotBlank() })
    }
}
