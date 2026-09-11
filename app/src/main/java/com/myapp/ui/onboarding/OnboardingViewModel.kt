package com.myapp.ui.onboarding

import androidx.lifecycle.ViewModel
import com.myapp.prefs.OnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingUiState(
    val accepted: Boolean = false,
    /** True once the flag is stored; the host navigates on. */
    val completed: Boolean = false,
    val certificationText: String = PLACEHOLDER_CERTIFICATION,
) {
    val canContinue: Boolean get() = accepted && !completed

    companion object {
        /** Placeholder. The final wording is a T7/DT11 deliverable. */
        const val PLACEHOLDER_CERTIFICATION =
            "Self-certification placeholder: the final wording lands with the design pass."
    }
}

/** One screen, one checkbox: the flag is written only when the user confirms. */
class OnboardingViewModel(
    private val store: OnboardingStore,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(completed = store.isOnboarded()))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setAccepted(accepted: Boolean) {
        _state.update { it.copy(accepted = accepted) }
    }

    fun confirm() {
        if (!_state.value.accepted) return
        store.setOnboarded(true)
        _state.update { it.copy(completed = true) }
    }
}
