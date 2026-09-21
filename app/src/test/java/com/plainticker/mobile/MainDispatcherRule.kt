package com.plainticker.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * viewModelScope runs on Dispatchers.Main. Pointing Main at a test dispatcher lets runTest
 * drive it with virtual time; runTest picks up this dispatcher's scheduler automatically.
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // A ViewModel constructed directly by a test (every test here) has no
        // ViewModelStoreOwner to call the (internal) ViewModel.clear() and cancel its
        // viewModelScope, so a collector started in `init` can still be scheduled on this
        // dispatcher after the test body returns. runTest's own cleanup only drains work
        // that is structurally a child of the test's coroutine; viewModelScope is an
        // independent root, so it is not covered by that. Draining this scheduler here,
        // before Main is swapped back, runs that leftover work to its next suspension point
        // while it is still safely confined to this thread, rather than leaving it to be
        // dispatched later - possibly while the next test's `starting()` is mutating
        // Dispatchers.Main on the JUnit thread, which is exactly the race that used to throw
        // "Dispatchers.Main is used concurrently with setting it" (see PassViewModelTest).
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
