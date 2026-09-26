package com.plainticker.mobile.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Every way `GET /api/v1/account` and `POST /api/v1/account/wallets/unlink` can refuse to answer
 * (the server contract a web agent is building in parallel with this app), read the same way
 * [GoogleAuthError] reads `POST /api/v1/auth/google`'s: the `error` field is the code, a 404 means
 * the route is not deployed yet ([NotOpen], the same convention [GoogleAuthFailure.NOT_OPEN] and
 * [com.plainticker.mobile.data.plainticker.PromoError.NotOpen] already read), and anything else
 * unparsable is [Unavailable].
 *
 * [NotSignedIn] is its own case because both callers treat it identically: the device is no longer
 * bound to a Google account, so the caller clears the local account and shows the signed-out state
 * honestly, rather than reading it as a retryable failure the way [Unavailable] is.
 */
sealed class AccountApiError(val status: Int?, val code: String?, val detail: String?, message: String) :
    IOException(message) {

    /** 401 `not_signed_in`: this device's code is no longer bound to a Google account. */
    class NotSignedIn(status: Int?) : AccountApiError(status, CODE_NOT_SIGNED_IN, null, "account: not signed in")

    /** 400 `bad_request`. */
    class BadRequest(detail: String?) :
        AccountApiError(400, CODE_BAD_REQUEST, detail, "account: bad request: ${detail ?: "-"}")

    /** 400 `not_linked`: the wallet named in the unlink request is not one of the account's own. */
    class NotLinked(detail: String?) :
        AccountApiError(400, CODE_NOT_LINKED, detail, "account: not linked: ${detail ?: "-"}")

    /** 409 `last_method`: unlinking this wallet would leave the account with no way to sign in. */
    class LastMethod(detail: String?) :
        AccountApiError(409, CODE_LAST_METHOD, detail, "account: last method: ${detail ?: "-"}")

    class RateLimited(detail: String?) :
        AccountApiError(429, CODE_RATE_LIMITED, detail, "account: rate limited: ${detail ?: "-"}")

    /** The route is not deployed yet: a 404, the same convention every other `/api/v1` route reads. */
    class NotOpen(status: Int?) : AccountApiError(status, null, null, "account: not open ($status)")

    /** A 5xx, a gateway page, or a 200 this app could not parse as the contract. */
    class Unavailable(status: Int?, detail: String?) :
        AccountApiError(status, null, detail, "account unavailable${status?.let { " (HTTP $it)" } ?: ""}: ${detail ?: "-"}")

    companion object {
        const val CODE_NOT_SIGNED_IN = "not_signed_in"
        const val CODE_BAD_REQUEST = "bad_request"
        const val CODE_NOT_LINKED = "not_linked"
        const val CODE_LAST_METHOD = "last_method"
        const val CODE_RATE_LIMITED = "rate_limited"

        /** Maps a non-2xx `/account` or `/account/wallets/unlink` answer onto one of the states above. */
        fun fromErrorBody(status: Int, body: String?, json: Json): AccountApiError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val code = (obj?.get("error") as? JsonPrimitive)?.contentOrNull
            return when (code) {
                CODE_NOT_SIGNED_IN -> NotSignedIn(status)
                CODE_BAD_REQUEST -> BadRequest(code)
                CODE_NOT_LINKED -> NotLinked(code)
                CODE_LAST_METHOD -> LastMethod(code)
                CODE_RATE_LIMITED -> RateLimited(code)
                else -> when (status) {
                    401 -> NotSignedIn(status)
                    400 -> BadRequest(code)
                    409 -> LastMethod(code)
                    429 -> RateLimited(code)
                    404 -> NotOpen(status)
                    else -> Unavailable(status, code ?: body?.trim()?.take(EXCERPT))
                }
            }
        }

        private const val EXCERPT = 200
    }
}
