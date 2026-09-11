package com.myapp.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.RpcCluster
import com.solana.mobilewalletadapter.clientlib.TransactionParams
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana

/**
 * The [AdapterOperations] a [FakeWalletSession.operations] hands to a `call` block, so a
 * test can see exactly what the ViewModel asked the wallet to sign and script what comes
 * back. Only [signTransactions] is implemented; the wallet's other verbs are not part of
 * any flow under test and throw if reached.
 */
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class FakeAdapterOperations(
    /** What the "wallet" returns for each transaction it is asked to sign, in order. Empty = no payloads. */
    var signedPayloads: List<ByteArray> = emptyList(),
) : AdapterOperations {

    /** Every batch of unsigned transactions the ViewModel asked to sign. */
    val signRequests = mutableListOf<List<ByteArray>>()

    override suspend fun signTransactions(transactions: Array<ByteArray>): MobileWalletAdapterClient.SignPayloadsResult {
        signRequests += transactions.toList()
        return MobileWalletAdapterClient.SignPayloadsResult(signedPayloads.toTypedArray())
    }

    override suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        rpcCluster: RpcCluster,
    ): MobileWalletAdapterClient.AuthorizationResult = notScripted("authorize")

    override suspend fun authorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        chain: String,
        authToken: String?,
        features: Array<String>?,
        addresses: Array<ByteArray>?,
        signInPayload: SignInWithSolana.Payload?,
    ): MobileWalletAdapterClient.AuthorizationResult = notScripted("authorize")

    override suspend fun reauthorize(
        identityUri: Uri,
        iconUri: Uri,
        identityName: String,
        authToken: String,
    ): MobileWalletAdapterClient.AuthorizationResult = notScripted("reauthorize")

    override suspend fun deauthorize(authToken: String) = notScripted("deauthorize")

    override suspend fun getCapabilities(): MobileWalletAdapterClient.GetCapabilitiesResult = notScripted("getCapabilities")

    override suspend fun signMessages(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>,
    ): MobileWalletAdapterClient.SignPayloadsResult = notScripted("signMessages")

    override suspend fun signMessagesDetached(
        messages: Array<ByteArray>,
        addresses: Array<ByteArray>,
    ): MobileWalletAdapterClient.SignMessagesResult = notScripted("signMessagesDetached")

    override suspend fun signAndSendTransactions(
        transactions: Array<ByteArray>,
        params: TransactionParams,
    ): MobileWalletAdapterClient.SignAndSendTransactionsResult = notScripted("signAndSendTransactions")

    private fun notScripted(verb: String): Nothing =
        throw UnsupportedOperationException("FakeAdapterOperations: $verb is not part of any flow under test")
}
