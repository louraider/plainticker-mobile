package com.myapp.repo

import com.myapp.data.Fixtures
import com.myapp.data.MockApi
import com.myapp.data.respondJson
import com.myapp.data.xstocks.CatalogCache
import com.myapp.data.xstocks.FileCatalogCache
import com.myapp.data.xstocks.StoredCatalog
import com.myapp.data.xstocks.XStockAsset
import com.myapp.data.xstocks.XStocksApi
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/** The two per-symbol reads Detail added to the catalog repository: the multiplier record and the reserves. */
class CachedCatalogRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = 0L

    private fun repository(mock: MockApi, disk: CatalogCache? = null) =
        CachedCatalogRepository(XStocksApi(mock.client), clock = { now }, disk = disk)

    /** The catalog file this process would find on disk, on the test dispatcher. */
    private fun cache() = FileCatalogCache(File(temp.root, CatalogCache.FILE_NAME), io = EmptyCoroutineContext)

    /** The two recorded catalog pages, and nothing else. */
    private fun catalogApi() = MockApi { request: HttpRequestData ->
        val page = request.url.parameters["page"]?.toIntOrNull() ?: 0
        respondJson(Fixtures.read("xstocks/assets-page-$page.json"))
    }

    private fun catalogRequests(mock: MockApi) = mock.requests.count { it.url.encodedPath.endsWith("/assets") }

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
    // ---- The catalog on disk ---------------------------------------------------------------

    @Test
    fun `the catalog is written once and the next start reads it instead of the network`() = runTest {
        val disk = cache()

        // First launch: the pages are paid for, and what came back is written down.
        val first = catalogApi()
        val fetched = repository(first, disk).catalog()
        assertEquals(listOf("XRXx", "TSLAx", "ASx"), fetched.map { it.symbol })
        assertEquals("both pages, once", 2, catalogRequests(first))

        // Same process, second ask: the memory cache answers and the file is not rewritten.
        val writtenAt = disk.read()!!.capturedAtMillis
        assertEquals(2, catalogRequests(first))

        // A second launch is a new repository with an empty memory cache over the same file.
        val second = catalogApi()
        val fromDisk = repository(second, disk).catalog()
        assertEquals("the second launch pays nothing for the catalog", 0, catalogRequests(second))
        assertEquals(fetched.map { it.symbol }, fromDisk.map { it.symbol })
        assertEquals(fetched.map { it.solanaMint }, fromDisk.map { it.solanaMint })
        assertEquals("and it did not rewrite what it just read", writtenAt, disk.read()!!.capturedAtMillis)
    }

    @Test
    fun `a catalog past its window is refetched and the file is brought forward`() = runTest {
        val disk = cache()
        repository(catalogApi(), disk).catalog()
        val firstCapture = disk.read()!!.capturedAtMillis

        now += CatalogCache.TTL_MS
        val later = catalogApi()
        repository(later, disk).catalog()

        assertEquals("past the window, the network is asked", 2, catalogRequests(later))
        assertTrue("and the file carries the newer capture", disk.read()!!.capturedAtMillis > firstCapture)
    }

    @Test
    fun `the memory cache stays in front of the file`() = runTest {
        val disk = cache()
        val mock = catalogApi()
        val repo = repository(mock, disk)

        repo.catalog()
        repo.catalog()
        repo.catalog()
        assertEquals("one fetch, then memory", 2, catalogRequests(mock))
    }

    @Test
    fun `a build with no catalog file still works, it just pays every launch`() = runTest {
        val mock = catalogApi()
        assertEquals(3, repository(mock, disk = null).catalog().size)
        assertEquals(2, catalogRequests(mock))
    }


    // ---- The catalog as it arrives -----------------------------------------------------------

    @Test
    fun `page zero publishes before page one, and the set only ever grows`() = runTest {
        val mock = catalogApi()
        val updates = repository(mock, cache()).catalogUpdates().toList()

        assertEquals("one publish per page, plus the whole catalog at the end", 3, updates.size)
        assertEquals(listOf("XRXx", "TSLAx"), updates[0].assets.map { it.symbol })
        assertFalse("the first pages of a catalog are not a catalog", updates[0].whole)
        assertEquals(listOf("XRXx", "TSLAx", "ASx"), updates.last().assets.map { it.symbol })
        assertTrue(updates.last().whole)
        updates.zipWithNext { earlier, later ->
            assertTrue("a page shrank the catalog", later.assets.size >= earlier.assets.size)
        }
    }

    @Test
    fun `a stale catalog paints first and the network refreshes behind it`() = runTest {
        val disk = cache()
        // Written long ago, and carrying a token xStocks has since dropped.
        disk.write(listOf(xStock("OLDx", "OLD", "XsMintOld")), capturedAtMillis = 0L)
        now = CatalogCache.TTL_MS + 1

        val mock = catalogApi()
        val updates = repository(mock, disk).catalogUpdates().toList()

        // Before a single request, the stale catalog is on its way to the screen.
        assertEquals(listOf("OLDx"), updates.first().assets.map { it.symbol })
        assertTrue("a stale catalog is still a whole one", updates.first().whole)
        assertTrue("and it refreshed behind that paint", updates.size > 2)
        assertEquals(2, catalogRequests(mock))

        // The live catalog wins once it is whole, so the dropped token goes with it.
        assertEquals(listOf("XRXx", "TSLAx", "ASx"), updates.last().assets.map { it.symbol })
        assertTrue(updates.last().whole)
        assertEquals("and the file was brought forward", now, disk.read()!!.capturedAtMillis)
    }

    @Test
    fun `a fresh catalog on disk is the whole answer and asks the network nothing`() = runTest {
        val disk = cache()
        repository(catalogApi(), disk).catalog()

        val next = catalogApi()
        val updates = repository(next, disk).catalogUpdates().toList()

        assertEquals("one publish, straight off the disk", 1, updates.size)
        assertTrue(updates.single().whole)
        assertEquals(listOf("XRXx", "TSLAx", "ASx"), updates.single().assets.map { it.symbol })
        assertEquals(0, catalogRequests(next))
    }

    @Test
    fun `the memory cache answers the stream too, without touching the disk`() = runTest {
        val disk = cache()
        val mock = catalogApi()
        val repo = repository(mock, disk)
        repo.catalog()

        val updates = repo.catalogUpdates().toList()
        assertEquals(1, updates.size)
        assertTrue(updates.single().whole)
        assertEquals(2, catalogRequests(mock))
    }


    /** A catalog file that also counts what was asked of it. */
    private class CountingCache(private val inner: CatalogCache) : CatalogCache {
        var reads = 0
            private set
        var writes = 0
            private set

        override suspend fun read(): StoredCatalog? {
            reads++
            return inner.read()
        }

        override suspend fun write(assets: List<XStockAsset>, capturedAtMillis: Long) {
            writes++
            inner.write(assets, capturedAtMillis)
        }
    }

    @Test
    fun `one launch writes the file once, however many times the catalog is asked for`() = runTest {
        val disk = CountingCache(cache())
        val mock = catalogApi()
        val repo = repository(mock, disk)

        // The List streams it, then two other screens ask for it the ordinary way.
        repo.catalogUpdates().toList()
        repo.catalog()
        repo.catalog()

        assertEquals("written once, by the run that fetched it", 1, disk.writes)
        assertEquals("and fetched once", 2, catalogRequests(mock))
    }

    @Test
    fun `a catalog stamped in the future is expired, not fresh for as long as the clock is wrong`() = runTest {
        val disk = cache()
        // A device whose clock was ahead when the file was written, then corrected.
        disk.write(listOf(xStock("OLDx", "OLD", "XsMintOld")), capturedAtMillis = CatalogCache.TTL_MS * 10)
        now = 0L

        val mock = catalogApi()
        assertEquals(listOf("XRXx", "TSLAx", "ASx"), repository(mock, disk).catalog().map { it.symbol })
        assertEquals("a moved clock costs one refetch, not the same file until it catches up", 2, catalogRequests(mock))
    }

    /** xStocks answering 200 with nothing in it: a server with nothing to say, not a catalog. */
    private fun emptyCatalogApi() = MockApi { _: HttpRequestData ->
        respondJson("""{"nodes":[],"page":{"currentPage":0,"hasNextPage":false}}""")
    }

    @Test
    fun `an empty answer never replaces a catalog and is never remembered as one`() = runTest {
        val disk = cache()
        disk.write(listOf(xStock("OLDx", "OLD", "XsMintOld")), capturedAtMillis = 0L)
        now = CatalogCache.TTL_MS + 1

        val mock = emptyCatalogApi()
        val repo = repository(mock, disk)
        val updates = repo.catalogUpdates().toList()

        // The stale file paints and nothing follows it. Publishing the empty answer as a whole
        // catalog is what would blank a list that had the bundled snapshot on it, because a
        // whole catalog is what the screen decides "this ticker has no xStock" from.
        assertEquals("the empty answer is not an emission", 1, updates.size)
        assertEquals(listOf("OLDx"), updates.single().assets.map { it.symbol })
        assertEquals("nothing was written over the file", listOf("OLDx"), disk.read()!!.assets.map { it.symbol })

        // And it was not remembered, so the next screen asks again rather than being told for
        // the next six hours that no ticker has a token.
        val asked = catalogRequests(mock)
        assertEquals("a stale catalog beats no catalog", listOf("OLDx"), repo.catalog().map { it.symbol })
        assertTrue("the empty answer was not cached", catalogRequests(mock) > asked)
    }

    @Test
    fun `the next launch reads the file once and writes nothing`() = runTest {
        repository(catalogApi(), cache()).catalogUpdates().toList()

        val disk = CountingCache(cache())
        val next = catalogApi()
        val repo = repository(next, disk)
        repo.catalogUpdates().toList()
        repo.catalog()

        assertEquals("nothing to write, it is already right", 0, disk.writes)
        assertEquals(0, catalogRequests(next))
        assertEquals("and the file is read once, not once per screen", 1, disk.reads)
    }


}
