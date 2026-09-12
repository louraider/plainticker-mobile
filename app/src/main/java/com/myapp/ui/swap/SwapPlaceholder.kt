package com.myapp.ui.swap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.myapp.R
import com.myapp.ui.Fmt

/**
 * Placeholder for the swap sheet (design task DT7): the machine's state as text.
 *
 * The composition is the next task and it belongs in this package. This draws only what the state
 * already decided, so that the sheet, when it lands, replaces a rendering and not a rule: every
 * sentence below is a resource the state named, and no number here is computed.
 */
@Composable
fun SwapPlaceholder(
    state: SwapState,
    enabled: Boolean,
    onSwap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val symbol = (state as? SwapState.OnLeg)?.leg?.token?.symbol
        Text(
            stringResource(R.string.swap_button, "USDC", symbol ?: "this xStock"),
            modifier = Modifier.clickable(enabled = enabled && !state.isBusy, onClick = onSwap),
        )
        Text(state.javaClass.simpleName)

        when (state) {
            is SwapState.Amount -> {
                Text(stringResource(R.string.swap_balance, Fmt.tokenAmount(state.balanceRaw, state.leg.input.decimals), state.leg.input.symbol))
                state.input.problem?.let { Text(stringResource(it.text, state.leg.input.symbol, Fmt.count(state.leg.input.decimals))) }
                state.note?.let { Text(stringResource(it.text)) }
            }
            is SwapState.Shortfall -> {
                Text(stringResource(R.string.swap_sol_needed, Fmt.tokenAmount(state.need.totalLamports, LAMPORT_DECIMALS)))
                Text(
                    stringResource(
                        R.string.swap_sol_short,
                        Fmt.tokenAmount(state.missingLamports, LAMPORT_DECIMALS),
                        Fmt.tokenAmount(state.haveLamports, LAMPORT_DECIMALS),
                    )
                )
            }
            is SwapState.AwaitingWallet -> if (state.requote) Text(stringResource(R.string.swap_requote_approval))
            is SwapState.Signed -> Text(stringResource(R.string.swap_debug_banner))
            is SwapState.Landed -> {
                Text(stringResource(R.string.receipt_received))
                Text(Fmt.tokenAmount(state.fill.outAmountRaw, state.leg.output.decimals) + " " + state.leg.output.symbol)
                Text(
                    stringResource(
                        R.string.swap_fill_vs_quote,
                        Fmt.percent(state.quote.allInCostPct, signed = false),
                        Fmt.percent(state.fillDeltaPct),
                    )
                )
                Text(Fmt.shortKey(state.fill.signature))
            }
            is SwapState.Failed -> Text(stringResource(state.reason.text))
            is SwapState.Closed -> state.note?.let { Text(stringResource(it.text)) }
            else -> Unit
        }

        state.quoteOrNull?.let { quote ->
            Text(stringResource(R.string.swap_route, quote.route))
            Text(stringResource(R.string.swap_all_in_cost) + " " + Fmt.percent(quote.allInCostPct, signed = false))
        }
        (state as? SwapState.Running)?.timing?.walletMillis?.let {
            Text(stringResource(R.string.swap_elapsed_wallet, Fmt.plain(it / 1000.0, maxDecimals = 1)))
        }
        if (state is SwapState.Terminal) {
            Text(stringResource(R.string.action_close), modifier = Modifier.clickable(onClick = onReset))
        }
    }
}

/** SOL counts 9 decimals, so a lamport figure reads as SOL through the same formatter. */
private const val LAMPORT_DECIMALS = 9
