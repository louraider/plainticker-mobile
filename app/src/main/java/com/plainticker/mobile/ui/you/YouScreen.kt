package com.plainticker.mobile.ui.you

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSecondaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactTone
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.focusOutline
import com.plainticker.mobile.ui.components.rememberMotionEnabled
import com.plainticker.mobile.ui.components.spoken
import com.plainticker.mobile.ui.home.HomeTab
import com.plainticker.mobile.ui.pass.PassActions
import com.plainticker.mobile.ui.pass.PassSheet
import com.plainticker.mobile.ui.pass.PassViewModel
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.theme.JetBrainsMono
import com.plainticker.mobile.ui.theme.TABULAR_NUMERALS
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.wallet.WalletAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * You (docs/plan-app-uiux-2026-09-21.md, task U1; restyled to Amber, docs/design-research-2026-09-21.md
 * section 5.3/5.5). Not a profile and not a nav destination of its own; reached from the bottom bar
 * like every other Amber destination now (HomeScreen.kt).
 *
 * The wallet, the pass and the stake, this device's own record, the notifications line the
 * Watchlist already draws, and the version and the disclaimer every screen owes a reader, in that
 * order, unchanged by this pass. Nothing here computes: [YouModel.kt] picks every sentence and
 * every numeral arrives already formatted, the split every screen in this app keeps.
 *
 * **The trap this screen carries, and what changed under it.** This is where the app's worst
 * recurring defect lives: a fact cell's value is drawn `maxLines = 1, softWrap = false` beside a
 * fixed-width sibling, so anything the type does not fit clips mid-character rather than wrapping
 * (v0.12.0: "no wallet connected" clipped to "no wallet c"). Amber changes the face and the size
 * (Instrument's 24sp JetBrains Mono to 18sp Bricolage Grotesque, [AmberType.figureRow]'s own size),
 * so the character budget the previous pass pinned is wrong for this pass and is recomputed below,
 * measured against the font file itself rather than assumed (see [YouModelTest]'s own comment for
 * the fontTools numbers). No value moved to a sub-line: every real value still fits the recomputed
 * budget with room to spare.
 *
 * **No Amber [com.plainticker.mobile.ui.components.FactGrid] exists yet** (DESIGN.md section 4:
 * "not yet restyled"; it is also shared with Detail, outside this task's file set), so the two
 * fact groups on this screen (Pro plus Staked SKR; On this device) are drawn by a local `FactGrid`
 * defined at the bottom of this file, reusing [FactCell] (a plain data holder, not a styled
 * component) rather than the shared composable. It keeps the exact call shape YouScreenTest
 * already pins (`FactGrid(cells = listOf(proCell(pro), stakeCell(pro)))`,
 * `FactGrid(cells = deviceCells(state, onOpenTab))`), so the section-order test needs no change.
 *
 * The device's own code ([com.plainticker.mobile.prefs.DevicePassStore]) is a bearer credential
 * and never appears here or on any other screen: this file reads only [PassViewModel]'s already
 * resolved [ProUiState], never the store itself, and DeviceCodeNeverDrawnTest scans every
 * composable under ui/ so a later change cannot draw it by accident.
 *
 * **Fonts and licenses, after the footer (task U11).** [bundledFontLicenses] names every face the
 * app ships today, not the two U11 was written against: Amber added Bricolage Grotesque
 * (docs/fonts.md), so this list is the current three, Outfit and JetBrains Mono from Instrument
 * alongside it. Each [LicenseRow] states the font and its copyright as its own sentence, then
 * reads the shipped OFL text itself (`app/src/main/assets/licenses/`) on request rather than
 * retyping it: a license's own wording is not this screen's copy to author or run through
 * strings.xml's formatting.
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
    // only that it outlives the block that opened it. PassSheet itself is unchanged Instrument:
    // ui/pass/* is outside this task's file set.
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
    val colors = amberColors()
    val actions = youActions(pro)
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.surfaceGround),
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "heading") { AmberSectionHead(title = stringResource(R.string.you_heading), colors = colors) }
        item(key = "wallet") {
            WalletBlock(account = state.account, onRefresh = onRefreshEntitlement, onDisconnect = onDisconnect, colors = colors)
        }
        item(key = "pro-grid") {
            // Motion: the one moment on this screen the research's "quick, 150ms fade"
            // (docs/design-research-2026-09-21.md section 4) means something rather than
            // decorating a fact a reader would read the same way either way, because Pro and
            // Staked SKR are what this cabinet actually vouches for about the reader. Gated by
            // rememberMotionEnabled: at animator scale 0 (the smoke script's own setting) it snaps
            // to fully opaque on the first frame, so nothing here is readable only because it
            // finished animating.
            val motion = rememberMotionEnabled()
            var revealed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { revealed = true }
            val alpha by animateFloatAsState(
                targetValue = if (revealed) 1f else 0f,
                animationSpec = if (motion) tween(durationMillis = 150, easing = LinearOutSlowInEasing) else snap(),
                label = "you-identity-reveal",
            )
            Box(Modifier.graphicsLayer { this.alpha = alpha }) {
                FactGrid(cells = listOf(proCell(pro), stakeCell(pro)), colors = colors)
            }
        }
        if (actions.primary != null) {
            item(key = "action") { ActionButtons(actions = actions, onConnect = onConnect, onPay = onPay, colors = colors) }
        }
        item(key = "device-heading") { AmberSectionHead(title = stringResource(R.string.you_heading_device), colors = colors) }
        item(key = "device-grid") { FactGrid(cells = deviceCells(state, onOpenTab), colors = colors, numeric = true) }
        item(key = "notifications") {
            NotificationsLine(notificationsOn = state.notificationsOn, onEnable = onEnableNotifications, colors = colors)
        }
        item(key = "footer") { Footer() }
        item(key = "licenses-heading") {
            AmberSectionHead(title = stringResource(R.string.you_heading_licenses), colors = colors)
        }
        items(bundledFontLicenses, key = { it.assetPath }) { license -> LicenseRow(license = license, colors = colors) }
    }
}

/** Dark by default, light when the system asks for it: Amber ships both (DESIGN.md section 2). */
@Composable
private fun amberColors(): AmberColors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors

/** Which callback a [YouAction] fires, so the label text is never parsed to find out. */
private fun YouAction.handler(onConnect: () -> Unit, onPay: () -> Unit): () -> Unit = when (kind) {
    YouActionKind.CONNECT -> onConnect
    YouActionKind.PAY -> onPay
}

/**
 * Exactly one 56dp button per state (plan section 1.2), never two Accent fills:
 * [AmberPrimaryAction] for the primary slot, [AmberSecondaryAction][
 * com.plainticker.mobile.ui.components.AmberSecondaryAction] for the one case that pairs Connect
 * wallet with Pay for Pro. Until this pass [AmberSecondaryAction][
 * com.plainticker.mobile.ui.components.AmberSecondaryAction] was a private copy living only in this
 * file ("no shared Amber equivalent exists yet"); it is now the shared component `SecondaryButton`
 * retired in favour of, so this call site and the swap, pass and vote sheets reach for the same
 * function rather than two that mean the same thing.
 */
@Composable
private fun ActionButtons(actions: YouActions, onConnect: () -> Unit, onPay: () -> Unit, colors: AmberColors) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side, vertical = BlockGap),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.primary?.let {
            AmberPrimaryAction(label = it.label.text(), onClick = it.handler(onConnect, onPay), colors = colors)
        }
        actions.secondary?.let {
            AmberSecondaryAction(label = it.label.text(), onClick = it.handler(onConnect, onPay), colors = colors)
        }
    }
}

/**
 * Short key in mono (DESIGN.md section 3: on-chain identifiers stay JetBrains Mono under Amber),
 * or the state word for no session; Refresh and Disconnect trail when connected.
 */
@Composable
private fun WalletBlock(account: WalletAccount?, onRefresh: () -> Unit, onDisconnect: () -> Unit, colors: AmberColors) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = BlockGap).padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = stringResource(R.string.you_wallet_label), style = AmberType.meta, color = colors.textTertiary(AmberSurface.GROUND))
        Text(
            text = walletValue(account).text(),
            style = if (account != null) WalletKeyStyle else AmberType.body,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.you_wallet_note),
            style = AmberType.context,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (account != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextAction(label = stringResource(R.string.action_refresh), onClick = onRefresh, color = colors.actionText)
                TextAction(label = stringResource(R.string.action_disconnect), onClick = onDisconnect, color = colors.actionText)
            }
        }
    }
}

private val WalletKeyStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 15.sp, lineHeight = 20.sp)

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

/**
 * The stake cell: a short value word (never a verdict about the wallet), the figure or the reason
 * there is none as the mono sub line beneath it, the same shape [proCell] already uses.
 */
@Composable
private fun stakeCell(pro: ProUiState): FactCell {
    val fact = stakeFact(pro)
    return FactCell(
        label = stringResource(R.string.you_stake_label),
        value = fact.value.text(),
        sub = fact.sub?.text(),
        subMono = true,
    )
}

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
private fun NotificationsLine(notificationsOn: Boolean, onEnable: (() -> Unit)?, colors: AmberColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = BlockGap).padding(horizontal = Side),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = notificationLine(notificationsOn).text(),
            style = AmberType.context,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (!notificationsOn && onEnable != null) {
            TextAction(label = stringResource(R.string.action_enable), onClick = onEnable, color = colors.actionText)
        }
    }
}

/**
 * The version in mono, then the disclaimer every screen owes a reader. Kept to the exact call
 * shape `Footer()` (YouScreenTest pins the substring), so colours are resolved inside rather than
 * threaded in as a parameter, the same trick [com.plainticker.mobile.ui.portfolio.PortfolioScreen]'s
 * own `Total(state)` uses for the same reason.
 */
@Composable
private fun Footer() {
    val colors = amberColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side, vertical = FooterGap),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.you_version, BuildConfig.VERSION_NAME),
            style = AmberType.meta,
            color = colors.textTertiary(AmberSurface.GROUND),
        )
        Text(
            text = stringResource(R.string.onboarding_body_disclaimer),
            style = AmberType.context,
            color = colors.textTertiary(AmberSurface.GROUND),
        )
    }
}

/**
 * One bundled font (task U11): its device-facing name and credit line, always on screen, then the
 * license terms sentence and a text action that reads the OFL text itself in place. The body is
 * read from `app/src/main/assets/` on first open and kept for the life of this composition
 * ([remember] keyed on the asset path), never retyped as copy: a license's own wording is not this
 * app's to author or to run through strings.xml's formatting and pluralization.
 */
@Composable
private fun LicenseRow(license: BundledFontLicense, colors: AmberColors) {
    var expanded by remember(license.assetPath) { mutableStateOf(false) }
    var body by remember(license.assetPath) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(license.assetPath, expanded) {
        if (expanded && body == null) {
            body = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(license.assetPath).bufferedReader().use { it.readText() } }.getOrNull()
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = BlockGap).padding(horizontal = Side),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = stringResource(license.nameRes), style = AmberType.body, color = colors.textPrimary)
        Text(text = stringResource(license.creditRes), style = AmberType.context, color = colors.textSecondary)
        Text(text = stringResource(R.string.you_license_terms), style = AmberType.context, color = colors.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            TextAction(
                label = stringResource(if (expanded) R.string.action_hide_license else R.string.action_read_license),
                onClick = { expanded = !expanded },
                color = colors.actionText,
            )
        }
        if (expanded) {
            Text(
                text = body ?: stringResource(R.string.you_license_unavailable),
                style = AmberType.meta,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val Side = 20.dp
private val BlockGap = 20.dp
private val FooterGap = 28.dp

// ---- The fact grid, local to this screen ---------------------------------------------------

/**
 * You's own two- and three-cell fact groups (Pro plus Staked SKR; On this device), restyled to
 * Amber: see this file's own top doc comment for why this is a local reimplementation rather than
 * a fork of the shared, not-yet-restyled `FactGrid`. Each [FactCell] draws as its own rounded
 * 16dp [AmberColors.surfaceRaised] card in a row of equal-weight cards (two for the entitlement
 * pair, three for the device facts), which is why the two calls below end up with genuinely
 * different card widths and, therefore, two different character budgets
 * ([YouModelTest.maxFactWordValueLength], [YouModelTest.maxFactCountValueLength]) rather than one.
 *
 * [numeric] switches the value between [AmberType.figureRow] (tabular figures, amber, for a plain
 * count: "1,234" on the device-fact cells) and [FactValueWordStyle], the same size and weight with
 * tabular figures off, for a state word ("Pass," "No wallet"): DESIGN.md section 3's rule under
 * Amber is that tnum belongs to number styles only, never a word style, and research finding 2 is
 * the historical reason this matters here specifically ("Pass and No wallet at 40sp in code font…
 * is the tell of a rule run past its purpose").
 */
@Composable
private fun FactGrid(cells: List<FactCell>, colors: AmberColors, numeric: Boolean = false) {
    if (cells.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Side),
        horizontalArrangement = Arrangement.spacedBy(FactCardGap),
    ) {
        cells.forEach { cell ->
            FactCardView(cell = cell, colors = colors, numeric = numeric, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FactCardView(cell: FactCell, colors: AmberColors, numeric: Boolean, modifier: Modifier = Modifier) {
    val description = buildString {
        append(cell.label).append(": ").append(spoken(cell.value))
        cell.sub?.let { append(", ").append(spoken(it)) }
    }
    val tap = cell.onTap
    val interactionSource = remember { MutableInteractionSource() }
    val interaction = if (tap != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = tap,
        )
    } else {
        Modifier
    }
    val valueColor = when {
        cell.tone == FactTone.Caution -> colors.stateCaution
        numeric -> colors.actionText
        else -> colors.textPrimary
    }
    Column(
        modifier = modifier
            .focusOutline(interactionSource)
            .clip(RoundedCornerShape(FactCardRadius))
            .background(colors.surfaceRaised)
            .then(interaction)
            .padding(horizontal = FactCardPaddingH, vertical = FactCardPaddingV)
            // The cell speaks one sentence, so its own text nodes are cleared; a tappable cell has
            // to put its action back, since clearing took the clickable's semantics with it.
            .clearAndSetSemantics {
                contentDescription = description
                if (tap != null) {
                    role = Role.Button
                    onClick(label = cell.tapLabel) { tap(); true }
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = cell.label,
            style = AmberType.meta,
            color = colors.textTertiary(AmberSurface.RAISED),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = cell.value,
            style = if (numeric) AmberType.figureRow else FactValueWordStyle,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
        )
        cell.sub?.let { sub ->
            Text(
                text = sub,
                style = if (cell.subMono) FactSubTabularStyle else AmberType.context,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A gap between two cards, not a hairline inside one shared container (DESIGN.md section 8). */
private val FactCardGap = 8.dp
private val FactCardRadius = 16.dp
private val FactCardPaddingH = 12.dp
private val FactCardPaddingV = 14.dp

/**
 * [AmberType.figureRow] (18/600, opsz 18) with tabular figures switched off: the same size and
 * weight the row figure uses, for a cell whose value is a word rather than a number. Built with
 * [TextStyle.copy] on the published style rather than a new size, so the physical font instance
 * (opsz 18, wght 600, wdth 100) stays exactly what [AmberType] already bundles.
 */
private val FactValueWordStyle = AmberType.figureRow.copy(fontFeatureSettings = null)

/** [AmberType.context] with tabular figures switched on, for a sub line that carries a figure. */
private val FactSubTabularStyle = AmberType.context.copy(fontFeatureSettings = TABULAR_NUMERALS)

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
    AmberPreviewCanvas {
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
    AmberPreviewCanvas {
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
