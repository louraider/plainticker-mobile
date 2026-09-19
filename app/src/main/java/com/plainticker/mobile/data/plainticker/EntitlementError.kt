package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Why `GET /api/v1/entitlement` did not hand back a usable answer.
 *
 * The server's own `error` sentence never reaches a screen, exactly as [VoteError] keeps it out:
 * it is not translated and is not subject to the copy lint that governs every other sentence this
 * app shows. It goes to the debug log; the reader gets one of this app's own sentences instead.
 */
sealed class EntitlementError(message: String) : IOException(message) {

    /**
     * The route is not turned on yet: 503 `monetization_disabled`, or a 404 the route answers
     * before it exists at all. A state, not a fault: the app's own Pro block reads it as "not
     * offered by this server yet" rather than as an error banner.
     */
    class Disabled(val status: Int, val code: String?) :
        EntitlementError("entitlement disabled ($status${code?.let { ", $it" } ?: ""})")

    class RateLimited(val retryAfterSeconds: Long?) : EntitlementError("entitlement rate limited")

    /** Anything else: a 5xx, a gateway page, or a 200 this app could not parse as the contract. */
    class Unavailable(val status: Int?, val detail: String?) :
        EntitlementError("entitlement unavailable${status?.let { " (HTTP $it)" } ?: ""}: ${detail ?: "-"}")

    companion object {
        const val CODE_MONETIZATION_DISABLED = "monetization_disabled"
        private const val EXCERPT = 200

        /** Maps a non-2xx entitlement answer onto one of the three states above. */
        fun fromErrorBody(status: Int, body: String?, json: Json): EntitlementError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            return when {
                status == 503 && code == CODE_MONETIZATION_DISABLED -> Disabled(status, code)
                // A 404 predates the monetization flag (server/vote/README.md), and today, while
                // the flag is unset, is exactly the state a fresh install meets: read the same way.
                status == 404 -> Disabled(status, code)
                status == 429 -> RateLimited(null)
                else -> Unavailable(status, detail)
            }
        }

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
