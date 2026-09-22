package com.plainticker.mobile.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.ui.watchlist.WatchlistContent
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel

/**
 * Today (docs/design-research-2026-09-21.md section 3): the new home, replacing the endless list
 * the founder complained about. The research draws it in five blocks:
 *
 * 1. Venue and data age, one line of facts.
 * 2. Yours: holdings and watched stocks, each with its one relevant fact.
 * 3. Tracked today: the tokens above the liquidity floor, sorted by depth.
 * 4. Next up: the vote leader, one row, opens Vote.
 * 5. A footer count opening Stocks.
 *
 * This is the shell phase, not the content phase: it creates the destination and wires its
 * ViewModel through the existing factory, and moves the one block that already exists as real,
 * tested product code rather than rebuilding it. Block 2 is the whole of the former Watchlist
 * screen: [WatchlistContent] itself is unchanged (not rewritten), called here through the two new
 * seams it grew for exactly this move (`beforeContent`/`afterContent`), with the same
 * [WatchlistViewModel] HomeScreen used to hand to `HomeTab.WATCHLIST`, so watching a ticker, the
 * daily digest, the "no notifications" line and the empty state all still work unchanged.
 *
 * Blocks 1, 3, 4 and 5 have no Amber-restyled components to draw them with yet (DESIGN.md section
 * 4: the components under every screen are "not yet restyled"), so guessing their layout here
 * would be content work, not shell work. They are left as the `TODO(content)` markers below for
 * the agent who builds Today's real content.
 *
 * On the scroll the research asks for: `HomeTab.WATCHLIST`'s ordinal (the digest notification's
 * `EXTRA_TAB`) resolves to this destination now
 * ([com.plainticker.mobile.ui.home.toAmberDestination]), and the research says that should land
 * "scrolled to Yours." Today, with blocks 1/3/4/5 undrawn, Yours already renders at the top of the
 * screen, so there is nothing to scroll past and no scroll state is wired here; the content agent
 * who builds block 1 is the one who makes that scroll real, once there is something above Yours
 * to scroll past.
 */
@Composable
fun TodayScreen(
    watchlistViewModel: WatchlistViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseStocks: (() -> Unit)? = null,
    onRunCheck: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val state by watchlistViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The one piece of Yours that can change while the app is away: a reader who took the Enable
    // action went to the system settings and came back. The same rule WatchlistScreen kept before
    // this block moved here.
    LifecycleResumeEffect(watchlistViewModel) {
        watchlistViewModel.notificationsChanged()
        onPauseOrDispose { }
    }

    WatchlistContent(
        state = state,
        onUnwatch = watchlistViewModel::unwatch,
        onRetry = watchlistViewModel::refresh,
        onOpenDetail = onOpenDetail,
        // Nothing watched yet is the common first state, and the one place to fix it is Stocks
        // now, not List.
        onBrowseList = onBrowseStocks,
        // The same gate the gallery and the wallet spike sit behind.
        onRunCheck = onRunCheck,
        onEnableNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        modifier = modifier,
        header = header,
        beforeContent = {
            // TODO(content): block 1, "Venue and data age, one line of facts" (research section 3).
        },
        afterContent = {
            // TODO(content): block 3 "Tracked today" and block 4 "Next up" (research section 3),
            // then block 5's footer count opening Stocks. `onBrowseStocks` above already opens
            // Stocks and is the action block 5's footer should call once it exists.
        },
    )
}
