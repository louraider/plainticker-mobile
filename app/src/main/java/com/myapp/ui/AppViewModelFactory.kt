package com.myapp.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myapp.AppContainer
import com.myapp.ui.detail.DetailViewModel
import com.myapp.ui.list.ListViewModel
import com.myapp.ui.nav.Routes
import com.myapp.ui.onboarding.OnboardingViewModel
import com.myapp.ui.portfolio.PortfolioViewModel
import com.myapp.ui.swap.SwapViewModel
import com.myapp.ui.watchlist.WatchlistViewModel

/** One ViewModel per screen, each built from [AppContainer]; the detail ticker comes from the route. */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ListViewModel(
            container.summaryRepository,
            container.catalogRepository,
            container.priceRepository,
            container.snapshotRepository,
            container.watchlistStore,
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
        )
    }
    initializer {
        WatchlistViewModel(container.watchlistStore)
    }
    initializer {
        OnboardingViewModel(container.onboardingStore)
    }
}
