package com.myapp.repo

import com.myapp.data.net.HttpClientFactory
import com.myapp.data.snapshot.ListSnapshot
import com.myapp.data.snapshot.SnapshotCatalog
import com.myapp.data.snapshot.SnapshotSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/** Opens one bundled asset by its path under `app/src/main/assets`, or null when it is missing. */
fun interface AssetSource {
    fun open(path: String): InputStream?
}

/** The bundled outage snapshot of the List, or null when there is none to read. */
interface SnapshotRepository {
    suspend fun listSnapshot(): ListSnapshot?
}

/**
 * Reads the two assets `scripts/capture-list-snapshot.mjs` writes. It is only ever asked when
 * both live sources have already failed, so the read is off the critical path; it still happens
 * on the IO dispatcher, and the parsed result is kept so a second outage costs nothing.
 *
 * A missing, truncated or unparseable asset is not an error to shout about: it means the app
 * has no fallback, the caller draws its "unavailable" banner, and the reason is swallowed here
 * rather than replacing the outage the user actually hit.
 */
class BundledSnapshotRepository(
    private val assets: AssetSource,
    private val json: Json = HttpClientFactory.json,
    /** Where the asset is read and parsed; a test passes its own so the read is deterministic. */
    private val readContext: CoroutineContext = Dispatchers.IO,
) : SnapshotRepository {

    private var loaded: ListSnapshot? = null

    override suspend fun listSnapshot(): ListSnapshot? {
        loaded?.let { return it }
        val snapshot = withContext(readContext) { read() } ?: return null
        loaded = snapshot
        return snapshot
    }

    private fun read(): ListSnapshot? {
        val summary = parse(SUMMARY_ASSET) { json.decodeFromString(SnapshotSummary.serializer(), it) }
        val catalog = parse(CATALOG_ASSET) { json.decodeFromString(SnapshotCatalog.serializer(), it) }
        val snapshot = ListSnapshot(
            capturedOn = day(summary?.capturedAt) ?: day(catalog?.capturedAt),
            rows = summary?.rows.orEmpty(),
            assets = catalog?.assets.orEmpty(),
        )
        return snapshot.takeUnless { it.isEmpty }
    }

    private fun <T> parse(path: String, decode: (String) -> T): T? = runCatching {
        assets.open(path)?.bufferedReader(Charsets.UTF_8)?.use { decode(it.readText()) }
    }.getOrNull()

    private fun day(stamp: String?): LocalDate? =
        stamp?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        const val SUMMARY_ASSET = "snapshot/summary.json"
        const val CATALOG_ASSET = "snapshot/xstocks.json"
    }
}
