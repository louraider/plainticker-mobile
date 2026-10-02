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
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.receipts.FakeReceiptStore
import com.plainticker.mobile.data.respondJson
import com.plainticker.mobile.data.rpc.TokenBalance
import com.plainticker.mobile.repo.FakePriceRepository
import com.plainticker.mobile.repo.FakeRpcRepository
import com.plainticker.mobile.repo.FakeSecondSource
import com.plainticker.mobile.repo.agreeingSecondRead
import com.plainticker.mobile.repo.mintFacts
import com.plainticker.mobile.repo.price
import com.plainticker.mobile.repo.scaled
import com.plainticker.mobile.wallet.FakeAdapterOperations
import com.plainticker.mobile.wallet.FakeWalletSession
import com.plainticker.mobile.wallet.TransactionGuard
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.wallet.WireMessage
import com.plainticker.mobile.wallet.leData
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.testAccount
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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

    /**
     * A REAL Jupiter transaction (the 2026-09-23 taker-pays Metis order for 5 USDC into TSLAx) with
     * its taker key swapped for [seeker]'s, so the bytes are ones [TransactionGuard] reads as this
     * wallet's own. The golden order below carried "REDACTED" in place of a transaction, which the
     * guard now refuses before the wallet (security audit, finding 2), as it must.
     *
     * The taker's two token accounts are swapped for [seeker]'s as well (2026-09-24): the guard
     * now reads the route's own accounts and requires the one spent from and the one paid into
     * to be the wallet's own for the two mints, so a transaction whose taker is the seeker but
     * whose accounts are still the original taker's is, correctly, refused.
     */
    private val unsignedBase64: String = withGoldenQuote(
        asSeeker(
            WireMessage.parseBase64(
                Fixtures.read("jupiter/order-usdc-tslax-5-metis-taker-pays.json")
                    .let { HttpClientFactory.json.decodeFromString(SwapOrder.serializer(), it) }.transaction!!,
            ),
            REAL_TAKER,
        ),
    ).base64()

    /**
     * [m] with route_v2's quoted_out_amount set to the golden order's own (mock judges' review,
     * 2026-09-26). The guard now reads the output terms from the bytes and holds them to the floor
     * the sheet shows, so bytes from the 2026-09-23 order (quoted 1,319,475, floor 1,306,280) next
     * to the 2026-09-12 golden JSON (floor 1,346,933) are, correctly, refused. 1,360,539 at the
     * golden order's 100 bps floors to exactly its otherAmountThreshold, as every real order does.
     */
    private fun withGoldenQuote(m: WireMessage): WireMessage {
        val j = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.JUPITER_AGGREGATOR_V6 }
        return m.mapInstruction(j) { ix -> ix.copy(data = ix.data.copyOf().also { leData(GOLDEN_QUOTED_OUT).copyInto(it, 16) }) }
    }

    /** [m] with [realTaker] and its USDC and TSLAx token accounts replaced by [seeker]'s own. */
    private fun asSeeker(m: WireMessage, realTaker: String): WireMessage = kotlinx.coroutines.runBlocking {
        m.replaceKey(realTaker, seeker.address)
            .replaceKey(
                TransactionGuard.ata(realTaker, KnownMints.USDC, KnownPrograms.TOKEN),
                TransactionGuard.ata(seeker.address, KnownMints.USDC, KnownPrograms.TOKEN),
            )
            .replaceKey(
                TransactionGuard.ata(realTaker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022),
                TransactionGuard.ata(seeker.address, KnownMints.TSLAX, KnownPrograms.TOKEN_2022),
            )
            // The reverse route's wrapped-SOL account and its pool-token hop account, which the
            // guard requires to be the wallet's own since 2026-09-27.
            .replaceKey(
                TransactionGuard.ata(realTaker, KnownMints.WSOL, KnownPrograms.TOKEN),
                TransactionGuard.ata(seeker.address, KnownMints.WSOL, KnownPrograms.TOKEN),
            )
            .replaceKey(
                TransactionGuard.ata(realTaker, REVERSE_HOP_MINT, KnownPrograms.TOKEN),
                TransactionGuard.ata(seeker.address, REVERSE_HOP_MINT, KnownPrograms.TOKEN),
            )
    }

    /** The golden order's own transaction line, as this file serves it. */
    private val transactionField = """"transaction": "$unsignedBase64","""

    /**
     * The golden 2026-09-12 order: 5 USDC into TSLAx, rentFeeLamports 1,488,440, no expiry, with
     * the redacted transaction and taker filled in for [seeker] (see [unsignedBase64]).
     */
    private var orderResponse = Fixtures.read("jupiter/order-usdc-tslax-5-rent.json")
        .replace(REDACTED_TRANSACTION_FIELD, transactionField)
        .replace(REDACTED_TAKER_FIELD, """"taker": "${seeker.address}",""")
    private var orderStatus = HttpStatusCode.OK

    /** Thrown by the transport in place of any /order answer: the phone has no network. */
    private var orderTransportFailure: Throwable? = null

    /** Answers for /execute in order; the last one repeats once the list runs out. */
    private var executePlan: List<Pair<String, HttpStatusCode>> = listOf(LANDED to HttpStatusCode.OK)
    private var executeIndex = 0

    private val unsignedBytes: ByteArray = Base64.getDecoder().decode(unsignedBase64)
    /**
     * What a wallet hands back: the same message, with the seeker's signature in its slot (slot 0,
     * the taker pays). The swap machine now compares the two before /execute (mock judges' review,
     * 2026-09-26), so an arbitrary byte string no longer stands in for a signed transaction.
     */
    private val signedBytes: ByteArray = signAsSeeker(unsignedBytes)

    private fun signAsSeeker(tx: ByteArray): ByteArray = tx.copyOf().also { java.util.Arrays.fill(it, 1, 65, 0x5A) }

    private val receipts = FakeReceiptStore()

    private fun jupiter() = MockApi { request ->
        when (request.url.encodedPath) {
            "/swap/v2/order" -> {
                orderTransportFailure?.let { throw it }
                respondJson(orderResponse, orderStatus)
            }
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
        if (signs) operations = FakeAdapterOperations(sign = ::signAsSeeker)
    }

    private fun viewModel(
        mock: MockApi,
        wallet: FakeWalletSession,
        rpc: FakeRpcRepository = chain(),
        submitSwaps: Boolean = true,
        receipts: FakeReceiptStore = this.receipts,
        mints: com.plainticker.mobile.repo.MintRepository? = null,
        secondSource: com.plainticker.mobile.repo.SecondSource = FakeSecondSource(),
        prices: com.plainticker.mobile.repo.PriceRepository = tslaxPriced(),
        /**
         * Taps "Continue to wallet" whenever the machine stops at Review (mock judges' review,
         * 2026-09-27), so the tests written before that step still drive a whole attempt. The
         * tests about Review itself pass false and tap it themselves.
         */
        autoContinue: Boolean = true,
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
        mints = mints,
        secondSource = secondSource,
        prices = prices,
    ).also { vm ->
        if (autoContinue) {
            continuers += CoroutineScope(mainDispatcher.dispatcher).launch {
                vm.state.collect { if (it is SwapState.Review) vm.continueToWallet() }
            }
        }
    }

    private val continuers = mutableListOf<Job>()

    @After
    fun stopContinuers() {
        continuers.forEach { it.cancel() }
    }

    /**
     * TSLAx at the price Jupiter's own reverse order implies: inUsdValue 0.9960867 for 264,600
     * base units (0.002646 TSLAx) is 376.45 a token, so the value check passes the real order.
     */
    private fun tslaxPriced(usd: Double = TSLAX_USD) =
        FakePriceRepository(Result.success(mapOf(KnownMints.TSLAX to price(usd), KnownMints.WSOL to price(SOL_USD))))

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
    fun `a cancelled connect keeps the sheet on no wallet connected, and Connect again asks once more`() = runTest {
        // QA of 1.3.19: back from the wallet's Connect sheet left "Reading the wallet" counting.
        val mock = jupiter()
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Cancelled, WalletOutcome.Cancelled) }
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.NOT_CONNECTED, failed.reason)
            assertEquals(FailureNext.CONNECT, failed.reason.next)
            assertNull("the wallet was never read", failed.funds)
            assertTrue(mock.requests.isEmpty())
            assertEquals(0, wallet.callCount)
            assertEquals(1, wallet.connectCount)

            vm.retry()
            awaitUntil { it is SwapState.Opening }
            val again = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.NOT_CONNECTED, again.reason)
            assertEquals("Connect again is one more connect", 2, wallet.connectCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The connect step's own failure, which the approval step no longer shares with it.
     *
     * CONNECT_REFUSED was WALLET_REFUSED, and the only test that pinned it drove the approval
     * step, which is a cancellation now. That left the connect half of it unasserted, so this
     * asserts it: the wallet was never read, nothing was quoted, and the sentence the reader
     * gets is about connecting rather than about a swap that did not land.
     */
    @Test
    fun `a connect that errors fails before the amount step and keeps its words in the log`() = runTest {
        val mock = jupiter()
        val wallet = FakeWalletSession().apply { enqueue(WalletOutcome.Error("association failed")) }
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.CONNECT_REFUSED, failed.reason)
            assertNull("the wallet was never read", failed.funds)
            assertTrue("so nothing was quoted", mock.requests.isEmpty())
            assertTrue("the upstream words are logged, never drawn", logged.any { "association failed" in it })
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
            // Security review, 2026-09-27: the requirement is what the bytes can charge, not the
            // JSON summed. One signature at 5,000 and the priority fee the compute budget sets
            // (395; the JSON declares 1,450), plus the declared deposit, which is the upper bound
            // here because the bytes open no account of their own.
            assertEquals(5_000L, short.need.signatureFeeLamports)
            assertEquals(1_488_440L, short.need.rentFeeLamports)
            assertEquals(395L, short.need.prioritizationFeeLamports)
            assertEquals(1_493_835L, short.need.totalLamports)
            assertEquals(1_000_000L, short.haveLamports)
            assertEquals(493_835L, short.missingLamports)
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
        val vm = viewModel(mock, wallet, rpc = chain(lamports = 1_493_835L))

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

            // The route's cost is the quote's, corrected by the fill: it beat the quote, so it cost less.
            assertEquals(0.586, landed.quote.routeCostPct!!, 0.001)
            assertTrue(landed.routeCostPaidPct!! < landed.quote.routeCostPct!!)
            assertEquals(0.549, landed.routeCostPaidPct!!, 0.001)
            // All-in adds the SOL this wallet pays, at the SOL price read with the quote (mock judges'
            // review, 2026-09-27): 5,000 + 395 + 1,488,440 lamports at SOL_USD, over 5 dollars in,
            // the fees as the bytes set them (security review, 2026-09-27).
            assertEquals(SOL_USD, landed.quote.solUsd!!, 0.0)
            val solShare = 1_493_835L / 1e9 * SOL_USD / 5.0 * 100.0
            assertEquals(0.586 + solShare, landed.quote.allInCostPct!!, 0.001)
            assertEquals(0.549 + solShare, landed.allInCostPaidPct!!, 0.001)

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
            assertEquals(landed.routeCostPaidPct!!, receipt.routeCostPct!!, 1e-9)
            assertEquals(1_488_440L / 1e9 * SOL_USD, receipt.rentUsd!!, 1e-9)
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
        orderResponse = orderResponse.replace(transactionField, """"transaction": "not base64 !!",""")
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
        orderResponse = orderResponse.replace(transactionField, """"transaction": null,""")
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

    @Test
    fun `a signed transaction that is not the one checked is never sent`() = runTest {
        val tampered = WireMessage.parseTransaction(unsignedBytes)
            .plus(KnownPrograms.TOKEN, listOf(REAL_TAKER, REAL_TAKER, seeker.address), byteArrayOf(4, -1, -1, -1, -1, -1, -1, -1, 127))
            .transaction().let(::signAsSeeker)
        val unsignedEcho = unsignedBytes.copyOf()
        val said = listOf(R.string.swap_signed_control, R.string.swap_signed_not_signed, R.string.swap_signed_unreadable)
        for ((returned, sentence) in listOf(tampered, unsignedEcho, "SIGNED-BY-THE-WALLET".encodeToByteArray()).zip(said)) {
            resetPerCase()
            val mock = jupiter()
            val wallet = FakeWalletSession().apply {
                connectedAs(seeker)
                operations = FakeAdapterOperations(signedPayloads = listOf(returned))
            }
            val vm = viewModel(mock, wallet)
            vm.state.test {
                awaitItem()
                submitFive(vm, this)
                val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
                assertEquals(SwapFailure.SIGNED_MISMATCH, failed.reason)
                assertEquals("the sheet names what the wallet changed", sentence, (failed.sentence as Copy.Words).id)
                assertEquals("the wallet was asked once", 1, wallet.callCount)
                assertTrue("nothing reaches /execute", mock.executes().isEmpty())
                assertTrue("and no receipt is written", receipts.writes.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `a priority fee the wallet raised before signing lands, and the fee shown is the signed one`() = runTest {
        // Seeker, 1.3.26, 2026-09-29: Seed Vault Wallet set its own priority fee, and the old
        // byte-for-byte rule refused the swap. Within the ceiling it is the wallet's call.
        fun raise(tx: ByteArray): ByteArray {
            val m = WireMessage.parseTransaction(tx)
            val at = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3 }
            return signAsSeeker(m.mapInstruction(at) { it.copy(data = leData(3.toByte(), 1_000_000L)) }.transaction())
        }
        val walletSigned = raise(unsignedBytes)
        val mock = jupiter()
        val wallet = FakeWalletSession().apply {
            connectedAs(seeker)
            operations = FakeAdapterOperations(signedPayloads = listOf(walletSigned))
        }
        val vm = viewModel(mock, wallet)
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            val costs = landed.quote.costs!!
            assertTrue("the signed fee, above the order's 395", costs.priorityFeeLamports > 395L)
            assertEquals(costs.priorityFeeLamports, landed.quote.paidSol.prioritizationFeeLamports)
            val body = HttpClientFactory.json.parseToJsonElement(mock.executes().single().bodyText()).jsonObject
            assertEquals(Base64.getEncoder().encodeToString(walletSigned), body["signedTransaction"]!!.jsonPrimitive.content)
            assertEquals(1, receipts.writes.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an order whose bytes are not the requested swap fails before the wallet is ever opened`() = runTest {
        // Security audit, finding 2. Each case changes one thing the sheet cannot see: the JSON's
        // amount, the JSON's mint, or an Approve slipped into the bytes.
        val approve = WireMessage.parseBase64(unsignedBase64)
            .plus(KnownPrograms.TOKEN, listOf(REAL_TAKER, REAL_TAKER, seeker.address), byteArrayOf(4, -1, -1, -1, -1, -1, -1, -1, 127))
            .base64()
        val golden = orderResponse
        val cases = listOf(
            golden.replace(""""inAmount": "5000000",""", """"inAmount": "4000000","""),
            golden.replace(""""outputMint": "${KnownMints.TSLAX}",""", """"outputMint": "${KnownMints.SKR}","""),
            golden.replace(transactionField, """"transaction": "$approve","""),
        )
        val reasons = listOf(TransactionGuard.Why.WRONG_AMOUNT, TransactionGuard.Why.NOT_THIS_REQUEST, TransactionGuard.Why.HANDS_OVER_CONTROL)
        for ((case, why) in cases.zip(reasons)) {
            assertTrue("the case must differ from the golden order", case != golden)
            orderResponse = case
            val mock = jupiter()
            val wallet = wallet()
            val vm = viewModel(mock, wallet)
            vm.state.test {
                awaitItem()
                submitFive(vm, this)
                val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
                assertEquals(SwapFailure.GUARD_REFUSED, failed.reason)
                assertEquals("the plain reason travels with the refusal", why, failed.why)
                assertEquals("the wallet is never opened for it", 0, wallet.callCount)
                assertTrue(mock.executes().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // ---- Review: nothing is asked of the wallet before Continue (mock judges' review, 2026-09-27) ------

    @Test
    fun `the machine stops at Review after every check, and the wallet opens only on Continue`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, autoContinue = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val review = awaitUntil { it is SwapState.Review } as SwapState.Review
            runCurrent()
            assertTrue("Review waits: nothing is in flight", vm.state.value is SwapState.Review)
            assertFalse(review.isBusy)
            assertEquals("the wallet was never opened", 0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            assertFalse(review.requote)
            assertFalse(review.refreshed)
            // The figures Review states are the bytes' own (security review, 2026-09-27).
            val costs = review.quote.costs!!
            assertEquals(5_000L, costs.signatureFeeLamports)
            assertEquals("the compute budget's fee, not the JSON's 1,450", 395L, costs.priorityFeeLamports)
            assertEquals(1_488_440L, costs.rentUpperBoundLamports)
            assertEquals(1_488_440L, costs.rentDeclaredLamports)
            assertEquals(0, costs.walletFundedCreates)

            vm.continueToWallet()
            awaitUntil { it is SwapState.Landed }
            assertEquals(1, wallet.callCount)
            assertEquals(1, mock.executes().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancel on Review asks nothing of the wallet, and edit keeps the amount`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, autoContinue = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Review }
            vm.edit()
            val back = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals("5", back.input.text)
            assertTrue(back.canSubmit)

            vm.submit()
            awaitUntil { it is SwapState.Review }
            vm.close()
            assertEquals(SwapState.Closed(), awaitUntil { it is SwapState.Closed })
            runCurrent()
            assertEquals(0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            // Continue from anywhere but Review does nothing.
            vm.continueToWallet()
            runCurrent()
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a quote older than its budget is fetched again before the wallet, and Review says it is fresh`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, autoContinue = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Review }
            now += 46_000L
            vm.continueToWallet()
            val fresh = awaitUntil { it is SwapState.Review && it.refreshed } as SwapState.Review
            assertEquals("a second quote", 2, mock.orders().size)
            assertEquals("and still nothing asked of the wallet", 0, wallet.callCount)
            assertFalse(fresh.requote)

            vm.continueToWallet()
            awaitUntil { it is SwapState.Landed }
            assertEquals(1, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an RFQ quote about to expire is fetched again before the wallet`() = runTest {
        val soon = START / 1000 + 3
        orderResponse = orderResponse.replace(""""lastValidBlockHeight":""", """"expireAt": "$soon", "lastValidBlockHeight":""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, autoContinue = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val review = awaitUntil { it is SwapState.Review } as SwapState.Review
            assertTrue(review.quote.hasExpiry)
            // The fresh quote carries a later expiry.
            orderResponse = orderResponse.replace(""""expireAt": "$soon"""", """"expireAt": "${soon + 60}"""")
            vm.continueToWallet()
            awaitUntil { it is SwapState.Review && it.refreshed }
            assertEquals(2, mock.orders().size)
            assertEquals(0, wallet.callCount)
            vm.continueToWallet()
            awaitUntil { it is SwapState.Landed }
            assertEquals(1, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the requote after a dead quote comes back to Review, and the second approval waits for Continue`() = runTest {
        executePlan = listOf(refusal(SwapError.CODE_QUOTE_EXPIRED) to HttpStatusCode.OK, LANDED to HttpStatusCode.OK)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, autoContinue = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Review }
            vm.continueToWallet()
            val again = awaitUntil { it is SwapState.Review && it.requote } as SwapState.Review
            runCurrent()
            assertEquals("one approval so far", 1, wallet.callCount)
            assertEquals(2, mock.orders().size)
            assertTrue(again.requote)
            vm.continueToWallet()
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            assertTrue(landed.requoted)
            assertEquals(2, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet that changes while Review waits is never handed the bytes`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, autoContinue = false)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Review }
            wallet.connectedAs(null)
            vm.continueToWallet()
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.WALLET_CHANGED, failed.reason)
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Fees and rent from the bytes (security review, 2026-09-27) -------------------------------

    @Test
    fun `an order declaring a negative fee is refused before Review, whatever its fields add up to`() = runTest {
        // The mock judges' counterexample: priority 1,000,000,000, signature -999,995,000, rent 0 adds
        // up to the 5,000 lamports a sheet would show, while the transaction could charge a SOL.
        orderResponse = orderResponse
            .replace(""""signatureFeeLamports": 5000""", """"signatureFeeLamports": -999995000""")
            .replace(""""prioritizationFeeLamports": 1450""", """"prioritizationFeeLamports": 1000000000""")
            .replace(""""rentFeeLamports": 1488440""", """"rentFeeLamports": 0""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.GUARD_REFUSED, failed.reason)
            assertEquals(TransactionGuard.Why.COSTS_MORE_THAN_SHOWN, failed.why)
            assertNull("no figure from that order is ever drawn", failed.quote)
            assertEquals(0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a huge declared priority fee is not what the sheet adds, and cannot pass the ceiling`() = runTest {
        // Positive and within a Long, but past the 0.01 SOL fee ceiling: refused, not summed.
        orderResponse = orderResponse.replace(""""prioritizationFeeLamports": 1450""", """"prioritizationFeeLamports": 1000000000""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.GUARD_REFUSED, failed.reason)
            assertEquals(TransactionGuard.Why.COSTS_MORE_THAN_SHOWN, failed.why)
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Unpriced tokens: check whether a route exists (mock judges' review, 2026-09-27) ---------------

    @Test
    fun `checking availability on an unpriced token with an executable order reaches Review, marked unpriced`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        // Jupiter has no price for the token at all.
        val unpricedPrices = FakePriceRepository(Result.success(mapOf(KnownMints.WSOL to price(SOL_USD))))
        val vm = viewModel(mock, wallet, prices = unpricedPrices, autoContinue = false)

        vm.state.test {
            awaitItem()
            vm.checkAvailability(tslax)
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertTrue(amount.leg.unpriced)
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            vm.submit()
            val review = awaitUntil { it is SwapState.Review } as SwapState.Review
            assertTrue(review.leg.unpriced)
            assertEquals(0, wallet.callCount)
            vm.continueToWallet()
            awaitUntil { it is SwapState.Landed }
            assertEquals(1, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking availability with no route says so, and never opens the wallet`() = runTest {
        orderResponse = """{"error":"No routes found"}"""
        orderStatus = HttpStatusCode.BadRequest
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            vm.checkAvailability(tslax)
            awaitUntil { it is SwapState.Amount }
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            vm.submit()
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.NO_ROUTE, failed.reason)
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking availability on a quote with nothing to sign is not called no route`() = runTest {
        // The order carries a route (amounts, a route plan) and no bytes: Jupiter found a way, so
        // "no route" would be a claim the answer contradicts.
        orderResponse = orderResponse.replace(transactionField, """"transaction": null,""")
        val failed = checkAvailabilityFails()
        assertEquals(SwapFailure.NO_TRANSACTION, failed.reason)
    }

    // ---- Only a true "no route" reads as one (1.3.25 on the Seeker, 2026-09-29) ------------------

    /** "Check swap availability" on TSLAx, 5 USDC, to the first Failed; the wallet never opens. */
    private suspend fun kotlinx.coroutines.test.TestScope.checkAvailabilityFails(): SwapState.Failed {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)
        var failed: SwapState.Failed? = null
        vm.state.test {
            awaitItem()
            vm.checkAvailability(tslax)
            awaitUntil { it is SwapState.Amount }
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            vm.submit()
            failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, wallet.callCount)
        assertTrue(mock.executes().isEmpty())
        return requireNotNull(failed)
    }

    @Test
    fun `checking availability when the aggregator finds nothing either is no route`() = runTest {
        // AALx, live: "Quote not available from market maker", then "Failed to get quotes".
        orderResponse = Fixtures.read("jupiter/order-error-400-failed-to-get-quotes.json")
        orderStatus = HttpStatusCode.BadRequest
        assertEquals(SwapFailure.NO_ROUTE, checkAvailabilityFails().reason)
    }

    @Test
    fun `checking availability on a busy Jupiter says busy, not no route`() = runTest {
        orderResponse = Fixtures.read("jupiter/order-error-429-gateway.json")
        orderStatus = HttpStatusCode.TooManyRequests
        val failed = checkAvailabilityFails()
        assertEquals(SwapFailure.RATE_LIMITED, failed.reason)
        assertEquals(FailureNext.RETRY, failed.reason.next)
        assertEquals("the amount survives, so Try again is one tap", "5", failed.input?.text)
    }

    @Test
    fun `a priced token on a busy Jupiter says busy, not that the pair cannot be quoted`() = runTest {
        orderResponse = Fixtures.read("jupiter/order-error-429-gateway.json")
        orderStatus = HttpStatusCode.TooManyRequests
        val vm = viewModel(jupiter(), wallet())
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.RATE_LIMITED, failed.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checking availability on a server error is unavailable, not no route`() = runTest {
        orderResponse = """{"error":"Internal server error"}"""
        orderStatus = HttpStatusCode.InternalServerError
        assertEquals(SwapFailure.QUOTE_UNAVAILABLE, checkAvailabilityFails().reason)
    }

    @Test
    fun `checking availability that Jupiter refuses for funds is a refusal, not no route`() = runTest {
        orderResponse = Fixtures.read("jupiter/order-live-0929-aaplx-usdc-insufficient.json")
        assertEquals(SwapFailure.QUOTE_REFUSED, checkAvailabilityFails().reason)
    }

    @Test
    fun `checking availability with no network says offline, not no route`() = runTest {
        orderTransportFailure = java.net.UnknownHostException("api.jup.ag")
        assertEquals(SwapFailure.OFFLINE, checkAvailabilityFails().reason)
    }

    @Test
    fun `a priced token with no network says offline too`() = runTest {
        orderTransportFailure = java.net.ConnectException("failed to connect to api.jup.ag")
        val vm = viewModel(jupiter(), wallet())
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.OFFLINE, failed.reason)
            assertEquals(FailureNext.RETRY, failed.reason.next)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a priced token keeps its own words for a refused order`() = runTest {
        orderResponse = """{"error":"No routes found"}"""
        orderStatus = HttpStatusCode.BadRequest
        val vm = viewModel(jupiter(), wallet())

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.QUOTE_REFUSED, failed.reason)
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

    /**
     * Mock judges' review, 2026-09-27: without a SOL price the SOL costs cannot be priced, so nothing is
     * called all-in; the route's cost stands under its own name, and the swap is not held up.
     */
    @Test
    fun `without a SOL price the swap still lands, with the route cost and no all-in figure`() = runTest {
        for (prices in listOf(FakePriceRepository(Result.success(emptyMap())), FakePriceRepository(Result.failure(java.io.IOException("down"))))) {
            resetPerCase()
            val mock = jupiter()
            val wallet = wallet()
            val vm = viewModel(mock, wallet, prices = prices)
            vm.state.test {
                awaitItem()
                submitFive(vm, this)
                val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
                assertNull(landed.quote.solUsd)
                assertNull(landed.quote.allInCostPct)
                assertNull(landed.allInCostPaidPct)
                assertEquals(0.586, landed.quote.routeCostPct!!, 0.001)
                val receipt = receipts.receipts.value.single()
                assertNull(receipt.allInCostPct)
                assertNull(receipt.solCostUsd)
                assertEquals(landed.routeCostPaidPct!!, receipt.routeCostPct!!, 1e-9)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `the SOL price is asked for once, after the quote and before the wallet`() = runTest {
        val prices = tslaxPriced()
        val vm = viewModel(jupiter(), wallet(), prices = prices)
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            assertEquals(listOf(listOf(KnownMints.WSOL)), prices.requested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Mock judges' review 2026-09-27: sign_transactions is optional in MWA 2.x. A wallet whose
     * capabilities leave it out is told so plainly, and is never asked to sign.
     */
    @Test
    fun `a wallet that only signs by sending is named, and never asked to sign`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val ops = wallet.operations as FakeAdapterOperations
        ops.optionalFeatures = arrayOf("solana:signInWithSolana")
        val vm = viewModel(mock, wallet)
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.SIGN_ONLY_UNSUPPORTED, failed.reason)
            assertEquals(FailureOutcome.NOTHING_SENT, failed.reason.outcome)
            assertEquals(FailureNext.NONE, failed.reason.next)
            assertTrue("nothing was asked of the wallet", ops.signRequests.isEmpty())
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a wallet that lists sign-only is asked to sign in the same session, and lands`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val ops = wallet.operations as FakeAdapterOperations
        ops.optionalFeatures = arrayOf("solana:signTransactions")
        val vm = viewModel(mock, wallet)
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            assertEquals(1, ops.signRequests.size)
            assertEquals("one wallet round-trip for both", 1, wallet.callCount)
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


    // ---- Swap to USDC: the reverse direction, on a REAL order (2026-09-24) -----------------------

    /**
     * The real TSLAx-to-USDC order captured 2026-09-24 for 0.002646 TSLAx (see
     * TransactionGuardReverseTest for where it came from), with its taker and the taker's two
     * token accounts replaced by [seeker]'s, served as this wallet's own order.
     */
    private val reverseOrder: String = run {
        val text = Fixtures.read("jupiter/order-tslax-usdc-default-metis.json")
        val order = HttpClientFactory.json.decodeFromString(SwapOrder.serializer(), text)
        val bytes = asSeeker(WireMessage.parseBase64(order.transaction!!), REVERSE_TAKER).base64()
        text.replace(order.transaction!!, bytes).replace(REVERSE_TAKER, seeker.address)
    }

    private val reverseLanded =
        """{"status":"Success","signature":"${"1".repeat(88)}","slot":"450068700","inputAmountResult":"264600","outputAmountResult":"996812"}"""

    private suspend fun openOutAndMax(vm: SwapViewModel, turbine: app.cash.turbine.ReceiveTurbine<SwapState>, token: SwapToken = tslax): SwapState.Amount {
        vm.openOut(token)
        turbine.awaitUntil { it is SwapState.Amount }
        vm.useMax()
        return turbine.awaitUntil { it is SwapState.Amount && it.input.isUsable } as SwapState.Amount
    }

    @Test
    fun `swap to USDC spends the whole raw balance on Max, quotes the xStock into USDC for this wallet, and lands`() = runTest {
        orderResponse = reverseOrder
        executePlan = listOf(reverseLanded to HttpStatusCode.OK)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, rpc = chain(tokenRaw = 264_600L))

        vm.state.test {
            awaitItem()
            val max = openOutAndMax(vm, this)
            assertEquals(KnownMints.TSLAX, max.leg.input.mint)
            assertEquals(KnownMints.USDC, max.leg.output.mint)
            assertEquals("the field shows what the wallet shows", "0.002646", max.input.text)
            assertEquals("and Max sends the raw balance itself", 264_600L, max.input.raw)

            vm.submit()
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            val order = mock.orders().single().url.parameters
            assertEquals(KnownMints.TSLAX, order["inputMint"])
            assertEquals(KnownMints.USDC, order["outputMint"])
            assertEquals("264600", order["amount"])
            assertEquals("the taker is the connected wallet", seeker.address, order["taker"])
            assertEquals("the guard passed the real reverse bytes to the wallet unchanged", 1, wallet.callCount)
            assertEquals(996_812L, landed.fill.outAmountRaw)

            val receipt = receipts.writes.single()
            assertEquals(KnownMints.TSLAX, receipt.inputMint)
            assertEquals(264_600L, receipt.inputAmountRaw)
            assertEquals(KnownMints.USDC, receipt.outputMint)
            assertEquals(996_812L, receipt.outputAmountRaw)
            assertEquals(1.0, receipt.inputMultiplier, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `swap to USDC with none of the xStock cannot be submitted and asks Jupiter nothing`() = runTest {
        val mock = jupiter()
        val vm = viewModel(mock, wallet(), rpc = chain(tokenRaw = 0L))

        vm.state.test {
            awaitItem()
            vm.openOut(tslax)
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals(0L, amount.balanceRaw)
            vm.amountChanged("0.001")
            val typed = awaitUntil { it is SwapState.Amount && it.input.text == "0.001" } as SwapState.Amount
            assertEquals(AmountProblem.ABOVE_BALANCE, typed.input.problem)
            vm.submit()
            runCurrent()
            assertTrue(mock.requests.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a frozen token account is not a spendable balance, and the sheet knows why`() = runTest {
        val frozen = TokenBalance(
            tokenAccount = "frozen-account",
            mint = KnownMints.TSLAX,
            owner = seeker.address,
            amountRaw = 264_600L,
            decimals = 8,
            uiAmountString = "0.002646",
            programId = KnownPrograms.TOKEN_2022,
            state = "frozen",
        )
        val rpc = chain().apply { balances = Result.success(balances.getOrThrow() + frozen) }
        val vm = viewModel(jupiter(), wallet(), rpc = rpc)

        vm.state.test {
            awaitItem()
            vm.openOut(tslax)
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals(0L, amount.balanceRaw)
            assertTrue(amount.spendFrozen)
            vm.useMax()
            runCurrent()
            assertFalse((vm.state.value as SwapState.Amount).canSubmit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a split xStock is typed and sent in the right units, and Max is still the exact raw balance`() = runTest {
        // NFLXx's shape: an effective multiplier of 10, so 1 share in the wallet is 10^7 raw.
        val split = tslax.copy(multiplier = java.math.BigDecimal.TEN)
        // The public node reports the same split in force, so the second-source check agrees.
        val second = FakeSecondSource(Result.success(agreeingSecondRead(mintFacts(scaledUiAmount = scaled(10.0)))))
        val vm = viewModel(jupiter(), wallet(), rpc = chain(tokenRaw = 272_557_048_309L), secondSource = second)

        vm.state.test {
            awaitItem()
            val max = openOutAndMax(vm, this, split)
            assertEquals("27255.7048309", max.input.text)
            assertEquals(272_557_048_309L, max.input.raw)

            vm.amountChanged("1")
            val one = awaitUntil { it is SwapState.Amount && it.input.text == "1" } as SwapState.Amount
            assertEquals("one share as the wallet shows it is 10^7 raw at a multiplier of 10", 10_000_000L, one.input.raw)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a token opened without a known multiplier has it read from the mint, the one in force now`() = runTest {
        val mints = com.plainticker.mobile.repo.FakeMintRepository(
            reading = Result.success(
                com.plainticker.mobile.repo.mintReading(
                    com.plainticker.mobile.repo.mintFacts(
                        scaledUiAmount = com.plainticker.mobile.data.rpc.ScaledUiAmountConfig(
                            multiplier = 1.0,
                            newMultiplier = 10.0,
                            // NFLXx's own: scheduled for 2025-11-16 and long since in force.
                            newMultiplierEffectiveAtEpochSeconds = 1_763_337_300L,
                            authority = null,
                        ),
                    ),
                    // Read on 2026-09-24, after the scheduled change took effect.
                    readAtMillis = 1_790_208_000_000L,
                ),
            ),
        )
        // The public node reads the same mint at the same moment, so it finds the same 10 in force.
        val second = FakeSecondSource(
            Result.success(
                agreeingSecondRead(
                    mintFacts(scaledUiAmount = scaled(1.0, newMultiplier = 10.0, effectiveAtEpochSeconds = 1_763_337_300L)),
                    readAtMillis = 1_790_208_000_000L,
                ),
            ),
        )
        val vm = viewModel(jupiter(), wallet(), rpc = chain(tokenRaw = 10_000_000L), mints = mints, secondSource = second)

        vm.state.test {
            awaitItem()
            vm.openOut(tslax.copy(multiplier = null))
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals(0, java.math.BigDecimal.TEN.compareTo(amount.leg.input.multiplier))
            assertEquals(listOf(KnownMints.TSLAX), mints.asked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a mint that cannot be read stops the sheet rather than guessing at a split`() = runTest {
        val mints = com.plainticker.mobile.repo.FakeMintRepository(reading = Result.failure(java.io.IOException("down")))
        val vm = viewModel(jupiter(), wallet(), mints = mints)

        vm.state.test {
            awaitItem()
            vm.openOut(tslax.copy(multiplier = null))
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.CHAIN_UNREAD, failed.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Swap to USDC against a semi-trusted forwarder (security audit, 2026-09-26) -------------------

    @Test
    fun `inflated decimals refuse Swap to USDC before any quantity is offered or quoted`() = runTest {
        // The audit's example: the forwarder answers decimals 10, so "1 TSLAx" would be the base
        // units of 100. Whether the screen handed the token in or the mint read supplied it, the
        // sheet stops at the plain chain error and never falls back to 8.
        val second = FakeSecondSource()
        val handedIn = viewModel(jupiter(), wallet(), rpc = chain(tokenRaw = 264_600L), secondSource = second)
        handedIn.state.test {
            awaitItem()
            handedIn.openOut(tslax.copy(decimals = 10))
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.CHAIN_UNREAD, failed.reason)
            assertNull("no balance was offered", failed.funds)
            cancelAndIgnoreRemainingEvents()
        }

        val mock = jupiter()
        val mints = com.plainticker.mobile.repo.FakeMintRepository(
            reading = Result.success(com.plainticker.mobile.repo.mintReading(mintFacts(decimals = 10))),
        )
        val readHere = viewModel(mock, wallet(), rpc = chain(tokenRaw = 264_600L), mints = mints, secondSource = second)
        readHere.state.test {
            awaitItem()
            readHere.openOut(tslax.copy(multiplier = null))
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.CHAIN_UNREAD, failed.reason)
            assertTrue("nothing was quoted", mock.requests.isEmpty())
            assertTrue("refused before the second source was even needed", second.asked.isEmpty())
            assertTrue(logged.any { "10 decimals" in it })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a multiplier the second source does not confirm refuses Swap to USDC, both ways round`() = runTest {
        // The forwarder says unsplit; the public node says STRCx's real 1.086 is in force.
        val missed = FakeSecondSource(Result.success(agreeingSecondRead(mintFacts(scaledUiAmount = scaled(1.0863570205637327)))))
        // The forwarder says a tiny multiplier (more base units per typed share); the node says 1.
        val tiny = FakeSecondSource()
        val cases = listOf(tslax to missed, tslax.copy(multiplier = java.math.BigDecimal("0.01")) to tiny)
        for ((token, second) in cases) {
            val mock = jupiter()
            val vm = viewModel(mock, wallet(), rpc = chain(tokenRaw = 264_600L), secondSource = second)
            vm.state.test {
                awaitItem()
                vm.openOut(token)
                val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
                assertEquals(SwapFailure.SECOND_SOURCE_MISMATCH, failed.reason)
                assertEquals("a disagreement offers nothing but Close", FailureNext.NONE, failed.reason.next)
                assertEquals(listOf(seeker.address to KnownMints.TSLAX), second.asked)
                assertTrue("nothing was quoted", mock.requests.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `a second source that is down refuses Swap to USDC, and the holding is still shown`() = runTest {
        val mock = jupiter()
        val second = FakeSecondSource(Result.failure(java.io.IOException("public node unreachable")))
        val rpc = chain(tokenRaw = 264_600L)
        val vm = viewModel(mock, wallet(), rpc = rpc, secondSource = second)

        vm.watchHolding(tslax)
        runCurrent()
        assertEquals("viewing does not depend on the second source", SwapHolding(tslax, 264_600L), vm.holding.value)
        assertTrue(vm.holding.value!!.canSwapOut)

        vm.state.test {
            awaitItem()
            vm.openOut(tslax)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.SECOND_SOURCE_UNREACHABLE, failed.reason)
            assertEquals(FailureOutcome.NOTHING_SENT, failed.reason.outcome)
            assertTrue("nothing was quoted", mock.requests.isEmpty())
            assertTrue("the node's own words are logged, never drawn", logged.any { "public node unreachable" in it })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("and the holding is still there", SwapHolding(tslax, 264_600L), vm.holding.value)
    }

    @Test
    fun `the flip into Swap to USDC is checked at the tap, since it never passed the opening`() = runTest {
        val mock = jupiter()
        val second = FakeSecondSource()
        val wallet = wallet()
        val vm = viewModel(mock, wallet, rpc = chain(tokenRaw = 1_366_141L), secondSource = second)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            awaitUntil { it is SwapState.Amount }
            assertTrue("USDC into the token needs no second source", second.asked.isEmpty())
            vm.flip()
            awaitUntil { it is SwapState.Amount && it.leg.input.mint == KnownMints.TSLAX }
            vm.useMax()
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            second.result = Result.failure(java.io.IOException("down"))
            vm.submit()
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.SECOND_SOURCE_UNREACHABLE, failed.reason)
            assertTrue("nothing was quoted", mock.orders().isEmpty())
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a forged balance cannot raise the cap past what the second source shows`() = runTest {
        orderResponse = reverseOrder
        executePlan = listOf(reverseLanded to HttpStatusCode.OK)
        val mock = jupiter()
        // The forwarder claims 264.6 TSLAx; the public node shows the 0.002646 actually held.
        val second = FakeSecondSource(Result.success(agreeingSecondRead(spendableRaw = 264_600L)))
        val vm = viewModel(mock, wallet(), rpc = chain(tokenRaw = 26_460_000_000L), secondSource = second)

        vm.state.test {
            awaitItem()
            vm.openOut(tslax)
            val amount = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals("the cap is the smaller balance", 264_600L, amount.balanceRaw)
            vm.amountChanged("1")
            val one = awaitUntil { it is SwapState.Amount && it.input.text == "1" } as SwapState.Amount
            assertEquals("1 TSLAx is more than the wallet really has", AmountProblem.ABOVE_BALANCE, one.input.problem)
            assertFalse(one.canSubmit)

            vm.useMax()
            val max = awaitUntil { it is SwapState.Amount && it.input.isUsable } as SwapState.Amount
            assertEquals("Max is the confirmed balance, not the forged one", 264_600L, max.input.raw)
            vm.submit()
            awaitUntil { it is SwapState.Landed }
            assertEquals("264600", mock.orders().single().url.parameters["amount"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a balance that shrank by the tap goes back to the amount step and quotes nothing`() = runTest {
        orderResponse = reverseOrder
        val mock = jupiter()
        val second = FakeSecondSource()
        val vm = viewModel(mock, wallet(), rpc = chain(tokenRaw = 264_600L), secondSource = second)

        vm.state.test {
            awaitItem()
            openOutAndMax(vm, this)
            second.result = Result.success(agreeingSecondRead(spendableRaw = 100_000L))
            vm.submit()
            val back = awaitUntil { it is SwapState.Amount && it.input.problem != null } as SwapState.Amount
            assertEquals(AmountProblem.ABOVE_BALANCE, back.input.problem)
            assertEquals(100_000L, back.balanceRaw)
            assertTrue("nothing was quoted", mock.orders().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an order whose dollar value is far from the typed quantity at the shown price never reaches the wallet`() = runTest {
        // The real reverse order is worth 0.996 USD. At a shown price 100 times lower, the typed
        // 0.002646 TSLAx is worth a hundredth of that: the shape of an amount inflated by two
        // decimals. At 10 percent off it is still refused; the bound is 5.
        for (shown in listOf(TSLAX_USD / 100.0, TSLAX_USD * 1.10, TSLAX_USD * 0.90)) {
            resetPerCase()
            orderResponse = reverseOrder
            val mock = jupiter()
            val wallet = wallet()
            val vm = viewModel(mock, wallet, rpc = chain(tokenRaw = 264_600L), prices = tslaxPriced(shown))
            vm.state.test {
                awaitItem()
                openOutAndMax(vm, this)
                vm.submit()
                val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
                assertEquals("shown $shown", SwapFailure.VALUE_MISMATCH, failed.reason)
                assertEquals("the wallet was never opened", 0, wallet.callCount)
                assertTrue(mock.executes().isEmpty())
                assertTrue(receipts.writes.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `an order within the value bound goes on to the wallet`() = runTest {
        // 3 percent apart either way: price movement inside the price cache's 30 seconds.
        for (shown in listOf(TSLAX_USD * 1.03, TSLAX_USD * 0.97)) {
            resetPerCase()
            orderResponse = reverseOrder
            executePlan = listOf(reverseLanded to HttpStatusCode.OK)
            val wallet = wallet()
            val vm = viewModel(jupiter(), wallet, rpc = chain(tokenRaw = 264_600L), prices = tslaxPriced(shown))
            vm.state.test {
                awaitItem()
                openOutAndMax(vm, this)
                vm.submit()
                awaitUntil { it is SwapState.Landed }
                assertEquals(1, wallet.callCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `a value check that cannot be made is a refusal, not a pass`() = runTest {
        val noPrice = FakePriceRepository(Result.success(emptyMap()))
        val priceDown = FakePriceRepository(Result.failure(java.io.IOException("price v3 down")))
        val noUsd = reverseOrder.replace(""""inUsdValue":0.9960867,""", "")
        assertTrue("the fixture carries the field this case removes", noUsd != reverseOrder)
        val cases = listOf(
            reverseOrder to noPrice,
            reverseOrder to priceDown,
            noUsd to tslaxPriced(),
        )
        for ((order, prices) in cases) {
            resetPerCase()
            orderResponse = order
            val wallet = wallet()
            val vm = viewModel(jupiter(), wallet, rpc = chain(tokenRaw = 264_600L), prices = prices)
            vm.state.test {
                awaitItem()
                openOutAndMax(vm, this)
                vm.submit()
                val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
                assertEquals(SwapFailure.VALUE_UNCHECKED, failed.reason)
                assertEquals(0, wallet.callCount)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `USDC into a token is not held to the Swap to USDC checks`() = runTest {
        val second = FakeSecondSource(Result.failure(java.io.IOException("down")))
        val prices = FakePriceRepository(Result.success(emptyMap()))
        val wallet = wallet()
        val vm = viewModel(jupiter(), wallet, secondSource = second, prices = prices)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            assertTrue(second.asked.isEmpty())
            // The token's own price is not asked for; only SOL's, for the all-in cost (2026-09-27).
            assertEquals(listOf(listOf(KnownMints.WSOL)), prices.requested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Prices that answer at their own pace (security review, 2026-09-27): SOL's after [solDelayMs],
     * any other mint's after [tokenDelayMs]. The SOL price is the all-in cost's alone and may never
     * stop a swap; the token's is the value check's, bound as it always was.
     */
    private class PacedPrices(private val solDelayMs: Long, private val tokenDelayMs: Long = 0L) : com.plainticker.mobile.repo.PriceRepository {
        val requested = mutableListOf<List<String>>()
        override suspend fun prices(mints: Collection<String>): Map<String, com.plainticker.mobile.data.jupiter.PriceEntry> {
            requested += mints.toList()
            return if (KnownMints.WSOL in mints) {
                kotlinx.coroutines.delay(solDelayMs)
                mapOf(KnownMints.WSOL to price(SOL_USD))
            } else {
                kotlinx.coroutines.delay(tokenDelayMs)
                mints.associateWith { price(TSLAX_USD) }
            }
        }
        override suspend fun pricesFirst(mints: List<String>, limit: Int) =
            com.plainticker.mobile.data.jupiter.PriceFetch(priced = mints.associateWith { price(if (it == KnownMints.WSOL) SOL_USD else TSLAX_USD) })
    }

    @Test
    fun `a slow SOL price never stops Swap to USDC, which lands on the route cost after the value check`() = runTest {
        orderResponse = reverseOrder
        executePlan = listOf(reverseLanded to HttpStatusCode.OK)
        val prices = PacedPrices(solDelayMs = 60_000L)
        val wallet = wallet()
        val vm = viewModel(jupiter(), wallet, rpc = chain(tokenRaw = 264_600L), prices = prices)
        vm.state.test {
            awaitItem()
            openOutAndMax(vm, this)
            vm.submit()
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            assertNull("past the bound the sheet says Route cost", landed.quote.solUsd)
            assertNull(landed.quote.allInCostPct)
            assertEquals(1, wallet.callCount)
            assertTrue("the value check read the token's own price", listOf(KnownMints.TSLAX) in prices.requested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the value check is not cut short by the SOL price's bound, as before the all-in cost`() = runTest {
        orderResponse = reverseOrder
        executePlan = listOf(reverseLanded to HttpStatusCode.OK)
        // The token's price takes twice the SOL bound: the value check still waits for it and passes.
        val prices = PacedPrices(solDelayMs = 0L, tokenDelayMs = 5_000L)
        val wallet = wallet()
        val vm = viewModel(jupiter(), wallet, rpc = chain(tokenRaw = 264_600L), prices = prices)
        vm.state.test {
            awaitItem()
            openOutAndMax(vm, this)
            vm.submit()
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            assertEquals(SOL_USD, landed.quote.solUsd!!, 0.0)
            assertEquals(1, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a slow SOL price never stops a swap into a token either`() = runTest {
        val prices = PacedPrices(solDelayMs = 60_000L)
        val wallet = wallet()
        val vm = viewModel(jupiter(), wallet, prices = prices)
        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            assertNull(landed.quote.solUsd)
            assertEquals(1, wallet.callCount)
            assertEquals(listOf(listOf(KnownMints.WSOL)), prices.requested)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `swap back asks the second source for a balance no older than the landing's slot`() = runTest {
        val rpc = chain()
        val second = FakeSecondSource()
        val vm = viewModel(jupiter(), wallet(), rpc = rpc, secondSource = second)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            rpc.balances = Result.success(listOf(balance(KnownMints.USDC, 15_200_000L, 6), balance(KnownMints.TSLAX, 1_360_941L, 8)))
            vm.swapBack()
            val back = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals(1_360_941L, back.balanceRaw)
            assertEquals(listOf<Long?>(367_000_000L), second.slots)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- The edges of either direction ------------------------------------------------------------

    @Test
    fun `a wallet that disconnects before signing is never handed the bytes`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            awaitUntil { it is SwapState.Amount }
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            wallet.connectedAs(null)
            vm.submit()
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.WALLET_CHANGED, failed.reason)
            assertEquals(0, wallet.callCount)
            assertTrue(mock.executes().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a session that answers for another account is not handed this account's order`() = runTest {
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            vm.open(tslax)
            awaitUntil { it is SwapState.Amount }
            vm.amountChanged("5")
            awaitUntil { it is SwapState.Amount && it.input.isUsable }
            wallet.connectedAs(testAccount(fill = 9))
            vm.submit()
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.WALLET_CHANGED, failed.reason)
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a quote that would deliver nothing is refused before the wallet`() = runTest {
        orderResponse = orderResponse.replace(""""outAmount": "1360437",""", """"outAmount": "0",""")
            .replace(THRESHOLD_FIELD, """"otherAmountThreshold": "0",""")
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(SwapFailure.QUOTE_DUST, failed.reason)
            assertEquals(0, wallet.callCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each execute refusal maps to what it can honestly claim about the money`() = runTest {
        val cases = listOf(
            refusal(6001) to SwapFailure.SLIPPAGE,
            """{"status":"Failed","code":-1000,"error":"Slippage tolerance exceeded"}""" to SwapFailure.SLIPPAGE,
            refusal(-1005) to SwapFailure.QUOTE_EXPIRED,
            refusal(-1) to SwapFailure.QUOTE_EXPIRED,
            refusal(-1006) to SwapFailure.SUBMIT_UNAVAILABLE,
            refusal(-1001) to SwapFailure.SUBMIT_UNAVAILABLE,
            refusal(-1000) to SwapFailure.SWAP_REFUSED,
        )
        for ((answer, expected) in cases) {
            resetPerCase()
            executePlan = listOf(answer to HttpStatusCode.OK)
            val mock = jupiter()
            val vm = viewModel(mock, wallet())
            vm.state.test {
                awaitItem()
                submitFive(vm, this)
                val failed = awaitUntil { it is SwapState.Failed } as SwapState.Failed
                assertEquals(answer, expected, failed.reason)
                assertTrue("no receipt for a swap that is not known to have landed", receipts.writes.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `retry asks for a fresh quote for the same amount, and is refused where the first may have landed`() = runTest {
        executePlan = listOf(refusal(-1005) to HttpStatusCode.OK, LANDED to HttpStatusCode.OK)
        val mock = jupiter()
        val wallet = wallet()
        val vm = viewModel(mock, wallet)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            val expired = awaitUntil { it is SwapState.Failed } as SwapState.Failed
            assertEquals(FailureNext.NEW_QUOTE, expired.reason.next)
            vm.retry()
            val landed = awaitUntil { it is SwapState.Landed } as SwapState.Landed
            assertEquals("a second quote and a second approval", 2, mock.orders().size)
            assertEquals(2, wallet.callCount)
            assertEquals(5_000_000L, landed.quote.inAmountRaw)
            cancelAndIgnoreRemainingEvents()
        }

        resetPerCase()
        executePlan = listOf(refusal(-1006) to HttpStatusCode.OK)
        val second = jupiter()
        val vm2 = viewModel(second, wallet())
        vm2.state.test {
            awaitItem()
            submitFive(vm2, this)
            awaitUntil { it is SwapState.Failed && it.reason == SwapFailure.SUBMIT_UNAVAILABLE }
            vm2.retry()
            runCurrent()
            assertEquals("no second attempt over one that may have landed", 1, second.orders().size)
            assertEquals(1, second.executes().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `swap back opens the other direction with a balance no older than the landing's slot`() = runTest {
        val rpc = chain()
        val vm = viewModel(jupiter(), wallet(), rpc = rpc)

        vm.state.test {
            awaitItem()
            submitFive(vm, this)
            awaitUntil { it is SwapState.Landed }
            rpc.balances = Result.success(listOf(balance(KnownMints.USDC, 15_200_000L, 6), balance(KnownMints.TSLAX, 1_360_941L, 8)))
            vm.swapBack()
            val back = awaitUntil { it is SwapState.Amount } as SwapState.Amount
            assertEquals(KnownMints.TSLAX, back.leg.input.mint)
            assertEquals(KnownMints.USDC, back.leg.output.mint)
            assertEquals("the TSLAx that just arrived", 1_360_941L, back.balanceRaw)
            assertEquals("the read after the landing names its slot", 367_000_000L, rpc.balanceSlots.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a watched holding is read from the chain and read again after a landing changes it`() = runTest {
        val rpc = chain(tokenRaw = 0L)
        val vm = viewModel(jupiter(), wallet(), rpc = rpc)

        vm.holding.test {
            assertNull(awaitItem())
            vm.watchHolding(tslax)
            assertEquals(SwapHolding(tslax, 0L), awaitItem())
            assertFalse(SwapHolding(tslax, 0L).canSwapOut)

            rpc.balances = Result.success(listOf(balance(KnownMints.USDC, 15_200_000L, 6), balance(KnownMints.TSLAX, 1_360_941L, 8)))
            vm.open(tslax)
            awaitUntilState(vm) { it is SwapState.Amount }
            vm.amountChanged("5")
            awaitUntilState(vm) { it is SwapState.Amount && it.input.isUsable }
            vm.submit()
            val after = awaitItem()
            assertEquals(1_360_941L, after?.raw)
            assertTrue(after!!.canSwapOut)
            assertEquals("the refresh asks for the landing's slot", 367_000_000L, rpc.balanceSlots.last())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun awaitUntilState(vm: SwapViewModel, predicate: (SwapState) -> Boolean) {
        while (!predicate(vm.state.value)) kotlinx.coroutines.yield()
    }

    private companion object {
        const val START = 1_757_600_000_000L
        const val TICK = 100L

        /** TSLAx in USD as the real 2026-09-24 reverse order priced it (see tslaxPriced). */
        const val TSLAX_USD = 376.45

        /** A SOL price for the all-in cost; the tests that remove it expect "Route cost". */
        const val SOL_USD = 200.0
        const val REQUEST_ID = "01a08b00-0000-7000-8000-00000000f00d"

        /** 88 characters of base58 padding, a placeholder and never a real signature. */
        val SIGNATURE = "1".repeat(88)

        /** The 2026-09-12 fixture's redacted transaction and taker, filled in for the test wallet. */
        const val REDACTED_TRANSACTION_FIELD = """"transaction": "UkVEQUNURUQ=","""
        const val REDACTED_TAKER_FIELD = """"taker": "11111111111111111111111111111111","""

        /** The taker of the real 2026-09-23 order whose bytes stand in for the redacted ones. */
        const val REAL_TAKER = "5tzFkiKscXHK5ZXCGbXZxdw7gTjjD1mBwuoFbhUvuAi9"

        /** The pool token the real reverse route passes through, named in its own route plan. */
        const val REVERSE_HOP_MINT = "BjcRmwm8e25RgjkyaFE56fc7bxRgGPw96JUkXRJFEroT"

        /** The taker of the real 2026-09-24 TSLAx-to-USDC order, replaced by the test wallet. */
        const val REVERSE_TAKER = "AC5RDfQFmDS1deWZos921JfqscXdByf8BKHs5ACWjtW2"

        /** The 2026-09-12 fixture's own field, so a test can take it away by name. */
        const val THRESHOLD_FIELD = """"otherAmountThreshold": "1346933","""

        /** The quote behind that threshold: floor(1,360,539 x 0.99) is 1,346,933. */
        const val GOLDEN_QUOTED_OUT = 1_360_539L

        /** The same line under a key the parser ignores, which is how a field is taken away. */
        const val THRESHOLD_FIELD_IGNORED = """"_noOtherAmountThreshold": "1346933","""

        /** A landing whose answer carried no amounts at all: the fill is unknown, not the quote. */
        val LANDED_NO_AMOUNTS = """{"status":"Success","signature":"${"1".repeat(88)}","slot":"367000000"}"""

        /** The fill beat the 1,360,437 estimate by 0.037 percent, as a real landing did. */
        val LANDED = """{"status":"Success","signature":"${"1".repeat(88)}","slot":"367000000","inputAmountResult":"5000000","outputAmountResult":"1360941"}"""
    }
}
