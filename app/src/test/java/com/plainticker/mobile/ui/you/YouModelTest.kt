package com.plainticker.mobile.ui.you

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.ShippedCopy
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.pass.PromoRefusal
import com.plainticker.mobile.ui.pass.PromoState
import com.plainticker.mobile.wallet.WalletAccount
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What You says, the same split every other screen's model keeps: a pure function decides the
 * sentence, the composable only places it.
 *
 * Rewritten for the cabinet pass (2026-09-25). The file used to pin the Pro and Staked SKR fact
 * words, the old Connect/Pay button matrix and the fact cards' character budgets; the fact cards
 * are gone (every group is wrapping rows now, measured in [CabinetFitTest]) and the button matrix
 * became the hero's action matrix. Every behaviour the old matrix pinned is still pinned here
 * against the new one: a server refusal withholds pay, a pending payment withholds pay, a stake
 * offers nothing, and a state never offers two fills.
 */
class YouModelTest {

    private val now = 1_790_000_000_000L
    private val day = 86_400_000L

    private fun words(copy: Copy): Int = (copy as Copy.Words).id

    private val signedOut = AccountUiState.SignedOut()
    private val signedIn = AccountUiState.SignedIn(SignedInAccount("ann@example.com", "Ann", emptyList()))
    private val wallet = WalletAccount(publicKey = ByteArray(32) { 7 })

    private val free = ProUiState(entitlementLoading = false, walletConnected = true)
    private val pass = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.PASS, untilMillis = now + 26 * day, walletConnected = true)
    private val stake = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.STAKE, walletConnected = true, stakeRaw = 38_406_150_222L)
    private val subscription = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.SUBSCRIPTION, untilMillis = now + 3 * day)
    private val promo = ProUiState(entitlementLoading = false, pro = true, source = EntitlementSource.PROMO, untilMillis = now + 30 * day, walletConnected = true)

    // ---- Identity ------------------------------------------------------------------------------

    @Test
    fun `the identity is the google account first, then the wallet, then not signed in`() {
        val google = youHero(signedIn, wallet, free, now)
        assertEquals(IdentityKind.GOOGLE, google.identityKind)
        assertEquals("ann@example.com", (google.identity as Copy.Raw).text)
        assertEquals(R.string.account_signed_in_label, words(google.identityLabel!!))

        val byWallet = youHero(signedOut, wallet, free, now)
        assertEquals(IdentityKind.WALLET, byWallet.identityKind)
        assertEquals("a wallet reads as its short key, head and tail", 9, (byWallet.identity as Copy.Raw).text.length)
        assertEquals(R.string.you_wallet_method, words(byWallet.identityLabel!!))

        val nobody = youHero(signedOut, null, free.copy(walletConnected = false), now)
        assertEquals(IdentityKind.NONE, nobody.identityKind)
        assertEquals(R.string.you_identity_none, words(nobody.identity))
        assertNull(nobody.identityLabel)
    }

    // ---- The headline --------------------------------------------------------------------------

    @Test
    fun `a pass reads as pro until its day, with the days left under it`() {
        val hero = youHero(signedIn, wallet, pass, now)
        val headline = hero.headline as Copy.Words
        assertEquals(R.string.you_hero_pro_until, headline.id)
        assertEquals("Pro until 17 Oct 2026", ShippedCopy.render(headline))
        assertEquals("26 days left.", ShippedCopy.render(hero.lines.single()))
    }

    @Test
    fun `a stake reads as pro while staked and carries no date`() {
        val hero = youHero(signedIn, wallet, stake, now)
        assertEquals(R.string.you_hero_stake, words(hero.headline))
        assertEquals(listOf(R.string.you_hero_stake_line), hero.lines.map(::words))
    }

    @Test
    fun `a subscription reads as pro until its day too`() {
        val hero = youHero(signedIn, null, subscription, now)
        assertEquals(R.string.you_hero_pro_until, words(hero.headline))
        assertEquals("3 days left.", ShippedCopy.render(hero.lines.single()))
    }

    @Test
    fun `a promo code reads as pro until its day too, and offers no pay action`() {
        val hero = youHero(signedIn, wallet, promo, now)
        assertEquals(R.string.you_hero_pro_until, words(hero.headline))
        assertEquals("30 days left.", ShippedCopy.render(hero.lines.single()))
        assertNull("a promo is not paid for, so it offers no pay action of its own", hero.action)
    }

    @Test
    fun `free names the one open example from the constant, never a count of presets`() {
        val hero = youHero(signedIn, wallet, free, now)
        assertEquals(R.string.you_hero_free, words(hero.headline))
        val line = ShippedCopy.render(hero.lines.single())
        assertTrue(line.startsWith("$OPEN_EXAMPLE_TICKER is open to everyone"))
        assertFalse("never the retired preset count", Regex("""\d+ preset""").containsMatchIn(line))
        assertEquals("AAPL", OPEN_EXAMPLE_TICKER)
    }

    @Test
    fun `loading, a refusal and a failed read are three different headlines`() {
        assertEquals(R.string.you_hero_loading, words(youHero(signedIn, wallet, ProUiState(), now).headline))
        val disabled = youHero(signedIn, wallet, free.copy(entitlementDisabled = true), now)
        assertEquals(R.string.you_hero_free, words(disabled.headline))
        assertEquals(listOf(R.string.pro_entitlement_disabled), disabled.lines.map(::words))
        val failed = youHero(signedIn, wallet, free.copy(entitlementFailed = true), now)
        assertEquals(R.string.you_hero_unread, words(failed.headline))
    }

    @Test
    fun `a pending payment is stated in the hero`() {
        val hero = youHero(signedIn, wallet, free.copy(pendingSignature = "sig"), now)
        assertTrue(hero.lines.any { it is Copy.Words && it.id == R.string.pro_payment_pending })
    }

    @Test
    fun `days left counts one as one, and the end day as ends today`() {
        assertEquals("1 day left.", ShippedCopy.render(daysLeft(now + day, now)))
        assertEquals(R.string.you_ends_today, words(daysLeft(now, now)))
        assertEquals("a past end never reads as a negative count", R.string.you_ends_today, words(daysLeft(now - 3 * day, now)))
    }

    // ---- The action matrix ---------------------------------------------------------------------

    @Test
    fun `no identity offers sign in with google, with connect wallet as the text action`() {
        val hero = youHero(signedOut, null, free.copy(walletConnected = false), now)
        assertEquals(HeroAction.SIGN_IN, hero.action)
        assertTrue(hero.offersConnect)
    }

    @Test
    fun `no identity while the account is still read offers nothing, and a sign-in in flight is disabled`() {
        assertNull(youHero(AccountUiState.Restoring, null, free, now).action)
        val signingIn = youHero(AccountUiState.SigningIn, null, free, now)
        assertEquals(HeroAction.SIGNING_IN, signingIn.action)
        assertFalse(signingIn.offersConnect)
    }

    @Test
    fun `free offers get pro, pass offers extend, stake and subscription offer nothing`() {
        assertEquals(HeroAction.GET_PRO, youHero(signedIn, wallet, free, now).action)
        assertEquals(HeroAction.GET_PRO, youHero(signedOut, wallet, free, now).action)
        assertEquals(HeroAction.EXTEND, youHero(signedIn, wallet, pass, now).action)
        assertNull(youHero(signedIn, wallet, stake, now).action)
        assertNull(youHero(signedIn, wallet, subscription, now).action)
        assertNull(youHero(signedIn, wallet, ProUiState(), now).action)
        listOf(free, pass, stake).forEach { assertFalse(youHero(signedIn, wallet, it, now).offersConnect) }
    }

    @Test
    fun `a server refusal withholds pay in the hero and in the plan`() {
        val refused = free.copy(entitlementDisabled = true)
        val hero = youHero(signedIn, wallet, refused, now)
        assertNull(hero.action)
        assertTrue(planRows(refused, hero.action, now).none { it.action == PlanAction.GET_PRO || it.action == PlanAction.EXTEND })
    }

    @Test
    fun `a pending payment withholds pay even for a reader who is not yet pro`() {
        val pending = free.copy(pendingSignature = "sig")
        val hero = youHero(signedIn, wallet, pending, now)
        assertNull(hero.action)
        assertTrue(planRows(pending, hero.action, now).none { it.action == PlanAction.GET_PRO || it.action == PlanAction.EXTEND })
        val pendingPass = pass.copy(pendingSignature = "sig")
        assertNull(youHero(signedIn, wallet, pendingPass, now).action)
    }

    @Test
    fun `a failed read still offers pay, because pass build is its own call`() {
        assertEquals(HeroAction.GET_PRO, youHero(signedIn, wallet, free.copy(entitlementFailed = true), now).action)
    }

    // ---- The Plan group ------------------------------------------------------------------------

    private fun labels(rows: List<PlanRow>) = rows.map { words(it.label) }

    @Test
    fun `a pass plan states source, valid until with days left, and how to extend`() {
        val rows = planRows(pass, HeroAction.EXTEND, now)
        assertEquals(
            listOf(R.string.you_plan_source_label, R.string.you_plan_until_label, R.string.you_plan_extend_label, R.string.you_stake_label),
            labels(rows),
        )
        assertEquals(R.string.you_plan_source_pass, words(rows[0].value))
        assertNull("the days left are the hero's line, never repeated in the plan", rows[1].sub)
        assertEquals("26 days left.", ShippedCopy.render(youHero(signedIn, wallet, pass, now).lines.first()))
        assertNull("the hero already carries Extend, so the plan does not draw it twice", rows[2].action)
    }

    @Test
    fun `the pay entry lands in the plan whenever the hero does not carry it`() {
        // A reader with no identity sees Sign in in the hero; the pass flow is still one tap away.
        val noIdentity = free.copy(walletConnected = false)
        val hero = youHero(signedOut, null, noIdentity, now)
        assertEquals(HeroAction.SIGN_IN, hero.action)
        assertEquals(PlanAction.GET_PRO, planRows(noIdentity, hero.action, now).single { it.action == PlanAction.GET_PRO }.action)

        val passNoIdentity = pass.copy(walletConnected = false)
        val passHero = youHero(signedOut, null, passNoIdentity, now)
        assertTrue(planRows(passNoIdentity, passHero.action, now).any { it.action == PlanAction.EXTEND })
    }

    @Test
    fun `a promo plan names its source, valid until, and the plain way to extend`() {
        val rows = planRows(promo, null, now)
        assertEquals(
            listOf(R.string.you_plan_source_label, R.string.you_plan_until_label, R.string.you_plan_extend_label, R.string.you_stake_label),
            labels(rows),
        )
        assertEquals(R.string.you_plan_source_promo, words(rows[0].value))
        assertNull("the days left are the hero's line, never repeated in the plan", rows[1].sub)
        assertEquals(R.string.you_plan_extend_promo, words(rows[2].value))
        assertNull("a promo does not renew by paying here; the plain way is another code", rows[2].action)
    }

    @Test
    fun `the promo success line matches the hero's own Pro until sentence`() {
        assertEquals(R.string.you_hero_pro_until, words(promoSuccessLine(now + 30 * day)))
        assertEquals("Pro until 21 Oct 2026", ShippedCopy.render(promoSuccessLine(now + 30 * day)))
        assertEquals(R.string.you_hero_pro, words(promoSuccessLine(null)))
    }

    // ---- The promo row, closed (fresh-device QA of 1.3.24, B3 and B4) -----------------------

    @Test
    fun `a promo Pro read from the entitlement draws what the redeem drew, after a restart too`() {
        // After a relaunch PromoState is Idle again; only the entitlement remembers the redeem.
        val relaunched = promoLine(PromoState.Idle, promo, signedOut = true)!!
        val redeemed = promoLine(PromoState.Success(now + 30 * day), promo, signedOut = true)!!
        assertEquals(redeemed, relaunched)
        assertEquals("Pro until 21 Oct 2026", ShippedCopy.render(relaunched.value))
        assertEquals(R.string.pro_saved_to_phone, words(relaunched.sub!!))
        assertEquals(
            "Saved to this phone until you sign in. Signing in moves it to your Google account.",
            ShippedCopy.render(relaunched.sub!!),
        )
        assertFalse("a held Pro is not the quiet prompt", relaunched.quiet)
    }

    @Test
    fun `signed in, a promo Pro says only until when`() {
        val line = promoLine(PromoState.Idle, promo, signedOut = false)!!
        assertEquals(R.string.you_hero_pro_until, words(line.value))
        assertNull(line.sub)
    }

    @Test
    fun `before any redeem the row is the quiet prompt, with the sign in first hint only when signed out`() {
        val out = promoLine(PromoState.Idle, free, signedOut = true)!!
        assertEquals(R.string.promo_prompt, words(out.value))
        assertEquals(R.string.promo_signin_first_hint, words(out.sub!!))
        assertTrue(out.quiet)
        assertNull(promoLine(PromoState.Idle, free, signedOut = false)!!.sub)
        // Pro from somewhere else is not a promo: the prompt stays.
        assertEquals(R.string.promo_prompt, words(promoLine(PromoState.Idle, pass, signedOut = true)!!.value))
        assertEquals(R.string.promo_prompt, words(promoLine(PromoState.Idle, stake, signedOut = true)!!.value))
    }

    @Test
    fun `an entitlement still being read or refused is not taken for a promo Pro`() {
        val reading = promo.copy(entitlementLoading = true)
        assertEquals(R.string.promo_prompt, words(promoLine(PromoState.Idle, reading, signedOut = true)!!.value))
        val failed = promo.copy(entitlementFailed = true)
        assertEquals(R.string.promo_prompt, words(promoLine(PromoState.Idle, failed, signedOut = true)!!.value))
    }

    @Test
    fun `a redeem that just landed shows its own date, else the entitlement's`() {
        val justNow = promoLine(PromoState.Success(now + 40 * day), free, signedOut = false)!!
        assertEquals("Pro until 31 Oct 2026", ShippedCopy.render(justNow.value))
        val undated = promoLine(PromoState.Success(null), promo, signedOut = false)!!
        assertEquals("Pro until 21 Oct 2026", ShippedCopy.render(undated.value))
    }

    @Test
    fun `the open field's own states draw the field, not a closed line`() {
        assertNull(promoLine(PromoState.Editing(""), promo, signedOut = true))
        assertNull(promoLine(PromoState.Applying("PT"), promo, signedOut = true))
        assertNull(promoLine(PromoState.Failed("PT", PromoRefusal.INVALID_CODE), promo, signedOut = true))
    }

    // ---- Bringing the opened promo row into view (fresh-device QA of 1.3.24, B1) ------------

    @Test
    fun `a row above the status bar band comes down to just below it`() {
        // 24-have-code: the label and field sat above the top edge, Apply and Cancel just under it.
        val clear = 130f
        val delta = promoRevealScroll(rowTop = -120f, rowBottom = 180f, clearTop = clear, viewportBottom = 1400f)
        assertEquals(-250f, delta, 0f)
        assertEquals("the row's top lands on the clear line", clear, -120f - delta, 0f)
    }

    @Test
    fun `a row under the keyboard goes up only as far as its bottom edge`() {
        val delta = promoRevealScroll(rowTop = 1200f, rowBottom = 1500f, clearTop = 130f, viewportBottom = 1400f)
        assertEquals(100f, delta, 0f)
    }

    @Test
    fun `a row already whole on screen is left where it is`() {
        assertEquals(0f, promoRevealScroll(rowTop = 400f, rowBottom = 700f, clearTop = 130f, viewportBottom = 1400f), 0f)
        assertEquals("touching both edges is still whole", 0f, promoRevealScroll(130f, 1400f, 130f, 1400f), 0f)
    }

    @Test
    fun `a row taller than the room keeps its label and field in view, not its buttons`() {
        // Font scale 1.3 on a short phone with the keyboard up: 600px of room, a 700px row.
        val below = promoRevealScroll(rowTop = 500f, rowBottom = 1200f, clearTop = 100f, viewportBottom = 700f)
        assertEquals("the top goes to the clear line", 400f, below, 0f)
        val above = promoRevealScroll(rowTop = -50f, rowBottom = 650f, clearTop = 100f, viewportBottom = 700f)
        assertEquals(-150f, above, 0f)
    }

    @Test
    fun `a stake plan says there is nothing to do and offers no pay`() {
        val rows = planRows(stake, null, now)
        assertEquals(R.string.you_plan_source_stake, words(rows[0].value))
        assertEquals(R.string.you_plan_until_stake, words(rows[1].value))
        assertEquals(R.string.you_plan_extend_stake, words(rows[2].value))
        assertTrue(rows.none { it.action == PlanAction.GET_PRO || it.action == PlanAction.EXTEND })
        assertEquals("This wallet has 38,406.150222 SKR staked.", ShippedCopy.render(rows.last().value))
    }

    @Test
    fun `a free plan names the open example and what pro adds`() {
        val rows = planRows(free, HeroAction.GET_PRO, now)
        assertEquals(listOf(R.string.you_plan_free_label, R.string.you_pro_label, R.string.you_stake_label), labels(rows))
        assertEquals("The full read for AAPL, open to everyone. No end date.", ShippedCopy.render(rows[0].value))
    }

    @Test
    fun `refresh is offered where the old wallet block offered it, and on a failed read`() {
        assertEquals(PlanAction.REFRESH, planRows(free, HeroAction.GET_PRO, now).first().action)
        assertEquals(PlanAction.REFRESH, planRows(pass, HeroAction.EXTEND, now).first().action)
        val noWallet = free.copy(walletConnected = false)
        assertNull(planRows(noWallet, HeroAction.GET_PRO, now).first().action)
        val failed = noWallet.copy(entitlementFailed = true)
        assertEquals(PlanAction.REFRESH, planRows(failed, HeroAction.GET_PRO, now).first().action)
    }

    @Test
    fun `the stake row states the reason when there is no figure, never a zero`() {
        val rows = planRows(free.copy(walletConnected = false), null, now)
        assertEquals(R.string.pro_stake_disconnected, words(rows.last().value))
        val inFlight = planRows(free.copy(stakeRaw = null), null, now)
        assertEquals(R.string.state_loading, words(inFlight.last().value))
    }

    @Test
    fun `a pending payment gets its own plan row`() {
        val rows = planRows(free.copy(pendingSignature = "sig"), null, now)
        assertEquals(R.string.you_plan_payment_label, words(rows.last().label))
        assertEquals(R.string.pro_payment_pending, words(rows.last().value))
    }

    // ---- Device facts, notifications ---------------------------------------------------------

    @Test
    fun `the device facts are already formatted, never a raw int`() {
        val facts = deviceFacts(YouUiState(swapsRecorded = 1_234, votesCast = 0, stocksWatched = 7))
        assertEquals("1,234", facts.swaps)
        assertEquals("0", facts.votes)
        assertEquals("7", facts.watched)
    }

    @Test
    fun `the notifications line matches the watchlist s own words`() {
        assertEquals(R.string.watchlist_notifications_on, words(notificationLine(true)))
        assertEquals(R.string.watchlist_notifications_off, words(notificationLine(false)))
    }

    @Test
    fun `the stocks watched sub names the destination it actually opens, not the folded Watchlist tab`() {
        // HomeTab.WATCHLIST maps to AmberDestination.TODAY (HomeScreen.kt's toAmberDestination):
        // Watchlist folded into Today, and tapping this row opens Today, so the copy must say Today.
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

    // ---- About's links (mock judges' round 2) ------------------------------------------------------

    @Test
    fun `about links name plainticker's own privacy, terms and account deletion pages`() {
        assertEquals("https://www.plainticker.com/en/privacy", AboutLinks.PRIVACY)
        assertEquals("https://www.plainticker.com/en/terms", AboutLinks.TERMS)
        assertEquals("https://www.plainticker.com/en/account#delete", AboutLinks.DELETE_ACCOUNT)
        listOf(AboutLinks.PRIVACY, AboutLinks.TERMS, AboutLinks.DELETE_ACCOUNT).forEach {
            assertTrue("$it must be https on plainticker.com", it.startsWith("https://www.plainticker.com/"))
        }
    }

    @Test
    fun `the wallet group's words say it signs and is not a sign-in`() {
        assertEquals("Wallet", ShippedCopy.strings.getValue("you_heading_wallet"))
        val short = ShippedCopy.strings.getValue("you_wallet_note_short")
        assertTrue(short.contains("Not a sign-in"))
    }

    // ---- Whose Pro it is (2026-09-29: Pro belongs to the Google account, not the phone) --------

    private val ownerOut = planOwner(signedOut)
    private val ownerIn = planOwner(signedIn)

    @Test
    fun `the plan's owner follows the account state, and says nothing while it is still being read`() {
        assertEquals(PlanOwner(signedIn = false), ownerOut)
        assertEquals(PlanOwner(signedIn = true, account = signedIn.account), ownerIn)
        assertEquals(true, planOwner(signedIn.copy(movedToAccount = true))?.moved)
        assertNull(planOwner(AccountUiState.Restoring))
        assertNull(planOwner(AccountUiState.SigningIn))
        assertNull("no owner, no line", planRows(pass, HeroAction.EXTEND, now, owner = null)[0].sub)
    }

    @Test
    fun `signed out, a pass on this phone is saved to it until a sign-in moves it to the account`() {
        val source = planRows(pass, HeroAction.EXTEND, now, owner = ownerOut)[0]
        assertEquals(R.string.pro_saved_to_phone, words(source.sub!!))
        assertEquals(
            "Saved to this phone until you sign in. Signing in moves it to your Google account.",
            ShippedCopy.render(source.sub!!),
        )
    }

    @Test
    fun `signed out, a promo says it once, in its own row, and a stake never, since no sign-in moves a wallet`() {
        assertNull(planRows(promo, null, now, owner = ownerOut)[0].sub)
        assertEquals(R.string.pro_saved_to_phone, words(promoLine(PromoState.Idle, promo, signedOut = true)!!.sub!!))
        assertNull(planRows(stake, null, now, owner = ownerOut)[0].sub)
    }

    @Test
    fun `signed in, the source names the Google account by its email`() {
        for (plan in listOf(pass, stake, subscription, promo)) {
            val source = planRows(plan, null, now, owner = ownerIn)[0]
            assertEquals(R.string.you_plan_on_account, words(source.sub!!))
            assertEquals("On the Google account ann@example.com.", ShippedCopy.render(source.sub!!))
        }
        val noEmail = planOwner(AccountUiState.SignedIn(SignedInAccount(null, "Ann", emptyList())))
        assertEquals(R.string.you_plan_on_account_unnamed, words(planRows(pass, null, now, owner = noEmail)[0].sub!!))
        assertEquals("On your Google account.", ShippedCopy.strings.getValue("you_plan_on_account_unnamed"))
    }

    @Test
    fun `right after a sign-in that moved this phone's Pro, the source says it moved`() {
        val moved = planOwner(signedIn.copy(movedToAccount = true))
        val source = planRows(pass, null, now, owner = moved)[0]
        assertEquals(R.string.pro_moved_to_account, words(source.sub!!))
        assertEquals("Moved to your Google account.", ShippedCopy.render(source.sub!!))
    }

    @Test
    fun `a phone that is not Pro draws no owner line at all`() {
        assertTrue(planRows(free, HeroAction.GET_PRO, now, owner = ownerOut).none { (it.sub as? Copy.Words)?.id == R.string.pro_saved_to_phone })
        assertTrue(planRows(free, HeroAction.GET_PRO, now, owner = ownerIn).none { (it.sub as? Copy.Words)?.id == R.string.you_plan_on_account })
    }
}
