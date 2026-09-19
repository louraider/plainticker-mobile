package com.plainticker.mobile.ui.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.data.plainticker.NextUpRow
import com.plainticker.mobile.data.plainticker.PreviousRound
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.data.receipts.VoteReceiptStore
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.NextUpAnswer
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.SummaryRepository
import com.plainticker.mobile.wallet.WalletSession
import kotlinx.coroutines.CancellationException
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
 * layer and this class keeps again at its own.
 */
class VoteTabViewModel(
    private val nextUp: NextUpRepository,
    private val catalog: CatalogRepository,
    private val summaries: SummaryRepository,
    private val voteReceipts: VoteReceiptStore,
    private val wallet: WalletSession,
) : ViewModel() {

    private val _state = MutableStateFlow(VoteTabUiState(isLoading = true))
    val state: StateFlow<VoteTabUiState> = _state.asStateFlow()

    private var allBallot: List<BallotEntry> = emptyList()
    private var catalogByTicker: Map<String, XStockAsset> = emptyMap()
    private var lastRows: List<NextUpRow> = emptyList()
    private var lastPrevious: PreviousRound? = null
    private var allReceipts: List<VoteReceipt> = emptyList()
    private var everAnswered = false

    init {
        viewModelScope.launch { voteReceipts.receipts.collect { allReceipts = it; republishVotes() } }
        // The connected wallet is the scope "Your votes" reads by (see VoteReceipt's own doc on
        // what a wallet change means for this record): a reconnect or a disconnect narrows or
        // widens the section without a manual refresh.
        viewModelScope.launch { wallet.account.collect { republishVotes() } }
        viewModelScope.launch { loadBallot() }
        refresh()
    }

    /** Asks `next-up` again. Called on init, and offered as Retry from the failed banner. */
    fun refresh() {
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
                                previous = previousDisplay(lastPrevious, catalogByTicker),
                                leaders = leadersFor(lastRows, allBallot),
                            )
                        }
                        republishVotes()
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
     */
    private suspend fun loadBallot() {
        val assets = runCatching { catalog.catalog() }.getOrNull() ?: return
        catalogByTicker = assets.associateBy { it.underlyingTicker.trim().uppercase() } // lint-allow uppercase: map key
        // The previous winner may now be resolvable even if the summary call below never answers.
        _state.update { it.copy(previous = previousDisplay(lastPrevious, catalogByTicker)) }

        val summary = runCatching { summaries.summary() }.getOrNull() ?: return
        val classified = summary.rows.map { it.ticker.trim().uppercase() }.toSet() // lint-allow uppercase: map key
        allBallot = assets
            .filter { it.solanaMint != null && it.underlyingTicker.trim().uppercase() !in classified } // lint-allow uppercase: map key
            .map { BallotEntry(ticker = it.underlyingTicker, symbol = it.symbol, company = it.name) }
            .sortedBy { it.symbol }
        _state.update {
            it.copy(
                ballotLoaded = true,
                ballot = allBallot.matchingBallot(it.query),
                leaders = leadersFor(lastRows, allBallot),
            )
        }
    }

    private fun republishVotes() {
        val voter = wallet.account.value?.address
        _state.update { it.copy(myVotes = myVotesFor(allReceipts, it.round, voter)) }
    }
}
