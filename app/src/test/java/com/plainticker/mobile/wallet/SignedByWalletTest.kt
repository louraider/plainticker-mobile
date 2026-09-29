package com.plainticker.mobile.wallet

import com.plainticker.mobile.R
import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.sentence
import com.plainticker.mobile.wallet.TransactionGuard.SignedReading
import com.plainticker.mobile.wallet.TransactionGuard.SwapReading
import com.plainticker.mobile.wallet.TransactionGuard.WalletChange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * [TransactionGuard.readSigned]: what a wallet hands back from `sign_transactions`, read in full
 * (Seeker, 1.3.26, 2026-09-29: Seed Vault Wallet signed a USDC to AMZNx order and the old
 * byte-for-byte rule refused it). Every case starts from a REAL order, the live 1 USDC to AAPLx
 * one of 29 Sep, changes what a wallet might, signs it in the wallet's slot, and reads it.
 */
class SignedByWalletTest {

    private val demo = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ"
    private val attacker = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
    private val aaplx = "XsbEhLAtcf6HdfpFZ5xEMdqW8nfAvcsP5bdudRLJzJp"
    private val amount = 1_000_000L

    private val order: SwapOrder = HttpClientFactory.json.decodeFromString(
        SwapOrder.serializer(),
        Fixtures.read("jupiter/order-live-0929-usdc-aaplx-1.json"),
    )
    private val unsigned: ByteArray = Base64.getDecoder().decode(order.transaction!!)
    private val wire = WireMessage.parseTransaction(unsigned)

    /** [tx] with a non-zero signature in slot 0, the wallet's: it pays the fee on this order. */
    private fun signed(tx: ByteArray): ByteArray = tx.copyOf().also { java.util.Arrays.fill(it, 1, 65, 0x5A) }

    private suspend fun read(signed: ByteArray) =
        TransactionGuard.readSigned(unsigned, signed, demo, order, KnownMints.USDC, aaplx, amount)

    private suspend fun allowed(signed: ByteArray): SignedReading.Allowed {
        val r = read(signed)
        assertTrue("expected Allowed, got $r", r is SignedReading.Allowed)
        return r as SignedReading.Allowed
    }

    private suspend fun refused(signed: ByteArray, change: WalletChange): SignedReading.Refused {
        val r = read(signed)
        assertTrue("expected a refusal, got $r", r is SignedReading.Refused)
        assertEquals((r as SignedReading.Refused).reason, change, r.change)
        return r
    }

    private val priceIx = wire.instructions.indexOfFirst {
        wire.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3
    }

    private val unitLimit: Long = wire.instructions.first {
        wire.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 2
    }.data.let { d -> (0 until 4).fold(0L) { v, i -> v or ((d[1 + i].toLong() and 0xff) shl (8 * i)) } }

    /** The order with its compute unit price set to [microLamports], as a wallet might. */
    private fun withPrice(microLamports: Long): ByteArray =
        wire.mapInstruction(priceIx) { it.copy(data = leData(3.toByte(), microLamports)) }.transaction()

    private val route = wire.indexOfProgram(KnownPrograms.JUPITER_AGGREGATOR_V6)

    @Test
    fun `the checked message signed as it is passes, with the order's own costs`() = runTest {
        assertTrue(priceIx >= 0 && route >= 0)
        val before = TransactionGuard.readSwap(unsigned, demo, order, KnownMints.USDC, aaplx, amount) as SwapReading.Allowed
        val r = allowed(signed(unsigned))
        assertFalse(r.changed)
        assertEquals(before.costs, r.costs)
        assertEquals(166L, r.costs.priorityFeeLamports)
    }

    @Test
    fun `a priority fee the wallet raised within the ceiling passes, and the fee is the signed one`() = runTest {
        val price = 50_000L
        val r = allowed(signed(withPrice(price)))
        assertTrue(r.changed)
        val expected = (price * unitLimit + 999_999L) / 1_000_000L
        assertTrue("above the order's own 166", expected > order.prioritizationFeeLamports)
        assertEquals(expected, r.costs.priorityFeeLamports)
        assertEquals(5_000L, r.costs.signatureFeeLamports)
        // The order's own bytes at that price are still refused: the order is held to its declared fee.
        assertTrue(TransactionGuard.readSwap(withPrice(price), demo, order, KnownMints.USDC, aaplx, amount) is SwapReading.Refused)
    }

    @Test
    fun `a compute limit and a new blockhash the wallet set pass`() = runTest {
        val limitIx = wire.instructions.indexOfFirst { wire.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 2 }
        val m = wire.mapInstruction(limitIx) { it.copy(data = leData(2.toByte(), 1_000_000)) }
            .copy(blockhash = wire.blockhash.copyOf().also { it[0] = (it[0] + 1).toByte() })
        assertTrue(allowed(signed(m.transaction())).changed)
    }

    @Test
    fun `a priority fee above the ceiling is refused, naming the ceiling`() = runTest {
        val price = (TransactionGuard.MAX_WALLET_FEE_LAMPORTS * 1_000_000L) / unitLimit + 1_000_000L
        val r = refused(signed(withPrice(price)), WalletChange.FEE_ABOVE_CEILING)
        assertEquals(R.string.swap_signed_fee_ceiling, r.sentence().id)
        assertEquals(listOf("0.01"), r.sentence().args)
    }

    @Test
    fun `a Lighthouse assertion the wallet added passes`() = runTest {
        val destination = TransactionGuard.ata(demo, aaplx, KnownPrograms.TOKEN_2022)
        val m = wire.plus(KnownPrograms.LIGHTHOUSE, listOf(destination), byteArrayOf(0x10, 0, 1, 2, 3))
        val r = allowed(signed(m.transaction()))
        assertTrue(r.changed)
    }

    @Test
    fun `an instruction of any other program is refused, and the program is named`() = runTest {
        val m = wire.plus(attacker, listOf(demo), byteArrayOf(1))
        val r = refused(signed(m.transaction()), WalletChange.PROGRAM)
        assertEquals(attacker, r.program)
        assertEquals(R.string.swap_signed_program, r.sentence().id)
        assertEquals(listOf(Fmt.shortKey(attacker)), r.sentence().args)
        // A Memo is allowed in a pass, not in a swap, signed or not.
        refused(signed(wire.plus(KnownPrograms.MEMO, listOf(demo), byteArrayOf(1)).transaction()), WalletChange.PROGRAM)
    }

    @Test
    fun `a destination the wallet changed is refused`() = runTest {
        val (withKey, at) = wire.withKey(attacker)
        val m = withKey.mapInstruction(route) { ix -> ix.copy(accounts = ix.accounts.toMutableList().also { it[2] = at }) }
        refused(signed(m.transaction()), WalletChange.RECIPIENT)
    }

    @Test
    fun `an amount the wallet changed is refused`() = runTest {
        val m = wire.mapInstruction(route) { ix ->
            ix.copy(data = ix.data.copyOf().also { d -> leData(2_000_000L).copyInto(d, 8) })
        }
        refused(signed(m.transaction()), WalletChange.AMOUNT)
    }

    @Test
    fun `an Approve the wallet added is refused`() = runTest {
        val m = wire.plus(KnownPrograms.TOKEN, listOf(demo, attacker, demo), leData(4.toByte(), Long.MAX_VALUE))
        refused(signed(m.transaction()), WalletChange.CONTROL)
    }

    @Test
    fun `no signature, another fee payer, or bytes that are not a transaction are refused`() = runTest {
        refused(unsigned, WalletChange.NOT_SIGNED)
        refused(signed(wire.replaceKey(demo, attacker).transaction()), WalletChange.FEE_PAYER)
        refused("SIGNED".encodeToByteArray(), WalletChange.UNREADABLE)
        // The right bytes held to the wrong wallet: it is not the fee payer, nor a signer.
        val r = TransactionGuard.readSigned(unsigned, signed(unsigned), attacker, order, KnownMints.USDC, aaplx, amount)
        assertTrue(r is SignedReading.Refused)
    }
}
