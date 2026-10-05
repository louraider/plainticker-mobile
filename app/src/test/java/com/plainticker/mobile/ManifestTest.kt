package com.plainticker.mobile

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

    /**
     * The dApp Store review refused USE_BIOMETRIC and USE_FINGERPRINT while a library merged them
     * in with nothing behind them (PER-001). Since 1.3.28 the optional app lock uses both, so the
     * manifest declares them itself, says what for, and removes only REORDER_TASKS (androidx.test).
     * release.yml's allowlist names the same two, so the release step passes with them.
     */
    @Test
    fun `the app lock's permissions are declared, and only the test library's is removed`() {
        val file = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.isFile }
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
        val toolsNs = "http://schemas.android.com/tools"
        val nodes = document.getElementsByTagName("uses-permission")
        val permissions = (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttributeNS(androidNs, "name") to it.getAttributeNS(toolsNs, "node") }
        assertEquals("", permissions["android.permission.USE_BIOMETRIC"])
        assertEquals("", permissions["android.permission.USE_FINGERPRINT"])
        assertEquals("remove", permissions["android.permission.REORDER_TASKS"])
        assertEquals(setOf("android.permission.REORDER_TASKS"), permissions.filterValues { it == "remove" }.keys)

        val workflow = listOf("../.github/workflows/release.yml", ".github/workflows/release.yml").map(::File).first { it.isFile }.readText()
        val allowlist = workflow.substringAfter("allowed=\"").substringBefore("\"").lines().map { it.trim() }.toSet()
        assertTrue(allowlist.containsAll(setOf("android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT")))
        assertTrue(permissions.filterValues { it != "remove" }.keys.all { it in allowlist })
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
