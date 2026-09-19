package com.plainticker.mobile.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.TopScrim
import com.plainticker.mobile.ui.components.TopTabs
import com.plainticker.mobile.ui.list.ListScreen
import com.plainticker.mobile.ui.portfolio.PortfolioScreen
import com.plainticker.mobile.ui.vote.VoteScreen
import com.plainticker.mobile.ui.vote.VoteTabViewModel
import com.plainticker.mobile.ui.vote.VoteViewModel
import com.plainticker.mobile.ui.watchlist.WatchlistScreen
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel

/** List, Vote, Portfolio, Watchlist, the order docs/plan-monetisation-2026-09-19.md section 1.5 settles. */
enum class HomeTab(@StringRes val label: Int) {
    LIST(R.string.tab_list),
    VOTE(R.string.tab_vote),
    PORTFOLIO(R.string.tab_portfolio),
    WATCHLIST(R.string.tab_watchlist),
}

/**
 * The four tab screens under one wordmark: the real TopBar and TopTabs (Muted inactive labels
 * and a 2dp Accent indicator, not the Material TabRow the placeholder carried), handed to the
 * selected screen as a header it draws at the top of its own scroll. That is what makes the
 * header scroll away instead of sitting sticky over the content (DESIGN.md sections 4 and 5).
 *
 * The ViewModels are scoped to the home back-stack entry, so switching tabs keeps their state.
 */
@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    onOpenDetail: (String) -> Unit,
    onOpenSpike: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
    initialTab: Int = HomeTab.LIST.ordinal,
) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(HomeTab.entries.indices)) }
    val tabs = HomeTab.entries
    val labels = tabs.map { stringResource(it.label) }

    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            TopBar(onTitleLongPress = onOpenGallery)
            TopTabs(items = labels, selected = selected, onSelect = { selected = it })
            DebugActions(onOpenSpike = onOpenSpike)
        }
    }

    Box(modifier.fillMaxSize()) {
        when (tabs[selected]) {
            HomeTab.LIST -> ListScreen(
                viewModel = viewModel(factory = factory),
                // Scoped to the home entry like every other ViewModel here, so a vote that is
                // mid-flight survives a tab switch and comes back to its own sheet.
                voteViewModel = viewModel(factory = factory),
                onOpenDetail = onOpenDetail,
                header = header,
            )

            HomeTab.VOTE -> VoteScreen(
                viewModel = viewModel(factory = factory),
                // The same sheet the List's rows and Detail's action open: scoped to this home
                // entry like every ViewModel here, so a vote mid-flight survives a tab switch.
                voteViewModel = viewModel(factory = factory),
                onOpenDetail = onOpenDetail,
                header = header,
            )

            HomeTab.PORTFOLIO -> PortfolioScreen(
                viewModel = viewModel(factory = factory),
                // Scoped to the home entry like every other ViewModel here, so a payment mid
                // flight and this device's entitlement survive a tab switch.
                passViewModel = viewModel(factory = factory),
                onOpenDetail = onOpenDetail,
                // A wallet holding no xStock is offered the list rather than a dead end; the tab
                // is the host's to select, so the screen asks for it rather than navigating.
                onBrowseList = { selected = HomeTab.LIST.ordinal },
                header = header,
            )

            HomeTab.WATCHLIST -> {
                val watchlist: WatchlistViewModel = viewModel(factory = factory)
                WatchlistScreen(
                    viewModel = watchlist,
                    onOpenDetail = onOpenDetail,
                    // Nothing watched yet is the common first state, and the one place to fix it
                    // is the list. The tab is the host's to select, so the screen asks for it.
                    onBrowseList = { selected = HomeTab.LIST.ordinal },
                    // The same gate the gallery and the wallet spike sit behind.
                    onRunCheck = if (BuildConfig.DEBUG) watchlist::runCheckNow else null,
                    header = header,
                )
            }
        }

        // The one thing on these four screens that does not scroll, and it is not content: the
        // band the system clock sits in, so the hero and the tabs dissolve under it instead of
        // colliding with it (Insets.kt). Last in the Box, so it draws over whichever tab is up.
        TopScrim(Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Debug builds only: one text action to the wallet spike. The component gallery has its own way
 * in, a long press on the wordmark (see TopBar), so it costs the header no width: two actions
 * side by side do not fit the 400dp frame and the second one was clipped on the device.
 */
@Composable
private fun DebugActions(onOpenSpike: (() -> Unit)?) {
    if (onOpenSpike == null) return
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        TextAction(label = stringResource(R.string.debug_open_wallet_spike), onClick = onOpenSpike)
    }
}
