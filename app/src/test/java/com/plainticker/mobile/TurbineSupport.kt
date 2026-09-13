package com.plainticker.mobile

import app.cash.turbine.ReceiveTurbine

/**
 * Skips intermediate StateFlow values until one matches. StateFlow conflates fast
 * successive updates, so ViewModel tests assert on the state they wait for rather than on
 * an exact sequence of transient ones.
 */
suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
