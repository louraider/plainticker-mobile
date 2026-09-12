package com.myapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Plan section 13 Pass 6 (DT5): the one Activity is portrait locked and handles the size
 * configuration changes itself, so the wallet round trip is never torn down by a rotation.
 */
class ManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private fun mainActivity(): Element {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File)
            .first { it.isFile }
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val activities = document.getElementsByTagName("activity")
        return (0 until activities.length)
            .map { activities.item(it) as Element }
            .single { it.getAttributeNS(androidNs, "name") == ".MainActivity" }
    }

    @Test
    fun `main activity is portrait locked`() {
        assertEquals("portrait", mainActivity().getAttributeNS(androidNs, "screenOrientation"))
    }

    @Test
    fun `main activity handles the size configuration changes in place`() {
        val changes = mainActivity().getAttributeNS(androidNs, "configChanges").split('|').toSet()
        listOf("orientation", "screenSize", "screenLayout", "smallestScreenSize").forEach {
            assertTrue("configChanges lacks $it", it in changes)
        }
    }
}
