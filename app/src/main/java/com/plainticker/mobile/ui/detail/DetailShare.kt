package com.plainticker.mobile.ui.detail

import com.plainticker.mobile.R
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.share.ShareCard
import com.plainticker.mobile.ui.share.ShareCardKind
import com.plainticker.mobile.ui.share.ShareFact
import com.plainticker.mobile.ui.words
import java.time.Instant
import java.time.ZoneOffset

/**
 * What Detail's Share action hands the system share sheet (mock judges' round 2): one factual line off
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

/**
 * The share image for this stock (founder feedback 2026-09-29), the picture the text above rides
 * with: the symbol and the company, then the issuer-control facts the trust card read (the
 * permanent delegate, the reserves xStocks reports, the supply the mint states beside the count
 * xStocks calls in circulation), the classification with its qualifier only when the server sent
 * this reader the word, and the token against the share's US price only above the liquidity floor.
 *
 * The same rule as [shareClauses]: a source that did not answer adds no cell, so the picture never
 * says more than the screen did. A free reader's payload carries [VerdictBlock.Locked] and no
 * word, so there is nothing to leak; the composite and the axes are on no card for anyone.
 */
fun DetailUiState.shareCard(): ShareCard {
    val facts = ArrayList<ShareFact>(6)
    chain.valueOrNull?.facts?.let { mintFacts ->
        val delegate = mintFacts.permanentDelegate
        val active = delegate != null && delegate.active
        facts += ShareFact(
            label = words(R.string.detail_fact_delegate),
            value = words(if (active) R.string.value_yes else R.string.value_none),
            sub = words(if (active) R.string.detail_fact_delegate_sub else R.string.detail_fact_delegate_none_sub),
            caution = active,
        )
    }
    reserves.valueOrNull?.coverage?.let { coverage ->
        facts += ShareFact(
            label = words(R.string.share_card_reserves),
            value = raw(Fmt.percent(coverage * 100.0, signed = false, decimals = 1)),
        )
    }
    chain.valueOrNull?.let { read ->
        facts += ShareFact(label = words(R.string.detail_fact_supply), value = raw(Fmt.decimal(read.supplyShown(), 2)))
    }
    reserves.valueOrNull?.tokensInCirculation?.takeIf { it > 0.0 }?.let { circulating ->
        facts += ShareFact(
            label = words(R.string.share_card_circulating),
            value = raw(Fmt.decimal(java.math.BigDecimal.valueOf(circulating), 2)),
        )
    }
    // Above the floor only: below it the premium is pool arithmetic, and the screen withholds it.
    (tracking as? com.plainticker.mobile.data.jupiter.TrackingQuality.Tracked)?.premiumPct?.let { premium ->
        facts += ShareFact(label = gaugeReference, value = raw(Fmt.percent(premium)))
    }
    (verdictBlock as? VerdictBlock.Unlocked)?.let { verdict ->
        facts += ShareFact(
            label = words(R.string.detail_verdict_label),
            value = verdict.label,
            sub = words(R.string.detail_verdict_qualifier),
        )
    }
    // A cell left alone on its row takes the row, so no half-empty row is drawn.
    if (facts.size % 2 == 1) facts[facts.lastIndex] = facts.last().copy(span = 2)
    val company = heroCompany
    val symbolShown = heroTicker
    val served = analysisState is AnalysisState.Served
    return ShareCard(
        kind = ShareCardKind.Stock,
        eyebrow = if (company != null) raw(symbolShown) else null,
        headline = raw(company ?: symbolShown),
        factsLabel = if (facts.isEmpty()) null else words(R.string.detail_heading_backing),
        facts = facts,
        url = if (served) words(R.string.share_card_stock_url, ticker.trim()) else words(R.string.share_card_home_url),
        footer = nowMillis.takeIf { it > 0L }?.let {
            words(R.string.share_card_stock_footer, Fmt.day(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
        },
    )
}
