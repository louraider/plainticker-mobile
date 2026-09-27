package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.words

/**
 * What Detail's Share action hands the system share sheet (judges' round 2): one factual line off
 * the "Backing and controls" facts this screen already read, then the stock's page on
 * plainticker.com (the site itself for a stock the web does not analyse). For example: "AAPLx:
 * issuer can move tokens (permanent delegate), reserves reported at 100.8%.
 * https://www.plainticker.com/en/AAPL".
 *
 * Only facts, and only facts that were read: a mint that did not answer adds no delegate clause
 * and reserves that did not answer add no percentage, so a share can never say more than the
 * screen did. No verdict, no score, no price, nothing that reads as advice.
 */
fun DetailUiState.shareClauses(): List<Copy> {
    val clauses = ArrayList<Copy>(2)
    chain.valueOrNull?.facts?.let { facts ->
        val delegate = facts.permanentDelegate
        clauses += words(
            if (delegate != null && delegate.active) R.string.detail_share_delegate else R.string.detail_share_no_delegate,
        )
    }
    reserves.valueOrNull?.coverage?.let { coverage ->
        clauses += words(R.string.detail_share_reserves, Fmt.percent(coverage * 100.0, signed = false, decimals = 1))
    }
    return clauses
}

/** The whole share text, resolved through [render] (resources in the app, the shipped file in a test). */
fun DetailUiState.shareText(render: (Copy) -> String): String {
    val name = symbol ?: ticker
    val clauses = shareClauses().map(render)
    val line = if (clauses.isEmpty()) {
        render(words(R.string.detail_share_plain, name))
    } else {
        render(words(R.string.detail_share_line, name, clauses.joinToString(", ")))
    }
    // The web serves a page only for a stock it analyses (an unknown ticker is a 404 there), so
    // anything else links the site itself rather than a dead end.
    val url = if (analysisState is AnalysisState.Served) {
        words(R.string.detail_share_url, ticker.trim())
    } else {
        words(R.string.detail_share_home_url)
    }
    return line + " " + render(url)
}
