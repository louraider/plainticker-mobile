package com.plainticker.mobile.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plainticker.mobile.AppContainer
import com.plainticker.mobile.ui.detail.DetailViewModel
import com.plainticker.mobile.ui.list.ListViewModel
import com.plainticker.mobile.ui.nav.Routes
import com.plainticker.mobile.ui.onboarding.OnboardingViewModel
import com.plainticker.mobile.ui.portfolio.PortfolioViewModel
import com.plainticker.mobile.ui.swap.SwapViewModel
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel

/** One ViewModel per screen, each built from [AppContainer]; the detail ticker comes from the route. */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ListViewModel(
            container.summaryRepository,
            container.catalogRepository,
            container.priceRepository,
            container.snapshotRepository,
            container.watchlistStore,
            container.digestStore,
        )
    }
    initializer {
        val ticker = checkNotNull(createSavedStateHandle().get<String>(Routes.ARG_TICKER)) {
            "the detail route carries no ticker"
        }
        DetailViewModel(
            ticker,
            container.summaryRepository,
            container.catalogRepository,
            container.priceRepository,
            container.mintRepository,
            container.watchlistStore,
            container.notificationPromptStore,
            container.clock,
        )
    }
    initializer {
        SwapViewModel(
            container.jupiterSwapApi,
            container.walletSession,
            container.rpcRepository,
            container.receiptStore,
            container.clock,
        )
    }
    initializer {
        PortfolioViewModel(
            container.walletSession,
            container.rpcRepository,
            container.catalogRepository,
            container.priceRepository,
            container.mintRepository,
            container.receiptStore,
        )
    }
    initializer {
        WatchlistViewModel(
            container.watchlistStore,
            container.watchlistFacts,
            container.digestStore,
            container.digestNotifier,
            container.watchlistScheduler,
            container.clock,
        )
    }
    initializer {
        OnboardingViewModel(container.onboardingStore)
    }
}
