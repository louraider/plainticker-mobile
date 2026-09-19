package com.plainticker.mobile.ui.pass

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.EntitlementResponse
import com.plainticker.mobile.data.plainticker.PassBuild

/**
 * The pay machine's states, and every transition between them (task A6: paying for Pro from the
 * app). Built on the same division of labour as [com.plainticker.mobile.ui.vote.VoteViewModel]'s
 * vote machine, because it is the same trick: the server builds an unsigned transaction, the
 * wallet signs and sends it through Mobile Wallet Adapter, and this app never holds a key.
 *
 *     pay()
 *     Closed ....> Opening(CONNECTING) ...> Building ....> Ready
 *                       :                        :            :
 *                       : no wallet on the       : the        : confirm()
 *                       : device, or nothing     : route is   v
 *                       : connected              : not open  Signing
 *                       v                        v            :
 *                    Refused                  Refused         : a signature
 *                                                              v
 *                                                          Confirming ....> Landed
 *
 *     Building <- confirm() on a Ready whose transaction has expired: asked again, never signed.
 *     Refused  <- no signature came back, or the wallet answered with a failure, from Signing.
 *     Landed   <- reached even when the confirm call itself failed: a landed signature is not
 *                 undone by this app's own follow-up call not answering in time (the cron is the
 *                 backstop server/vote/README.md already documents), so [Landed.entitlement] is
 *                 nullable rather than the state being a third kind of refusal.
 *     Closed   <- close(), from any state that is not [PassState.Running].
 */
sealed interface PassState {

    /** No payment is in progress and the sheet is not up. */
    data object Closed : PassState

    /** A round-trip is in flight. [phase] names which one, so no state of this sheet is a spinner. */
    sealed interface Running : PassState {
        val phase: PassPhase
    }

    /** Authorizing the wallet. Reached only when this app holds no session of its own. */
    data class Opening(override val phase: PassPhase = PassPhase.CONNECTING) : Running

    /** The wallet is known; the server is assembling the transfer. */
    data class Building(val payer: String) : Running {
        override val phase: PassPhase get() = PassPhase.BUILDING
    }

    /** The transfer is in hand and nothing has been signed. The amount and destination are on screen. */
    data class Ready(
        val payer: String,
        val build: PassBuild,
        /** True when this confirm step replaced one whose transaction had gone stale. */
        val refreshed: Boolean = false,
    ) : PassState

    /** The wallet is open: it signs the transfer and submits it, in one round-trip. */
    data class Signing(val payer: String, val build: PassBuild) : Running {
        override val phase: PassPhase get() = PassPhase.SIGNING
    }

    /** The wallet answered with a signature; this app asks the server to verify it on demand. */
    data class Confirming(val payer: String, val signature: String) : Running {
        override val phase: PassPhase get() = PassPhase.CONFIRMING
    }

    /**
     * The payment landed. [entitlement] is the fresh answer when the confirm call itself
     * succeeded; null when it did not, in which case the cron still mints the pass within about
     * ten minutes, and the signature is the receipt of that regardless.
     */
    data class Landed(val signature: String, val entitlement: EntitlementResponse?) : PassState

    /** The attempt is over. [reason] is one of this app's own sentences, never the server's. */
    data class Refused(val reason: PassRefusal) : PassState

    val isBusy: Boolean get() = this is Running
}

/** What is in flight, as the sheet's phase sentence. */
enum class PassPhase(@StringRes val text: Int) {
    CONNECTING(R.string.pass_phase_connecting),
    BUILDING(R.string.pass_phase_building),
    SIGNING(R.string.pass_phase_signing),
    CONFIRMING(R.string.pass_phase_confirming),
}

/**
 * Every way a payment can end without landing, as one of this app's own sentences. [retryable] is
 * whether tapping again could plausibly end differently.
 */
enum class PassRefusal(@StringRes val text: Int, val retryable: Boolean = false) {
    /** No wallet on this device speaks the adapter. */
    NO_WALLET(R.string.pass_no_wallet),

    /** The authorize round-trip ended without an account: closed, declined, or no answer. */
    NOT_CONNECTED(R.string.pass_not_connected, retryable = true),

    /** 503 `monetization_disabled`, or a 404: this route is not open yet. Not an error. */
    NOT_OPEN(R.string.pass_not_open),

    RATE_LIMITED(R.string.pass_rate_limited, retryable = true),

    /** The server did not build the transfer: a refusal it explained, a 5xx, or no answer at all. */
    UNAVAILABLE(R.string.pass_unavailable, retryable = true),

    /** No signature came back: declined, closed, or a session that dropped. */
    NOT_APPROVED(R.string.pass_not_approved, retryable = true),

    /** The wallet answered with a failure. Whether the transfer reached the network is not known. */
    FAILED(R.string.pass_failed, retryable = true),
}
