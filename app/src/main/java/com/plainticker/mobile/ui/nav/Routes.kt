package com.plainticker.mobile.ui.nav

/** Navigation-Compose routes. `gallery` is registered in debug builds only. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ARG_TICKER = "ticker"
    const val ARG_TAB = "tab"
    const val DETAIL = "detail/{$ARG_TICKER}"
    const val GALLERY = "gallery"

    /** The daily digest under You: the last digest, when it landed, and the notifications setting. */
    const val DIGEST = "digest"

    /**
     * Home with the tab to open on, as an optional query argument so that plain [HOME] still
     * matches it and the start destination is unchanged. The receipt's "View in Portfolio" is
     * the one caller: it names a tab because the holding it just created is on that tab.
     */
    const val HOME_TAB = "$HOME?$ARG_TAB={$ARG_TAB}"

    fun home(tab: Int): String = "$HOME?$ARG_TAB=$tab"

    /**
     * A flag left on the home entry's `SavedStateHandle` (never in a route, so nothing about it is
     * ever a URL): Detail's "Have a code? Get Pro" sets it and pops back, and home answers by
     * opening You with the promo code field open and focused, then clears it.
     */
    const val KEY_OPEN_PROMO = "open_promo"

    /** The detail route for one underlying ticker, e.g. "detail/AAPL". */
    fun detail(ticker: String): String = "detail/${ticker.trim().uppercase()}" // lint-allow uppercase: route key

    /**
     * Where the app opens. Onboarding is shown once (DT11): once the flag is stored the graph
     * starts at [HOME] and the onboarding screen is never composed again.
     */
    fun start(onboarded: Boolean): String = if (onboarded) HOME else ONBOARDING
}
