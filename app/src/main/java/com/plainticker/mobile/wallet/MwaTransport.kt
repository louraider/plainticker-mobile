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

    /**
     * Everything the adapter keeps between round trips, as it is now: the token, and on the
     * phone also the wallet's https base URI that the next association is sent to. A request the
     * person walked away from puts this back when its round trip finally ends, so the adapter
     * carries nothing from an association the app already reported as Cancelled.
     */
    fun snapshot(): AdapterSlotSnapshot = AdapterSlotSnapshot(authToken)

    /** Puts back what [snapshot] read. */
    fun restore(snapshot: AdapterSlotSnapshot) {
        authToken = snapshot.authToken
    }
}

/** What [AuthTokenSlot.snapshot] read. [walletUriBase] is opaque: only the adapter reads it. */
data class AdapterSlotSnapshot(val authToken: String?, val walletUriBase: Any? = null)

/** One wallet association: what [MwaWalletSession] needs of [MobileWalletAdapter], and no more. */
interface MwaTransport {
    /** Authorizes (or reauthorizes with the slot's token), then runs [block] in the same session. */
    suspend fun <T> transact(block: suspend (AdapterOperations) -> T): TransactionResult<T>

    /** Deauthorizes the slot's token with the wallet, when there is one. */
    suspend fun disconnect(): TransactionResult<Unit>
}

/**
 * The adapter's own token field, as an [AuthTokenSlot]. clientlib-ktx 2.2.0 keeps the wallet's
 * base URI in a private field (`walletUriBase`) that it writes next to `authToken` after every
 * authorization; the snapshot reads and restores it by reflection (kept by name in
 * proguard-rules.pro), and a build where that field is missing simply snapshots the token alone.
 */
fun MobileWalletAdapter.tokenSlot(): AuthTokenSlot = object : AuthTokenSlot {
    override var authToken: String?
        get() = this@tokenSlot.authToken
        set(value) {
            this@tokenSlot.authToken = value
        }

    override fun snapshot(): AdapterSlotSnapshot =
        AdapterSlotSnapshot(authToken, runCatching { walletUriBaseField?.get(this@tokenSlot) }.getOrNull())

    override fun restore(snapshot: AdapterSlotSnapshot) {
        authToken = snapshot.authToken
        runCatching { walletUriBaseField?.set(this@tokenSlot, snapshot.walletUriBase) }
    }
}

private val walletUriBaseField: java.lang.reflect.Field? by lazy {
    runCatching {
        MobileWalletAdapter::class.java.getDeclaredField("walletUriBase").apply { isAccessible = true }
    }.getOrNull()
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
