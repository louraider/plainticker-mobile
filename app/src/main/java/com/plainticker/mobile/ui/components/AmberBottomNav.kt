package com.plainticker.mobile.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberSurface
import com.plainticker.mobile.ui.theme.AmberType

/**
 * The five destinations docs/design-research-2026-09-21.md section 3 gives the redesign: Today,
 * Stocks, Vote, Portfolio, You. A component-owned enum, not
 * [com.plainticker.mobile.ui.home.HomeTab]: that enum belongs to the screens this task does not
 * touch (today List, Vote, Portfolio, Watchlist, plus a separate You route), and the shared
 * information architecture the research draws is a different five with a different shape.
 * Translating one to the other, and folding Watchlist into Today and a Watched filter on Stocks
 * (research section 3), is the screens phase's work, not this one's.
 */
enum class AmberDestination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val iconSelected: Int,
) {
    TODAY(R.string.nav_today, R.drawable.ic_nav_today, R.drawable.ic_nav_today_fill),
    STOCKS(R.string.nav_stocks, R.drawable.ic_nav_stocks, R.drawable.ic_nav_stocks_fill),
    VOTE(R.string.nav_vote, R.drawable.ic_nav_vote, R.drawable.ic_nav_vote_fill),
    PORTFOLIO(R.string.nav_portfolio, R.drawable.ic_nav_portfolio, R.drawable.ic_nav_portfolio_fill),
    YOU(R.string.nav_you, R.drawable.ic_nav_you, R.drawable.ic_nav_you_fill),
}

/**
 * The bar Instrument banned and the research overturned by name (DESIGN.md section 8: "the
 * research's own first finding is that the founder's instinct for a bottom bar was right, and all
 * four directions, Amber included, specify `ShortNavigationBar`"). It is material3:1.4.0's stable
 * component, confirmed against the jar itself (DESIGN.md section 10, this branch's own
 * `AmberTheme` comment): no `ExperimentalMaterial3Api` or `ExperimentalMaterial3ExpressiveApi`
 * annotation on `ShortNavigationBarKt.class`, so this bar costs no new dependency and nothing
 * experimental, only the five icon pairs below it.
 *
 * 64dp on [AmberColors.surfaceRaised] (research 5.3's bar line) comes for free from
 * `NavigationBarTokens.ContainerHeight`, which is 64dp in this exact jar; nothing here overrides
 * height. Labels always show, equal-weight arrangement (the default, correct for three to five
 * items per Material's own guidance), 24dp glyphs (research section 4's foundation rule, also
 * `NavigationBarVerticalItemTokens.IconSize` in the same jar). The selected item fills its glyph,
 * turns its label [AmberColors.actionText] amber, and sits over a lifted tonal pill.
 *
 * The mockup (gen.py's "amber" dict) draws that pill in a literal `#5C4300`, a seventh surface no
 * semantic role names. `CopyLintTest`'s "every literal color under ui is a token" test pins
 * Tokens.kt to exactly 26 opaque colors, and this task's scope is `ui/components/`, not
 * `Tokens.kt`, so a 27th literal was not the way to match it. [AmberColors.surfaceHigh] stands in
 * instead: DESIGN.md section 2 already names it "the highest surface: a selected chip, a sheet",
 * which is the exact role an active nav indicator plays. The hue differs from the mockup's pill;
 * the role it is drawn for does not.
 *
 * The pill's width-morph and any bespoke press spring (research 5.3's "the bar pill morphs
 * width") are motion-token work DESIGN.md section 6 leaves for a later pass; what plays here is
 * `NavigationItem`'s own built-in selection transition, unmodified.
 */
@Composable
fun AmberBottomNav(
    selected: AmberDestination,
    onSelect: (AmberDestination) -> Unit,
    modifier: Modifier = Modifier,
    colors: AmberColors = defaultAmberColors(),
    destinations: List<AmberDestination> = AmberDestination.entries,
) {
    ShortNavigationBar(
        modifier = modifier,
        containerColor = colors.surfaceRaised,
        contentColor = colors.textPrimary,
    ) {
        destinations.forEach { destination ->
            val isSelected = destination == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        painter = painterResource(if (isSelected) destination.iconSelected else destination.icon),
                        // The label beside it already names the destination; painting it twice
                        // would have a screen reader speak "Today" back to back.
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(text = stringResource(destination.label), style = AmberType.meta, maxLines = 1)
                },
                iconPosition = NavigationItemIconPosition.Top,
                colors = ShortNavigationBarItemDefaults.colors(
                    selectedIconColor = colors.actionText,
                    selectedTextColor = colors.actionText,
                    selectedIndicatorColor = colors.surfaceHigh,
                    // The bar's own surface is surfaceRaised, so textTertiary needs no promotion
                    // (AmberColors.textTertiary only promotes on AmberSurface.HIGH).
                    unselectedIconColor = colors.textTertiary(AmberSurface.RAISED),
                    unselectedTextColor = colors.textTertiary(AmberSurface.RAISED),
                ),
            )
        }
    }
}

@InstrumentPreviews
@Composable
private fun AmberBottomNavPreview() {
    AmberPreviewCanvas {
        AmberBottomNav(selected = AmberDestination.TODAY, onSelect = {})
    }
}
