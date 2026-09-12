package com.myapp.ui.nav

import androidx.compose.runtime.Composable
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
) {
    val navController = rememberNavController()
    val factory = remember(container) { appViewModelFactory(container) }
    val startDestination = remember(container) { Routes.start(container.onboardingStore.isOnboarded()) }

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
        composable(Routes.HOME) {
            HomeScreen(
                factory = factory,
                onOpenDetail = { ticker -> navController.navigate(Routes.detail(ticker)) },
                onOpenSpike = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.SPIKE) }) else null,
                onOpenGallery = if (BuildConfig.DEBUG) ({ navController.navigate(Routes.GALLERY) }) else null,
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
