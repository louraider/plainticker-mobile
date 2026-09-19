package com.plainticker.mobile.data.plainticker

import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Paying for Pro from the app (server/vote/README.md, task S3): the server builds the transfer
 * the way it already builds a vote, the wallet signs it through Mobile Wallet Adapter, and the app
 * confirms the signature rather than waiting up to ten minutes for the cron.
 *
 * ```
 * POST /api/v1/pass/build   { "payer": "<base58>", "mint": "USDC", "codeHash": "<sha-256 hex>" }
 * POST /api/v1/pass/confirm { "signature": "<base58>" }
 * ```
 */
class PassApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * Ask the server to assemble one pass payment. [payer] is the connected wallet; [codeHash] is
     * the SHA-256 hex digest of this device's own code, never the code itself.
     *
     * @throws PassError.Disabled on 503 `monetization_disabled` or `pass_not_configured`
     * @throws PassError.RateLimited on 429
     * @throws PassError.Refused on any other 4xx the server explained
     * @throws PassError.Unavailable on anything else, a 200 this app cannot parse included
     * @throws IllegalArgumentException when [payer] is not the shape it must be
     */
    suspend fun build(payer: String, mint: String, codeHash: String): PassBuild {
        val response = client.post("$baseUrl$BUILD_PATH") {
            contentType(ContentType.Application.Json)
            setBody(PassBuildRequest(SolanaRpcApi.requireBase58(payer), mint, codeHash))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw PassError.fromBuildErrorBody(response.status.value, text, json)
        }
        val build = text
            ?.let { runCatching { json.decodeFromString(PassBuild.serializer(), it) }.getOrNull() }
            ?: throw PassError.Unavailable(response.status.value, text?.trim()?.take(EXCERPT))
        if (build.transactionBytes() == null) {
            throw PassError.Unavailable(response.status.value, "transaction is not base64 this app can decode")
        }
        return build
    }

    /**
     * Verify one signed pass transaction on demand, so a reader is not left waiting for the cron.
     * Answers the same shape [EntitlementApi.get] does for a `pass` source.
     *
     * @throws PassError.Unconfirmed on 409, a transient state a later try can resolve
     * @throws PassError.VerificationFailed on 422, which a retry cannot fix
     */
    suspend fun confirm(signature: String): EntitlementResponse {
        val response = client.post("$baseUrl$CONFIRM_PATH") {
            contentType(ContentType.Application.Json)
            setBody(PassConfirmRequest(signature))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw PassError.fromConfirmErrorBody(response.status.value, text, json)
        }
        return text
            ?.let { runCatching { json.decodeFromString(EntitlementResponse.serializer(), it) }.getOrNull() }
            ?: throw PassError.Unavailable(response.status.value, text?.trim()?.take(EXCERPT))
    }

    companion object {
        const val BUILD_PATH = "/pass/build"
        const val CONFIRM_PATH = "/pass/confirm"

        /** The one mint this app offers; USDT is accepted server-side at the same amount. */
        const val MINT_USDC = "USDC"

        private const val EXCERPT = 200
    }
}

@Serializable
private data class PassConfirmRequest(val signature: String)
