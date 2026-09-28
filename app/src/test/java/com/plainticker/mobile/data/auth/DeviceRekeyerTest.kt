package com.plainticker.mobile.data.auth

import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.expectThrows
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.prefs.AccountStore
import com.plainticker.mobile.prefs.FakePrefs
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rekey state machine (DeviceRekeyer, the pack's shared server contract items 1 and 2),
 * against a real [SharedPrefsDevicePassStore] over [FakePrefs] and a MockEngine server: success,
 * a crash or lost answer retried with the SAME pair, `already_rekeyed`, `not_legacy`, network
 * errors with backoff, and the one rekey and one retry an account call gets on `rekey_required`.
 * Throughout: the old code is never lost before a 200, and no code reaches a log line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRekeyerTest {

    private val legacy = "K7M9QRSTXY"

    private class FakeClock(var millis: Long = 1_000_000L) : Clock {
        override fun nowMillis(): Long = millis
    }

    private class RecordingLog : AccountDebugLog {
        val lines = mutableListOf<String>()
        override fun raw(line: String) {
            lines += line
        }
    }

    private class InMemoryAccountStore(initial: SignedInAccount? = null) : AccountStore {
        private val flow = MutableStateFlow(initial)
        override val account: StateFlow<SignedInAccount?> = flow
        var clears = 0
        override suspend fun save(account: SignedInAccount) {
            flow.value = account
        }
        override suspend fun clear() {
            clears++
            flow.value = null
        }
    }

    private val ann = SignedInAccount("ann@example.com", "Ann", emptyList())

    private fun legacyPrefs(): FakePrefs = FakePrefs().also {
        it.edit().putString(SharedPrefsDevicePassStore.KEY_CODE, legacy).commit()
    }

    private class Rig(
        val prefs: FakePrefs,
        val store: SharedPrefsDevicePassStore,
        val api: MockApi,
        val rekeyer: DeviceRekeyer,
        val clock: FakeClock,
        val log: RecordingLog,
    )

    private fun TestScope.rig(
        prefs: FakePrefs = legacyPrefs(),
        clock: FakeClock = FakeClock(),
        account: AccountStore? = null,
        cipher: com.plainticker.mobile.wallet.AesGcmSessionCipher? = null,
        handler: MockRequestHandler,
    ): Rig {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = MockApi(dispatcher, handler)
        val store = SharedPrefsDevicePassStore(prefs, cipher)
        val log = RecordingLog()
        return Rig(prefs, store, api, DeviceRekeyer(store, DeviceRekeyApi(api.client), clock, log, dispatcher, account), clock, log)
    }

    private suspend fun MockApi.sentNewCodes(): List<String> = requests
        .filter { it.url.encodedPath.endsWith("/api/v1/device/rekey") }
        .map { Json.parseToJsonElement(it.bodyText()).jsonObject.getValue("new_code").jsonPrimitive.content }

    private suspend fun newCodeOf(request: io.ktor.client.request.HttpRequestData): String =
        Json.parseToJsonElement(request.bodyText()).jsonObject.getValue("new_code").jsonPrimitive.content

    private fun Rig.assertNoCodeLogged(vararg codes: String) {
        codes.forEach { code -> log.lines.forEach { assertFalse("a code reached the log: $it", code in it) } }
    }

    // ---- The code sealed at rest (security review, 2026-09-27) --------------------------------

    private val sealKey: javax.crypto.SecretKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun sealing(key: javax.crypto.SecretKey = sealKey) = com.plainticker.mobile.wallet.AesGcmSessionCipher { key }

    @Test
    fun `on a sealing store a legacy code is migrated, rekeyed, and the new code lands sealed, never plain`() = runTest {
        val r = rig(cipher = sealing()) { respondJson("""{"ok":true}""") }
        assertEquals(RekeyOutcome.REKEYED, r.rekeyer.rekeyIfNeeded())
        val sent = r.api.sentNewCodes().single()
        assertEquals(legacy, r.api.lastRequest.headers["X-PT-Code"])
        assertEquals(sent, r.store.code())
        assertEquals(setOf(SharedPrefsDevicePassStore.KEY_CODE_SEALED), r.prefs.all.keys)
        assertFalse(r.prefs.all.values.any { it == sent || it == legacy })
        assertEquals(sent, SharedPrefsDevicePassStore(r.prefs, sealing()).code())
        r.assertNoCodeLogged(legacy, sent)
    }

    @Test
    fun `a sealed code this phone cannot open sends nothing, mints nothing, and You says so`() = runTest {
        val prefs = FakePrefs()
        // Sealed under the right key; this launch's Keystore answers with another.
        SharedPrefsDevicePassStore(prefs, sealing()).also { it.code() }
        prefs.edit().putString(
            SharedPrefsDevicePassStore.KEY_CODE_SEALED,
            java.util.Base64.getEncoder().encodeToString(sealing().seal(legacy.toByteArray())),
        ).commit()
        prefs.writes.clear()
        val otherKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val r = rig(prefs = prefs, cipher = sealing(otherKey)) { error("nothing may be sent without the code") }

        assertEquals(RekeyOutcome.RETRY_LATER, r.rekeyer.rekeyIfNeeded(force = true))
        assertEquals(DeviceCodeStatus.UNREADABLE, r.rekeyer.status.value)
        assertTrue("no request was made", r.api.requests.isEmpty())
        assertTrue("nothing was written: no code minted over the sealed one", prefs.writes.isEmpty())
        assertNull(r.store.codeOrNull())
        expectThrows<com.plainticker.mobile.prefs.DeviceCodeUnreadableException> {
            r.rekeyer.withCode({ false }) { error("the block never runs without a code") }
        }
        assertTrue(r.api.requests.isEmpty())

        // The right key answers again on a later launch: the same legacy code, rekeyed as usual.
        val later = rig(prefs = prefs, cipher = sealing()) { respondJson("""{"ok":true}""") }
        assertEquals(RekeyOutcome.REKEYED, later.rekeyer.rekeyIfNeeded(force = true))
        assertEquals(legacy, later.api.lastRequest.headers["X-PT-Code"])
        assertEquals(DeviceCodeStatus.OK, later.rekeyer.status.value)
    }

    // ---- Success -----------------------------------------------------------------------------

    @Test
    fun `a 200 makes the persisted new code current, with the old code in the header and the new one in the body`() = runTest {
        val r = rig { respondJson("""{"ok":true}""") }
        assertEquals(RekeyOutcome.REKEYED, r.rekeyer.rekeyIfNeeded())

        val request = r.api.lastRequest
        assertEquals("/api/v1/device/rekey", request.url.encodedPath)
        assertEquals("POST", request.method.value)
        assertEquals(legacy, request.headers["X-PT-Code"])
        assertFalse("no code in the URL", legacy in request.url.toString())
        val sent = r.api.sentNewCodes().single()
        assertTrue(SharedPrefsDevicePassStore.isNewFormat(sent))
        assertFalse("no code in the URL", sent in request.url.toString())

        assertEquals(sent, r.store.code())
        assertFalse(r.store.isLegacy())
        assertNull(r.store.pendingNewCode())
        assertEquals(sent, SharedPrefsDevicePassStore(r.prefs).code())
        assertEquals(DeviceCodeStatus.OK, r.rekeyer.status.value)
        r.assertNoCodeLogged(legacy, sent)
    }

    @Test
    fun `a 200 on a signed-in phone clears the account, since the new code is not bound to it, and asks for one sign-in`() = runTest {
        val prefs = legacyPrefs()
        val account = InMemoryAccountStore(ann)
        val r = rig(prefs = prefs, account = account) { respondJson("""{"ok":true}""") }
        assertEquals(RekeyOutcome.REKEYED, r.rekeyer.rekeyIfNeeded())
        assertFalse(r.store.isLegacy())
        assertNull(account.account.value)
        assertEquals(1, account.clears)
        assertEquals(DeviceCodeStatus.SIGN_IN_AGAIN, r.rekeyer.status.value)

        // The line survives a restart, and goes once a Google sign-in succeeds.
        val cold = rig(prefs = prefs, account = account) { error("a current code never rekeys") }
        cold.rekeyer.runOnLaunch()
        assertEquals(DeviceCodeStatus.SIGN_IN_AGAIN, cold.rekeyer.status.value)
        cold.rekeyer.signedInAgain()
        assertEquals(DeviceCodeStatus.OK, cold.rekeyer.status.value)
        assertNull(cold.store.rekeyNote())
    }

    @Test
    fun `a 200 on a phone with no account says nothing and clears nothing`() = runTest {
        val account = InMemoryAccountStore(null)
        val r = rig(account = account) { respondJson("""{"ok":true}""") }
        assertEquals(RekeyOutcome.REKEYED, r.rekeyer.rekeyIfNeeded())
        assertEquals(0, account.clears)
        assertEquals(DeviceCodeStatus.OK, r.rekeyer.status.value)
        assertNull(r.store.rekeyNote())
    }

    @Test
    fun `a current-format code sends nothing`() = runTest {
        val r = rig(prefs = FakePrefs()) { error("a 26-symbol code must never be rekeyed") }
        val code = r.store.code()
        assertEquals(RekeyOutcome.NOT_NEEDED, r.rekeyer.rekeyIfNeeded(force = true))
        assertTrue(r.api.requests.isEmpty())
        assertEquals(code, r.store.code())
    }

    // ---- Crash and lost answers: the same pair, every time ----------------------------------

    @Test
    fun `a lost answer is retried with the very same new code, and the legacy code is kept until the 200`() = runTest {
        val prefs = legacyPrefs()
        // The server takes the pair, but the answer never arrives: to this device, a network error.
        // (A MockEngine request whose handler throws is not kept in its history, so this one
        // records what it was sent itself.)
        val heard = mutableListOf<String>()
        val first = rig(prefs = prefs) { request ->
            heard += newCodeOf(request)
            throw IOException("connection reset")
        }
        assertEquals(RekeyOutcome.RETRY_LATER, first.rekeyer.rekeyIfNeeded())
        val pending = first.store.pendingNewCode()!!
        assertEquals(listOf(pending), heard)
        assertEquals("the old code is not lost", legacy, first.store.code())

        // A cold start (process death) over the same file: a new store, a new rekeyer.
        val second = rig(prefs = prefs) { respondJson("""{"ok":true}""") }
        assertEquals(RekeyOutcome.REKEYED, second.rekeyer.rekeyIfNeeded())
        assertEquals("the same pair is sent again", listOf(pending), second.api.sentNewCodes())
        assertEquals(pending, second.store.code())
    }

    @Test
    fun `a 200 whose body is not ok true is not a confirmation`() = runTest {
        val r = rig { respondJson("""{"status":"accepted"}""") }
        assertEquals(RekeyOutcome.RETRY_LATER, r.rekeyer.rekeyIfNeeded())
        assertEquals(legacy, r.store.code())
        assertTrue(r.store.pendingNewCode() != null)
    }

    @Test
    fun `nothing is sent when the replacement could not be written first`() = runTest {
        val prefs = legacyPrefs().also { it.failCommits = true }
        val r = rig(prefs = prefs) { error("nothing may be sent before the new code is on disk") }
        assertEquals(RekeyOutcome.RETRY_LATER, r.rekeyer.rekeyIfNeeded())
        assertTrue(r.api.requests.isEmpty())
        assertEquals(legacy, r.store.code())
    }

    // ---- Refusals ----------------------------------------------------------------------------

    @Test
    fun `already_rekeyed adopts the fresh code, drops the legacy one, never sends again, and You is told`() = runTest {
        val prefs = legacyPrefs()
        val account = InMemoryAccountStore(ann)
        val heard = mutableListOf<String>()
        val r = rig(prefs = prefs, account = account) { request ->
            heard += newCodeOf(request)
            respondJson("""{"error":"already_rekeyed"}""", HttpStatusCode.Conflict)
        }
        assertEquals(RekeyOutcome.REPLACED, r.rekeyer.rekeyIfNeeded())
        val adopted = heard.single()
        assertEquals("the pending code is now current", adopted, r.store.code())
        assertFalse(r.store.isLegacy())
        assertNull(r.store.pendingNewCode())
        assertEquals(DeviceCodeStatus.REPLACED, r.rekeyer.status.value)
        assertEquals("the fresh code is bound to no account", 1, account.clears)

        assertEquals(RekeyOutcome.NOT_NEEDED, r.rekeyer.rekeyIfNeeded(force = true))
        assertEquals("no second attempt", 1, heard.size)

        // After a restart it still says so, and a sign-in does not take the line away.
        val cold = rig(prefs = prefs) { error("an adopted code is never rekeyed") }
        cold.rekeyer.runOnLaunch()
        assertEquals(DeviceCodeStatus.REPLACED, cold.rekeyer.status.value)
        assertEquals(adopted, cold.store.code())
        cold.rekeyer.signedInAgain()
        assertEquals(DeviceCodeStatus.REPLACED, cold.rekeyer.status.value)
        r.assertNoCodeLogged(legacy, adopted)
    }

    @Test
    fun `new_code_in_use, code_retired and legacy_sunset adopt the fresh code the same way`() = runTest {
        listOf(409 to "new_code_in_use", 401 to "code_retired", 400 to "legacy_sunset").forEach { (status, code) ->
            val r = rig { respondJson("""{"error":"$code"}""", HttpStatusCode.fromValue(status)) }
            assertEquals(code, RekeyOutcome.REPLACED, r.rekeyer.rekeyIfNeeded())
            assertFalse(code, r.store.isLegacy())
            assertTrue(code, SharedPrefsDevicePassStore.isNewFormat(r.store.code()))
            assertEquals(code, SharedPrefsDevicePassStore.NOTE_REPLACED, r.store.rekeyNote())
            assertEquals(code, DeviceCodeStatus.REPLACED, r.rekeyer.status.value)
        }
    }

    @Test
    fun `not_legacy drops the pending code and keeps the current one`() = runTest {
        val r = rig { respondJson("""{"error":"not_legacy"}""", HttpStatusCode.BadRequest) }
        assertEquals(RekeyOutcome.NOT_LEGACY, r.rekeyer.rekeyIfNeeded())
        assertNull(r.store.pendingNewCode())
        assertEquals(legacy, r.store.code())
        assertNull(r.store.rekeyNote())
    }

    @Test
    fun `an unknown 409, invalid_new_code, a 429, a 404 and a 5xx are all retried later, the pending code kept`() = runTest {
        listOf(
            409 to """{"error":"something_else"}""",
            400 to """{"error":"invalid_new_code"}""",
            429 to """{"error":"rate_limited"}""",
            404 to "",
            503 to "<html>down</html>",
        ).forEach { (status, body) ->
            val r = rig { respondJson(body, HttpStatusCode.fromValue(status)) }
            assertEquals("$status $body", RekeyOutcome.RETRY_LATER, r.rekeyer.rekeyIfNeeded())
            assertEquals(legacy, r.store.code())
            assertTrue(r.store.pendingNewCode() != null)
            assertNull(r.store.rekeyNote())
        }
    }

    // ---- Network errors and backoff ----------------------------------------------------------

    @Test
    fun `after a network error a preflight waits out the backoff, and a forced attempt does not`() = runTest {
        var calls = 0
        val r = rig {
            calls++
            throw IOException("offline")
        }
        assertEquals(RekeyOutcome.RETRY_LATER, r.rekeyer.rekeyIfNeeded())
        assertEquals(RekeyOutcome.RETRY_LATER, r.rekeyer.rekeyIfNeeded(force = false))
        assertEquals("inside the backoff window nothing is sent", 1, calls)
        r.clock.millis += DeviceRekeyer.backoffMillis(1)
        r.rekeyer.rekeyIfNeeded(force = false)
        assertEquals(2, calls)
        r.rekeyer.rekeyIfNeeded(force = true)
        assertEquals(3, calls)
    }

    @Test
    fun `on launch it retries with backoff while the process lives, then lands`() = runTest {
        var calls = 0
        val r = rig {
            calls++
            if (calls < 3) throw IOException("offline") else respondJson("""{"ok":true}""")
        }
        val job = launch { r.rekeyer.runOnLaunch() }
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(DeviceRekeyer.BACKOFF_MILLIS[0] + 1)
        assertEquals(2, calls)
        advanceTimeBy(DeviceRekeyer.BACKOFF_MILLIS[1] + 1)
        assertEquals(3, calls)
        assertTrue(job.isCompleted)
        assertFalse(r.store.isLegacy())
        assertEquals("one pending code across every attempt", 1, r.api.sentNewCodes().toSet().size)
    }

    @Test
    fun `on launch it gives up after the last backoff and leaves the legacy code for the next launch`() = runTest {
        val heard = mutableListOf<String>()
        val r = rig { request ->
            heard += newCodeOf(request)
            throw IOException("offline")
        }
        r.rekeyer.runOnLaunch()
        assertEquals(1 + DeviceRekeyer.BACKOFF_MILLIS.size, heard.size)
        assertEquals(legacy, r.store.code())
        assertEquals("one pending code across every attempt", 1, heard.toSet().size)
    }

    // ---- withCode: an account call on rekey_required -----------------------------------------

    private class RekeyRequired : IOException("rekey_required")

    @Test
    fun `an account call from a legacy device rekeys first, then runs once with the new code`() = runTest {
        val r = rig { respondJson("""{"ok":true}""") }
        val seen = mutableListOf<String>()
        val result = r.rekeyer.withCode({ it is RekeyRequired }) { code -> seen += code; "done" }
        assertEquals("done", result)
        assertEquals(listOf(r.store.code()), seen)
        assertFalse(r.store.isLegacy())
    }

    @Test
    fun `rekey_required runs the rekey once and the call once more with the new code`() = runTest {
        var rekeyCalls = 0
        // The preflight is offline; the rekey forced by rekey_required lands.
        val r = rig {
            rekeyCalls++
            if (rekeyCalls == 1) throw IOException("offline") else respondJson("""{"ok":true}""")
        }
        val seen = mutableListOf<String>()
        val result = r.rekeyer.withCode({ it is RekeyRequired }) { code ->
            seen += code
            if (code == legacy) throw RekeyRequired() else "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, rekeyCalls)
        assertEquals(listOf(legacy, r.store.code()), seen)
    }

    @Test
    fun `when the rekey cannot land, rekey_required is rethrown after one call, not retried with the same code`() = runTest {
        val r = rig { throw IOException("offline") }
        var calls = 0
        expectThrows<RekeyRequired> {
            r.rekeyer.withCode({ it is RekeyRequired }) { _ ->
                calls++
                throw RekeyRequired()
            }
        }
        assertEquals(1, calls)
        assertEquals(legacy, r.store.code())
    }

    @Test
    fun `any other failure passes straight through, with no extra rekey`() = runTest {
        val r = rig(prefs = FakePrefs()) { error("a current code never rekeys") }
        expectThrows<IllegalStateException> {
            r.rekeyer.withCode({ it is RekeyRequired }) { _ -> throw IllegalStateException("boom") }
        }
        assertTrue(r.api.requests.isEmpty())
    }

    @Test
    fun `markRetired shows the retired line unless a replaced code already says more`() = runTest {
        val r = rig(prefs = FakePrefs()) { error("unused") }
        r.rekeyer.markRetired()
        assertEquals(DeviceCodeStatus.RETIRED, r.rekeyer.status.value)

        val replaced = rig { respondJson("""{"error":"already_rekeyed"}""", HttpStatusCode.Conflict) }
        replaced.rekeyer.rekeyIfNeeded()
        replaced.rekeyer.markRetired()
        assertEquals(DeviceCodeStatus.REPLACED, replaced.rekeyer.status.value)
    }

    // ---- Error mapping ------------------------------------------------------------------------

    @Test
    fun `every contract error code maps to its own case, and only known codes are terminal`() {
        val json = Json
        fun map(status: Int, code: String?) =
            DeviceRekeyError.fromErrorBody(status, code?.let { """{"error":"$it"}""" }, json)
        assertTrue(map(400, "invalid_new_code") is DeviceRekeyError.InvalidNewCode)
        assertTrue(map(400, "not_legacy") is DeviceRekeyError.NotLegacy)
        assertTrue(map(409, "already_rekeyed") is DeviceRekeyError.AlreadyRekeyed)
        assertTrue(map(409, "new_code_in_use") is DeviceRekeyError.NewCodeInUse)
        assertTrue(map(401, "code_retired") is DeviceRekeyError.CodeRetired)
        assertTrue(map(429, "rate_limited") is DeviceRekeyError.RateLimited)
        assertTrue(map(400, "legacy_sunset") is DeviceRekeyError.LegacySunset)
        assertTrue(map(429, null) is DeviceRekeyError.RateLimited)
        assertTrue(map(404, null) is DeviceRekeyError.NotOpen)
        assertTrue(map(409, null) is DeviceRekeyError.Unavailable)
        assertTrue(map(401, "who_knows") is DeviceRekeyError.Unavailable)
        assertTrue(map(500, null) is DeviceRekeyError.Unavailable)
    }

    // ---- A key that is gone for good (security review M3) --------------------------------------

    @Test
    fun `a code whose key is gone says so, retries nothing, and starts over only when asked`() = runTest {
        val prefs = FakePrefs()
        prefs.edit().putString(
            SharedPrefsDevicePassStore.KEY_CODE_SEALED,
            java.util.Base64.getEncoder().encodeToString(sealing().seal(legacy.toByteArray())),
        ).commit()
        val newKey = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val api = MockApi(dispatcher) { error("nothing may be sent without the code") }
        val store = SharedPrefsDevicePassStore(prefs, sealing(newKey)) { true }
        val account = InMemoryAccountStore(ann)
        val rekeyer = DeviceRekeyer(store, DeviceRekeyApi(api.client), FakeClock(), RecordingLog(), dispatcher, account)

        assertEquals(RekeyOutcome.NOT_NEEDED, rekeyer.rekeyIfNeeded(force = true))
        assertEquals(DeviceCodeStatus.LOST, rekeyer.status.value)
        assertTrue(api.requests.isEmpty())
        assertTrue("never started over on its own", store.codeLost())

        assertTrue(rekeyer.startWithNewCode())
        assertEquals(DeviceCodeStatus.OK, rekeyer.status.value)
        assertTrue(SharedPrefsDevicePassStore.isNewFormat(store.code()))
        assertEquals("the new code is bound to no account, so the one shown is cleared", 1, account.clears)
        assertFalse("done once, it is not offered again", rekeyer.startWithNewCode())
    }
}
