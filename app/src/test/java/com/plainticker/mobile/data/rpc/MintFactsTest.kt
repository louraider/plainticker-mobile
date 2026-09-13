package com.plainticker.mobile.data.rpc

import com.plainticker.mobile.data.Fixtures
import com.plainticker.mobile.data.KnownPrograms
import com.plainticker.mobile.data.net.HttpClientFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * The typed reading of a Token-2022 mint, over the live capture and two hand-built shapes
 * (app/src/test/resources/rpc/mint-*.json).
 *
 * The point of most of these is the difference between a fact and an absence of one: a mint that
 * says nothing about a permanent delegate and a mint this app could not read are opposite answers,
 * and the one thing the mapper may never do is turn the second into the first.
 */
class MintFactsTest {

    private fun envelope(path: String): ContextValue<RpcAccount> =
        HttpClientFactory.json.decodeFromString(
            RpcResponse.serializer(ContextValue.serializer(RpcAccount.serializer())),
            Fixtures.read(path),
        ).result!!

    private fun account(path: String): RpcAccount = checkNotNull(envelope(path).value) { "no account in $path" }

    private fun facts(path: String): MintFacts = checkNotNull(MintFacts.from(account(path))) { "unreadable: $path" }

    private fun inline(json: String): RpcAccount =
        HttpClientFactory.json.decodeFromString(RpcAccount.serializer(), json)

    // ---- The live mint ----------------------------------------------------------------------

    @Test
    fun `the live TSLAx mint reads as Token-2022 with eight decimals at the slot it was answered at`() {
        val envelope = envelope(FIXTURE_LIVE)
        val account = checkNotNull(envelope.value)

        assertEquals("the forwarder answered at a slot", 446_503_662L, envelope.context?.slot)
        assertEquals(KnownPrograms.TOKEN_2022, account.owner)
        assertEquals(MintFacts.PARSED_PROGRAM, account.parsedProgram)
        assertEquals(MintFacts.PARSED_TYPE, account.parsedType)

        val facts = facts(FIXTURE_LIVE)
        assertEquals(MintFacts.XSTOCK_DECIMALS, facts.decimals)
        assertEquals(BigInteger("22963733950050"), facts.supplyRaw)
        assertEquals("229637.3395005", facts.supplyTokens().stripTrailingZeros().toPlainString())
    }

    @Test
    fun `the live TSLAx mint carries a permanent delegate, a pausable config that is not paused, and an empty hook`() {
        val facts = facts(FIXTURE_LIVE)

        assertEquals("5aMNNLQJwAEeoemTEMkv5NVjqKwvvefRYCQ5Z67HFvEq", facts.permanentDelegate?.delegate)
        assertTrue("a delegate address is present, so the delegation is live", facts.permanentDelegate!!.active)

        assertNotNull("the extension is there", facts.pausable)
        assertFalse("and it is not paused right now", facts.pausable!!.paused)

        val hook = checkNotNull(facts.transferHook) { "the hook extension is there" }
        assertNull("but its programId is JSON null", hook.programId)
        assertFalse("so no program runs on a transfer", hook.runs)

        assertEquals(DefaultAccountState.INITIALIZED, facts.defaultAccountState?.state)
        assertEquals("a fresh account is not frozen", false, facts.freshAccountFrozen)

        val scaled = checkNotNull(facts.scaledUiAmount)
        assertEquals(1.0, scaled.multiplier, 0.0)
        assertEquals(1.0, scaled.newMultiplier, 0.0)
        assertEquals(0L, scaled.newMultiplierEffectiveAtEpochSeconds)

        assertEquals("JDq14BWvqCRFNu1krb12bcRpbGtJZ1FLEakMw6FdxJNs", facts.freezeAuthority)
        assertEquals("7pt9tkctJPK7PPNQJ77GKg8ZffSF6QxoMiCFYHxrtaCj", facts.mintAuthority)
    }

    // ---- Absence is a fact ------------------------------------------------------------------

    @Test
    fun `a mint with no extension list reads every extension as absent, not as unknown`() {
        val facts = facts(FIXTURE_NO_EXTENSIONS)

        assertNull(facts.permanentDelegate)
        assertNull(facts.pausable)
        assertNull(facts.scaledUiAmount)
        assertNull(facts.transferHook)
        assertNull(facts.defaultAccountState)
        assertNull("the mint says nothing about a fresh account either way", facts.freshAccountFrozen)

        assertEquals(MintFacts.XSTOCK_DECIMALS, facts.decimals)
        assertEquals(BigInteger("100000000000"), facts.supplyRaw)
        assertNull(facts.mintAuthority)
        assertNull(facts.freezeAuthority)
    }

    @Test
    fun `a present extension that is quiet is never read as an absent one`() {
        val delegated = facts(FIXTURE_LIVE)
        val plain = facts(FIXTURE_NO_EXTENSIONS)

        // Both mints run no transfer hook program today. Only one of them can ever be given one.
        assertFalse(delegated.transferHook!!.runs)
        assertNull(plain.transferHook)

        // Both are unpaused today. Only one of them has an issuer who can pause it.
        assertFalse(delegated.pausable!!.paused)
        assertNull(plain.pausable)
    }

    @Test
    fun `a present permanent delegate with the address revoked is not the same as no extension`() {
        val revoked = MintFacts.from(inline(mint(extensions = """{"extension":"permanentDelegate","state":{"delegate":null}}""")))!!
        val delegate = checkNotNull(revoked.permanentDelegate) { "the extension is on the mint" }
        assertNull(delegate.delegate)
        assertFalse("nobody can exercise it right now", delegate.active)

        val absent = MintFacts.from(inline(mint()))!!
        assertNull(absent.permanentDelegate)
    }

    // ---- A scheduled split ------------------------------------------------------------------

    @Test
    fun `a pending multiplier carries the new value and the second it activates`() {
        val facts = facts(FIXTURE_PENDING_SPLIT)
        val scaled = checkNotNull(facts.scaledUiAmount)

        assertEquals(1.0, scaled.multiplier, 0.0)
        assertEquals(4.0, scaled.newMultiplier, 0.0)
        assertEquals(1_789_948_800L, scaled.newMultiplierEffectiveAtEpochSeconds)
    }

    @Test
    fun `the pending-split fixture also exercises paused transfers, a live hook and a frozen new account`() {
        val facts = facts(FIXTURE_PENDING_SPLIT)

        assertTrue(facts.pausable!!.paused)
        assertEquals("HookProgram111111111111111111111111111111", facts.transferHook?.programId)
        assertTrue(facts.transferHook!!.runs)
        assertEquals(DefaultAccountState.FROZEN, facts.defaultAccountState?.state)
        assertEquals(true, facts.freshAccountFrozen)
    }

    // ---- Unknown, never "no risk" -----------------------------------------------------------

    @Test
    fun `an account owned by the classic token program reads as unknown`() {
        val classic = inline(mint(owner = KnownPrograms.TOKEN, program = "spl-token"))
        assertNull(MintFacts.from(classic))
    }

    @Test
    fun `a Token-2022 account that is not a mint reads as unknown`() {
        assertNull(MintFacts.from(inline(mint(type = "account"))))
    }

    @Test
    fun `a shape that does not match reads as unknown`() {
        assertNull("no account at all", MintFacts.from(null))
        assertNull("a base64 read carries no parsed object", MintFacts.from(inline(BASE64_ACCOUNT)))
        assertNull("no decimals", MintFacts.from(inline(mint(info = """"supply":"1""""))))
        assertNull("no supply", MintFacts.from(inline(mint(info = """"decimals":8"""))))
        assertNull("supply is not a number", MintFacts.from(inline(mint(info = """"decimals":8,"supply":"lots""""))))
        assertNull("the info object is missing", MintFacts.from(inline(NO_INFO_ACCOUNT)))
    }

    @Test
    fun `an unrecognised default account state is unknown rather than open`() {
        val odd = MintFacts.from(
            inline(mint(extensions = """{"extension":"defaultAccountState","state":{"accountState":"quarantined"}}""")),
        )!!
        assertNotNull("the extension is present", odd.defaultAccountState)
        assertNull("and this app will not guess what the word means", odd.freshAccountFrozen)
    }

    @Test
    fun `an extension with no state object still counts as present`() {
        val facts = MintFacts.from(inline(mint(extensions = """{"extension":"pausableConfig"}""")))!!
        assertNotNull(facts.pausable)
        assertFalse("unknown paused flag reads as not paused now, but the issuer can still pause", facts.pausable!!.paused)
    }

    @Test
    fun `a scaled amount with no readable multiplier reads as no scaled amount`() {
        val facts = MintFacts.from(
            inline(mint(extensions = """{"extension":"scaledUiAmountConfig","state":{"multiplier":"nope"}}""")),
        )!!
        assertNull(facts.scaledUiAmount)
    }

    // ---- Inline shapes ----------------------------------------------------------------------

    private fun mint(
        owner: String = KnownPrograms.TOKEN_2022,
        program: String = MintFacts.PARSED_PROGRAM,
        type: String = MintFacts.PARSED_TYPE,
        info: String = """"decimals":8,"supply":"1000"""",
        extensions: String? = null,
    ): String {
        val list = extensions?.let { ""","extensions":[$it]""" }.orEmpty()
        return """
        {"lamports":1,"owner":"$owner","executable":false,"space":82,
         "data":{"program":"$program","space":82,"parsed":{"type":"$type","info":{$info$list}}}}
        """.trimIndent()
    }

    private companion object {
        const val FIXTURE_LIVE = "rpc/mint-tslax.json"
        const val FIXTURE_NO_EXTENSIONS = "rpc/mint-no-extensions.json"
        const val FIXTURE_PENDING_SPLIT = "rpc/mint-pending-split.json"

        const val BASE64_ACCOUNT =
            """{"lamports":1,"owner":"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb","data":["AQID","base64"]}"""

        const val NO_INFO_ACCOUNT =
            """{"lamports":1,"owner":"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",""" +
                """"data":{"program":"spl-token-2022","parsed":{"type":"mint"}}}"""
    }
}
