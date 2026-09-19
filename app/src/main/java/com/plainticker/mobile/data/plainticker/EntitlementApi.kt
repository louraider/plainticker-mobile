package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * `GET /api/v1/entitlement`: whether a wallet is Pro, and which of the three sources carries it
 * (server/vote/README.md).
 *
 * ```
 * GET /api/v1/entitlement
 * X-PT-Code: <the device's code, in the clear>
 *
 * 200 { "pro": true, "source": "pass", "until": "2026-10-19T00:00:00.000Z" }
 * ```
 *
 * No header, or a code bound to no wallet, answers `{pro: false, source: null, until: null}` on a
 * 200: not an error. `X-PT-Code` carries the code itself, never its hash; the server hashes it to
 * find the wallet, the same way [ReadApi] and the memo prefixes do.
 */
class EntitlementApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * [code] is this device's own code, in the clear, or null to ask unauthenticated (the shape
     * the route answers for anyone).
     *
     * @throws EntitlementError.Disabled on 503 `monetization_disabled` or a 404
     * @throws EntitlementError.RateLimited on 429
     * @throws EntitlementError.Unavailable on anything else, a 200 this app cannot parse included
     */
    suspend fun get(code: String? = null): EntitlementResponse {
        val response = client.get("$baseUrl$PATH") {
            if (!code.isNullOrBlank()) header(HEADER_CODE, code)
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw EntitlementError.fromErrorBody(response.status.value, text, json)
        }
        return text
            ?.let { runCatching { json.decodeFromString(EntitlementResponse.serializer(), it) }.getOrNull() }
            ?: throw EntitlementError.Unavailable(response.status.value, text?.trim()?.take(EXCERPT))
    }

    companion object {
        const val PATH = "/entitlement"
        const val HEADER_CODE = "X-PT-Code"
        private const val EXCERPT = 200
    }
}
