package com.myapp.data.xstocks

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The catalog file: what survives process death, and what it refuses to answer with. The window
 * it is judged against lives in [CatalogCache.TTL_MS]; nothing here asserts a duration, only
 * that the capture time is written down so somebody can judge it.
 */
class CatalogDiskCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(): File = File(temp.root, CatalogCache.FILE_NAME)

    // The write and the read stay on the test dispatcher, so runTest covers them.
    private fun cache(f: File = file()) = FileCatalogCache(f, io = EmptyCoroutineContext)

    private fun asset(symbol: String, ticker: String, mint: String) = XStockAsset(
        name = "$ticker xStock",
        symbol = symbol,
        underlying = Underlying(symbol = ticker),
        deployments = listOf(
            Deployment(address = mint, network = XStockAsset.NETWORK_SOLANA),
            Deployment(address = "0xEthereum", network = "Ethereum"),
            Deployment(address = "0xMantle", network = "Mantle"),
        ),
    )

    @Test
    fun `a catalog written now reads back with its capture time`() = runTest {
        val store = cache()
        assertNull("nothing written yet", store.read())

        store.write(listOf(asset("TSLAx", "TSLA", "XsMint1")), capturedAtMillis = 1_757_700_000_000L)
        val stored = store.read()
        assertNotNull("written, so it reads back", stored)
        checkNotNull(stored)

        assertEquals(1_757_700_000_000L, stored.capturedAtMillis)
        assertEquals(listOf("TSLAx"), stored.assets.map { it.symbol })
        assertEquals("TSLA", stored.assets[0].underlyingTicker)
        assertEquals("XsMint1", stored.assets[0].solanaMint)
    }

    @Test
    fun `a second write replaces the first, so the file is one catalog and not a log`() = runTest {
        val store = cache()
        store.write(listOf(asset("TSLAx", "TSLA", "XsMint1")), capturedAtMillis = 1L)
        store.write(listOf(asset("AAPLx", "AAPL", "XsMint2")), capturedAtMillis = 2L)

        val stored = store.read()!!
        assertEquals(2L, stored.capturedAtMillis)
        assertEquals(listOf("AAPLx"), stored.assets.map { it.symbol })
    }

    @Test
    fun `an empty catalog is not written over a usable one`() = runTest {
        val store = cache()
        store.write(listOf(asset("TSLAx", "TSLA", "XsMint1")), capturedAtMillis = 1L)
        store.write(emptyList(), capturedAtMillis = 2L)

        assertEquals(listOf("TSLAx"), store.read()!!.assets.map { it.symbol })
    }

    @Test
    fun `a truncated or missing file is a cache miss, never a crash and never rubbish`() = runTest {
        val broken = file()
        broken.writeText("""{"captured_at_millis": 1, "assets": [{"symbol": "TS""")
        assertNull("half a file is not an answer", cache(broken).read())

        broken.writeText("""{"captured_at_millis": 1, "assets": []}""")
        assertNull("an empty catalog is not an answer either", cache(broken).read())

        assertNull(cache(File(temp.root, "nested/never-written.json")).read())
    }

    @Test
    fun `only the Solana deployment is kept, which is all the app ever reads`() {
        val full = asset("TSLAx", "TSLA", "XsMint1")
        assertEquals(3, full.deployments.size)

        val trimmed = full.solanaOnly()
        assertEquals(listOf(XStockAsset.NETWORK_SOLANA), trimmed.deployments.map { it.network })
        // The two ways in answer the same before and after, which is why the trim is safe.
        assertEquals(full.solanaMint, trimmed.solanaMint)
        assertEquals(full.solanaDeployment, trimmed.solanaDeployment)
        assertEquals(full.underlyingTicker, trimmed.underlyingTicker)
        assertEquals(full.symbol, trimmed.symbol)
        assertEquals(full.name, trimmed.name)
    }

    @Test
    fun `an asset with no Solana deployment trims to none rather than to another network`() {
        val elsewhere = XStockAsset(
            symbol = "NOPEx",
            deployments = listOf(Deployment(address = "0xEthereum", network = "Ethereum")),
        )
        assertTrue(elsewhere.solanaOnly().deployments.isEmpty())
        assertNull(elsewhere.solanaOnly().solanaMint)
    }
}
