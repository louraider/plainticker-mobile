package com.myapp.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapp.ui.theme.Elevated
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Line
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * A 64dp row: ticker mono 18 and company 13 left with a mono 12 meta line, a mono 18 value and
 * a 13 sub word right, an optional trailing text action, one Line divider below. One focusable
 * item for a screen reader (descendants merged) with the click labelled "Open TSLAx"; the
 * trailing action stays its own target. [muted] is the price-only row: everything in Muted.
 */
@Composable
fun ListRow(
    ticker: String,
    company: String?,
    modifier: Modifier = Modifier,
    meta: String? = null,
    valueRight: String? = null,
    valueSub: String? = null,
    trailingAction: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    muted: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Open $ticker",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tickerColor = if (muted) Muted else Ink
    val companyColor = if (muted) Muted else Ink2
    val interaction = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusOutline(interactionSource)
                .background(if (pressed) Elevated else Color.Transparent)
                .then(interaction)
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = ticker,
                        style = PlainTickerType.listTicker,
                        color = tickerColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (company != null) {
                        Text(
                            text = company,
                            style = PlainTickerType.small,
                            color = companyColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                        )
                    }
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = PlainTickerType.meta,
                        color = Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (valueRight != null) {
                        Text(
                            text = valueRight,
                            style = if (muted) PlainTickerType.listValueMuted else PlainTickerType.listTicker,
                            color = tickerColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    if (valueSub != null) {
                        Text(
                            text = valueSub,
                            style = PlainTickerType.small,
                            color = Muted,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                if (trailingAction != null && onTrailingAction != null) {
                    TextAction(
                        label = trailingAction,
                        onClick = onTrailingAction,
                        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 0.dp, bottom = 10.dp),
                    )
                }
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = Line)
    }
}

@InstrumentPreviews
@Composable
private fun ListRowPreview() {
    PreviewCanvas {
        Column {
            ListRow(
                ticker = "TSLAx",
                company = "Tesla, Inc.",
                meta = "+0.09% vs NYSE close · 2 d old",
                valueRight = "0.71",
                valueSub = "strong",
                onClick = {},
            )
            ListRow(
                ticker = "AAPLx",
                company = "Apple Inc.",
                meta = "Reports Oct 30 · +0.01% vs NYSE close",
                trailingAction = "Unwatch",
                onTrailingAction = {},
                onClick = {},
            )
            ListRow(
                ticker = "ASMLx",
                company = "ASML Holding",
                meta = "-0.01% vs NYSE close",
                valueRight = "\$812.40",
                muted = true,
                divider = false,
                onClick = {},
            )
        }
    }
}
