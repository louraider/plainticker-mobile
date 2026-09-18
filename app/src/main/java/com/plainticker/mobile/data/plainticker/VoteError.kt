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
     * Voting is not open yet, in either of the two ways that is true.
     *
     * HTTP 404 is a route nobody has published, which was the answer for every ticker until the
     * server half landed on 2026-09-18. HTTP 503 with `vote_not_configured` is that route,
     * published, waiting on the operator to set the collector address and redeploy. To a reader
     * they are one answer: nothing can be built, nothing was signed, and it is not a fault. It is
     * not hidden and not dressed as a failure, and the same code path lights up the moment the
     * route builds.
     */
    class NotOpen(status: Int, code: String?, detail: String?) :
        VoteError(status, code, detail, "vote/build is not open ($status${code?.let { ", $it" } ?: ""}): ${detail ?: "-"}")

    /**
     * HTTP 409 `already_voted`: this wallet already has a counted vote for this ticker. One wallet
     * counts once per ticker, and the server refuses before it builds anything, so a second tap
     * never costs a signature fee. An answer, not a fault, and one the spec named as the state the
     * first contract could not say (docs/skr-curation-spec-2026-09-13.md, gap 1).
     */
    class AlreadyVoted(detail: String?) :
        VoteError(409, CODE_ALREADY_VOTED, detail, "vote/build refused, this wallet already voted: ${detail ?: "-"}")

    /**
     * HTTP 429: the per-IP bucket, the per-voter bucket or the shared RPC daily budget. Whatever the
     * body says, a 429 is this, because an edge limiter answers it with a page and not the contract.
     * A later tap can end differently, so the screen offers one.
     */
    class RateLimited(code: String?, detail: String?) :
        VoteError(429, code, detail, "vote/build rate limited${code?.let { " ($it)" } ?: ""}: ${detail ?: "-"}")

    /** A 4xx the server explained: an unknown ticker, a ticker already covered, a wallet with no stake. */
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
         * `code` is the load-bearing field, as the contract says. The status alone decides only
         * where the body cannot be trusted to be the contract: 404 is [NotOpen] whatever the body
         * says, because a route that is not published answers it with whatever the host chose,
         * and 429 is [RateLimited] whatever the body says, because an edge limiter answers with a
         * page. 503 is [NotOpen] only with `vote_not_configured`; any other 503 is the server
         * being down, which is [Unreadable].
         */
        fun fromErrorBody(status: Int, body: String?, json: Json): VoteError {
            val obj = body?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
            val detail = obj.str("error") ?: body?.trim()?.take(EXCERPT)?.takeIf { it.isNotEmpty() }
            val code = obj.str("code")
            return when {
                status == 404 -> NotOpen(status, code, detail)
                status == 503 && code == CODE_VOTE_NOT_CONFIGURED -> NotOpen(status, code, detail)
                status == 429 -> RateLimited(code, detail)
                status == 409 && code == CODE_ALREADY_VOTED -> AlreadyVoted(detail)
                status in 400..499 && obj != null -> Refused(status, code, detail)
                else -> Unreadable(status, detail)
            }
        }

        /** The slugs this app acts on. Every other slug is logged and reaches the screen as one sentence. */
        const val CODE_ALREADY_VOTED = "already_voted"
        const val CODE_VOTE_NOT_CONFIGURED = "vote_not_configured"
        const val CODE_RATE_LIMITED = "rate_limited"

        private const val EXCERPT = 200

        private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    }
}
