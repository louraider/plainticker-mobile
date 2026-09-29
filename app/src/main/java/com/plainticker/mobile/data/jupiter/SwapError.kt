package com.plainticker.mobile.data.jupiter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * What Jupiter's Swap v2 said no to, typed by the codes that change what the UI does next.
 *
 * - [NotFullySigned] (-1003): our signature set was incomplete; a bug, not a retry.
 * - [QuoteExpired] (-2003): the RFQ window closed between quote and submit.
 * - [RejectedByMaker] (-2004): the maker's last look declined after we signed.
 *
 * The last two need a fresh /order and a second wallet approval; the same signed bytes
 * can never be resubmitted. [needsFreshOrder] says so.
 */
sealed class SwapError(
    val stage: Stage,
    val code: Int?,
    val detail: String?,
    message: String,
) : IOException(message) {

    enum class Stage { ORDER, EXECUTE }

    class NotFullySigned(detail: String?) :
        SwapError(Stage.EXECUTE, CODE_NOT_FULLY_SIGNED, detail, "transaction not fully signed: ${detail ?: "-"}")

    class QuoteExpired(detail: String?) :
        SwapError(Stage.EXECUTE, CODE_QUOTE_EXPIRED, detail, "quote expired: ${detail ?: "-"}")

    class RejectedByMaker(detail: String?) :
        SwapError(Stage.EXECUTE, CODE_REJECTED_BY_MAKER, detail, "swap rejected by market maker: ${detail ?: "-"}")

    /** /order answered with an error body (bad mint, no route, amount out of bounds, ...). */
    class OrderRejected(code: Int?, detail: String?) :
        SwapError(Stage.ORDER, code, detail, "order rejected${code?.let { " ($it)" } ?: ""}: ${detail ?: "-"}")

    /**
     * /order answered 200 with an order this app will not read as one: a fee or rent field that
     * is negative, or fields whose sum overflows ([SwapOrder.feeFieldsValid]). Not a refusal of
     * the pair by Jupiter; a refusal of the order by this app, before anything adds the figures.
     */
    class InvalidOrder(detail: String?) :
        SwapError(Stage.ORDER, null, detail, "order refused by this app: ${detail ?: "-"}")

    /**
     * /order answered 429: the keyless bucket is empty. Jupiter's gateway sends a structured
     * body for it, `{"code":429,"message":"[API Gateway] Too many requests"}` (measured
     * 2026-09-29, the sixth ask in a row), which read as a refusal of the pair until this type
     * existed, and "Check swap availability" then told the Seeker Jupiter had no route. It says
     * nothing about the pair, only that this app asked too often.
     */
    class RateLimited(stage: Stage, detail: String?) :
        SwapError(stage, null, detail, "rate limited at ${stage.name.lowercase()}: ${detail ?: "-"}")

    /** /execute failed with a code this app does not special-case. */
    class ExecuteFailed(code: Int?, detail: String?) :
        SwapError(Stage.EXECUTE, code, detail, "execute failed${code?.let { " ($it)" } ?: ""}: ${detail ?: "-"}")

    /** A non-2xx with no structured body: gateway page, empty body, ... */
    class Http(stage: Stage, val status: Int, detail: String?) :
        SwapError(stage, null, detail, "HTTP $status from ${stage.name.lowercase()}: ${detail ?: "-"}")

    /**
     * The RFQ router refused because no market maker covers this pair, which is not a statement
     * about whether the token trades. Measured 2026-09-13 (docs/data-map.md): the default
     * `GET /order` answers 400 "Quote not available from market maker" for NFLXx, ORCLx, PEPx,
     * UBERx and APPx at every size, and the same request excluding that router returns a full
     * signable transaction through Metis on Raydium CLMM. Treating it as a dead end is what made
     * the app refuse tokens that trade.
     *
     * Jupiter sends this body with no `code`, so the text is the only signal. Matched on the two
     * words that carry it rather than the whole sentence: the message has no schema, and a
     * re-worded refusal must not silently become a dead end again. The `code == null` guard keeps
     * it apart from [RejectedByMaker] (-2004), which is a maker declining after we signed.
     */
    val noMarketMakerQuote: Boolean
        get() = stage == Stage.ORDER && code == null &&
            detail?.contains("market maker", ignoreCase = true) == true

    /**
     * Jupiter looked for a way to fill this order and found none: the RFQ router's "Quote not
     * available from market maker", the aggregator's "Failed to get quotes" (AALx and JEFx at every
     * size, measured 2026-09-29), "No routes found", a token Metis calls not tradable. The one
     * refusal that may be said as "no route".
     *
     * Only an [OrderRejected] qualifies: a rate limit ([RateLimited]) or a server that failed
     * ([Http]) never looked at the pair. Matched on the words, like [noMarketMakerQuote], because
     * these bodies carry no code; a refusal worded otherwise ("Insufficient funds", a bad mint)
     * stays a refusal, which the sheet says as one rather than claiming there is no route.
     */
    val noRoute: Boolean
        get() = this is OrderRejected && detail?.let { words ->
            NO_ROUTE_WORDS.any { words.contains(it, ignoreCase = true) }
        } == true

    val needsFreshOrder: Boolean
        get() = code == CODE_QUOTE_EXPIRED || code == CODE_REJECTED_BY_MAKER

    /**
     * The three codes task T10 names as one automatic requote: -1003, -2003 and -2004. All three
     * mean the signed bytes are dead, so a fresh GET /order is the only way on, and fresh bytes
     * need a fresh wallet approval. [needsFreshOrder] is the narrower fact that the quote itself
     * went; -1003 is our own signature set being incomplete, which a rebuilt order is also the
     * only cure for, so the machine requotes on it and the two properties stay distinct.
     */
    val requotable: Boolean
        get() = code == CODE_NOT_FULLY_SIGNED || needsFreshOrder

    companion object {
        const val CODE_NOT_FULLY_SIGNED = -1003
        const val CODE_QUOTE_EXPIRED = -2003
        const val CODE_REJECTED_BY_MAKER = -2004

        /**
         * The words Jupiter's route refusals carry: "Failed to get quotes", "No routes found",
         * "Quote not available from market maker", and Metis's TOKEN_NOT_TRADABLE ("not tradable").
         */
        private val NO_ROUTE_WORDS = listOf("route", "quote", "tradable")

        private const val STATUS_TOO_MANY_REQUESTS = 429
        private const val STATUS_SERVER_ERROR = 500

        fun fromCode(code: Int?, detail: String?, stage: Stage): SwapError = when (code) {
            CODE_NOT_FULLY_SIGNED -> NotFullySigned(detail)
            CODE_QUOTE_EXPIRED -> QuoteExpired(detail)
            CODE_REJECTED_BY_MAKER -> RejectedByMaker(detail)
            else -> if (stage == Stage.ORDER) OrderRejected(code, detail) else ExecuteFailed(code, detail)
        }

        /**
         * Maps a response body onto a [SwapError]. Jupiter's error bodies are
         * `{"error": "..."}` (order) or `{"code": -2, "error": "..."}` (execute); anything
         * else becomes [Http] with a short excerpt.
         *
         * At the order stage the status speaks first: a 429 is [RateLimited] and a 5xx is [Http]
         * whatever the body says, because neither is Jupiter's answer about the pair. The execute
         * stage keeps reading the body, where a code can say what happened to signed bytes.
         */
        fun fromErrorBody(status: Int, body: String?, stage: Stage, json: Json): SwapError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: obj.str("errorMessage") ?: obj.str("message")
            val code = obj.str("code")?.toIntOrNull() ?: obj.str("errorCode")?.toIntOrNull()
            if (stage == Stage.ORDER && status == STATUS_TOO_MANY_REQUESTS) {
                return RateLimited(stage, detail ?: body?.trim()?.take(200)?.takeIf { it.isNotEmpty() })
            }
            if (stage == Stage.ORDER && status >= STATUS_SERVER_ERROR) {
                return Http(stage, status, detail ?: body?.trim()?.take(200)?.takeIf { it.isNotEmpty() })
            }
            if (obj == null || (detail == null && code == null)) {
                return Http(stage, status, body?.trim()?.take(200)?.takeIf { it.isNotEmpty() })
            }
            return fromCode(code, detail, stage)
        }

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
