package com.myapp.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myapp.AppContainer
import com.myapp.BuildConfig
import com.myapp.ui.appViewModelFactory
import com.myapp.ui.detail.DetailScreen
import com.myapp.ui.gallery.GalleryScreen
import com.myapp.ui.home.HomeScreen
import com.myapp.ui.home.HomeTab
import com.myapp.ui.onboarding.OnboardingScreen
import com.myapp.ui.spike.SpikeScreen
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * onboarding (once) -> home (list | portfolio | watchlist) -> detail/{ticker}; plus, in debug
 * builds, the spike screen for manual wallet QA and the component gallery for design QA.
 */
@Composable
fun AppNavHost(
    container: AppContainer,
    sender: ActivityResultSender,
    modifier: Modifier = Modifier,
    openTab: Int? = null,
    onTabOpened: () -> Unit = {},
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

    // A second tap while the app is already open arrives here rather than at the start
    // destination, so the tab is switched by navigating home again in place.
    LaunchedEffect(openTab) {
        val tab = openTab ?: return@LaunchedEffect
        onTabOpened()
        if (!onboarded) return@LaunchedEffect
        if (navController.currentDestination?.route == startDestination) return@LaunchedEffect
        navController.navigate(Routes.home(tab)) {
            popUpTo(Routes.HOME_TAB) { inclusive = true }
            launchSingleTop = true
        }
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
                onOpenSpike = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.SPIKE) }) else null,
                onOpenGallery = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.GALLERY) }) else null,
                initialTab = entry.arguments?.getInt(Routes.ARG_TAB) ?: HomeTab.LIST.ordinal,
            )
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
            composable(Routes.SPIKE) {
                SpikeScreen(sender = sender)
            }
            composable(Routes.GALLERY) {
                GalleryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
