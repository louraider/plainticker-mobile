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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * A request the person walks away from ends (QA of 1.3.19: back from the wallet's Connect sheet
 * left "Reading the wallet" counting past 87 s). clientlib-ktx 2.2.0 means to end it on
 * RESULT_CANCELED, but launches that into a scope that has already finished, so the request
 * waits out the adapter's own timeouts (10 s with no association, 90 s once the wallet has
 * associated and been asked to authorize). [inFront] is the Activity's resumed state: once it
 * has left the front for the wallet and come back, and [dismissGraceMillis] later the wallet has
 * still not been asked anything past the authorization, the request is over and reads as
 * [WalletOutcome.Cancelled]. A request whose block has started (a signature asked for) is never
 * given up on this way: the wallet may be sending it, so only the wallet's own answer or the
 * adapter's timeout ends it. The abandoned round trip finishes in the background and its result
 * is dropped, so it can neither save nor clear a session a newer request made.
 */
class MwaWalletSession(
    private val transport: MwaTransport,
    private val tokens: AuthTokenSlot,
    private val accounts: MutableStateFlow<WalletAccount?> = MutableStateFlow(null),
    private val store: WalletSessionStore = WalletSessionStore.NONE,
    private val mutex: Mutex = Mutex(),
    private val inFront: StateFlow<Boolean> = MutableStateFlow(true),
    private val dismissGraceMillis: Long = DISMISS_GRACE_MS,
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
        val outcome = untilDismissed(asked = { false }) { transport.disconnect() }.toWalletOutcome()
        tokens.authToken = null
        accounts.value = null
        clearStore()
        outcome
    }

    /** One request, with the single fallback from a rejected saved token to a fresh authorize. */
    private suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> {
        val hadToken = tokens.authToken != null
        val asked = AtomicBoolean(false)
        val result = untilDismissed(asked = { asked.get() }) {
            val first = transport.transact { ops ->
                asked.set(true)
                block(ops)
            }
            if (hadToken && !asked.get() && authorizationRejected(first)) {
                tokens.authToken = null
                transport.transact { ops ->
                    asked.set(true)
                    block(ops)
                }
            } else {
                first
            }
        }
        if (result.isDismissal()) return result
        remember(result)
        forgetIfUnauthorized(result)
        return result
    }

    /**
     * Runs [request] outside the caller's job, and answers with it, or with a dismissal once the
     * person is back in the app and the wallet has not been [asked] anything. Outside the caller's
     * job because the adapter blocks a thread on its futures, which no cancellation interrupts: a
     * child would keep the caller waiting until the adapter's own timeout.
     */
    private suspend fun <T> untilDismissed(
        asked: () -> Boolean,
        request: suspend () -> TransactionResult<T>,
    ): TransactionResult<T> = coroutineScope {
        val detached = CoroutineScope(coroutineContext.minusKey(Job) + SupervisorJob())
        val pending = detached.async { request() }
        val dismissed = async {
            inFront.first { !it }
            inFront.first { it }
            delay(dismissGraceMillis)
            if (asked()) awaitCancellation()
            TransactionResult.Failure<T>("The person came back without choosing", WalletDismissedException())
        }
        try {
            select {
                pending.onAwait { it }
                dismissed.onAwait { it }
            }
        } finally {
            dismissed.cancel()
            if (!pending.isCompleted) pending.cancel()
        }
    }

    private fun TransactionResult<*>.isDismissal(): Boolean =
        this is TransactionResult.Failure && e is WalletDismissedException

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
         * From the app coming back to the front to a request read as dismissed. A wallet that
         * approved has answered before it closes (the app closes the association and the wallet
         * finishes on that), so this only has to cover the adapter's own close.
         */
        const val DISMISS_GRACE_MS = 3_000L

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
