package com.myapp.data.jupiter

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

    /** /execute failed with a code this app does not special-case. */
    class ExecuteFailed(code: Int?, detail: String?) :
        SwapError(Stage.EXECUTE, code, detail, "execute failed${code?.let { " ($it)" } ?: ""}: ${detail ?: "-"}")

    /** A non-2xx with no structured body: gateway page, empty body, ... */
    class Http(stage: Stage, val status: Int, detail: String?) :
        SwapError(stage, null, detail, "HTTP $status from ${stage.name.lowercase()}: ${detail ?: "-"}")

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
         */
        fun fromErrorBody(status: Int, body: String?, stage: Stage, json: Json): SwapError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: obj.str("errorMessage") ?: obj.str("message")
            val code = obj.str("code")?.toIntOrNull() ?: obj.str("errorCode")?.toIntOrNull()
            if (obj == null || (detail == null && code == null)) {
                return Http(stage, status, body?.trim()?.take(200)?.takeIf { it.isNotEmpty() })
            }
            return fromCode(code, detail, stage)
        }

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
