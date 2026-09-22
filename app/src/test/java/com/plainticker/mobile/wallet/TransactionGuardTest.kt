package com.plainticker.mobile.wallet

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.PinnedAddresses
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.data.net.HttpClientFactory
import com.plainticker.mobile.data.plainticker.PassBuild
import com.plainticker.mobile.data.plainticker.VoteBuild
import com.plainticker.mobile.wallet.TransactionGuard.Verdict
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TransactionGuard] against real bytes (security audit, finding 2).
 *
 * The swap cases run on three REAL Jupiter /order answers captured 2026-09-23 (gasless Metis,
 * gasless RFQ, taker-pays Metis). The pass and vote cases run on server-shaped transactions built
 * to FinanceAnalyst's `lib/pass/build.ts` and `lib/vote/build.ts` (see each fixture's own note).
 * Every negative case takes one of those, changes exactly one thing, and expects a refusal: a guard
 * that let the real fixtures through but not the mutations is the whole point, and one that
 * refused the real fixtures would break every swap.
 */
class TransactionGuardTest {

    private val json = HttpClientFactory.json
    private val attacker = "8rUvvKhaNqDVdGjBpkB4XoTBrmMPfsVZJSLQPUHzZyEC"
    private val simPayer = "HzrEstnLfzsijhaD6z5frkSE2vWZEH5EUfn3bU9swo1f"
    private val codeHash = "b67f2e5c" + "0".repeat(56)

    private fun passBuild(path: String) = json.decodeFromString(PassBuild.serializer(), Fixtures.read(path))
    private fun voteBuild(path: String) = json.decodeFromString(VoteBuild.serializer(), Fixtures.read(path))
    private fun order(path: String) = json.decodeFromString(SwapOrder.serializer(), Fixtures.read(path))

    private fun assertAllowed(v: Verdict) = assertEquals("expected Allow, got $v", Verdict.Allow, v)

    private fun assertRefused(v: Verdict, containing: String? = null) {
        assertTrue("expected a refusal, got $v", v is Verdict.Refuse)
        if (containing != null) {
            assertTrue("refusal '${(v as Verdict.Refuse).reason}' should mention '$containing'", containing in v.reason)
        }
    }

    // ---- Addresses ------------------------------------------------------------------------

    @Test
    fun `the derived treasury token account is the pinned one, and matches what Jupiter derived itself`() = runTest {
        assertEquals(
            PinnedAddresses.TREASURY_USDC_ACCOUNT,
            TransactionGuard.ata(PinnedAddresses.TREASURY, KnownMints.USDC, KnownPrograms.TOKEN),
        )
        // Jupiter's own real order for the treasury names these accounts; both are its derivation.
        val m = WireMessage.parseBase64(order("jupiter/order-usdc-tslax-5-gasless-metis.json").transaction!!)
        assertTrue(PinnedAddresses.TREASURY_USDC_ACCOUNT in m.keys)
        val tslaxAccount = TransactionGuard.ata(PinnedAddresses.TREASURY, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        assertEquals("ESWu3UkJyRbNCMdHWSn72gEStPFGHBfB4hJoVeTBeaBf", tslaxAccount)
        assertTrue(tslaxAccount in m.keys)
        val rfq = WireMessage.parseBase64(order("jupiter/order-usdc-tslax-5-gasless-rfq.json").transaction!!)
        assertTrue(TransactionGuard.ata(simPayer, KnownMints.USDC, KnownPrograms.TOKEN) in rfq.keys)
    }

    // ---- The decoder ----------------------------------------------------------------------

    @Test
    fun `the library decoder agrees with the wire format on every real fixture`() {
        listOf(
            "jupiter/order-usdc-tslax-5-gasless-metis.json",
            "jupiter/order-usdc-tslax-5-gasless-rfq.json",
            "jupiter/order-usdc-tslax-5-metis-taker-pays.json",
        ).forEach { path ->
            val bytes = java.util.Base64.getDecoder().decode(order(path).transaction!!)
            val tx = TransactionGuard.decode(bytes)
            assertNotNull(path, tx)
            tx!!
            val wire = WireMessage.parseTransaction(bytes)
            assertEquals(wire.keys, tx.message.accounts.map { it.base58() })
            assertEquals(wire.instructions.size, tx.message.instructions.size)
            wire.instructions.zip(tx.message.instructions).forEach { (w, l) ->
                assertEquals(w.program, l.programIdIndex.toInt())
                assertEquals(w.accounts, l.accountIndices.map { it.toInt() and 0xff })
                assertArrayEquals(w.data, l.data)
            }
            // And the codec round-trips the real bytes exactly, so every mutation below starts from them.
            assertArrayEquals(bytes, wire.transaction())
        }
    }

    @Test
    fun `lengths past 127 are compact-u16 and decode, and trailing bytes are refused`() {
        val long = ServerBuilt.vote(simPayer, "JEF").let { m ->
            m.mapInstruction(1) { it.copy(data = ByteArray(200) { 'A'.code.toByte() }) }
        }
        val tx = TransactionGuard.decode(long.transaction())
        assertNotNull(tx)
        assertEquals(200, tx!!.message.instructions[1].data.size)
        assertEquals(null, TransactionGuard.decode(long.transaction() + byteArrayOf(0)))
        assertEquals(null, TransactionGuard.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `a legacy message is read too`() = runTest {
        val legacy = ServerBuilt.vote(simPayer, "JEF").copy(version = null)
        val build = voteBuild("plainticker/vote-build-jef.json")
        assertAllowed(TransactionGuard.checkVote(legacy.transaction(), simPayer, "JEF", build.summary))
    }

    @Test
    fun `a message version other than 0 is refused`() = runTest {
        val v1 = ServerBuilt.vote(simPayer, "JEF").copy(version = 1)
        assertRefused(TransactionGuard.checkVote(v1.transaction(), simPayer, "JEF", voteBuild("plainticker/vote-build-jef.json").summary))
    }

    // ---- Pass -----------------------------------------------------------------------------

    @Test
    fun `the server-shaped pass fixtures are allowed, with and without the token account creates`() = runTest {
        for (path in listOf("plainticker/pass-build-usdc.json", "plainticker/pass-build-usdc-create-atas.json")) {
            val build = passBuild(path)
            assertAllowed(TransactionGuard.checkPass(build.transactionBytes()!!, simPayer, build.summary, codeHash))
        }
    }

    @Test
    fun `the Kotlin server shape and the generated fixtures agree byte for byte`() = runTest {
        assertArrayEquals(
            passBuild("plainticker/pass-build-usdc.json").transactionBytes(),
            ServerBuilt.pass(simPayer, codeHash).transaction(),
        )
        assertArrayEquals(
            passBuild("plainticker/pass-build-usdc-create-atas.json").transactionBytes(),
            ServerBuilt.pass(simPayer, codeHash, createPayerAta = true, createTreasuryAta = true).transaction(),
        )
        assertArrayEquals(
            voteBuild("plainticker/vote-build-jef.json").transactionBytes(),
            ServerBuilt.vote(simPayer, "JEF").transaction(),
        )
    }

    // ---- The server's own builder output --------------------------------------------------

    /** The `inputs` block FinanceAnalyst's mobile-fixtures tests write beside each real build. */
    private fun inputsOf(path: String) =
        json.parseToJsonElement(Fixtures.read(path)).jsonObject.getValue("inputs").jsonObject
            .mapValues { it.value.jsonPrimitive.content }

    @Test
    fun `the real server pass builds are allowed, both token accounts existing and neither`() = runTest {
        for (path in listOf("server/pass-build-usdc.json", "server/pass-build-usdc-create-atas.json")) {
            val build = passBuild(path)
            val inputs = inputsOf(path)
            assertEquals("the fixture pays the pinned treasury", PinnedAddresses.TREASURY, inputs.getValue("treasury"))
            assertAllowed(
                TransactionGuard.checkPass(build.transactionBytes()!!, inputs.getValue("payer"), build.summary, inputs.getValue("codeHash")),
            )
        }
        // The first-payment path: the payer and the treasury both have no USDC account yet, so both
        // creates ship and the lamports shown carry both rents.
        val fresh = WireMessage.parseTransaction(passBuild("server/pass-build-usdc-create-atas.json").transactionBytes()!!)
        assertEquals(2, fresh.instructions.count { fresh.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN })
        assertEquals(5_000L + 2 * TransactionGuard.TOKEN_ACCOUNT_RENT_LAMPORTS, passBuild("server/pass-build-usdc-create-atas.json").summary.lamports)
    }

    @Test
    fun `the real server vote build is allowed`() = runTest {
        val path = "server/vote-build-jef.json"
        val build = voteBuild(path)
        val inputs = inputsOf(path)
        assertEquals(PinnedAddresses.VOTE_COLLECTOR, inputs.getValue("collector"))
        assertAllowed(TransactionGuard.checkVote(build.transactionBytes()!!, inputs.getValue("voter"), inputs.getValue("ticker"), build.summary))
    }

    @Test
    fun `the real server builds and this suite's assembled fixtures are the same bytes`() {
        for (name in listOf("pass-build-usdc.json", "pass-build-usdc-create-atas.json")) {
            assertArrayEquals(name, passBuild("server/$name").transactionBytes(), passBuild("plainticker/$name").transactionBytes())
        }
        assertArrayEquals(voteBuild("server/vote-build-jef.json").transactionBytes(), voteBuild("plainticker/vote-build-jef.json").transactionBytes())
    }

    private val pass = "plainticker/pass-build-usdc.json"
    private fun passWire() = WireMessage.parseTransaction(passBuild(pass).transactionBytes()!!)
    private suspend fun checkPass(m: WireMessage, wallet: String = simPayer, hash: String = codeHash) =
        TransactionGuard.checkPass(m.transaction(), wallet, passBuild(pass).summary, hash)

    @Test
    fun `pass - a fee payer that is not the connected wallet is refused`() = runTest {
        assertRefused(checkPass(passWire(), wallet = attacker), "fee payer")
    }

    @Test
    fun `pass - an extra Approve is refused`() = runTest {
        val m = passWire()
        val source = m.keys[1]
        assertRefused(checkPass(m.plus(KnownPrograms.TOKEN, listOf(source, attacker, simPayer), leData(4.toByte(), Long.MAX_VALUE))), "Approve")
    }

    @Test
    fun `pass - SetAuthority and CloseAccount are refused`() = runTest {
        val m = passWire()
        val source = m.keys[1]
        val setAuthority = leData(6.toByte(), 2.toByte(), 1.toByte(), WireMessage.key(attacker))
        assertRefused(checkPass(m.plus(KnownPrograms.TOKEN, listOf(source, simPayer), setAuthority)), "SetAuthority")
        assertRefused(checkPass(m.plus(KnownPrograms.TOKEN, listOf(source, attacker, simPayer), byteArrayOf(9))), "CloseAccount")
    }

    @Test
    fun `pass - a transfer to another destination is refused`() = runTest {
        val m = passWire()
        val t = m.indexOfProgram(KnownPrograms.TOKEN)
        val (withAttacker, a) = m.withKey(attacker)
        val redirected = withAttacker.mapInstruction(t) { it.copy(accounts = listOf(it.accounts[0], a, it.accounts[2])) }
        assertRefused(checkPass(redirected), "treasury")
    }

    @Test
    fun `pass - a transfer of another amount is refused`() = runTest {
        val m = passWire()
        val t = m.indexOfProgram(KnownPrograms.TOKEN)
        assertRefused(checkPass(m.mapInstruction(t) { it.copy(data = leData(3.toByte(), 1_000_000_000L)) }), "amount")
    }

    @Test
    fun `pass - a second transfer, even of the right amount to the right place, is refused`() = runTest {
        val m = passWire()
        val t = m.instructions[m.indexOfProgram(KnownPrograms.TOKEN)]
        assertRefused(checkPass(m.copy(instructions = m.instructions + t)), "exactly one")
    }

    @Test
    fun `pass - a summary for a swapped mint is refused`() = runTest {
        val build = passBuild(pass)
        val usdt = build.summary.copy(mint = "USDT")
        assertRefused(TransactionGuard.checkPass(build.transactionBytes()!!, simPayer, usdt, codeHash))
        val sol = build.summary.copy(mint = "SOL")
        assertRefused(TransactionGuard.checkPass(build.transactionBytes()!!, simPayer, sol, codeHash), "mint")
    }

    @Test
    fun `pass - a summary naming another treasury or destination is refused`() = runTest {
        val build = passBuild(pass)
        assertRefused(TransactionGuard.checkPass(build.transactionBytes()!!, simPayer, build.summary.copy(treasury = attacker), codeHash), "treasury")
        assertRefused(TransactionGuard.checkPass(build.transactionBytes()!!, simPayer, build.summary.copy(destination = attacker), codeHash), "destination")
    }

    @Test
    fun `pass - an unknown program is refused`() = runTest {
        assertRefused(checkPass(passWire().plus(attacker, listOf(simPayer), byteArrayOf(0))), "allowlist")
    }

    @Test
    fun `pass - a memo for another code is refused`() = runTest {
        assertRefused(checkPass(passWire(), hash = "f".repeat(64)), "memo")
    }

    @Test
    fun `pass - a System transfer that moves lamports is refused`() = runTest {
        val m = passWire()
        val s = m.indexOfProgram(KnownPrograms.SYSTEM)
        assertRefused(checkPass(m.mapInstruction(s) { it.copy(data = leData(2, 50_000_000L)) }), "reference")
    }

    @Test
    fun `pass - a priority fee larger than the lamports shown is refused`() = runTest {
        val m = passWire()
            .plus(KnownPrograms.COMPUTE_BUDGET, emptyList(), leData(2.toByte(), 200_000))
            .plus(KnownPrograms.COMPUTE_BUDGET, emptyList(), leData(3.toByte(), 1_000_000_000L))
        assertRefused(checkPass(m), "lamports")
    }

    @Test
    fun `pass - a second signer or an address table is refused`() = runTest {
        val m = passWire()
        assertRefused(checkPass(m.copy(numRequiredSignatures = 2)))
        assertRefused(checkPass(m.copy(lookups = listOf(WireMessage.WireLookup(attacker, listOf(0), emptyList())))), "address table")
    }

    @Test
    fun `pass - a token account created for a stranger is refused`() = runTest {
        val build = passBuild("plainticker/pass-build-usdc-create-atas.json")
        val m = WireMessage.parseTransaction(build.transactionBytes()!!)
        val (withAttacker, a) = m.withKey(attacker)
        val stranger = withAttacker.mapInstruction(1) { it.copy(accounts = it.accounts.toMutableList().also { acc -> acc[2] = a }) }
        assertRefused(TransactionGuard.checkPass(stranger.transaction(), simPayer, build.summary, codeHash), "owner")
    }

    // ---- Vote -----------------------------------------------------------------------------

    private val vote = "plainticker/vote-build-jef.json"
    private fun voteWire() = WireMessage.parseTransaction(voteBuild(vote).transactionBytes()!!)
    private suspend fun checkVote(m: WireMessage, wallet: String = simPayer, ticker: String = "JEF") =
        TransactionGuard.checkVote(m.transaction(), wallet, ticker, voteBuild(vote).summary)

    @Test
    fun `vote - the server-shaped fixture is allowed, and the ticker is compared as the server normalises it`() = runTest {
        assertAllowed(checkVote(voteWire()))
        assertAllowed(checkVote(voteWire(), ticker = " jef "))
    }

    @Test
    fun `vote - wrong payer, wrong ticker, wrong collector are each refused`() = runTest {
        assertRefused(checkVote(voteWire(), wallet = attacker), "fee payer")
        assertRefused(checkVote(voteWire(), ticker = "NFLX"), "ticker")
        assertRefused(checkVote(voteWire().replaceKey(PinnedAddresses.VOTE_COLLECTOR, attacker)), "collector")
        val build = voteBuild(vote)
        assertRefused(
            TransactionGuard.checkVote(build.transactionBytes()!!, simPayer, "JEF", build.summary.copy(collector = attacker)),
            "collector",
        )
    }

    @Test
    fun `vote - lamports on the transfer, an extra token transfer or an unknown program are refused`() = runTest {
        val m = voteWire()
        assertRefused(checkVote(m.mapInstruction(0) { it.copy(data = leData(2, 1_000_000_000L)) }), "0-lamport")
        assertRefused(checkVote(m.plus(KnownPrograms.TOKEN, listOf(attacker, attacker, simPayer), leData(3.toByte(), 1L))), "allowlist")
        assertRefused(checkVote(m.plus(attacker, emptyList(), byteArrayOf())), "allowlist")
        assertRefused(checkVote(m.copy(instructions = m.instructions + m.instructions[0])), "exactly one")
    }

    @Test
    fun `vote - ComputeBudget alone may sit beside it, within the lamports shown`() = runTest {
        val cheap = voteWire().plus(KnownPrograms.COMPUTE_BUDGET, emptyList(), leData(2.toByte(), 1))
        assertAllowed(checkVote(cheap))
        val dear = voteWire()
            .plus(KnownPrograms.COMPUTE_BUDGET, emptyList(), leData(2.toByte(), 1_400_000))
            .plus(KnownPrograms.COMPUTE_BUDGET, emptyList(), leData(3.toByte(), 1_000_000L))
        assertRefused(checkVote(dear), "lamports")
    }

    // ---- Swap -----------------------------------------------------------------------------

    private data class Case(val path: String, val taker: String)

    private val swaps = listOf(
        Case("jupiter/order-usdc-tslax-5-gasless-metis.json", PinnedAddresses.TREASURY),
        Case("jupiter/order-usdc-tslax-5-gasless-rfq.json", simPayer),
        Case("jupiter/order-usdc-tslax-5-metis-taker-pays.json", "5tzFkiKscXHK5ZXCGbXZxdw7gTjjD1mBwuoFbhUvuAi9"),
    )

    private fun checkSwap(
        order: SwapOrder,
        bytes: ByteArray,
        wallet: String,
        input: String = KnownMints.USDC,
        output: String = KnownMints.TSLAX,
        amount: Long = 5_000_000L,
    ) = TransactionGuard.checkSwap(bytes, wallet, order, input, output, amount)

    private fun bytesOf(o: SwapOrder) = java.util.Base64.getDecoder().decode(o.transaction!!)

    @Test
    fun `swap - all three real orders are allowed, gasless or not`() {
        for (c in swaps) {
            val o = order(c.path)
            assertAllowed(checkSwap(o, bytesOf(o), c.taker))
        }
        // The fixtures do cover both: a gasless payer at account 0, and the taker paying itself.
        assertTrue(order(swaps[0].path).gasless && order(swaps[1].path).gasless && !order(swaps[2].path).gasless)
    }

    @Test
    fun `swap - a wallet that is not a required signer is refused`() {
        for (c in swaps) {
            val o = order(c.path)
            assertRefused(checkSwap(o.copy(taker = null), bytesOf(o), attacker), "signer")
        }
    }

    @Test
    fun `swap - a swapped mint is refused`() {
        for (c in swaps) {
            val o = order(c.path)
            assertRefused(checkSwap(o, bytesOf(o), c.taker, input = KnownMints.TSLAX, output = KnownMints.USDC), "mint")
            assertRefused(checkSwap(o.copy(outputMint = KnownMints.SKR), bytesOf(o), c.taker), "output mint")
        }
    }

    @Test
    fun `swap - a wrong amount is refused, in the JSON and in the instruction bytes`() {
        for (c in swaps) {
            val o = order(c.path)
            assertRefused(checkSwap(o, bytesOf(o), c.taker, amount = 4_000_000L), "amount")
            // The JSON says 5 USDC but the Jupiter instruction itself spends more.
            val m = WireMessage.parseTransaction(bytesOf(o))
            val j = m.instructions.indexOfFirst { m.keys[it.program] in setOf(KnownPrograms.JUPITER_AGGREGATOR_V6, KnownPrograms.JUPITER_RFQ) }
            val tampered = m.mapInstruction(j) { ix ->
                ix.copy(data = ix.data.copyOf().also { d -> leData(500_000_000L).copyInto(d, 8) })
            }
            assertRefused(checkSwap(o, tampered.transaction(), c.taker), "Jupiter instruction amount")
        }
    }

    @Test
    fun `swap - an Approve or SetAuthority to anyone but the wallet is refused`() {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val approve = m.plus(KnownPrograms.TOKEN, listOf(attacker, attacker, c.taker), leData(4.toByte(), Long.MAX_VALUE))
            assertRefused(checkSwap(o, approve.transaction(), c.taker), "Approve")
            val approveChecked = m.plus(KnownPrograms.TOKEN_2022, listOf(attacker, KnownMints.TSLAX, attacker, c.taker), leData(13.toByte(), 1L, 8.toByte()))
            assertRefused(checkSwap(o, approveChecked.transaction(), c.taker), "Approve")
            val setAuthority = m.plus(KnownPrograms.TOKEN, listOf(attacker, c.taker), leData(6.toByte(), 2.toByte(), 1.toByte(), WireMessage.key(attacker)))
            assertRefused(checkSwap(o, setAuthority.transaction(), c.taker), "SetAuthority")
            val closeAway = m.plus(KnownPrograms.TOKEN, listOf(attacker, attacker, c.taker), byteArrayOf(9))
            assertRefused(checkSwap(o, closeAway.transaction(), c.taker), "closes")
            // To the wallet itself it is harmless and allowed.
            val toSelf = m.plus(KnownPrograms.TOKEN, listOf(attacker, c.taker, c.taker), leData(4.toByte(), 1L))
            assertAllowed(checkSwap(o, toSelf.transaction(), c.taker))
        }
    }

    @Test
    fun `swap - an unknown program, a top-level transfer, or lamports out of the wallet are refused`() {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertRefused(checkSwap(o, m.plus(attacker, listOf(c.taker), byteArrayOf(1)).transaction(), c.taker), "allowlist")
            assertRefused(checkSwap(o, m.plus(KnownPrograms.MEMO, emptyList(), byteArrayOf(1)).transaction(), c.taker), "allowlist")
            val drain = m.plus(KnownPrograms.TOKEN, listOf(attacker, attacker, c.taker), leData(3.toByte(), 5_000_000L))
            assertRefused(checkSwap(o, drain.transaction(), c.taker), "transfer")
            val sol = m.plus(KnownPrograms.SYSTEM, listOf(c.taker, attacker), leData(2, 50_000_000L))
            assertRefused(checkSwap(o, sol.transaction(), c.taker), "lamports")
            val foreignAta = m.plus(KnownPrograms.ASSOCIATED_TOKEN, listOf(c.taker, attacker, attacker, KnownMints.USDC, KnownPrograms.SYSTEM, KnownPrograms.TOKEN), byteArrayOf(1))
            assertRefused(checkSwap(o, foreignAta.transaction(), c.taker), "owner")
        }
    }

    @Test
    fun `swap - an order built for another taker is refused`() {
        val c = swaps[2]
        val o = order(c.path)
        assertRefused(checkSwap(o.copy(taker = attacker), bytesOf(o), c.taker), "taker")
    }

    @Test
    fun `swap - when the wallet pays, a priority fee above the order's own is refused`() {
        val c = swaps[2]
        val o = order(c.path)
        assertEquals("the real order's priority fee is the one its compute budget computes", o.prioritizationFeeLamports, 395L)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val price = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3 }
        val dear = m.mapInstruction(price) { it.copy(data = leData(3.toByte(), 10_000_000_000L)) }
        assertRefused(checkSwap(o, dear.transaction(), c.taker), "priority fee")
    }

    @Test
    fun `swap - a transaction with no Jupiter instruction is refused`() {
        val c = swaps[2]
        val o = order(c.path)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val onlyBudget = m.copy(instructions = m.instructions.filter { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET })
        assertRefused(checkSwap(o, onlyBudget.transaction(), c.taker), "no Jupiter")
    }
}
