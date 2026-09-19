package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.bodyOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.request.get

/**
 * The read half of SKR-weighted coverage curation: which uncovered tickers lead, and by how much.
 *
 * ```
 * GET https://www.plainticker.com/api/v1/vote/next-up
 *   200 { "schema": "v1.1", "generated_at": "<ISO-8601>",
 *         "rows": [ { "ticker": "NFLX", "weight": "123456000000", "voters": 3, "last_vote_at": "<ISO-8601>" } ] }
 *   429 { "error": "rate_limited", "code": 429 }
 *   500 { "error": "internal", "code": 500 }
 * ```
 *
 * A sibling of `/summary`: the same envelope, its own per-IP bucket, and `s-maxage=300` at the
 * edge, so the List and a Detail opened from it see one answer for five minutes whichever of them
 * asked first. A 429 arrives as [com.plainticker.mobile.data.net.RateLimitedException] with its
 * Retry-After and any other non-2xx as [com.plainticker.mobile.data.net.ApiException], exactly as
 * [PlainTickerApi] does it. Neither reaches a screen: the strip this feeds simply does not draw.
 */
class NextUpApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
) {
    /** The leaders, heaviest first, in the server's order. */
    suspend fun getNextUp(): NextUpResponse = client.get("$baseUrl$PATH").bodyOrThrow()

    companion object {
        const val PATH = "/vote/next-up"
    }
}
