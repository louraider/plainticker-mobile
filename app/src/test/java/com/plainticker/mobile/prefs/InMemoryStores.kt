package com.plainticker.mobile.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryOnboardingStore(private var onboarded: Boolean = false) : OnboardingStore {
    var writes = 0
        private set

    override fun isOnboarded(): Boolean = onboarded

    override fun setOnboarded(value: Boolean) {
        writes++
        onboarded = value
    }
}

class InMemoryNotificationPromptStore(private var asked: Boolean = false) : NotificationPromptStore {
    var writes = 0
        private set

    override fun hasAsked(): Boolean = asked

    override fun setAsked() {
        writes++
        asked = true
    }
}

class InMemoryWatchlistStore(initial: Set<String> = emptySet()) : WatchlistStore {
    private val _tickers = MutableStateFlow(initial)
    override val tickers: StateFlow<Set<String>> = _tickers.asStateFlow()

    override fun add(ticker: String) = _tickers.update { it + ticker.trim().uppercase() }

    override fun remove(ticker: String) = _tickers.update { it - ticker.trim().uppercase() }
}

/** A fixed device code, so a test's expected memo hash never depends on [java.security.SecureRandom]. */
class InMemoryDevicePassStore(private val fixedCode: String = "ABCDE12345") : DevicePassStore {
    override fun code(): String = fixedCode
    override fun codeHash(): String = SharedPrefsDevicePassStore.sha256Hex(fixedCode)
}
