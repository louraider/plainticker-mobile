package com.plainticker.mobile.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DataStoreAccountStore]'s mapping over a Preferences DataStore: what goes in comes back, a later
 * save replaces what the new answer no longer carries, sign-out forgets all of it.
 *
 * The mapping tests run over an in-memory [DataStore]: DataStore's JVM file storage replaces the
 * file with `File.renameTo`, which Windows refuses once the target exists ("Unable to rename"),
 * so a second write to a real file cannot run on the Windows machine these tests run on. On the
 * device (Linux) the same rename is atomic. The real file is still exercised once, by the last
 * test, for the file's name and for what it must never contain.
 */
class DataStoreAccountStoreTest {

    /** A DataStore<Preferences> in memory, serialized the way DataStore serializes its writes. */
    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val lock = Mutex()
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private val store = DataStoreAccountStore(MemoryDataStore())

    private val dir: File = Files.createTempDirectory("account-store").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun `an empty store reads as signed out`() = runBlocking {
        assertNull(store.account.first())
    }

    @Test
    fun `a saved account reads back whole, wallets in order`() = runBlocking {
        val account = SignedInAccount(
            email = "ann@example.com",
            name = "Ann",
            linkedWallets = listOf("4Nd1mBQtrMJVYVfKf2PJy9NZUZdTAsp7D4xWLs4gDB4T", "9xQeWvG816bUx9EPjHmaT23yvVM2ZWbrrpZb9PusVFin"),
        )
        store.save(account)
        assertEquals(account, store.account.first())
    }

    @Test
    fun `no email, no name and no wallets is still a signed-in account`() = runBlocking {
        store.save(SignedInAccount(null, null, emptyList()))
        assertEquals(SignedInAccount(null, null, emptyList()), store.account.first())
    }

    @Test
    fun `saving again replaces a field the new answer no longer carries`() = runBlocking {
        store.save(SignedInAccount("ann@example.com", "Ann", listOf("A1")))
        store.save(SignedInAccount("ann@example.com", null, emptyList()))
        assertEquals(SignedInAccount("ann@example.com", null, emptyList()), store.account.first())
    }

    @Test
    fun `clear forgets everything`() = runBlocking {
        store.save(SignedInAccount("ann@example.com", "Ann", listOf("A1")))
        store.clear()
        assertNull(store.account.first())
    }

    @Test
    fun `on a real file, the account is its own file and holds no token or code`() = runBlocking {
        val file = File(dir, "${DataStoreAccountStore.FILE_NAME}.preferences_pb")
        val onDisk = DataStoreAccountStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        val account = SignedInAccount("ann@example.com", "Ann", listOf("A1"))
        onDisk.save(account)

        assertEquals(account, onDisk.account.first())
        assertTrue(file.isFile)
        assertEquals("account.preferences_pb", file.name)
        val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("no key names a token", "token" in bytes.lowercase())
        assertFalse("no key names the device code", "device_pass_code" in bytes)
    }
}
