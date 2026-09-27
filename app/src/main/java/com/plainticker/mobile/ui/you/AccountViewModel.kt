package com.plainticker.mobile.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.auth.GoogleCredentialResult
import com.plainticker.mobile.auth.GoogleCredentialSource
import com.plainticker.mobile.auth.SignInNonce
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock
import com.plainticker.mobile.data.auth.AccountApi
import com.plainticker.mobile.data.auth.AccountApiError
import com.plainticker.mobile.data.auth.AccountSignOut
import com.plainticker.mobile.data.auth.DeviceCodeStatus
import com.plainticker.mobile.data.auth.DeviceRekeyer
import com.plainticker.mobile.data.auth.SignOutResult
import com.plainticker.mobile.data.auth.GoogleAuthApi
import com.plainticker.mobile.data.auth.GoogleAuthError
import com.plainticker.mobile.data.auth.GoogleAuthFailure
import com.plainticker.mobile.data.auth.GoogleAuthResponse
import com.plainticker.mobile.prefs.AccountStore
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.prefs.SignedInAccount
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    /**
     * No nonce could be fetched before the sheet opened, so nothing was asked of Google. The
     * server requires one (GOOGLE_SIGNIN_NONCE_REQUIRED in production), and this device never
     * signs in without it.
     */
    NONCE_UNAVAILABLE,
    EMAIL_NOT_VERIFIED,
    RATE_LIMITED,
    INTERNAL,
    AUTH_DISABLED,
    NOT_CONFIGURED,
    JWKS_UNAVAILABLE,
    NOT_OPEN,
    UNKNOWN,

    /**
     * 409 `link_on_web`: this Google account's email already belongs to a PlainTicker account made
     * another way. Linking happens on the web ([LINK_ON_WEB_URL]), never silently here.
     */
    LINK_ON_WEB,

    /** 401 `rekey_required` still, after the one rekey and retry: this phone's code is mid-update. */
    REKEY_PENDING,

    /** 401 `code_retired`: the server no longer accepts this phone's code, so nothing was linked. */
    CODE_RETIRED,

    /**
     * Signed out on this phone, but `POST /api/v1/account/signout` has not answered 200 yet: one
     * retry is queued. Never worded as if the server had already let go of the device.
     */
    SIGN_OUT_UNCONFIRMED,
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
            GoogleAuthFailure.LINK_ON_WEB -> LINK_ON_WEB
            GoogleAuthFailure.REKEY_REQUIRED -> REKEY_PENDING
            GoogleAuthFailure.CODE_RETIRED -> CODE_RETIRED
        }

        /** Where [LINK_ON_WEB] sends the reader: the web account page, which links Google there. */
        const val LINK_ON_WEB_URL = "https://www.plainticker.com/en/account"
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

    data class SignedIn(
        val account: SignedInAccount,
        /** The wallet address a `wallets/unlink` call is in flight for, or null. */
        val unlinkingWallet: String? = null,
        /** The wallet whose last unlink attempt failed, and why; cleared the instant a new one starts. */
        val unlinkFailure: Pair<String, UnlinkFailure>? = null,
        /** `POST /api/v1/account/signout` is in flight: the row says so and offers nothing meanwhile. */
        val signingOut: Boolean = false,
    ) : AccountUiState
}

/**
 * Why `POST /api/v1/account/wallets/unlink` refused, one plain line each
 * ([unlinkFailureRes], AccountModel.kt). [AccountApiError.NotSignedIn] carries no case here: it is
 * not one wallet's own refusal, it means this device is no longer bound at all, so
 * [AccountViewModel.unlink] treats it exactly like [AccountViewModel.refresh]'s own and signs the
 * device out rather than blaming the row.
 */
enum class UnlinkFailure {
    BAD_REQUEST,
    NOT_LINKED,
    LAST_METHOD,
    RATE_LIMITED,
    NOT_OPEN,
    UNAVAILABLE,
}

/**
 * Sign in with Google (docs/google-sign-in.md). One pass through [signIn]:
 *
 * 1. [fetchServerNonce] asks the server for a nonce before the sheet opens. The server requires
 *    it (GOOGLE_SIGNIN_NONCE_REQUIRED in production), so a fetch that fails for any reason ends
 *    the attempt on [AccountMessage.NONCE_UNAVAILABLE] before Google is asked anything; there is
 *    no local fallback (judges' review, 2026-09-26);
 * 2. that nonce goes into the Google request;
 * 3. the token that comes back must carry that same nonce, or it is refused here;
 * 4. the token goes to `POST /api/v1/auth/google` with this device's code in `X-PT-Code`, and the
 *    server nonce alongside it;
 * 5. what the server returns for display (email, name, linked wallets) is stored, the token is
 *    dropped, and [signedIn] fires so the screen re-reads the entitlement through the refresh
 *    every other screen uses, which is what makes a Pro bought on the web count here at once.
 *
 * Every failure lands back on [AccountUiState.SignedOut] with one [AccountMessage].
 *
 * The device code is read here, never in a composable (DeviceCodeNeverDrawnTest), and only to
 * put it in the header.
 *
 * **Refreshing the account.** [refresh] re-reads `GET /api/v1/account` (email, name, linked
 * wallets) whenever a Google account is signed in, throttled to at most once every
 * [REFRESH_THROTTLE_MILLIS]: called on init in [store]'s absence, and again whenever the screen
 * that shows You is shown or resumed, so a wallet unlinked on the web (or linked there) shows up
 * here too, which the cache alone never would. Offline, a 404 (not deployed yet) or a 5xx keep the
 * cached account exactly as it was; only a 401 `not_signed_in` clears it, the same as [signOut]'s
 * own local clear, because the device is no longer bound.
 *
 * **Unlinking a wallet.** [unlink] drops one linked wallet through
 * `POST /api/v1/account/wallets/unlink`. Success fires [signedIn] again, the same event a
 * finished sign-in fires, so the hero's Pro state is re-read too, in case that wallet's own stake
 * or pass was the source. Every other refusal is kept against just that one wallet
 * ([AccountUiState.SignedIn.unlinkFailure]), never surfaced as a screen-wide message; a
 * `not_linked` also re-reads the account at once, past the throttle, because it proves the cached
 * list is stale, and the row goes away if the server no longer lists it.
 *
 * **Signing out** ([signOut], 2026-09-27) asks `POST /api/v1/account/signout` first, through
 * [AccountSignOut], then clears the local account whatever the answer: a 200 lands on a plain
 * [AccountUiState.SignedOut]; a network error or any unclean answer lands on
 * [AccountMessage.SIGN_OUT_UNCONFIRMED] with one retry queued, so the screen never claims the
 * server forgot this device before it said so. A new [signIn] drops that queued retry first.
 *
 * **The device code** (the pack's shared server contract, items 1 and 2). Every account call goes
 * through [rekeyer] when there is one: a legacy 10-symbol code is rekeyed before the call, and a
 * 401 `rekey_required` runs the rekey once more and retries the call once. A `rekey_required` that
 * survives that keeps the cached account exactly as it was (it is not a sign-out); a 401
 * `code_retired` clears it, since the server no longer knows this phone at all, and
 * [deviceCodeStatus] carries the honest line You draws for it.
 */
class AccountViewModel(
    private val api: GoogleAuthApi,
    private val accountApi: AccountApi,
    private val store: AccountStore,
    private val devicePassStore: DevicePassStore,
    private val debugLog: AccountDebugLog = AccountDebugLog.ANDROID,
    private val clock: Clock = WallClock,
    /** Rekeys a legacy device code around every account call; null reads the code as stored. */
    private val rekeyer: DeviceRekeyer? = null,
    /** The process-wide sign-out, whose queued retry outlives this screen; null builds a local one. */
    signOutRunner: AccountSignOut? = null,
) : ViewModel() {

    // The local fallback keeps its queue in memory and reads the code where this ViewModel always
    // has, so it needs no dispatcher of its own.
    private val signOutRunner: AccountSignOut =
        signOutRunner ?: AccountSignOut(accountApi, devicePassStore, rekeyer = rekeyer, log = debugLog, io = Dispatchers.Unconfined)

    /** Stands in for [DeviceRekeyer.status] when there is no [rekeyer]. */
    private val localDeviceCodeStatus = MutableStateFlow(DeviceCodeStatus.OK)

    /** What You says about this phone's own code: nothing, a rekey that cannot finish, or a retired code. */
    val deviceCodeStatus: StateFlow<DeviceCodeStatus> = rekeyer?.status ?: localDeviceCodeStatus.asStateFlow()

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Restoring)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val signedInEvents = Channel<Unit>(Channel.BUFFERED)

    /** One event per sign-in, refresh or unlink that finished: the screen refreshes the entitlement on each. */
    val signedIn: Flow<Unit> = signedInEvents.receiveAsFlow()

    private var signInJob: Job? = null
    private var refreshJob: Job? = null
    private var unlinkJob: Job? = null
    private var signOutJob: Job? = null

    /**
     * When the last [refresh] that SUCCEEDED started; null before the first one. A failure never
     * sets it, so the next resume tries again rather than waiting out the window on a stale list.
     */
    private var lastRefreshMillis: Long? = null

    /**
     * A [refresh] asked for while the stored account was still being read ([AccountUiState.Restoring]).
     * The screen asks the instant You is shown, which on a cold open is always before the store's
     * first emission lands, so the ask is kept here and run the moment the state becomes
     * [AccountUiState.SignedIn], instead of being dropped with nothing to retry it.
     */
    private var refreshPending = false

    init {
        viewModelScope.launch {
            store.account.collect { stored ->
                _state.update { current ->
                    when {
                        current is AccountUiState.SigningIn -> current
                        // Keep an unlink in flight, and a failure whose wallet is still listed; a
                        // wallet the fresh list no longer carries takes its failure line with it.
                        // A sign-out in flight clears the store when it lands; until then the row
                        // keeps saying it is signing out.
                        current is AccountUiState.SignedIn && current.signingOut -> current
                        stored != null && current is AccountUiState.SignedIn -> current.copy(
                            account = stored,
                            unlinkFailure = current.unlinkFailure?.takeIf { it.first in stored.linkedWallets },
                        )
                        stored != null -> AccountUiState.SignedIn(stored)
                        current is AccountUiState.SignedOut -> current
                        else -> AccountUiState.SignedOut()
                    }
                }
                when (_state.value) {
                    is AccountUiState.SignedIn -> if (refreshPending) {
                        refreshPending = false
                        refresh()
                    }
                    is AccountUiState.SignedOut -> refreshPending = false
                    else -> Unit
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
        val nonce = fetchServerNonce() ?: return Outcome.Failed(AccountMessage.NONCE_UNAVAILABLE)
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
        // A sign-out that never reached the server must not land after this sign-in and unbind it.
        signOutRunner.cancelPending()
        val response = try {
            withDeviceCode({ it is GoogleAuthError && it.failure == GoogleAuthFailure.REKEY_REQUIRED }) { code ->
                api.signIn(idToken = idToken, deviceCode = code, nonce = nonce)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleAuthError) {
            debugLog.raw("sign-in: server ${e.failure.name} ${e.status ?: "-"}")
            if (e.failure == GoogleAuthFailure.CODE_RETIRED) markCodeRetired()
            return Outcome.Failed(AccountMessage.of(e.failure))
        } catch (e: IOException) {
            debugLog.raw("sign-in: network ${e::class.simpleName}")
            return Outcome.Failed(AccountMessage.NETWORK)
        }
        val account = accountOf(response)
        store.save(account)
        return Outcome.Done(account)
    }

    /** What the server returned for display, the same shape every one of these three calls answers. */
    private fun accountOf(response: GoogleAuthResponse): SignedInAccount = SignedInAccount(
        email = response.user.email?.takeIf { it.isNotBlank() },
        name = response.user.name?.takeIf { it.isNotBlank() },
        linkedWallets = response.linkedWallets,
    )

    /**
     * The server nonce for the Google request that follows, fetched fresh before every attempt
     * (docs/google-sign-in.md, "The nonce"), or null when none could be had: a network error, a
     * non-2xx, a blank or unparsable answer. Null ends the attempt in [runSignIn]; a sign-in
     * without the server's nonce is one the server refuses, so none is ever started.
     */
    private suspend fun fetchServerNonce(): String? = try {
        api.fetchNonce().nonce.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        debugLog.raw("nonce: fetch failed, sign-in not started (${e::class.simpleName})")
        null
    }

    /** Clears the message after the reader has seen it act; a new attempt clears it too. */
    fun dismissMessage() {
        _state.update { if (it is AccountUiState.SignedOut) AccountUiState.SignedOut() else it }
    }

    /**
     * Unbinds this device from the account (`POST /api/v1/account/signout`), then forgets the
     * account locally whatever the answer. Only a 200 lands on a plain [AccountUiState.SignedOut];
     * anything else lands on [AccountMessage.SIGN_OUT_UNCONFIRMED] with one retry queued in
     * [AccountSignOut]. A second tap while one is in flight does nothing.
     */
    fun signOut() {
        if (signOutJob?.isActive == true) return
        signInJob?.cancel()
        refreshJob?.cancel()
        unlinkJob?.cancel()
        refreshPending = false
        _state.update { (it as? AccountUiState.SignedIn)?.copy(signingOut = true, unlinkingWallet = null) ?: it }
        signOutJob = viewModelScope.launch {
            val result = try {
                signOutRunner.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugLog.raw("sign-out: unexpected ${e::class.simpleName}")
                SignOutResult.QUEUED
            }
            if (result == SignOutResult.CODE_RETIRED) markCodeRetired()
            store.clear()
            _state.value = AccountUiState.SignedOut(
                if (result == SignOutResult.QUEUED) AccountMessage.SIGN_OUT_UNCONFIRMED else null,
            )
        }
    }

    /** [block] with this device's code: through [rekeyer] when there is one, else as stored. */
    private suspend fun <T> withDeviceCode(isRekeyRequired: (Throwable) -> Boolean, block: suspend (String) -> T): T {
        val r = rekeyer ?: return block(devicePassStore.code())
        return r.withCode(isRekeyRequired, block)
    }

    /** An account route answered 401 `code_retired`: You draws the honest line for it. */
    private fun markCodeRetired() {
        val r = rekeyer
        if (r != null) r.markRetired() else localDeviceCodeStatus.value = DeviceCodeStatus.RETIRED
    }

    /** The server no longer knows this phone's code: the local account goes, like a 401 `not_signed_in`. */
    private suspend fun signOutRetired() {
        markCodeRetired()
        store.clear()
        _state.value = AccountUiState.SignedOut()
    }

    // ---- Refreshing the account, and unlinking a wallet --------------------------------------

    /**
     * Re-reads the signed-in account from the server (email, name, linked wallets) and, on success,
     * fires [signedIn] so the entitlement is re-read too. A no-op with no Google account signed in,
     * while a refresh is already running, or inside [REFRESH_THROTTLE_MILLIS] of the last one that
     * succeeded. Asked for while the stored account is still being read, it is kept and run the
     * moment that account lands ([refreshPending]). Offline, a 404 (not deployed yet) or a 5xx are
     * logged (debug builds only) and the cached account is kept exactly as it was, and none of them
     * starts the throttle, so the next resume tries again; only [AccountApiError.NotSignedIn]
     * clears it, because the device itself is no longer bound, the same fact [unlink] treats
     * identically.
     */
    fun refresh() = startRefresh(force = false)

    /** [force] skips the throttle: a server answer has just shown the cached list to be stale. */
    private fun startRefresh(force: Boolean) {
        when (_state.value) {
            is AccountUiState.SignedIn -> Unit
            AccountUiState.Restoring -> {
                refreshPending = true
                return
            }
            else -> return
        }
        val now = clock.nowMillis()
        val last = lastRefreshMillis
        if (!force && last != null && now - last < REFRESH_THROTTLE_MILLIS) return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            try {
                val response = withDeviceCode({ it is AccountApiError.RekeyRequired }) { code -> accountApi.get(code) }
                lastRefreshMillis = now
                applyAccount(response, finishesUnlink = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountApiError.NotSignedIn) {
                store.clear()
                _state.value = AccountUiState.SignedOut()
            } catch (e: AccountApiError.CodeRetired) {
                signOutRetired()
            } catch (e: AccountApiError.NotOpen) {
                debugLog.raw("account refresh: not open yet")
            } catch (e: AccountApiError) {
                debugLog.raw("account refresh: ${e::class.simpleName}: ${e.message}")
            } catch (e: IOException) {
                debugLog.raw("account refresh: network ${e::class.simpleName}")
            } catch (e: Exception) {
                debugLog.raw("account refresh: unexpected ${e::class.simpleName}")
            }
        }
    }

    /**
     * Stores and shows the fresh account. [finishesUnlink] clears the row state an unlink left;
     * a plain refresh keeps an unlink still in flight, and keeps a failure line only while its
     * wallet is still listed: a wallet the server no longer lists takes its row, and its line, away.
     */
    private suspend fun applyAccount(response: GoogleAuthResponse, finishesUnlink: Boolean) {
        val account = accountOf(response)
        store.save(account)
        _state.update { current ->
            val previous = current as? AccountUiState.SignedIn
            if (finishesUnlink || previous == null) {
                AccountUiState.SignedIn(account)
            } else {
                previous.copy(
                    account = account,
                    unlinkFailure = previous.unlinkFailure?.takeIf { it.first in account.linkedWallets },
                )
            }
        }
        signedInEvents.trySend(Unit)
    }

    /**
     * Drops [wallet] from the signed-in account. A no-op while another unlink is already in
     * flight, for any wallet: [AccountUiState.SignedIn.unlinkingWallet] is what the row reads to
     * hide its own actions meanwhile. On success the wallet is gone from the fresh list the server
     * returned and [signedIn] fires, exactly as [refresh] does; every other refusal is kept
     * against just this wallet ([AccountUiState.SignedIn.unlinkFailure]), so its row can show one
     * plain line and be tried again, and a [AccountApiError.NotSignedIn] is treated like
     * [refresh]'s own: the device is no longer bound at all, not a fact about one wallet.
     */
    fun unlink(wallet: String) {
        val current = _state.value as? AccountUiState.SignedIn ?: return
        if (current.unlinkingWallet != null) return
        _state.value = current.copy(unlinkingWallet = wallet, unlinkFailure = null)
        unlinkJob = viewModelScope.launch {
            try {
                val response = withDeviceCode({ it is AccountApiError.RekeyRequired }) { code ->
                    accountApi.unlinkWallet(wallet, code)
                }
                // This answer already is the fresh account: a refresh read before it landed must
                // not land after it and put the wallet back.
                refreshJob?.cancel()
                applyAccount(response, finishesUnlink = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountApiError.NotSignedIn) {
                store.clear()
                _state.value = AccountUiState.SignedOut()
            } catch (e: AccountApiError.CodeRetired) {
                signOutRetired()
            } catch (e: AccountApiError) {
                debugLog.raw("unlink: ${e::class.simpleName}: ${e.message}")
                failUnlink(wallet, unlinkFailureOf(e))
                // `not_linked` means the server no longer lists this wallet for this account, so
                // the row this device drew came from a stale cache. Re-read now, past the throttle:
                // the row goes away if the server has dropped it, rather than sitting there with
                // a failure line that no second tap could ever clear.
                if (e is AccountApiError.NotLinked) startRefresh(force = true)
            } catch (e: IOException) {
                debugLog.raw("unlink: network ${e::class.simpleName}")
                failUnlink(wallet, UnlinkFailure.UNAVAILABLE)
            } catch (e: Exception) {
                debugLog.raw("unlink: unexpected ${e::class.simpleName}")
                failUnlink(wallet, UnlinkFailure.UNAVAILABLE)
            }
        }
    }

    private fun failUnlink(wallet: String, reason: UnlinkFailure) {
        _state.update { current ->
            (current as? AccountUiState.SignedIn)?.copy(unlinkingWallet = null, unlinkFailure = wallet to reason) ?: current
        }
    }

    private fun unlinkFailureOf(e: AccountApiError): UnlinkFailure = when (e) {
        is AccountApiError.BadRequest -> UnlinkFailure.BAD_REQUEST
        is AccountApiError.NotLinked -> UnlinkFailure.NOT_LINKED
        is AccountApiError.LastMethod -> UnlinkFailure.LAST_METHOD
        is AccountApiError.RateLimited -> UnlinkFailure.RATE_LIMITED
        is AccountApiError.NotOpen -> UnlinkFailure.NOT_OPEN
        is AccountApiError.Unavailable -> UnlinkFailure.UNAVAILABLE
        is AccountApiError.NotSignedIn -> UnlinkFailure.UNAVAILABLE // unreachable: caught before this branch
        is AccountApiError.CodeRetired -> UnlinkFailure.UNAVAILABLE // unreachable: caught before this branch
        // Still refused after the one rekey and retry: the code is mid-update, try again shortly.
        is AccountApiError.RekeyRequired -> UnlinkFailure.UNAVAILABLE
    }

    companion object {
        /** How often [refresh] may ask the server, at most: task's own "at most once every 30 s". */
        const val REFRESH_THROTTLE_MILLIS = 30_000L
    }
}
