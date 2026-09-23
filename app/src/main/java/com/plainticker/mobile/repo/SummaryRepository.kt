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

/**
 * Pass-through, with no cache of its own. [code] is read on every [summary] call and sent as
 * `X-PT-Code`, so the row fields a Pro device is owed (the tone dot among them) arrive on the
 * very next request after it becomes Pro; there is no stored anonymous body to outlive that.
 * Null (the default, and what tests use) asks unauthenticated.
 */
class PlainTickerSummaryRepository(
    private val api: PlainTickerApi,
    private val code: () -> String? = { null },
) : SummaryRepository {
    override suspend fun summary(): SummaryResponse = api.getSummary(code())
    override suspend fun analysis(ticker: String, code: String?): AnalysisPayload = api.getAnalysis(ticker, code)
}
