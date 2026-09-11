package com.myapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myapp.ui.list.ListScreen
import com.myapp.ui.portfolio.PortfolioScreen
import com.myapp.ui.watchlist.WatchlistScreen

enum class HomeTab(val label: String) {
    LIST("List"),
    PORTFOLIO("Portfolio"),
    WATCHLIST("Watchlist"),
}

/**
 * Placeholder home: a plain TabRow over the three tab screens. The ViewModels are scoped
 * to the home back-stack entry, so switching tabs keeps their state.
 */
@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    onOpenDetail: (String) -> Unit,
    onOpenSpike: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabs = HomeTab.entries

    Column(modifier = modifier.fillMaxSize()) {
        Text("PLAINTICKER")
        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    text = { Text(tab.label) },
                )
            }
        }
        if (onOpenSpike != null) {
            Text("Open wallet spike (debug build only)", modifier = Modifier.clickable(onClick = onOpenSpike))
        }
        when (tabs[selected]) {
            HomeTab.LIST -> ListScreen(viewModel = viewModel(factory = factory), onOpenDetail = onOpenDetail)
            HomeTab.PORTFOLIO -> PortfolioScreen(viewModel = viewModel(factory = factory), onOpenDetail = onOpenDetail)
            HomeTab.WATCHLIST -> WatchlistScreen(viewModel = viewModel(factory = factory), onOpenDetail = onOpenDetail)
        }
    }
}
