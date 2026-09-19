package com.plainticker.mobile.data.plainticker

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * `GET /api/v1/entitlement`'s body, and the exact shape `POST /api/v1/pass/confirm` answers with
 * once a payment verifies (server/vote/README.md, "the SAME shape GET /api/v1/entitlement
 * returns for a pass source").
 *
 * Never a user identifier, never the wallet, never an email: the load-bearing privacy property
 * of the route is that this shape is all it ever carries.
 */
@Serializable
data class EntitlementResponse(
    val pro: Boolean = false,
    /** "pass", "stake", "subscription", or null when [pro] is false. */
    val source: String? = null,
    /** ISO timestamp for pass or subscription; null for stake (continuously re-evaluated) or none. */
    val until: String? = null,
) {
    val sourceKind: EntitlementSource? get() = EntitlementSource.of(source)

    /** Epoch millis of [until], or null when absent or not ISO-8601. */
    fun untilEpochMillis(): Long? = until?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    companion object {
        /** The answer for a device presenting no code, or a code bound to nothing: not an error. */
        val NONE = EntitlementResponse(pro = false, source = null, until = null)
    }
}

/**
 * The three ways a wallet carries Pro (docs/plan-monetisation-2026-09-19.md section 1.2): a paid
 * 30-day pass, a staked SKR principal at or above the threshold, or a web subscription reached
 * through a linked wallet. Read honestly and stated as one of these, never collapsed into a
 * single "Pro" badge that hides which one is actually carrying it.
 */
enum class EntitlementSource {
    PASS, STAKE, SUBSCRIPTION;

    companion object {
        /** Null for a blank, missing or unrecognized source: an unread source says nothing rather than guesses. */
        fun of(raw: String?): EntitlementSource? = when (raw?.trim()?.lowercase()) {
            "pass" -> PASS
            "stake" -> STAKE
            "subscription" -> SUBSCRIPTION
            else -> null
        }
    }
}
