package com.myapp.watchlist

import com.myapp.data.plainticker.AnalysisPayload
import com.myapp.data.plainticker.SummaryRow
import com.myapp.data.xstocks.XStockAsset
import com.myapp.repo.CatalogRepository
import com.myapp.repo.PriceRepository
import com.myapp.repo.SummaryRepository

/**
 * The join the Watchlist rests on: watched tickers against `/summary`, the xStocks catalog, one
 * analysis payload each and Jupiter's prices.
 *
 * It is its own class because two callers need exactly the same answer: the screen draws it, and
 * the daily check turns it into the digest. Anything decided here (which ticker is still served,
 * which report date is real, which premium survives the liquidity floor) is therefore decided once.
 *
 * Three rules about failure, since this runs in a worker as often as in a ViewModel:
 *
 * 1. It never throws. A source that did not answer is reported as a flag beside the rows, and the
 *    rows keep everything the other sources did say.
 * 2. The per-ticker analysis is asked for only where `/summary` still carries the ticker. The
 *    detail route answers 404 for anything else, and a watched ticker PlainTicker has dropped is
 *    a state the screen draws rather than an error to log.
 * 3. The watched set is small (this is a watchlist, not the list), so the analyses are fetched one
 *    after another rather than in parallel: PlainTicker rate limits per IP, and a burst of calls
 *    from a background worker is the one shape of traffic that gets an app a 429.
 */
class WatchlistFacts(
    private val summaries: SummaryRepository,
    private val catalog: CatalogRepository,
    private val prices: PriceRepository,
) {

    suspend fun load(tickers: Collection<String>): WatchedFacts {
        val watched = tickers.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (watched.isEmpty()) return WatchedFacts()

        val summary = runCatching { summaries.summary() }
        val assets = runCatching { catalog.catalog() }

        val rows = summary.getOrNull()?.rows.orEmpty().associateBy { it.ticker.uppercase() }
        val byTicker = assets.getOrNull().orEmpty()
            .filter { it.solanaMint != null }
            .associateBy { it.underlyingTicker.uppercase() }

        // One analysis per ticker the leaderboard still serves; a payload that fails costs that
        // row its report date and nothing else.
        val analyses = watched.associateWith { ticker ->
            if (ticker !in rows) null else runCatching { summaries.analysis(ticker) }.getOrNull()
        }

        val mints = watched.mapNotNull { byTicker[it]?.solanaMint }.distinct()
        val fetch = if (mints.isEmpty()) null else prices.pricesFirst(mints)

        return WatchedFacts(
            rows = watched.map { ticker ->
                val mint = byTicker[ticker]?.solanaMint
                val entry = mint?.let { fetch?.priced?.get(it) }
                row(
                    ticker = ticker,
                    summaryRow = rows[ticker],
                    asset = byTicker[ticker],
                    analysis = analyses[ticker],
                    priceUsd = entry?.usdPrice,
                    referencePriceUsd = entry?.stockData?.price,
                    poolUsd = entry?.liquidity,
                )
            },
            analysisUnavailable = summary.isFailure,
            catalogUnavailable = assets.isFailure,
            // A chunk that never came back with nothing to show for it: that is Jupiter refusing,
            // not a set of tokens it has no price for (a mint it answered about without a price
            // is an answer, and the row simply draws no premium).
            pricesUnavailable = fetch != null && fetch.isPartial && fetch.priced.isEmpty(),
        )
    }

    private fun row(
        ticker: String,
        summaryRow: SummaryRow?,
        asset: XStockAsset?,
        analysis: AnalysisPayload?,
        priceUsd: Double?,
        referencePriceUsd: Double?,
        poolUsd: Double?,
    ) = WatchedTicker(
        ticker = ticker,
        symbol = asset?.symbol,
        company = summaryRow?.company ?: analysis?.company ?: asset?.name,
        mint = asset?.solanaMint,
        analyzed = summaryRow != null,
        nextReport = analysis?.nextReportDate(),
        priceUsd = priceUsd,
        referencePriceUsd = referencePriceUsd,
        poolUsd = poolUsd,
    )
}
