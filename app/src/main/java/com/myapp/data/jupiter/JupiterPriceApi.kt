package com.myapp.data.jupiter

import com.myapp.data.net.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Jupiter Price v3: the app's single price source (list and detail alike).
 *
 * Keyless, 0.5 requests/second, up to 50 mints per call, so the list screen batches every
 * visible mint into as few calls as possible and never quotes per row.
 */
class JupiterPriceApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
) {
    /**
     * USD prices keyed by mint. Mints Jupiter cannot price are simply absent from the
     * result, so callers treat a missing key as "no price", not as an error.
     */
    suspend fun prices(mints: List<String>): Map<String, PriceEntry> {
        val wanted = mints.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, PriceEntry>(wanted.size)
        for (chunk in wanted.chunked(MAX_IDS_PER_REQUEST)) {
            val page: Map<String, PriceEntry?> = client.get(baseUrl) {
                parameter("ids", chunk.joinToString(","))
            }.bodyOrThrow()
            for ((mint, entry) in page) if (entry != null) out[mint] = entry
        }
        return out
    }

    companion object {
        const val BASE_URL = "https://api.jup.ag/price/v3"
        const val MAX_IDS_PER_REQUEST = 50
    }
}
