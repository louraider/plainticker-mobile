package com.plainticker.mobile.repo

import com.plainticker.mobile.data.plainticker.SummaryResponse
import com.plainticker.mobile.data.plainticker.SummaryRow

/**
 * Which companies PlainTicker covers: the ones with their own full page on plainticker.com, the
 * site's own definition (web `lib/data/covered.ts`: every entry of `SERVED_TICKERS`).
 *
 * **Why not `/summary`'s rows** (final QA of 1.3.19). `/summary` lists the companies with a
 * leaderboard row, 51 of the 57 on 28 Sep 2026; ABBV, CMCSA, V, MA, NKE and BABA have full pages
 * (figures, F-Score, The read) and no row. Taking the rows as the covered set drew ABBVx under
 * "Without analysis" with a Vote button, in Stocks search, in the Health Care chip and on the
 * Vote ballot, while its Detail showed the analysis. The app must never offer a vote for an
 * analysed company, nor call it not analysed.
 *
 * The server's own list ([SummaryResponse.covered]) is used whenever it is sent. Until the web
 * serves it, [WITHOUT_ROW] stands in: the covered companies `/summary` had no row for on 28 Sep
 * 2026. Only those, not the whole list, so a company the site stops covering is never kept
 * covered here by an old list. A row `/summary` does send is always covered, whichever list is
 * in use. [BUNDLED] is the site's whole list that day, with the company and GICS sector each page
 * shows, so a covered company without a row still lands in its own sector.
 */
object Coverage {

    data class CoveredCompany(val ticker: String, val company: String, val sector: String)

    /** plainticker.com's covered companies on 28 Sep 2026 (web `SERVED_TICKERS`, 57). */
    val BUNDLED: List<CoveredCompany> = listOf(
        CoveredCompany("AAPL", "Apple Inc.", "Information Technology"),
        CoveredCompany("MSFT", "Microsoft Corporation", "Information Technology"),
        CoveredCompany("NVDA", "NVIDIA Corporation", "Information Technology"),
        CoveredCompany("GOOGL", "Alphabet Inc.", "Communication Services"),
        CoveredCompany("TSLA", "Tesla, Inc.", "Consumer Discretionary"),
        CoveredCompany("AMZN", "Amazon.com, Inc.", "Consumer Discretionary"),
        CoveredCompany("META", "Meta Platforms, Inc.", "Communication Services"),
        CoveredCompany("AMD", "Advanced Micro Devices, Inc.", "Information Technology"),
        CoveredCompany("INTC", "Intel Corporation", "Information Technology"),
        CoveredCompany("AMAT", "Applied Materials, Inc.", "Information Technology"),
        CoveredCompany("ORCL", "Oracle Corporation", "Information Technology"),
        CoveredCompany("CRM", "Salesforce, Inc.", "Information Technology"),
        CoveredCompany("ADBE", "Adobe Inc.", "Information Technology"),
        CoveredCompany("NFLX", "Netflix, Inc.", "Communication Services"),
        CoveredCompany("CSCO", "Cisco Systems, Inc.", "Information Technology"),
        CoveredCompany("AVGO", "Broadcom Inc.", "Information Technology"),
        CoveredCompany("NOW", "ServiceNow, Inc.", "Information Technology"),
        CoveredCompany("INTU", "Intuit Inc.", "Information Technology"),
        CoveredCompany("IBM", "International Business Machines Corporation", "Information Technology"),
        CoveredCompany("ACN", "Accenture plc", "Information Technology"),
        CoveredCompany("TXN", "Texas Instruments Incorporated", "Information Technology"),
        CoveredCompany("CMCSA", "Comcast Corporation", "Communication Services"),
        CoveredCompany("PM", "Philip Morris International Inc.", "Consumer Staples"),
        CoveredCompany("MO", "Altria Group, Inc.", "Consumer Staples"),
        CoveredCompany("JPM", "JPMorgan Chase & Co.", "Financials"),
        CoveredCompany("V", "Visa Inc.", "Financials"),
        CoveredCompany("MA", "Mastercard Incorporated", "Financials"),
        CoveredCompany("BAC", "Bank of America Corporation", "Financials"),
        CoveredCompany("GS", "The Goldman Sachs Group, Inc.", "Financials"),
        CoveredCompany("UNH", "UnitedHealth Group Incorporated", "Health Care"),
        CoveredCompany("JNJ", "Johnson & Johnson", "Health Care"),
        CoveredCompany("PFE", "Pfizer Inc.", "Health Care"),
        CoveredCompany("ABBV", "AbbVie Inc.", "Health Care"),
        CoveredCompany("MRK", "Merck & Co., Inc.", "Health Care"),
        CoveredCompany("LLY", "Eli Lilly and Company", "Health Care"),
        CoveredCompany("TMO", "Thermo Fisher Scientific Inc.", "Health Care"),
        CoveredCompany("ABT", "Abbott Laboratories", "Health Care"),
        CoveredCompany("WMT", "Walmart Inc.", "Consumer Staples"),
        CoveredCompany("PG", "The Procter & Gamble Company", "Consumer Staples"),
        CoveredCompany("KO", "The Coca-Cola Company", "Consumer Staples"),
        CoveredCompany("PEP", "PepsiCo, Inc.", "Consumer Staples"),
        CoveredCompany("COST", "Costco Wholesale Corporation", "Consumer Staples"),
        CoveredCompany("HD", "The Home Depot, Inc.", "Consumer Discretionary"),
        CoveredCompany("MCD", "McDonald's Corporation", "Consumer Discretionary"),
        CoveredCompany("NKE", "NIKE, Inc.", "Consumer Discretionary"),
        CoveredCompany("DIS", "The Walt Disney Company", "Communication Services"),
        CoveredCompany("T", "AT&T Inc.", "Communication Services"),
        CoveredCompany("VZ", "Verizon Communications Inc.", "Communication Services"),
        CoveredCompany("XOM", "Exxon Mobil Corporation", "Energy"),
        CoveredCompany("BABA", "Alibaba Group Holding Limited", "Consumer Discretionary"),
        CoveredCompany("NVS", "Novartis AG", "Health Care"),
        CoveredCompany("TSM", "Taiwan Semiconductor Manufacturing Company Limited", "Information Technology"),
        CoveredCompany("ASML", "ASML Holding NV", "Information Technology"),
        CoveredCompany("APP", "AppLovin Corporation", "Information Technology"),
        CoveredCompany("NEM", "Newmont Corp.", "Materials"),
        CoveredCompany("PGR", "Progressive Corp.", "Financials"),
        CoveredCompany("JEF", "Jefferies Financial Group Inc.", "Financials"),
    )

    /** The covered companies `/summary` sent no row for on 28 Sep 2026. */
    val WITHOUT_ROW: List<String> = listOf("ABBV", "BABA", "CMCSA", "MA", "NKE", "V")

    private val bundledByTicker: Map<String, CoveredCompany> = BUNDLED.associateBy { it.ticker }

    /** The company and sector a covered ticker's page shows, from [BUNDLED]; null for any other. */
    fun company(ticker: String): CoveredCompany? = bundledByTicker[key(ticker)]

    private fun key(ticker: String): String = ticker.trim().uppercase() // lint-allow uppercase: map key

    /** Every covered ticker, upper case: the server's list or [WITHOUT_ROW], and every row sent. */
    fun tickers(summary: SummaryResponse): Set<String> =
        listed(summary) + summary.rows.map { key(it.ticker) }

    /** The same, from rows alone (the bundled list snapshot carries no `covered`). */
    fun tickers(rows: List<SummaryRow>, covered: List<String>? = null): Set<String> =
        listed(covered) + rows.map { key(it.ticker) }

    /**
     * [SummaryResponse.rows], and a bare row for every covered company it has none for: the
     * company and sector its page shows (from [BUNDLED] when known), no composite, no tone, no
     * age, no report date. Such a row is an analysed one everywhere a row is read, with nothing claimed about a
     * classification it does not have.
     */
    fun rows(summary: SummaryResponse): List<SummaryRow> = rows(summary.rows, summary.covered)

    /**
     * The covered tickers [rows] would add a bare row for. A screen that cannot tell yet whether a
     * ticker has an xStock (its catalog is down) leaves those out rather than list a company it
     * knows nothing else about.
     */
    fun bare(summary: SummaryResponse): Set<String> = bare(summary.rows, summary.covered)

    fun bare(rows: List<SummaryRow>, covered: List<String>? = null): Set<String> {
        val present = rows.map { key(it.ticker) }.toHashSet()
        return listed(covered).filterTo(LinkedHashSet()) { it !in present }
    }

    fun rows(rows: List<SummaryRow>, covered: List<String>? = null): List<SummaryRow> {
        val present = rows.map { key(it.ticker) }.toHashSet()
        val missing = listed(covered).filter { it !in present }
        if (missing.isEmpty()) return rows
        return rows + missing.map { ticker ->
            val known = bundledByTicker[ticker]
            SummaryRow(ticker = ticker, company = known?.company, sector = known?.sector)
        }
    }

    private fun listed(summary: SummaryResponse): Set<String> = listed(summary.covered)

    private fun listed(covered: List<String>?): Set<String> {
        val served = covered?.map(::key)?.filter { it.isNotEmpty() }
        return (served ?: WITHOUT_ROW).toCollection(LinkedHashSet())
    }
}
