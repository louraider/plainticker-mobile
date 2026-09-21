package com.plainticker.mobile.ui.vote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.PreviousRoundStatus
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.Heading
import com.plainticker.mobile.ui.components.InstrumentPreviews
// The row component and this package's row model share a name; the anatomy keeps an alias, the
// same one ListScreen and PortfolioScreen already carry.
import com.plainticker.mobile.ui.components.ListRow as InstrumentRow
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.list.NextUpLeader
import com.plainticker.mobile.ui.list.skrWeight
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.Ink2
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType
import java.math.BigInteger

/**
 * The Vote tab (task A2, `HomeTab.VOTE`, docs/plan-monetisation-2026-09-19.md section 1.5). One
 * scrolling column, in the order the plan settles: the header the host hands in, the explainer
 * (drawn in every state, because a reader who has never heard of this needs it whether or not a
 * round is open), the round header only when [VoteTabUiState.round] is present, the leaders, the
 * wallet's own votes for the round, the last round, and the ballot: every token without an
 * analysis, searched independently of the List's own search box.
 *
 * The not-open state (HTTP 404, or 503 `vote_not_configured`, [VoteTabViewModel]'s job to tell
 * apart from a real failure) replaces every section after the explainer with one honest line
 * rather than an error banner, exactly as the plan asks: nothing below the explainer describes a
 * round the server does not run.
 */
@Composable
fun VoteScreen(
    viewModel: VoteTabViewModel,
    voteViewModel: VoteViewModel,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val vote by voteViewModel.state.collectAsStateWithLifecycle()
    // A sibling of the LazyColumn, exactly as the List hosts it: an item is disposed when it
    // scrolls out, and a vote mid-flight would go with it.
    Box(modifier.fillMaxSize()) {
        VoteTabContent(
            state = state,
            onQueryChange = viewModel::search,
            onClearSearch = viewModel::clearSearch,
            onRetry = viewModel::refresh,
            onOpenDetail = onOpenDetail,
            onVote = { ticker, symbol -> voteViewModel.vote(ticker, symbol, state.round?.id) },
            header = header,
        )
        VoteSheet(
            state = vote,
            actions = VoteActions(
                onConfirm = voteViewModel::confirm,
                onRetry = voteViewModel::retry,
                onClose = voteViewModel::close,
            ),
        )
    }
}

@Composable
internal fun VoteTabContent(
    state: VoteTabUiState,
    onQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRetry: () -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onVote: ((ticker: String, symbol: String) -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "header") { header() }
        item(key = "explainer") { Explainer() }

        when {
            state.notOpen -> item(key = "not-open") {
                Footnote(text = stringResource(R.string.vote_tab_not_open), muted = false)
            }

            state.failed -> item(key = "failed") {
                Banner(
                    text = stringResource(R.string.vote_tab_unavailable),
                    action = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
            }

            state.isLoading -> item(key = "skeleton") { SkeletonRows(count = SkeletonRowCount) }

            else -> {
                state.round?.let { round -> item(key = "round") { RoundHeader(round) } }

                if (state.leaders.isNotEmpty()) {
                    item(key = "leaders-heading") {
                        Heading(
                            text = stringResource(R.string.vote_tab_heading_leaders),
                            meta = Fmt.count(state.leaders.size),
                        )
                    }
                    itemsIndexed(state.leaders, key = { _, leader -> "leader:" + leader.ticker }) { index, leader ->
                        LeaderRow(leader = leader, last = index == state.leaders.lastIndex, onOpenDetail = onOpenDetail, onVote = onVote)
                    }
                }

                if (state.myVotes.isNotEmpty()) {
                    item(key = "my-votes-heading") {
                        Heading(
                            text = stringResource(R.string.vote_tab_heading_your_votes),
                            meta = Fmt.count(state.myVotes.size),
                        )
                    }
                    item(key = "my-votes-note") { Footnote(text = stringResource(R.string.vote_tab_your_votes_note)) }
                    itemsIndexed(state.myVotes, key = { _, receipt -> "mine:" + receipt.signature }) { index, receipt ->
                        MyVoteRow(receipt = receipt, last = index == state.myVotes.lastIndex, onOpenDetail = onOpenDetail)
                    }
                }

                state.previous?.let { previous ->
                    item(key = "last-round-heading") {
                        Heading(text = stringResource(R.string.vote_tab_heading_last_round))
                    }
                    item(key = "last-round") { LastRoundRow(previous = previous, onOpenDetail = onOpenDetail) }
                }

                item(key = "ballot-heading") {
                    Heading(
                        text = stringResource(R.string.list_heading_without_analysis),
                        meta = if (state.ballotLoaded) Fmt.count(state.ballot.size) else null,
                    )
                }
                item(key = "ballot-search") {
                    BallotSearchField(query = state.query, onQueryChange = onQueryChange, onClearSearch = onClearSearch)
                }

                when {
                    // A join that has never once succeeded is a source outage, not a screen with
                    // nothing to show yet: it gets its own banner and its own Retry rather than
                    // sitting on its skeleton forever with no way back (VoteTabViewModel.refresh
                    // retries this alongside next-up).
                    state.ballotFailed -> item(key = "ballot-failed") {
                        Banner(
                            text = stringResource(R.string.vote_tab_ballot_unavailable),
                            action = stringResource(R.string.action_retry),
                            onAction = onRetry,
                        )
                    }

                    !state.ballotLoaded -> item(key = "ballot-skeleton") { SkeletonRows(count = SkeletonRowCount) }

                    state.searchMiss -> item(key = "ballot-miss") {
                        Footnote(text = stringResource(R.string.list_search_empty, state.query))
                    }

                    else -> itemsIndexed(state.ballot, key = { _, entry -> "ballot:" + entry.ticker }) { index, entry ->
                        BallotRow(entry = entry, last = index == state.ballot.lastIndex, onOpenDetail = onOpenDetail, onVote = onVote)
                    }
                }
            }
        }
    }
}

/**
 * Two short paragraphs, then the disclosure as its own quieter line (section 1.5, tightened on
 * review): coverage is finite and costs money, and staked SKR decides what is read next, in the
 * first; a vote is a signed transaction rather than a login and counts once per wallet per token
 * per round, in the second. All four facts the plan asks for, none dropped, in two paragraphs
 * rather than the three this screen shipped with, because a third one at 1.3x font scale pushed
 * Leaders below the fold, and the thing this tab is for should not be the thing nobody scrolls
 * to. Drawn above every other state, including the not-open one: a reader who has never heard of
 * this is owed the explanation whether or not a round happens to be open right now.
 */
@Composable
private fun Explainer() {
    Column(Modifier.fillMaxWidth().padding(horizontal = Side, vertical = 2.dp)) {
        Text(text = stringResource(R.string.vote_tab_explainer_what), style = PlainTickerType.body, color = Ink, modifier = Modifier.padding(top = 12.dp))
        Text(text = stringResource(R.string.vote_tab_explainer_how), style = PlainTickerType.body, color = Ink, modifier = Modifier.padding(top = 10.dp))
        Text(text = stringResource(R.string.vote_gameable), style = PlainTickerType.small, color = Muted, modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * "Round" and its number on one line, the close time on its own beneath: its own small anatomy
 * rather than [Heading], because [Heading] gives its title a `weight(1f)` and its meta the rest
 * of the row at the meta's own natural width. A short count in that meta slot (Leaders, Your
 * votes, the ballot) never asks for more than a few digits, so the title barely notices; a full
 * clause like "1, closes 21 Sep 2026 00:00 UTC" asked for nearly the whole row's width instead,
 * which starved the title down to one letter of "Round" per line, R-o-u-n-d, top to bottom,
 * about a fifth of the screen tall. This is not that division: the round's number sits beside the
 * word at the word's own size, in the mono face DESIGN.md section 3 keeps for every numeral, and
 * the close time is a second, quieter line with no row to starve.
 */
@Composable
private fun RoundHeader(round: VoteRound) {
    val roundId = Fmt.count(round.id)
    val closesText = round.closesAtInstant()?.let { stringResource(R.string.vote_tab_round_closes, Fmt.utc(it)) }
    // One rhythm (U6): the same 32dp above, 14dp below Heading itself defaults to, kept as a
    // literal because this block cannot use Heading (see the doc comment above).
    Column(Modifier.fillMaxWidth().padding(start = Side, end = Side, top = 32.dp, bottom = 14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = stringResource(R.string.vote_tab_round_heading),
                style = PlainTickerType.heading,
                color = Ink,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = roundId,
                style = PlainTickerType.trackValue,
                color = Ink,
                modifier = Modifier.alignByBaseline(),
            )
        }
        if (closesText != null) {
            Text(text = closesText, style = PlainTickerType.monoSmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** One leader: the token and its company left, the voters on the meta line, the weight right. */
@Composable
private fun LeaderRow(
    leader: NextUpLeader,
    last: Boolean,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
) {
    InstrumentRow(
        ticker = leader.display,
        company = leader.company,
        meta = leader.votersCopy.text(),
        valueRight = leader.weight.text(),
        trailingAction = if (onVote == null) null else stringResource(R.string.vote_action_row),
        onTrailingAction = if (onVote == null) null else ({ onVote(leader.ticker, leader.display) }),
        divider = !last,
        onClick = { onOpenDetail(leader.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, leader.display),
    )
}

/** One vote this device recorded: the token left, when it landed on the meta line, the weight right. */
@Composable
private fun MyVoteRow(receipt: VoteReceipt, last: Boolean, onOpenDetail: (String) -> Unit) {
    InstrumentRow(
        ticker = receipt.symbol,
        company = null,
        meta = stringResource(R.string.vote_tab_your_vote_meta, Fmt.utc(receipt.landedAtMillis)),
        valueRight = stringResource(R.string.next_up_weight, skrWeight(BigInteger.valueOf(receipt.weightRaw))),
        divider = !last,
        onClick = { onOpenDetail(receipt.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, receipt.symbol),
    )
}

/**
 * The previous round's winner: the sentence naming its status first, in Ink, above the figures
 * that qualify it (DESIGN.md section 1.1's rule, applied here as it is on Detail below the
 * liquidity floor). Clickable only once published, into the Detail the loop closes on.
 */
@Composable
private fun LastRoundRow(previous: PreviousRoundDisplay, onOpenDetail: (String) -> Unit) {
    val statusText = stringResource(statusStringRes(previous.status), previous.display)
    val weightVoters = previous.weightRaw?.let {
        pluralStringResource(R.plurals.next_up_detail_weight, previous.voters, skrWeight(it), Fmt.count(previous.voters))
    }
    val meta = when {
        weightVoters != null && previous.closedAtText != null ->
            stringResource(R.string.list_row_meta_join, weightVoters, previous.closedAtText)

        weightVoters != null -> weightVoters
        else -> previous.closedAtText
    }
    val clickable = previous.status == PreviousRoundStatus.PUBLISHED
    val openLabel = stringResource(R.string.action_open_ticker, previous.display)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (clickable) base.clickable(onClickLabel = openLabel, role = Role.Button) { onOpenDetail(previous.ticker) } else base }
            .padding(horizontal = Side, vertical = 12.dp),
    ) {
        Text(text = statusText, style = PlainTickerType.body, color = Ink)
        if (meta != null) {
            Text(text = meta, style = PlainTickerType.meta, color = Muted, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

private fun statusStringRes(status: PreviousRoundStatus): Int = when (status) {
    PreviousRoundStatus.PENDING -> R.string.vote_tab_last_round_pending
    PreviousRoundStatus.PUBLISHED -> R.string.vote_tab_last_round_published
    PreviousRoundStatus.UNCOVERABLE -> R.string.vote_tab_last_round_uncoverable
}

/** One token without analysis: the same anatomy the List's own uncovered rows carry, muted. */
@Composable
private fun BallotRow(
    entry: BallotEntry,
    last: Boolean,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
) {
    InstrumentRow(
        ticker = entry.display,
        company = entry.company,
        trailingAction = if (onVote == null) null else stringResource(R.string.vote_action_row),
        onTrailingAction = if (onVote == null) null else ({ onVote(entry.ticker, entry.display) }),
        muted = true,
        divider = !last,
        onClick = { onOpenDetail(entry.ticker) },
        onClickLabel = stringResource(R.string.action_open_ticker, entry.display),
    )
}

@Composable
private fun BallotSearchField(query: String, onQueryChange: (String) -> Unit, onClearSearch: () -> Unit) {
    Field(
        label = stringResource(R.string.list_search_label),
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.list_search_placeholder),
        mono = false,
        action = if (query.isEmpty()) null else stringResource(R.string.action_clear),
        onAction = onClearSearch,
    )
}

/** A closing line, [muted] by default (a footnote); the not-open line stands on its own instead. */
@Composable
private fun Footnote(text: String, muted: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Side), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = if (muted) PlainTickerType.small else PlainTickerType.body,
            color = if (muted) Muted else Ink2,
            modifier = Modifier.weight(1f).padding(vertical = if (muted) 8.dp else EmptyLineGap),
        )
    }
}

private val Side = 20.dp
/** Vertical centering for a standalone Footnote's sentence; unrelated to Heading's own rhythm (U6). */
private val EmptyLineGap = 30.dp
private const val SkeletonRowCount = 4

// ---- Previews ------------------------------------------------------------------------------

private fun ballotEntry(ticker: String, symbol: String, company: String) = BallotEntry(ticker, symbol, company)

private val PreviewRound = VoteRound(id = 1, opensAt = "2026-09-15T00:00:00.000Z", closesAt = "2026-09-22T00:00:00.000Z")

private val PreviewLeaders = listOf(
    NextUpLeader("TSM", "TSMx", "Taiwan Semiconductor", BigInteger("31209870777"), 3),
    NextUpLeader("ASML", "ASMLx", "ASML Holding", BigInteger("6719000000"), 1),
)

private val PreviewBallot = listOf(
    ballotEntry("TSM", "TSMx", "Taiwan Semiconductor"),
    ballotEntry("ASML", "ASMLx", "ASML Holding"),
    ballotEntry("NKE", "NKEx", "Nike, Inc."),
)

private val PreviewVotes = listOf(
    VoteReceipt(
        signature = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe",
        ticker = "TSM",
        symbol = "TSMx",
        weightRaw = 31_209_870_777L,
        landedAtMillis = 1_789_045_020_000L,
        voter = "9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ",
        round = 1,
    ),
)

private val PreviewState = VoteTabUiState(
    round = PreviewRound,
    leaders = PreviewLeaders,
    myVotes = PreviewVotes,
    previous = PreviousRoundDisplay(
        ticker = "JEF",
        display = "JEFx",
        company = "Jefferies Financial Group",
        status = PreviousRoundStatus.PUBLISHED,
        weightRaw = BigInteger("18500000000"),
        voters = 4,
        closedAtText = "15 Sep 2026 00:00 UTC",
    ),
    ballot = PreviewBallot,
    ballotLoaded = true,
)

@InstrumentPreviews
@Composable
private fun VoteTabPreview() {
    PreviewCanvas {
        VoteTabContent(
            state = PreviewState,
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
            onVote = { _, _ -> },
        )
    }
}

@InstrumentPreviews
@Composable
private fun VoteTabNotOpenPreview() {
    PreviewCanvas {
        VoteTabContent(
            state = VoteTabUiState(notOpen = true),
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun VoteTabFailedPreview() {
    PreviewCanvas {
        VoteTabContent(
            state = VoteTabUiState(failed = true),
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
        )
    }
}

@InstrumentPreviews
@Composable
private fun VoteTabNoRoundPreview() {
    PreviewCanvas {
        VoteTabContent(
            state = PreviewState.copy(round = null, previous = null, myVotes = emptyList()),
            onQueryChange = {},
            onClearSearch = {},
            onRetry = {},
            onOpenDetail = {},
            onVote = { _, _ -> },
        )
    }
}
