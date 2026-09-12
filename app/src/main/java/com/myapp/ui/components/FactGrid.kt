package com.myapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.ui.theme.Canvas
import com.myapp.ui.theme.Caution
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Ink2
import com.myapp.ui.theme.Line
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/** Caution is for an explicit issuer-control risk only (permanent delegate, pausable transfers). */
enum class FactTone { Neutral, Caution }

/**
 * One cell of a [FactGrid]: label 13 Muted, value in mono, an optional sub line in Outfit 13
 * (or mono 12 when it carries numbers). A [span] of 2 takes the whole row.
 */
data class FactCell(
    val label: String,
    val value: String,
    val sub: String? = null,
    val span: Int = 1,
    val subMono: Boolean = false,
    val tone: FactTone = FactTone.Neutral,
    val valueSize: TextUnit = 24.sp,
    /**
     * What tapping the cell does, for a fact that is also a handle: the receipt's signature is
     * copied this way. Null on every cell that is only a fact, which is nearly all of them.
     */
    val onTap: (() -> Unit)? = null,
    /** What a screen reader says the tap does ("Copy the signature"). Required by [onTap]. */
    val tapLabel: String? = null,
)

/**
 * The blueprint grid: two columns, 1dp Line gaps and border, cells on the surface. Exactly as many
 * cells as facts (no facts, no grid); an odd trailing cell leaves an empty half. Each cell speaks
 * one sentence.
 */
@Composable
fun FactGrid(
    cells: List<FactCell>,
    modifier: Modifier = Modifier,
    surface: Color = Canvas,
    minCellHeight: Dp = 96.dp,
) {
    if (cells.isEmpty()) return
    val rows = remember(cells) { packRows(cells) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(Line)
            .border(1.dp, Line)
            .padding(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                row.forEach { cell ->
                    FactCellView(
                        cell = cell,
                        surface = surface,
                        minHeight = minCellHeight,
                        modifier = Modifier.weight(cell.span.coerceIn(1, 2).toFloat()).fillMaxHeight(),
                    )
                }
                if (row.size == 1 && row[0].span < 2) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(surface))
                }
            }
        }
    }
}

/** Two columns: a cell spanning 2 takes a row alone; the others pair up in order. */
fun packRows(cells: List<FactCell>): List<List<FactCell>> {
    val rows = mutableListOf<List<FactCell>>()
    var pending: FactCell? = null
    for (cell in cells) {
        when {
            cell.span >= 2 -> {
                pending?.let { rows += listOf(it) }
                pending = null
                rows += listOf(cell)
            }
            pending == null -> pending = cell
            else -> {
                rows += listOf(pending, cell)
                pending = null
            }
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}

@Composable
private fun FactCellView(
    cell: FactCell,
    surface: Color,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val description = buildString {
        append(cell.label).append(": ").append(spoken(cell.value))
        cell.sub?.let { append(", ").append(spoken(it)) }
    }
    val tap = cell.onTap
    Column(
        modifier = modifier
            .background(surface)
            .then(if (tap == null) Modifier else Modifier.clickable(role = Role.Button, onClick = tap))
            .defaultMinSize(minHeight = minHeight)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)
            // The cell speaks one sentence, so its own text nodes are cleared; a tappable cell
            // has to put its action back, since clearing took the clickable's semantics with it.
            .clearAndSetSemantics {
                contentDescription = description
                if (tap != null) {
                    role = Role.Button
                    onClick(label = cell.tapLabel) { tap(); true }
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = cell.label, style = PlainTickerType.label, color = Muted)
        Text(
            text = cell.value,
            style = PlainTickerType.factValueAt(cell.valueSize),
            color = if (cell.tone == FactTone.Caution) Caution else Ink,
            maxLines = 1,
            softWrap = false,
        )
        cell.sub?.let { sub ->
            Text(
                text = sub,
                style = if (cell.subMono) PlainTickerType.meta else PlainTickerType.small,
                color = Ink2,
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun FactGridPreview() {
    PreviewCanvas {
        FactGrid(
            cells = listOf(
                FactCell("Proof of reserves", "100.7%", "26,101 shares held for 25,924 tokens", span = 2, subMono = true, valueSize = 32.sp),
                FactCell("Permanent delegate", "Yes", "Issuer can move tokens", tone = FactTone.Caution),
                FactCell("Transfers pausable", "Yes", "Not paused now, issuer can pause", tone = FactTone.Caution),
                FactCell("Split multiplier", "1.00", "No pending split"),
                FactCell("Transfer hook", "None", "No transfer hook program"),
            ),
        )
    }
}
