package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.PinnedAddresses
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.wallet.TransactionGuard.Verdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * [TransactionGuard.checkSwap] held to the same standard in the reverse direction, an xStock back
 * to USDC ("Swap to USDC"), on REAL Jupiter answers.
 *
 * **The fixtures, and where they came from.** `jupiter/order-tslax-usdc-default-metis.json` and
 * `jupiter/order-tslax-usdc-exclude-rfq-metis.json` are two live `GET https://api.jup.ag/swap/v2/order`
 * answers captured 2026-09-24 and stored byte for byte as they arrived, with nothing added (the
 * earlier fixtures carry a `_fixture_note` key; these carry none, so the note lives here):
 * `inputMint` TSLAx, `outputMint` USDC, `amount` 264600 (0.002646 TSLAx, the founder's own fill of
 * that day), once with Jupiter's default routing and once with `excludeRouters=jupiterz`, the
 * aggregator retry [com.plainticker.mobile.data.jupiter.JupiterSwapApi] sends. The taker is
 * `AC5R...jtW2`, found read-only with `getTokenLargestAccounts` on the TSLAx mint (the third
 * largest account, 9,923.7 TSLAx, initialized, not frozen) and tagged by Jupiter's own holder data
 * as an exchange hot wallet; an address with that much TSLAx and SOL is what a signable order for
 * this direction needs. Nothing was signed and nothing was sent. Both came back `router` metis,
 * `gasless` false (the taker pays 5,000 lamports plus priority plus rent, so the reverse direction
 * needs SOL exactly as the forward one does), with a `route_v2` whose amount is 264600, and a
 * closing CloseAccount of a temporary wrapped-SOL account back to the taker. A larger amount (1 and
 * 20 TSLAx) also routed through Metis; no market maker quoted this direction, so there is no
 * reverse RFQ fixture, and the RFQ shape is covered by the forward one.
 *
 * Every negative case takes a real order, changes exactly one thing, and expects a refusal.
 */
class TransactionGuardReverseTest {

    private val json = HttpClientFactory.json
    private val attacker = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
    private val taker = "AC5RDfQFmDS1deWZos921JfqscXdByf8BKHs5ACWjtW2"
    private val amount = 264_600L

    private val reverse = listOf(
        "jupiter/order-tslax-usdc-default-metis.json",
        "jupiter/order-tslax-usdc-exclude-rfq-metis.json",
    )

    private val forward = listOf(
        "jupiter/order-usdc-tslax-5-gasless-metis.json" to PinnedAddresses.TREASURY,
        "jupiter/order-usdc-tslax-5-gasless-rfq.json" to "HzrEstnLfzsijhaD6z5frkSE2vWZEH5EUfn3bU9swo1f",
        "jupiter/order-usdc-tslax-5-metis-taker-pays.json" to "5tzFkiKscXHK5ZXCGbXZxdw7gTjjD1mBwuoFbhUvuAi9",
    )

    private fun order(path: String) = json.decodeFromString(SwapOrder.serializer(), Fixtures.read(path))

    private fun bytesOf(o: SwapOrder) = Base64.getDecoder().decode(o.transaction!!)

    private suspend fun checkOut(
        o: SwapOrder,
        bytes: ByteArray = bytesOf(o),
        wallet: String = taker,
        input: String = KnownMints.TSLAX,
        output: String = KnownMints.USDC,
        amount: Long = this.amount,
    ) = TransactionGuard.checkSwap(bytes, wallet, o, input, output, amount)

    private fun assertAllowed(v: Verdict) = assertEquals("expected Allow, got $v", Verdict.Allow, v)

    private fun assertRefused(v: Verdict, containing: String) {
        assertTrue("expected a refusal, got $v", v is Verdict.Refuse)
        val reason = (v as Verdict.Refuse).reason
        assertTrue("refusal '$reason' should mention '$containing'", containing in reason)
    }

    /** The index, within the message, of the one Jupiter swap instruction. */
    private fun WireMessage.jupiterIndex(): Int =
        instructions.indexOfFirst { keys[it.program] in setOf(KnownPrograms.JUPITER_AGGREGATOR_V6, KnownPrograms.JUPITER_RFQ) }

    /** Points account slot [slot] of the Jupiter instruction at [address], adding the key if needed. */
    private fun WireMessage.pointJupiterAccount(slot: Int, address: String): WireMessage {
        val (withKey, index) = withKey(address)
        val j = withKey.jupiterIndex()
        return withKey.mapInstruction(j) { ix -> ix.copy(accounts = ix.accounts.toMutableList().also { it[slot] = index }) }
    }

    // ---- The real answers ------------------------------------------------------------------

    @Test
    fun `both real TSLAx to USDC orders are allowed, exactly as Jupiter sent them`() = runTest {
        for (path in reverse) {
            val o = order(path)
            assertEquals(KnownMints.TSLAX, o.inputMint)
            assertEquals(KnownMints.USDC, o.outputMint)
            assertEquals("264600", o.inAmount)
            assertEquals(taker, o.taker)
            assertFalse("the reverse direction is not gasless on Metis: the taker needs SOL", o.gasless)
            assertEquals("metis", o.router)
            assertEquals(taker, o.signatureFeePayer)
            assertTrue("rent for the route's temporary accounts is the taker's", o.rentFeeLamports > 0L)
            assertAllowed(checkOut(o))
        }
    }

    @Test
    fun `the route spends from the taker's own TSLAx account and pays into its own USDC account`() = runTest {
        val tslaxAccount = TransactionGuard.ata(taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        val usdcAccount = TransactionGuard.ata(taker, KnownMints.USDC, KnownPrograms.TOKEN)
        // The largest-accounts read that found this taker named this exact token account.
        assertEquals("6NNBrevcrbkDGB7Yt6yDvN8wPFT15j8h6URaZPANWX8s", tslaxAccount)
        for (path in reverse) {
            val m = WireMessage.parseBase64(order(path).transaction!!)
            val route = m.instructions[m.jupiterIndex()]
            assertEquals(taker, m.keys[route.accounts[0]])
            assertEquals(tslaxAccount, m.keys[route.accounts[1]])
            assertEquals(usdcAccount, m.keys[route.accounts[2]])
            assertEquals("no second destination", KnownPrograms.JUPITER_AGGREGATOR_V6, m.keys[route.accounts[7]])
        }
    }

    /**
     * Why wrapped SOL and the route's own hops are among the mints a swap may open an account for
     * (mock judges' review, 2026-09-27): both real reverse orders open the taker's own wrapped-SOL
     * account, route through it, and close it back to the taker, and the default route
     * (TSLAx, then pool token Bjc..., then SOL, then USDC) also opens the taker's own account for
     * the pool token its route plan names. Input and output alone would refuse a real swap.
     */
    @Test
    fun `the real reverse creates open the taker's wrapped-SOL and route-hop accounts, and a third create is refused`() = runTest {
        val wsol = TransactionGuard.ata(taker, KnownMints.WSOL, KnownPrograms.TOKEN)
        val hopMint = "BjcRmwm8e25RgjkyaFE56fc7bxRgGPw96JUkXRJFEroT"
        val hop = TransactionGuard.ata(taker, hopMint, KnownPrograms.TOKEN)
        assertTrue(order(reverse[0]).routePlan.any { it.swapInfo?.outputMint == hopMint })
        for (path in reverse) {
            val m = WireMessage.parseBase64(order(path).transaction!!)
            val created = m.instructions.filter { m.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN }.map { m.keys[it.accounts[1]] }
            assertEquals(path, wsol, created.first())
            assertTrue(path, created.all { it == wsol || it == hop })
            val closed = m.instructions.filter { m.keys[it.program] == KnownPrograms.TOKEN && it.data.firstOrNull()?.toInt() == 9 }
            assertEquals(path, listOf(wsol), closed.map { m.keys[it.accounts[0]] })
        }
        val o = order(reverse[0])
        val m = WireMessage.parseTransaction(bytesOf(o))
        assertEquals(2, m.instructions.count { m.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN })
        val tslax = TransactionGuard.ata(taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        val third = m.plus(
            KnownPrograms.ASSOCIATED_TOKEN,
            listOf(taker, tslax, taker, KnownMints.TSLAX, KnownPrograms.SYSTEM, KnownPrograms.TOKEN_2022),
            byteArrayOf(1),
        )
        assertRefused(checkOut(o, third.transaction()), "more than 2")
        // The same bytes with a route plan that does not name the pool token: its account is no
        // longer one this swap may open.
        assertRefused(checkOut(o.copy(routePlan = emptyList())), "nor a hop")
    }

    // ---- The request against the order -------------------------------------------------------

    @Test
    fun `a reverse order checked as though it were the forward direction is refused`() = runTest {
        for (path in reverse) {
            val o = order(path)
            assertRefused(checkOut(o, input = KnownMints.USDC, output = KnownMints.TSLAX), "mint")
            assertRefused(checkOut(o, output = KnownMints.USDT), "output mint")
            assertRefused(checkOut(o.copy(inputMint = KnownMints.SKR)), "input mint")
        }
    }

    @Test
    fun `a different amount is refused, in the JSON and in the route instruction`() = runTest {
        for (path in reverse) {
            val o = order(path)
            assertRefused(checkOut(o, amount = 264_599L), "amount")
            assertRefused(checkOut(o.copy(inAmount = "999999999")), "amount")
            // The JSON says 264600 but the route itself spends the whole balance.
            val m = WireMessage.parseTransaction(bytesOf(o))
            val tampered = m.mapInstruction(m.jupiterIndex()) { ix ->
                ix.copy(data = ix.data.copyOf().also { d -> leData(992_371_814_810L).copyInto(d, 8) })
            }
            assertRefused(checkOut(o, tampered.transaction()), "Jupiter instruction amount")
        }
    }

    @Test
    fun `an order for another taker, or a wallet that is not its signer, is refused`() = runTest {
        for (path in reverse) {
            val o = order(path)
            assertRefused(checkOut(o.copy(taker = attacker)), "taker")
            assertRefused(checkOut(o.copy(taker = null), wallet = attacker), "signer")
        }
    }

    // ---- The route's own accounts ------------------------------------------------------------

    @Test
    fun `proceeds paid into anyone else's USDC account are refused`() = runTest {
        val attackerUsdc = TransactionGuard.ata(attacker, KnownMints.USDC, KnownPrograms.TOKEN)
        for (path in reverse) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertRefused(checkOut(o, m.pointJupiterAccount(2, attackerUsdc).transaction()), "pays into")
            // Nor the optional second destination, the slot route_v2 leaves empty.
            assertRefused(checkOut(o, m.pointJupiterAccount(7, attackerUsdc).transaction()), "second destination")
        }
    }

    @Test
    fun `spending from any account but the wallet's own for the requested xStock is refused`() = runTest {
        val takersUsdc = TransactionGuard.ata(taker, KnownMints.USDC, KnownPrograms.TOKEN)
        val attackerTslax = TransactionGuard.ata(attacker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        val takersNflx = TransactionGuard.ata(taker, "XsEH7wWfJJu2ZT3UCFeVfALnVA6CP5ur7Ee11KmzVpL", KnownPrograms.TOKEN_2022)
        for (path in reverse) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            // The wallet's own USDC, another holder's TSLAx, the wallet's own other xStock.
            listOf(takersUsdc, attackerTslax, takersNflx).forEach { account ->
                assertRefused(checkOut(o, m.pointJupiterAccount(1, account).transaction()), "spends from")
            }
            // And a route authorised by someone other than the wallet.
            assertRefused(checkOut(o, m.pointJupiterAccount(0, attacker).transaction()), "not authorised")
        }
    }

    @Test
    fun `a second Jupiter instruction, or one whose layout this app cannot read, is refused`() = runTest {
        for (path in reverse) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val route = m.instructions[m.jupiterIndex()]
            val twice = m.copy(instructions = m.instructions + route)
            assertRefused(checkOut(o, twice.transaction()), "not exactly one")
            val unknown = m.mapInstruction(m.jupiterIndex()) { ix ->
                ix.copy(data = ix.data.copyOf().also { d -> byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8).copyInto(d, 0) })
            }
            assertRefused(checkOut(o, unknown.transaction()), "not one whose accounts")
        }
    }

    // ---- Around the route: the forward direction's own checks, unchanged ---------------------

    @Test
    fun `authority handed away, a top-level transfer, lamports out, or an unknown program are refused`() = runTest {
        val walletTslax = TransactionGuard.ata(taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        for (path in reverse) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val approve = m.plus(KnownPrograms.TOKEN_2022, listOf(walletTslax, attacker, taker), leData(4.toByte(), Long.MAX_VALUE))
            assertRefused(checkOut(o, approve.transaction()), "Approve")
            val setAuthority = m.plus(KnownPrograms.TOKEN_2022, listOf(walletTslax, taker), leData(6.toByte(), 2.toByte(), 1.toByte(), WireMessage.key(attacker)))
            assertRefused(checkOut(o, setAuthority.transaction()), "SetAuthority")
            val closeAway = m.plus(KnownPrograms.TOKEN_2022, listOf(walletTslax, attacker, taker), byteArrayOf(9))
            assertRefused(checkOut(o, closeAway.transaction()), "closes")
            val drain = m.plus(
                KnownPrograms.TOKEN_2022,
                listOf(walletTslax, KnownMints.TSLAX, attacker, taker),
                leData(12.toByte(), 992_371_814_810L, 8.toByte()),
            )
            assertRefused(checkOut(o, drain.transaction()), "transfer")
            val sol = m.plus(KnownPrograms.SYSTEM, listOf(taker, attacker), leData(2, 50_000_000L))
            assertRefused(checkOut(o, sol.transaction()), "lamports")
            assertRefused(checkOut(o, m.plus(attacker, listOf(taker), byteArrayOf(1)).transaction()), "allowlist")
            val foreignAta = m.plus(
                KnownPrograms.ASSOCIATED_TOKEN,
                listOf(taker, attacker, attacker, KnownMints.USDC, KnownPrograms.SYSTEM, KnownPrograms.TOKEN),
                byteArrayOf(1),
            )
            assertRefused(checkOut(o, foreignAta.transaction()), "owner")
        }
    }

    @Test
    fun `the taker pays here, so a priority fee above the order's own is refused`() = runTest {
        for (path in reverse) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertEquals("the taker is the fee payer", taker, m.keys.first())
            val price = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3 }
            val dear = m.mapInstruction(price) { it.copy(data = leData(3.toByte(), 10_000_000_000L)) }
            assertRefused(checkOut(o, dear.transaction()), "priority fee")
        }
    }

    // ---- The forward direction gains the same account checks ---------------------------------

    @Test
    fun `forward orders paying the proceeds anywhere but the wallet's own xStock account are refused`() = runTest {
        val attackerTslax = TransactionGuard.ata(attacker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        for ((path, wallet) in forward) {
            val o = order(path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val destinationSlot = if (m.keys[m.instructions[m.jupiterIndex()].program] == KnownPrograms.JUPITER_RFQ) 4 else 2
            val redirected = m.pointJupiterAccount(destinationSlot, attackerTslax)
            assertRefused(
                TransactionGuard.checkSwap(redirected.transaction(), wallet, o, KnownMints.USDC, KnownMints.TSLAX, 5_000_000L),
                "pays into",
            )
            // The untouched order still passes, so the refusal above is the redirect and nothing else.
            assertAllowed(TransactionGuard.checkSwap(bytesOf(o), wallet, o, KnownMints.USDC, KnownMints.TSLAX, 5_000_000L))
        }
    }

    @Test
    fun `an RFQ fill naming another output mint in its own accounts is refused`() = runTest {
        val (path, wallet) = forward[1]
        val o = order(path)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val swappedMint = m.pointJupiterAccount(8, KnownMints.SKR)
        assertRefused(
            TransactionGuard.checkSwap(swappedMint.transaction(), wallet, o, KnownMints.USDC, KnownMints.TSLAX, 5_000_000L),
            "another output mint",
        )
    }

    // ---- The output, read from the bytes (mock judges' review, 2026-09-26) ----------------------

    private fun u64At(d: ByteArray, at: Int) =
        java.nio.ByteBuffer.wrap(d, at, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).long

    private fun WireMessage.patchJupiter(at: Int, bytes: ByteArray): WireMessage =
        mapInstruction(jupiterIndex()) { ix -> ix.copy(data = ix.data.copyOf().also { bytes.copyInto(it, at) }) }

    @Test
    fun `the reverse route_v2 bytes restate the JSON's floor, and the sheet shows that floor`() = runTest {
        for (path in reverse) {
            val o = order(path)
            val d = WireMessage.parseBase64(o.transaction!!).let { it.instructions[it.jupiterIndex()].data }
            val slippage = (d[24].toInt() and 0xff) or ((d[25].toInt() and 0xff) shl 8)
            assertEquals(o.slippageBps, slippage)
            assertEquals(o.otherAmountThreshold!!.toLong(), SwapFloor.of(u64At(d, 16), slippage))
            assertEquals(o.otherAmountThreshold!!.toLong(), SwapFloor.shownRaw(o))
        }
    }

    @Test
    fun `reverse - inflated slippage or a reduced quoted output in the bytes is refused`() = runTest {
        for (path in reverse) {
            val o = order(path)
            val m = WireMessage.parseBase64(o.transaction!!)
            val quoted = u64At(m.instructions[m.jupiterIndex()].data, 16)
            assertRefused(checkOut(o, m.patchJupiter(24, byteArrayOf(0x88.toByte(), 0x13)).transaction()), "slippage")
            assertRefused(checkOut(o, m.patchJupiter(16, leData(quoted - 1)).transaction()), "below the displayed floor")
            assertRefused(checkOut(o, m.patchJupiter(16, leData(quoted / 10)).transaction()), "below the displayed floor")
        }
    }
}
