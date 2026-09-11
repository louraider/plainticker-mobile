package com.myapp.data.net

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * A non-2xx answer from one of the JSON APIs.
 *
 * [errorCode] is the body's `error` string when the body was structured JSON
 * (PlainTicker sends `rate_limited` and `internal`); null for HTML or empty bodies.
 */
open class ApiException(
    val status: Int,
    val endpoint: String,
    val errorCode: String?,
    val body: String?,
    message: String = "HTTP $status from $endpoint" + (errorCode?.let { " ($it)" } ?: ""),
) : IOException(message)

/** HTTP 429. [retryAfterSeconds] is the Retry-After header when it is a plain number of seconds. */
class RateLimitedException(
    endpoint: String,
    errorCode: String?,
    body: String?,
    val retryAfterSeconds: Long?,
) : ApiException(
    status = 429,
    endpoint = endpoint,
    errorCode = errorCode,
    body = body,
    message = "rate limited by $endpoint" + (retryAfterSeconds?.let { ", retry after ${it}s" } ?: ""),
)

internal suspend fun HttpResponse.toApiException(): ApiException {
    val url = call.request.url
    val endpoint = url.host + url.encodedPath
    val text = runCatching { bodyAsText() }.getOrNull()?.takeIf { it.isNotBlank() }
    val code = text?.let(::extractErrorCode)
    return if (status.value == 429) {
        RateLimitedException(endpoint, code, text, headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull())
    } else {
        ApiException(status.value, endpoint, code, text)
    }
}

internal fun extractErrorCode(body: String): String? = runCatching {
    val obj = HttpClientFactory.json.parseToJsonElement(body) as? JsonObject
    (obj?.get("error") as? JsonPrimitive)?.contentOrNull
}.getOrNull()

/** Decodes the body on 2xx and throws a typed [ApiException] otherwise. */
internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (!status.isSuccess()) throw toApiException()
    return body()
}
