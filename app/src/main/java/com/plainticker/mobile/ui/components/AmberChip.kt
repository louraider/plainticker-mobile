package com.plainticker.mobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
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
 * **The morph, filled in at the seam the earlier pass left.** Section 5.3 calls for the corner
 * radius itself to morph from 8dp to full as a chip is selected; [shapeFor] now animates that one
 * number with a spring rather than switching between two fixed [Shape] instances. See [shapeFor]'s
 * own doc for why that is a plain `animateDpAsState`, not `1.5.0-alpha`'s shape-morphing API
 * (DESIGN.md section 10, section 5.3's own risk line: "it is also where an implementer reaches for
 * `1.5.0-alpha`, which must be refused").
 */
@Composable
fun AmberChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val motionEnabled = rememberMotionEnabled()
    val shape = shapeFor(selected, motionEnabled)
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
 * The corner radius morphs from [AmberChipCornerRadius] (8dp) to [AmberChipFullRadius] ([ChipHeight]
 * halved: 16dp, the exact radius `CircleShape` would draw at this fixed height) with a spring.
 * Internal, not private, so [AmberChipTest] can pin the two end states and the animation choices
 * from source, the way it pinned the un-morphed seam before this pass filled it in.
 *
 * **Why a plain `animateDpAsState`, not `1.5.0-alpha`'s shape-morphing API.** That library exists
 * to morph between shapes whose *vertex topology* disagrees: a star into a circle, a cookie into a
 * FAB, pairs with no single number that describes "in between." This chip's two states are the same
 * rectangle disagreeing on one value, its corner radius, so interpolating that one `Dp` reads
 * identically on a phone at 32dp as the alpha library's morph would, because there is no
 * vertex-correspondence problem here for it to solve. Section 5.3 names this exact reach as the
 * direction's own risk ("it is also where an implementer reaches for `1.5.0-alpha`, which must be
 * refused"); this is that refusal, not an oversight. Nothing about this chip needed the dependency,
 * so it stays out, eight days from a minified release freeze. If a later component's two shapes
 * genuinely disagree in topology, not just in one radius, that is a real reason to revisit
 * `1.5.0-alpha`, and it should be a founder decision made against that concrete component, the same
 * way DESIGN.md section 10 already asked for.
 *
 * [motionEnabled] is threaded in from [AmberChip] rather than read again here, so the component and
 * its shape agree on one read of the system setting. At animator scale 0 `snap()` replaces the
 * spring: the shape lands on its selected or unselected end state on the next frame, never caught
 * mid-morph, so the chip's shape is never legible only because an animation finished.
 */
@Composable
internal fun shapeFor(selected: Boolean, motionEnabled: Boolean = rememberMotionEnabled()): Shape {
    val targetRadius = if (selected) AmberChipFullRadius else AmberChipCornerRadius
    val radius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = if (motionEnabled) AmberChipMorphSpring else snap(),
        label = "amber-chip-shape-morph",
    )
    return RoundedCornerShape(radius)
}

private val ChipHeight = 32.dp
private val AmberChipCornerRadius = 8.dp
private val AmberChipFullRadius = ChipHeight / 2
private val AmberChipMorphSpring = spring<Dp>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

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
