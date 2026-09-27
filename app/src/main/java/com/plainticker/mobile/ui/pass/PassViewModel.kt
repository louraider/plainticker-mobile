package com.plainticker.mobile.ui.pass

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funkatronics.encoders.Base58
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock
import com.plainticker.mobile.data.plainticker.EntitlementApi
import com.plainticker.mobile.data.plainticker.EntitlementError
import com.plainticker.mobile.data.plainticker.EntitlementResponse
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.data.plainticker.PassApi
import com.plainticker.mobile.data.plainticker.PassError
import com.plainticker.mobile.data.plainticker.PromoApi
import com.plainticker.mobile.data.plainticker.PromoError
import com.plainticker.mobile.data.receipts.PassReceipt
import com.plainticker.mobile.data.receipts.PassReceiptStore
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.repo.RpcRepository
import com.plainticker.mobile.wallet.TransactionGuard
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the raw upstream text goes, which is never to a screen (the same rule
 * [com.plainticker.mobile.ui.vote.VoteDebugLog] keeps for the vote machine).
 */
fun interface PassDebugLog {
    fun raw(line: String)

    companion object {
        val ANDROID = PassDebugLog { line -> if (BuildConfig.DEBUG) Log.d("PassMachine", line) }
    }
}

/**
 * What the Portfolio block reads (task A6): this device's own entitlement, and the connected
 * wallet's own staked SKR, read independently of any payment. Neither is a verdict about the
 * reader: a stake below the threshold is stated as a figure, never as a refusal.
 */
data class ProUiState(
    val entitlementLoading: Boolean = true,
    val pro: Boolean = false,
    val source: EntitlementSource? = null,
    /** ISO-derived epoch millis for a pass or a subscription; null for stake or for no entitlement. */
    val untilMillis: Long? = null,
    /** 503 `monetization_disabled`, or a 404: Pro is not offered by this server yet. Not a failure. */
    val entitlementDisabled: Boolean = false,
    /** Any other failure reading entitlement; retryable, unlike [entitlementDisabled]. */
    val entitlementFailed: Boolean = false,
    val walletConnected: Boolean = false,
    /** The connected wallet's own staked SKR principal, in base units; null while unread. */
    val stakeRaw: Long? = null,
    /** The wallet is connected and a stake read was attempted, but the figure could not be read. */
    val stakeUnread: Boolean = false,
    /**
     * A landed pass signature this device has not seen the server confirm yet (task A6 review),
     * or null when none is pending. Read from [PassReceiptStore] on init, so it survives process
     * death, and while it is non-null the Pay action is withheld: a second payment is never
     * offered for one that might still land through the chain or the ten-minute cron.
     */
    val pendingSignature: String? = null,
)

/**
 * Pro entitlement and the pay machine, in one class (task A6).
 *
 * The two halves share a constructor because a landed payment changes the first: [pro] is this
 * device's own entitlement, read from the code [DevicePassStore] keeps and refreshed on init and
 * on request; [state] is the machine that turns a tap into a signed transfer, built on the same
 * division of labour [com.plainticker.mobile.ui.vote.VoteViewModel] already proved for a vote: the
 * server builds an unsigned transaction because the RPC forwarder exposes no
 * `getLatestBlockhash`, the wallet signs and sends it through Mobile Wallet Adapter, and this app
 * never holds a key.
 *
 * **The stake read here is informational, not a gate.** Unlike the vote machine, nothing here
 * refuses to draw a stake below the entitlement threshold or reads it as an answer about the
 * wallet: the Portfolio block states the figure and lets the server's own entitlement resolver be
 * the one place that decides what it means.
 *
 * **A landed payment is not undone by a confirm call that did not answer.** `signAndSendTransactions`
 * both signs and submits; once it returns a signature the payment is on the chain, and this app's
 * own early check is a convenience for not waiting for the cron, not the payment's proof. So
 * [PassState.Landed] is reached whether or not [PassApi.confirm] itself resolved, with
 * [PassState.Landed.entitlement] null when it did not, and the signature is written to
 * [PassReceiptStore] before that call is even made, so a process death between the wallet
 * answering and the confirm call resolving does not lose the record: [init] reads it back and
 * retries the confirm, and [pay] refuses a second attempt while one is still pending.
 *
 * **Neither line of the Portfolio block may describe a different wallet than the other, even for
 * one frame.** [ProUiState.pro] is resolved from this device's code, not from whichever wallet
 * happens to be connected, but the sentence it renders as reads "this wallet"; so the instant the
 * connected wallet changes, both halves drop to a loading state together before either is asked
 * again, and the entitlement half is re-asked on every change alongside the stake half, never left
 * standing on what the previous wallet answered.
 */
class PassViewModel(
    private val passApi: PassApi,
    private val entitlementApi: EntitlementApi,
    private val promoApi: PromoApi,
    private val wallet: WalletSession,
    private val rpc: RpcRepository,
    private val devicePassStore: DevicePassStore,
    private val passReceiptStore: PassReceiptStore,
    private val clock: Clock = WallClock,
    private val debugLog: PassDebugLog = PassDebugLog.ANDROID,
    /** Where a receipt is written. viewModelScope runs on Main, and a file write does not. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _pro = MutableStateFlow(ProUiState(pendingSignature = pendingReceipt()?.signature))
    val pro: StateFlow<ProUiState> = _pro.asStateFlow()

    private val _state = MutableStateFlow<PassState>(PassState.Closed)
    val state: StateFlow<PassState> = _state.asStateFlow()

    private val _promo = MutableStateFlow<PromoState>(PromoState.Idle)
    val promo: StateFlow<PromoState> = _promo.asStateFlow()

    private var payJob: Job? = null
    private var entitlementJob: Job? = null
    private var promoJob: Job? = null

    init {
        // A payment this device saw land but never saw confirmed, from before this instance
        // existed: the retry is silent, and its result is exactly what a fresh confirm's would be.
        pendingReceipt()?.let { pending -> viewModelScope.launch { confirmLanded(pending) } }

        viewModelScope.launch {
            wallet.account.collect { account ->
                // The wallet changed (including to or from no wallet at all): both halves of the
                // block are stale the instant this fires, so both drop to one honest loading
                // state, in the SAME update, before either is asked again. Splitting this into two
                // updates (or leaving entitlementLoading to refreshEntitlement's own first line)
                // would let a fast stake read land while the entitlement line still showed the
                // previous wallet's source and date, which is the contradiction this guards
                // against: two "this wallet" sentences about two different real wallets.
                _pro.update {
                    it.copy(
                        walletConnected = account != null,
                        stakeRaw = null,
                        stakeUnread = false,
                        entitlementLoading = true,
                        entitlementDisabled = false,
                        entitlementFailed = false,
                    )
                }
                refreshEntitlement()
                if (account != null) loadStake(account.address)
            }
        }
    }

    /** The one pending receipt this device holds, or null. At most one exists by construction. */
    private fun pendingReceipt(): PassReceipt? = passReceiptStore.receipts.value.firstOrNull { !it.confirmed }

    /**
     * Reads this device's code and asks the server what it carries. Never costs a wallet call.
     * Cancels a refresh already in flight, so a wallet change that fires twice in quick succession
     * cannot let the first answer land after the second and show a stale source or date.
     */
    fun refreshEntitlement() {
        entitlementJob?.cancel()
        entitlementJob = viewModelScope.launch {
            _pro.update { it.copy(entitlementLoading = true, entitlementDisabled = false, entitlementFailed = false) }
            val code = devicePassStore.code()
            try {
                applyEntitlement(entitlementApi.get(code))
            } catch (e: CancellationException) {
                throw e
            } catch (e: EntitlementError.Disabled) {
                _pro.update { it.copy(entitlementLoading = false, entitlementDisabled = true) }
            } catch (e: Exception) {
                debugLog.raw("entitlement: ${e::class.simpleName}: ${e.message}")
                _pro.update { it.copy(entitlementLoading = false, entitlementFailed = true) }
            }
        }
    }

    private fun applyEntitlement(response: EntitlementResponse) {
        _pro.update {
            it.copy(
                entitlementLoading = false,
                entitlementDisabled = false,
                entitlementFailed = false,
                pro = response.pro,
                source = response.sourceKind,
                untilMillis = response.untilEpochMillis(),
            )
        }
    }

    // ---- Promo code -----------------------------------------------------------------------

    /** "Have a code?": opens the inline field, empty, from [PromoState.Idle]. */
    fun openPromo() {
        if (_promo.value !is PromoState.Idle) return
        _promo.value = PromoState.Editing("")
    }

    /**
     * The field's text changed. Normalized here, the same way the server normalizes before it
     * checks a code ([PromoApi.normalize]), so what the field shows is exactly what [applyPromo]
     * will send. Also the way a [PromoState.Failed] resumes editing: the reader touching the
     * field again drops the error and keeps whatever they had typed, now normalized.
     */
    fun promoInputChanged(raw: String) {
        val current = when (val s = _promo.value) {
            is PromoState.Editing -> s.input
            is PromoState.Failed -> s.input
            else -> return
        }
        val normalized = PromoApi.normalize(raw)
        if (normalized == current && _promo.value is PromoState.Editing) return
        _promo.value = PromoState.Editing(normalized)
    }

    /** Collapses the field from any state; a redeem in flight is left to finish on its own. */
    fun dismissPromo() {
        if (_promo.value is PromoState.Applying) return
        _promo.value = PromoState.Idle
    }

    /**
     * Sends whatever [PromoState.Editing] or [PromoState.Failed] currently holds. A blank code
     * (the Apply action should not even be offered for one, but this is the belt to that brace)
     * reads as the server's own `invalid_code`, never a network call.
     */
    fun applyPromo() {
        val input = when (val s = _promo.value) {
            is PromoState.Editing -> s.input
            is PromoState.Failed -> s.input
            else -> return
        }
        if (input.isBlank()) {
            _promo.value = PromoState.Failed(input, PromoRefusal.INVALID_CODE)
            return
        }
        promoJob?.cancel()
        promoJob = viewModelScope.launch {
            _promo.value = PromoState.Applying(input)
            try {
                val response = promoApi.redeem(input, devicePassStore.code())
                _promo.value = PromoState.Success(response.untilEpochMillis())
                // The hero and the Pro locks read ProUiState, not PromoState, so the fresh read
                // this device just earned reaches them the one way anything else here does.
                refreshEntitlement()
            } catch (e: CancellationException) {
                throw e
            } catch (e: PromoError) {
                debugLog.raw("promo/redeem refused: status=${e.status} code=${e.code} ${e.detail ?: e.message}")
                _promo.value = PromoState.Failed(input, promoRefusalOf(e))
            } catch (e: Exception) {
                debugLog.raw("promo/redeem threw ${e::class.simpleName}: ${e.message}")
                _promo.value = PromoState.Failed(input, PromoRefusal.UNAVAILABLE)
            }
        }
    }

    private fun promoRefusalOf(e: PromoError): PromoRefusal = when (e) {
        is PromoError.BadRequest -> PromoRefusal.BAD_REQUEST
        is PromoError.InvalidCode -> PromoRefusal.INVALID_CODE
        is PromoError.ExpiredCode -> PromoRefusal.EXPIRED_CODE
        is PromoError.AlreadyRedeemed -> PromoRefusal.ALREADY_REDEEMED
        is PromoError.AlreadyApplied -> PromoRefusal.ALREADY_APPLIED
        is PromoError.RateLimited -> PromoRefusal.RATE_LIMITED
        is PromoError.NotOpen -> PromoRefusal.NOT_OPEN
        is PromoError.Unavailable -> PromoRefusal.UNAVAILABLE
    }

    private suspend fun loadStake(address: String) {
        val stake = try {
            rpc.skrStake(address)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog.raw("skrStake threw ${e::class.simpleName}: ${e.message}")
            _pro.update { it.copy(stakeRaw = null, stakeUnread = true) }
            return
        }
        val principal = SkrStakeBound.principalOf(stake)
        _pro.update { it.copy(stakeRaw = principal, stakeUnread = principal == null) }
    }

    // ---- Paying -------------------------------------------------------------------------------

    /**
     * The tap: connect if there is no session, then ask the server to build the transfer.
     * Refuses while [ProUiState.pendingSignature] is set: a payment already on the chain and not
     * yet confirmed is not a reason to sign a second one, it is a reason to wait or retry confirm.
     *
     * Also refuses outright while [ProUiState.entitlementDisabled] is true: the Portfolio block
     * already withholds the Pay action for that state ([com.plainticker.mobile.ui.portfolio.payOffered]),
     * and this guard is the belt to that brace, so a stale composition or a caller other than the
     * block can never send a wallet through its own connect sheet for a payment the server has
     * already said it will refuse (a review finding on a v0.7.0 release build).
     */
    fun pay(mint: String = PassApi.MINT_USDC) {
        if (_state.value.isBusy) return
        if (_pro.value.pendingSignature != null) return
        if (_pro.value.entitlementDisabled) return
        val known = wallet.account.value?.address
        _state.value = if (known == null) PassState.Opening() else PassState.Building(known)
        payJob?.cancel()
        payJob = viewModelScope.launch { attempt(mint, known) }
    }

    /** Try the whole attempt again, from the state that offered it. */
    fun retry() {
        val refused = _state.value as? PassState.Refused ?: return
        if (!refused.reason.retryable) return
        _state.value = PassState.Closed
        pay()
    }

    /** Approve: the wallet signs the server's transfer and submits it, in one round-trip. */
    fun confirm() {
        val ready = _state.value as? PassState.Ready ?: return
        payJob?.cancel()
        payJob = viewModelScope.launch {
            if (ready.build.isExpiredAt(clock.nowMillis())) {
                debugLog.raw("pass/build expired at ${ready.build.expiresAt}; asking again before anything is signed")
                build(ready.payer, refreshed = true)
            } else {
                send(ready)
            }
        }
    }

    /** Dismiss from any state. A round-trip in flight is dropped with it. */
    fun close() {
        payJob?.cancel()
        payJob = null
        _state.value = PassState.Closed
    }

    private suspend fun attempt(mint: String, known: String?) {
        val payer = known ?: when (val outcome = wallet.connect()) {
            is WalletOutcome.Success -> outcome.value.address
            is WalletOutcome.NoWallet -> return refuse(PassRefusal.NO_WALLET)
            is WalletOutcome.Cancelled -> return refuse(PassRefusal.NOT_CONNECTED)
            is WalletOutcome.Error -> {
                debugLog.raw("connect: ${outcome.message}")
                return refuse(PassRefusal.NOT_CONNECTED)
            }
        }
        build(payer, mint)
    }

    private suspend fun build(payer: String, mint: String = PassApi.MINT_USDC, refreshed: Boolean = false) {
        _state.value = PassState.Building(payer)
        val build = try {
            passApi.build(payer, mint, devicePassStore.codeHash())
        } catch (e: CancellationException) {
            throw e
        } catch (e: PassError) {
            debugLog.raw("pass/build refused: status=${e.status} code=${e.code} ${e.detail ?: e.message}")
            val reason = when (e) {
                is PassError.Disabled -> PassRefusal.NOT_OPEN
                is PassError.RateLimited -> PassRefusal.RATE_LIMITED
                else -> PassRefusal.UNAVAILABLE
            }
            return refuse(reason)
        } catch (e: Exception) {
            debugLog.raw("pass/build threw ${e::class.simpleName}: ${e.message}")
            return refuse(PassRefusal.UNAVAILABLE)
        }
        // The sheet's figures are the server's JSON; what the wallet would sign is the bytes. They
        // are read against each other, and against this app's own pinned treasury, before the
        // confirm step is ever shown, so a transaction that does anything but the one transfer on
        // the screen is never offered for approval at all (security audit, finding 2).
        val unsigned = build.transactionBytes()
        val verdict = if (unsigned == null) {
            TransactionGuard.Verdict.Refuse("no transaction bytes")
        } else {
            TransactionGuard.checkPass(unsigned, payer, build.summary, devicePassStore.codeHash())
        }
        if (verdict is TransactionGuard.Verdict.Refuse) {
            debugLog.raw("pass/build transaction refused before the wallet: ${verdict.reason}")
            // The server did build one; this phone would not hand it on, and says why.
            _state.value = PassState.Refused(PassRefusal.GUARD_REFUSED, verdict.why)
            return
        }
        _state.value = PassState.Ready(payer, build, refreshed = refreshed)
    }

    private suspend fun send(ready: PassState.Ready) {
        val unsigned = ready.build.transactionBytes()
        if (unsigned == null) {
            debugLog.raw("pass/build carried no transaction this app could read")
            return refuse(PassRefusal.UNAVAILABLE)
        }

        _state.value = PassState.Signing(ready.payer, ready.build)
        val outcome = wallet.call { it.signAndSendTransactions(arrayOf(unsigned)) }
        val signature = when (outcome) {
            is WalletOutcome.Success -> outcome.value.signatures.firstOrNull()
            is WalletOutcome.NoWallet -> return refuse(PassRefusal.NO_WALLET)
            is WalletOutcome.Cancelled -> return refuse(PassRefusal.NOT_APPROVED)
            is WalletOutcome.Error -> {
                debugLog.raw("wallet: ${outcome.message}")
                return refuse(PassRefusal.FAILED)
            }
        }
        if (signature == null || signature.isEmpty()) {
            debugLog.raw("signAndSendTransactions answered with no signature")
            return refuse(PassRefusal.FAILED)
        }

        val signatureText = Base58.encodeToString(signature)
        // Written before the confirm call is even made: signAndSendTransactions both signs and
        // submits, so the payment is on the chain from this instant, and a process death on the
        // next line must not lose the only record of it (task A6 review). The same rule and the
        // same shape VoteViewModel already keeps for its own receipt, viewModelScope runs on
        // Main, and a file write does not.
        val receipt = PassReceipt(signature = signatureText, payer = ready.payer, landedAtMillis = clock.nowMillis())
        withContext(ioDispatcher) { passReceiptStore.record(receipt) }
        _pro.update { it.copy(pendingSignature = signatureText) }

        _state.value = PassState.Confirming(ready.payer, signatureText)
        val entitlement = confirmLanded(receipt)
        _state.value = PassState.Landed(signatureText, entitlement)
    }

    /**
     * Verifies one landed receipt with the server, on demand rather than waiting for the cron.
     * Called from [send] the instant a signature comes back, and from [init] for a receipt this
     * device saw land in an earlier process. The transfer is on the chain the moment the wallet
     * answers; this call is only this app's own shortcut past the cron's up-to-ten-minute window,
     * so a refusal here is logged and never turned into a claim that the payment did not land, and
     * [pendingReceipt] simply stays non-null for the next attempt (another confirm, or the cron)
     * to clear.
     */
    private suspend fun confirmLanded(receipt: PassReceipt): EntitlementResponse? {
        val entitlement = try {
            passApi.confirm(receipt.signature)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog.raw("pass/confirm did not resolve: ${e::class.simpleName}: ${e.message}")
            null
        }
        if (entitlement != null) {
            withContext(ioDispatcher) { passReceiptStore.markConfirmed(receipt.signature) }
            applyEntitlement(entitlement)
            _pro.update { it.copy(pendingSignature = pendingReceipt()?.signature) }
        }
        return entitlement
    }

    private fun refuse(reason: PassRefusal) {
        _state.value = PassState.Refused(reason)
    }
}
