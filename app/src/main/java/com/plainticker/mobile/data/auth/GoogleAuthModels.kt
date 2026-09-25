package com.plainticker.mobile.data.auth

import kotlinx.serialization.Serializable

/**
 * The body of `POST /api/v1/auth/google` (server/auth/README.md, section 1 of the web repo): the
 * Google ID token and nothing else. The device code is never a body field; it travels only in
 * the `X-PT-Code` header, and the server ignores a body field that tries to carry it.
 */
@Serializable
internal data class GoogleAuthRequest(val idToken: String) {
    /** An ID token is a bearer credential: a data class's generated toString would print it. */
    override fun toString(): String = "GoogleAuthRequest(idToken=<redacted>)"
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
)

/** Both fields can be null: a wallet-first account may have no name, and the contract allows either. */
@Serializable
data class GoogleAuthUser(
    val email: String? = null,
    val name: String? = null,
)
