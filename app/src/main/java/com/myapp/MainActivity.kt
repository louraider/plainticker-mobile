package com.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.myapp.ui.nav.AppNavHost
import com.myapp.ui.theme.MyappTheme
import com.myapp.wallet.MwaWalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {

    private var walletSession: MwaWalletSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = appContainer
        // Must be created before the Activity starts: it registers for an activity result.
        val sender = ActivityResultSender(this)
        walletSession = container.walletSession.bind(sender)

        setContent {
            MyappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        container = container,
                        sender = sender,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        walletSession?.let { appContainer.walletSession.unbind(it) }
        walletSession = null
        super.onDestroy()
    }
}
