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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.Elevated
import com.plainticker.mobile.ui.theme.Ink
import com.plainticker.mobile.ui.theme.LineStrong
import com.plainticker.mobile.ui.theme.Muted
import com.plainticker.mobile.ui.theme.PlainTickerType

/**
 * The modal sheet (swap, receipt): Elevated surface, radius 0, a 1dp Line strong top edge and a
 * 28x2dp handle instead of the Material pill, the default slide with no bounce. The scrim is
 * Canvas at 82 percent, matching the canvas mockups. The content is padded by [SheetInsets]: the
 * navigation bar and the keyboard at the bottom, the status bar once the sheet is dragged up to it.
 */
@Composable
fun Sheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = Elevated,
        contentColor = Ink,
        tonalElevation = 0.dp,
        scrimColor = Canvas.copy(alpha = 0.82f),
        dragHandle = { SheetHandle() },
        contentWindowInsets = { SheetInsets },
        content = content,
    )
}

/** The top edge and the handle, shared by the modal sheet and the static surface. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LineStrong))
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 8.dp)
                .size(width = 28.dp, height = 2.dp)
                .background(LineStrong),
        )
    }
}

/**
 * The sheet's surface without the modal: the onboarding panel, a landed receipt shown inline,
 * and every preview. Same edge, same handle (optional), same Elevated fill.
 */
@Composable
fun SheetSurface(
    modifier: Modifier = Modifier,
    handle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().background(Elevated)) {
        if (handle) {
            SheetHandle()
        } else {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LineStrong))
        }
        content()
    }
}

@InstrumentPreviews
@Composable
private fun SheetSurfacePreview() {
    PreviewCanvas {
        SheetSurface {
            Box(Modifier.padding(top = 8.dp)) {
                LiveBar(label = "Landed", meta = "confirmed in 3.1 s", live = false)
            }
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "Received", style = PlainTickerType.label, color = Muted)
                Text(text = "0.01364 TSLAx", style = PlainTickerType.bigValue, color = Ink, maxLines = 1, softWrap = false)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
                SecondaryButton(label = "View in Portfolio", onClick = {})
            }
        }
    }
}
