package com.plainticker.mobile.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

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
 */
class CredentialManagerGoogleSource(
    private val context: Context,
    private val serverClientId: String,
) : GoogleCredentialSource {

    override suspend fun requestIdToken(nonce: String): GoogleCredentialResult {
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleCredentialResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleCredentialResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleCredentialResult.NoAccount
        } catch (e: GetCredentialProviderConfigurationException) {
            // credentials-play-services-auth found no usable provider: Play services missing or
            // too old to serve Credential Manager.
            GoogleCredentialResult.NoPlayServices
        } catch (e: GetCredentialUnsupportedException) {
            GoogleCredentialResult.NoPlayServices
        } catch (e: GoogleIdTokenParsingException) {
            GoogleCredentialResult.Failed
        } catch (e: GetCredentialException) {
            GoogleCredentialResult.Failed
        }
    }
}
