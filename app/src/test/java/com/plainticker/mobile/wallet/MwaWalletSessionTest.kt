package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wallet session across process death (judges' review, 2026-09-26): restored at launch for
 * display and reads, reauthorized with the saved token when the wallet is needed, one fresh
 * authorize when the wallet rejects that token, and nothing left on the phone after Disconnect.
 *
 * [FakeTransport] stands in for clientlib-ktx's `transact`: it records the token each association
 * started with (the adapter chooses reauthorize when there is one), answers from a script, and on
 * success writes the new token back into the slot exactly as [com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter]
 * does.
 */
class MwaWalletSessionTest {

    private val saved = testAccount(fill = 3, label = "Seeker")
    private val other = testAccount(fill = 9, label = "Backpack")

    private class Slot(override var authToken: String? = null) : AuthTokenSlot

    private class MemoryStore(var session: SavedWalletSession? = null) : WalletSessionStore {
        var saves = 0
        var clears = 0
        override suspend fun load(): SavedWalletSession? = session
        override suspend fun save(session: SavedWalletSession) {
            saves++
            this.session = session
        }
        override suspend fun clear() {
            clears++
            session = null
        }
    }

    private sealed interface Answer {
        /** The wallet authorized (or reauthorized) [account] and issued [token]; the block runs. */
        data class Authorized(val token: String, val account: WalletAccount) : Answer

        /** The wallet refused the authorization itself; the block never runs. */
        data object Rejected : Answer

        /** The person backed out of the wallet. */
        data object BackedOut : Answer

        /** Authorized, the block ran, and the wallet then answered ERROR_AUTHORIZATION_FAILED. */
        data object RejectedAfterAsking : Answer
    }

    private class FakeTransport(private val slot: Slot, vararg answers: Answer) : MwaTransport {
        val script = ArrayDeque(answers.toList())
        val tokensSeen = mutableListOf<String?>()
        var disconnects = 0
        var disconnectAnswer: TransactionResult<Unit> = TransactionResult.Success(Unit)

        private fun rejected(): TransactionResult.Failure<Nothing> = TransactionResult.Failure(
            "Auth token invalid",
            JsonRpc20Client.JsonRpc20RemoteException(ProtocolContract.ERROR_AUTHORIZATION_FAILED, "rejected", null),
        )

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> {
            tokensSeen += slot.authToken
            return when (val answer = script.removeFirstOrNull() ?: error("no scripted answer left")) {
                is Answer.Authorized -> {
                    val value = block(FakeAdapterOperations())
                    slot.authToken = answer.token
                    TransactionResult.Success(
                        value,
                        MobileWalletAdapterClient.AuthorizationResult.create(answer.token, answer.account.publicKey, answer.account.label, null),
                    )
                }
                Answer.Rejected -> rejected() as TransactionResult<T>
                Answer.BackedOut -> TransactionResult.Failure("Request was interrupted", InterruptedException())
                Answer.RejectedAfterAsking -> {
                    block(FakeAdapterOperations())
                    rejected() as TransactionResult<T>
                }
            }
        }

        override suspend fun disconnect(): TransactionResult<Unit> {
            disconnects++
            return disconnectAnswer
        }
    }

    private fun holder(slot: Slot, store: MemoryStore, transport: FakeTransport) =
        WalletSessionHolder(slot, store) { transport }.also { it.attach(transport) }

    // ---- Restore ---------------------------------------------------------------------------

    @Test
    fun `a saved session is restored for display and reads without opening the wallet`() = runTest {
        val slot = Slot()
        val transport = FakeTransport(slot)
        val h = holder(slot, MemoryStore(SavedWalletSession("token-1", saved)), transport)
        assertNull(h.account.value)

        h.restore()

        assertEquals(saved, h.account.value)
        assertEquals("token-1", slot.authToken)
        assertTrue("no wallet was opened", transport.tokensSeen.isEmpty())
    }

    @Test
    fun `restore never overwrites a session that is already live, and an empty store restores nothing`() = runTest {
        val slot = Slot("live-token")
        val h = holder(slot, MemoryStore(SavedWalletSession("token-1", saved)), FakeTransport(slot))
        h.restore()
        assertEquals("live-token", slot.authToken)
        assertNull(h.account.value)

        val empty = Slot()
        val h2 = holder(empty, MemoryStore(), FakeTransport(empty))
        h2.restore()
        assertNull(h2.account.value)
        assertNull(empty.authToken)
    }

    @Test
    fun `the first wallet request after a restore reauthorizes with the saved token and saves the new one`() = runTest {
        val slot = Slot()
        val store = MemoryStore(SavedWalletSession("token-1", saved))
        val transport = FakeTransport(slot, Answer.Authorized("token-2", saved))
        val h = holder(slot, store, transport)
        h.restore()

        val outcome = h.call { "signed" }

        assertEquals(WalletOutcome.Success("signed"), outcome)
        assertEquals("the association started from the saved token", listOf<String?>("token-1"), transport.tokensSeen)
        assertEquals(SavedWalletSession("token-2", saved), store.session)
        assertEquals(saved, h.account.value)
    }

    // ---- The fallback ----------------------------------------------------------------------

    @Test
    fun `a saved token the wallet rejects falls back to one fresh authorize, and the request runs once`() = runTest {
        val slot = Slot()
        val store = MemoryStore(SavedWalletSession("stale-token", saved))
        val transport = FakeTransport(slot, Answer.Rejected, Answer.Authorized("fresh-token", other))
        val h = holder(slot, store, transport)
        h.restore()
        var asked = 0

        val outcome = h.call { asked++; "signed" }

        assertEquals(WalletOutcome.Success("signed"), outcome)
        assertEquals("reauthorize with the saved token, then authorize with none", listOf("stale-token", null), transport.tokensSeen)
        assertEquals("the wallet was asked to sign exactly once", 1, asked)
        assertEquals("the account the wallet chose this time", other, h.account.value)
        assertEquals(SavedWalletSession("fresh-token", other), store.session)
    }

    @Test
    fun `connect falls back the same way`() = runTest {
        val slot = Slot("stale-token")
        val store = MemoryStore()
        val transport = FakeTransport(slot, Answer.Rejected, Answer.Authorized("fresh-token", saved))
        val h = holder(slot, store, transport)

        assertEquals(WalletOutcome.Success(saved), h.connect())
        assertEquals(listOf("stale-token", null), transport.tokensSeen)
        assertEquals(SavedWalletSession("fresh-token", saved), store.session)
    }

    @Test
    fun `a fresh authorize declined after the fallback forgets the session, and does not loop`() = runTest {
        val slot = Slot()
        val store = MemoryStore(SavedWalletSession("stale-token", saved))
        val transport = FakeTransport(slot, Answer.Rejected, Answer.Rejected)
        val h = holder(slot, store, transport)
        h.restore()

        val outcome = h.call { "signed" }

        assertSame(WalletOutcome.Cancelled, outcome)
        assertEquals(2, transport.tokensSeen.size)
        assertNull(h.account.value)
        assertNull(slot.authToken)
        assertNull(store.session)
    }

    @Test
    fun `there is no second attempt without a saved token, or once the wallet was already asked`() = runTest {
        val fresh = Slot()
        val t1 = FakeTransport(fresh, Answer.Rejected)
        assertSame(WalletOutcome.Cancelled, holder(fresh, MemoryStore(), t1).call { "signed" })
        assertEquals("a declined first authorize is the person's answer", 1, t1.tokensSeen.size)

        val slot = Slot("token-1")
        val store = MemoryStore(SavedWalletSession("token-1", saved))
        val t2 = FakeTransport(slot, Answer.RejectedAfterAsking)
        var asked = 0
        assertSame(WalletOutcome.Cancelled, holder(slot, store, t2).call { asked++; "signed" })
        assertEquals("never asked to sign twice", 1, asked)
        assertEquals(1, t2.tokensSeen.size)
        assertNull("a rejection still clears the saved session", store.session)
    }

    @Test
    fun `backing out of the wallet keeps the saved session`() = runTest {
        val slot = Slot()
        val store = MemoryStore(SavedWalletSession("token-1", saved))
        val transport = FakeTransport(slot, Answer.BackedOut)
        val h = holder(slot, store, transport)
        h.restore()

        assertSame(WalletOutcome.Cancelled, h.call { "signed" })
        assertEquals(1, transport.tokensSeen.size)
        assertEquals(saved, h.account.value)
        assertEquals(SavedWalletSession("token-1", saved), store.session)
    }

    // ---- Disconnect ------------------------------------------------------------------------

    @Test
    fun `disconnect clears the saved session, the token and the account`() = runTest {
        val slot = Slot()
        val store = MemoryStore(SavedWalletSession("token-1", saved))
        val transport = FakeTransport(slot)
        val h = holder(slot, store, transport)
        h.restore()

        assertEquals(WalletOutcome.Success(Unit), h.disconnect())

        assertEquals(1, transport.disconnects)
        assertNull(store.session)
        assertEquals(1, store.clears)
        assertNull(slot.authToken)
        assertNull(h.account.value)
    }

    @Test
    fun `disconnect forgets the session on this phone even when the wallet cannot be reached`() = runTest {
        val slot = Slot()
        val store = MemoryStore(SavedWalletSession("token-1", saved))
        val transport = FakeTransport(slot).apply { disconnectAnswer = TransactionResult.NoWalletFound("No compatible wallet found.") }
        val h = holder(slot, store, transport)
        h.restore()

        assertSame(WalletOutcome.NoWallet, h.disconnect())
        assertNull(store.session)
        assertNull(slot.authToken)
        assertNull(h.account.value)

        // And the next launch restores nothing.
        val relaunched = Slot()
        val again = holder(relaunched, store, FakeTransport(relaunched))
        again.restore()
        assertNull(again.account.value)
    }

    @Test
    fun `a first connect saves the session for the next launch`() = runTest {
        val slot = Slot()
        val store = MemoryStore()
        val h = holder(slot, store, FakeTransport(slot, Answer.Authorized("token-1", saved)))

        assertEquals(WalletOutcome.Success(saved), h.connect())
        assertEquals(SavedWalletSession("token-1", saved), store.session)

        val relaunched = Slot()
        val next = holder(relaunched, store, FakeTransport(relaunched))
        next.restore()
        assertEquals(saved, next.account.value)
        assertEquals("token-1", relaunched.authToken)
    }
}
