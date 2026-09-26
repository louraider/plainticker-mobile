package com.plainticker.mobile.repo

import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.rpc.KeyedAccount
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.data.rpc.RpcEncoding
import com.plainticker.mobile.data.rpc.SolanaRpcApi
import com.plainticker.mobile.data.rpc.toTokenBalance
import com.plainticker.mobile.wallet.TransactionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
 *
 * **Tolerance (audit 2026-09-26, item 6).** The public node rate-limits by IP and can trail the
 * forwarder by a few slots right after a landing, and either one used to pause Swap to USDC on
 * the first answer. A read now runs, at most:
 *
 * 1. [api] with the asked-for `minContextSlot`, exactly as before;
 * 2. after [RETRY_BACKOFF_MS], [api] again with the slot relaxed by [SLOT_LAG_ALLOWANCE] (about a
 *    minute of slots). This is safe because of what the read is for: the decimals and the
 *    multiplier do not move within a minute's slots, and the balance only ever caps the amount
 *    ([com.plainticker.mobile.ui.swap.SwapTrust.confirmScale] takes the smaller of this read and
 *    the forwarder's fresh one), so a trailing balance can narrow the cap but never widen it;
 * 3. only when both failed, [fallback] ([FALLBACK_RPC_URL], a second operator), with the same
 *    relaxed slot. That node refuses the indexed `getTokenAccountsByOwner` without a key, so it is
 *    asked for the mint and the owner's Token-2022 associated token account in one
 *    `getMultipleAccounts`. The associated account is the one a swap spends from
 *    ([com.plainticker.mobile.wallet.TransactionGuard] refuses any other), so a balance held in
 *    another account is left out, which again can only narrow the cap.
 *
 * When every source fails, the last failure is thrown with the earlier ones suppressed on it, and
 * the swap pauses with nothing sent, exactly as before.
 */
class PublicRpcSecondSource(
    private val api: SolanaRpcApi,
    private val clock: Clock,
    private val fallback: SolanaRpcApi? = null,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : SecondSource {

    override suspend fun read(owner: String, mint: String, minContextSlot: Long?): SecondRead {
        val failures = mutableListOf<Exception>()
        attempt(failures) { readByOwner(api, owner, mint, minContextSlot) }?.let { return it }
        pause(RETRY_BACKOFF_MS)
        val relaxed = relaxedSlot(minContextSlot)
        attempt(failures) { readByOwner(api, owner, mint, relaxed) }?.let { return it }
        if (fallback != null) {
            attempt(failures) { readAssociated(fallback, owner, mint, relaxed) }?.let { return it }
        }
        val last = failures.last()
        failures.forEach { if (it !== last) last.addSuppressed(it) }
        throw last
    }

    private suspend fun attempt(failures: MutableList<Exception>, read: suspend () -> SecondRead): SecondRead? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failures += e
        null
    }

    private suspend fun readByOwner(node: SolanaRpcApi, owner: String, mint: String, minContextSlot: Long?): SecondRead {
        val account = node.getAccountInfo(mint, RpcEncoding.JSON_PARSED).value
        val readAt = clock.nowMillis()
        // Token-2022 only: every xStock mint is owned by it (MintFacts.PROGRAM), so an account
        // under the classic program cannot carry one.
        val accounts = node.getTokenAccountsByOwner(owner, KnownPrograms.TOKEN_2022, minContextSlot).value.orEmpty()
        val spendable = accounts.mapNotNull { it.toTokenBalance() }
            .filter { it.mint == mint && it.owner == owner && !it.frozen }
            .sumOf { it.amountRaw }
        return SecondRead(facts = MintFacts.from(account), spendableRaw = spendable, readAtMillis = readAt)
    }

    /** The fallback node's shape: the mint and the owner's Token-2022 associated account, in one call. */
    private suspend fun readAssociated(node: SolanaRpcApi, owner: String, mint: String, minContextSlot: Long?): SecondRead {
        val ata = TransactionGuard.ata(owner, mint, KnownPrograms.TOKEN_2022)
        val accounts = node.getMultipleAccounts(listOf(mint, ata), RpcEncoding.JSON_PARSED, minContextSlot).value.orEmpty()
        val readAt = clock.nowMillis()
        val spendable = accounts.getOrNull(1)
            ?.let { KeyedAccount(pubkey = ata, account = it).toTokenBalance() }
            ?.takeIf { it.mint == mint && it.owner == owner && !it.frozen && it.programId == KnownPrograms.TOKEN_2022 }
            ?.amountRaw ?: 0L
        return SecondRead(facts = accounts.getOrNull(0)?.let { MintFacts.from(it) }, spendableRaw = spendable, readAtMillis = readAt)
    }

    companion object {
        /** The public Solana mainnet node. Not PlainTicker's, which is the point. */
        const val PUBLIC_RPC_URL = "https://api.mainnet-beta.solana.com"

        /**
         * A second keyless public node, run by Allnodes rather than the Solana Foundation, asked
         * only after [PUBLIC_RPC_URL] has failed twice. Checked 2026-09-26: it answers
         * `getAccountInfo` and `getMultipleAccounts` without a key and refuses
         * `getTokenAccountsByOwner` ("Indexed requests require a personal token").
         */
        const val FALLBACK_RPC_URL = "https://solana-rpc.publicnode.com"

        /** One short beat before the second attempt, long enough to clear a per-second rate window. */
        const val RETRY_BACKOFF_MS = 600L

        /** Slots a retried read may trail the asked-for slot by: 150 slots, about a minute. */
        const val SLOT_LAG_ALLOWANCE = 150L

        /** [slot] less [SLOT_LAG_ALLOWANCE]; null when there was no slot, or the result would not be positive. */
        fun relaxedSlot(slot: Long?): Long? = slot?.let { it - SLOT_LAG_ALLOWANCE }?.takeIf { it > 0L }
    }
}
