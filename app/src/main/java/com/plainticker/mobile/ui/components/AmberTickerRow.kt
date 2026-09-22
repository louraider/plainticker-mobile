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
 * **The clipping trap this task was warned about a second time, on the real device, and what was
 * actually wrong with the first fix.** The previous doc comment here argued that [ticker] and
 * [figure] were safe because they "sit beside a weighted sibling," and stopped there. That is true
 * of [ticker] beside [company] inside the *name* group, but it was the wrong pair to worry about:
 * the device drew `META x  Meta Pl…` because of the *outer* pair, name group versus meta group.
 * [Row] measures a plain, unweighted child before it divides whatever is left among weighted ones,
 * regardless of source order, and the meta [Column] ([figure] plus [context]) was that unweighted
 * child. [context] is not the short, bounded content [ListRow]'s own right-hand column carries (a
 * numeral and a state word); it is a full clause ("$2.7k behind, too thin · 2 d old", 32
 * characters, `list_row_meta_join`), so an unweighted meta column claims however much single-line
 * width that clause wants, up to the *entire* row, before the weighted name group sees a budget at
 * all — a fixed-width sibling in every way that matters except that the number was computed from
 * content instead of typed as a literal `.width(Xdp)`, which is exactly why the test that only
 * grepped for `.width(` did not catch it.
 *
 * **The anatomy, decided rather than assumed.** [ticker] and [figure] still never wrap: both are
 * short, bounded content (an 8-character symbol at most, a formatted figure) with nothing beside
 * them that can squeeze them once the fix below holds, so softWrap = false stays deliberate. The
 * identity ([ticker], [company]) outranks the annotation ([figure], [context]): a reader identifies
 * the row by its name, and a squeezed meta line still means something wrapped onto a second line,
 * while a squeezed name is the report this task exists to fix. So *both* groups now carry a weight
 * ([NameWeight] to [MetaWeight], 3 to 2) instead of one being weighted and the other left to claim
 * whatever it wants: each is bounded to its own share of the row no matter what the other one
 * contains, the name's share is the larger one, and within its own bounded share [context] falls
 * back to exactly the wrap-then-ellipsis behaviour it already had ([ListRow]'s meta line uses the
 * same fallback) instead of never needing it because nothing ever constrained it. A name and its
 * meta stay one row rather than two: research 5.5's Amber column draws the ticker row as a single
 * 64dp line, and a bounded split is enough to fix the overlap without leaving that anatomy.
 *
 * Measured anyway, so none of this is a guess: the xStocks catalog's longest symbol on 2026-09-22
 * is `AUTO.GBx`, 8 characters (`app/src/main/assets/snapshot/xstocks.json`), and the longest
 * company name is 54 ("SPDR S&P Oil & Gas Exploration & Production ETF xStock"). [company] and
 * [context] are the two slots a real value can actually run long on, so both wrap or ellipsize
 * instead of clipping: [company] to one line with an ellipsis (a truncated company name is still
 * identifiable; this mirrors [ListRow]'s own proven pattern), [context] to two with an ellipsis
 * backstop past that (mirroring [ListRow]'s meta line, which this task's own brief cites as the fix
 * for the other historical bug: a numeral wraps rather than clips).
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
            modifier = Modifier.weight(NameWeight),
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
            Column(
                modifier = Modifier.weight(MetaWeight),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
 * The name group's share of the row, out of [NameWeight] plus [MetaWeight]: 3 of 5, 60 percent.
 * Identity over annotation (this function's own doc comment), sized against the two real worst
 * cases together rather than either alone: at 60 percent of a roughly 340dp row (a 400dp phone
 * less this row's own padding and [AmberTickerRowGroup]'s), the name group keeps on the order of
 * 25 to 28 characters for ticker plus company after the ticker's own width, comfortably past
 * "Meta Platforms, Inc." (21) and short of needing an ellipsis for most of the catalog; the 40
 * percent left to the meta group is enough for `list_row_meta_join`'s worst case ("$2.7k behind,
 * too thin · 2 d old", 32 characters) to sit on its own two lines rather than clip.
 */
private const val NameWeight = 3f

/** The meta group's share; see [NameWeight]. */
private const val MetaWeight = 2f

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
