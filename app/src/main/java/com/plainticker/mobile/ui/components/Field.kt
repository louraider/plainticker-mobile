package com.plainticker.mobile.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * The only text field: label above (tertiary), the value in mono 36 (numbers) or Outfit 16
 * (words), a 1dp underline that turns to the action colour while focused, a matching caret, one
 * text action right (Max, Clear). The label never doubles as the placeholder; [placeholder] is a
 * tertiary hint in the value slot and disappears on the first character. A tap anywhere on the
 * block (label, value, underline) focuses the input, so the 16sp words variant is a 48dp target
 * without growing past the canvas; the text action keeps its own tap.
 *
 * [colors] defaults to the system-following [defaultAmberColors] rather than Instrument's
 * fixed-dark Ink/Accent/Muted/LineStrong: this is Stocks' own search field and the swap sheet's
 * amount field, both of which stayed dark regardless of the system setting before this fix.
 */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    placeholder: String? = null,
    mono: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    colors: AmberColors = defaultAmberColors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val textStyle = (if (mono) PlainTickerType.fieldValue else PlainTickerType.fieldText)
        .copy(color = colors.textPrimary)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .pointerInput(enabled) {
                // Children (the input, the text action) consume their own taps first.
                if (enabled) detectTapGestures { focusRequester.requestFocus() }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val muted = colors.textTertiary(AmberSurface.GROUND)
        Text(text = label, style = PlainTickerType.label, color = muted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRect(
                        color = if (focused) colors.actionText else colors.border,
                        topLeft = Offset(0f, size.height - stroke),
                        size = Size(size.width, stroke),
                    )
                }
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = label },
                    enabled = enabled,
                    textStyle = textStyle,
                    keyboardOptions = keyboardOptions,
                    singleLine = true,
                    interactionSource = interactionSource,
                    cursorBrush = SolidColor(colors.actionText),
                    decorationBox = { innerField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(text = placeholder, style = textStyle, color = muted, maxLines = 1)
                            }
                            innerField()
                        }
                    },
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = PlainTickerType.monoUnit,
                        color = muted,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            if (action != null && onAction != null) {
                TextAction(
                    label = action,
                    onClick = onAction,
                    color = colors.actionText,
                    contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 0.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@InstrumentPreviews
@Composable
private fun FieldPreview() {
    PreviewCanvas {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            var amount by remember { mutableStateOf("5.00") }
            Field(
                label = "Amount, USDC",
                value = amount,
                onValueChange = { amount = it },
                action = "Max",
                onAction = { amount = "10.00" },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            var query by remember { mutableStateOf("") }
            Field(
                label = "Search",
                value = query,
                onValueChange = { query = it },
                placeholder = "Ticker or company",
                mono = false,
            )
        }
    }
}
