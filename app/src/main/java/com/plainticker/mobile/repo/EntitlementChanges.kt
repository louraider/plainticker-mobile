package com.plainticker.mobile.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update

/**
 * Process wide: something that can change what this device is entitled to has happened, so every
 * screen that draws Pro numbers reads its source again.
 *
 * Fresh-device QA of 1.3.23 (27 against 28): a promo code redeemed on You unlocked Detail, but
 * Stocks kept every "Pro" badge until the app was restarted, because the List read `/summary`
 * once per load and nothing told it the answer had changed. `/summary`, the analysis and the read
 * are entitlement-aware on the server and keep no cache here, so re-reading is all a screen needs.
 *
 * Two ways in. [changed] is an event that may have changed the entitlement: a promo redeemed, a
 * pass confirmed, a Google sign-in or sign-out, a new device code. [observed] is a fresh
 * entitlement read (on You, and on every wallet change, which is where a stake read lands): an
 * answer that differs from the last one known is a change too, so Pro that arrives through a
 * stake, or lapses, reaches every screen without a restart. The first answer ever read is not a
 * change: every screen's own first read already reflected it.
 */
class EntitlementChanges {
    private val _version = MutableStateFlow(0L)

    /** Moves once per change; screens collect [changes] rather than this. */
    val version: StateFlow<Long> = _version.asStateFlow()

    private val _pro = MutableStateFlow<Boolean?>(null)

    /** The last entitlement read in this process, or null when none has been, or one is owed. */
    val pro: StateFlow<Boolean?> = _pro.asStateFlow()

    /**
     * Something may have changed the entitlement. [pro] is what it is now, when the event itself
     * says (a redeemed code is Pro); null leaves it to the next read, which then cannot count the
     * same change twice.
     */
    fun changed(pro: Boolean? = null) {
        synchronized(this) { _pro.value = pro }
        _version.update { it + 1 }
    }

    /** A fresh entitlement read. An answer different from the last one known is a change. */
    fun observed(pro: Boolean) {
        val flipped = synchronized(this) {
            val before = _pro.value
            _pro.value = pro
            before != null && before != pro
        }
        if (flipped) _version.update { it + 1 }
    }

    /** Every change from now on, never the state already known when collection starts. */
    val changes get() = version.drop(1)
}
