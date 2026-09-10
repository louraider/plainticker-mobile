package com.myapp

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Jupiter Swap API v2, Meta-Aggregator path.
 *
 * GET /order returns a fully assembled transaction plus a requestId; we sign it and POST
 * it to /execute, which lands it through Jupiter's own pipeline. That is why this app
 * needs no RPC to send anything.
 *
 * Keyless access works with no signup at 0.5 RPS, which is why quotes are fetched on
 * demand and never for a list. /execute has its own, far higher bucket.
 *
 * Deliberately built on HttpURLConnection and org.json: both are in the platform, so the
 * spike adds no dependency and no build time.
 */
object JupiterSwap {

    private const val BASE = "https://api.jup.ag/swap/v2"
    private const val TIMEOUT_MS = 20_000

    const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    data class Order(
        val transactionBase64: String,
        val requestId: String,
        val inAmount: Long,
        val outAmount: Long,
        val inUsdValue: Double,
        val outUsdValue: Double,
        val feeBps: Int,
        val platformFeeBps: Int,
        val priceImpactPct: Double,
        val slippageBps: Int,
        val gasless: Boolean,
        val router: String,
        val swapType: String,
        val expireAtEpochSec: Long,
    ) {
        /** All-in cost, the number worth showing a user: what went in vs what came out. */
        val allInCostPct: Double
            get() = if (inUsdValue > 0) (inUsdValue - outUsdValue) / inUsdValue * 100.0 else 0.0

        /** True only for RFQ quotes. Metis orders carry no expireAt at all. */
        val hasExpiry: Boolean get() = expireAtEpochSec > 0

        /** Seconds left, or null when the order has no expiry (a Metis route). */
        fun secondsLeft(nowEpochSec: Long): Long? =
            if (!hasExpiry) null else expireAtEpochSec - nowEpochSec
    }

    data class ExecuteResult(
        val status: String,
        val signature: String?,
        val code: Int?,
        val error: String?,
    )

    /**
     * Fetch a quote AND the assembled transaction. Call this at the moment of the final
     * tap, not when a preview is shown: an RFQ quote reserves part of its lifetime for
     * the maker's own verification, so the user-facing window is shorter than expireAt
     * suggests.
     */
    fun order(inputMint: String, outputMint: String, amount: Long, taker: String): Order {
        val url = "$BASE/order?inputMint=$inputMint&outputMint=$outputMint&amount=$amount&taker=$taker"
        val body = get(url)
        val j = JSONObject(body)
        if (j.has("error")) throw IOException("order failed: " + j.optString("error"))
        return Order(
            transactionBase64 = j.getString("transaction"),
            requestId = j.getString("requestId"),
            inAmount = j.optString("inAmount", "0").toLongOrNull() ?: 0L,
            outAmount = j.optString("outAmount", "0").toLongOrNull() ?: 0L,
            inUsdValue = j.optDouble("inUsdValue", 0.0),
            outUsdValue = j.optDouble("outUsdValue", 0.0),
            feeBps = j.optInt("feeBps", 0),
            platformFeeBps = j.optJSONObject("platformFee")?.optInt("feeBps", 0) ?: 0,
            priceImpactPct = j.optDouble("priceImpactPct", 0.0),
            slippageBps = j.optInt("slippageBps", 0),
            gasless = j.optBoolean("gasless", false),
            router = j.optString("router", "?"),
            swapType = j.optString("swapType", "?"),
            expireAtEpochSec = j.optString("expireAt", "0").toLongOrNull() ?: 0L,
        )
    }

    /**
     * Submit the signed transaction. THIS is the call that moves money; signing alone
     * does not. Documented failure codes worth handling: -2003 quote expired, -2004 swap
     * rejected by the maker's last look (after we signed), -1003 not fully signed. A
     * rejected RFQ cannot be retried with the same signature - it needs a fresh order
     * and a second wallet approval.
     */
    fun execute(signedTransactionBase64: String, requestId: String): ExecuteResult {
        val payload = JSONObject()
            .put("signedTransaction", signedTransactionBase64)
            .put("requestId", requestId)
        val body = post("$BASE/execute", payload.toString())
        val j = JSONObject(body)
        return ExecuteResult(
            status = j.optString("status", "?"),
            signature = j.optString("signature").ifEmpty { null },
            code = if (j.has("code")) j.optInt("code") else null,
            error = j.optString("error").ifEmpty { null },
        )
    }

    private fun get(url: String): String = open(url, "GET", null)

    private fun post(url: String, json: String): String = open(url, "POST", json)

    private fun open(url: String, method: String, json: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            if (json != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            json?.let { conn.outputStream.use { os -> os.write(it.toByteArray()) } }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            // 4xx bodies carry the useful error, so hand them back rather than throwing.
            if (code !in 200..299 && text.isBlank()) {
                throw IOException("HTTP $code from $method $url")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
