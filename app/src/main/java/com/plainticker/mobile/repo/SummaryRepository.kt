package com.plainticker.mobile.repo

import com.plainticker.mobile.data.plainticker.AnalysisPayload
import com.plainticker.mobile.data.plainticker.PlainTickerApi
import com.plainticker.mobile.data.plainticker.SummaryResponse

/** PlainTicker's analysis surface: the leaderboard and one ticker's classification. */
interface SummaryRepository {
    suspend fun summary(): SummaryResponse
    suspend fun analysis(ticker: String): AnalysisPayload
}

/** Pass-through: both routes are edge-cached server-side (5 min), so the app adds no cache of its own yet. */
class PlainTickerSummaryRepository(private val api: PlainTickerApi) : SummaryRepository {
    override suspend fun summary(): SummaryResponse = api.getSummary()
    override suspend fun analysis(ticker: String): AnalysisPayload = api.getAnalysis(ticker)
}
