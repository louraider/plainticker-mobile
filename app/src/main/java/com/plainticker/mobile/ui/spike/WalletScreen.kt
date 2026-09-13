package com.plainticker.mobile.ui.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.MainViewModel
import com.plainticker.mobile.WalletUiState
import com.plainticker.mobile.ui.theme.PlainTickerTheme
import com.plainticker.mobile.wallet.MwaWalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * The wallet spike, reachable from the home screen in debug builds only, for manual QA of
 * capabilities and the quote + sign path. Moved verbatim from MainActivity; replaced, not
 * restyled, by the real screens.
 */
@Composable
fun SpikeScreen(
    sender: ActivityResultSender,
    modifier: Modifier = Modifier,
) {
    val viewModel: MainViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WalletScreen(
        state = state,
        onConnect = { viewModel.connect(sender) },
        // The wallet shows this text to a person and asks them to sign it, so it says the
        // product's name, from the one place the name lives.
        onSignMessage = { viewModel.signMessage(sender, "Hello from " + MwaWalletSession.IDENTITY_NAME) },
        onDisconnect = { viewModel.disconnect(sender) },
        onProbeCapabilities = { viewModel.probeCapabilities(sender) },
        onSwapTsla = { viewModel.swapForXStock(sender, "TSLAx", MainViewModel.TSLAX_MINT) },
        modifier = modifier,
    )
}

@Composable
fun WalletScreen(
    state: WalletUiState,
    onConnect: () -> Unit,
    onSignMessage: () -> Unit,
    onDisconnect: () -> Unit,
    onProbeCapabilities: () -> Unit,
    onSwapTsla: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = MwaWalletSession.IDENTITY_NAME + " + Mobile Wallet Adapter",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        // Runs before anything else in the spike: it decides whether the gasless swap
        // path exists on this wallet. Signs nothing, spends nothing.
        Button(onClick = onProbeCapabilities, enabled = !state.isLoading) {
            Text("Probe wallet capabilities")
        }

        state.capabilities?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        if (state.address != null) {
            Button(onClick = onSwapTsla, enabled = !state.isLoading) {
                Text(
                    if (BuildConfig.SUBMIT_SWAPS) "Swap \$5 USDC for TSLAx (REAL)"
                    else "Quote + sign \$5 TSLAx (no submit)"
                )
            }
        }

        state.swapReport?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        if (state.address == null) {
            Button(onClick = onConnect, enabled = !state.isLoading) {
                Text("Connect wallet")
            }
        } else {
            Text(
                text = "Connected: ${state.address.take(4)}…${state.address.takeLast(4)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onSignMessage, enabled = !state.isLoading) {
                Text("Sign message")
            }
            OutlinedButton(onClick = onDisconnect, enabled = !state.isLoading) {
                Text("Disconnect")
            }
        }

        state.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WalletScreenDisconnectedPreview() {
    PlainTickerTheme {
        WalletScreen(
            state = WalletUiState(),
            onConnect = {},
            onSignMessage = {},
            onDisconnect = {},
            onProbeCapabilities = {},
            onSwapTsla = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WalletScreenCapabilitiesPreview() {
    PlainTickerTheme {
        WalletScreen(
            state = WalletUiState(
                address = "11111111111111111111111111111111",
                capabilities = "cluster: solana:mainnet\n" +
                    "tx versions: legacy, 0\n" +
                    "max tx/request: 0\n" +
                    "max msg/request: 0\n\n" +
                    "[yes] signTransactions  <- gasless swap needs this\n" +
                    "[yes] signAndSendTransaction  (Metis fallback)\n" +
                    "[NO ] signInWithSolana  (SKR curation needs this)\n" +
                    "[yes] signMessages",
            ),
            onConnect = {},
            onSignMessage = {},
            onDisconnect = {},
            onProbeCapabilities = {},
            onSwapTsla = {},
        )
    }
}
