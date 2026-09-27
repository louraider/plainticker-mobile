package com.plainticker.mobile.ui.portfolio

import com.plainticker.mobile.R
import com.plainticker.mobile.data.KnownMints
import com.plainticker.mobile.data.jupiter.TrackingQuality
import com.plainticker.mobile.data.receipts.SwapReceipt
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.counted
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.swap.SwapToken
import com.plainticker.mobile.ui.words
import java.math.BigDecimal

/**
 * What the Portfolio screen says, decided away from the composition (task T11, design task DT8).
 *
 * The same split the Detail screen keeps: a model picks the sentence, a composable places it. It
 * is worth the file here for three reasons, each of which is a rule a later edit could quietly
 * undo and a unit test can pin:
 *
 * 1. **The liquidity floor is the list's, not a second copy of it.** A holding's tracking half
 *    comes from [TrackingQuality] and reads with the same words the list row uses, out of the same
 *    string resources, so the two screens cannot disagree about a $34 pool.
 * 2. **A missing number is a sentence, never a zero.** A mint that was not read has no quantity
 *    and no value, a position Jupiter cannot price keeps its quantity and shows no value, and the
 *    total says how many positions it covers.
 * 3. **Nothing here computes a cost basis or a change.** There is no arithmetic in this file that
 *    the chain did not carry: the shares come from the mint, the value from Jupiter, and the only
 *    sum is over the positions that have one.
 * 4. **The app's own record is never dressed as a chain read.** [recordedHoldings] folds this
 *    device's receipts into what the app put in the wallet, and that is all it claims: no price
 *    is applied to it, it is not summed into a total, and the screen states above it that the
 *    wallet has not been read.
 */

// ---- The pieces the screen draws ---------------------------------------------------------------

/** The big number under the Holdings heading and the sentence that says what it covers. */
data class TotalBlock(
    /** The total of the valued positions, or null when none of them could be valued. */
    val value: String?,
    /** How many of the positions on screen that total covers. */
    val sub: Copy,
)

/** One holding, in the parts a 64dp row draws and a screen reader speaks in order. */
data class HoldingRow(
    /** Underlying equity ticker: the Detail route, never drawn. */
    val ticker: String,
    /** The token symbol, drawn left in mono. */
    val symbol: String,
    val company: String,
    /** The first half of the meta line: the shares held, or why there is no quantity. */
    val quantity: Copy,
    /** The second half: the premium, the pool sentence below the floor, or nothing at all. */
    val tracking: Copy?,
    /** The value right, or null for a position that could not be valued. */
    val value: String?,
)

/**
 * One xStock this app's own receipts say it swapped into, with nothing read from any chain.
 *
 * This exists because the wallet session does not survive process death, so a cold open finds no
 * account and the chain is never asked. What the app still knows is what it did: it wrote a
 * receipt the moment each swap landed, and those receipts net out to a quantity. That quantity is
 * the app's record, not the wallet's balance, and the two can differ the moment anything moves
 * the token elsewhere, so the screen says which one it is drawing before it draws it.
 *
 * Two things are deliberately absent. There is no value, because no price was read and a quantity
 * multiplied by a live quote would be half a chain read wearing the other half's clothes. There is
 * no [TrackingQuality] either, for the same reason: a premium is a statement about a pool this
 * screen has not looked at.
 */
data class RecordedHolding(
    val mint: String,
    /** The token symbol as the receipt recorded it, e.g. "TSLAx". */
    val symbol: String,
    /** Underlying equity ticker, once the catalog has named it; null leaves the row unopenable. */
    val ticker: String? = null,
    /** The company behind the token, once the catalog has named it. */
    val company: String? = null,
    /** Net base units over every receipt naming this mint. Positive by construction. */
    val amountRaw: Long,
    /** The decimals those base units are counted in, as the receipt recorded them. */
    val decimals: Int,
    /** When the newest swap touching this mint landed. */
    val landedAtMillis: Long,
    /**
     * The Token-2022 scaled UI multiplier the newest receipt naming this mint was drawn with when
     * it landed. A scale factor, not a price: what the stored figure meant on that day.
     */
    val recordedMultiplier: Double = 1.0,
    /**
     * The multiplier in force now, read off the mint, or null while it has not been read (a cold
     * open offline, a mint that did not answer). Also a scale factor, not a price. With it, this
     * row reads the same quantity the wallet, the chain-read holding and "Swap to USDC" read.
     */
    val multiplier: Double? = null,
) {
    /**
     * The quantity to draw: raw / 10^decimals x the multiplier in force now, the one rule every
     * xStock quantity in the app follows. Without a current multiplier it is the stored figure,
     * scaled as it was when the swap landed, and [scaleRead] is false so the row says so.
     */
    val quantity: java.math.BigDecimal
        get() = BigDecimal.valueOf(amountRaw).movePointLeft(decimals)
            .multiply(BigDecimal.valueOf(usable(multiplier ?: recordedMultiplier)))

    /** True when [quantity] uses the multiplier in force now, false for the stored figure. */
    val scaleRead: Boolean get() = multiplier != null
}

private fun usable(multiplier: Double): Double = if (multiplier.isFinite() && multiplier > 0.0) multiplier else 1.0

/** One recorded holding in the parts a 64dp row draws. */
data class RecordedRow(
    /** The Detail route, null when the catalog has not named the token. */
    val ticker: String?,
    val symbol: String,
    val company: String?,
    /** The quantity, drawn right in mono: the one number on the row and the app's own. */
    val quantity: String,
    val meta: Copy,
)

/** One landed swap out of the app's own record: what was paid, what came back, the cost and when. */
data class SwapRow(
    /** The identity of the row, which is the identity of the landing. */
    val signature: String,
    val paid: Copy,
    val received: Copy,
    val cost: Copy,
    val landed: Copy,
)

// ---- The rules ----------------------------------------------------------------------------------

/**
 * The total and its sentence. Only called with positions on screen: an empty wallet is its own
 * state and says so in words rather than drawing a total of zero.
 */
fun totalBlock(state: PortfolioUiState): TotalBlock {
    val held = state.positions.size
    val valued = state.valuedCount
    return TotalBlock(
        value = state.totalUsd?.let(Fmt::price),
        // The noun is the one the count governs, which is how many positions are on screen: the
        // partial sentence names the valued ones first and still agrees with the total it covers.
        sub = when {
            valued == held -> counted(R.plurals.portfolio_priced_by, held, Fmt.count(held))
            valued == 0 -> counted(R.plurals.portfolio_priced_none, held, Fmt.count(held))
            else -> counted(R.plurals.portfolio_priced_partial, held, Fmt.count(valued), Fmt.count(held))
        },
    )
}

/**
 * One holding as a row. The quantity is the mint's answer or the statement that there is none;
 * the tracking half is withheld along with it, because a premium beside "quantity unknown" invites
 * a reader to multiply two numbers, one of which this app does not have.
 */
fun holdingRow(position: PortfolioPosition): HoldingRow = HoldingRow(
    ticker = position.ticker,
    symbol = position.symbol,
    company = position.company,
    quantity = position.quantity
        ?.let { words(R.string.portfolio_row_quantity, Fmt.tokenAmount(it), position.symbol) }
        ?: words(R.string.portfolio_row_mint_unread),
    tracking = if (position.mintRead) trackingCopy(position.tracking) else null,
    value = position.valueUsd?.let(Fmt::price),
)

/**
 * The quote's half of the meta line, in the list's own words (DESIGN.md section 1.1): the signed
 * premium above the liquidity floor, the pool below it, and the plain statement that Jupiter
 * reported no depth. Null when Jupiter did not price the token at all, which the banner explains.
 */
private fun trackingCopy(quality: TrackingQuality?): Copy? = when (quality) {
    is TrackingQuality.Tracked ->
        quality.premiumPct?.let { words(R.string.list_row_meta_premium, Fmt.percent(it)) }

    is TrackingQuality.Thin ->
        words(R.string.list_row_meta_thin, Fmt.compactMoney(quality.poolUsd, roundDown = true))

    TrackingQuality.Untracked -> words(R.string.list_row_meta_pool_unknown)

    null -> null
}

/**
 * One receipt as a row.
 *
 * Every number is the app's own record of the landing and is drawn the same way whether or not
 * anything has since looked the signature up: this screen makes no chain read for these rows and
 * therefore claims no confirmation. An output the execute answer did not report stays unreported
 * here, rather than being filled in from the quote it was estimated at.
 */
fun swapRow(receipt: SwapReceipt): SwapRow = SwapRow(
    signature = receipt.signature,
    // What the wallet showed on each side: raw scaled by the multiplier recorded at the landing,
    // so a split xStock reads as its wallet reads, not ten times smaller.
    paid = words(
        R.string.portfolio_row_quantity,
        Fmt.tokenAmount(receipt.inputUi()),
        receipt.inputSymbol,
    ),
    received = receipt.outputUi()
        ?.let {
            words(
                R.string.portfolio_swap_row_received,
                Fmt.tokenAmount(it),
                receipt.outputSymbol,
            )
        }
        ?: words(R.string.portfolio_swap_row_received_unknown, receipt.outputSymbol),
    // "all-in" only when the receipt priced the SOL it paid (judges' review, 2026-09-27); a
    // receipt written before that, or without a SOL price, has the route's cost and says so.
    cost = receipt.allInCostPct
        ?.let { words(R.string.portfolio_swap_cost, Fmt.percent(it, signed = false)) }
        ?: receipt.routeCostPct?.let { words(R.string.portfolio_swap_route_cost, Fmt.percent(it, signed = false)) }
        ?: words(R.string.portfolio_swap_cost_unknown),
    landed = raw(Fmt.utc(receipt.landedAtMillis)),
)

/**
 * What this device's receipts say the app put in the wallet, netted per mint.
 *
 * Both sides of every swap count: an output adds and an input subtracts, so a token swapped back
 * out leaves no row rather than a row the wallet has not held since. A mint that nets to zero or
 * below is not a holding.
 *
 * **USDC is never one of these rows (QA 2026-09-26, D2), even though "Swap to USDC" can now land it
 * on the output side of a receipt.** This list is [RecordedHolding]'s own claim, "one xStock this
 * app's own receipts say it swapped into": USDC is the cash the receipts are denominated in, never
 * an xStock, and drawing it through the same row as TSLAx or METAx would state things about it that
 * are only true of an xStock. [RecordedRow]'s meta line says a quantity is "quantity not rescaled"
 * when the multiplier at landing is stale; USDC has no Token-2022 scaled-UI split to be stale about,
 * so that sentence would be describing a risk USDC does not carry. The proceeds of a
 * "Swap to USDC" are not hidden: the swap itself already states them once, honestly, on the swap row
 * under "Recent swaps" ([swapRow]'s own `received`), the one place this file's own doc already names
 * as owning a disclosure so it cannot drift from a second copy of it here.
 *
 * A receipt whose fill was never reported ([SwapReceipt.outputAmountRaw] null) adds nothing. That
 * understates rather than invents, which is the only direction this file is allowed to be wrong
 * in, and the swap itself is still on the screen under "Recent swaps" saying its amount was not
 * reported. The disclosure is owned there, once: the same sentence in two places is how the two
 * drift apart.
 *
 * Pure over the receipts. The catalog names the rows afterwards, so this answers with or without
 * a network.
 */
fun recordedHoldings(receipts: List<SwapReceipt>): List<RecordedHolding> {
    val net = LinkedHashMap<String, RecordedHolding>(receipts.size)
    fun fold(mint: String, symbol: String, decimals: Int, delta: Long, landedAtMillis: Long, multiplier: Double) {
        val seen = net[mint]
        net[mint] = seen?.copy(
            amountRaw = seen.amountRaw + delta,
            landedAtMillis = maxOf(seen.landedAtMillis, landedAtMillis),
            recordedMultiplier = if (landedAtMillis >= seen.landedAtMillis) multiplier else seen.recordedMultiplier,
        ) ?: RecordedHolding(
            mint = mint,
            symbol = symbol,
            amountRaw = delta,
            decimals = decimals,
            landedAtMillis = landedAtMillis,
            recordedMultiplier = multiplier,
        )
    }
    for (receipt in receipts) {
        receipt.outputAmountRaw?.let {
            fold(receipt.outputMint, receipt.outputSymbol, receipt.outputDecimals, it, receipt.landedAtMillis, receipt.outputMultiplier)
        }
        fold(
            receipt.inputMint,
            receipt.inputSymbol,
            receipt.inputDecimals,
            -receipt.inputAmountRaw,
            receipt.landedAtMillis,
            receipt.inputMultiplier,
        )
    }
    return net.values.filter { it.amountRaw > 0L && it.mint != KnownMints.USDC }.sortedBy { it.symbol }
}

/**
 * One recorded holding as a row. The quantity is drawn where a position draws its dollar value,
 * because on this screen in this state it is the only number there is and the symbol beside it
 * says what it counts. The meta is the provenance: when the swap behind it landed.
 *
 * **QA 2026-09-26, D2.** [R.string.portfolio_recorded_meta_unscaled] used to read "Swapped %1$s ·
 * as recorded, current split not read": the full [Fmt.utc] stamp already runs to 21 characters, and
 * that clause added 36 more, past what [AmberTickerRow]'s `context` slot (`maxLines = 2`) can fit
 * at font scale 1.3 beside this row's own `figure`, which clipped it mid-word on the device
 * ("…as recorded, c…"). The section's own lede (`portfolio_recorded_lede`) already states once,
 * above every row, that these are the app's own record and not a chain read, so repeating "as
 * recorded" on each row was saying the same thing twice; shortened to "quantity not rescaled," the
 * one fact this clause actually needs to add. fontTools 4.63 against
 * `res/font/bricolage_grotesque.ttf`, 2026-09-26, `context`'s 14sp/400 instance: the shortened
 * string is 340.830dp at 1.0x and 443.079dp at 1.3x, against a two-line capacity of 524.060dp /
 * 484.478dp beside this row's own widest realistic figure (`AmberTickerRow`'s own "38,406.2,"
 * 65.970dp / 85.761dp, [AmberTickerRowTest]'s "widest realistic figure this row draws across every
 * screen"), so it wraps to a real second line rather than clipping. `PortfolioModelTest` pins the
 * string and the arithmetic.
 */
fun recordedRow(holding: RecordedHolding): RecordedRow = RecordedRow(
    ticker = holding.ticker,
    symbol = holding.symbol,
    company = holding.company,
    quantity = Fmt.tokenAmount(holding.quantity),
    // Never a silently different number: a figure not scaled by today's multiplier says so.
    meta = if (holding.scaleRead) {
        words(R.string.portfolio_recorded_meta, Fmt.utc(holding.landedAtMillis))
    } else {
        words(R.string.portfolio_recorded_meta_unscaled, Fmt.utc(holding.landedAtMillis))
    },
)

/**
 * The token a holding row's "Swap to USDC" opens the sheet for, or null when the row must not
 * offer it. Offered only for a position the chain read in this session found with a balance and a
 * readable mint: the mint gives the decimals and the multiplier the sheet states quantities in, and
 * a position with neither has no quantity to swap. The app's own record never offers it, because
 * it was not read from any wallet.
 */
fun swapOutToken(position: PortfolioPosition, state: PortfolioUiState): SwapToken? {
    if (!state.connected || position.amountRaw <= 0L) return null
    val decimals = position.decimals ?: return null
    val multiplier = position.multiplier ?: return null
    return SwapToken(
        mint = position.mint,
        symbol = position.symbol,
        decimals = decimals,
        multiplier = BigDecimal.valueOf(multiplier),
    )
}

/** What the one banner slot says. The order of the tiers is [PortfolioUiState.banner]'s. */
fun bannerText(banner: PortfolioBanner): Copy = when (banner) {
    PortfolioBanner.ChainUnavailable -> words(R.string.portfolio_error_unavailable)
    PortfolioBanner.CatalogUnavailable -> words(R.string.list_catalog_unavailable)
    PortfolioBanner.PricesUnavailable -> words(R.string.list_prices_unavailable)
    PortfolioBanner.PricesPartial -> words(R.string.list_prices_partial)
    is PortfolioBanner.Wallet -> words(banner.note.text)
}

/**
 * Whether the banner offers a way to ask again. A wallet note carries none: the action that
 * answers it is the connect action already standing where the holdings would be.
 */
fun bannerRetries(banner: PortfolioBanner): Boolean = banner !is PortfolioBanner.Wallet
