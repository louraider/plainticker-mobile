package com.plainticker.mobile.ui.you

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.wallet.WalletAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What You says (task U1), the same split every other screen's model keeps: a pure function
 * decides the sentence, the composable only places it. Two things this file exists to pin:
 *
 * 1. **The Pro fact is a single word, for every state [com.plainticker.mobile.ui.portfolio.ProModelTest]
 *    already proves the full sentence for.**
 * 2. **The button matrix (plan section 1.2) is exhaustive and never offers two Accent fills.**
 */
class YouModelTest {

    private fun words(copy: Copy): Int = (copy as Copy.Words).id

    // ---- The Pro fact -------------------------------------------------------------------------

    @Test
    fun `loading reads as loading, before the source is known`() {
        assertEquals(R.string.state_loading, words(proFact(ProUiState(entitlementLoading = true)).value))
        assertNull(proFact(ProUiState(entitlementLoading = true)).until)
    }

    @Test
    fun `disabled and a failed read are two different words`() {
        val disabled = ProUiState(entitlementLoading = false, entitlementDisabled = true)
        assertEquals(R.string.you_pro_disabled, words(proFact(disabled).value))
        val failed = ProUiState(entitlementLoading = false, entitlementFailed = true)
        assertEquals(R.string.you_pro_unread, words(proFact(failed).value))
    }

    @Test
    fun `a pass names its source and carries the until sub line`() {
        val state = ProUiState(
            entitlementLoading = false,
            pro = true,
            source = EntitlementSource.PASS,
            untilMillis = 1_792_368_000_000L,
        )
        val fact = proFact(state)
        assertEquals(R.string.you_pro_pass, words(fact.value))
        assertEquals(R.string.you_pro_until, words(fact.until!!))
    }

    @Test
    fun `a stake names its source and carries no date, because it is never granted once`() {
        val state = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.STAKE, untilMillis = 1_760_832_000_000L)
        val fact = proFact(state)
        assertEquals(R.string.you_pro_stake, words(fact.value))
        assertNull("a stake carries no date even if one is passed in", fact.until)
    }

    @Test
    fun `pro true with no recognized source still reads as a plain yes`() {
        val state = ProUiState(entitlementLoading = false, pro = true, source = null)
        assertEquals(R.string.you_pro_active, words(proFact(state).value))
    }

    @Test
    fun `not pro reads as a plain no`() {
        val state = ProUiState(entitlementLoading = false, pro = false, source = null)
        assertEquals(R.string.you_pro_none, words(proFact(state).value))
    }

    // ---- The stake fact -------------------------------------------------------------------------

    @Test
    fun `no wallet states the reason, never a figure`() {
        assertEquals(R.string.you_stake_none_wallet, words(stakeFact(ProUiState(walletConnected = false))))
    }

    @Test
    fun `an unread stake reads the same word disabled entitlement does not use`() {
        val state = ProUiState(walletConnected = true, stakeUnread = true, stakeRaw = null)
        assertEquals(R.string.you_pro_unread, words(stakeFact(state)))
    }

    @Test
    fun `a stake still in flight reads as loading, never a placeholder`() {
        val state = ProUiState(walletConnected = true, stakeUnread = false, stakeRaw = null)
        assertEquals(R.string.state_loading, words(stakeFact(state)))
    }

    @Test
    fun `a read stake is a bare figure, with no verdict word near it`() {
        val state = ProUiState(walletConnected = true, stakeRaw = 31_209_870_777L)
        val figure = stakeFact(state) as Copy.Raw
        assertEquals("31,209.870777", figure.text)
    }

    // ---- The button matrix (plan section 1.2) ------------------------------------------------

    private data class Buttons(val primary: YouActionKind?, val secondary: YouActionKind?)

    private fun buttons(state: ProUiState) = Buttons(youActions(state).primary?.kind, youActions(state).secondary?.kind)

    @Test
    fun `no wallet and not pro offers connect, plus pay when the server has not refused it`() {
        val offered = ProUiState(entitlementLoading = false, walletConnected = false, pro = false)
        assertEquals(Buttons(YouActionKind.CONNECT, YouActionKind.PAY), buttons(offered))

        val refused = ProUiState(entitlementLoading = false, walletConnected = false, pro = false, entitlementDisabled = true)
        assertEquals("a server refusal withholds Pay even with no wallet", Buttons(YouActionKind.CONNECT, null), buttons(refused))
    }

    @Test
    fun `no wallet and already pro offers only connect`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = false, pro = true, source = EntitlementSource.PASS)
        assertEquals(Buttons(YouActionKind.CONNECT, null), buttons(state))
    }

    @Test
    fun `a wallet that is not pro offers pay alone, never beside connect`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = true, pro = false)
        assertEquals(Buttons(YouActionKind.PAY, null), buttons(state))
    }

    @Test
    fun `a wallet already pro offers no button at all`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = true, pro = true, source = EntitlementSource.STAKE)
        assertEquals(Buttons(null, null), buttons(state))
    }

    @Test
    fun `a pending payment withholds pay even for a wallet that is not yet pro`() {
        val state = ProUiState(entitlementLoading = false, walletConnected = true, pro = false, pendingSignature = "sig")
        assertEquals(Buttons(null, null), buttons(state))
    }

    // ---- The device facts and the wallet value -----------------------------------------------

    @Test
    fun `the device facts are already formatted, never a raw int`() {
        val facts = deviceFacts(YouUiState(swapsRecorded = 1_234, votesCast = 0, stocksWatched = 7))
        assertEquals("1,234", facts.swaps)
        assertEquals("0", facts.votes)
        assertEquals("7", facts.watched)
    }

    @Test
    fun `the wallet value is a short mono key, or the not connected word`() {
        assertEquals(R.string.you_wallet_none, words(walletValue(null)))
        val account = WalletAccount(publicKey = ByteArray(32) { 7 })
        val value = walletValue(account) as Copy.Raw
        assertEquals(4 + 1 + 4, value.text.length)
    }

    @Test
    fun `the notifications line matches the watchlist s own words`() {
        assertEquals(R.string.watchlist_notifications_on, words(notificationLine(true)))
        assertEquals(R.string.watchlist_notifications_off, words(notificationLine(false)))
    }
}
