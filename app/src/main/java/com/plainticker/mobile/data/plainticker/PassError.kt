package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Why `POST /api/v1/pass/build` or `POST /api/v1/pass/confirm` did not hand back a usable answer.
 * The server's own `error` sentence never reaches a screen, the same rule [VoteError] keeps.
 */
sealed class PassError(val status: Int?, val code: String?, val detail: String?, message: String) :
    IOException(message) {

    /** 503 `monetization_disabled`: a state, not a fault. */
    class Disabled(status: Int, code: String?, detail: String?) :
        PassError(status, code, detail, "pass disabled ($status${code?.let { ", $it" } ?: ""})")

    class RateLimited(code: String?, detail: String?) :
        PassError(429, code, detail, "pass rate limited${code?.let { " ($it)" } ?: ""}")

    /** A 4xx the server explained: `invalid_input`, or `pass_not_configured` on build. */
    class Refused(status: Int, code: String?, detail: String?) :
        PassError(status, code, detail, "pass refused ($status${code?.let { ", $it" } ?: ""}): ${detail ?: "-"}")

    /**
     * `confirm` only, 409 `unconfirmed`: the transaction is not visible at `finalized` yet. A
     * transient state a later try can resolve, not a verification failure.
     */
    class Unconfirmed(detail: String?) :
        PassError(409, "unconfirmed", detail, "pass confirm: not yet visible at finalized: ${detail ?: "-"}")

    /** `confirm` only, 422 `verification_failed`: the transaction did not verify. Not a retry. */
    class VerificationFailed(detail: String?) :
        PassError(422, "verification_failed", detail, "pass confirm refused: ${detail ?: "-"}")

    /** A 5xx, a gateway page, or a 200 this app could not parse as the contract. */
    class Unavailable(status: Int?, detail: String?) :
        PassError(status, null, detail, "pass unavailable${status?.let { " (HTTP $it)" } ?: ""}: ${detail ?: "-"}")

    companion object {
        private const val CODE_MONETIZATION_DISABLED = "monetization_disabled"
        private const val CODE_UNCONFIRMED = "unconfirmed"
        private const val CODE_VERIFICATION_FAILED = "verification_failed"
        private const val EXCERPT = 200

        /** Maps a build failure. */
        fun fromBuildErrorBody(status: Int, body: String?, json: Json): PassError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            return when {
                status == 503 && code == CODE_MONETIZATION_DISABLED -> Disabled(status, code, detail)
                status == 503 -> Disabled(status, code, detail)
                status == 429 -> RateLimited(code, detail)
                status in 400..499 && obj != null -> Refused(status, code, detail)
                else -> Unavailable(status, detail)
            }
        }

        /** Maps a confirm failure, which carries two states build never answers. */
        fun fromConfirmErrorBody(status: Int, body: String?, json: Json): PassError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            return when {
                status == 503 && code == CODE_MONETIZATION_DISABLED -> Disabled(status, code, detail)
                status == 503 -> Disabled(status, code, detail)
                status == 429 -> RateLimited(code, detail)
                status == 409 && code == CODE_UNCONFIRMED -> Unconfirmed(detail)
                status == 422 && code == CODE_VERIFICATION_FAILED -> VerificationFailed(detail)
                status in 400..499 && obj != null -> Refused(status, code, detail)
                else -> Unavailable(status, detail)
            }
        }

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
