package com.plainticker.mobile.data.xstocks

import com.plainticker.mobile.data.net.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * The xStocks catalog as it was last written to disk, and the moment it was captured. The
 * capture time is the key the cache is judged on: it says how old this catalog is, which is the
 * only question a reader of it has.
 */
@Serializable
data class StoredCatalog(
    @SerialName("captured_at_millis") val capturedAtMillis: Long = 0L,
    val assets: List<XStockAsset> = emptyList(),
)

/** The catalog across process death. Null and no-op are both fine answers: it is only a cache. */
interface CatalogCache {
    /** The stored catalog, or null when there is none, it is empty, or it cannot be read. */
    suspend fun read(): StoredCatalog?

    /** Replaces the stored catalog. An empty list is not written: it would erase a usable one. */
    suspend fun write(assets: List<XStockAsset>, capturedAtMillis: Long)

    companion object {
        /**
         * How long a catalog on disk is used without asking the network as well.
         *
         * The catalog changes when xStocks lists or delists a token. That is a batch event a few
         * times a year, not a feed: the capture bundled with this build holds the same 832 Solana
         * assets that the API serves today. So a day is generous against what it risks, which is
         * one newly listed token missing from the list for at most a day, and it is what buys the
         * thing worth having, which is a second launch that pays nothing for the 4.31 MB.
         *
         * The TTL never decides whether the reader waits, only whether the network is asked as
         * well: a catalog past it still paints first and refreshes behind (see
         * [com.plainticker.mobile.repo.CachedCatalogRepository.catalogUpdates]). Guessing it wrong therefore
         * costs a request, never a blank screen.
         */
        const val TTL_MS = 24 * 60 * 60 * 1000L

        /** In cacheDir, not filesDir: the catalog is replaceable, and the system may reclaim it. */
        const val FILE_NAME = "xstocks-catalog.json"
    }
}

/**
 * The catalog in one JSON file. The write goes to a sibling temp file and is renamed over the
 * real one, so a process killed mid-write leaves the previous catalog intact rather than a
 * truncated file; a truncated file would only cost a refetch, but a cache that is quietly
 * unreadable is worse than one that is visibly absent.
 *
 * Nothing here shouts: a cache that cannot be read or written is a cache miss, and the caller
 * has the network to fall back on. The only thing it must never do is answer with rubbish.
 */
class FileCatalogCache(
    private val file: File,
    private val json: Json = HttpClientFactory.json,
    /** Where the file is touched; a test passes its own so the read is deterministic. */
    private val io: CoroutineContext = Dispatchers.IO,
) : CatalogCache {

    override suspend fun read(): StoredCatalog? = withContext(io) {
        runCatching { json.decodeFromString(StoredCatalog.serializer(), file.readText()) }
            .getOrNull()
            ?.takeIf { it.assets.isNotEmpty() }
    }

    override suspend fun write(assets: List<XStockAsset>, capturedAtMillis: Long) {
        if (assets.isEmpty()) return
        withContext(io) {
            runCatching {
                file.parentFile?.mkdirs()
                // A temp name per write, never one shared name: two writers are rare but
                // possible (a screen asking for the catalog while a paging run is out), and
                // sharing the name would let them interleave into one file and then rename
                // that over a catalog that was fine. The temp is deleted either way, so a
                // write that threw leaves nothing behind.
                val temp = File.createTempFile(file.name, ".tmp", file.parentFile)
                try {
                    temp.writeText(json.encodeToString(StoredCatalog.serializer(), StoredCatalog(capturedAtMillis, assets)))
                    // File.renameTo does not replace an existing file on every platform.
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                } finally {
                    temp.delete()
                }
            }
        }
    }
}
