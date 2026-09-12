package com.myapp.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Placeholder: promise, self-certification checkbox, one action, laid out between the two system
 * bars. Final copy and the bottom-anchored panel (its button absorbing the navigation inset) are DT11.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.completed) {
        if (state.completed) onDone()
    }

    Column(modifier = modifier.fillMaxSize().systemBarsPadding()) {
        Text("PlainTicker")
        Text("Tokenized stocks, read before you swap.")
        Text(state.certificationText)
        Row {
            Checkbox(checked = state.accepted, onCheckedChange = viewModel::setAccepted)
            Text("I confirm the statement above")
        }
        Text(
            if (state.canContinue) "Read the list" else "Read the list (confirm first)",
            modifier = Modifier.clickable(enabled = state.canContinue) { viewModel.confirm() },
        )
    }
}
