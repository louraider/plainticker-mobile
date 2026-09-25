package com.plainticker.mobile.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException

/**
 * Turns a Credential Manager [GetCredentialException] into the one [GoogleCredentialResult] the
 * You screen names (docs/google-sign-in.md). Pulled out of [CredentialManagerGoogleSource] so the
 * mapping runs under a plain JVM unit test: no `Context`, no real `CredentialManager`, no device.
 *
 * **The precise mapping:**
 * - [GetCredentialCancellationException] -> [GoogleCredentialResult.Cancelled], but only when
 *   [classifyCancellation] agrees it is a real one; see below.
 * - [NoCredentialException] -> [GoogleCredentialResult.NoAccount].
 * - [GetCredentialProviderConfigurationException] and [GetCredentialUnsupportedException] ->
 *   [GoogleCredentialResult.SetupProblem]: Android itself could not run the request, which is
 *   never something the reader did.
 * - [GetCredentialInterruptedException] -> [GoogleCredentialResult.Interrupted]: safe to retry.
 * - Any other [GetCredentialException] (including a bare [GetCredentialCustomException][
 *   androidx.credentials.exceptions.GetCredentialCustomException]) is checked by [classifyGeneric].
 *
 * **Why a generic exception needs a heuristic, not just a type check.** On a real device, a
 * founder picked an account and the app said "Sign-in was cancelled. Nothing changed." What had
 * actually happened: the Android OAuth client did not exist yet in Google Cloud, so Google's own
 * servers answered "This android application is not registered to use OAuth2.0, please confirm
 * the package name and SHA-1 certificate fingerprint match" (Play services' own log, `Auth:
 * [GetTokenResponseHandler]`). The app only ever saw `CredManProvService: GetCredentialResponse
 * error returned from framework` and whichever [GetCredentialException] the framework chose for
 * it. Credential Manager has no dedicated "the caller is not registered" exception, so Play
 * services' provider folds a developer-configuration failure into the same shape (sometimes the
 * literal [GetCredentialCancellationException] type) it uses for a plain, deliberate cancel.
 *
 * Two signals separate a real cancel from a wrapped setup failure, and either one alone is enough
 * to call it a setup problem:
 * 1. **Timing.** A person cannot read Google's sheet, weigh an account and back out in under
 *    [FAST_FAILURE_MS]. [CredentialManagerGoogleSource] times its own call and hands the elapsed
 *    milliseconds here; a cancellation that lands before the sheet could plausibly have been read
 *    did not come from a tap, no user interaction happened, and it reads as a setup problem.
 * 2. **Message.** When the framework does pass the provider's own words through, they name OAuth,
 *    a client id, a package name or a certificate; [SETUP_ERROR_HINTS] matches on those regardless
 *    of timing.
 *
 * A slow, real cancel whose message names none of this still reads as
 * [GoogleCredentialResult.Cancelled]. This trades a small chance of miscalling a very fast, real
 * cancel (unlikely: even a reflexive tap on an already-familiar sheet takes noticeably longer than
 * a same-frame provider failure) against the alternative this bug showed: telling a reader
 * "cancelled, nothing changed" for something they never touched.
 */
object GoogleCredentialFailureClassifier {

    /** Below this, the sheet had no time to be read, let alone decided against. */
    const val FAST_FAILURE_MS = 1_000L

    /**
     * Words Play services has been seen passing through from the real provider error: an OAuth
     * client problem, an unregistered app, or a certificate/package mismatch. Deliberately broad
     * (case-insensitive, no anchors) since the exact phrasing is Google's to change.
     */
    private val SETUP_ERROR_HINTS = Regex(
        "oauth|not registered|package name|sha-?1|developer[_ -]?error|client[_ -]?id|" +
            "certificate|misconfigur|not configured|unauthorized_client|invalid_client|developer console",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The wire value of [GetCredentialCancellationException]'s own `type`
     * (`androidx.credentials.exceptions.GetCredentialCancellationException.TYPE_GET_CREDENTIAL_CANCELLATION_EXCEPTION`,
     * verified against the library's `.class` file), hardcoded because that constant's own
     * Kotlin-level accessor is `internal` to its module in `credentials` 1.6.0, even though the
     * backing field is a public compile-time constant any exception in that module can inline.
     * Not private, so `GoogleCredentialFailureClassifierTest` can build the exact shape the real
     * bug took (a generic exception typed as a cancellation) without repeating the literal.
     */
    const val CANCELLATION_TYPE = "android.credentials.GetCredentialException.TYPE_USER_CANCELED"

    /** [elapsedMs] is the time [CredentialManagerGoogleSource] spent inside `getCredential`. */
    fun classify(exception: GetCredentialException, elapsedMs: Long): GoogleCredentialResult = when (exception) {
        is GetCredentialCancellationException -> classifyCancellation(exception.message, elapsedMs)
        is NoCredentialException -> GoogleCredentialResult.NoAccount
        is GetCredentialProviderConfigurationException -> GoogleCredentialResult.SetupProblem
        is GetCredentialUnsupportedException -> GoogleCredentialResult.SetupProblem
        is GetCredentialInterruptedException -> GoogleCredentialResult.Interrupted
        else -> classifyGeneric(exception, elapsedMs)
    }

    /**
     * A [GetCredentialException] that is none of the specific subtypes above: some providers
     * (Play services' Credential Manager provider among them) construct the base type directly,
     * or a custom one, rather than the typed subclass Credential Manager otherwise offers for the
     * same [GetCredentialException.type]. When [exception]'s own `type` string is the
     * cancellation one, it gets the same scrutiny a typed cancellation does; failing that, its
     * message alone can still mark it a setup problem, and anything left over is
     * [GoogleCredentialResult.Failed], unchanged from before this fix.
     */
    private fun classifyGeneric(exception: GetCredentialException, elapsedMs: Long): GoogleCredentialResult = when {
        exception.type == CANCELLATION_TYPE -> classifyCancellation(exception.message, elapsedMs)
        looksLikeSetupProblem(exception.message) -> GoogleCredentialResult.SetupProblem
        else -> GoogleCredentialResult.Failed
    }

    private fun classifyCancellation(message: String?, elapsedMs: Long): GoogleCredentialResult =
        if (elapsedMs < FAST_FAILURE_MS || looksLikeSetupProblem(message)) {
            GoogleCredentialResult.SetupProblem
        } else {
            GoogleCredentialResult.Cancelled
        }

    private fun looksLikeSetupProblem(message: String?): Boolean =
        message != null && SETUP_ERROR_HINTS.containsMatchIn(message)
}
