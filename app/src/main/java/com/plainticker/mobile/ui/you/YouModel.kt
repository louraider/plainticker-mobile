package com.plainticker.mobile.ui.you

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.portfolio.payOffered
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.watchlist.digestFooter
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.wallet.WalletAccount
import com.plainticker.mobile.watchlist.DigestRecord

/**
 * What You says, decided away from the composition the way every other screen's model is
 * (docs/plan-app-uiux-2026-09-21.md, task U1). Two rules live here and nowhere else:
 *
 * 1. **The Pro fact is a word, not the sentence Portfolio used to draw.** [proFact] answers the
 *    same states [com.plainticker.mobile.ui.portfolio.entitlementLine] does, one word at a time,
 *    for the FactGrid cell DESIGN.md's grid rule wants: a label and a mono value.
 * 2. **The button matrix is exhaustive and reuses the one guard that already exists.**
 *    [youActions] never invents its own answer for whether Pay may be offered; it asks
 *    [payOffered], the same predicate [com.plainticker.mobile.ui.pass.PassViewModel.pay] itself
 *    checks, so the two can never disagree about a state the server has already refused.
 */

/** The Wallet block's value: a mono key fragment, or the state word for no session. */
fun walletValue(account: WalletAccount?): Copy =
    account?.let { raw(Fmt.shortKey(it.address)) } ?: words(R.string.you_wallet_none)

/** The Pro fact and its "until" sub line, null when the source carries no date. */
data class ProFact(val value: Copy, val until: Copy?)

fun proFact(state: ProUiState): ProFact = when {
    state.entitlementLoading -> ProFact(words(R.string.state_loading), null)
    state.entitlementDisabled -> ProFact(words(R.string.you_pro_disabled), null)
    state.entitlementFailed -> ProFact(words(R.string.you_pro_unread), null)
    state.pro -> proFactSourced(state)
    else -> ProFact(words(R.string.you_pro_none), null)
}

private fun proFactSourced(state: ProUiState): ProFact {
    val until = state.untilMillis?.let { words(R.string.you_pro_until, Fmt.utc(it)) }
    return when (state.source) {
        EntitlementSource.PASS -> ProFact(words(R.string.you_pro_pass), until)
        EntitlementSource.STAKE -> ProFact(words(R.string.you_pro_stake), null)
        EntitlementSource.SUBSCRIPTION -> ProFact(words(R.string.you_pro_subscription), until)
        null -> ProFact(words(R.string.you_pro_active), null)
    }
}

/** The Staked SKR fact: a short value word, and the figure or the reason as its sub line. */
data class StakeFact(val value: Copy, val sub: Copy?)

/**
 * The wallet's own staked SKR, never a verdict.
 *
 * The value is always a short word, never the figure itself, for the same reason [proFact]'s
 * value always is: FactCellView draws a cell's value `maxLines = 1, softWrap = false` (FactGrid.kt),
 * so anything the mono glyphs do not fit is clipped mid-character rather than wrapped. A plausible
 * stake can run to 20 characters near [SkrStakeBound.STAKED_SUPPLY_RAW]'s own worst case, well past
 * the roughly 10 characters a half-width cell fits at the default 24sp value size (v0.12.0, on the
 * Seeker at default font scale: "no wallet connected" clipped to "no wallet c"), so the figure, and
 * the reason when there is none, are the sub line instead, which wraps and is never clipped.
 *
 * "No wallet" is deliberately not the same word as a zero: a wallet that stakes nothing and a
 * wallet this screen never read are different facts, the same distinction
 * [SkrStakeBound.isPlausible] draws for a principal outside its own bound, so the value names the
 * missing subject rather than reading as a reading of zero.
 */
fun stakeFact(state: ProUiState): StakeFact = when {
    !state.walletConnected ->
        StakeFact(words(R.string.you_stake_no_wallet), words(R.string.you_stake_none_wallet))
    state.stakeUnread -> StakeFact(words(R.string.you_pro_unread), null)
    state.stakeRaw == null -> StakeFact(words(R.string.state_loading), null)
    else -> StakeFact(
        words(R.string.you_stake_read),
        raw(Fmt.tokenAmount(state.stakeRaw, SkrStakeBound.SKR_DECIMALS)),
    )
}

/** Which action a button in the Action section performs, so the label never has to be parsed. */
enum class YouActionKind { CONNECT, PAY }

data class YouAction(val kind: YouActionKind, val label: Copy)

/**
 * Exactly one 56dp button per state (plan section 1.2's matrix): no wallet and not Pro offers
 * Connect wallet plus Pay for Pro when it is offered; no wallet and Pro offers only Connect
 * wallet; a wallet that is not Pro offers Pay for Pro when it is offered; a wallet that is
 * already Pro offers no button at all. Never two Accent fills: [secondary] is non-null only
 * beside a [primary] that is Connect wallet, never beside Pay for Pro.
 */
data class YouActions(val primary: YouAction?, val secondary: YouAction?)

fun youActions(state: ProUiState): YouActions {
    val connect = YouAction(YouActionKind.CONNECT, words(R.string.action_connect_wallet))
    val pay = YouAction(YouActionKind.PAY, words(R.string.pass_action)).takeIf { payOffered(state) }
    return when {
        !state.walletConnected && !state.pro -> YouActions(connect, pay)
        !state.walletConnected -> YouActions(connect, null)
        !state.pro -> YouActions(pay, null)
        else -> YouActions(null, null)
    }
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
