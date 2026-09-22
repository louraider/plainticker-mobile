package com.plainticker.mobile.ui.stocks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.plainticker.mobile.ui.list.ListScreen
import com.plainticker.mobile.ui.list.ListViewModel
import com.plainticker.mobile.ui.vote.VoteViewModel

/**
 * Stocks (docs/design-research-2026-09-21.md section 3): the 160-row list, moved here rather than
 * rewritten, exactly as it was under the old List tab. [ListScreen] itself is untouched: sector
 * chapters, the search field and the "Next up" strip under "Without analysis" all still work
 * unchanged. This file is the seam, not new content: the research also asks Stocks for a wrapping
 * filter row (Tracked, Watched, sector) and a chapter jump index, neither of which exists yet
 * (DESIGN.md section 4, "not yet restyled"), and both belong to the agent who restyles this
 * screen's components, not to this shell pass.
 */
@Composable
fun StocksScreen(
    viewModel: ListViewModel,
    voteViewModel: VoteViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    ListScreen(
        viewModel = viewModel,
        voteViewModel = voteViewModel,
        onOpenDetail = onOpenDetail,
        modifier = modifier,
        header = header,
    )
}
