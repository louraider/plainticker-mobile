package com.plainticker.mobile.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.components.AmberFigure
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.watchlist.WatchlistContent
import com.plainticker.mobile.ui.watchlist.WatchlistUiState
import com.plainticker.mobile.ui.watchlist.WatchlistViewModel

/**
 * Today (docs/design-research-2026-09-21.md section 3): the new home, replacing the endless list
 * the founder complained about. The research draws it in five blocks:
 *
 * 1. Venue and data age, one line of facts.
 * 2. Yours: holdings and watched stocks, each with its one relevant fact.
 * 3. Tracked today: the tokens above the liquidity floor, sorted by depth.
 * 4. Next up: the vote leader, one row, opens Vote.
 * 5. A footer count opening Stocks.
 *
 * Block 2 is the whole of the former Watchlist screen: [WatchlistContent] itself is unchanged (not
 * rewritten), called here through the two seams it grew for exactly this move
 * (`beforeContent`/`afterContent`), with the same [WatchlistViewModel] HomeScreen used to hand to
 * `HomeTab.WATCHLIST`, so watching a ticker, the daily digest, the "no notifications" line and the
 * empty state all still work unchanged, and the same instance now also carries blocks 1, 3 and 4's
 * data (see [WatchlistViewModel]'s own class doc for why one ViewModel powers both halves).
 *
 * Blocks 1, 3, 4 and 5 are built here, in the Amber components already in `ui/components`
 * (`AmberFigure`, `AmberSectionHead`, `AmberTickerRow`): the ticker row and the number-with-context
 * component this task's brief names both exist and are used below; the departures-board three-tile
 * status card the approved Amber mockup frame draws for block 1 does not have a matching component
 * yet (none of the five listed fits a row of three stat tiles), so this screen states the same
 * underlying facts (the venue, then the analysis and price ages, exactly as the Ink and Bureau
 * directions' own mockup frames word them) inside [AmberFigure]'s own 28dp status card rather than
 * forking a new shared component for one screen. See the report for the rest of that gap.
 *
 * The lede a reader sees on block 3 ([trackedLede]) states how far the product's coverage reaches
 * today, not how many stocks moved: see that function's own doc for why a coverage fact is the one
 * shape of sentence that stays true in every state this screen can be in, including before the
 * day's first refresh, which a sentence about movement could not promise without a price history
 * this app does not keep.
 *
 * On the scroll the research asks for: `HomeTab.WATCHLIST`'s ordinal (the digest notification's
 * `EXTRA_TAB`) resolves to this destination ([com.plainticker.mobile.ui.home.toAmberDestination]),
 * and the research says that should land "scrolled to Yours." No scroll state is wired here: with
 * block 1 now drawn above Yours, a notification tap would land above it rather than on it, but
 * wiring a scroll-to-anchor is `ui/home` and `ui/nav` work (the digest's `EXTRA_TAB` is read and
 * consumed in [com.plainticker.mobile.ui.home.HomeScreen]), outside this task's file set.
 */
@Composable
fun TodayScreen(
    watchlistViewModel: WatchlistViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBrowseStocks: (() -> Unit)? = null,
    onRunCheck: (() -> Unit)? = null,
    /**
     * Block 4's row opens Vote (research section 3). Null draws the row without a click target
     * rather than nothing at all: [com.plainticker.mobile.ui.home.HomeScreen] is what would wire a
     * real destination switch here (the same way it already does for [onBrowseStocks]), and that
     * file is outside this task's `ui/today` and `ui/watchlist` file set, so the seam is left for
     * whoever next touches `ui/home/HomeScreen.kt` rather than reached for here.
     */
    onOpenVote: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val state by watchlistViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The one piece of Yours that can change while the app is away: a reader who took the Enable
    // action went to the system settings and came back. The same rule WatchlistScreen kept before
    // this block moved here.
    LifecycleResumeEffect(watchlistViewModel) {
        watchlistViewModel.notificationsChanged()
        onPauseOrDispose { }
    }

    WatchlistContent(
        state = state,
        onUnwatch = watchlistViewModel::unwatch,
        onRetry = watchlistViewModel::refresh,
        onOpenDetail = onOpenDetail,
        // Nothing watched yet is the common first state, and the one place to fix it is Stocks
        // now, not List.
        onBrowseList = onBrowseStocks,
        // The same gate the gallery and the wallet spike sit behind.
        onRunCheck = onRunCheck,
        onEnableNotifications = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        modifier = modifier,
        header = header,
        beforeContent = { TodayVenueBlock(state) },
        afterContent = {
            TodayAfterContent(
                state = state,
                onOpenDetail = onOpenDetail,
                onOpenVote = onOpenVote,
                onBrowseStocks = onBrowseStocks,
            )
        },
    )
}

/**
 * Block 1: the venue and data age, in [AmberFigure]'s own status card (DESIGN.md section 4's "28dp
 * for a status card"). Undrawn while [WatchlistUiState.market] is null, which is true both before
 * Today's join has ever run and if the catalog it reads never answers: "the NYSE is closed" is not
 * a sentence this screen can state about a venue it has not read.
 */
@Composable
private fun TodayVenueBlock(state: WatchlistUiState) {
    val sentence = venueSentence(state.market) ?: return
    val freshness = freshnessSentence(state.analysisGeneratedAtMillis, state.pricesFetchedAtMillis, state.nowMillis)
    val contextText = listOfNotNull(sentence.text(), freshness?.text()).joinToString(" ")
    val stateWord = if (state.market?.regularSession == true) {
        stringResource(R.string.today_venue_state_open)
    } else {
        stringResource(R.string.today_venue_state_closed)
    }
    AmberFigure(
        figure = stateWord,
        context = contextText,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Blocks 3, 4 and 5, the seam [WatchlistContent] draws after Yours. */
@Composable
private fun TodayAfterContent(
    state: WatchlistUiState,
    onOpenDetail: (String) -> Unit,
    onOpenVote: (() -> Unit)?,
    onBrowseStocks: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        TodayTrackedBlock(state = state, onOpenDetail = onOpenDetail, onBrowseStocks = onBrowseStocks)
        TodayNextUpBlock(state = state, onOpenVote = onOpenVote)
        footerCopy(state.analyzedTotal, state.withoutAnalysisTotal)?.let { footer ->
            TodayFooter(text = footer.text(), onBrowseStocks = onBrowseStocks)
        }
    }
}

/**
 * Block 3: Tracked today. A skeleton while [WatchlistUiState.todayLoading] is true, then either the
 * rows (deepest pool first, [TrackedPreviewCount] of them, "All N tracked" handing the rest to
 * Stocks) or nothing at all once the join has settled with no analyzed universe to draw from
 * ([WatchlistUiState.analyzedTotal] at 0): the section is undrawn rather than drawn with a "0 of 0"
 * lede that has nothing behind it.
 */
@Composable
private fun TodayTrackedBlock(
    state: WatchlistUiState,
    onOpenDetail: (String) -> Unit,
    onBrowseStocks: (() -> Unit)?,
) {
    if (!state.todayLoading && state.analyzedTotal <= 0) return
    AmberSectionHead(
        title = stringResource(R.string.today_heading_tracked),
        meta = if (state.todayLoading) null else Fmt.count(state.tracked.size),
        lede = trackedLede(state.tracked.size, state.analyzedTotal)?.text(),
    )
    when {
        state.todayLoading -> SkeletonRows(count = TrackedSkeletonCount)
        state.tracked.isNotEmpty() -> {
            AmberTickerRowGroup {
                state.tracked.take(TrackedPreviewCount).forEach { row ->
                    AmberTickerRow(
                        ticker = row.display,
                        company = row.company,
                        figure = row.figure,
                        context = stringResource(R.string.today_tracked_context),
                        onClick = { onOpenDetail(row.ticker) },
                        onClickLabel = stringResource(R.string.action_open_ticker, row.display),
                    )
                }
            }
            if (onBrowseStocks != null) {
                AmberTextLink(text = trackedAllCopy(state.tracked.size).text(), onClick = onBrowseStocks)
            }
        }
    }
}

/**
 * Block 4: Next up. Undrawn while nothing staked SKR has chosen can be read
 * ([WatchlistUiState.nextUpLeader] null), which covers both "the join has not settled yet" and "the
 * server answered nothing": a strip with no leader is not a strip.
 */
@Composable
private fun TodayNextUpBlock(state: WatchlistUiState, onOpenVote: (() -> Unit)?) {
    val leader = state.nextUpLeader ?: return
    AmberSectionHead(
        title = stringResource(R.string.next_up_label),
        lede = nextUpRoundLede(state.voteRound)?.text(),
    )
    AmberTickerRowGroup {
        AmberTickerRow(
            ticker = leader.display,
            company = leader.company,
            figure = leader.weight.text(),
            context = leader.votersContext.text(),
            onClick = onOpenVote,
            onClickLabel = stringResource(R.string.action_open_ticker, leader.display),
        )
    }
}

/**
 * Block 5: the footer count, "160 analyzed, 768 without analysis" beside a text action opening
 * Stocks. The count wraps rather than being forced to one line (the trap this task's brief names by
 * name): only "Stocks" is fixed-width, and it is four characters, never a variable value.
 */
@Composable
private fun TodayFooter(text: String, onBrowseStocks: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = AmberType.context,
            color = AmberDarkColors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (onBrowseStocks != null) {
            AmberTextLink(text = stringResource(R.string.nav_stocks), onClick = onBrowseStocks)
        }
    }
}

/**
 * The one thing this screen needs beside the five named components and does not have an Amber
 * equivalent for yet: a plain clickable text ("All N tracked", "Stocks"). Kept private and local
 * to this file rather than added to `ui/components` (outside this task's file set, and one two-line
 * wrapper does not earn a new shared component); see the report for the same note in full.
 */
@Composable
private fun AmberTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AmberType.context,
        color = AmberDarkColors.actionText,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

private const val TrackedSkeletonCount = 3
