package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.MockApi
import com.myapp.data.expectThrows
import com.myapp.data.jupiter.JupiterPriceApi
import com.myapp.data.net.RateLimitedException
import com.myapp.data.respondJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedPriceRepositoryTest {

    private class FakeClock(var now: Long = 1_000_000L) : Clock {
        override fun nowMillis(): Long = now
    }

    private fun mints(count: Int) = (1..count).map { "Mint%03d".format(it) }

    private fun HttpRequestData.ids(): List<String> = url.parameters["ids"]!!.split(",")

    /** Prices every mint at 2.0, except mints starting with "Unpriced", which come back null like Jupiter does. */
    private fun priceAll(ids: List<String>) = ids.joinToString(",", "{", "}") {
        if (it.startsWith("Unpriced")) "\"$it\": null" else "\"$it\": {\"usdPrice\": 2.0}"
    }

    /** The body the keyless gateway actually returns, recorded from the laptop on 2026-09-12. */
    private val gateway429 = """{"code":429,"message":"[API Gateway] Too many requests"}"""

    private fun mock() = MockApi { request -> respondJson(priceAll(request.ids())) }

    private fun idsOf(mock: MockApi) = mock.requests.map { it.url.parameters["ids"]!! }

    /** The pacing is the api's business and is tested there; here it costs nothing. */
    private fun repo(
        mock: MockApi,
        clock: Clock = FakeClock(),
        ttlMillis: Long = 30_000L,
    ): PriceRepository = CachedPriceRepository(JupiterPriceApi(mock.client, sleep = {}), clock, ttlMillis)

    @Test
    fun `a second ask inside the TTL makes no request, one after it does`() = runTest {
        val mock = mock()
        val clock = FakeClock()
        val repo = repo(mock, clock)

        assertEquals(setOf("MintA", "MintB"), repo.prices(listOf("MintA", "MintB")).keys)
        assertEquals(setOf("MintA", "MintB"), repo.prices(listOf("MintA", "MintB")).keys)
        assertEquals(listOf("MintA,MintB"), idsOf(mock))

        clock.now += 29_999L
        repo.prices(listOf("MintA"))
        assertEquals(1, mock.requests.size)

        clock.now += 1L
        repo.prices(listOf("MintA"))
        assertEquals(listOf("MintA,MintB", "MintA"), idsOf(mock))
    }

    @Test
    fun `only unseen or expired mints are requested, the answer still covers every mint asked`() = runTest {
        val mock = mock()
        val repo = repo(mock)

        repo.prices(listOf("MintA"))
        val both = repo.prices(listOf("MintA", "MintB"))

        assertEquals(setOf("MintA", "MintB"), both.keys)
        assertEquals(listOf("MintA", "MintB"), idsOf(mock))
    }

    @Test
    fun `a mint Jupiter answered about and cannot price is a remembered miss for one TTL`() = runTest {
        val mock = mock()
        val clock = FakeClock()
        val repo = repo(mock, clock)

        val first = repo.pricesFirst(listOf("UnpricedC"))
        assertTrue(first.priced.isEmpty())
        // Jupiter answered, so this is a price that does not exist, not a fetch that failed.
        assertTrue(first.unfetched.isEmpty())
        assertFalse(first.isPartial)

        assertTrue(repo.prices(listOf("UnpricedC")).isEmpty())
        assertEquals("the miss is cached, so no second request", 1, mock.requests.size)

        clock.now += 30_000L
        assertTrue(repo.prices(listOf("UnpricedC")).isEmpty())
        assertEquals(2, mock.requests.size)
    }

    @Test
    fun `a mint whose request was refused is never cached as a miss`() = runTest {
        var refusing = true
        val mock = MockApi { request ->
            val ids = request.ids()
            if (refusing && ids.first() == "Mint051") respondJson(gateway429, HttpStatusCode.TooManyRequests)
            else respondJson(priceAll(ids))
        }
        val repo = repo(mock)
        val wanted = mints(60)
        val refused = wanted.drop(50)

        val partial = repo.pricesFirst(wanted)
        assertEquals(50, partial.priced.size)
        assertEquals(refused.toSet(), partial.unfetched)
        assertTrue(partial.wasRateLimited)
        assertEquals("chunk one, chunk two, chunk two retried", 3, mock.requests.size)

        // Same instant, well inside the TTL: the fifty that were answered stay cached and the
        // ten that carried no answer are asked for again rather than being blank for 30 s.
        refusing = false
        val second = repo.pricesFirst(wanted)
        assertEquals(60, second.priced.size)
        assertTrue(second.unfetched.isEmpty())
        assertEquals(listOf(refused.joinToString(",")), idsOf(mock).drop(3))
    }

    @Test
    fun `one refused chunk no longer costs every price on the screen`() = runTest {
        val mock = MockApi { request ->
            val ids = request.ids()
            if (ids.first() == "Mint051") respondJson(gateway429, HttpStatusCode.TooManyRequests)
            else respondJson(priceAll(ids))
        }
        val repo = repo(mock)

        // The 2026-09-12 device bug: this used to throw and leave the list with no prices.
        assertEquals(50, repo.prices(mints(60)).size)
    }

    @Test
    fun `pricesFirst prices the visible window first and the rest on the next call`() = runTest {
        val mock = mock()
        val repo = repo(mock)
        val wanted = mints(60)

        val visible = repo.pricesFirst(wanted, limit = 12)
        assertEquals(mints(12), visible.priced.keys.toList())
        assertEquals(listOf(mints(12).joinToString(",")), idsOf(mock))

        val everything = repo.pricesFirst(wanted)
        assertEquals(60, everything.priced.size)
        assertEquals("the first window is cached by now", 2, mock.requests.size)
        assertEquals(wanted.drop(12).joinToString(","), idsOf(mock)[1])
    }

    @Test
    fun `pricesFirst reports a total failure, prices throws it`() = runTest {
        val mock = MockApi { respondJson(gateway429, HttpStatusCode.TooManyRequests) }
        val repo = repo(mock)

        val fetch = repo.pricesFirst(listOf("MintA"))
        assertTrue(fetch.priced.isEmpty())
        assertEquals(setOf("MintA"), fetch.unfetched)
        assertTrue(fetch.wasRateLimited)
        assertEquals("one call and one retry", 2, mock.requests.size)

        // prices() is the entry point for callers that only want numbers, so it says why
        // there are none at all rather than handing back an unexplained empty map.
        expectThrows<RateLimitedException> { repo.prices(listOf("MintA")) }
        assertEquals(4, mock.requests.size)
    }

    @Test
    fun `a failed refresh throws and the cache survives it`() = runTest {
        var refusing = false
        val mock = MockApi { request ->
            if (refusing) respondJson(gateway429, HttpStatusCode.TooManyRequests)
            else respondJson(priceAll(request.ids()))
        }
        val clock = FakeClock()
        val repo = repo(mock, clock)

        repo.prices(listOf("MintA"))

        clock.now += 30_000L
        refusing = true
        expectThrows<RateLimitedException> { repo.prices(listOf("MintA")) }

        refusing = false
        assertEquals(setOf("MintA"), repo.prices(listOf("MintA")).keys)
        assertEquals("first fetch, refusal, retry, refresh", 4, mock.requests.size)
    }

    @Test
    fun `a price that was fresh when the screen asked survives a slow paced fetch`() = runTest {
        val clock = FakeClock()
        // Pacing 149 mints costs several seconds; here one fetch outlasts the whole TTL.
        val mock = MockApi { request ->
            clock.now += 40_000L
            respondJson(priceAll(request.ids()))
        }
        val repo = repo(mock, clock)

        assertEquals(setOf("MintA"), repo.prices(listOf("MintA")).keys)
        assertEquals(setOf("MintA", "MintB"), repo.prices(listOf("MintA", "MintB")).keys)
        assertEquals(listOf("MintA", "MintB"), idsOf(mock))
    }

    @Test
    fun `blank and duplicate mints collapse, an empty ask makes no request`() = runTest {
        val mock = mock()
        val repo = repo(mock)

        assertTrue(repo.prices(emptyList()).isEmpty())
        assertTrue(repo.prices(listOf(" ", "")).isEmpty())
        assertEquals(0, mock.requests.size)

        assertEquals(setOf("MintA"), repo.prices(listOf("MintA", " MintA ", "")).keys)
        assertEquals(listOf("MintA"), idsOf(mock))
    }
}
