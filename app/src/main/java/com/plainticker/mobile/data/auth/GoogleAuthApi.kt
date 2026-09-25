package com.plainticker.mobile.data.auth

import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * `POST /api/v1/auth/google/nonce` and `POST /api/v1/auth/google` (server/auth/README.md in the
 * web repo, section 1; docs/google-sign-in.md, "The nonce"): a fresh nonce for the Google request,
 * then signing a person in with the resulting ID token, binding this device's code to that
 * account so it carries the account's Pro.
 *
 * ```
 * POST /api/v1/auth/google/nonce
 * 200 { "nonce": "<opaque>", "expiresAt": "2026-09-25T00:05:00.000Z" }
 *
 * POST /api/v1/auth/google
 * Content-Type: application/json
 * X-PT-Code: <the device's code, in the clear>
 *
 * { "idToken": "<Google ID token>", "nonce": "<the fetched nonce, omitted on a local fallback>" }
 *
 * 200 { "user": { "email", "name" }, "linkedWallets": [...], "pro", "source", "until" }
 * ```
 *
 * The ID token rides in the JSON body only: never a URL, never a query parameter, never a log
 * line. The device code rides in `X-PT-Code` only, the same header [EntitlementApi] sends, and
 * never in the body. The base URL is the one every other `/api/v1` call in this app uses.
 */
class GoogleAuthApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * A fresh nonce for the Google request that follows, and when it stops being valid.
     * `AccountViewModel` calls this before opening Google's sheet, on every sign-in attempt; any
     * failure — a 404 from a server that predates this route, a network error, or anything else —
     * is the caller's cue to fall back to a local random nonce and send [signIn] no `nonce` field
     * at all, so the app keeps working against a server that has not deployed this route yet.
     *
     * @throws GoogleAuthError for every non-2xx answer, and for a 200 this app cannot parse
     * @throws java.io.IOException when the network itself fails (no route, a timeout)
     */
    suspend fun fetchNonce(): GoogleAuthNonceResponse {
        val response = client.post("$baseUrl$NONCE_PATH")
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw GoogleAuthError.fromErrorBody(response.status.value, text, json)
        }
        return text
            ?.let { runCatching { json.decodeFromString(GoogleAuthNonceResponse.serializer(), it) }.getOrNull() }
            ?: throw GoogleAuthError(GoogleAuthFailure.UNKNOWN, response.status.value)
    }

    /**
     * [nonce] is the value [fetchNonce] returned, the same one asked of Google, so the server can
     * check the token's `nonce` claim against what it issued; null on a local fallback, in which
     * case the request carries no `nonce` field and the server sees exactly what it always has.
     *
     * @throws GoogleAuthError for every non-2xx answer the contract names, and for a 200 this app
     *   cannot parse
     * @throws java.io.IOException when the network itself fails (no route, a timeout)
     */
    suspend fun signIn(idToken: String, deviceCode: String?, nonce: String? = null): GoogleAuthResponse {
        val response = client.post("$baseUrl$PATH") {
            contentType(ContentType.Application.Json)
            if (!deviceCode.isNullOrBlank()) header(HEADER_CODE, deviceCode)
            setBody(GoogleAuthRequest(idToken, nonce))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw GoogleAuthError.fromErrorBody(response.status.value, text, json)
        }
        return text
            ?.let { runCatching { json.decodeFromString(GoogleAuthResponse.serializer(), it) }.getOrNull() }
            ?: throw GoogleAuthError(GoogleAuthFailure.UNKNOWN, response.status.value)
    }

    companion object {
        const val PATH = "/auth/google"
        const val NONCE_PATH = "/auth/google/nonce"
        const val HEADER_CODE = EntitlementApi.HEADER_CODE
    }
}
