package com.myapp.ui.swap

import app.cash.turbine.test
import com.myapp.MainDispatcherRule
import com.myapp.awaitUntil
import com.myapp.core.Clock
import com.myapp.data.Fixtures
import com.myapp.data.KnownMints
import com.myapp.data.MockApi
import com.myapp.data.bodyText
import com.myapp.data.jupiter.JupiterSwapApi
import com.myapp.data.net.HttpClientFactory
import com.myapp.data.respondJson
import com.myapp.wallet.FakeAdapterOperations
import com.myapp.wallet.FakeWalletSession
import com.myapp.wallet.WalletOutcome
import com.myapp.wallet.testAccount
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Base64

/**
 * The money path behind [WalletSession.call]: quote at the tap, sign in the wallet, and
 * either stop (debug) or land through /execute. Jupiter is a MockEngine on the recorded
 * fixtures; the wallet is [FakeWalletSession] with [FakeAdapterOperations] recording what
 * it was asked to sign.
 */
class SwapViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val seeker = testAccount()
    private val fixedClock = Clock { NOW }

    /** The golden 5 USDC -> TSLAx Metis order: transaction "UkVEQUNURUQ=", requestId ...cafe, no expiry. */
    private val unsignedBytes: ByteArray = Base64.getDecoder().decode("UkVEQUNURUQ=")
    private val signedBytes: ByteArray = "SIGNED-BY-THE-WALLET".encodeToByteArray()

    private var orderResponse = Fixtures.read("jupiter/order-usdc-tslax-5.json")
    private var orderStatus = HttpStatusCode.OK
    private var executeResponse = Fixtures.read("jupiter/execute-success.json")
    private var executeStatus = HttpStatusCode.OK

    private fun jupiter() = MockApi { request ->
        when (request.url.encodedPath) {
            "/swap/v2/order" -> respondJson(orderResponse, orderStatus)
            "/swap/v2/execute" -> respondJson(executeResponse, executeStatus)
            else -> respondJson("""{"error":"unexpected path"}""", HttpStatusCode.NotFound)
        }
    }

    private fun MockApi.orders() = requests.filter { it.url.encodedPath == "/swap/v2/order" }
    private fun MockApi.executes() = requests.filter { it.url.encodedPath == "/swap/v2/execute" }

    private fun viewModel(mock: MockApi, wallet: FakeWalletSession, submitSwaps: Boolean) =
        SwapViewModel(JupiterSwapApi(mock.client), wallet, fixedClock, submitSwaps = submitSwaps)

    @Test
    fun `debug build - connects, quotes at the tap for that taker, signs the order bytes, never executes`() = runTest {
        val mock = jupiter()
        val ops = FakeAdapterOperations(signedPayloads = listOf(signedBytes))
        val wallet = FakeWalletSession().apply {
            enqueue(WalletOutcome.Success(seeker)) // the connect
            operations = ops // the sign
        }
        val vm = viewModel(mock, wallet, submitSwaps = false)

        vm.state.test {
            assertEquals(SwapPhase.IDLE, awaitItem().phase)
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            val done = awaitUntil { it.phase == SwapPhase.SIGNED_NOT_SUBMITTED }

            assertEquals("Debug build: signed, not submitted", done.message)
            assertEquals(REQUEST_ID, done.order?.requestId)
            assertEquals(5_000_000L, done.order?.inAmountRaw)
            assertEquals("TSLAx", done.outputSymbol)
            assertNull(done.signature)
            assertFalse(done.needsFreshOrder)
            assertFalse(done.isBusy)
            assertEquals(NOW, done.phaseStartedAtMillis)

            // One quote, at the tap, built for the authorized account.
            val order = mock.orders().single()
            assertEquals(HttpMethod.Get, order.method)
            assertEquals(KnownMints.USDC, order.url.parameters["inputMint"])
            assertEquals(KnownMints.TSLAX, order.url.parameters["outputMint"])
            assertEquals("5000000", order.url.parameters["amount"])
            assertEquals(seeker.address, order.url.parameters["taker"])

            // The wallet was asked to sign exactly the order's transaction, once.
            assertEquals(1, wallet.connectCount)
            assertEquals(1, wallet.callCount)
            assertTrue(unsignedBytes.contentEquals(ops.signRequests.single().single()))

            // SUBMIT_SWAPS=false: nothing reached /execute.
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with SUBMIT_SWAPS - the signed bytes and the requestId go to execute, and the swap lands`() = runTest {
        val mock = jupiter()
        val ops = FakeAdapterOperations(signedPayloads = listOf(signedBytes))
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            operations = ops
        }
        val vm = viewModel(mock, wallet, submitSwaps = true)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            val landed = awaitUntil { it.phase == SwapPhase.LANDED }

            val expectedSignature = HttpClientFactory.json.parseToJsonElement(executeResponse)
                .jsonObject["signature"]!!.jsonPrimitive.content
            assertEquals(expectedSignature, landed.signature)
            assertFalse(landed.needsFreshOrder)
            assertEquals(0, wallet.connectCount) // already authorized: no extra wallet round-trip

            val execute = mock.executes().single()
            assertEquals(HttpMethod.Post, execute.method)
            val body = HttpClientFactory.json.parseToJsonElement(execute.bodyText()).jsonObject
            assertEquals(Base64.getEncoder().encodeToString(signedBytes), body["signedTransaction"]!!.jsonPrimitive.content)
            assertEquals(REQUEST_ID, body["requestId"]!!.jsonPrimitive.content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `declining to sign is Cancelled, and nothing is submitted`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            enqueue(WalletOutcome.Cancelled) // the sign, scripted (no operations)
        }
        val vm = viewModel(mock, wallet, submitSwaps = true)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            val state = awaitUntil { it.phase == SwapPhase.CANCELLED }
            assertEquals("Cancelled in wallet", state.message)
            assertEquals(REQUEST_ID, state.order?.requestId)
            assertEquals(1, mock.orders().size)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the connect never asks for a quote`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Cancelled) }
        val vm = viewModel(mock, wallet, submitSwaps = false)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            assertEquals("Cancelled in wallet", awaitUntil { it.phase == SwapPhase.CANCELLED }.message)
            assertTrue(mock.requests.isEmpty())
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refused quote fails before the wallet is asked`() = runTest {
        orderResponse = Fixtures.read("jupiter/order-error-400.json")
        orderStatus = HttpStatusCode.BadRequest
        val mock = jupiter()
        val wallet = FakeWalletSession().apply { connectedAs(seeker) }
        val vm = viewModel(mock, wallet, submitSwaps = true)

        vm.state.test {
            awaitItem()
            vm.start("NotAnXStockMint".padEnd(44, '1'), "NOPEx", 5_000_000L)
            val failed = awaitUntil { it.phase == SwapPhase.FAILED }
            assertTrue(failed.message!!, failed.message!!.contains("Invalid outputMint"))
            assertFalse(failed.needsFreshOrder)
            assertNull(failed.order)
            assertEquals(0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `execute answering Failed with -2003 asks for a fresh order and a second approval`() = runTest {
        executeResponse = """{"status":"Failed","code":-2003,"error":"Quote expired"}"""
        val mock = jupiter()
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            operations = FakeAdapterOperations(signedPayloads = listOf(signedBytes))
        }
        val vm = viewModel(mock, wallet, submitSwaps = true)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            val failed = awaitUntil { it.phase == SwapPhase.FAILED }
            assertTrue(failed.needsFreshOrder)
            assertTrue(failed.message!!, failed.message!!.contains("quote expired"))
            assertNull(failed.signature)
            assertEquals(1, mock.executes().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a non-2xx from execute is a failure that does not ask for a fresh order`() = runTest {
        executeResponse = Fixtures.read("jupiter/execute-error-400.json")
        executeStatus = HttpStatusCode.BadRequest
        val mock = jupiter()
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            operations = FakeAdapterOperations(signedPayloads = listOf(signedBytes))
        }
        val vm = viewModel(mock, wallet, submitSwaps = true)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            val failed = awaitUntil { it.phase == SwapPhase.FAILED }
            assertFalse(failed.needsFreshOrder)
            assertTrue(failed.message!!, failed.message!!.contains("Failed to decode signed transaction"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet that signs nothing is a failure, not a crash`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            operations = FakeAdapterOperations(signedPayloads = emptyList())
        }
        val vm = viewModel(mock, wallet, submitSwaps = true)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            val failed = awaitUntil { it.phase == SwapPhase.FAILED }
            assertEquals("The wallet returned no signed transaction", failed.message)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset returns to idle`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Cancelled) }
        val vm = viewModel(mock, wallet, submitSwaps = false)

        vm.state.test {
            awaitItem()
            vm.start(KnownMints.TSLAX, "TSLAx", 5_000_000L)
            awaitUntil { it.phase == SwapPhase.CANCELLED }
            vm.reset()
            assertEquals(SwapUiState(), awaitUntil { it.phase == SwapPhase.IDLE })
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val NOW = 1_757_600_000_000L
        const val REQUEST_ID = "01a08b00-0000-7000-8000-00000000cafe"
    }
}
