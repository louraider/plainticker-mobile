package com.plainticker.mobile.data.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Every error `POST /api/v1/auth/google` can answer (server/auth/README.md, section 1, "Errors"),
 * one constant per `error` code, plus [NOT_OPEN] for a 404 (the route never answers 404 itself, so
 * a 404 means the deployment does not carry it yet) and [UNKNOWN] for anything outside the table.
 */
enum class GoogleAuthFailure(val wire: String?) {
    BAD_REQUEST("bad_request"),
    BAD_DEVICE_CODE("bad_device_code"),
    INVALID_TOKEN("invalid_token"),
    EXPIRED_TOKEN("expired_token"),
    WRONG_AUDIENCE("wrong_audience"),
    EMAIL_NOT_VERIFIED("email_not_verified"),
    RATE_LIMITED("rate_limited"),
    INTERNAL("internal"),
    AUTH_DISABLED("auth_disabled"),
    NOT_CONFIGURED("not_configured"),
    JWKS_UNAVAILABLE("jwks_unavailable"),
    NOT_OPEN(null),
    UNKNOWN(null),
    ;

    companion object {
        fun fromWire(code: String?): GoogleAuthFailure? = entries.firstOrNull { it.wire != null && it.wire == code }
    }
}

/**
 * A non-2xx answer, or a 200 this app could not read. The message carries the status and the
 * server's short `error` code only: never the request, never the token, never a header.
 */
class GoogleAuthError(val failure: GoogleAuthFailure, val status: Int?) :
    IOException("google sign-in refused: ${failure.name.lowercase()}${status?.let { " (HTTP $it)" } ?: ""}") {

    companion object {
        fun fromErrorBody(status: Int, body: String?, json: Json): GoogleAuthError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val code = (obj?.get("error") as? JsonPrimitive)?.contentOrNull
            val failure = GoogleAuthFailure.fromWire(code) ?: when (status) {
                404 -> GoogleAuthFailure.NOT_OPEN
                429 -> GoogleAuthFailure.RATE_LIMITED
                else -> GoogleAuthFailure.UNKNOWN
            }
            return GoogleAuthError(failure, status)
        }
    }
}
