package com.plainticker.mobile.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.R
import com.plainticker.mobile.prefs.SignedInAccount
import com.plainticker.mobile.ui.components.AmberDisabledAction
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSecondaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.theme.JetBrainsMono

/**
 * You's Account section (docs/google-sign-in.md): Sign in with Google when signed out; the
 * account, its linked wallets and Sign out when signed in. The one-line pitch is the section's
 * lede in every state, so what signing in gives is read before the button that does it.
 *
 * **The clipping rule (DESIGN.md section 4), measured rather than assumed.** Nothing here draws on
 * one line beside a sibling except the two buttons' labels, and those sit alone in a full-width
 * 56dp button: `AccountCopyFitTest` measures "Sign in with Google" and "Signing in" with fontTools
 * against `res/font/bricolage_grotesque.ttf` at [AmberType.button]'s exact instance and proves
 * both fit the button's own content width at 1.3x. Every other line (the pitch, the email, each
 * message) carries no `maxLines`, so a long email or a larger text size wraps instead of
 * clipping; a wallet's short key is nine monospace characters on a line of its own.
 *
 * The sign-in button is [AmberSecondaryAction], never a filled one: the Action block above may
 * already draw an [com.plainticker.mobile.ui.components.AmberPrimaryAction], and You never shows
 * two amber fills.
 */
@Composable
internal fun AccountSection(
    state: AccountUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    colors: AmberColors,
) {
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(
            title = stringResource(R.string.account_heading),
            lede = stringResource(R.string.account_pitch),
            colors = colors,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AccountSide).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                AccountUiState.Restoring -> Unit
                is AccountUiState.SignedOut -> {
                    state.message?.let { AccountMessageLine(it, colors) }
                    AmberSecondaryAction(label = stringResource(R.string.account_sign_in), onClick = onSignIn, colors = colors)
                }
                AccountUiState.SigningIn ->
                    AmberDisabledAction(label = stringResource(R.string.account_signing_in), colors = colors)
                is AccountUiState.SignedIn -> SignedInBlock(state.account, onSignOut, colors)
            }
        }
    }
}

@Composable
private fun AccountMessageLine(message: AccountMessage, colors: AmberColors) {
    Text(text = stringResource(accountMessageRes(message)), style = AmberType.context, color = colors.stateCaution)
}

@Composable
private fun SignedInBlock(account: SignedInAccount, onSignOut: () -> Unit, colors: AmberColors) {
    Text(
        text = stringResource(R.string.account_signed_in_label),
        style = AmberType.meta,
        color = colors.textTertiary(AmberSurface.GROUND),
    )
    Text(text = accountIdentity(account).text(), style = AmberType.body, color = colors.textPrimary)
    Text(
        text = stringResource(R.string.account_wallets_label),
        style = AmberType.meta,
        color = colors.textTertiary(AmberSurface.GROUND),
        modifier = Modifier.padding(top = 8.dp),
    )
    val keys = linkedWalletKeys(account)
    if (keys.isEmpty()) {
        Text(text = stringResource(R.string.account_wallets_none), style = AmberType.context, color = colors.textSecondary)
    } else {
        keys.forEach { key -> Text(text = key, style = AccountWalletKeyStyle, color = colors.textPrimary) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextAction(label = stringResource(R.string.account_sign_out), onClick = onSignOut, color = colors.actionText)
    }
}

/** The same mono key style the Wallet block above draws its own key in (DESIGN.md section 3). */
private val AccountWalletKeyStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = 15.sp, lineHeight = 20.sp)

private val AccountSide = 20.dp

// ---- Previews ------------------------------------------------------------------------------

@InstrumentPreviews
@Composable
private fun AccountSignedOutPreview() {
    AmberPreviewCanvas {
        AccountSection(
            state = AccountUiState.SignedOut(AccountMessage.CANCELLED),
            onSignIn = {},
            onSignOut = {},
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
            colors = AmberDarkColors,
        )
    }
}
