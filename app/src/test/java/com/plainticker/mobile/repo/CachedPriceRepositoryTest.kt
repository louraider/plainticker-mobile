package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.jupiter.JupiterPriceApi
import com.plainticker.mobile.data.net.RateLimitedException
import com.plainticker.mobile.data.respondJson
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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

    // ---- The shared source every screen observes (device QA of 1.3.18) ------------------------

    /** Final QA of 1.3.19: a pull inside the 30 s window drew the same figure again. */
    @Test
    fun `a pull forgets the cached answers, so the next ask reaches Jupiter inside the window`() = runTest {
        var usd = 2.0
        val mock = MockApi { request ->
            respondJson(request.ids().joinToString(",", "{", "}") { "\"$it\": {\"usdPrice\": $usd}" })
        }
        val repo = repo(mock)
        repo.prices(listOf("MintA", "MintB"))
        usd = 2.5
        repo.prices(listOf("MintA"))
        assertEquals("inside the window the cache answers", 1, mock.requests.size)

        repo.forget(listOf("MintA"))
        assertEquals(2.5, repo.prices(listOf("MintA")).getValue("MintA").usdPrice, 0.0)
        assertEquals(2, mock.requests.size)
        repo.prices(listOf("MintB"))
        assertEquals("a mint not forgotten is still cached", 2, mock.requests.size)

        repo.forget()
        repo.prices(listOf("MintB"))
        assertEquals("forget() drops every mint", 3, mock.requests.size)
    }

    @Test
    fun `every fetch lands in the shared latest quotes, whichever screen asked`() = runTest {
        var usd = 2.0
        val mock = MockApi { request ->
            respondJson(request.ids().joinToString(",", "{", "}") { "\"$it\": {\"usdPrice\": $usd}" })
        }
        val clock = FakeClock()
        val repo = repo(mock, clock)
        assertTrue("nothing asked, nothing held", repo.latest.value.isEmpty())

        // Today asks for one mint, Stocks for another: both land in the one source.
        repo.prices(listOf("MintA"))
        repo.pricesFirst(listOf("MintB"))
        assertEquals(mapOf("MintA" to 2.0, "MintB" to 2.0), repo.latest.value.mapValues { it.value.usdPrice })

        // A later refresh on any screen moves the quote every screen reads.
        usd = 2.5
        clock.now += 30_000L
        repo.prices(listOf("MintA"))
        assertEquals(2.5, repo.latest.value.getValue("MintA").usdPrice, 0.0)
        assertEquals("a mint nobody re-asked keeps its quote", 2.0, repo.latest.value.getValue("MintB").usdPrice, 0.0)
    }

    @Test
    fun `a mint Jupiter now answers without a price leaves the shared quotes, an unreached one stays`() = runTest {
        var unpriced = false
        var refuse = false
        val mock = MockApi { request ->
            when {
                refuse -> respondJson(gateway429, HttpStatusCode.TooManyRequests)
                unpriced -> respondJson(request.ids().joinToString(",", "{", "}") { "\"$it\": null" })
                else -> respondJson(priceAll(request.ids()))
            }
        }
        val clock = FakeClock()
        val repo = repo(mock, clock)
        repo.prices(listOf("MintA", "MintB"))
        assertEquals(setOf("MintA", "MintB"), repo.latest.value.keys)

        // The ask never lands: the last quote is still the best answer anyone has.
        refuse = true
        clock.now += 30_000L
        repo.pricesFirst(listOf("MintA"))
        assertEquals(setOf("MintA", "MintB"), repo.latest.value.keys)

        // Jupiter answers and has no price any more: no screen may keep drawing the old one.
        refuse = false
        unpriced = true
        clock.now += 30_000L
        repo.pricesFirst(listOf("MintA"))
        assertEquals(setOf("MintB"), repo.latest.value.keys)
    }

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
    fun `a detail screen is not made to wait behind the list's paced refresh`() = runTest {
        // The List's run is the slow one: paced chunks and one backoff are seconds of waiting.
        // `reached` puts a chunk of it in flight and holds it there, the way a real run holds
        // the network; the fetch used to happen inside the cache lock, so the Detail screen's
        // one mint sat behind the whole thing and this call never came back.
        val reached = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val mock = MockApi { request ->
            val ids = request.ids()
            if (ids.size > 1) {
                reached.complete(Unit)
                release.await()
            }
            respondJson(priceAll(ids))
        }
        val repo = repo(mock)

        val list = launch { repo.pricesFirst(mints(60)) }
        reached.await()

        assertEquals(setOf("MintZ"), repo.prices(listOf("MintZ")).keys)

        release.complete(Unit)
        list.join()
        assertEquals("both list chunks and the detail mint", 3, mock.requests.size)
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
