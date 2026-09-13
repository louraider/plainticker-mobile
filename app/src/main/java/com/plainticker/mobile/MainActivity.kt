package com.plainticker.mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.plainticker.mobile.ui.nav.AppNavHost
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.PlainTickerTheme
import com.plainticker.mobile.wallet.MwaWalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The one Activity. Portrait and edge to edge (plan section 13 Pass 6): the manifest locks the
 * orientation and declares the size configuration changes, so neither a rotation nor a
 * multi-window resize recreates it during the wallet round trip. Both system bars are transparent
 * with light icons whatever the system theme (the app is dark only), and the root pads nothing:
 * each screen absorbs its own insets, see ui/components/Insets.kt.
 *
 * The manifest starts it in Theme.PlainTicker.Starting (Canvas behind the launcher glyph, DESIGN.md
 * section 9); installSplashScreen must run before super.onCreate so it can swap in the app theme.
 */
class MainActivity : ComponentActivity() {

    private var walletSession: MwaWalletSession? = null

    /**
     * The tab an intent asked for, or null. The digest notification names the Watchlist, and a
     * notification that opens something other than what it named is worse than no notification;
     * this is a flow rather than a start argument because the Activity is single top and the
     * second tap arrives at [onNewIntent], long after the graph was composed.
     */
    private val openTab = MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        openTab.value = tabFrom(intent)
        val container = appContainer
        // Must be created before the Activity starts: it registers for an activity result.
        val sender = ActivityResultSender(this)
        walletSession = container.walletSession.bind(sender)

        setContent {
            val tab by openTab.collectAsState()
            PlainTickerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                    AppNavHost(
                        container = container,
                        sender = sender,
                        openTab = tab,
                        onTabOpened = { openTab.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTab.value = tabFrom(intent)
    }

    private fun tabFrom(intent: Intent?): Int? = intent?.getIntExtra(EXTRA_TAB, -1)?.takeIf { it >= 0 }

    override fun onDestroy() {
        walletSession?.let { appContainer.walletSession.unbind(it) }
        walletSession = null
        super.onDestroy()
    }

    companion object {
        /** Which home tab to open on, as an ordinal of [com.plainticker.mobile.ui.home.HomeTab]. */
        const val EXTRA_TAB = "com.plainticker.mobile.extra.TAB"
    }
}
