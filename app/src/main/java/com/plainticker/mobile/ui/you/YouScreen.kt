package com.plainticker.mobile.ui.you

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.R
import com.plainticker.mobile.data.auth.DeviceCodeStatus
import com.plainticker.mobile.auth.CredentialManagerGoogleSource
import com.plainticker.mobile.data.plainticker.EntitlementSource
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.components.AmberDisabledAction
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.focusOutline
import com.plainticker.mobile.ui.components.rememberMotionEnabled
import com.plainticker.mobile.ui.home.HomeTab
import com.plainticker.mobile.ui.pass.PassActions
import com.plainticker.mobile.ui.pass.PassSheet
import com.plainticker.mobile.ui.pass.PassViewModel
import com.plainticker.mobile.ui.pass.ProUiState
import com.plainticker.mobile.ui.pass.PromoRefusal
import com.plainticker.mobile.ui.pass.PromoState
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.theme.JetBrainsMono
import com.plainticker.mobile.wallet.WalletAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * You, the account cabinet (2026-09-25). The founder asked for the app's cabinet to match the one
 * plainticker.com just shipped (web PR #145), so the screen that used to stack a wallet block, two
 * fact cards, a big Connect button, an Account section, a device trio, a notifications line, the
 * footer and three license blocks now reads, top to bottom:
 *
 * 1. **The hero card** ([Hero]): who (the Google account, else the wallet's short key, else "Not
 *    signed in"), the plan as the headline ("Pro until 20 Oct 2026", "Pro while staked", "Free"),
 *    the days-left or what-Free-opens line, and at most one action ([youHero] owns the matrix).
 * 2. **Plan** ([planRows]): source, valid until, how to extend, the staked SKR figure, a pending
 *    payment. The pass entry lands here as a text action whenever the hero does not carry it.
 *    "Have a code?" is the group's first row, in full action colour, so it sits above the fold
 *    (judges' round 2); Detail's "Have a code? Get Pro" opens You with its field already focused.
 * 3. **Sign-in methods** ([AccountSection]): Google, then the wallets linked to the Google
 *    account, if the server returned any.
 * 4. **Wallet** ([WalletSection]): the phone's Solana wallet connection, its own group since the
 *    judges' round 2 because it signs transactions and is not a way to sign in.
 * 5. **On this device**: swaps, votes and stocks watched, each a row that opens its tab.
 * 6. **Notifications**: the delivery line with Enable, and the daily digest.
 * 7. **About**: version, disclaimer, privacy policy, terms, account deletion (each opening
 *    plainticker.com in the browser), and Fonts and licenses behind one row that shows them.
 *
 * Nothing here computes: [YouModel.kt] picks every sentence and every numeral arrives formatted.
 *
 * **The clipping rule, and why the fact cards went.** The two- and three-card fact grids this
 * screen used to draw were the app's worst recurring clip ("no wallet c", then "Swaps record…").
 * The cabinet draws no card grid at all: every group is [CabinetRow]s in [AmberTickerRowGroup]'s
 * tonal container, where the text column is weighted and wraps and the only one-line slots are a
 * row's trailing figure or text action, and the hero's buttons. `CabinetFitTest` measures every
 * one of those with fontTools against the bundled font each is drawn in, at 1.0x and 1.3x.
 *
 * The device's own code ([com.plainticker.mobile.prefs.DevicePassStore]) is a bearer credential
 * and never appears here: this file reads only [PassViewModel]'s resolved [ProUiState], and
 * DeviceCodeNeverDrawnTest scans every composable under ui/ so a later change cannot draw it.
 *
 * A finished sign-in re-reads the entitlement through [PassViewModel.refreshEntitlement], so a Pro
 * bought on the web shows in the hero as soon as the server has linked the device.
 */
@Composable
fun YouScreen(
    viewModel: YouViewModel,
    passViewModel: PassViewModel,
    accountViewModel: AccountViewModel,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The daily digest screen: its own route, reached from here and from Today's "Read it". */
    onOpenDigest: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
    /** Detail's "Have a code? Get Pro" asked for the code field; see [YouContent]'s own. */
    openPromo: Boolean = false,
    onPromoOpened: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pro by passViewModel.pro.collectAsStateWithLifecycle()
    val promo by passViewModel.promo.collectAsStateWithLifecycle()
    val pass by passViewModel.state.collectAsStateWithLifecycle()
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val deviceCodeStatus by accountViewModel.deviceCodeStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // An Activity context: Credential Manager draws Google's sheet over it.
    val credentials = remember(context) { CredentialManagerGoogleSource(context, BuildConfig.GOOGLE_SERVER_CLIENT_ID) }

    // A sign-in that finished linked this device to the account server side, so the device may be
    // Pro now: re-read it through the one refresh every screen shares. A later account refresh or
    // a landed unlink fires the same event, for the same reason: either can change which wallet's
    // stake or pass counts.
    LaunchedEffect(accountViewModel, passViewModel) {
        accountViewModel.signedIn.collect { passViewModel.refreshEntitlement() }
    }

    // The one piece of this screen that can change while the app is away: a reader who took the
    // Enable action went to the system settings and came back (the same rule WatchlistScreen
    // keeps for its own notifications line).
    LifecycleResumeEffect(viewModel) {
        viewModel.notificationsChanged()
        onPauseOrDispose { }
    }

    // GET /api/v1/account whenever a Google account is signed in: shown here (LifecycleResumeEffect
    // runs its own effect immediately when the lifecycle is already resumed, which covers "You is
    // shown"), and again on every later resume, so a wallet linked or unlinked on the web shows up
    // here too. AccountViewModel.refresh throttles this to at most once every thirty seconds.
    LifecycleResumeEffect(accountViewModel) {
        accountViewModel.refresh()
        onPauseOrDispose { }
    }

    // A sibling of the LazyColumn: the sheet is a modal surface and draws in its own window, so
    // where it sits in this tree does not matter, only that it outlives the row that opened it.
    Box(modifier.fillMaxSize()) {
        YouContent(
            state = state,
            pro = pro,
            promo = promo,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onRefreshEntitlement = passViewModel::refreshEntitlement,
            onPay = passViewModel::pay,
            onOpenPromo = passViewModel::openPromo,
            onPromoInputChanged = passViewModel::promoInputChanged,
            onApplyPromo = passViewModel::applyPromo,
            onDismissPromo = passViewModel::dismissPromo,
            onOpenTab = onOpenTab,
            onOpenDigest = onOpenDigest,
            account = account,
            deviceCodeStatus = deviceCodeStatus,
            onSignIn = { accountViewModel.signIn(credentials) },
            onSignOut = accountViewModel::signOut,
            onUnlink = accountViewModel::unlink,
            onEnableNotifications = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            // Privacy, terms and account deletion live on plainticker.com: the browser opens them.
            // A phone with no browser at all leaves the tap doing nothing rather than crashing.
            onOpenLink = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                }
            },
            header = header,
            openPromo = openPromo,
            onPromoOpened = onPromoOpened,
        )
        PassSheet(
            state = pass,
            actions = PassActions(
                onConfirm = passViewModel::confirm,
                onRetry = passViewModel::retry,
                onClose = passViewModel::close,
                onHaveCode = {
                    passViewModel.close()
                    passViewModel.openPromo()
                },
            ),
        )
    }
}

@Composable
internal fun YouContent(
    state: YouUiState,
    pro: ProUiState,
    /** Connect, told where it was tapped, so a phone with no wallet app hears it right there. */
    onConnect: (ConnectPlace) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshEntitlement: () -> Unit,
    onPay: () -> Unit,
    onOpenTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    promo: PromoState = PromoState.Idle,
    onOpenPromo: () -> Unit = {},
    onPromoInputChanged: (String) -> Unit = {},
    onApplyPromo: () -> Unit = {},
    onDismissPromo: () -> Unit = {},
    onEnableNotifications: (() -> Unit)? = null,
    onOpenDigest: (() -> Unit)? = null,
    account: AccountUiState = AccountUiState.Restoring,
    deviceCodeStatus: DeviceCodeStatus = DeviceCodeStatus.OK,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onUnlink: (String) -> Unit = {},
    /** Opens one of [AboutLinks] in the browser; a no-op in the previews. */
    onOpenLink: (String) -> Unit = {},
    header: @Composable () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
    /**
     * True when Detail's "Have a code? Get Pro" sent the reader here (judges' round 2): the code
     * field opens (a finished redeem's confirmation folds first, so the field can open at all),
     * the list returns to its top, where the hero's Get Pro and the Plan group's first row, the
     * field itself, share the first screen, and the field takes focus as it appears
     * ([PromoField]). [onPromoOpened] clears the request so it runs once.
     */
    openPromo: Boolean = false,
    onPromoOpened: () -> Unit = {},
) {
    val colors = amberColors()
    val listState = rememberLazyListState()
    val motion = rememberMotionEnabled()
    LaunchedEffect(openPromo) {
        if (!openPromo) return@LaunchedEffect
        if (promo is PromoState.Success) onDismissPromo()
        onOpenPromo()
        // Cleared last: clearing it recomposes this effect's key and cancels whatever is left of it.
        if (motion) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        onPromoOpened()
    }
    val hero = youHero(account, state.account, pro, nowMillis)
    val plan = planRows(pro, hero.action, nowMillis)
    val heroMessage = (account as? AccountUiState.SignedOut)?.message?.takeIf { hero.action == HeroAction.SIGN_IN }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.surfaceGround),
        state = listState,
        // The tab content ends above the navigation bar; the padding is part of the scroll.
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "heading") { AmberSectionHead(title = stringResource(R.string.you_heading), colors = colors) }
        item(key = "hero") {
            // Motion: the one moment on this screen the research's "quick, 150ms fade" means
            // something, because the hero is what this cabinet vouches for about the reader. Gated
            // by rememberMotionEnabled: at animator scale 0 it snaps to fully opaque on the first
            // frame, so nothing here is readable only because it finished animating.
            val motion = rememberMotionEnabled()
            var revealed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { revealed = true }
            val alpha by animateFloatAsState(
                targetValue = if (revealed) 1f else 0f,
                animationSpec = if (motion) tween(durationMillis = 150, easing = LinearOutSlowInEasing) else snap(),
                label = "you-hero-reveal",
            )
            Box(Modifier.graphicsLayer { this.alpha = alpha }) {
                Hero(hero = hero,
                    message = heroMessage,
                    onSignIn = onSignIn,
                    onConnect = { onConnect(ConnectPlace.HERO) },
                    onPay = onPay,
                    colors = colors,
                    noWallet = state.noWalletAt == ConnectPlace.HERO,
                )
            }
        }
        item(key = "plan") {
            PlanGroup(
                rows = plan,
                onPay = onPay,
                onRefresh = onRefreshEntitlement,
                promo = promo,
                onOpenPromo = onOpenPromo,
                onPromoInputChanged = onPromoInputChanged,
                onApplyPromo = onApplyPromo,
                onDismissPromo = onDismissPromo,
                colors = colors,
                // A promo grant belongs to this phone's own code, which a reinstall loses; a
                // signed-in account keeps it. Said only once the account is known to be absent.
                promoKeepNote = account is AccountUiState.SignedOut,
            )
        }
        item(key = "methods") {
            AccountSection(
                state = account,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onUnlink = onUnlink,
                colors = colors,
                showMessage = heroMessage == null,
                deviceCodeStatus = deviceCodeStatus,
            )
        }
        item(key = "wallet") {
            WalletSection(
                wallet = state.account,
                onConnect = { onConnect(ConnectPlace.WALLET) },
                onDisconnect = onDisconnect,
                colors = colors,
                noWallet = state.noWalletAt == ConnectPlace.WALLET,
            )
        }
        item(key = "device") { DeviceGroup(state = state, onOpenTab = onOpenTab, colors = colors) }
        item(key = "notifications") {
            NotificationsGroup(
                notificationsOn = state.notificationsOn,
                onEnable = onEnableNotifications,
                onOpenDigest = onOpenDigest,
                colors = colors,
            )
        }
        item(key = "about") { AboutGroup(colors = colors, onOpenLink = onOpenLink) }
        item(key = "end") { Box(Modifier.padding(bottom = EndGap)) }
    }
}

/** Dark by default, light when the system asks for it: Amber ships both (DESIGN.md section 2). */
@Composable
private fun amberColors(): AmberColors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors

// ---- The hero ------------------------------------------------------------------------------

/**
 * Who, the plan as a headline, and at most one action, in one 28dp card (DESIGN.md 5.2's status
 * card radius). The only amber fill on You: Get Pro, Extend Pro or Sign in with Google, each an
 * [AmberPrimaryAction]; a sign-in in flight is an [AmberDisabledAction] in the same slot. Connect
 * wallet sits under Sign in as a text action, the web cabinet's own "or a Solana wallet".
 *
 * The headline ([HeroHeadlineStyle], 28/700) carries no `maxLines`: "Pro until 30 May 2030", the
 * widest date the headline can print (every day, month and year to 2039, fontTools), is 292.99dp
 * at 1.0x against the card's 328dp, and wraps at 1.3x rather than clipping (`CabinetFitTest`).
 * Every line under it wraps too.
 */
@Composable
private fun Hero(
    hero: YouHero,
    message: AccountMessage?,
    onSignIn: () -> Unit,
    onConnect: () -> Unit,
    onPay: () -> Unit,
    colors: AmberColors,
    /** The hero's Connect wallet found no wallet app: the shared sentence stands under it. */
    noWallet: Boolean = false,
) {
    val shape = RoundedCornerShape(HeroRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GroupSide)
            .padding(top = 8.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .then(if (colors === AmberLightColors) Modifier.border(1.dp, colors.border, shape) else Modifier)
            .padding(HeroPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        hero.identityLabel?.let {
            Text(text = it.text(), style = AmberType.meta, color = colors.textTertiary(AmberSurface.RAISED))
        }
        Text(
            text = hero.identity.text(),
            style = if (hero.identityKind == IdentityKind.WALLET) KeyStyle else AmberType.body,
            color = if (hero.identityKind == IdentityKind.NONE) colors.textSecondary else colors.textPrimary,
        )
        Text(
            text = hero.headline.text(),
            style = HeroHeadlineStyle,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
        hero.lines.forEach { line ->
            Text(text = line.text(), style = AmberType.context, color = colors.textSecondary)
        }
        message?.let {
            Text(
                text = stringResource(accountMessageRes(it)),
                style = AmberType.context,
                color = colors.stateCaution,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (accountMessageOpensWeb(it)) {
                val uriHandler = LocalUriHandler.current
                TextAction(
                    label = stringResource(R.string.account_action_open_web),
                    onClick = { runCatching { uriHandler.openUri(AccountMessage.LINK_ON_WEB_URL) } },
                    color = colors.actionText,
                    contentPadding = MessageTextActionPadding,
                )
            }
        }
        hero.action?.let { action ->
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                HeroButton(action = action, onSignIn = onSignIn, onPay = onPay, colors = colors)
                if (hero.offersConnect) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextAction(
                            label = stringResource(R.string.action_connect_wallet),
                            onClick = onConnect,
                            color = colors.actionText,
                            contentPadding = CenteredTextActionPadding,
                        )
                    }
                    if (noWallet) NoWalletLine(colors = colors, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** The hero's one button; the label is picked by [HeroAction], never parsed. */
@Composable
private fun HeroButton(action: HeroAction, onSignIn: () -> Unit, onPay: () -> Unit, colors: AmberColors) {
    when (action) {
        HeroAction.SIGN_IN ->
            AmberPrimaryAction(label = stringResource(R.string.account_sign_in), onClick = onSignIn, colors = colors)
        HeroAction.SIGNING_IN ->
            AmberDisabledAction(label = stringResource(R.string.account_signing_in), colors = colors)
        HeroAction.GET_PRO ->
            AmberPrimaryAction(label = stringResource(R.string.you_action_get_pro), onClick = onPay, colors = colors)
        HeroAction.EXTEND ->
            AmberPrimaryAction(label = stringResource(R.string.you_action_extend), onClick = onPay, colors = colors)
    }
}

// ---- Plan ----------------------------------------------------------------------------------

@Composable
private fun PlanGroup(
    rows: List<PlanRow>,
    onPay: () -> Unit,
    onRefresh: () -> Unit,
    promo: PromoState,
    onOpenPromo: () -> Unit,
    onPromoInputChanged: (String) -> Unit,
    onApplyPromo: () -> Unit,
    onDismissPromo: () -> Unit,
    colors: AmberColors,
    promoKeepNote: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(title = stringResource(R.string.you_heading_plan), colors = colors)
        AmberTickerRowGroup(colors = colors) {
            // First, not last (judges' round 2): a judge holding a code found "Have a code?" only
            // after scrolling past the fold, under every Plan row.
            PromoRow(
                promo = promo,
                onOpen = onOpenPromo,
                onInputChanged = onPromoInputChanged,
                onApply = onApplyPromo,
                onDismiss = onDismissPromo,
                colors = colors,
                keepNote = promoKeepNote,
            )
            rows.forEach { row ->
                CabinetRow(
                    colors = colors,
                    label = row.label.text(),
                    value = row.value.text(),
                    sub = row.sub?.text(),
                    actions = listOfNotNull(row.action?.let { planRowAction(it, onPay, onRefresh) }),
                )
            }
        }
    }
}

@Composable
private fun planRowAction(action: PlanAction, onPay: () -> Unit, onRefresh: () -> Unit): RowAction = when (action) {
    PlanAction.GET_PRO -> RowAction(stringResource(R.string.you_action_get_pro), onPay)
    PlanAction.EXTEND -> RowAction(stringResource(R.string.you_action_extend), onPay)
    PlanAction.REFRESH -> RowAction(stringResource(R.string.action_refresh), onRefresh)
}

// ---- Promo code redemption -------------------------------------------------------------------

/**
 * "Have a code?" (task: promo-redeem): a plain text action that opens an inline field, drawn in
 * capitals and kept exactly as typed or pasted, normalized only when it is sent
 * ([com.plainticker.mobile.data.plainticker.PromoApi.normalize], from
 * [com.plainticker.mobile.ui.pass.PassViewModel.applyPromo]), with Apply and
 * Cancel beside it. Shown even when this device is already Pro, so a judge's second code can
 * extend or stack it, and always in full [AmberColors.actionText] (judges' round 2: the dimmed
 * grey it used to take on a Pro device read as disabled).
 *
 * Errors are one plain line under the field ([PromoRefusal.text]), never the server's own
 * sentence; a success collapses the field and shows the same "Pro until" sentence the hero draws,
 * through [promoSuccessLine], so the two can never disagree about the same fact.
 */
@Composable
private fun PromoRow(
    promo: PromoState,
    onOpen: () -> Unit,
    onInputChanged: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    colors: AmberColors,
    keepNote: Boolean = false,
) {
    when (promo) {
        // Signed out (device QA of 1.3.17): the server ties a code to an account only at the moment
        // it is redeemed, so the way to keep Pro past a reinstall is to sign in first. Said before
        // redemption, where it can still be acted on, and never promised after it.
        PromoState.Idle -> CabinetRow(
            colors = colors,
            value = stringResource(R.string.promo_prompt),
            valueKind = RowValueKind.QUIET,
            sub = if (keepNote) stringResource(R.string.promo_signin_first_hint) else null,
            actions = listOf(RowAction(stringResource(R.string.promo_action_have_code), onOpen)),
        )
        is PromoState.Editing -> PromoEditingRow(promo.input, error = null, onInputChanged, onApply, onDismiss, colors, keepNote)
        is PromoState.Failed -> PromoEditingRow(promo.input, error = promo.reason, onInputChanged, onApply, onDismiss, colors, keepNote)
        is PromoState.Applying -> CabinetRow(
            colors = colors,
            value = stringResource(R.string.promo_field_label),
            sub = stringResource(R.string.promo_field_checking),
        )
        is PromoState.Success -> CabinetRow(
            colors = colors,
            value = promoSuccessLine(promo.untilMillis).text(),
            sub = if (keepNote) stringResource(R.string.promo_success_saved_to_phone) else null,
        )
    }
}

@Composable
private fun PromoEditingRow(
    input: String,
    error: PromoRefusal?,
    onInputChanged: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    colors: AmberColors,
    signInHint: Boolean = false,
) {
    // Label, then the input right under it, then the refusal, then Apply and Cancel (device QA of
    // 1.3.16): as a CabinetRow the two actions drew on their own row between the label and the
    // field, so the input a reader types into sat below the buttons that act on it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised)
            .padding(start = RowPadding, end = RowPadding, top = RowVerticalPadding),
    ) {
        Text(text = stringResource(R.string.promo_field_label), style = AmberType.body, color = colors.textPrimary)
        PromoField(value = input, onValueChange = onInputChanged, colors = colors)
        if (signInHint) {
            Text(
                text = stringResource(R.string.promo_signin_first_hint),
                style = AmberType.context,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        error?.let {
            Text(
                text = stringResource(it.text),
                style = AmberType.context,
                color = colors.stateCaution,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextAction(label = stringResource(R.string.promo_action_apply), onClick = onApply, color = colors.actionText)
            TextAction(label = stringResource(R.string.you_action_cancel), onClick = onDismiss, color = colors.actionText)
        }
    }
}

/**
 * The one input on You: single line, Bricolage `figureInline` (tnum on) rather than a monospace
 * face, because DESIGN.md section 3's foundation rule keeps JetBrains Mono for on-chain
 * identifiers only, and a promo code is not one. Paste works the way every [BasicTextField] on
 * Android already supports it, through the system's own context menu; nothing here disables it.
 *
 * The text is never rewritten as it is typed (fresh-device QA of 1.3.23: normalizing on every
 * change raced the keyboard and a fast `PT-AAAA-BBBB-CCCC` lost characters). The capitals are
 * drawn by [UppercaseCodeTransformation], one character for one, so the cursor never moves under
 * the reader and the value the keyboard edits is the one it typed.
 *
 * The field takes focus as it appears (judges' round 2), whether "Have a code?" opened it here or
 * Detail's "Have a code? Get Pro" did, so the keyboard is up and the next thing typed is the code.
 * A request the node cannot take yet is dropped rather than thrown.
 */
@Composable
private fun PromoField(value: String, onValueChange: (String) -> Unit, colors: AmberColors) {
    val style = AmberType.figureInline.copy(color = colors.textPrimary)
    val placeholder = stringResource(R.string.promo_field_placeholder)
    val label = stringResource(R.string.promo_field_label)
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        visualTransformation = UppercaseCodeTransformation,
        // Codes are ASCII capitals and digits: no autocorrect rewriting "PT" into a word, no
        // suggestions strip, and the keyboard opens on capitals (device QA of 1.3.16).
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
        ),
        cursorBrush = SolidColor(colors.actionText),
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            // The same underline [com.plainticker.mobile.ui.components.Field] draws, amber while
            // focused (device QA of 1.3.17: the field had no visible edge at all).
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRect(
                    color = if (focused) colors.actionText else colors.border,
                    topLeft = Offset(0f, size.height - stroke),
                    size = Size(size.width, stroke),
                )
            }
            .padding(bottom = 10.dp)
            .focusRequester(focus)
            .semantics { contentDescription = label },
        decorationBox = { innerField ->
            Box {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = style, color = colors.textTertiary(AmberSurface.RAISED))
                }
                innerField()
            }
        },
    )
}

/**
 * Draws a promo code in capitals without changing what was typed: each character is raised on
 * its own ([Char.uppercaseChar]), so the drawn text is exactly as long as the typed one and the
 * identity offset mapping holds for every input, a German sharp s included.
 */
internal object UppercaseCodeTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(uppercaseCode(text.text)), OffsetMapping.Identity)
}

/** The drawn form of a typed promo code: one capital per character, never a longer string. */
internal fun uppercaseCode(typed: String): String =
    String(CharArray(typed.length) { typed[it].uppercaseChar() }) // lint-allow uppercase: a typed promo code, drawn in capitals

// ---- On this device ------------------------------------------------------------------------

/**
 * Swaps recorded, votes cast, stocks watched: each a row whose figure sits on the right, the whole
 * row one tap target that opens the tab listing them. The label and the "Listed under" line wrap;
 * the figure is the one-line slot ("999,999" is 90.28dp at 1.3x against the row's 336dp).
 */
@Composable
private fun DeviceGroup(state: YouUiState, onOpenTab: (Int) -> Unit, colors: AmberColors) {
    val facts = deviceFacts(state)
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(title = stringResource(R.string.you_heading_device), colors = colors)
        AmberTickerRowGroup(colors = colors) {
            DeviceRow(R.string.you_fact_swaps, facts.swaps, R.string.you_fact_sub_portfolio, HomeTab.PORTFOLIO, onOpenTab, colors)
            DeviceRow(R.string.you_fact_votes, facts.votes, R.string.you_fact_sub_vote, HomeTab.VOTE, onOpenTab, colors)
            DeviceRow(R.string.you_fact_watched, facts.watched, R.string.you_fact_sub_watchlist, HomeTab.WATCHLIST, onOpenTab, colors)
        }
    }
}

@Composable
private fun DeviceRow(label: Int, value: String, sub: Int, tab: HomeTab, onOpenTab: (Int) -> Unit, colors: AmberColors) {
    val tabLabel = stringResource(tab.label)
    CabinetRow(
        colors = colors,
        value = stringResource(label),
        sub = stringResource(sub),
        figure = value,
        onTap = { onOpenTab(tab.ordinal) },
        tapLabel = stringResource(R.string.you_open_tab, tabLabel),
    )
}

// ---- Notifications -------------------------------------------------------------------------

/** The Watchlist's own delivery line with Enable when off, then the daily digest, one tap away. */
@Composable
private fun NotificationsGroup(
    notificationsOn: Boolean,
    onEnable: (() -> Unit)?,
    onOpenDigest: (() -> Unit)?,
    colors: AmberColors,
) {
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(title = stringResource(R.string.you_heading_notifications), colors = colors)
        AmberTickerRowGroup(colors = colors) {
            CabinetRow(
                colors = colors,
                value = notificationLine(notificationsOn).text(),
                actions = listOfNotNull(
                    onEnable?.takeIf { !notificationsOn }?.let { RowAction(stringResource(R.string.action_enable), it) },
                ),
            )
            onOpenDigest?.let { open ->
                val digest = stringResource(R.string.you_digest_link)
                CabinetRow(colors = colors, value = digest, onTap = open, tapLabel = digest, valueKind = RowValueKind.LINK)
            }
        }
    }
}

// ---- About ---------------------------------------------------------------------------------

/**
 * The version, the disclaimer every screen owes a reader, the privacy policy, the terms and account
 * deletion (judges' round 2 and the dApp Store listing: each a link row that opens plainticker.com
 * in the browser, [AboutLinks]), and Fonts and licenses collapsed behind one row: Show opens the
 * three bundled fonts' [LicenseRow]s in place, each able to read its own shipped OFL text (task
 * U11), and Hide folds them away again.
 */
@Composable
private fun AboutGroup(colors: AmberColors, onOpenLink: (String) -> Unit) {
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(title = stringResource(R.string.you_heading_about), colors = colors)
        AmberTickerRowGroup(colors = colors) {
            CabinetRow(colors = colors, value = stringResource(R.string.you_version, BuildConfig.VERSION_NAME))
            CabinetRow(colors = colors, value = stringResource(R.string.onboarding_body_disclaimer), valueKind = RowValueKind.QUIET)
            AboutLinkRow(R.string.you_about_privacy, AboutLinks.PRIVACY, onOpenLink, colors)
            AboutLinkRow(R.string.you_about_terms, AboutLinks.TERMS, onOpenLink, colors)
            // Neutral, not the action amber (device QA of 1.3.16): deleting an account is a way out,
            // not something the screen invites; the row stays one tap to the web page.
            AboutLinkRow(
                R.string.you_about_delete_account,
                AboutLinks.DELETE_ACCOUNT,
                onOpenLink,
                colors,
                sub = R.string.you_about_delete_account_sub,
                valueKind = RowValueKind.WORDS,
            )
            CabinetRow(
                colors = colors,
                value = stringResource(R.string.you_heading_licenses),
                sub = stringResource(R.string.you_license_terms),
                actions = listOf(
                    RowAction(
                        stringResource(if (licensesOpen) R.string.you_action_hide else R.string.you_action_show),
                        { licensesOpen = !licensesOpen },
                    ),
                ),
            )
            if (licensesOpen) {
                bundledFontLicenses.forEach { license -> LicenseRow(license = license, colors = colors) }
            }
        }
    }
}

/** One About row that opens [url] on plainticker.com in the browser; the whole row is the tap target. */
@Composable
private fun AboutLinkRow(
    label: Int,
    url: String,
    onOpenLink: (String) -> Unit,
    colors: AmberColors,
    sub: Int? = null,
    valueKind: RowValueKind = RowValueKind.LINK,
) {
    val text = stringResource(label)
    CabinetRow(
        colors = colors,
        value = text,
        sub = sub?.let { stringResource(it) },
        valueKind = valueKind,
        onTap = { onOpenLink(url) },
        tapLabel = stringResource(R.string.you_about_open_link, text),
    )
}

/**
 * One bundled font (task U11): its device-facing name and credit line, then a text action that
 * reads the OFL text itself in place. The body is read from `app/src/main/assets/` on first open
 * and kept for the life of this composition ([remember] keyed on the asset path), never retyped
 * as copy: a license's own wording is not this app's to author or run through strings.xml.
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
    CabinetRow(
        colors = colors,
        label = stringResource(license.creditRes),
        value = stringResource(license.nameRes),
        actions = listOf(
            RowAction(
                stringResource(if (expanded) R.string.action_hide_license else R.string.action_read_license),
                { expanded = !expanded },
            ),
        ),
    ) {
        if (expanded) {
            Text(
                text = body ?: stringResource(R.string.you_license_unavailable),
                style = AmberType.meta,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// ---- The cabinet row -----------------------------------------------------------------------

/** How a row's value is set: words, an on-chain key in mono, a quieter sentence, or a link. */
internal enum class RowValueKind { WORDS, KEY, QUIET, LINK }

/**
 * A row's text action: a label and what it does. [color] overrides the group's own
 * [AmberColors.actionText] for one action only; null (the ordinary case) draws it at the group's
 * own emphasis, the same amber every other row's action reads at.
 */
internal class RowAction(val label: String, val onClick: () -> Unit, val color: Color? = null)

/**
 * One row of a cabinet group, the web cabinet's calm row: an optional small label, the value, an
 * optional sub line, all in one weighted column that wraps, then either a figure (tabular, amber)
 * or one text action on the right. Two actions (Copy and Disconnect; Sign out and Cancel) move
 * onto their own right-aligned line under the text, so they never squeeze the column between them.
 *
 * **The clipping rule** (DESIGN.md section 4): the only one-line slots are the figure and a text
 * action's label, both unweighted and measured first; the column gets the rest and wraps. Every
 * label and figure that can land here is measured in `CabinetFitTest` against the row's 336dp of
 * content on a 400dp frame (16dp group inset, 16dp row padding, each side).
 *
 * [onTap] makes the whole row one 56dp tap target with a role and [tapLabel], and the shared 2dp
 * focus ring; the pressed row steps to `surfaceHigh`, the move every Amber row makes.
 *
 * **QA 2026-09-26, D4: "Sign out" drew as "Sian out."** Not a font or line-height defect: fontTools
 * against `res/font/outfit_semibold.ttf` puts Outfit SemiBold 14sp's real ascent-to-descent span at
 * 17.64dp, comfortably inside [PlainTickerType.textAction]'s declared 20sp line height, itself
 * centred inside [TextAction]'s own 48dp box with 14dp of padding on each side, itself inside this
 * row's own [RowVerticalPadding]; nothing in this row's own budget was ever tight enough to clip a
 * "g" on its own, and `CabinetFitTest` pins that budget so it cannot silently shrink.
 *
 * The actual cause sits one level up, in `YouContent`'s `LazyColumn`, not in this row: a scrolling
 * list clips whatever is left of its last, partially visible item at its own viewport edge, the
 * ordinary way any list shows a sliver of what is next. This only read as broken, rather than as an
 * obviously partial row, because that edge happened to fall inside "Sign out"'s own ink rather than
 * in the blank space around it, and only in the offline, entitlement-unavailable state: that state's
 * `Plan` group draws three shorter rows instead of four (no "Valid until," no "How to extend,"
 * neither known without a read that failed), which changes how much of this screen fits above the
 * fold before a first scroll. Widening this row's own vertical margin (was 10dp) narrows how often a
 * viewport edge can land inside its ink rather than around it, but it is a mitigation at the row
 * level for a coincidence that is decided one level up, by how tall the groups above it happen to
 * be; nothing at this level can prove no future combination of states puts some row's own ink back
 * at that same edge, the same honest limit `AmberTickerRow`'s own 54-character company outlier
 * already accepts for a different clip.
 */
@Composable
internal fun CabinetRow(
    colors: AmberColors,
    value: String,
    label: String? = null,
    valueKind: RowValueKind = RowValueKind.WORDS,
    sub: String? = null,
    subCaution: Boolean = false,
    figure: String? = null,
    actions: List<RowAction> = emptyList(),
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val interaction = if (onTap != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = tapLabel,
            role = Role.Button,
            onClick = onTap,
        )
    } else {
        Modifier.semantics(mergeDescendants = actions.isEmpty()) {}
    }
    val inline = actions.singleOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusOutline(interactionSource, colors)
            .background(if (pressed) colors.surfaceHigh else colors.surfaceRaised)
            .then(interaction)
            .defaultMinSize(minHeight = RowMinHeight)
            .padding(start = RowPadding, end = RowPadding, top = RowVerticalPadding, bottom = RowVerticalPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                label?.let { Text(text = it, style = AmberType.meta, color = colors.textTertiary(AmberSurface.RAISED)) }
                Text(
                    text = value,
                    style = when (valueKind) {
                        RowValueKind.KEY -> KeyStyle
                        RowValueKind.QUIET -> AmberType.context
                        else -> AmberType.body
                    },
                    color = when (valueKind) {
                        RowValueKind.QUIET -> colors.textSecondary
                        RowValueKind.LINK -> colors.actionText
                        else -> colors.textPrimary
                    },
                )
                sub?.let {
                    Text(text = it, style = AmberType.context, color = if (subCaution) colors.stateCaution else colors.textSecondary)
                }
            }
            figure?.let {
                Text(
                    text = it,
                    style = AmberType.figureRow,
                    color = colors.actionText,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = FigureGap),
                )
            }
            inline?.let { TextAction(label = it.label, onClick = it.onClick, color = it.color ?: colors.actionText) }
        }
        if (actions.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                actions.forEach { TextAction(label = it.label, onClick = it.onClick, color = it.color ?: colors.actionText) }
            }
        }
        extra()
    }
}

/** An on-chain key: JetBrains Mono 15sp, the one role DESIGN.md section 3 keeps the mono face for. */
private val KeyStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 15.sp, lineHeight = 20.sp)

/**
 * The hero's plan headline: [AmberType.figureLarge]'s own font instance (wght 700, opsz 34, the
 * one AmberFigure's hero figure bundles) drawn at 28sp, tabular figures off because it is a
 * sentence with a date in it, not a figure. Sized between the 22sp section heads and the 34sp
 * figure so "Pro until 20 Oct 2026" stays one line at 1.0x on a 400dp frame (`CabinetFitTest`).
 */
private val HeroHeadlineStyle = AmberType.figureLarge.copy(fontSize = 28.sp, lineHeight = 32.sp, fontFeatureSettings = null)

/** TextAction's own padding, balanced so the label centres under the hero's button. */
private val CenteredTextActionPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

/** A text action under the hero's message line: flush with the line's own start edge. */
private val MessageTextActionPadding = PaddingValues(start = 0.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)

/** AmberTickerRowGroup's own 16dp side inset, shared by the hero so every block lines up. */
private val GroupSide = 16.dp
private val HeroRadius = 28.dp
private val HeroPadding = 20.dp
private val RowPadding = 16.dp
private val RowMinHeight = 56.dp
/**
 * The row's own top and bottom clearance around its content, widened from 10dp (QA 2026-09-26, D4,
 * this file's own [CabinetRow] doc comment): real margin between a trailing single-line action's
 * ink and the row's own edge, so a clip that lands at that edge (a scrolling list's own viewport
 * bound, never this row's fault on its own) falls in blank space rather than through a descender.
 */
private val RowVerticalPadding = 12.dp
private val FigureGap = 12.dp
private val EndGap = 28.dp

// ---- Previews ------------------------------------------------------------------------------

private val PreviewAccount = WalletAccount(publicKey = ByteArray(32) { 7 }, label = "Seed Vault Wallet")

private val PreviewDevice = YouUiState(swapsRecorded = 2, votesCast = 1, stocksWatched = 4)
private val PreviewFree = ProUiState(entitlementLoading = false, walletConnected = false)
private val PreviewProPass = ProUiState(
    entitlementLoading = false,
    pro = true,
    source = EntitlementSource.PASS,
    untilMillis = 1_792_368_000_000L,
    walletConnected = true,
    stakeRaw = 38_406_150_222L,
)
private const val PreviewNow = 1_790_000_000_000L

@InstrumentPreviews
@Composable
private fun YouSignedOutPreview() {
    AmberPreviewCanvas {
        YouContent(
            state = PreviewDevice,
            pro = PreviewFree,
            onConnect = { _ -> },
            onDisconnect = {},
            onRefreshEntitlement = {},
            onPay = {},
            onOpenTab = {},
            account = AccountUiState.SignedOut(),
            nowMillis = PreviewNow,
        )
    }
}

@InstrumentPreviews
@Composable
private fun YouProPassPreview() {
    AmberPreviewCanvas {
        YouContent(
            state = PreviewDevice.copy(account = PreviewAccount, notificationsOn = true),
            pro = PreviewProPass,
            onConnect = { _ -> },
            onDisconnect = {},
            onRefreshEntitlement = {},
            onPay = {},
            onOpenTab = {},
            account = AccountUiState.SignedIn(SignedInAccount("ann@example.com", "Ann", emptyList())),
            onOpenDigest = {},
            nowMillis = PreviewNow,
        )
    }
}
