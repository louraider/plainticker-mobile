package com.plainticker.mobile.ui.nav

/** Navigation-Compose routes. `spike` and `gallery` are registered in debug builds only. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ARG_TICKER = "ticker"
    const val ARG_TAB = "tab"
    const val DETAIL = "detail/{$ARG_TICKER}"
    const val SPIKE = "spike"
    const val GALLERY = "gallery"

    /**
     * Home with the tab to open on, as an optional query argument so that plain [HOME] still
     * matches it and the start destination is unchanged. The receipt's "View in Portfolio" is
     * the one caller: it names a tab because the holding it just created is on that tab.
     */
    const val HOME_TAB = "$HOME?$ARG_TAB={$ARG_TAB}"

    fun home(tab: Int): String = "$HOME?$ARG_TAB=$tab"

    /** The detail route for one underlying ticker, e.g. "detail/AAPL". */
    fun detail(ticker: String): String = "detail/${ticker.trim().uppercase()}" // lint-allow uppercase: route key

    /**
     * Where the app opens. Onboarding is shown once (DT11): once the flag is stored the graph
     * starts at [HOME] and the onboarding screen is never composed again.
     */
    fun start(onboarded: Boolean): String = if (onboarded) HOME else ONBOARDING
}
