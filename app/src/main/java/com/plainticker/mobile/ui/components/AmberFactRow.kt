package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType
import com.plainticker.mobile.ui.theme.JetBrainsMono

/**
 * One fact as a row: a label on the left, its value on the right, and an optional sub line under
 * both. The swap sheet's cost block and its receipt draw through this (2026-09-24), in place of
 * [FactGrid]'s bordered two-column cells, which were Instrument's anatomy and the last place a
 * money figure on the sheet was still set in monospace.
 *
 * @param value set in [AmberType.figureRow] (Bricolage, `tnum`), the face every other figure in
 *   the app uses; [identifier] switches it to JetBrains Mono for an on-chain key, the one thing
 *   DESIGN.md section 3 keeps the mono face for (a signature fragment, never a quantity).
 * @param subNumeric true when the sub line is a sentence built around numerals ("At least 985.884
 *   USDC after slippage"), so it is set in [AmberType.figureInline] rather than the words face.
 * @param onTap makes the whole row one tap target (the receipt's signature, which copies itself),
 *   with a role and [tapLabel] for a screen reader and a 56dp minimum height.
 */
data class AmberFact(
    val label: String,
    val value: String,
    val sub: String? = null,
    val identifier: Boolean = false,
    val subNumeric: Boolean = false,
    val onTap: (() -> Unit)? = null,
    val tapLabel: String? = null,
)

/**
 * A run of [AmberFact]s in [AmberTickerRowGroup]'s own 16dp tonal container: the same seam, the
 * same light-only edge, the same grouped look every list of rows in the app already has, so the
 * receipt reads like the rest of the app and not like a form.
 *
 * **The clipping rule, measured, not assumed** (DESIGN.md section 4, fontTools against
 * `res/font/bricolage_grotesque.ttf` at [AmberType.figureRow]'s exact 18sp / `wght` 600 / `opsz`
 * 18 with `tnum`, 2026-09-24; `SwapResultFitTest` pins the arithmetic). On a 400dp frame the sheet's
 * 20dp side inset and the row's own 16dp padding leave **328dp** of content width. [AmberFact.value]
 * is unweighted and never wraps, so it measures first and owns up to all 328dp; the label beside it
 * is weighted and wraps onto as many lines as it needs, so it can be squeezed but never clipped.
 * The widest real value the sheet can produce, "123.456789 AUTO.GBx" (the catalog's longest symbol
 * behind a six-decimal quantity), is 194.184dp at 1.0x and 252.439dp at 1.3x: 133.816dp and
 * 75.561dp of margin against 328dp. The signature fragment in JetBrains Mono 16sp is 124.8dp
 * (162.24dp at 1.3x). The sub line is a full-width line of its own and wraps.
 */
@Composable
fun AmberFactRows(
    facts: List<AmberFact>,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    AmberTickerRowGroup(modifier = modifier, colors = colors) {
        facts.forEach { AmberFactRow(it, colors) }
    }
}

@Composable
private fun AmberFactRow(fact: AmberFact, colors: AmberColors) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tap = fact.onTap
    val interaction = if (tap != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = fact.tapLabel,
            role = Role.Button,
            onClick = tap,
        )
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusOutline(interactionSource)
            .background(if (pressed) colors.surfaceHigh else colors.surfaceRaised)
            .then(interaction)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Weighted and wrapping: the label gives way, the figure never does.
            Text(
                text = fact.label,
                style = AmberType.context,
                color = colors.textSecondary,
                modifier = Modifier.alignByBaseline().weight(1f),
            )
            Text(
                text = fact.value,
                style = if (fact.identifier) IdentifierStyle else AmberType.figureRow,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
        fact.sub?.let {
            Text(
                text = it,
                style = if (fact.subNumeric) AmberType.figureInline else AmberType.context,
                color = colors.textTertiary(AmberSurface.RAISED),
            )
        }
    }
}

/** An on-chain key: JetBrains Mono 16sp, the one role DESIGN.md section 3 keeps the face for. */
private val IdentifierStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
)

@InstrumentPreviews
@Composable
private fun AmberFactRowsPreview() {
    AmberPreviewCanvas {
        AmberFactRows(
            facts = listOf(
                AmberFact(label = "Paid", value = "0.999 USDC"),
                AmberFact(label = "All-in cost paid", value = "0.17%", sub = "quote 0.24%, fill -0.02%", subNumeric = true),
                AmberFact(label = "Signature", value = "4TmGDA…jwksZo", sub = "Tap to copy", identifier = true, onTap = {}),
                AmberFact(label = "Slot", value = "450,068,679"),
            ),
        )
    }
}
