package com.plainticker.mobile.ui.portfolio

import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.words

/**
 * What the Portfolio's Pro block says (task A6), decided away from the composition the way every
 * other sentence on this screen is ([PortfolioModel]).
 *
 * Two rules live here and nowhere else:
 *
 * 1. **The entitlement sentence names its source honestly.** A pass and a subscription carry a
 *    date they end; a stake does not, because it is re-evaluated continuously rather than granted
 *    once (server/vote/README.md). None of the three is ever collapsed into a bare "Pro" badge.
 * 2. **A stake is a figure, never a verdict.** Below the entitlement threshold or above it, the
 *    sentence states what this wallet has staked and stops there: it does not say whether that is
 *    enough, because that judgment belongs to the server's own resolver, not to this screen.
 */
fun entitlementLine(state: ProUiState): Copy = when {
    state.entitlementLoading -> words(R.string.pro_entitlement_loading)
    state.entitlementDisabled -> words(R.string.pro_entitlement_disabled)
    state.entitlementFailed -> words(R.string.pro_entitlement_failed)
    state.pro -> proLine(state)
    else -> words(R.string.pro_entitlement_none)
}

private fun proLine(state: ProUiState): Copy {
    val until = state.untilMillis?.let(Fmt::utc)
    return when (state.source) {
        EntitlementSource.PASS ->
            if (until != null) words(R.string.pro_entitlement_pass_until, until) else words(R.string.pro_entitlement_pass)
        EntitlementSource.STAKE -> words(R.string.pro_entitlement_stake)
        EntitlementSource.SUBSCRIPTION ->
            if (until != null) {
                words(R.string.pro_entitlement_subscription_until, until)
            } else {
                words(R.string.pro_entitlement_subscription)
            }
        null -> words(R.string.pro_entitlement_active)
    }
}

/**
 * The wallet's own staked SKR, stated as a figure: what this device could read, or why it could
 * not, or that nothing is connected to read. Null only while a connected wallet's read is still in
 * flight, so the block draws nothing rather than a placeholder that would flash before the figure.
 */
fun stakeLine(state: ProUiState): Copy? = when {
    !state.walletConnected -> words(R.string.pro_stake_disconnected)
    state.stakeUnread -> words(R.string.pro_stake_unread)
    state.stakeRaw == null -> null
    else -> words(R.string.pro_stake_read, Fmt.tokenAmount(state.stakeRaw, SkrStakeBound.SKR_DECIMALS))
}

/**
 * Whether the entitlement banner offers a way to ask again.
 *
 * Only [ProUiState.entitlementFailed] carries one: a failed *read* is a call that did not answer
 * and a later tap could plausibly end differently, but [ProUiState.entitlementDisabled] is the
 * server itself saying the route is not turned on, and asking it again returns the same answer.
 * The two are kept apart on purpose (see [payOffered]): a failure and a refusal are different
 * facts and must not both suppress the same things.
 */
fun entitlementRetries(state: ProUiState): Boolean = state.entitlementFailed

/**
 * Whether the Pay action may be offered at all.
 *
 * False exactly where paying is known, in advance, to end in a refusal: [ProUiState.entitlementDisabled]
 * means the server has already said `503 monetization_disabled` for this exact feature, and
 * `POST /api/v1/pass/build` is gated on the same flag, so a payment attempt here could only ever
 * be refused too, after first sending the reader through the wallet's own connect sheet for
 * nothing (a review finding on a v0.7.0 release build: the entitlement line already said "Pro is
 * not offered by this server yet" while Pay stood directly under it). A pending payment is the
 * other reason, unrelated to this one: never a second payment while the first might still land.
 *
 * An entitlement read that merely *failed* (`entitlementFailed`) is not the same fact and does not
 * belong on this list: `/pass/build` is its own call with its own success or failure, so a reader
 * whose entitlement check timed out is still owed the chance to try paying, and a refusal there
 * surfaces through the pay sheet's own states exactly as it always has.
 */
fun payOffered(state: ProUiState): Boolean = !state.entitlementDisabled && state.pendingSignature == null

/**
 * A payment this device has signed and sent but not yet seen the server confirm (task A6 review),
 * or null when none is pending. Drawn in place of the Pay action, never beside it: offering a
 * second payment while the first might still land would risk paying twice for one pass.
 */
fun pendingPaymentLine(state: ProUiState): Copy? =
    if (state.pendingSignature != null) words(R.string.pro_payment_pending) else null
