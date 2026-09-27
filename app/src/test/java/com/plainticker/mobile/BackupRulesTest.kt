package com.plainticker.mobile

import com.plainticker.mobile.prefs.DataStoreAccountStore
import com.plainticker.mobile.prefs.SharedPrefsDevicePassStore
import com.plainticker.mobile.wallet.EncryptedFileWalletSessionStore
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * The files that act as credentials never leave the phone in a backup or a device transfer
 * (judges' review, 2026-09-26): the preferences file that keeps the device's Pro code, sent raw
 * in X-PT-Code; the signed-in account's DataStore; the saved wallet session. The expected names
 * come from the constants the app writes those files under, so renaming one without its rule
 * fails here.
 */
class BackupRulesTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val expected = setOf(
        "sharedpref:${DefaultAppContainer.PREFS_NAME}.xml",
        "file:datastore/${DataStoreAccountStore.FILE_NAME}.preferences_pb",
        "file:${EncryptedFileWalletSessionStore.FILE_NAME}",
        "file:${EncryptedFileWalletSessionStore.FILE_NAME}.tmp",
    )

    /** Gradle runs unit tests from the module directory; the fallback covers an IDE run from the root. */
    private fun parse(path: String): Document {
        val file = listOf("src/main/$path", "app/src/main/$path").map(::File).first { it.isFile }
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
    }

    private fun excludes(parent: Element): Set<String> {
        val nodes = parent.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }
            .toSet()
    }

    @Test
    fun `the manifest points at both rule files`() {
        val application = parse("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("@xml/backup_rules", application.getAttributeNS(androidNs, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(androidNs, "dataExtractionRules"))
    }

    @Test
    fun `Android 11 and lower back up everything but the credential files`() {
        val root = parse("res/xml/backup_rules.xml").documentElement
        assertEquals("full-backup-content", root.tagName)
        assertEquals(expected, excludes(root))
        assertEquals("no include list, so everything else is still backed up", 0, root.getElementsByTagName("include").length)
    }

    @Test
    fun `Android 12 and higher exclude the same files from cloud backup and from device transfer`() {
        val root = parse("res/xml/data_extraction_rules.xml").documentElement
        for (section in listOf("cloud-backup", "device-transfer")) {
            val nodes = root.getElementsByTagName(section)
            assertEquals(section, 1, nodes.length)
            val element = nodes.item(0) as Element
            assertEquals(section, expected, excludes(element))
            assertEquals(section, 0, element.getElementsByTagName("include").length)
        }
    }

    /**
     * The rekey (2026-09-27) added keys that are credentials too: the minted replacement code
     * waiting for the server's confirmation. They live in the device code's own preferences file,
     * which the rules above exclude whole; this pins that the container builds that store, and the
     * sign-out retry flag, on exactly that file, and that the store names every key it writes.
     */
    @Test
    fun `the rekey and sign-out keys live in the excluded preferences file`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }.canonicalFile
        val container = File(module, "src/main/java/com/plainticker/mobile/AppContainer.kt").readText()
        assertTrue("app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)" in container)
        assertTrue("SharedPrefsDevicePassStore(prefs, deviceCodeCipher)" in container)
        assertTrue("SharedPrefsPendingSignOutStore(prefs)" in container)
        assertEquals(
            setOf(
                SharedPrefsDevicePassStore.KEY_CODE,
                SharedPrefsDevicePassStore.KEY_PENDING_NEW_CODE,
                SharedPrefsDevicePassStore.KEY_REKEY_NOTE,
                SharedPrefsDevicePassStore.KEY_CODE_SEALED,
                SharedPrefsDevicePassStore.KEY_PENDING_NEW_CODE_SEALED,
            ),
            SharedPrefsDevicePassStore.ALL_KEYS,
        )
        assertTrue("sharedpref:${DefaultAppContainer.PREFS_NAME}.xml" in expected)
    }

    /**
     * The sealed codes (security review, 2026-09-27) live in that same excluded file, under keys
     * of their own, sealed with a Keystore key of their own that is not the wallet session's, so
     * neither the sealed blobs nor anything that could open them can reach a backup. Keystore keys
     * are never backed up at all; the file they seal into is excluded whole besides.
     */
    @Test
    fun `the sealed device code keys live in the excluded file, under their own Keystore key`() {
        val module = listOf(".", "app").map(::File).first { File(it, "src/main/AndroidManifest.xml").isFile }.canonicalFile
        val container = File(module, "src/main/java/com/plainticker/mobile/AppContainer.kt").readText()
        assertTrue(
            "AesGcmSessionCipher { AesGcmSessionCipher.androidKeystoreKey(SharedPrefsDevicePassStore.KEY_ALIAS) }" in container,
        )
        assertTrue(SharedPrefsDevicePassStore.KEY_ALIAS != com.plainticker.mobile.wallet.AesGcmSessionCipher.KEY_ALIAS)
        assertTrue(SharedPrefsDevicePassStore.KEY_CODE_SEALED in SharedPrefsDevicePassStore.ALL_KEYS)
        assertTrue(SharedPrefsDevicePassStore.KEY_PENDING_NEW_CODE_SEALED in SharedPrefsDevicePassStore.ALL_KEYS)
        for (rules in listOf("res/xml/backup_rules.xml", "res/xml/data_extraction_rules.xml")) {
            val root = parse(rules).documentElement
            assertTrue(rules, "sharedpref:${DefaultAppContainer.PREFS_NAME}.xml" in excludes(root))
        }
    }
}
