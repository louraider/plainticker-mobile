package com.plainticker.mobile.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.AmberBottomNav
import com.plainticker.mobile.ui.components.AmberDestination
import com.plainticker.mobile.ui.components.TopBar
import com.plainticker.mobile.ui.components.TopScrim
import com.plainticker.mobile.ui.portfolio.PortfolioScreen
import com.plainticker.mobile.ui.stocks.StocksScreen
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.today.TodayScreen
import com.plainticker.mobile.ui.vote.VoteScreen
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel
import com.plainticker.mobile.ui.you.YouScreen

/**
 * The pre-Amber destinations, kept only as a stable ordinal namespace for the paths that still
 * address a screen by number rather than by [AmberDestination] directly: the digest
 * notification's stored `EXTRA_TAB` (com.plainticker.mobile.watchlist.WatchlistNotifications), a
 * landed swap's "View in Portfolio" (ui/nav/AppNavHost.kt), and You's own device-fact cells
 * (ui/you/YouScreen.kt, left untouched by this task, which still calls `onOpenTab(tab.ordinal)`
 * with a [HomeTab]). [HomeTabTest] pins every ordinal exactly because a silent renumbering here
 * would send one of those stored or hardcoded ordinals to the wrong screen.
 *
 * Nothing composes against this enum directly any more: [toAmberDestination] is the one place a
 * [HomeTab] ordinal becomes the [AmberDestination] the shell actually draws
 * (docs/design-research-2026-09-21.md section 3). LIST and WATCHLIST both resolve to TODAY: LIST
 * because the app's home is Today now (LIST.ordinal was always only ever used as the "no tab
 * named" default, never as an explicit deep-link target), and WATCHLIST because Watchlist folded
 * into Today's Yours block rather than staying a destination of its own. VOTE, PORTFOLIO and YOU
 * are unchanged, since the research kept those three as peers.
 */
enum class HomeTab(@StringRes val label: Int) {
    LIST(R.string.tab_list),
    VOTE(R.string.tab_vote),
    PORTFOLIO(R.string.tab_portfolio),
    WATCHLIST(R.string.tab_watchlist),
    YOU(R.string.you_heading),
}

/**
 * Which [AmberDestination] a [HomeTab] ordinal now opens; see [HomeTab]'s own doc for why two of
 * the five collapse onto one destination. [HomeTabDestinationTest] pins this mapping, including
 * the two-to-one collapse, so it cannot drift from the research without a test noticing.
 */
fun HomeTab.toAmberDestination(): AmberDestination = when (this) {
    HomeTab.LIST -> AmberDestination.TODAY
    HomeTab.VOTE -> AmberDestination.VOTE
    HomeTab.PORTFOLIO -> AmberDestination.PORTFOLIO
    HomeTab.WATCHLIST -> AmberDestination.TODAY
    HomeTab.YOU -> AmberDestination.YOU
}

/**
 * A [HomeTab] ordinal from outside this screen (a stored intent extra, a route argument), read
 * defensively the way the pre-Amber code coerced an out-of-range `initialTab` into range: an
 * ordinal this build does not recognise falls back to [HomeTab.LIST], the same "no tab named"
 * default the nav graph itself declares (ui/nav/AppNavHost.kt's `Routes.ARG_TAB` default).
 */
private fun homeTabFrom(ordinal: Int): HomeTab = HomeTab.entries.getOrElse(ordinal) { HomeTab.LIST }

/**
 * The shell: five bar destinations under one [AmberBottomNav], docs/design-research-2026-09-21.md
 * section 3: Today, Stocks, Vote, Portfolio, You. The top tab row is gone entirely, and so is the
 * TopBar's own You action: You is one of the five peers now, reached the same way the other four
 * are.
 *
 * Today and Stocks are this task's own scaffolding ([TodayScreen], [StocksScreen]); Vote,
 * Portfolio and You are untouched screens this task only re-hosts. Every ViewModel is scoped to
 * this back-stack entry through [factory], so switching destinations keeps their state exactly as
 * the four-tab shell did.
 *
 * **Back.** The old cabinet made back from You return to the tab the reader had left, because You
 * sat outside the tab row and every other tab was reached only by tapping it directly. With five
 * equal peers there is no "outside" any more, so the rule generalises: back from any destination
 * but Today (the home) returns to whichever destination was selected immediately before it, and
 * that return is consumed once, resetting the memory to Today, so a second back press keeps
 * unwinding toward the home instead of ping-ponging between the same two destinations. Back from
 * Today itself is the system's own back: Today has nothing under it to return to.
 *
 * **The TopBar's right slot, judged against this shell rather than the one U9 was written for
 * (docs/plan-app-uiux-2026-09-21.md's after-the-hackathon table).** U9 asked for the wallet's
 * short key there when a session is open, "You" otherwise. "You" is answered above: it is a bar
 * destination now, not a fallback for an empty slot. The wallet half does not carry over either,
 * and is deliberately not built: [header] is the *same* composable on all five destinations, so a
 * session's key drawn there would sit over Today, Stocks and Vote, three screens a wallet has
 * nothing to do with, and it would duplicate what the two screens that do already draw
 * ([com.plainticker.mobile.ui.portfolio.PortfolioScreen]'s own `WalletKeyLine`, You's own
 * `WalletBlock`). The slot stays empty; [HomeScreenTest] pins that the shared [TopBar] call
 * carries neither `action` nor `meta` so a later change cannot reopen this quietly.
 */
@Composable
fun HomeScreen(
    factory: ViewModelProvider.Factory,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
    initialTab: Int = HomeTab.LIST.ordinal,
    /** Today's digest line and You's digest link: the digest screen, a route of its own. */
    onOpenDigest: (() -> Unit)? = null,
    /**
     * True when Detail's "Have a code? Get Pro" asked for You with the promo code field open
     * (judges' round 2). Home selects You and hands the request on; [onPromoOpened] clears it once
     * You has acted on it, so a later recomposition does not open the field again.
     */
    openPromo: Boolean = false,
    onPromoOpened: () -> Unit = {},
) {
    val initialDestination = homeTabFrom(initialTab).toAmberDestination()
    var selectedOrdinal by rememberSaveable { mutableIntStateOf(initialDestination.ordinal) }
    var previousOrdinal by rememberSaveable { mutableIntStateOf(AmberDestination.TODAY.ordinal) }
    val destinations = AmberDestination.entries
    val selected = destinations.getOrElse(selectedOrdinal) { AmberDestination.TODAY }

    fun select(destination: AmberDestination) {
        if (destination.ordinal != selectedOrdinal) previousOrdinal = selectedOrdinal
        selectedOrdinal = destination.ordinal
    }

    // You's device-fact cells still hand back a HomeTab ordinal (YouScreen.kt, left untouched by
    // this task); translated the same way a stored deep link is.
    fun selectTab(tabOrdinal: Int) = select(homeTabFrom(tabOrdinal).toAmberDestination())

    LaunchedEffect(openPromo) {
        if (openPromo) select(AmberDestination.YOU)
    }

    BackHandler(enabled = selected != AmberDestination.TODAY) {
        selectedOrdinal = previousOrdinal
        previousOrdinal = AmberDestination.TODAY.ordinal
    }

    // Computed once here rather than left to each component's own default: the bar, the wordmark
    // header and the scrim all sit on the same five screens, and they must agree with each other
    // and with the destination beneath them on every recomposition a theme switch causes, not just
    // happen to end up with the same answer because they each asked isSystemInDarkTheme() apart.
    val colors = if (isSystemInDarkTheme()) AmberDarkColors else AmberLightColors

    val header: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            TopBar(onTitleLongPress = onOpenGallery, colors = colors)
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (selected) {
                    AmberDestination.TODAY -> {
                        val watchlistViewModel: WatchlistViewModel = viewModel(factory = factory)
                        TodayScreen(
                            watchlistViewModel = watchlistViewModel,
                            onOpenDetail = onOpenDetail,
                            // Nothing watched yet is the common first state, and the one place to
                            // fix it is Stocks now, not List.
                            onBrowseStocks = { select(AmberDestination.STOCKS) },
                            // The same gate the gallery sits behind.
                            onRunCheck = if (BuildConfig.DEBUG) watchlistViewModel::runCheckNow else null,
                            onOpenVote = { select(AmberDestination.VOTE) },
                            onOpenDigest = onOpenDigest,
                            header = header,
                        )
                    }

                    AmberDestination.STOCKS -> StocksScreen(
                        viewModel = viewModel(factory = factory),
                        // Scoped to the home entry like every other ViewModel here, so a vote that
                        // is mid-flight survives a destination switch and comes back to its sheet.
                        voteViewModel = viewModel(factory = factory),
                        onOpenDetail = onOpenDetail,
                        header = header,
                    )

                    AmberDestination.VOTE -> VoteScreen(
                        viewModel = viewModel(factory = factory),
                        // The same sheet Stocks' rows and Detail's action open: scoped to this
                        // home entry like every ViewModel here, so a vote mid-flight survives a
                        // destination switch.
                        voteViewModel = viewModel(factory = factory),
                        onOpenDetail = onOpenDetail,
                        header = header,
                    )

                    AmberDestination.PORTFOLIO -> PortfolioScreen(
                        viewModel = viewModel(factory = factory),
                        onOpenDetail = onOpenDetail,
                        // A wallet holding no xStock is offered Stocks rather than a dead end; the
                        // destination is the host's to select, so the screen asks for it rather
                        // than navigating.
                        onBrowseList = { select(AmberDestination.STOCKS) },
                        header = header,
                        // "Swap to USDC" on a holding: scoped to this home entry like every
                        // ViewModel here, so a swap mid flight survives a destination switch.
                        swapViewModel = viewModel(factory = factory),
                    )

                    AmberDestination.YOU -> YouScreen(
                        viewModel = viewModel(factory = factory),
                        // Scoped to the home entry like every other ViewModel here, so a payment
                        // mid flight and this device's entitlement survive a destination switch.
                        passViewModel = viewModel(factory = factory),
                        accountViewModel = viewModel(factory = factory),
                        onOpenTab = ::selectTab,
                        onOpenDigest = onOpenDigest,
                        header = header,
                        openPromo = openPromo,
                        onPromoOpened = onPromoOpened,
                    )
                }
            }
            AmberBottomNav(selected = selected, onSelect = ::select, colors = colors)
        }

        // The one thing on these five screens that does not scroll, and it is not content: the
        // band the system clock sits in, so the hero and the header dissolve under it instead of
        // colliding with it (Insets.kt). Last in the Box, so it draws over whichever destination
        // is up. groundColor follows the same [colors] as the bar and the header above: Instrument's
        // fixed-dark Canvas painted an opaque near-black band across the top of every screen
        // regardless of theme, which read as a black bar under the clock in light.
        TopScrim(Modifier.align(Alignment.TopCenter), groundColor = colors.surfaceGround)
    }
}
