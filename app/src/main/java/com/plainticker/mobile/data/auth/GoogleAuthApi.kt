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
 * `POST /api/v1/auth/google`: signs a person in with a Google ID token and binds this device's
 * code to that account, so the device carries the account's Pro (server/auth/README.md in the web
 * repo, section 1).
 *
 * ```
 * POST /api/v1/auth/google
 * Content-Type: application/json
 * X-PT-Code: <the device's code, in the clear>
 *
 * { "idToken": "<Google ID token>" }
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
     * @throws GoogleAuthError for every non-2xx answer the contract names, and for a 200 this app
     *   cannot parse
     * @throws java.io.IOException when the network itself fails (no route, a timeout)
     */
    suspend fun signIn(idToken: String, deviceCode: String?): GoogleAuthResponse {
        val response = client.post("$baseUrl$PATH") {
            contentType(ContentType.Application.Json)
            if (!deviceCode.isNullOrBlank()) header(HEADER_CODE, deviceCode)
            setBody(GoogleAuthRequest(idToken))
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
        const val HEADER_CODE = EntitlementApi.HEADER_CODE
    }
}
