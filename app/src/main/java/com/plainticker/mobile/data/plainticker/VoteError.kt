package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Why `POST /api/v1/vote/build` did not hand back a transaction, typed by what it changes on
 * the screen rather than by status code.
 *
 * [detail] is the server's own `error` sentence and [code] its slug. Neither reaches a surface:
 * an upstream string is not in this app's voice, is not translated, and is not subject to the
 * copy lint that governs every other sentence the app shows. They go to the debug log, where
 * they are the only thing that explains a refusal after the fact, and the reader gets one of
 * this app's own sentences instead.
 */
sealed class VoteError(
    val status: Int?,
    val code: String?,
    val detail: String?,
    message: String,
) : IOException(message) {

    /**
     * HTTP 404, which today means the route does not exist yet.
     *
     * The endpoint is blocked on the founder and on a migration only the operator applies
     * (docs/skr-curation-spec-2026-09-13.md, "What needs the founder"), so a 404 is the answer
     * this app gets every time until it lands. It is not hidden and it is not dressed as a
     * failure: the surface says voting is not open yet, and the same code path lights up the
     * moment the route answers.
     */
    class NotOpen(detail: String?) :
        VoteError(404, null, detail, "vote/build is not published yet: ${detail ?: "-"}")

    /** A 4xx the server explained: an unknown ticker, a ticker already covered, a refused voter. */
    class Refused(status: Int, code: String?, detail: String?) :
        VoteError(status, code, detail, "vote refused ($status${code?.let { ", $it" } ?: ""}): ${detail ?: "-"}")

    /**
     * The server did not answer in a way this app can act on: a 5xx, a gateway page, a body that
     * is not the contract, or a 200 whose transaction is not base64 this app can decode.
     */
    class Unreadable(status: Int?, detail: String?) :
        VoteError(status, null, detail, "vote/build unreadable${status?.let { " (HTTP $it)" } ?: ""}: ${detail ?: "-"}")

    companion object {
        /**
         * Maps a non-2xx body onto a [VoteError]. The contract answers
         * `{"error": "<human sentence>", "code": "<slug>"}`, and anything else, HTML included,
         * becomes [Unreadable] with a short excerpt.
         *
         * 404 is [NotOpen] whatever the body says, because a route that is not published answers
         * it with whatever the host chose and never with the contract.
         */
        fun fromErrorBody(status: Int, body: String?, json: Json): VoteError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            return when {
                status == 404 -> NotOpen(detail)
                status in 400..499 && obj != null -> Refused(status, code, detail)
                else -> Unreadable(status, detail)
            }
        }

        private const val EXCERPT = 200

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
