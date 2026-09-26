package com.plainticker.mobile.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutionException

/**
 * The real [WalletSession]: Mobile Wallet Adapter through one Activity's [transport].
 *
 * The auth token lives in [tokens] (the app-scoped adapter's own field) and the account in
 * [accounts], both shared through [WalletSessionHolder], so a reconnect after an Activity
 * recreation is a reauthorize, not a new consent. Since 2026-09-26 both are also saved to
 * [store] after every successful round trip and restored at launch
 * ([WalletSessionHolder.restore]), so a process death no longer forgets the wallet (Beeman, in
 * the judges' review: every launch said "Connect a wallet"). What is saved is the token the
 * wallet issued and the account's public key and label; never a key, and nothing that can sign.
 *
 * This is the Solana Mobile templates' pattern for clientlib-ktx: keep `authToken`, and let the
 * next `transact` reauthorize with it. When the wallet rejects that token
 * (ERROR_AUTHORIZATION_FAILED: revoked in the wallet, expired, or issued before a reinstall),
 * the token is dropped and the same request is made once more as a fresh `authorize`, so the
 * person sees the wallet's connect prompt instead of a failure; the request only repeats when the
 * wallet refused the authorization itself, before anything was asked of it, so nothing is ever
 * asked to be signed twice. A fresh authorize the person declines drops the account and the saved
 * session too. Calls are serialized by [mutex], shared with the holder's restore: the sender
 * refuses a second request while one is pending.
 */
class MwaWalletSession(
    private val transport: MwaTransport,
    private val tokens: AuthTokenSlot,
    private val accounts: MutableStateFlow<WalletAccount?> = MutableStateFlow(null),
    private val store: WalletSessionStore = WalletSessionStore.NONE,
    private val mutex: Mutex = Mutex(),
) : WalletSession {

    override val account: StateFlow<WalletAccount?> = accounts.asStateFlow()

    override suspend fun <T> call(block: suspend (AdapterOperations) -> T): WalletOutcome<T> = mutex.withLock {
        transact(block).toWalletOutcome()
    }

    override suspend fun connect(): WalletOutcome<WalletAccount> = mutex.withLock {
        when (val outcome = transact { }.toWalletOutcome()) {
            is WalletOutcome.Success ->
                accounts.value?.let { WalletOutcome.Success(it) }
                    ?: WalletOutcome.Error("The wallet authorized no account")
            is WalletOutcome.NoWallet -> outcome
            is WalletOutcome.Cancelled -> outcome
            is WalletOutcome.Error -> outcome
        }
    }

    /**
     * Deauthorizes with the wallet, and forgets the session on this device whatever the wallet
     * answered: a person who taps Disconnect wants the token gone from this phone, and a wallet
     * that has since been uninstalled, or does not answer, must not keep it here forever. The
     * wallet's own answer is still returned, so a screen can say the wallet was not reached.
     */
    override suspend fun disconnect(): WalletOutcome<Unit> = mutex.withLock {
        val outcome = transport.disconnect().toWalletOutcome()
        tokens.authToken = null
        accounts.value = null
        clearStore()
        outcome
    }

    /** One request, with the single fallback from a rejected saved token to a fresh authorize. */
    private suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> {
        val hadToken = tokens.authToken != null
        var asked = false
        val first = transport.transact { ops ->
            asked = true
            block(ops)
        }
        val result = if (hadToken && !asked && authorizationRejected(first)) {
            tokens.authToken = null
            transport.transact(block)
        } else {
            first
        }
        remember(result)
        forgetIfUnauthorized(result)
        return result
    }

    /** Every successful round-trip carries the authorization; the first account is the app's. */
    private suspend fun remember(result: TransactionResult<*>) {
        if (result !is TransactionResult.Success) return
        val auth = runCatching { result.authResult }.getOrNull() ?: return
        val first = auth.accounts.firstOrNull() ?: return
        val account = WalletAccount(first.publicKey, first.accountLabel)
        accounts.value = account
        val token = tokens.authToken ?: auth.authToken ?: return
        tokens.authToken = token
        try {
            store.save(SavedWalletSession(token, account))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A session that could not be written lasts until the process ends, as it always did.
        }
    }

    private suspend fun forgetIfUnauthorized(result: TransactionResult<*>) {
        if (!authorizationRejected(result)) return
        tokens.authToken = null
        accounts.value = null
        clearStore()
    }

    private suspend fun clearStore() {
        try {
            store.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing to do: the next restore that cannot read the file deletes it.
        }
    }

    private fun authorizationRejected(result: TransactionResult<*>): Boolean {
        if (result !is TransactionResult.Failure) return false
        val cause = (result.e as? ExecutionException)?.cause ?: result.e
        return cause is JsonRpc20Client.JsonRpc20RemoteException && cause.code == ProtocolContract.ERROR_AUTHORIZATION_FAILED
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
