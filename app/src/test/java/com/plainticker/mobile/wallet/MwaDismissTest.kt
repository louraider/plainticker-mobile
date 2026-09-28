package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeoutException

/**
 * QA of 1.3.19: back from the wallet's Connect sheet left the swap sheet on "Reading the wallet"
 * past 87 s, because clientlib-ktx 2.2.0 drops its own RESULT_CANCELED signal and waits out its
 * timeouts. A request the person walked away from now ends as [WalletOutcome.Cancelled] once the
 * app is back in front; one that already asked the wallet to sign is never abandoned.
 */
class MwaDismissTest {

    private val account = testAccount(fill = 4, label = "Seeker")

    private class Slot(override var authToken: String? = null) : AuthTokenSlot

    private class MemoryStore : WalletSessionStore {
        var saves = 0
        var clears = 0
        override suspend fun load(): SavedWalletSession? = null
        override suspend fun save(session: SavedWalletSession) {
            saves++
        }
        override suspend fun clear() {
            clears++
        }
    }

    /**
     * A wallet that answers only when the test says so. [authorized] completes the authorization
     * step (the block then runs); [answer] is what the association ends with, whatever the block did.
     */
    private class HeldTransport(private val slot: Slot) : MwaTransport {
        val authorized = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<TransactionResult<Unit>>()
        val blockEntered = CompletableDeferred<Unit>()
        var disconnectAnswer = CompletableDeferred<TransactionResult<Unit>>()
        var transacts = 0

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> {
            transacts++
            authorized.await()
            blockEntered.complete(Unit)
            val value = block(FakeAdapterOperations())
            return when (val end = answer.await()) {
                is TransactionResult.Success -> {
                    slot.authToken = "token-late"
                    TransactionResult.Success(value, end.authResult)
                }
                else -> end as TransactionResult<T>
            }
        }

        override suspend fun disconnect(): TransactionResult<Unit> = disconnectAnswer.await()
    }

    private fun success(): TransactionResult<Unit> = TransactionResult.Success(
        Unit,
        MobileWalletAdapterClient.AuthorizationResult.create("token-late", account.publicKey, account.label, null),
    )

    @Test
    fun `back from the wallet's Connect sheet ends the connect as a cancel, and saves nothing`() = runTest {
        val slot = Slot()
        val store = MemoryStore()
        val inFront = MutableStateFlow(true)
        val transport = HeldTransport(slot)
        val holder = WalletSessionHolder(slot, store) { transport }.also { it.attach(transport, inFront, 3_000L) }

        val connect = async { holder.connect() }
        runCurrent()
        inFront.value = false // the wallet's sheet is up
        runCurrent()
        advanceTimeBy(20_000L)
        assertFalse("waiting on the wallet is not a dismissal", connect.isCompleted)

        inFront.value = true // back pressed: the app is in front, the wallet never answered
        advanceTimeBy(2_000L)
        runCurrent()
        assertFalse("a short grace for the adapter's own close", connect.isCompleted)
        advanceTimeBy(1_500L)
        runCurrent()
        assertTrue(connect.isCompleted)
        assertSame(WalletOutcome.Cancelled, connect.await())

        // The abandoned association answering later changes nothing on this phone.
        transport.authorized.complete(Unit)
        transport.answer.complete(success())
        runCurrent()
        assertNull(holder.account.value)
        assertEquals(0, store.saves)
        assertEquals(0, store.clears)
    }

    @Test
    fun `a second connect after a dismissed one is not refused as still pending`() = runTest {
        val slot = Slot()
        val inFront = MutableStateFlow(true)
        val first = HeldTransport(slot)
        val holder = WalletSessionHolder(slot, MemoryStore()) { first }.also { it.attach(first, inFront, 3_000L) }

        val connect = async { holder.connect() }
        runCurrent()
        inFront.value = false
        runCurrent()
        inFront.value = true
        advanceTimeBy(3_500L)
        runCurrent()
        assertSame(WalletOutcome.Cancelled, connect.await())

        // Connect again: a fresh association that the wallet approves.
        val second = HeldTransport(slot)
        holder.attach(second, inFront, 3_000L)
        val again = async { holder.connect() }
        runCurrent()
        second.authorized.complete(Unit)
        second.answer.complete(success())
        runCurrent()
        assertEquals(WalletOutcome.Success(account), again.await())
        assertEquals(account, holder.account.value)
    }

    @Test
    fun `a wallet that approves before the app is back in front connects as always`() = runTest {
        val slot = Slot()
        val inFront = MutableStateFlow(true)
        val transport = HeldTransport(slot)
        val holder = WalletSessionHolder(slot, MemoryStore()) { transport }.also { it.attach(transport, inFront, 3_000L) }

        val connect = async { holder.connect() }
        runCurrent()
        inFront.value = false
        transport.authorized.complete(Unit)
        transport.answer.complete(success())
        runCurrent()
        inFront.value = true
        runCurrent()
        assertEquals(WalletOutcome.Success(account), connect.await())
    }

    @Test
    fun `a request that already asked for a signature is never abandoned on return`() = runTest {
        val slot = Slot()
        val inFront = MutableStateFlow(true)
        val transport = HeldTransport(slot)
        val holder = WalletSessionHolder(slot, MemoryStore()) { transport }.also { it.attach(transport, inFront, 3_000L) }

        val call = async { holder.call { "signed" } }
        runCurrent()
        inFront.value = false
        transport.authorized.complete(Unit)
        runCurrent()
        assertTrue(transport.blockEntered.isCompleted)
        inFront.value = true
        advanceTimeBy(30_000L)
        runCurrent()
        assertFalse("only the wallet's answer or the adapter's timeout ends a signing request", call.isCompleted)

        transport.answer.complete(TransactionResult.Failure("Timed out while waiting for result", TimeoutException()))
        runCurrent()
        assertTrue(call.await() is WalletOutcome.Error)
    }

    @Test
    fun `a disconnect the wallet never answers ends on return and still forgets the session`() = runTest {
        val slot = Slot("token-1")
        val store = MemoryStore()
        val inFront = MutableStateFlow(true)
        val transport = HeldTransport(slot)
        val holder = WalletSessionHolder(slot, store) { transport }.also { it.attach(transport, inFront, 3_000L) }

        val disconnect = async { holder.disconnect() }
        runCurrent()
        inFront.value = false
        runCurrent()
        inFront.value = true
        advanceTimeBy(3_500L)
        runCurrent()
        assertSame(WalletOutcome.Cancelled, disconnect.await())
        assertNull(slot.authToken)
        assertEquals(1, store.clears)
    }

    @Test
    fun `the adapter's own failures keep their meaning`() {
        assertSame(
            WalletOutcome.Cancelled,
            TransactionResult.Failure<Unit>("dismissed", WalletDismissedException()).toWalletOutcome(),
        )
        assertSame(WalletOutcome.NoWallet, TransactionResult.NoWalletFound<Unit>("No compatible wallet found.").toWalletOutcome())
        assertTrue(
            TransactionResult.Failure<Unit>("Timed out waiting for local association to be ready", TimeoutException())
                .toWalletOutcome() is WalletOutcome.Error,
        )
    }
}
