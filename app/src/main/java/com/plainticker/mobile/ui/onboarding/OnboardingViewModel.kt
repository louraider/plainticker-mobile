package com.plainticker.mobile.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.prefs.OnboardingStore
import com.plainticker.mobile.prefs.WatchlistStore
import com.plainticker.mobile.repo.CatalogRepository
import com.plainticker.mobile.repo.SnapshotRepository
import com.plainticker.mobile.repo.SummaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The steps: the self-certification, then one screen on the controls every tokenized stock
 * carries (founder feedback 2026-09-29, skippable), then picking stocks to watch (skippable).
 *
 * The controls step comes before the pick step, not after it: the pick step's Skip lands on the
 * AAPLx page, where "Backing and controls" sits right under the price, so the reader meets the
 * rows having just been told what they are; after the pick it would stand between the picking and
 * the Today it fills.
 */
enum class OnboardingStep { CONSENT, CONTROLS, PICK }

/** One stock the pick step offers: the ticker is the watchlist key, the symbol is what it reads. */
data class PickChip(val ticker: String, val symbol: String)

/**
 * Where onboarding hands the reader over. [ticker] non-null opens that stock's page over Today
 * (the worked example, AAPLx, whose every figure is open); null opens Today itself, with whatever
 * was picked already in its Watched block.
 */
data class OnboardingExit(val ticker: String?)

/**
 * What the screen needs: whether the self-certification box is checked, which step is up, the
 * stocks offered and picked, and, once finished, where to go. The copy lives in strings.xml.
 */
data class OnboardingUiState(
    /** The self-certification checkbox. */
    val accepted: Boolean = false,
    /** True once onboarding is over; the host navigates to [exit]. */
    val completed: Boolean = false,
    val step: OnboardingStep = OnboardingStep.CONSENT,
    /** Analysed stocks, from the covered list this app actually has; empty while none has loaded. */
    val suggestions: List<PickChip> = emptyList(),
    val picked: Set<String> = emptySet(),
    val exit: OnboardingExit = OnboardingExit(null),
) {
    /** The consent button: AmberPrimaryAction's filled state while true, its disabled one while false. */
    val canContinue: Boolean get() = step == OnboardingStep.CONSENT && accepted && !completed

    /** The pick step's primary action: live once at least one stock is picked. */
    val canFinish: Boolean get() = step == OnboardingStep.PICK && picked.isNotEmpty() && !completed

    /** The stock "Skip" opens: the worked example when it is on offer, else Today. */
    val example: PickChip? get() = suggestions.firstOrNull { it.ticker == EXAMPLE_TICKER }
}

/** The fully open example every reader can read without paying: Apple's token. */
const val EXAMPLE_TICKER = "AAPL"

/** How many chips the pick step offers: two short rows on a phone, not a catalog. */
const val MAX_PICKS = 8

/**
 * Names a reader is most likely to know, tried first when the covered list carries them. Only an
 * order: a name the live list does not analyse is never offered, and the rest of the offer comes
 * from that list itself.
 */
private val FamiliarFirst = listOf("AAPL", "TSLA", "NVDA", "META", "JEF", "MSFT", "AMZN", "GOOGL")

/**
 * The pick step's chips: every [analysed] ticker that also has a token ([symbols], ticker to
 * symbol), familiar names first, then the rest by symbol, at most [max].
 */
fun onboardingPicks(analysed: Collection<String>, symbols: Map<String, String>, max: Int = MAX_PICKS): List<PickChip> {
    val keys = analysed.map { it.trim().uppercase() }.filter { it.isNotEmpty() && it in symbols }.distinct() // lint-allow uppercase: map key
    val familiar = FamiliarFirst.filter { it in keys }
    val rest = (keys - familiar.toSet()).sortedBy { symbols.getValue(it) }
    return (familiar + rest).take(max).map { PickChip(it, symbols.getValue(it)) }
}

/**
 * Consent first, written once when the reader confirms; then the pick step, which watches what
 * was picked and hands over to Today, or skips to the worked example.
 *
 * The chips come from the bundled snapshot at once (real covered stocks, no network), then from
 * the live `/summary` joined with the catalog when that answers, the same "analysed and has a
 * token" rule the Stocks list uses.
 */
class OnboardingViewModel(
    private val store: OnboardingStore,
    private val watchlist: WatchlistStore? = null,
    private val summaries: SummaryRepository? = null,
    private val catalog: CatalogRepository? = null,
    private val snapshot: SnapshotRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(completed = store.isOnboarded()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        val hasSource = snapshot != null || (summaries != null && catalog != null)
        if (!_state.value.completed && watchlist != null && hasSource) viewModelScope.launch { loadSuggestions() }
    }

    fun setAccepted(accepted: Boolean) {
        _state.update { it.copy(accepted = accepted) }
    }

    /**
     * Persists the onboarded flag and moves on to the controls step. A press while the box is
     * unchecked, and a second press after the first one landed, both write nothing. Without a
     * watchlist to fill (a caller that has none), there is no pick step and this finishes at once.
     */
    fun confirm() {
        if (!_state.value.canContinue) return
        store.setOnboarded(true)
        if (watchlist == null) {
            _state.update { it.copy(completed = true) }
        } else {
            _state.update { it.copy(step = OnboardingStep.CONTROLS) }
        }
    }

    /** Continue or Skip on the controls step: both move on to the pick step, and nothing is written. */
    fun continueToPick() {
        if (_state.value.step != OnboardingStep.CONTROLS || _state.value.completed) return
        _state.update { it.copy(step = OnboardingStep.PICK) }
    }

    fun togglePick(ticker: String) {
        if (_state.value.step != OnboardingStep.PICK || _state.value.completed) return
        _state.update { it.copy(picked = if (ticker in it.picked) it.picked - ticker else it.picked + ticker) }
    }

    /** Watches every pick and hands over to Today, where they now fill the Watched block. */
    fun finish() {
        val current = _state.value
        if (!current.canFinish) return
        current.picked.sorted().forEach { watchlist?.add(it) }
        _state.update { it.copy(completed = true, exit = OnboardingExit(null)) }
    }

    /** Watches nothing and opens the worked example, or Today when the example is not on offer. */
    fun skip() {
        val current = _state.value
        if (current.step != OnboardingStep.PICK || current.completed) return
        _state.update { it.copy(completed = true, exit = OnboardingExit(current.example?.ticker)) }
    }

    private suspend fun loadSuggestions() {
        val bundled = try {
            snapshot?.listSnapshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            null
        }
        if (bundled != null) {
            val picks = onboardingPicks(
                bundled.rows.map { it.ticker },
                bundled.assets.associate { it.ticker.trim().uppercase() to it.symbol }, // lint-allow uppercase: map key
            )
            if (picks.isNotEmpty()) _state.update { it.copy(suggestions = picks) }
        }
        val summarySource = summaries ?: return
        val catalogSource = catalog ?: return
        val live = try {
            val rows = summarySource.summary().rows.map { it.ticker }
            val symbols = catalogSource.catalog()
                .filter { it.solanaMint != null && it.symbol.isNotBlank() }
                .associate { it.underlyingTicker.trim().uppercase() to it.symbol } // lint-allow uppercase: map key
            onboardingPicks(rows, symbols)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: Exception) {
            emptyList()
        }
        if (live.isNotEmpty()) _state.update { it.copy(suggestions = live, picked = it.picked.filterTo(HashSet()) { t -> live.any { c -> c.ticker == t } }) }
    }
}
