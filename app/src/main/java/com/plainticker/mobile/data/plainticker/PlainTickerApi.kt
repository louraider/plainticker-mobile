package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header

/**
 * PlainTicker public API v1.
 *
 * Both routes are edge-cached and rate-limited per IP; a 429 surfaces as
 * [com.plainticker.mobile.data.net.RateLimitedException] with its Retry-After, a 500 as a structured
 * [com.plainticker.mobile.data.net.ApiException] (the server never answers these with HTML).
 */
class PlainTickerApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
) {
    /**
     * The whole leaderboard in one call, stale rows included. The route is entitlement-aware: with
     * monetization on, `headline`, `tone` and `setup_score` come back only for a [code] that
     * resolves to Pro (answered private, no-store), and as nulls for everyone else. [code] travels
     * exactly as [getAnalysis] sends it: in the `X-PT-Code` header only, never in the URL, and
     * nothing here logs it. This client keeps no HTTP cache (no OkHttp `Cache`, no Ktor
     * `HttpCache`), so an anonymous answer is never replayed to a device that has since become Pro.
     */
    suspend fun getSummary(code: String? = null): SummaryResponse = client.get("$baseUrl/summary") {
        if (!code.isNullOrBlank()) header(EntitlementApi.HEADER_CODE, code)
    }.bodyOrThrow()

    /**
     * Full classification for one ticker. [code] is this device's own code, in the clear, the same
     * header [ReadApi] and [EntitlementApi] already send, and it is what turns [Verdict.locked]
     * off: this route is entitlement-aware for the verdict block alone, and every field before it
     * in the payload is unchanged whether or not a code is presented.
     */
    suspend fun getAnalysis(ticker: String, code: String? = null): AnalysisPayload {
        val symbol = ticker.trim().uppercase()
        require(TICKER.matches(symbol)) { "not a ticker: '$ticker'" }
        return client.get("$baseUrl/$symbol") {
            if (!code.isNullOrBlank()) header(EntitlementApi.HEADER_CODE, code)
        }.bodyOrThrow()
    }

    companion object {
        const val BASE_URL = "https://www.plainticker.com/api/v1"
        private val TICKER = Regex("[A-Z][A-Z0-9.-]{0,9}")
    }
}
