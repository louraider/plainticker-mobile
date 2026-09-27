package com.plainticker.mobile.data.auth

import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.prefs.DevicePassStore
import com.plainticker.mobile.prefs.InMemoryPendingSignOutStore
import com.plainticker.mobile.prefs.PendingSignOutStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How a sign-out ended on the server's side. The local account is cleared in every case. */
enum class SignOutResult {
    /** 200 (or 401 `not_signed_in`, the same fact): the server no longer binds this device. */
    CONFIRMED,

    /** The server could not be asked, or did not answer cleanly: one retry is queued. */
    QUEUED,

    /** 401 `code_retired`: the server no longer accepts this device's code at all. */
    CODE_RETIRED,
}

/**
 * `POST /api/v1/account/signout` (the pack's shared server contract, item 3), process wide so a
 * queued retry outlives the screen that asked.
 *
 * [signOut] asks the server once. Anything short of a clean answer (offline, a 5xx, the route not
 * deployed yet, a rate limit, a rekey that could not land) sets [pending]'s flag, and
 * [retryPending] asks once more: [RETRY_DELAY_MILLIS] later on [retryScope] while the process
 * lives, and again on the next launch (PlainTickerApp). The flag clears only on an answer that
 * settles it. [cancelPending] is what a new sign-in calls first: it waits out any retry
 * in flight ([mutex]) and drops the flag, so a late retry can never unbind the account the reader
 * just signed in to.
 *
 * The device code is read fresh for each call through [rekeyer] when there is one (a legacy code
 * is rekeyed first, and a `rekey_required` retried once), and never logged.
 */
class AccountSignOut(
    private val api: AccountApi,
    private val codes: DevicePassStore,
    private val pending: PendingSignOutStore = InMemoryPendingSignOutStore(),
    private val rekeyer: DeviceRekeyer? = null,
    private val log: AccountDebugLog = AccountDebugLog.ANDROID,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Where the one queued retry runs; process wide in the app, null in a test that drives it by hand. */
    private val retryScope: CoroutineScope? = null,
) {
    private val mutex = Mutex()

    suspend fun signOut(): SignOutResult {
        val result = mutex.withLock { attempt() }
        if (result == SignOutResult.QUEUED) {
            retryScope?.launch {
                delay(RETRY_DELAY_MILLIS)
                retryPending()
            }
        }
        return result
    }

    /** One more try for a sign-out that was never confirmed; null when none is queued. */
    suspend fun retryPending(): SignOutResult? = mutex.withLock {
        if (!withContext(io) { pending.isPending() }) null else attempt()
    }

    /** Drops a queued retry, after any retry already in flight has finished. */
    suspend fun cancelPending() = mutex.withLock { withContext(io) { pending.setPending(false) } }

    suspend fun isPending(): Boolean = withContext(io) { pending.isPending() }

    private suspend fun attempt(): SignOutResult {
        val result = try {
            call()
            SignOutResult.CONFIRMED
        } catch (e: CancellationException) {
            throw e
        } catch (e: AccountApiError.NotSignedIn) {
            SignOutResult.CONFIRMED
        } catch (e: AccountApiError.CodeRetired) {
            rekeyer?.markRetired()
            SignOutResult.CODE_RETIRED
        } catch (e: AccountApiError) {
            note("sign-out: ${e::class.simpleName} ${e.status ?: "-"}")
            SignOutResult.QUEUED
        } catch (e: IOException) {
            note("sign-out: network ${e::class.simpleName}")
            SignOutResult.QUEUED
        } catch (e: Exception) {
            note("sign-out: unexpected ${e::class.simpleName}")
            SignOutResult.QUEUED
        }
        withContext(io) { pending.setPending(result == SignOutResult.QUEUED) }
        return result
    }

    private suspend fun call() {
        val r = rekeyer
        if (r == null) {
            api.signOut(withContext(io) { codes.code() })
        } else {
            r.withCode({ it is AccountApiError.RekeyRequired }) { code -> api.signOut(code) }
        }
    }

    /** A debug line that can never change what happens: a logger that throws is ignored. */
    private fun note(line: String) {
        runCatching { log.raw(line) }
    }

    companion object {
        /** How long the one in-process retry waits: long enough for a dropped connection to return. */
        const val RETRY_DELAY_MILLIS = 60_000L
    }
}
