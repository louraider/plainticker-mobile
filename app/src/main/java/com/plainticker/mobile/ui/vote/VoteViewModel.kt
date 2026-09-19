package com.plainticker.mobile.ui.vote

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.funkatronics.encoders.Base58
import com.plainticker.mobile.BuildConfig
import com.plainticker.mobile.core.Clock
import com.plainticker.mobile.core.WallClock
import com.plainticker.mobile.data.plainticker.VoteApi
import com.plainticker.mobile.data.plainticker.VoteBuild
import com.plainticker.mobile.data.plainticker.VoteError
import com.plainticker.mobile.data.receipts.VoteReceipt
import com.plainticker.mobile.data.receipts.VoteReceiptStore
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.repo.RpcRepository
import com.plainticker.mobile.wallet.WalletOutcome
import com.plainticker.mobile.wallet.WalletSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the raw upstream text goes, which is never to a screen. The server's `error` sentence and
 * the wallet's own failure message are the only things that explain a refusal afterwards, and they
 * belong in a log rather than in front of a person who cannot act on them.
 */
fun interface VoteDebugLog {
    fun raw(line: String)

    companion object {
        /** Debug builds only: a release build keeps no upstream text at all. */
        val ANDROID = VoteDebugLog { line -> if (BuildConfig.DEBUG) Log.d("VoteMachine", line) }
    }
}

/**
 * The vote machine. Its states, and every transition between them, are [VoteState].
 *
 * A Seeker owner votes with the weight of their staked SKR for which of the 672 xStocks with no
 * analysis is covered next (docs/skr-curation-spec-2026-09-13.md). The vote is a transaction
 * carrying a memo rather than a request behind a login, which is what makes the signer the voter
 * by construction: there is no nonce, no token, no replay window, and no account anywhere in
 * this app.
 *
 * **The app does not build the transaction.** PlainTicker's RPC forwarder allows five read-only
 * methods and `getLatestBlockhash` is not one of them, so nothing on the device can date a
 * transaction. [VoteApi] asks the server for one, and the wallet signs and submits it through
 * `signAndSendTransactions`, which Mobile Wallet Adapter 2.x makes mandatory. It is the same
 * division of labour as the swap, which already lands real money on mainnet.
 *
 * **The weight is read here and it is bounded here.** [RpcRepository.skrStake] reads the voter's
 * principal through the pinned `getProgramAccounts`, and [SkrStakeBound] refuses a figure larger
 * than everything that is staked. The bounded figure is on the screen before anything is signed,
 * because a person approving a transaction is owed the number it carries. What the vote is
 * actually counted at is the server's own read of the same accounts, so nothing sent from here
 * can inflate it; and since 2026-09-18 the server sends that figure back as `summary.weight`, so
 * the confirm step shows the figure that counts wherever the server has stated one.
 *
 * **A stale transaction is never signed.** The server promises its blockhash for 45 s. A confirm
 * past that goes back to the server for a fresh transaction and returns to the confirm step,
 * marked [VoteState.Ready.refreshed] so the sheet can say what happened, rather than spending a
 * wallet approval on a hash the network will refuse.
 *
 * **A landed vote is recorded before the state says it landed.** Task A3: [voteReceipts] gets a
 * [VoteReceipt] built from the same figures the sheet is about to show, written on [ioDispatcher]
 * rather than the Main dispatcher this class otherwise runs on, so "Your votes" and the landed
 * sheet can never drift apart and the file write never blocks a frame. [pendingRoundId] is the
 * round the caller believed was open when the tap that started this attempt was made (the Vote
 * tab's ballot knows it; a row on the List or on Detail does not, so it stays null there), carried
 * as a plain field rather than as a fifth [VoteState] because no state in that machine needs to
 * draw it.
 */
class VoteViewModel(
    private val voteApi: VoteApi,
    private val wallet: WalletSession,
    private val rpc: RpcRepository,
    private val voteReceipts: VoteReceiptStore,
    private val clock: Clock = WallClock,
    private val debugLog: VoteDebugLog = VoteDebugLog.ANDROID,
    /** Where the receipt is written. viewModelScope runs on Main, and a file write does not. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow<VoteState>(VoteState.Closed)
    val state: StateFlow<VoteState> = _state.asStateFlow()

    private var job: Job? = null

    /** See the class doc: the round this attempt believes is open, for the receipt alone. */
    private var pendingRoundId: Int? = null

    /**
     * The tap. [ticker] is the equity ticker the server joins on, [symbol] what a reader calls it.
     * [roundId] is the round the caller currently believes is open, recorded on the receipt if
     * this attempt lands; null when the caller has no round to offer.
     *
     * Connect if there is no session, read the stake, bound it, then ask the server to build. It
     * stops at [VoteState.Ready] with the figure on the screen; nothing is signed until [confirm].
     */
    fun vote(ticker: String, symbol: String, roundId: Int? = null) {
        if (_state.value.isBusy) return
        pendingRoundId = roundId
        // Set here and not inside the coroutine, so the sheet is up on the frame the row was
        // tapped and so a second tap on a second row cannot start a second attempt in the gap
        // before the first one is scheduled.
        //
        // CONNECTING is entered only when this app holds no session, which makes the phase
        // itself the answer to "is a wallet connected": reaching it means one was not.
        val known = wallet.account.value?.address
        _state.value =
            VoteState.Opening(ticker, symbol, if (known == null) VotePhase.CONNECTING else VotePhase.READING)
        job?.cancel()
        job = viewModelScope.launch { attempt(ticker, symbol, known) }
    }

    /** Try the whole attempt again, from the state that offered it, with the same round. */
    fun retry() {
        val refused = _state.value as? VoteState.Refused ?: return
        if (!refused.reason.retryable) return
        _state.value = VoteState.Closed
        vote(refused.ticker, refused.symbol, pendingRoundId)
    }

    /**
     * Approve: the wallet signs the server's transaction and submits it, in one round-trip.
     *
     * There is no second confirmation and no debug gate. The vote costs one signature fee, about
     * 5,000 lamports, and it is the whole of what this feature does; a build that could not vote
     * could not be walked on a device at all.
     *
     * One thing stands between the tap and the wallet: the transaction's own expiry. The server
     * promises its blockhash for 45 s, and a reader who left the sheet open past that is not
     * handed a stale transaction to approve, because the approval would be spent on a failure the
     * network reports afterwards. The server is asked again instead and the machine returns to
     * the confirm step with a fresh transaction, and a fresh figure, to approve.
     */
    fun confirm() {
        val ready = _state.value as? VoteState.Ready ?: return
        job?.cancel()
        job = viewModelScope.launch {
            if (ready.build.isExpiredAt(clock.nowMillis())) {
                debugLog.raw("vote/build expired at ${ready.build.expiresAt}; asking again before anything is signed")
                build(ready.ticker, ready.symbol, ready.voter, ready.stakeRaw, refreshed = true)
            } else {
                send(ready)
            }
        }
    }

    /** Dismiss from any state. A round-trip in flight is dropped with it. */
    fun close() {
        job?.cancel()
        job = null
        _state.value = VoteState.Closed
    }

    // ---- The attempt ---------------------------------------------------------------------------

    private suspend fun attempt(ticker: String, symbol: String, known: String?) {
        val voter = known ?: when (val outcome = wallet.connect()) {
            is WalletOutcome.Success -> outcome.value.address
            is WalletOutcome.NoWallet -> return refuse(ticker, symbol, VoteRefusal.NO_WALLET)
            is WalletOutcome.Cancelled -> return refuse(ticker, symbol, VoteRefusal.NOT_CONNECTED)
            is WalletOutcome.Error -> {
                debugLog.raw("connect: ${outcome.message}")
                return refuse(ticker, symbol, VoteRefusal.NOT_CONNECTED)
            }
        }

        _state.value = VoteState.Opening(ticker, symbol, VotePhase.READING)
        val stake = try {
            rpc.skrStake(voter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLog.raw("skrStake threw ${e::class.simpleName}: ${e.message}")
            return refuse(ticker, symbol, VoteRefusal.STAKE_UNREAD)
        }

        // The bound, before the figure exists anywhere a screen could read it. A principal larger
        // than the whole staked supply is a misread and not a large stake, so nothing is printed.
        val stakeRaw = SkrStakeBound.principalOf(stake)
        if (stakeRaw == null) {
            debugLog.raw("stake outside the bound: ${stake.accounts.map { it.principalRaw }}")
            return refuse(ticker, symbol, VoteRefusal.STAKE_UNREAD)
        }
        // Nothing staked is not a failed read, and it never costs the server a request: this
        // wallet has no weight to vote with, and that is what its sentence says.
        if (stakeRaw == 0L) return refuse(ticker, symbol, VoteRefusal.NO_STAKE)

        build(ticker, symbol, voter, stakeRaw)
    }

    /**
     * The server step: ask for the transaction, then stop at [VoteState.Ready] with the figure on
     * the screen. Reached from [attempt] once the stake is bounded, and again from [confirm] when
     * the transaction it was about to sign has gone stale, so the machine returns to the confirm
     * step with a fresh one rather than spending an approval on a dead blockhash. [refreshed]
     * carries that second case onto the screen, so a tap that produced a new transaction instead
     * of a wallet is not a tap that appeared to do nothing.
     */
    private suspend fun build(
        ticker: String,
        symbol: String,
        voter: String,
        stakeRaw: Long,
        refreshed: Boolean = false,
    ) {
        _state.value = VoteState.Building(ticker, symbol, voter, stakeRaw)
        val build = try {
            voteApi.build(ticker, voter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: VoteError) {
            debugLog.raw("vote/build refused: status=${e.status} code=${e.code} ${e.detail ?: e.message}")
            // Three refusals are answers with sentences of their own: voting not being open (a
            // 404 from a route nobody has published, or a 503 from one not yet set up), a wallet
            // that has already voted for this ticker, and a server taking votes more slowly than
            // this. Every other refusal, explained or not, is the server not having built the vote.
            val reason = when (e) {
                is VoteError.NotOpen -> VoteRefusal.NOT_OPEN
                is VoteError.AlreadyVoted -> VoteRefusal.ALREADY_VOTED
                is VoteError.RateLimited -> VoteRefusal.RATE_LIMITED
                else -> VoteRefusal.UNAVAILABLE
            }
            return refuse(ticker, symbol, reason)
        } catch (e: Exception) {
            debugLog.raw("vote/build threw ${e::class.simpleName}: ${e.message}")
            return refuse(ticker, symbol, VoteRefusal.UNAVAILABLE)
        }

        _state.value = VoteState.Ready(ticker, symbol, voter, stakeRaw, build, refreshed = refreshed)
    }

    private suspend fun send(ready: VoteState.Ready) {
        val build: VoteBuild = ready.build
        // Decoded here rather than inside the wallet round-trip: bytes this app cannot read are
        // this app's problem, and failing mid-call would spend an approval and then blame the
        // wallet for a payload it was never handed. VoteApi has already refused a 200 that
        // carried no readable transaction, so this is the belt to that brace.
        val unsigned = build.transactionBytes()
        if (unsigned == null) {
            debugLog.raw("vote/build carried no transaction this app could read")
            return refuse(ready.ticker, ready.symbol, VoteRefusal.UNAVAILABLE)
        }

        _state.value = VoteState.Signing(ready.ticker, ready.symbol, ready.voter, ready.stakeRaw, build)
        val outcome = wallet.call { it.signAndSendTransactions(arrayOf(unsigned)) }
        val signature = when (outcome) {
            is WalletOutcome.Success -> outcome.value.signatures.firstOrNull()
            is WalletOutcome.NoWallet -> return refuse(ready.ticker, ready.symbol, VoteRefusal.NO_WALLET)
            // No signature came back. Declined, closed, or a session that dropped: the app cannot
            // tell them apart and does not have to, because all three mean the same thing here.
            is WalletOutcome.Cancelled -> return refuse(ready.ticker, ready.symbol, VoteRefusal.NOT_APPROVED)
            is WalletOutcome.Error -> {
                debugLog.raw("wallet: ${outcome.message}")
                // signAndSendTransactions signs and submits in one call, so a failure here says
                // nothing about whether the transaction reached the network. The sentence for
                // FAILED claims neither, which is why it is not the one Cancelled gets.
                return refuse(ready.ticker, ready.symbol, VoteRefusal.FAILED)
            }
        }
        if (signature == null || signature.isEmpty()) {
            debugLog.raw("signAndSendTransactions answered with no signature")
            return refuse(ready.ticker, ready.symbol, VoteRefusal.FAILED)
        }

        // The figure that was signed for: the server's own where it stated one, because that is
        // the figure the vote is counted at, and the app's bounded read where it did not.
        val weightRaw = build.summary.weight ?: ready.stakeRaw
        val signatureText = Base58.encodeToString(signature)
        // The record is written before the state says it landed, so "Your votes" and the landed
        // sheet can never disagree about whether this vote happened, the same rule and the same
        // shape SwapViewModel already keeps for its own receipt: viewModelScope runs on Main, and
        // a temp-file write and rename does not belong there.
        val receipt = VoteReceipt(
            signature = signatureText,
            ticker = ready.ticker,
            symbol = ready.symbol,
            weightRaw = weightRaw,
            landedAtMillis = clock.nowMillis(),
            voter = ready.voter,
            round = pendingRoundId,
        )
        withContext(ioDispatcher) { voteReceipts.record(receipt) }
        _state.value = VoteState.Landed(
            ticker = ready.ticker,
            symbol = ready.symbol,
            stakeRaw = weightRaw,
            signature = signatureText,
        )
    }

    private fun refuse(ticker: String, symbol: String, reason: VoteRefusal) {
        _state.value = VoteState.Refused(ticker, symbol, reason)
    }
}
