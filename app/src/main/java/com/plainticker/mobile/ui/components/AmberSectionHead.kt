package com.plainticker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * Amber's section head (docs/design-research-2026-09-21.md section 5.5): 22/700 at `opsz` 22, 24dp
 * above, "sits on the card edge" rather than the 32dp-above, rule-separated spacing Instrument's
 * retired `Heading` used. [title] carries the whole row's
 * [androidx.compose.ui.semantics.heading] mark, so a screen reader can still jump section to
 * section the way plan section 13 Pass 6 asked `Heading` to.
 *
 * **This is now the one section head Amber draws, everywhere.** `Heading`
 * (`ui/components/Heading.kt`) was retired rather than kept as a second component that meant the
 * same thing: it carried Instrument's fixed-dark colours and Outfit face under Amber's screens (six
 * call sites across Gallery, Onboarding and Watchlist), and its anatomy was the exact starved-title
 * trap this component was already built to close everywhere else. `VoteScreen.kt`'s `RoundHeader`
 * doc comment names that trap directly (`Heading` hands its title a `weight(1f)` column and its
 * meta the rest of the row at the meta's own width, so a long meta starves a short title) as the
 * reason it moved to this component before this pass ever started; every remaining `Heading` call
 * site got the same move rather than a second, parallel fix. [title] is allowed to wrap to two
 * lines rather than being forced to one for the same reason: [meta] is meant to stay a short count
 * ("22", "160", research 5.3's "board" figures), but [title] is not always short. The GICS sector
 * names the Stocks chapter head draws it for run up to 22 characters ("Consumer Discretionary",
 * "Communication Services", "Information Technology"), and wrapping costs nothing next to a
 * two-or-three-digit count, where clipping would.
 */
@Composable
fun AmberSectionHead(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    lede: String? = null,
    colors: AmberColors = defaultAmberColors(),
    /**
     * The background this head paints itself, since a `stickyHeader` item scrolls over content
     * that must not show through it while it is pinned. Defaults to the page ground; a chapter
     * head sitting on a different surface passes that surface's colour instead.
     */
    background: Color = colors.surfaceGround,
    /**
     * An optional [TextAction] after the title (founder feedback 2026-09-29: "Explain" on Detail's
     * "Backing and controls"). Drawn only with [onAction]; it shares the title's line and baseline,
     * and the title keeps its weighted column, so a long title wraps rather than starving it.
     */
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().background(background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = AmberType.sectionHead,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline()
                    .semantics { heading() },
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = AmberType.context,
                    color = colors.textTertiary(surfaceOf(background, colors)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (action != null && onAction != null) {
                TextAction(
                    label = action,
                    onClick = onAction,
                    color = colors.actionText,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (lede != null) {
            Text(
                text = lede,
                style = AmberType.context,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * [AmberColors.textTertiary] takes an [AmberSurface], not a raw [Color], because the
 * tertiary-on-high promotion (DESIGN.md section 2) is keyed to the three named surfaces, not to
 * arbitrary colour equality. A section head's [background] is always one of
 * [AmberColors.surfaceGround] or [AmberColors.surfaceHigh] in practice (the page, or a chapter
 * pinned over a sheet); anything else falls back to ground's rule, which is the more permissive of
 * the two and therefore never the one that could fail contrast.
 */
private fun surfaceOf(background: Color, colors: AmberColors): AmberSurface =
    if (background == colors.surfaceHigh) AmberSurface.HIGH else AmberSurface.GROUND

/** The longest GICS sector name Stocks groups xStocks into (`app/src/main/assets/snapshot/xstocks.json`'s sectors, 2026-09-22): 22 characters, tied by three names. Read by [AmberSectionHeadTest]. */
val LongestSectorName: String = "Communication Services"

@InstrumentPreviews
@Composable
private fun AmberSectionHeadPreview() {
    AmberPreviewCanvas {
        Column {
            AmberSectionHead(title = "Tracked today", meta = "22", lede = "22 of 160 analyzed can be tracked today")
            AmberSectionHead(title = LongestSectorName, meta = "11")
        }
    }
}
