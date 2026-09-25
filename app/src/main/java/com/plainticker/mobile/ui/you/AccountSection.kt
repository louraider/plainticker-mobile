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
import androidx.compose.ui.res.stringResource
import com.plainticker.mobile.R
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.wallet.WalletAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * You's "Sign-in methods" group (docs/google-sign-in.md; the cabinet pass, 2026-09-25, after the
 * web cabinet's own group of the same name): the Google account and the Solana wallet as two rows
 * of one group, then the wallets the server returned as linked to the Google account, if any.
 *
 * - **Google**: the email with Sign out, or "Not signed in" with Sign in, or "Signing in". Sign
 *   out is a two-step inline confirm (the web cabinet's rule for anything that drops a link):
 *   the first tap opens a sentence and Sign out / Cancel in the same row, never a dialog.
 *   Every [AccountMessage] a sign-in can end in is drawn under this row, unless the hero is the
 *   one offering Sign in ([showMessage] false), in which case the hero draws it beside its button.
 * - **Solana wallet**: the short key with Copy and Disconnect, or "Not connected" with Connect,
 *   and the honest note that no key or session is kept.
 * - **Linked wallets**: only when the server returned one or more; an account with none draws no
 *   row, rather than an empty one.
 *
 * **The clipping rule.** Every row is [CabinetRow]: the text column is weighted and wraps; the one
 * inline action is unweighted and one line. `CabinetFitTest` measures every action label here with
 * fontTools against the Outfit SemiBold file `TextAction` draws, and the wallet key in JetBrains
 * Mono, and proves each fits at 1.3x beside the space left for the key.
 *
 * No amber fill here: the hero above is the only place You draws one.
 */
@Composable
internal fun AccountSection(
    state: AccountUiState,
    wallet: WalletAccount?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    colors: AmberColors,
    showMessage: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(
            title = stringResource(R.string.you_heading_methods),
            lede = stringResource(R.string.account_pitch),
            colors = colors,
        )
        AmberTickerRowGroup(colors = colors) {
            GoogleRow(state = state, onSignIn = onSignIn, onSignOut = onSignOut, colors = colors, showMessage = showMessage)
            WalletRow(wallet = wallet, onConnect = onConnect, onDisconnect = onDisconnect, colors = colors)
            (state as? AccountUiState.SignedIn)?.account?.let { account ->
                val keys = linkedWalletKeys(account)
                if (keys.isNotEmpty()) {
                    CabinetRow(
                        colors = colors,
                        label = stringResource(R.string.account_wallets_label),
                        value = keys.joinToString("\n"),
                        valueKind = RowValueKind.KEY,
                    )
                }
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
    when (state) {
        AccountUiState.Restoring -> CabinetRow(colors = colors, label = label, value = stringResource(R.string.state_loading))
        is AccountUiState.SignedOut -> CabinetRow(
            colors = colors,
            label = label,
            value = stringResource(R.string.you_identity_none),
            sub = state.message?.takeIf { showMessage }?.let { stringResource(accountMessageRes(it)) },
            subCaution = true,
            actions = listOf(RowAction(stringResource(R.string.you_action_sign_in), onSignIn)),
        )
        AccountUiState.SigningIn -> CabinetRow(colors = colors, label = label, value = stringResource(R.string.account_signing_in))
        is AccountUiState.SignedIn -> SignedInRow(state.account, label, onSignOut, colors)
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

@Composable
private fun WalletRow(wallet: WalletAccount?, onConnect: () -> Unit, onDisconnect: () -> Unit, colors: AmberColors) {
    val label = stringResource(R.string.you_wallet_method)
    val note = stringResource(R.string.you_wallet_note)
    if (wallet == null) {
        CabinetRow(
            colors = colors,
            label = label,
            value = stringResource(R.string.you_wallet_none),
            sub = note,
            actions = listOf(RowAction(stringResource(R.string.you_action_connect), onConnect)),
        )
        return
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.you_copy_clip_label)
    var copied by remember(wallet.address) { mutableStateOf(false) }
    // "Copied" reads for two seconds, then the action is Copy again.
    LaunchedEffect(copied) {
        if (copied) {
            delay(CopiedMillis)
            copied = false
        }
    }
    CabinetRow(
        colors = colors,
        label = label,
        value = Fmt.shortKey(wallet.address),
        valueKind = RowValueKind.KEY,
        sub = note,
        actions = listOf(
            RowAction(stringResource(if (copied) R.string.you_action_copied else R.string.you_action_copy), {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, wallet.address))) }
                copied = true
            }),
            RowAction(stringResource(R.string.action_disconnect), onDisconnect),
        ),
    )
}

private const val CopiedMillis = 2_000L

// ---- Previews ------------------------------------------------------------------------------

@InstrumentPreviews
@Composable
private fun AccountSignedOutPreview() {
    AmberPreviewCanvas {
        AccountSection(
            state = AccountUiState.SignedOut(AccountMessage.CANCELLED),
            wallet = null,
            onSignIn = {},
            onSignOut = {},
            onConnect = {},
            onDisconnect = {},
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
            wallet = WalletAccount(publicKey = ByteArray(32) { 7 }),
            onSignIn = {},
            onSignOut = {},
            onConnect = {},
            onDisconnect = {},
            colors = AmberDarkColors,
        )
    }
}
