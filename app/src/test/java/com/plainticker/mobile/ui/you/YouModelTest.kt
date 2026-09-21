package com.plainticker.mobile.ui.you

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.wallet.WalletAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What You says (task U1), the same split every other screen's model keeps: a pure function
 * decides the sentence, the composable only places it. Three things this file exists to pin:
 *
 * 1. **The Pro fact is a single word, for every state [com.plainticker.mobile.ui.portfolio.ProModelTest]
 *    already proves the full sentence for.**
 * 2. **The button matrix (plan section 1.2) is exhaustive and never offers two Accent fills.**
 * 3. **No FactGrid value on this screen exceeds the half-width mono slot's character budget**
 *    (v0.12.0 fix: "no wallet connected" clipped to "no wallet c"), for every state each cell can
 *    take, fixed string or composed.
 */
class YouModelTest {

    /**
     * FactCellView draws a cell's value `maxLines = 1, softWrap = false` (FactGrid.kt), so
     * anything past what the mono glyphs fit clips mid-character rather than wrapping. At You's
     * default 24sp valueSize, a half-width cell on the Seeker's 400dp frame has roughly 146.5dp of
     * inner content (400dp minus the FactGrid's 20dp outer padding on each side, its 1dp border
     * plus 1dp inset on each side, one 1dp inter-cell gap, halved, minus the cell's own 16dp
     * padding on each side) and JetBrains Mono's ~0.6em advance width puts about 10.2 characters
     * in that space at 24sp. "no wallet connected" (20 characters) clipped to "no wallet c" on the
     * Seeker at default font scale: 10 whole characters plus a fragment of the 11th, matching the
     * arithmetic. 10 is kept here, one character under the computed fit, because the number above
     * is arithmetic and not a device reading.
     */
    private val maxFactValueLength = 10

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
    fun `no wallet states a short value and the reason as its sub line, never a figure and never zero`() {
        val fact = stakeFact(ProUiState(walletConnected = false))
        assertEquals(R.string.you_stake_no_wallet, words(fact.value))
        assertEquals(R.string.you_stake_none_wallet, words(fact.sub!!))
    }

    @Test
    fun `an unread stake reads the same word disabled entitlement does not use, with no sub line`() {
        val state = ProUiState(walletConnected = true, stakeUnread = true, stakeRaw = null)
        val fact = stakeFact(state)
        assertEquals(R.string.you_pro_unread, words(fact.value))
        assertNull(fact.sub)
    }

    @Test
    fun `a stake still in flight reads as loading, never a placeholder`() {
        val state = ProUiState(walletConnected = true, stakeUnread = false, stakeRaw = null)
        val fact = stakeFact(state)
        assertEquals(R.string.state_loading, words(fact.value))
        assertNull(fact.sub)
    }

    @Test
    fun `a read stake is a short value word, the bare figure moved to the sub line`() {
        val state = ProUiState(walletConnected = true, stakeRaw = 31_209_870_777L)
        val fact = stakeFact(state)
        assertEquals(R.string.you_stake_read, words(fact.value))
        val figure = fact.sub as Copy.Raw
        assertEquals("31,209.870777", figure.text)
    }

    @Test
    fun `the worst case plausible stake would clip the value if it were ever drawn there`() {
        // The largest principal SkrStakeBound.isPlausible ever accepts, one base unit under the
        // ceiling so the fractional part does not trim away to nothing: this is the actual worst
        // case a connected wallet's own stake could reach, not an arbitrary large number, and it
        // is why the figure moved to the sub line rather than the value in the v0.12.0 fix.
        val worstCase = SkrStakeBound.STAKED_SUPPLY_RAW - 1L
        assertTrue(SkrStakeBound.isPlausible(worstCase))
        val figure = Fmt.tokenAmount(worstCase, SkrStakeBound.SKR_DECIMALS)
        assertTrue(
            "the worst-case figure (\"$figure\", ${figure.length} chars) is expected to exceed the " +
                "value budget, which is exactly why it lives in the sub line and not the value",
            figure.length > maxFactValueLength,
        )
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

    // ---- The FactGrid value budget (v0.12.0 fix) ---------------------------------------------

    /** Every strings.xml value a You FactGrid cell can draw at the default 24sp value size. */
    private val factValueNames = listOf(
        "state_loading",
        "you_pro_disabled",
        "you_pro_unread",
        "you_pro_pass",
        "you_pro_stake",
        "you_pro_subscription",
        "you_pro_active",
        "you_pro_none",
        "you_stake_no_wallet",
        "you_stake_read",
    )

    @Test
    fun `every fixed FactGrid value on You fits the half-width mono slot`() {
        factValueNames.forEach { name ->
            val text = ShippedCopy.strings.getValue(name)
            assertTrue(
                "$name (\"$text\", ${text.length} chars) exceeds the $maxFactValueLength-character " +
                    "FactGrid value budget; move the detail to the sub line instead of widening the cell",
                text.length <= maxFactValueLength,
            )
        }
    }

    @Test
    fun `the composed On this device counts stay inside the budget too`() {
        // Swaps and votes are capped at 200 (FileReceiptStore.MAX_RECEIPTS, FileVoteReceiptStore's
        // own copy); the watchlist has no such cap, but the whole xStocks catalog this app can
        // ever watch from is in the low hundreds today (DESIGN.md section 1.1), so a six-digit
        // watchlist is already generations past plausible. Comma grouping only adds one character
        // per three digits, so the budget does not run out until eight digits either way.
        listOf(0, 1, 200, 9_999, 999_999).forEach { count ->
            val facts = deviceFacts(YouUiState(swapsRecorded = count, votesCast = count, stocksWatched = count))
            listOf(facts.swaps, facts.votes, facts.watched).forEach { text ->
                assertTrue(
                    "Fmt.count($count) = \"$text\" (${text.length} chars) exceeds the " +
                        "$maxFactValueLength-character FactGrid value budget",
                    text.length <= maxFactValueLength,
                )
            }
        }
    }
}
