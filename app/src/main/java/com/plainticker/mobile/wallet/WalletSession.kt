package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.flow.StateFlow

/** The account the wallet authorized. [address] is the base58 form of [publicKey]. */
class WalletAccount(
    val publicKey: ByteArray,
    val label: String? = null,
) {
    val address: String by lazy { SolanaPublicKey(publicKey).base58() }

    override fun equals(other: Any?): Boolean =
        other is WalletAccount && publicKey.contentEquals(other.publicKey) && label == other.label

    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + (label?.hashCode() ?: 0)

    override fun toString(): String = "WalletAccount(${address.take(4)}...${address.takeLast(4)})"
}

/**
 * One wallet round-trip at a time, every result folded into [WalletOutcome].
 *
 * [call] runs [AdapterOperations] against the connected wallet (authorizing or
 * reauthorizing first, as MWA does), so a ViewModel never sees a TransactionResult, an
 * ActivityResultSender or an auth token. [account] is the last account the wallet
 * authorized, shared by every screen, null until the first successful call.
 */
interface WalletSession {
    val account: StateFlow<WalletAccount?>

    suspend fun <T> call(block: suspend (AdapterOperations) -> T): WalletOutcome<T>

    /** Authorize only; the payload is the account the wallet chose. */
    suspend fun connect(): WalletOutcome<WalletAccount>

    /** Deauthorize and forget [account]. */
    suspend fun disconnect(): WalletOutcome<Unit>
}
