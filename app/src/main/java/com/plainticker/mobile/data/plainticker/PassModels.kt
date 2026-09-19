package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.Base64

// ---- POST /api/v1/pass/build ------------------------------------------------------------------

/**
 * What the app asks for: the payer that will sign, the mint it pays with, and the SHA-256 hash of
 * this device's own code (server/vote/README.md, task S3). The code itself never leaves the
 * device; only its hash does, exactly like the memo a signed transaction will carry.
 */
@Serializable
data class PassBuildRequest(
    val payer: String,
    /** "USDC" or "USDT", a symbol rather than a raw mint address. */
    val mint: String,
    val codeHash: String,
)

/**
 * The transfer the server assembled, and the summary the signer is owed before approving it. The
 * app cannot build this itself for the same reason it cannot build a vote: the RPC forwarder
 * exposes no `getLatestBlockhash`, so the server builds and the app signs and sends, exactly the
 * shape [VoteBuild] already established.
 */
@Serializable
data class PassBuild(
    /** Base64 of an unsigned v0 transaction carrying the `PT-PASS:<hash>` memo. */
    val transaction: String,
    val summary: PassSummary,
    /** When the blockhash in [transaction] stops being trustworthy, ISO-8601, about 45 s out. */
    val expiresAt: String? = null,
) {
    /** The bytes to hand the wallet, or null when [transaction] is not base64 this app can read. */
    fun transactionBytes(): ByteArray? =
        runCatching { Base64.getDecoder().decode(transaction) }.getOrNull()?.takeIf { it.isNotEmpty() }

    fun expiresAtMillis(): Long? = expiresAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    /** True when the expiry is known and [nowMillis] is at or past it. */
    fun isExpiredAt(nowMillis: Long): Boolean = expiresAtMillis()?.let { nowMillis >= it } ?: false
}

/**
 * What the transaction does, in the server's own figures. [destination] is where the funds land
 * (the treasury's own token account for [mint]); [treasury] is the treasury's bare wallet address,
 * present only as a Solana Pay reference key and never as the transfer's source or destination.
 */
@Serializable
data class PassSummary(
    val mint: String,
    /** Base units of [mint]; USDC and USDT both carry 6 decimals. */
    val amount: Long,
    val destination: String,
    val treasury: String,
    /** The signature fee in lamports, about 5,000 for a one-signature transaction. */
    val lamports: Long,
)
