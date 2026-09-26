package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app-scoped [WalletSession] ViewModels depend on.
 *
 * An [ActivityResultSender] must be created by the Activity before it starts, and dies
 * with it, while ViewModels outlive Activities. The Activity therefore [bind]s a
 * [MwaWalletSession] in onCreate and [unbind]s it in onDestroy; calls are forwarded to
 * whichever session is bound. The authorized [account] and the auth token live here, so they
 * survive Activity recreation, and in [store], so they survive process death: [restore], run
 * once at launch, puts the saved account back on [account] for display and reads, and the saved
 * token back where the next wallet request reauthorizes with it.
 */
class WalletSessionHolder(
    private val tokens: AuthTokenSlot,
    private val store: WalletSessionStore = WalletSessionStore.NONE,
    private val transportFor: (ActivityResultSender) -> MwaTransport,
) : WalletSession {

    constructor(adapter: MobileWalletAdapter, store: WalletSessionStore = WalletSessionStore.NONE) :
        this(adapter.tokenSlot(), store, { sender -> AdapterTransport(adapter, sender) })

    private val accounts = MutableStateFlow<WalletAccount?>(null)
    override val account: StateFlow<WalletAccount?> = accounts.asStateFlow()

    /** One round trip, or one restore, at a time, across every session this holder binds. */
    private val mutex = Mutex()

    @Volatile
    private var current: WalletSession? = null

    fun bind(sender: ActivityResultSender): MwaWalletSession = attach(transportFor(sender))

    /** [bind] without a sender, for a transport that needs none. */
    internal fun attach(transport: MwaTransport): MwaWalletSession =
        MwaWalletSession(transport, tokens, accounts, store, mutex).also { current = it }

    fun unbind(session: WalletSession) {
        if (current === session) current = null
    }

    /**
     * Puts the saved session back, if there is one and nothing newer is already here: a connect
     * that raced ahead of this read wins. No wallet is opened; the first request that needs one
     * reauthorizes with the restored token, and falls back to a fresh authorize if the wallet no
     * longer honours it ([MwaWalletSession]).
     */
    suspend fun restore() = mutex.withLock {
        if (tokens.authToken != null || accounts.value != null) return@withLock
        val saved = try {
            store.load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return@withLock
        tokens.authToken = saved.authToken
        accounts.value = saved.account
    }

    override suspend fun <T> call(block: suspend (AdapterOperations) -> T): WalletOutcome<T> =
        current?.call(block) ?: noActivity()

    override suspend fun connect(): WalletOutcome<WalletAccount> = current?.connect() ?: noActivity()

    override suspend fun disconnect(): WalletOutcome<Unit> = current?.disconnect() ?: noActivity()

    private fun noActivity() = WalletOutcome.Error("No screen is open to hand off to the wallet")
}
