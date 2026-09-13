package com.plainticker.mobile

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funkatronics.encoders.Base58
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.jupiter.JupiterSwapApi
import com.plainticker.mobile.data.jupiter.SwapError
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.wallet.MwaWalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val address: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val capabilities: String? = null,
    val swapReport: String? = null,
)

class MainViewModel : ViewModel() {

    /**
     * The app has one wallet identity and this is it: [MwaWalletSession.defaultAdapter] is the
     * single place the identity is built, and the shipping screens reach the same builder
     * through [AppContainer.walletAdapter]. This is a second adapter instance, not the shared
     * one, so the spike carries its own authorization token; what it cannot carry is a
     * different identity. What a person reads in the Seed Vault prompt is
     * [MwaWalletSession.IDENTITY_NAME] over [MwaWalletSession.IDENTITY_URI] either way, so the
     * spike cannot drift from the product, and the identity moves in one place when it moves.
     *
     * Mainnet, as the adapter sets: xStocks and Jupiter have no devnet equivalent, so the
     * capability probe has to run against the cluster the product will actually use.
     */
    private val walletAdapter = MwaWalletSession.defaultAdapter()

    private val httpClient = HttpClientFactory.create()
    private val swapApi = JupiterSwapApi(httpClient)

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        httpClient.close()
    }

    /**
     * Asks the connected wallet what it can do. Signs nothing, spends nothing.
     *
     * This answers the one question the product rests on. The Jupiter gasless swap needs
     * the SIGN-ONLY path, because Jupiter co-signs as fee payer AFTER us. That path is
     * sign_transactions, which MWA 2.0 deprecated (clientlib 2.2.0 marks it
     * forRemoval = true) and made optional: only signMessages and
     * signAndSendTransactions are mandatory. signAndSendTransactions cannot substitute,
     * since the wallet would broadcast a transaction still missing the fee payer
     * signature.
     *
     * ProtocolContract carries a feature id for it, so capabilities settle the question
     * per wallet without attempting a signature.
     */
    fun probeCapabilities(sender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, capabilities = null) }
            when (val result = walletAdapter.transact(sender) { getCapabilities() }) {
                is TransactionResult.Success -> {
                    val caps = result.payload
                    val features = caps.supportedOptionalFeatures.toSet()
                    val versions = caps.supportedTransactionVersions.joinToString(", ") { it.toString() }
                    val report = buildString {
                        appendLine("cluster: " + Solana.Mainnet.fullName)
                        appendLine("tx versions: " + versions)
                        appendLine("max tx/request: " + caps.maxTransactionsPerSigningRequest)
                        appendLine("max msg/request: " + caps.maxMessagesPerSigningRequest)
                        appendLine()
                        // getCapabilities reports OPTIONAL features only. For these,
                        // absence is a real answer.
                        appendLine("optional (absent = unsupported):")
                        appendLine("  " + mark(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS in features) + " signTransactions  <- gasless swap needs this")
                        appendLine("  " + mark(ProtocolContract.FEATURE_ID_SIGN_IN_WITH_SOLANA in features) + " signInWithSolana  (else sign our own nonce)")
                        appendLine("  " + mark(ProtocolContract.FEATURE_ID_CLONE_AUTHORIZATION in features) + " cloneAuthorization")
                        appendLine()
                        // signMessages and signAndSendTransactions are MANDATORY in MWA
                        // 2.0, so a compliant wallet never lists them here. Seed Vault
                        // Wallet reports neither yet signs messages fine, which is how
                        // this distinction was found. Do not read absence as missing.
                        appendLine("mandatory in MWA 2.0, never listed above:")
                        appendLine("  signMessages, signAndSendTransactions")
                        appendLine()
                        append("raw features: ")
                        append(if (features.isEmpty()) "(none reported)" else features.sorted().joinToString(", "))
                    }
                    _uiState.update {
                        it.copy(
                            address = SolanaPublicKey(result.authResult.accounts.first().publicKey).base58(),
                            isLoading = false,
                            capabilities = report,
                        )
                    }
                }
                is TransactionResult.NoWalletFound -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
                is TransactionResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, message = result.message + " (" + result.e::class.simpleName + ")")
                }
            }
        }
    }

    private fun mark(supported: Boolean) = if (supported) "[yes]" else "[NO ]"

    /**
     * The spike that decides whether this product exists: quote, sign, land.
     *
     * BuildConfig.SUBMIT_SWAPS guards the one call that moves money. It is false in every
     * debug build, so the flow stops after signing, which costs nothing and expires
     * harmlessly; only a release build submits through /execute.
     *
     * Timings are reported because the open question is not whether the calls work, it is
     * whether the wallet round-trip fits inside an RFQ quote's usable window. The maker
     * reserves part of the quote lifetime for its own last-look verification, so the
     * user-facing budget is shorter than expireAt implies. That is also why /order is
     * called here, at the tap, rather than when a preview is rendered.
     */
    @Suppress("DEPRECATION")
    fun swapForXStock(
        sender: ActivityResultSender,
        symbol: String,
        outputMint: String,
        usdcAmount: Long = 5_000_000L, // 5 USDC, 6 decimals
    ) {
        viewModelScope.launch {
            val taker = _uiState.value.address
            if (taker == null) {
                _uiState.update { it.copy(message = "Connect the wallet first") }
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, message = null, swapReport = null) }

            val log = StringBuilder()
            try {
                val t0 = System.currentTimeMillis()
                val order = swapApi.order(KnownMints.USDC, outputMint, usdcAmount, taker)
                val tOrder = System.currentTimeMillis() - t0
                val nowSec = System.currentTimeMillis() / 1000

                log.appendLine("USDC -> $symbol")
                log.appendLine("in  ${Fmt.tokenAmount(order.inAmountRaw, decimals = 6)} USDC  (${Fmt.price(order.inUsdValue)})")
                log.appendLine("out ${Fmt.count(order.outAmountRaw)}  raw  (${Fmt.price(order.outUsdValue)})")
                log.appendLine("all-in cost ${Fmt.percent(order.allInCostPct, signed = false)}")
                log.appendLine("router=${order.router} type=${order.swapType} gasless=${order.gasless}")
                log.appendLine("feeBps=${order.feeBps} platformBps=${order.platformFeeBps} slipBps=${order.slippageBps}")
                log.appendLine(
                    order.secondsLeft(nowSec)
                        ?.let { "quote window: ${it}s left (RFQ, maker last-look applies)" }
                        ?: "quote window: none (Metis route, bounded by blockhash + slippage)"
                )
                log.appendLine("GET /order: ${tOrder}ms")

                val unsigned = order.transaction?.takeIf { order.isSignable }
                if (unsigned == null) {
                    log.appendLine("order carried no transaction, nothing to sign")
                } else {
                    val t1 = System.currentTimeMillis()
                    val signResult = walletAdapter.transact(sender) {
                        signTransactions(arrayOf(Base64.decode(unsigned, Base64.DEFAULT)))
                    }
                    val tSign = System.currentTimeMillis() - t1
                    log.appendLine("wallet round-trip: ${tSign}ms")

                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signed = signResult.payload.signedPayloads.first()
                            val leftAfterSign = order.secondsLeft(System.currentTimeMillis() / 1000)
                            log.appendLine(
                                "signed ${signed.size} bytes" +
                                    (leftAfterSign?.let { ", ${it}s left on quote" } ?: "")
                            )

                            if (!BuildConfig.SUBMIT_SWAPS) {
                                log.appendLine()
                                log.appendLine("STOPPED: SUBMIT_SWAPS=false (debug build), nothing submitted.")
                                log.appendLine("No money moved. Only a release build lands it.")
                            } else {
                                val t2 = System.currentTimeMillis()
                                val exec = swapApi.execute(
                                    Base64.encodeToString(signed, Base64.NO_WRAP),
                                    order.requestId,
                                )
                                log.appendLine("POST /execute: ${System.currentTimeMillis() - t2}ms")
                                log.appendLine("status=${exec.status} code=${exec.code ?: "-"}")
                                exec.signature?.let { log.appendLine("signature: $it") }
                                exec.error?.let { log.appendLine("error: $it") }
                                exec.errorOrNull()?.takeIf { it.needsFreshOrder }?.let {
                                    log.appendLine("quote gone: needs a fresh /order and a second approval")
                                }
                                log.appendLine("total: ${System.currentTimeMillis() - t0}ms")
                            }
                        }
                        is TransactionResult.NoWalletFound ->
                            log.appendLine("no wallet: ${signResult.message}")
                        is TransactionResult.Failure ->
                            log.appendLine("sign failed: ${signResult.message} (${signResult.e::class.simpleName})")
                    }
                }
            } catch (e: SwapError) {
                log.appendLine("${e.stage.name.lowercase()} refused: ${e.message}")
            } catch (e: Exception) {
                log.appendLine("threw: ${e::class.simpleName}: ${e.message}")
            }
            _uiState.update { it.copy(isLoading = false, swapReport = log.toString()) }
        }
    }

    companion object {
        const val TSLAX_MINT = KnownMints.TSLAX
    }

    fun connect(sender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            when (val result = walletAdapter.connect(sender)) {
                is TransactionResult.Success -> _uiState.update {
                    it.copy(
                        address = SolanaPublicKey(result.authResult.accounts.first().publicKey).base58(),
                        isLoading = false,
                    )
                }
                is TransactionResult.NoWalletFound -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
                is TransactionResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
            }
        }
    }

    fun signMessage(sender: ActivityResultSender, message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            val result = walletAdapter.transact(sender) { authResult ->
                signMessagesDetached(
                    arrayOf(message.encodeToByteArray()),
                    arrayOf(authResult.accounts.first().publicKey),
                ).messages.first().signatures.first()
            }
            when (result) {
                is TransactionResult.Success -> _uiState.update {
                    it.copy(
                        address = SolanaPublicKey(result.authResult.accounts.first().publicKey).base58(),
                        isLoading = false,
                        message = "Signature: " + Base58.encodeToString(result.payload),
                    )
                }
                is TransactionResult.NoWalletFound -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
                is TransactionResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
            }
        }
    }

    fun disconnect(sender: ActivityResultSender) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            when (val result = walletAdapter.disconnect(sender)) {
                is TransactionResult.Success -> _uiState.value = WalletUiState()
                is TransactionResult.NoWalletFound -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
                is TransactionResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, message = result.message)
                }
            }
        }
    }
}
