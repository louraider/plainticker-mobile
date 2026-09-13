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
import kotlinx.coroutines.delay
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
    /**
     * Pause before the aggregator retry below. /order shares the keyless 0.5 rps bucket, so two
     * calls back to back invite a 429 in place of the quote we went back for. Tests pass 0.
     */
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {
    /**
     * Quote and, when [taker] is given, the transaction to sign. Call it at the final tap:
     * an RFQ quote reserves part of its lifetime for the maker's own verification, so the
     * usable window is shorter than `expireAt` suggests.
     *
     * Asks with Jupiter's default routing first, then once more without the RFQ router when the
     * answer was [SwapError.noMarketMakerQuote]. Without that second ask the app refuses every
     * xStock no market maker covers, which on the 2026-09-13 sample was five of the twelve that
     * quote at all, each of them routing fine on the aggregator.
     *
     * @throws SwapError.OrderRejected when Jupiter answers with an error body
     * @throws SwapError.Http on a non-2xx without a structured body
     */
    suspend fun order(inputMint: String, outputMint: String, amount: Long, taker: String? = null): SwapOrder =
        try {
            requestOrder(inputMint, outputMint, amount, taker, excludeRouters = null)
        } catch (rfqRefused: SwapError) {
            if (!rfqRefused.noMarketMakerQuote) throw rfqRefused
            delay(retryDelayMs)
            requestOrder(inputMint, outputMint, amount, taker, excludeRouters = RFQ_ROUTER)
        }

    /**
     * One GET /order. [excludeRouters] is null on the first attempt so RFQ still gets to win when
     * it can, and [RFQ_ROUTER] on the retry.
     */
    private suspend fun requestOrder(
        inputMint: String,
        outputMint: String,
        amount: Long,
        taker: String?,
        excludeRouters: String?,
    ): SwapOrder {
        val response = client.get("$baseUrl/order") {
            parameter("inputMint", inputMint)
            parameter("outputMint", outputMint)
            parameter("amount", amount)
            if (taker != null) parameter("taker", taker)
            if (excludeRouters != null) parameter("excludeRouters", excludeRouters)
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

        /**
         * Jupiter's RFQ router. Excluding it falls the order back to the aggregator (Metis over
         * Raydium, Whirlpool, Manifest and the rest), which is the only way to quote an xStock no
         * market maker covers. Not excluded by default: RFQ prices better when it answers at all
         * (TSLAx at $1,000 came back through it at a price improvement, 2026-09-12).
         */
        const val RFQ_ROUTER = "jupiterz"

        /** Two seconds and change, one tick of the keyless 0.5 rps bucket. */
        const val RETRY_DELAY_MS = 2_100L
    }
}
