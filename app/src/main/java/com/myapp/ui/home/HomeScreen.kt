package com.myapp.ui.home

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.R
import com.myapp.ui.components.TextAction
import com.myapp.ui.components.TopBar
import com.myapp.ui.components.TopTabs
import com.myapp.ui.list.ListScreen
import com.myapp.ui.portfolio.PortfolioScreen
import com.myapp.ui.watchlist.WatchlistScreen

enum class HomeTab(@StringRes val label: Int) {
    LIST(R.string.tab_list),
    PORTFOLIO(R.string.tab_portfolio),
    WATCHLIST(R.string.tab_watchlist),
}

/**
 * The three tab screens under one wordmark: the real TopBar and TopTabs (Muted inactive labels
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
                onOpenDetail = onOpenDetail,
                header = header,
            )

            HomeTab.PORTFOLIO -> PortfolioScreen(
                viewModel = viewModel(factory = factory),
                onOpenDetail = onOpenDetail,
                // A wallet holding no xStock is offered the list rather than a dead end; the tab
                // is the host's to select, so the screen asks for it rather than navigating.
                onBrowseList = { selected = HomeTab.LIST.ordinal },
                header = header,
            )

            HomeTab.WATCHLIST -> WatchlistScreen(
                viewModel = viewModel(factory = factory),
                onOpenDetail = onOpenDetail,
                // Nothing watched yet is the common first state, and the one place to fix it is
                // the list. The tab is the host's to select, so the screen asks for it.
                onBrowseList = { selected = HomeTab.LIST.ordinal },
                header = header,
            )
        }
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
