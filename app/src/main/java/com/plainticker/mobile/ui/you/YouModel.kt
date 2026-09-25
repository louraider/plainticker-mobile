package com.plainticker.mobile.ui.you

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.pass.ProUiState
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
        EntitlementSource.PASS, EntitlementSource.SUBSCRIPTION ->
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
            EntitlementSource.PASS, EntitlementSource.SUBSCRIPTION ->
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

// ---- The Plan group ------------------------------------------------------------------------

/** What a Plan row's one text action does, so the label is never parsed to find out. */
enum class PlanAction { GET_PRO, EXTEND, REFRESH }

data class PlanRow(val label: Copy, val value: Copy, val sub: Copy? = null, val action: PlanAction? = null)

/**
 * The Plan group: source, valid until, how to extend, then the stake figure and a pending
 * payment. [heroAction] is what the hero already draws, so the pay entry lands here only when the
 * hero does not carry it (a reader with no identity yet still reaches the pass flow).
 */
fun planRows(pro: ProUiState, heroAction: HeroAction?, nowMillis: Long): List<PlanRow> {
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
        else -> rows += proRows(pro, pay, refresh, nowMillis)
    }
    rows += PlanRow(words(R.string.you_stake_label), stakeLine(pro) ?: words(R.string.state_loading))
    pendingPaymentLine(pro)?.let { rows += PlanRow(words(R.string.you_plan_payment_label), it) }
    return rows
}

private fun freeRows(pay: PlanAction?): List<PlanRow> = listOf(
    PlanRow(words(R.string.you_plan_free_label), words(R.string.you_plan_free_value, OPEN_EXAMPLE_TICKER)),
    PlanRow(words(R.string.you_pro_label), words(R.string.you_plan_pro_value), action = pay),
)

private fun proRows(pro: ProUiState, pay: PlanAction?, refresh: PlanAction?, nowMillis: Long): List<PlanRow> {
    val source = when (pro.source) {
        EntitlementSource.PASS -> R.string.you_plan_source_pass
        EntitlementSource.STAKE -> R.string.you_plan_source_stake
        EntitlementSource.SUBSCRIPTION -> R.string.you_plan_source_subscription
        null -> R.string.you_plan_source_unnamed
    }
    val rows = mutableListOf(PlanRow(words(R.string.you_plan_source_label), words(source), action = refresh))
    when (pro.source) {
        EntitlementSource.STAKE -> {
            rows += PlanRow(words(R.string.you_plan_until_label), words(R.string.you_plan_until_stake))
            rows += PlanRow(words(R.string.you_plan_extend_label), words(R.string.you_plan_extend_stake))
        }
        EntitlementSource.PASS, EntitlementSource.SUBSCRIPTION -> {
            pro.untilMillis?.let {
                rows += PlanRow(words(R.string.you_plan_until_label), raw(Fmt.utc(it)), sub = daysLeft(it, nowMillis))
            }
            val extend = if (pro.source == EntitlementSource.PASS) R.string.you_plan_extend_pass else R.string.you_plan_extend_subscription
            rows += PlanRow(words(R.string.you_plan_extend_label), words(extend), action = pay)
        }
        null -> Unit
    }
    return rows
}

// ---- On this device, notifications, about -------------------------------------------------

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
