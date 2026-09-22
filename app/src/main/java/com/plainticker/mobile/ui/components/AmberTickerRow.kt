package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberType

/**
 * Amber's ticker row (docs/design-research-2026-09-21.md section 5.5): ticker 16/600, company
 * 14/400 secondary; figure 18/600 tnum amber and an optional context line; 64dp minimum, matching
 * the height every direction's anatomy table gives this row (content past that floor grows it, the
 * same way [ListRow] already does). Flat: the 16dp radius and the ground-coloured 1dp seam between
 * rows belong to the group ([AmberTickerRowGroup]), not to one row, so a single-row block (Today's
 * "Next up") still reads as a rounded tonal container and a five-row block does not draw five
 * separate radii.
 *
 * **Three passes at the same clipping report, and why only this one is provable.** Pass one gave
 * [ticker] and [figure] a weighted sibling and stopped there; the device still drew `META x  Meta
 * Pl…`, because the pair that actually starved was the *outer* one, name group versus meta group:
 * [Row] measures a plain, unweighted child before it divides whatever is left among weighted ones,
 * and the meta [Column] ([figure] plus [context], a full clause up to 32 characters,
 * `list_row_meta_join`) was that unweighted child, claiming up to the entire row before the name
 * group saw a budget — a fixed-width sibling in effect, invisible to a test that only grepped for
 * `.width(`. Pass two gave *both* groups a weight, [NameWeight] to [MetaWeight] the file used to
 * carry, 3 to 2. The device still clipped: at 60 percent of this row's ~336dp content width (the
 * arithmetic below), the name group gets roughly 194dp, and once an 8-character worst-case ticker
 * and its 8dp gap are taken out of that, "Meta Platforms, Inc." — 20 characters, not even the
 * catalog's worst name — had nowhere near enough room. A wider weight split would only move the
 * same failure to a different company length; splitting one row's width two ways between an
 * identity (a name with no natural ceiling) and an annotation (a sentence with no natural ceiling
 * either) cannot be tuned into working, because neither side's real worst case is bounded by the
 * other's. That is the anatomy problem the brief names: the two groups were never supposed to
 * compete for the same horizontal budget.
 *
 * **The fix: two lines, not two columns.** [ticker] and [company] keep the pairing research 5.5
 * gives them and [ListRow] already proves (an unweighted, non-wrapping [ticker] beside a
 * `weight(1f, fill = false)` [company] that ellipsizes), but that line now owns the row's *entire*
 * content width instead of a fraction of it. [figure] and [context] move to a second line below,
 * built the identical, already-proven way — [context] first with `weight(1f, fill = false)`, so it
 * claims whatever the unweighted [figure] does not, [figure] last so it still reads as the
 * right-hand numeral a scanning list wants — and that line also owns the full width. Two identical,
 * already-safe pairings, each given the whole row instead of half of it, in place of one splitting
 * arrangement that could not give either side enough. [context] no longer sits right-aligned as a
 * block under [figure]: a full sentence reads better start-aligned than right-ragged once it is not
 * sharing a narrow column with a number, and nothing in research 5.5 ties the two together beyond
 * "a figure's supporting line."
 *
 * **Proof, not another guess: fontTools against `res/font/bricolage_grotesque.ttf` itself**,
 * 2026-09-22, each style instantiated at the exact `wght`/`wdth`/`opsz` [AmberType] builds it with
 * (the same method [com.plainticker.mobile.ui.you.YouModelTest]'s own fact-cell character budget
 * used). This row's content width is fixed by its own chrome: a 400dp frame less
 * [AmberTickerRowGroup]'s 16dp side padding and this row's own 16dp side padding, 64dp of insets,
 * leaves **336dp**.
 * - **Name line.** [rowTicker] at the catalog's own worst symbol, `AUTO.GBx`
 *   (`app/src/main/assets/snapshot/xstocks.json`, 8 characters) measures 77.30dp; less the 8dp gap,
 *   [company] gets **250.70dp** in the worst case, more with any shorter ticker. The regression
 *   itself, "Meta Platforms, Inc." (`app/src/main/assets/snapshot/summary.json`'s own `company`
 *   field for `META`, 20 characters, the exact source behind the screenshot) measures 129.07dp: a
 *   121.64dp margin, comfortably inside the budget even at 1.3x font scale computed the same
 *   conservative way [ListRow.valueSubWidth] documents its own scale check (the real Android 14
 *   curve compresses small text below the nominal multiplier; sizing against the uncompressed 1.3x
 *   is the safer, worse-than-real assumption) — "Meta Platforms, Inc." at that scale measures
 *   167.79dp against a 227.52dp budget. The catalog's own worst name, 54 characters ("SPDR S&P Oil
 *   & Gas Exploration & Production ETF xStock", the fallback [company] on an unanalyzed row) needs
 *   375.56dp and *does not* fit this or any single-line budget on a 400dp frame at this size; that
 *   is expected, not a defect — [company] still ellipsizes rather than clipping, the same resolution
 *   [ListRow] already ships, and 44 of the catalog's 928 names (4.7 percent, all past 34 characters)
 *   share that fate. The point this row exists to fix is the other 95.3 percent, names like
 *   "Meta Platforms, Inc." that used to clip and now do not.
 * - **Meta line.** The widest realistic [figure] this row draws across every screen that calls it
 *   is a worded one, `next_up_weight` ("31,209.9 SKR", 12 characters, [VoteScreen]'s own vote
 *   weight), 113.22dp; less the 8dp gap, [context] gets **214.78dp** in that worst case. The
 *   longest real [context], `list_row_meta_join`'s own worst join ("$2.7k behind, too thin · 2 d
 *   old", 32 characters) measures 192.44dp: a 22.34dp margin, and at 1.3x scale against the far more
 *   common bare-figure case ("100", the composite score this exact row draws) it still clears one
 *   line, 250.18dp of text against a 284.97dp budget — [context]'s `maxLines = 2` stays a backstop
 *   for a case this arithmetic says should not occur, not the thing making the row correct.
 */
@Composable
fun AmberTickerRow(
    ticker: String,
    company: String?,
    modifier: Modifier = Modifier,
    figure: String? = null,
    context: String? = null,
    colors: AmberColors = defaultAmberColors(),
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
    Column(
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The name line: this row's own doc comment above has the arithmetic. Unweighted [ticker]
        // measures first (never wraps, nothing beside it can squeeze it); weight(1f, fill = false)
        // lets [company] claim whatever the ticker did not, up to the row's full content width now
        // that this line no longer shares that width with the meta line below it.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        // The meta line: the identical pairing, mirrored. [context] comes first with
        // weight(1f, fill = false) so it claims whatever the unweighted [figure] does not, and
        // [figure] comes last so it still reads as the row's right-hand numeral. Never right-aligned
        // as a block any more (the old Column's horizontalAlignment = End): a full sentence with
        // room to itself reads better start-aligned than right-ragged.
        if (figure != null || context != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context != null) {
                    Text(
                        text = context,
                        style = AmberType.context,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alignByBaseline().weight(1f, fill = false),
                    )
                }
                if (figure != null) {
                    Text(
                        text = figure,
                        style = AmberType.figureRow,
                        color = colors.actionText,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline(),
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
 *
 * **Light theme's own exception, said once.** `surfaceRaised` over `surfaceGround` is about
 * 1.03:1 in the light set (`#FFFFFF` on `#FFFBF2`, Tokens.kt) against a healthy 1.12:1 in dark, so
 * the whole point of this container, a group of rows reading as one tonal block, does not survive
 * in light: the rows and the ground around them are nearly the same colour. A single 1dp
 * [AmberColors.border] ring around the group's own already-clipped 16dp silhouette, drawn only
 * when `colors === AmberLightColors`, is the one exception to "no border drawn as a frame" above:
 * it outlines the group once, not each row inside it, so the anti-pattern this section still bans
 * (a frame around every row) does not come back. Dark keeps the plain, unringed container it
 * always drew.
 */
@Composable
fun AmberTickerRowGroup(
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(colors.surfaceGround)
            .then(if (colors === AmberLightColors) Modifier.border(1.dp, colors.border, shape) else Modifier),
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
                // The exact regression this row's own doc comment measures against: this company
                // name, at this length, used to draw "Meta Platforms, …" on the device.
                AmberTickerRow(
                    ticker = "METAx",
                    company = "Meta Platforms, Inc.",
                    figure = "66",
                    context = "-0.03% vs NYSE close · 2 d old",
                    onClick = {},
                )
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
