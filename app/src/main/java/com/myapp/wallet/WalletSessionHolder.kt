package com.myapp.wallet

import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app-scoped [WalletSession] ViewModels depend on.
 *
 * An [ActivityResultSender] must be created by the Activity before it starts, and dies
 * with it, while ViewModels outlive Activities. The Activity therefore [bind]s a
 * [MwaWalletSession] in onCreate and [unbind]s it in onDestroy; calls are forwarded to
 * whichever session is bound. The authorized [account] and the adapter's auth token live
 * here, so they survive Activity recreation.
 */
class WalletSessionHolder(
    val adapter: MobileWalletAdapter,
) : WalletSession {

    private val accounts = MutableStateFlow<WalletAccount?>(null)
    override val account: StateFlow<WalletAccount?> = accounts.asStateFlow()

    @Volatile
    private var current: WalletSession? = null

    fun bind(sender: ActivityResultSender): MwaWalletSession =
        MwaWalletSession(sender, adapter, accounts).also { current = it }

    fun unbind(session: WalletSession) {
        if (current === session) current = null
    }

    override suspend fun <T> call(block: suspend (AdapterOperations) -> T): WalletOutcome<T> =
        current?.call(block) ?: noActivity()

    override suspend fun connect(): WalletOutcome<WalletAccount> = current?.connect() ?: noActivity()

    override suspend fun disconnect(): WalletOutcome<Unit> = current?.disconnect() ?: noActivity()

    private fun noActivity() = WalletOutcome.Error("No screen is open to hand off to the wallet")
}
