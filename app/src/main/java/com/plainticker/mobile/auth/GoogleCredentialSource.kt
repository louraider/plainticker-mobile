package com.plainticker.mobile.auth

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

    /**
     * Android or Google itself could not run this request: the Android OAuth client is missing
     * or misconfigured in Google Cloud, or the platform reports no usable credential provider.
     * [GoogleCredentialFailureClassifier] also reaches this from a generic exception that carries
     * a setup failure's message, or a cancellation-shaped one that arrived too fast to be a real
     * tap. Never the reader's doing, so it must never read as "cancelled".
     */
    data object SetupProblem : GoogleCredentialResult

    /** The request was interrupted before it could finish; nothing was decided, safe to retry. */
    data object Interrupted : GoogleCredentialResult

    /** Anything else: an answer that was not a Google ID token, or a token that failed to parse. */
    data object Failed : GoogleCredentialResult
}

/**
 * The nonce this app puts in every Google sign-in request (`setNonce`), and the check that the
 * token that came back carries it. `AccountViewModel.fetchServerNonce` asks the server for this
 * value before the sheet opens (docs/google-sign-in.md, "The nonce"), and without it no sign-in
 * starts: the local fallback [create] once provided is gone, because the server now requires its
 * own nonce (judges' review, 2026-09-26). This object's own [matches] check still runs: it
 * refuses a token whose `nonce` claim is not the one just asked for, which keeps a token minted
 * for some other request out of this flow regardless of what the server does with its copy. The
 * claim is read without verifying the signature: that is the server's job, and this check only
 * compares a value the app itself chose or received.
 */
object SignInNonce {
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
