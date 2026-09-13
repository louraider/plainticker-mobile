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
import kotlinx.serialization.json.Json

/**
 * SKR-weighted coverage curation: the one call that turns a tap into something a wallet can sign.
 *
 * ```
 * POST https://www.plainticker.com/api/v1/vote/build
 *   body { "ticker": "NFLX", "voter": "<base58 wallet>" }
 *   200  { "transaction": "<base64 v0 transaction>",
 *          "summary": { "ticker": "NFLX", "lamports": 5000, "collector": "<base58>" } }
 *   4xx  { "error": "<human sentence>", "code": "<slug>" }
 * ```
 *
 * **The app does not build the transaction and cannot.** PlainTicker's RPC forwarder allows five
 * read-only methods and `getLatestBlockhash` is not among them, so nothing on the device can date
 * a transaction. The server builds it, the app signs and submits it through Mobile Wallet
 * Adapter, which is the same shape as the swap.
 *
 * **The route is not published yet.** It is blocked on the founder's trigger and on a migration
 * only the operator applies, so today every call here ends in [VoteError.NotOpen]. That is a
 * state and not a crash: the surface says voting is not open yet, and nothing else has to change
 * when the route starts answering.
 */
class VoteApi(
    private val client: HttpClient,
    private val baseUrl: String = PlainTickerApi.BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * Ask the server to assemble one vote. [ticker] is the underlying equity ticker, the same key
     * the rest of the app joins on; [voter] is the connected wallet, and the server reads its
     * weight itself rather than believing anything sent from here.
     *
     * @throws VoteError.NotOpen on 404, which is today's answer for every ticker
     * @throws VoteError.Refused on a 4xx the server explained
     * @throws VoteError.Unreadable on anything else, a 200 this app cannot parse included
     * @throws IllegalArgumentException when [ticker] or [voter] is not the shape it must be
     */
    suspend fun build(ticker: String, voter: String): VoteBuild {
        val symbol = ticker.trim().uppercase()
        require(TICKER.matches(symbol)) { "not a ticker: '$ticker'" }
        val response = client.post("$baseUrl$PATH") {
            contentType(ContentType.Application.Json)
            setBody(VoteBuildRequest(symbol, SolanaRpcApi.requireBase58(voter)))
        }
        val text = runCatching { response.bodyAsText() }.getOrNull()
        if (!response.status.isSuccess()) {
            throw VoteError.fromErrorBody(response.status.value, text, json)
        }
        // A 2xx whose body is not the contract is a failure of this call, not of the wallet, and
        // it is caught here rather than thrown as a serialization exception out of a ViewModel.
        val build = text
            ?.let { runCatching { json.decodeFromString(VoteBuild.serializer(), it) }.getOrNull() }
            ?: throw VoteError.Unreadable(response.status.value, text?.trim()?.take(EXCERPT))
        // The one field this app has to be able to read: bytes it is about to hand to a wallet.
        if (build.transactionBytes() == null) {
            throw VoteError.Unreadable(response.status.value, "transaction is not base64 this app can decode")
        }
        return build
    }

    companion object {
        const val PATH = "/vote/build"

        private const val EXCERPT = 200

        private val TICKER = Regex("[A-Z][A-Z0-9.-]{0,9}")
    }
}
