package com.plainticker.mobile.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.plainticker.mobile.lint.KotlinScan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Founder's report from the Seeker, 2026-09-29: going back, the closing screen faded out slowly
 * and stayed visible over the screen being returned to. Navigation-Compose's default is a 700ms
 * cross-fade on every transition, and Detail and Home drew on a transparent root, so the two
 * screens showed through each other for most of a second, and the predictive back gesture scrubbed
 * the same fade by hand. This pins the replacement, read from source the way the sheets are.
 */
class NavMotionTest {

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun code(path: String): String {
        val file = File(module, "src/main/java/com/plainticker/mobile/ui/$path")
        assertTrue("$path is missing", file.isFile)
        return KotlinScan(file.readText()).code
    }

    private val host by lazy { code("nav/AppNavHost.kt") }
    private val motion by lazy { code("nav/NavMotion.kt") }

    private fun body(source: String, function: String, until: String): String {
        val start = source.indexOf(function)
        assertTrue("no $function", start >= 0)
        val end = source.indexOf(until, start)
        assertTrue("no $until after $function", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `the host sets all four transitions, so the default cross-fade never plays`() {
        listOf(
            "enterTransition = { if (isReturnHome()) NavMotion.popEnter(motion) else NavMotion.enter(motion) }",
            "exitTransition = { if (isReturnHome()) NavMotion.popExit(motion) else NavMotion.exit(motion) }",
            "popEnterTransition = { NavMotion.popEnter(motion) }",
            "popExitTransition = { NavMotion.popExit(motion) }",
        ).forEach { assertTrue("the host does not pass `$it`", it in host) }
        assertTrue("motion is not gated on the animator scale", "val motion = rememberMotionEnabled()" in host)
    }

    @Test
    fun `every destination paints its own ground`() {
        val destinations = host.split("composable(").size - 1
        assertEquals("a destination is drawn on a transparent root", destinations, host.split("NavScreen {").size - 1)
        val screen = body(motion, "fun NavScreen(", "\n}")
        assertTrue("NavScreen does not fill the screen", "fillMaxSize()" in screen)
        assertTrue("NavScreen does not paint the page ground", ".background(defaultAmberColors().surfaceGround)" in screen)
    }

    @Test
    fun `the screen returned to comes back with no fade, so it is never half there`() {
        val popEnter = body(motion, "fun popEnter(", "fun popExit(")
        assertFalse("the returning screen fades", "fadeIn" in popEnter)
        assertTrue("the returning screen does not come back from the start", "{ width -> -width / PARALLAX_DIVISOR }" in popEnter)
    }

    @Test
    fun `the closing screen slides to the end and fades only once it is mostly gone`() {
        val popExit = body(motion, "fun popExit(", "fun AnimatedContentTransitionScope")
        assertTrue("the closing screen does not slide out to the end", "slideOutHorizontally(" in popExit && "{ width -> width }" in popExit)
        assertTrue("the fade is not delayed to the end of the slide", "fadeOut(tween(POP_FADE_MILLIS, delayMillis = POP_FADE_DELAY_MILLIS))" in popExit)
        assertEquals(NavMotion.SLIDE_MILLIS, NavMotion.POP_FADE_DELAY_MILLIS + NavMotion.POP_FADE_MILLIS)
        assertTrue("the fade is not quick", NavMotion.POP_FADE_MILLIS <= 150)
        assertTrue(
            "the closing screen must be fully opaque for at least two thirds of its slide",
            NavMotion.POP_FADE_DELAY_MILLIS * 3 >= NavMotion.SLIDE_MILLIS * 2,
        )
    }

    @Test
    fun `the screen left underneath on a push drifts and never fades`() {
        val exit = body(motion, "fun exit(", "fun popEnter(")
        assertFalse("the screen underneath fades", "fadeOut" in exit)
        val enter = body(motion, "fun enter(", "fun exit(")
        assertTrue("the arriving screen does not come in from the end", "{ width -> width }" in enter)
        assertTrue("the arriving screen's fade is not the quick token", "fadeIn(tween(FADE_IN_MILLIS" in enter)
        assertEquals(150, NavMotion.FADE_IN_MILLIS)
    }

    @Test
    fun `the motion is DESIGN md's own, short, and a drift is a tenth of the width`() {
        assertTrue(NavMotion.SLIDE_MILLIS in 200..400)
        assertEquals(10, NavMotion.PARALLAX_DIVISOR)
        assertTrue("CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)" in motion)
    }

    @Test
    fun `with the animator scale at zero nothing moves`() {
        assertEquals(EnterTransition.None, NavMotion.enter(motion = false))
        assertEquals(ExitTransition.None, NavMotion.exit(motion = false))
        assertEquals(EnterTransition.None, NavMotion.popEnter(motion = false))
        assertEquals(ExitTransition.None, NavMotion.popExit(motion = false))
        assertNotEquals(EnterTransition.None, NavMotion.enter(motion = true))
        assertNotEquals(ExitTransition.None, NavMotion.popExit(motion = true))
    }

    @Test
    fun `home replacing a screen above it plays the back motion, onboarding's hand over does not`() {
        val rule = body(motion, "fun AnimatedContentTransitionScope<NavBackStackEntry>.isReturnHome()", "\n}")
        assertTrue("targetState.destination.route == Routes.HOME_TAB" in rule)
        assertTrue("initialState.destination.route != Routes.ONBOARDING" in rule)
    }
}
