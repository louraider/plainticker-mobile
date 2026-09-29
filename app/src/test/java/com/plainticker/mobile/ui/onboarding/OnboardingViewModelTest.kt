package com.plainticker.mobile.ui.onboarding

import com.plainticker.mobile.prefs.InMemoryOnboardingStore
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
    fun `canContinue follows the box in both directions`() {
        val store = InMemoryOnboardingStore()
        val vm = OnboardingViewModel(store)

        assertFalse(vm.state.value.canContinue)

        vm.setAccepted(true)
        assertTrue(vm.state.value.accepted)
        assertTrue(vm.state.value.canContinue)

        vm.setAccepted(false)
        assertFalse(vm.state.value.accepted)
        assertFalse(vm.state.value.canContinue)

        // Unchecking is not a decision: nothing is stored on the way in or on the way out.
        assertEquals(0, store.writes)
    }

    @Test
    fun `a second press persists nothing`() {
        val store = InMemoryOnboardingStore()
        val vm = OnboardingViewModel(store)

        vm.setAccepted(true)
        vm.confirm()
        vm.confirm()
        vm.setAccepted(true)
        vm.confirm()

        assertEquals(1, store.writes)
        assertTrue(store.isOnboarded())
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun `an already onboarded user starts completed`() {
        val vm = OnboardingViewModel(InMemoryOnboardingStore(onboarded = true))
        assertTrue(vm.state.value.completed)
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `an already onboarded user never writes the flag again`() {
        val store = InMemoryOnboardingStore(onboarded = true)
        val vm = OnboardingViewModel(store)

        vm.setAccepted(true)
        vm.confirm()

        assertEquals(0, store.writes)
        assertTrue(vm.state.value.completed)
    }

    // ---- Step two: pick stocks to watch -------------------------------------------------------

    private fun picking(watchlist: com.plainticker.mobile.prefs.InMemoryWatchlistStore) =
        OnboardingViewModel(InMemoryOnboardingStore(), watchlist = watchlist).apply {
            setAccepted(true)
            confirm()
            continueToPick()
        }

    @Test
    fun `with a watchlist, consent is written, then the controls step, then the pick step, not the exit`() {
        val store = InMemoryOnboardingStore()
        val vm = OnboardingViewModel(store, watchlist = com.plainticker.mobile.prefs.InMemoryWatchlistStore())
        vm.setAccepted(true)
        vm.confirm()
        assertTrue("consent is recorded at once", store.isOnboarded())
        assertEquals(OnboardingStep.CONTROLS, vm.state.value.step)
        assertFalse(vm.state.value.completed)
        vm.continueToPick()
        assertEquals("the controls step writes nothing", 1, store.writes)
        assertEquals(OnboardingStep.PICK, vm.state.value.step)
        assertFalse(vm.state.value.completed)
        assertFalse("nothing picked, nothing to finish with", vm.state.value.canFinish)
    }

    @Test
    fun `finishing watches every pick and hands over to Today`() {
        val watchlist = com.plainticker.mobile.prefs.InMemoryWatchlistStore()
        val vm = picking(watchlist)
        vm.togglePick("TSLA")
        vm.togglePick("NVDA")
        vm.togglePick("NVDA")
        vm.togglePick("META")
        assertTrue(vm.state.value.canFinish)
        vm.finish()
        assertEquals(setOf("TSLA", "META"), watchlist.tickers.value)
        assertTrue(vm.state.value.completed)
        assertEquals("Today, with the picks in Watched", OnboardingExit(null), vm.state.value.exit)
    }

    @Test
    fun `skipping watches nothing and opens the worked example when it is on offer`() {
        val watchlist = com.plainticker.mobile.prefs.InMemoryWatchlistStore()
        val vm = picking(watchlist)
        vm.togglePick("TSLA")
        vm.skip()
        assertTrue(watchlist.tickers.value.isEmpty())
        assertTrue(vm.state.value.completed)
        assertEquals("no suggestions loaded, so no example to open: Today", OnboardingExit(null), vm.state.value.exit)

        val withExample = OnboardingUiState(step = OnboardingStep.PICK, suggestions = listOf(PickChip("TSLA", "TSLAx"), PickChip("AAPL", "AAPLx")))
        assertEquals(PickChip("AAPL", "AAPLx"), withExample.example)
    }

    @Test
    fun `the chips are analysed stocks with a token, familiar names first, then the rest by symbol`() {
        val symbols = mapOf(
            "AAPL" to "AAPLx", "TSLA" to "TSLAx", "JEF" to "JEFx", "ABT" to "ABTx", "ZTS" to "ZTSx", "NOTOKEN" to "",
        )
        val picks = onboardingPicks(listOf("zts", "JEF", "ABT", "TSLA", "AAPL", "UNLISTED"), symbols.filterValues { it.isNotEmpty() })
        assertEquals(listOf("AAPLx", "TSLAx", "JEFx", "ABTx", "ZTSx"), picks.map { it.symbol })
        assertEquals("a ticker with no token is never offered", false, picks.any { it.ticker == "UNLISTED" })
        assertEquals(MAX_PICKS, onboardingPicks((1..20).map { "T$it" }, (1..20).associate { "T$it" to "T${it}x" }).size)
    }

    @Test
    fun `the controls step moves on only from itself`() {
        val vm = OnboardingViewModel(InMemoryOnboardingStore(), watchlist = com.plainticker.mobile.prefs.InMemoryWatchlistStore())
        vm.continueToPick()
        assertEquals("a press before consent does nothing", OnboardingStep.CONSENT, vm.state.value.step)
        vm.setAccepted(true)
        vm.confirm()
        vm.continueToPick()
        vm.continueToPick()
        assertEquals(OnboardingStep.PICK, vm.state.value.step)
        assertFalse(vm.state.value.completed)
    }
}
