package com.plainticker.mobile.lock

import com.plainticker.mobile.prefs.AppLockStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optional app lock's rules, against a fake prompt and an injected clock: the setting moves
 * only after a prompt succeeds, a cold start opens on the lock screen, five minutes away locks
 * again, a wallet round trip never does, and nothing ever prompts in a loop.
 *
 * Every coroutine here runs on the test scheduler and is driven with runCurrent; no flow ticks.
 */
class AppLockTest {

    private class MemoryStore(var on: Boolean = false) : AppLockStore {
        var writes = 0
        override fun isEnabled(): Boolean = on
        override fun setEnabled(value: Boolean) {
            on = value
            writes++
        }
    }

    /** A prompt that answers when the test says so, and records what it was asked for. */
    private class FakePrompt : Authenticator {
        val asked = mutableListOf<AuthPurpose>()
        private var answer: CompletableDeferred<AuthResult>? = null

        override suspend fun authenticate(purpose: AuthPurpose, availability: LockAvailability): AuthResult {
            asked += purpose
            return CompletableDeferred<AuthResult>().also { answer = it }.await()
        }

        fun answer(result: AuthResult) {
            answer!!.complete(result)
            answer = null
        }
    }

    private val minute = 60_000L
    private var clock = 50_000_000L
    private var available = LockAvailability.BIOMETRIC
    private val walletBusy = MutableStateFlow(false)

    private fun TestScope.lock(store: AppLockStore, scope: CoroutineScope = backgroundScope): AppLock =
        AppLock(store, { available }, scope, now = { clock }, walletBusy = walletBusy).also { runCurrent() }

    // ---- Cold start ---------------------------------------------------------------------------

    @Test
    fun `a cold start with the lock on opens on the lock screen and owes one prompt`() = runTest {
        val prompt = FakePrompt()
        val lock = lock(MemoryStore(on = true)).apply { bind(prompt) }
        assertTrue("locked from the first frame", lock.state.value.locked)
        lock.returned() // the Activity's first start
        assertTrue(lock.takeAutoPrompt())
        assertFalse("one automatic prompt, not two", lock.takeAutoPrompt())

        lock.open()
        runCurrent()
        assertEquals(listOf(AuthPurpose.OPEN), prompt.asked)
        assertTrue(lock.state.value.authenticating)
        prompt.answer(AuthResult.Success)
        runCurrent()
        assertFalse(lock.state.value.locked)
        assertFalse(lock.state.value.authenticating)
    }

    @Test
    fun `a cold start with the lock off shows the app and owes nothing`() = runTest {
        val lock = lock(MemoryStore(on = false))
        lock.returned()
        assertFalse(lock.state.value.locked)
        assertFalse(lock.takeAutoPrompt())
    }

    @Test
    fun `a cancel keeps the app locked and nothing asks again until the person does`() = runTest {
        val prompt = FakePrompt()
        val lock = lock(MemoryStore(on = true)).apply { bind(prompt) }
        lock.returned()
        assertTrue(lock.takeAutoPrompt())
        lock.open()
        runCurrent()
        prompt.answer(AuthResult.Cancelled)
        runCurrent()
        assertTrue(lock.state.value.locked)
        assertNull("a cancel says nothing", lock.state.value.message)
        assertFalse("resuming after the prompt closed does not prompt again", lock.takeAutoPrompt())

        lock.open() // the lock screen's Open
        runCurrent()
        assertEquals(2, prompt.asked.size)
        prompt.answer(AuthResult.Success)
        runCurrent()
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `a prompt the system ended with an error says why, and stays locked`() = runTest {
        val prompt = FakePrompt()
        val lock = lock(MemoryStore(on = true)).apply { bind(prompt) }
        lock.open()
        runCurrent()
        prompt.answer(AuthResult.Failed("Too many attempts."))
        runCurrent()
        assertTrue(lock.state.value.locked)
        assertEquals("Too many attempts.", lock.state.value.message)
    }

    @Test
    fun `two taps in one frame show one prompt`() = runTest {
        val prompt = FakePrompt()
        val lock = lock(MemoryStore(on = true)).apply { bind(prompt) }
        lock.open()
        lock.open()
        runCurrent()
        assertEquals(1, prompt.asked.size)
        prompt.answer(AuthResult.Success)
        runCurrent()
    }

    @Test
    fun `with no screen bound nothing opens and nothing hangs`() = runTest {
        val lock = lock(MemoryStore(on = true))
        lock.open()
        runCurrent()
        assertTrue(lock.state.value.locked)
        assertFalse(lock.state.value.authenticating)
    }

    // ---- The setting --------------------------------------------------------------------------

    @Test
    fun `turning the lock on asks first, and a cancel leaves it off`() = runTest {
        val store = MemoryStore()
        val prompt = FakePrompt()
        val lock = lock(store).apply { bind(prompt) }

        lock.setEnabled(true)
        runCurrent()
        assertEquals(listOf(AuthPurpose.TURN_ON), prompt.asked)
        assertFalse("the switch waits for the prompt", lock.state.value.enabled)
        prompt.answer(AuthResult.Cancelled)
        runCurrent()
        assertFalse(lock.state.value.enabled)
        assertFalse(store.on)
        assertEquals(0, store.writes)

        lock.setEnabled(true)
        runCurrent()
        prompt.answer(AuthResult.Success)
        runCurrent()
        assertTrue(lock.state.value.enabled)
        assertTrue(store.on)
        assertFalse("turning it on does not lock the screen it was turned on from", lock.state.value.locked)
    }

    @Test
    fun `turning the lock off asks too`() = runTest {
        val store = MemoryStore(on = true)
        val prompt = FakePrompt()
        val lock = lock(store).apply { bind(prompt) }
        lock.open()
        runCurrent()
        prompt.answer(AuthResult.Success)
        runCurrent()

        lock.setEnabled(false)
        runCurrent()
        assertEquals(AuthPurpose.TURN_OFF, prompt.asked.last())
        prompt.answer(AuthResult.Cancelled)
        runCurrent()
        assertTrue("a cancel keeps it on", store.on)

        lock.setEnabled(false)
        runCurrent()
        prompt.answer(AuthResult.Success)
        runCurrent()
        assertFalse(store.on)
        assertFalse(lock.state.value.enabled)
    }

    @Test
    fun `the switch asks nothing when there is nothing to change or nothing to confirm with`() = runTest {
        val prompt = FakePrompt()
        val lock = lock(MemoryStore()).apply { bind(prompt) }
        lock.setEnabled(false)
        runCurrent()
        assertTrue("already off", prompt.asked.isEmpty())

        available = LockAvailability.NONE
        lock.returned()
        lock.setEnabled(true)
        runCurrent()
        assertTrue("no fingerprint and no screen lock: the lock cannot be turned on", prompt.asked.isEmpty())
        assertEquals(LockAvailability.NONE, lock.state.value.availability)
    }

    // ---- Availability -------------------------------------------------------------------------

    @Test
    fun `availability reads a fingerprint first, then a screen lock, then nothing`() {
        assertEquals(LockAvailability.BIOMETRIC, LockAvailability.of(strongBiometric = true, deviceCredential = true))
        assertEquals(LockAvailability.SCREEN_LOCK, LockAvailability.of(strongBiometric = false, deviceCredential = true))
        assertEquals(LockAvailability.NONE, LockAvailability.of(strongBiometric = false, deviceCredential = false))
        assertTrue(LockAvailability.BIOMETRIC.canLock)
        assertTrue(LockAvailability.SCREEN_LOCK.canLock)
        assertFalse(LockAvailability.NONE.canLock)
    }

    @Test
    fun `a phone that lost its screen lock turns the setting off rather than locking the person out`() = runTest {
        available = LockAvailability.NONE
        val store = MemoryStore(on = true)
        val cold = lock(store)
        assertFalse("a cold start with nothing to confirm with opens", cold.state.value.locked)
        assertFalse(cold.state.value.enabled)
        assertFalse(store.on)

        available = LockAvailability.BIOMETRIC
        store.on = true
        val running = lock(store)
        val prompt = FakePrompt()
        running.bind(prompt)
        running.open()
        runCurrent()
        prompt.answer(AuthResult.Success)
        runCurrent()
        running.leftApp()
        available = LockAvailability.NONE // the screen lock was removed while away
        clock += 30 * minute
        running.returned()
        assertFalse(running.state.value.locked)
        assertFalse(running.state.value.enabled)
        assertFalse(store.on)
    }

    // ---- Coming back --------------------------------------------------------------------------

    private fun TestScope.openedLock(prompt: FakePrompt): AppLock {
        val lock = lock(MemoryStore(on = true)).apply { bind(prompt) }
        lock.returned()
        lock.takeAutoPrompt()
        lock.open()
        runCurrent()
        prompt.answer(AuthResult.Success)
        runCurrent()
        assertFalse(lock.state.value.locked)
        return lock
    }

    @Test
    fun `five minutes away locks again and owes one prompt, less does not`() = runTest {
        val prompt = FakePrompt()
        val lock = openedLock(prompt)

        lock.leftApp()
        clock += 5 * minute - 1_000L
        lock.returned()
        assertFalse(lock.state.value.locked)
        assertFalse(lock.takeAutoPrompt())

        lock.leftApp()
        clock += 5 * minute
        lock.returned()
        assertTrue(lock.state.value.locked)
        assertTrue(lock.takeAutoPrompt())
        assertFalse(lock.takeAutoPrompt())
    }

    @Test
    fun `a return to a screen still locked owes one prompt, a return from the prompt's own screen lock does not`() = runTest {
        val prompt = FakePrompt()
        val lock = lock(MemoryStore(on = true)).apply { bind(prompt) }
        lock.returned()
        lock.takeAutoPrompt()
        lock.open()
        runCurrent()
        // Below Android 11 the screen lock is an Activity: the app stops for it.
        lock.leftApp(forPrompt = true)
        lock.returned()
        assertFalse("still authenticating: no second prompt", lock.takeAutoPrompt())
        prompt.answer(AuthResult.Cancelled)
        runCurrent()
        assertFalse("the return was the prompt's own", lock.takeAutoPrompt())

        // The person goes home and comes back to the lock screen: one prompt for that return.
        lock.leftApp()
        clock += minute
        lock.returned()
        assertTrue(lock.takeAutoPrompt())
    }

    @Test
    fun `the lock's own prompt is not time away`() = runTest {
        val prompt = FakePrompt()
        val lock = openedLock(prompt)
        lock.setEnabled(false) // asks; below Android 11 the screen lock covers the app
        runCurrent()
        lock.leftApp(forPrompt = true)
        clock += 20 * minute
        lock.returned()
        prompt.answer(AuthResult.Cancelled)
        runCurrent()
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `a new lock screen does not carry the last prompt's error`() = runTest {
        val prompt = FakePrompt()
        val lock = openedLock(prompt)
        lock.setEnabled(false)
        runCurrent()
        prompt.answer(AuthResult.Failed("Sensor busy."))
        runCurrent()
        assertEquals("Sensor busy.", lock.state.value.message)
        lock.leftApp()
        clock += 6 * minute
        lock.returned()
        assertTrue(lock.state.value.locked)
        assertNull(lock.state.value.message)
    }

    // ---- The wallet round trip ----------------------------------------------------------------

    @Test
    fun `back from the Seed Vault does not lock, however long the wallet was on screen`() = runTest {
        val prompt = FakePrompt()
        val lock = openedLock(prompt)

        walletBusy.value = true // the swap asks the wallet to sign
        runCurrent()
        lock.leftApp() // the Seed Vault covers the app
        clock += 12 * minute
        walletBusy.value = false // the wallet answered while still on screen
        runCurrent()
        clock += 20_000L
        lock.returned()
        assertFalse(lock.state.value.locked)
        assertFalse(lock.takeAutoPrompt())
    }

    @Test
    fun `back from the wallet inside five minutes does not lock, the order of answer and return aside`() = runTest {
        val prompt = FakePrompt()
        val lock = openedLock(prompt)

        walletBusy.value = true
        runCurrent()
        lock.leftApp()
        clock += 4 * minute
        lock.returned() // back in the app before the round trip ends
        walletBusy.value = false
        runCurrent()
        assertFalse(lock.state.value.locked)
    }

    @Test
    fun `away after the wallet answered counts from the answer`() = runTest {
        val prompt = FakePrompt()
        val lock = openedLock(prompt)

        walletBusy.value = true
        runCurrent()
        lock.leftApp()
        clock += 2 * minute
        walletBusy.value = false
        runCurrent()
        clock += 5 * minute // the person left for another app after the wallet answered
        lock.returned()
        assertTrue(lock.state.value.locked)
    }
}
