package com.plainticker.mobile.data.snapshot

import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.repo.AssetSource
import com.plainticker.mobile.repo.BundledSnapshotRepository
import com.plainticker.mobile.ui.list.isProLocked
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDate

/**
 * The bundled outage snapshot, read from the assets exactly as the app reads them (task T8,
 * rule 5). Written by scripts/capture-list-snapshot.mjs; this test is what stops a re-run from
 * shipping a payload that is empty, undated, oversized or carrying the Ukrainian headline.
 */
class BundledSnapshotTest {

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private val assetsDir = File(module, "src/main/assets")

    private val fromDisk = AssetSource { path ->
        File(assetsDir, path).takeIf { it.isFile }?.inputStream()
    }

    private fun bytes(path: String): ByteArray = File(assetsDir, path).readBytes()

    private val cyrillic = Regex("[\\u0400-\\u04FF]")

    @Test
    fun `the bundled snapshot parses, is dated and covers the list`() = runTest {
        val snapshot = BundledSnapshotRepository(fromDisk).listSnapshot()
        assertNotNull("no snapshot under app/src/main/assets/snapshot", snapshot)
        checkNotNull(snapshot)

        val capturedOn = snapshot.capturedOn
        assertNotNull("the snapshot carries no capture date", capturedOn)
        checkNotNull(capturedOn)
        assertTrue("captured in the future: $capturedOn", !capturedOn.isAfter(LocalDate.now()))
        assertTrue("captured before this project existed: $capturedOn", capturedOn.isAfter(LocalDate.of(2026, 1, 1)))

        assertTrue("only ${snapshot.rows.size} analyzed rows", snapshot.rows.size >= 100)
        assertTrue("only ${snapshot.assets.size} xStocks", snapshot.assets.size >= 100)

        snapshot.rows.forEach { row ->
            assertTrue("a row with no ticker", row.ticker.isNotBlank())
            row.composite?.let { assertTrue("composite off scale: $it", it in 0.0..100.0) }
        }
        snapshot.assets.forEach { asset ->
            assertTrue("an asset with no symbol", asset.symbol.isNotBlank())
            assertTrue("an asset with no ticker", asset.ticker.isNotBlank())
            assertTrue("${asset.symbol} has a mint that is not one", asset.mint.length in 32..44)
        }

        // The join the List runs has something to join: most rows find their token.
        val tokens = snapshot.assets.map { it.ticker.uppercase() }.toSet()
        val matched = snapshot.rows.count { it.ticker.uppercase() in tokens }
        assertTrue("only $matched of ${snapshot.rows.size} rows have an xStock", matched >= snapshot.rows.size / 2)
    }

    /**
     * Security review M2: the asset was captured on 19 Sep, before the Pro-numbers lock, with a
     * composite and a tone on every row, and a free reader saw them on every cold start and
     * offline. The APK is public, so the asset itself must not carry them, and the read strips
     * them again whatever an asset carries.
     */
    @Test
    fun `the snapshot carries Pro numbers for the open example only, in the asset and as read`() = runTest {
        val snapshot = checkNotNull(BundledSnapshotRepository(fromDisk).listSnapshot())
        val withNumbers = snapshot.rows.filter { it.composite != null || it.tone != null }.map { it.ticker }
        assertTrue("the asset carries Pro numbers for $withNumbers", withNumbers.all { it.equals("AAPL", ignoreCase = true) })

        val captured = listOf(
            SnapshotRow(ticker = "AAPL", composite = 49.67, tone = Tone.DANGER),
            SnapshotRow(ticker = "NEM", composite = 82.37, tone = Tone.POSITIVE),
        ).map { it.toSummaryRow() }
        assertEquals(49.67, captured[0].composite!!, 0.0)
        assertEquals(Tone.DANGER, captured[0].tone)
        assertNull("a Pro composite never reaches a free reader from the snapshot", captured[1].composite)
        assertNull(captured[1].tone)
        assertTrue(captured[1].isProLocked())
    }

    @Test
    fun `the assets carry only what the list draws`() {
        val summary = bytes(BundledSnapshotRepository.SUMMARY_ASSET).decodeToString()
        val catalog = bytes(BundledSnapshotRepository.CATALOG_ASSET).decodeToString()

        // The Ukrainian headline is never rendered, so it is never captured either.
        assertFalse("the snapshot captured the headline field", summary.contains("headline"))
        assertFalse("the snapshot captured Cyrillic text", cyrillic.containsMatchIn(summary))
        assertFalse(cyrillic.containsMatchIn(catalog))

        // Fields the list has no use for, dropped to keep the assets small. `sector` is kept
        // (task A1 follow-up): the List chapters by it, and a cold start is the first thing a
        // reader, or a judge, ever sees.
        listOf("fscore", "setup_score", "computed_at").forEach {
            assertFalse("the summary snapshot still carries $it", summary.contains("\"$it\""))
        }
        assertTrue("the snapshot is chaptered from the first frame, so it must carry sector", summary.contains("\"sector\""))
        listOf("deployments", "isin", "logo", "description", "stablecoins").forEach {
            assertFalse("the catalog snapshot still carries $it", catalog.contains("\"$it\""))
        }

        assertTrue("summary.json is ${summary.length / 1024} KB", summary.length < 64 * 1024)
        assertTrue("xstocks.json is ${catalog.length / 1024} KB", catalog.length < 256 * 1024)
    }

    @Test
    fun `a build with no snapshot, or a broken one, simply has no fallback`() = runTest {
        assertNull(BundledSnapshotRepository(AssetSource { null }).listSnapshot())

        val truncated = AssetSource { path ->
            ByteArrayInputStream(bytes(path).copyOfRange(0, 400)) as InputStream
        }
        assertNull("half a JSON file is not a snapshot", BundledSnapshotRepository(truncated).listSnapshot())
    }

    @Test
    fun `the snapshot is read once and kept`() = runTest {
        var opens = 0
        val counting = AssetSource { path ->
            opens++
            File(assetsDir, path).takeIf { it.isFile }?.inputStream()
        }
        val repository = BundledSnapshotRepository(counting)
        assertNotNull(repository.listSnapshot())
        assertNotNull(repository.listSnapshot())
        assertEquals("both assets, once each", 2, opens)
    }
}
