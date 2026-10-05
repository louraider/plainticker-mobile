package com.plainticker.mobile.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plainticker.mobile.R
import com.plainticker.mobile.lock.AppLockState
import com.plainticker.mobile.ui.components.AmberPreviewCanvas
import com.plainticker.mobile.ui.components.InstrumentPreviews
import com.plainticker.mobile.ui.components.TextAction
import com.plainticker.mobile.ui.theme.AmberColors
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberType

/**
 * True while the app lock's screen is up. [com.plainticker.mobile.ui.components.AmberSheet] reads
 * it: a ModalBottomSheet draws in a window of its own, above anything this composition draws, so a
 * sheet left open when the app locked would otherwise sit over the lock screen. The sheet's state
 * lives in its ViewModel, so it comes back as it was once the app opens.
 */
val LocalAppLocked = compositionLocalOf { false }

/**
 * The app lock's screen (1.3.28): the page ground edge to edge, the brand mark, "PlainTicker is
 * locked" and one Open action. Everything the app draws stays composed beneath it, so a screen, a
 * scroll position and a deep link or notification that arrived while locked are all there once it
 * opens; MainActivity clears that content's semantics and this screen takes every touch, so nothing
 * under it can be read or reached. Back leaves the app rather than reaching the screen underneath.
 *
 * Amber: `surfaceGround`, the two-corners mark in `actionText` (the TopBar's own tint, larger here
 * because it is the only thing on the page), the title in `sectionHead`, the action a [TextAction].
 * No motion: the screen is simply there, which is also its settled state (DESIGN.md section 6). No
 * one-line slot can clip: the title and the message wrap, and "Open" is one short word.
 */
@Composable
fun LockOverlay(
    state: AppLockState,
    onOpen: () -> Unit,
    onLeave: () -> Unit,
    colors: AmberColors,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    // A field focused before the lock keeps no keyboard up over the lock screen.
    LaunchedEffect(Unit) { focus.clearFocus(force = true) }
    BackHandler(onBack = onLeave)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceGround)
            // Every touch stops here, so nothing beneath can be tapped or scrolled.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SideInset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Decorative: the title under it names the app.
            Icon(
                painter = painterResource(R.drawable.ic_brand_mark_tight),
                contentDescription = null,
                tint = colors.actionText,
                modifier = Modifier.size(MarkSize),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.lock_title),
                style = AmberType.sectionHead,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = AmberType.context,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextAction(
                label = stringResource(R.string.lock_open),
                onClick = onOpen,
                enabled = !state.authenticating,
                color = colors.actionText,
            )
        }
    }
}

private val MarkSize = 48.dp
private val SideInset = 20.dp

// ---- Previews ------------------------------------------------------------------------------

@InstrumentPreviews
@Composable
private fun LockOverlayPreview() {
    AmberPreviewCanvas {
        LockOverlay(state = AppLockState(enabled = true, locked = true), onOpen = {}, onLeave = {}, colors = AmberDarkColors)
    }
}
