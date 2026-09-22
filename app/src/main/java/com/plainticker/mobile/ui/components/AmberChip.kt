package com.plainticker.mobile.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * Amber's chip (DESIGN.md section 2, docs/design-research-2026-09-21.md section 5.3): 32dp,
 * [AmberColors.surfaceRaised] and [AmberColors.textPrimary] unselected, matching the mockup's
 * un-morphed set (gen.py's stocks board: "Chips drawn at 8dp, none selected"); [selected] switches
 * to [AmberColors.surfaceHigh] with a 1dp [AmberColors.border] ring, DESIGN.md section 2's "the
 * selected chip once it morphs to full radius" description of what [AmberColors.surfaceHigh] and
 * [AmberColors.actionFill] are for.
 *
 * **The seam, left for the later phase rather than faked.** Section 5.3 calls for the corner
 * radius itself to morph from 8dp to full as a chip is selected, and section 8's calendar keeps
 * that out of this pass on purpose ("Chip morphing... does not land by 29 Sep"). [shapeFor] is a
 * plain, un-animated `if`: [AmberChipRadius] unselected, [AmberChipSelectedShape] (a full/pill
 * radius) selected, switched instantly with no interpolation in between. That is the seam: a later
 * pass drops shape interpolation (`Shape` lerp, or `1.5.0-alpha`'s morphing APIs once that
 * dependency is a founder decision, DESIGN.md section 10) in at [shapeFor]'s call site without
 * touching anything else here. Building a hand-tweened halfway shape now would read as the real
 * thing and be thrown away the moment real morphing lands; drawing the two honest end states and
 * naming the gap is not that.
 */
@Composable
fun AmberChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: AmberColors = AmberDarkColors,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = shapeFor(selected)
    val background = if (selected) colors.surfaceHigh else colors.surfaceRaised
    // Captured under its own name: inside `.semantics { }` below, `this` is a
    // SemanticsPropertyReceiver whose own `selected` (a property, not a function; see
    // SemanticsProperties.kt's `var SemanticsPropertyReceiver.selected by ...`) would otherwise
    // shadow this composable's own `selected` parameter of the same name.
    val isSelected = selected
    Row(
        modifier = modifier
            // Before any size modifier, per the API's own doc: a 32dp visual chip still reserves
            // the database's 48dp touch target (Database rule "Touch Target Size" High) without
            // the chip row's rhythm growing to match.
            .minimumInteractiveComponentSize()
            .focusOutline(interactionSource)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(1.dp, colors.border, shape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.selected = isSelected }
            .height(ChipHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AmberType.context,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The instant, un-morphed toggle: see the class doc for why this is the seam, not the feature.
 * Internal, not private, so [AmberChipTest] can pin that the two states are two fixed [Shape]
 * values and nothing in between, without a Compose layout test to render either one.
 */
internal fun shapeFor(selected: Boolean): Shape = if (selected) AmberChipSelectedShape else AmberChipRadius

private val ChipHeight = 32.dp
private val AmberChipRadius = RoundedCornerShape(8.dp)
private val AmberChipSelectedShape = CircleShape

@InstrumentPreviews
@Composable
private fun AmberChipPreview() {
    AmberPreviewCanvas {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberChip(label = "Tracked 22", selected = false, onClick = {})
            AmberChip(label = "Watched 1", selected = true, onClick = {})
            AmberChip(label = "Consumer Discretionary", selected = false, onClick = {})
        }
    }
}
