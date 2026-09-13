package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.Serializable
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
) {
    /** The bytes to hand the wallet, or null when [transaction] is not base64 this app can read. */
    fun transactionBytes(): ByteArray? =
        runCatching { Base64.getDecoder().decode(transaction) }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/**
 * What the transaction does, in the server's own figures: which ticker the vote is for, what the
 * signature costs, and the address the vote is sent to.
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
)
