package com.plainticker.mobile.ui.you

import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.auth.GoogleCredentialResult
import com.plainticker.mobile.auth.GoogleCredentialSource
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.auth.GoogleAuthApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.prefs.AccountStore
import com.plainticker.mobile.prefs.InMemoryDevicePassStore
import com.plainticker.mobile.prefs.SignedInAccount
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun mockApi(handler: MockRequestHandler) = MockApi(mainDispatcherRule.dispatcher, handler)

    private fun source(result: GoogleCredentialResult): GoogleCredentialSource = GoogleCredentialSource { result }

    private fun machine(
        api: MockApi = mockApi { respondJson(okBody) },
        store: InMemoryAccountStore = InMemoryAccountStore(),
        log: AccountDebugLog = RecordingLog(),
    ) = AccountViewModel(
        api = GoogleAuthApi(api.client),
        store = store,
        devicePassStore = InMemoryDevicePassStore(deviceCode),
        nonces = { nonce },
        debugLog = log,
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
        val api = mockApi { request ->
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

    @Test
    fun `a 404 from the nonce endpoint falls back to a local nonce, and the sign-in body carries no nonce field`() = runTest {
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) {
                respondJson("""{"error":"not_found"}""", HttpStatusCode.NotFound)
            } else {
                respondJson(okBody)
            }
        }
        var asked: String? = null
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn { n -> asked = n; GoogleCredentialResult.Token(token) }
        advanceUntilIdle()

        assertEquals("falls back to the local nonce", nonce, asked)
        val signInRequest = api.requests.last()
        val body = Json.parseToJsonElement(signInRequest.bodyText()).jsonObject
        assertEquals("no nonce field on a fallback", setOf("idToken"), body.keys)
        assertTrue(vm.state.value is AccountUiState.SignedIn)
    }

    @Test
    fun `a network error fetching the nonce falls back the same way, and sign-in still succeeds`() = runTest {
        var nonceCalled = false
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) {
                nonceCalled = true
                throw IOException("Unable to resolve host")
            } else {
                respondJson(okBody)
            }
        }
        var asked: String? = null
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn { n -> asked = n; GoogleCredentialResult.Token(token) }
        advanceUntilIdle()

        assertTrue("the nonce endpoint was tried", nonceCalled)
        assertEquals(nonce, asked)
        val signInRequest = api.requests.single { it.url.encodedPath.endsWith("/api/v1/auth/google") }
        val body = Json.parseToJsonElement(signInRequest.bodyText()).jsonObject
        assertEquals(setOf("idToken"), body.keys)
        assertTrue(vm.state.value is AccountUiState.SignedIn)
    }

    @Test
    fun `a blank nonce from the server is treated as a fetch failure and falls back`() = runTest {
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) {
                respondJson("""{"nonce":"","expiresAt":"2026-09-25T00:05:00.000Z"}""")
            } else {
                respondJson(okBody)
            }
        }
        var asked: String? = null
        val vm = machine(api = api)
        advanceUntilIdle()
        vm.signIn { n -> asked = n; GoogleCredentialResult.Token(token) }
        advanceUntilIdle()
        assertEquals(nonce, asked)
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
            assertTrue(api.requests.isEmpty())
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
            assertTrue(api.requests.isEmpty())
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
        // The nonce fetch runs before every attempt (see the nonce tests below); route it
        // separately so this test's own counter tracks only the /auth/google calls it cares
        // about. The nonce response here carries no "nonce" key, so it decodes blank and the
        // ViewModel falls back to the fixed local nonce `token` was built with.
        var calls = 0
        val api = mockApi { request ->
            if (request.url.encodedPath.endsWith("/nonce")) {
                respondJson(okBody)
            } else {
                calls++
                if (calls == 1) respondJson("""{"error":"expired_token","code":401}""", HttpStatusCode.Unauthorized) else respondJson(okBody)
            }
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

    @Test
    fun `sign out forgets the stored account locally and makes no server call`() = runTest {
        val stored = SignedInAccount("ann@example.com", "Ann", emptyList())
        val api = mockApi { error("sign out has no server call in the contract") }
        val store = InMemoryAccountStore(stored)
        val vm = machine(api = api, store = store)
        advanceUntilIdle()
        assertTrue(vm.state.value is AccountUiState.SignedIn)

        vm.signOut()
        advanceUntilIdle()
        assertEquals(AccountUiState.SignedOut(), vm.state.value)
        assertEquals(1, store.clears)
        assertTrue(api.requests.isEmpty())
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
