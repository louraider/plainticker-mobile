package com.plainticker.mobile.ui.you

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.pass.PromoState
import com.plainticker.mobile.ui.portfolio.payOffered
import com.plainticker.mobile.ui.portfolio.pendingPaymentLine
import com.plainticker.mobile.ui.portfolio.stakeLine
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.watchlist.digestFooter
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.wallet.WalletAccount
import com.plainticker.mobile.watchlist.DigestRecord
import java.time.Instant
import java.time.ZoneOffset

/**
 * What You says, decided away from the composition the way every other screen's model is. Since
 * the cabinet pass (2026-09-25, matching plainticker.com's own account cabinet, web PR #145) the
 * screen is one hero card and then calm row groups; this file decides both.
 *
 * Three rules live here and nowhere else:
 *
 * 1. **The hero names who, then the plan as a headline, then at most one action.** [youHero] picks
 *    the identity (the Google account, else the connected wallet, else "Not signed in"), the plan
 *    headline ("Pro until 20 Oct 2026", "Pro while staked", "Free") and the one primary action for
 *    the state, exhaustively. Never two amber fills on the screen: every other action on You is a
 *    text action.
 * 2. **Pay is offered only where the one existing guard allows it.** Both the hero and the Plan
 *    group ask [payOffered], the same predicate [com.plainticker.mobile.ui.pass.PassViewModel.pay]
 *    itself checks, so neither can offer a payment the server has refused or one that might double
 *    a payment still landing. The Plan group carries the pay entry only when the hero does not, so
 *    it is always reachable and never drawn twice.
 * 3. **Free copy names the open example from one constant.** [OPEN_EXAMPLE_TICKER] is the only
 *    ticker open to everyone (the web's `lib/billing/gate.ts`); the copy is formatted from it so it
 *    cannot drift into "five preset tickers" again.
 */

/**
 * The one ticker open to everyone as a full example, the web's `OPEN_EXAMPLE_TICKER`
 * (`lib/billing/gate.ts`). Every Free sentence on You is formatted from this, never retyped.
 */
const val OPEN_EXAMPLE_TICKER = "AAPL"

// ---- The hero ------------------------------------------------------------------------------

/** How the hero's identity line is drawn: words, an email or name, or a wallet key in mono. */
enum class IdentityKind { NONE, GOOGLE, WALLET }

/** The hero's one action. Exhaustive: a state with none draws no button at all. */
enum class HeroAction { SIGN_IN, SIGNING_IN, GET_PRO, EXTEND }

data class YouHero(
    val identityKind: IdentityKind,
    /** A small label above the identity ("Signed in with Google", "Solana wallet"), or null. */
    val identityLabel: Copy?,
    val identity: Copy,
    val headline: Copy,
    /** Supporting lines under the headline: days left, what Free opens, a pending payment. */
    val lines: List<Copy>,
    val action: HeroAction?,
    /** Connect wallet as a text action under Sign in, only when there is no identity at all. */
    val offersConnect: Boolean,
)

/**
 * The hero for every state. [nowMillis] is passed in, never read here, so the days-left line is a
 * pure function of its inputs; dates are UTC, the same clock the Plan group's "Valid until" row
 * states in full, so the headline's day and that row's timestamp can never disagree.
 *
 * The action matrix:
 * - **No identity** (not signed in, no wallet): Sign in with Google, with Connect wallet as a
 *   text action beside it. While the stored account is still being read, nothing, so no button
 *   flashes; while a sign-in is in flight, the same slot shows a disabled "Signing in".
 * - **Free** (including a server that did not answer the entitlement read): Get Pro, when
 *   [payOffered]. A server that has switched Pro off offers nothing.
 * - **Pro by pass**: Extend Pro, when [payOffered].
 * - **Pro by stake, by a web subscription, or with no named source**: none.
 * - **Still reading the plan**: none.
 */
fun youHero(account: AccountUiState, wallet: WalletAccount?, pro: ProUiState, nowMillis: Long): YouHero {
    val signedIn = account as? AccountUiState.SignedIn
    val kind = when {
        signedIn != null -> IdentityKind.GOOGLE
        wallet != null -> IdentityKind.WALLET
        else -> IdentityKind.NONE
    }
    val identityLabel = when (kind) {
        IdentityKind.GOOGLE -> words(R.string.account_signed_in_label)
        IdentityKind.WALLET -> words(R.string.you_wallet_method)
        IdentityKind.NONE -> null
    }
    val identity = when {
        signedIn != null -> accountIdentity(signedIn.account)
        wallet != null -> raw(Fmt.shortKey(wallet.address))
        else -> words(R.string.you_identity_none)
    }
    val action = if (kind == IdentityKind.NONE) {
        when (account) {
            is AccountUiState.SignedOut -> HeroAction.SIGN_IN
            AccountUiState.SigningIn -> HeroAction.SIGNING_IN
            else -> null
        }
    } else {
        planAction(pro)
    }
    return YouHero(
        identityKind = kind,
        identityLabel = identityLabel,
        identity = identity,
        headline = heroHeadline(pro),
        lines = heroLines(pro, nowMillis),
        action = action,
        offersConnect = action == HeroAction.SIGN_IN,
    )
}

/** The pay action a plan state offers, if any, before identity is considered. */
private fun planAction(pro: ProUiState): HeroAction? = when {
    pro.entitlementLoading -> null
    !payOffered(pro) -> null
    !pro.pro -> HeroAction.GET_PRO
    pro.source == EntitlementSource.PASS -> HeroAction.EXTEND
    else -> null
}

private fun heroHeadline(pro: ProUiState): Copy = when {
    pro.entitlementLoading -> words(R.string.you_hero_loading)
    pro.entitlementDisabled -> words(R.string.you_hero_free)
    pro.entitlementFailed -> words(R.string.you_hero_unread)
    pro.pro -> when (pro.source) {
        EntitlementSource.STAKE -> words(R.string.you_hero_stake)
        EntitlementSource.PASS, EntitlementSource.SUBSCRIPTION, EntitlementSource.PROMO ->
            pro.untilMillis?.let { words(R.string.you_hero_pro_until, utcDay(it)) } ?: words(R.string.you_hero_pro)
        null -> words(R.string.you_hero_pro)
    }
    else -> words(R.string.you_hero_free)
}

private fun heroLines(pro: ProUiState, nowMillis: Long): List<Copy> {
    val lines = mutableListOf<Copy>()
    when {
        pro.entitlementLoading -> Unit
        pro.entitlementDisabled -> lines += words(R.string.pro_entitlement_disabled)
        pro.entitlementFailed -> lines += words(R.string.pro_entitlement_failed)
        pro.pro -> when (pro.source) {
            EntitlementSource.STAKE -> lines += words(R.string.you_hero_stake_line)
            EntitlementSource.PASS, EntitlementSource.SUBSCRIPTION, EntitlementSource.PROMO ->
                pro.untilMillis?.let { lines += daysLeft(it, nowMillis) }
            null -> Unit
        }
        else -> lines += words(R.string.you_hero_free_line, OPEN_EXAMPLE_TICKER)
    }
    pendingPaymentLine(pro)?.let { lines += it }
    return lines
}

/**
 * "26 days left.", "1 day left.", or "Ends today." once the end falls on today's UTC date or has
 * passed (the server's own read decides whether Pro still holds; this line never says it ended).
 */
fun daysLeft(untilMillis: Long, nowMillis: Long): Copy {
    val days = Fmt.daysAhead(untilMillis, nowMillis, ZoneOffset.UTC)
    return if (days <= 0) {
        words(R.string.you_ends_today)
    } else {
        val n = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        counted(R.plurals.you_days_left, n, Fmt.count(n))
    }
}

/** A calendar day in UTC, "20 Oct 2026", the headline's date. */
private fun utcDay(epochMillis: Long): String = Fmt.day(Instant.ofEpochMilli(epochMillis).atOffset(ZoneOffset.UTC).toLocalDate())

/**
 * The confirmation line a successful promo redeem shows, before the field collapses: the hero's
 * own "Pro until" sentence, so the one-off confirmation and the Plan group's own headline never
 * disagree on the wording for the same fact.
 */
fun promoSuccessLine(untilMillis: Long?): Copy =
    untilMillis?.let { words(R.string.you_hero_pro_until, utcDay(it)) } ?: words(R.string.you_hero_pro)

/**
 * What the Plan group's first row says while the code field is closed: [PromoState.Idle] and
 * [PromoState.Success]; null for the open field's own states, which draw the field instead.
 * "Have a code?" stays beside it in every case this returns (fresh-device QA of 1.3.24, B4: right
 * after a redeem the row said "Enter another code to add more time" with no way to).
 *
 * The Pro this row describes is read from the entitlement ([ProUiState]), not only from the
 * redeem that just ran (fresh-device QA of 1.3.24, B3: after a restart the row went back to "A
 * promo code adds Pro time" under a hero that said "Pro until"). [signedOut] is the only reason
 * for a second line: before a redeem it says signing in first keeps the Pro past a reinstall, and
 * once a promo Pro is on this phone it says where that Pro lives.
 */
data class PromoLine(val value: Copy, val sub: Copy?, val quiet: Boolean)

fun promoLine(promo: PromoState, pro: ProUiState, signedOut: Boolean): PromoLine? {
    val promoUntil = pro.untilMillis.takeIf { promoPro(pro) }
    return when {
        promo is PromoState.Success -> held(promo.untilMillis ?: promoUntil, signedOut)
        promo !is PromoState.Idle -> null
        promoPro(pro) -> held(promoUntil, signedOut)
        else -> PromoLine(
            value = words(R.string.promo_prompt),
            sub = if (signedOut) words(R.string.promo_signin_first_hint) else null,
            quiet = true,
        )
    }
}

/**
 * How far You's list scrolls so the open promo row (label, field, hint, error, Apply and Cancel)
 * stands whole between the status bar band and the keyboard, for a field opened from somewhere
 * other than its own row: the Pay sheet's "Have a code?" and Detail's "Have a code? Get Pro"
 * (fresh-device QA of 1.3.24, B1: from the sheet the field and hint sat above the top edge, and
 * only Apply and Cancel showed).
 *
 * Every figure is in pixels in the list viewport's own coordinates, measured once the keyboard has
 * finished rising: [rowTop] and [rowBottom] the row's edges, [clearTop] where the band the clock
 * and its scrim cover ends, [viewportBottom] the list's bottom edge (the keyboard's top while it
 * is up). The answer is what [androidx.compose.foundation.lazy.LazyListState.scrollBy] takes:
 * positive moves the content up, negative brings it down, 0 leaves a row already in view alone.
 * A row taller than the room between the two edges keeps its top (the label and the field) in
 * view rather than its buttons.
 */
fun promoRevealScroll(rowTop: Float, rowBottom: Float, clearTop: Float, viewportBottom: Float): Float {
    val room = viewportBottom - clearTop
    return when {
        rowTop < clearTop -> rowTop - clearTop
        rowBottom - rowTop > room -> rowTop - clearTop
        rowBottom > viewportBottom -> rowBottom - viewportBottom
        else -> 0f
    }
}

/** This device's plan, as the server last answered it, is Pro through a promo code. */
private fun promoPro(pro: ProUiState): Boolean =
    pro.entitlementKnown && pro.pro && pro.source == EntitlementSource.PROMO

private fun held(untilMillis: Long?, signedOut: Boolean) = PromoLine(
    value = promoSuccessLine(untilMillis),
    sub = if (signedOut) words(R.string.pro_saved_to_phone) else null,
    quiet = false,
)

// ---- The Plan group ------------------------------------------------------------------------

/** What a Plan row's one text action does, so the label is never parsed to find out. */
enum class PlanAction { GET_PRO, EXTEND, REFRESH }

data class PlanRow(val label: Copy, val value: Copy, val sub: Copy? = null, val action: PlanAction? = null)

/**
 * Who the Pro on this phone belongs to (2026-09-29, the founder's rule: Pro belongs to the Google
 * account or to a wallet, never to the phone). Signed in, the server answers with that account's
 * Pro alone, so the Plan's source names the account; signed out, any Pro left is this phone's own
 * and moves to the account at the next sign-in. Null when the account is still being read, which
 * says neither.
 */
data class PlanOwner(
    val signedIn: Boolean,
    /** The signed-in account, for its email; null when signed out. */
    val account: SignedInAccount? = null,
    /** This sign-in moved the phone's own Pro to the account ([AccountUiState.SignedIn.movedToAccount]). */
    val moved: Boolean = false,
)

fun planOwner(account: AccountUiState): PlanOwner? = when (account) {
    is AccountUiState.SignedIn -> PlanOwner(signedIn = true, account = account.account, moved = account.movedToAccount)
    is AccountUiState.SignedOut -> PlanOwner(signedIn = false)
    AccountUiState.Restoring, AccountUiState.SigningIn -> null
}

/**
 * The line under the Plan's source: whose Pro it is. Signed in, the account by its email, or
 * "Moved to your Google account." right after a sign-in that moved the phone's own Pro there.
 * Signed out, a pass or an unnamed source is this phone's own until it signs in; a promo says the
 * same in its own row just above ([promoLine]), and a stake belongs to the wallet, which no
 * sign-in moves, so neither repeats it here.
 */
private fun ownerLine(owner: PlanOwner?, source: EntitlementSource?): Copy? = when {
    owner == null -> null
    owner.signedIn && owner.moved -> words(R.string.pro_moved_to_account)
    owner.signedIn -> owner.account?.email?.takeIf { it.isNotBlank() }
        ?.let { words(R.string.you_plan_on_account, it) }
        ?: words(R.string.you_plan_on_account_unnamed)
    source == EntitlementSource.PASS || source == null -> words(R.string.pro_saved_to_phone)
    else -> null
}

/**
 * The Plan group: source, valid until, how to extend, then the stake figure and a pending
 * payment. [heroAction] is what the hero already draws, so the pay entry lands here only when the
 * hero does not carry it (a reader with no identity yet still reaches the pass flow). [owner]
 * names whose Pro it is under the source ([ownerLine]).
 */
fun planRows(pro: ProUiState, heroAction: HeroAction?, nowMillis: Long, owner: PlanOwner? = null): List<PlanRow> {
    val rows = mutableListOf<PlanRow>()
    val pay = planAction(pro).takeIf { it != heroAction }?.let {
        if (it == HeroAction.EXTEND) PlanAction.EXTEND else PlanAction.GET_PRO
    }
    val refresh = PlanAction.REFRESH.takeIf { pro.walletConnected || pro.entitlementFailed }
    when {
        pro.entitlementLoading ->
            rows += PlanRow(words(R.string.you_plan_status_label), words(R.string.pro_entitlement_loading))
        pro.entitlementDisabled ->
            rows += PlanRow(words(R.string.you_plan_status_label), words(R.string.pro_entitlement_disabled), action = refresh)
        pro.entitlementFailed -> {
            rows += PlanRow(words(R.string.you_plan_status_label), words(R.string.pro_entitlement_failed), action = PlanAction.REFRESH)
            rows += freeRows(pay)[1]
        }
        !pro.pro -> {
            val (free, proRow) = freeRows(pay)
            rows += free.copy(action = refresh)
            rows += proRow
        }
        else -> rows += proRows(pro, pay, refresh, owner)
    }
    rows += PlanRow(words(R.string.you_stake_label), stakeLine(pro) ?: words(R.string.state_loading))
    pendingPaymentLine(pro)?.let { rows += PlanRow(words(R.string.you_plan_payment_label), it) }
    return rows
}

private fun freeRows(pay: PlanAction?): List<PlanRow> = listOf(
    PlanRow(words(R.string.you_plan_free_label), words(R.string.you_plan_free_value, OPEN_EXAMPLE_TICKER)),
    PlanRow(words(R.string.you_pro_label), words(R.string.you_plan_pro_value), action = pay),
)

private fun proRows(pro: ProUiState, pay: PlanAction?, refresh: PlanAction?, owner: PlanOwner?): List<PlanRow> {
    val source = when (pro.source) {
        EntitlementSource.PASS -> R.string.you_plan_source_pass
        EntitlementSource.STAKE -> R.string.you_plan_source_stake
        EntitlementSource.SUBSCRIPTION -> R.string.you_plan_source_subscription
        EntitlementSource.PROMO -> R.string.you_plan_source_promo
        null -> R.string.you_plan_source_unnamed
    }
    val rows = mutableListOf(
        PlanRow(words(R.string.you_plan_source_label), words(source), sub = ownerLine(owner, pro.source), action = refresh),
    )
    when (pro.source) {
        EntitlementSource.STAKE -> {
            rows += PlanRow(words(R.string.you_plan_until_label), words(R.string.you_plan_until_stake))
            rows += PlanRow(words(R.string.you_plan_extend_label), words(R.string.you_plan_extend_stake))
        }
        EntitlementSource.PASS, EntitlementSource.SUBSCRIPTION, EntitlementSource.PROMO -> {
            // The days left are the hero's line under "Pro until" (heroLines), and only there:
            // drawn here too, "227 days left." read twice on one screen (device QA of 1.3.16).
            pro.untilMillis?.let {
                rows += PlanRow(words(R.string.you_plan_until_label), raw(Fmt.utc(it)))
            }
            // A promo does not renew, and it is not paid for ([planAction] offers no pay action
            // for it, so [pay] is already null here): its "How to extend" row is a plain sentence
            // rather than the pay action a pass or a subscription draws; the "Have a code?" action
            // just below (PlanGroup) is the plain way to.
            val extend = when (pro.source) {
                EntitlementSource.PASS -> R.string.you_plan_extend_pass
                EntitlementSource.PROMO -> R.string.you_plan_extend_promo
                else -> R.string.you_plan_extend_subscription
            }
            rows += PlanRow(words(R.string.you_plan_extend_label), words(extend), action = pay)
        }
        null -> Unit
    }
    return rows
}

// ---- On this device, notifications, about -------------------------------------------------

/**
 * The pages About links to on plainticker.com (mock judges' round 2; the dApp Store listing asks for a
 * privacy policy, terms and a way to delete the account). Each opens in the browser, English
 * locale, the same pages the web footer links. [DELETE_ACCOUNT] lands on the web account page's
 * own deletion section, where a signed-in reader can remove the account and what it holds.
 */
object AboutLinks {
    const val PRIVACY = "https://www.plainticker.com/en/privacy"
    const val TERMS = "https://www.plainticker.com/en/terms"
    const val DELETE_ACCOUNT = "https://www.plainticker.com/en/account#delete"
}

/** The three numerals "On this device" draws, each already formatted, never worked out on screen. */
data class DeviceFacts(val swaps: String, val votes: String, val watched: String)

fun deviceFacts(state: YouUiState): DeviceFacts = DeviceFacts(
    swaps = Fmt.count(state.swapsRecorded),
    votes = Fmt.count(state.votesCast),
    watched = Fmt.count(state.stocksWatched),
)

/**
 * The Watchlist footer's own delivery line (WatchlistModel.kt), so a device that will not show
 * notifications says so in the same words on both screens. [record] and the checked time are the
 * Watchlist's alone; You draws only whether the next digest will actually notify.
 */
fun notificationLine(notificationsOn: Boolean): Copy =
    digestFooter(record = DigestRecord.NONE, notificationsOn = notificationsOn, nowMillis = 0L).delivery

/**
 * One bundled font's attribution (task U11): a device-facing name and credit line, both authored
 * here and pinned to strings.xml like every other sentence on this screen, and the asset path of
 * the OFL text itself, read byte for byte rather than retyped as copy (docs/fonts.md's own
 * license section names the same three files this list points at).
 */
data class BundledFontLicense(@StringRes val nameRes: Int, @StringRes val creditRes: Int, val assetPath: String)

/**
 * The three faces the app ships today (docs/fonts.md): Outfit and JetBrains Mono from Instrument,
 * Bricolage Grotesque added for Amber. U11 was written before Amber's own typeface landed, so this
 * is the full, current list rather than the two the row assumed.
 */
val bundledFontLicenses: List<BundledFontLicense> = listOf(
    BundledFontLicense(R.string.you_license_outfit_name, R.string.you_license_outfit_credit, "licenses/outfit_ofl.txt"),
    BundledFontLicense(
        R.string.you_license_jetbrains_mono_name,
        R.string.you_license_jetbrains_mono_credit,
        "licenses/jetbrains_mono_ofl.txt",
    ),
    BundledFontLicense(
        R.string.you_license_bricolage_name,
        R.string.you_license_bricolage_credit,
        "licenses/bricolage_grotesque_ofl.txt",
    ),
)
