package com.plainticker.mobile.data.receipts

import kotlinx.serialization.Serializable

/**
 * One vote this app landed, as this app saw it (task A3, docs/plan-monetisation-2026-09-19.md
 * section 1.5: "the wallet's own votes for this round").
 *
 * **Across a wallet change.** The MWA session lives in the adapter's memory alone and answers to
 * no cold start (DESIGN.md section 1.2), so "the connected wallet" is a fact of the moment, not of
 * the device. A receipt is written once, permanently, the instant a vote lands, and is never
 * deleted, edited or scoped away by a later disconnect or a different wallet connecting: the
 * signature is a public fact on the chain and forgetting the session that made it does not make it
 * stop having happened. [voter] is recorded so "Your votes" can narrow to the wallet actually
 * connected right now; the narrowing is a read-time filter in [com.plainticker.mobile.ui.vote.VoteTabViewModel],
 * not a property of storage. With no wallet connected (every cold start, until the reader
 * reconnects) there is no live voter to narrow against, and the section falls back to showing
 * every receipt this device holds: an honest history of what this device did, not a claim about
 * who is holding it now. The instant a wallet reconnects the section narrows to that address alone.
 *
 * [round] is this app's own guess at which round the vote counted for: the round [VoteViewModel]
 * had fetched when the tap that started this vote was made, not a figure the server sends back
 * from `vote/build`. It is null for a vote cast before the app knew about rounds at all (from the
 * List's row or Detail's action, neither of which reads `next-up.round`), and a receipt like that
 * simply falls outside any round filter rather than claiming a round it was never told. The tally
 * is the only authority on which round a vote actually counted in; this is what the reader's own
 * device remembers about what it asked the wallet to sign.
 */
@Serializable
data class VoteReceipt(
    /** The transaction signature. Also the identity of the receipt: one landing, one row. */
    val signature: String,
    /** The underlying equity ticker: the Detail route and the server's join key. */
    val ticker: String,
    /** What a reader calls it, the tokenized symbol ("NFLXx"). */
    val symbol: String,
    /**
     * The staked SKR this vote was counted at, in base units (six decimals): the server's own
     * figure where `vote/build` sent one, the app's bounded read otherwise, exactly what the
     * landed sheet showed at the moment of signing.
     */
    val weightRaw: Long,
    /** Wall clock when the vote landed, epoch millis. */
    val landedAtMillis: Long,
    /** The wallet that signed, base58. See the wallet-change note above. */
    val voter: String,
    val round: Int? = null,
)
