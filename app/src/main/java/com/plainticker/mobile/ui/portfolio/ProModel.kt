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

/** Whether the entitlement banner offers a way to ask again: only the failure carries one. */
fun entitlementRetries(state: ProUiState): Boolean = state.entitlementFailed
