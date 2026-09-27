package com.plainticker.mobile.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.data.net.ApiException
import com.plainticker.mobile.data.plainticker.ReadApi
import com.plainticker.mobile.data.plainticker.ReadError
import com.plainticker.mobile.data.xstocks.MarketHours
import com.plainticker.mobile.data.xstocks.XStockAsset
import com.plainticker.mobile.data.xstocks.toReserves
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.prefs.NotificationPromptStore
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.MintRepository
import com.plainticker.mobile.repo.NextUpRepository
import com.plainticker.mobile.repo.PriceRepository
import com.plainticker.mobile.repo.SummaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Detail screen's data path (plan T9, docs/data-map.md "Detail (T9)").
 *
 * Six sources, each loaded in its own coroutine and published the moment it lands, because the
 * screen's promise is that the trust layer stands on its own: PlainTicker not classifying a ticker
 * must not take the mint read with it, a dead Jupiter must not take the reserves, and a forwarder
 * that refuses the account read must not blank the fundamentals. The only ordering that exists is
 * the one the data forces: the catalog names the mint and the symbol, so the price, the chain, the
 * reserves and the multiplier wait on it and on nothing else.
 *
 * Two rules are enforced here rather than on the screen. A mint that could not be read is
 * [Piece.Failed], never an empty [com.plainticker.mobile.data.rpc.MintFacts]: the trust rows then say the chain
 * could not be read, and they never vanish and never claim the token is clean. And the split
 * multiplier prefers the mint's own `scaledUiAmountConfig` over the xStocks endpoint, carrying
 * which one answered, so an issuer's description of a split is never drawn as an on-chain fact.
 */
class DetailViewModel(
    ticker: String,
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
    private val mints: MintRepository,
    private val nextUp: NextUpRepository,
    private val watchlist: WatchlistStore,
    private val prompts: NotificationPromptStore,
    private val clock: Clock,
    /**
     * "The read" and "What to check next" (task A6). Nullable and defaulted so every existing
     * caller of this constructor is untouched by the addition: a null [readApi] simply never
     * asks, and [DetailUiState.read] stays [ReadState.Loading], which draws nothing extra and
     * never gates anything the six sources above it already carried.
     */
    private val readApi: ReadApi? = null,
    private val devicePassStore: DevicePassStore? = null,
) : ViewModel() {

    private val ticker = ticker.trim().uppercase() // lint-allow uppercase: API ticker key

    private val _state = MutableStateFlow(
        DetailUiState(ticker = this.ticker, watched = this.ticker in watchlist.tickers.value),
    )
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // `this.ticker` on purpose: inside init the constructor parameter shadows the property,
        // and it is the untrimmed, unnormalized string the route handed over.
        val key = this.ticker
        viewModelScope.launch {
            watchlist.tickers.collect { watched -> _state.update { it.copy(watched = key in watched) } }
        }
        viewModelScope.launch { keepTheClockMoving() }
        refresh()
    }

    /**
     * Every age on this screen is read against [DetailUiState.nowMillis], so that field has to
     * move. Taken once at the refresh it would be older than the reads it is compared against, and
     * the live bar would breathe for ever over a meta line that says "0 s ago" however old the
     * read is. The clock is re-read every second, and only while something is collecting the
     * state, so an unobserved screen costs nothing.
     */
    private suspend fun keepTheClockMoving() {
        _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { observed ->
            while (observed) {
                delay(TICK_MILLIS)
                _state.update { it.copy(nowMillis = clock.nowMillis()) }
            }
        }
    }

    /**
     * Adds or removes this ticker from the one watchlist the whole app shares, and answers whether
     * the caller should now ask for permission to send notifications.
     *
     * True exactly once in the life of an install: on the tap that puts the first ticker on an
     * empty watchlist, which is the moment the plan names (section 13 Pass 2 and Pass 7) and the
     * only moment at which the request means anything to a reader. Never at launch, never on the
     * second stock, and never again after a refusal, which is an answer rather than a state to
     * work around. The permission itself is the screen's to request: this decides only when.
     */
    fun toggleWatch(): Boolean {
        val watchedBefore = watchlist.tickers.value
        val adding = ticker !in watchedBefore
        watchlist.toggle(ticker)
        if (!adding || watchedBefore.isNotEmpty() || prompts.hasAsked()) return false
        prompts.setAsked()
        return true
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val now = clock.nowMillis()
            _state.update {
                DetailUiState(ticker = ticker, watched = it.watched, nowMillis = now)
            }

            // The analysis does not wait for the chain and the chain does not wait for the
            // analysis: a ticker PlainTicker has never classified still gets its whole trust layer.
            launch { loadAnalysis() }
            launch { loadTokenSide(now) }
            // Independent of both: a server that cannot answer this call, or has not turned the
            // route on yet, must never delay a single block this screen drew before task A6.
            launch { loadRead() }
        }
    }

    // ---- The read and What to check next (task A6) ---------------------------------------

    private suspend fun loadRead() {
        val api = readApi ?: return
        val read = try {
            ReadState.Ready(api.get(ticker, devicePassStore?.code()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReadError.Disabled) {
            ReadState.Disabled
        } catch (e: ReadError.NotServed) {
            ReadState.NotServed
        } catch (e: Exception) {
            ReadState.Failed
        }
        _state.update { it.copy(read = read) }
    }

    // ---- PlainTicker --------------------------------------------------------------------

    private suspend fun loadAnalysis() {
        // The same device code the read call already presents (task A6), so an entitled wallet
        // gets the real verdict rather than the locked shape (task app-verdict).
        val analysis = runCatching { summaries.analysis(ticker, devicePassStore?.code()) }
        val classified = analysis.fold(
            onSuccess = { payload -> AnalysisState.Served(payload) },
            onFailure = ::classify,
        )
        _state.update { it.copy(analysisState = classified) }

        // Asked only where it could matter: a covered ticker has no standing among the uncovered
        // ones, and a call that failed leaves it unknown whether it is covered at all. The answer
        // is the edge's five-minute cache, shared with the List through the repository, and a
        // failure states no standing rather than raising a banner.
        if (classified is AnalysisState.NotServed) {
            val leaders = try {
                nextUp.nextUp()
            } catch (cancelled: CancellationException) {
                // Not an answer, and never written as one: a refresh that replaced this run owns
                // the screen, and nothing from a cancelled one may reach it (ListViewModel keeps
                // the same rule, and CachedNextUpRepository does not cache a cancellation either).
                throw cancelled
            } catch (failed: Exception) {
                emptyList()
            }
            _state.update { it.copy(nextUp = leaders) }
        }
    }

    /**
     * 404 is the served/not-served line: PlainTicker answers `unsupported_ticker` for a ticker it
     * does not cover and `not_available` for one it has not produced yet, and both mean the same
     * thing to the screen. 503 `insufficient_data` is a known filer whose classification could not
     * be completed. Anything else is a call that did not land, and nothing is known either way.
     */
    private fun classify(error: Throwable): AnalysisState = when {
        error is ApiException && error.status == 404 -> AnalysisState.NotServed
        error is ApiException && error.status == 503 -> AnalysisState.Incomplete
        else -> AnalysisState.Unavailable
    }

    // ---- The token side -----------------------------------------------------------------

    private suspend fun loadTokenSide(now: Long) {
        val lookup = runCatching { catalog.catalog().forTicker(ticker) }
        val asset = lookup.getOrNull()

        _state.update {
            it.copy(
                catalogAsset = when {
                    asset != null -> Piece.Ready(asset)
                    // The catalog answered and this ticker has no xStock on Solana at all.
                    lookup.isSuccess -> Piece.Absent
                    else -> Piece.Failed
                },
                // The venue's own trading block when there is one, the local weekday schedule when
                // there is not; never the other way round (MarketHours).
                market = MarketHours.of(asset, now),
            )
        }

        val mint = asset?.solanaMint
        val symbol = asset?.symbol
        if (mint == null || symbol == null) {
            noTokenSide()
            return
        }

        // Three calls to three different hosts. Concurrent, each published on its own, and all of
        // them children of this refresh so a second refresh cancels them.
        coroutineScope {
            launch { loadQuote(mint) }
            launch { loadReserves(symbol) }
            launch { loadChainThenSplit(mint, symbol) }
        }
    }

    /**
     * Without a mint and a symbol nothing on the token side can even be asked for, so every piece
     * of it reads as failed rather than absent: the chain was not read, which is not the same as a
     * chain that grants the issuer nothing. The quote is the exception, because "we never asked
     * Jupiter" is not "Jupiter refused" and a prices banner would be a lie.
     */
    private fun noTokenSide() {
        _state.update {
            it.copy(
                quote = Piece.Absent,
                chain = Piece.Failed,
                reserves = Piece.Failed,
                split = Piece.Failed,
            )
        }
    }

    private suspend fun loadQuote(mint: String) {
        val entry = runCatching { prices.prices(listOf(mint))[mint] }
        _state.update {
            it.copy(
                quote = entry.fold(
                    onSuccess = { priced -> if (priced != null) Piece.Ready(priced) else Piece.Absent },
                    onFailure = { Piece.Failed },
                ),
            )
        }
    }

    /**
     * A JSON `null` from xStocks is [Piece.Absent]: the issuer publishes no reserves for this
     * symbol, which the row states as unavailable and never as a coverage of zero. A call that
     * failed is [Piece.Failed], a different sentence.
     */
    private suspend fun loadReserves(symbol: String) {
        val answer = runCatching { catalog.proofOfReserves(symbol).toReserves() }
        _state.update {
            it.copy(
                reserves = answer.fold(
                    onSuccess = { reserves -> if (reserves != null) Piece.Ready(reserves) else Piece.Absent },
                    onFailure = { Piece.Failed },
                ),
            )
        }
    }

    /**
     * The mint first, published as soon as it lands so the live bar can start, then the split
     * multiplier from whichever source can answer: the mint's own extension when the read worked,
     * the xStocks endpoint when it did not or when the mint carries no scaled amount at all.
     */
    private suspend fun loadChainThenSplit(mint: String, symbol: String) {
        val reading = runCatching { mints.mint(mint) }.getOrNull()
        val facts = reading?.facts

        _state.update {
            it.copy(
                chain = if (reading != null && facts != null) {
                    Piece.Ready(ChainRead(facts, reading.slot, reading.readAtMillis, reading.rpcAgeSeconds))
                } else {
                    Piece.Failed
                },
            )
        }

        val onChain = facts?.scaledUiAmount
        if (onChain != null) {
            _state.update { it.copy(split = Piece.Ready(SplitMultiplier.ofMint(onChain))) }
            return
        }
        val fallback = runCatching { catalog.multiplierRecord(symbol) }
        _state.update {
            it.copy(
                split = fallback.fold(
                    onSuccess = { record -> Piece.Ready(SplitMultiplier.ofXStocks(record)) },
                    onFailure = { Piece.Failed },
                ),
            )
        }
    }

    companion object {
        /** How often the wall clock is re-read while the screen is observed. */
        internal const val TICK_MILLIS = 1_000L
    }
}

/** The catalog entry for one underlying ticker, or null. Kept here so tests can name the rule. */
internal fun List<XStockAsset>.forTicker(ticker: String): XStockAsset? =
    firstOrNull { it.underlyingTicker.equals(ticker, ignoreCase = true) }
