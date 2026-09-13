package com.plainticker.mobile.ui.onboarding

import androidx.lifecycle.ViewModel
import com.plainticker.mobile.prefs.OnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The two things the screen needs: whether the self-certification box is checked, and whether the
 * button is live. The copy itself lives in strings.xml (DT4), never here.
 */
data class OnboardingUiState(
    /** The self-certification checkbox. */
    val accepted: Boolean = false,
    /** True once the flag is stored; the host navigates on. */
    val completed: Boolean = false,
) {
    /** The button's enabled state: a PrimaryButton while true, a DisabledButton while false. */
    val canContinue: Boolean get() = accepted && !completed
}

/** One screen, one checkbox: the flag is written when the user confirms, and written once. */
class OnboardingViewModel(
    private val store: OnboardingStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(completed = store.isOnboarded()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setAccepted(accepted: Boolean) {
        _state.update { it.copy(accepted = accepted) }
    }

    /**
     * Persists the onboarded flag and flips [OnboardingUiState.completed], which is what moves the
     * host to home. A press while the box is unchecked, and a second press after the first one
     * landed, both write nothing.
     */
    fun confirm() {
        if (!_state.value.canContinue) return
        store.setOnboarded(true)
        _state.update { it.copy(completed = true) }
    }
}
