package com.myapp.ui.onboarding

import com.myapp.prefs.InMemoryOnboardingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun `confirm writes the flag only after the box is checked`() {
        val store = InMemoryOnboardingStore()
        val vm = OnboardingViewModel(store)

        assertFalse(vm.state.value.accepted)
        assertFalse(vm.state.value.completed)
        assertFalse(vm.state.value.canContinue)

        vm.confirm()
        assertFalse(store.isOnboarded())
        assertEquals(0, store.writes)

        vm.setAccepted(true)
        assertTrue(vm.state.value.canContinue)
        vm.confirm()

        assertTrue(store.isOnboarded())
        assertEquals(1, store.writes)
        assertTrue(vm.state.value.completed)
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `an already onboarded user starts completed`() {
        val vm = OnboardingViewModel(InMemoryOnboardingStore(onboarded = true))
        assertTrue(vm.state.value.completed)
        assertFalse(vm.state.value.canContinue)
    }
}
