package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.PinnedAddresses
import com.solana.publickey.SolanaPublicKey
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * A Solana transaction's wire format, written out by hand for the tests (compact-u16 lengths,
 * legacy and v0), independent of the web3-solana decoder [TransactionGuard] uses. Two jobs: it
 * cross-checks that library against the format itself, and it lets a test take a real transaction
 * apart, change one thing, and put it back together, which is what every negative case is.
 */
data class WireMessage(
    /** null for a legacy message, otherwise the version (0). */
    val version: Int?,
    val numRequiredSignatures: Int,
    val numReadonlySigned: Int,
    val numReadonlyUnsigned: Int,
    val keys: List<String>,
    val blockhash: ByteArray,
    val instructions: List<WireIx>,
    val lookups: List<WireLookup> = emptyList(),
) {
    data class WireIx(val program: Int, val accounts: List<Int>, val data: ByteArray)

    data class WireLookup(val table: String, val writable: List<Int>, val readonly: List<Int>)

    fun message(): ByteArray {
        val out = ByteArrayOutputStream()
        if (version != null) out.write(0x80 or version)
        out.write(numRequiredSignatures)
        out.write(numReadonlySigned)
        out.write(numReadonlyUnsigned)
        out.writeCompact(keys.size)
        keys.forEach { out.write(key(it)) }
        out.write(blockhash)
        out.writeCompact(instructions.size)
        for (ix in instructions) {
            out.write(ix.program)
            out.writeCompact(ix.accounts.size)
            ix.accounts.forEach { out.write(it) }
            out.writeCompact(ix.data.size)
            out.write(ix.data)
        }
        if (version != null) {
            out.writeCompact(lookups.size)
            for (l in lookups) {
                out.write(key(l.table))
                out.writeCompact(l.writable.size)
                l.writable.forEach { out.write(it) }
                out.writeCompact(l.readonly.size)
                l.readonly.forEach { out.write(it) }
            }
        }
        return out.toByteArray()
    }

    /** The unsigned transaction: one zeroed 64-byte slot per required signature, then the message. */
    fun transaction(): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeCompact(numRequiredSignatures)
        repeat(numRequiredSignatures) { out.write(ByteArray(64)) }
        out.write(message())
        return out.toByteArray()
    }

    fun base64(): String = Base64.getEncoder().encodeToString(transaction())

    /** The index of [address], appending it as a read-only non-signer when it is not a static key yet. */
    fun withKey(address: String): Pair<WireMessage, Int> {
        val at = keys.indexOf(address)
        if (at >= 0) return this to at
        val staticCount = keys.size
        // Appending a static key moves every address-table index up by one.
        val shifted = instructions.map { ix ->
            ix.copy(accounts = ix.accounts.map { if (it >= staticCount) it + 1 else it })
        }
        return copy(
            keys = keys + address,
            numReadonlyUnsigned = numReadonlyUnsigned + 1,
            instructions = shifted,
        ) to staticCount
    }

    /** Appends one instruction calling [program] over [accounts], adding any key it lacks. */
    fun plus(program: String, accounts: List<String>, data: ByteArray): WireMessage {
        var m = this
        val (withProgram, p) = m.withKey(program)
        m = withProgram
        val indices = accounts.map { a -> m.withKey(a).also { m = it.first }.second }
        return m.copy(instructions = m.instructions + WireIx(p, indices, data))
    }

    fun replaceKey(from: String, to: String): WireMessage = copy(keys = keys.map { if (it == from) to else it })

    fun mapInstruction(index: Int, change: (WireIx) -> WireIx): WireMessage =
        copy(instructions = instructions.mapIndexed { i, ix -> if (i == index) change(ix) else ix })

    fun indexOfProgram(program: String): Int = instructions.indexOfFirst { keys[it.program] == program }

    companion object {
        fun key(address: String): ByteArray = SolanaPublicKey.from(address).bytes

        fun address(bytes: ByteArray): String = SolanaPublicKey(bytes).base58()

        fun parseTransaction(bytes: ByteArray): WireMessage {
            val r = Reader(bytes)
            val sigs = r.compact()
            r.skip(64 * sigs)
            return parseMessage(r).also { check(r.done) { "trailing bytes" } }
        }

        fun parseBase64(tx: String): WireMessage = parseTransaction(Base64.getDecoder().decode(tx))

        private fun parseMessage(r: Reader): WireMessage {
            val first = r.u8()
            val version = if (first and 0x80 != 0) first and 0x7f else null
            val required = if (version == null) first else r.u8()
            val roSigned = r.u8()
            val roUnsigned = r.u8()
            val keys = List(r.compact()) { address(r.bytes(32)) }
            val blockhash = r.bytes(32)
            val ixs = List(r.compact()) {
                val p = r.u8()
                val acc = List(r.compact()) { r.u8() }
                WireIx(p, acc, r.bytes(r.compact()))
            }
            val lookups = if (version == null) {
                emptyList()
            } else {
                List(r.compact()) {
                    WireLookup(address(r.bytes(32)), List(r.compact()) { r.u8() }, List(r.compact()) { r.u8() })
                }
            }
            return WireMessage(version, required, roSigned, roUnsigned, keys, blockhash, ixs, lookups)
        }

        private fun ByteArrayOutputStream.writeCompact(value: Int) {
            var v = value
            while (true) {
                val b = v and 0x7f
                v = v shr 7
                if (v == 0) {
                    write(b)
                    return
                }
                write(b or 0x80)
            }
        }
    }

    private class Reader(private val b: ByteArray) {
        private var i = 0
        val done: Boolean get() = i == b.size
        fun u8(): Int = b[i++].toInt() and 0xff
        fun skip(n: Int) {
            i += n
        }
        fun bytes(n: Int): ByteArray = b.copyOfRange(i, i + n).also { i += n }
        fun compact(): Int {
            var v = 0
            var shift = 0
            while (true) {
                val x = u8()
                v = v or ((x and 0x7f) shl shift)
                if (x and 0x80 == 0) return v
                shift += 7
            }
        }
    }
}

/** Little-endian instruction data. */
fun leData(vararg parts: Any): ByteArray {
    val size = parts.sumOf {
        when (it) {
            is Byte -> 1
            is Int -> 4
            is Long -> 8
            is ByteArray -> it.size
            else -> error("unsupported $it")
        }.toInt()
    }
    val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    for (p in parts) {
        when (p) {
            is Byte -> buf.put(p)
            is Int -> buf.putInt(p)
            is Long -> buf.putLong(p)
            is ByteArray -> buf.put(p)
        }
    }
    return buf.array()
}

/**
 * Transactions in the exact shape PlainTicker's server builds them (FinanceAnalyst
 * `lib/pass/build.ts` and `lib/vote/build.ts`), compiled the way `@solana/web3.js`
 * `compileToV0Message` orders keys, for any payer. A test pins that this and the independently
 * generated resource fixtures agree byte for byte.
 */
object ServerBuilt {
    const val BLOCKHASH = "GHtXQBsoZHVnNFa9YevAzFr17DJjgHXk3ycTKD5xD3Zi"

    private class Meta(var signer: Boolean, var writable: Boolean)

    private data class Ix(val program: String, val accounts: List<Triple<String, Boolean, Boolean>>, val data: ByteArray)

    private fun compile(payer: String, ixs: List<Ix>): WireMessage {
        val meta = LinkedHashMap<String, Meta>()
        fun get(k: String) = meta.getOrPut(k) { Meta(false, false) }
        get(payer).apply { signer = true; writable = true }
        for (ix in ixs) {
            get(ix.program)
            for ((k, s, w) in ix.accounts) get(k).apply { signer = signer || s; writable = writable || w }
        }
        val ws = meta.filter { it.value.signer && it.value.writable }.keys
        val rs = meta.filter { it.value.signer && !it.value.writable }.keys
        val wn = meta.filter { !it.value.signer && it.value.writable }.keys
        val rn = meta.filter { !it.value.signer && !it.value.writable }.keys
        val keys = (ws + rs + wn + rn).toList()
        return WireMessage(
            version = 0,
            numRequiredSignatures = ws.size + rs.size,
            numReadonlySigned = rs.size,
            numReadonlyUnsigned = rn.size,
            keys = keys,
            blockhash = WireMessage.key(BLOCKHASH),
            instructions = ixs.map { ix ->
                WireMessage.WireIx(keys.indexOf(ix.program), ix.accounts.map { keys.indexOf(it.first) }, ix.data)
            },
        )
    }

    suspend fun pass(
        payer: String,
        codeHash: String,
        createPayerAta: Boolean = false,
        createTreasuryAta: Boolean = false,
        amount: Long = 12_000_000L,
        mint: String = KnownMints.USDC,
    ): WireMessage {
        val treasury = PinnedAddresses.TREASURY
        val token = KnownPrograms.TOKEN
        val source = TransactionGuard.ata(payer, mint, token)
        val destination = TransactionGuard.ata(treasury, mint, token)
        val create = { ata: String, owner: String ->
            Ix(
                KnownPrograms.ASSOCIATED_TOKEN,
                listOf(
                    Triple(payer, true, true), Triple(ata, false, true), Triple(owner, false, false),
                    Triple(mint, false, false), Triple(KnownPrograms.SYSTEM, false, false), Triple(token, false, false),
                ),
                byteArrayOf(1),
            )
        }
        val ixs = buildList {
            if (createPayerAta) add(create(source, payer))
            if (createTreasuryAta) add(create(destination, treasury))
            add(Ix(token, listOf(Triple(source, false, true), Triple(destination, false, true), Triple(payer, true, false)), leData(3.toByte(), amount)))
            add(Ix(KnownPrograms.SYSTEM, listOf(Triple(payer, true, true), Triple(treasury, false, true)), leData(2, 0L)))
            add(Ix(KnownPrograms.MEMO, emptyList(), "PT-PASS:$codeHash".encodeToByteArray()))
        }
        return compile(payer, ixs)
    }

    fun vote(voter: String, ticker: String, collector: String = PinnedAddresses.VOTE_COLLECTOR): WireMessage =
        compile(
            voter,
            listOf(
                Ix(KnownPrograms.SYSTEM, listOf(Triple(voter, true, true), Triple(collector, false, true)), leData(2, 0L)),
                Ix(KnownPrograms.MEMO, emptyList(), "PT-VOTE:$ticker".encodeToByteArray()),
            ),
        )
}
