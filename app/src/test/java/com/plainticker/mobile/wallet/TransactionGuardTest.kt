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

    private suspend fun checkSwap(
        order: SwapOrder,
        bytes: ByteArray,
        wallet: String,
        input: String = KnownMints.USDC,
        output: String = KnownMints.TSLAX,
        amount: Long = 5_000_000L,
    ) = TransactionGuard.checkSwap(bytes, wallet, order, input, output, amount)

    private fun bytesOf(o: SwapOrder) = java.util.Base64.getDecoder().decode(o.transaction!!)

    @Test
    fun `swap - all three real orders are allowed, gasless or not`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            assertAllowed(checkSwap(o, bytesOf(o), c.taker))
        }
        // The fixtures do cover both: a gasless payer at account 0, and the taker paying itself.
        assertTrue(order(swaps[0].path).gasless && order(swaps[1].path).gasless && !order(swaps[2].path).gasless)
    }

    @Test
    fun `swap - a wallet that is not a required signer is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            assertRefused(checkSwap(o.copy(taker = null), bytesOf(o), attacker), "signer")
        }
    }

    @Test
    fun `swap - a swapped mint is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            assertRefused(checkSwap(o, bytesOf(o), c.taker, input = KnownMints.TSLAX, output = KnownMints.USDC), "mint")
            assertRefused(checkSwap(o.copy(outputMint = KnownMints.SKR), bytesOf(o), c.taker), "output mint")
        }
    }

    @Test
    fun `swap - a wrong amount is refused, in the JSON and in the instruction bytes`() = runTest {
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
    fun `swap - an Approve or SetAuthority to anyone but the wallet is refused`() = runTest {
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

    /**
     * ApproveChecked is [source, mint, delegate, owner], so its delegate is account 2, where Approve
     * keeps it at account 1 (security audit, 2026-09-26). Reading account 1 for both compared the
     * mint with the wallet: an approve to the wallet itself was refused, and one that put the wallet
     * in the mint slot passed whoever the delegate was. Still strict: only the wallet may be named.
     */
    @Test
    fun `swap - ApproveChecked is judged by its delegate, allowed to the wallet and refused to anyone else`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val source = TransactionGuard.ata(c.taker, KnownMints.USDC, KnownPrograms.TOKEN)
            fun approveChecked(accounts: List<String>) =
                m.plus(KnownPrograms.TOKEN, accounts, leData(13.toByte(), 1L, 6.toByte())).transaction()

            assertAllowed(checkSwap(o, approveChecked(listOf(source, KnownMints.USDC, c.taker, c.taker)), c.taker))
            assertRefused(checkSwap(o, approveChecked(listOf(source, KnownMints.USDC, attacker, c.taker)), c.taker), "ApproveChecked")
            assertRefused(checkSwap(o, approveChecked(listOf(source, c.taker, attacker, c.taker)), c.taker), "ApproveChecked")
            // A truncated account list names no delegate at all, which is not the wallet.
            assertRefused(checkSwap(o, approveChecked(listOf(source, KnownMints.USDC)), c.taker), "ApproveChecked")
        }
    }

    @Test
    fun `swap - an unknown program, a top-level transfer, or lamports out of the wallet are refused`() = runTest {
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
    fun `swap - an order built for another taker is refused`() = runTest {
        val c = swaps[2]
        val o = order(c.path)
        assertRefused(checkSwap(o.copy(taker = attacker), bytesOf(o), c.taker), "taker")
    }

    @Test
    fun `swap - when the wallet pays, a priority fee above the order's own is refused`() = runTest {
        val c = swaps[2]
        val o = order(c.path)
        assertEquals("the real order's priority fee is the one its compute budget computes", o.prioritizationFeeLamports, 395L)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val price = m.instructions.indexOfFirst { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET && it.data[0].toInt() == 3 }
        val dear = m.mapInstruction(price) { it.copy(data = leData(3.toByte(), 10_000_000_000L)) }
        assertRefused(checkSwap(o, dear.transaction(), c.taker), "priority fee")
    }

    @Test
    fun `swap - a transaction with no Jupiter instruction is refused`() = runTest {
        val c = swaps[2]
        val o = order(c.path)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val onlyBudget = m.copy(instructions = m.instructions.filter { m.keys[it.program] == KnownPrograms.COMPUTE_BUDGET })
        assertRefused(checkSwap(o, onlyBudget.transaction(), c.taker), "no Jupiter")
    }

    // ---- Swap output, read from the bytes (judges' review, 2026-09-26) --------------------

    private val jupiterPrograms = setOf(KnownPrograms.JUPITER_AGGREGATOR_V6, KnownPrograms.JUPITER_RFQ)

    private fun WireMessage.jupiterIndex(): Int = instructions.indexOfFirst { keys[it.program] in jupiterPrograms }

    private fun WireMessage.jupiterData(): ByteArray = instructions[jupiterIndex()].data

    /** The Jupiter instruction's data with [bytes] written over it at [at]. */
    private fun WireMessage.patchJupiter(at: Int, bytes: ByteArray): WireMessage =
        mapInstruction(jupiterIndex()) { ix -> ix.copy(data = ix.data.copyOf().also { bytes.copyInto(it, at) }) }

    private fun u16(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())

    private fun u64At(d: ByteArray, at: Int) = java.nio.ByteBuffer.wrap(d, at, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).long

    private fun u16At(d: ByteArray, at: Int) = (d[at].toInt() and 0xff) or ((d[at + 1].toInt() and 0xff) shl 8)

    private val metisSwaps get() = listOf(swaps[0], swaps[2])
    private val rfqSwap get() = swaps[1]

    /**
     * The layouts, pinned against the real bytes: route_v2 is in_amount u64 @8, quoted_out_amount
     * u64 @16, slippage_bps u16 @24; fill is input_amount u64 @8, output_amount u64 @16, expire_at
     * i64 @24. Read that way, every real order's own bytes restate its own JSON exactly.
     */
    @Test
    fun `swap - read at the published layouts, the real bytes restate the JSON's output terms`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val d = WireMessage.parseTransaction(bytesOf(o)).jupiterData()
            assertEquals(o.inAmountRaw, u64At(d, 8))
            val quoted = u64At(d, 16)
            val slippage = u16At(d, 24)
            assertEquals(c.path, o.slippageBps, slippage)
            assertEquals(c.path, o.otherAmountThreshold!!.toLong(), SwapFloor.of(quoted, slippage))
            assertEquals("the sheet shows the same floor", SwapFloor.shownRaw(o), o.otherAmountThreshold!!.toLong())
        }
        val o = order(rfqSwap.path)
        val d = WireMessage.parseTransaction(bytesOf(o)).jupiterData()
        assertEquals(o.inAmountRaw, u64At(d, 8))
        assertEquals(o.outAmountRaw, u64At(d, 16))
        assertEquals(o.expireAt, u64At(d, 24))
    }

    @Test
    fun `swap - route_v2 bytes with a slippage above the order's are refused`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertRefused(checkSwap(o, m.patchJupiter(24, u16(5_000)).transaction(), c.taker), "slippage")
            assertRefused(checkSwap(o, m.patchJupiter(24, u16(o.slippageBps + 1)).transaction(), c.taker), "slippage")
            assertRefused(checkSwap(o, m.patchJupiter(24, u16(65_535)).transaction(), c.taker), "slippage")
        }
    }

    @Test
    fun `swap - route_v2 bytes with a reduced quoted output are refused, by a single base unit`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val quoted = u64At(m.jupiterData(), 16)
            assertRefused(checkSwap(o, m.patchJupiter(16, leData(quoted - 1)).transaction(), c.taker), "below the displayed floor")
            assertRefused(checkSwap(o, m.patchJupiter(16, leData(quoted / 2)).transaction(), c.taker), "below the displayed floor")
            assertRefused(checkSwap(o, m.patchJupiter(16, leData(0L)).transaction(), c.taker), "below the displayed floor")
        }
    }

    @Test
    fun `swap - route_v2 bytes that promise more than the sheet, a tighter slippage or a better quote, are allowed`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val quoted = u64At(m.jupiterData(), 16)
            assertAllowed(checkSwap(o, m.patchJupiter(24, u16(50)).transaction(), c.taker))
            assertAllowed(checkSwap(o, m.patchJupiter(16, leData(quoted + 1_000)).transaction(), c.taker))
        }
    }

    @Test
    fun `swap - a displayed floor raised above what the bytes accept is refused`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val raised = o.copy(otherAmountThreshold = (o.otherAmountThreshold!!.toLong() + 1).toString())
            assertRefused(checkSwap(raised, bytesOf(o), c.taker), "below the displayed floor")
            // With no threshold in the JSON, the sheet's floor is the estimate less the order's
            // slippage, and the bytes are held to that instead.
            val noThreshold = o.copy(otherAmountThreshold = null, outAmount = (o.outAmountRaw * 2).toString())
            assertRefused(checkSwap(noThreshold, bytesOf(o), c.taker), "below the displayed floor")
        }
    }

    @Test
    fun `swap - an RFQ fill paying out less than the sheet displays is refused`() = runTest {
        val c = rfqSwap
        val o = order(c.path)
        val m = WireMessage.parseTransaction(bytesOf(o))
        assertRefused(checkSwap(o, m.patchJupiter(16, leData(o.outAmountRaw - 1)).transaction(), c.taker), "fill output")
        assertRefused(checkSwap(o, m.patchJupiter(16, leData(1L)).transaction(), c.taker), "fill output")
        assertRefused(checkSwap(o, m.patchJupiter(16, leData(-1L)).transaction(), c.taker), "fill output")
        // The JSON claiming more than the maker's bytes pay is the same lie told the other way.
        assertRefused(checkSwap(o.copy(outAmount = (o.outAmountRaw + 1).toString()), bytesOf(o), c.taker), "fill output")
        // Paying more than displayed costs the reader nothing.
        assertAllowed(checkSwap(o, m.patchJupiter(16, leData(o.outAmountRaw + 1)).transaction(), c.taker))
    }

    @Test
    fun `swap - a Jupiter instruction cut short of its output terms is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val cut = m.mapInstruction(m.jupiterIndex()) { ix -> ix.copy(data = ix.data.copyOf(20)) }
            assertRefused(checkSwap(o, cut.transaction(), c.taker), "too short")
        }
    }

    // ---- System instructions, by tag (judges' review, 2026-09-26) -------------------------

    private val nonceAccount = "9fX7DHqX5nFzV1aNAKZsfFnbHRyBn2VgXNiMGrbKV1CM"
    private val recentBlockhashes = "SysvarRecentB1ockHashes11111111111111111111"
    private val rentSysvar = "SysvarRent111111111111111111111111111111111"

    /** The RFQ fixture's market maker: a fee payer that is none of the three takers. */
    private val maker = "2Cq2RNFFxxPXL7teNQAji1beA2vFbBDYW5BGPBFvoN9m"

    @Test
    fun `swap - a System instruction naming the wallet as nonce authority or seed base is refused, whatever slot 0 is`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            // WithdrawNonceAccount: [nonce, to, recent blockhashes, rent, authority] + u64.
            val withdraw = m.plus(KnownPrograms.SYSTEM, listOf(nonceAccount, attacker, recentBlockhashes, rentSysvar, c.taker), leData(5, 1_000_000L))
            assertRefused(checkSwap(o, withdraw.transaction(), c.taker), "naming the wallet")
            // AuthorizeNonceAccount: [nonce, authority] + new authority.
            val authorize = m.plus(KnownPrograms.SYSTEM, listOf(nonceAccount, c.taker), leData(7, WireMessage.key(attacker)))
            assertRefused(checkSwap(o, authorize.transaction(), c.taker), "naming the wallet")
            // TransferWithSeed: [from, base, to] + u64 lamports + seed + owner.
            val seedBytes = "x".encodeToByteArray()
            val withSeed = m.plus(
                KnownPrograms.SYSTEM,
                listOf(attacker, c.taker, attacker),
                leData(11, 1_000_000L, seedBytes.size.toLong(), seedBytes, WireMessage.key(KnownPrograms.SYSTEM)),
            )
            assertRefused(checkSwap(o, withSeed.transaction(), c.taker), "naming the wallet")
        }
    }

    @Test
    fun `swap - CreateAccount funded by the wallet is refused, even for its own token account`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val own = TransactionGuard.ata(c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
            val create = m.plus(
                KnownPrograms.SYSTEM,
                listOf(c.taker, own),
                leData(0, TransactionGuard.TOKEN_ACCOUNT_RENT_LAMPORTS, 165L, WireMessage.key(KnownPrograms.TOKEN_2022)),
            )
            assertRefused(checkSwap(o, create.transaction(), c.taker), "naming the wallet")
            val withSeed = m.plus(
                KnownPrograms.SYSTEM,
                listOf(c.taker, own, c.taker),
                leData(3, WireMessage.key(c.taker), 1L, "x".encodeToByteArray(), TransactionGuard.TOKEN_ACCOUNT_RENT_LAMPORTS, 165L, WireMessage.key(KnownPrograms.TOKEN_2022)),
            )
            assertRefused(checkSwap(o, withSeed.transaction(), c.taker), "naming the wallet")
        }
    }

    @Test
    fun `swap - a 0-lamport transfer from the wallet is allowed, one that names it only as the recipient is not`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertAllowed(checkSwap(o, m.plus(KnownPrograms.SYSTEM, listOf(c.taker, attacker), leData(2, 0L)).transaction(), c.taker))
            assertRefused(checkSwap(o, m.plus(KnownPrograms.SYSTEM, listOf(attacker, c.taker), leData(2, 0L)).transaction(), c.taker), "not as the source")
            assertRefused(checkSwap(o, m.plus(KnownPrograms.SYSTEM, listOf(c.taker), leData(2, 0L)).transaction(), c.taker), "malformed")
            assertRefused(checkSwap(o, m.plus(KnownPrograms.SYSTEM, listOf(c.taker, attacker), byteArrayOf(2)).transaction(), c.taker))
        }
    }

    @Test
    fun `swap - a System instruction that does not name the wallet passes only as a create or a transfer`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertAllowed(checkSwap(o, m.plus(KnownPrograms.SYSTEM, listOf(maker, attacker), leData(2, 5_000L)).transaction(), c.taker))
            val payerCreates = m.plus(KnownPrograms.SYSTEM, listOf(maker, attacker), leData(0, 890_880L, 0L, WireMessage.key(KnownPrograms.SYSTEM)))
            assertAllowed(checkSwap(o, payerCreates.transaction(), c.taker))
            // AdvanceNonceAccount would make the order durable, landable long after it was shown.
            val advance = m.plus(KnownPrograms.SYSTEM, listOf(nonceAccount, recentBlockhashes, maker), leData(4))
            assertRefused(checkSwap(o, advance.transaction(), c.taker), "System instruction 4")
            val withdraw = m.plus(KnownPrograms.SYSTEM, listOf(nonceAccount, attacker, recentBlockhashes, rentSysvar, maker), leData(5, 1L))
            assertRefused(checkSwap(o, withdraw.transaction(), c.taker), "System instruction 5")
            assertRefused(checkSwap(o, m.plus(KnownPrograms.SYSTEM, listOf(maker), byteArrayOf(1, 0)).transaction(), c.taker), "without a tag")
        }
    }

    // ---- What the wallet hands back (judges' review, 2026-09-26) ---------------------------

    /** [tx] with a non-zero signature written into signature slot [slot]. */
    private fun signedIn(tx: ByteArray, slot: Int): ByteArray =
        tx.copyOf().also { java.util.Arrays.fill(it, 1 + 64 * slot, 1 + 64 * (slot + 1), 0x5A) }

    @Test
    fun `swap - the wallet's answer matches only when it is the checked message with the wallet's own signature`() {
        for (c in swaps) {
            val o = order(c.path)
            val unsigned = bytesOf(o)
            val m = WireMessage.parseTransaction(unsigned)
            val slot = m.keys.take(m.numRequiredSignatures).indexOf(c.taker)
            assertTrue(c.path, slot >= 0)
            assertTrue(c.path, TransactionGuard.signedMatches(unsigned, signedIn(unsigned, slot), c.taker))
            // Handed back unsigned, or signed only in another signer's slot, is not this wallet's signature.
            assertEquals(c.path, false, TransactionGuard.signedMatches(unsigned, unsigned, c.taker))
            if (m.numRequiredSignatures > 1) {
                val other = if (slot == 0) 1 else 0
                assertEquals(c.path, false, TransactionGuard.signedMatches(unsigned, signedIn(unsigned, other), c.taker))
            }
            // Any change to the message itself, one byte of the blockhash or an extra instruction.
            val otherBlockhash = m.copy(blockhash = m.blockhash.copyOf().also { it[0] = (it[0] + 1).toByte() }).transaction()
            assertEquals(c.path, false, TransactionGuard.signedMatches(unsigned, signedIn(otherBlockhash, slot), c.taker))
            val extra = m.plus(KnownPrograms.COMPUTE_BUDGET, emptyList(), leData(3.toByte(), 1L)).transaction()
            assertEquals(c.path, false, TransactionGuard.signedMatches(unsigned, signedIn(extra, slot), c.taker))
            // Garbage, and the right bytes checked against the wrong wallet.
            assertEquals(false, TransactionGuard.signedMatches(unsigned, "SIGNED".encodeToByteArray(), c.taker))
            assertEquals(false, TransactionGuard.signedMatches(unsigned, signedIn(unsigned, slot), attacker))
        }
    }

    // ---- Token account creates, fees, slippage and the pass price (judges' review, 2026-09-27) ----

    private class Create(val accounts: List<String>, val data: ByteArray)

    private fun create(funder: String, account: String, owner: String, mint: String, tokenProgram: String, data: ByteArray = byteArrayOf(1)) =
        Create(listOf(funder, account, owner, mint, KnownPrograms.SYSTEM, tokenProgram), data)

    private fun WireMessage.plusCreate(c: Create) = plus(KnownPrograms.ASSOCIATED_TOKEN, c.accounts, c.data)

    private fun assertWhy(v: Verdict, why: TransactionGuard.Why) {
        assertTrue("expected a refusal, got $v", v is Verdict.Refuse)
        assertEquals((v as Verdict.Refuse).reason, why, v.why)
    }

    @Test
    fun `swap - every real order's own creates open the wallet's account for one side, funded by the wallet or the fee payer`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val creates = m.instructions.filter { m.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN }
            assertTrue(c.path, creates.size <= TransactionGuard.MAX_SWAP_ACCOUNT_CREATES)
            for (ix in creates) {
                assertEquals(c.path, c.taker, m.keys[ix.accounts[2]])
                assertTrue(c.path, m.keys[ix.accounts[0]] == c.taker || ix.accounts[0] == 0)
                assertEquals(c.path, TransactionGuard.ata(c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022), m.keys[ix.accounts[1]])
            }
        }
        // The gasless order is funded by Jupiter's gas payer at account 0, not by the taker.
        val gasless = WireMessage.parseTransaction(bytesOf(order(swaps[0].path)))
        val create = gasless.instructions.first { gasless.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN }
        assertEquals(0, create.accounts[0])
        assertTrue(gasless.keys[0] != swaps[0].taker)
    }

    @Test
    fun `swap - a create of the wallet's own account for a mint that is neither side is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val own = TransactionGuard.ata(c.taker, KnownMints.SKR, KnownPrograms.TOKEN)
            val v = checkSwap(o, m.plusCreate(create(c.taker, own, c.taker, KnownMints.SKR, KnownPrograms.TOKEN)).transaction(), c.taker)
            assertRefused(v, "neither side")
            assertWhy(v, TransactionGuard.Why.STRANGE_TOKEN_ACCOUNT)
            // The same account with the mint slot naming a side of the swap: the account still gives it away.
            assertRefused(checkSwap(o, m.plusCreate(create(c.taker, own, c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN)).transaction(), c.taker), "neither side")
        }
    }

    @Test
    fun `swap - a create whose static mint slot disagrees with the account it opens is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val usdcAccount = TransactionGuard.ata(c.taker, KnownMints.USDC, KnownPrograms.TOKEN)
            val v = checkSwap(o, m.plusCreate(create(c.taker, usdcAccount, c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN)).transaction(), c.taker)
            assertRefused(v, "another mint")
        }
    }

    @Test
    fun `swap - a create funded by a stranger, or by the wallet for a stranger, is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val own = TransactionGuard.ata(c.taker, KnownMints.USDC, KnownPrograms.TOKEN)
            assertRefused(checkSwap(o, m.plusCreate(create(attacker, own, c.taker, KnownMints.USDC, KnownPrograms.TOKEN)).transaction(), c.taker), "funded by neither")
            val theirs = TransactionGuard.ata(attacker, KnownMints.USDC, KnownPrograms.TOKEN)
            assertRefused(checkSwap(o, m.plusCreate(create(c.taker, theirs, attacker, KnownMints.USDC, KnownPrograms.TOKEN)).transaction(), c.taker), "owner")
        }
    }

    @Test
    fun `swap - an associated-token instruction that is not a plain create, or names the wrong programs, is refused`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val own = TransactionGuard.ata(c.taker, KnownMints.USDC, KnownPrograms.TOKEN)
            assertRefused(checkSwap(o, m.plusCreate(create(c.taker, own, c.taker, KnownMints.USDC, KnownPrograms.TOKEN, byteArrayOf(1, 0))).transaction(), c.taker), "not a create")
            // RecoverNested (2) moves tokens out of a nested account: never a create.
            assertRefused(checkSwap(o, m.plusCreate(create(c.taker, own, c.taker, KnownMints.USDC, KnownPrograms.TOKEN, byteArrayOf(2))).transaction(), c.taker), "not a create")
            assertRefused(checkSwap(o, m.plusCreate(create(c.taker, own, c.taker, KnownMints.USDC, attacker)).transaction(), c.taker), "wrong programs")
            val short = m.plus(KnownPrograms.ASSOCIATED_TOKEN, listOf(c.taker, own, c.taker), byteArrayOf(1))
            assertRefused(checkSwap(o, short.transaction(), c.taker), "malformed")
        }
    }

    @Test
    fun `swap - the wallet's own account for the output or wrapped SOL may be created, up to two creates in all`() = runTest {
        val c = swaps[2] // taker pays, and the real order opens no account at all
        // Declaring the rent of two accounts, paid by the taker, as the real Swap to USDC does.
        val o = order(c.path).copy(rentFeeLamports = 2_976_880L, rentFeePayer = c.taker)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val tslax = TransactionGuard.ata(c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        val wsol = TransactionGuard.ata(c.taker, KnownMints.WSOL, KnownPrograms.TOKEN)
        val usdc = TransactionGuard.ata(c.taker, KnownMints.USDC, KnownPrograms.TOKEN)
        val one = m.plusCreate(create(c.taker, tslax, c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022))
        assertAllowed(checkSwap(o, one.transaction(), c.taker))
        val two = one.plusCreate(create(c.taker, wsol, c.taker, KnownMints.WSOL, KnownPrograms.TOKEN, byteArrayOf()))
        assertAllowed(checkSwap(o, two.transaction(), c.taker))
        val three = two.plusCreate(create(c.taker, usdc, c.taker, KnownMints.USDC, KnownPrograms.TOKEN))
        assertRefused(checkSwap(o, three.transaction(), c.taker), "more than 2")
    }

    @Test
    fun `swap - every real order declares at least the rent of each account the wallet funds`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val walletFunded = m.instructions.count { m.keys[it.program] == KnownPrograms.ASSOCIATED_TOKEN && m.keys[it.accounts[0]] == c.taker }
            val declared = if ((o.rentFeePayer ?: m.keys[0]) == c.taker) o.rentFeeLamports else 0L
            assertTrue(c.path, declared >= walletFunded * TransactionGuard.MIN_DECLARED_RENT_PER_ACCOUNT_LAMPORTS)
            assertAllowed(checkSwap(o, bytesOf(o), c.taker))
        }
    }

    @Test
    fun `swap - a wallet-funded create the order's rent does not cover is refused`() = runTest {
        val c = swaps[2] // taker pays; the real order declares no rent and opens nothing
        val o = order(c.path)
        assertEquals(0L, o.rentFeeLamports)
        val m = WireMessage.parseTransaction(bytesOf(o))
        val tslax = TransactionGuard.ata(c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022)
        val wsol = TransactionGuard.ata(c.taker, KnownMints.WSOL, KnownPrograms.TOKEN)
        val one = m.plusCreate(create(c.taker, tslax, c.taker, KnownMints.TSLAX, KnownPrograms.TOKEN_2022))
        val undeclared = checkSwap(o, one.transaction(), c.taker)
        assertRefused(undeclared, "declares 0 lamports")
        assertWhy(undeclared, TransactionGuard.Why.COSTS_MORE_THAN_SHOWN)
        // Rent declared, but for somebody else to pay: nothing is declared for the wallet.
        assertRefused(checkSwap(o.copy(rentFeeLamports = 1_488_440L, rentFeePayer = attacker), one.transaction(), c.taker), "declares 0 lamports")
        // One account declared, two opened from the wallet.
        val oneDeclared = o.copy(rentFeeLamports = 1_488_440L, rentFeePayer = c.taker)
        assertAllowed(checkSwap(oneDeclared, one.transaction(), c.taker))
        val two = one.plusCreate(create(c.taker, wsol, c.taker, KnownMints.WSOL, KnownPrograms.TOKEN))
        assertRefused(checkSwap(oneDeclared, two.transaction(), c.taker), "funds 2 token account")
        // A create the fee payer funds costs the wallet nothing, so it needs no declared rent.
        val gasless = swaps[0]
        val g = order(gasless.path)
        assertAllowed(checkSwap(g.copy(rentFeeLamports = 0L), bytesOf(g), gasless.taker))
    }

    @Test
    fun `swap - route_v2 platform fee is capped at 400 bps even when the order says more`() = runTest {
        assertEquals(400, TransactionGuard.MAX_PLATFORM_FEE_BPS)
        for (c in metisSwaps) {
            val o = order(c.path).copy(feeBps = 1_000)
            val m = WireMessage.parseTransaction(bytesOf(o))
            val over = checkSwap(o, m.patchJupiter(26, u16(401)).transaction(), c.taker)
            assertRefused(over, "ceiling of 400")
            assertWhy(over, TransactionGuard.Why.COSTS_MORE_THAN_SHOWN)
            assertAllowed(checkSwap(o, m.patchJupiter(26, u16(400)).transaction(), c.taker))
        }
        // The widest real fee, the gasless order's 378, sits under it.
        assertTrue(order(swaps[0].path).feeBps <= TransactionGuard.MAX_PLATFORM_FEE_BPS)
    }

    @Test
    fun `swap - route_v2 bytes charging a platform fee above the order's feeBps are refused`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertEquals("the real bytes state the order's own fee", o.feeBps, u16At(m.jupiterData(), 26))
            val dearer = checkSwap(o, m.patchJupiter(26, u16(o.feeBps + 1)).transaction(), c.taker)
            assertRefused(dearer, "platform fee")
            assertWhy(dearer, TransactionGuard.Why.COSTS_MORE_THAN_SHOWN)
            assertRefused(checkSwap(o.copy(feeBps = 0), bytesOf(o), c.taker), "platform fee")
            assertAllowed(checkSwap(o, m.patchJupiter(26, u16(0)).transaction(), c.taker))
        }
        // The gasless order folds the gas into the fee: 378 in the bytes and in feeBps, 10 in platformFee.
        val gasless = order(swaps[0].path)
        assertEquals(378, gasless.feeBps)
        assertEquals(10, gasless.platformFeeBps)
    }

    @Test
    fun `swap - route_v2 positive slippage share is allowed up to the whole surplus and refused past it`() = runTest {
        for (c in metisSwaps) {
            val o = order(c.path)
            val m = WireMessage.parseTransaction(bytesOf(o))
            assertEquals(0, u16At(m.jupiterData(), 28))
            assertAllowed(checkSwap(o, m.patchJupiter(28, u16(10_000)).transaction(), c.taker))
            assertRefused(checkSwap(o, m.patchJupiter(28, u16(10_001)).transaction(), c.taker), "positive slippage")
        }
    }

    @Test
    fun `swap - an order allowing more than the slippage ceiling is refused, whatever the bytes say`() = runTest {
        for (c in swaps) {
            val o = order(c.path)
            val wide = checkSwap(o.copy(slippageBps = TransactionGuard.MAX_SLIPPAGE_BPS + 1), bytesOf(o), c.taker)
            assertRefused(wide, "ceiling")
            assertWhy(wide, TransactionGuard.Why.SLIPPAGE_TOO_WIDE)
            assertAllowed(checkSwap(o.copy(slippageBps = TransactionGuard.MAX_SLIPPAGE_BPS), bytesOf(o), c.taker))
        }
        // Every real order sits well inside it.
        for (c in swaps) assertTrue(order(c.path).slippageBps <= 100)
    }

    @Test
    fun `pass - a price above the pinned ceiling is refused, even when summary and bytes agree`() = runTest {
        val build = passBuild(pass)
        assertEquals(TransactionGuard.PASS_PRICE_CEILING_RAW, build.summary.amount)
        val dear = ServerBuilt.pass(simPayer, codeHash, amount = TransactionGuard.PASS_PRICE_CEILING_RAW + 1)
        val v = TransactionGuard.checkPass(dear.transaction(), simPayer, build.summary.copy(amount = TransactionGuard.PASS_PRICE_CEILING_RAW + 1), codeHash)
        assertRefused(v, "ceiling")
        assertWhy(v, TransactionGuard.Why.ABOVE_PASS_PRICE)
        val thousand = ServerBuilt.pass(simPayer, codeHash, amount = 1_000_000_000L)
        assertRefused(TransactionGuard.checkPass(thousand.transaction(), simPayer, build.summary.copy(amount = 1_000_000_000L), codeHash), "ceiling")
        // A lower price, a discount, is still a pass.
        val cheaper = ServerBuilt.pass(simPayer, codeHash, amount = 6_000_000L)
        assertAllowed(TransactionGuard.checkPass(cheaper.transaction(), simPayer, build.summary.copy(amount = 6_000_000L), codeHash))
    }

    @Test
    fun `each refusal carries the plain category a screen states`() = runTest {
        assertWhy(TransactionGuard.checkPass(byteArrayOf(1, 2, 3), simPayer, passBuild(pass).summary, codeHash), TransactionGuard.Why.UNREADABLE)
        val m = passWire()
        val approve = m.plus(KnownPrograms.TOKEN, listOf(m.keys[1], attacker, simPayer), leData(4.toByte(), Long.MAX_VALUE))
        assertWhy(checkPass(approve), TransactionGuard.Why.HANDS_OVER_CONTROL)
        val t = m.indexOfProgram(KnownPrograms.TOKEN)
        val (withAttacker, a) = m.withKey(attacker)
        assertWhy(checkPass(withAttacker.mapInstruction(t) { it.copy(accounts = listOf(it.accounts[0], a, it.accounts[2])) }), TransactionGuard.Why.WRONG_RECIPIENT)
        assertWhy(checkPass(m.mapInstruction(t) { it.copy(data = leData(3.toByte(), 1_000_000L)) }), TransactionGuard.Why.WRONG_AMOUNT)
        assertWhy(checkPass(m, wallet = attacker), TransactionGuard.Why.NOT_YOUR_WALLET)
        assertWhy(checkPass(m.plus(attacker, listOf(simPayer), byteArrayOf(0))), TransactionGuard.Why.UNKNOWN_PROGRAM)
        assertWhy(checkVote(voteWire(), ticker = "NFLX"), TransactionGuard.Why.NOT_THIS_REQUEST)
    }
}
