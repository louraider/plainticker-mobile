package com.plainticker.mobile.data.jupiter

import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.net.RateLimitedException
import com.plainticker.mobile.data.net.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What one run of [JupiterPriceApi.prices] could and could not answer.
 *
 * Every mint asked for lands in exactly one of three states, and a caller has to tell them
 * apart:
 *  - in [priced]: Jupiter quoted it;
 *  - in neither [priced] nor [unfetched]: Jupiter answered and has no price for it, which
 *    is a legitimate result and not a failure;
 *  - in [unfetched]: the request carrying it never came back, so its price is unknown
 *    rather than absent. [failure] is the first error behind [unfetched], null otherwise.
 */
data class PriceFetch(
    val priced: Map<String, PriceEntry> = emptyMap(),
    val unfetched: Set<String> = emptySet(),
    val failure: Throwable? = null,
) {
    /** True when at least one request never came back, so what is on screen is incomplete. */
    val isPartial: Boolean get() = unfetched.isNotEmpty()

    /** True when the keyless bucket refused: worth asking again, not a broken endpoint. */
    val wasRateLimited: Boolean get() = failure is RateLimitedException

    companion object {
        val EMPTY = PriceFetch()
    }
}

/**
 * Jupiter Price v3: the app's single price source (list and detail alike).
 *
 * Keyless, 0.5 requests per second, up to 50 mints per call. Measured on 2026-09-12 the
 * bucket serves about five rapid calls and then answers 429, which used to throw out of
 * this function and discard the chunks that had already succeeded, so one 429 cost every
 * price on the list. Now each chunk stands on its own: calls are [SPACING_MS] apart, a
 * refused chunk waits [BACKOFF_MS] and is tried once more, and whatever came back is
 * returned together with the mints that did not.
 *
 * [sleep] is injected so tests pace without waiting.
 */
class JupiterPriceApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val sleep: suspend (Long) -> Unit = { millis -> delay(millis) },
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * USD prices keyed by mint, fetched [MAX_IDS_PER_REQUEST] at a time. Does not throw for
     * a chunk that failed; read [PriceFetch] for how "no price" and "no answer" differ.
     */
    suspend fun prices(mints: List<String>): PriceFetch {
        val wanted = mints.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return PriceFetch.EMPTY

        val priced = LinkedHashMap<String, PriceEntry>(wanted.size)
        val unfetched = LinkedHashSet<String>()
        var failure: Throwable? = null

        wanted.chunked(MAX_IDS_PER_REQUEST).forEachIndexed { index, chunk ->
            // Pacing sits between calls, so the first chunk goes out at once.
            if (index > 0) sleep(SPACING_MS)

            var answer = attempt(chunk)
            val refused = answer.exceptionOrNull() as? RateLimitedException
            if (refused != null) {
                sleep(backoffMillis(refused))
                answer = attempt(chunk)
            }

            val page = answer.getOrNull()
            if (page == null) {
                // This chunk carries no answer either way. The run keeps going: a 429 is
                // one empty bucket, not a verdict on the next fifty mints.
                unfetched += chunk
                if (failure == null) failure = answer.exceptionOrNull()
            } else {
                for ((mint, entry) in page) if (entry != null) priced[mint] = entry
            }
        }
        return PriceFetch(priced, unfetched, failure)
    }

    /** One request. A transport, status or decoding failure comes back as a value, not a throw. */
    private suspend fun attempt(chunk: List<String>): Result<Map<String, PriceEntry?>> = try {
        val page: JsonObject = client.get(baseUrl) {
            parameter("ids", chunk.joinToString(","))
        }.bodyOrThrow()
        Result.success(page.mapValues { (_, value) -> entryOrNull(value) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        Result.failure(failed)
    }

    /**
     * One value of the answer map, or null when it carries no price.
     *
     * Jupiter lists a mint it cannot price right now with the rest of its metadata and no
     * `usdPrice` key at all: on 2026-09-12, 39 of 50 xStocks in one chunk came back that way.
     * Decoding the map in one go made those 39 throw a missing-field error that took the 11
     * real prices with them, which is why the device showed "Prices unavailable" for the whole
     * list. Each entry is decoded on its own, so an entry with no price is exactly what the
     * contract says it is: a mint Jupiter answered about and has no price for.
     */
    private fun entryOrNull(value: JsonElement): PriceEntry? =
        runCatching { json.decodeFromJsonElement(PriceEntry.serializer(), value) }.getOrNull()

    /** Retry-After when the gateway sends one, never shorter than the standing backoff. */
    private fun backoffMillis(refused: RateLimitedException): Long =
        refused.retryAfterSeconds?.times(1_000L)?.coerceIn(BACKOFF_MS, MAX_BACKOFF_MS) ?: BACKOFF_MS

    companion object {
        const val BASE_URL = "https://api.jup.ag/price/v3"
        const val MAX_IDS_PER_REQUEST = 50

        /** The documented keyless budget is 0.5 requests per second. */
        const val SPACING_MS = 2_000L

        /** A refused chunk waits longer than the pacing before its one retry. */
        const val BACKOFF_MS = 4_000L
        const val MAX_BACKOFF_MS = 10_000L
    }
}
