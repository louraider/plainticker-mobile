package com.plainticker.mobile.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutionException

/**
 * The real [WalletSession]: [MobileWalletAdapter] driven through one Activity's
 * [ActivityResultSender].
 *
 * The adapter is app-scoped (it keeps the auth token in memory, so a reconnect after an
 * Activity recreation is a reauthorize, not a new consent; process death forgets it) while
 * the sender is bound to the Activity that created it, so the session has the Activity's
 * lifetime and [WalletSessionHolder] hands ViewModels whichever one is current. Calls are
 * serialized: the sender refuses a second request while one is pending.
 *
 * A wallet answering ERROR_AUTHORIZATION_FAILED has rejected the token (declined, revoked,
 * or stale on a legacy-protocol wallet, where clientlib 2.2.0 does not fall back to a fresh
 * authorize). The token and the account are dropped so the next attempt starts clean
 * instead of failing the same way until process death.
 */
class MwaWalletSession(
    private val activityResultSender: ActivityResultSender,
    private val adapter: MobileWalletAdapter = defaultAdapter(),
    private val accounts: MutableStateFlow<WalletAccount?> = MutableStateFlow(null),
) : WalletSession {

    override val account: StateFlow<WalletAccount?> = accounts.asStateFlow()

    private val mutex = Mutex()

    override suspend fun <T> call(block: suspend (AdapterOperations) -> T): WalletOutcome<T> = mutex.withLock {
        val result = adapter.transact(activityResultSender) { block(this) }
        remember(result)
        forgetIfUnauthorized(result)
        result.toWalletOutcome()
    }

    override suspend fun connect(): WalletOutcome<WalletAccount> = mutex.withLock {
        val result = adapter.connect(activityResultSender)
        remember(result)
        forgetIfUnauthorized(result)
        when (val outcome = result.toWalletOutcome()) {
            is WalletOutcome.Success ->
                accounts.value?.let { WalletOutcome.Success(it) }
                    ?: WalletOutcome.Error("The wallet authorized no account")
            is WalletOutcome.NoWallet -> outcome
            is WalletOutcome.Cancelled -> outcome
            is WalletOutcome.Error -> outcome
        }
    }

    override suspend fun disconnect(): WalletOutcome<Unit> = mutex.withLock {
        val outcome = adapter.disconnect(activityResultSender).toWalletOutcome()
        if (outcome is WalletOutcome.Success) accounts.value = null
        outcome
    }

    /** Every successful round-trip carries the authorization; the first account is the app's. */
    private fun remember(result: TransactionResult<*>) {
        if (result !is TransactionResult.Success) return
        val auth = runCatching { result.authResult }.getOrNull() ?: return
        val first = auth.accounts.firstOrNull() ?: return
        accounts.value = WalletAccount(first.publicKey, first.accountLabel)
    }

    private fun forgetIfUnauthorized(result: TransactionResult<*>) {
        if (result !is TransactionResult.Failure) return
        val cause = (result.e as? ExecutionException)?.cause ?: result.e
        if (cause is JsonRpc20Client.JsonRpc20RemoteException && cause.code == ProtocolContract.ERROR_AUTHORIZATION_FAILED) {
            adapter.authToken = null
            accounts.value = null
        }
    }

    companion object {
        const val IDENTITY_URI = "https://www.plainticker.com"
        const val IDENTITY_ICON = "favicon.ico"
        const val IDENTITY_NAME = "PlainTicker"

        /**
         * The identity wallets verify through `/.well-known/assetlinks.json` on the identity
         * host (T3). Mainnet: xStocks and Jupiter have no devnet.
         */
        fun defaultAdapter(): MobileWalletAdapter = MobileWalletAdapter(
            connectionIdentity = ConnectionIdentity(
                identityUri = Uri.parse(IDENTITY_URI),
                iconUri = Uri.parse(IDENTITY_ICON),
                identityName = IDENTITY_NAME,
            ),
        ).apply { blockchain = Solana.Mainnet }
    }
}
