package com.plainticker.mobile.ui.swap

import app.cash.turbine.test
import com.plainticker.mobile.MainDispatcherRule
import com.plainticker.mobile.awaitUntil
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.MockApi
import com.plainticker.mobile.data.bodyText
import com.plainticker.mobile.data.jupiter.JupiterSwapApi
import com.plainticker.mobile.data.jupiter.SwapError
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.receipts.FakeReceiptStore
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.TokenBalance
import com.plainticker.mobile.repo.FakeRpcRepository
import com.plainticker.mobile.wallet.FakeAdapterOperations
import com.plainticker.mobile.wallet.FakeWalletSession
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.testAccount
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
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
 * Every transition of the swap machine, asserted as a transition.
 *
 * Jupiter is a MockEngine over the recorded orders, the wallet is [FakeWalletSession] with
 * [FakeAdapterOperations] recording what it was asked to sign, the chain is [FakeRpcRepository]
 * and the receipts are a [FakeReceiptStore] that counts every write. Nothing here asserts on a
 * sentence: the copy is the sheet's, the machine is what has to be right.
 *
 * The clock advances a fixed [TICK] on every read, so a phase that was entered and closed once
 * measures exactly TICK and a phase entered twice measures twice that. The one automatic requote
 * is proved that way: a landing whose wallet phase measures 2 x TICK went through two approvals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwapViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val seeker = testAccount()
    private val tslax = SwapToken(KnownMints.TSLAX, "TSLAx", 8)

    private var now = START
    private val clock = Clock {
        now += TICK
        now
    }

    /** The golden 2026-09-12 order: 5 USDC into TSLAx, rentFeeLamports 1,488,440, no expiry. */
    private var orderResponse = Fixtures.read("jupiter/order-usdc-tslax-5-rent.json")
    private var orderStatus = HttpStatusCode.OK

    /** Answers for /execute in order; the last one repeats once the list runs out. */
    private var executePlan: List<Pair<String, HttpStatusCode>> = listOf(LANDED to HttpStatusCode.OK)
    private var executeIndex = 0

    private val unsignedBytes: ByteArray = Base64.getDecoder().decode("UkVEQUNURUQ=")
    private val signedBytes: ByteArray = "SIGNED-BY-THE-WALLET".encodeToByteArray()

    private val receipts = FakeReceiptStore()

    private fun jupiter() = MockApi { request ->
        when (request.url.encodedPath) {
            "/swap/v2/order" -> respondJson(orderResponse, orderStatus)
            "/swap/v2/execute" -> {
                val answer = executePlan[minOf(executeIndex, executePlan.lastIndex)]
                executeIndex++
                respondJson(answer.first, answer.second)
            }
            else -> respondJson("""{"error":"unexpected path"}""", HttpStatusCode.NotFound)
        }
    }

    private fun MockApi.orders() = requests.filter { it.url.encodedPath == "/swap/v2/order" }
    private fun MockApi.executes() = requests.filter { it.url.encodedPath == "/swap/v2/execute" }

    private fun balance(mint: String, raw: Long, decimals: Int) = TokenBalance(
        tokenAccount = "token-account-for-$mint",
        mint = mint,
        owner = seeker.address,
        amountRaw = raw,
        decimals = decimals,
        uiAmountString = null,
        programId = if (mint == KnownMints.USDC) KnownPrograms.TOKEN else KnownPrograms.TOKEN_2022,
    )

    /** The demo wallet as measured on 2026-09-12: 20.2 USDC, 0.096 SOL, no xStock account. */
    private fun chain(
        lamports: Long = 96_000_000L,
        usdcRaw: Long = 20_200_000L,
        tokenRaw: Long = 0L,
    ) = FakeRpcRepository(
        balances = Result.success(
            buildList {
                if (usdcRaw > 0L) add(balance(KnownMints.USDC, usdcRaw, 6))
                if (tokenRaw > 0L) add(balance(KnownMints.TSLAX, tokenRaw, 8))
            }
        ),
        lamports = Result.success(lamports),
    )

    private fun wallet(signs: Boolean = true): FakeWalletSession = FakeWalletSession().apply {
        connectedAs(seeker)
        if (signs) operations = FakeAdapterOperations(signedPayloads = listOf(signedBytes))
    }

    private fun viewModel(
        mock: MockApi,
        wallet: FakeWalletSession,
        rpc: FakeRpcRepository = chain(),
        submitSwaps: Boolean = true,
        receipts: FakeReceiptStore = this.receipts,
    ) = SwapViewModel(
        swapApi = JupiterSwapApi(mock.client),
        wallet = wallet,
        rpc = rpc,
        receipts = receipts,
        clock = clock,
        submitSwaps = submitSwaps,
        debugLog = { line -> logged += line },
        // The receipt write stays on the test scheduler, so virtual time still orders it.
        ioDispatcher = mainDispatcher.dispatcher,
    )

    private val logged = mutableListOf<String>()

    // ---- Closed -> Opening -> Amount -----------------------------------------------------------

    @Test
    fun `open reads the wallet and stops at the amount step, asking Jupiter nothing`() = runTest {
        val mock = jupiter()
        val vm = viewModel(mock, wallet())

        vm.state.test {
            assertEquals(SwapState.Closed(), awaitItem())
            vm.open(tslax)
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount

            assertEquals(KnownMints.USDC, amount.leg.input.mint)
            assertEquals(KnownMints.TSLAX, amount.leg.output.mint)
            assertEquals(20_200_000L, amount.balanceRaw)
            assertEquals(96_000_000L, amount.funds.lamports)
            assertEquals(AmountProblem.EMPTY, amount.input.problem)
            assertFalse(amount.canSubmit)
            assertFalse("the wallet has no TSLAx, so there is nothing to send back", amount.canFlip)
            assertTrue("no quote before the tap", mock.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling the connect closes the sheet with a neutral note and no failure`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Cancelled) }
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            val closed = awaitUntil { it is SwapState.Closed && it.note != null } as SwapState.Closed
            assertEquals(SwapNote.CANCELLED_IN_WALLET, closed.note)
            assertTrue(mock.requests.isEmpty())
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a chain that cannot be read fails before the amount step`() = runTest {
        val mock = jupiter()
        val rpc = chain().apply { lamports = Result.failure(IllegalStateException("forwarder down")) }
        val vm = viewModel(mock, wallet(), rpc = rpc)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.CHAIN_UNREAD, failed.reason)
            assertNull(failed.funds)
            assertTrue(mock.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Amount -> Amount ----------------------------------------------------------------------

    @Test
    fun `an amount over the balance is refused before any network call`() = runTest {
        val mock = jupiter()
        val vm = viewModel(mock, wallet())

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            awaitUntil { it is SwapState.Amount }

            vm.amountChanged("20.3")
            val over = awaitUntil { it is SwapState.Amount && it.input.text == "20.3" } as SwapState.Amount
            assertEquals(AmountProblem.ABOVE_BALANCE, over.input.problem)
            assertFalse(over.canSubmit)

            vm.submit()
            runCurrent()
            assertTrue("an over-balance amount never becomes a request", mock.requests.isEmpty())
            assertTrue(receipts.writes.isEmpty())
            assertTrue("the machine did not leave the amount step", vm.state.value is SwapState.Amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `zero is refused and Max fills the whole balance exactly`() = runTest {
        val mock = jupiter()
        val vm = viewModel(mock, wallet())

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            awaitUntil { it is SwapState.Amount }

            vm.amountChanged("0")
            val zero = awaitUntil { it is SwapState.Amount && it.input.text == "0" } as SwapState.Amount
            assertEquals(AmountProblem.NOT_ABOVE_ZERO, zero.input.problem)
            vm.submit()
            runCurrent()
            assertTrue(mock.requests.isEmpty())

            vm.useMax()
            val max = awaitUntil { it is SwapState.Amount && it.input.isUsable } as SwapState.Amount
            assertEquals("20.2", max.input.text)
            assertEquals(20_200_000L, max.input.raw)
            assertTrue(max.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the flip is the same machine with the mints exchanged, and only when the token is there`() = runTest {
        val mock = jupiter()
        val vm = viewModel(mock, wallet(), rpc = chain(tokenRaw = 1_366_141L))

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            val into = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertTrue(into.canFlip)
            assertEquals("USDC", into.leg.input.symbol)
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }

            vm.flip()
            val back = awaitUntil { it is SwapState.Amount && it.leg.input.mint == KnownMints.TSLAX } as SwapState.Amount
            assertEquals(KnownMints.USDC, back.leg.output.mint)
            assertEquals(8, back.leg.input.decimals)
            assertEquals("the balance is now the token's", 1_366_141L, back.balanceRaw)
            assertEquals("the typed amount was in the other side's units", AmountInput.EMPTY, back.input)
            assertEquals("the token is still the xStock, either way round", tslax.mint, back.leg.token.mint)

            // And the quote it asks for is the flipped pair.
            vm.useMax()
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            vm.submit()
            awaitUntil { it is SwapState.Terminal }
            val order = mock.orders().single()
            assertEquals(KnownMints.TSLAX, order.url.parameters["inputMint"])
            assertEquals(KnownMints.USDC, order.url.parameters["outputMint"])
            assertEquals("1366141", order.url.parameters["amount"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the flip is not offered when the wallet has none of the token`() = runTest {
        val mock = jupiter()
        val vm = viewModel(mock, wallet())

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertFalse(amount.canFlip)
            vm.flip()
            runCurrent()
            assertEquals(KnownMints.USDC, (vm.state.value as SwapState.Amount).leg.input.mint)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The shortfall, before the wallet opens --------------------------------------------------

    @Test
    fun `a SOL shortfall is its own state, from the quote's own three fields, before any approval`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        // Enough for the signature and the priority fee, not for the token account rent.
        val vm = viewModel(mock, wallet, rpc = chain(lamports = 1_000_000L))

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            awaitUntil { it is SwapState.Amount }
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            vm.submit()

            val short = awaitUntil { it is SwapState.Shortfall } as SwapState.Shortfall
            assertEquals(5_000L, short.need.signatureFeeLamports)
            assertEquals(1_488_440L, short.need.rentFeeLamports)
            assertEquals(1_450L, short.need.prioritizationFeeLamports)
            assertEquals("the requirement is the quote's three fields summed", 1_494_890L, short.need.totalLamports)
            assertEquals(1_000_000L, short.haveLamports)
            assertEquals(494_890L, short.missingLamports)
            assertTrue(
                "the plan's 2039280 constant is not what this quote charges",
                short.need.rentFeeLamports != 2_039_280L,
            )

            assertEquals("one quote was fetched", 1, mock.orders().size)
            assertEquals("the wallet never opened", 0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            assertTrue(receipts.writes.isEmpty())

            // And the way out is back to the amount, with what was typed still there.
            vm.edit()
            val back = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals("5", back.input.text)
            assertTrue(back.canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet that can pay the quote's SOL is asked to sign`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, rpc = chain(lamports = 1_494_890L))

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            assertEquals("exactly covered is covered", 1, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The landing ---------------------------------------------------------------------------

    @Test
    fun `a clean landing reports the fill, not the estimate, and writes one receipt`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val ops = wallet.operations!!
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed

            // The quote is the estimate; the fill is what the chain did, and they differ.
            assertEquals(1_360_437L, landed.quote.outAmountRaw)
            assertEquals(1_360_941L, landed.fill.outAmountRaw)
            assertEquals(0.037, landed.fillDeltaPct!!, 0.001)
            assertEquals(SIGNATURE, landed.fill.signature)
            assertEquals(367_000_000L, landed.fill.slot)
            assertFalse(landed.requoted)

            // The cost is the quote's, corrected by the fill: it beat the quote, so it cost less.
            assertEquals(0.586, landed.quote.allInCostPct!!, 0.001)
            assertTrue(landed.allInCostPaidPct!! < landed.quote.allInCostPct)
            assertEquals(0.549, landed.allInCostPaidPct!!, 0.001)

            // The worst case is the quote's own threshold, stated beside the estimate.
            assertEquals(1_346_933L, landed.quote.worstCaseOutRaw)
            assertEquals(100, landed.quote.slippageBps)
            assertEquals("Metis", landed.quote.route)
            assertFalse("the taker pays, so no SOL is never the pitch", landed.quote.gasless)
            assertFalse("a Metis order carries no expireAt, which is not an expiry of zero", landed.quote.hasExpiry)
            assertNull(landed.quote.secondsLeft(START / 1000))

            // One quote, one approval, one submit, one receipt.
            assertEquals(1, mock.orders().size)
            assertEquals(1, wallet.callCount)
            assertEquals(1, mock.executes().size)
            assertTrue(unsignedBytes.contentEquals(ops.signRequests.single().single()))

            assertEquals(1, receipts.writes.size)
            val receipt = receipts.receipts.value.single()
            assertEquals(SIGNATURE, receipt.signature)
            assertEquals(KnownMints.USDC, receipt.inputMint)
            assertEquals(KnownMints.TSLAX, receipt.outputMint)
            assertEquals(5_000_000L, receipt.inputAmountRaw)
            assertEquals(6, receipt.inputDecimals)
            assertEquals("the receipt records the fill, never the quote", 1_360_941L, receipt.outputAmountRaw)
            assertEquals(8, receipt.outputDecimals)
            assertEquals(landed.allInCostPaidPct!!, receipt.allInCostPct!!, 1e-9)
            assertEquals("Metis", receipt.route)
            assertTrue(receipt.landedAtMillis >= START)

            // The executed transaction is the bytes the wallet signed, against this requestId.
            val body = HttpClientFactory.json.parseToJsonElement(mock.executes().single().bodyText()).jsonObject
            assertEquals(HttpMethod.Post, mock.executes().single().method)
            assertEquals(Base64.getEncoder().encodeToString(signedBytes), body["signedTransaction"]!!.jsonPrimitive.content)
            assertEquals(REQUEST_ID, body["requestId"]!!.jsonPrimitive.content)

            // Each phase was entered and closed once.
            assertEquals(TICK, landed.timing.quotingMillis)
            assertEquals(TICK, landed.timing.walletMillis)
            assertEquals(TICK, landed.timing.landingMillis)
            assertEquals(3 * TICK, landed.timing.measuredMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a debug build signs and stops, submitting nothing and writing no receipt`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, submitSwaps = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val signed = awaitUntil { it is SwapState.Signed } as SwapState.Signed

            assertEquals(REQUEST_ID, signed.quote.requestId)
            assertEquals(1, wallet.callCount)
            assertTrue("SUBMIT_SWAPS is false, so nothing reached execute", mock.executes().isEmpty())
            assertTrue("no landing, no receipt", receipts.writes.isEmpty())
            assertEquals(TICK, signed.timing.walletMillis)
            assertNull("there was no landing phase to measure", signed.timing.landingMillis)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The wallet ----------------------------------------------------------------------------

    /**
     * The approval round trip, in the three shapes it can come back in without a signature.
     *
     * On the Seeker on 2026-09-13 the attempt that was not approved and the one that was looked
     * identical in the log up to the last line: "Encrypted session established" at 11:01:04 and
     * "mobile-wallet-adapter session closed" at 11:01:42, against 11:08:09 and 11:08:19 for the
     * swap that landed at slot 446,653,478. The only difference the app can see is whether a
     * signature came back, never why the session ended, so all three are the same answer: nothing
     * was signed, nothing was sent, back to the amount with it intact.
     */
    @Test
    fun `an approval that comes back without a signature is a cancellation, whatever ended it`() = runTest {
        val declined = FakeWalletSession().apply {
            connectedAs(seeker)
            enqueue(WalletOutcome.Cancelled)
        }
        val sessionClosed = FakeWalletSession().apply {
            connectedAs(seeker)
            enqueue(WalletOutcome.Error("mobile-wallet-adapter session closed"))
        }
        val nothingSigned = FakeWalletSession().apply {
            connectedAs(seeker)
            operations = FakeAdapterOperations(signedPayloads = emptyList())
        }

        listOf(
            "declined in the wallet" to declined,
            "the session closed" to sessionClosed,
            "the wallet signed nothing" to nothingSigned,
        ).forEach { (what, wallet) ->
            val mock = jupiter()
            val store = FakeReceiptStore()
            val vm = viewModel(mock, wallet, receipts = store)

            vm.state.test {
                awaitItem()
                submitFive(vm, this)
                val back = awaitUntil { it is SwapState.Amount && it.note != null } as SwapState.Amount

                assertEquals(what, SwapNote.NOT_APPROVED, back.note)
                assertEquals("$what: the typed amount survived", "5", back.input.text)
                assertEquals(what, 5_000_000L, back.input.raw)
                assertTrue("$what: and it can be sent again", back.canSubmit)
                assertEquals("$what: nothing was spent", 20_200_000L, back.balanceRaw)
                assertEquals(what, 1, mock.orders().size)
                assertTrue("$what: nothing was sent", mock.executes().isEmpty())
                assertTrue("$what: nothing landed, so nothing was recorded", store.writes.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `a wallet error on the approval keeps its own words in the debug log`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            enqueue(WalletOutcome.Error("association failed: SocketTimeoutException"))
        }
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val back = awaitUntil { it is SwapState.Amount && it.note != null } as SwapState.Amount
            assertEquals(SwapNote.NOT_APPROVED, back.note)
            assertTrue("the upstream words are logged, never drawn", logged.any { "SocketTimeoutException" in it })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The quote refusing --------------------------------------------------------------------

    @Test
    fun `a refused quote never reaches the wallet and never shows the upstream words`() = runTest {
        orderResponse = Fixtures.read("jupiter/order-error-400.json")
        orderStatus = HttpStatusCode.BadRequest
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed

            assertEquals(SwapFailure.QUOTE_REFUSED, failed.reason)
            assertNull(failed.quote)
            assertEquals(0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            assertEquals("the amount survives a refusal", "5", failed.input?.text)

            // The raw upstream sentence exists, in the log and nowhere else.
            assertTrue(logged.any { "Invalid outputMint" in it })

            vm.edit()
            assertTrue(awaitUntil { it is SwapState.Amount } is SwapState.Amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a gateway page at the order stage is unavailable, not a verdict on the pair`() = runTest {
        // A 502 with no structured body knows nothing about this pair; saying it cannot be
        // quoted at this size would be a claim the answer never made.
        orderResponse = "<html><body>502 Bad Gateway</body></html>"
        orderStatus = HttpStatusCode.BadGateway
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.QUOTE_UNAVAILABLE, failed.reason)
            assertEquals(0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bytes this app cannot decode fail before the wallet is ever opened`() = runTest {
        orderResponse = orderResponse.replace(TRANSACTION_FIELD, """"transaction": "not base64 !!",""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.NO_TRANSACTION, failed.reason)
            assertEquals("our own payload is not the wallet's fault", 0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an order with no transaction to sign is a failure, not an approval`() = runTest {
        orderResponse = orderResponse.replace(""""transaction": "UkVEQUNURUQ=",""", """"transaction": null,""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.NO_TRANSACTION, failed.reason)
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The one automatic requote ---------------------------------------------------------------

    @Test
    fun `each requotable code earns exactly one fresh quote and a second approval`() = runTest {
        listOf(
            SwapError.CODE_NOT_FULLY_SIGNED,
            SwapError.CODE_QUOTE_EXPIRED,
            SwapError.CODE_REJECTED_BY_MAKER,
        ).forEach { code ->
            resetPerCase()
            executePlan = listOf(
                refusal(code) to HttpStatusCode.OK,
                LANDED to HttpStatusCode.OK,
            )
            val mock = jupiter()
            val wallet = wallet()
            val vm = viewModel(mock, wallet)

            vm.state.test {
                awaitItem()
                submitFive(vm, this)
                val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed

                assertTrue("code $code should requote", landed.requoted)
                assertEquals("code $code: a fresh order", 2, mock.orders().size)
                assertEquals("code $code: a second approval", 2, wallet.callCount)
                assertEquals("code $code: two submits", 2, mock.executes().size)
                assertEquals("code $code: two quoting phases", 2 * TICK, landed.timing.quotingMillis)
                assertEquals("code $code: two wallet round trips", 2 * TICK, landed.timing.walletMillis)
                assertEquals("code $code: one landing, one receipt", 1, receipts.writes.size)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `a second requotable failure after the requote is terminal for the attempt`() = runTest {
        executePlan = listOf(refusal(SwapError.CODE_QUOTE_EXPIRED) to HttpStatusCode.OK)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed

            assertEquals(SwapFailure.QUOTE_GONE, failed.reason)
            assertTrue(failed.requoted)
            assertEquals("two quotes and no more", 2, mock.orders().size)
            assertEquals("two approvals and no more", 2, wallet.callCount)
            assertEquals(2, mock.executes().size)
            assertTrue("nothing landed, so nothing was recorded", receipts.writes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a code that is not requotable is terminal at once`() = runTest {
        executePlan = listOf(Fixtures.read("jupiter/execute-error-400.json") to HttpStatusCode.BadRequest)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed

            assertEquals(SwapFailure.SWAP_REFUSED, failed.reason)
            assertFalse(failed.requoted)
            assertEquals("no fresh quote for a code a requote cannot cure", 1, mock.orders().size)
            assertEquals(1, wallet.callCount)
            assertTrue(logged.any { "Failed to decode signed transaction" in it })
            assertTrue(receipts.writes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an execute answer with no code claims neither that it landed nor that it did not`() = runTest {
        executePlan = listOf("<html><body>502 Bad Gateway</body></html>" to HttpStatusCode.BadGateway)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed

            assertEquals(SwapFailure.SUBMIT_UNAVAILABLE, failed.reason)
            assertFalse("a gateway page is not a quote going", failed.requoted)
            assertEquals(1, mock.executes().size)
            assertTrue("nothing is recorded without a signature", receipts.writes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a success with no signature claims neither that it landed nor that it did not`() = runTest {
        // The answer went missing, not the swap: it was submitted and may well have landed, so
        // "Nothing was swapped" would be the one sentence this screen must never get wrong.
        executePlan = listOf("""{"status":"Success"}""" to HttpStatusCode.OK)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.SUBMIT_UNAVAILABLE, failed.reason)
            assertTrue("nothing is recorded without a signature", receipts.writes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an answer that reports no fill records the fill as unknown, never the estimate`() = runTest {
        executePlan = listOf(LANDED_NO_AMOUNTS to HttpStatusCode.OK)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed

            assertNull("the estimate is not the fill", landed.fill.outAmountRaw)
            assertNull(landed.fillDeltaPct)
            assertNull(landed.allInCostPaidPct)
            assertEquals("the quote still knows its own estimate", 1_360_437L, landed.quote.outAmountRaw)

            val receipt = receipts.receipts.value.single()
            assertNull("an unknown fill is recorded as unknown", receipt.outputAmountRaw)
            assertNull(receipt.allInCostPct)
            assertEquals(SIGNATURE, receipt.signature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an order with no threshold states the floor its own slippage sets, not the estimate`() = runTest {
        orderResponse = orderResponse.replace(THRESHOLD_FIELD, THRESHOLD_FIELD_IGNORED)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            // 1,360,437 less the order's own 100 bps, floored. Never the estimate itself, which
            // would promise a minimum this quote made no promise about.
            assertEquals(1_346_832L, landed.quote.worstCaseOutRaw)
            assertTrue(landed.quote.worstCaseOutRaw < landed.quote.outAmountRaw)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an order that priced neither side in dollars leaves the cost unknown, not zero`() = runTest {
        orderResponse = orderResponse
            .replace(""""inUsdValue": 5.0,""", """"inUsdValue": 0.0,""")
            .replace(""""outUsdValue": 4.9707,""", """"outUsdValue": 0.0,""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            assertNull("zero would read as a swap that cost nothing", landed.quote.allInCostPct)
            assertNull(landed.allInCostPaidPct)
            assertNull(receipts.receipts.value.single().allInCostPct)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The receipt ---------------------------------------------------------------------------

    @Test
    fun `one landing writes one receipt, and a second landing writes a second`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            assertEquals(1, receipts.writes.size)

            // A second attempt that lands the same signature is still one row in the record.
            vm.close()
            awaitUntil { it is SwapState.Closed }
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            assertEquals("recorded twice", 2, receipts.writes.size)
            assertEquals("one row, because the signature is the identity", 1, receipts.receipts.value.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Helpers -------------------------------------------------------------------------------

    /** Opens the sheet, types 5, and submits; leaves the machine running. */
    private suspend fun submitFive(vm: SwapViewModel, turbine: app.cash.turbine.ReceiveTurbine<SwapState>) {
        vm.open(tslax)
        turbine.awaitUntil { it is SwapState.Amount }
        vm.amountChanged("5")
        turbine.awaitUntil { it is SwapState.Amount && it.input.isUsable }
        vm.submit()
    }

    private fun resetPerCase() {
        executeIndex = 0
        receipts.clear()
        receipts.writes.clear()
        logged.clear()
    }

    private fun refusal(code: Int) = """{"status":"Failed","code":$code,"error":"upstream words a reader cannot act on"}"""

    private companion object {
        const val START = 1_757_600_000_000L
        const val TICK = 100L
        const val REQUEST_ID = "01a08b00-0000-7000-8000-00000000f00d"

        /** 88 characters of base58 padding, a placeholder and never a real signature. */
        val SIGNATURE = "1".repeat(88)

        /** The 2026-09-12 fixture's own two fields, so a test can take one away by name. */
        const val TRANSACTION_FIELD = """"transaction": "UkVEQUNURUQ=","""
        const val THRESHOLD_FIELD = """"otherAmountThreshold": "1346933","""

        /** The same line under a key the parser ignores, which is how a field is taken away. */
        const val THRESHOLD_FIELD_IGNORED = """"_noOtherAmountThreshold": "1346933","""

        /** A landing whose answer carried no amounts at all: the fill is unknown, not the quote. */
        val LANDED_NO_AMOUNTS = """{"status":"Success","signature":"${"1".repeat(88)}","slot":"367000000"}"""

        /** The fill beat the 1,360,437 estimate by 0.037 percent, as a real landing did. */
        val LANDED = """{"status":"Success","signature":"${"1".repeat(88)}","slot":"367000000","inputAmountResult":"5000000","outputAmountResult":"1360941"}"""
    }
}
