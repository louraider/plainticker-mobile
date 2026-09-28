package com.plainticker.mobile.ui.vote

import com.plainticker.mobile.repo.Coverage
import com.plainticker.mobile.repo.researchPublished
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.data.receipts.VoteReceiptStore
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.RpcRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Vote tab (task A2, `HomeTab.VOTE`, docs/plan-monetisation-2026-09-19.md section 1.5): the
 * round header, the leaders, the wallet's own votes for the round, the last round, and the ballot
 * of every uncovered token, or the one-line not-open state in place of all of it.
 *
 * **The ballot is its own, small join.** [ListViewModel][com.plainticker.mobile.ui.list.ListViewModel]
 * already computes the same "no analysis yet" set for the List's own strip, but with a snapshot
 * fallback, a price budget and a chapter-by-sector pass this tab needs none of: the ballot is
 * searched and voted on, not priced, so this class asks [catalog] and [summaries] directly. Both
 * are the same container-level singletons the List already warmed, exactly as
 * [com.plainticker.mobile.ui.portfolio.PortfolioViewModel] independently asks the catalog to name
 * its own recorded rows, so a second ask here is a cache hit rather than a second 4.31 MB fetch.
 *
 * **A first failure is the only failure this screen shows.** Once [nextUp] has answered once,
 * open or not, a later refresh that throws changes nothing: the reader keeps whatever last loaded,
 * the same rule [com.plainticker.mobile.repo.CachedNextUpRepository] already keeps at the cache
 * layer and this class keeps again at its own. The ballot keeps the same rule independently: it
 * has its own [VoteTabUiState.ballotFailed] and [refresh] retries it too, whenever it has not
 * yet loaded once, so a transient hiccup in the catalog or the summary call is never the one
 * failure this screen cannot recover from.
 */
class VoteTabViewModel(
    private val nextUp: NextUpRepository,
    private val catalog: CatalogRepository,
    private val summaries: SummaryRepository,
    private val voteReceipts: VoteReceiptStore,
    private val wallet: WalletSession,
    /**
     * The staking read behind the top card's stake line. Null (every test written before the
     * card existed) reads as no wallet at all: the card still draws, without a figure.
     */
    private val rpc: RpcRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(VoteTabUiState(isLoading = true))
    val state: StateFlow<VoteTabUiState> = _state.asStateFlow()

    private var allBallot: List<BallotEntry> = emptyList()
    private var catalogByTicker: Map<String, XStockAsset> = emptyMap()
    private var lastRows: List<NextUpRow> = emptyList()
    private var lastPrevious: PreviousRound? = null
    private var allReceipts: List<VoteReceipt> = emptyList()
    private var everAnswered = false

    /** The previous winner whose research is known to be published, or null. */
    private var winnerResearch: String? = null
    private var researchAskedFor: String? = null
    private var stakeJob: Job? = null
    private var stakeFor: String? = null

    init {
        viewModelScope.launch { voteReceipts.receipts.collect { allReceipts = it; republishVotes() } }
        // The connected wallet is the scope "Your votes" reads by (see VoteReceipt's own doc on
        // what a wallet change means for this record): a reconnect or a disconnect narrows or
        // widens the section without a manual refresh.
        viewModelScope.launch {
            wallet.account.collect { account ->
                republishVotes()
                readStake(account?.address)
            }
        }
        // refresh() itself starts the ballot's first load (it has not loaded, so its own check
        // fires), so there is one call here rather than two racing to be the first.
        refresh()
    }

    /**
     * Asks `next-up` again, and the ballot too when it has never once loaded. Called on init,
     * and offered as Retry from both the failed banner and the ballot's own: one tap for the
     * reader, whichever half of the screen actually needs it.
     */
    fun refresh() {
        if (!_state.value.ballotLoaded) viewModelScope.launch { loadBallot() }
        viewModelScope.launch {
            try {
                when (val answer = nextUp.current()) {
                    is NextUpAnswer.Open -> {
                        lastRows = answer.rows
                        lastPrevious = answer.previous
                        everAnswered = true
                        _state.update {
                            it.copy(
                                isLoading = false,
                                failed = false,
                                notOpen = false,
                                round = answer.round,
                                previous = previousNow(),
                                leaders = leadersFor(lastRows, allBallot),
                            )
                        }
                        republishVotes()
                        checkWinnerResearch()
                    }

                    NextUpAnswer.NotOpen -> {
                        lastRows = emptyList()
                        lastPrevious = null
                        everAnswered = true
                        _state.update {
                            it.copy(
                                isLoading = false,
                                failed = false,
                                notOpen = true,
                                round = null,
                                previous = null,
                                leaders = emptyList(),
                                myVotes = emptyList(),
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // Nothing has ever loaded: the reader is owed a banner, not a blank screen.
                // Once something has, a later failure keeps it rather than wiping it away.
                if (!everAnswered) _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    /** The previous round as the screen draws it, with what is known about its winner's research. */
    private fun previousNow(): PreviousRoundDisplay? =
        previousDisplay(lastPrevious, catalogByTicker)?.copy(researchPublished = winnerResearch == lastPrevious?.winner?.trim())

    /**
     * Asks whether the previous winner's research is published ([researchPublished], the check the
     * digest and auto-watch share), once per winner, and redraws the row when it is.
     */
    private fun checkWinnerResearch() {
        val winner = lastPrevious?.winner?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (winner == winnerResearch || winner == researchAskedFor) return
        researchAskedFor = winner
        viewModelScope.launch {
            if (summaries.researchPublished(winner)) {
                winnerResearch = winner
                _state.update { it.copy(previous = previousNow()) }
            } else {
                researchAskedFor = null
            }
        }
    }

    /** The ballot's own search, independent of the List's: typing here never touches the List. */
    fun search(query: String) {
        _state.update { it.copy(query = query, ballot = allBallot.matchingBallot(query)) }
    }

    fun clearSearch() = search("")

    /**
     * The catalog joined with `/summary`, exactly the "no analysis at all" rule
     * [com.plainticker.mobile.ui.list.ListViewModel.republish] uses, without the price and
     * snapshot machinery that rule also carries. The catalog alone (independent of coverage) is
     * kept too, so a previous round's winner, no longer uncovered once published, can still be
     * named.
     *
     * A throw from either call used to return silently here, which left the ballot on its
     * skeleton forever: nothing else ever asked again, because [refresh] only re-hit `next-up`.
     * Now a failure before the first success sets [VoteTabUiState.ballotFailed], which draws a
     * banner [refresh] is wired to retry, exactly like the next-up banner already is.
     */
    private suspend fun loadBallot() {
        val assets = runCatching { catalog.catalog() }.getOrNull()
        if (assets == null) {
            if (!_state.value.ballotLoaded) _state.update { it.copy(ballotFailed = true) }
            return
        }
        catalogByTicker = assets.associateBy { it.underlyingTicker.trim().uppercase() } // lint-allow uppercase: map key
        // The previous winner may now be resolvable even if the summary call below never answers.
        _state.update { it.copy(previous = previousNow()) }

        val summary = runCatching { summaries.summary() }.getOrNull()
        if (summary == null) {
            if (!_state.value.ballotLoaded) _state.update { it.copy(ballotFailed = true) }
            return
        }
        // Covered, not just classified: a company with a page and no /summary row (ABBV) is never
        // on the ballot (QA of 1.3.19). The server refuses a vote for it anyway (409 ticker_covered).
        val classified = Coverage.tickers(summary)
        allBallot = assets
            .filter { it.solanaMint != null && it.underlyingTicker.trim().uppercase() !in classified } // lint-allow uppercase: map key
            // US underlyings only: the server refuses every other listing, so a London or Hong
            // Kong row here dead-ended after the wallet connected (1,072 rows became 898 live,
            // 2026-09-26). The catalog map above keeps them, for naming only.
            .filter { it.isUsUnderlying }
            .map { BallotEntry(ticker = it.underlyingTicker, symbol = it.symbol, company = it.name) }
            .sortedBy { it.symbol }
        _state.update {
            it.copy(
                ballotLoaded = true,
                ballotFailed = false,
                ballot = allBallot.matchingBallot(it.query),
                leaders = leadersFor(lastRows, allBallot),
            )
        }
    }

    /**
     * The top card's stake, read once per connected wallet the same way the vote sheet reads it
     * ([SkrStakeBound.principalOf]), so the two can never disagree. A reconnect to the same
     * wallet does not read again; a different wallet, or none, replaces what was shown.
     */
    private fun readStake(address: String?) {
        if (address == stakeFor && _state.value.stake !is TabStake.Unread) return
        stakeFor = address
        stakeJob?.cancel()
        val source = rpc
        if (address == null || source == null) {
            _state.update { it.copy(stake = TabStake.NoWallet) }
            return
        }
        _state.update { it.copy(stake = TabStake.Reading) }
        stakeJob = viewModelScope.launch {
            val stake = try {
                SkrStakeBound.principalOf(source.skrStake(address))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                null
            }
            _state.update { it.copy(stake = if (stake == null) TabStake.Unread else TabStake.Read(stake)) }
        }
    }

    private fun republishVotes() {
        val voter = wallet.account.value?.address
        _state.update {
            it.copy(myVotes = myVotesFor(allReceipts, it.round, voter), pastVotes = pastVotesFor(allReceipts, it.round, voter))
        }
    }
}
