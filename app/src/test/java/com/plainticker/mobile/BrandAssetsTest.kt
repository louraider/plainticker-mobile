package com.plainticker.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.plainticker.mobile.ui.theme.Accent
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.Ink
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
 * launcher icon is the "Two corners" mark in Canvas over an Ink tile, carries a monochrome layer
 * with the same shapes, keeps every coordinate inside the mask a launcher actually cuts, and no
 * template bitmap is left in a mipmap folder. The splash theme paints Canvas behind the same
 * rectangles in Ink with light system bar icons and hands over to the app theme; the notification
 * icon is the same mark in white. Colors are compared with the Kotlin tokens, not with copied
 * literals, so a change to [Canvas], [Ink] or [Accent] cannot leave the icon behind.
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
 * wallpaper, where it measures 1.03 to 1 across its own edge and is not a tile at all. The tile is
 * Ink now and the mark on it is Canvas; that pair measures 15.48 to 1 in the same drawer. So the
 * background layer is asserted against the [Ink] token and asserted to differ from the fill the
 * mark is drawn in, because a figure the same colour as its ground is the failure this replaces.
 *
 * Mask. Every earlier mark was asserted inside the central 66 circle, radius 33, on the grounds
 * that a launcher might cut a circle. This mark's corners sit 39.6 units out, so that rule would
 * refuse it. The rule was a proxy: the mask on the phone this ships to was lifted off a
 * neighbouring tile in a drawer screenshot and fits a superellipse of exponent 3.05, and
 * [MASK_EXPONENT] is that rounded down to 3.0 because the smaller exponent is the tighter shape. A
 * true circular mask would clip about 2% of this mark and would take the outer right-angle point
 * off both corners; design/brand/two-corners/gallery.html draws that, and it is the cost of the
 * arrangement the founder chose.
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
    fun `launcher background is the ink token and the mark on it is not`() {
        // The ground is the part four rejected attempts got wrong: a Canvas tile has no boundary
        // at all against this phone's drawer wallpaper, 1.03 to 1 measured. The tile is Ink now.
        assertEquals(hex(Ink), color("ic_launcher_background"))
        assertEquals(hex(Ink), color("ink"))
        assertEquals(hex(Canvas), color("canvas"))
        // And the mark has to be a figure on that ground rather than the same colour as it.
        assertEquals(setOf(hex(Canvas)), vector("ic_launcher_foreground.xml").fills.toSet())
    }

    /** A color resource with `@color/` aliases followed. */
    private fun color(name: String): String {
        val colors = File(res, "values").listFiles { f -> f.extension == "xml" }.orEmpty()
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
        // Four rectangles, all in Canvas over the Ink tile. DESIGN.md section 2 allows one accent
        // and this mark uses none, so nothing here may be Accent.
        assertEquals(4, fg.paths.size)
        assertFalse("the mark carries an accent it was not drawn with", fg.fills.contains(hex(Accent)))

        val (topAcross, topDown, bottomAcross, bottomDown) = fg.boxes()
        // Each corner is two arms meeting at one vertex: the top-left pair share (26, 26), the
        // bottom-right pair share (82, 82). An L, twice, and nothing joining them.
        assertEquals("the top-left arms do not share a vertex", topAcross.left, topDown.left, 0.011)
        assertEquals("the top-left arms do not share a vertex", topAcross.top, topDown.top, 0.011)
        assertEquals("the bottom-right arms do not share a vertex", bottomAcross.right, bottomDown.right, 0.011)
        assertEquals("the bottom-right arms do not share a vertex", bottomAcross.bottom, bottomDown.bottom, 0.011)
        // The two corners are opposite, not adjacent: one starts where the other ends.
        assertTrue("the corners are not on opposite diagonals", topAcross.right < bottomDown.left)
        assertTrue("the corners are not on opposite diagonals", topDown.bottom < bottomAcross.top)
        // And the middle is empty. This is the mark: the place is kept and nothing is printed in
        // it, which is what the app does below the liquidity floor of DESIGN.md section 1.1.
        val middle = Box(topDown.right, topAcross.bottom, bottomDown.left, bottomAcross.top)
        assertEquals("the empty centre is not centred", 54.0, middle.centerX, 0.011)
        assertEquals("the empty centre is not centred", 54.0, middle.centerY, 0.011)
        assertTrue("the centre is not empty enough to read as kept: $middle", middle.thinnest >= 24.0)
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
        // this one's corners are 39.6 units out, and the circle was only ever a proxy for the
        // superellipse a launcher really cuts. 3.05 was measured off the phone and 3.0 is tighter.
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
        // The furthest corner is outside the 33 circle on purpose, and this pins how far: a
        // launcher that cuts a true circle bevels the outer point of both corners, and that cost
        // is accepted rather than discovered. Past 36 the corner would leave the visible 72.
        val furthest = fg.data.flatMap(::points).maxOf { (x, y) -> hypot(x - 54.0, y - 54.0) }
        assertTrue("the mark no longer reaches past the 33 circle: $furthest", furthest > 33.0)
        assertTrue("the mark has been pushed out past the visible 72: $furthest", furthest <= 39.61)
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
            "ic_brand_mark.xml",
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
    fun `the splash draws the same shapes in ink, because the foreground is drawn for a light tile`() {
        // The splash paints Canvas and then this vector over it. It cannot be the adaptive icon's
        // foreground layer any more: that layer is Canvas, for the Ink tile the launcher shows,
        // and Canvas on Canvas is nothing. Same rectangles, different color, one generator.
        val fg = vector("ic_launcher_foreground.xml")
        val brand = vector("ic_brand_mark.xml")
        assertEquals(108, brand.size)
        assertEquals(fg.data, brand.data)
        assertEquals(setOf(hex(Ink)), brand.fills.toSet())
        assertEquals(hex(Canvas), color("canvas"))
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

    // ---- Splash and manifest ------------------------------------------------------------------

    private fun style(name: String): Element =
        parse(File(res, "values/themes.xml")).elements("style").single { it.getAttribute("name") == name }

    private fun Element.item(name: String): String =
        getElementsByTagName("item").let { l -> (0 until l.length).map { l.item(it) as Element } }
            .single { it.getAttribute("name") == name }.textContent.trim()

    @Test
    fun `starting theme paints canvas behind the brand mark then hands over to the app theme`() {
        val starting = style("Theme.PlainTicker.Starting")
        assertEquals("Theme.SplashScreen", starting.getAttribute("parent"))
        assertEquals("@color/canvas", starting.item("windowSplashScreenBackground"))
        assertEquals("@drawable/ic_brand_mark", starting.item("windowSplashScreenAnimatedIcon"))
        assertEquals("@style/Theme.PlainTicker", starting.item("postSplashScreenTheme"))
        assertEquals("@color/canvas", style("Theme.PlainTicker").item("android:windowBackground"))
    }

    @Test
    fun `starting theme keeps light system bar icons over canvas whatever the system theme`() {
        // Theme.SplashScreen is DayNight: without these, a light system theme gets dark icons on Canvas.
        val starting = style("Theme.PlainTicker.Starting")
        assertEquals("false", starting.item("android:windowLightStatusBar"))
        assertEquals("false", starting.item("android:windowLightNavigationBar"))
        assertEquals("false", style("Theme.PlainTicker").item("android:windowLightStatusBar"))
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
