package com.plainticker.mobile.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GoogleCredentialFailureClassifier]: one test per Credential Manager exception type the mapping
 * names precisely, plus the heuristic that tells a real cancel from a setup failure the framework
 * dressed up as one (docs/google-sign-in.md, "Mapping a Credential Manager failure"). This is the
 * real bug: a founder picked an account on a real device, the Android OAuth client did not exist
 * yet, and the app told them "Sign-in was cancelled. Nothing changed."
 */
class GoogleCredentialFailureClassifierTest {

    private val slow = GoogleCredentialFailureClassifier.FAST_FAILURE_MS
    private val fast = 0L

    // The exact provider message this bug reported (Play services' own log line).
    private val setupErrorMessage =
        "This android application is not registered to use OAuth2.0, please confirm the package " +
            "name and SHA-1 certificate fingerprint match"

    // ---- The specific, documented subtypes -------------------------------------------------

    @Test
    fun `a slow cancellation with a benign message is a real cancel`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCancellationException("user backed out"),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.Cancelled, result)
    }

    @Test
    fun `a no-credential exception is no Google account on the phone`() {
        val result = GoogleCredentialFailureClassifier.classify(NoCredentialException(), elapsedMs = slow)
        assertEquals(GoogleCredentialResult.NoAccount, result)
    }

    @Test
    fun `a provider configuration exception is a setup problem`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialProviderConfigurationException(),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, result)
    }

    @Test
    fun `an unsupported exception is a setup problem`() {
        val result = GoogleCredentialFailureClassifier.classify(GetCredentialUnsupportedException(), elapsedMs = slow)
        assertEquals(GoogleCredentialResult.SetupProblem, result)
    }

    @Test
    fun `an interrupted exception says try again, not cancelled or failed`() {
        val result = GoogleCredentialFailureClassifier.classify(GetCredentialInterruptedException(), elapsedMs = slow)
        assertEquals(GoogleCredentialResult.Interrupted, result)
    }

    // ---- The heuristic on a typed cancellation ----------------------------------------------

    @Test
    fun `a cancellation that lands before the sheet could be read is a setup problem, not a cancel`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCancellationException("no message at all"),
            elapsedMs = fast,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, result)
    }

    @Test
    fun `a slow cancellation whose message names the real cause is a setup problem, not a cancel`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCancellationException(setupErrorMessage),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, result)
    }

    @Test
    fun `elapsed time exactly at the threshold is not fast enough to override a benign message`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCancellationException("user backed out"),
            elapsedMs = GoogleCredentialFailureClassifier.FAST_FAILURE_MS,
        )
        assertEquals("the boundary itself counts as slow", GoogleCredentialResult.Cancelled, result)
    }

    // ---- The heuristic on a generic exception (the shape this real bug actually took) -------

    @Test
    fun `a generic exception typed as a cancellation follows the same heuristic`() {
        val cancellationType = GoogleCredentialFailureClassifier.CANCELLATION_TYPE
        val slowBenign = GoogleCredentialFailureClassifier.classify(
            GetCredentialCustomException(cancellationType, "user backed out"),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.Cancelled, slowBenign)

        val fastAny = GoogleCredentialFailureClassifier.classify(
            GetCredentialCustomException(cancellationType, "user backed out"),
            elapsedMs = fast,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, fastAny)

        val slowWithSetupMessage = GoogleCredentialFailureClassifier.classify(
            GetCredentialCustomException(cancellationType, setupErrorMessage),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, slowWithSetupMessage)
    }

    @Test
    fun `the exact bug report reads as a setup problem`() {
        // What actually reached the app: Play services wrapped the "app not registered" error
        // into a cancellation-shaped exception, and it closed almost instantly since no sheet was
        // ever meaningfully shown.
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCancellationException(setupErrorMessage),
            elapsedMs = fast,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, result)
    }

    @Test
    fun `a generic exception with an unrelated type but a setup-shaped message is a setup problem`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCustomException("some.other.type", "invalid_client: no matching client id found"),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.SetupProblem, result)
    }

    @Test
    fun `a generic exception with an unrelated type and a plain message is a credential failure`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCustomException("some.other.type", "something else went wrong"),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.Failed, result)
    }

    @Test
    fun `a generic exception with no message and an unrelated type is a credential failure`() {
        val result = GoogleCredentialFailureClassifier.classify(
            GetCredentialCustomException("some.other.type"),
            elapsedMs = slow,
        )
        assertEquals(GoogleCredentialResult.Failed, result)
    }
}
