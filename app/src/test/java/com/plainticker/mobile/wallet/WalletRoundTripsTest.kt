package com.plainticker.mobile.wallet

import com.plainticker.mobile.lock.AppLock
import com.plainticker.mobile.lock.AuthPurpose
import com.plainticker.mobile.lock.AuthResult
import com.plainticker.mobile.lock.Authenticator
import com.plainticker.mobile.lock.LockAvailability
import com.plainticker.mobile.prefs.AppLockStore
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The holder counts wallet requests in flight, and the app lock reads that count, so a real round
 * trip through [WalletSessionHolder] (not a flag set by hand) is what keeps a return from the Seed
 * Vault from locking the app.
 */
class WalletRoundTripsTest {

    private class Slot(override var authToken: String? = null) : AuthTokenSlot

    /** A wallet that answers when the test opens [gate]. */
    private class GatedTransport : MwaTransport {
        val gate = CompletableDeferred<Unit>()

        override suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> {
            gate.await()
            return TransactionResult.NoWalletFound("No compatible wallet found.")
        }

        override suspend fun disconnect(): TransactionResult<Unit> {
            gate.await()
            return TransactionResult.NoWalletFound("No compatible wallet found.")
        }
    }

    private class OnStore(var on: Boolean = true) : AppLockStore {
        override fun isEnabled(): Boolean = on
        override fun setEnabled(value: Boolean) {
            on = value
        }
    }

    @Test
    fun `a request in flight counts as a round trip until the wallet answers`() = runTest {
        val transport = GatedTransport()
        val holder = WalletSessionHolder(Slot()) { transport }.also { it.attach(transport, MutableStateFlow(true)) }
        assertEquals(0, holder.roundTrips.value)

        val connect = async { holder.connect() }
        runCurrent()
        assertEquals(1, holder.roundTrips.value)

        transport.gate.complete(Unit)
        runCurrent()
        assertTrue(connect.isCompleted)
        assertEquals(0, holder.roundTrips.value)
    }

    @Test
    fun `a request with no screen to hand off to is not left counted`() = runTest {
        val holder = WalletSessionHolder(Slot()) { GatedTransport() }
        assertTrue(holder.connect() is WalletOutcome.Error)
        assertEquals(0, holder.roundTrips.value)
    }

    @Test
    fun `twelve minutes in the Seed Vault through the holder does not lock the app`() = runTest {
        var clock = 1_000_000L
        val transport = GatedTransport()
        val holder = WalletSessionHolder(Slot()) { transport }.also { it.attach(transport, MutableStateFlow(true)) }
        val lock = AppLock(
            store = OnStore(),
            availability = { LockAvailability.BIOMETRIC },
            scope = backgroundScope,
            now = { clock },
            walletBusy = holder.roundTrips.map { it > 0 }.distinctUntilChanged(),
        )
        lock.bind(
            object : Authenticator {
                override suspend fun authenticate(purpose: AuthPurpose, availability: LockAvailability) = AuthResult.Success
            },
        )
        lock.open()
        runCurrent()
        assertFalse(lock.state.value.locked)

        val swap = async { holder.call { } }
        runCurrent()
        lock.leftApp() // the Seed Vault is on screen
        clock += 12 * 60_000L
        transport.gate.complete(Unit) // the wallet answers
        runCurrent()
        clock += 10_000L
        lock.returned()
        assertTrue(swap.isCompleted)
        assertFalse(lock.state.value.locked)

        // The same absence with no wallet involved locks it.
        lock.leftApp()
        clock += 12 * 60_000L
        lock.returned()
        assertTrue(lock.state.value.locked)
    }
}
