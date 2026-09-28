package com.plainticker.mobile.wallet

import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.common.ProtocolContract
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * Folds MWA's [TransactionResult] into [WalletOutcome].
 *
 * The spike surfaced failures as `message (ExceptionClass)`, which is how the cancel paths
 * of clientlib 2.2.0 were told apart from real errors:
 * - the user backs out of the wallet: the wallet Activity returns RESULT_CANCELED and the
 *   adapter reports "Request was interrupted" with an [InterruptedException];
 * - the user declines in the wallet: a JSON-RPC error with [ProtocolContract.ERROR_NOT_SIGNED]
 *   ("User did not authorize signing") or, on the authorize step,
 *   [ProtocolContract.ERROR_AUTHORIZATION_FAILED];
 * - the calling coroutine is cancelled: the adapter swallows the [CancellationException]
 *   and reports "Request was cancelled".
 * - the person came back to the app with no answer from the wallet: [WalletDismissedException],
 *   raised by [MwaWalletSession] itself.
 * Everything else, including the adapter's own timeouts, is a [WalletOutcome.Error].
 */
fun <T> TransactionResult<T>.toWalletOutcome(): WalletOutcome<T> = when (this) {
    is TransactionResult.Success -> WalletOutcome.Success(payload)
    is TransactionResult.NoWalletFound -> WalletOutcome.NoWallet
    is TransactionResult.Failure -> if (isUserCancel(e)) WalletOutcome.Cancelled else WalletOutcome.Error(message, e)
}

internal fun isUserCancel(error: Throwable): Boolean {
    val cause = if (error is ExecutionException) error.cause ?: error else error
    return when (cause) {
        is WalletDismissedException -> true
        is TimeoutCancellationException -> false
        is InterruptedException -> true
        is CancellationException -> true
        is JsonRpc20Client.JsonRpc20RemoteException ->
            cause.code == ProtocolContract.ERROR_NOT_SIGNED || cause.code == ProtocolContract.ERROR_AUTHORIZATION_FAILED
        else -> false
    }
}

/**
 * The person left the wallet and came back to the app with no answer from it: a back press on
 * the wallet's Connect sheet, a closed chooser, a wallet that never associated. Read as a cancel.
 */
class WalletDismissedException : Exception("The wallet was left without an answer")
