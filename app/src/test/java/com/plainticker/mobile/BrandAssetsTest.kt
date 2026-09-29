package com.plainticker.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.plainticker.mobile.ui.theme.AmberDarkColors
import com.plainticker.mobile.ui.theme.AmberLightColors
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Brand assets, DESIGN.md section 9, read straight from disk like [ManifestTest]: the adaptive
 * launcher icon is the "Two corners" mark in Amber's own dark ground over an Amber-action tile,
 * carries a monochrome layer with the same shapes, keeps every coordinate inside the mask a
 * launcher actually cuts, and no template bitmap is left in a mipmap folder. The splash theme
 * (since 2026-09-29) draws the launcher icon itself, the foreground layer inside a circle of the
 * tile colour, over the page ground of the theme the system is in, with system bar icons to suit
 * it, and hands over to the app theme; the notification icon is the same mark in white. Colors
 * are compared with [AmberDarkColors], not with copied literals, so a change to
 * [AmberDarkColors.surfaceGround] or [AmberDarkColors.actionFill] cannot leave the icon behind.
 *
 * **2026-09-22: the colour pair, not the shapes.** Every assertion below used to read
 * Instrument's own fixed `Canvas` (`#0B0F14`) and `Ink` (`#E8ECF1`) Kotlin tokens, chosen on
 * 2026-09-15, over a week before the founder picked Amber, and never revisited: DESIGN.md
 * section 9 found the arrangement survived Amber but the colour pair was argued entirely inside
 * Instrument's retired, flat palette and had become, as of Amber, a colour nobody else in the app
 * drew any more, visible as a launcher tile that no longer matched the app and a cool-to-warm
 * flash on the splash in light mode. The shapes are untouched (the founder's own pick, not this
 * pass's to redraw); every colour assertion below read [AmberDarkColors.surfaceGround] and
 * [AmberDarkColors.textPrimary] in exactly the same two roles instead, mirrored into
 * `res/values/colors.xml` as `amber_ground` and `amber_ink` (`design/brand/marks.py`,
 * `AMBER_GROUND`/`AMBER_INK`).
 *
 * **2026-09-24: the tile and the reach, not the colour pair's roles or the arrangement.**
 * Direction A of a three-direction audit ("Two corners, refit," DESIGN.md section 9), the
 * founder's pick: the launcher tile moved a second time, off [AmberDarkColors.textPrimary]
 * (`amber_ink`) onto [AmberDarkColors.actionFill] (`amber_action`, `#FFC247`), so the icon is the
 * one warm-yellow tile in the drawer instead of one more white one holding Amber's colours
 * without any amber in it; the mark itself stays [AmberDarkColors.surfaceGround]. And the four
 * rectangles pulled in from a 26-to-82 block with a 14-unit arm to a 31-to-77 block with a
 * 12-unit arm, which moves the furthest corner from 39.60 units out to 32.53: inside not only the
 * superellipse a launcher actually cuts but also the plain 36-unit circle a "Pixel-style"
 * launcher cuts, which the arrangement used to miss entirely. The splash and the notification
 * icon are untouched by either change (they never drew the tile), and the launcher, the splash
 * and the plain window background painted before Compose's first frame still all agree on one
 * ground.
 *
 * Four lessons are pinned here and every one of them was learned by shipping the wrong thing.
 *
 * Size. An icon is read at 48dp, and a launcher shows the central 72 of the 108 viewport, so one
 * viewport unit is 0.667dp on a launcher grid. The first mark carried its only distinguishing
 * detail in a 2 unit tick, 1.3dp at 48dp, which nobody ever saw. [MIN_STROKE] is that lesson: no
 * shape may be thinner than 4dp at 48dp.
 *
 * Silhouette. The first construction of the gauge centred all three shapes on y 54. In colour the
 * Accent tick pulled away and it read as the gauge; flattened for the themed icon and for the 24dp
 * notification silhouette it read as a plus sign. Colour can pull two shapes apart; a silhouette
 * can only be pulled apart by where its shapes point, so no layer may mirror itself top to bottom.
 * Two corners passes that by being symmetric the other way: turned 180 degrees about the centre it
 * is itself, flipped top to bottom it is not, and the test for the second is below.
 *
 * Ground. Four icon attempts were rejected and all four put a Canvas tile on a near-black drawer
 * wallpaper, where it measured 1.03 to 1 across its own edge and was not a tile at all. The tile
 * is [AmberDarkColors.actionFill] now (`amber_action`, since the 2026-09-24 refit;
 * [AmberDarkColors.textPrimary]/`amber_ink` before it) and the mark on it is
 * [AmberDarkColors.surfaceGround]; Instrument's own Ink-on-Canvas pair measured 15.48 to 1 in the
 * real drawer, the ink tile that replaced it computed to 16.0:1, and Amber's own action tile
 * computes to 11.5:1, all by the same WCAG formula (AmberContrastTest's own pinned
 * actionText-over-surfaceGround figure for this exact pair; this file has no device to re-shoot
 * the drawer photo with). So the background layer is asserted against
 * [AmberDarkColors.actionFill] and asserted to differ from the fill the mark is drawn in,
 * because a figure the same colour as its ground is the failure this replaces.
 *
 * Mask. Every earlier mark was asserted inside the central 66 circle, radius 33, on the grounds
 * that a launcher might cut a circle. This mark's corners sat 39.6 units out from 2026-09-15
 * through 2026-09-22, so that rule would have refused it. The rule was a proxy: the mask on the
 * phone this ships to was lifted off a neighbouring tile in a drawer screenshot and fits a
 * superellipse of exponent 3.05, and [MASK_EXPONENT] is that rounded down to 3.0 because the
 * smaller exponent is the tighter shape. A true circular mask would have clipped about 2% of the
 * original placement and would have taken the outer right-angle point off both corners;
 * design/brand/two-corners/gallery.html draws that. The 2026-09-24 refit (DESIGN.md section 9,
 * "Two corners, refit") closes that cost rather than accepting it: the corners now sit 32.53
 * units out, inside not only [MASK_EXPONENT]'s superellipse but also the plain 36-unit circle
 * ([VISIBLE_HALF]) a "Pixel-style" launcher cuts, so a circular mask frames this mark instead of
 * bevelling it.
 */
class BrandAssetsTest {

    private companion object {
        /** The thinnest a shape of the 108 viewport may be: 6 units is 4dp at 48dp. */
        const val MIN_STROKE = 6.0

        /**
         * The superellipse a launcher cuts out of the visible 72, as |x|^n + |y|^n = 1.
         *
         * 3.05 was measured off the Seeker's own drawer; 3.0 is the tighter shape, so a mark that
         * clears this clears the phone. 2.0 would be a plain circle and this mark does not clear it.
         */
        const val MASK_EXPONENT = 3.0

        /** A launcher shows only the central 72 of the 108 viewport, and masks that. */
        const val VISIBLE_HALF = 36.0

        /** Android 12's splash icon with an icon background: 240dp, of which a 160dp circle shows. */
        const val SPLASH_ICON_DP = 240.0
        const val SPLASH_CIRCLE_DP = 160.0
    }

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private val module: File = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }
    private val res = File(module, "src/main/res")

    private fun parse(file: File): Document {
        assertTrue("missing ${file.path}", file.isFile)
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
    }

    private fun Document.elements(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    private fun Element.android(attribute: String): String = getAttributeNS(androidNs, attribute)

    /** The token as the upper-case `#RRGGBB` literal a resource file carries. */
    private fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    // ---- Adaptive icon ------------------------------------------------------------------------

    @Test
    fun `both launcher icons are adaptive with background, foreground and monochrome layers`() {
        listOf("ic_launcher.xml", "ic_launcher_round.xml").forEach { name ->
            val icon = parse(File(res, "mipmap-anydpi-v26/$name"))
            assertEquals(name, "adaptive-icon", icon.documentElement.tagName)
            assertEquals(name, "@color/ic_launcher_background", icon.elements("background").single().android("drawable"))
            assertEquals(name, "@drawable/ic_launcher_foreground", icon.elements("foreground").single().android("drawable"))
            assertEquals(name, "@drawable/ic_launcher_monochrome", icon.elements("monochrome").single().android("drawable"))
        }
    }

    @Test
    fun `no template launcher bitmaps or drawables remain`() {
        val mipmaps = res.listFiles { f -> f.isDirectory && f.name.startsWith("mipmap") }.orEmpty()
        val leftovers = mipmaps.flatMap { dir -> dir.listFiles().orEmpty().toList() }
            .filterNot { it.parentFile.name == "mipmap-anydpi-v26" && it.extension == "xml" }
        assertEquals(emptyList<File>(), leftovers)
        assertFalse(File(res, "drawable/ic_launcher_background.xml").exists())
    }

    @Test
    fun `launcher background is amber's own action token and the mark on it is amber's own ground`() {
        // The ground is the part four rejected attempts got wrong: a near-black tile has no
        // boundary at all against this phone's drawer wallpaper, 1.03 to 1 measured. The tile is
        // AmberDarkColors.actionFill now (2026-09-24, "Two corners, refit": the amber_ink tile
        // that briefly played this role, 2026-09-15 through 2026-09-22, is retired in turn).
        assertEquals(hex(AmberDarkColors.actionFill), color("ic_launcher_background"))
        assertEquals(hex(AmberDarkColors.actionFill), color("amber_action"))
        assertEquals(hex(AmberDarkColors.surfaceGround), color("amber_ground"))
        // And the mark has to be a figure on that ground rather than the same colour as it.
        assertEquals(setOf(hex(AmberDarkColors.surfaceGround)), vector("ic_launcher_foreground.xml").fills.toSet())
    }

    /** A color resource with `@color/` aliases followed. */
    private fun color(name: String): String {
        return color(name, "values")
    }

    private fun color(name: String, folder: String): String {
        val colors = File(res, "values").listFiles { f -> f.extension == "xml" }.orEmpty()
            .flatMap { parse(it).elements("color") }
            .associate { it.getAttribute("name") to it.textContent.trim() } +
            File(res, folder).takeIf { folder != "values" }?.listFiles { f -> f.extension == "xml" }.orEmpty()
                .flatMap { parse(it).elements("color") }
                .associate { it.getAttribute("name") to it.textContent.trim() }
        var value = colors[name] ?: error("no color resource named $name")
        while (value.startsWith("@color/")) value = colors.getValue(value.removePrefix("@color/"))
        return value
    }

    // ---- Vectors ------------------------------------------------------------------------------

    private class Vector(val size: Int, val paths: List<Element>) {
        val fills: List<String> get() = paths.map { it.getAttributeNS(NS, "fillColor") }
        val data: List<String> get() = paths.map { it.getAttributeNS(NS, "pathData") }

        companion object {
            const val NS = "http://schemas.android.com/apk/res/android"
        }
    }

    private fun vector(name: String): Vector {
        val doc = parse(File(res, "drawable/$name"))
        val root = doc.documentElement
        assertEquals(name, "vector", root.tagName)
        val size = root.android("width").removeSuffix("dp").toInt()
        assertEquals("$name height", "${size}dp", root.android("height"))
        assertEquals("$name viewport", size.toDouble(), root.android("viewportWidth").toDouble(), 0.0)
        assertEquals("$name viewport", size.toDouble(), root.android("viewportHeight").toDouble(), 0.0)
        return Vector(size, doc.elements("path"))
    }

    /**
     * Every point of a path written with absolute commands only, control points included: the
     * safe-zone circle is convex, so a curve whose control points lie inside it lies inside too.
     */
    private fun points(pathData: String): List<Pair<Double, Double>> {
        assertFalse("relative path commands are not walked: $pathData", pathData.any { it.isLowerCase() })
        val tokens = Regex("""[MLHVQCZ]|-?(?:\d*\.\d+|\d+)""").findAll(pathData).map { it.value }.toList()
        assertEquals("unparsed characters in $pathData", pathData.replace(Regex("""[\s,]"""), ""), tokens.joinToString(""))
        val out = mutableListOf<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        var command = ""
        var i = 0
        fun next(): Double = tokens[i++].toDouble()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) {
                command = tokens[i++]
                // Z closes the subpath and moves the current point back to its start.
                if (command == "Z") { x = startX; y = startY }
                continue
            }
            when (command) {
                "M" -> { x = next(); y = next(); startX = x; startY = y; out += x to y }
                "L" -> { x = next(); y = next(); out += x to y }
                "H" -> { x = next(); out += x to y }
                "V" -> { y = next(); out += x to y }
                "Q" -> { out += next() to next(); x = next(); y = next(); out += x to y }
                "C" -> { out += next() to next(); out += next() to next(); x = next(); y = next(); out += x to y }
                else -> fail("unsupported path command '$command' in $pathData")
            }
        }
        assertTrue("empty path: $pathData", out.isNotEmpty())
        return out
    }

    /** A rectangle of the mark, in viewport units. Radius 0: the shape lock lives in the art. */
    private data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val width get() = right - left
        val height get() = bottom - top
        val thinnest get() = min(width, height)
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
    }

    /** Every path of the mark is an axis-aligned rectangle; a path that is not fails here. */
    private fun box(pathData: String): Box {
        val corners = points(pathData)
        assertEquals("not the four corners of a rectangle: $pathData", 4, corners.size)
        val box = Box(corners.minOf { it.first }, corners.minOf { it.second },
                      corners.maxOf { it.first }, corners.maxOf { it.second })
        assertEquals(
            "not an axis-aligned rectangle: $pathData",
            setOf(box.left to box.top, box.right to box.top, box.right to box.bottom, box.left to box.bottom),
            corners.toSet(),
        )
        return box
    }

    private fun Vector.boxes(): List<Box> = data.map(::box)

    @Test
    fun `foreground is two registration corners with an empty centre between them`() {
        val fg = vector("ic_launcher_foreground.xml")
        assertEquals(108, fg.size)
        // Four rectangles, all in Amber's own dark ground; the tile under them is Amber's own
        // action token as of the 2026-09-24 refit (a separate layer, res/values, not this
        // vector), and DESIGN.md section 2 still allows one accent, which this mark's own paths
        // still use none of: nothing here may be either theme's action.fill itself, the same rule
        // this test pinned against Instrument's retired Accent before the colour pair moved.
        assertEquals(4, fg.paths.size)
        assertFalse("the mark carries an accent it was not drawn with", fg.fills.contains(hex(AmberDarkColors.actionFill)))
        assertFalse("the mark carries an accent it was not drawn with", fg.fills.contains(hex(AmberLightColors.actionFill)))

        val (topAcross, topDown, bottomAcross, bottomDown) = fg.boxes()
        // Each corner is two arms meeting at one vertex: the top-left pair share (31, 31), the
        // bottom-right pair share (77, 77) (2026-09-24: pulled in from (26, 26)/(82, 82) by the
        // refit). An L, twice, and nothing joining them.
        assertEquals("the top-left arms do not share a vertex", topAcross.left, topDown.left, 0.011)
        assertEquals("the top-left arms do not share a vertex", topAcross.top, topDown.top, 0.011)
        assertEquals("the bottom-right arms do not share a vertex", bottomAcross.right, bottomDown.right, 0.011)
        assertEquals("the bottom-right arms do not share a vertex", bottomAcross.bottom, bottomDown.bottom, 0.011)
        // The two corners are opposite, not adjacent: one starts where the other ends.
        assertTrue("the corners are not on opposite diagonals", topAcross.right < bottomDown.left)
        assertTrue("the corners are not on opposite diagonals", topDown.bottom < bottomAcross.top)
        // And the middle is empty. This is the mark: the place is kept and nothing is printed in
        // it, which is what the app does below the liquidity floor of DESIGN.md section 1.1.
        // 22x22 as of the 2026-09-24 refit (28x28 before it): the block pulled in with the refit,
        // and the kept centre pulled in with the block, in roughly the same proportion (28 of 56
        // before, 22 of 46 now).
        val middle = Box(topDown.right, topAcross.bottom, bottomDown.left, bottomAcross.top)
        assertEquals("the empty centre is not centred", 54.0, middle.centerX, 0.011)
        assertEquals("the empty centre is not centred", 54.0, middle.centerY, 0.011)
        assertTrue("the centre is not empty enough to read as kept: $middle", middle.thinnest >= 20.0)
    }

    @Test
    fun `the mark turns onto itself but does not mirror, which is what holds the two corners together`() {
        // Rotating 180 degrees about the centre maps the top-left corner onto the bottom-right
        // one exactly. That is what makes the pair read as one object across an empty middle
        // rather than as two unrelated brackets, and it is also why the mark is not a plus: a
        // shape can be rotationally symmetric and still have nowhere a mirror line can go.
        val boxes = vector("ic_launcher_foreground.xml").boxes()
        val order = compareBy<Box>({ it.left }, { it.top }, { it.right }, { it.bottom })
        val turned = boxes
            .map { Box(108.0 - it.right, 108.0 - it.bottom, 108.0 - it.left, 108.0 - it.top) }
            .sortedWith(order)
        boxes.sortedWith(order).zip(turned).forEach { (a, b) ->
            assertEquals("the mark is not the same turned 180 degrees", a.left, b.left, 0.011)
            assertEquals("the mark is not the same turned 180 degrees", a.top, b.top, 0.011)
            assertEquals("the mark is not the same turned 180 degrees", a.right, b.right, 0.011)
            assertEquals("the mark is not the same turned 180 degrees", a.bottom, b.bottom, 0.011)
        }
    }

    @Test
    fun `every shape of the launcher mark survives being seen at 48dp and inside the mask`() {
        val fg = vector("ic_launcher_foreground.xml")
        val boxes = fg.boxes()
        boxes.forEach {
            val dp = it.thinnest * 48.0 / 72.0
            assertTrue("a $dp dp shape is not there at 48dp: $it", it.thinnest >= MIN_STROKE)
        }
        // The mask, not the circle. Every earlier mark was asserted inside the central 66 circle;
        // the original placement's corners were 39.6 units out, and the circle was only ever a
        // proxy for the superellipse a launcher really cuts. 3.05 was measured off the phone and
        // 3.0 is tighter.
        fg.data.flatMap(::points).forEach { (x, y) ->
            val ax = abs(x - 54.0) / VISIBLE_HALF
            val ay = abs(y - 54.0) / VISIBLE_HALF
            val inside = ax.pow(MASK_EXPONENT) + ay.pow(MASK_EXPONENT)
            assertTrue(
                "($x, $y) is outside the superellipse of exponent $MASK_EXPONENT a launcher cuts: $inside",
                inside <= 1.0 + 1e-9,
            )
            // And still inside the 72 a launcher shows at all, mask or no mask.
            assertTrue("($x, $y) is outside the visible 72", ax <= 1.0 + 1e-9 && ay <= 1.0 + 1e-9)
        }
        // 2026-09-24, "Two corners, refit" (DESIGN.md section 9): every corner now sits inside
        // the plain 36-unit circle (VISIBLE_HALF) a "Pixel-style" launcher cuts too, not just the
        // tighter superellipse above, so a circular mask frames this mark instead of bevelling
        // the outer right-angle point off both corners the way the original placement's 39.6
        // units out used to.
        fg.data.flatMap(::points).forEach { (x, y) ->
            val fromCenter = hypot(x - 54.0, y - 54.0)
            assertTrue(
                "($x, $y) is $fromCenter units out, outside the 36-unit circle mask a launcher cuts",
                fromCenter <= VISIBLE_HALF,
            )
        }
        // And this pins how far in: comfortably inside even the deprecated 33-unit SAFE_RADIUS
        // every earlier mark (before "Two corners") was asserted against.
        val furthest = fg.data.flatMap(::points).maxOf { (x, y) -> hypot(x - 54.0, y - 54.0) }
        assertEquals("the furthest corner has moved off its own refit numbers", 32.53, furthest, 0.01)
        // And the block sits in the middle of the viewport, so no mask crops it unevenly.
        assertEquals(54.0, (boxes.minOf { it.left } + boxes.maxOf { it.right }) / 2, 0.011)
        assertEquals(54.0, (boxes.minOf { it.top } + boxes.maxOf { it.bottom }) / 2, 0.011)
    }

    @Test
    fun `no layer of the mark is a plus sign once the color is gone`() {
        // The first construction of the gauge this replaced put the track, the reference tick and
        // the token tick all on y 54, so the silhouette was a perfect cross. In color the Accent
        // tick separated and the mark read as the gauge; the monochrome layer and the notification
        // icon have no color to separate with and both read as a plus. The rejected construction is
        // kept for comparison in design/brand/candidates/crossed_*.xml, and the rule outlives the
        // mark that taught it.
        listOf(
            "ic_launcher_foreground.xml",
            "ic_launcher_monochrome.xml",
            "ic_brand_mark_tight.xml",
            "ic_stat_plainticker.xml",
        ).forEach { name ->
            val layer = vector(name)
            val middle = layer.size / 2.0
            val order = compareBy<Box>({ it.left }, { it.top }, { it.right }, { it.bottom })
            val here = layer.boxes().sortedWith(order)
            val flipped = layer.boxes()
                .map { Box(it.left, 2 * middle - it.bottom, it.right, 2 * middle - it.top) }
                .sortedWith(order)
            val mirrors = here.zip(flipped).all { (a, b) ->
                abs(a.left - b.left) < 0.011 && abs(a.top - b.top) < 0.011 &&
                    abs(a.right - b.right) < 0.011 && abs(a.bottom - b.bottom) < 0.011
            }
            assertFalse("$name mirrors itself top to bottom, so in one color it is a plus", mirrors)
        }
    }

    @Test
    fun `monochrome layer carries the same shapes in one color`() {
        val fg = vector("ic_launcher_foreground.xml")
        val mono = vector("ic_launcher_monochrome.xml")
        assertEquals(108, mono.size)
        // A themed icon is one color on a plate the launcher supplies, so the only thing that can
        // carry the mark is the mark: the same four rectangles, never the field they sit on. The
        // founder's gallery draws exactly this as the flat variant of the cell they chose.
        assertEquals(fg.data, mono.data)
        assertEquals(1, mono.fills.toSet().size)
    }

    @Test
    fun `the splash is the launcher icon itself, the dark corners on the amber tile`() {
        // 2026-09-29, the founder on the Seeker: a cold start showed "the old white logo", a cream
        // copy of the mark (ic_brand_mark) straight on the dark ground, beside the amber icon just
        // tapped. The splash now draws the launcher's own foreground layer inside a circle of the
        // launcher's own tile colour, so it is the icon, and the cream copy is gone.
        listOf(style("Theme.PlainTicker.Starting"), style("Theme.PlainTicker.Starting", "values-v31")).forEach { starting ->
            assertEquals("Theme.SplashScreen.IconBackground", starting.getAttribute("parent"))
            assertEquals("@drawable/ic_launcher_foreground", starting.item("windowSplashScreenAnimatedIcon"))
            assertEquals("@color/amber_action", starting.item("windowSplashScreenIconBackgroundColor"))
        }
        assertEquals(hex(AmberDarkColors.actionFill), color("amber_action"))
        assertEquals(color("ic_launcher_background"), color("amber_action"))
        assertFalse("the cream splash mark is back", File(res, "drawable/ic_brand_mark.xml").exists())
    }

    @Test
    fun `the splash mark sits inside the 160dp circle of the 240dp icon, so it is never clipped`() {
        // Android 12 and later draw a splash icon with a background at 240dp and keep only a
        // 160dp circle of it. The 108 viewport scales to 240dp, so the circle's 80dp radius is 36
        // viewport units from the centre: the same circle a launcher cuts, which is why the
        // 2026-09-24 refit already clears it.
        val scale = SPLASH_ICON_DP / 108.0
        vector("ic_launcher_foreground.xml").data.flatMap(::points).forEach { (x, y) ->
            val reach = hypot(x - 54.0, y - 54.0) * scale
            assertTrue("($x, $y) reaches ${"%.1f".format(reach)}dp, past the splash circle", reach <= SPLASH_CIRCLE_DP / 2)
        }
    }

    @Test
    fun `the splash and the plain window paint the page ground of the theme the system is in`() {
        assertEquals(hex(AmberLightColors.surfaceGround), color("window_ground"))
        assertEquals(hex(AmberDarkColors.surfaceGround), color("window_ground", "values-night"))
        assertEquals(hex(AmberDarkColors.surfaceGround), color("amber_ground"))
        listOf(
            style("Theme.PlainTicker.Starting"),
            style("Theme.PlainTicker.Starting", "values-v31"),
        ).forEach { assertEquals("@color/window_ground", it.item("windowSplashScreenBackground")) }
        assertEquals("@color/window_ground", style("Theme.PlainTicker").item("android:windowBackground"))
        assertEquals("@color/window_ground", style("Theme.PlainTicker").item("android:colorBackground"))
    }

    @Test
    fun `the android 12 starting theme sets the platform's own attributes to the same values`() {
        val base = style("Theme.PlainTicker.Starting")
        val v31 = style("Theme.PlainTicker.Starting", "values-v31")
        listOf(
            "windowSplashScreenBackground",
            "windowSplashScreenAnimatedIcon",
            "windowSplashScreenIconBackgroundColor",
            "postSplashScreenTheme",
            "android:windowLightStatusBar",
            "android:windowLightNavigationBar",
        ).forEach { assertEquals(it, base.item(it), v31.item(it)) }
        listOf(
            "windowSplashScreenBackground",
            "windowSplashScreenAnimatedIcon",
            "windowSplashScreenIconBackgroundColor",
        ).forEach { assertEquals("android:$it", base.item(it), v31.item("android:$it")) }
    }

    @Test
    fun `notification small icon is the same mark in white inside the 20dp live area`() {
        val stat = vector("ic_stat_plainticker.xml")
        assertEquals(24, stat.size)
        assertEquals(4, stat.paths.size)
        assertEquals(setOf("#FFFFFF"), stat.fills.toSet())
        // A 24dp status bar icon keeps 2dp of padding on every side (the 20dp live area).
        stat.data.flatMap(::points).forEach { (x, y) ->
            assertTrue("($x, $y) is outside the 20dp live area", x >= 2.0 && x <= 22.0 && y >= 2.0 && y <= 22.0)
        }
        // It gets one color, so the shape is all it has. It is the launcher mark scaled to fill
        // the live area, never a redrawn simplification that can drift away from the icon.
        val launcher = vector("ic_launcher_foreground.xml").boxes()
        val scale = 20.0 / (launcher.maxOf { it.right } - launcher.minOf { it.left })
        stat.boxes().zip(launcher).forEach { (small, big) ->
            assertEquals("width", big.width * scale, small.width, 0.011)
            assertEquals("height", big.height * scale, small.height, 0.011)
            assertEquals("across", 12.0 + (big.centerX - 54.0) * scale, small.centerX, 0.011)
            assertEquals("down", 12.0 + (big.centerY - 54.0) * scale, small.centerY, 0.011)
        }
        // 2 of the 24 viewport is 2dp in the status bar, the floor for a silhouette.
        stat.boxes().forEach { assertTrue("$it is thinner than 2dp in the status bar", it.thinnest >= 2.0) }
    }

    /**
     * The TopBar lockup's mark (2026-09-24). Drawn from the 108 launcher grid at a text size, the
     * glyph fills only its central 46 units, so it renders at about 43 percent of its own box: the
     * lesson the web's TopNav learned first. The lockup's drawable is the same four rectangles
     * moved to the origin of a viewport exactly as big as their block, so the glyph is the box,
     * in white for the TopBar to tint.
     */
    @Test
    fun `the topbar mark is the launcher mark cropped tight to its own block`() {
        val tight = vector("ic_brand_mark_tight.xml")
        val launcher = vector("ic_launcher_foreground.xml").boxes()
        val left = launcher.minOf { it.left }
        val top = launcher.minOf { it.top }
        val side = launcher.maxOf { it.right } - left
        assertEquals("the viewport is the block, not the launcher grid", side, tight.size.toDouble(), 0.0)
        assertEquals(setOf("#FFFFFF"), tight.fills.toSet())
        val boxes = tight.boxes()
        assertEquals(launcher.size, boxes.size)
        boxes.zip(launcher).forEach { (small, big) ->
            assertEquals("across", big.left - left, small.left, 0.011)
            assertEquals("down", big.top - top, small.top, 0.011)
            assertEquals("width", big.width, small.width, 0.011)
            assertEquals("height", big.height, small.height, 0.011)
        }
        // The glyph touches all four edges: nothing of the box is margin.
        assertEquals(0.0, boxes.minOf { it.left }, 0.0)
        assertEquals(0.0, boxes.minOf { it.top }, 0.0)
        assertEquals(tight.size.toDouble(), boxes.maxOf { it.right }, 0.0)
        assertEquals(tight.size.toDouble(), boxes.maxOf { it.bottom }, 0.0)
    }

    // ---- Splash and manifest ------------------------------------------------------------------

    private fun style(name: String, folder: String = "values"): Element =
        parse(File(res, "$folder/themes.xml")).elements("style").single { it.getAttribute("name") == name }

    private fun bool(name: String, folder: String): String =
        parse(File(res, "$folder/bools.xml")).elements("bool").single { it.getAttribute("name") == name }.textContent.trim()

    private fun Element.item(name: String): String =
        getElementsByTagName("item").let { l -> (0 until l.length).map { l.item(it) as Element } }
            .single { it.getAttribute("name") == name }.textContent.trim()

    @Test
    fun `starting theme hands over to the app theme`() {
        assertEquals("@style/Theme.PlainTicker", style("Theme.PlainTicker.Starting").item("postSplashScreenTheme"))
    }

    @Test
    fun `system bar icons suit the ground of the theme the system is in`() {
        // Theme.SplashScreen is DayNight, so the icons are pinned by the same day/night split the
        // ground is: dark icons on the light ground, light icons on the dark one.
        listOf(
            style("Theme.PlainTicker.Starting"),
            style("Theme.PlainTicker.Starting", "values-v31"),
            style("Theme.PlainTicker"),
        ).forEach {
            assertEquals("@bool/window_light_bars", it.item("android:windowLightStatusBar"))
            assertEquals("@bool/window_light_bars", it.item("android:windowLightNavigationBar"))
        }
        assertEquals("true", bool("window_light_bars", "values"))
        assertEquals("false", bool("window_light_bars", "values-night"))
    }

    @Test
    fun `manifest wires the icon, the label and the two themes`() {
        val manifest = parse(File(module, "src/main/AndroidManifest.xml"))
        val application = manifest.elements("application").single()
        assertEquals("@mipmap/ic_launcher", application.android("icon"))
        assertEquals("@mipmap/ic_launcher_round", application.android("roundIcon"))
        assertEquals("@string/app_name", application.android("label"))
        assertEquals("@style/Theme.PlainTicker", application.android("theme"))
        val activity = manifest.elements("activity").single { it.android("name") == ".MainActivity" }
        assertEquals("@style/Theme.PlainTicker.Starting", activity.android("theme"))

        val strings = parse(File(res, "values/strings.xml")).elements("string")
        assertEquals("PlainTicker", strings.single { it.getAttribute("name") == "app_name" }.textContent)
    }

    @Test
    fun `main activity installs the splash screen before super onCreate`() {
        val source = File(module, "src/main/java/com/plainticker/mobile/MainActivity.kt").readText()
        assertTrue(source.contains("import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen"))
        val install = source.indexOf("installSplashScreen()")
        val onCreate = source.indexOf("super.onCreate(savedInstanceState)")
        assertTrue(install in 1 until onCreate)
    }
}
