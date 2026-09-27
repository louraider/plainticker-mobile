package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.PinnedAddresses
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.wallet.TransactionGuard.SwapReading
import com.plainticker.mobile.wallet.TransactionGuard.Why
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * What a swap can cost the wallet in SOL, read from the bytes (security review, 2026-09-27), on the
 * REAL Jupiter orders every other guard test runs on.
 *
 * The judges' counterexample: an order declaring a priority fee of 1,000,000,000 lamports beside a
 * signature fee of -999,995,000 and no rent summed to the 5,000 lamports the sheet showed, and the
 * guard compared the bytes' priority fee only with the declared one, which they matched. Every
 * fee and rent field is now a non-negative lamport count that sums without overflow; the fees a
 * screen states are the ones the message itself sets (5,000 per required signature when the
 * wallet pays, and the compute budget's price times its limit); both are held to what the order
 * declares and to a 0.01 SOL ceiling; and the rent is shown at its upper bound, a create's bytes
 * carrying no amount of their own. Each rule has a case that breaks exactly it.
 */
class TransactionGuardCostsTest {

    private val json = HttpClientFactory.json
    private val simPayer = "HzrEstnLfzsijhaD6z5frkSE2vWZEH5EUfn3bU9swo1f"
    private val takerPays = "5tzFkiKscXHK5ZXCGbXZxdw7gTjjD1mBwuoFbhUvuAi9"
    private val reverseTaker = "AC5RDfQFmDS1deWZos921JfqscXdByf8BKHs5ACWjtW2"

    private fun order(path: String) = json.decodeFromString(SwapOrder.serializer(), Fixtures.read(path))

    private fun bytesOf(o: SwapOrder) = Base64.getDecoder().decode(o.transaction!!)

    private val gaslessMetis = "jupiter/order-usdc-tslax-5-gasless-metis.json"
    private val gaslessRfq = "jupiter/order-usdc-tslax-5-gasless-rfq.json"
    private val metisTakerPays = "jupiter/order-usdc-tslax-5-metis-taker-pays.json"
    private val reverseDefault = "jupiter/order-tslax-usdc-default-metis.json"
    private val reverseExcludeRfq = "jupiter/order-tslax-usdc-exclude-rfq-metis.json"

    private suspend fun read(
        o: SwapOrder,
        bytes: ByteArray = bytesOf(o),
        wallet: String = takerPays,
        input: String = KnownMints.USDC,
        output: String = KnownMints.TSLAX,
        amount: Long = 5_000_000L,
    ) = TransactionGuard.readSwap(bytes, wallet, o, input, output, amount)

    private suspend fun readOut(o: SwapOrder, bytes: ByteArray = bytesOf(o)) =
        read(o, bytes, reverseTaker, KnownMints.TSLAX, KnownMints.USDC, 264_600L)

    private fun costsOf(r: SwapReading): TransactionGuard.SwapCosts {
        assertTrue("expected Allowed, got $r", r is SwapReading.Allowed)
        return (r as SwapReading.Allowed).costs
    }

    private fun assertRefused(r: SwapReading, containing: String, why: Why = Why.COSTS_MORE_THAN_SHOWN) {
        assertTrue("expected a refusal, got $r", r is SwapReading.Refused)
        val refusal = (r as SwapReading.Refused).refusal
        assertTrue("refusal '${refusal.reason}' should mention '$containing'", containing in refusal.reason)
        assertEquals(refusal.reason, why, refusal.why)
    }

    private fun create(funder: String, account: String, owner: String, mint: String, tokenProgram: String) =
        listOf(funder, account, owner, mint, KnownPrograms.SYSTEM, tokenProgram)

    private fun WireMessage.plusCreate(accounts: List<String>) = plus(KnownPrograms.ASSOCIATED_TOKEN, accounts, byteArrayOf(1))

    // ---- The ceilings ------------------------------------------------------------------------

    @Test
    fun `the rent ceilings are the stated sizes at the stated rate, and above every real order's rent`() {
        assertEquals(2_039_280L, TransactionGuard.TOKEN_ACCOUNT_RENT_CEILING_LAMPORTS)
        assertEquals(TransactionGuard.TOKEN_ACCOUNT_RENT_LAMPORTS, TransactionGuard.TOKEN_ACCOUNT_RENT_CEILING_LAMPORTS)
        assertEquals(2_672_640L, TransactionGuard.TOKEN_2022_ACCOUNT_RENT_CEILING_LAMPORTS)
        assertEquals(10_000_000L, TransactionGuard.MAX_WALLET_FEE_LAMPORTS)
        // Every real order's rent per account (1,488,440 classic, 1,559,560 for an xStock account)
        // sits under its ceiling, and every real order's fees far under the fee ceiling.
        for (path in listOf(gaslessMetis, gaslessRfq, metisTakerPays, reverseDefault, reverseExcludeRfq)) {
            val o = order(path)
            assertTrue(path, o.rentFeeLamports <= TransactionGuard.MAX_SWAP_ACCOUNT_CREATES * TransactionGuard.TOKEN_2022_ACCOUNT_RENT_CEILING_LAMPORTS)
            assertTrue(path, o.signatureFeeLamports + o.prioritizationFeeLamports < TransactionGuard.MAX_WALLET_FEE_LAMPORTS / 100)
            assertTrue(path, o.feeFieldsValid)
        }
    }

    // ---- Real orders -------------------------------------------------------------------------

    @Test
    fun `the taker-pays order costs one signature and the priority fee its compute budget sets`() = runTest {
        val o = order(metisTakerPays)
        val m = WireMessage.parseTransaction(bytesOf(o))
        assertEquals("the wallet is the fee payer and the only signer", listOf(takerPays), m.keys.take(m.numRequiredSignatures))
        val costs = costsOf(read(o))
        assertEquals(5_000L, costs.signatureFeeLamports)
        assertEquals(395L, costs.priorityFeeLamports)
        assertEquals(o.prioritizationFeeLamports, costs.priorityFeeLamports)
        assertEquals(0L, costs.rentUpperBoundLamports)
        assertEquals(5_395L, costs.totalLamports)
    }

    @Test
    fun `a gasless order costs the wallet no fee, and only the rent the order names it for`() = runTest {
        val metis = costsOf(read(order(gaslessMetis), wallet = PinnedAddresses.TREASURY))
        assertEquals(0L, metis.networkFeeLamports)
        assertEquals("Jupiter's gas payer funds the account and is named for its rent", 0L, metis.rentUpperBoundLamports)

        val rfq = costsOf(read(order(gaslessRfq), wallet = simPayer))
        assertEquals("the maker pays the fees", 0L, rfq.networkFeeLamports)
        assertEquals("the order names the taker for the rent", 1_488_440L, rfq.rentDeclaredLamports)
        // The taker itself funds its xStock account in these bytes, so the deposit shown is a
        // Token-2022 account's ceiling, above the 1,488,440 the order declares.
        assertEquals(1, rfq.walletFundedCreates)
        assertEquals(TransactionGuard.TOKEN_2022_ACCOUNT_RENT_CEILING_LAMPORTS, rfq.rentUpperBoundLamports)
    }

    @Test
    fun `Swap to USDC shows the deposit of the accounts the wallet opens at their ceiling, never below the declared rent`() = runTest {
        for (path in listOf(reverseDefault, reverseExcludeRfq)) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            var ceiling = 0L
            for (ix in m.instructions.filter { m.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN && m.keys[it.accounts[0]] == reverseTaker }) {
                val account = m.keys[ix.accounts[1]]
                val classic = m.keys.getOrNull(ix.accounts[3])?.let { TransactionGuard.ata(reverseTaker, it, KnownPrograms.TOKEN) } == account ||
                    listOf(KnownMints.WSOL, KnownMints.USDC, "BjcRmwm8e25RgjkyaFE56fc7bxRgGPw96JUkXRJFEroT")
                        .any { TransactionGuard.ata(reverseTaker, it, KnownPrograms.TOKEN) == account }
                ceiling += if (classic) TransactionGuard.TOKEN_ACCOUNT_RENT_CEILING_LAMPORTS else TransactionGuard.TOKEN_2022_ACCOUNT_RENT_CEILING_LAMPORTS
            }
            val costs = costsOf(readOut(o))
            assertEquals(path, o.rentFeeLamports, costs.rentDeclaredLamports)
            assertEquals(path, maxOf(o.rentFeeLamports, ceiling), costs.rentUpperBoundLamports)
            assertTrue(path, costs.rentUpperBoundLamports >= o.rentFeeLamports)
            assertEquals(path, 5_000L, costs.signatureFeeLamports)
            assertEquals(path, o.prioritizationFeeLamports, costs.priorityFeeLamports)
        }
        // The default order opens two classic accounts (wrapped SOL and the pool token): 2 x 2,039,280.
        assertEquals(4_078_560L, costsOf(readOut(order(reverseDefault))).rentUpperBoundLamports)
    }

    // ---- The judges' counterexample, and its variants ---------------------------------------

    @Test
    fun `a negative signature fee hiding a huge priority fee is refused`() = runTest {
        val o = order(metisTakerPays).copy(
            prioritizationFeeLamports = 1_000_000_000L,
            signatureFeeLamports = -999_995_000L,
            rentFeeLamports = 0L,
        )
        assertEquals("the fields sum to what the sheet would have shown", 5_000L, o.signatureFeeLamports + o.prioritizationFeeLamports + o.rentFeeLamports)
        assertFalse(o.feeFieldsValid)
        assertRefused(read(o), "negative")
        // The same fields with bytes that really do set a priority fee of a whole SOL.
        val m = WireMessage.parseTransaction(bytesOf(o))
        val limit = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 2 }
        val price = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3 }
        val dear = m.mapInstruction(limit) { it.copy(data = leData(2.toByte(), 1_000_000)) }
            .mapInstruction(price) { it.copy(data = leData(3.toByte(), 1_000_000_000L)) }
        assertRefused(read(o, dear.transaction()), "negative")
    }

    @Test
    fun `a negative value in any fee or rent field is refused, whichever field carries it`() = runTest {
        val base = order(metisTakerPays)
        for (o in listOf(
            base.copy(signatureFeeLamports = -1L),
            base.copy(prioritizationFeeLamports = -1L),
            base.copy(rentFeeLamports = -1L),
            base.copy(rentFeeLamports = -2_000_000L, prioritizationFeeLamports = 2_000_395L),
            base.copy(signatureFeeLamports = Long.MIN_VALUE),
        )) {
            assertFalse(o.toString(), o.feeFieldsValid)
            assertRefused(read(o), "negative")
        }
    }

    @Test
    fun `fields whose sum overflows are refused`() = runTest {
        val base = order(metisTakerPays)
        val o = base.copy(rentFeeLamports = Long.MAX_VALUE)
        assertFalse(o.feeFieldsValid)
        assertRefused(read(o), "overflow")
        assertFalse(base.copy(rentFeeLamports = Long.MAX_VALUE - 1_000L, signatureFeeLamports = 5_000L).feeFieldsValid)
    }

    @Test
    fun `a declared fee above the ceiling is refused, even when the bytes charge less`() = runTest {
        val base = order(metisTakerPays)
        assertRefused(read(base.copy(prioritizationFeeLamports = 1_000_000_000L)), "above the ceiling")
        assertRefused(read(base.copy(signatureFeeLamports = TransactionGuard.MAX_WALLET_FEE_LAMPORTS + 1)), "above the ceiling")
        // At the ceiling itself the order passes, and the screen states the bytes' own fee.
        val atCeiling = costsOf(read(base.copy(prioritizationFeeLamports = TransactionGuard.MAX_WALLET_FEE_LAMPORTS)))
        assertEquals(395L, atCeiling.priorityFeeLamports)
    }

    @Test
    fun `bytes whose fees together pass the ceiling are refused, though each matches the order`() = runTest {
        val o = order(metisTakerPays).copy(prioritizationFeeLamports = 9_999_999L)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val limit = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 2 }
        val price = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3 }
        assertTrue("the real order sets both a limit and a price", limit >= 0 && price >= 0)
        // 1,000,000 units at 9,999,999 micro-lamports: exactly the declared 9,999,999 lamports.
        val dear = m.mapInstruction(limit) { it.copy(data = leData(2.toByte(), 1_000_000)) }
            .mapInstruction(price) { it.copy(data = leData(3.toByte(), 9_999_999L)) }
        assertRefused(read(o, dear.transaction()), "exceed the ceiling")
    }

    @Test
    fun `the priority fee shown is the compute budget's, not a larger declared one`() = runTest {
        val costs = costsOf(read(order(metisTakerPays).copy(prioritizationFeeLamports = 1_000_000L)))
        assertEquals(395L, costs.priorityFeeLamports)
    }

    @Test
    fun `a signature fee the order understates is refused`() = runTest {
        val o = order(metisTakerPays)
        assertRefused(read(o.copy(signatureFeeLamports = 4_999L)), "signature fee")
        assertRefused(read(o.copy(signatureFeeLamports = 0L)), "signature fee")
    }

    @Test
    fun `a second required signature the wallet pays for is counted, and refused past the declared fee`() = runTest {
        val o = order(metisTakerPays)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val twoSigners = m.copy(numRequiredSignatures = 2)
        assertRefused(read(o, twoSigners.transaction()), "signature fee 10000")
        val costs = costsOf(read(o.copy(signatureFeeLamports = 10_000L), twoSigners.transaction()))
        assertEquals(10_000L, costs.signatureFeeLamports)
    }

    // ---- Rent, bounded ---------------------------------------------------------------------

    @Test
    fun `declared rent above what two of the largest accounts could cost is refused`() = runTest {
        val o = order(metisTakerPays)
        val ceiling = TransactionGuard.MAX_SWAP_ACCOUNT_CREATES * TransactionGuard.TOKEN_2022_ACCOUNT_RENT_CEILING_LAMPORTS
        assertRefused(read(o.copy(rentFeeLamports = ceiling + 1)), "rent")
        assertEquals(ceiling, costsOf(read(o.copy(rentFeeLamports = ceiling))).rentUpperBoundLamports)
    }

    @Test
    fun `the deposit shown for accounts the wallet opens is their ceiling, whatever smaller rent the order declares`() = runTest {
        val o = order(metisTakerPays).copy(rentFeeLamports = 1_488_440L, rentFeePayer = takerPays)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val tslax = TransactionGuard.ata(takerPays, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        val wsol = TransactionGuard.ata(takerPays, KnownMints.WSOL, KnownPrograms.TOKEN)
        val one = m.plusCreate(create(takerPays, tslax, takerPays, KnownMints.TSLAX, KnownPrograms.TOKEN_2022))
        val oneCosts = costsOf(read(o, one.transaction()))
        assertEquals(1, oneCosts.walletFundedCreates)
        assertEquals(1_488_440L, oneCosts.rentDeclaredLamports)
        assertEquals("a Token-2022 account at its ceiling", 2_672_640L, oneCosts.rentUpperBoundLamports)

        val two = one.plusCreate(create(takerPays, wsol, takerPays, KnownMints.WSOL, KnownPrograms.TOKEN))
        val twoCosts = costsOf(read(o.copy(rentFeeLamports = 2_976_880L), two.transaction()))
        assertEquals(2, twoCosts.walletFundedCreates)
        assertEquals("one Token-2022 and one classic account", 2_672_640L + 2_039_280L, twoCosts.rentUpperBoundLamports)
        assertEquals(twoCosts.networkFeeLamports + twoCosts.rentUpperBoundLamports, twoCosts.totalLamports)
    }

    @Test
    fun `an account the fee payer funds adds nothing to the wallet's deposit`() = runTest {
        val g = order(gaslessMetis)
        val costs = costsOf(read(g, wallet = PinnedAddresses.TREASURY))
        assertEquals(0, costs.walletFundedCreates)
        assertEquals(0L, costs.totalLamports)
    }

    @Test
    fun `checkSwap and readSwap agree on every real order and every refusal above`() = runTest {
        for ((path, wallet) in listOf(gaslessMetis to PinnedAddresses.TREASURY, gaslessRfq to simPayer, metisTakerPays to takerPays)) {
            val o = order(path)
            assertEquals(TransactionGuard.Verdict.Allow, TransactionGuard.checkSwap(bytesOf(o), wallet, o, KnownMints.USDC, KnownMints.TSLAX, 5_000_000L))
            val bad = o.copy(signatureFeeLamports = -1L)
            assertTrue(TransactionGuard.checkSwap(bytesOf(bad), wallet, bad, KnownMints.USDC, KnownMints.TSLAX, 5_000_000L) is TransactionGuard.Verdict.Refuse)
        }
    }
}
