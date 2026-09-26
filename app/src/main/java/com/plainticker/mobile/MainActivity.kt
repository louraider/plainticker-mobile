package com.plainticker.mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.plainticker.mobile.ui.nav.AppNavHost
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberTheme
import com.plainticker.mobile.wallet.MwaWalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The one Activity. Portrait and edge to edge (plan section 13 Pass 6): the manifest locks the
 * orientation and declares the size configuration changes, including `uiMode`, so neither a
 * rotation, a multi-window resize nor a day/night flip recreates it during the wallet round trip;
 * Compose re-reads the configuration itself and [isSystemInDarkTheme] recomposes in place. Both
 * system bars are transparent, with icons that follow the system setting rather than staying
 * light always: light icons on Amber's dark ground, dark icons on Amber's light one, applied in a
 * [SideEffect] in [setContent] so a live theme switch restyles them on the same frame the content
 * recomposes on, not only at the process this Activity happened to launch under. The root pads
 * nothing: each screen absorbs its own insets, see ui/components/Insets.kt.
 *
 * Amber's own theme, not Instrument's fixed-dark `PlainTickerTheme`: this is the wiring DESIGN.md
 * section 11 named as the one thing left, "AmberTheme is not yet the app's active theme." A screen
 * that is open when the system setting changes keeps its full state (its own ViewModel is not
 * recreated, no navigation happens, nothing is lost from a text field or a scroll position) and
 * simply recomposes in the new palette on the next frame, the same way any other configuration
 * value read through Compose would.
 *
 * The manifest starts it in Theme.PlainTicker.Starting (Canvas behind the launcher glyph, DESIGN.md
 * section 9, unchanged: the brand mark is pinned to Instrument's tokens by BrandAssetsTest and is
 * explicitly not this task's scope); installSplashScreen must run before super.onCreate so it can
 * swap in the app theme.
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
        // A starting style only; the real, theme-following style is applied every frame below,
        // once Compose knows whether the system is in dark or light mode.
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
            val darkTheme = isSystemInDarkTheme()
            val colors = if (darkTheme) AmberDarkColors else AmberLightColors

            // Re-applied on every recomposition where darkTheme changes, so a live system-setting
            // switch restyles the status and navigation bar icons rather than leaving them stuck
            // at whatever they were when the process started (SystemBarStyle is a one-shot call,
            // not a value Compose can observe on its own).
            SideEffect {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            AmberTheme(useDarkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = colors.surfaceGround) {
                    AppNavHost(
                        container = container,
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
