package com.plainticker.mobile.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * What You draws for a signed-in Google account, and nothing more: the email and name the server
 * returned, and the wallets linked to that account. No token of any kind is ever stored: the ID
 * token is spent on the one sign-in call and dropped, and the server keeps no session for the app
 * beyond the device binding it made.
 */
data class SignedInAccount(
    val email: String?,
    val name: String?,
    val linkedWallets: List<String>,
)

/**
 * The signed-in account, kept across launches. Sign-out is [clear]: the server contract defines
 * no sign-out call, so forgetting is local only.
 */
interface AccountStore {
    /** The stored account, or null when signed out. Emits the current value first. */
    val account: Flow<SignedInAccount?>

    suspend fun save(account: SignedInAccount)

    suspend fun clear()
}

/**
 * [AccountStore] on a Preferences DataStore of its own (`account.preferences_pb`), deliberately
 * not the `plainticker` SharedPreferences file that keeps the device code
 * ([SharedPrefsDevicePassStore]): clearing the account can never touch the code, and nothing that
 * reads the account can read the code by accident.
 */
class DataStoreAccountStore(private val store: DataStore<Preferences>) : AccountStore {

    override val account: Flow<SignedInAccount?> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            if (prefs[KEY_STATE] != STATE_SIGNED_IN) {
                null
            } else {
                SignedInAccount(
                    email = prefs[KEY_EMAIL],
                    name = prefs[KEY_NAME],
                    linkedWallets = prefs[KEY_WALLETS]?.split(WALLET_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
                )
            }
        }
        .distinctUntilChanged()

    override suspend fun save(account: SignedInAccount) {
        store.edit { prefs ->
            prefs[KEY_STATE] = STATE_SIGNED_IN
            account.email?.let { prefs[KEY_EMAIL] = it } ?: prefs.remove(KEY_EMAIL)
            account.name?.let { prefs[KEY_NAME] = it } ?: prefs.remove(KEY_NAME)
            prefs[KEY_WALLETS] = account.linkedWallets.joinToString(WALLET_SEPARATOR)
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    companion object {
        /** The DataStore file name, without its `.preferences_pb` suffix. */
        const val FILE_NAME = "account"

        private const val STATE_SIGNED_IN = "google"

        /** Base58 wallet addresses never contain a comma. */
        private const val WALLET_SEPARATOR = ","

        private val KEY_STATE = stringPreferencesKey("signed_in_with")
        private val KEY_EMAIL = stringPreferencesKey("email")
        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_WALLETS = stringPreferencesKey("linked_wallets")
    }
}
