package com.plainticker.mobile.repo

import kotlinx.coroutines.CancellationException

/**
 * Whether PlainTicker has published research for [ticker]: `GET /api/v1/{ticker}` answers with a
 * payload. The one check every "is the vote winner covered yet" question asks.
 *
 * **Why not `/summary` or the round's status** (device QA of 1.3.17). `/summary` lists classified
 * tickers only, and JEF, round 1's winner, is served with its class unavailable (financials wait
 * for their own sector model), so it never appears there. The round's own status stays `closed`
 * on the live server even once the winner is covered, never `published`. Both made JEF look
 * uncovered: the Vote tab offered no way to its research and a pending auto-watch never resolved.
 * The analysis route itself is the fact.
 *
 * Never throws for a failed read: a 404, a refusal or no network all answer false, which is the
 * safe side (nothing is claimed as published that was not read).
 */
suspend fun SummaryRepository.researchPublished(ticker: String): Boolean {
    val key = ticker.trim()
    if (key.isEmpty()) return false
    return try {
        analysis(key)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        false
    }
}
