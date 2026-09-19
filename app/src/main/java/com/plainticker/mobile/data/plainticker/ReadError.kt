package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Why `GET /api/v1/{ticker}/read` did not hand back a payload. Never a 404 (server/vote/README.md
 * S3): every refusal is `{error, code}` with a slug, read here into a state rather than a fault.
 */
sealed class ReadError(message: String) : IOException(message) {

    /** 503 `monetization_disabled`: the route exists but the server has not turned it on. */
    class Disabled(val code: String?) : ReadError("read disabled ($code)")

    /** 422 `unsupported_ticker`, or 503 `not_available` for a served ticker with no cached row yet. */
    class NotServed(val status: Int, val code: String?) : ReadError("read not served ($status, $code)")

    class RateLimited(val retryAfterSeconds: Long?) : ReadError("read rate limited")

    /** A 5xx, a gateway page, or a 200 this app could not parse as the contract. */
    class Unavailable(val status: Int?, val detail: String?) :
        ReadError("read unavailable${status?.let { " (HTTP $it)" } ?: ""}: ${detail ?: "-"}")

    companion object {
        private const val CODE_MONETIZATION_DISABLED = "monetization_disabled"
        private const val CODE_UNSUPPORTED_TICKER = "unsupported_ticker"
        private const val CODE_NOT_AVAILABLE = "not_available"
        private const val EXCERPT = 200

        fun fromErrorBody(status: Int, body: String?, json: Json): ReadError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            return when {
                status == 503 && code == CODE_MONETIZATION_DISABLED -> Disabled(code)
                status == 422 && code == CODE_UNSUPPORTED_TICKER -> NotServed(status, code)
                status == 503 && code == CODE_NOT_AVAILABLE -> NotServed(status, code)
                status == 429 -> RateLimited(null)
                else -> Unavailable(status, detail)
            }
        }

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
