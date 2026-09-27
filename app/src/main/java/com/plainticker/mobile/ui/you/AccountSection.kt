package com.plainticker.mobile.ui.you

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.plainticker.mobile.R
import com.plainticker.mobile.data.auth.DeviceCodeStatus
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * You's "Sign-in methods" group (docs/google-sign-in.md; the cabinet pass, 2026-09-25, after the
 * web cabinet's own group of the same name): the Google account, then the wallets the server
 * returned as linked to that account, if any. The phone's own Solana wallet connection is not a
 * sign-in method (it is a Mobile Wallet Adapter session that signs transactions), so since the
 * judges' round 2 it is its own group, [WalletSection].
 *
 * - **Google**: the email with Sign out, or "Not signed in" with Sign in, or "Signing in". Sign
 *   out is a two-step inline confirm (the web cabinet's rule for anything that drops a link):
 *   the first tap opens a sentence and Sign out / Cancel in the same row, never a dialog.
 *   Every [AccountMessage] a sign-in can end in is drawn under this row, unless the hero is the
 *   one offering Sign in ([showMessage] false), in which case the hero draws it beside its button.
 * - **Linked wallets**: one row per wallet the server returned, each its own short key with Copy
 *   and Unlink; an account with none draws no row at all, rather than an empty one. Unlink is the
 *   same two-step inline confirm as Sign out ("Unlink this wallet?" with Unlink and Keep). A
 *   landed unlink drops the wallet from the next state the server hands back, so the row is simply
 *   gone; a refusal keeps the row with one plain line under it ([unlinkFailureRes]), never a
 *   screen-wide message, and [AccountUiState.SignedIn.unlinkingWallet] hides a row's own actions
 *   while its own call is in flight, so a second tap on any row cannot start a second one.
 *
 * **The clipping rule.** Every row is [CabinetRow]: the text column is weighted and wraps; the one
 * inline action is unweighted and one line. `CabinetFitTest` measures every action label here with
 * fontTools against the Outfit SemiBold file `TextAction` draws, and the wallet key in JetBrains
 * Mono, and proves each fits at 1.3x beside the space left for the key.
 *
 * **The device code** (2026-09-27): a rekey that can never finish, or a code the server has
 * retired, draws one caution row first ("This phone", [deviceCodeNoticeRes]), never the code.
 * A Google sign-in refused with `link_on_web` carries its own text action to the web account
 * page ([AccountMessage.LINK_ON_WEB_URL]), since linking happens there. A sign-out in flight
 * reads "Signing out" and offers nothing until the server has answered.
 *
 * No amber fill here: the hero above is the only place You draws one.
 */
@Composable
internal fun AccountSection(
    state: AccountUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onUnlink: (String) -> Unit,
    colors: AmberColors,
    showMessage: Boolean = true,
    deviceCodeStatus: DeviceCodeStatus = DeviceCodeStatus.OK,
) {
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(
            title = stringResource(R.string.you_heading_methods),
            lede = stringResource(R.string.account_pitch),
            colors = colors,
        )
        AmberTickerRowGroup(colors = colors) {
            deviceCodeNoticeRes(deviceCodeStatus)?.let { notice ->
                CabinetRow(
                    colors = colors,
                    value = stringResource(R.string.device_code_label),
                    sub = stringResource(notice),
                    subCaution = true,
                )
            }
            GoogleRow(state = state, onSignIn = onSignIn, onSignOut = onSignOut, colors = colors, showMessage = showMessage)
            (state as? AccountUiState.SignedIn)?.let { signedIn ->
                LinkedWalletsGroup(state = signedIn, onUnlink = onUnlink, colors = colors)
            }
        }
    }
}

@Composable
private fun GoogleRow(
    state: AccountUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    colors: AmberColors,
    showMessage: Boolean,
) {
    val label = stringResource(R.string.you_method_google)
    val uriHandler = LocalUriHandler.current
    // runCatching: a phone with no browser has nothing to open, and a tap must never crash.
    val openWeb = RowAction(stringResource(R.string.account_action_open_web), {
        runCatching { uriHandler.openUri(AccountMessage.LINK_ON_WEB_URL) }
    })
    when (state) {
        AccountUiState.Restoring -> CabinetRow(colors = colors, label = label, value = stringResource(R.string.state_loading))
        is AccountUiState.SignedOut -> CabinetRow(
            colors = colors,
            label = label,
            value = stringResource(R.string.you_identity_none),
            sub = state.message?.takeIf { showMessage }?.let { stringResource(accountMessageRes(it)) },
            subCaution = true,
            actions = listOfNotNull(
                RowAction(stringResource(R.string.you_action_sign_in), onSignIn),
                openWeb.takeIf { showMessage && accountMessageOpensWeb(state.message) },
            ),
        )
        AccountUiState.SigningIn -> CabinetRow(colors = colors, label = label, value = stringResource(R.string.account_signing_in))
        is AccountUiState.SignedIn -> if (state.signingOut) {
            CabinetRow(
                colors = colors,
                label = label,
                value = accountIdentity(state.account).text(),
                sub = stringResource(R.string.account_signing_out),
            )
        } else {
            SignedInRow(state.account, label, onSignOut, colors)
        }
    }
}

/** The two-step sign-out: the first tap asks in place, the second signs out. */
@Composable
private fun SignedInRow(account: SignedInAccount, label: String, onSignOut: () -> Unit, colors: AmberColors) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val signOut = stringResource(R.string.account_sign_out)
    CabinetRow(
        colors = colors,
        label = label,
        value = accountIdentity(account).text(),
        sub = if (confirming) stringResource(R.string.you_sign_out_confirm) else null,
        actions = if (confirming) {
            listOf(
                RowAction(signOut, { confirming = false; onSignOut() }),
                RowAction(stringResource(R.string.you_action_cancel), { confirming = false }),
            )
        } else {
            listOf(RowAction(signOut, { confirming = true }))
        },
    )
}

private const val CopiedMillis = 2_000L

/** One row per wallet the server returned as linked; none draws no row at all. */
@Composable
private fun LinkedWalletsGroup(state: AccountUiState.SignedIn, onUnlink: (String) -> Unit, colors: AmberColors) {
    if (state.account.linkedWallets.isEmpty()) return
    state.account.linkedWallets.forEach { wallet ->
        LinkedWalletRow(
            wallet = wallet,
            busy = state.unlinkingWallet == wallet,
            failure = state.unlinkFailure?.takeIf { it.first == wallet }?.second,
            onUnlink = { onUnlink(wallet) },
            colors = colors,
        )
    }
}

/**
 * The short key with Copy, and Unlink behind the same two-step inline confirm Sign out uses:
 * "Unlink this wallet?" with Unlink and Keep. [busy] hides both actions while this exact wallet's
 * own call is in flight, so a race between two taps on the same row cannot start two calls;
 * [failure] draws the one plain line the last attempt refused with, if any, and both actions stay
 * offered so the row can be tried again.
 */
@Composable
private fun LinkedWalletRow(wallet: String, busy: Boolean, failure: UnlinkFailure?, onUnlink: () -> Unit, colors: AmberColors) {
    var confirming by rememberSaveable(wallet) { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.you_copy_clip_label)
    var copied by remember(wallet) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(CopiedMillis)
            copied = false
        }
    }
    val unlink = stringResource(R.string.account_action_unlink)
    val actions = when {
        busy -> emptyList()
        confirming -> listOf(
            RowAction(unlink, { confirming = false; onUnlink() }),
            RowAction(stringResource(R.string.you_action_keep), { confirming = false }),
        )
        else -> listOf(
            RowAction(stringResource(if (copied) R.string.you_action_copied else R.string.you_action_copy), {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, wallet))) }
                copied = true
            }),
            RowAction(unlink, { confirming = true }),
        )
    }
    CabinetRow(
        colors = colors,
        label = stringResource(R.string.account_wallets_label),
        value = Fmt.shortKey(wallet),
        valueKind = RowValueKind.KEY,
        sub = when {
            confirming && !busy -> stringResource(R.string.account_unlink_confirm)
            failure != null -> stringResource(unlinkFailureRes(failure))
            else -> null
        },
        subCaution = failure != null,
        actions = actions,
    )
}

// ---- Previews ------------------------------------------------------------------------------

@InstrumentPreviews
@Composable
private fun AccountSignedOutPreview() {
    AmberPreviewCanvas {
        AccountSection(
            state = AccountUiState.SignedOut(AccountMessage.CANCELLED),
            onSignIn = {},
            onSignOut = {},
            onUnlink = {},
            colors = AmberDarkColors,
        )
    }
}

@InstrumentPreviews
@Composable
private fun AccountSignedInPreview() {
    AmberPreviewCanvas {
        AccountSection(
            state = AccountUiState.SignedIn(
                SignedInAccount(
                    email = "ann@example.com",
                    name = "Ann",
                    linkedWallets = listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T"),
                ),
            ),
            onSignIn = {},
            onSignOut = {},
            onUnlink = {},
            colors = AmberDarkColors,
        )
    }
}
