package com.plainticker.mobile.ui.vote

import androidx.annotation.StringRes
import com.plainticker.mobile.R
import com.plainticker.mobile.data.plainticker.VoteBuild

/**
 * The vote machine's states, and every transition between them.
 *
 * SKR-weighted coverage curation (docs/skr-curation-spec-2026-09-13.md): an xStock nobody has
 * analyzed is voted for with the weight of the voter's staked SKR, and the vote is a transaction
 * carrying a memo, so the signer is the voter by construction and there is no login anywhere.
 *
 *     vote(ticker)
 *     Closed ......> Opening(CONNECTING) ....> Opening(READING) ....> Building ....> Ready
 *                         :                          :                    :            :
 *                         : no wallet on the         : nothing staked,    : 404,       : confirm()
 *                         : device, or nothing       : or a principal     : or the     v
 *                         : connected                : outside the bound  : server   Signing
 *                         v                          v                    : did not    :
 *                      Refused                    Refused                 : answer     : a signature
 *                                                                         v            v
 *                                                                      Refused       Landed
 *
 *     Refused <- no signature came back, or the wallet answered with a failure, from Signing.
 *     Closed  <- close(), from any state.
 *
 * Three rules live in the machine and nowhere else.
 *
 * 1. **The stake is read before the server is asked.** A wallet with nothing staked has no weight
 *    to vote with, so it never costs a request, and the sentence it gets says what is actually
 *    true of it rather than blaming the server.
 * 2. **The figure is bounded before it is shown.** [com.plainticker.mobile.data.rpc.SkrStakeBound]
 *    refuses a principal larger than everything staked, and a refused figure is never printed and
 *    then hedged: the surface says the stake could not be read.
 * 3. **Nothing is signed before the figure is on the screen.** [Ready] carries the weight, the fee
 *    and the collector, and it is a state the machine will not leave on its own, because a person
 *    approving a transaction is owed the figure it carries.
 */
sealed interface VoteState {

    /** No vote is in progress and the sheet is not up. */
    data object Closed : VoteState

    /** A state that knows which xStock it is about. Everything except [Closed]. */
    sealed interface OnTicker : VoteState {
        /** The underlying equity ticker, the key the server and the rest of the app join on. */
        val ticker: String

        /** What a reader calls it, the tokenized symbol: NFLX is voted for as NFLXx. */
        val symbol: String
    }

    /** A round-trip is in flight. [phase] names which one, so no state of this sheet is a spinner. */
    sealed interface Running : OnTicker {
        val phase: VotePhase
    }

    /** Authorizing the wallet, then reading what it has staked. No server call has been made. */
    data class Opening(
        override val ticker: String,
        override val symbol: String,
        override val phase: VotePhase,
    ) : Running

    /** The wallet is known and its stake is bounded; the server is assembling the transaction. */
    data class Building(
        override val ticker: String,
        override val symbol: String,
        val voter: String,
        val stakeRaw: Long,
    ) : Running {
        override val phase: VotePhase get() = VotePhase.BUILDING
    }

    /**
     * The transaction is in hand and nothing has been signed. The weight, the fee and the
     * collector are on the screen, and the machine stays here until the reader acts.
     */
    data class Ready(
        override val ticker: String,
        override val symbol: String,
        val voter: String,
        val stakeRaw: Long,
        val build: VoteBuild,
    ) : OnTicker

    /** The wallet is open: it signs the transaction and submits it, in one round-trip. */
    data class Signing(
        override val ticker: String,
        override val symbol: String,
        val voter: String,
        val stakeRaw: Long,
        val build: VoteBuild,
    ) : Running {
        override val phase: VotePhase get() = VotePhase.SIGNING
    }

    /** The vote landed. [signature] is base58, the same form the swap receipt names. */
    data class Landed(
        override val ticker: String,
        override val symbol: String,
        val stakeRaw: Long,
        val signature: String,
    ) : OnTicker

    /** The attempt is over. [reason] is one of this app's own sentences, never the server's. */
    data class Refused(
        override val ticker: String,
        override val symbol: String,
        val reason: VoteRefusal,
    ) : OnTicker

    val isBusy: Boolean get() = this is Running
}

/** What is in flight, as the live bar says it. Each one is a sentence, never a bare spinner. */
enum class VotePhase(@StringRes val text: Int) {
    /** Authorizing the wallet, because this app holds no session of its own. */
    CONNECTING(R.string.vote_phase_connecting),

    /** The pinned `getProgramAccounts` over the SKR staking program, through the forwarder. */
    READING(R.string.vote_phase_reading),

    /** POST /api/v1/vote/build. The app cannot build the transaction itself. */
    BUILDING(R.string.vote_phase_building),

    /** The wallet signs and submits, in the one round-trip Mobile Wallet Adapter makes mandatory. */
    SIGNING(R.string.vote_phase_signing),
}

/**
 * Every way a vote can end without landing, as one of this app's own sentences.
 *
 * A server string never reaches a screen. The contract's `error` field is a human sentence, but
 * it is written by the server, is not translated, and is not subject to the copy lint that holds
 * every other sentence in this app to its voice. It goes to the debug log; the reader gets one of
 * these.
 *
 * [retryable] is whether tapping again could plausibly end differently. Voting not being open
 * yet, a wallet with nothing staked and a device with no wallet at all are not retries, they are
 * answers, and offering to try again would be the app pretending it did not understand them.
 */
enum class VoteRefusal(@StringRes val text: Int, val retryable: Boolean = false) {
    /** No wallet on this device speaks the adapter, so there is nothing to vote with. */
    NO_WALLET(R.string.vote_no_wallet),

    /**
     * The authorize round-trip ended without an account: closed, declined, or the wallet did not
     * answer. Mobile Wallet Adapter does not tell the three apart, so the sentence claims none of
     * them and says only what is certainly true, which is that nothing was connected.
     */
    NOT_CONNECTED(R.string.vote_not_connected, retryable = true),

    /** Connected, and the staking program holds nothing for this wallet. There is no weight. */
    NO_STAKE(R.string.vote_no_stake),

    /**
     * The staking read failed, or what came back was outside
     * [com.plainticker.mobile.data.rpc.SkrStakeBound]. No figure is shown either way.
     */
    STAKE_UNREAD(R.string.vote_stake_unread, retryable = true),

    /** HTTP 404 from `vote/build`: the route is not published yet. Not an error, a state. */
    NOT_OPEN(R.string.vote_not_open),

    /** The server did not build the vote: a refusal it explained, a 5xx, or no answer at all. */
    UNAVAILABLE(R.string.vote_unavailable, retryable = true),

    /**
     * The approval round-trip came back with no signature. Declined, closed, or a session that
     * dropped: the app cannot tell them apart, and it does not have to. Nothing was signed and
     * nothing was sent.
     */
    NOT_APPROVED(R.string.vote_not_approved, retryable = true),

    /**
     * The wallet answered with a failure. It signs and submits in one call, so whether the
     * transaction reached the network is not something this app can know, and the sentence says
     * so rather than claiming the vote did or did not land.
     */
    FAILED(R.string.vote_failed, retryable = true),
}
