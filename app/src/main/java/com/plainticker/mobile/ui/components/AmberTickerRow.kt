package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberType

/**
 * Amber's ticker row (docs/design-research-2026-09-21.md section 5.5): ticker 16/600, company
 * 14/400 secondary, left; figure 18/600 tnum amber and an optional context line, right; 64dp
 * minimum, matching the height every direction's anatomy table gives this row. Flat: the 16dp
 * radius and the ground-coloured 1dp seam between rows belong to the group
 * ([AmberTickerRowGroup]), not to one row, so a single-row block (Today's "Next up") still reads
 * as a rounded tonal container and a five-row block does not draw five separate radii.
 *
 * **The clipping trap this task was warned about, and how this row avoids it by construction**
 * rather than by a pinned budget. [ticker] and [figure] are single line, no wrap: they sit beside
 * a *weighted* sibling ([company]) or alone, never beside a *fixed-width* one, so Compose always
 * grants them their full intrinsic width first and there is nothing for them to clip against
 * ([company] absorbs the squeeze instead, gracefully, via ellipsis). Measured anyway, so this is
 * not a guess: the xStocks catalog's longest symbol on 2026-09-22 is `AUTO.GBx`, 8 characters
 * (`app/src/main/assets/snapshot/xstocks.json`), and the longest company name is 54
 * ("SPDR S&P Oil & Gas Exploration & Production ETF xStock"). [company] and [context] are the two
 * slots a real value can actually run long on, so both wrap or ellipsize instead of clipping:
 * [company] to one line with an ellipsis (a truncated company name is still identifiable; this
 * mirrors [ListRow]'s own proven pattern), [context] to two with an ellipsis backstop past that
 * (mirroring [ListRow]'s meta line, which this task's own brief cites as the fix for the other
 * historical bug: a numeral wraps rather than clips).
 */
@Composable
fun AmberTickerRow(
    ticker: String,
    company: String?,
    modifier: Modifier = Modifier,
    figure: String? = null,
    context: String? = null,
    colors: AmberColors = AmberDarkColors,
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Open $ticker",
    /** What a merged screen reader item says instead of its parts read end to end; see [ListRow]. */
    description: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
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
    val spokenAs = if (description != null) {
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .focusOutline(interactionSource)
            // One step up the surface ladder while pressed, the same tonal move surfaceHigh
            // already means everywhere else (DESIGN.md section 2: "a selected chip, a sheet").
            .background(if (pressed) colors.surfaceHigh else colors.surfaceRaised)
            .then(interaction)
            .then(spokenAs)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ticker,
                style = AmberType.rowTicker,
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            if (company != null) {
                Text(
                    text = company,
                    style = AmberType.rowCompany,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                )
            }
        }
        if (figure != null || context != null) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (figure != null) {
                    Text(
                        text = figure,
                        style = AmberType.figureRow,
                        color = colors.actionText,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.End,
                    )
                }
                if (context != null) {
                    Text(
                        text = context,
                        style = AmberType.context,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/**
 * The 16dp tonal container every direction's anatomy puts a ticker row inside (research 5.5,
 * "rows in a 16dp tonal container, 1dp gap"): [AmberColors.surfaceGround] behind a 1dp
 * [Arrangement.spacedBy] seam, each row's own [AmberColors.surfaceRaised] filling the rest, the
 * whole column clipped to 16dp. DESIGN.md section 8 names the reason this is not the "cards for
 * lists" anti-pattern it resembles: no shadow, no elevation and no border drawn as a frame, which
 * was the actual ban; a shared tonal surface is what Amber's own anatomy calls for instead.
 */
@Composable
fun AmberTickerRowGroup(
    modifier: Modifier = Modifier,
    colors: AmberColors = AmberDarkColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceGround),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

@InstrumentPreviews
@Composable
private fun AmberTickerRowPreview() {
    AmberPreviewCanvas {
        Column {
            AmberTickerRowGroup {
                AmberTickerRow(
                    ticker = "NVDAx",
                    company = "NVIDIA Corporation",
                    figure = "-1.01%",
                    context = "vs NYSE close",
                    onClick = {},
                )
                AmberTickerRow(
                    ticker = "AUTO.GBx",
                    company = "SPDR S&P Oil & Gas Exploration & Production ETF xStock",
                    figure = "+0.09%",
                    context = "vs NYSE close",
                    onClick = {},
                )
            }
            AmberTickerRowGroup {
                AmberTickerRow(
                    ticker = "TSLAx",
                    company = "Tesla xStock",
                    figure = "0.013629",
                    context = "TSLAx, swapped 13 Sep",
                    onClick = {},
                )
            }
        }
    }
}
