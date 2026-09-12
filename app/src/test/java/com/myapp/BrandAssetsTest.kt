package com.myapp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.myapp.ui.theme.Accent
import com.myapp.ui.theme.Canvas
import com.myapp.ui.theme.Ink
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Brand assets, DESIGN.md section 9 (DT3), read straight from disk like [ManifestTest]: the
 * adaptive launcher icon is Canvas behind the JetBrains Mono P in Ink with the Accent gauge tick,
 * carries a monochrome layer with the same shapes, keeps every coordinate inside the 66dp safe
 * zone, and no template bitmap is left in a mipmap folder. The splash theme paints Canvas behind
 * the same vector with light system bar icons and hands over to the app theme; the notification
 * icon is the glyph in white. Colors are compared with the Kotlin tokens, not with copied literals.
 */
class BrandAssetsTest {

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

    @Test
    fun `foreground is the ink glyph and the accent tick inside the safe zone`() {
        val fg = vector("ic_launcher_foreground.xml")
        assertEquals(108, fg.size)
        assertEquals(listOf(hex(Ink), hex(Accent)), fg.fills)

        val glyph = points(fg.data[0])
        val tick = points(fg.data[1])
        (glyph + tick).forEach { (x, y) ->
            val radius = hypot(x - 54.0, y - 54.0)
            assertTrue("($x, $y) is $radius from the center, outside the 33 safe radius", radius <= 33.0)
        }
        // A traced TrueType outline: quadratic curves and two contours (the bowl has a counter).
        assertTrue(fg.data[0].contains('Q'))
        assertEquals(2, fg.data[0].count { it == 'Z' })
        // The tick is a 2 high bar under the baseline, as wide as the glyph.
        val tickTop = tick.minOf { it.second }
        val tickBottom = tick.maxOf { it.second }
        assertEquals(2.0, tickBottom - tickTop, 0.011)
        assertTrue("tick overlaps the glyph", tickTop > glyph.maxOf { it.second })
        assertEquals(glyph.minOf { it.first }, tick.minOf { it.first }, 0.011)
        assertEquals(glyph.maxOf { it.first }, tick.maxOf { it.first }, 0.011)
    }

    @Test
    fun `monochrome layer carries the same shapes in one color`() {
        val fg = vector("ic_launcher_foreground.xml")
        val mono = vector("ic_launcher_monochrome.xml")
        assertEquals(108, mono.size)
        assertEquals(fg.data, mono.data)
        assertEquals(1, mono.fills.toSet().size)
    }

    @Test
    fun `notification small icon is the glyph in white inside the 20dp live area`() {
        val stat = vector("ic_stat_plainticker.xml")
        assertEquals(24, stat.size)
        assertEquals(2, stat.paths.size)
        assertEquals(setOf("#FFFFFF"), stat.fills.toSet())
        // A 24dp status bar icon keeps 2dp of padding on every side (the 20dp live area).
        stat.data.flatMap(::points).forEach { (x, y) ->
            assertTrue("($x, $y) is outside the 20dp live area", x >= 2.0 && x <= 22.0 && y >= 2.0 && y <= 22.0)
        }
        assertTrue(stat.data[0].contains('Q'))
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
        val source = File(module, "src/main/java/com/myapp/MainActivity.kt").readText()
        assertTrue(source.contains("import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen"))
        val install = source.indexOf("installSplashScreen()")
        val onCreate = source.indexOf("super.onCreate(savedInstanceState)")
        assertTrue(install in 1 until onCreate)
    }
}
