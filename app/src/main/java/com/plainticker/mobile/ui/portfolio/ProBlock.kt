package com.plainticker.mobile.ui.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * The Portfolio's Pro block (task A6): the wallet's staked SKR read the way the app already
 * reads it, and this device's entitlement from the server, honest about which of the three
 * sources carries it.
 *
 * Additive only. Nothing above this block in [PortfolioContent] changes shape or state because
 * this exists: the hard invariant task A6 names is that every block this screen already draws
 * keeps drawing exactly as it does, and this block is proof of that by construction, appended
 * after "Recent swaps" rather than woven into anything that came before it.
 *
 * [entitlementLine] and [stakeLine] both carry a numeral (a date, a token amount), so both are
 * set in the mono `meta` style, the same one [com.plainticker.mobile.ui.detail.costLine] uses for
 * its own numeral-bearing sentence: DESIGN.md section 3 keeps every number out of Outfit, and that
 * rule does not relax for a number sitting inside a longer sentence.
 *
 * A pending payment (task A6 review: a landed signature this device has not seen confirmed yet,
 * [PassReceiptStore]) replaces the Pay action with its own sentence rather than standing beside
 * it: a second payment is never offered while one might still be waiting on the chain or the cron.
 */
@Composable
fun ProBlock(state: ProUiState, onPay: (() -> Unit)?, onRetryEntitlement: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = entitlementLine(state).text(), style = PlainTickerType.meta, color = Ink)
        stakeLine(state)?.let {
            Text(text = it.text(), style = PlainTickerType.meta, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        pendingPaymentLine(state)?.let {
            Text(text = it.text(), style = PlainTickerType.small, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = ActionsTop), horizontalArrangement = Arrangement.End) {
            if (entitlementRetries(state)) {
                TextAction(label = stringResource(R.string.action_retry), onClick = onRetryEntitlement)
            }
            if (onPay != null && state.pendingSignature == null) {
                TextAction(label = stringResource(R.string.pass_action), onClick = onPay)
            }
        }
    }
}

private val Side = 20.dp
private val ActionsTop = 10.dp
