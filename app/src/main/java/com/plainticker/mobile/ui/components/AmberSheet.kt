@file:OptIn(ExperimentalMaterial3Api::class)

package com.plainticker.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * Amber's sheet (docs/design-research-2026-09-21.md section 5.5): the surface swap, vote and pass
 * all use. [AmberColors.surfaceHigh], not [AmberColors.surfaceRaised]: the anatomy table names
 * `surface.high` for it specifically, the same lifted tone DESIGN.md section 2 already reserves
 * for "a selected chip, a sheet," so a sheet reads as the highest thing on screen the moment it
 * opens. 28dp top radius (matching `AmberShapes.large`/`extraLarge` in Theme.kt; literal here
 * because only the top corners round on a sheet, and both shape constants round every corner), a
 * handle in [AmberColors.actionText] amber rather than a neutral line.
 *
 * "Spring entry" (research 5.5) is motion-token work DESIGN.md section 6 defers to a later pass;
 * what plays today is `ModalBottomSheet`'s own default slide, unmodified, the same way the retired
 * Instrument `Sheet` left it.
 */
@Composable
fun AmberSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    colors: AmberColors = defaultAmberColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = AmberSheetShape,
        containerColor = colors.surfaceHigh,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = colors.surfaceGround.copy(alpha = 0.82f),
        dragHandle = { AmberSheetHandle(colors = colors) },
        contentWindowInsets = { SheetInsets },
        content = content,
    )
}

/**
 * The top edge and handle, shared by the modal sheet and [AmberSheetSurface]. `ModalBottomSheet`
 * wraps whatever this draws in a plain `clickable` box with no minimum size of its own (confirmed
 * against the pinned `material3` 1.4.0 sources: the drag handle's tap target is exactly this
 * composable's measured bounds), so the 23dp padding on each side of the 2dp bar is not decorative
 * spacing, it is the target: 23 + 2 + 23 = 48dp, the same floor `BottomSheetDefaults.DragHandle`
 * reaches with 22dp around its own 4dp bar. A handle drawn at the old 12/8dp padding (22dp total)
 * measured under the 48dp rule this task's brief calls a hard one, not a preference.
 */
@Composable
fun AmberSheetHandle(modifier: Modifier = Modifier, colors: AmberColors = defaultAmberColors()) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .padding(vertical = HandleTouchPadding)
                .size(width = 28.dp, height = HandleBarHeight)
                .background(colors.actionText),
        )
    }
}

/**
 * The sheet's surface without the modal: a landed receipt shown inline, the onboarding panel
 * (`OnboardingScreen.kt`'s `ConsentPanel`, migrated off the retired Instrument `SheetSurface`), and
 * every preview here. Same 28dp top radius and amber handle as the modal version. [handle] false
 * draws the same 1dp [AmberColors.border] top edge the retired `SheetSurface` fell back to instead
 * of drawing nothing: the onboarding panel sits over a 25 percent backdrop and needs that edge to
 * read as a surface rather than a soft fade.
 */
@Composable
fun AmberSheetSurface(
    modifier: Modifier = Modifier,
    handle: Boolean = true,
    colors: AmberColors = defaultAmberColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(AmberSheetShape)
            .background(colors.surfaceHigh),
    ) {
        if (handle) {
            AmberSheetHandle(colors = colors)
        } else {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        }
        content()
    }
}

private val AmberSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** The visible bar (design research 5.5's "28x2dp handle instead of the Material pill"). */
private val HandleBarHeight = 2.dp

/** 23dp each side of [HandleBarHeight] is the 48dp touch-target floor; see [AmberSheetHandle]. */
private val HandleTouchPadding = 23.dp

@InstrumentPreviews
@Composable
private fun AmberSheetSurfacePreview() {
    AmberPreviewCanvas {
        AmberSheetSurface {
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Received", style = AmberType.meta, color = AmberDarkColors.textTertiary(AmberSurface.HIGH))
                Text(text = "0.01364 TSLAx", style = AmberType.figureLarge, color = AmberDarkColors.actionText, maxLines = 1, softWrap = false)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
                AmberPrimaryAction(label = "View in Portfolio", onClick = {})
            }
        }
    }
}
