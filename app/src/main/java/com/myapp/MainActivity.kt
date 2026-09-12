package com.myapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.myapp.ui.nav.AppNavHost
import com.myapp.ui.theme.Canvas
import com.myapp.ui.theme.PlainTickerTheme
import com.myapp.wallet.MwaWalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * The one Activity. Portrait and edge to edge (plan section 13 Pass 6): the manifest locks the
 * orientation and declares the size configuration changes, so neither a rotation nor a
 * multi-window resize recreates it during the wallet round trip. Both system bars are transparent
 * with light icons whatever the system theme (the app is dark only), and the root pads nothing:
 * each screen absorbs its own insets, see ui/components/Insets.kt.
 */
class MainActivity : ComponentActivity() {

    private var walletSession: MwaWalletSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val container = appContainer
        // Must be created before the Activity starts: it registers for an activity result.
        val sender = ActivityResultSender(this)
        walletSession = container.walletSession.bind(sender)

        setContent {
            PlainTickerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                    AppNavHost(container = container, sender = sender)
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
