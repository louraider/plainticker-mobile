package com.plainticker.mobile.auth

import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Where a Google ID token comes from. The real one is [CredentialManagerGoogleSource]; tests pass
 * a fake, so the sign-in state machine is exercised without Play services or a device.
 */
fun interface GoogleCredentialSource {
    /** Asks Google for an ID token that carries [nonce] as its `nonce` claim. */
    suspend fun requestIdToken(nonce: String): GoogleCredentialResult
}

/** Every way asking Google for a token can end, each of which the You screen names in one line. */
sealed interface GoogleCredentialResult {
    /** The token itself. Its toString never prints it, so a stray log line cannot leak it. */
    class Token(val idToken: String) : GoogleCredentialResult {
        override fun toString(): String = "Token(<redacted>)"
    }

    /** The person closed the Google sheet. */
    data object Cancelled : GoogleCredentialResult

    /** No Google account on this device to sign in with. */
    data object NoAccount : GoogleCredentialResult

    /** No Google Play services (or no credential provider at all) on this device. */
    data object NoPlayServices : GoogleCredentialResult

    /** Anything else: an interrupted request, an answer that was not a Google ID token. */
    data object Failed : GoogleCredentialResult
}

/**
 * The nonce this app puts in every Google sign-in request (`setNonce`), and the check that the
 * token that came back carries it.
 *
 * **What it protects today, and what it does not.** `POST /api/v1/auth/google` does not verify a
 * nonce (the web repo's lib/auth/google-id-token.ts checks signature, issuer, audience, expiry,
 * `sub` and `email_verified`, and nothing else), so the server cannot yet refuse a replayed
 * token. The nonce is sent anyway so the server can start checking it without an app release, and
 * the app itself refuses a token whose `nonce` claim is not the one it just asked for, which keeps
 * a token minted for some other request out of this flow. The claim is read without verifying
 * the signature: that is the server's job, and this check only compares a value the app chose.
 */
object SignInNonce {
    private const val BYTES = 32

    /** 256 random bits, base64url without padding: safe in a JWT claim and in a URL alike. */
    fun create(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** The `nonce` claim of [idToken]'s payload, or null when the token is not a readable JWT. */
    fun claimOf(idToken: String): String? {
        val parts = idToken.split('.')
        if (parts.size != 3) return null
        val payload = runCatching { String(Base64.getUrlDecoder().decode(parts[1].trimEnd('=')), Charsets.UTF_8) }
            .getOrNull() ?: return null
        val obj = runCatching { Json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        return (obj["nonce"] as? JsonPrimitive)?.contentOrNull
    }

    /** True only when [idToken] carries exactly [expected]. */
    fun matches(idToken: String, expected: String): Boolean = claimOf(idToken) == expected
}
