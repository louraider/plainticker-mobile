package com.myapp.wallet

/**
 * What came back from one wallet round-trip, typed so every screen handles the same four
 * cases (plan D10). [Cancelled] is the user closing or declining in the wallet: neutral
 * copy, no haptic, the sheet stays. [NoWallet] is a device without an MWA wallet.
 */
sealed interface WalletOutcome<out T> {
    data class Success<T>(val value: T) : WalletOutcome<T>
    data object NoWallet : WalletOutcome<Nothing>
    data object Cancelled : WalletOutcome<Nothing>
    data class Error(val message: String, val cause: Throwable? = null) : WalletOutcome<Nothing>

    val valueOrNull: T?
        get() = (this as? Success)?.value

    fun <R> map(transform: (T) -> R): WalletOutcome<R> = when (this) {
        is Success -> Success(transform(value))
        is NoWallet -> this
        is Cancelled -> this
        is Error -> this
    }
}
