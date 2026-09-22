package com.plainticker.mobile.repo

import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.prefs.InMemoryDevicePassStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `/api/v1/summary` is entitlement-aware: the tone, headline and setup score come back only for a
 * code that resolves to Pro, and as nulls otherwise. The repository sends this device's code on
 * every call and keeps nothing, so an anonymous answer cannot outlive the device becoming Pro.
 */
class PlainTickerSummaryRepositoryTest {

    private fun body(tone: String?) =
        """{"schema":"v1.1","generated_at":"2026-09-23T12:00:00.000Z","rows":[{"ticker":"AAPL",""" +
            """"tone":${tone?.let { "\"$it\"" } ?: "null"},"setup_score":${if (tone == null) "null" else "72"}}]}"""

    @Test
    fun `every summary call carries the device's code, read fresh each time`() = runTest {
        val mock = MockApi { respondJson(body(null)) }
        val store = InMemoryDevicePassStore("ABCDE12345")
        val repo = PlainTickerSummaryRepository(PlainTickerApi(mock.client)) { store.code() }
        repo.summary()
        repo.summary()
        assertEquals(2, mock.requests.size)
        mock.requests.forEach { assertEquals("ABCDE12345", it.headers[EntitlementApi.HEADER_CODE]) }
    }

    @Test
    fun `an anonymous answer is not kept once the server answers as Pro`() = runTest {
        // The server's first answer is the anonymous one (the pass has not been confirmed yet);
        // its second, after the pass lands, carries the Pro fields. Nothing in between is cached.
        var pro = false
        val mock = MockApi { respondJson(body(if (pro) "positive" else null)) }
        val repo = PlainTickerSummaryRepository(PlainTickerApi(mock.client)) { "ABCDE12345" }

        val before = repo.summary().rows.single()
        assertNull(before.tone)
        assertNull(before.setupScore)

        pro = true
        val after = repo.summary().rows.single()
        assertEquals(Tone.POSITIVE, after.tone)
        assertEquals(72, after.setupScore)
        assertEquals("both answers came from the network", 2, mock.requests.size)
    }

    @Test
    fun `the default repository asks unauthenticated`() = runTest {
        val mock = MockApi { respondJson(body(null)) }
        PlainTickerSummaryRepository(PlainTickerApi(mock.client)).summary()
        assertNull(mock.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }
}
