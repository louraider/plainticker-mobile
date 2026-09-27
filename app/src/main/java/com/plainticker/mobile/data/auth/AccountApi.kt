package com.plainticker.mobile.data.auth

import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `GET /api/v1/account` and `POST /api/v1/account/wallets/unlink` (a web agent's contract, built
 * in parallel with this app): re-reading the signed-in account's own state the way every screen
 * re-reads the entitlement, and dropping one linked wallet from it. Both answer the exact shape
 * [GoogleAuthApi.signIn] does.
 *
 * ```
 * GET /api/v1/account
 * X-PT-Code: <the device's code, in the clear>
 * 200 { "user": { "email", "name" }, "linkedWallets": [...], "pro", "source", "until" }
 *
 * POST /api/v1/account/wallets/unlink
 * X-PT-Code: <the device's code, in the clear>
 * Content-Type: application/json
 * { "wallet": "<base58>" }
 * 200 { "user": { "email", "name" }, "linkedWallets": [...], "pro", "source", "until" }
 * ```
 *
 * The device code rides in `X-PT-Code` only, the same header [GoogleAuthApi] and
 * [EntitlementApi] send: never a query parameter, never the body, never a log line.
 */
class AccountApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * Called whenever a Google account is signed in (`AccountViewModel.refresh`), throttled there
     * to at most once every thirty seconds. A 404 (the route is not deployed yet) or a 5xx are the
     * caller's cue to keep the cached account exactly as it was, the same convention every other
     * `/api/v1` route not yet deployed reads.
     *
     * @throws AccountApiError.NotSignedIn on 401 `not_signed_in`: this device is no longer bound
     * @throws AccountApiError.BadRequest on 400 `bad_request`
     * @throws AccountApiError.RateLimited on 429 `rate_limited`
     * @throws AccountApiError.NotOpen on 404: the route is not deployed yet
     * @throws AccountApiError.Unavailable on a 5xx, or a 200 this app cannot parse
     * @throws java.io.IOException when the network itself fails (no route, a timeout)
     */
    suspend fun get(deviceCode: String?): GoogleAuthResponse {
        val response = client.get("$baseUrl$PATH") {
            if (!deviceCode.isNullOrBlank()) header(HEADER_CODE, deviceCode)
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw AccountApiError.fromErrorBody(response.status.value, text, json)
        }
        return decodeAccount(response.status.value, text)
    }

    /**
     * [wallet] is the base58 address to drop from the signed-in account's own linked wallets.
     *
     * @throws AccountApiError.NotSignedIn on 401 `not_signed_in`
     * @throws AccountApiError.BadRequest on 400 `bad_request`
     * @throws AccountApiError.NotLinked on 400 `not_linked`: not one of this account's own wallets
     * @throws AccountApiError.LastMethod on 409 `last_method`: it is the only way to sign in
     * @throws AccountApiError.RateLimited on 429 `rate_limited`
     * @throws AccountApiError.NotOpen on 404: the route is not deployed yet
     * @throws AccountApiError.Unavailable on a 5xx, or a 200 this app cannot parse
     * @throws java.io.IOException when the network itself fails (no route, a timeout)
     */
    suspend fun unlinkWallet(wallet: String, deviceCode: String?): GoogleAuthResponse {
        val response = client.post("$baseUrl$UNLINK_PATH") {
            contentType(ContentType.Application.Json)
            if (!deviceCode.isNullOrBlank()) header(HEADER_CODE, deviceCode)
            setBody(AccountUnlinkRequest(wallet))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw AccountApiError.fromErrorBody(response.status.value, text, json)
        }
        return decodeAccount(response.status.value, text)
    }

    /**
     * A 2xx body as the shared account shape, or [AccountApiError.Unavailable] naming why not. The
     * detail names the decoder's exception class, never the body: a 200 here carries the account's
     * email and wallets, which have no business in a log line, debug build or not.
     */
    private fun decodeAccount(status: Int, text: String?): GoogleAuthResponse {
        if (text.isNullOrBlank()) throw AccountApiError.Unavailable(status, "empty body")
        return try {
            json.decodeFromString(GoogleAuthResponse.serializer(), text)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw AccountApiError.Unavailable(status, "undecodable body (${e::class.simpleName})")
        }
    }

    companion object {
        const val PATH = "/account"
        const val UNLINK_PATH = "/account/wallets/unlink"
        const val HEADER_CODE = EntitlementApi.HEADER_CODE
    }
}

@Serializable
internal data class AccountUnlinkRequest(val wallet: String)
