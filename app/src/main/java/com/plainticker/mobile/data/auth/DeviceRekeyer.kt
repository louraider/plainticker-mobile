package com.plainticker.mobile.data.auth

import com.plainticker.mobile.auth.AccountDebugLog
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock
import com.plainticker.mobile.prefs.DeviceCodeRekeyStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What You says about this device's own code, if anything. */
enum class DeviceCodeStatus {
    /** Nothing to say: the code is current, or a legacy one whose rekey simply has not landed yet. */
    OK,

    /** The rekey can never finish on this device (409 `already_rekeyed` or `new_code_in_use`, 401 `code_retired`). */
    BLOCKED,

    /** An account route answered 401 `code_retired`: the server no longer accepts this code at all. */
    RETIRED,
}

/** How one [DeviceRekeyer.rekeyIfNeeded] ended. */
enum class RekeyOutcome {
    /** The current code is already a 26-symbol one; nothing was sent. */
    NOT_NEEDED,

    /** The server answered 200 and the new code is now the current one. */
    REKEYED,

    /** The server answered 400 `not_legacy`: the pending code was dropped, the current one kept. */
    NOT_LEGACY,

    /** A refusal no retry can fix; recorded on the device, both codes kept. */
    BLOCKED,

    /** Offline, rate limited, not deployed yet, or an answer that was not a clean 200: try again later. */
    RETRY_LATER,
}

/**
 * Moves a legacy 10-symbol device code onto a 26-symbol one through `POST /api/v1/device/rekey`
 * (the pack's shared server contract, items 1 and 2), and is the one place anything calls it.
 *
 * **The state machine**, every step persisted before the next one can happen
 * ([com.plainticker.mobile.prefs.DevicePassStore], "Replacing a legacy code"):
 *
 *     legacy --beginRekey (commit)--> legacy + pending --POST--> 200 --completeRekey (commit)--> new
 *                                            |                   |
 *                                            |                   +-- 400 not_legacy ----> legacy (pending dropped)
 *                                            |                   +-- 409 / code_retired -> BLOCKED (both kept)
 *                                            |                   +-- anything else -----> legacy + pending, retried
 *                                            +-- crash, lost answer: the next attempt resends the SAME pending code
 *
 * So the old code is never lost before the server confirmed the new one, and a replacement is
 * never forgotten after the server may have heard of it.
 *
 * **When it runs.** [runOnLaunch] once per process start, retrying with backoff while the process
 * lives ([BACKOFF_MILLIS]), then again on the next launch; and [withCode], around every account
 * call, both before it (a legacy code is rekeyed first, outside the backoff window) and after a
 * 401 `rekey_required` (rekeyed once, past the backoff, then the call retried once). [mutex] makes
 * all of them one attempt at a time, so two calls can never mint two replacements.
 *
 * It never logs a code: only outcome names and the server's error code.
 */
class DeviceRekeyer(
    private val store: DeviceCodeRekeyStore,
    private val api: DeviceRekeyApi,
    private val clock: Clock = WallClock,
    private val log: AccountDebugLog = AccountDebugLog.ANDROID,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(DeviceCodeStatus.OK)
    val status: StateFlow<DeviceCodeStatus> = _status.asStateFlow()

    /** Consecutive attempts that ended [RekeyOutcome.RETRY_LATER]; zero after any other outcome. */
    private var failures = 0

    /** Before this, a preflight from [withCode] does not try again; [force] ignores it. */
    private var retryAtMillis = 0L

    /**
     * One rekey attempt, if the current code is legacy. [force] ignores the backoff window (a
     * `rekey_required` answer, or launch); without it an attempt inside the window returns
     * [RekeyOutcome.RETRY_LATER] without calling.
     */
    suspend fun rekeyIfNeeded(force: Boolean = false): RekeyOutcome = mutex.withLock {
        withContext(io) { attempt(force) }
    }

    private suspend fun attempt(force: Boolean): RekeyOutcome {
        store.rekeyBlocked()?.let {
            _status.value = DeviceCodeStatus.BLOCKED
            return RekeyOutcome.BLOCKED
        }
        if (!store.isLegacy()) {
            // A pending code next to a current one can only be a leftover; completeRekey drops
            // both in the same commit, so this does not happen, but it must never be sent.
            if (store.pendingNewCode() != null) store.abandonRekey()
            return RekeyOutcome.NOT_NEEDED
        }
        if (!force && clock.nowMillis() < retryAtMillis) return RekeyOutcome.RETRY_LATER

        val oldCode = store.code()
        val newCode = store.beginRekey() ?: run {
            note("rekey: the replacement could not be written, nothing sent")
            return retryLater()
        }
        try {
            api.rekey(oldCode, newCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DeviceRekeyError) {
            note("rekey: refused ${e.code ?: "-"} ${e.status ?: "-"}")
            return when (e) {
                is DeviceRekeyError.NotLegacy -> {
                    store.abandonRekey()
                    settled(RekeyOutcome.NOT_LEGACY)
                }
                is DeviceRekeyError.AlreadyRekeyed,
                is DeviceRekeyError.NewCodeInUse,
                is DeviceRekeyError.CodeRetired,
                -> {
                    // Nothing is deleted: the legacy code stays current and the pending one stays
                    // where it is, so support can still act on either.
                    store.markRekeyBlocked(e.code ?: "blocked")
                    _status.value = DeviceCodeStatus.BLOCKED
                    settled(RekeyOutcome.BLOCKED)
                }
                // invalid_new_code keeps the pending code too: a fresh one would have the same
                // format, and sending the same pair again is always safe.
                else -> retryLater()
            }
        } catch (e: IOException) {
            note("rekey: network ${e::class.simpleName}")
            return retryLater()
        } catch (e: Exception) {
            note("rekey: unexpected ${e::class.simpleName}")
            return retryLater()
        }
        store.completeRekey(newCode)
        // A commit that failed on disk has still changed the in-memory value this process reads;
        // after a restart the same pair is sent again and answered 200 again.
        return if (store.code() == newCode) {
            note("rekey: done")
            settled(RekeyOutcome.REKEYED)
        } else {
            retryLater()
        }
    }

    private fun settled(outcome: RekeyOutcome): RekeyOutcome {
        failures = 0
        retryAtMillis = 0L
        return outcome
    }

    private fun retryLater(): RekeyOutcome {
        failures++
        retryAtMillis = clock.nowMillis() + backoffMillis(failures)
        return RekeyOutcome.RETRY_LATER
    }

    /**
     * On process start: reads the stored state into [status], then tries the rekey with backoff
     * while the process lives, up to [BACKOFF_MILLIS]'s length; the next launch starts over.
     */
    suspend fun runOnLaunch() {
        var outcome = rekeyIfNeeded(force = true)
        for (wait in BACKOFF_MILLIS) {
            if (outcome != RekeyOutcome.RETRY_LATER) return
            delay(wait)
            outcome = rekeyIfNeeded(force = true)
        }
    }

    /** An account route answered 401 `code_retired`. A recorded [DeviceCodeStatus.BLOCKED] says more, and stays. */
    fun markRetired() {
        if (_status.value != DeviceCodeStatus.BLOCKED) _status.value = DeviceCodeStatus.RETIRED
    }

    /**
     * Runs [block] with the current code, the contract's rule for account routes: a legacy code is
     * rekeyed first when it may be tried; and when [block] still fails with an error
     * [isRekeyRequired] recognizes (401 `rekey_required`), the rekey runs once more, past the
     * backoff, and [block] is retried once with the new code. If no new code came of it, the
     * original error is rethrown for the caller to name.
     */
    suspend fun <T> withCode(isRekeyRequired: (Throwable) -> Boolean, block: suspend (String) -> T): T {
        rekeyIfNeeded(force = false)
        val first = currentCode()
        try {
            return block(first)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isRekeyRequired(e)) throw e
            rekeyIfNeeded(force = true)
            val next = currentCode()
            if (next == first) throw e
            return block(next)
        }
    }

    private suspend fun currentCode(): String = withContext(io) { store.code() }

    /** A debug line that can never change what happens: a logger that throws is ignored. */
    private fun note(line: String) {
        runCatching { log.raw(line) }
    }

    companion object {
        /** The waits between attempts after launch: 30 s, 2 min, 10 min, then the next launch. */
        val BACKOFF_MILLIS = listOf(30_000L, 120_000L, 600_000L)

        /** The preflight backoff after [failures] consecutive failures, capped at ten minutes. */
        fun backoffMillis(failures: Int): Long =
            BACKOFF_MILLIS.getOrElse(failures - 1) { BACKOFF_MILLIS.last() }
    }
}
