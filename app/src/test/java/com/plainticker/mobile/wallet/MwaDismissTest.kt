package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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

    /**
     * Stands in for the real adapter on the security review's paths: its authorization cannot be
     * cancelled (the adapter blocks a thread on a future), it writes the token into the slot as
     * soon as the wallet authorizes, before the block runs, and the first association can be
     * scripted to reject the saved token so the fallback opens a second one.
     */
    private class AdapterLikeTransport(private val slot: Slot, private val rejectFirst: Boolean = false) : MwaTransport {
        val firstAnswered = CompletableDeferred<Unit>()
        val authorized = CompletableDeferred<Unit>()
        var transacts = 0
        var blockFailure: Throwable? = null

        override suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> {
            transacts++
            if (rejectFirst && transacts == 1) {
                withContext(NonCancellable) { firstAnswered.await() }
                return TransactionResult.Failure(
                    "Auth token invalid",
                    JsonRpc20Client.JsonRpc20RemoteException(ProtocolContract.ERROR_AUTHORIZATION_FAILED, "rejected", null),
                )
            }
            withContext(NonCancellable) { authorized.await() }
            slot.authToken = "token-late"
            val value = try {
                withContext(NonCancellable) { block(FakeAdapterOperations()) }
            } catch (e: Exception) {
                blockFailure = e
                throw e
            }
            return TransactionResult.Success(
                value,
                MobileWalletAdapterClient.AuthorizationResult.create("token-late", testAccount(fill = 4, label = "Seeker").publicKey, "Seeker", null),
            )
        }

        override suspend fun disconnect(): TransactionResult<Unit> = TransactionResult.Success(Unit)
    }

    @Test
    fun `the fallback's second Connect prompt is not cancelled by the grace from the first return`() = runTest {
        // Security review M1: the saved token is rejected, the app is briefly in front, then the
        // fallback opens the wallet's Connect sheet again. The grace that started on that brief
        // return must not end the live prompt.
        val slot = Slot("stale-token")
        val inFront = MutableStateFlow(true)
        val transport = AdapterLikeTransport(slot, rejectFirst = true)
        val holder = WalletSessionHolder(slot, MemoryStore()) { transport }.also { it.attach(transport, inFront, 3_000L) }

        val call = async { holder.call { "signed" } }
        runCurrent()
        inFront.value = false // the wallet opens to reauthorize
        runCurrent()
        transport.firstAnswered.complete(Unit) // and rejects the saved token
        inFront.value = true // the app is back for a moment
        runCurrent()
        assertEquals("the fallback opened a second association", 2, transport.transacts)
        advanceTimeBy(1_000L)
        inFront.value = false // the second association's Connect sheet is up
        runCurrent()
        advanceTimeBy(20_000L)
        runCurrent()
        assertFalse("a live Connect prompt is not a dismissal", call.isCompleted)

        transport.authorized.complete(Unit)
        runCurrent()
        inFront.value = true
        runCurrent()
        assertEquals(WalletOutcome.Success("signed"), call.await())
    }

    @Test
    fun `the re-armed grace still ends a request once the app stays in front`() = runTest {
        val slot = Slot("stale-token")
        val inFront = MutableStateFlow(true)
        val transport = AdapterLikeTransport(slot, rejectFirst = true)
        val holder = WalletSessionHolder(slot, MemoryStore()) { transport }.also { it.attach(transport, inFront, 3_000L) }

        val call = async { holder.call { "signed" } }
        runCurrent()
        inFront.value = false
        runCurrent()
        transport.firstAnswered.complete(Unit)
        inFront.value = true
        runCurrent()
        advanceTimeBy(1_000L)
        inFront.value = false // Connect sheet
        runCurrent()
        inFront.value = true // back pressed on it
        runCurrent()
        advanceTimeBy(2_500L)
        runCurrent()
        assertFalse(call.isCompleted)
        advanceTimeBy(1_000L)
        runCurrent()
        assertSame(WalletOutcome.Cancelled, call.await())
    }

    @Test
    fun `a late authorize after the UI said Cancelled never runs the block, and leaves the slot as it was`() = runTest {
        // Security review L1 and L2: the wallet authorizes after the request was given up. The
        // block (the signature request) must not run, and the token the adapter wrote during
        // that authorization must not outlive the abandoned call.
        val slot = Slot("token-1")
        val store = MemoryStore()
        val inFront = MutableStateFlow(true)
        val transport = AdapterLikeTransport(slot)
        val holder = WalletSessionHolder(slot, store) { transport }.also { it.attach(transport, inFront, 3_000L) }
        var signed = 0

        val call = async { holder.call { signed++; "signed" } }
        runCurrent()
        inFront.value = false
        runCurrent()
        inFront.value = true
        advanceTimeBy(3_500L)
        runCurrent()
        assertSame(WalletOutcome.Cancelled, call.await())

        transport.authorized.complete(Unit) // the wallet answers late
        runCurrent()
        assertEquals("nothing was asked to be signed", 0, signed)
        assertTrue(transport.blockFailure is WalletDismissedException)
        assertEquals("the abandoned call's token was put back", "token-1", slot.authToken)
        assertNull(holder.account.value)
        assertEquals(0, store.saves)
    }

    @Test
    fun `the asked and abandoned decision is taken once`() {
        val claimedFirst = MwaWalletSession.Ask()
        assertTrue(claimedFirst.claim())
        assertFalse("a request that asked is never given up", claimedFirst.abandon())
        assertTrue("the fallback's second association may still run the block", claimedFirst.claim())

        val abandonedFirst = MwaWalletSession.Ask()
        assertTrue(abandonedFirst.abandon())
        assertFalse("a given-up request never asks", abandonedFirst.claim())
        assertTrue(abandonedFirst.abandoned)
    }

    @Test
    fun `the adapter still keeps the wallet URI where the slot snapshot reads it`() {
        // tokenSlot() restores MobileWalletAdapter's private walletUriBase by reflection; a
        // clientlib upgrade that renames it fails here, not silently on a phone.
        val field = MobileWalletAdapter::class.java.getDeclaredField("walletUriBase")
        assertEquals(android.net.Uri::class.java, field.type)
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
