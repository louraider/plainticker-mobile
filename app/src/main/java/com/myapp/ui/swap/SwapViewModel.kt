package com.myapp.ui.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.BuildConfig
import com.myapp.core.Clock
import com.myapp.data.KnownMints
import com.myapp.data.jupiter.JupiterSwapApi
import com.myapp.data.jupiter.SwapError
import com.myapp.data.jupiter.SwapOrder
import com.myapp.wallet.WalletOutcome
import com.myapp.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Base64

/** The honest phases of a swap, in the order the sheet shows them (plan section 13, Pass 3). */
enum class SwapPhase {
    IDLE,
    CONNECTING,
    QUOTING,
    SIGNING,
    EXECUTING,
    /** Debug builds stop here: signed, nothing submitted, no money moved. */
    SIGNED_NOT_SUBMITTED,
    LANDED,
    CANCELLED,
    FAILED,
}

data class SwapUiState(
    val phase: SwapPhase = SwapPhase.IDLE,
    val inputMint: String = KnownMints.USDC,
    val outputMint: String? = null,
    val outputSymbol: String? = null,
    val inputAmountRaw: Long = 0L,
    val order: SwapOrder? = null,
    val signature: String? = null,
    val message: String? = null,
    /** The quote expired or the maker declined: the same bytes can never be resubmitted. */
    val needsFreshOrder: Boolean = false,
    val phaseStartedAtMillis: Long? = null,
) {
    val isBusy: Boolean
        get() = phase == SwapPhase.CONNECTING || phase == SwapPhase.QUOTING ||
            phase == SwapPhase.SIGNING || phase == SwapPhase.EXECUTING
}

/**
 * Quote at the tap, sign in the wallet, land through Jupiter: the spike's flow with the
 * wallet behind [WalletSession]. [submitSwaps] is BuildConfig.SUBMIT_SWAPS, false in every
 * debug build, so the flow stops after signing there.
 */
class SwapViewModel(
    private val swapApi: JupiterSwapApi,
    private val wallet: WalletSession,
    private val clock: Clock,
    private val submitSwaps: Boolean = BuildConfig.SUBMIT_SWAPS,
) : ViewModel() {

    private val _state = MutableStateFlow(SwapUiState())
    val state: StateFlow<SwapUiState> = _state.asStateFlow()

    fun reset() {
        _state.value = SwapUiState()
    }

    @Suppress("DEPRECATION")
    fun start(outputMint: String, outputSymbol: String, usdcAmountRaw: Long) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = SwapUiState(
                outputMint = outputMint,
                outputSymbol = outputSymbol,
                inputAmountRaw = usdcAmountRaw,
            )

            val taker = wallet.account.value?.address ?: run {
                phase(SwapPhase.CONNECTING)
                when (val outcome = wallet.connect()) {
                    is WalletOutcome.Success -> outcome.value.address
                    is WalletOutcome.NoWallet -> return@launch fail("No compatible wallet found")
                    is WalletOutcome.Cancelled -> return@launch cancelled()
                    is WalletOutcome.Error -> return@launch fail(outcome.message)
                }
            }

            phase(SwapPhase.QUOTING)
            val order = try {
                swapApi.order(KnownMints.USDC, outputMint, usdcAmountRaw, taker)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SwapError) {
                return@launch fail(e.message ?: "Quote refused")
            } catch (e: Exception) {
                return@launch fail("Quote unavailable")
            }
            _state.update { it.copy(order = order) }
            val unsigned = order.transaction?.takeIf { order.isSignable }
                ?: return@launch fail("The quote carried no transaction to sign")

            phase(SwapPhase.SIGNING)
            val signed = when (val outcome = wallet.call { it.signTransactions(arrayOf(Base64.getDecoder().decode(unsigned))) }) {
                is WalletOutcome.Success -> outcome.value.signedPayloads.firstOrNull()
                    ?: return@launch fail("The wallet returned no signed transaction")
                is WalletOutcome.NoWallet -> return@launch fail("No compatible wallet found")
                is WalletOutcome.Cancelled -> return@launch cancelled()
                is WalletOutcome.Error -> return@launch fail(outcome.message)
            }

            if (!submitSwaps) {
                phase(SwapPhase.SIGNED_NOT_SUBMITTED, message = "Debug build: signed, not submitted")
                return@launch
            }

            phase(SwapPhase.EXECUTING)
            val result = try {
                swapApi.execute(Base64.getEncoder().encodeToString(signed), order.requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SwapError) {
                return@launch fail(e.message ?: "Execute refused", needsFreshOrder = e.needsFreshOrder)
            } catch (e: Exception) {
                return@launch fail("Execute unavailable")
            }
            val error = result.errorOrNull()
            if (error != null) {
                fail(error.message ?: "Swap failed", needsFreshOrder = error.needsFreshOrder)
            } else {
                _state.update {
                    it.copy(phase = SwapPhase.LANDED, signature = result.signature, phaseStartedAtMillis = clock.nowMillis())
                }
            }
        }
    }

    private fun phase(phase: SwapPhase, message: String? = null) {
        _state.update { it.copy(phase = phase, message = message, phaseStartedAtMillis = clock.nowMillis()) }
    }

    private fun cancelled() {
        _state.update { it.copy(phase = SwapPhase.CANCELLED, message = "Cancelled in wallet", phaseStartedAtMillis = clock.nowMillis()) }
    }

    private fun fail(message: String, needsFreshOrder: Boolean = false) {
        _state.update {
            it.copy(phase = SwapPhase.FAILED, message = message, needsFreshOrder = needsFreshOrder, phaseStartedAtMillis = clock.nowMillis())
        }
    }
}
