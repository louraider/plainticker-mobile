package com.plainticker.mobile.ui.detail

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.MultiplierSource
import com.plainticker.mobile.data.SplitMultiplier
import com.plainticker.mobile.data.rpc.MintFacts
import com.plainticker.mobile.ui.swap.SwapHolding
import com.plainticker.mobile.ui.swap.SwapToken
import java.math.BigDecimal
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.plainticker.Verdict
import com.plainticker.mobile.data.plainticker.Axes
import com.plainticker.mobile.data.plainticker.Axis
import com.plainticker.mobile.data.xstocks.MarketSource
import com.plainticker.mobile.data.xstocks.PriceLabel
import com.plainticker.mobile.data.xstocks.Reserves
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.ReadText
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.list.skrWeight
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.words
import java.time.Instant
import java.time.ZoneOffset

/**
 * What the Detail screen says, decided away from the composition (task T9, design task DT6).
 *
 * The screen is a join of six sources with three renderings each (the fact, its absence, and a
 * source that could not be read), so the rules that matter are the ones that pick a sentence, not
 * the ones that place a pixel. They live here as pure functions over [DetailUiState] so every
 * state in plan section 13 Pass 2 is a unit test rather than a device run, and so a regression
 * that turns "we could not read the mint" into "the issuer holds nothing" fails the gate.
 *
 * Two rules are enforced here and nowhere else:
 *
 * 1. **No premium is computed in a composable.** The gauge is handed [DetailUiState.tracking], the
 *    one answer from [TrackingQuality], and draws or withholds on that alone.
 * 2. **Caution belongs to the value of an issuer control that is actually present**, which is the
 *    permanent delegate and pausable transfers and nothing else (DESIGN.md section 2). A mint that
 *    could not be read is Unknown in Ink, never a warning and never a clean bill.
 */

// ---- The pieces the screen draws ---------------------------------------------------------------

/** The one banner slot on Detail: where the venue is, and nothing else (DESIGN.md section 4). */
enum class DetailBanner(@StringRes val text: Int) {
    /** The issuer stopped trading this asset, which is not the exchange closing. */
    HALTED(R.string.banner_market_halted),

    /** The venue's own block says the exchange session is over. */
    CLOSED(R.string.banner_market_closed),

    /** No venue block answered, so the weekday schedule decided and the banner says so. */
    CLOSED_LOCAL(R.string.banner_market_closed_local),

    /**
     * The weekday schedule reads as a session and no venue block confirmed it. The schedule knows
     * no holidays, so an open it alone claims is named as the schedule's claim: the price row is
     * labelled live off that answer, and a reader is told where it came from.
     */
    OPEN_LOCAL(R.string.banner_market_open_local),
}

/**
 * The price row: the token's own figure left, the reference right. The reference is a *close* only
 * while the exchange is shut; during its session the same field is a live price and the label says
 * so, which is the whole reason [PriceLabel] exists in the data layer.
 *
 * The row also carries the liquidity floor's own sentence, because the floor is a property of this
 * block and not of the gauge under it. See [lead] and [comparable].
 */
data class PriceRow(
    /**
     * The sentence that disqualifies the comparison, read before either figure. Non-null exactly
     * where the floor withheld the premium (DESIGN.md section 1.1), which is also exactly where
     * the gauge draws nothing.
     */
    val lead: Copy?,
    /** What the token's own figure is called: a price above the floor, a pool quote below it. */
    val tokenLabel: Copy,
    val tokenPrice: String?,
    /** Why there is no token price, when there is none. */
    val tokenNote: Copy?,
    val referenceLabel: Copy,
    val referencePrice: String?,
    /** Why there is no reference price, when there is none. */
    val referenceNote: Copy?,
    /**
     * Jupiter answered and has no price for this token (JEFx, AALx). The screen then states that
     * sentence in the figure's place rather than a bare placeholder dash above it (device QA of
     * 1.3.17: a lone "-" at 40sp read as a broken page).
     */
    val tokenAbsent: Boolean = false,
) {
    /**
     * Whether the two figures may be read against one another.
     *
     * False below the floor, and the screen sets them at one size there. The shipped v0.2.0 did
     * not: it printed the token at 40sp Ink over the NYSE close at 20sp Ink 2 with the pool
     * sentence at 13sp between them, so on APPx a reader was handed $611.56 and $323.00 and
     * subtracted them to the +89.34 percent the floor exists to suppress. The floor's own argument
     * settles it: if the quote is too thin to compare, the figure is not a price of the company
     * either, and it may not be the largest true thing on the screen.
     */
    val comparable: Boolean get() = lead == null
}

/** The live bar over the trust grid: what it says, and whether it may breathe. */
data class LiveLine(
    val label: Copy,
    val meta: Copy,
    /** Breathing is the only continuous motion on the screen, and only while the read is live. */
    val live: Boolean,
    /** Stable on purpose: a screen reader must not be interrupted by the ticking age. */
    val announcement: Copy,
)

/** One cell of the "Backing and controls" grid. */
data class TrustFact(
    val label: Copy,
    val value: Copy,
    val sub: Copy?,
    val span: Int = 1,
    val subMono: Boolean = false,
    /** Caution on the value only, and only where the issuer actually holds the control. */
    val caution: Boolean = false,
    /** True when [value] is an on-chain address, set in JetBrains Mono (DESIGN.md section 3). */
    val valueMono: Boolean = false,
    /** The full text a tap copies, for a cell whose value is a shortened address. */
    val copies: String? = null,
)

/**
 * One axis of "Against the sector". A [value] of null is, ordinarily, a SEC-derived field the
 * filer does not publish (a foreign 20-F filer, a young one): the row says it is not available for
 * this filer and draws no marker, because a marker at zero would be a claim the payload never made.
 *
 * [locked] is the other, unrelated reason [value] can be null: the Pro-numbers lock (founder
 * decision 2026-09-23) withholds valuation and momentum from a free, non-AAPL caller, and the
 * sector actually has a value there, the server simply did not send it. The two must never draw
 * the same sentence: "not available for this filer" is a fact about the filer, [locked] is a fact
 * about entitlement, and screen has to tell them apart or it would claim data was never collected
 * when it was only withheld.
 */
data class TrackRow(
    val label: Copy,
    val value: String?,
    /** The server's own state word, lowercased. Empty when the payload sent none. */
    val state: String,
    val positionPct: Float,
    /** True when [Axis.locked] withheld this axis; see this class's own doc comment. */
    val locked: Boolean = false,
)

/** One F-Score signal in the fixed order of docs/data-map.md. [ok] is null for "n/a". */
data class SignalItem(@StringRes val name: Int, val ok: Boolean?)

/** The F-Score block: the numeral out of its scale, then the nine signals. */
data class FScoreContent(
    /** Null when the filer has no F-Score at all; the block then states that instead of a zero. */
    val score: String?,
    /** The scale, as a number and not as a numeral: it is what selects the plural of "signals". */
    val outOf: Int,
    val signals: List<SignalItem>,
) {
    /**
     * True when the filer publishes no F-Score at all, which is the SEC-null case: no numeral and
     * nine signals nobody evaluated. The heading stays and one line says so, rather than a zero
     * over nine rows of "n/a".
     */
    val unavailable: Boolean get() = score == null && signals.all { it.ok == null }
}

/** The Method block: how old the analysis is, what it is, and where it came from. */
data class MethodContent(
    /** Only past a day (docs/data-map.md, Detail "Analysis age"). */
    val age: Copy?,
    val statement: Copy,
    val sources: Copy,
)

/** The one line that replaces the fundamentals when there are none, with its next step. */
data class FundamentalsNotice(val text: Copy, val hint: Copy?)

/**
 * The ticker's standing in the next-up list of SKR-weighted coverage curation: where it ranks
 * among the uncovered tickers staked SKR has voted for, and what stands behind it.
 */
data class NextUpLine(
    /** "Next up: 2 of 20, by staked SKR" */
    val rank: Copy,
    /** "38,406.2 SKR from 3 voters", counted copy so one voter reads as one. */
    val weight: Copy,
)

// ---- The rules ---------------------------------------------------------------------------------

/** USDC is the input side of every swap in the hackathon build; the symbol is not translated copy. */
internal const val INPUT_SYMBOL = "USDC"

/**
 * How long a mint read stays live. The forwarder caches each `getAccountInfo` for 60 s, so past a
 * minute what is on screen is no longer the newest the node would hand over: the bar stops
 * breathing and the meta line keeps counting, which is the honest pair.
 */
internal const val LIVE_WINDOW_MILLIS = 60_000L

/**
 * The scale the gauge is drawn on, as DESIGN.md section 4 fixes it: the spread the tracked
 * catalogue actually produced, measured beside the floor itself.
 */
internal const val GAUGE_SCALE_PCT = TrackingQuality.TRACKED_SPREAD_PCT

/** The nine F-Score signals, in the fixed order of docs/data-map.md. Never re-sorted. */
internal val F_SCORE_SIGNALS: List<Int> = listOf(
    R.string.signal_roa_positive,
    R.string.signal_cfo_positive,
    R.string.signal_roa_improving,
    R.string.signal_accruals,
    R.string.signal_leverage_falling,
    R.string.signal_liquidity_improving,
    R.string.signal_no_new_shares,
    R.string.signal_gross_margin_improving,
    R.string.signal_asset_turnover_improving,
)

/** The default F-Score scale when the payload does not state one. */
private const val F_SCORE_OUT_OF = 9

/** The hero: the token's symbol once the catalog names it, the plain ticker until then. */
val DetailUiState.heroTicker: String get() = symbol ?: ticker

/** The company, from the analysis first because it is the registrant's own name. */
val DetailUiState.heroCompany: String? get() = analysis?.company ?: asset?.name

/**
 * The sector line under the hero's company name, in the approved Amber Detail frame (`AAPLx /
 * Apple Inc. / Information Technology`). Read straight from the analysis, the same raw-data
 * treatment [heroCompany] already gets: null while nothing is served yet, or for a ticker
 * PlainTicker does not classify, in which case the hero simply carries two lines instead of
 * three rather than a heading over nothing.
 */
val DetailUiState.heroSector: String? get() = analysis?.sector

// ---- The verdict (task app-verdict) ------------------------------------------------------------

/**
 * Directly under the hero and above the gauge, the slot the web gives its verdict band
 * (components/ticker/VerdictBand.tsx). The founder's decision made this word a paid element, and
 * the server enforces it: the real classification never reaches an unentitled client, so this type
 * has nothing to filter or blur on its own, only [Locked] to read off the payload's own flag.
 *
 * Null, drawing nothing, in every state this repository already drew nothing in before this task:
 * the analysis not served, incomplete or unavailable, and a served payload that carries no
 * `verdict` block at all (a server that predates it, or one with monetization off).
 */
sealed interface VerdictBlock {
    /** The analysis itself has not resolved yet, so whether a verdict exists is not known either. */
    data object Loading : VerdictBlock

    /** [label] is the payload's own text, raw: never translated here, never invented. */
    data class Unlocked(val label: Copy) : VerdictBlock

    /** The server withheld the word. Nothing behind this state stands in for it. */
    data object Locked : VerdictBlock

    /**
     * The method gives no class for this company (`verdict.class_state` "unavailable"), and says
     * why in [reason]: the web's own wording (lib/methodology/decision-copy.ts), mirrored in
     * strings.xml. Device QA of 1.3.17: JEFx, a financial, drew no Classification block at all,
     * which read as a broken page rather than as a sector the model does not cover yet.
     */
    data class Unavailable(val reason: Copy) : VerdictBlock
}

val DetailUiState.verdictBlock: VerdictBlock?
    get() = when (val state = analysisState) {
        AnalysisState.Loading -> VerdictBlock.Loading
        is AnalysisState.Served -> {
            val verdict = state.payload.verdict ?: return null
            if (verdict.unavailable) {
                VerdictBlock.Unavailable(
                    words(
                        if (verdict.classReason == Verdict.REASON_SECTOR_MODEL_PENDING) {
                            R.string.detail_verdict_unavailable_sector
                        } else {
                            R.string.detail_verdict_unavailable_data
                        },
                    ),
                )
            } else if (verdict.locked) {
                VerdictBlock.Locked
            } else {
                val label = verdict.labelEn?.takeIf { it.isNotBlank() } ?: return null
                VerdictBlock.Unlocked(raw(label))
            }
        }

        AnalysisState.NotServed, AnalysisState.Incomplete, AnalysisState.Unavailable -> null
    }

/** The token's own facts can only be drawn once the catalog names a mint for this ticker. */
val DetailUiState.hasToken: Boolean get() = catalogAsset.valueOrNull?.solanaMint != null

/**
 * One line where the whole token side would be, when there is no token side: the catalog answered
 * and this ticker has no xStock, or the catalog did not answer at all. Null whenever a mint is
 * known, including while the reads against it are still in flight.
 */
val DetailUiState.tokenNotice: Copy?
    get() = when {
        hasToken || catalogAsset.isLoading -> null
        catalogAsset.isFailed -> words(R.string.list_catalog_unavailable)
        else -> words(R.string.detail_no_xstock)
    }

/** Where the venue is. Nothing to say while the venue itself reports its own session. */
val DetailUiState.banner: DetailBanner?
    get() {
        val market = market ?: return null
        val guessed = market.source == MarketSource.LOCAL_SCHEDULE
        return when {
            market.halted -> DetailBanner.HALTED
            market.regularSession && guessed -> DetailBanner.OPEN_LOCAL
            market.regularSession -> null
            guessed -> DetailBanner.CLOSED_LOCAL
            else -> DetailBanner.CLOSED
        }
    }

/**
 * The price row. A quote that failed is a missing price with a reason, never a zero; a token
 * Jupiter answered about without pricing says that instead, because "Jupiter refused" and "Jupiter
 * does not price this" are different facts about the token.
 *
 * The floor speaks here, at the top of the block, rather than under it: the sentence that says the
 * two figures cannot be compared has to be read before them, and the figures have to stop being a
 * staged comparison. [PriceRow.lead] carries the sentence the gauge used to carry, and
 * [PriceRow.comparable] is what the screen sets the type from.
 */
val DetailUiState.priceRow: PriceRow
    get() {
        val live = priceLabel == PriceLabel.TRACKING_WITHIN
        val reference = price?.stockData?.price
        val lead = when (val quality = tracking) {
            is TrackingQuality.Thin ->
                words(R.string.detail_gauge_thin, Fmt.compactMoney(quality.poolUsd, roundDown = true))

            TrackingQuality.Untracked -> words(R.string.detail_gauge_pool_unknown)

            // Tracked, or no quote at all: nothing to withhold, and the notes below say the rest.
            else -> null
        }
        return PriceRow(
            lead = lead,
            tokenLabel = words(if (lead == null) R.string.detail_token_price else R.string.detail_pool_quote),
            tokenPrice = price?.usdPrice?.let(Fmt::price),
            tokenNote = when {
                price != null -> null
                quote.isFailed -> words(R.string.list_prices_unavailable)
                quote.isAbsent -> words(R.string.detail_price_absent)
                else -> null
            },
            referenceLabel = words(if (live) R.string.detail_nyse_price else R.string.detail_nyse_close),
            referencePrice = reference?.let(Fmt::price),
            // Jupiter priced the token and sent no `stockData`: there is a token price and nothing
            // to measure it against, which is a missing reference and not a tracking failure.
            referenceNote = if (reference == null && price != null) {
                words(R.string.detail_reference_unavailable)
            } else {
                null
            },
            tokenAbsent = price == null && quote.isAbsent,
        )
    }

/**
 * What the gauge is measuring, named by the same market state as the price row. The premium itself
 * is never computed here: [DetailUiState.tracking] already decided whether one may be drawn.
 */
val DetailUiState.gaugeReference: Copy
    get() = words(
        if (priceLabel == PriceLabel.TRACKING_WITHIN) {
            R.string.detail_gauge_reference_live
        } else {
            R.string.detail_gauge_reference_close
        },
    )

/** The gauge is drawn only where there is a quote to judge; the price row explains the rest. */
val DetailUiState.gauge: TrackingQuality? get() = tracking

/**
 * The live bar. A mint that could not be read keeps the slot and says so: the trust rows below it
 * are then Unknown, and a reader has to be told why before reading them.
 */
val DetailUiState.liveLine: LiveLine?
    get() {
        val read = chain.valueOrNull
        if (read == null) {
            if (!chain.isFailed) return null
            return LiveLine(
                label = words(R.string.detail_live_unread_label),
                meta = words(R.string.detail_live_unread_meta),
                live = false,
                announcement = words(R.string.detail_live_unread_label),
            )
        }
        val slot = Fmt.slot(read.slot)
        // Counted from when the node answered, not from when the answer reached this phone: the
        // forwarder's X-Rpc-Age says how long it sat in its cache (Mert, judges' review 2026-09-27).
        val observedAt = read.observedAtMillis
        val ageMillis = (nowMillis - observedAt).coerceAtLeast(0L)
        val live = ageMillis <= LIVE_WINDOW_MILLIS
        return LiveLine(
            label = words(R.string.detail_live_label),
            meta = words(
                R.string.detail_live_meta,
                slot,
                Fmt.relativeAgo(Instant.ofEpochMilli(observedAt), Instant.ofEpochMilli(nowMillis)),
            ),
            live = live,
            // "At slot", so the number is never heard as a time, and the age as the live window
            // rather than the ticking seconds, so the polite region speaks once per change.
            announcement = words(if (live) R.string.detail_live_a11y else R.string.detail_live_a11y_older, slot),
        )
    }

/** True while any source behind "Backing and controls" is still in flight. */
val DetailUiState.trustLoading: Boolean
    get() = chain.isLoading || reserves.isLoading || split.isLoading

/**
 * The cells of "Backing and controls", in the order DESIGN.md section 5 fixes: the reserves
 * spanning the first row, then the two issuer controls, then the multiplier and the hook. Since
 * the judges' review of 2026-09-27 (Mert) the reserves say they are the issuer's own report, a
 * second full-width row holds that report up against the supply the mint states, and a mint
 * with an active permanent delegate names the delegate's address on a last full-width row.
 *
 * Every cell has three renderings and they are kept apart on purpose. An extension the mint does
 * not carry reads "None", which is a fact. A mint that could not be read reads "Unknown" with the
 * reason under it, which is an absence of facts. Only the first can ever be reassuring, and only
 * a control the issuer actually holds takes Caution.
 */
val DetailUiState.trustFacts: List<TrustFact>
    get() = listOfNotNull(reservesCell(), supplyCell(), delegateCell(), pausableCell(), splitCell(), hookCell(), delegateAddressCell())

/**
 * Relative gap, as a fraction, under which the chain's supply and the issuer's circulating count
 * are called a match. The two are read at different moments (the attestation is stamped, the
 * mint is live), so the two are almost never exactly equal for a token that trades.
 */
internal const val SUPPLY_MATCH_TOLERANCE = 0.001

/**
 * The supply the mint states, as a wallet counts it: raw supply over 10^decimals, times the
 * scaled UI multiplier in force at the read (the Token-2022 gotcha: a split changes the
 * multiplier, not the raw supply, so raw alone would be off by the split ratio). Null when the
 * mint was not read.
 */
internal fun ChainRead.supplyShown(): java.math.BigDecimal {
    val multiplier = facts.scaledUiAmount
        ?.let { com.plainticker.mobile.data.SplitMultiplier.ofMint(it).effectiveAt(readAtMillis) }
        ?: com.plainticker.mobile.data.SplitMultiplier.NONE
    return facts.supplyTokens().multiply(java.math.BigDecimal.valueOf(multiplier))
}

/**
 * The supply the mint states, with the issuer's circulating count beside it (Mert, judges' review
 * 2026-09-27). The value is the mint's own count; the sub line says the two match within
 * [SUPPLY_MATCH_TOLERANCE], or states xStocks' count as it is.
 *
 * **Never a signed gap** (device QA of 1.3.16). The row used to print the difference as a percent
 * of the issuer's figure, and on 2026-09-27 AAPLx read "+297.6%", TSLAx +21.2% and NVDAx +72.7%.
 * That was checked for a unit bug first and is not one: the AAPLx mint's raw supply is 153,762.68
 * tokens and its multiplier in force is 1.00327, so the mint states 154,265.34 (the same figure
 * Jupiter's `scaledUiConfig` and `getTokenSupply`'s `uiAmount` give), while xStocks' own
 * proof-of-reserves answers 38,800.68 in circulation, backed by 39,102 shares. No multiplier or
 * decimals choice closes a four-to-one gap, and TSLAx, whose multiplier is exactly 1, differs too.
 * The two are different measures (tokens minted on this chain against the count xStocks calls in
 * circulation), and the app can see neither why nor where the rest sits, so it states both counts
 * and draws no conclusion: a signed percentage read as a claim of over-issuance nothing backs.
 * Unknown when either side is missing.
 */
private fun DetailUiState.supplyCell(): TrustFact {
    val label = words(R.string.detail_fact_supply)
    val read = chain.valueOrNull ?: return unreadCell(label).copy(span = 2)
    val onChain = read.supplyShown()
    // Fixed two decimals, so a figure ending in zero keeps it (72,583.60, not 72,583.6).
    val value = raw(Fmt.decimal(onChain, 2))
    val reported = reserves.valueOrNull
        ?: return TrustFact(label = label, value = value, sub = words(R.string.detail_fact_supply_no_report_sub), span = 2)
    val issuer = java.math.BigDecimal.valueOf(reported.tokensInCirculation)
    val issuerText = Fmt.decimal(issuer, 2)
    if (issuer.signum() <= 0) {
        return TrustFact(label = label, value = value, sub = words(R.string.detail_fact_supply_no_report_sub), span = 2)
    }
    val gap = onChain.subtract(issuer).toDouble() / issuer.toDouble()
    val matches = kotlin.math.abs(gap) <= SUPPLY_MATCH_TOLERANCE
    return TrustFact(
        label = label,
        value = value,
        sub = words(if (matches) R.string.detail_fact_supply_matches else R.string.detail_fact_supply_sub, issuerText),
        span = 2,
        subMono = true,
    )
}

/**
 * The permanent delegate's address, which [MintFacts] parses and the grid never drew (Mert,
 * judges' review 2026-09-27: name the delegate). Shortened, in the identifier face, and copied
 * whole on tap. Only for a delegate that can act; absent otherwise, since the delegate cell
 * already says "None".
 */
private fun DetailUiState.delegateAddressCell(): TrustFact? {
    val address = chain.valueOrNull?.facts?.permanentDelegate?.delegate ?: return null
    return TrustFact(
        label = words(R.string.detail_fact_delegate_address),
        value = raw(Fmt.shortKey(address)),
        sub = words(R.string.receipt_tap_to_copy),
        span = 2,
        valueMono = true,
        copies = address,
    )
}

private fun DetailUiState.reservesCell(): TrustFact {
    val label = words(R.string.detail_fact_por)
    val held = reserves.valueOrNull
    if (held == null) {
        // Absent is xStocks not having published anything for this token (a JSON null, or its
        // zero-for-zero placeholder): said as that, not as an Unknown that reads like a failure.
        return TrustFact(
            label = label,
            value = words(if (reserves.isAbsent) R.string.detail_value_not_published else R.string.detail_value_unknown),
            sub = words(
                if (reserves.isAbsent) R.string.detail_fact_por_absent_sub else R.string.detail_fact_por_failed_sub,
            ),
            span = 2,
        )
    }
    return TrustFact(
        label = label,
        value = held.coverage?.let { raw(Fmt.percent(it * 100.0, signed = false, decimals = 1)) }
            ?: words(R.string.detail_value_unknown),
        sub = held.coverageSub(),
        span = 2,
        subMono = true,
    )
}

/** "26,101 shares held by Alpaca for 25,924 tokens", naming the custodian when exactly one is. */
private fun Reserves.coverageSub(): Copy {
    val shares = Fmt.tokenAmount(sharesHeld, maxDecimals = 2)
    val tokens = Fmt.tokenAmount(tokensInCirculation, maxDecimals = 2)
    val named = custodian
    return if (named != null) {
        words(R.string.detail_fact_por_custodian_sub, shares, named, tokens)
    } else {
        words(R.string.detail_fact_por_sub, shares, tokens)
    }
}

private fun DetailUiState.delegateCell(): TrustFact {
    val label = words(R.string.detail_fact_delegate)
    val facts = chain.valueOrNull?.facts ?: return unreadCell(label)
    val delegate = facts.permanentDelegate
    return if (delegate != null && delegate.active) {
        TrustFact(
            label = label,
            value = words(R.string.value_yes),
            sub = words(R.string.detail_fact_delegate_sub),
            caution = true,
        )
    } else {
        // The extension absent, or present with the delegate revoked: nobody can move a holder's
        // tokens either way, so the row is a fact and not a warning.
        TrustFact(label = label, value = words(R.string.value_none), sub = words(R.string.detail_fact_delegate_none_sub))
    }
}

private fun DetailUiState.pausableCell(): TrustFact {
    val label = words(R.string.detail_fact_pausable)
    val facts = chain.valueOrNull?.facts ?: return unreadCell(label)
    val pausable = facts.pausable
        ?: return TrustFact(
            label = label,
            value = words(R.string.value_none),
            sub = words(R.string.detail_fact_pausable_none_sub),
        )
    return TrustFact(
        label = label,
        value = words(R.string.value_yes),
        sub = words(
            if (pausable.paused) R.string.detail_fact_pausable_paused_sub else R.string.detail_fact_pausable_sub,
        ),
        caution = true,
    )
}

/**
 * The multiplier, and the split still to come. The cell names its source whenever the answer is
 * the issuer's description rather than the chain's own extension, so an xStocks statement is never
 * drawn as an on-chain fact (docs/data-map.md, "The split multiplier prefers the chain").
 */
private fun DetailUiState.splitCell(): TrustFact {
    val label = words(R.string.detail_fact_split)
    val multiplier = split.valueOrNull
        ?: return TrustFact(
            label = label,
            value = words(R.string.detail_value_unknown),
            sub = words(R.string.detail_fact_split_failed_sub),
        )
    val fromMint = multiplier.source == MultiplierSource.MINT
    val pending = multiplier.pending?.takeIf { !it.activated(nowMillis) }
    val sub = when {
        pending != null -> words(
            if (fromMint) R.string.detail_fact_split_changes_sub else R.string.detail_fact_split_changes_xstocks_sub,
            Fmt.decimal(pending.multiplier),
            Fmt.day(Instant.ofEpochMilli(pending.activatesAtMillis()).atZone(ZoneOffset.UTC).toLocalDate()),
        )

        fromMint -> words(R.string.detail_fact_split_sub)
        else -> words(R.string.detail_fact_split_from_xstocks)
    }
    return TrustFact(label = label, value = raw(Fmt.decimal(multiplier.current)), sub = sub)
}

private fun DetailUiState.hookCell(): TrustFact {
    val label = words(R.string.detail_fact_hook)
    val facts = chain.valueOrNull?.facts ?: return unreadCell(label)
    val hook = facts.transferHook
    val program = hook?.programId
    return if (program != null) {
        TrustFact(label = label, value = raw(Fmt.shortKey(program)), sub = words(R.string.detail_fact_hook_runs_sub), valueMono = true)
    } else {
        // No extension, or the extension with an empty slot: either way no program runs on a
        // transfer, which is the fact a reader needs and the only one the mint supports.
        TrustFact(label = label, value = words(R.string.value_none), sub = words(R.string.detail_fact_hook_sub))
    }
}

/** A chain fact nobody could read: Unknown, with the reason, in Ink. Never "None", never Caution. */
private fun unreadCell(label: Copy): TrustFact = TrustFact(
    label = label,
    value = words(R.string.detail_value_unknown),
    sub = words(R.string.detail_chain_unread_sub),
)

/**
 * True while the Pro-numbers lock is withholding this payload's composite and axes (founder
 * decision 2026-09-23): read off [Axis.locked] on the two axes the server actually locks, since
 * [AnalysisPayload.compositePercentile] carries no lock flag of its own on the wire. [Axes.quality]
 * is never locked, so it is not consulted here.
 */
private val Axes.proNumbersLocked: Boolean get() = valuation?.locked == true || momentum?.locked == true

/**
 * "composite 51" from the percentile the payload carries; "composite, Pro" when the lock withheld
 * it instead of a payload that simply carries none, told apart by [Axes.proNumbersLocked]; absent
 * when there is no composite and no lock either (a server that predates the lock, or one with
 * monetization off, on a payload with nothing to report).
 */
val DetailUiState.compositeMeta: Copy?
    get() {
        val payload = analysis ?: return null
        val value = payload.compositePercentile
        if (value != null) return words(R.string.detail_composite, Fmt.decimal(value, decimals = 0))
        return if (payload.axes.proNumbersLocked) words(R.string.detail_composite_locked) else null
    }

/**
 * Quality, valuation and momentum, in that order, each with the server's own state word lowercased.
 * The v1.1 payload carries no leaf fundamentals, so nothing stands under these three
 * (docs/data-map.md, gap 1): three tracks and the composite in the heading is the whole section.
 */
val DetailUiState.tracks: List<TrackRow>
    get() {
        val axes = analysis?.axes ?: return emptyList()
        return listOf(
            trackRow(R.string.detail_track_quality, axes.quality),
            trackRow(R.string.detail_track_valuation, axes.valuation),
            trackRow(R.string.detail_track_momentum, axes.momentum),
        )
    }

private fun trackRow(@StringRes label: Int, axis: Axis?): TrackRow = TrackRow(
    label = words(label),
    value = axis?.value?.takeIf { it.isFinite() }?.let { axisValue(it, axis.scale) },
    state = (axis?.labelEn ?: axis?.state).orEmpty().lowercase(),
    positionPct = ((axis?.position ?: 0.0) * 100.0).toFloat(),
    locked = axis?.locked == true,
)

/**
 * An axis value in the shape its own scale asks for: "8/9" out of nine, "51" out of a hundred,
 * "0.79" out of one. An unknown scale falls back to two decimals rather than guessing a shape.
 */
internal fun axisValue(value: Double, scale: String?): String = when (scale) {
    "0-9" -> Fmt.decimal(value, decimals = 0) + "/9"
    "0-100" -> Fmt.decimal(value, decimals = 0)
    "0-1" -> Fmt.decimal(value, decimals = 2)
    else -> Fmt.decimal(value, decimals = 2)
}

/**
 * The F-Score numeral and the nine signals. A filer with no F-Score at all keeps the heading and
 * says the score is not available for it; a single signal the filings cannot answer reads "n/a".
 */
val DetailUiState.fScore: FScoreContent?
    get() {
        val payload = analysis ?: return null
        val fscore = payload.fscore
        val outOf = fscore?.scale?.substringAfter('-', "")?.toIntOrNull() ?: F_SCORE_OUT_OF
        val signals = F_SCORE_SIGNALS.mapIndexed { index, name ->
            SignalItem(name = name, ok = fscore?.signals?.getOrNull(index))
        }
        return FScoreContent(
            score = fscore?.score?.let { Fmt.count(it) },
            outOf = outOf,
            signals = signals,
        )
    }

/**
 * The Method block. The statement is the payload's own sentence whenever it sent one; the bundled
 * one is the same disclaimer, so a payload that omits it still carries the claim it has to carry.
 */
val DetailUiState.method: MethodContent?
    get() {
        val payload = analysis ?: return null
        val asOf = payload.asOfEpochMillis()
        return MethodContent(
            age = if (analysisAgeShown && asOf != null) {
                words(
                    R.string.detail_analysis_age,
                    Fmt.relativeAgo(Instant.ofEpochMilli(asOf), Instant.ofEpochMilli(nowMillis)),
                )
            } else {
                null
            },
            statement = payload.method?.statementEn?.takeIf { it.isNotBlank() }?.let(::raw)
                ?: words(R.string.detail_method_body),
            sources = words(R.string.detail_method_sources),
        )
    }

/**
 * The one line that stands where the fundamentals would be. The trust layer above it is untouched:
 * a token nobody has classified still gets its reserves and its issuer controls, which is the half
 * of the product that has to work for a new listing.
 */
val DetailUiState.fundamentalsNotice: FundamentalsNotice?
    get() = when (analysisState) {
        is AnalysisState.Served, is AnalysisState.Loading -> null

        is AnalysisState.NotServed -> FundamentalsNotice(
            text = words(R.string.detail_analysis_pending),
            hint = words(R.string.detail_analysis_pending_action),
        )

        is AnalysisState.Incomplete -> FundamentalsNotice(words(R.string.detail_analysis_incomplete), null)

        is AnalysisState.Unavailable -> FundamentalsNotice(words(R.string.detail_analysis_unavailable), null)
    }

/**
 * The standing, drawn only where PlainTicker classifies nothing and the leaders name this ticker.
 * A served ticker has no standing, a ticker nobody has voted for has nothing to state, and a
 * weight that is not a number is not printed. The rank is a position in the server's own order,
 * heaviest first, out of however many leaders it sent (at most twenty).
 */
val DetailUiState.nextUpLine: NextUpLine?
    get() {
        if (!analysisNotServed) return null
        val index = nextUp.indexOfFirst { it.ticker.equals(ticker, ignoreCase = true) }
        if (index < 0) return null
        val row = nextUp[index]
        val raw = row.weightRaw() ?: return null
        return NextUpLine(
            rank = words(R.string.next_up_detail_rank, Fmt.count(index + 1), Fmt.count(nextUp.size)), // lint-allow count: a position and a size, no noun follows either
            weight = counted(R.plurals.next_up_detail_weight, row.voters, skrWeight(raw), Fmt.count(row.voters)),
        )
    }

/** "Swap USDC to TSLAx". Null until the catalog names the token, because the verb needs an object. */
val DetailUiState.swapLabel: Copy?
    get() = symbol?.let { words(R.string.detail_swap_button, INPUT_SYMBOL, it) }

/**
 * The mono line under the Swap button. Before a quote exists it states the pool the swap would go
 * through, which is the only cost fact this screen holds; the all-in cost joins it once an order
 * has been asked for at the tap (T10). No pool and no quote means no line, never an empty one.
 */
fun DetailUiState.costLine(allInCostPct: Double?): Copy? {
    // Below the liquidity floor the line says "too thin", in the lists' own words and rounding
    // (device QA of 1.3.17: ABBVx on $2 to $3 of depth read "Depth $3 behind this price" under an
    // active Swap, with nothing saying what every list row says about the same pool).
    val thin = tracking as? TrackingQuality.Thin
    if (thin != null) {
        val depth = Fmt.compactMoney(thin.poolUsd, roundDown = true)
        return if (allInCostPct != null) {
            words(R.string.detail_cost_line_thin, Fmt.percent(allInCostPct, signed = false), depth)
        } else {
            words(R.string.list_row_meta_thin, depth)
        }
    }
    val pool = price?.liquidity?.let { Fmt.compactMoney(it) }
    return when {
        allInCostPct != null && pool != null ->
            words(R.string.detail_cost_line, Fmt.percent(allInCostPct, signed = false), pool)

        pool != null -> words(R.string.detail_liquidity_line, pool)
        else -> null
    }
}

/**
 * The swap button steps down to the secondary (outline) style when the pool is below the
 * liquidity floor (device QA of 1.3.18: ABBVx on $2 of depth kept the full amber button). It stays
 * a button and stays tappable: a thin pool is a reason to look twice, not a refusal, and the
 * "too thin" line under it ([costLine]) says why.
 */
val DetailUiState.swapQuiet: Boolean
    get() = tracking is TrackingQuality.Thin

/**
 * True when Jupiter answered about this token and has no reference price for it (device QA of
 * 1.3.17: JEFx and AALx). A missing price is not a missing route (judges' review, 2026-09-27), so
 * the button is not switched off: it reads "Check swap availability" and opens the same machine,
 * whose quote answers whether a route exists ([com.plainticker.mobile.ui.swap.SwapViewModel.checkAvailability]).
 * A quote that failed (Jupiter refused, a network hiccup) is not this: that is transient, and the
 * swap machine asks again and reports its own failure.
 */
val DetailUiState.swapUnpriced: Boolean
    get() = mint != null && quote.isAbsent

/** The one line under the button on an unpriced token, or null: why it asks rather than promises. */
val DetailUiState.swapUnpricedNote: Copy?
    get() = if (swapUnpriced) words(R.string.detail_swap_no_price) else null

/**
 * The swap button's label: "Swap USDC to TSLAx", or "Check swap availability" on a token Jupiter
 * has no reference price for. Null until the catalog names the token.
 */
val DetailUiState.swapButtonLabel: Copy?
    get() = if (swapUnpriced && symbol != null) words(R.string.detail_swap_check_availability) else swapLabel

/**
 * The token this screen swaps, carrying the scaled UI multiplier Detail's own chain read found in
 * force. When the chain was not read the multiplier is null, and the swap machine reads the mint
 * itself before it states any quantity: a split read as unsplit is a balance ten times off.
 */
fun DetailUiState.swapToken(): SwapToken? {
    val mint = mint ?: return null
    val read = chain.valueOrNull
    val multiplier = read?.let { r ->
        BigDecimal.valueOf(r.facts.scaledUiAmount?.let(SplitMultiplier::ofMint)?.effectiveAt(r.readAtMillis) ?: SplitMultiplier.NONE)
    }
    return SwapToken(
        mint = mint,
        symbol = symbol ?: ticker,
        decimals = read?.facts?.decimals ?: MintFacts.XSTOCK_DECIMALS,
        multiplier = multiplier,
    )
}

/**
 * "Swap TSLAx to USDC", the exit from a holding, offered only when the connected wallet's own
 * chain read found some of this token. Never from the app's receipts: a record of what this app
 * once swapped is not a balance anyone can spend.
 */
fun DetailUiState.swapOutLabel(holding: SwapHolding?): Copy? {
    val held = holding?.takeIf { it.canSwapOut && it.token.mint == mint } ?: return null
    return symbol?.let { words(R.string.detail_swap_out_button, it) }?.takeIf { held.raw > 0L }
}

// ---- The read and What to check next (task A6) --------------------------------------------------

/** "The read": [full] is true only when the payload itself said `pro` and carried the full text. */
data class ReadNarrativeBlock(val full: Boolean, val text: Copy)

/** One item of "What to check next": the title everyone sees, the full step text once entitled. */
data class NextStepRow(val title: String, val detail: String?)

data class NextStepsBlock(val full: Boolean, val items: List<NextStepRow>)

/**
 * "The read", or null when there is nothing to show: still loading, this ticker is not served,
 * the route is not turned on by this server, or the call failed. None of those four is a locked
 * door; they simply draw nothing, exactly the way a source [DetailUiState] carried before task A6
 * draws nothing when it has not answered yet.
 *
 * `narrative.excerptEn` is the peek and is populated whenever the underlying analysis exists,
 * whichever way [ReadState.Ready.payload]'s own `pro` reads; `fullEn` is null unless it is. So the
 * excerpt is what a reader with no code, or a code that resolved to nothing entitled, is shown:
 * the server's own honest short form, never a bare door (docs/plan-monetisation-2026-09-19.md,
 * task A6).
 */
/**
 * The year [ReadText] drops from a date in the server's prose ("23 Oct", not "23 Oct 2026"): the
 * reader's current year, read off [DetailUiState.nowMillis] in UTC so the model stays pure.
 */
private val DetailUiState.readYear: Int
    get() = Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneOffset.UTC).year

val DetailUiState.readNarrative: ReadNarrativeBlock?
    get() {
        val payload = (read as? ReadState.Ready)?.payload ?: return null
        val narrative = payload.narrative ?: return null
        val full = payload.pro && narrative.fullEn != null
        val text = if (full) narrative.fullEn else narrative.excerptEn
        return text?.let { ReadNarrativeBlock(full = full, text = raw(ReadText.normalize(it, readYear))) }
    }

/**
 * "What to check next": the three step titles for everyone, the full step text beside each title
 * once entitled. A step past the titles the server sent carries no detail rather than an index
 * error, because the two arrays are not contracted to be the same length.
 */
val DetailUiState.nextStepsBlock: NextStepsBlock?
    get() {
        val payload = (read as? ReadState.Ready)?.payload ?: return null
        val steps = payload.nextSteps ?: return null
        val titles = steps.titlesEn?.takeIf { it.isNotEmpty() } ?: return null
        val full = payload.pro && steps.stepsEn != null
        // What the titles are sentence-cased against: the names this ticker's own prose writes with
        // a capital, so "Jefferies" and "Management's Discussion" keep theirs (ReadText.sentenceCase).
        val evidence = listOfNotNull(
            payload.narrative?.fullEn ?: payload.narrative?.excerptEn,
            steps.stepsEn?.joinToString(" ") { it.body },
        ).joinToString(" ")
        val items = titles.mapIndexed { index, title ->
            NextStepRow(
                title = ReadText.sentenceCase(ReadText.normalize(title, readYear), evidence, listOfNotNull(heroCompany)),
                detail = if (full) steps.stepsEn.getOrNull(index)?.body?.let { ReadText.normalize(it, readYear) } else null,
            )
        }
        return NextStepsBlock(full = full, items = items)
    }

/**
 * The one line that stands where "The read" and "What to check next" would be, drawn only for
 * [ReadState.Failed]. [ReadState.Loading], [ReadState.NotServed] and [ReadState.Disabled] all
 * still draw nothing, the same withholding [readNarrative] and [nextStepsBlock] already keep: a
 * server that has not answered yet, does not cover this ticker, or has not turned the route on is
 * not a fault. A call that reached the server and got back a 200 this app could not read is: that
 * failure used to draw nothing at all, indistinguishable from the three calm states beside it,
 * which is why finding it once took a device walk and a server log rather than a look at the
 * screen. This line is the fix, and it is a notice in Ink2, not a banner (the repo keeps exactly
 * one banner slot on this screen, [DetailBanner], and a failed read is not what it is for).
 */
val DetailUiState.readNotice: Copy?
    get() = if (read is ReadState.Failed) words(R.string.detail_read_unavailable) else null
