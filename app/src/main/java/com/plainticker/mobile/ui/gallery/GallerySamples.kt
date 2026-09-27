package com.plainticker.mobile.ui.gallery

import androidx.compose.ui.unit.sp
import com.plainticker.mobile.ui.components.FactCell
import com.plainticker.mobile.ui.components.FactTone
import com.plainticker.mobile.ui.swap.SolCost
import com.plainticker.mobile.ui.swap.SwapFill
import com.plainticker.mobile.ui.swap.SwapFunds
import com.plainticker.mobile.ui.swap.SwapLeg
import com.plainticker.mobile.ui.swap.SwapQuote
import com.plainticker.mobile.ui.swap.SwapTiming
import com.plainticker.mobile.ui.swap.SwapToken

/** One list row on the canvas. */
data class RowSample(
    val ticker: String,
    val company: String,
    val meta: String,
    val value: String? = null,
    val valueSub: String? = null,
)

/** One marker track on the canvas. */
data class TrackSample(val label: String, val value: String, val state: String, val positionPct: Float)

/**
 * Illustrative sample data, the same numbers as design/canvas/instrument.py, so the phone can be
 * held against the artboards. Nothing here is a real signature or wallet.
 */
object GallerySamples {
    const val TICKER = "TSLAx"
    const val COMPANY = "Tesla, Inc."
    const val PRICE = "\$366.17"
    const val REFERENCE = "\$365.84"
    const val PREMIUM_PCT = 0.09

    /** The pool behind the sample quote, the same $1.3M the cost line names. */
    const val POOL_USD = 1_300_000.0
    const val LIVE_META = "slot 445,912,118 · 2 s ago"
    const val TODAY = "Today: 3 watched, next report TSLAx on Oct 22"
    const val COMPOSITE = "composite 0.71"
    const val COST_LINE = "est. all-in cost 0.09% · liquidity \$1.3M"
    const val WALLET = "3kF9…Qm2v"
    const val SIGNATURE = "4xQm…9tHe"

    /** Invented base58, never a landed transaction: the sheet shortens it to a fragment itself. */
    const val SIGNATURE_FULL = "4xQm7gZ1LdPqR8vWnJb3sT6yUeK2cHaX9fNmD5oVtHe"

    /** The public TSLAx mint; the gallery needs a leg and this is the one the artboards draw. */
    const val MINT = "XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB"
    const val DIGEST_TIME = "Today 08:00"
    const val DIGEST = "3 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 41 days."
    const val METHOD = "We classify the company against its sector by a fixed rule. This is not a price forecast or investment advice."
    const val SOURCES = "Filings from SEC EDGAR XBRL. Prices from Jupiter. Reference from the NYSE close."

    val tabs = listOf("List", "Portfolio", "Watchlist")

    val backing = listOf(
        FactCell("Proof of reserves", "100.7%", "26,101 shares held for 25,924 tokens", span = 2, subMono = true, valueSize = 32.sp),
        FactCell("Permanent delegate", "Yes", "Issuer can move tokens", tone = FactTone.Caution),
        FactCell("Transfers pausable", "Yes", "Not paused now, issuer can pause", tone = FactTone.Caution),
        FactCell("Split multiplier", "1.00", "No pending split"),
        FactCell("Transfer hook", "None", "No transfer hook program"),
    )

    val tracks = listOf(
        TrackSample("Quality", "8/9", "strong", 89f),
        TrackSample("Valuation", "51", "fair", 51f),
        TrackSample("Momentum", "0.79", "high", 79f),
    )

    val sector = listOf(
        FactCell("Sector rank", "14 of 62", "Technology hardware, by composite", span = 2, valueSize = 22.sp),
        FactCell("Return on capital", "9.8%", "sector median 6.1%", valueSize = 22.sp),
        FactCell("Gross margin", "17.9%", "sector median 21.4%", valueSize = 22.sp),
        FactCell("Price to earnings", "92.4x", "sector median 27.0x", valueSize = 22.sp),
        FactCell("EV to sales", "11.2x", "sector median 2.4x", valueSize = 22.sp),
        FactCell("52-week position", "0.83", "of the low to high range", valueSize = 22.sp),
        FactCell("Six months vs sector", "+14.2 pt", "relative price change", valueSize = 22.sp),
    )

    val signals = listOf(
        "Return on assets positive" to true,
        "Operating cash flow positive" to true,
        "Return on assets improving" to true,
        "Cash flow exceeds earnings" to true,
        "Leverage falling" to true,
        "Liquidity improving" to false,
        "No new shares issued" to true,
        "Gross margin improving" to true,
        "Asset turnover improving" to true,
    )

    val analyzed = listOf(
        RowSample("TSLAx", "Tesla, Inc.", "+0.09% vs NYSE close · 2 d old", "0.71", "strong"),
        RowSample("NVDAx", "NVIDIA Corp.", "-0.04% vs NYSE close · 1 d old", "0.68", "strong"),
        RowSample("AAPLx", "Apple Inc.", "+0.01% vs NYSE close · 2 d old", "0.61", "fair"),
        RowSample("MSFTx", "Microsoft Corp.", "+0.03% vs NYSE close · 6 d old", "0.58", "fair"),
        RowSample("AMZNx", "Amazon.com, Inc.", "-0.02% vs NYSE close · 2 d old", "0.55", "fair"),
        RowSample("COINx", "Coinbase Global", "+0.08% vs NYSE close · 3 d old", "0.47", "weak"),
    )

    val priceOnly = listOf(
        RowSample("TSMx", "Taiwan Semiconductor", "+0.05% vs NYSE close", "\$264.10"),
        RowSample("ASMLx", "ASML Holding", "-0.01% vs NYSE close", "\$812.40"),
    )

    val watched = listOf(
        RowSample("TSLAx", "Tesla, Inc.", "Reports Oct 22 · +0.09% vs NYSE close"),
        RowSample("AAPLx", "Apple Inc.", "Reports Oct 30 · +0.01% vs NYSE close"),
        RowSample("NVDAx", "NVIDIA Corp.", "Reports Nov 19 · -0.04% vs NYSE close"),
    )

    val holdings = listOf(
        RowSample("TSLAx", "Tesla, Inc.", "2.01364 TSLAx · +0.09% vs NYSE close", "\$737.33"),
        RowSample("NVDAx", "NVIDIA Corp.", "2.1 NVDAx · -0.04% vs NYSE close", "\$366.05"),
        RowSample("AAPLx", "Apple Inc.", "0.8 AAPLx · +0.01% vs NYSE close", "\$185.63"),
    )

    // The swap sheet and the receipt are not sampled here any more: the gallery composes the
    // real SwapSheet over a real SwapState (T10, DT7), so a cell the product drops cannot go on
    // living in a sample. What it composes them from is below.

    /** USDC into TSLAx, the pair every artboard uses. */
    val swapLeg = SwapLeg.into(SwapToken(MINT, TICKER, 8))

    /** The demo wallet as measured on 2026-09-12: 20.2 USDC, 0.096 SOL, no account for the mint. */
    val swapFunds = SwapFunds(owner = MINT, lamports = 96_000_000L, usdcRaw = 20_200_000L, tokenRaw = 0L)

    /** The live order of 2026-09-12, field for field, including the rent for a first account. */
    val swapQuote = SwapQuote(
        requestId = "gallery",
        inAmountRaw = 5_000_000L,
        outAmountRaw = 1_360_437L,
        worstCaseOutRaw = 1_346_933L,
        routeCostPct = 0.586,
        slippageBps = 100,
        route = "Metis",
        swapType = "aggregator",
        gasless = false,
        solCost = SolCost(
            signatureFeeLamports = 5_000L,
            rentFeeLamports = 1_488_440L,
            prioritizationFeeLamports = 1_450L,
        ),
        transaction = "tx",
        expireAtEpochSec = null,
    )

    /** A fill that beat its quote, as one did on 2026-09-10. Not a real signature. */
    val swapFill = SwapFill(
        signature = SIGNATURE_FULL,
        inAmountRaw = 5_000_000L,
        outAmountRaw = 1_360_940L,
        slot = 445_912_340L,
    )

    val swapTiming = SwapTiming(
        startedAtMillis = 0L,
        phaseStartedAtMillis = 0L,
        quotingMillis = 310L,
        walletMillis = 12_700L,
        landingMillis = 3_100L,
    )
}
