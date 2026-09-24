package com.plainticker.mobile.repo

import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.rpc.ContextValue
import com.plainticker.mobile.data.rpc.RpcAccount
import com.plainticker.mobile.data.rpc.RpcEncoding
import com.plainticker.mobile.data.rpc.SkrStake
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import com.plainticker.mobile.data.rpc.TokenBalance
import com.plainticker.mobile.data.rpc.toSkrStakeAccount
import com.plainticker.mobile.data.rpc.toTokenBalance

/** On-chain reads the app needs, all through PlainTicker's bounded RPC forwarder. */
interface RpcRepository {
    /**
     * SOL balance of [owner] in lamports. [minContextSlot], when given, is a slot the answer must
     * be at least as new as: the slot a swap just landed in, so the read cannot predate it.
     */
    suspend fun lamports(owner: String, minContextSlot: Long? = null): Long

    /**
     * Non-empty token accounts of [owner] under the classic and the Token-2022 programs, each with
     * its own state (a frozen one is listed, and says so). [minContextSlot] as on [lamports].
     */
    suspend fun tokenBalances(owner: String, minContextSlot: Long? = null): List<TokenBalance>

    /** One account, or null when it does not exist. */
    suspend fun accountInfo(pubkey: String, encoding: RpcEncoding = RpcEncoding.BASE64): RpcAccount?

    /**
     * One account with the slot the node answered at, so a surface can say how fresh the read is
     * ("slot N, 2 s ago"). [ContextValue.value] is null when the account does not exist.
     */
    suspend fun accountAt(pubkey: String, encoding: RpcEncoding = RpcEncoding.BASE64): ContextValue<RpcAccount>

    /** Several accounts in order; a missing one is null in its position. */
    suspend fun accounts(pubkeys: List<String>, encoding: RpcEncoding = RpcEncoding.BASE64): List<RpcAccount?>

    /** SKR staked by [wallet] across all of its stake accounts. */
    suspend fun skrStake(wallet: String): SkrStake
}

class ForwarderRpcRepository(private val api: SolanaRpcApi) : RpcRepository {

    override suspend fun lamports(owner: String, minContextSlot: Long?): Long =
        api.getBalance(owner, minContextSlot).value ?: 0L

    override suspend fun tokenBalances(owner: String, minContextSlot: Long?): List<TokenBalance> {
        // Sequential on purpose: the forwarder rate-limits per IP.
        val classic = api.getTokenAccountsByOwner(owner, KnownPrograms.TOKEN, minContextSlot).value.orEmpty()
        val token2022 = api.getTokenAccountsByOwner(owner, KnownPrograms.TOKEN_2022, minContextSlot).value.orEmpty()
        return (classic + token2022).mapNotNull { it.toTokenBalance() }.filter { it.amountRaw > 0L }
    }

    override suspend fun accountInfo(pubkey: String, encoding: RpcEncoding): RpcAccount? =
        accountAt(pubkey, encoding).value

    override suspend fun accountAt(pubkey: String, encoding: RpcEncoding): ContextValue<RpcAccount> =
        api.getAccountInfo(pubkey, encoding)

    override suspend fun accounts(pubkeys: List<String>, encoding: RpcEncoding): List<RpcAccount?> {
        if (pubkeys.isEmpty()) return emptyList()
        return pubkeys.chunked(SolanaRpcApi.MAX_MULTIPLE_ACCOUNTS).flatMap { chunk ->
            api.getMultipleAccounts(chunk, encoding).value ?: List(chunk.size) { null }
        }
    }

    override suspend fun skrStake(wallet: String): SkrStake =
        SkrStake(api.getSkrStakeAccounts(wallet).mapNotNull { it.toSkrStakeAccount() })
}
