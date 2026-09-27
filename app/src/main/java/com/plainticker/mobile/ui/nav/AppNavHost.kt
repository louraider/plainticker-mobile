package com.plainticker.mobile.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plainticker.mobile.AppContainer
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.ui.appViewModelFactory
import com.plainticker.mobile.ui.detail.DetailScreen
import com.plainticker.mobile.ui.gallery.GalleryScreen
import com.plainticker.mobile.ui.home.HomeScreen
import com.plainticker.mobile.ui.home.HomeTab
import com.plainticker.mobile.ui.onboarding.OnboardingScreen
import com.plainticker.mobile.ui.you.DigestScreen

/**
 * onboarding (once) -> home (list | portfolio | watchlist) -> detail/{ticker}; plus, in debug
 * builds, the component gallery for design QA. The gallery draws sample states and signs nothing.
 * The wallet spike that once sat beside it signed Jupiter bytes without TransactionGuard, and was
 * removed (judges' review, 2026-09-26): every signature this app asks for now goes through a
 * guarded flow.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    openTab: Int? = null,
    onTabOpened: () -> Unit = {},
    /** A stock a notification named: opened over home once the graph is up, then consumed. */
    openTicker: String? = null,
    onTickerOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    val factory = remember(container) { appViewModelFactory(container) }
    val onboarded = remember(container) { container.onboardingStore.isOnboarded() }
    val startDestination = remember(container) {
        // A notification names a tab, and the graph opens on it rather than opening the List and
        // moving: a deep link exists so that no frame of the wrong screen is drawn. Onboarding
        // still wins, though nothing can be watched before it has been passed.
        if (onboarded && openTab != null) Routes.home(openTab) else Routes.start(onboarded)
    }

    // True when the line above already opened the tab the intent named, which is the cold start
    // from a notification. It cannot be read back off the graph: a destination's route is the
    // pattern it was registered under ("home?tab={tab}") and never the filled one, so comparing
    // routes always disagreed and the first tab was opened, popped and built a second time, with
    // every call its screens make paid for twice. Consumed once, so a later tap always navigates.
    var startTabPending by remember { mutableStateOf(startDestination != Routes.start(onboarded)) }

    // A second tap while the app is already open arrives here rather than at the start
    // destination, so the tab is switched by navigating home again in place.
    LaunchedEffect(openTab) {
        val tab = openTab ?: return@LaunchedEffect
        onTabOpened()
        if (!onboarded) return@LaunchedEffect
        if (startTabPending) {
            startTabPending = false
            return@LaunchedEffect
        }
        navController.navigate(Routes.home(tab)) {
            popUpTo(Routes.HOME_TAB) { inclusive = true }
            launchSingleTop = true
        }
    }

    // After the tab above, so the stock opens over the tab the notification also named and back
    // lands on it. Nothing opens before onboarding has been passed.
    LaunchedEffect(openTicker) {
        val ticker = openTicker ?: return@LaunchedEffect
        onTickerOpened()
        if (!onboarded) return@LaunchedEffect
        navController.navigate(Routes.detail(ticker)) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                viewModel = viewModel(factory = factory),
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.HOME_TAB,
            arguments = listOf(
                navArgument(Routes.ARG_TAB) { type = NavType.IntType; defaultValue = HomeTab.LIST.ordinal },
            ),
        ) { entry ->
            HomeScreen(
                factory = factory,
                onOpenDetail = { ticker -> navController.navigate(Routes.detail(ticker)) },
                onOpenGallery = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.GALLERY) }) else null,
                initialTab = entry.arguments?.getInt(Routes.ARG_TAB) ?: HomeTab.LIST.ordinal,
                onOpenDigest = { navController.navigate(Routes.DIGEST) },
            )
        }
        composable(Routes.DIGEST) {
            // No back control on the screen, the same as Detail: the system gesture pops it.
            DigestScreen(viewModel = viewModel(factory = factory))
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument(Routes.ARG_TICKER) { type = NavType.StringType }),
        ) {
            // No back control on the screen: the TopBar carries the Watch action and nothing
            // else (DESIGN.md section 4), and the system gesture pops this entry.
            DetailScreen(
                viewModel = viewModel(factory = factory),
                swapViewModel = viewModel(factory = factory),
                voteViewModel = viewModel(factory = factory),
                // A landed swap sends the reader to the holding it made, and home is replaced
                // rather than stacked so Portfolio reads the chain again with the swap in it.
                onViewPortfolio = {
                    navController.navigate(Routes.home(HomeTab.PORTFOLIO.ordinal)) {
                        popUpTo(Routes.HOME_TAB) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        if (BuildConfig.DEBUG) {
            composable(Routes.GALLERY) {
                GalleryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
