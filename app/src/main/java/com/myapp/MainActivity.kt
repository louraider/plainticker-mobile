package com.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.myapp.ui.theme.MyappTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sender = ActivityResultSender(this)

        setContent {
            MyappTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WalletScreen(
                        state = state,
                        onConnect = { viewModel.connect(sender) },
                        onSignMessage = { viewModel.signMessage(sender, "Hello from Myapp!") },
                        onDisconnect = { viewModel.disconnect(sender) },
                        onProbeCapabilities = { viewModel.probeCapabilities(sender) },
                        onBuyTsla = {
                            viewModel.buyXStock(sender, "TSLAx", MainViewModel.TSLAX_MINT)
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun WalletScreen(
    state: WalletUiState,
    onConnect: () -> Unit,
    onSignMessage: () -> Unit,
    onDisconnect: () -> Unit,
    onProbeCapabilities: () -> Unit,
    onBuyTsla: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Myapp + Mobile Wallet Adapter",
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
            Button(onClick = onBuyTsla, enabled = !state.isLoading) {
                Text(
                    if (MainViewModel.SUBMIT_SWAPS) "Buy \$5 TSLAx (REAL)"
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
    MyappTheme {
        WalletScreen(
            state = WalletUiState(),
            onConnect = {},
            onSignMessage = {},
            onDisconnect = {},
            onProbeCapabilities = {},
            onBuyTsla = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WalletScreenCapabilitiesPreview() {
    MyappTheme {
        WalletScreen(
            state = WalletUiState(
                address = "Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU",
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
            onBuyTsla = {},
        )
    }
}
