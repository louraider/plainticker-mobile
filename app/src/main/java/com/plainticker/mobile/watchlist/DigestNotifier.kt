package com.plainticker.mobile.watchlist

/**
 * Where a produced digest goes besides the screen.
 *
 * [enabled] is asked by the Watchlist screen so it can say which of the two surfaces a reader is
 * actually getting, and it is the reason a refused permission is not a broken feature: the digest
 * is written and drawn either way, and the notification is the extra.
 */
interface DigestNotifier {

    /** True when this device would actually show a digest notification. */
    fun enabled(): Boolean

    /** Show one digest. A device that will not show it does nothing, quietly and without throwing. */
    fun post(text: String)

    /** The notifier for a build with nothing to post to, and the null object the previews use. */
    companion object {
        val NONE: DigestNotifier = object : DigestNotifier {
            override fun enabled(): Boolean = false
            override fun post(text: String) = Unit
        }
    }
}
