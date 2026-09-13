package com.plainticker.mobile.ui.vote

import app.cash.turbine.test
import com.funkatronics.encoders.Base58
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.VoteApi
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.SkrStake
import com.plainticker.mobile.data.rpc.SkrStakeAccount
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.repo.FakeRpcRepository
import com.plainticker.mobile.wallet.FakeAdapterOperations
import com.plainticker.mobile.wallet.FakeWalletSession
import com.plainticker.mobile.wallet.WalletAccount
import com.plainticker.mobile.wallet.WalletOutcome
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Every transition of the vote machine, asserted as a transition.
 *
 * The server is a MockEngine over the contract, the wallet is [FakeWalletSession] with
 * [FakeAdapterOperations] recording what it was asked to sign and send, and the chain is
 * [FakeRpcRepository]. Nothing here asserts a sentence: the copy is [VoteSheetModelTest]'s, and
 * what has to be right here is which state follows which.
 *
 * The state the whole feature is blocked on has its own case. `POST /api/v1/vote/build` answers
 * 404 today and will until the founder triggers the server work, and this file pins that the
 * answer is [VoteRefusal.NOT_OPEN] and not a failure, so the path the app takes the day the
 * route lands is the path it is already taking.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val voter = WalletAccount(ByteArray(32) { 7 }, "Seeker")

    private val collector = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"

    /** 31,209.870777 SKR: what the production forwarder returned for a staking wallet, 2026-09-13. */
    private val measuredStake = 31_209_870_777L

    private val body = """{"transaction":"UkVEQUNURUQ=","summary":""" +
        """{"ticker":"NFLX","lamports":5000,"collector":"$collector"}}"""

    private fun wallet(connected: Boolean = true) = FakeWalletSession().apply {
        if (connected) connectedAs(voter)
    }

    private fun staking(stakeRaw: Long) = FakeRpcRepository(
        stake = Result.success(SkrStake(listOf(SkrStakeAccount("s".padEnd(44, '1'), stakeRaw)))),
    )

    private fun machine(
        mock: MockApi = MockApi { respondJson(body) },
        wallet: FakeWalletSession = wallet(),
        rpc: FakeRpcRepository = staking(measuredStake),
    ) = VoteViewModel(VoteApi(mock.client), wallet, rpc, debugLog = VoteDebugLog { })

    /**
     * Every state the machine passes through, not only the ones a conflating StateFlow keeps.
     * The collector is unconfined, so each assignment resumes it on the assigning thread and the
     * short-lived phases are seen rather than skipped.
     */
    private fun TestScope.trail(machine: VoteViewModel): List<VoteState> {
        val seen = mutableListOf<VoteState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { machine.state.toList(seen) }
        return seen
    }

    private fun refusalOf(state: VoteState): VoteRefusal =
        (state as? VoteState.Refused)?.reason ?: throw AssertionError("not a refusal: $state")

    // ---- The happy path -------------------------------------------------------------------------

    @Test
    fun `a connected wallet with a stake reaches the confirm step with the figure and the summary`() = runTest {
        val mock = MockApi { respondJson(body) }
        val machine = machine(mock)

        machine.state.test {
            assertEquals(VoteState.Closed, awaitItem())
            machine.vote("NFLX", "NFLXx")
            val ready = awaitUntil { it is VoteState.Ready } as VoteState.Ready

            assertEquals("NFLX", ready.ticker)
            assertEquals("NFLXx", ready.symbol)
            assertEquals(voter.address, ready.voter)
            assertEquals(measuredStake, ready.stakeRaw)
            assertEquals(5_000L, ready.build.summary.lamports)
            assertEquals(collector, ready.build.summary.collector)
            assertNotNull(ready.build.transactionBytes())
            cancelAndIgnoreRemainingEvents()
        }

        // The server was asked for the equity ticker and the connected wallet, and nothing else.
        val sent = HttpClientFactory.json.parseToJsonElement(mock.lastRequest.bodyText()).jsonObject
        assertEquals("NFLX", sent["ticker"]!!.jsonPrimitive.content)
        assertEquals(voter.address, sent["voter"]!!.jsonPrimitive.content)
    }

    @Test
    fun `confirm signs and sends in one wallet call and lands with the signature in base58`() = runTest {
        val signature = ByteArray(64) { (it + 1).toByte() }
        val operations = FakeAdapterOperations(signatures = listOf(signature))
        val session = wallet().apply { this.operations = operations }
        val machine = machine(wallet = session)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            val ready = awaitUntil { it is VoteState.Ready } as VoteState.Ready
            assertTrue("nothing is signed before the figure is on the screen", operations.sendRequests.isEmpty())

            machine.confirm()
            val landed = awaitUntil { it is VoteState.Landed } as VoteState.Landed
            assertEquals(Base58.encodeToString(signature), landed.signature)
            assertEquals(measuredStake, landed.stakeRaw)
            assertEquals("NFLXx", landed.symbol)

            // One round-trip, carrying exactly the bytes the server built, and signTransactions
            // was never reached: MWA 2.x makes signAndSendTransactions mandatory and the other
            // one optional, so the vote uses the verb every wallet has to implement.
            assertEquals(1, operations.sendRequests.size)
            assertTrue(operations.signRequests.isEmpty())
            assertTrue(ready.build.transactionBytes()!!.contentEquals(operations.sendRequests.single().single()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Whether a wallet is connected ------------------------------------------------------------

    @Test
    fun `with no session the machine authorizes first, and the phase says a wallet was not connected`() = runTest {
        val session = wallet(connected = false).apply { enqueue(WalletOutcome.Success(voter)) }
        val machine = machine(wallet = session)
        val trail = trail(machine)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            awaitUntil { it is VoteState.Ready }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, session.connectCount)
        assertTrue(
            "the connect phase is the state that says no wallet was connected",
            trail.any { it is VoteState.Opening && it.phase == VotePhase.CONNECTING },
        )
    }

    @Test
    fun `with a session already open nothing is authorized and the read is the first phase`() = runTest {
        val session = wallet()
        val machine = machine(wallet = session)
        val trail = trail(machine)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            awaitUntil { it is VoteState.Ready }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, session.connectCount)
        assertFalse(trail.any { it is VoteState.Opening && it.phase == VotePhase.CONNECTING })
        assertTrue(trail.any { it is VoteState.Opening && it.phase == VotePhase.READING })
        assertTrue(trail.any { it is VoteState.Building })
    }

    @Test
    fun `a device with no wallet is told so, and nothing is read or requested`() = runTest {
        val session = wallet(connected = false).apply { enqueue(WalletOutcome.NoWallet) }
        val mock = MockApi { respondJson(body) }
        val machine = machine(mock, session)

        assertEquals(VoteRefusal.NO_WALLET, refusalOf(settle(machine)))
        assertTrue("no wallet means nothing to weigh, so the server is never asked", mock.requests.isEmpty())
        assertFalse("a device with no wallet is an answer, not a retry", VoteRefusal.NO_WALLET.retryable)
    }

    @Test
    fun `an authorize that ended without an account is not connected, whichever way it ended`() = runTest {
        val cancelled = wallet(connected = false).apply { enqueue(WalletOutcome.Cancelled) }
        assertEquals(VoteRefusal.NOT_CONNECTED, refusalOf(settle(machine(wallet = cancelled))))

        val refused = wallet(connected = false).apply { enqueue(WalletOutcome.Error("no answer")) }
        assertEquals(VoteRefusal.NOT_CONNECTED, refusalOf(settle(machine(wallet = refused))))
    }

    // ---- The stake ---------------------------------------------------------------------------------

    @Test
    fun `a connected wallet with nothing staked is told so, and costs the server no request`() = runTest {
        val mock = MockApi { respondJson(body) }
        val machine = machine(mock, rpc = FakeRpcRepository(stake = Result.success(SkrStake.NONE)))

        assertEquals(VoteRefusal.NO_STAKE, refusalOf(settle(machine)))
        assertTrue(mock.requests.isEmpty())
        assertFalse("nothing staked is an answer, not something a second tap fixes", VoteRefusal.NO_STAKE.retryable)
    }

    @Test
    fun `the impossible principal is refused, and no state anywhere carries a figure`() = runTest {
        // The account one getProgramAccounts over the whole program actually returned on
        // 2026-09-13: 11,452,317,590,968,446,072 raw, which does not fit a signed long and
        // decodes on the far side of zero.
        val asDecoded = -6_994_426_482_741_105_544L
        val mock = MockApi { respondJson(body) }
        val machine = machine(mock, rpc = staking(asDecoded))
        val trail = trail(machine)

        assertEquals(VoteRefusal.STAKE_UNREAD, refusalOf(settle(machine)))
        assertTrue("a figure this app will not stand behind is never voted with", mock.requests.isEmpty())
        assertTrue(
            "no state the sheet could draw a number from is ever reached",
            trail.none { it is VoteState.Building || it is VoteState.Ready || it is VoteState.Landed },
        )
    }

    @Test
    fun `a principal above the staked supply is refused the same way`() = runTest {
        val machine = machine(rpc = staking(SkrStakeBound.STAKED_SUPPLY_RAW + 1L))
        assertEquals(VoteRefusal.STAKE_UNREAD, refusalOf(settle(machine)))
    }

    @Test
    fun `a staking read that failed is unread, which is not the same as nothing staked`() = runTest {
        val machine = machine(rpc = FakeRpcRepository(stake = Result.failure(IllegalStateException("forwarder"))))
        assertEquals(VoteRefusal.STAKE_UNREAD, refusalOf(settle(machine)))
    }

    // ---- The server ---------------------------------------------------------------------------------

    @Test
    fun `todays 404 is voting not being open yet, and not a failure`() = runTest {
        val notPublished = MockApi { respondHtml("<html>404</html>", HttpStatusCode.NotFound) }
        assertEquals(VoteRefusal.NOT_OPEN, refusalOf(settle(machine(notPublished))))
        assertFalse("a route nobody has published is not retried", VoteRefusal.NOT_OPEN.retryable)
    }

    @Test
    fun `any other refusal is the server not having built the vote`() = runTest {
        val down = MockApi { respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError) }
        assertEquals(VoteRefusal.UNAVAILABLE, refusalOf(settle(machine(down))))

        val explained = MockApi {
            respondJson("""{"error":"Already covered.","code":"already_covered"}""", HttpStatusCode.Conflict)
        }
        assertEquals(VoteRefusal.UNAVAILABLE, refusalOf(settle(machine(explained))))
    }

    @Test
    fun `a 200 that is not the contract never reaches the wallet`() = runTest {
        val garbled = MockApi { respondJson("""{"transaction":"UkVEQUNURUQ="}""") }
        val operations = FakeAdapterOperations(signatures = listOf(ByteArray(64)))
        val session = wallet().apply { this.operations = operations }

        assertEquals(VoteRefusal.UNAVAILABLE, refusalOf(settle(machine(garbled, session))))
        assertTrue(operations.sendRequests.isEmpty())
    }

    // ---- The wallet, at the moment it signs ------------------------------------------------------------

    @Test
    fun `an approval that came back with no signature sent no vote, and claims no fault`() = runTest {
        val session = wallet()
        val machine = machine(wallet = session)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            awaitUntil { it is VoteState.Ready }
            session.enqueue(WalletOutcome.Cancelled)
            machine.confirm()
            val refused = awaitUntil { it is VoteState.Refused } as VoteState.Refused
            assertEquals(VoteRefusal.NOT_APPROVED, refused.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet that answered with a failure is a failure, and not a decline`() = runTest {
        val session = wallet()
        val machine = machine(wallet = session)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            awaitUntil { it is VoteState.Ready }
            session.enqueue(WalletOutcome.Error("not submitted"))
            machine.confirm()
            val refused = awaitUntil { it is VoteState.Refused } as VoteState.Refused
            assertEquals(VoteRefusal.FAILED, refused.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a success carrying no signature is a failure, because nothing came back to name`() = runTest {
        val session = wallet().apply { operations = FakeAdapterOperations(signatures = emptyList()) }
        val machine = machine(wallet = session)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            awaitUntil { it is VoteState.Ready }
            machine.confirm()
            val refused = awaitUntil { it is VoteState.Refused } as VoteState.Refused
            assertEquals(VoteRefusal.FAILED, refused.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the signing round-trip is the one state the sheet may not be dismissed from`() = runTest {
        val running = listOf(
            VoteState.Opening("NFLX", "NFLXx", VotePhase.CONNECTING),
            VoteState.Opening("NFLX", "NFLXx", VotePhase.READING),
            VoteState.Building("NFLX", "NFLXx", voter.address, measuredStake),
        )
        running.forEach { assertFalse("$it costs nothing to close", it.sheet()!!.holdsOpen) }
    }

    // ---- The machine's own rules ------------------------------------------------------------------------

    @Test
    fun `confirm does nothing from any state that is not the confirm step`() = runTest {
        val operations = FakeAdapterOperations(signatures = listOf(ByteArray(64)))
        val session = wallet().apply { this.operations = operations }
        val machine = machine(wallet = session)

        machine.confirm()
        assertEquals(VoteState.Closed, machine.state.value)
        assertTrue(operations.sendRequests.isEmpty())
    }

    @Test
    fun `a second tap while a round-trip is in flight is ignored`() = runTest {
        val mock = MockApi { respondJson(body) }
        val machine = machine(mock)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            machine.vote("AAPL", "AAPLx")
            val ready = awaitUntil { it is VoteState.Ready } as VoteState.Ready
            assertEquals("NFLX", ready.ticker)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, mock.requests.size)
    }

    @Test
    fun `retry runs the whole attempt again, rather than reusing bytes nothing signed`() = runTest {
        val session = wallet()
        val mock = MockApi { respondJson(body) }
        val machine = machine(mock, session)

        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            awaitUntil { it is VoteState.Ready }
            session.enqueue(WalletOutcome.Cancelled)
            machine.confirm()
            awaitUntil { it is VoteState.Refused }

            machine.retry()
            awaitUntil { it is VoteState.Ready }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("a retry asks the server for a fresh transaction", 2, mock.requests.size)
    }

    @Test
    fun `a refusal that is an answer offers no retry, and retry does nothing on it`() = runTest {
        val mock = MockApi { respondHtml("<html>404</html>", HttpStatusCode.NotFound) }
        val machine = machine(mock)
        assertEquals(VoteRefusal.NOT_OPEN, refusalOf(settle(machine)))

        machine.retry()
        assertEquals(VoteRefusal.NOT_OPEN, refusalOf(machine.state.value))
        assertEquals(1, mock.requests.size)
    }

    @Test
    fun `close drops the attempt`() = runTest {
        val machine = machine()
        settle(machine)
        machine.close()
        assertEquals(VoteState.Closed, machine.state.value)
    }

    // ---- Reachability -------------------------------------------------------------------------------------

    @Test
    fun `every refusal this app can show is reachable from the machine`() = runTest {
        val reached = mutableSetOf<VoteRefusal>()

        reached += refusalOf(
            settle(machine(wallet = wallet(connected = false).apply { enqueue(WalletOutcome.NoWallet) })),
        )
        reached += refusalOf(
            settle(machine(wallet = wallet(connected = false).apply { enqueue(WalletOutcome.Cancelled) })),
        )
        reached += refusalOf(settle(machine(rpc = FakeRpcRepository(stake = Result.success(SkrStake.NONE)))))
        reached += refusalOf(settle(machine(rpc = staking(-6_994_426_482_741_105_544L))))
        reached += refusalOf(settle(machine(MockApi { respondHtml("<html>404</html>", HttpStatusCode.NotFound) })))
        reached += refusalOf(settle(machine(MockApi { respondJson("{}", HttpStatusCode.InternalServerError) })))

        val declined = wallet()
        machine(wallet = declined).let { machine ->
            machine.state.test {
                awaitItem()
                machine.vote("NFLX", "NFLXx")
                awaitUntil { it is VoteState.Ready }
                declined.enqueue(WalletOutcome.Cancelled)
                machine.confirm()
                reached += refusalOf(awaitUntil { it is VoteState.Refused })
                cancelAndIgnoreRemainingEvents()
            }
        }

        val failing = wallet()
        machine(wallet = failing).let { machine ->
            machine.state.test {
                awaitItem()
                machine.vote("NFLX", "NFLXx")
                awaitUntil { it is VoteState.Ready }
                failing.enqueue(WalletOutcome.Error("not submitted"))
                machine.confirm()
                reached += refusalOf(awaitUntil { it is VoteState.Refused })
                cancelAndIgnoreRemainingEvents()
            }
        }

        assertEquals(
            "every refusal this app can show has to be reachable, or it is copy nobody will read",
            VoteRefusal.entries.toSet(),
            reached,
        )
    }

    @Test
    fun `no state of the machine is silent, and only the closed one draws nothing`() {
        val states = listOf(
            VoteState.Opening("NFLX", "NFLXx", VotePhase.CONNECTING),
            VoteState.Opening("NFLX", "NFLXx", VotePhase.READING),
            VoteState.Landed("NFLX", "NFLXx", measuredStake, "sig"),
        ) + VoteRefusal.entries.map { VoteState.Refused("NFLX", "NFLXx", it) }

        states.forEach { state ->
            val sheet = state.sheet() ?: throw AssertionError("$state draws no sheet at all")
            assertNotNull("$state says nothing", sheet.phase ?: sheet.notice ?: sheet.bar)
        }
        assertNull("only the closed state draws nothing", VoteState.Closed.sheet())
    }

    /** Runs one attempt to whichever state the machine will not leave on its own. */
    private suspend fun settle(machine: VoteViewModel): VoteState {
        var settled: VoteState = VoteState.Closed
        machine.state.test {
            awaitItem()
            machine.vote("NFLX", "NFLXx")
            settled = awaitUntil { it is VoteState.Ready || it is VoteState.Landed || it is VoteState.Refused }
            cancelAndIgnoreRemainingEvents()
        }
        return settled
    }
}
