package com.myapp.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myapp.ui.theme.Accent
import com.myapp.ui.theme.Ink
import com.myapp.ui.theme.Line
import com.myapp.ui.theme.Muted
import com.myapp.ui.theme.PlainTickerType

/**
 * List, Portfolio, Watchlist as text tabs: 14sp Outfit, the selected one in Ink with a 2dp Accent
 * underline, the rest Muted, a hairline below. No icons, no bottom bar.
 */
@Composable
fun TopTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // 8dp row inset plus 12dp per tab puts the first label at 20dp and 24dp between labels,
        // while every tab's touch target is at least 48dp wide and tall.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectableGroup()) {
            items.forEachIndexed { index, item ->
                val on = index == selected
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .focusOutline(interactionSource)
                        .selectable(
                            selected = on,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .width(IntrinsicSize.Max)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = item,
                        style = if (on) PlainTickerType.tabSelected else PlainTickerType.tab,
                        color = if (on) Ink else Muted,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 12.dp, bottom = 14.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (on) Accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Line)
    }
}

@InstrumentPreviews
@Composable
private fun TopTabsPreview() {
    PreviewCanvas {
        var selected by remember { mutableIntStateOf(0) }
        TopTabs(items = listOf("List", "Portfolio", "Watchlist"), selected = selected, onSelect = { selected = it })
    }
}
