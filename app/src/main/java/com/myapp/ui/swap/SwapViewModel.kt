package com.myapp.ui.swap

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.BuildConfig
import com.myapp.core.Clock
import com.myapp.data.KnownMints
import com.myapp.data.jupiter.ExecuteResult
import com.myapp.data.jupiter.JupiterSwapApi
import com.myapp.data.jupiter.SwapError
import com.myapp.data.jupiter.SwapOrder
import com.myapp.data.receipts.ReceiptStore
import com.myapp.data.receipts.SwapReceipt
import com.myapp.repo.RpcRepository
import com.myapp.wallet.WalletOutcome
import com.myapp.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Where the raw upstream text goes, which is never to a screen.
 *
 * Jupiter answers an integrator, not a reader: "Failed to decode signed transaction", "not fully
 * signed", a gateway page. Those sentences are worth keeping, because they are the only thing
 * that explains a failure after the fact, and they belong in a log rather than in front of a
 * person who cannot act on them.
 */
fun interface SwapDebugLog {
    fun raw(line: String)

    companion object {
        /** Debug builds only: a release build keeps no upstream text at all. */
        val ANDROID = SwapDebugLog { line -> if (BuildConfig.DEBUG) Log.d("SwapMachine", line) }
    }
}

/**
 * The swap machine. Its states, and every transition between them, are [SwapState].
 *
 * Four rules live here and nowhere else.
 *
 * 1. **The amount is refused before the network is touched.** [SwapAmount] validates the typed
 *    text against the balance this class read from the chain, so zero and over-balance never
 *    become a request, and the conversion to base units goes through BigDecimal.
 * 2. **The SOL check happens after the quote and before the wallet.** It is the quote's own
 *    signatureFee, rentFee and prioritizationFee added up, never a rent constant, and a wallet
 *    that cannot pay lands in [SwapState.Shortfall] without a single approval being asked for.
 * 3. **Exactly one automatic requote.** A -1003, -2003 or -2004 from /execute means the signed
 *    bytes are dead: a fresh /order is the only way on, and fresh bytes need a fresh approval, so
 *    the requote reaches [SwapState.AwaitingWallet] with `requote = true` and the sheet says so.
 *    A second one is terminal.
 * 4. **A cancelled approval is not a failure.** It returns to [SwapState.Amount] with the typed
 *    amount intact and a neutral note. Nothing was signed, nothing was sent, nothing is owed.
 *
 * [submitSwaps] is BuildConfig.SUBMIT_SWAPS, false in every debug build, so a debug build signs
 * and stops at [SwapState.Signed]: no /execute, no money, and no receipt.
 *
 * The quote is fetched at the tap and never for a preview: GET /order shares a 0.5 rps bucket
 * with Price v3 (docs/data-map.md), so [SwapState.Amount] shows a balance and no estimate, and
 * the cost cells appear with the quote from [SwapState.Quoting] onward.
 */
class SwapViewModel(
    private val swapApi: JupiterSwapApi,
    private val wallet: WalletSession,
    private val rpc: RpcRepository,
    private val receipts: ReceiptStore,
    private val clock: Clock,
    private val submitSwaps: Boolean = BuildConfig.SUBMIT_SWAPS,
    private val debugLog: SwapDebugLog = SwapDebugLog.ANDROID,
    /** Where the receipt is written. viewModelScope runs on Main, and a file write does not. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<SwapState>(SwapState.Closed())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    private var job: Job? = null

    // ---- Closed -> Opening -> Amount ---------------------------------------------------------

    /**
     * Opens the sheet for USDC into [token]: authorize the wallet if it is not authorized yet,
     * then read the lamports and the two balances the rest of the machine decides on.
     */
    fun open(token: SwapToken) {
        if (_state.value.isBusy) return
        val leg = SwapLeg.into(token)
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = SwapState.Opening(leg, SwapTiming.started(clock.nowMillis()))

            val owner = wallet.account.value?.address ?: when (val outcome = wallet.connect()) {
                is WalletOutcome.Success -> outcome.value.address
                is WalletOutcome.NoWallet -> return@launch failOpen(leg, SwapFailure.NO_WALLET)
                is WalletOutcome.Cancelled -> {
                    _state.value = SwapState.Closed(SwapNote.CANCELLED_IN_WALLET)
                    return@launch
                }
                is WalletOutcome.Error -> {
                    debugLog.raw("connect: ${outcome.message}")
                    return@launch failOpen(leg, SwapFailure.WALLET_REFUSED)
                }
            }

            val funds = readFunds(owner, leg.token) ?: return@launch failOpen(leg, SwapFailure.CHAIN_UNREAD)
            _state.value = SwapState.Amount(leg, funds, AmountInput.EMPTY)
        }
    }

    // ---- Amount -> Amount ---------------------------------------------------------------------

    /** The user typed. Validation is immediate and local; nothing is requested. */
    fun amountChanged(text: String) {
        val amount = _state.value as? SwapState.Amount ?: return
        _state.value = amount.copy(input = validate(amount, text), note = null)
    }

    /** Max: the whole balance of the side being spent, exactly, with no rounding on the way. */
    fun useMax() {
        val amount = _state.value as? SwapState.Amount ?: return
        amountChanged(SwapAmount.maxText(amount.balanceRaw, amount.leg.input.decimals))
    }

    /**
     * The direction flip: the same machine with the two mints exchanged. Offered only when the
     * wallet actually has the token, because a direction that cannot be funded is not an option.
     * The typed amount is cleared, since the units it was typed in are now the other side's.
     */
    fun flip() {
        val amount = _state.value as? SwapState.Amount ?: return
        if (!amount.canFlip) return
        _state.value = SwapState.Amount(amount.leg.flipped(), amount.funds, AmountInput.EMPTY)
    }

    // ---- Amount -> the attempt ----------------------------------------------------------------

    /** Quote, check the SOL, approve, land. Refuses to start on an amount that is not usable. */
    fun submit() {
        val amount = _state.value as? SwapState.Amount ?: return
        if (!amount.canSubmit) return
        job?.cancel()
        job = viewModelScope.launch { attempt(amount) }
    }

    /** Back to the amount step from a shortfall or a failure, with the typed amount revalidated. */
    fun edit() {
        when (val current = _state.value) {
            is SwapState.Shortfall -> _state.value = amountStep(current.leg, current.funds, current.input)
            is SwapState.Failed -> {
                val funds = current.funds ?: return close()
                _state.value = amountStep(current.leg, funds, current.input)
            }
            else -> Unit
        }
    }

    /** Dismisses the sheet from any state. */
    fun close() {
        job?.cancel()
        job = null
        _state.value = SwapState.Closed()
    }

    // ---- The attempt --------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private suspend fun attempt(start: SwapState.Amount) {
        val leg = start.leg
        val funds = start.funds
        val input = start.input
        var timing = SwapTiming.started(clock.nowMillis())
        var requote = false

        while (true) {
            // ---- Quoting: GET /order, at the tap.
            timing = timing.enterPhase(clock.nowMillis())
            _state.value = SwapState.Quoting(leg, funds, input, requote, timing)
            var order: SwapOrder? = null
            var quoteFailure: SwapFailure? = null
            try {
                order = swapApi.order(leg.input.mint, leg.output.mint, input.raw, funds.owner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SwapError) {
                debugLog.raw("order refused: code=${e.code} ${e.detail ?: e.message}")
                // A non-2xx with no structured body is a transport answer, not a verdict on the
                // pair: a gateway page or a rate limit knows nothing about whether this pair can
                // be quoted, so it must not be reported as though the pair were the problem.
                quoteFailure =
                    if (e is SwapError.Http) SwapFailure.QUOTE_UNAVAILABLE else SwapFailure.QUOTE_REFUSED
            } catch (e: Exception) {
                debugLog.raw("order threw ${e::class.simpleName}: ${e.message}")
                quoteFailure = SwapFailure.QUOTE_UNAVAILABLE
            }
            timing = timing.closeQuoting(clock.nowMillis())
            if (order == null) {
                return fail(leg, funds, input, quoteFailure ?: SwapFailure.QUOTE_UNAVAILABLE, null, requote, timing)
            }

            val quote = SwapQuote.from(order)
            // Decoded here rather than inside the wallet round-trip: bytes this app cannot read
            // are this app's problem, and failing in the middle of the call would spend an
            // approval and then blame the wallet for a payload it was never handed.
            val unsigned = quote.transaction?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            if (unsigned == null) {
                debugLog.raw("order ${quote.requestId} carried no transaction this app could read")
                return fail(leg, funds, input, SwapFailure.NO_TRANSACTION, quote, requote, timing)
            }

            // ---- The SOL check. Here, on the quote's own three fields, before any approval.
            if (!quote.solCost.isCoveredBy(funds.lamports)) {
                _state.value = SwapState.Shortfall(leg, funds, input, quote, timing)
                return
            }

            // ---- AwaitingWallet: the round-trip this product rests on.
            timing = timing.enterPhase(clock.nowMillis())
            _state.value = SwapState.AwaitingWallet(leg, funds, input, quote, requote, timing)
            val outcome = wallet.call { it.signTransactions(arrayOf(unsigned)) }
            timing = timing.closeWallet(clock.nowMillis())
            val signed = when (outcome) {
                is WalletOutcome.Success -> outcome.value.signedPayloads.firstOrNull()
                is WalletOutcome.NoWallet -> return fail(leg, funds, input, SwapFailure.NO_WALLET, quote, requote, timing)
                is WalletOutcome.Cancelled -> {
                    // Nothing was signed and nothing was sent: back to the amount, with it intact.
                    _state.value = SwapState.Amount(leg, funds, input, SwapNote.CANCELLED_IN_WALLET)
                    return
                }
                is WalletOutcome.Error -> {
                    debugLog.raw("wallet: ${outcome.message}")
                    return fail(leg, funds, input, SwapFailure.WALLET_REFUSED, quote, requote, timing)
                }
            }
            if (signed == null) {
                return fail(leg, funds, input, SwapFailure.NOTHING_SIGNED, quote, requote, timing)
            }

            if (!submitSwaps) {
                // Debug: signed, never submitted, no money moved, and no receipt to write.
                _state.value = SwapState.Signed(leg, quote, requote, timing)
                return
            }

            // ---- Landing: POST /execute. This is the call that moves money.
            timing = timing.enterPhase(clock.nowMillis())
            _state.value = SwapState.Landing(leg, funds, input, quote, requote, timing)
            var result: ExecuteResult? = null
            var thrown: SwapError? = null
            var unreachable = false
            try {
                result = swapApi.execute(Base64.getEncoder().encodeToString(signed), quote.requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SwapError) {
                thrown = e
            } catch (e: Exception) {
                debugLog.raw("execute threw ${e::class.simpleName}: ${e.message}")
                unreachable = true
            }
            timing = timing.closeLanding(clock.nowMillis())
            if (unreachable) {
                return fail(leg, funds, input, SwapFailure.SUBMIT_UNAVAILABLE, quote, requote, timing)
            }

            val refusal = thrown ?: result?.errorOrNull()
            if (refusal != null) {
                debugLog.raw("execute refused: code=${refusal.code} ${refusal.detail ?: refusal.message}")
                if (refusal.requotable && !requote) {
                    // The one automatic requote. Fresh order, fresh bytes, a second approval.
                    requote = true
                    continue
                }
                // A non-2xx with no structured body is not a refusal we can read: whether the
                // transaction was forwarded is unknown, so the sentence must not claim either way.
                val reason = when {
                    refusal.requotable -> SwapFailure.QUOTE_GONE
                    refusal is SwapError.Http -> SwapFailure.SUBMIT_UNAVAILABLE
                    else -> SwapFailure.SWAP_REFUSED
                }
                return fail(leg, funds, input, reason, quote, requote, timing)
            }

            val answer = result
            val signature = answer?.signature?.takeIf { it.isNotBlank() }
            if (answer == null || signature == null) {
                debugLog.raw("execute answered ${answer?.status} with no signature")
                // A success with no signature is not a swap that did not happen: what went
                // missing is the answer, not the swap, so the sentence is the one that claims
                // neither. Only an answer that refused may say nothing was swapped.
                val reason =
                    if (answer?.isSuccess == true) SwapFailure.SUBMIT_UNAVAILABLE else SwapFailure.SWAP_REFUSED
                return fail(leg, funds, input, reason, quote, requote, timing)
            }

            // ---- Landed. The fill is the executed result, never the quote.
            val fill = SwapFill(
                signature = signature,
                inAmountRaw = answer.inputAmountResult?.toLongOrNull() ?: quote.inAmountRaw,
                // Never the quote's outAmount: an unreported fill is unknown, not the estimate.
                outAmountRaw = answer.outputAmountResult?.toLongOrNull(),
                slot = answer.slot?.toLongOrNull(),
            )
            // The record is written before the state says it landed, so the receipt screen and
            // Portfolio can never disagree about whether this swap happened.
            val receipt = receiptOf(leg, quote, fill, clock.nowMillis())
            withContext(ioDispatcher) { receipts.record(receipt) }
            _state.value = SwapState.Landed(leg, quote, fill, requote, timing)
            return
        }
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun amountStep(leg: SwapLeg, funds: SwapFunds, input: AmountInput?): SwapState.Amount {
        val text = input?.text.orEmpty()
        return SwapState.Amount(
            leg = leg,
            funds = funds,
            input = if (text.isEmpty()) AmountInput.EMPTY else SwapAmount.parse(text, leg.input.decimals, funds.balanceOf(leg.input)),
        )
    }

    private fun validate(amount: SwapState.Amount, text: String): AmountInput =
        SwapAmount.parse(text, amount.leg.input.decimals, amount.balanceRaw)

    /**
     * Lamports and both balances in two reads. A wallet with no account for a mint has a balance
     * of zero rather than an unknown one: the forwarder returns only non-empty accounts, and a
     * mint with no account is a mint this wallet has none of.
     */
    private suspend fun readFunds(owner: String, token: SwapToken): SwapFunds? {
        val lamports = runCatching { rpc.lamports(owner) }
        val balances = runCatching { rpc.tokenBalances(owner) }
        if (lamports.isFailure || balances.isFailure) {
            debugLog.raw("balances: ${(lamports.exceptionOrNull() ?: balances.exceptionOrNull())?.message}")
            return null
        }
        val accounts = balances.getOrThrow()
        return SwapFunds(
            owner = owner,
            lamports = lamports.getOrThrow(),
            usdcRaw = accounts.filter { it.mint == KnownMints.USDC }.sumOf { it.amountRaw },
            tokenRaw = accounts.filter { it.mint == token.mint }.sumOf { it.amountRaw },
        )
    }

    private fun receiptOf(leg: SwapLeg, quote: SwapQuote, fill: SwapFill, nowMillis: Long): SwapReceipt =
        SwapReceipt(
            signature = fill.signature,
            inputMint = leg.input.mint,
            inputSymbol = leg.input.symbol,
            inputAmountRaw = fill.inAmountRaw,
            inputDecimals = leg.input.decimals,
            outputMint = leg.output.mint,
            outputSymbol = leg.output.symbol,
            outputAmountRaw = fill.outAmountRaw,
            outputDecimals = leg.output.decimals,
            allInCostPct = fill.allInCostPaidPct(quote),
            route = quote.route,
            landedAtMillis = nowMillis,
            slot = fill.slot,
        )

    private fun failOpen(leg: SwapLeg, reason: SwapFailure) {
        _state.value = SwapState.Failed(leg, funds = null, input = null, reason = reason)
    }

    private fun fail(
        leg: SwapLeg,
        funds: SwapFunds,
        input: AmountInput,
        reason: SwapFailure,
        quote: SwapQuote?,
        requoted: Boolean,
        timing: SwapTiming,
    ) {
        _state.value = SwapState.Failed(leg, funds, input, reason, quote, requoted, timing)
    }
}
