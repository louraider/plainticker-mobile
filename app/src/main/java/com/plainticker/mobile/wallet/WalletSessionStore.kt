package com.plainticker.mobile.wallet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * What survives process death of a wallet session: the MWA auth token the wallet issued and the
 * account it authorized, never a key (judges' review, 2026-09-26; Beeman: every launch asked to
 * connect again).
 *
 * The token is a wallet-issued grant that lets this app ask the same wallet to reauthorize
 * without a fresh consent prompt. It signs nothing by itself: every transaction still opens the
 * wallet and waits for the person to approve it there. It is still a bearer grant, so it is kept
 * encrypted, in a file excluded from cloud backup and device transfer (res/xml/backup_rules.xml,
 * res/xml/data_extraction_rules.xml), and dropped the moment the wallet rejects it or the reader
 * disconnects.
 */
data class SavedWalletSession(val authToken: String, val account: WalletAccount)

interface WalletSessionStore {
    /** The saved session, or null when there is none or it cannot be read back. */
    suspend fun load(): SavedWalletSession?

    suspend fun save(session: SavedWalletSession)

    suspend fun clear()

    companion object {
        /** Keeps nothing: a session that ends with the process, as before persistence. */
        val NONE: WalletSessionStore = object : WalletSessionStore {
            override suspend fun load(): SavedWalletSession? = null
            override suspend fun save(session: SavedWalletSession) = Unit
            override suspend fun clear() = Unit
        }
    }
}

/**
 * AES-256-GCM over a key that [key] supplies: on the phone, one generated inside the Android
 * Keystore ([androidKeystoreKey]), which never leaves the secure hardware and is not exportable,
 * so a copy of the file on another device, or in a backup, cannot be opened. The IV is the one
 * the cipher chose (the Keystore requires randomized encryption) and is stored in front of the
 * ciphertext; GCM's tag makes any edit to the file fail to open rather than decrypt to garbage.
 *
 * Why the Keystore directly and not EncryptedSharedPreferences: androidx.security:security-crypto
 * is deprecated and would be a new dependency for what is one value, while this is the same
 * Keystore-backed AES-GCM it wraps, in thirty lines this repository can read and test on the JVM
 * with an in-memory key.
 */
class AesGcmSessionCipher(private val key: () -> SecretKey) {

    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == IV_BYTES)
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > 1 + IV_BYTES && sealed[0] == VERSION)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 1, IV_BYTES))
        return cipher.doFinal(sealed, 1 + IV_BYTES, sealed.size - 1 - IV_BYTES)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val VERSION: Byte = 1

        const val KEY_ALIAS = "plainticker.wallet_session.v1"

        /**
         * The Keystore key under [alias], created on first use: AES-256, GCM, no user auth.
         * [onCreated] runs when there was none and one was just made, which is how a sealed value
         * learns that the key it was sealed under is gone (security review M3).
         */
        fun androidKeystoreKey(alias: String = KEY_ALIAS, onCreated: () -> Unit = {}): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            onCreated()
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

/**
 * The session sealed with [cipher] in [file]. A file that cannot be opened (another device's key,
 * a cleared Keystore, an edit) reads as no session and is deleted, so the next launch simply asks
 * to connect, which is where every launch stood before persistence.
 */
class EncryptedFileWalletSessionStore(
    private val file: File,
    private val cipher: AesGcmSessionCipher,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WalletSessionStore {

    @Serializable
    private data class Stored(val v: Int = 1, val authToken: String, val publicKey: String, val label: String? = null)

    override suspend fun load(): SavedWalletSession? = withContext(io) {
        if (!file.isFile) return@withContext null
        val stored = runCatching {
            json.decodeFromString(Stored.serializer(), cipher.open(file.readBytes()).decodeToString())
        }.getOrNull()
        val key = stored?.let { runCatching { Base64.getDecoder().decode(it.publicKey) }.getOrNull() }
        if (stored == null || key == null || key.size != 32 || stored.authToken.isBlank()) {
            file.delete()
            return@withContext null
        }
        SavedWalletSession(stored.authToken, WalletAccount(key, stored.label))
    }

    override suspend fun save(session: SavedWalletSession) = withContext(io) {
        val plain = json.encodeToString(
            Stored.serializer(),
            Stored(
                authToken = session.authToken,
                publicKey = Base64.getEncoder().encodeToString(session.account.publicKey),
                label = session.account.label,
            ),
        ).encodeToByteArray()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(cipher.seal(plain))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    override suspend fun clear() = withContext(io) {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
        Unit
    }

    companion object {
        /** Under files/, excluded from backup and device transfer by name in both rule files. */
        const val FILE_NAME = "wallet_session.bin"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
