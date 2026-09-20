package com.plainticker.mobile.repo

import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import com.plainticker.mobile.data.plainticker.SummaryResponse

/** PlainTicker's analysis surface: the leaderboard and one ticker's classification. */
interface SummaryRepository {
    suspend fun summary(): SummaryResponse

    /**
     * [code] is this device's own code, in the clear, or null to ask unauthenticated. It is what
     * turns the verdict block's `locked` off (task app-verdict); every existing caller of this
     * method passes none and is unaffected.
     */
    suspend fun analysis(ticker: String, code: String? = null): AnalysisPayload
}

/** Pass-through: both routes are edge-cached server-side (5 min), so the app adds no cache of its own yet. */
class PlainTickerSummaryRepository(private val api: PlainTickerApi) : SummaryRepository {
    override suspend fun summary(): SummaryResponse = api.getSummary()
    override suspend fun analysis(ticker: String, code: String?): AnalysisPayload = api.getAnalysis(ticker, code)
}
