package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.PinnedAddresses
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.plainticker.PassSummary
import com.plainticker.mobile.data.plainticker.VoteSummary
import com.solana.publickey.ProgramDerivedAddress
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.VersionedMessage

/**
 * Reads a transaction before the wallet is ever opened for it, and refuses one that does not do
 * what the sheet in front of the signer says it does.
 *
 * Every transaction this app hands a wallet was assembled by someone else: a pass or a vote by
 * PlainTicker's server, a swap by Jupiter. The sheet's figures come from the JSON beside the bytes,
 * not from the bytes, so a compromised server could send a transaction that drains a token account
 * or sets an Approve delegate while the sheet says 12 USDC to the treasury. This class closes that
 * gap: it decodes the message with the web3-solana library already in the dependency graph
 * ([Transaction.from], legacy and v0 alike), and compares what the instructions actually do with
 * the figures shown and with addresses this app pins itself ([PinnedAddresses], [KnownPrograms]).
 *
 * What it can see is the top level of the message: which programs are invoked, with which
 * accounts and which data. What a program does inside itself (Jupiter's route through its venues)
 * is not visible from the bytes; the defence there is that only Jupiter's own programs may be
 * invoked at all, and that every instruction this class can read beside them is one it allows.
 *
 * It never throws. A transaction it cannot parse is a [Verdict.Refuse], like any other it will not
 * let through, and the reason is for a debug log only, never for a screen.
 */
object TransactionGuard {

    sealed interface Verdict {
        data object Allow : Verdict

        /** [reason] is for the debug log; the screen shows the flow's existing plain error state. */
        data class Refuse(val reason: String) : Verdict
    }

    /** A pass is paid in USDC or USDT, both classic-Token mints, named by symbol in the summary. */
    private val PASS_MINTS = mapOf("USDC" to KnownMints.USDC, "USDT" to KnownMints.USDT)

    private val PASS_PROGRAMS = setOf(
        KnownPrograms.COMPUTE_BUDGET,
        KnownPrograms.SYSTEM,
        KnownPrograms.TOKEN,
        KnownPrograms.TOKEN_2022,
        KnownPrograms.ASSOCIATED_TOKEN,
        KnownPrograms.MEMO,
    )

    private val VOTE_PROGRAMS = setOf(KnownPrograms.COMPUTE_BUDGET, KnownPrograms.SYSTEM, KnownPrograms.MEMO)

    private val JUPITER_PROGRAMS = setOf(KnownPrograms.JUPITER_AGGREGATOR_V6, KnownPrograms.JUPITER_RFQ)

    private val SWAP_PROGRAMS = JUPITER_PROGRAMS + setOf(
        KnownPrograms.COMPUTE_BUDGET,
        KnownPrograms.SYSTEM,
        KnownPrograms.TOKEN,
        KnownPrograms.TOKEN_2022,
        KnownPrograms.ASSOCIATED_TOKEN,
    )

    /**
     * Anchor discriminators of the two Jupiter instructions whose layout is known from real /order
     * answers: `route_v2` (sha256("global:route_v2")[0..8]) and JupiterZ `fill`. Both carry the
     * input amount as a little-endian u64 straight after the discriminator, so the amount can be
     * read from the bytes and not only from the JSON. Any other Jupiter instruction is still
     * allowed; for those the JSON's own amount check is the one that applies.
     */
    private val ROUTE_V2 = hex("bb64facc31c4af14")
    private val RFQ_FILL = hex("a860b7a35c0a28a0")
    private const val AMOUNT_OFFSET = 8

    /** Rent-exempt minimum of a 165-byte token account: what one CreateIdempotent can cost the payer. */
    const val TOKEN_ACCOUNT_RENT_LAMPORTS = 2_039_280L

    /** The fee of one signature. */
    const val SIGNATURE_FEE_LAMPORTS = 5_000L

    // SPL Token instruction tags (the same for Token and Token-2022).
    private const val TOKEN_TRANSFER = 3
    private const val TOKEN_APPROVE = 4
    private const val TOKEN_SET_AUTHORITY = 6
    private const val TOKEN_BURN = 8
    private const val TOKEN_CLOSE_ACCOUNT = 9
    private const val TOKEN_TRANSFER_CHECKED = 12
    private const val TOKEN_APPROVE_CHECKED = 13
    private const val TOKEN_BURN_CHECKED = 15
    private const val TOKEN_SYNC_NATIVE = 17

    private const val SYSTEM_TRANSFER = 2

    // ---- The three flows -------------------------------------------------------------------

    /**
     * A pass, as `lib/pass/build.ts` builds it: optional CreateIdempotent for the payer's and the
     * treasury's token accounts, exactly one SPL Token transfer of [PassSummary.amount] from the
     * payer to the pinned treasury's token account for the pinned mint, an optional 0-lamport
     * System transfer to the treasury (the reference key), and exactly one `PT-PASS:<codeHash>`
     * memo. Nothing else but ComputeBudget, and no Approve, SetAuthority or CloseAccount at all.
     */
    suspend fun checkPass(bytes: ByteArray, wallet: String, summary: PassSummary, codeHash: String): Verdict =
        guarded {
            val tx = decode(bytes) ?: return@guarded refuse("not a transaction this app can read")
            val m = tx.message
            val keys = staticKeys(m)
            requireServerBuiltShape(m, keys, wallet)?.let { return@guarded it }
            requirePrograms(m, keys, PASS_PROGRAMS)?.let { return@guarded it }

            val mint = PASS_MINTS[summary.mint] ?: return@guarded refuse("pass mint ${summary.mint} is not one this app pays in")
            val treasury = PinnedAddresses.TREASURY
            val treasuryAccount = ata(treasury, mint, KnownPrograms.TOKEN)
            val walletAccount = ata(wallet, mint, KnownPrograms.TOKEN)
            if (summary.treasury != treasury) return@guarded refuse("summary names a treasury that is not the pinned one")
            if (summary.destination != treasuryAccount) {
                return@guarded refuse("summary destination is not the pinned treasury's token account")
            }

            var transfers = 0
            var memos = 0
            var createdAccounts = 0
            for (ix in m.instructions) {
                val program = keys[ix.programIdIndex.toInt()]
                val acc = ix.accountIndices.map { keys[it.toInt() and 0xff] }
                val data = ix.data
                when (program) {
                    KnownPrograms.TOKEN, KnownPrograms.TOKEN_2022 -> {
                        val tag = data.firstOrNull()?.toInt()?.and(0xff)
                            ?: return@guarded refuse("token instruction without data")
                        when (tag) {
                            TOKEN_TRANSFER, TOKEN_TRANSFER_CHECKED -> {
                                if (program != KnownPrograms.TOKEN) return@guarded refuse("pass transfer is not on the classic Token program")
                                val t = tokenTransfer(tag, acc, data) ?: return@guarded refuse("token transfer is malformed")
                                if (t.mint != null && t.mint != mint) return@guarded refuse("transfer mint is not the pinned mint")
                                if (t.destination != treasuryAccount) return@guarded refuse("transfer does not go to the pinned treasury")
                                if (t.source != walletAccount) return@guarded refuse("transfer does not come from the wallet's own token account")
                                if (t.authority != wallet) return@guarded refuse("transfer is not authorised by the wallet")
                                if (t.amount != summary.amount) return@guarded refuse("transfer amount ${t.amount} is not the displayed ${summary.amount}")
                                transfers++
                            }
                            TOKEN_APPROVE, TOKEN_APPROVE_CHECKED -> return@guarded refuse("pass carries an Approve")
                            TOKEN_SET_AUTHORITY -> return@guarded refuse("pass carries a SetAuthority")
                            TOKEN_CLOSE_ACCOUNT -> return@guarded refuse("pass carries a CloseAccount")
                            else -> return@guarded refuse("pass carries token instruction $tag")
                        }
                    }
                    KnownPrograms.ASSOCIATED_TOKEN -> {
                        checkCreateAta(acc, data, wallet, allowedOwners = setOf(wallet, treasury), mint = mint)
                            ?.let { return@guarded it }
                        createdAccounts++
                    }
                    KnownPrograms.SYSTEM -> {
                        val lamports = systemTransferLamports(acc, data)
                            ?: return@guarded refuse("pass carries a System instruction that is not a transfer")
                        if (lamports != 0L || acc[0] != wallet || acc[1] != treasury) {
                            return@guarded refuse("pass System transfer is not the 0-lamport treasury reference")
                        }
                    }
                    KnownPrograms.MEMO -> {
                        if (acc.any { it != wallet }) return@guarded refuse("memo names an account that is not the wallet")
                        if (data.decodeToString() != "PT-PASS:${codeHash.trim().lowercase()}") {
                            return@guarded refuse("memo is not this device's PT-PASS memo")
                        }
                        memos++
                    }
                    KnownPrograms.COMPUTE_BUDGET -> Unit
                    else -> return@guarded refuse("program $program is not allowed in a pass")
                }
            }
            if (transfers != 1) return@guarded refuse("pass carries $transfers token transfers, not exactly one")
            if (memos != 1) return@guarded refuse("pass carries $memos memos, not exactly one")
            val cost = feeLamports(m, keys) ?: return@guarded refuse("compute budget instruction is malformed")
            if (cost + createdAccounts * TOKEN_ACCOUNT_RENT_LAMPORTS > summary.lamports) {
                return@guarded refuse("pass can cost more lamports than the ${summary.lamports} displayed")
            }
            Verdict.Allow
        }

    /**
     * A vote, as `lib/vote/build.ts` builds it: one 0-lamport System transfer from the voter to the
     * pinned collector and one `PT-VOTE:<TICKER>` memo for the ticker that was asked for. Nothing
     * else but ComputeBudget.
     */
    suspend fun checkVote(bytes: ByteArray, wallet: String, ticker: String, summary: VoteSummary): Verdict =
        guarded {
            val tx = decode(bytes) ?: return@guarded refuse("not a transaction this app can read")
            val m = tx.message
            val keys = staticKeys(m)
            requireServerBuiltShape(m, keys, wallet)?.let { return@guarded it }
            requirePrograms(m, keys, VOTE_PROGRAMS)?.let { return@guarded it }
            val collector = PinnedAddresses.VOTE_COLLECTOR
            if (summary.collector != collector) return@guarded refuse("summary names a collector that is not the pinned one")

            var transfers = 0
            var memos = 0
            for (ix in m.instructions) {
                val program = keys[ix.programIdIndex.toInt()]
                val acc = ix.accountIndices.map { keys[it.toInt() and 0xff] }
                when (program) {
                    KnownPrograms.SYSTEM -> {
                        val lamports = systemTransferLamports(acc, ix.data)
                            ?: return@guarded refuse("vote carries a System instruction that is not a transfer")
                        if (lamports != 0L || acc[0] != wallet || acc[1] != collector) {
                            return@guarded refuse("vote System transfer is not the 0-lamport transfer to the collector")
                        }
                        transfers++
                    }
                    KnownPrograms.MEMO -> {
                        if (acc.any { it != wallet }) return@guarded refuse("memo names an account that is not the wallet")
                        if (ix.data.decodeToString() != "PT-VOTE:${ticker.trim().uppercase()}") {
                            return@guarded refuse("memo is not the PT-VOTE memo for the requested ticker")
                        }
                        memos++
                    }
                    KnownPrograms.COMPUTE_BUDGET -> Unit
                    else -> return@guarded refuse("program $program is not allowed in a vote")
                }
            }
            if (transfers != 1) return@guarded refuse("vote carries $transfers collector transfers, not exactly one")
            if (memos != 1) return@guarded refuse("vote carries $memos memos, not exactly one")
            val cost = feeLamports(m, keys) ?: return@guarded refuse("compute budget instruction is malformed")
            if (cost > summary.lamports) return@guarded refuse("vote can cost more lamports than the ${summary.lamports} displayed")
            Verdict.Allow
        }

    /**
     * A Jupiter Ultra order. On a gasless order Jupiter, or the RFQ maker, is the fee payer, so the
     * wallet must be a required signer rather than account 0. The order must be for the mints and
     * the amount that were asked for; every top-level program must be Jupiter's own or one of the
     * five a swap needs around it; no token authority may be handed to anyone but the wallet; and
     * when the wallet does pay the fee, the priority fee may not exceed the one the order stated,
     * which is the figure the SOL check before the wallet already used.
     */
    fun checkSwap(
        bytes: ByteArray,
        wallet: String,
        order: SwapOrder,
        inputMint: String,
        outputMint: String,
        amount: Long,
    ): Verdict = guardedBlocking {
        if (order.inputMint != inputMint) return@guardedBlocking refuse("order input mint is not the requested one")
        if (order.outputMint != outputMint) return@guardedBlocking refuse("order output mint is not the requested one")
        if (order.inAmount.toLongOrNull() != amount) return@guardedBlocking refuse("order amount ${order.inAmount} is not the requested $amount")
        if (order.taker != null && order.taker != wallet) return@guardedBlocking refuse("order was built for another taker")

        val tx = decode(bytes) ?: return@guardedBlocking refuse("not a transaction this app can read")
        val m = tx.message
        val keys = staticKeys(m)
        val signers = keys.take(m.signatureCount.toInt())
        if (wallet !in signers) return@guardedBlocking refuse("the wallet is not a required signer")
        requirePrograms(m, keys, SWAP_PROGRAMS)?.let { return@guardedBlocking it }

        var jupiter = 0
        for (ix in m.instructions) {
            val program = keys[ix.programIdIndex.toInt()]
            // An index past the static keys is an address-table account: never the wallet, which
            // signs and so is always static.
            val acc = ix.accountIndices.map { keys.getOrNull(it.toInt() and 0xff) }
            val data = ix.data
            when (program) {
                in JUPITER_PROGRAMS -> {
                    jupiter++
                    val head = data.copyOfRange(0, minOf(8, data.size))
                    if ((head.contentEquals(ROUTE_V2) || head.contentEquals(RFQ_FILL)) &&
                        readU64(data, AMOUNT_OFFSET) != amount
                    ) {
                        return@guardedBlocking refuse("Jupiter instruction amount is not the requested $amount")
                    }
                }
                KnownPrograms.TOKEN, KnownPrograms.TOKEN_2022 -> {
                    val tag = data.firstOrNull()?.toInt()?.and(0xff)
                        ?: return@guardedBlocking refuse("token instruction without data")
                    when (tag) {
                        TOKEN_APPROVE, TOKEN_APPROVE_CHECKED ->
                            if (acc.getOrNull(1) != wallet) return@guardedBlocking refuse("swap carries an Approve to another delegate")
                        TOKEN_SET_AUTHORITY -> {
                            val newAuthority = setAuthorityTarget(data)
                            if (newAuthority != wallet) return@guardedBlocking refuse("swap carries a SetAuthority to another key")
                        }
                        TOKEN_CLOSE_ACCOUNT ->
                            if (acc.getOrNull(1) != wallet) return@guardedBlocking refuse("swap closes an account into another key")
                        TOKEN_SYNC_NATIVE -> Unit
                        TOKEN_TRANSFER, TOKEN_TRANSFER_CHECKED, TOKEN_BURN, TOKEN_BURN_CHECKED ->
                            return@guardedBlocking refuse("swap carries a top-level token transfer or burn")
                        else -> return@guardedBlocking refuse("swap carries token instruction $tag")
                    }
                }
                KnownPrograms.ASSOCIATED_TOKEN -> {
                    val kind = data.firstOrNull()?.toInt() ?: 0
                    if (kind != 0 && kind != 1) return@guardedBlocking refuse("swap carries an associated-token instruction that is not a create")
                    if (acc.getOrNull(2) != wallet) return@guardedBlocking refuse("swap creates a token account for another owner")
                }
                KnownPrograms.SYSTEM -> {
                    // A System instruction funded by someone else (a gasless payer) costs the
                    // wallet nothing; one funded by the wallet may move no lamports.
                    if (acc.getOrNull(0) == wallet) {
                        val lamports = systemTransferLamports(acc.map { it.orEmpty() }, data)
                        if (lamports != 0L) return@guardedBlocking refuse("swap moves lamports out of the wallet")
                    }
                }
                KnownPrograms.COMPUTE_BUDGET -> Unit
                else -> return@guardedBlocking refuse("program $program is not allowed in a swap")
            }
        }
        if (jupiter == 0) return@guardedBlocking refuse("swap carries no Jupiter instruction")

        if (keys.first() == wallet) {
            val priority = priorityFeeLamports(m, keys) ?: return@guardedBlocking refuse("compute budget instruction is malformed")
            if (priority > order.prioritizationFeeLamports) {
                return@guardedBlocking refuse("priority fee $priority exceeds the order's ${order.prioritizationFeeLamports}")
            }
        }
        Verdict.Allow
    }

    // ---- Decoding ---------------------------------------------------------------------------

    /**
     * The transaction, or null. It must re-serialize to exactly the bytes it was read from, so the
     * message this class checked is byte for byte the one the wallet is handed; a version other
     * than legacy or v0 is refused.
     */
    internal fun decode(bytes: ByteArray): Transaction? {
        val tx = runCatching { Transaction.from(bytes) }.getOrNull() ?: return null
        if (!runCatching { tx.serialize() }.getOrNull().contentEquals(bytes)) return null
        val m = tx.message
        if (m is VersionedMessage && m.version.toInt() != 0) return null
        if (m.accounts.isEmpty()) return null
        val count = m.accounts.size
        val lookups = (m as? VersionedMessage)?.addressTableLookups.orEmpty()
            .sumOf { it.writableIndexes.size + it.readOnlyIndexes.size }
        for (ix in m.instructions) {
            // A program id can never come from an address table.
            if (ix.programIdIndex.toInt() >= count) return null
            if (ix.accountIndices.any { (it.toInt() and 0xff) >= count + lookups }) return null
        }
        return tx
    }

    private fun staticKeys(m: Message): List<String> = m.accounts.map { it.base58() }

    /** A server-built transaction: the wallet pays and is the only signer, and nothing is looked up. */
    private fun requireServerBuiltShape(m: Message, keys: List<String>, wallet: String): Verdict? {
        if (keys.first() != wallet) return refuse("the fee payer is not the connected wallet")
        if (m.signatureCount.toInt() != 1) return refuse("a server-built transaction must need only the wallet's signature")
        if ((m as? VersionedMessage)?.addressTableLookups.orEmpty().isNotEmpty()) {
            return refuse("a server-built transaction must not hide accounts in an address table")
        }
        return null
    }

    private fun requirePrograms(m: Message, keys: List<String>, allowed: Set<String>): Verdict? {
        val unknown = m.instructions.map { keys[it.programIdIndex.toInt()] }.firstOrNull { it !in allowed }
        return unknown?.let { refuse("program $it is not on the allowlist") }
    }

    private class TokenTransfer(
        val source: String,
        val destination: String,
        val authority: String,
        val mint: String?,
        val amount: Long,
    )

    /** Transfer: [source, destination, authority] + u64. TransferChecked: [source, mint, destination, authority] + u64 + u8. */
    private fun tokenTransfer(tag: Int, acc: List<String>, data: ByteArray): TokenTransfer? = when (tag) {
        TOKEN_TRANSFER ->
            if (data.size != 9 || acc.size != 3) null else TokenTransfer(acc[0], acc[1], acc[2], null, readU64(data, 1))
        TOKEN_TRANSFER_CHECKED ->
            if (data.size != 10 || acc.size != 4) null else TokenTransfer(acc[0], acc[2], acc[3], acc[1], readU64(data, 1))
        else -> null
    }

    /** SetAuthority: tag, authority type, then COption<Pubkey>. Null when the new authority is None. */
    private fun setAuthorityTarget(data: ByteArray): String? {
        if (data.size < 3 || data[2].toInt() != 1 || data.size < 35) return null
        return SolanaPublicKey(data.copyOfRange(3, 35)).base58()
    }

    /** System Transfer: u32 tag 2, u64 lamports, accounts [from, to]. Null for anything else. */
    private fun systemTransferLamports(acc: List<String>, data: ByteArray): Long? {
        if (data.size != 12 || acc.size < 2) return null
        if (readU32(data, 0) != SYSTEM_TRANSFER.toLong()) return null
        return readU64(data, 4)
    }

    /**
     * CreateAssociatedTokenAccount (no data) or CreateIdempotent (`[1]`): [funder, ata, owner,
     * mint, system, token program], funded by the wallet, for an owner in [allowedOwners] and the
     * pinned mint, at the address derived for them.
     */
    private suspend fun checkCreateAta(
        acc: List<String>,
        data: ByteArray,
        wallet: String,
        allowedOwners: Set<String>,
        mint: String,
    ): Verdict? {
        val kind = if (data.isEmpty()) 0 else data[0].toInt()
        if (data.size > 1 || (kind != 0 && kind != 1)) return refuse("associated-token instruction is not a create")
        if (acc.size != 6) return refuse("associated-token create is malformed")
        val (funder, account, owner, createdMint, system, tokenProgram) = acc
        if (funder != wallet) return refuse("token account creation is not funded by the wallet")
        if (owner !in allowedOwners) return refuse("token account is created for an owner this pass does not involve")
        if (createdMint != mint) return refuse("token account is created for another mint")
        if (system != KnownPrograms.SYSTEM || tokenProgram != KnownPrograms.TOKEN) return refuse("token account create names the wrong programs")
        if (account != ata(owner, mint, KnownPrograms.TOKEN)) return refuse("token account is not the owner's associated account")
        return null
    }

    private operator fun <T> List<T>.component6(): T = this[5]

    // ---- Fees -------------------------------------------------------------------------------

    /** What the fee payer can be charged: one signature fee per required signature plus the priority fee. */
    private fun feeLamports(m: Message, keys: List<String>): Long? =
        priorityFeeLamports(m, keys)?.let { it + m.signatureCount.toLong() * SIGNATURE_FEE_LAMPORTS }

    /**
     * ceil(unit price in micro-lamports x unit limit / 1,000,000). Without a SetComputeUnitLimit
     * the runtime allows 200,000 units per non-ComputeBudget instruction, capped at 1.4 million.
     * Null when a ComputeBudget instruction is one this class does not recognise.
     */
    internal fun priorityFeeLamports(m: Message, keys: List<String>): Long? {
        var limit: Long? = null
        var price = 0L
        var others = 0
        for (ix in m.instructions) {
            if (keys[ix.programIdIndex.toInt()] != KnownPrograms.COMPUTE_BUDGET) {
                others++
                continue
            }
            val d = ix.data
            when (d.firstOrNull()?.toInt()) {
                1 -> if (d.size != 5) return null // RequestHeapFrame
                2 -> if (d.size != 5) return null else limit = readU32(d, 1)
                3 -> if (d.size != 9) return null else price = readU64(d, 1)
                4 -> if (d.size != 5) return null // SetLoadedAccountsDataSizeLimit
                else -> return null
            }
        }
        if (price < 0) return null
        val units = limit ?: minOf(others * 200_000L, 1_400_000L)
        val micro = java.math.BigInteger.valueOf(price).multiply(java.math.BigInteger.valueOf(units))
        val lamports = micro.add(java.math.BigInteger.valueOf(999_999)).divide(java.math.BigInteger.valueOf(1_000_000))
        return if (lamports.bitLength() < 63) lamports.toLong() else null
    }

    // ---- Addresses --------------------------------------------------------------------------

    /** The associated token account of [owner] for [mint] under [tokenProgram]. */
    internal suspend fun ata(owner: String, mint: String, tokenProgram: String): String =
        ProgramDerivedAddress.find(
            listOf(
                SolanaPublicKey.from(owner).bytes,
                SolanaPublicKey.from(tokenProgram).bytes,
                SolanaPublicKey.from(mint).bytes,
            ),
            SolanaPublicKey.from(KnownPrograms.ASSOCIATED_TOKEN),
        ).getOrThrow().base58()

    // ---- Small helpers ----------------------------------------------------------------------

    private fun refuse(reason: String) = Verdict.Refuse(reason)

    private inline fun guardedBlocking(block: () -> Verdict): Verdict =
        try {
            block()
        } catch (e: Exception) {
            refuse("transaction could not be read: ${e::class.simpleName}")
        }

    private suspend inline fun guarded(crossinline block: suspend () -> Verdict): Verdict =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            refuse("transaction could not be read: ${e::class.simpleName}")
        }

    private fun readU32(b: ByteArray, at: Int): Long {
        require(at + 4 <= b.size)
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xff)
        return v
    }

    /** A little-endian u64; one past Long.MAX_VALUE would read negative and match no real amount. */
    private fun readU64(b: ByteArray, at: Int): Long {
        require(at + 8 <= b.size)
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xff)
        return v
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
