package com.plainticker.mobile.ui.pass

import app.cash.turbine.test
import com.funkatronics.encoders.Base58
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.data.plainticker.PassApi
import com.plainticker.mobile.data.receipts.FakePassReceiptStore
import com.plainticker.mobile.data.receipts.PassReceipt
import com.plainticker.mobile.data.respondHtml
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.SkrStake
import com.plainticker.mobile.data.rpc.SkrStakeAccount
import com.plainticker.mobile.prefs.InMemoryDevicePassStore
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
import java.time.Instant

/**
 * Every transition of the pay machine, and the entitlement half it shares a class with (task A6).
 *
 * Built the way [com.plainticker.mobile.ui.vote.VoteViewModelTest] proves the vote machine: the
 * server is a [MockApi], the wallet is [FakeWalletSession] with [FakeAdapterOperations] recording
 * what it was asked to sign and send, and the chain is [FakeRpcRepository]. What matters here is
 * which state follows which, and, unlike the vote machine, that a landed signature is never
 * undone by this app's own confirm call not answering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PassViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val payer = WalletAccount(ByteArray(32) { 7 }, "Seeker")
    private val destination = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
    private val treasury = "E1STBTGEYpHanVG4HWUJHfzEu6eGbnE9mnGX9KnAmJdL"
    private val devicePassStore = InMemoryDevicePassStore("ABCDE12345")

    private val buildBody = """{"transaction":"UkVEQUNURUQ=","summary":
        {"mint":"USDC","amount":12000000,"destination":"$destination","treasury":"$treasury","lamports":5000}}"""

    private val confirmBody = """{"pro":true,"source":"pass","until":"2026-10-19T12:00:00.000Z"}"""

    /** Routes `/pass/build` and `/pass/confirm` off one engine, the way one live server answers both. */
    private fun passMock(build: String = buildBody, confirm: String = confirmBody) = MockApi { request ->
        if (request.url.encodedPath.endsWith(PassApi.CONFIRM_PATH)) respondJson(confirm) else respondJson(build)
    }

    private fun entitlementMock(body: String = """{"pro":false,"source":null,"until":null}""") =
        MockApi { respondJson(body) }

    private fun wallet(connected: Boolean = true) = FakeWalletSession().apply { if (connected) connectedAs(payer) }

    private fun staking(stakeRaw: Long) =
        FakeRpcRepository(stake = Result.success(SkrStake(listOf(SkrStakeAccount("s".padEnd(44, '1'), stakeRaw)))))

    private val now = 1_789_394_400_000L

    private fun machine(
        pass: MockApi = passMock(),
        entitlement: MockApi = entitlementMock(),
        wallet: FakeWalletSession = wallet(),
        rpc: FakeRpcRepository = staking(0L),
        store: InMemoryDevicePassStore = devicePassStore,
        receipts: FakePassReceiptStore = FakePassReceiptStore(),
        clock: Clock = Clock { now },
    ) = PassViewModel(
        PassApi(pass.client),
        EntitlementApi(entitlement.client),
        wallet,
        rpc,
        store,
        receipts,
        clock = clock,
        debugLog = PassDebugLog { },
        // The same test dispatcher Main is pointed at, so a receipt's write is deterministic
        // under runTest exactly the way VoteViewModelTest already keeps its own.
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    private fun TestScope.trail(machine: PassViewModel): List<PassState> {
        val seen = mutableListOf<PassState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { machine.state.toList(seen) }
        return seen
    }

    /**
     * Every [ProUiState], not only the ones a conflating StateFlow would keep for a collector
     * that is not actively suspended: the collector is unconfined, so each assignment resumes it
     * on the assigning thread and a short-lived reset between two settled states is seen rather
     * than skipped (the same reasoning [trail] already carries for [PassState]).
     */
    private fun TestScope.proTrail(machine: PassViewModel): List<ProUiState> {
        val seen = mutableListOf<ProUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { machine.pro.toList(seen) }
        return seen
    }

    private fun refusalOf(state: PassState): PassRefusal =
        (state as? PassState.Refused)?.reason ?: throw AssertionError("not a refusal: $state")

    // ---- The happy path -----------------------------------------------------------------------

    @Test
    fun `a connected wallet reaches the confirm step with the server's own summary`() = runTest {
        val pass = passMock()
        val vm = machine(pass = pass)

        vm.state.test {
            assertEquals(PassState.Closed, awaitItem())
            vm.pay()
            val ready = awaitUntil { it is PassState.Ready } as PassState.Ready

            assertEquals(payer.address, ready.payer)
            assertEquals(12_000_000L, ready.build.summary.amount)
            assertEquals(destination, ready.build.summary.destination)
            assertEquals(5_000L, ready.build.summary.lamports)
            assertNotNull(ready.build.transactionBytes())
            cancelAndIgnoreRemainingEvents()
        }

        val sent = HttpClientFactory.json.parseToJsonElement(pass.requests.first().bodyText()).jsonObject
        assertEquals(payer.address, sent["payer"]!!.jsonPrimitive.content)
        assertEquals("USDC", sent["mint"]!!.jsonPrimitive.content)
        assertEquals(devicePassStore.codeHash(), sent["codeHash"]!!.jsonPrimitive.content)
    }

    @Test
    fun `confirm signs and sends in one wallet call, then the server is asked to confirm the signature`() = runTest {
        val signature = ByteArray(64) { (it + 1).toByte() }
        val operations = FakeAdapterOperations(signatures = listOf(signature))
        val session = wallet().apply { this.operations = operations }
        val pass = passMock()
        val vm = machine(pass = pass, wallet = session)

        vm.state.test {
            awaitItem()
            vm.pay()
            awaitUntil { it is PassState.Ready }
            assertTrue("nothing is signed before the figure is on the screen", operations.sendRequests.isEmpty())

            vm.confirm()
            val landed = awaitUntil { it is PassState.Landed } as PassState.Landed
            val expectedSignature = Base58.encodeToString(signature)
            assertEquals(expectedSignature, landed.signature)
            assertTrue(landed.entitlement!!.pro)

            // Exactly one send, carrying the server's own bytes, and the confirm call named the
            // exact signature the wallet handed back: the acceptance task A6 names by name.
            assertEquals(1, operations.sendRequests.size)
            assertEquals(2, pass.requests.size)
            val confirmSent = HttpClientFactory.json.parseToJsonElement(pass.requests[1].bodyText()).jsonObject
            assertEquals(expectedSignature, confirmSent["signature"]!!.jsonPrimitive.content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a landed signature is not undone by a confirm call that did not answer`() = runTest {
        val signature = ByteArray(64) { 5 }
        val operations = FakeAdapterOperations(signatures = listOf(signature))
        val session = wallet().apply { this.operations = operations }
        // The build answers, and the confirm half of the same route answers with a failure this
        // app cannot act on: the payment already reached the network by the time this call runs.
        val pass = MockApi { request ->
            if (request.url.encodedPath.endsWith(PassApi.CONFIRM_PATH)) {
                respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError)
            } else {
                respondJson(buildBody)
            }
        }
        val vm = machine(pass = pass, wallet = session)

        vm.state.test {
            awaitItem()
            vm.pay()
            awaitUntil { it is PassState.Ready }
            vm.confirm()
            val landed = awaitUntil { it is PassState.Landed } as PassState.Landed
            assertEquals(Base58.encodeToString(signature), landed.signature)
            assertNull("the confirm call refused, so there is nothing fresh to show", landed.entitlement)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Whether a wallet is connected -----------------------------------------------------

    @Test
    fun `with no session the machine authorizes first`() = runTest {
        val session = wallet(connected = false).apply { enqueue(WalletOutcome.Success(payer)) }
        val vm = machine(wallet = session)
        val trail = trail(vm)

        vm.state.test {
            awaitItem()
            vm.pay()
            awaitUntil { it is PassState.Ready }
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, session.connectCount)
        assertTrue(trail.any { it is PassState.Opening })
    }

    @Test
    fun `a device with no wallet is told so, and nothing is built`() = runTest {
        val session = wallet(connected = false).apply { enqueue(WalletOutcome.NoWallet) }
        val pass = passMock()
        val vm = machine(pass = pass, wallet = session)

        assertEquals(PassRefusal.NO_WALLET, refusalOf(settle(vm)))
        assertTrue("no wallet, so the server is never asked to build", pass.requests.isEmpty())
        assertFalse(PassRefusal.NO_WALLET.retryable)
    }

    @Test
    fun `an authorize that ended without an account is not connected`() = runTest {
        val cancelled = wallet(connected = false).apply { enqueue(WalletOutcome.Cancelled) }
        assertEquals(PassRefusal.NOT_CONNECTED, refusalOf(settle(machine(wallet = cancelled))))
    }

    // ---- The server ------------------------------------------------------------------------

    @Test
    fun `503 monetization disabled reads as not open, not as an error`() = runTest {
        val flagOff = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        assertEquals(PassRefusal.NOT_OPEN, refusalOf(settle(machine(pass = flagOff))))
        assertFalse(PassRefusal.NOT_OPEN.retryable)
    }

    @Test
    fun `a 429 is rate limited and can end differently on a later tap`() = runTest {
        val limited = MockApi { respondJson("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }
        assertEquals(PassRefusal.RATE_LIMITED, refusalOf(settle(machine(pass = limited))))
        assertTrue(PassRefusal.RATE_LIMITED.retryable)
    }

    @Test
    fun `a 200 that is not the contract never reaches the wallet`() = runTest {
        val garbled = MockApi { respondJson("""{"transaction":"UkVEQUNURUQ="}""") }
        val operations = FakeAdapterOperations(signatures = listOf(ByteArray(64)))
        val session = wallet().apply { this.operations = operations }

        assertEquals(PassRefusal.UNAVAILABLE, refusalOf(settle(machine(pass = garbled, wallet = session))))
        assertTrue(operations.sendRequests.isEmpty())
    }

    @Test
    fun `an approval that came back with no signature sent no payment, and never calls confirm`() = runTest {
        val session = wallet()
        val pass = passMock()
        val vm = machine(pass = pass, wallet = session)

        vm.state.test {
            awaitItem()
            vm.pay()
            awaitUntil { it is PassState.Ready }
            session.enqueue(WalletOutcome.Cancelled)
            vm.confirm()
            val refused = awaitUntil { it is PassState.Refused } as PassState.Refused
            assertEquals(PassRefusal.NOT_APPROVED, refused.reason)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("only the build call, never a confirm with nothing to confirm", 1, pass.requests.size)
    }

    // ---- The expiry --------------------------------------------------------------------------

    private fun bodyExpiring(at: Long) = """{"transaction":"UkVEQUNURUQ=","summary":
        {"mint":"USDC","amount":12000000,"destination":"$destination","treasury":"$treasury","lamports":5000},
         "expiresAt":"${Instant.ofEpochMilli(at)}"}"""

    @Test
    fun `a transaction that has expired is never signed, the server is asked again instead`() = runTest {
        val pass = MockApi { respondJson(bodyExpiring(now - 1_000L)) }
        val operations = FakeAdapterOperations(signatures = listOf(ByteArray(64) { 1 }))
        val session = wallet().apply { this.operations = operations }
        val vm = machine(pass = pass, wallet = session)

        vm.state.test {
            awaitItem()
            vm.pay()
            val first = awaitUntil { it is PassState.Ready } as PassState.Ready
            assertFalse(first.refreshed)

            vm.confirm()
            val again = awaitUntil { it is PassState.Ready && pass.requests.size == 2 } as PassState.Ready
            assertTrue(again.refreshed)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("nothing stale reaches the wallet", operations.sendRequests.isEmpty())
    }

    // ---- Entitlement -----------------------------------------------------------------------

    @Test
    fun `entitlement reads pro true with its source and when it ends`() = runTest {
        val entitlement = entitlementMock("""{"pro":true,"source":"pass","until":"2026-10-19T00:00:00.000Z"}""")
        val vm = machine(entitlement = entitlement)

        vm.pro.test {
            val loaded = awaitUntil { !it.entitlementLoading }
            assertTrue(loaded.pro)
            assertEquals(EntitlementSource.PASS, loaded.source)
            assertEquals(1_792_368_000_000L, loaded.untilMillis)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(devicePassStore.code(), entitlement.lastRequest.headers[EntitlementApi.HEADER_CODE])
    }

    @Test
    fun `a stake source carries no date, because it is re-evaluated rather than granted once`() = runTest {
        val entitlement = entitlementMock("""{"pro":true,"source":"stake","until":null}""")
        val vm = machine(entitlement = entitlement)

        vm.pro.test {
            val loaded = awaitUntil { !it.entitlementLoading }
            assertTrue(loaded.pro)
            assertEquals(EntitlementSource.STAKE, loaded.source)
            assertNull(loaded.untilMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no entitlement at all reads pro false with no source and no date`() = runTest {
        val vm = machine(entitlement = entitlementMock())
        vm.pro.test {
            val loaded = awaitUntil { !it.entitlementLoading }
            assertFalse(loaded.pro)
            assertNull(loaded.source)
            assertNull(loaded.untilMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `503 monetization disabled on entitlement reads as disabled, not as a failure`() = runTest {
        val entitlement = MockApi {
            respondJson(
                """{"error":"This endpoint is not enabled on this server.","code":"monetization_disabled"}""",
                HttpStatusCode.ServiceUnavailable,
            )
        }
        val vm = machine(entitlement = entitlement)
        vm.pro.test {
            val loaded = awaitUntil { !it.entitlementLoading }
            assertTrue(loaded.entitlementDisabled)
            assertFalse(loaded.entitlementFailed)
            assertFalse(loaded.pro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `any other entitlement failure is retryable and distinct from disabled`() = runTest {
        val entitlement = MockApi { respondHtml("<html>down</html>", HttpStatusCode.BadGateway) }
        val vm = machine(entitlement = entitlement)
        vm.pro.test {
            val loaded = awaitUntil { !it.entitlementLoading }
            assertTrue(loaded.entitlementFailed)
            assertFalse(loaded.entitlementDisabled)

            // A second refresh, driven the same way every other wait in this file is: through the
            // collector that is still active, rather than by poking the scheduler from outside it.
            vm.refreshEntitlement()
            awaitUntil { it.entitlementLoading }
            awaitUntil { !it.entitlementLoading }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, entitlement.requests.size)
    }

    // ---- The wallet's own stake, read for the Portfolio block -------------------------------

    @Test
    fun `a connected wallet's stake is read and bounded the same way the vote reads it`() = runTest {
        val vm = machine(wallet = wallet(), rpc = staking(31_209_870_777L))
        vm.pro.test {
            val loaded = awaitUntil { it.stakeRaw != null }
            assertEquals(31_209_870_777L, loaded.stakeRaw)
            assertFalse(loaded.stakeUnread)
            assertTrue(loaded.walletConnected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no wallet connected reads no stake, and says nothing was connected`() = runTest {
        val vm = machine(wallet = wallet(connected = false))
        vm.pro.test {
            val settled = awaitUntil { !it.entitlementLoading }
            assertFalse(settled.walletConnected)
            assertNull(settled.stakeRaw)
            assertFalse(settled.stakeUnread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an implausible principal is unread, never a number, and never a verdict about the wallet`() = runTest {
        val impossible = -6_994_426_482_741_105_544L
        val vm = machine(wallet = wallet(), rpc = staking(impossible))
        vm.pro.test {
            val loaded = awaitUntil { it.walletConnected && (it.stakeUnread || it.stakeRaw != null) }
            assertTrue(loaded.stakeUnread)
            assertNull(loaded.stakeRaw)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stake below the entitlement threshold is still a plain figure`() = runTest {
        // Below 10,000 SKR (docs/plan-monetisation-2026-09-19.md section 1.2): this class states
        // it and stops, the way ProModelTest pins the sentence never carries a verdict about it.
        val vm = machine(wallet = wallet(), rpc = staking(3_200_000_000L))
        vm.pro.test {
            val loaded = awaitUntil { it.stakeRaw != null }
            assertEquals(3_200_000_000L, loaded.stakeRaw)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- A wallet switch (task A6 review) ----------------------------------------------------

    @Test
    fun `a wallet switch refreshes both the entitlement and the stake line together`() = runTest {
        val walletB = WalletAccount(ByteArray(32) { 9 }, "Wallet B")
        val entitlement = entitlementMock("""{"pro":true,"source":"pass","until":"2026-10-19T00:00:00.000Z"}""")
        val session = wallet()
        val rpc = staking(1_000_000_000L)
        val vm = machine(entitlement = entitlement, wallet = session, rpc = rpc)
        val trail = proTrail(vm)

        vm.pro.test {
            awaitUntil { !it.entitlementLoading && it.stakeRaw == 1_000_000_000L }

            // A different wallet connects mid-session, the way a swap or vote flow's own connect
            // would leave this one finding a new account.
            rpc.stake = Result.success(SkrStake(listOf(SkrStakeAccount("s".padEnd(44, '2'), 5_000_000_000L))))
            session.connectedAs(walletB)

            awaitUntil { !it.entitlementLoading && it.stakeRaw == 5_000_000_000L }
            cancelAndIgnoreRemainingEvents()
        }

        // Entitlement was actually re-asked for the switch, not left standing on wallet A's answer.
        assertEquals(2, entitlement.requests.size)

        // The reset is one atomic update, not two: the entry right after wallet A's settled state
        // already carries both halves reset together, so a fast stake read for wallet B can never
        // land beside an entitlement sentence a reader would read as still being about wallet A.
        val settledForA = trail.indexOfFirst { it.stakeRaw == 1_000_000_000L && !it.entitlementLoading }
        assertTrue("wallet A's settled state must appear in the trail", settledForA >= 0)
        val reset = trail[settledForA + 1]
        assertTrue("the entitlement half resets the instant the stake half does", reset.entitlementLoading)
        assertNull("the stake half resets in the same update, never a frame later", reset.stakeRaw)
        assertFalse(reset.stakeUnread)
    }

    // ---- A pending payment (task A6 review) --------------------------------------------------

    @Test
    fun `a confirmed payment is marked confirmed, and no longer pending`() = runTest {
        val signature = ByteArray(64) { 6 }
        val operations = FakeAdapterOperations(signatures = listOf(signature))
        val session = wallet().apply { this.operations = operations }
        val receipts = FakePassReceiptStore()
        val vm = machine(wallet = session, receipts = receipts)

        vm.state.test {
            awaitItem()
            vm.pay()
            awaitUntil { it is PassState.Ready }
            assertNull("nothing is pending before anything has signed", vm.pro.value.pendingSignature)
            vm.confirm()
            awaitUntil { it is PassState.Landed }
            cancelAndIgnoreRemainingEvents()
        }

        val stored = receipts.receipts.value.single()
        assertEquals(Base58.encodeToString(signature), stored.signature)
        assertTrue("confirm succeeded, so this device no longer treats it as pending", stored.confirmed)
        assertNull(vm.pro.value.pendingSignature)
    }

    @Test
    fun `a signature written before process death is retried on the next launch, and clears once confirmed`() = runTest {
        val pendingSignature = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe"
        val receipts = FakePassReceiptStore()
        receipts.record(PassReceipt(signature = pendingSignature, payer = payer.address, landedAtMillis = now))
        val pass = passMock()
        // A fresh ViewModel, exactly as a cold start after process death builds one, over the
        // same backing store: nothing about the pending payment survived in memory, only on disk.
        val vm = machine(pass = pass, receipts = receipts)

        assertEquals("the pending payment is surfaced immediately, before any network call answers", pendingSignature, vm.pro.value.pendingSignature)

        vm.pro.test {
            awaitUntil { it.pendingSignature == null }
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(receipts.receipts.value.single().confirmed)
        assertTrue(
            "the confirm call actually ran again for the receipt this device already had",
            pass.requests.any { it.url.encodedPath.endsWith(PassApi.CONFIRM_PATH) },
        )
    }

    @Test
    fun `a pending payment that still cannot be confirmed stays pending, and offers no second payment`() = runTest {
        val receipts = FakePassReceiptStore()
        receipts.record(PassReceipt(signature = "sig", payer = payer.address, landedAtMillis = now))
        val pass = MockApi { request ->
            if (request.url.encodedPath.endsWith(PassApi.CONFIRM_PATH)) {
                respondJson("""{"error":"internal"}""", HttpStatusCode.InternalServerError)
            } else {
                respondJson(buildBody)
            }
        }
        val vm = machine(pass = pass, receipts = receipts)

        vm.pro.test {
            awaitUntil { !it.entitlementLoading }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("sig", vm.pro.value.pendingSignature)

        vm.pay()
        assertEquals("pay refuses outright while a payment is pending", PassState.Closed, vm.state.value)
        assertTrue(
            "no second payment is ever asked for while one is pending",
            pass.requests.none { it.url.encodedPath.endsWith(PassApi.BUILD_PATH) },
        )
    }

    // ---- The machine's own rules -------------------------------------------------------------

    @Test
    fun `confirm does nothing from any state that is not the confirm step`() = runTest {
        val operations = FakeAdapterOperations(signatures = listOf(ByteArray(64)))
        val session = wallet().apply { this.operations = operations }
        val vm = machine(wallet = session)

        vm.confirm()
        assertEquals(PassState.Closed, vm.state.value)
        assertTrue(operations.sendRequests.isEmpty())
    }

    @Test
    fun `close drops the attempt`() = runTest {
        val vm = machine()
        settle(vm)
        vm.close()
        assertEquals(PassState.Closed, vm.state.value)
    }

    @Test
    fun `retry runs the whole attempt again`() = runTest {
        val session = wallet()
        val pass = passMock()
        val vm = machine(pass = pass, wallet = session)

        vm.state.test {
            awaitItem()
            vm.pay()
            awaitUntil { it is PassState.Ready }
            session.enqueue(WalletOutcome.Cancelled)
            vm.confirm()
            awaitUntil { it is PassState.Refused }

            session.operations = FakeAdapterOperations(signatures = listOf(ByteArray(64) { 4 }))
            vm.retry()
            awaitUntil { it is PassState.Ready }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("a retry asks the server for a fresh transaction", 2, pass.requests.size)
    }

    /** Runs one attempt to whichever state the machine will not leave on its own. */
    private suspend fun settle(machine: PassViewModel): PassState {
        var settled: PassState = PassState.Closed
        machine.state.test {
            awaitItem()
            machine.pay()
            settled = awaitUntil { it is PassState.Ready || it is PassState.Landed || it is PassState.Refused }
            cancelAndIgnoreRemainingEvents()
        }
        return settled
    }
}
