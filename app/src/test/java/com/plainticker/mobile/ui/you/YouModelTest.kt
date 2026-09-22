package com.plainticker.mobile.ui.you

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.wallet.WalletAccount
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 3. **No FactGrid value on this screen exceeds its card's character budget, recomputed for
 *    Amber** (v0.12.0's original fix: "no wallet connected" clipped to "no wallet c"; DESIGN.md's
 *    Amber pass changes the face and the size the v0.12.0 fix was measured against, so the old
 *    10-character budget is wrong and is redone below against the real font file), for every
 *    state each cell can take, fixed string or composed.
 */
class YouModelTest {

    /**
     * YouScreen.kt's `FactCardView` draws a cell's value `maxLines = 1, softWrap = false`, so
     * anything past what the glyphs fit clips mid-character rather than wrapping, the same trap
     * v0.12.0 hit. Amber changes both the face (JetBrains Mono, monospace, to Bricolage Grotesque,
     * proportional) and the size (24sp to 18sp, [com.plainticker.mobile.ui.theme.AmberType.figureRow]'s
     * own size), so the old budget's arithmetic no longer applies and is redone here from the real
     * geometry and the real font file, not assumed.
     *
     * **The two cards are genuinely different widths**, because `FactGrid` (YouScreen.kt) lays out
     * each group as N equal-weight 16dp-radius cards in one row (8dp gaps, 12dp horizontal padding
     * per card) rather than Instrument's always-two-per-row blueprint grid: the Pro/Staked SKR pair
     * is two cards, On this device is three, and a narrower row of three leaves each card less
     * room. On the Seeker's 400dp frame, 20dp screen margin on each side:
     * - Two cards: (400 − 2×20 − 1×8) ÷ 2 − 2×12 = **152dp** of inner content per card.
     * - Three cards: (400 − 2×20 − 2×8) ÷ 3 − 2×12 = **90.67dp** of inner content per card.
     *
     * **Measured against `res/font/bricolage_grotesque.ttf` itself** (fontTools, 2026-09-22,
     * instantiated at `wght` 600 `wdth` 100 `opsz` 18, the exact variation coordinates
     * `AmberType.figureRow` builds): every glyph a real fixed value or a plausible composed count
     * can contain, at 18sp.
     * - Word values (no tabular feature; [com.plainticker.mobile.ui.you.YouScreen]'s
     *   `FactValueWordStyle`): the widest of the real fixed strings below averages 9.905dp per
     *   character ("Not open", 79.24dp over 8 characters); 152dp ÷ 9.905dp ≈ 15.34, kept one
     *   character under the computed fit, the same margin the original budget kept: **14**.
     * - Tabular digits (`tnum` on, [AmberType.figureRow] itself): every digit is 11.03dp wide
     *   under `tnum` (`,` is 3.24dp, cheaper, so counting every character as a full digit only
     *   underestimates how much actually fits); 90.67dp ÷ 11.03dp ≈ 8.22, kept one character
     *   under the computed fit: **7**, which lands exactly on "999,999" (7 characters), the
     *   largest count this file's own synthetic ceiling below already probes.
     *
     * Both real card widths end up with *more* headroom than Instrument's one ~145.5dp estimate,
     * not less: Amber's own value style is smaller (18sp against 24sp) by more than the pair card
     * loses to the trio card's tighter share of the row.
     */
    private val maxFactWordValueLength = 14

    /** See [maxFactWordValueLength]'s own comment: the On this device row's three-card budget. */
    private val maxFactCountValueLength = 7

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
            figure.length > maxFactWordValueLength,
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

    // ---- The FactGrid value budget (v0.12.0 fix, recomputed for Amber) -----------------------

    /** Every strings.xml word value a You FactGrid cell can draw, at Amber's 18sp figureRow size. */
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
    fun `every fixed FactGrid word value on You fits its card`() {
        factValueNames.forEach { name ->
            val text = ShippedCopy.strings.getValue(name)
            assertTrue(
                "$name (\"$text\", ${text.length} chars) exceeds the $maxFactWordValueLength-character " +
                    "FactGrid value budget; move the detail to the sub line instead of widening the cell",
                text.length <= maxFactWordValueLength,
            )
        }
    }

    @Test
    fun `the composed On this device counts stay inside the trio card's budget`() {
        // Swaps and votes are capped at 200 (FileReceiptStore.MAX_RECEIPTS, FileVoteReceiptStore's
        // own copy); the watchlist has no such cap, but the whole xStocks catalog this app can
        // ever watch from is in the low hundreds today (DESIGN.md section 1.1), so a six-digit
        // watchlist is already generations past plausible: 999,999 is this file's own synthetic
        // ceiling and it is also the exact string maxFactCountValueLength's own comment derives
        // the budget from, so this list stays the largest count this test still expects to fit.
        listOf(0, 1, 200, 9_999, 999_999).forEach { count ->
            val facts = deviceFacts(YouUiState(swapsRecorded = count, votesCast = count, stocksWatched = count))
            listOf(facts.swaps, facts.votes, facts.watched).forEach { text ->
                assertTrue(
                    "Fmt.count($count) = \"$text\" (${text.length} chars) exceeds the " +
                        "$maxFactCountValueLength-character FactGrid count budget",
                    text.length <= maxFactCountValueLength,
                )
            }
        }
    }

    // ---- The label and sub-line budget the earlier value-only pass never measured (fifth clip) -

    /**
     * The trio card's own inner content width (see [maxFactWordValueLength]'s own comment for the
     * derivation: (400 − 2×20 − 2×8) ÷ 3 − 2×12 on the Seeker's 400dp frame). Every text slot in a
     * device-fact cell (label, value, sub) draws inside exactly this many dp, whatever the font
     * scale: dp layout does not grow with a reader's text-size setting, only the glyphs inside it
     * do, which is why 1.3x is strictly the harder case below and 1.0x needs no separate table.
     */
    private val factLineWidthDp = 90.667

    /**
     * `FactCardView`'s label ([com.plainticker.mobile.ui.theme.AmberType.meta], 12sp/400, opsz 12)
     * grown to 1.3x. fontTools against `res/font/bricolage_grotesque.ttf`, `wght` 400, `wdth` 100,
     * `opsz` 12, 2026-09-22, one glyph run per word (no kerning applied, which only ever makes the
     * real render narrower than this, so the number stays the safe direction for a clip check).
     */
    private val factLabelWordWidthAt13xDp = mapOf(
        "Swaps" to 49.078,
        "recorded" to 67.049,
        "Votes" to 42.432,
        "cast" to 31.387,
        "Stocks" to 50.544,
        "watched" to 63.367,
    )

    /**
     * `FactCardView`'s sub ([com.plainticker.mobile.ui.theme.AmberType.context], 14sp/400, opsz
     * 14) grown to 1.3x, same method as [factLabelWordWidthAt13xDp].
     */
    private val factSubWordWidthAt13xDp = mapOf(
        "Listed" to 51.488,
        "under" to 50.105,
        "Portfolio" to 73.928,
        "Vote" to 39.749,
        "Today" to 52.671,
    )

    /**
     * A greedy word-wrap only ever needs as many lines as there are words (it starts a new line
     * only when the current one is already full), so "every word fits one line, and there are no
     * more words than the cell's own line budget" is a sufficient, conservative proof that the
     * real wrap never needs an extra line — the same kind of arithmetic proof
     * [AmberTickerRowTest] runs against this font file, rather than a simulation of Compose's own
     * line-breaker.
     */
    private fun assertWordsFitOnTheirOwnLines(text: String, widths: Map<String, Double>, maxLines: Int) {
        val words = text.split(" ")
        assertTrue(
            "\"$text\" is ${words.size} words, past the $maxLines-line budget this cell's anatomy " +
                "gives it; even one word per line would need a line this cell does not have",
            words.size <= maxLines,
        )
        words.forEach { word ->
            val width = widths[word] ?: error(
                "\"$word\" (from \"$text\") has no measured width in this table; remeasure it with " +
                    "fontTools against res/font/bricolage_grotesque.ttf at this style's exact " +
                    "wght/wdth/opsz before this test can prove the new copy still fits",
            )
            assertTrue(
                "\"$word\" ($width dp at 1.3x) exceeds the trio card's own $factLineWidthDp dp line " +
                    "budget on its own, so no amount of wrapping saves it",
                width <= factLineWidthDp,
            )
        }
    }

    @Test
    fun `every On this device label wraps within its own line budget, at 1_3x`() {
        listOf(R.string.you_fact_swaps, R.string.you_fact_votes, R.string.you_fact_watched).forEach { id ->
            val label = ShippedCopy.render(Copy.Words(id))
            assertWordsFitOnTheirOwnLines(label, factLabelWordWidthAt13xDp, FactLabelMaxLines)
        }
    }

    @Test
    fun `every On this device sub wraps within its own line budget, at 1_3x`() {
        listOf(R.string.you_fact_sub_portfolio, R.string.you_fact_sub_vote, R.string.you_fact_sub_watchlist).forEach { id ->
            val sub = ShippedCopy.render(Copy.Words(id))
            assertWordsFitOnTheirOwnLines(sub, factSubWordWidthAt13xDp, FactSubMaxLines)
        }
    }

    @Test
    fun `the stocks watched sub names the destination it actually opens, not the folded Watchlist tab`() {
        // HomeTab.WATCHLIST maps to AmberDestination.TODAY (HomeScreen.kt's toAmberDestination):
        // Watchlist folded into Today, and tapping this cell opens Today (deviceCells above), so
        // the copy must say Today, the same staleness rule as every other "Listed under" string.
        val sub = ShippedCopy.strings.getValue("you_fact_sub_watchlist")
        assertEquals("Listed under Today", sub)
        assertFalse("the copy still names the retired Watchlist destination", sub.contains("Watchlist"))
    }

    // ---- Fonts and licenses (task U11) ---------------------------------------------------------

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    @Test
    fun `every bundled font the app actually ships today is attributed, not just the two U11 was written against`() {
        // docs/fonts.md's own table: Outfit and JetBrains Mono from Instrument, Bricolage Grotesque
        // added for Amber. U11 predates the third; this pins that the list did not stay at two.
        assertEquals(3, bundledFontLicenses.size)
        assertEquals(
            "one row per asset, none doubled or dropped",
            bundledFontLicenses.size,
            bundledFontLicenses.map { it.assetPath }.distinct().size,
        )
    }

    @Test
    fun `every attributed font names a real, non-blank string and a license file that ships in the APK`() {
        bundledFontLicenses.forEach { license ->
            val name = ShippedCopy.render(Copy.Words(license.nameRes))
            val credit = ShippedCopy.render(Copy.Words(license.creditRes))
            assertTrue("a font's device-facing name must not be blank", name.isNotBlank())
            assertTrue("$name's credit line must not be blank", credit.isNotBlank())
            assertTrue("$name's credit line must name a copyright", credit.contains("Copyright"))

            // The asset itself: docs/fonts.md's own three paths, read the same way the license
            // screen will (relative to app/src/main/assets/), not assumed to exist.
            val file = File(module, "src/main/assets/${license.assetPath}")
            assertTrue("${license.assetPath} does not exist; docs/fonts.md's own list is now wrong", file.isFile)
            assertTrue("${license.assetPath} is empty", file.readText().isNotBlank())
        }
    }

    @Test
    fun `the license terms sentence names the actual license, spelled out rather than abbreviated`() {
        val terms = ShippedCopy.strings.getValue("you_license_terms")
        assertTrue(terms.contains("SIL Open Font License 1.1"))
    }
}
