package com.myapp.ui.swap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Placeholder for the swap sheet (T10/DT7): the phase and the quote as text. */
@Composable
fun SwapPlaceholder(
    state: SwapUiState,
    enabled: Boolean,
    onSwap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "Swap USDC to ${state.outputSymbol ?: "this xStock"}",
            modifier = Modifier.clickable(enabled = enabled && !state.isBusy, onClick = onSwap),
        )
        Text("phase ${state.phase}")
        state.order?.let { order ->
            Text("in ${order.inAmountRaw} raw USDC  out ${order.outAmountRaw} raw  all-in cost ${order.allInCostPct}%")
            Text("router ${order.router}  type ${order.swapType}  gasless ${order.gasless}")
        }
        state.message?.let { Text(it) }
        state.signature?.let { Text("signature $it") }
        if (state.needsFreshOrder) Text("Quote gone: a fresh quote and a second approval are needed")
        if (state.phase != SwapPhase.IDLE && !state.isBusy) {
            Text("Reset", modifier = Modifier.clickable(onClick = onReset))
        }
    }
}
