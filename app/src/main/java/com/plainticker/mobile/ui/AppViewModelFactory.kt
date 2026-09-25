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
import com.plainticker.mobile.ui.pass.PassViewModel
import com.plainticker.mobile.ui.portfolio.PortfolioViewModel
import com.plainticker.mobile.ui.swap.SwapViewModel
import com.plainticker.mobile.ui.vote.VoteTabViewModel
import com.plainticker.mobile.ui.vote.VoteViewModel
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel
import com.plainticker.mobile.ui.you.AccountViewModel
import com.plainticker.mobile.ui.you.DigestViewModel
import com.plainticker.mobile.ui.you.YouViewModel

/** One ViewModel per screen, each built from [AppContainer]; the detail ticker comes from the route. */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        ListViewModel(
            container.summaryRepository,
            container.catalogRepository,
            container.priceRepository,
            container.snapshotRepository,
            container.nextUpRepository,
            container.watchlistStore,
            container.digestStore,
            container.clock,
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
            container.nextUpRepository,
            container.watchlistStore,
            container.notificationPromptStore,
            container.clock,
            container.readApi,
            container.devicePassStore,
        )
    }
    initializer {
        SwapViewModel(
            container.jupiterSwapApi,
            container.walletSession,
            container.rpcRepository,
            container.receiptStore,
            container.clock,
            mints = container.mintRepository,
            secondSource = container.secondSource,
            prices = container.priceRepository,
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
            // Today's other blocks (docs/design-research-2026-09-21.md section 3): the same
            // repositories ListViewModel above joins against.
            container.summaryRepository,
            container.catalogRepository,
            container.priceRepository,
            container.nextUpRepository,
            // Today's first-open Watch asks for notifications once, after the first watch, through
            // the same store Detail's Watch reads.
            prompts = container.notificationPromptStore,
        )
    }
    initializer {
        DigestViewModel(container.digestStore, container.digestNotifier, container.clock)
    }
    initializer {
        VoteViewModel(
            container.voteApi,
            container.walletSession,
            container.rpcRepository,
            container.voteReceiptStore,
            container.clock,
        )
    }
    initializer {
        VoteTabViewModel(
            container.nextUpRepository,
            container.catalogRepository,
            container.summaryRepository,
            container.voteReceiptStore,
            container.walletSession,
        )
    }
    initializer {
        PassViewModel(
            container.passApi,
            container.entitlementApi,
            container.walletSession,
            container.rpcRepository,
            container.devicePassStore,
            container.passReceiptStore,
            container.clock,
        )
    }
    initializer {
        OnboardingViewModel(container.onboardingStore)
    }
    initializer {
        YouViewModel(
            container.walletSession,
            container.receiptStore,
            container.voteReceiptStore,
            container.watchlistStore,
            container.digestNotifier,
        )
    }
    initializer {
        AccountViewModel(container.googleAuthApi, container.accountStore, container.devicePassStore)
    }
}
