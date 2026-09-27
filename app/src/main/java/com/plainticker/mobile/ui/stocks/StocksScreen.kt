package com.plainticker.mobile.ui.stocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.plainticker.mobile.ui.list.ListScreen
import com.plainticker.mobile.ui.list.ListViewModel
import com.plainticker.mobile.ui.vote.VoteViewModel

/**
 * Stocks (docs/design-research-2026-09-21.md section 3): the 160-row list, moved here rather than
 * rewritten, exactly as it was under the old List tab. [ListScreen] itself is untouched: sector
 * chapters, the search field and the "Next up" strip under "Without analysis" all still work
 * unchanged. This file is the seam, not new content: the filter row lives in [ListScreen]
 * (a chapter jump index was built there and removed again in the judges' round 2).
 */
@Composable
fun StocksScreen(
    viewModel: ListViewModel,
    voteViewModel: VoteViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    // The hours banner reads a clock, not a photograph: recomputed every time Stocks comes back
    // and at every open or close while it stays on screen, stopped while it is away.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    ListScreen(
        viewModel = viewModel,
        voteViewModel = voteViewModel,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
        header = header,
    )
}
