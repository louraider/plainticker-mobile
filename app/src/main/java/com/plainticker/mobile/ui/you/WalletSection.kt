package com.plainticker.mobile.ui.you

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.wallet.WalletAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * You's "Wallet" group (judges' round 2, 2026-09-27). The Solana wallet row used to sit under
 * "Sign-in methods", beside Google, and a judge read that as a claim of wallet sign-in the app does
 * not make: this row is a Mobile Wallet Adapter connection that signs swaps, votes and Pro
 * payments, not a way into an account. So it is a group of its own now, and Sign-in methods keeps
 * Google and the wallets the server has linked to the account (those really are sign-in methods on
 * plainticker.com).
 *
 * - **The wallet**: the short key with Copy and Disconnect, or "Not connected" with Connect, and
 *   one line saying what the connection is for (it used to be a four-line note).
 * - **How it stays connected**: the old note (the session token the wallet issued, encrypted on
 *   this phone, never a key) is kept, behind Show and Hide, rather than dropped: it is the honest
 *   answer to "what does this app keep of my wallet", just not one every reader needs up front.
 *
 * **The clipping rule.** Every row is [CabinetRow]: the text column wraps and the actions are one
 * line each, measured in `CabinetFitTest`. No amber fill: the hero is the only place You draws one.
 */
@Composable
internal fun WalletSection(
    wallet: WalletAccount?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    colors: AmberColors,
    /** This row's Connect found no wallet app on the phone: the shared sentence replaces the note. */
    noWallet: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(title = stringResource(R.string.you_heading_wallet), colors = colors)
        AmberTickerRowGroup(colors = colors) {
            WalletRow(wallet = wallet, onConnect = onConnect, onDisconnect = onDisconnect, colors = colors, noWallet = noWallet)
            // "Stays connected between launches... Disconnect forgets it" describes a session this
            // phone holds, so it is offered only while one is (device QA of 1.3.17: it sat under
            // "Not connected").
            if (wallet != null) WalletNoteRow(colors = colors)
        }
    }
}

@Composable
private fun WalletRow(
    wallet: WalletAccount?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    colors: AmberColors,
    noWallet: Boolean = false,
) {
    val label = stringResource(R.string.you_wallet_method)
    val note = stringResource(R.string.you_wallet_note_short)
    if (wallet == null) {
        // Fresh-device QA of 1.3.23: Connect on a phone with no wallet app did nothing at all. It
        // now says so here, in the sentence every wallet flow shares, and Connect stays for after
        // one is installed.
        CabinetRow(
            colors = colors,
            label = label,
            value = stringResource(R.string.you_wallet_none),
            sub = if (noWallet) stringResource(R.string.no_wallet_app) else note,
            subCaution = noWallet,
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

/**
 * The one no-wallet sentence ([R.string.no_wallet_app]), under the hero's Connect wallet when that
 * is the action that found no wallet app. The same words the swap, vote and pay sheets say.
 */
@Composable
internal fun NoWalletLine(colors: AmberColors, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.no_wallet_app),
        style = AmberType.context,
        color = colors.stateCaution,
        modifier = modifier.fillMaxWidth(),
    )
}

/** What this phone keeps of the wallet connection, folded behind Show until asked for. */
@Composable
private fun WalletNoteRow(colors: AmberColors) {
    var open by rememberSaveable { mutableStateOf(false) }
    CabinetRow(
        colors = colors,
        value = stringResource(R.string.you_wallet_how),
        valueKind = RowValueKind.QUIET,
        actions = listOf(
            RowAction(stringResource(if (open) R.string.you_action_hide else R.string.you_action_show), { open = !open }),
        ),
    ) {
        if (open) {
            Text(
                text = stringResource(R.string.you_wallet_note),
                style = AmberType.context,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private const val CopiedMillis = 2_000L

// ---- Previews ------------------------------------------------------------------------------

@InstrumentPreviews
@Composable
private fun WalletSectionPreview() {
    AmberPreviewCanvas {
        WalletSection(
            wallet = WalletAccount(publicKey = ByteArray(32) { 7 }),
            onConnect = {},
            onDisconnect = {},
            colors = AmberDarkColors,
        )
    }
}
