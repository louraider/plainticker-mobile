package com.plainticker.mobile.ui.you

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.plainticker.mobile.R
import com.plainticker.mobile.lock.AppLock
import com.plainticker.mobile.lock.AppLockState
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.AmberSectionHead
import com.plainticker.mobile.ui.components.AmberTickerRowGroup
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.focusOutline
import com.plainticker.mobile.lock.LockAvailability
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberType
import kotlinx.coroutines.flow.StateFlow

/** What You's Security row draws, decided from the lock's state alone. */
internal data class LockRow(
    /** The switch can be used: the phone has a fingerprint or a screen lock to confirm with. */
    val offered: Boolean,
    /** The switch's position: the stored setting, which moves only after a prompt succeeded. */
    val on: Boolean,
    /** A prompt is up; the switch waits for it rather than taking a second tap. */
    val busy: Boolean,
    /** Why the last prompt ended without confirming, in the system's words. */
    val message: String?,
)

internal fun lockRow(state: AppLockState): LockRow = LockRow(
    offered = state.availability.canLock,
    on = state.enabled && state.availability.canLock,
    busy = state.authenticating,
    message = state.message?.takeIf { state.availability.canLock },
)

/**
 * You's switch for the app lock: the lock's own state, and the one change You can ask of it. The
 * prompt runs on the lock's process-wide scope, so leaving You mid-prompt does not lose its answer.
 */
class AppLockViewModel(private val lock: AppLock) : ViewModel() {
    val state: StateFlow<AppLockState> = lock.state

    fun setEnabled(on: Boolean) = lock.setEnabled(on)
}

/**
 * You's "Security" group (1.3.28), under Wallet: the optional app lock, off by default. One row,
 * "Lock PlainTicker with fingerprint", and the line that says what it does and what it does not
 * (the Seed Vault still signs every transaction). The whole row is one switch target; the switch
 * shows the stored setting, and a tap asks the phone to confirm its owner first, so the switch moves
 * only once the prompt succeeds, on and off alike. A phone with neither a fingerprint nor a screen
 * lock gets the same row disabled, saying so, rather than a switch that could lock nobody in.
 *
 * **The clipping rule.** The text column is weighted and wraps; the switch beside it is a fixed
 * 52dp control, the one sibling it shares a row with, so neither can starve the other.
 */
@Composable
internal fun SecuritySection(
    lock: AppLockState,
    onSetLock: (Boolean) -> Unit,
    colors: AmberColors,
) {
    val row = lockRow(lock)
    Column(Modifier.fillMaxWidth()) {
        AmberSectionHead(title = stringResource(R.string.you_heading_security), colors = colors)
        AmberTickerRowGroup(colors = colors) {
            LockSwitchRow(row = row, onSetLock = onSetLock, colors = colors)
        }
    }
}

@Composable
private fun LockSwitchRow(row: LockRow, onSetLock: (Boolean) -> Unit, colors: AmberColors) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val enabled = row.offered && !row.busy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusOutline(interactionSource, colors)
            .background(if (pressed) colors.surfaceHigh else colors.surfaceRaised)
            .toggleable(
                value = row.on,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onValueChange = onSetLock,
            )
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.you_lock_label),
                style = AmberType.body,
                color = if (row.offered) colors.textPrimary else colors.textSecondary,
            )
            Text(
                text = stringResource(if (row.offered) R.string.you_lock_note else R.string.you_lock_unavailable),
                style = AmberType.context,
                color = colors.textSecondary,
            )
            row.message?.let { Text(text = it, style = AmberType.context, color = colors.stateCaution) }
        }
        // The row is the target; the switch only shows the setting.
        Switch(
            checked = row.on,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.actionOnFill,
                checkedTrackColor = colors.actionFill,
                checkedBorderColor = colors.actionFill,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceHigh,
                uncheckedBorderColor = colors.textSecondary,
                disabledCheckedThumbColor = colors.actionOnFill.copy(alpha = DisabledAlpha),
                disabledCheckedTrackColor = colors.actionFill.copy(alpha = DisabledAlpha),
                disabledCheckedBorderColor = colors.actionFill.copy(alpha = DisabledAlpha),
                disabledUncheckedThumbColor = colors.border,
                disabledUncheckedTrackColor = colors.surfaceRaised,
                disabledUncheckedBorderColor = colors.border,
            ),
        )
    }
}

private const val DisabledAlpha = 0.38f

// ---- Previews ------------------------------------------------------------------------------

@InstrumentPreviews
@Composable
private fun SecuritySectionPreview() {
    AmberPreviewCanvas {
        Column {
            SecuritySection(lock = AppLockState(availability = LockAvailability.BIOMETRIC, enabled = true), onSetLock = {}, colors = AmberDarkColors)
            SecuritySection(lock = AppLockState(availability = LockAvailability.NONE), onSetLock = {}, colors = AmberDarkColors)
        }
    }
}
