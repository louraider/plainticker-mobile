package com.plainticker.mobile.data.auth

import kotlinx.serialization.Serializable

/**
 * The body of `POST /api/v1/auth/google` (server/auth/README.md, section 1 of the web repo): the
 * Google ID token, and the server-issued [nonce] `GoogleAuthApi.fetchNonce` returned, when there
 * was one to send (docs/google-sign-in.md, "The nonce"). The device code is never a body field;
 * it travels only in the `X-PT-Code` header, and the server ignores a body field that tries to
 * carry it.
 *
 * [nonce] is null on a local fallback (the nonce endpoint was unreachable or predates this
 * server), and `Json.explicitNulls = false` ([com.plainticker.mobile.data.net.HttpClientFactory])
 * then drops the key entirely rather than sending `"nonce":null`, so an older server sees exactly
 * the one-field body it always has.
 */
@Serializable
internal data class GoogleAuthRequest(val idToken: String, val nonce: String? = null) {
    /** An ID token is a bearer credential: a data class's generated toString would print it. */
    override fun toString(): String = "GoogleAuthRequest(idToken=<redacted>, nonce=${nonce ?: "null"})"
}

/**
 * The 200 answer. `pro`/`source`/`until` are the same shape as `GET /api/v1/entitlement`; the app
 * does not act on them directly; it re-reads the entitlement through the one refresh every other
 * screen already uses, so there is one place Pro is decided on this device.
 */
@Serializable
data class GoogleAuthResponse(
    val user: GoogleAuthUser = GoogleAuthUser(),
    val linkedWallets: List<String> = emptyList(),
    val pro: Boolean = false,
    val source: String? = null,
    val until: String? = null,
    /**
     * How many of this phone's own grants (a pass paid with its code, a promo code redeemed on
     * it) moved to the Google account in this sign-in (server/auth/README.md §1, 2026-09-29: Pro
     * belongs to the account, not the phone). A server older than that never sends it: 0, and
     * You says nothing about a move.
     */
    val moved: Int = 0,
)

/** Both fields can be null: a wallet-first account may have no name, and the contract allows either. */
@Serializable
data class GoogleAuthUser(
    val email: String? = null,
    val name: String? = null,
)

/**
 * The 200 answer of `POST /api/v1/auth/google/nonce` (docs/google-sign-in.md, "The nonce"): a
 * fresh nonce for the Google request that follows, and when it stops being valid. A blank
 * [nonce] (missing key, or a server that answers something this app cannot parse) is treated by
 * `GoogleAuthApi.fetchNonce`'s caller exactly like a fetch failure: fall back to a local one.
 */
@Serializable
data class GoogleAuthNonceResponse(
    val nonce: String = "",
    val expiresAt: String? = null,
)
