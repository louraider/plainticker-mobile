package com.myapp.repo

import com.myapp.core.Clock
import com.myapp.data.MockApi
import com.myapp.data.expectThrows
import com.myapp.data.jupiter.JupiterPriceApi
import com.myapp.data.net.RateLimitedException
import com.myapp.data.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedPriceRepositoryTest {

    private class FakeClock(var now: Long = 1_000_000L) : Clock {
        override fun nowMillis(): Long = now
    }

    private var rateLimited = false

    /** Prices every requested mint at 2.0, except mints starting with "Unpriced", which come back null like Jupiter does. */
    private fun mock() = MockApi { request ->
        if (rateLimited) {
            respondJson("""{"error":"Rate limit exceeded"}""", HttpStatusCode.TooManyRequests)
        } else {
            val ids = request.url.parameters["ids"]!!.split(",")
            respondJson(ids.joinToString(",", "{", "}") { if (it.startsWith("Unpriced")) "\"$it\": null" else "\"$it\": {\"usdPrice\": 2.0}" })
        }
    }

    private fun idsOf(mock: MockApi) = mock.requests.map { it.url.parameters["ids"]!! }

    @Test
    fun `a second ask inside the TTL makes no request, one after it does`() = runTest {
        val mock = mock()
        val clock = FakeClock()
        val repo = CachedPriceRepository(JupiterPriceApi(mock.client), clock, ttlMillis = 30_000L)

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
        val repo = CachedPriceRepository(JupiterPriceApi(mock.client), FakeClock())

        repo.prices(listOf("MintA"))
        val both = repo.prices(listOf("MintA", "MintB"))

        assertEquals(setOf("MintA", "MintB"), both.keys)
        assertEquals(listOf("MintA", "MintB"), idsOf(mock))
    }

    @Test
    fun `a mint Jupiter cannot price is a remembered miss for one TTL`() = runTest {
        val mock = mock()
        val clock = FakeClock()
        val repo = CachedPriceRepository(JupiterPriceApi(mock.client), clock, ttlMillis = 30_000L)

        assertTrue(repo.prices(listOf("UnpricedC")).isEmpty())
        assertTrue(repo.prices(listOf("UnpricedC")).isEmpty())
        assertEquals(1, mock.requests.size)

        clock.now += 30_000L
        assertTrue(repo.prices(listOf("UnpricedC")).isEmpty())
        assertEquals(2, mock.requests.size)
    }

    @Test
    fun `a failed refresh throws and the cache survives it`() = runTest {
        val mock = mock()
        val clock = FakeClock()
        val repo = CachedPriceRepository(JupiterPriceApi(mock.client), clock, ttlMillis = 30_000L)

        repo.prices(listOf("MintA"))

        clock.now += 30_000L
        rateLimited = true
        expectThrows<RateLimitedException> { repo.prices(listOf("MintA")) }

        rateLimited = false
        assertEquals(setOf("MintA"), repo.prices(listOf("MintA")).keys)
        assertEquals(3, mock.requests.size)
    }

    @Test
    fun `blank and duplicate mints collapse, an empty ask makes no request`() = runTest {
        val mock = mock()
        val repo = CachedPriceRepository(JupiterPriceApi(mock.client), FakeClock())

        assertTrue(repo.prices(emptyList()).isEmpty())
        assertTrue(repo.prices(listOf(" ", "")).isEmpty())
        assertEquals(0, mock.requests.size)

        assertEquals(setOf("MintA"), repo.prices(listOf("MintA", " MintA ", "")).keys)
        assertEquals(listOf("MintA"), idsOf(mock))
    }
}
