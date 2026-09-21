package com.plainticker.mobile.ui.you

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactGrid
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.PrimaryButton
import com.plainticker.mobile.ui.components.SecondaryButton
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.home.HomeTab
import com.plainticker.mobile.ui.pass.PassActions
import com.plainticker.mobile.ui.pass.PassSheet
import com.plainticker.mobile.ui.pass.PassViewModel
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.wallet.WalletAccount

/**
 * You (docs/plan-app-uiux-2026-09-21.md, task U1): what the app can vouch for about the reader.
 * Not a profile and not a nav destination of its own; reached from the TopBar's action on the
 * four tabs (DESIGN.md section 4) and left the way it was opened, back to the tab left
 * (HomeScreen.kt's BackHandler).
 *
 * The wallet, the pass and the stake, this device's own record, the notifications line the
 * Watchlist already draws, and the version and the disclaimer every screen owes a reader, in
 * that order. Nothing here computes: [YouModel.kt] picks every sentence and every numeral
 * arrives already formatted, the split every screen in this app keeps.
 *
 * The device's own code ([com.plainticker.mobile.prefs.DevicePassStore]) is a bearer credential
 * and never appears here or on any other screen: this file reads only [PassViewModel]'s already
 * resolved [ProUiState], never the store itself, and DeviceCodeNeverDrawnTest scans every
 * composable under ui/ so a later change cannot draw it by accident.
 */
@Composable
fun YouScreen(
    viewModel: YouViewModel,
    passViewModel: PassViewModel,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pro by passViewModel.pro.collectAsStateWithLifecycle()
    val pass by passViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The one piece of this screen that can change while the app is away: a reader who took the
    // Enable action went to the system settings and came back (the same rule WatchlistScreen
    // keeps for its own notifications line).
    LifecycleResumeEffect(viewModel) {
        viewModel.notificationsChanged()
        onPauseOrDispose { }
    }

    // A sibling of the LazyColumn, exactly as PortfolioScreen used to host it: the sheet is a
    // modal surface and draws in its own window, so where it sits in this tree does not matter,
    // only that it outlives the block that opened it.
    Box(modifier.fillMaxSize()) {
        YouContent(
            state = state,
            pro = pro,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onRefreshEntitlement = passViewModel::refreshEntitlement,
            onPay = passViewModel::pay,
            onOpenTab = onOpenTab,
            onEnableNotifications = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            header = header,
        )
        PassSheet(
            state = pass,
            actions = PassActions(
                onConfirm = passViewModel::confirm,
                onRetry = passViewModel::retry,
                onClose = passViewModel::close,
            ),
        )
    }
}

@Composable
internal fun YouContent(
    state: YouUiState,
    pro: ProUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshEntitlement: () -> Unit,
    onPay: () -> Unit,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onEnableNotifications: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val actions = youActions(pro)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "heading") { Heading(text = stringResource(R.string.you_heading)) }
        item(key = "wallet") {
            WalletBlock(account = state.account, onRefresh = onRefreshEntitlement, onDisconnect = onDisconnect)
        }
        item(key = "pro-grid") { FactGrid(cells = listOf(proCell(pro), stakeCell(pro))) }
        if (actions.primary != null) {
            item(key = "action") { ActionButtons(actions = actions, onConnect = onConnect, onPay = onPay) }
        }
        item(key = "device-heading") { Heading(text = stringResource(R.string.you_heading_device)) }
        item(key = "device-grid") { FactGrid(cells = deviceCells(state, onOpenTab)) }
        item(key = "notifications") {
            NotificationsLine(notificationsOn = state.notificationsOn, onEnable = onEnableNotifications)
        }
        item(key = "footer") { Footer() }
    }
}

/** Which callback a [YouAction] fires, so the label text is never parsed to find out. */
private fun YouAction.handler(onConnect: () -> Unit, onPay: () -> Unit): () -> Unit = when (kind) {
    YouActionKind.CONNECT -> onConnect
    YouActionKind.PAY -> onPay
}

/** Exactly one 56dp button per state (plan section 1.2), never two Accent fills. */
@Composable
private fun ActionButtons(actions: YouActions, onConnect: () -> Unit, onPay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side, vertical = BlockGap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.primary?.let { PrimaryButton(label = it.label.text(), onClick = it.handler(onConnect, onPay)) }
        actions.secondary?.let { SecondaryButton(label = it.label.text(), onClick = it.handler(onConnect, onPay)) }
    }
}

/** Short key in mono, or the state word for no session; Refresh and Disconnect trail when connected. */
@Composable
private fun WalletBlock(account: WalletAccount?, onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = BlockGap).padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = stringResource(R.string.you_wallet_label), style = PlainTickerType.label, color = Muted)
        Text(
            text = walletValue(account).text(),
            style = if (account != null) PlainTickerType.monoSmall else PlainTickerType.body,
            color = Ink,
        )
        Text(
            text = stringResource(R.string.you_wallet_note),
            style = PlainTickerType.small,
            color = Ink2,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (account != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextAction(label = stringResource(R.string.action_refresh), onClick = onRefresh)
                TextAction(label = stringResource(R.string.action_disconnect), onClick = onDisconnect)
            }
        }
    }
}

/** The Pro cell: a fact word, plus the mono "until" sub line when the source carries a date. */
@Composable
private fun proCell(pro: ProUiState): FactCell {
    val fact = proFact(pro)
    return FactCell(
        label = stringResource(R.string.you_pro_label),
        value = fact.value.text(),
        sub = fact.until?.text(),
        subMono = true,
    )
}

/** The stake cell: a figure or the reason there is none, never a verdict about the wallet. */
@Composable
private fun stakeCell(pro: ProUiState): FactCell = FactCell(
    label = stringResource(R.string.you_stake_label),
    value = stakeFact(pro).text(),
)

/** Swaps recorded, votes cast, stocks watched: each a numeral, each cell opening its own tab. */
@Composable
private fun deviceCells(state: YouUiState, onOpenTab: (Int) -> Unit): List<FactCell> {
    val facts = deviceFacts(state)
    return listOf(
        deviceCell(
            label = R.string.you_fact_swaps,
            value = facts.swaps,
            sub = R.string.you_fact_sub_portfolio,
            tab = HomeTab.PORTFOLIO,
            onOpenTab = onOpenTab,
        ),
        deviceCell(
            label = R.string.you_fact_votes,
            value = facts.votes,
            sub = R.string.you_fact_sub_vote,
            tab = HomeTab.VOTE,
            onOpenTab = onOpenTab,
        ),
        deviceCell(
            label = R.string.you_fact_watched,
            value = facts.watched,
            sub = R.string.you_fact_sub_watchlist,
            tab = HomeTab.WATCHLIST,
            onOpenTab = onOpenTab,
        ),
    )
}

@Composable
private fun deviceCell(label: Int, value: String, sub: Int, tab: HomeTab, onOpenTab: (Int) -> Unit): FactCell {
    val tabLabel = stringResource(tab.label)
    return FactCell(
        label = stringResource(label),
        value = value,
        sub = stringResource(sub),
        onTap = { onOpenTab(tab.ordinal) },
        tapLabel = stringResource(R.string.you_open_tab, tabLabel),
    )
}

/** The Watchlist's own delivery line, and its Enable action when notifications are off. */
@Composable
private fun NotificationsLine(notificationsOn: Boolean, onEnable: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = BlockGap).padding(horizontal = Side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notificationLine(notificationsOn).text(),
            style = PlainTickerType.small,
            color = Ink2,
            modifier = Modifier.weight(1f),
        )
        if (!notificationsOn && onEnable != null) {
            TextAction(label = stringResource(R.string.action_enable), onClick = onEnable)
        }
    }
}

/** The version in mono, then the disclaimer every screen owes a reader. */
@Composable
private fun Footer() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side, vertical = FooterGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.you_version, BuildConfig.VERSION_NAME),
            style = PlainTickerType.meta,
            color = Muted,
        )
        Text(text = stringResource(R.string.onboarding_body_disclaimer), style = PlainTickerType.small, color = Muted)
    }
}

private val Side = 20.dp
private val BlockGap = 20.dp
private val FooterGap = 28.dp

// ---- Previews ------------------------------------------------------------------------------

private val PreviewAccount = WalletAccount(publicKey = ByteArray(32) { 7 }, label = "Seed Vault Wallet")

private val PreviewNoWalletNotPro = YouUiState(swapsRecorded = 2, votesCast = 1, stocksWatched = 4)
private val PreviewProState = ProUiState(entitlementLoading = false, walletConnected = true)
private val PreviewProPass = ProUiState(
    entitlementLoading = false,
    pro = true,
    source = EntitlementSource.PASS,
    untilMillis = 1_792_368_000_000L,
    walletConnected = true,
    stakeRaw = 31_209_870_777L,
)

@InstrumentPreviews
@Composable
private fun YouNoWalletPreview() {
    PreviewCanvas {
        YouContent(
            state = PreviewNoWalletNotPro,
            pro = PreviewProState,
            onConnect = {},
            onDisconnect = {},
            onRefreshEntitlement = {},
            onPay = {},
            onOpenTab = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun YouWalletProPreview() {
    PreviewCanvas {
        YouContent(
            state = PreviewNoWalletNotPro.copy(account = PreviewAccount, notificationsOn = true),
            pro = PreviewProPass,
            onConnect = {},
            onDisconnect = {},
            onRefreshEntitlement = {},
            onPay = {},
            onOpenTab = {},
        )
    }
}
