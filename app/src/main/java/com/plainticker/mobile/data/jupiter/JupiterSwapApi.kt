package com.plainticker.mobile.data.jupiter

import com.plainticker.mobile.data.net.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Jupiter Swap API v2 (Ultra), meta-aggregator path.
 *
 * GET /order returns a fully assembled transaction plus a requestId; we sign it and POST
 * it to /execute, which lands it through Jupiter's own pipeline. The app needs no RPC to
 * send anything. Keyless: /order shares the 0.5 rps bucket with Price v3, so it is called
 * at the moment of the final tap, never to render a preview; /execute has its own bucket.
 */
class JupiterSwapApi(
    private val client: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val json: Json = HttpClientFactory.json,
) {
    /**
     * Quote and, when [taker] is given, the transaction to sign. Call it at the final tap:
     * an RFQ quote reserves part of its lifetime for the maker's own verification, so the
     * usable window is shorter than `expireAt` suggests.
     *
     * @throws SwapError.OrderRejected when Jupiter answers with an error body
     * @throws SwapError.Http on a non-2xx without a structured body
     */
    suspend fun order(inputMint: String, outputMint: String, amount: Long, taker: String? = null): SwapOrder {
        val response = client.get("$baseUrl/order") {
            parameter("inputMint", inputMint)
            parameter("outputMint", outputMint)
            parameter("amount", amount)
            if (taker != null) parameter("taker", taker)
        }
        val text = response.bodyAsText()
        val obj = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject
        val errorDetail = obj?.str("error") ?: obj?.str("errorMessage")
        if (!response.status.isSuccess() || obj == null || errorDetail != null) {
            throw SwapError.fromErrorBody(response.status.value, text, SwapError.Stage.ORDER, json)
        }
        return json.decodeFromJsonElement(SwapOrder.serializer(), obj)
    }

    /**
     * Submit the signed transaction. THIS is the call that moves money; signing alone does
     * not. A 2xx comes back as [ExecuteResult] whether it landed or not (check
     * [ExecuteResult.isSuccess] / [ExecuteResult.errorOrNull]); a non-2xx is thrown as the
     * matching [SwapError].
     */
    suspend fun execute(signedTransactionBase64: String, requestId: String): ExecuteResult {
        val response = client.post("$baseUrl/execute") {
            contentType(ContentType.Application.Json)
            setBody(ExecuteRequest(signedTransactionBase64, requestId))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw SwapError.fromErrorBody(response.status.value, text, SwapError.Stage.EXECUTE, json)
        }
        return json.decodeFromString(ExecuteResult.serializer(), text)
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    companion object {
        const val BASE_URL = "https://api.jup.ag/swap/v2"
    }
}
