package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult

/**
 * Where the MWA auth token lives between round trips. On the phone it is
 * [MobileWalletAdapter.authToken] itself, the field clientlib-ktx reads to choose `reauthorize`
 * (or MWA 2.0's `authorize` with an `auth_token`) over a fresh `authorize`, and writes back after
 * every successful one. Restoring a saved session is writing this field before the first call.
 */
interface AuthTokenSlot {
    var authToken: String?
}

/** One wallet association: what [MwaWalletSession] needs of [MobileWalletAdapter], and no more. */
interface MwaTransport {
    /** Authorizes (or reauthorizes with the slot's token), then runs [block] in the same session. */
    suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T>

    /** Deauthorizes the slot's token with the wallet, when there is one. */
    suspend fun disconnect(): TransactionResult<Unit>
}

/** The adapter's own token field, as an [AuthTokenSlot]. */
fun MobileWalletAdapter.tokenSlot(): AuthTokenSlot = object : AuthTokenSlot {
    override var authToken: String?
        get() = this@tokenSlot.authToken
        set(value) {
            this@tokenSlot.authToken = value
        }
}

/** The real [MwaTransport]: the app-scoped [adapter] driven through one Activity's [sender]. */
class AdapterTransport(
    private val adapter: MobileWalletAdapter,
    private val sender: ActivityResultSender,
) : MwaTransport {
    override suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T> =
        adapter.transact(sender) { block(this) }

    override suspend fun disconnect(): TransactionResult<Unit> = adapter.disconnect(sender)
}
