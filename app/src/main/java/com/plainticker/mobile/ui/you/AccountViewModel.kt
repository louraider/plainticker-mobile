package com.plainticker.mobile.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.auth.GoogleCredentialResult
import com.plainticker.mobile.auth.GoogleCredentialSource
import com.plainticker.mobile.auth.SignInNonce
import com.plainticker.mobile.data.auth.GoogleAuthApi
import com.plainticker.mobile.data.auth.GoogleAuthError
import com.plainticker.mobile.data.auth.GoogleAuthFailure
import com.plainticker.mobile.prefs.AccountStore
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.prefs.SignedInAccount
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last sign-in did not finish, one per case the You screen names in one line. */
enum class AccountMessage {
    CANCELLED,
    NO_ACCOUNT,
    NO_PLAY_SERVICES,
    CREDENTIAL_FAILED,

    /** Android or Google itself was not set up for this app (GoogleCredentialFailureClassifier). */
    SETUP_PROBLEM,

    /** The Credential Manager request was interrupted; nothing was decided. */
    INTERRUPTED,
    NONCE_MISMATCH,
    NETWORK,
    BAD_REQUEST,
    BAD_DEVICE_CODE,
    INVALID_TOKEN,
    EXPIRED_TOKEN,
    WRONG_AUDIENCE,

    /** The server rejected the nonce this device sent (docs/google-sign-in.md, "The nonce"). */
    NONCE_INVALID,

    /** The nonce this device fetched had already expired by the time the server saw it. */
    NONCE_EXPIRED,
    EMAIL_NOT_VERIFIED,
    RATE_LIMITED,
    INTERNAL,
    AUTH_DISABLED,
    NOT_CONFIGURED,
    JWKS_UNAVAILABLE,
    NOT_OPEN,
    UNKNOWN,
    ;

    companion object {
        fun of(failure: GoogleAuthFailure): AccountMessage = when (failure) {
            GoogleAuthFailure.BAD_REQUEST -> BAD_REQUEST
            GoogleAuthFailure.BAD_DEVICE_CODE -> BAD_DEVICE_CODE
            GoogleAuthFailure.INVALID_TOKEN -> INVALID_TOKEN
            GoogleAuthFailure.EXPIRED_TOKEN -> EXPIRED_TOKEN
            GoogleAuthFailure.WRONG_AUDIENCE -> WRONG_AUDIENCE
            GoogleAuthFailure.NONCE_INVALID -> NONCE_INVALID
            GoogleAuthFailure.NONCE_EXPIRED -> NONCE_EXPIRED
            GoogleAuthFailure.EMAIL_NOT_VERIFIED -> EMAIL_NOT_VERIFIED
            GoogleAuthFailure.RATE_LIMITED -> RATE_LIMITED
            GoogleAuthFailure.INTERNAL -> INTERNAL
            GoogleAuthFailure.AUTH_DISABLED -> AUTH_DISABLED
            GoogleAuthFailure.NOT_CONFIGURED -> NOT_CONFIGURED
            GoogleAuthFailure.JWKS_UNAVAILABLE -> JWKS_UNAVAILABLE
            GoogleAuthFailure.NOT_OPEN -> NOT_OPEN
            GoogleAuthFailure.UNKNOWN -> UNKNOWN
        }
    }
}

/** The Account section's states. */
sealed interface AccountUiState {
    /** The stored account has not been read yet: draw no action, so nothing flashes. */
    data object Restoring : AccountUiState

    /** Signed out, with the reason the last attempt did not finish, if there was one. */
    data class SignedOut(val message: AccountMessage? = null) : AccountUiState

    /** Google's sheet is up, or the server call is in flight. */
    data object SigningIn : AccountUiState

    data class SignedIn(val account: SignedInAccount) : AccountUiState
}

/**
 * Sign in with Google (docs/google-sign-in.md). One pass through [signIn]:
 *
 * 1. [fetchServerNonce] asks the server for a nonce before the sheet opens; any failure falls
 *    back to a fresh local [SignInNonce] instead, sent to Google exactly as before that endpoint
 *    existed;
 * 2. that nonce (server-issued or local) goes into the Google request;
 * 3. the token that comes back must carry that same nonce, or it is refused here;
 * 4. the token goes to `POST /api/v1/auth/google` with this device's code in `X-PT-Code`, and the
 *    server nonce alongside it when there was one (never the local fallback);
 * 5. what the server returns for display (email, name, linked wallets) is stored, the token is
 *    dropped, and [signedIn] fires so the screen re-reads the entitlement through the refresh
 *    every other screen uses, which is what makes a Pro bought on the web count here at once.
 *
 * Every failure lands back on [AccountUiState.SignedOut] with one [AccountMessage].
 *
 * The device code is read here, never in a composable (DeviceCodeNeverDrawnTest), and only to
 * put it in the header.
 */
class AccountViewModel(
    private val api: GoogleAuthApi,
    private val store: AccountStore,
    private val devicePassStore: DevicePassStore,
    private val nonces: () -> String = { SignInNonce.create() },
    private val debugLog: AccountDebugLog = AccountDebugLog.ANDROID,
) : ViewModel() {

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Restoring)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val signedInEvents = Channel<Unit>(Channel.BUFFERED)

    /** One event per sign-in that finished: the screen refreshes the entitlement on each. */
    val signedIn: Flow<Unit> = signedInEvents.receiveAsFlow()

    private var signInJob: Job? = null

    init {
        viewModelScope.launch {
            store.account.collect { stored ->
                _state.update { current ->
                    when {
                        current is AccountUiState.SigningIn -> current
                        stored != null -> AccountUiState.SignedIn(stored)
                        current is AccountUiState.SignedOut -> current
                        else -> AccountUiState.SignedOut()
                    }
                }
            }
        }
    }

    /** Starts a sign-in with [credentials]; ignored while one is already running or signed in. */
    fun signIn(credentials: GoogleCredentialSource) {
        val current = _state.value
        if (current is AccountUiState.SigningIn || current is AccountUiState.SignedIn) return
        _state.value = AccountUiState.SigningIn
        signInJob = viewModelScope.launch {
            val outcome = try {
                runSignIn(credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugLog.raw("sign-in: unexpected ${e::class.simpleName}")
                Outcome.Failed(AccountMessage.UNKNOWN)
            }
            when (outcome) {
                is Outcome.Done -> {
                    _state.value = AccountUiState.SignedIn(outcome.account)
                    signedInEvents.trySend(Unit)
                }
                is Outcome.Failed -> _state.value = AccountUiState.SignedOut(outcome.message)
            }
        }
    }

    private sealed interface Outcome {
        data class Done(val account: SignedInAccount) : Outcome
        data class Failed(val message: AccountMessage) : Outcome
    }

    private suspend fun runSignIn(credentials: GoogleCredentialSource): Outcome {
        val serverNonce = fetchServerNonce()
        val nonce = serverNonce ?: nonces()
        val idToken = when (val result = credentials.requestIdToken(nonce)) {
            is GoogleCredentialResult.Token -> result.idToken
            GoogleCredentialResult.Cancelled -> return Outcome.Failed(AccountMessage.CANCELLED)
            GoogleCredentialResult.NoAccount -> return Outcome.Failed(AccountMessage.NO_ACCOUNT)
            GoogleCredentialResult.NoPlayServices -> return Outcome.Failed(AccountMessage.NO_PLAY_SERVICES)
            GoogleCredentialResult.SetupProblem -> return Outcome.Failed(AccountMessage.SETUP_PROBLEM)
            GoogleCredentialResult.Interrupted -> return Outcome.Failed(AccountMessage.INTERRUPTED)
            GoogleCredentialResult.Failed -> return Outcome.Failed(AccountMessage.CREDENTIAL_FAILED)
        }
        if (!SignInNonce.matches(idToken, nonce)) {
            debugLog.raw("sign-in: the token's nonce is not the one requested")
            return Outcome.Failed(AccountMessage.NONCE_MISMATCH)
        }
        val response = try {
            api.signIn(idToken = idToken, deviceCode = devicePassStore.code(), nonce = serverNonce)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleAuthError) {
            debugLog.raw("sign-in: server ${e.failure.name} ${e.status ?: "-"}")
            return Outcome.Failed(AccountMessage.of(e.failure))
        } catch (e: IOException) {
            debugLog.raw("sign-in: network ${e::class.simpleName}")
            return Outcome.Failed(AccountMessage.NETWORK)
        }
        val account = SignedInAccount(
            email = response.user.email?.takeIf { it.isNotBlank() },
            name = response.user.name?.takeIf { it.isNotBlank() },
            linkedWallets = response.linkedWallets,
        )
        store.save(account)
        return Outcome.Done(account)
    }

    /**
     * The server nonce for the Google request that follows, fetched fresh before every attempt
     * (docs/google-sign-in.md, "The nonce"). Any failure to fetch one — a 404 from a server that
     * predates the route, a network error, or anything else, blank or unparsable answers included
     * — falls back to null: [runSignIn] then asks Google for [nonces]'s local random nonce instead
     * and tells the server nothing about it, so this device keeps working against a server that
     * has not deployed the route yet, or is briefly unavailable, exactly as before this endpoint
     * existed. The local check in `SignInNonce.matches` runs either way.
     */
    private suspend fun fetchServerNonce(): String? = try {
        api.fetchNonce().nonce.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        debugLog.raw("nonce: fetch failed, falling back to a local nonce (${e::class.simpleName})")
        null
    }

    /** Clears the message after the reader has seen it act; a new attempt clears it too. */
    fun dismissMessage() {
        _state.update { if (it is AccountUiState.SignedOut) AccountUiState.SignedOut() else it }
    }

    /** Forgets the account on this device. The contract defines no server call for it. */
    fun signOut() {
        signInJob?.cancel()
        viewModelScope.launch {
            store.clear()
            _state.value = AccountUiState.SignedOut()
        }
    }
}
