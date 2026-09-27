package com.plainticker.mobile.ui.you

import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.auth.GoogleCredentialResult
import com.plainticker.mobile.auth.GoogleCredentialSource
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.auth.AccountApi
import com.plainticker.mobile.data.auth.GoogleAuthApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.prefs.AccountStore
import com.plainticker.mobile.prefs.InMemoryDevicePassStore
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.prefs.FakePrefs
import com.plainticker.mobile.prefs.InMemoryPendingSignOutStore
import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore
import com.plainticker.mobile.data.auth.AccountSignOut
import com.plainticker.mobile.data.auth.DeviceCodeStatus
import com.plainticker.mobile.data.auth.DeviceRekeyApi
import com.plainticker.mobile.data.auth.DeviceRekeyer
import com.plainticker.mobile.data.auth.SignOutResult
import com.plainticker.mobile.prefs.SignedInAccount
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Account section's state machine (docs/google-sign-in.md): a fake [GoogleCredentialSource]
 * stands in for Credential Manager, a MockEngine stands in for `POST /api/v1/auth/google`, and
 * every way a sign-in can end is walked to the one [AccountMessage] the screen names it with.
 * Also pinned here: the device code rides in `X-PT-Code`, and the ID token reaches no log line,
 * no stored field and no state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val machines = mutableListOf<AccountViewModel>()

    @After
    fun tearDown() {
        machines.forEach { it.viewModelScope.cancel() }
    }

    private val nonce = "fixed-test-nonce-0123456789abcdef"
    private val deviceCode = "K7M9QRSTXYK7M9QRSTXYK7M9QR"

    /** A JWT-shaped token: a readable payload carrying [nonce], and a signature that must never leak. */
    private fun jwt(nonce: String?): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"RS256","kid":"k1"}""".toByteArray())
        val claims = buildString {
            append("""{"iss":"https://accounts.google.com","sub":"1234567890"""")
            if (nonce != null) append(""","nonce":"$nonce"""")
            append("}")
        }
        return "$header.${enc.encodeToString(claims.toByteArray())}.SECRET-SIGNATURE-7f3a9c"
    }

    private val token = jwt(nonce)

    private val okBody = """
        {"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"],
         "pro":true,"source":"pass","until":"2026-10-20T00:00:00.000Z"}
    """.trimIndent()

    private class InMemoryAccountStore(initial: SignedInAccount? = null) : AccountStore {
        private val flow = MutableStateFlow(initial)
        override val account: StateFlow<SignedInAccount?> = flow
        val saved = mutableListOf<SignedInAccount>()
        var clears = 0

        override suspend fun save(account: SignedInAccount) {
            saved += account
            flow.value = account
        }

        override suspend fun clear() {
            clears++
            flow.value = null
        }
    }

    private class RecordingLog : AccountDebugLog {
        val lines = mutableListOf<String>()
        override fun raw(line: String) {
            lines += line
        }
    }

    /** A [Clock] this test moves by hand, so [AccountViewModel.refresh]'s throttle is deterministic. */
    private class FakeClock(var millis: Long = 0L) : Clock {
        override fun nowMillis(): Long = millis
    }

    /**
     * The server this suite talks to, with `POST /api/v1/auth/google/nonce` answered by [nonce]
     * ahead of [handler]: the server requires its nonce (judges' review, 2026-09-26), so every
     * sign-in fetches one first, and [token] is built on it. A test about the nonce route itself
     * scripts it with [rawMockApi].
     */
    private fun mockApi(handler: MockRequestHandler) = rawMockApi { request ->
        if (request.url.encodedPath.endsWith("/nonce")) {
            respondJson("""{"nonce":"$nonce","expiresAt":"2026-09-25T00:05:00.000Z"}""")
        } else {
            handler(request)
        }
    }

    private fun rawMockApi(handler: MockRequestHandler) = MockApi(mainDispatcherRule.dispatcher, handler)

    /** The requests that carried a Google token to the server, as opposed to asking for a nonce. */
    private fun MockApi.signInRequests() = requests.filter { it.url.encodedPath.endsWith("/api/v1/auth/google") }

    private fun source(result: GoogleCredentialResult): GoogleCredentialSource = GoogleCredentialSource { result }

    private fun machine(
        api: MockApi = mockApi { respondJson(okBody) },
        store: AccountStore = InMemoryAccountStore(),
        log: AccountDebugLog = RecordingLog(),
        clock: Clock = FakeClock(),
        accountApi: AccountApi = AccountApi(api.client),
        devicePassStore: DevicePassStore = InMemoryDevicePassStore(deviceCode),
        rekeyer: DeviceRekeyer? = null,
        signOut: AccountSignOut? = null,
    ) = AccountViewModel(
        api = GoogleAuthApi(api.client),
        accountApi = accountApi,
        store = store,
        devicePassStore = devicePassStore,
        debugLog = log,
        clock = clock,
        rekeyer = rekeyer,
        signOutRunner = signOut,
    ).also { machines += it }

    // ---- Restoring ---------------------------------------------------------------------------

    @Test
    fun `it starts restoring, then reads signed out from an empty store`() = runTest {
        val vm = machine()
        assertEquals(AccountUiState.Restoring, vm.state.value)
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
    }

    @Test
    fun `a stored account restores as signed in, with no network call`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))
        val api = mockApi { error("restoring must not call the server") }
        val vm = machine(api = api, store = InMemoryAccountStore(stored))
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedIn(stored), vm.state.value)
        assertTrue(api.requests.isEmpty())
    }

    // ---- The happy path ----------------------------------------------------------------------

    @Test
    fun `a sign-in stores what the server returned for display and fires one refresh event`() = runTest {
        val store = InMemoryAccountStore()
        val vm = machine(store = store)
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.signedIn.toList(events) }
        advanceUntilIdle()

        vm.signIn(source(GoogleCredentialResult.Token(token)))
        assertEquals(AccountUiState.SigningIn, vm.state.value)
        advanceUntilIdle()

        val expected = SignedInAccount("ann@example.com", "Ann", listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))
        assertEquals(AccountUiState.SignedIn(expected), vm.state.value)
        assertEquals(listOf(expected), store.saved)
        assertEquals("the screen re-reads the entitlement exactly once", 1, events.size)
    }

    @Test
    fun `the device code rides in the X-PT-Code header of the sign-in call`() = runTest {
        val api = mockApi { respondJson(okBody) }
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()

        val request = api.lastRequest
        assertEquals(deviceCode, request.headers[GoogleAuthApi.HEADER_CODE])
        assertTrue(request.url.encodedPath.endsWith("/api/v1/auth/google"))
    }

    @Test
    fun `the nonce the machine chose is the one asked of Google`() = runTest {
        var asked: String? = null
        val vm = machine()
        advanceUntilIdle()
        vm.signIn { n -> asked = n; GoogleCredentialResult.Token(token) }
        advanceUntilIdle()
        assertEquals(nonce, asked)
    }

    // ---- The nonce fetched from the server (docs/google-sign-in.md, "The nonce") -------------

    @Test
    fun `the server nonce is fetched before the sheet opens, asked of Google, and sent in the sign-in body`() = runTest {
        val serverNonce = "server-issued-nonce-9f3a"
        val serverToken = jwt(serverNonce)
        var asked: String? = null
        val api = rawMockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) {
                respondJson("""{"nonce":"$serverNonce","expiresAt":"2026-09-25T00:05:00.000Z"}""")
            } else {
                respondJson(okBody)
            }
        }
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn { n -> asked = n; GoogleCredentialResult.Token(serverToken) }
        advanceUntilIdle()

        assertEquals("the fetched nonce is the one asked of Google", serverNonce, asked)
        assertEquals(2, api.requests.size)
        assertTrue("the nonce endpoint is called first", api.requests[0].url.encodedPath.endsWith("/nonce"))
        val signInRequest = api.requests[1]
        assertTrue(signInRequest.url.encodedPath.endsWith("/api/v1/auth/google"))
        val body = Json.parseToJsonElement(signInRequest.bodyText()).jsonObject
        assertEquals(serverNonce, body.getValue("nonce").jsonPrimitive.content)
        assertTrue(vm.state.value is AccountUiState.SignedIn)
    }

    /**
     * The server requires its nonce (GOOGLE_SIGNIN_NONCE_REQUIRED in production), so a nonce that
     * cannot be had ends the attempt: Google is never asked, no token is ever sent, and the line is
     * the plain "Couldn't reach PlainTicker". There is no local fallback any more.
     */
    private fun TestScope.assertNoSignInWithout(nonceAnswer: MockRequestHandler) {
        val api = rawMockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) nonceAnswer(request) else error("no sign-in without the server's nonce")
        }
        var asked = false
        val store = InMemoryAccountStore()
        val vm = machine(api = api, store = store)
        advanceUntilIdle()
        vm.signIn { asked = true; GoogleCredentialResult.Token(token) }
        advanceUntilIdle()

        assertEquals(AccountUiState.SignedOut(AccountMessage.NONCE_UNAVAILABLE), vm.state.value)
        assertFalse("Google is never asked without the server's nonce", asked)
        assertTrue("no token reaches the server", api.signInRequests().isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `a 404 from the nonce endpoint ends the sign-in before Google is asked`() = runTest {
        assertNoSignInWithout { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }
    }

    @Test
    fun `a network error fetching the nonce ends the sign-in the same way`() = runTest {
        assertNoSignInWithout { throw IOException("Unable to resolve host") }
    }

    @Test
    fun `a blank, missing or unreadable nonce ends the sign-in the same way`() = runTest {
        assertNoSignInWithout { respondJson("""{"nonce":"","expiresAt":"2026-09-25T00:05:00.000Z"}""") }
        assertNoSignInWithout { respondJson(okBody) }
        assertNoSignInWithout { respondJson("not json") }
        assertNoSignInWithout { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
    }

    @Test
    fun `after a nonce failure, the next attempt fetches a fresh one and can succeed`() = runTest {
        var nonceCalls = 0
        val api = rawMockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) {
                nonceCalls++
                if (nonceCalls == 1) throw IOException("Unable to resolve host")
                respondJson("""{"nonce":"$nonce","expiresAt":"2026-09-25T00:05:00.000Z"}""")
            } else {
                respondJson(okBody)
            }
        }
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(AccountMessage.NONCE_UNAVAILABLE), vm.state.value)

        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedIn)
        assertEquals(2, nonceCalls)
        val body = Json.parseToJsonElement(api.signInRequests().single().bodyText()).jsonObject
        assertEquals(nonce, body.getValue("nonce").jsonPrimitive.content)
    }

    @Test
    fun `blank email and name are dropped, so the identity falls back to plain words`() = runTest {
        val store = InMemoryAccountStore()
        val vm = machine(api = mockApi { respondJson("""{"user":{"email":" ","name":""},"linkedWallets":[],"pro":false}""") }, store = store)
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertEquals(SignedInAccount(null, null, emptyList()), store.saved.single())
    }

    @Test
    fun `a second tap while signing in starts nothing`() = runTest {
        var asks = 0
        val vm = machine()
        advanceUntilIdle()
        val counting = GoogleCredentialSource { asks++; GoogleCredentialResult.Token(token) }
        vm.signIn(counting)
        vm.signIn(counting)
        advanceUntilIdle()
        assertEquals(1, asks)
        vm.signIn(counting)
        advanceUntilIdle()
        assertEquals("signed in: the button is gone and a stray call does nothing", 1, asks)
    }

    // ---- Google's side -----------------------------------------------------------------------

    @Test
    fun `every way Google can decline lands signed out with its own message, and never calls the server`() = runTest {
        val cases = mapOf(
            GoogleCredentialResult.Cancelled to AccountMessage.CANCELLED,
            GoogleCredentialResult.NoAccount to AccountMessage.NO_ACCOUNT,
            GoogleCredentialResult.NoPlayServices to AccountMessage.NO_PLAY_SERVICES,
            GoogleCredentialResult.SetupProblem to AccountMessage.SETUP_PROBLEM,
            GoogleCredentialResult.Interrupted to AccountMessage.INTERRUPTED,
            GoogleCredentialResult.Failed to AccountMessage.CREDENTIAL_FAILED,
        )
        cases.forEach { (result, message) ->
            val api = mockApi { error("no token, no call") }
            val vm = machine(api = api)
            advanceUntilIdle()
            vm.signIn(source(result))
            advanceUntilIdle()
            assertEquals(result.toString(), AccountUiState.SignedOut(message), vm.state.value)
            // The nonce is fetched before the sheet opens; nothing else reaches the server.
            assertTrue(api.signInRequests().isEmpty())
            assertEquals(1, api.requests.size)
        }
    }

    @Test
    fun `a credential source that throws is an unknown failure, not a crash`() = runTest {
        val vm = machine()
        advanceUntilIdle()
        vm.signIn { throw IllegalStateException("provider exploded") }
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(AccountMessage.UNKNOWN), vm.state.value)
    }

    @Test
    fun `a token without the requested nonce is refused before it leaves the device`() = runTest {
        listOf(jwt("some-other-nonce"), jwt(null), "not-a-jwt").forEach { wrong ->
            val api = mockApi { error("a token with the wrong nonce must never be sent") }
            val vm = machine(api = api)
            advanceUntilIdle()
            vm.signIn(source(GoogleCredentialResult.Token(wrong)))
            advanceUntilIdle()
            assertEquals(AccountUiState.SignedOut(AccountMessage.NONCE_MISMATCH), vm.state.value)
            assertTrue(api.signInRequests().isEmpty())
        }
    }

    // ---- The server's side -------------------------------------------------------------------

    /** Every row of the README's error table, and the two answers outside it. */
    private val serverCases = listOf(
        Triple(400, "bad_request", AccountMessage.BAD_REQUEST),
        Triple(400, "bad_device_code", AccountMessage.BAD_DEVICE_CODE),
        Triple(401, "invalid_token", AccountMessage.INVALID_TOKEN),
        Triple(401, "expired_token", AccountMessage.EXPIRED_TOKEN),
        Triple(401, "wrong_audience", AccountMessage.WRONG_AUDIENCE),
        Triple(401, "nonce_invalid", AccountMessage.NONCE_INVALID),
        Triple(401, "nonce_expired", AccountMessage.NONCE_EXPIRED),
        Triple(403, "email_not_verified", AccountMessage.EMAIL_NOT_VERIFIED),
        Triple(429, "rate_limited", AccountMessage.RATE_LIMITED),
        Triple(500, "internal", AccountMessage.INTERNAL),
        Triple(503, "auth_disabled", AccountMessage.AUTH_DISABLED),
        Triple(503, "not_configured", AccountMessage.NOT_CONFIGURED),
        Triple(503, "jwks_unavailable", AccountMessage.JWKS_UNAVAILABLE),
        Triple(404, "not_found", AccountMessage.NOT_OPEN),
        // The pack's shared server contract (2026-09-27).
        Triple(409, "link_on_web", AccountMessage.LINK_ON_WEB),
        Triple(401, "rekey_required", AccountMessage.REKEY_PENDING),
        Triple(401, "code_retired", AccountMessage.CODE_RETIRED),
        Triple(502, "gateway", AccountMessage.UNKNOWN),
    )

    @Test
    fun `every server answer lands signed out with its own message, and stores nothing`() = runTest {
        serverCases.forEach { (status, code, message) ->
            val store = InMemoryAccountStore()
            val vm = machine(api = mockApi { respondJson("""{"error":"$code","code":$status}""", HttpStatusCode.fromValue(status)) }, store = store)
            val events = mutableListOf<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.signedIn.toList(events) }
            advanceUntilIdle()
            vm.signIn(source(GoogleCredentialResult.Token(token)))
            advanceUntilIdle()
            assertEquals(code, AccountUiState.SignedOut(message), vm.state.value)
            assertTrue(code, store.saved.isEmpty())
            assertTrue("$code must not refresh the entitlement", events.isEmpty())
        }
    }

    @Test
    fun `a network failure lands signed out with the network message`() = runTest {
        val vm = machine(api = mockApi { throw IOException("Unable to resolve host") })
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(AccountMessage.NETWORK), vm.state.value)
    }

    @Test
    fun `a retry after a failure clears the old message and can succeed`() = runTest {
        // The nonce fetch runs before every attempt and mockApi answers it with the fixed nonce
        // `token` was built on, so this test's own counter tracks only the /auth/google calls.
        var calls = 0
        val api = mockApi {
            calls++
            if (calls == 1) respondJson("""{"error":"expired_token","code":401}""", HttpStatusCode.Unauthorized) else respondJson(okBody)
        }
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(AccountMessage.EXPIRED_TOKEN), vm.state.value)

        vm.signIn(source(GoogleCredentialResult.Token(token)))
        assertEquals(AccountUiState.SigningIn, vm.state.value)
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedIn)
    }

    @Test
    fun `dismissing the message keeps the reader signed out`() = runTest {
        val vm = machine()
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Cancelled))
        advanceUntilIdle()
        vm.dismissMessage()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
    }

    // ---- Sign-out ----------------------------------------------------------------------------

    private val signedInAnn = SignedInAccount("ann@example.com", "Ann", emptyList())

    /** The server's `POST /api/v1/account/signout` requests, as opposed to every other route. */
    private fun MockApi.signOutRequests() = requests.filter { it.url.encodedPath.endsWith("/api/v1/account/signout") }

    @Test
    fun `sign out asks the server first with the device code, and only its 200 is a plain signed out`() = runTest {
        val api = mockApi { respondJson("""{"ok":true}""") }
        val store = InMemoryAccountStore(signedInAnn)
        val vm = machine(api = api, store = store)
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedIn)

        vm.signOut()
        advanceUntilIdle()
        val request = api.signOutRequests().single()
        assertEquals("POST", request.method.value)
        assertEquals(deviceCode, request.headers["X-PT-Code"])
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
        assertEquals(1, store.clears)
    }

    @Test
    fun `while the sign-out call is in flight the row says so, keeps the account, and a second tap sends nothing`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val api = mockApi {
            gate.await()
            respondJson("""{"ok":true}""")
        }
        val store = InMemoryAccountStore(signedInAnn)
        val vm = machine(api = api, store = store)
        advanceUntilIdle()

        vm.signOut()
        runCurrent()
        assertEquals(AccountUiState.SignedIn(signedInAnn, signingOut = true), vm.state.value)
        assertEquals("nothing is forgotten before the server answers", 0, store.clears)
        vm.signOut()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, api.signOutRequests().size)
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
    }

    @Test
    fun `offline, sign out still forgets the account here, says the server has not confirmed, and queues one retry`() = runTest {
        var offline = true
        // Counted here: a MockEngine request whose handler throws is not kept in its history.
        var signOutCalls = 0
        val api = mockApi {
            signOutCalls++
            if (offline) throw IOException("Unable to resolve host") else respondJson("""{"ok":true}""")
        }
        val pending = InMemoryPendingSignOutStore()
        val log = RecordingLog()
        val runner = AccountSignOut(AccountApi(api.client), InMemoryDevicePassStore(deviceCode), pending, log = log, io = mainDispatcherRule.dispatcher)
        val store = InMemoryAccountStore(signedInAnn)
        val vm = machine(api = api, store = store, signOut = runner, log = log)
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(AccountMessage.SIGN_OUT_UNCONFIRMED), vm.state.value)
        assertEquals(1, store.clears)
        assertTrue("one retry is queued; log: ${log.lines}", pending.isPending())

        offline = false
        assertEquals(SignOutResult.CONFIRMED, runner.retryPending())
        assertFalse(pending.isPending())
        assertEquals(2, signOutCalls)
        assertNull("nothing left to retry", runner.retryPending())
        assertEquals(2, signOutCalls)
    }

    @Test
    fun `a 5xx, a 404 or a rate limit on sign out are unconfirmed too, never a plain signed out`() = runTest {
        listOf(503 to "<html>down</html>", 404 to """{"error":"not_found"}""", 429 to """{"error":"rate_limited"}""").forEach { (status, body) ->
            val api = mockApi { respondJson(body, HttpStatusCode.fromValue(status)) }
            val store = InMemoryAccountStore(signedInAnn)
            val vm = machine(api = api, store = store)
            advanceUntilIdle()
            vm.signOut()
            advanceUntilIdle()
            assertEquals("$status", AccountUiState.SignedOut(AccountMessage.SIGN_OUT_UNCONFIRMED), vm.state.value)
            assertEquals("$status", 1, store.clears)
        }
    }

    @Test
    fun `not_signed_in on sign out is the same fact as a 200`() = runTest {
        val vm = machine(api = mockApi { respondJson("""{"error":"not_signed_in"}""", HttpStatusCode.Unauthorized) }, store = InMemoryAccountStore(signedInAnn))
        advanceUntilIdle()
        vm.signOut()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
    }

    @Test
    fun `code_retired on sign out clears the account and You gets the retired line`() = runTest {
        val store = InMemoryAccountStore(signedInAnn)
        val vm = machine(api = mockApi { respondJson("""{"error":"code_retired"}""", HttpStatusCode.Unauthorized) }, store = store)
        advanceUntilIdle()
        vm.signOut()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
        assertEquals(1, store.clears)
        assertEquals(DeviceCodeStatus.RETIRED, vm.deviceCodeStatus.value)
    }

    @Test
    fun `a new sign-in drops a queued sign-out retry first, so it can never unbind the new account`() = runTest {
        val api = mockApi { respondJson(okBody) }
        val pending = InMemoryPendingSignOutStore(pending = true)
        val runner = AccountSignOut(AccountApi(api.client), InMemoryDevicePassStore(deviceCode), pending, log = { }, io = mainDispatcherRule.dispatcher)
        val vm = machine(api = api, signOut = runner)
        advanceUntilIdle()

        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedIn)
        assertFalse(pending.isPending())
        assertNull(runner.retryPending())
        assertTrue(api.signOutRequests().isEmpty())
    }

    @Test
    fun `sign-in code_retired also tells You the code is retired`() = runTest {
        val vm = machine(api = mockApi { respondJson("""{"error":"code_retired"}""", HttpStatusCode.Unauthorized) })
        advanceUntilIdle()
        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(AccountMessage.CODE_RETIRED), vm.state.value)
        assertEquals(DeviceCodeStatus.RETIRED, vm.deviceCodeStatus.value)
    }

    @Test
    fun `only link_on_web offers the web account page, and it is the one the contract names`() {
        AccountMessage.entries.forEach { message ->
            assertEquals(message.name, message == AccountMessage.LINK_ON_WEB, accountMessageOpensWeb(message))
        }
        assertFalse(accountMessageOpensWeb(null))
        assertEquals("https://www.plainticker.com/en/account", AccountMessage.LINK_ON_WEB_URL)
    }

    // ---- The device code: rekey_required on account routes ------------------------------------

    private val legacyCode = "K7M9QRSTXY"

    @Test
    fun `a 401 rekey_required on refresh keeps the cached account, never reads as signed out`() = runTest {
        val store = InMemoryAccountStore(signedInAnn)
        val vm = machine(api = mockApi { respondJson("""{"error":"rekey_required"}""", HttpStatusCode.Unauthorized) }, store = store)
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedIn(signedInAnn), vm.state.value)
        assertEquals(0, store.clears)
    }

    @Test
    fun `a legacy device's refresh answered rekey_required rekeys once and retries once with the new code`() = runTest {
        val prefs = FakePrefs().also { it.edit().putString(SharedPrefsDevicePassStore.KEY_CODE, legacyCode).commit() }
        val codes = SharedPrefsDevicePassStore(prefs)
        var rekeyCalls = 0
        val api = mockApi { request ->
            when {
                request.url.encodedPath.endsWith("/device/rekey") -> {
                    rekeyCalls++
                    // The preflight is offline; the rekey forced by rekey_required lands.
                    if (rekeyCalls == 1) throw IOException("offline") else respondJson("""{"ok":true}""")
                }
                request.headers["X-PT-Code"] == legacyCode ->
                    respondJson("""{"error":"rekey_required"}""", HttpStatusCode.Unauthorized)
                else -> respondJson(refreshedBody)
            }
        }
        val log = RecordingLog()
        val rekeyer = DeviceRekeyer(codes, DeviceRekeyApi(api.client), FakeClock(), log, mainDispatcherRule.dispatcher)
        val store = InMemoryAccountStore(signedInAnn)
        val vm = machine(api = api, store = store, devicePassStore = codes, rekeyer = rekeyer, log = log)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        val newCode = codes.code()
        assertTrue(SharedPrefsDevicePassStore.isNewFormat(newCode))
        val accountCalls = api.requests.filter { it.url.encodedPath.endsWith("/api/v1/account") }
        assertEquals(listOf(legacyCode, newCode), accountCalls.map { it.headers["X-PT-Code"] })
        assertEquals(2, rekeyCalls)
        assertEquals(2, (vm.state.value as AccountUiState.SignedIn).account.linkedWallets.size)
        assertEquals(0, store.clears)
        log.lines.forEach { line ->
            assertFalse(legacyCode in line)
            assertFalse(newCode in line)
        }
    }

    // ---- Refreshing the account (GET /api/v1/account) -----------------------------------------

    private val refreshedBody = """
        {"user":{"email":"ann@example.com","name":"Ann"},
         "linkedWallets":["4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T","9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"],
         "pro":true,"source":"pass","until":"2026-10-20T00:00:00.000Z"}
    """.trimIndent()

    @Test
    fun `refresh does nothing before a Google account is signed in`() = runTest {
        val api = mockApi { error("refresh must not call the server with nobody signed in") }
        val vm = machine(api = api)
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedOut)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `refresh re-reads the account, updates the store, and fires the shared refresh event`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))
        val store = InMemoryAccountStore(stored)
        val api = mockApi { respondJson(refreshedBody) }
        val vm = machine(api = api, store = store)
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.signedIn.toList(events) }
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        val expected = SignedInAccount(
            "ann@example.com", "Ann",
            listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T", "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"),
        )
        assertEquals(AccountUiState.SignedIn(expected), vm.state.value)
        assertEquals(expected, store.saved.last())
        assertEquals(1, events.size)
        assertTrue("the account route was read", api.lastRequest.url.encodedPath.endsWith("/api/v1/account"))
    }

    @Test
    fun `refresh throttles to at most once every thirty seconds`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", emptyList())
        val clock = FakeClock(0L)
        val api = mockApi { respondJson(refreshedBody) }
        val vm = machine(api = api, store = InMemoryAccountStore(stored), clock = clock)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, api.requests.size)

        vm.refresh()
        advanceUntilIdle()
        assertEquals("inside the window, a second call does nothing", 1, api.requests.size)

        clock.millis = AccountViewModel.REFRESH_THROTTLE_MILLIS - 1
        vm.refresh()
        advanceUntilIdle()
        assertEquals("one millisecond short of the window still does nothing", 1, api.requests.size)

        clock.millis = AccountViewModel.REFRESH_THROTTLE_MILLIS
        vm.refresh()
        advanceUntilIdle()
        assertEquals("at the window's edge, refresh runs again", 2, api.requests.size)
    }

    @Test
    fun `a 401 not_signed_in on refresh clears the account and shows signed out honestly`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))
        val store = InMemoryAccountStore(stored)
        val vm = machine(api = mockApi { respondJson("""{"error":"not_signed_in"}""", HttpStatusCode.Unauthorized) }, store = store)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
        assertEquals(1, store.clears)
    }

    @Test
    fun `a 404 on refresh keeps the cached account quietly`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"))
        val store = InMemoryAccountStore(stored)
        val vm = machine(api = mockApi { respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound) }, store = store)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedIn(stored), vm.state.value)
        assertTrue("the 404 is not written back as a save", store.saved.isEmpty())
    }

    @Test
    fun `offline or a 5xx on refresh also keeps the cached account quietly`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", emptyList())
        listOf<MockRequestHandler>(
            { throw IOException("Unable to resolve host") },
            { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) },
        ).forEach { answer ->
            val store = InMemoryAccountStore(stored)
            val vm = machine(api = mockApi(answer), store = store)
            advanceUntilIdle()
            vm.refresh()
            advanceUntilIdle()
            assertEquals(AccountUiState.SignedIn(stored), vm.state.value)
            assertTrue(store.saved.isEmpty())
        }
    }

    // ---- The stale linked-wallets list (v1.3.14, seen on a Seeker) -----------------------------
    //
    // On the device, You kept listing a wallet the server had already dropped: unlink answered
    // 400 not_linked, and a relaunch past the throttle still showed it. The server's GET and the
    // unlink check read the same rows, so the list was the sign-in cache, never refreshed. Cause:
    // YouScreen's LifecycleResumeEffect calls refresh() the instant You is shown, which on a cold
    // open is before the DataStore's first emission lands, so the state was still Restoring and
    // refresh() returned without asking, and nothing ever asked again while You stayed up.

    /** A fake key, never a real wallet: the one the server no longer lists. */
    private val staleWallet = "StaLeWa11etFakeKey1111111111111111111111111"

    /** A fake key: the one the server still lists. */
    private val keptWallet = "KeptWa11etFakeKey11111111111111111111111111"

    /**
     * An [AccountStore] whose first read lands only when [land] is called, like the DataStore
     * file read on Dispatchers.IO: the screen's first refresh() always runs before it.
     */
    private class SlowAccountStore(initial: SignedInAccount?) : AccountStore {
        private val loaded = CompletableDeferred<Unit>()
        private val current = MutableStateFlow(initial)
        val saved = mutableListOf<SignedInAccount>()

        override val account: Flow<SignedInAccount?> = flow {
            loaded.await()
            emitAll(current)
        }

        fun land() {
            loaded.complete(Unit)
        }

        override suspend fun save(account: SignedInAccount) {
            saved += account
            current.value = account
        }

        override suspend fun clear() {
            current.value = null
        }
    }

    /** What `buildAccountResponse` answers once the wallet is gone: a real server shape, name null. */
    private val serverWithoutStale = """
        {"user":{"email":"ann@example.com","name":null},"linkedWallets":["$keptWallet"],"pro":false,"source":null,"until":null}
    """.trimIndent()

    /** What it answers while the wallet is still linked. */
    private val serverWithStale = """
        {"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["$staleWallet","$keptWallet"],"pro":false,"source":null,"until":null}
    """.trimIndent()

    private val notLinked = """{"error":"not_linked","code":400}"""

    @Test
    fun `a refresh asked for before the stored account loads runs once it lands`() = runTest {
        val cached = SignedInAccount("ann@example.com", "Ann", listOf(staleWallet, keptWallet))
        val store = SlowAccountStore(cached)
        val api = mockApi { respondJson(serverWithoutStale) }
        val vm = machine(api = api, store = store)
        advanceUntilIdle()
        assertEquals("the store has not answered yet", AccountUiState.Restoring, vm.state.value)

        vm.refresh() // YouScreen's LifecycleResumeEffect, on a cold open of You
        advanceUntilIdle()
        assertTrue("nothing to refresh until the account is known", api.requests.isEmpty())

        store.land()
        advanceUntilIdle()

        assertEquals("the kept ask ran exactly once", 1, api.requests.size)
        assertTrue(api.lastRequest.url.encodedPath.endsWith("/api/v1/account"))
        val signedIn = vm.state.value as AccountUiState.SignedIn
        assertEquals("the wallet the server dropped is gone", listOf(keptWallet), signedIn.account.linkedWallets)
        assertNull("the server's null name reads as no name", signedIn.account.name)
        assertEquals(listOf(keptWallet), store.saved.last().linkedWallets)
    }

    @Test
    fun `a refresh asked for before a store that turns out empty calls nothing, then or later`() = runTest {
        val store = SlowAccountStore(null)
        val api = mockApi { respondJson(okBody) }
        val vm = machine(api = api, store = store)
        vm.refresh()
        store.land()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)

        vm.signIn(source(GoogleCredentialResult.Token(token)))
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedIn)
        assertTrue(
            "the dropped ask never fires a GET after a later sign-in",
            api.requests.none { it.url.encodedPath.endsWith("/api/v1/account") },
        )
    }

    @Test
    fun `a failed refresh does not start the throttle, so the next resume tries again`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", listOf(staleWallet, keptWallet))
        val store = InMemoryAccountStore(stored)
        var failing = true
        val api = mockApi {
            if (failing) {
                respondJson("""{"error":"internal","code":500}""", HttpStatusCode.InternalServerError)
            } else {
                respondJson(serverWithoutStale)
            }
        }
        val vm = machine(api = api, store = store, clock = FakeClock(0L))
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, api.requests.size)
        assertEquals("a 5xx keeps the cache", AccountUiState.SignedIn(stored), vm.state.value)

        failing = false
        vm.refresh() // the next resume, same instant: a failure never started the window
        advanceUntilIdle()
        assertEquals(2, api.requests.size)
        assertEquals(listOf(keptWallet), (vm.state.value as AccountUiState.SignedIn).account.linkedWallets)

        vm.refresh() // a success does start it
        advanceUntilIdle()
        assertEquals(2, api.requests.size)
    }

    @Test
    fun `unlink not_linked re-reads the account past the throttle and the stale row goes away`() = runTest {
        val cached = SignedInAccount("ann@example.com", "Ann", listOf(staleWallet, keptWallet))
        val store = InMemoryAccountStore(cached)
        var accountReads = 0
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/wallets/unlink")) {
                respondJson(notLinked, HttpStatusCode.BadRequest)
            } else {
                // The first read (the one that started the throttle) predates the server's drop.
                accountReads++
                respondJson(if (accountReads == 1) serverWithStale else serverWithoutStale)
            }
        }
        val vm = machine(api = api, store = store, clock = FakeClock(0L))
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, accountReads)

        vm.unlink(staleWallet) // well inside the thirty-second window
        advanceUntilIdle()

        assertEquals("not_linked forced a second read", 2, accountReads)
        val signedIn = vm.state.value as AccountUiState.SignedIn
        assertEquals(listOf(keptWallet), signedIn.account.linkedWallets)
        assertNull("no failure line is left on a row that is gone", signedIn.unlinkFailure)
        assertNull(signedIn.unlinkingWallet)
        assertEquals(listOf(keptWallet), store.saved.last().linkedWallets)
    }

    @Test
    fun `unlink not_linked keeps its line when the server still lists the wallet`() = runTest {
        val cached = SignedInAccount("ann@example.com", "Ann", listOf(staleWallet, keptWallet))
        val store = InMemoryAccountStore(cached)
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/wallets/unlink")) {
                respondJson(notLinked, HttpStatusCode.BadRequest)
            } else {
                respondJson(serverWithStale)
            }
        }
        val vm = machine(api = api, store = store)
        advanceUntilIdle()

        vm.unlink(staleWallet)
        advanceUntilIdle()

        assertEquals(1, api.requests.count { it.url.encodedPath.endsWith("/api/v1/account") })
        val signedIn = vm.state.value as AccountUiState.SignedIn
        assertEquals(listOf(staleWallet, keptWallet), signedIn.account.linkedWallets)
        assertEquals(staleWallet to UnlinkFailure.NOT_LINKED, signedIn.unlinkFailure)
    }

    @Test
    fun `a refresh that lands while an unlink is in flight keeps the row busy`() = runTest {
        val cached = SignedInAccount("ann@example.com", "Ann", listOf(staleWallet, keptWallet))
        val store = InMemoryAccountStore(cached)
        val unlinkGate = CompletableDeferred<Unit>()
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/wallets/unlink")) {
                unlinkGate.await()
                respondJson(serverWithoutStale)
            } else {
                respondJson(serverWithStale)
            }
        }
        val vm = machine(api = api, store = store)
        advanceUntilIdle()

        vm.unlink(staleWallet)
        vm.refresh()
        // runCurrent, not advanceUntilIdle: virtual time must not reach the client's own timeout
        // while the unlink is held open.
        runCurrent()
        assertEquals("the refresh landed", 1, api.requests.count { it.url.encodedPath.endsWith("/api/v1/account") })
        assertEquals(staleWallet, (vm.state.value as AccountUiState.SignedIn).unlinkingWallet)

        unlinkGate.complete(Unit)
        advanceUntilIdle()
        val signedIn = vm.state.value as AccountUiState.SignedIn
        assertEquals(listOf(keptWallet), signedIn.account.linkedWallets)
        assertNull(signedIn.unlinkingWallet)
    }

    // ---- Unlinking a wallet (POST /api/v1/account/wallets/unlink) -----------------------------

    private val walletA = "4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"
    private val walletB = "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"

    private fun signedInWithTwoWallets(): Pair<InMemoryAccountStore, SignedInAccount> {
        val account = SignedInAccount("ann@example.com", "Ann", listOf(walletA, walletB))
        return InMemoryAccountStore(account) to account
    }

    @Test
    fun `unlink drops the wallet, updates the store, and fires the shared refresh event`() = runTest {
        val (store, _) = signedInWithTwoWallets()
        val afterUnlink = """{"user":{"email":"ann@example.com","name":"Ann"},"linkedWallets":["$walletB"],"pro":false}"""
        val api = mockApi { respondJson(afterUnlink) }
        val vm = machine(api = api, store = store)
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.signedIn.toList(events) }
        advanceUntilIdle()

        vm.unlink(walletA)
        advanceUntilIdle()

        val expected = SignedInAccount("ann@example.com", "Ann", listOf(walletB))
        assertEquals(AccountUiState.SignedIn(expected), vm.state.value)
        assertEquals(expected, store.saved.last())
        assertEquals(1, events.size)

        val request = api.lastRequest
        assertTrue(request.url.encodedPath.endsWith("/api/v1/account/wallets/unlink"))
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals(walletA, body.getValue("wallet").jsonPrimitive.content)
    }

    @Test
    fun `the device code rides in the X-PT-Code header of the unlink call`() = runTest {
        val (store, _) = signedInWithTwoWallets()
        val api = mockApi { respondJson("""{"user":{},"linkedWallets":["$walletB"],"pro":false}""") }
        val vm = machine(api = api, store = store)
        advanceUntilIdle()

        vm.unlink(walletA)
        advanceUntilIdle()
        assertEquals(deviceCode, api.lastRequest.headers[GoogleAuthApi.HEADER_CODE])
    }

    @Test
    fun `a second unlink call while one is in flight does nothing, for any wallet`() = runTest {
        val (store, _) = signedInWithTwoWallets()
        var calls = 0
        val api = mockApi { calls++; respondJson("""{"user":{},"linkedWallets":["$walletB"],"pro":false}""") }
        val vm = machine(api = api, store = store)
        advanceUntilIdle()

        vm.unlink(walletA)
        vm.unlink(walletB)
        val busy = vm.state.value as AccountUiState.SignedIn
        assertEquals(walletA, busy.unlinkingWallet)
        advanceUntilIdle()
        assertEquals("only the first call reached the server", 1, calls)
    }

    /** Every row of the contract's own unlink errors table, and the two answers outside it. */
    private val unlinkCases = listOf(
        Triple(400, "bad_request", UnlinkFailure.BAD_REQUEST),
        Triple(400, "not_linked", UnlinkFailure.NOT_LINKED),
        Triple(409, "last_method", UnlinkFailure.LAST_METHOD),
        Triple(429, "rate_limited", UnlinkFailure.RATE_LIMITED),
        Triple(404, "not_found", UnlinkFailure.NOT_OPEN),
        Triple(502, "gateway", UnlinkFailure.UNAVAILABLE),
    )

    @Test
    fun `every unlink refusal is kept against just that wallet, with its own plain line`() = runTest {
        unlinkCases.forEach { (status, code, failure) ->
            val (store, _) = signedInWithTwoWallets()
            val vm = machine(api = mockApi { respondJson("""{"error":"$code"}""", HttpStatusCode.fromValue(status)) }, store = store)
            advanceUntilIdle()

            vm.unlink(walletA)
            advanceUntilIdle()

            val signedIn = vm.state.value as AccountUiState.SignedIn
            assertEquals(code, listOf(walletA, walletB), signedIn.account.linkedWallets)
            assertNull(code, signedIn.unlinkingWallet)
            assertEquals(code, walletA to failure, signedIn.unlinkFailure)
            assertTrue(code, store.saved.isEmpty())
        }
    }

    @Test
    fun `a 401 not_signed_in on unlink clears the account, not just this one wallet`() = runTest {
        val (store, _) = signedInWithTwoWallets()
        val vm = machine(api = mockApi { respondJson("""{"error":"not_signed_in"}""", HttpStatusCode.Unauthorized) }, store = store)
        advanceUntilIdle()

        vm.unlink(walletA)
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
        assertEquals(1, store.clears)
    }

    @Test
    fun `a network failure unlinking is kept against the wallet as unavailable`() = runTest {
        val (store, _) = signedInWithTwoWallets()
        val vm = machine(api = mockApi { throw IOException("Unable to resolve host") }, store = store)
        advanceUntilIdle()

        vm.unlink(walletA)
        advanceUntilIdle()
        val signedIn = vm.state.value as AccountUiState.SignedIn
        assertEquals(walletA to UnlinkFailure.UNAVAILABLE, signedIn.unlinkFailure)
    }

    @Test
    fun `unlink does nothing before a Google account is signed in`() = runTest {
        val api = mockApi { error("unlink must not call the server with nobody signed in") }
        val vm = machine(api = api)
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedOut)

        vm.unlink(walletA)
        advanceUntilIdle()
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `the device code from refresh and unlink never reaches a log line`() = runTest {
        val log = RecordingLog()
        val (refreshStore, _) = signedInWithTwoWallets()
        val refreshVm = machine(
            api = mockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) },
            store = refreshStore,
            log = log,
        )
        advanceUntilIdle()
        refreshVm.refresh()
        advanceUntilIdle()

        unlinkCases.forEach { (status, code, _) ->
            val (store, _) = signedInWithTwoWallets()
            val vm = machine(
                api = mockApi { respondJson("""{"error":"$code"}""", HttpStatusCode.fromValue(status)) },
                store = store,
                log = log,
            )
            advanceUntilIdle()
            vm.unlink(walletA)
            advanceUntilIdle()
        }

        assertTrue("the recorder saw the failure paths", log.lines.isNotEmpty())
        log.lines.forEach { line -> assertFalse("log line leaks the device code: $line", deviceCode in line) }
    }

    // ---- The ID token never reaches a log ----------------------------------------------------

    @Test
    fun `the ID token reaches no log line, no stored field and no state, on any path`() = runTest {
        val log = RecordingLog()
        val stores = mutableListOf<InMemoryAccountStore>()
        val states = mutableListOf<AccountUiState>()
        val answers = mutableListOf<MockRequestHandler>()
        serverCases.forEach { (status, code, _) ->
            answers.add { respondJson("""{"error":"$code","code":$status}""", HttpStatusCode.fromValue(status)) }
        }
        answers.add { throw IOException("timeout") }
        answers.add { respondJson(okBody) }
        answers.forEach { answer ->
            val store = InMemoryAccountStore().also(stores::add)
            val vm = machine(api = mockApi(answer), store = store, log = log)
            advanceUntilIdle()
            vm.signIn(source(GoogleCredentialResult.Token(token)))
            advanceUntilIdle()
            states += vm.state.value
        }
        // The nonce refusal and a throwing source log too; walk them with the same recorder.
        machine(log = log).also { advanceUntilIdle(); it.signIn(source(GoogleCredentialResult.Token(jwt("other")))); advanceUntilIdle() }
        machine(log = log).also { advanceUntilIdle(); it.signIn { throw IllegalStateException(token) }; advanceUntilIdle() }

        assertTrue("the recorder saw the failure paths", log.lines.size >= serverCases.size)
        val secrets = listOf(token, "SECRET-SIGNATURE-7f3a9c", deviceCode)
        log.lines.forEach { line -> secrets.forEach { assertFalse("log line leaks a secret: $line", it in line) } }
        stores.flatMap { it.saved }.forEach { saved -> assertFalse(token in saved.toString()) }
        states.forEach { state -> assertFalse(token in state.toString()) }
        assertFalse("a Token result never prints its token", token in GoogleCredentialResult.Token(token).toString())
    }

    /**
     * The runtime test above covers the paths that exist today; this one keeps a future edit from
     * adding a new one: no source file under src/main may put `idToken` on the same line as a log
     * call, and no Ktor logging plugin (which would print request bodies) may be installed.
     */
    @Test
    fun `no source line logs an ID token, and no HTTP logging plugin is installed`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }.canonicalFile
        val sources = File(module, "src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.size > 50)
        val logCall = Regex("""\b(Log\.[vdiwe]|debugLog\.|println|print)\s*\(""")
        val findings = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (logCall.containsMatchIn(line) && Regex("""(?i)id_?token""").containsMatchIn(line)) "${file.name}:${i + 1}: $line" else null
            }
        }
        assertTrue(findings.joinToString("\n"), findings.isEmpty())
        val logging = sources.filter { "io.ktor.client.plugins.logging" in it.readText() }
        assertTrue("Ktor's Logging plugin would print request bodies: $logging", logging.isEmpty())
    }

    @Test
    fun `every message has its own sentence`() {
        val ids = AccountMessage.entries.map(::accountMessageRes)
        assertEquals("no two messages share a sentence", ids.size, ids.toSet().size)
    }
}
