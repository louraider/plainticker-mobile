package com.plainticker.mobile

import com.plainticker.mobile.ui.share.ShareImage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * The share image's provider (founder feedback 2026-09-29), read from the manifest and the paths
 * file the way [ManifestTest] and [BackupRulesTest] read theirs: an AndroidX FileProvider that is
 * not exported, grants a read per URI, answers to the authority [ShareImage] asks it for, and
 * serves the one cache folder [ShareImage.write] writes into, nothing else.
 */
class ShareProviderTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val module: File = listOf(".", "app").map(::File)
        .first { File(it, "src/main/AndroidManifest.xml").isFile }
        .canonicalFile

    private fun parse(path: String): Document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(module, path))

    private fun provider(): Element {
        val providers = parse("src/main/AndroidManifest.xml").getElementsByTagName("provider")
        return (0 until providers.length)
            .map { providers.item(it) as Element }
            .single { it.getAttributeNS(androidNs, "name") == "androidx.core.content.FileProvider" }
    }

    @Test
    fun `the provider is AndroidX's FileProvider, not exported, granting reads per URI`() {
        val provider = provider()
        assertEquals("false", provider.getAttributeNS(androidNs, "exported"))
        assertEquals("true", provider.getAttributeNS(androidNs, "grantUriPermissions"))
    }

    @Test
    fun `its authority is the one the share asks for, under this app's own id`() {
        assertEquals("\${applicationId}" + ShareImage.AUTHORITY_SUFFIX, provider().getAttributeNS(androidNs, "authorities"))
    }

    @Test
    fun `it serves the share folder under the cache and nothing else`() {
        val meta = provider().getElementsByTagName("meta-data").item(0) as Element
        assertEquals("android.support.FILE_PROVIDER_PATHS", meta.getAttributeNS(androidNs, "name"))
        assertEquals("@xml/share_paths", meta.getAttributeNS(androidNs, "resource"))

        val paths = parse("src/main/res/xml/share_paths.xml").documentElement
        val entries = (0 until paths.childNodes.length).map { paths.childNodes.item(it) }.filterIsInstance<Element>()
        assertEquals("one path, no files, external or root entry", 1, entries.size)
        val entry = entries.single()
        assertEquals("cache-path", entry.tagName)
        assertEquals(ShareImage.DIR + "/", entry.getAttribute("path"))
    }

    @Test
    fun `the image goes out as a png`() {
        assertEquals("image/png", ShareImage.MIME)
        assertTrue(ShareImage.DIR.isNotBlank())
    }
}
