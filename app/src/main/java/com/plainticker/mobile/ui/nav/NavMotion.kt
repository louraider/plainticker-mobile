package com.plainticker.mobile.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.plainticker.mobile.ui.components.defaultAmberColors

/**
 * How one screen gives way to another (founder's report from the Seeker, 2026-09-29: going back,
 * the closing screen faded out slowly and stayed visible over the screen being returned to).
 *
 * The cause was two things at once. Navigation-Compose's own default is a 700ms cross-fade on all
 * four transitions, so for most of a second the closing screen sat half transparent on top of the
 * destination, and the predictive back gesture (on by default at this target SDK) scrubbed that
 * same fade by hand. And no destination painted a ground of its own: Detail and Home draw on a
 * transparent root, so even a solid screen let the one under it show through wherever it had no
 * content.
 *
 * What replaces it, in the one direction a reader already expects from every Android app:
 *
 * - **Forward** ([enter], [exit]): the new screen slides in from the end over the old one, which
 *   drifts a tenth of the width toward the start underneath it. The new screen fades in over the
 *   quick 150ms token only, so it is opaque long before it is in place.
 * - **Back** ([popEnter], [popExit]): the closing screen slides out to the end, staying fully
 *   opaque for most of the slide and fading only over the last [POP_FADE_MILLIS] while it is
 *   already mostly off screen. The screen returned to comes back from a tenth of the width toward
 *   the start with no fade at all, so it is never half there. The predictive back gesture scrubs
 *   exactly these, so dragging reveals the destination beside the closing screen, never through it.
 * - **Z order**: NavHost itself puts the closing screen above the destination on a pop (and the
 *   arriving one above the old one on a push), which is what a slide needs; nothing here changes it.
 * - **Ground**: every destination is drawn inside [NavScreen], which paints Amber's own page ground
 *   edge to edge, so no screen is ever see-through during a transition.
 * - **Reduced motion**: with the system animator duration scale at 0 (`rememberMotionEnabled()`),
 *   every transition is [EnterTransition.None] / [ExitTransition.None]: the destination is simply
 *   there on the next frame, the same rule every other motion in DESIGN.md section 6 answers to.
 *
 * Home replaced from a screen above it (Detail's "View in Portfolio", a notification naming a tab)
 * is a navigation forward in the graph but a return in the reader's eyes, so [enter] and [exit]
 * play the back motion for it ([isReturnHome]).
 */
object NavMotion {
    /** The slide. Track's settle curve (DESIGN.md section 6), decelerating into place. */
    const val SLIDE_MILLIS = 300

    /** The quick token: the arriving screen's fade in on a push. */
    const val FADE_IN_MILLIS = 150

    /** The closing screen fades only at the very end of its slide, once it is mostly gone. */
    const val POP_FADE_MILLIS = 100
    const val POP_FADE_DELAY_MILLIS = SLIDE_MILLIS - POP_FADE_MILLIS

    /** How far the screen underneath drifts, as a share of the width: a parallax, not a second slide. */
    const val PARALLAX_DIVISOR = 10

    val SlideEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** The arriving screen on a push: in from the end, opaque within the quick token. */
    fun enter(motion: Boolean): EnterTransition = if (!motion) EnterTransition.None else
        slideInHorizontally(tween(SLIDE_MILLIS, easing = SlideEasing)) { width -> width } +
            fadeIn(tween(FADE_IN_MILLIS, easing = LinearOutSlowInEasing))

    /** The screen left underneath on a push: a small drift toward the start, never a fade. */
    fun exit(motion: Boolean): ExitTransition = if (!motion) ExitTransition.None else
        slideOutHorizontally(tween(SLIDE_MILLIS, easing = SlideEasing)) { width -> -width / PARALLAX_DIVISOR }

    /** The screen returned to: back from a small drift toward the start, with no fade at all. */
    fun popEnter(motion: Boolean): EnterTransition = if (!motion) EnterTransition.None else
        slideInHorizontally(tween(SLIDE_MILLIS, easing = SlideEasing)) { width -> -width / PARALLAX_DIVISOR }

    /** The closing screen: out to the end, opaque until it is mostly gone, then a quick fade. */
    fun popExit(motion: Boolean): ExitTransition = if (!motion) ExitTransition.None else
        slideOutHorizontally(tween(SLIDE_MILLIS, easing = SlideEasing)) { width -> width } +
            fadeOut(tween(POP_FADE_MILLIS, delayMillis = POP_FADE_DELAY_MILLIS))

    /**
     * Home arriving from a screen that sat above it, which the reader reads as going back. The
     * route is the pattern it was registered under, so a tab named by a notification over Home
     * counts too; only onboarding's hand-over into Home is a real step forward.
     */
    fun AnimatedContentTransitionScope<NavBackStackEntry>.isReturnHome(): Boolean =
        targetState.destination.route == Routes.HOME_TAB && initialState.destination.route != Routes.ONBOARDING
}

/**
 * One destination, on Amber's own page ground edge to edge. A screen whose root is transparent
 * (Detail's and Home's are) would otherwise show the screen under it through every gap for as
 * long as a transition runs.
 */
@Composable
fun NavScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(defaultAmberColors().surfaceGround)) { content() }
}
