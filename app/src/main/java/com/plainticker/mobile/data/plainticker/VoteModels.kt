package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Base64

// ---- POST /api/v1/vote/build -------------------------------------------------------------

/**
 * What the app asks for: a ticker to vote for and the wallet that will sign.
 *
 * The weight is deliberately not in this body. The server reads the voter's staked principal
 * itself, through the same pinned `getProgramAccounts` the app reads it with, so a client cannot
 * inflate its own vote even if it wanted to (docs/skr-curation-spec-2026-09-13.md).
 */
@Serializable
data class VoteBuildRequest(
    val ticker: String,
    val voter: String,
)

/**
 * The transaction the server assembled, and the summary the signer is owed before approving it.
 *
 * The app cannot build this itself and that is a property of its own architecture, not an
 * oversight: the RPC forwarder allows five read-only methods and `getLatestBlockhash` is not one
 * of them, so nothing on the device can date a transaction. The server builds, the app signs and
 * sends. It is the same division of labour as the swap, which already lands real money.
 */
@Serializable
data class VoteBuild(
    /** Base64 of an unsigned v0 transaction carrying the vote memo. */
    val transaction: String,
    val summary: VoteSummary,
    /**
     * When the blockhash in [transaction] stops being trustworthy, ISO-8601, about 45 s after the
     * build: a hash lives about a minute and the Seeker's wallet round-trip measured 14 s. Past it
     * the app asks for a fresh transaction rather than handing a stale one to the wallet, which
     * would spend an approval on a failure. Null when the server sent none.
     */
    val expiresAt: String? = null,
) {
    /** The bytes to hand the wallet, or null when [transaction] is not base64 this app can read. */
    fun transactionBytes(): ByteArray? =
        runCatching { Base64.getDecoder().decode(transaction) }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** Epoch millis of [expiresAt], or null when absent or not ISO-8601. */
    fun expiresAtMillis(): Long? = expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    /**
     * True when the expiry is known and [nowMillis] is at or past it. An unknown expiry is not an
     * expired one: a server that sent none has made no promise the app could measure against.
     */
    fun isExpiredAt(nowMillis: Long): Boolean = expiresAtMillis()?.let { nowMillis >= it } ?: false
}

/**
 * What the transaction does, in the server's own figures: which ticker the vote is for, what the
 * signature costs, the address the vote is sent to, and the weight it will be counted at.
 *
 * [collector] is on the screen because a person approving a transfer is owed its destination, and
 * because the vote is public by construction: the server counts votes by walking that address's
 * signatures and reading their memos, so anyone else can count them the same way.
 */
@Serializable
data class VoteSummary(
    val ticker: String,
    /** The signature fee in lamports, about 5,000 for a one-signature transaction. */
    val lamports: Long,
    val collector: String,
    /**
     * The bounded staked principal the server will record, in base units (SKR carries six
     * decimals). It is the figure the vote is counted at, so where it is present it is the figure
     * a signer is shown (docs/skr-curation-spec-2026-09-13.md, gap 3: the figure a person signs
     * for must be the figure that counts). The server's bound keeps it below 2^53, so a Long
     * carries it exactly. Null when the server sent none.
     */
    val weight: Long? = null,
    /** Always false on a 200: a second vote is refused with 409 `already_voted` before anything is built. */
    val alreadyVoted: Boolean = false,
)
