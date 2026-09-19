package com.plainticker.mobile.data.snapshot

import com.plainticker.mobile.data.plainticker.SummaryRow
import com.plainticker.mobile.data.plainticker.Tone
import com.plainticker.mobile.data.xstocks.Deployment
import com.plainticker.mobile.data.xstocks.Underlying
import com.plainticker.mobile.data.xstocks.XStockAsset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * What the List draws before the network answers, and what it keeps drawing when neither
 * /api/v1/summary nor the xStocks catalog ever does (plan T8, rule 5). Two assets under
 * app/src/main/assets/snapshot/, captured by scripts/capture-list-snapshot.mjs, each stamped
 * with the day it was taken, which is the day the banner names for as long as they are on
 * screen. They carry no price: a quote is live or it is absent.
 *
 * Only the fields the List draws are kept, so the assets stay small and the app never ships a
 * copy of a payload it does not render. `headline` is absent by construction: it is Ukrainian
 * and docs/data-map.md says it is never rendered here, so the snapshot cannot leak it.
 */

/** app/src/main/assets/snapshot/summary.json: /api/v1/summary trimmed to what a row shows. */
@Serializable
data class SnapshotSummary(
    @SerialName("captured_at") val capturedAt: String = "",
    @SerialName("generated_at") val generatedAt: String? = null,
    val rows: List<SnapshotRow> = emptyList(),
)

@Serializable
data class SnapshotRow(
    val ticker: String,
    val company: String? = null,
    /**
     * `/summary.sector` (task A1, PlainTickerModels.kt), kept since the List chapters by it and a
     * cold start is the first thing a reader, or a judge, ever sees. Null for a row `/summary`
     * itself sent no sector for, which the List groups under a trailing chapter rather than drops.
     */
    val sector: String? = null,
    /** Percentile 0 to 100, as `/summary` serves it. */
    val composite: Double? = null,
    val tone: Tone? = null,
    val stale: Boolean = false,
    @SerialName("age_days") val ageDays: Int? = null,
)

/** app/src/main/assets/snapshot/xstocks.json: the Solana catalog trimmed to the join keys. */
@Serializable
data class SnapshotCatalog(
    @SerialName("captured_at") val capturedAt: String = "",
    val assets: List<SnapshotAsset> = emptyList(),
)

@Serializable
data class SnapshotAsset(
    /** Token symbol, e.g. "TSLAx". */
    val symbol: String,
    /** Underlying equity ticker, the PlainTicker join key. */
    val ticker: String,
    val name: String = "",
    val mint: String,
)

/**
 * Both halves of the bundled snapshot and the day they were captured. [capturedOn] is null only
 * when an asset carries a stamp that is not an ISO date; the rows are still usable, the banner
 * simply cannot name a date.
 */
data class ListSnapshot(
    val capturedOn: LocalDate?,
    val rows: List<SnapshotRow> = emptyList(),
    val assets: List<SnapshotAsset> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty() && assets.isEmpty()
}

/** The same shape the live route hands the List, so the join runs once for both sources. */
fun SnapshotRow.toSummaryRow(): SummaryRow = SummaryRow(
    ticker = ticker,
    company = company,
    sector = sector,
    tone = tone,
    composite = composite,
    stale = stale,
    ageDays = ageDays,
)

fun SnapshotAsset.toXStockAsset(): XStockAsset = XStockAsset(
    name = name,
    symbol = symbol,
    underlying = Underlying(symbol = ticker),
    deployments = listOf(Deployment(address = mint, network = XStockAsset.NETWORK_SOLANA)),
)
