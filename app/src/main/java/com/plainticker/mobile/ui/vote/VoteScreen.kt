package com.plainticker.mobile.ui.vote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.PreviousRoundStatus
import com.plainticker.mobile.data.plainticker.VoteRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.components.AmberPrimaryAction
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRow
import com.plainticker.mobile.ui.components.Banner
import com.plainticker.mobile.ui.components.Field
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.PreviewCanvas
import com.plainticker.mobile.ui.components.SkeletonRows
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.components.defaultAmberColors
import com.plainticker.mobile.ui.list.NextUpLeader
import com.plainticker.mobile.ui.list.skrWeight
import com.plainticker.mobile.ui.text
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import java.math.BigInteger
import java.time.ZoneId
import kotlinx.coroutines.launch

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
 *
 * Restyled to Amber (docs/design-research-2026-09-21.md section 5.3, DESIGN.md section 4): the
 * round header, every section head and the ticker rows this screen owns now read [AmberType] and
 * [com.plainticker.mobile.ui.theme.AmberColors] instead of Instrument's tokens, resolved with the
 * system-following [defaultAmberColors] rather than the fixed-dark [com.plainticker.mobile.ui.theme.AmberDarkColors]
 * every function on this screen previously read directly. [LeaderRow] draws through
 * [AmberTickerRow]'s own `trailingAction`, which did not exist when this screen was first
 * restyled (see [LeaderRow]'s own doc comment for why the collision that blocked it needed that
 * component's anatomy to grow, not a workaround on this screen). What still has no Amber
 * component (the ballot search field) is called out at its own definition; see
 * [BallotSearchField].
 *
 * **U13, measured rather than changed** (docs/plan-app-uiux-2026-09-21.md's after-the-hackathon
 * table: "the scroll-away header's cost deep in the ballot," 0 hours, P3, deliberately left as a
 * measurement before proposing anything to DESIGN.md section 4). No reader panel was available for
 * this pass, so what follows is a structural measurement off the source and the shipped catalog,
 * not a device or a person's account, offered as that and not as a substitute for one.
 *
 * The ballot is genuinely deep: `app/src/main/assets/snapshot/xstocks.json` carries 928 symbols,
 * docs/data-map.md's own count puts 157 of them in the Analyzed section, so [state.ballot] can run
 * to 928 minus 157, 771 rows. Each is at least 64dp ([AmberTickerRow]'s own `defaultMinSize`), so
 * reaching the middle of that list alone is tens of thousands of display points below where the
 * ballot begins, itself already below the header, the explainer, the round header, Leaders, Your
 * votes and Last round.
 *
 * What that costs, and what it does not. [header]'s own content (the wordmark, a debug-only
 * gallery entry) carries nothing a voter needs mid-ballot, and [HomeScreen]'s own
 * [com.plainticker.mobile.ui.components.AmberBottomNav] is a sibling of this screen's `LazyColumn`,
 * not a child of it ([HomeScreenTest]'s own structural test), so switching destinations, the one
 * thing the pre-bottom-bar shell needed the header for, is reachable at any scroll depth regardless
 * of what this screen's own header carries. The action this screen exists for is not stranded
 * either: [BallotRow] draws its own inline "Vote" beside every row, so a voter who has found a
 * candidate deep in the list never needs to scroll back to cast it. What is genuinely lost,
 * confirmed structurally rather than assumed: [BallotSearchField], a plain `LazyColumn` item like
 * every section above it, not sticky, so re-reaching it once scrolled past means climbing back past
 * the whole ballot, at the same tens-of-thousands-of-dp cost the descent down was. If DESIGN.md
 * section 4 is ever revisited over this, that field, not the [header] the row named, is where the
 * real cost sits.
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
    zone: ZoneId = remember { ZoneId.systemDefault() },
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Where the ballot's search field sits in this list, counted while the items below are laid
    // out, so the top card's action can scroll straight to it (LazyListState scrolls by index).
    val ballotSearchIndex = remember { IntArray(1) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        var position = 0
        item(key = "header") { header() }
        position++

        when {
            state.notOpen -> {
                item(key = "explainer") { Explainer() }
                item(key = "not-open") {
                    Footnote(text = stringResource(R.string.vote_tab_not_open), muted = false)
                }
            }

            state.failed -> {
                item(key = "explainer") { Explainer() }
                item(key = "failed") {
                    Banner(
                        text = stringResource(R.string.vote_tab_unavailable),
                        action = stringResource(R.string.action_retry),
                        onAction = onRetry,
                    )
                }
            }

            state.isLoading -> {
                item(key = "explainer") { Explainer() }
                item(key = "skeleton") { SkeletonRows(count = SkeletonRowCount) }
            }

            else -> {
                // The top card: the round's close in the reader's own time, what this wallet's
                // vote weighs, and the one action that leads to the ballot. Leaders right under it.
                item(key = "round") {
                    RoundCard(
                        round = state.round,
                        stake = state.stake,
                        zone = zone,
                        onPick = { scope.launch { listState.animateScrollToItem(ballotSearchIndex[0]) } },
                    )
                }
                position++

                if (state.leaders.isNotEmpty()) {
                    item(key = "leaders-heading") {
                        AmberSectionHead(
                            title = stringResource(R.string.vote_tab_heading_leaders),
                            meta = Fmt.count(state.leaders.size),
                        )
                    }
                    itemsIndexed(state.leaders, key = { _, leader -> "leader:" + leader.ticker }) { index, leader ->
                        LeaderRow(leader = leader, last = index == state.leaders.lastIndex, onOpenDetail = onOpenDetail, onVote = onVote)
                    }
                    position += 1 + state.leaders.size
                } else if (state.round != null) {
                    // A round is open and nothing has been voted on yet: the heading above would
                    // otherwise sit over nothing. Drawn only here, never in the notOpen branch,
                    // which is a different state and already says its own piece.
                    item(key = "no-votes-yet") {
                        Footnote(text = stringResource(R.string.vote_tab_no_votes_yet))
                    }
                    position++
                }

                if (state.myVotes.isNotEmpty()) {
                    item(key = "my-votes-heading") {
                        AmberSectionHead(
                            title = stringResource(R.string.vote_tab_heading_your_votes),
                            meta = Fmt.count(state.myVotes.size),
                        )
                    }
                    item(key = "my-votes-note") { Footnote(text = stringResource(R.string.vote_tab_your_votes_note)) }
                    itemsIndexed(state.myVotes, key = { _, receipt -> "mine:" + receipt.signature }) { index, receipt ->
                        MyVoteRow(receipt = receipt, last = index == state.myVotes.lastIndex, onOpenDetail = onOpenDetail)
                    }
                    position += 2 + state.myVotes.size
                }

                // How it works, below what a returning voter came for rather than above it.
                item(key = "explainer") { Explainer() }
                position++

                state.previous?.let { previous ->
                    item(key = "last-round-heading") {
                        AmberSectionHead(title = stringResource(R.string.vote_tab_heading_last_round))
                    }
                    item(key = "last-round") { LastRoundRow(previous = previous, onOpenDetail = onOpenDetail) }
                    position += 2
                }

                item(key = "ballot-heading") {
                    AmberSectionHead(
                        title = stringResource(R.string.list_heading_without_analysis),
                        meta = if (state.ballotLoaded) Fmt.count(state.ballot.size) else null,
                    )
                }
                position++
                ballotSearchIndex[0] = position
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
 * The top card (judges' round 2): the round and when it closes in the reader's own time, what a
 * vote from the connected wallet weighs, and one action that scrolls to the ballot. The honest
 * note on how stake decides left this tab's header for the vote sheet, where a voter reads it at
 * the moment of signing ([VoteSheetModel]'s disclosure), phrased as the roadmap it is. A 28dp
 * [AmberColors.surfaceRaised] card at the 16dp group inset, the anatomy You's hero card uses.
 */
@Composable
private fun RoundCard(round: VoteRound?, stake: TabStake, zone: ZoneId, onPick: () -> Unit) {
    val colors = defaultAmberColors()
    val shape = RoundedCornerShape(CardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardInset)
            .padding(top = 8.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .then(if (colors === AmberLightColors) Modifier.border(1.dp, colors.border, shape) else Modifier)
            .padding(bottom = 16.dp),
    ) {
        if (round != null) RoundHeader(round = round, zone = zone, colors = colors)
        Text(
            text = stake.sentence.text(),
            style = AmberType.body,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (round == null) 16.dp else 4.dp),
        )
        AmberPrimaryAction(
            label = stringResource(R.string.vote_tab_pick_action),
            onClick = onPick,
            colors = colors,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
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
    val colors = defaultAmberColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = Side, vertical = 2.dp)) {
        Text(
            text = stringResource(R.string.vote_tab_explainer_what),
            style = AmberType.body,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.vote_tab_explainer_how),
            style = AmberType.body,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * "Round" and its number, now drawn through [AmberSectionHead] instead of the bespoke anatomy
 * this function used to carry.
 *
 * The bug this anatomy exists to keep shut: [Heading][com.plainticker.mobile.ui.components.Heading]
 * gives its title a `weight(1f)` and its meta the rest of the row at the meta's own natural
 * width. A short count (Leaders, Your votes, the ballot) never asks for more than a few digits,
 * so the title barely notices; a full clause like "1, closes 21 Sep 2026 00:00 UTC" asked for
 * nearly the whole row's width instead, which starved the title down to one letter of "Round" per
 * line, R-o-u-n-d, top to bottom, about a fifth of the screen tall, on-device at 1.3x on the
 * Seeker. The earlier fix was a private anatomy that never routed the close time through a
 * shared-width slot at all.
 *
 * [AmberSectionHead] is Amber's own section head (`ui/components/AmberSectionHead.kt`), and its
 * own doc comment names this exact function as one of the two cases its design generalizes: its
 * title is capped at `maxLines = 2` rather than left to wrap without a bound, so a short
 * [AmberSectionHead.meta] can never squeeze it past readable. The round id is exactly the short
 * count that slot is built for ([roundId], never more than a few digits), and the close time
 * moves to [AmberSectionHead.lede], which the component draws full width on its own line below
 * the title, never squeezed beside it. `AmberSectionHeadTest` and `AmberTickerRowTest` already pin
 * that component's own anti-starvation behaviour; what is pinned here, in [VoteScreenTest], is
 * that this call site actually hands it a short meta and a full-width lede, and how long each one
 * can really get.
 */
@Composable
private fun RoundHeader(round: VoteRound, zone: ZoneId, colors: AmberColors) {
    val roundId = Fmt.count(round.id)
    val closesText = roundClosesLocal(round, zone)?.text()
    AmberSectionHead(
        title = stringResource(R.string.vote_tab_round_heading),
        meta = roundId,
        lede = closesText,
        colors = colors,
        background = colors.surfaceRaised,
    )
}

/**
 * One leader: the token and its company on Amber's name line, its voters as the meta line's
 * context, the weight as its figure, an inline "Vote" action when [onVote] is offered.
 *
 * **The case that blocked this row's migration, and how it is solved.** Amber's own ticker row
 * (`AmberTickerRow`) used to have exactly one slot on its right: a figure with an optional context
 * line under it, which is where the vote weight and the voter count already belong, and no third,
 * independent slot for a trailing action beside that figure — yet this row genuinely needs one at
 * the same time: the weight is the reason "Leaders" exists, and the vote action is a real,
 * frequently used affordance on this exact row, not decoration. `AmberTickerRow` now takes an
 * optional `trailingAction`, sharing width with the figure and context on the meta line only,
 * never with the ticker and company above (`AmberTickerRow`'s own doc comment, "The leader row,
 * unblocked", has the anatomy and the arithmetic, including at 1.3x font scale where the weight
 * and the action both grow). [MyVoteRow] and [BallotRow] below draw through `AmberTickerRow`
 * directly without a trailing action wired in, because neither of them has this collision: the
 * wallet's own votes never carry a trailing action, and the ballot's rows carry one only when they
 * carry no figure at all, [BallotRow]'s own already-working overlay.
 */
@Composable
private fun LeaderRow(
    leader: NextUpLeader,
    last: Boolean,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
) {
    AmberRowDivider(last = last) {
        AmberTickerRow(
            ticker = leader.display,
            company = leader.company,
            figure = leader.weight.text(),
            context = leader.votersCopy.text(),
            trailingAction = if (onVote == null) null else stringResource(R.string.vote_action_row),
            onTrailingAction = if (onVote == null) null else ({ onVote(leader.ticker, leader.display) }),
            onClick = { onOpenDetail(leader.ticker) },
            onClickLabel = stringResource(R.string.action_open_ticker, leader.display),
        )
    }
}

/**
 * One vote this device recorded: the token in Amber's ticker row, the weight as its figure, when
 * it landed as the context line under it. A clean fit for [AmberTickerRow] because this row never
 * carries a trailing action.
 */
@Composable
private fun MyVoteRow(receipt: VoteReceipt, last: Boolean, onOpenDetail: (String) -> Unit) {
    AmberRowDivider(last = last) {
        AmberTickerRow(
            ticker = receipt.symbol,
            company = null,
            figure = stringResource(R.string.next_up_weight, skrWeight(BigInteger.valueOf(receipt.weightRaw))),
            context = stringResource(R.string.vote_tab_your_vote_meta, Fmt.utc(receipt.landedAtMillis)),
            onClick = { onOpenDetail(receipt.ticker) },
            onClickLabel = stringResource(R.string.action_open_ticker, receipt.symbol),
        )
    }
}

/**
 * The previous round's winner: the sentence naming its status first, in Amber's primary text,
 * above the figures that qualify it (DESIGN.md section 1.1's rule, applied here as it is on
 * Detail below the liquidity floor). Clickable only once published, into the Detail the loop
 * closes on. A status sentence plus a meta clause is not a ticker row or a figure, so this stays
 * its own small block of [AmberType] text rather than reaching for a component built for either.
 */
@Composable
private fun LastRoundRow(previous: PreviousRoundDisplay, onOpenDetail: (String) -> Unit) {
    val colors = defaultAmberColors()
    val statusText = previous.sentence.text()
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
        Text(text = statusText, style = AmberType.body, color = colors.textPrimary)
        if (meta != null) {
            Text(
                text = meta,
                style = AmberType.meta,
                color = colors.textTertiary(AmberSurface.GROUND),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * One token without analysis: Amber's ticker row, muted only by carrying no figure of its own,
 * with the "Vote" action composed over its empty right column when [onVote] is offered. A clean
 * fit, unlike [LeaderRow]: this row never draws a figure, so the overlaid action has nothing to
 * collide with. [com.plainticker.mobile.ui.components.ListRow]'s `muted` flag (a dimmer ticker and company colour for an
 * uncovered row) has no `AmberTickerRow` equivalent; the row still reads as lower priority from
 * its section (under "Without analysis") and from carrying no figure, so this is accepted rather
 * than forked.
 */
@Composable
private fun BallotRow(
    entry: BallotEntry,
    last: Boolean,
    onOpenDetail: (String) -> Unit,
    onVote: ((ticker: String, symbol: String) -> Unit)?,
) {
    val colors = defaultAmberColors()
    AmberRowDivider(last = last) {
        Box(Modifier.fillMaxWidth()) {
            AmberTickerRow(
                ticker = entry.display,
                company = entry.company,
                onClick = { onOpenDetail(entry.ticker) },
                onClickLabel = stringResource(R.string.action_open_ticker, entry.display),
            )
            if (onVote != null) {
                TextAction(
                    label = stringResource(R.string.vote_action_row),
                    onClick = { onVote(entry.ticker, entry.display) },
                    color = colors.actionText,
                    // The overlay's own end inset: AmberTickerRow's ticker/company line gets its
                    // right margin for free from the Column's own `padding(horizontal = 16.dp, ...)`
                    // (AmberTickerRow.kt), but this action is a sibling of that Column inside the
                    // Box above, not a child of it, so it never inherited that padding. Without this,
                    // Modifier.align(Alignment.CenterEnd) alone pins the action flush to the Box's own
                    // edge, which is the physical screen edge (this screen's LazyColumn and
                    // AmberRowDivider carry no horizontal padding of their own): on-device the row
                    // touched the edge at 1.0x font scale and clipped the final "e" of "Vote" at 1.3x
                    // (both themes). 16dp here matches AmberTickerRow's own horizontal padding exactly,
                    // the same margin every other trailing action in this app already gets by sitting
                    // inside that Column (LeaderRow and Watchlist's row, both through
                    // AmberTickerRow's own `trailingAction`; see VoteScreenTest's own margin test).
                    // Applied outside TextAction's `modifier` parameter, i.e. outside its own
                    // `defaultMinSize(48.dp, 48.dp)`, so it repositions the full 48dp+ touch target
                    // rather than shrinking it.
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                )
            }
        }
    }
}

/**
 * The 1dp seam [AmberTickerRow] leaves to its group ([com.plainticker.mobile.ui.components.
 * AmberTickerRowGroup]), drawn here instead: the ballot can run to the hundreds of rows this
 * app's own catalogue carries without analysis, so its rows stay individual lazy items rather
 * than one non-lazy group holding all of them. A manual divider between items is the same trade
 * [com.plainticker.mobile.ui.components.ListRow] itself makes (`divider: Boolean = !last`), read here against
 * [com.plainticker.mobile.ui.theme.AmberColors.border] instead of Instrument's Line.
 */
@Composable
private fun AmberRowDivider(last: Boolean, content: @Composable () -> Unit) {
    val colors = defaultAmberColors()
    Column(Modifier.fillMaxWidth()) {
        content()
        if (!last) HorizontalDivider(thickness = 1.dp, color = colors.border)
    }
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
    val colors = defaultAmberColors()
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Side), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = if (muted) AmberType.context else AmberType.body,
            color = if (muted) colors.textTertiary(AmberSurface.GROUND) else colors.textSecondary,
            modifier = Modifier.weight(1f).padding(vertical = if (muted) 8.dp else EmptyLineGap),
        )
    }
}

private val Side = 20.dp
private val CardInset = 16.dp
private val CardRadius = 28.dp
/** Vertical centering for a standalone Footnote's sentence; unrelated to Heading's own rhythm (U6). */
private val EmptyLineGap = 30.dp
private const val SkeletonRowCount = 4

// ---- Previews ------------------------------------------------------------------------------

private fun ballotEntry(ticker: String, symbol: String, company: String) = BallotEntry(ticker, symbol, company)

private val PreviewRound = VoteRound(id = 1, opensAt = "2026-09-15T00:00:00.000Z", closesAt = "2026-09-22T00:00:00.000Z")

private val PreviewLeaders = listOf(
    NextUpLeader("TSM", "TSMx", "Taiwan Semiconductor", BigInteger("38406150222"), 3),
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
        weightRaw = 38_406_150_222L,
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
        roundId = 1,
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
