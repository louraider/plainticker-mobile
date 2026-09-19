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
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.repo.RpcRepository
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * [PassState.Landed.entitlement] null when it did not.
 */
class PassViewModel(
    private val passApi: PassApi,
    private val entitlementApi: EntitlementApi,
    private val wallet: WalletSession,
    private val rpc: RpcRepository,
    private val devicePassStore: DevicePassStore,
    private val clock: Clock = WallClock,
    private val debugLog: PassDebugLog = PassDebugLog.ANDROID,
) : ViewModel() {

    private val _pro = MutableStateFlow(ProUiState())
    val pro: StateFlow<ProUiState> = _pro.asStateFlow()

    private val _state = MutableStateFlow<PassState>(PassState.Closed)
    val state: StateFlow<PassState> = _state.asStateFlow()

    private var payJob: Job? = null

    init {
        refreshEntitlement()
        viewModelScope.launch {
            wallet.account.collect { account ->
                if (account == null) {
                    _pro.update { it.copy(walletConnected = false, stakeRaw = null, stakeUnread = false) }
                } else {
                    _pro.update { it.copy(walletConnected = true) }
                    loadStake(account.address)
                }
            }
        }
    }

    /** Reads this device's code and asks the server what it carries. Never costs a wallet call. */
    fun refreshEntitlement() {
        viewModelScope.launch {
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

    /** The tap: connect if there is no session, then ask the server to build the transfer. */
    fun pay(mint: String = PassApi.MINT_USDC) {
        if (_state.value.isBusy) return
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
        _state.value = PassState.Confirming(ready.payer, signatureText)
        // The transfer is on the chain the moment the wallet answers; this call is only this
        // app's own shortcut past the cron's up-to-ten-minute window, so a refusal here is logged
        // and never turned into a state that claims the payment did not land.
        val entitlement = try {
            passApi.confirm(signatureText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog.raw("pass/confirm did not resolve: ${e::class.simpleName}: ${e.message}")
            null
        }
        entitlement?.let(::applyEntitlement)
        _state.value = PassState.Landed(signatureText, entitlement)
    }

    private fun refuse(reason: PassRefusal) {
        _state.value = PassState.Refused(reason)
    }
}
