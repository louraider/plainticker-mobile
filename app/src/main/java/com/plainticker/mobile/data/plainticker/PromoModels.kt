package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.Serializable
import java.time.Instant

/** `POST /api/v1/promo/redeem`'s body: the code, normalized the same way the server does. */
@Serializable
internal data class PromoRedeemRequest(val code: String)

/**
 * `POST /api/v1/promo/redeem`'s 200 body: the same shape [EntitlementResponse] carries, so a
 * successful redeem can feed straight into the entitlement half of the screen without a second
 * read, even though this app also asks for a fresh one right after ([PassViewModel.applyPromo]).
 */
@Serializable
data class PromoRedeemResponse(
    val pro: Boolean = false,
    /** "promo" on every success this route answers; kept as a string, read the same safe way. */
    val source: String? = null,
    val until: String? = null,
) {
    /** Epoch millis of [until], or null when absent or not ISO-8601. */
    fun untilEpochMillis(): Long? = until?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}
