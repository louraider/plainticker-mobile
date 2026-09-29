package com.plainticker.mobile.ui.swap

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.jupiter.SwapOrder
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.refusal
import com.plainticker.mobile.ui.words
import com.plainticker.mobile.wallet.SwapFloor
import com.plainticker.mobile.wallet.TransactionGuard
import java.math.BigDecimal
import java.util.Locale

/**
 * The swap machine's states, task T10 and design task DT7.
 *
 * It is written as a machine rather than as a phase plus a handful of booleans because every
 * interesting rule of this screen is a rule about which state may follow which: a shortfall is
 * refused before the wallet ever opens, an approval that came back with no signature costs
 * nothing, and the one automatic requote is exactly one. A sealed state makes those rules
 * readable, and makes each of them a test that names a transition instead of a string.
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
 *                          Shortfall                                         Review
 *                        (never opened                    continueToWallet() :   : the quote ran
 *                         the wallet)                     (quote still fresh):   : out: Quoting
 *                                                                            v   : (refreshed),
 *                                                                   AwaitingWallet  back to Review
 *                                                                             :   :
 *                                                                             :   : signed
 *                                                                             :   v
 *                                        no signature came back ..............+  Landing
 *                                        (back to Amount, nothing lost:           :   :
 *                                         declined, closed, or the                :   : landed
 *                                         session dropped, and the app            :   v
 *                                         cannot tell the three apart)            :  Landed
 *                                                                                 :
 *                                        SUBMIT_SWAPS false ....................> Signed
 *                                        (debug: never submitted)
 *
 *     From Landing, a -1003, -2003 or -2004 goes back to Quoting(requote = true) exactly once,
 *     and that requote reaches Review(requote = true): a fresh order is fresh bytes and fresh
 *     figures, so it is reviewed and approved again, and the state says so. A second requotable
 *     code, or any other refusal, is Failed and the attempt is over.
 *
 *     Review is the one stop between the checks and the wallet (judges' review, 2026-09-27): the
 *     wallet used to open the moment the guard passed, before the person had seen what the bytes
 *     spend and cost. Nothing is in flight there, and nothing is asked of the wallet until
 *     "Continue to wallet".
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

        /**
         * True when the side being spent sits in a token account its issuer has frozen. A frozen
         * balance is not spendable, so [balanceRaw] already reads zero, and the sheet says why
         * rather than calling the wallet empty.
         */
        val spendFrozen: Boolean get() = leg.input.mint in funds.frozenMints

        val canSubmit: Boolean get() = input.isUsable && !spendFrozen
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
     * asked for. The requirement is what the guard read the bytes can charge: their own fees, and
     * the deposit at its upper bound, so a wallet is never sent to sign what it may not cover.
     */
    data class Shortfall(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val quote: SwapQuote,
        override val timing: SwapTiming,
    ) : Terminal {
        /** What the wallet can be charged: the bytes' own fees and the deposit at its upper bound. */
        val need: SolCost get() = quote.paidSol
        val haveLamports: Long get() = funds.lamports
        val missingLamports: Long get() = quote.paidSol.missingFrom(funds.lamports)
    }

    /**
     * The quote is in hand, the bytes passed [TransactionGuard], the wallet can pay, and nothing
     * has been asked of the wallet yet (judges' review, 2026-09-27). The sheet states what the
     * bytes spend, the least they deliver, the network fees they charge, the most the deposit can
     * be and the all-in cost, and waits for "Continue to wallet".
     *
     * [quotedAtMillis] is when the quote came back, for [SwapViewModel]'s freshness rule: an RFQ
     * quote expires at its `expireAt`, and a Metis order's blockhash stops landing after about a
     * minute, so a quote older than its budget is fetched again before the wallet opens.
     * [refreshed] says this Review follows such a fetch, so the sheet can say the figures are new.
     */
    data class Review(
        override val leg: SwapLeg,
        val funds: SwapFunds,
        val input: AmountInput,
        val quote: SwapQuote,
        val requote: Boolean,
        val quotedAtMillis: Long,
        val timing: SwapTiming,
        val refreshed: Boolean = false,
    ) : OnLeg

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

        /** The route's cost paid, the quote's route cost corrected by the fill; null when either is unknown. */
        val routeCostPaidPct: Double? get() = fill.routeCostPaidPct(quote)

        /**
         * All-in cost paid: the route's cost paid plus the SOL the wallet paid at the SOL price.
         * Null without a SOL price, and the sheet then says "Route cost" rather than claim all-in.
         */
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
        /** Set only with [SwapFailure.GUARD_REFUSED]: the plain reason this phone refused it. */
        val why: TransactionGuard.Why? = null,
        /** Set only with [SwapFailure.SIGNED_MISMATCH]: what the wallet changed, in plain words. */
        val walletChange: Copy.Words? = null,
    ) : Terminal {
        /** The failure's sentence: the guard's plain reason where it refused, else [reason]'s own. */
        val sentence: Copy
            get() = why?.takeIf { reason == SwapFailure.GUARD_REFUSED }?.refusal()
                ?: walletChange?.takeIf { reason == SwapFailure.SIGNED_MISMATCH }
                ?: words(reason.text)
    }

    val isBusy: Boolean get() = this is Running
}

/**
 * The quote a state has in hand, or null before one exists. The one accessor a surface needs to
 * draw the cost cells without matching on every state that carries a quote.
 */
val SwapState.quoteOrNull: SwapQuote?
    get() = when (this) {
        is SwapState.Shortfall -> quote
        is SwapState.Review -> quote
        is SwapState.AwaitingWallet -> quote
        is SwapState.Landing -> quote
        is SwapState.Signed -> quote
        is SwapState.Landed -> quote
        is SwapState.Failed -> quote
        is SwapState.Closed, is SwapState.Opening, is SwapState.Amount, is SwapState.Quoting -> null
    }

// ---- The pair ----------------------------------------------------------------------------------

/**
 * One side of a swap: what the chain calls it, what a person calls it, its base units, and the
 * Token-2022 scaled UI amount multiplier that turns those base units into what a wallet shows.
 *
 * **Raw and UI are different numbers for an xStock.** Every amount on the wire (Jupiter's
 * `amount`, `inAmount`, `outAmount`, the route instruction's u64, a token account's `amount`) is
 * the raw u64. What a wallet displays is raw / 10^decimals x the mint's `scaledUiAmountConfig`
 * multiplier in force now ([com.plainticker.mobile.data.SplitMultiplier.effectiveAt]). NFLXx,
 * read 2026-09-24: stored multiplier 1, a scheduled 10 effective since 2025-11-16, and the node
 * answering `uiAmountString` 27255.7048309 for raw 272557048309, which is raw / 10^8 x 10. So this
 * app sends raw and draws [ui], and the two conversions live here and in [SwapAmount] only.
 */
data class SwapToken(
    val mint: String,
    val symbol: String,
    val decimals: Int,
    /**
     * The scaled UI multiplier in force. One for USDC and for any mint without the extension,
     * which is a fact read off the chain, not a guess. Null while it is not known: [SwapViewModel]
     * reads the mint before the sheet states any quantity of this token, and [ui] refuses to guess.
     */
    val multiplier: BigDecimal? = BigDecimal.ONE,
) {
    /** What a wallet shows for [raw] base units, or null while the multiplier is unknown. */
    fun ui(raw: Long): BigDecimal? =
        multiplier?.let { BigDecimal.valueOf(raw).movePointLeft(decimals).multiply(it) }

    /** True once the multiplier is known, so every quantity of this token can be stated. */
    val scaleKnown: Boolean get() = multiplier != null
}

/**
 * The direction. Flipping is the same machine with the two sides exchanged, which is why this is
 * a pair and not a boolean: nothing downstream needs to know which way round it is.
 */
data class SwapLeg(
    val input: SwapToken,
    val output: SwapToken,
    /**
     * Jupiter answered for the xStock and has no reference price for it (JEFx and AALx on
     * 2026-09-27). A missing price is not a missing route (judges' review, 2026-09-27), so the
     * sheet asks "Check swap availability" and lets the quote answer: an executable order goes to
     * Review with a line saying the value check is limited to the quote itself, and no route says
     * so. Swap to USDC still refuses without a price ([SwapFailure.VALUE_UNCHECKED]): there the
     * price is what checks the base units the app computed itself.
     */
    val unpriced: Boolean = false,
) {

    /** True while USDC is the side being spent. */
    val intoToken: Boolean get() = input.mint == KnownMints.USDC

    /** The xStock side of the pair, whichever way the leg points. */
    val token: SwapToken get() = if (intoToken) output else input

    fun flipped(): SwapLeg = SwapLeg(output, input, unpriced)

    /** The same pair with the xStock side replaced, for a multiplier read after the leg was built. */
    fun withToken(replacement: SwapToken): SwapLeg =
        if (intoToken) copy(output = replacement) else copy(input = replacement)

    companion object {
        val USDC = SwapToken(KnownMints.USDC, "USDC", 6)

        fun into(token: SwapToken, unpriced: Boolean = false): SwapLeg = SwapLeg(USDC, token, unpriced)

        /** The xStock back to USDC: the exit from a holding, "Swap to USDC". */
        fun outOf(token: SwapToken): SwapLeg = SwapLeg(token, USDC)
    }
}

/** What the connected wallet has, read from the chain at [SwapState.Opening]. */
data class SwapFunds(
    val owner: String,
    val lamports: Long,
    val usdcRaw: Long,
    /**
     * The xStock balance in its own base units. Zero when the wallet has no account for it, and
     * zero for an account its issuer has frozen: a frozen balance cannot be spent.
     */
    val tokenRaw: Long,
    /** Mints whose token account in this wallet is frozen, so the sheet can say why it reads zero. */
    val frozenMints: Set<String> = emptySet(),
) {
    fun balanceOf(side: SwapToken): Long = if (side.mint == KnownMints.USDC) usdcRaw else tokenRaw
}

// ---- The amount --------------------------------------------------------------------------------

/**
 * Why an amount cannot be swapped. Each one is refused before any network call is made.
 *
 * No resource id here: [TOO_PRECISE] says how many decimals the token counts, which is counted
 * copy and lives in R.plurals, and one enum cannot carry both kinds of id honestly. The sentence
 * for each is picked in one place, the sheet's own amountNotice.
 */
enum class AmountProblem {
    EMPTY,
    NOT_A_NUMBER,
    TOO_PRECISE,
    NOT_ABOVE_ZERO,
    ABOVE_BALANCE,

    /**
     * Above zero as typed, and still less than one base unit once the token's multiplier is
     * applied. Only a split token (a multiplier above one) can produce it: its smallest step, as
     * a wallet shows it, is larger than 10^-decimals.
     */
    BELOW_ONE_UNIT,
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

    /** The network's own charge: the signature fee and the priority fee, never refunded. */
    val networkFeeLamports: Long get() = signatureFeeLamports + prioritizationFeeLamports

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
     * The route's cost in percent, or null when the order priced neither side in dollars. It is
     * read from inUsdValue against outUsdValue, and an order carrying neither leaves it unknown:
     * zero would read as a swap that cost nothing, which is the one thing this figure must
     * never say by accident. It is not all-in: see [allInCostPct].
     */
    val routeCostPct: Double?,
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
    /** Jupiter's dollar value of what goes in, the base the percentages are taken of. */
    val inUsdValue: Double? = null,
    /**
     * The SOL this wallet itself pays, by payer: on a gasless order the fees whose named payer is
     * someone else (Jupiter's gas payer, the maker) are not the wallet's, and Jupiter takes them
     * back in its fee instead, which the route cost already counts. Null means [solCost].
     */
    val walletSol: SolCost? = null,
    /**
     * USD per SOL from Jupiter's price, read after the quote and before the wallet, or null when
     * it did not come back. Without it the SOL costs cannot be priced and the sheet says "Route
     * cost", never "All-in cost" (judges' review, 2026-09-27).
     */
    val solUsd: Double? = null,
    /**
     * What [TransactionGuard.readSwap] read the bytes can charge this wallet: the signature and
     * priority fees from the message itself and the deposit at its upper bound (security review,
     * 2026-09-27). Null only before the guard has read the bytes; once it has, every SOL figure on
     * the sheet, the SOL check and the receipt are these and never the JSON's declared fields.
     */
    val costs: TransactionGuard.SwapCosts? = null,
    /**
     * The order this quote was read from, kept so the transaction the wallet signs is held to the
     * same order ([TransactionGuard.readSigned]). Null only on a quote not built by [from].
     */
    val order: SwapOrder? = null,
) {
    val hasExpiry: Boolean get() = expireAtEpochSec != null

    /**
     * The SOL the wallet pays: what the guard read from the bytes where it has, else [walletSol]
     * where the order named payers, else every one of [solCost]. The rent in it is an upper
     * bound once the guard has read the bytes: the deposit can be less, never more.
     */
    val paidSol: SolCost
        get() = costs?.let {
            SolCost(
                signatureFeeLamports = it.signatureFeeLamports,
                rentFeeLamports = it.rentUpperBoundLamports,
                prioritizationFeeLamports = it.priorityFeeLamports,
            )
        } ?: walletSol ?: solCost

    private val usablePrice: Double? get() = solUsd?.takeIf { it.isFinite() && it > 0.0 }

    /** [paidSol] in dollars, or null without a SOL price. */
    val solCostUsd: Double? get() = usablePrice?.let { paidSol.totalLamports / LAMPORTS_PER_SOL * it }

    /** The part of [solCostUsd] that is token account rent, which comes back if the account is closed. */
    val rentUsd: Double? get() = usablePrice?.let { paidSol.rentFeeLamports / LAMPORTS_PER_SOL * it }

    /**
     * All-in cost in percent (judges' review, 2026-09-27): the route's cost plus the network fee,
     * the priority fee and the token account rent this wallet pays, priced at [solUsd], as a share
     * of what goes in. Null when any part is unknown, so the sheet never calls a figure all-in
     * that leaves the SOL out.
     */
    val allInCostPct: Double?
        get() {
            val route = routeCostPct ?: return null
            val sol = solCostUsd ?: return null
            val base = inUsdValue?.takeIf { it.isFinite() && it > 0.0 } ?: return null
            return route + sol / base * 100.0
        }

    /** Seconds left, or null when the quote has no expiry. Absence is never "expired". */
    fun secondsLeft(nowEpochSec: Long): Long? = expireAtEpochSec?.let { it - nowEpochSec }

    companion object {
        fun from(order: SwapOrder): SwapQuote = SwapQuote(
            requestId = order.requestId,
            inAmountRaw = order.inAmountRaw,
            outAmountRaw = order.outAmountRaw,
            // One computation for the sheet and for TransactionGuard, which holds the route's
            // own bytes to this same floor before the wallet opens.
            worstCaseOutRaw = SwapFloor.shownRaw(order),
            routeCostPct = order.routeCostPct.takeIf { order.inUsdValue > 0.0 && order.outUsdValue > 0.0 },
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
            inUsdValue = order.inUsdValue.takeIf { it > 0.0 },
            walletSol = walletPaid(order),
            order = order,
        )

        /**
         * The SOL fees this wallet pays. On an order that is not gasless the taker pays all three,
         * which every real one confirms. On a gasless one each fee counts only when its named
         * payer is the taker: the real gasless Metis order names Jupiter's gas payer for all three,
         * and the RFQ order the maker for the fees and the taker for the rent.
         */
        private fun walletPaid(order: SwapOrder): SolCost? {
            if (!order.gasless) return null
            val taker = order.taker ?: return null
            fun paid(lamports: Long, payer: String?) = if (payer == null || payer == taker) lamports else 0L
            return SolCost(
                signatureFeeLamports = paid(order.signatureFeeLamports, order.signatureFeePayer),
                rentFeeLamports = paid(order.rentFeeLamports, order.rentFeePayer),
                prioritizationFeeLamports = paid(order.prioritizationFeeLamports, order.prioritizationFeePayer),
            )
        }

        private const val LAMPORTS_PER_SOL = 1_000_000_000.0

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
     * The route's cost actually paid. The quote priced both sides in dollars; this fill delivered
     * a different quantity of the same token at the same price, so the cost paid is the quoted
     * cost corrected by that ratio. Exact-in means the input is what was asked for, and the
     * executed input is used as well when it is reported.
     */
    fun routeCostPaidPct(quote: SwapQuote): Double? {
        val quoted = quote.routeCostPct ?: return null
        val out = outAmountRaw ?: return null
        if (quote.outAmountRaw <= 0L || quote.inAmountRaw <= 0L || inAmountRaw <= 0L) return quoted
        val outRatio = out.toDouble() / quote.outAmountRaw.toDouble()
        val inRatio = inAmountRaw.toDouble() / quote.inAmountRaw.toDouble()
        if (outRatio <= 0.0 || inRatio <= 0.0) return quoted
        return 100.0 - (100.0 - quoted) * (outRatio / inRatio)
    }

    /**
     * All-in cost of the landed swap: [routeCostPaidPct], which the fill corrects, plus the SOL
     * the quote said the wallet would pay (its fees and the deposit at its upper bound) at the
     * SOL price read for the quote, as a share of what went in. The SOL part is an estimate: the
     * app does not read the landed transaction's fee, so no screen calls it paid. Null without
     * that price.
     */
    fun allInCostPaidPct(quote: SwapQuote): Double? {
        val route = routeCostPaidPct(quote) ?: return null
        val sol = quote.solCostUsd ?: return null
        val base = quote.inUsdValue?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return route + sol / base * 100.0
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
enum class SwapFailure(
    @StringRes val text: Int,
    /** What is certainly true about the money, which picks the result's headline and tone. */
    val outcome: FailureOutcome,
    /** The one way on this failure offers, beside Close. */
    val next: FailureNext,
) {
    /** No wallet that speaks the adapter is installed at all. */
    NO_WALLET(R.string.no_wallet_app, FailureOutcome.NOTHING_SENT, FailureNext.NONE),

    /** The wallet is connected but its lamports or its balances could not be read. */
    CHAIN_UNREAD(R.string.swap_failed_chain_unread, FailureOutcome.NOTHING_SENT, FailureNext.NONE),

    /** GET /order did not answer: a gateway page, a server error, a timeout. */
    QUOTE_UNAVAILABLE(R.string.swap_failed_quote_unavailable, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * GET /order answered 429: the keyless bucket, which Price v3 shares, is empty. Said as busy,
     * because it is, and a few seconds later the same tap is answered (Seeker, 1.3.25: this read
     * as "no route" on "Check swap availability").
     */
    RATE_LIMITED(R.string.swap_failed_rate_limited, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /** GET /order never left the phone: no DNS, no connection. */
    OFFLINE(R.string.swap_failed_offline, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /** GET /order answered with a refusal: no route, an amount out of bounds, a bad mint, funds. */
    QUOTE_REFUSED(R.string.swap_failed_quote_refused, FailureOutcome.NOTHING_SENT, FailureNext.EDIT),

    /** The order came back without a transaction, so there is nothing to approve. */
    NO_TRANSACTION(R.string.swap_failed_no_transaction, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * "Check swap availability" on a token Jupiter has no reference price for, and Jupiter looked
     * for a way to fill it and found none ([com.plainticker.mobile.data.jupiter.SwapError.noRoute]:
     * no market maker, then no aggregator quote). Said as no route right now, which is what it is;
     * a retry may find one. A rate limit, a server error, no network, or a quote that came back
     * without bytes each say their own sentence: none of them is Jupiter finding no route.
     */
    NO_ROUTE(R.string.swap_failed_no_route, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * The order carried a transaction, and [com.plainticker.mobile.wallet.TransactionGuard] read
     * it as something other than the swap on the screen: another mint, another amount, another
     * taker, proceeds routed to an account that is not this wallet's, a program off the list. The
     * wallet was never opened for it.
     */
    GUARD_REFUSED(R.string.swap_failed_guard_refused, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * Swap to USDC reads the xStock's mint and this wallet's balance of it a second time, from a
     * public node PlainTicker does not run ([com.plainticker.mobile.repo.SecondSource]), before
     * any amount of it is quoted. That node did not answer, so the swap is refused: this is a
     * money path, and a check that could not be made is not a check that passed. Viewing the
     * holding is unaffected. Security audit, 2026-09-26.
     */
    SECOND_SOURCE_UNREACHABLE(R.string.swap_failed_second_source_unreachable, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * The second read disagrees with the forwarder's: another multiplier, decimals other than 8,
     * or an account that is not an xStock mint. The base units the sheet would send are computed
     * from exactly those facts, so nothing is quoted and nothing is offered but Close.
     */
    SECOND_SOURCE_MISMATCH(R.string.swap_failed_second_source_mismatch, FailureOutcome.NOTHING_SENT, FailureNext.NONE),

    /**
     * Jupiter's dollar value of the order it built is further than
     * [SwapTrust.VALUE_BOUND] from the typed quantity at the price the app shows. The wallet was
     * never opened for it.
     */
    VALUE_MISMATCH(R.string.swap_failed_value_mismatch, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * The value check could not be made: no price for the token, or an order carrying no dollar
     * value. Refused rather than waved on, because the check exists for the case it cannot see.
     */
    VALUE_UNCHECKED(R.string.swap_failed_value_unchecked, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * The quote answered and would deliver nothing: an estimate or a floor of zero base units.
     * An xStock amount worth less than a millionth of a dollar rounds to no USDC at all. Refused
     * before the wallet, because paying fees to receive nothing is not a swap.
     */
    QUOTE_DUST(R.string.swap_failed_dust, FailureOutcome.NOTHING_SENT, FailureNext.EDIT),

    /**
     * The wallet session went away, or now answers for another account, between reading the
     * balance and signing. The order was built for the first account, so nothing is signed.
     */
    WALLET_CHANGED(R.string.swap_failed_wallet_changed, FailureOutcome.NOTHING_SENT, FailureNext.NONE),

    /**
     * The wallet signed, but what it handed back is not the message
     * [com.plainticker.mobile.wallet.TransactionGuard] read before opening it, or carries no
     * signature from this wallet. It is never sent to /execute, so nothing moved, and a fresh
     * quote is a fresh attempt.
     */
    SIGNED_MISMATCH(R.string.swap_failed_signed_mismatch, FailureOutcome.NOTHING_SENT, FailureNext.RETRY),

    /**
     * The wallet's own capabilities leave out `solana:signTransactions`, optional in MWA 2.x: it
     * only signs by sending, and a swap needs the signed bytes back to check and hand to Jupiter.
     * Nothing was asked of the wallet. A retry cannot change the wallet, so only Close.
     */
    SIGN_ONLY_UNSUPPORTED(R.string.swap_failed_sign_only_unsupported, FailureOutcome.NOTHING_SENT, FailureNext.NONE),

    /**
     * The authorize round-trip failed, so the wallet was never read and nothing was quoted.
     *
     * This is the connect step only. An approval that comes back without a signature is not a
     * failure at all: it is [SwapNote.NOT_APPROVED], and it goes back to the amount step.
     */
    CONNECT_REFUSED(R.string.swap_failed_connect_refused, FailureOutcome.NOT_CONNECTED, FailureNext.CONNECT),

    /**
     * The connect ended with no account: the person backed out of the wallet's Connect sheet,
     * closed the chooser, or came back to the app before the wallet answered (QA of 1.3.19: the
     * sheet used to count "Reading the wallet" past 87 s). The sheet stays, says so, and offers
     * the connect again beside Close.
     */
    NOT_CONNECTED(R.string.swap_failed_not_connected, FailureOutcome.NOT_CONNECTED, FailureNext.CONNECT),

    /**
     * POST /execute did not answer, answered with no structured code, or answered with a code
     * that means Jupiter itself does not know. The transaction was signed and may or may not have
     * been forwarded, so the sentence claims neither, and the sheet offers Portfolio and never a
     * retry: a second attempt over a first one that did land is two swaps.
     */
    SUBMIT_UNAVAILABLE(R.string.swap_failed_submit_unavailable, FailureOutcome.UNKNOWN, FailureNext.PORTFOLIO),

    /** A requotable code came back again after the one automatic requote. */
    QUOTE_GONE(R.string.swap_failed_quote_gone, FailureOutcome.NOT_LANDED, FailureNext.NEW_QUOTE),

    /**
     * /execute says the price moved past the order's slippage bound, so the route reverted on
     * chain. Nothing was swapped; a fresh quote is priced at the new level.
     */
    SLIPPAGE(R.string.swap_failed_slippage, FailureOutcome.NOT_LANDED, FailureNext.NEW_QUOTE),

    /** /execute says the order or its blockhash expired before it landed. Nothing was swapped. */
    QUOTE_EXPIRED(R.string.swap_failed_quote_expired, FailureOutcome.NOT_LANDED, FailureNext.NEW_QUOTE),

    /** Any other refusal from /execute. Nothing was swapped. */
    SWAP_REFUSED(R.string.swap_failed_swap_refused, FailureOutcome.NOT_LANDED, FailureNext.RETRY),
}

/** What a failure can honestly say happened to the money. */
enum class FailureOutcome {
    /** Nothing was signed, or nothing signed was sent. */
    NOTHING_SENT,

    /** It was sent, and Jupiter answered that it did not land. Nothing was swapped. */
    NOT_LANDED,

    /** It was signed and handed on, and no answer says whether it landed. */
    UNKNOWN,

    /** No wallet was connected, so nothing was read, asked or sent. */
    NOT_CONNECTED,
}

/** The one forward action a failure offers. [NONE] leaves Close on its own. */
enum class FailureNext {
    NONE,

    /** The same amount, a fresh quote, from the top. */
    RETRY,

    /** [RETRY], worded for a quote that went: "Get a new quote". */
    NEW_QUOTE,

    /** Back to the amount step with the typed amount intact, to change it. */
    EDIT,

    /** Portfolio, to see whether it landed. Never a retry. */
    PORTFOLIO,

    /** The connect again, from the top of the same leg: "Connect again". */
    CONNECT,
}

/** A neutral word about a round-trip that is not a failure. */
enum class SwapNote(@StringRes val text: Int) {
    /** The authorize round-trip ended without an account. Nothing was read and nothing is owed. */
    CANCELLED_IN_WALLET(R.string.swap_cancelled),

    /**
     * The approval round-trip came back with no signature.
     *
     * The person declined, the sheet went away, or the session dropped: Mobile Wallet Adapter
     * does not tell the three apart, and the device log of 2026-09-13 shows why (docs/data-map.md,
     * "What the first real swap taught us"). The attempt that was not approved and the one that
     * landed both read "Encrypted session established" and then "mobile-wallet-adapter session
     * closed"; the only difference the app can see is whether a signature came back with it.
     *
     * So the sentence claims none of the three. What it can say is what is certainly true:
     * nothing was signed, nothing was sent, and the typed amount is still there.
     */
    NOT_APPROVED(R.string.swap_not_approved),
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
