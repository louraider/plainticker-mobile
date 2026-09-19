package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * `GET /api/v1/{ticker}/read` (server/vote/README.md, task S3): the peek for everyone, the full
 * content for a caller presenting an entitled device code.
 *
 * `GET /api/v1/{ticker}` itself is byte-unchanged by this route; this is a separate call the
 * Detail screen makes alongside it (task A6), never in place of it, so a ticker this route
 * refuses still renders every free block the base call already carries.
 */
class ReadApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * [code] is this device's own code, in the clear, or null to ask for the peek alone.
     *
     * @throws ReadError.Disabled on 503 `monetization_disabled`
     * @throws ReadError.NotServed on 422 `unsupported_ticker` or 503 `not_available`
     * @throws ReadError.RateLimited on 429
     * @throws ReadError.Unavailable on anything else, a 200 this app cannot parse included
     * @throws IllegalArgumentException when [ticker] is not the shape it must be
     */
    suspend fun get(ticker: String, code: String? = null): TickerReadResponse {
        val symbol = ticker.trim().uppercase()
        require(TICKER.matches(symbol)) { "not a ticker: '$ticker'" }
        val response = client.get("$baseUrl/$symbol$PATH") {
            if (!code.isNullOrBlank()) header(EntitlementApi.HEADER_CODE, code)
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw ReadError.fromErrorBody(response.status.value, text, json)
        }
        return text
            ?.let { runCatching { json.decodeFromString(TickerReadResponse.serializer(), it) }.getOrNull() }
            ?: throw ReadError.Unavailable(response.status.value, text?.trim()?.take(EXCERPT))
    }

    companion object {
        const val PATH = "/read"
        private const val EXCERPT = 200
        private val TICKER = Regex("[A-Z][A-Z0-9.-]{0,9}")
    }
}
