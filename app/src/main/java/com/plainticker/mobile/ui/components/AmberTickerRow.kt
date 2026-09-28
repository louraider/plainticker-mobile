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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import com.plainticker.mobile.ui.theme.AmberSurface
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
 *   is a worded one, `next_up_weight` ("38,406.2 SKR", 12 characters, [VoteScreen]'s own vote
 *   weight), 113.22dp; less the 8dp gap, [context] gets **214.78dp** in that worst case. The
 *   longest real [context], `list_row_meta_join`'s own worst join ("Depth $2.7k, too thin · 2 d
 *   old", 31 characters) measures 186.90dp: a 27.88dp margin, and at 1.3x scale against the far more
 *   common bare-figure case ("100", the composite score this exact row draws) it still clears one
 *   line, 250.18dp of text against a 284.97dp budget — [context]'s `maxLines = 2` stays a backstop
 *   for a case this arithmetic says should not occur, not the thing making the row correct.
 *
 * **The leader row, unblocked: [trailingAction] shares the meta line, never the name line.** The
 * migration this row exists to unblock (Vote's leader row, `VoteScreen.kt`'s `LeaderRow`) needs the
 * weight *and* a "Vote" action at once, and this row used to have exactly one right-hand slot for
 * that: [figure]. Three shapes were open. Give the leader row its own anatomy from Amber's
 * language: rejected, because it would duplicate this row's own name line and figure/context
 * pairing for one caller, the anatomy this task's brief calls load-bearing everywhere else. Move
 * the action somewhere else a reader can still reach it (a swipe, a second tap target off the
 * row): rejected, because Vote's own explainer already spent its budget saying a vote is a signed
 * transaction, not a gesture to discover by accident. What ships: [trailingAction] optional and
 * additive, sharing width with [figure] and [context] on the *meta* line only, never with [ticker]
 * and [company] on the name line above — the two lines still never compete with each other, which
 * is this row's own load-bearing fix, and the name line's whole budget above (the "Meta Platforms,
 * Inc." arithmetic) is unchanged by a trailing action because nothing about it changed.
 *
 * The meta line's own inner [Row] (context, figure) now carries `weight(1f, fill = false)` itself,
 * one level up from where [context] already carries it against [figure]: the same "short, bounded
 * content first, flexible content claims whatever is left" pairing this row proves twice already,
 * proved a third time against [trailingAction], which is short and bounded the same way [ticker]
 * is — a fixed word ("Vote", "Unwatch") that only grows with font scale, never with unrelated data
 * the way a company name or a meta sentence does. [TextAction]'s own 16dp start padding is the gap;
 * nothing here adds a second one, the same convention [ListRow]'s own value-and-action [Row]
 * already uses.
 *
 * **Proof, the same method, for the two real callers.** Content width with [trailingAction] present
 * is 336dp less [TextAction]'s own rendered width (16dp start padding plus its label at
 * `AmberType.textAction`, Bricolage 600 opsz 14 at 14sp since 2026-09-26, when [TextAction] left
 * Outfit SemiBold; every number below was re-measured then).
 * - **Vote's leader row** (`figure` = the app's own widest figure, `next_up_weight` "38,406.2 SKR",
 *   113.220dp; `context` = `next_up_voters`, "9,999 voters" at four digits' realistic ceiling for
 *   one token's own leaderboard, 83.160dp; action = "Vote", 31.318dp at 1.0x). Content width with
 *   the action present: 336 − (16 + 31.318) = 288.682dp. Context and figure split that exactly as
 *   the budget above already proves for the two-item case: figure's 113.220dp plus the 8dp gap
 *   leaves context 167.462dp, and "9,999 voters" clears it by 84.302dp. At 1.3x (the same flat,
 *   worse-than-real scaling [ListRow.valueSubWidth] and the arithmetic above already use: dp
 *   padding is unscaled, sp text scales by the raw factor) the action's label grows to 40.713dp
 *   (rendered width 56.713dp), content width becomes 336 − 56.713 = 279.287dp, figure grows to
 *   147.186dp, and context's budget is 279.287 − 147.186 − 8 = 124.101dp against "9,999 voters"
 *   grown to 108.108dp: a 15.993dp margin. The name line is not part of either computation, so its
 *   own 1.0x and 1.3x margins above ("Meta Platforms, Inc." at 121.638dp and 59.734dp) are untouched
 *   by this row carrying a leader's own worst catalog ticker and company at the same time.
 * - **Watchlist's row** (`figure` = null, no figure at all on this caller; `context` = the report
 *   and tracking clauses `list_row_meta_join`s together; action = "Unwatch", the widest label this
 *   row draws, 60.648dp at 1.0x, rendered width 76.648dp). Content width with the action present:
 *   336 − 76.648 = 259.352dp, all of it context's own budget since there is no figure to share it
 *   with. The realistic join ("Reports 22 Oct · Depth $2.7k, too thin", 38 characters) measures
 *   241.024dp: a 18.33dp margin at 1.0x, but at 1.3x (the action's own label growing to 78.842dp,
 *   the budget shrinking to 241.158dp) that same clause grows to 313.33dp and no longer clears one
 *   line. The pathological join ("Not in the analysis list · Depth $99.9k, too thin", 49
 *   characters, 299.6dp) does not clear the 1.0x budget either. Both wrap to a second line rather
 *   than clipping — [context] keeps `maxLines = 2` and `TextOverflow.Ellipsis`, the same resolution
 *   the 54-character company outlier above accepts, and even the pathological join grown to 1.3x
 *   (396.69dp) fits inside two lines' own combined capacity (2 × 241.158 = 482.32dp) with room to
 *   spare, so `maxLines = 2` is a real backstop here, not a silent third line of truncation waiting
 *   to happen.
 */
@Composable
fun AmberTickerRow(
    ticker: String,
    company: String?,
    modifier: Modifier = Modifier,
    figure: String? = null,
    context: String? = null,
    /**
     * [figure] is a state word, not a number ("Not classified"): drawn in the context type and the
     * secondary colour instead of the amber figure, so it never reads as a score or a lock.
     */
    figureQuiet: Boolean = false,
    /**
     * An optional label sharing the meta line with [figure] and [context] (see this function's own
     * doc comment, "The leader row, unblocked"): a real, frequently used affordance on the same row
     * as the weight it acts on, drawn through [TextAction] exactly as every other trailing action in
     * this app already is. Ignored unless [onTrailingAction] is also given.
     */
    trailingAction: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    /**
     * A quiet state word in the action's place when there is no action to take ("Voted" on a
     * leader this wallet already backed this round). Drawn only when no [trailingAction] is.
     */
    trailingNote: String? = null,
    colors: AmberColors = defaultAmberColors(),
    /**
     * Pins [trailingAction] to the row's end edge instead of letting it follow a short meta line,
     * so a column of rows (Portfolio's recent swaps) lines its actions up whatever each line says.
     */
    trailingActionAtEnd: Boolean = false,
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
            .focusOutline(interactionSource, colors)
            // One step up the surface ladder while pressed, the same tonal move surfaceHigh
            // already means everywhere else (DESIGN.md section 2: "a selected chip, a sheet").
            .background(if (pressed) colors.surfaceHigh else colors.surfaceRaised)
            .then(interaction)
            .then(spokenAs)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        // Centred in the 64dp floor: a row with only its name line (a ballot row) sits level with
        // the action beside it instead of hugging the top (device QA of 1.3.17). A two-line row
        // is taller than the floor, so nothing moves for it.
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
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
        // The meta line: the identical pairing, mirrored, now itself wrapped in one more unweighted
        // Row so an optional trailingAction can sit beside it without ever touching the name line
        // above (see this function's own doc comment, "The leader row, unblocked"). The inner Row
        // keeps context and figure exactly as proven above; [context] still comes first with
        // weight(1f, fill = false) so it claims whatever the unweighted [figure] does not, and
        // [figure] still comes last so it still reads as the row's right-hand numeral. The inner Row
        // itself now carries that same weight(1f, fill = false), one level up, against
        // trailingAction: short, bounded content first (a fixed word, only font scale grows it),
        // flexible content claims the rest, the same pairing this row already proves twice. Never
        // right-aligned as a block any more (the old Column's horizontalAlignment = End): a full
        // sentence with room to itself reads better start-aligned than right-ragged.
        if (figure != null || context != null || trailingAction != null || trailingNote != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f, fill = trailingActionAtEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                        if (figureQuiet) {
                            Text(
                                text = figure,
                                style = AmberType.context,
                                color = colors.textSecondary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.alignByBaseline(),
                            )
                        } else {
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
                // TextAction's own 16dp start padding is the gap; this Row adds no second one, the
                // same convention ListRow's own value-and-action Row already uses.
                if (trailingAction != null && onTrailingAction != null) {
                    TextAction(
                        label = trailingAction,
                        onClick = onTrailingAction,
                        color = colors.actionText,
                        contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 0.dp, bottom = 10.dp),
                    )
                } else if (trailingNote != null) {
                    Text(
                        text = trailingNote,
                        style = AmberType.context,
                        color = colors.textTertiary(AmberSurface.RAISED),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 16.dp),
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
                    context = "-0.03% vs US price · 2 d old",
                    onClick = {},
                )
                AmberTickerRow(
                    ticker = "NVDAx",
                    company = "NVIDIA Corporation",
                    figure = "-1.01%",
                    context = "vs US price",
                    onClick = {},
                )
                AmberTickerRow(
                    ticker = "AUTO.GBx",
                    company = "SPDR S&P Oil & Gas Exploration & Production ETF xStock",
                    figure = "+0.09%",
                    context = "vs US price",
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
            // The leader row this file's own doc comment unblocks: the worst catalog ticker and the
            // app's own widest figure, still carrying a trailing action, at once.
            AmberTickerRowGroup {
                AmberTickerRow(
                    ticker = "AUTO.GBx",
                    company = "SPDR S&P Oil & Gas Exploration & Production ETF xStock",
                    figure = "38,406.2 SKR",
                    context = "9,999 voters",
                    trailingAction = "Vote",
                    onTrailingAction = {},
                    onClick = {},
                )
                AmberTickerRow(
                    ticker = "TSMx",
                    company = "Taiwan Semiconductor",
                    figure = "38,406.2 SKR",
                    context = "3 voters",
                    trailingAction = "Vote",
                    onTrailingAction = {},
                    onClick = {},
                )
            }
        }
    }
}
