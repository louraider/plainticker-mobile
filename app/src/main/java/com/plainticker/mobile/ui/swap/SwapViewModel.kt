package com.plainticker.mobile.ui.swap

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.jupiter.ExecuteResult
import com.plainticker.mobile.data.jupiter.JupiterSwapApi
import com.plainticker.mobile.data.jupiter.SwapError
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.receipts.ReceiptStore
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.data.rpc.TokenBalance
import com.plainticker.mobile.repo.MintRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.RpcRepository
import com.plainticker.mobile.repo.SecondRead
import com.plainticker.mobile.repo.SecondSource
import com.plainticker.mobile.wallet.TransactionGuard
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.WalletSession
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
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
 * 4. **An approval that comes back without a signature is not a failure.** Declined, closed, or a
 *    session that dropped: the app cannot tell them apart, and it does not have to. Nothing was
 *    signed, nothing was sent, nothing is owed, so it returns to [SwapState.Amount] with the typed
 *    amount intact and a neutral note, and the sentence claims no fault.
 *
 * When the xStock is the side being spent (Swap to USDC) three more checks run, all before the
 * wallet opens, because the base units sent are computed from what the forwarder said about the
 * mint and the balance (security audit, 2026-09-26; [SwapTrust]): the decimals must be 8, the
 * multiplier and the balance are read again from [secondSource] and must agree, and Jupiter's
 * dollar value of the order must match the typed quantity at the price the app shows. A second
 * source that does not answer refuses the swap; viewing the holding is unaffected.
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
    /**
     * Reads a token's Token-2022 scaled UI multiplier when the screen that opened the sheet did
     * not already know it. Null only in tests that open with a known multiplier.
     */
    private val mints: MintRepository? = null,
    /**
     * The independent read Swap to USDC is checked against: the mint and this wallet's balance of
     * it, from a node PlainTicker does not run. Required, so no build can leave the check out.
     */
    private val secondSource: SecondSource,
    /** The price the app shows for a token, which the order's own dollar value must agree with. */
    private val prices: PriceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SwapState>(SwapState.Closed())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    private var job: Job? = null

    private val _holding = MutableStateFlow<SwapHolding?>(null)

    /**
     * What the connected wallet holds of the token [watchHolding] named, read from the chain.
     * Null with no wallet session, or before the first read answers. Detail offers "Swap to
     * USDC" from it, and only from it: the app's own receipts never stand in for a balance here.
     */
    val holding: StateFlow<SwapHolding?> = _holding.asStateFlow()

    private var watched: SwapToken? = null
    private var holdingJob: Job? = null

    // ---- Closed -> Opening -> Amount ---------------------------------------------------------

    /**
     * Opens the sheet for USDC into [token]: authorize the wallet if it is not authorized yet,
     * then read the lamports and the two balances the rest of the machine decides on.
     */
    fun open(token: SwapToken) = openLeg(SwapLeg.into(token))

    /**
     * Opens the sheet for [token] back to USDC, "Swap to USDC": the same machine, the same
     * checks, with the xStock as the side being spent.
     */
    fun openOut(token: SwapToken) = openLeg(SwapLeg.outOf(token))

    /**
     * @param minContextSlot the slot a swap just landed in, when this opening follows one: the
     *   balance read must be at least that new, or it would show the wallet from before the swap.
     */
    private fun openLeg(requested: SwapLeg, minContextSlot: Long? = null) {
        if (_state.value.isBusy) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = SwapState.Opening(requested, SwapTiming.started(clock.nowMillis()))

            val owner = wallet.account.value?.address ?: when (val outcome = wallet.connect()) {
                is WalletOutcome.Success -> outcome.value.address
                is WalletOutcome.NoWallet -> return@launch failOpen(requested, SwapFailure.NO_WALLET)
                is WalletOutcome.Cancelled -> {
                    _state.value = SwapState.Closed(SwapNote.CANCELLED_IN_WALLET)
                    return@launch
                }
                is WalletOutcome.Error -> {
                    debugLog.raw("connect: ${outcome.message}")
                    return@launch failOpen(requested, SwapFailure.CONNECT_REFUSED)
                }
            }

            // No quantity of the xStock is stated until its multiplier is known: a split token
            // read as unsplit would put a balance ten times off on the screen and in the field.
            val token = resolveScale(requested.token) ?: return@launch failOpen(requested, SwapFailure.CHAIN_UNREAD)
            // Every xStock mint carries 8 decimals, and the typed amount becomes base units through
            // this number, so any other value is refused here, whoever supplied it. Never replaced
            // by the constant: a read that says 10 is a read to distrust, not one to correct.
            if (!SwapTrust.decimalsPinned(token.decimals)) {
                debugLog.raw("mint ${token.mint} read with ${token.decimals} decimals, refused")
                return@launch failOpen(requested, SwapFailure.CHAIN_UNREAD)
            }
            val leg = requested.withToken(token)
            val read = readFunds(owner, leg.token, minContextSlot)
                ?: return@launch failOpen(leg, SwapFailure.CHAIN_UNREAD)
            // Swap to USDC: the scale and the balance agree with a second source before any
            // quantity of the xStock is offered, and Max and the cap use the smaller balance.
            val funds = if (leg.intoToken) {
                read
            } else {
                when (val checked = confirmOut(leg, read, minContextSlot)) {
                    is Confirmation.Refused -> return@launch failOpen(leg, checked.reason)
                    is Confirmation.Capped -> checked.funds
                }
            }
            _state.value = SwapState.Amount(leg, funds, AmountInput.EMPTY)
        }
    }

    /**
     * Keeps [holding] current for [token] while a wallet is connected. Detail calls it once the
     * catalog has named the mint; a second call for the same token is free.
     */
    fun watchHolding(token: SwapToken) {
        if (watched == token) return
        watched = token
        holdingJob?.cancel()
        _holding.value = null
        holdingJob = viewModelScope.launch {
            wallet.account.collect { account ->
                if (account == null) _holding.value = null else readHolding(account.address, token, null)
            }
        }
    }

    // ---- Amount -> Amount ---------------------------------------------------------------------

    /** The user typed. Validation is immediate and local; nothing is requested. */
    fun amountChanged(text: String) {
        val amount = _state.value as? SwapState.Amount ?: return
        _state.value = amount.copy(input = validate(amount, text), note = null)
    }

    /**
     * Max: the whole balance of the side being spent, exactly. The field shows it as the wallet
     * does (scaled by the multiplier), and the amount sent is the raw balance itself, set here
     * rather than read back out of the text, so no conversion can shave a base unit off it.
     */
    fun useMax() {
        val amount = _state.value as? SwapState.Amount ?: return
        val side = amount.leg.input
        val text = SwapAmount.maxText(amount.balanceRaw, side.decimals, side.multiplier ?: BigDecimal.ONE)
        val parsed = validate(amount, text)
        val input = if (parsed.problem == null) parsed.copy(raw = amount.balanceRaw) else parsed
        _state.value = amount.copy(input = input, note = null)
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

    /**
     * The same amount again, with a fresh quote: the way on from a failure whose [SwapFailure.next]
     * is [FailureNext.RETRY] or [FailureNext.NEW_QUOTE]. Refused for every other failure, and
     * above all for [SwapFailure.SUBMIT_UNAVAILABLE], where a first attempt may have landed.
     */
    fun retry() {
        val failed = _state.value as? SwapState.Failed ?: return
        if (failed.reason.next != FailureNext.RETRY && failed.reason.next != FailureNext.NEW_QUOTE) return
        val funds = failed.funds ?: return
        val input = failed.input?.takeIf { it.isUsable } ?: return
        job?.cancel()
        job = viewModelScope.launch { attempt(SwapState.Amount(failed.leg, funds, input)) }
    }

    /**
     * From a fresh receipt, the other direction: the token that just arrived, back the way it
     * came. The balance is read again, no older than the slot the swap landed in.
     */
    fun swapBack() {
        val landed = _state.value as? SwapState.Landed ?: return
        openLeg(landed.leg.flipped(), minContextSlot = landed.fill.slot)
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
        val input = start.input
        var timing = SwapTiming.started(clock.nowMillis())
        var requote = false

        // ---- Swap to USDC is checked again at the tap, whatever path reached the amount step:
        // the flip from the other direction never passed the check at opening. A balance that
        // shrank below the typed amount goes back to the amount step to say so.
        val funds = if (leg.intoToken) {
            start.funds
        } else {
            _state.value = SwapState.Quoting(leg, start.funds, input, requote, timing)
            when (val checked = confirmOut(leg, start.funds)) {
                is Confirmation.Refused -> return fail(leg, start.funds, input, checked.reason, null, requote, timing)
                is Confirmation.Capped -> {
                    if (input.raw > checked.funds.tokenRaw) {
                        _state.value = amountStep(leg, checked.funds, input)
                        return
                    }
                    checked.funds
                }
            }
        }

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

            var quote = SwapQuote.from(order)
            // Decoded here rather than inside the wallet round-trip: bytes this app cannot read
            // are this app's problem, and failing in the middle of the call would spend an
            // approval and then blame the wallet for a payload it was never handed.
            val unsigned = quote.transaction?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            if (unsigned == null) {
                debugLog.raw("order ${quote.requestId} carried no transaction this app could read")
                return fail(leg, funds, input, SwapFailure.NO_TRANSACTION, quote, requote, timing)
            }

            // ---- The bytes against the request (security audit, finding 2). The sheet's figures
            // are Jupiter's JSON; what the wallet signs is the transaction. Both are read against
            // what was asked for before the wallet opens, and a mismatch is nothing to approve.
            val verdict = TransactionGuard.checkSwap(
                bytes = unsigned,
                wallet = funds.owner,
                order = order,
                inputMint = leg.input.mint,
                outputMint = leg.output.mint,
                amount = input.raw,
            )
            if (verdict is TransactionGuard.Verdict.Refuse) {
                debugLog.raw("order ${quote.requestId} refused before the wallet: ${verdict.reason}")
                _state.value = SwapState.Failed(leg, funds, input, SwapFailure.GUARD_REFUSED, quote, requote, timing, verdict.why)
                return
            }

            // ---- The dollar value against what was typed (security audit, 2026-09-26). The guard
            // proves the bytes spend input.raw; it cannot know whether input.raw is what the person
            // meant. Jupiter's value of the order it built must match the typed quantity at the
            // price the app shows, within SwapTrust.VALUE_BOUND. Swap to USDC only: the other
            // direction spends USDC, whose decimals are pinned in SwapLeg.USDC.
            // One Price v3 call for both the value check (Swap to USDC) and the SOL price the
            // all-in cost needs (judges' review, 2026-09-27). Bounded: a slow or refused price
            // costs the all-in figure, never the swap, and the sheet then says "Route cost".
            val priced = pricesOf(if (leg.intoToken) listOf(KnownMints.WSOL) else listOf(leg.input.mint, KnownMints.WSOL))
            quote = quote.copy(solUsd = priced[KnownMints.WSOL])
            if (!leg.intoToken) {
                val price = priced[leg.input.mint]
                when (val value = SwapTrust.checkValue(leg.input.ui(input.raw), price, order.inUsdValue)) {
                    is SwapTrust.ValueVerdict.Consistent -> Unit
                    is SwapTrust.ValueVerdict.Mismatch -> {
                        debugLog.raw("order ${quote.requestId} refused before the wallet: ${value.reason}")
                        return fail(leg, funds, input, SwapFailure.VALUE_MISMATCH, quote, requote, timing)
                    }
                    is SwapTrust.ValueVerdict.Unchecked -> {
                        debugLog.raw("order ${quote.requestId} refused before the wallet: ${value.reason}")
                        return fail(leg, funds, input, SwapFailure.VALUE_UNCHECKED, quote, requote, timing)
                    }
                }
            }

            // ---- Dust. A quote that delivers nothing, or whose floor is nothing, is fees for no
            // swap: a sliver of an xStock worth under a millionth of a dollar rounds to no USDC.
            if (quote.outAmountRaw <= 0L || quote.worstCaseOutRaw <= 0L) {
                debugLog.raw("order ${quote.requestId} delivers ${quote.outAmountRaw}, floor ${quote.worstCaseOutRaw}")
                return fail(leg, funds, input, SwapFailure.QUOTE_DUST, quote, requote, timing)
            }

            // ---- The SOL check. Here, on the quote's own three fields, before any approval.
            if (!quote.solCost.isCoveredBy(funds.lamports)) {
                _state.value = SwapState.Shortfall(leg, funds, input, quote, timing)
                return
            }

            // ---- The wallet this order was built for is still the one connected. A disconnect
            // mid-flow, or a session that now answers for another account, must not be handed
            // bytes whose taker is someone else.
            if (wallet.account.value?.address != funds.owner) {
                return fail(leg, funds, input, SwapFailure.WALLET_CHANGED, quote, requote, timing)
            }

            // ---- AwaitingWallet: the round-trip this product rests on.
            timing = timing.enterPhase(clock.nowMillis())
            _state.value = SwapState.AwaitingWallet(leg, funds, input, quote, requote, timing)
            // sign_transactions is optional in MWA 2.x (judges' review, 2026-09-27): the wallet's
            // capabilities are read in the same session, after it authorized and before anything
            // is asked of it, and a wallet that says it only signs by sending is told so plainly
            // instead of failing the request with a generic error.
            val outcome = wallet.call { ops ->
                if (SwapWalletCapabilities.refusesSignOnly(readCapabilities(ops))) {
                    null
                } else {
                    WalletSigned(ops.signTransactions(arrayOf(unsigned)).signedPayloads.firstOrNull())
                }
            }
            timing = timing.closeWallet(clock.nowMillis())
            if (outcome is WalletOutcome.Success && outcome.value == null) {
                debugLog.raw("wallet lists no solana:signTransactions, nothing asked of it")
                return fail(leg, funds, input, SwapFailure.SIGN_ONLY_UNSUPPORTED, quote, requote, timing)
            }
            val signed = when (outcome) {
                is WalletOutcome.Success -> outcome.value?.payload
                // The wallet itself is gone, which is not something a second tap can fix.
                is WalletOutcome.NoWallet -> return fail(leg, funds, input, SwapFailure.NO_WALLET, quote, requote, timing)
                is WalletOutcome.Cancelled -> null
                is WalletOutcome.Error -> {
                    debugLog.raw("wallet: ${outcome.message}")
                    null
                }
            }
            if (signed == null) {
                // No signature came back. Whether the person declined, the sheet went away or the
                // session dropped is not something this app can know: the device log of the
                // attempt that was not approved and of the one that landed differ only in whether
                // a signature followed the session closing (docs/data-map.md). What is certain is
                // the same in all three: nothing was signed, /execute was never called, and no
                // money moved. So it is a cancellation and not a failure, and it returns to the
                // amount step with the typed amount intact rather than to a terminal screen.
                _state.value = SwapState.Amount(leg, funds, input, SwapNote.NOT_APPROVED)
                return
            }

            // A signature that came back from a session now answering for another account is not
            // this order's signature, and sending it would only fail on chain at best.
            if (wallet.account.value?.address != funds.owner) {
                return fail(leg, funds, input, SwapFailure.WALLET_CHANGED, quote, requote, timing)
            }

            // ---- What the wallet handed back is what the guard read (judges' review, 2026-09-26).
            // The guard checked the bytes this app gave the wallet; /execute sends the bytes the
            // wallet gave back. Only signatures may differ, and this wallet's must be there.
            if (!TransactionGuard.signedMatches(unsigned, signed, funds.owner)) {
                debugLog.raw("order ${quote.requestId}: the signed transaction is not the one checked, not sent")
                return fail(leg, funds, input, SwapFailure.SIGNED_MISMATCH, quote, requote, timing)
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
                return fail(leg, funds, input, executeFailure(refusal), quote, requote, timing)
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
            // What Detail offers next depends on the balance this swap just changed, so it is
            // read again, no older than the slot the swap landed in.
            watched?.takeIf { it.mint == leg.token.mint }?.let { token ->
                viewModelScope.launch { readHolding(funds.owner, token, fill.slot) }
            }
            return
        }
    }

    /**
     * Which failure a structured /execute refusal is, once the one automatic requote is spent.
     *
     * The codes are Jupiter Ultra's own. What matters is the claim each lets the sheet make about
     * the money: a refusal Jupiter reports as final may say nothing was swapped; one that means
     * Jupiter itself does not know (-1001 and -2001 unknown, -1006 timed out) may not, so it is
     * [SwapFailure.SUBMIT_UNAVAILABLE], which offers Portfolio and never a second attempt. The
     * words are matched as well as the codes because an Ultra refusal's `code` is not always set.
     */
    private fun executeFailure(refusal: SwapError): SwapFailure {
        val detail = refusal.detail.orEmpty()
        return when {
            refusal.requotable -> SwapFailure.QUOTE_GONE
            // A non-2xx with no structured body is not a refusal we can read: whether the
            // transaction was forwarded is unknown, so the sentence must not claim either way.
            refusal is SwapError.Http -> SwapFailure.SUBMIT_UNAVAILABLE
            refusal.code in UNKNOWN_OUTCOME_CODES -> SwapFailure.SUBMIT_UNAVAILABLE
            refusal.code == CODE_SLIPPAGE || detail.contains("slippage", ignoreCase = true) -> SwapFailure.SLIPPAGE
            refusal.code in EXPIRED_CODES -> SwapFailure.QUOTE_EXPIRED
            else -> SwapFailure.SWAP_REFUSED
        }
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private fun amountStep(leg: SwapLeg, funds: SwapFunds, input: AmountInput?): SwapState.Amount {
        val text = input?.text.orEmpty()
        return SwapState.Amount(
            leg = leg,
            funds = funds,
            input = if (text.isEmpty()) {
                AmountInput.EMPTY
            } else {
                SwapAmount.parse(text, leg.input.decimals, funds.balanceOf(leg.input), leg.input.multiplier ?: BigDecimal.ONE)
            },
        )
    }

    private fun validate(amount: SwapState.Amount, text: String): AmountInput =
        SwapAmount.parse(
            text,
            amount.leg.input.decimals,
            amount.balanceRaw,
            amount.leg.input.multiplier ?: BigDecimal.ONE,
        )

    /**
     * Lamports and both balances in two reads. A wallet with no account for a mint has a balance
     * of zero rather than an unknown one: the forwarder returns only non-empty accounts, and a
     * mint with no account is a mint this wallet has none of. A frozen account is listed but not
     * counted, because nothing in it can be spent, and its mint is named so the sheet can say so.
     *
     * [minContextSlot] is asked for twice before giving up on it: a node a slot or two behind the
     * one Jupiter confirmed in answers "minimum context slot not reached" for a moment, and the
     * read that follows a landing is exactly when that happens. After that the plain read stands,
     * because a balance up to a minute old is still a balance, and no balance is a failure.
     */
    private suspend fun readFunds(owner: String, token: SwapToken, minContextSlot: Long? = null): SwapFunds? {
        val slots = if (minContextSlot == null) listOf(null) else listOf(minContextSlot, minContextSlot, null)
        var failure: Throwable? = null
        for ((attemptIndex, slot) in slots.withIndex()) {
            if (attemptIndex > 0) delay(FRESH_READ_RETRY_MS)
            val lamports = runCatching { rpc.lamports(owner, slot) }
            val balances = runCatching { rpc.tokenBalances(owner, slot) }
            if (lamports.isFailure || balances.isFailure) {
                failure = lamports.exceptionOrNull() ?: balances.exceptionOrNull()
                continue
            }
            return fundsOf(owner, token, lamports.getOrThrow(), balances.getOrThrow())
        }
        debugLog.raw("balances: ${failure?.message}")
        return null
    }

    private fun fundsOf(owner: String, token: SwapToken, lamports: Long, accounts: List<TokenBalance>): SwapFunds {
        fun spendable(mint: String) = accounts.filter { it.mint == mint && !it.frozen }.sumOf { it.amountRaw }
        val usdcRaw = spendable(KnownMints.USDC)
        val tokenRaw = spendable(token.mint)
        val frozen = accounts.filter { it.frozen && it.amountRaw > 0L }.map { it.mint }.toSet()
        return SwapFunds(
            owner = owner,
            lamports = lamports,
            usdcRaw = usdcRaw,
            tokenRaw = tokenRaw,
            // Named only where the frozen account is the whole story: a spendable balance beside
            // a frozen one is simply the spendable balance.
            frozenMints = buildSet {
                if (KnownMints.USDC in frozen && usdcRaw == 0L) add(KnownMints.USDC)
                if (token.mint in frozen && tokenRaw == 0L) add(token.mint)
            },
        )
    }

    /** The holding [watchHolding] reports, from one balance read; a failed read keeps the last one. */
    private suspend fun readHolding(owner: String, token: SwapToken, minContextSlot: Long?) {
        val accounts = runCatching { rpc.tokenBalances(owner, minContextSlot) }.getOrNull()
            ?: runCatching { rpc.tokenBalances(owner, null) }.getOrNull()
            ?: return
        if (watched != token) return
        _holding.value = SwapHolding(
            token = token,
            raw = accounts.filter { it.mint == token.mint && !it.frozen }.sumOf { it.amountRaw },
        )
    }

    /**
     * [token] with its multiplier known, or null when it cannot be. A token that already carries
     * one (the screen that opened the sheet read the mint) is taken as it is; otherwise the mint
     * is read here, and the multiplier in force at that read is the one used. A mint with no
     * scaled amount extension is a multiplier of one, read off the chain. A mint that cannot be
     * read leaves it unknown, and the sheet stops rather than guessing at a split.
     */
    private suspend fun resolveScale(token: SwapToken): SwapToken? {
        if (token.scaleKnown) return token
        val reading = runCatching { mints?.mint(token.mint) }.getOrNull() ?: return null
        val facts = reading.facts ?: return null
        val multiplier = facts.scaledUiAmount?.let { SplitMultiplier.ofMint(it).effectiveAt(reading.readAtMillis) }
            ?: SplitMultiplier.NONE
        return token.copy(decimals = facts.decimals, multiplier = BigDecimal.valueOf(multiplier))
    }

    /** The outcome of checking the xStock side against [secondSource]. */
    private sealed interface Confirmation {
        /** Agreed; [funds] carries the smaller of the two balances. */
        data class Capped(val funds: SwapFunds) : Confirmation

        data class Refused(val reason: SwapFailure) : Confirmation
    }

    /**
     * [funds] checked against a second, independent read of the xStock being spent. Unreachable
     * is a refusal and not a pass: this is the one path where a wrong number is somebody's money.
     */
    private suspend fun confirmOut(leg: SwapLeg, funds: SwapFunds, minContextSlot: Long? = null): Confirmation {
        // One call: the source owns its own patience now (audit 2026-09-26, item 6). It retries
        // once after a short backoff with the slot relaxed by about a minute, then asks a second
        // public node, so a rate limit or a node a few slots behind no longer pauses the swap. A
        // read that trails only makes the cap smaller, never larger. Retrying here as well would
        // multiply that ladder three times over for a node that is simply down.
        val second: SecondRead = try {
            secondSource.read(funds.owner, leg.token.mint, minContextSlot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog.raw("second source: ${e::class.simpleName}: ${e.message}")
            return Confirmation.Refused(SwapFailure.SECOND_SOURCE_UNREACHABLE)
        }
        return when (val verdict = SwapTrust.confirmScale(leg.token, funds.tokenRaw, second)) {
            is SwapTrust.ScaleVerdict.Mismatch -> {
                debugLog.raw("second source disagrees: ${verdict.reason}")
                Confirmation.Refused(SwapFailure.SECOND_SOURCE_MISMATCH)
            }
            is SwapTrust.ScaleVerdict.Confirmed -> {
                if (verdict.capRaw < funds.tokenRaw) {
                    debugLog.raw("balance capped from ${funds.tokenRaw} to ${verdict.capRaw} by the second source")
                }
                Confirmation.Capped(funds.copy(tokenRaw = verdict.capRaw))
            }
        }
    }

    /**
     * The prices the app shows for [mints], in one call, or without the ones that did not come
     * back. Bounded by [PRICE_TIMEOUT_MS] so a backed-off price never holds the wallet shut.
     */
    private suspend fun pricesOf(mints: List<String>): Map<String, Double> = try {
        withTimeoutOrNull(PRICE_TIMEOUT_MS) { prices.prices(mints) }
            ?.mapNotNull { (mint, entry) -> entry.usdPrice.takeIf { it.isFinite() && it > 0.0 }?.let { mint to it } }
            ?.toMap()
            ?: emptyMap<String, Double>().also { debugLog.raw("price: no answer within $PRICE_TIMEOUT_MS ms") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        debugLog.raw("price: ${e::class.simpleName}: ${e.message}")
        emptyMap()
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
            routeCostPct = fill.routeCostPaidPct(quote),
            solCostUsd = quote.solCostUsd,
            rentUsd = quote.rentUsd,
            inputUsd = quote.inUsdValue,
            route = quote.route,
            landedAtMillis = nowMillis,
            slot = fill.slot,
            inputMultiplier = (leg.input.multiplier ?: BigDecimal.ONE).toDouble(),
            outputMultiplier = (leg.output.multiplier ?: BigDecimal.ONE).toDouble(),
        )

    /**
     * The wallet's capabilities, or null when it would not say. Null is not a refusal: a wallet
     * that cannot answer is asked to sign as before, and fails there if it must.
     */
    private suspend fun readCapabilities(ops: AdapterOperations): MobileWalletAdapterClient.GetCapabilitiesResult? = try {
        ops.getCapabilities()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        debugLog.raw("getCapabilities: ${e::class.simpleName}: ${e.message}")
        null
    }

    /** What sign_transactions handed back, so a refusal before signing can be told apart from it. */
    private class WalletSigned(val payload: ByteArray?)

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

    private companion object {
        /** One beat between two reads that asked for a slot the node had not reached yet. */
        const val FRESH_READ_RETRY_MS = 1_000L

        /** The longest the price read before the wallet may take; past it the sheet says "Route cost". */
        const val PRICE_TIMEOUT_MS = 2_500L

        /** Jupiter's program error for a route that ended below its slippage bound. */
        const val CODE_SLIPPAGE = 6001

        /** /execute codes that mean Jupiter does not know whether it landed: unknown, timed out. */
        val UNKNOWN_OUTCOME_CODES = setOf(-1001, -1006, -2001)

        /** /execute codes that mean the order or its blockhash expired unsent or unlanded. */
        val EXPIRED_CODES = setOf(-1, -1004, -1005)
    }
}

/**
 * What the connected wallet holds of one token, from the chain. [raw] counts only accounts that
 * can spend: a frozen one is excluded, the same way [SwapFunds] excludes it.
 */
data class SwapHolding(val token: SwapToken, val raw: Long) {
    /** True when there is something to swap back to USDC. */
    val canSwapOut: Boolean get() = raw > 0L
}
