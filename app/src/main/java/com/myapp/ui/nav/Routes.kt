package com.myapp.ui.nav

/** Navigation-Compose routes. `spike` is registered in debug builds only. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ARG_TICKER = "ticker"
    const val DETAIL = "detail/{$ARG_TICKER}"
    const val SPIKE = "spike"

    /** The detail route for one underlying ticker, e.g. "detail/AAPL". */
    fun detail(ticker: String): String = "detail/${ticker.trim().uppercase()}"
}
