package com.myapp.ui.gallery

import androidx.compose.ui.unit.sp
import com.myapp.ui.components.FactCell
import com.myapp.ui.components.FactTone

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
    const val PREMIUM_TEXT = "+0.09%"

    /** The pool behind the sample quote, the same $1.3M the cost line names. */
    const val POOL_USD = 1_300_000.0
    const val LIVE_META = "slot 445,912,118 · 2 s ago"
    const val TODAY = "Today: 3 watched, next report TSLAx on Oct 22"
    const val COMPOSITE = "composite 0.71"
    const val COST_LINE = "est. all-in cost 0.09% · liquidity \$1.3M"
    const val WALLET = "3kF9…Qm2v"
    const val SIGNATURE = "4xQm…9tHe"
    const val DIGEST_TIME = "Today 08:00"
    const val DIGEST = "3 watched. NVDAx moved from -0.04% to -0.61% against the NYSE close. TSLAx reports in 41 days."
    const val METHOD = "Rule-based classification of fundamentals against the sector. Not a price forecast and not investment advice."
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

    val swapQuote = listOf(
        FactCell("You receive", "0.01364 TSLAx", "1 TSLAx = \$366.17, reference \$365.84", span = 2, subMono = true, valueSize = 28.sp),
        FactCell("All-in cost", "0.09%", "Route Metis", valueSize = 22.sp),
        FactCell("Liquidity", "\$1.3M", "Quote refreshes at tap", valueSize = 22.sp),
    )

    val receipt = listOf(
        FactCell("Paid", "5.00 USDC", valueSize = 22.sp),
        FactCell("All-in cost paid", "0.09%", "quote 0.09%, fill +0.00%", subMono = true, valueSize = 22.sp),
        FactCell("Signature", SIGNATURE, "Tap to copy", span = 2, valueSize = 18.sp),
        FactCell("Slot", "445,912,340", span = 2, valueSize = 18.sp),
    )
}
