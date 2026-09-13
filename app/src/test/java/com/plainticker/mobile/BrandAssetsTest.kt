package com.plainticker.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.plainticker.mobile.ui.theme.Accent
import com.plainticker.mobile.ui.theme.Canvas
import com.plainticker.mobile.ui.theme.Ink
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Brand assets, DESIGN.md section 9, read straight from disk like [ManifestTest]: the adaptive
 * launcher icon is Canvas behind the tracking gauge, an Ink track and reference tick with the
 * token tick in Accent, carries a monochrome layer with the same shapes, keeps every coordinate
 * inside the 66dp safe zone, and no template bitmap is left in a mipmap folder. The splash theme
 * paints Canvas behind the same vector with light system bar icons and hands over to the app
 * theme; the notification icon is the same mark in white. Colors are compared with the Kotlin
 * tokens, not with copied literals.
 *
 * The tests that matter here are the ones about size. An icon is read at 48dp, and a launcher
 * shows the central 72 of the 108 viewport, so one viewport unit is 0.667dp on a launcher grid.
 * The mark this replaced carried its only distinguishing detail in a 2 unit tick, 1.3dp at 48dp,
 * which nobody ever saw. [MIN_STROKE] is that lesson: no shape may be thinner than 4dp at 48dp.
 */
class BrandAssetsTest {

    private companion object {
        /** The thinnest a shape of the 108 viewport may be: 6 units is 4dp at 48dp. */
        const val MIN_STROKE = 6.0
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
    fun `launcher background resolves to canvas`() {
        assertEquals(hex(Canvas), color("ic_launcher_background"))
        assertEquals(hex(Canvas), color("canvas"))
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
    fun `foreground is the tracking gauge with the token tick in accent`() {
        val fg = vector("ic_launcher_foreground.xml")
        assertEquals(108, fg.size)
        // Two Ink shapes and exactly one Accent shape: DESIGN.md section 2 allows one accent.
        assertEquals(listOf(hex(Ink), hex(Ink), hex(Accent)), fg.fills)

        val (track, reference, token) = fg.boxes()
        // The track carries the scale, the reference tick stands on its center where the NYSE
        // close sits, and the token tick stands off it. That offset is the whole mark.
        assertTrue("the track is not the widest shape", track.width > reference.width + token.width)
        assertEquals("the reference is not on the center", 54.0, reference.centerX, 0.011)
        assertTrue("the token does not sit off the reference", token.left > reference.right)
        assertTrue("the token has left the track", token.left > track.left && token.right < track.right)
        listOf(reference, token).forEach {
            assertTrue("a tick does not cross the track", it.top < track.top && it.bottom > track.bottom)
        }
        assertTrue("the token does not read louder than the reference", token.height > reference.height)
    }

    @Test
    fun `every shape of the launcher mark survives being seen at 48dp`() {
        val fg = vector("ic_launcher_foreground.xml")
        val boxes = fg.boxes()
        boxes.forEach {
            val dp = it.thinnest * 48.0 / 72.0
            assertTrue("a $dp dp shape is not there at 48dp: $it", it.thinnest >= MIN_STROKE)
        }
        // The mask can be a circle, a squircle or a rounded square, so only the central 66dp
        // circle is guaranteed visible. Every corner is inside it.
        fg.data.flatMap(::points).forEach { (x, y) ->
            val radius = hypot(x - 54.0, y - 54.0)
            assertTrue("($x, $y) is $radius from the center, outside the 33 safe radius", radius <= 33.0)
        }
        // And the block sits in the middle of the viewport, so no mask crops it unevenly.
        assertEquals(54.0, (boxes.minOf { it.left } + boxes.maxOf { it.right }) / 2, 0.011)
        assertEquals(54.0, (boxes.minOf { it.top } + boxes.maxOf { it.bottom }) / 2, 0.011)
    }

    @Test
    fun `monochrome layer carries the same shapes in one color`() {
        val fg = vector("ic_launcher_foreground.xml")
        val mono = vector("ic_launcher_monochrome.xml")
        assertEquals(108, mono.size)
        // A themed icon has no color to lean on, so the shapes have to be the same ones: the
        // reference tick and the token tick differ by height and position, not by fill.
        assertEquals(fg.data, mono.data)
        assertEquals(1, mono.fills.toSet().size)
    }

    @Test
    fun `notification small icon is the same mark in white inside the 20dp live area`() {
        val stat = vector("ic_stat_plainticker.xml")
        assertEquals(24, stat.size)
        assertEquals(3, stat.paths.size)
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
    fun `starting theme paints canvas behind the launcher glyph then hands over to the app theme`() {
        val starting = style("Theme.PlainTicker.Starting")
        assertEquals("Theme.SplashScreen", starting.getAttribute("parent"))
        assertEquals("@color/canvas", starting.item("windowSplashScreenBackground"))
        assertEquals("@drawable/ic_launcher_foreground", starting.item("windowSplashScreenAnimatedIcon"))
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
