package com.myapp.repo

import com.myapp.data.Fixtures
import com.myapp.data.MockApi
import com.myapp.data.respondJson
import com.myapp.data.xstocks.XStocksApi
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The two per-symbol reads Detail added to the catalog repository: the multiplier record and the reserves. */
class CachedCatalogRepositoryTest {

    private var now = 0L

    private fun repository(mock: MockApi) = CachedCatalogRepository(XStocksApi(mock.client), clock = { now })

    /** Serves the recorded multiplier and proof-of-reserves answers, and counts the calls. */
    private fun api(porBody: String = Fixtures.read("xstocks/por-tslax.json")) = MockApi { request: HttpRequestData ->
        val path = request.url.encodedPath
        when {
            path.endsWith("/multiplier") -> respondJson(Fixtures.read("xstocks/multiplier-nflxx.json"))
            path.contains("/proof-of-reserves/") -> respondJson(porBody)
            else -> respondJson("""{"error":"unexpected"}""", HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `the multiplier record carries the whole answer and the plain multiplier reads through it`() = runTest {
        val mock = api()
        val repo = repository(mock)

        val record = repo.multiplierRecord("NFLXx")
        assertEquals(10.0, record.currentMultiplier, 0.0)
        assertEquals(10.0, repo.multiplier("NFLXx"), 0.0)
        assertEquals("the second read came from the cache", 1, mock.requests.size)
    }

    @Test
    fun `proof of reserves is cached per symbol for its own window`() = runTest {
        val mock = api()
        val repo = repository(mock)

        assertNotNull(repo.proofOfReserves("TSLAx"))
        assertNotNull(repo.proofOfReserves("TSLAx"))
        assertEquals(1, mock.requests.size)

        now += CachedCatalogRepository.RESERVES_TTL_MS
        assertNotNull(repo.proofOfReserves("TSLAx"))
        assertEquals(2, mock.requests.size)
    }

    @Test
    fun `a JSON null answer is cached as the answer it is, not re-asked on every open`() = runTest {
        val mock = api(porBody = "null")
        val repo = repository(mock)

        assertNull(repo.proofOfReserves("TSLAx"))
        assertNull(repo.proofOfReserves("TSLAx"))
        assertEquals("no reserves published is an answer", 1, mock.requests.size)
    }
}
