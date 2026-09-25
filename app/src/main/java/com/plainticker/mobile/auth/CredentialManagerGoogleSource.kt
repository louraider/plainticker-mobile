package com.plainticker.mobile.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock

/**
 * Sign in with Google through Android Credential Manager (docs/google-sign-in.md).
 *
 * [GetSignInWithGoogleOption] is the option Google pairs with an explicit "Sign in with Google"
 * button: it always shows Google's own sheet, lists every account on the device, and lets the
 * person add one there. [serverClientId] is the WEB OAuth client id, so the token's audience is
 * the one the server verifies; a token minted for the Android client id is refused with
 * `wrong_audience`. The Android OAuth client (package plus signing SHA-1) only has to exist in
 * the same Google Cloud project; its id is never passed here.
 *
 * [context] must be an Activity context: Credential Manager draws its sheet over it. The token is
 * returned to the caller and nowhere else: no log line, no field, no file.
 *
 * Every [GetCredentialException] is turned into the right [GoogleCredentialResult] by
 * [GoogleCredentialFailureClassifier], which this class times: [clock] marks the moment
 * `getCredential` is called, and the classifier gets the elapsed milliseconds when it fails, to
 * tell a real cancel from a setup failure the framework dressed up as one. [debugLog] gets the
 * exception's class and, for a [GetCredentialException], its `type` string, in debug builds only,
 * and nothing else: never the token, never a claim.
 */
class CredentialManagerGoogleSource(
    private val context: Context,
    private val serverClientId: String,
    private val debugLog: AccountDebugLog = AccountDebugLog.ANDROID,
    private val clock: Clock = WallClock,
) : GoogleCredentialSource {

    override suspend fun requestIdToken(nonce: String): GoogleCredentialResult {
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val startedAt = clock.nowMillis()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleCredentialResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleCredentialResult.Failed
            }
        } catch (e: GoogleIdTokenParsingException) {
            debugLog.raw("credential: ${e::class.simpleName}")
            GoogleCredentialResult.Failed
        } catch (e: GetCredentialException) {
            val elapsedMs = clock.nowMillis() - startedAt
            val result = GoogleCredentialFailureClassifier.classify(e, elapsedMs)
            debugLog.raw("credential: ${e::class.simpleName} type=${e.type} elapsedMs=$elapsedMs -> $result")
            result
        }
    }
}
