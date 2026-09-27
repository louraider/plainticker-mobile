package com.plainticker.mobile.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.TextAction

/**
 * Links to a public explorer, so a person can check on the chain what this app says happened
 * (Mert, judges' review 2026-09-27: "explorer links"). Solscan, because it reads Token-2022 and
 * v0 transactions and names xStock mints.
 *
 * Only a transaction signature or a public address ever goes into the URL, and only one that is
 * plain base58 of a plausible length: nothing else this app holds (the device code above all,
 * which is a bearer credential) can reach an explorer through here.
 */
object Explorer {
    private const val BASE = "https://solscan.io"

    /** A base58 signature: 64 bytes encode to 86 to 88 characters. */
    private val SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{64,90}$")

    /** A base58 public key: 32 bytes encode to 32 to 44 characters. */
    private val ADDRESS = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

    /** Solscan's page for the transaction [signature], or null when it is not a signature. */
    fun transaction(signature: String): String? =
        signature.trim().takeIf { SIGNATURE.matches(it) }?.let { "$BASE/tx/$it" }

    /** Solscan's page for the account [address], or null when it is not an address. */
    fun account(address: String): String? =
        address.trim().takeIf { ADDRESS.matches(it) }?.let { "$BASE/account/$it" }
}

/**
 * "View on Solscan" for a landed transaction, as a [TextAction]. Draws nothing for a string that
 * is not a signature. Every surface that shows a signature can call it: the swap receipt, the
 * pass receipt, and the vote that landed.
 */
@Composable
fun SolscanAction(
    signature: String,
    color: Color,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.action_view_on_solscan),
    contentPadding: PaddingValues = SolscanPadding,
) {
    val url = Explorer.transaction(signature) ?: return
    val open = rememberExplorerOpener()
    TextAction(label = label, onClick = { open(url) }, modifier = modifier, color = color, contentPadding = contentPadding)
}

/**
 * Opens an explorer URL in the browser. A phone with no browser to take it does nothing rather
 * than crash: the signature is still on the screen to copy.
 */
@Composable
fun rememberExplorerOpener(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return { url -> runCatching { handler.openUri(url) } }
}

/** Flush with the sheet's own text column: no start inset of its own, 48dp tall with its label. */
private val SolscanPadding = PaddingValues(start = 0.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)
