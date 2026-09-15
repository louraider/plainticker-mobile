# Demo video — script of record

Three minutes, the cap the Brief sets. Recorded 2 October on the release build, on the Seeker,
screen capture with voice-over. Completion is judged from this file's result, so it is the one
deliverable that has to be finished rather than merely started.

**Voice:** the app's own. Calm and exact, a reading instrument rather than a trading terminal. The
number is the argument and no intensifier is added to it. Nothing here says revolutionary, seamless,
powerful or unlock, and nothing raises its voice, including about its own good news.

**Timing, counted rather than estimated.** The voice-over below is **154 seconds of speech at 140
words a minute**, inside a 180-second cap, leaving **26 seconds** for the screen to breathe. The
first draft of this script ran to 188 seconds of speech, which overran the cap before a single
pause; the sections below carry their own word counts so the next edit cannot repeat that quietly.

---

## 0:00 — 0:18 · What the phone already shows you
*34 words · 15 s speech · 18 s slot*

**Screen:** the Seeker's own wallet, Stocks tab, scrolling. Ticker, price, ticker, price.

> Your phone already sells tokenized stocks. This is the whole of what it tells you about one. A
> ticker, and a price.
>
> Both are true. Neither is the thing you need before you swap.

**Cut on the word "swap".** No logo, no title card. The product has eighteen seconds to earn the
next two minutes and a wordmark spends them on nothing.

---

## 0:18 — 0:50 · The same token, read
*69 words · 30 s speech · 32 s slot*

**Screen:** PlainTicker opens on the List. Scroll to APPx, whose meta line reads `$34 behind this
price, too thin`. Tap through to Detail: where a premium would be drawn there is a sentence
instead. Then one cutaway to NFLXx, where the gauge does draw, `-1.95% vs NYSE close`.

> This is the same token in PlainTicker. The price is real. What stands behind it is thirty-four
> dollars.
>
> So the app does not draw the premium. It says why, and it draws nothing.
>
> Where there is a real market it draws the number. Netflix, two percent under the New York close.
>
> The difference is not a setting. It is four thousand dollars of depth, measured per token, every
> time.

**The beat to protect:** the empty gauge slot. It is the only moment in three minutes where the
product's whole argument is visible as an absence, and the Netflix cutaway exists only to make that
absence legible. Four seconds on Netflix, no more.

---

## 0:50 — 1:28 · What the issuer can do to you while you hold it
*74 words · 32 s speech · 38 s slot*

**Screen:** scroll Detail into Backing and controls. Proof of reserves 101.5 percent, sixteen
thousand seven hundred and eighteen shares held by Alpaca. Permanent delegate: Yes. Transfers
pausable: Yes. Then the live bar: `slot 446,726,998 · 5 s ago`.

> Under that, the part nobody shows. This token is a Token-2022 mint, and its issuer holds a
> permanent delegate: they can move it out of your wallet without your signature. They can pause
> transfers. Both are true right now.
>
> Reserves are a hundred and one and a half percent, held by a named custodian.
>
> Every line of that is read from the mint, on this phone, at the slot it names, five seconds ago.

**For the two security researchers on the panel this is the segment.** Thirty-eight seconds, and it
should not be shortened to make room for anything else.

---

## 1:28 — 2:00 · A real swap, from this phone
*54 words · 23 s speech · 32 s slot*

**Screen:** the swap sheet, five dollars of USDC into TSLAx. Seed Vault opens, fingerprint, the
sheet returns. The receipt: `4.995 USDC · to 0.013629 TSLAx · all-in cost 0.32%` and the signature
in mono. Tap the signature; it copies.

> Swapping is the easy part and the wallet already does it. This does it without leaving the
> reading, through Mobile Wallet Adapter and the Seed Vault.
>
> Five dollars of USDC into thirteen thousandths of a Tesla xStock, all-in cost a third of a
> percent. A signature on mainnet, and the app keeps the receipt.

**Record this live on the day.** A swap from the demo wallet, not a replay. The signature has to be
one a judge can paste into an explorer while the video is still open.

---

## 2:00 — 2:32 · What staked SKR decides
*67 words · 29 s speech · 32 s slot*

**Screen:** back to the List, scroll past Analyzed into Without analysis, a long run of rows with a
price and nothing else. Tap Vote on one. The sheet shows the staked SKR the vote carries, the
signature fee, and the sentence about large stakes. Sign. The vote lands.

> Six hundred and seventy-two of these have no analysis, because each one costs money to produce.
> Nobody decides which comes next.
>
> Staked SKR does. You vote for the one you want read, weighted by your stake, and the vote is a
> transaction, so the signer is the voter.
>
> A vote weighted by stake is decided by the largest stake. The app says so where you cast it.

**This segment cannot be recorded until the server half ships.** See the blocker below.

---

## 2:32 — 3:00 · What it will not tell you
*61 words · 26 s speech · 28 s slot*

**Screen:** the Method section on Detail. Then airplane mode, and Detail again:
`Nothing below this line is read from the chain`, every trust row Unknown with its own reason.

> One thing this app never does is tell you what to do. No buy, no sell, no hold. That is not
> modesty. We backtested our own method, found no detectable edge, and published the dataset. What
> you get is a classification, not a forecast.
>
> And when it cannot read something, it says which. Not a blank, not a stale number. Which.

**Last frame:** the wordmark and the two-corner mark, held for two seconds, silent.

---

## The blocker that decides whether SKR is in the video at all

The vote segment, 2:00 to 2:32, needs `POST /api/v1/vote/build` to exist. Today it answers 404 and
the app correctly says voting is not open. Recording is 2 October, so the server half has
seventeen days, and it is blocked on four things only the founder can do: the trigger to touch the
web repository, migration 0023 applied to production, a collector address that is not the founder's
own wallet, and whether the weight is linear, square-rooted or capped.

**If it does not ship, cut 2:00 to 2:32 entirely.** Give twenty of those seconds to the trust layer
and twelve to the swap, and re-time the rest from this file's own counts. Do not film the vote
reaching the wallet and stopping: a demo that shows a feature failing is worse than a demo that
does not mention it, and the SKR prize is judged from this video.

## What to cut first if it still runs long

In this order. The Netflix counter-example has already been folded into section two rather than
kept as its own segment, which is where the first eighteen seconds came from.

1. **The custodian figure** in the trust layer, six seconds. Permanent delegate and pausable
   transfers are the two that matter; reserves are a nice-to-have.
2. **The airplane-mode half of the close**, six seconds. Keep the sentence about no verdict. That
   one is the product.
3. **The Netflix cutaway**, four seconds, and with it the sentence that names it. Losing it costs
   the contrast, so it goes last.

Never cut: the empty gauge slot, the permanent delegate line, the signature on screen.

## What was deliberately left out, because a judge would not care

The cold-start time of 2.95 seconds, the 718 unit tests, ten cold starts with no crash, the
coverage-health script, the history rewrite, the design system, the redaction guard, and the
liquidity measurement methodology. Every one of those is real and several were hard, and all of
them belong in the README and the deck where somebody is choosing to read. In a three-minute video
they are thirty seconds that could have been the mint.
