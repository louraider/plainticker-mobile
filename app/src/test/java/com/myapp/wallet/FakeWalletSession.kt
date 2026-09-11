package com.myapp.wallet

import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [WalletSession] that answers from a scripted queue instead of opening a wallet.
 *
 * Each [call], [connect] or [disconnect] consumes the next queued outcome. A queued
 * `Success` must carry the type the caller expects (a [WalletAccount] for connect, the
 * block's result for call); running out of script is a test bug and throws.
 */
class FakeWalletSession : WalletSession {

    private val accounts = MutableStateFlow<WalletAccount?>(null)
    override val account: StateFlow<WalletAccount?> = accounts.asStateFlow()

    private val script = ArrayDeque<WalletOutcome<Any?>>()

    var callCount = 0
        private set
    var connectCount = 0
        private set
    var disconnectCount = 0
        private set

    fun enqueue(vararg outcomes: WalletOutcome<Any?>) {
        script.addAll(outcomes)
    }

    /** Pretend a previous session already authorized [account]. */
    fun connectedAs(account: WalletAccount?) {
        accounts.value = account
    }

    val remaining: Int get() = script.size

    override suspend fun <T> call(block: suspend (AdapterOperations) -> T): WalletOutcome<T> {
        callCount++
        return next()
    }

    override suspend fun connect(): WalletOutcome<WalletAccount> {
        connectCount++
        val outcome = next<WalletAccount>()
        if (outcome is WalletOutcome.Success) accounts.value = outcome.value
        return outcome
    }

    override suspend fun disconnect(): WalletOutcome<Unit> {
        disconnectCount++
        val outcome = next<Unit>()
        if (outcome is WalletOutcome.Success) accounts.value = null
        return outcome
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> next(): WalletOutcome<T> =
        (script.removeFirstOrNull() ?: error("FakeWalletSession: no scripted outcome left")) as WalletOutcome<T>
}

/** A deterministic 32-byte account for tests. */
fun testAccount(fill: Int = 7, label: String? = "Seeker"): WalletAccount =
    WalletAccount(ByteArray(32) { fill.toByte() }, label)
