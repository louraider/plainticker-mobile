package com.plainticker.mobile.ui.list

/**
 * How well a search [query] matches one row, lower first, or null when it does not match at all
 * (QA of 1.3.22: "MA" listed TSMx, GSx, IBMx and AMZNx, whose names hold "ma", above MAx itself,
 * and "V" buried Vx the same way).
 *
 * - [SEARCH_EXACT]: the ticker or the token symbol is the query ("MA" is MA, "MAx" is MAx).
 * - [SEARCH_PREFIX]: the ticker or the symbol starts with it ("MA" finds MARA, "MAx").
 * - [SEARCH_NAME]: anything else holding it: the company name, or the query inside a ticker.
 *
 * Case never matters and the query is trimmed. A caller sorts stably by the tier, so rows inside
 * one tier keep the order they had.
 */
fun searchTier(query: String, ticker: String, symbol: String?, company: String?): Int? {
    val q = query.trim()
    if (q.isEmpty()) return SEARCH_NAME
    val codes = listOfNotNull(ticker, symbol?.takeIf { it.isNotBlank() })
    return when {
        codes.any { it.equals(q, ignoreCase = true) } -> SEARCH_EXACT
        codes.any { it.startsWith(q, ignoreCase = true) } -> SEARCH_PREFIX
        codes.any { it.contains(q, ignoreCase = true) } -> SEARCH_NAME
        company?.contains(q, ignoreCase = true) == true -> SEARCH_NAME
        else -> null
    }
}

/**
 * The one flat list the Stocks search draws: [analyzed] and [withoutAnalysis] (each already
 * narrowed and ranked by [ListViewModel.search]) in one order, best tier first, so an exact ticker
 * among the uncovered tokens is not drawn under every analyzed company whose name merely holds the
 * query. Within a tier an analyzed row keeps its place ahead of a price-only one.
 */
fun searchResults(analyzed: List<ListRow>, withoutAnalysis: List<ListRow>, query: String): List<ListRow> {
    val all = analyzed + withoutAnalysis
    if (query.isBlank()) return all
    return all.sortedBy { searchTier(query, it.ticker, it.symbol, it.company) ?: SEARCH_NAME }
}

const val SEARCH_EXACT = 0
const val SEARCH_PREFIX = 1
const val SEARCH_NAME = 2
