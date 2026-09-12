package com.myapp.ui.swap

import androidx.annotation.StringRes
import com.myapp.R
import com.myapp.data.KnownMints
import com.myapp.data.jupiter.SwapOrder
import java.math.BigInteger
import java.util.Locale

/**
 * The swap machine's states, task T10 and design task DT7.
 *
 * It is written as a machine rather than as a phase plus a handful of booleans because every
 * interesting rule of this screen is a rule about which state may follow which: a shortfall is
 * refused before the wallet ever opens, a cancelled approval costs nothing, and the one automatic
 * requote is exactly one. A sealed state makes those rules readable, and makes each of them a
 * test that names a transition instead of a string.
 *
 * The transitions, all of them:
 *
 *     open(token)
 *     Closed ..............> Opening ..............> Amount
 *                              :                       :
 *                              : no wallet, or the     : submit(), amount usable
 *                              : chain unread          v
 *                              :                     Quoting
 *                              :                       :
 *                              v                       : the quote is in hand, and the SOL it
 *                            Failed                    : names is checked HERE, before any
 *                                                      : approval is asked for
 *                                                      :
 *                              +-----------------------+------------------------+
 *                              :                                                :
 *                              : the quote needs more SOL than the wallet has   : enough SOL
 *                              v                                                v
 *                          Shortfall                                      AwaitingWallet
 *                        (never opened                                      :   :   :
 *                         the wallet)                                       :   :   : signed
 *                                                                           :   :   v
 *                                        cancelled in wallet ...............+   :  Landing
 *                                        (back to Amount, nothing lost)         :   :   :
 *                                                                               :   :   : landed
 *                                        the wallet refused ....................+   :   v
 *                                                                                   :  Landed
 *                                        SUBMIT_SWAPS false ......................> Signed
 *                                        (debug: never submitted)
 *
 *     From Landing, a -1003, -2003 or -2004 goes back to Quoting(requote = true) exactly once,
 *     and that requote reaches AwaitingWallet(requote = true): a fresh order is fresh bytes, so
 *     it needs a second approval and the state says so. A second requotable code, or any other
 *     refusal, is Failed and the attempt is over.
 *
 *     Closed <- close(), from any state.
 *     Amount <- edit(), from Shortfall and Failed, with the typed amount intact.
 *
 * Terminal for this attempt means Shortfall, Signed, Landed and Failed: the machine will not
 * leave them on its own.
 */
sealed interface SwapState {

    /** A state that knows which pair it is about. Everything except [Closed]. */
    sealed interface OnLeg : SwapState {
        val leg: SwapLeg
    }

    /** A call is in flight. The sheet shows the phase and its elapsed time, and no action. */
    sealed interface Running : OnLeg {
        val timing: SwapTiming
    }

    /**
     * The attempt is over, one way or another. [timing] is what each phase took, and it is null
     * only on a failure that happened before an attempt began (no wallet, no chain read).
     */
    sealed interface Terminal : OnLeg {
        val timing: SwapTiming?
    }

    /** The sheet is not up. [note] carries a neutral word from the round-trip that closed it. */
    data class Closed(val note: SwapNote? = null) : SwapState

    /** Authorizing the wallet, then reading its lamports and its two balances. */
    data class Opening(
        override val leg: SwapLeg,
        override val timing: SwapTiming,
    ) : Running

    /**
     * The user types an amount. No network call has been made and none will be until submit().
     * [input] carries the text, the base units it converts to, and why it is refused when it is.
     */
    data class Amount(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val note: SwapNote? = null,
    ) : OnLeg {
        val balanceRaw: Long get() = funds.balanceOf(leg.input)

        /** The flip is offered only when the wallet actually has the token to send back. */
        val canFlip: Boolean get() = funds.tokenRaw > 0L

        val canSubmit: Boolean get() = input.isUsable
    }

    /** GET /order, at the tap and never for a preview. [requote] is the one automatic retry. */
    data class Quoting(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val requote: Boolean,
        override val timing: SwapTiming,
    ) : Running

    /**
     * The quote is in hand and the wallet cannot pay for it. Reached before any approval is
     * asked for, and every number in it is the quote's own: no rent constant appears anywhere.
     */
    data class Shortfall(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val quote: SwapQuote,
        override val timing: SwapTiming,
    ) : Terminal {
        val need: SolCost get() = quote.solCost
        val haveLamports: Long get() = funds.lamports
        val missingLamports: Long get() = quote.solCost.missingFrom(funds.lamports)
    }

    /** The wallet is open. [requote] says this is the second approval of the same attempt. */
    data class AwaitingWallet(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val quote: SwapQuote,
        val requote: Boolean,
        override val timing: SwapTiming,
    ) : Running

    /** POST /execute is in flight. This is the call that moves money. */
    data class Landing(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val quote: SwapQuote,
        val requoted: Boolean,
        override val timing: SwapTiming,
    ) : Running

    /** Debug builds stop here: signed, nothing submitted, no money moved, no receipt written. */
    data class Signed(
        override val leg: SwapLeg,
        val quote: SwapQuote,
        val requoted: Boolean,
        override val timing: SwapTiming,
    ) : Terminal

    /** The swap landed. [fill] is what the chain did; [quote] is what was estimated. */
    data class Landed(
        override val leg: SwapLeg,
        val quote: SwapQuote,
        val fill: SwapFill,
        val requoted: Boolean,
        override val timing: SwapTiming,
    ) : Terminal {
        /** The fill against the estimate, signed: positive when the swap beat its quote. Null when the answer did not report the fill. */
        val fillDeltaPct: Double? get() = fill.deltaPctAgainst(quote)

        /** All-in cost actually paid, the quote's cost corrected by the fill; null when either is unknown. */
        val allInCostPaidPct: Double? get() = fill.allInCostPaidPct(quote)
    }

    /**
     * Terminal for this attempt. [reason] is this app's own sentence; the upstream text never
     * reaches here, it goes to the debug log.
     */
    data class Failed(
        override val leg: SwapLeg,
        val funds: SwapFunds?,
        val input: AmountInput?,
        val reason: SwapFailure,
        val quote: SwapQuote? = null,
        val requoted: Boolean = false,
        override val timing: SwapTiming? = null,
    ) : Terminal

    val isBusy: Boolean get() = this is Running
}

/**
 * The quote a state has in hand, or null before one exists. The one accessor a surface needs to
 * draw the cost cells without matching on every state that carries a quote.
 */
val SwapState.quoteOrNull: SwapQuote?
    get() = when (this) {
        is SwapState.Shortfall -> quote
        is SwapState.AwaitingWallet -> quote
        is SwapState.Landing -> quote
        is SwapState.Signed -> quote
        is SwapState.Landed -> quote
        is SwapState.Failed -> quote
        is SwapState.Closed, is SwapState.Opening, is SwapState.Amount, is SwapState.Quoting -> null
    }

// ---- The pair ----------------------------------------------------------------------------------

/** One side of a swap: what the chain calls it, what a person calls it, and its base units. */
data class SwapToken(
    val mint: String,
    val symbol: String,
    val decimals: Int,
)

/**
 * The direction. Flipping is the same machine with the two sides exchanged, which is why this is
 * a pair and not a boolean: nothing downstream needs to know which way round it is.
 */
data class SwapLeg(val input: SwapToken, val output: SwapToken) {

    /** True while USDC is the side being spent. */
    val intoToken: Boolean get() = input.mint == KnownMints.USDC

    /** The xStock side of the pair, whichever way the leg points. */
    val token: SwapToken get() = if (intoToken) output else input

    fun flipped(): SwapLeg = SwapLeg(output, input)

    companion object {
        val USDC = SwapToken(KnownMints.USDC, "USDC", 6)

        fun into(token: SwapToken): SwapLeg = SwapLeg(USDC, token)
    }
}

/** What the connected wallet has, read from the chain at [SwapState.Opening]. */
data class SwapFunds(
    val owner: String,
    val lamports: Long,
    val usdcRaw: Long,
    /** The xStock balance in its own base units. Zero when the wallet has no account for it. */
    val tokenRaw: Long,
) {
    fun balanceOf(side: SwapToken): Long = if (side.mint == KnownMints.USDC) usdcRaw else tokenRaw
}

// ---- The amount --------------------------------------------------------------------------------

/** Why an amount cannot be swapped. Each one is refused before any network call is made. */
enum class AmountProblem(@StringRes val text: Int) {
    EMPTY(R.string.swap_enter_amount),
    NOT_A_NUMBER(R.string.swap_amount_not_a_number),
    TOO_PRECISE(R.string.swap_amount_too_precise),
    NOT_ABOVE_ZERO(R.string.swap_amount_not_above_zero),
    ABOVE_BALANCE(R.string.swap_amount_above_balance),
}

/**
 * What the user typed and what it means. [raw] is base units, converted through the token's
 * decimals with [java.math.BigDecimal] and never through a float: a double cannot represent
 * 20.2 USDC exactly, and the difference lands on the chain.
 */
data class AmountInput(
    val text: String,
    val raw: Long,
    val problem: AmountProblem?,
) {
    val isUsable: Boolean get() = problem == null && raw > 0L

    companion object {
        val EMPTY = AmountInput("", 0L, AmountProblem.EMPTY)
    }
}

// ---- The quote ---------------------------------------------------------------------------------

/**
 * The three SOL costs a Jupiter order names, each already carrying its own payer on the wire.
 *
 * Task T10 said to add the token account rent as the constant 2,039,280 lamports, the
 * rent-exempt minimum of a 165-byte classic token account. The live order on 2026-09-12 charges
 * rentFeeLamports 1,488,440 instead, and the plan is wrong there (docs/data-map.md, "The order,
 * verified live 2026-09-12"). The quote's own field already knows whether the destination account
 * exists and what it costs, so this type reads it and carries no constant of its own.
 */
data class SolCost(
    val signatureFeeLamports: Long,
    val rentFeeLamports: Long,
    val prioritizationFeeLamports: Long,
) {
    val totalLamports: Long get() = signatureFeeLamports + rentFeeLamports + prioritizationFeeLamports

    /** Lamports the wallet is short of [lamports]; zero when it can pay. */
    fun missingFrom(lamports: Long): Long = (totalLamports - lamports).coerceAtLeast(0L)

    fun isCoveredBy(lamports: Long): Boolean = lamports >= totalLamports
}

/**
 * A GET /order answer, reduced to what the sheet draws and what the machine decides on.
 *
 * Nothing here is a default or a constant: the cost, the route, the estimate, the worst case and
 * the SOL are all this quote's own numbers. The same pair measured 0.08 percent and then 1.44
 * percent fifteen minutes apart on 2026-09-10, so they belong on the screen per quote and never
 * in a fixed disclaimer.
 */
data class SwapQuote(
    val requestId: String,
    val inAmountRaw: Long,
    /** The estimate. What actually arrives is a [SwapFill], and it differs. */
    val outAmountRaw: Long,
    /** otherAmountThreshold: the least the swap may deliver before it reverts. */
    val worstCaseOutRaw: Long,
    /**
     * All-in cost in percent, or null when the order priced neither side in dollars. It is read
     * from inUsdValue against outUsdValue, and an order carrying neither leaves it unknown:
     * zero would read as a swap that cost nothing, which is the one thing this figure must
     * never say by accident.
     */
    val allInCostPct: Double?,
    val slippageBps: Int,
    /** The router that quoted it, as a name: "metis" reads "Metis". */
    val route: String,
    val swapType: String,
    val gasless: Boolean,
    val solCost: SolCost,
    /** Base64 of the unsigned transaction. Null when the order carried nothing to sign. */
    val transaction: String?,
    /** RFQ quotes only. A Metis order carries no expireAt at all, so it has no countdown. */
    val expireAtEpochSec: Long?,
) {
    val hasExpiry: Boolean get() = expireAtEpochSec != null

    /** Seconds left, or null when the quote has no expiry. Absence is never "expired". */
    fun secondsLeft(nowEpochSec: Long): Long? = expireAtEpochSec?.let { it - nowEpochSec }

    companion object {
        fun from(order: SwapOrder): SwapQuote = SwapQuote(
            requestId = order.requestId,
            inAmountRaw = order.inAmountRaw,
            outAmountRaw = order.outAmountRaw,
            worstCaseOutRaw = order.otherAmountThreshold?.toLongOrNull()
                ?: slippageFloor(order.outAmountRaw, order.slippageBps),
            allInCostPct = order.allInCostPct.takeIf { order.inUsdValue > 0.0 && order.outUsdValue > 0.0 },
            slippageBps = order.slippageBps,
            route = routeName(order.router),
            swapType = order.swapType,
            gasless = order.gasless,
            solCost = SolCost(
                signatureFeeLamports = order.signatureFeeLamports,
                rentFeeLamports = order.rentFeeLamports,
                prioritizationFeeLamports = order.prioritizationFeeLamports,
            ),
            transaction = order.transaction?.takeIf { order.isSignable },
            expireAtEpochSec = order.expireAt?.takeIf { order.hasExpiry },
        )

        /**
         * The floor when an order names no otherAmountThreshold: the estimate less the slippage
         * the order itself set, which is how an exact-in threshold is computed upstream.
         *
         * Never the estimate. Borrowing it would put "at least {the estimate}" on the screen,
         * which is a promise about money that this quote made no promise about.
         */
        private fun slippageFloor(outAmountRaw: Long, slippageBps: Int): Long {
            val bps = slippageBps.coerceIn(0, 10_000)
            return BigInteger.valueOf(outAmountRaw)
                .multiply(BigInteger.valueOf(10_000L - bps))
                .divide(BigInteger.valueOf(10_000L))
                .toLong()
        }

        /** A router id is a name on the wire and a name on the screen: "metis" reads "Metis". */
        private fun routeName(router: String): String =
            router.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
}

/** What POST /execute reported: the fill, not the estimate. */
data class SwapFill(
    val signature: String,
    val inAmountRaw: Long,
    /**
     * outputAmountResult: what actually arrived, or null when the answer did not report it.
     *
     * It is never the quote's outAmount. A landed swap on 2026-09-10 beat its quote by 0.037
     * percent, so standing the estimate in for the fill would print a quantity nobody received
     * and call it a receipt (docs/data-map.md, the receipts table: "never outAmount").
     */
    val outAmountRaw: Long?,
    val slot: Long?,
) {
    /**
     * The fill against the quote's estimate, in percent and signed: positive when more arrived
     * than was quoted. A landed swap on 2026-09-10 beat its quote by 0.037 percent, so the sheet
     * states this rather than repeating the estimate as though it were the result.
     */
    fun deltaPctAgainst(quote: SwapQuote): Double? {
        val out = outAmountRaw ?: return null
        if (quote.outAmountRaw <= 0L) return null
        return (out - quote.outAmountRaw).toDouble() / quote.outAmountRaw.toDouble() * 100.0
    }

    /**
     * All-in cost actually paid. The quote priced both sides in dollars; this fill delivered a
     * different quantity of the same token at the same price, so the cost paid is the quoted cost
     * corrected by that ratio. Exact-in means the input is what was asked for, and the executed
     * input is used as well when it is reported.
     */
    fun allInCostPaidPct(quote: SwapQuote): Double? {
        val quoted = quote.allInCostPct ?: return null
        val out = outAmountRaw ?: return null
        if (quote.outAmountRaw <= 0L || quote.inAmountRaw <= 0L || inAmountRaw <= 0L) return quoted
        val outRatio = out.toDouble() / quote.outAmountRaw.toDouble()
        val inRatio = inAmountRaw.toDouble() / quote.inAmountRaw.toDouble()
        if (outRatio <= 0.0 || inRatio <= 0.0) return quoted
        return 100.0 - (100.0 - quoted) * (outRatio / inRatio)
    }
}

// ---- What went wrong ---------------------------------------------------------------------------

/**
 * Every way an attempt can end badly, as one of this app's own sentences.
 *
 * An upstream string never reaches a screen. Jupiter's error bodies are written for an integrator
 * ("Failed to decode signed transaction", "transaction not fully signed"), they are not
 * translated, and they name internals a reader cannot act on. The raw text goes to the debug log,
 * where it is useful, and the reader gets the sentence below.
 */
enum class SwapFailure(@StringRes val text: Int) {
    /** No wallet that speaks the adapter is installed at all. */
    NO_WALLET(R.string.swap_failed_no_wallet),

    /** The wallet is connected but its lamports or its balances could not be read. */
    CHAIN_UNREAD(R.string.swap_failed_chain_unread),

    /** GET /order did not answer: no network, a gateway page, a timeout. */
    QUOTE_UNAVAILABLE(R.string.swap_failed_quote_unavailable),

    /** GET /order answered with a refusal: no route, an amount out of bounds, a bad mint. */
    QUOTE_REFUSED(R.string.swap_failed_quote_refused),

    /** The order came back without a transaction, so there is nothing to approve. */
    NO_TRANSACTION(R.string.swap_failed_no_transaction),

    /** The wallet returned an error that was not a cancellation. */
    WALLET_REFUSED(R.string.swap_failed_wallet_refused),

    /** The wallet came back with no signed payload. */
    NOTHING_SIGNED(R.string.swap_failed_nothing_signed),

    /**
     * POST /execute did not answer, or answered with no structured code. The transaction was
     * signed and may or may not have been forwarded, so the sentence claims neither.
     */
    SUBMIT_UNAVAILABLE(R.string.swap_failed_submit_unavailable),

    /** A requotable code came back again after the one automatic requote. */
    QUOTE_GONE(R.string.swap_failed_quote_gone),

    /** Any other refusal from /execute. Nothing was swapped. */
    SWAP_REFUSED(R.string.swap_failed_swap_refused),
}

/** A neutral word about a round-trip that is not a failure. */
enum class SwapNote(@StringRes val text: Int) {
    /** The user closed or declined the wallet. Nothing was lost and nothing is owed. */
    CANCELLED_IN_WALLET(R.string.swap_cancelled),
}

// ---- Time --------------------------------------------------------------------------------------

/**
 * How long each phase of one attempt took, so the sheet can say it rather than guess.
 *
 * The wallet round-trip is the number this product rests on: an RFQ maker reserves part of a
 * quote's life for its own verification, so the usable window is shorter than any expiry
 * suggests. An attempt that requoted spent two quotes and two approvals, and the totals below add
 * both, which is what a person waited through.
 */
data class SwapTiming(
    /** Wall clock when this attempt began, epoch millis. */
    val startedAtMillis: Long,
    /** Wall clock when the current phase began, epoch millis. */
    val phaseStartedAtMillis: Long,
    val quotingMillis: Long? = null,
    val walletMillis: Long? = null,
    val landingMillis: Long? = null,
) {
    /** Elapsed time in the current phase, for the ticking line on a Running state. */
    fun phaseElapsedMillis(nowMillis: Long): Long = (nowMillis - phaseStartedAtMillis).coerceAtLeast(0L)

    fun totalElapsedMillis(nowMillis: Long): Long = (nowMillis - startedAtMillis).coerceAtLeast(0L)

    /** The phases that have finished, added up. Null before any phase has finished. */
    val measuredMillis: Long?
        get() = listOfNotNull(quotingMillis, walletMillis, landingMillis)
            .takeIf { it.isNotEmpty() }
            ?.sum()

    internal fun enterPhase(nowMillis: Long): SwapTiming = copy(phaseStartedAtMillis = nowMillis)

    internal fun closeQuoting(nowMillis: Long): SwapTiming =
        copy(quotingMillis = (quotingMillis ?: 0L) + span(nowMillis), phaseStartedAtMillis = nowMillis)

    internal fun closeWallet(nowMillis: Long): SwapTiming =
        copy(walletMillis = (walletMillis ?: 0L) + span(nowMillis), phaseStartedAtMillis = nowMillis)

    internal fun closeLanding(nowMillis: Long): SwapTiming =
        copy(landingMillis = (landingMillis ?: 0L) + span(nowMillis), phaseStartedAtMillis = nowMillis)

    private fun span(nowMillis: Long): Long = (nowMillis - phaseStartedAtMillis).coerceAtLeast(0L)

    companion object {
        fun started(nowMillis: Long): SwapTiming = SwapTiming(nowMillis, nowMillis)
    }
}
