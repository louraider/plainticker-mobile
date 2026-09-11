package com.myapp.data.plainticker

import com.myapp.data.net.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/**
 * PlainTicker public API v1.
 *
 * Both routes are edge-cached and rate-limited per IP; a 429 surfaces as
 * [com.myapp.data.net.RateLimitedException] with its Retry-After, a 500 as a structured
 * [com.myapp.data.net.ApiException] (the server never answers these with HTML).
 */
class PlainTickerApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
) {
    /** The whole leaderboard in one call. Cached 5 min at the edge, stale rows included. */
    suspend fun getSummary(): SummaryResponse = client.get("$baseUrl/summary").bodyOrThrow()

    /** Full classification for one ticker. */
    suspend fun getAnalysis(ticker: String): AnalysisPayload {
        val symbol = ticker.trim().uppercase()
        require(TICKER.matches(symbol)) { "not a ticker: '$ticker'" }
        return client.get("$baseUrl/$symbol").bodyOrThrow()
    }

    companion object {
        const val BASE_URL = "https://www.plainticker.com/api/v1"
        private val TICKER = Regex("[A-Z][A-Z0-9.-]{0,9}")
    }
}
