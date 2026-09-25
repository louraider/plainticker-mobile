package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.data.rpc.RpcEncoding
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import com.plainticker.mobile.data.rpc.toTokenBalance

/**
 * What a second, independent node says about one xStock mint and one wallet's balance of it.
 *
 * [facts] is null when the node answered and the account is not a Token-2022 mint this app can
 * read, which includes a mint whose decimals are not [MintFacts.XSTOCK_DECIMALS].
 * [spendableRaw] adds up the wallet's accounts for the mint that are not frozen, the same rule
 * [com.plainticker.mobile.ui.swap.SwapFunds] uses for the forwarder's read.
 */
data class SecondRead(
    val facts: MintFacts?,
    val spendableRaw: Long,
    /** Wall clock at the mint read, epoch millis: the moment the multiplier in force is asked at. */
    val readAtMillis: Long,
)

/**
 * The independent half of Swap to USDC (security audit, 2026-09-26).
 *
 * Every chain read the app makes goes through PlainTicker's own `/api/v1/rpc` forwarder, and the
 * swap turns what the user types into base units with the decimals and the multiplier that
 * forwarder reported, then caps it at the balance that forwarder reported. TransactionGuard checks
 * the transaction against the amount the app computed, so it cannot see a wrong input. A forwarder
 * that answered `decimals: 10`, a tiny multiplier or an inflated balance could turn "1 TSLAx"
 * into an order for 100, the guard would pass it, and the wallet would be asked to approve it.
 *
 * So before the xStock side of a swap is spent, the same two facts are read again from a node
 * that PlainTicker does not run, and the two reads must agree.
 */
interface SecondSource {
    /**
     * Throws when the node could not be reached or answered with an error. [minContextSlot], when
     * given, is a slot the balance read must be at least as new as, as on
     * [com.plainticker.mobile.repo.RpcRepository.tokenBalances].
     */
    suspend fun read(owner: String, mint: String, minContextSlot: Long? = null): SecondRead
}

/**
 * [SecondSource] over the public Solana mainnet node, [PUBLIC_RPC_URL]. It needs no key, is run
 * by the Solana Foundation rather than by PlainTicker, and answers the same `getAccountInfo` and
 * `getTokenAccountsByOwner` the forwarder does, so both reads parse through the same code
 * ([MintFacts.from], [toTokenBalance]) and any difference between them is a difference in the
 * answer. It is used for the swap only, two calls per check, well inside the node's per-IP limit.
 *
 * [api] must be a [SolanaRpcApi] built with [PUBLIC_RPC_URL]; the forwarder's URL would make this
 * a second read of the same source, which is no check at all.
 */
class PublicRpcSecondSource(
    private val api: SolanaRpcApi,
    private val clock: Clock,
) : SecondSource {

    override suspend fun read(owner: String, mint: String, minContextSlot: Long?): SecondRead {
        val account = api.getAccountInfo(mint, RpcEncoding.JSON_PARSED).value
        val readAt = clock.nowMillis()
        // Token-2022 only: every xStock mint is owned by it (MintFacts.PROGRAM), so an account
        // under the classic program cannot carry one.
        val accounts = api.getTokenAccountsByOwner(owner, KnownPrograms.TOKEN_2022, minContextSlot).value.orEmpty()
        val spendable = accounts.mapNotNull { it.toTokenBalance() }
            .filter { it.mint == mint && it.owner == owner && !it.frozen }
            .sumOf { it.amountRaw }
        return SecondRead(facts = MintFacts.from(account), spendableRaw = spendable, readAtMillis = readAt)
    }

    companion object {
        /** The public Solana mainnet node. Not PlainTicker's, which is the point. */
        const val PUBLIC_RPC_URL = "https://api.mainnet-beta.solana.com"
    }
}
