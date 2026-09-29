package com.plainticker.mobile.ui.share

import com.plainticker.mobile.R
import com.plainticker.mobile.data.rpc.SkrStakeBound
import com.plainticker.mobile.ui.Copy
import com.plainticker.mobile.ui.Fmt
import com.plainticker.mobile.ui.raw
import com.plainticker.mobile.ui.swap.SwapState
import com.plainticker.mobile.ui.vote.VoteState
import com.plainticker.mobile.ui.words

/**
 * What a share image says (founder feedback 2026-09-29: Share sent only text). One card per
 * context, decided here as plain data so every fact a card can carry, and every fact it must not,
 * is a JVM test over the shipped strings.xml; [ShareCardRenderer] only draws it.
 *
 * **The size is 1080 by 1350**, a 4:5 portrait, and not the 1200 by 630 link-preview shape.
 * Instagram's feed takes 4:5 as its tallest uncropped post; X shows a single 4:5 image uncropped
 * in the timeline on phones; Telegram shows any image whole; a 9:16 story frames a 4:5 card with
 * a band above and below rather than cutting it. A 1.91:1 card is the one shape that works only
 * as a link preview: Instagram crops it to a square and on a phone it is a strip of small type.
 *
 * **The content rules apply to a picture too.** Only facts the screen read, the same way the
 * share text is built ([com.plainticker.mobile.ui.detail.shareClauses]); the classification only
 * when the server sent it to this reader, since a free reader's payload carries the lock and
 * never the word, and never a Pro number (the composite and the axes are not on any card, for
 * anyone). No amount of a swap, no wallet address; a transaction is its short signature.
 */
data class ShareCard(
    val kind: ShareCardKind,
    /** A short line above the headline: the token symbol over the company name. */
    val eyebrow: Copy?,
    /** The largest words on the card, inside the brand mark's two corners. */
    val headline: Copy,
    /** A plain label over the facts ("Backing and controls"). */
    val factsLabel: Copy?,
    val facts: List<ShareFact>,
    /** The link, printed without its scheme ("plainticker.com/en/AAPL"). */
    val url: Copy,
    /** Small print at the foot. */
    val footer: Copy?,
) {
    /** A stable part of the image's file name, so two kinds never overwrite one another mid-share. */
    val fileStem: String get() = kind.name.lowercase()
}

enum class ShareCardKind { Stock, Vote, Swap }

/**
 * One fact cell. Two half-width cells pair up on a row; a [span] of 2 takes the row alone.
 * [caution] colours the value, and only for an issuer control the mint actually carries (DESIGN.md
 * section 2); [mono] sets the value in JetBrains Mono, for an on-chain identifier only.
 */
data class ShareFact(
    val label: Copy,
    val value: Copy,
    val sub: Copy? = null,
    val span: Int = 1,
    val caution: Boolean = false,
    val mono: Boolean = false,
)

/**
 * A landed vote's card: "I voted for NVDA to be analyzed next", the round when this app knew it,
 * the staked SKR the vote was counted at, and the transaction's short signature. [round] is the
 * round the receipt was stamped with; null leaves the row out rather than guessing one.
 */
fun VoteState.Landed.shareCard(): ShareCard = ShareCard(
    kind = ShareCardKind.Vote,
    eyebrow = null,
    headline = words(R.string.share_card_vote_headline, ticker.trim()),
    factsLabel = null,
    facts = listOfNotNull(
        round?.let { ShareFact(label = words(R.string.share_card_vote_round), value = raw(Fmt.count(it))) },
        ShareFact(
            label = words(R.string.share_card_vote_weight),
            value = words(R.string.share_card_skr, Fmt.tokenAmount(stakeRaw, SkrStakeBound.SKR_DECIMALS)),
            span = if (round == null) 2 else 1,
        ),
        ShareFact(
            label = words(R.string.share_card_tx),
            value = raw(Fmt.shortKey(signature, head = 6, tail = 6)),
            span = 2,
            mono = true,
        ),
    ),
    url = words(R.string.share_card_vote_url),
    footer = words(R.string.share_card_vote_footer),
)

/**
 * A landed swap into a token: "Swapped into AAPLx", the route and the transaction, and nothing
 * about the money (no amount, no price, no cost). Null for a swap back to USDC, which is not
 * something to announce, and for a fill whose signature is unknown.
 */
fun SwapState.Landed.shareCard(): ShareCard? {
    if (!leg.intoToken) return null
    val signature = fill.signature.takeIf { it.isNotBlank() } ?: return null
    return ShareCard(
        kind = ShareCardKind.Swap,
        eyebrow = null,
        headline = words(R.string.share_card_swap_headline, leg.output.symbol),
        factsLabel = null,
        facts = listOf(
            ShareFact(label = words(R.string.share_card_swap_route), value = words(R.string.share_card_swap_route_value), span = 2),
            ShareFact(
                label = words(R.string.share_card_tx),
                value = raw(Fmt.shortKey(signature, head = 6, tail = 6)),
                span = 2,
                mono = true,
            ),
        ),
        url = words(R.string.share_card_home_url),
        footer = words(R.string.share_card_swap_footer),
    )
}
