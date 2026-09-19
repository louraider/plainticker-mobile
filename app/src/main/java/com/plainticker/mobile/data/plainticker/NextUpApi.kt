package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.net.RateLimitedException
import com.plainticker.mobile.data.net.extractErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * The read half of SKR-weighted coverage curation: which uncovered tickers lead, and by how much,
 * plus (docs/plan-monetisation-2026-09-19.md section 1.4) the round in progress and the round that
 * closed most recently.
 *
 * ```
 * GET https://www.plainticker.com/api/v1/vote/next-up
 *   200 { "schema": "v1.1", "generated_at": "<ISO-8601>",
 *         "rows": [ { "ticker": "NFLX", "weight": "123456000000", "voters": 3, "last_vote_at": "<ISO-8601>" } ],
 *         "round": { "id": 1, "opens_at": "<ISO-8601>", "closes_at": "<ISO-8601>" },
 *         "previous": null }
 *   404 (a route nobody has published yet)
 *   503 { "error": "vote_not_configured", "code": 503 }
 *   429 { "error": "rate_limited", "code": 429 }
 *   500 { "error": "internal", "code": 500 }
 * ```
 *
 * A sibling of `/summary`: the same envelope, its own per-IP bucket, and `s-maxage=300` at the
 * edge, so the List and a Detail opened from it see one answer for five minutes whichever of them
 * asked first. A 429 arrives as [RateLimitedException] with its Retry-After and any other non-2xx
 * as [ApiException], exactly as [PlainTickerApi] does it, save for one distinction the Vote tab
 * needs and the List's quiet strip never did: a 404, or a 503 whose `error` is
 * `vote_not_configured`, is not a failure at all. It is [NextUpNotOpenException], because voting
 * not being open yet is a state (task A2's not-open state), and conflating it with a 500 would
 * draw an error banner over a screen that has nothing wrong with it.
 */
class NextUpApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /** The leaders, heaviest first, in the server's order, with the round fields beside them. */
    suspend fun getNextUp(): NextUpResponse {
        val response = client.get("$baseUrl$PATH")
        // Read once: a second bodyAsText (or the body() a plain bodyOrThrow would call) on the
        // same response can come back empty once the channel behind it has already been read.
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) throw response.toNextUpException(text)
        return text
            ?.let { runCatching { json.decodeFromString(NextUpResponse.serializer(), it) }.getOrNull() }
            ?: throw ApiException(response.status.value, response.endpoint(), null, text)
    }

    companion object {
        const val PATH = "/vote/next-up"

        /** The one `error` slug this app treats as a state rather than a failure, on a 503. */
        const val CODE_VOTE_NOT_CONFIGURED = "vote_not_configured"
    }
}

/**
 * Voting is not open yet: a bare 404 from a route nobody has published, or a published 503 whose
 * body names [NextUpApi.CODE_VOTE_NOT_CONFIGURED]. Not an [ApiException]: the Vote tab's not-open
 * state (task A2) is not an error a banner should ever describe.
 */
class NextUpNotOpenException(val status: Int) : IOException("vote/next-up is not open ($status)")

private fun HttpResponse.endpoint(): String {
    val url = call.request.url
    return url.host + url.encodedPath
}

private fun HttpResponse.toNextUpException(text: String?): Exception {
    val code = text?.let(::extractErrorCode)
    if (status.value == 404 || (status.value == 503 && code == NextUpApi.CODE_VOTE_NOT_CONFIGURED)) {
        return NextUpNotOpenException(status.value)
    }
    return if (status.value == 429) {
        RateLimitedException(endpoint(), code, text, headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull())
    } else {
        ApiException(status.value, endpoint(), code, text)
    }
}
