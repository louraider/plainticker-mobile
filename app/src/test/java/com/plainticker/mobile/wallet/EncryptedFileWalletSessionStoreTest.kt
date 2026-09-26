package com.plainticker.mobile.wallet

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The saved wallet session on disk: the same AES-256-GCM the phone runs over its Keystore key,
 * here over an in-memory key, since the JVM has no AndroidKeyStore. What is pinned is what matters
 * about the file: it round-trips, it carries neither the token nor the address in the clear, and a
 * file that does not open (edited, or sealed under another key, as on another phone) reads as no
 * session and is removed.
 */
class EncryptedFileWalletSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val account = testAccount(fill = 5, label = "Seeker")
    private val token = "mwa-auth-token-issued-by-the-wallet-0123456789"

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()

    private fun store(file: java.io.File, k: SecretKey = key, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        EncryptedFileWalletSessionStore(file, AesGcmSessionCipher { k }, dispatcher)

    @Test
    fun `a saved session reads back exactly, and nothing in the file is readable`() = runTest {
        val file = folder.root.resolve(EncryptedFileWalletSessionStore.FILE_NAME)
        val s = store(file, dispatcher = StandardTestDispatcher(testScheduler))
        assertNull("nothing saved yet", s.load())

        s.save(SavedWalletSession(token, account))

        assertEquals(SavedWalletSession(token, account), s.load())
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("the token is not in the clear", token in raw)
        assertFalse("nor is the address", account.address in raw)
        assertFalse("nor the label", "Seeker" in raw)
        assertFalse("no temporary file is left", folder.root.resolve(EncryptedFileWalletSessionStore.FILE_NAME + ".tmp").exists())
    }

    @Test
    fun `two saves of the same session are sealed differently, and the second wins`() = runTest {
        val file = folder.root.resolve("a.bin")
        val s = store(file, dispatcher = StandardTestDispatcher(testScheduler))
        s.save(SavedWalletSession(token, account))
        val first = file.readBytes()
        s.save(SavedWalletSession("second-token", account))
        assertFalse("a fresh IV each time", first.contentEquals(file.readBytes()))
        assertEquals("second-token", s.load()?.authToken)
    }

    @Test
    fun `an edited file reads as no session and is removed`() = runTest {
        val file = folder.root.resolve("b.bin")
        val s = store(file, dispatcher = StandardTestDispatcher(testScheduler))
        s.save(SavedWalletSession(token, account))
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        file.writeBytes(bytes)

        assertNull(s.load())
        assertFalse(file.exists())
    }

    @Test
    fun `a file sealed under another key, as on another phone, reads as no session`() = runTest {
        val file = folder.root.resolve("c.bin")
        store(file, dispatcher = StandardTestDispatcher(testScheduler)).save(SavedWalletSession(token, account))

        assertNull(store(file, newKey(), StandardTestDispatcher(testScheduler)).load())
        assertFalse(file.exists())
    }

    @Test
    fun `clear removes the file, and a garbage file is no session`() = runTest {
        val file = folder.root.resolve("d.bin")
        val s = store(file, dispatcher = StandardTestDispatcher(testScheduler))
        s.save(SavedWalletSession(token, account))
        assertTrue(file.exists())
        s.clear()
        assertFalse(file.exists())
        assertNull(s.load())

        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(s.load())
        assertFalse(file.exists())
    }
}
