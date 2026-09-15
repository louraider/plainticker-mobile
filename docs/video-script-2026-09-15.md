# Demo video — script of record

Three minutes, the cap the Brief sets. Recorded 2 October on the release build, on the Seeker,
screen capture with voice-over. Completion is judged from this file's result, so it is the one
deliverable that has to be finished rather than merely started.

**Voice:** the app's own. Calm and exact, a reading instrument rather than a trading terminal. The
number is the argument and no intensifier is added to it. Nothing here says revolutionary, seamless,
powerful or unlock, and nothing raises its voice, including about its own good news.

**Read aloud at about 140 words a minute.** The whole voice-over below is 407 words, which lands at
2 minutes 55 with the pauses written in.

---

## 0:00 — 0:18 · What the phone already shows you

**Screen:** the Seeker's own wallet, Stocks tab, scrolling. Ticker, price, ticker, price.

> Your phone already sells tokenized stocks. This is the whole of what it tells you about one. A
> ticker, and a price.
>
> Both are true. Neither is the thing you need before you swap.

**Cut on the word "swap".** No logo, no title card. The product has eighteen seconds to earn the
next two minutes and a wordmark spends them on nothing.

---

## 0:18 — 0:44 · The same token, read

**Screen:** PlainTicker opens on the List. Scroll to APPx. The meta line reads
`$34 behind this price, too thin`. Tap through to Detail. Where a premium would be drawn there is a
sentence instead.

> This is the same token in PlainTicker. The price is real. What stands behind it is thirty-four
> dollars.
>
> So the app does not draw the premium. It says why, and it draws nothing.
>
> Of eight hundred and thirty-two tokenized stocks on Solana, twenty-two have more than four
> thousand dollars behind them. The rest quote a price anyway.

**The beat to protect:** the empty gauge slot. It is the only moment in three minutes where the
product's whole argument is visible as an absence.

---

## 0:44 — 1:16 · What the issuer can do to you while you hold it

**Screen:** scroll Detail into Backing and controls. Proof of reserves 101.5 percent, sixteen
thousand seven hundred and eighteen shares held by Alpaca. Permanent delegate: Yes. Transfers
pausable: Yes. Then the live bar: `slot 446,726,998 · 5 s ago`.

> Under that, the part nobody shows. This token is a Token-2022 mint, and its issuer holds a
> permanent delegate, which means they can move it out of your wallet without your signature. They
> can pause transfers. Both are true right now and both are on the screen.
>
> Reserves are a hundred and one and a half percent, held by a named custodian.
>
> Every line of that is read from the mint, on this phone, at the slot it names, five seconds ago.

**For the two security researchers on the panel this is the segment.** It is thirty-two seconds and
it should not be shortened to make room.

---

## 1:16 — 1:34 · Where there is a market, it says so

**Screen:** back, open NFLXx. The gauge draws. `-1.95% vs NYSE close`, caption `scale 4.5%`, the
tick inside the track.

> Where there is a real market the same screen draws the number. Netflix, one and ninety-five
> hundredths of a percent under the New York close.
>
> The difference between this screen and the last one is not a setting. It is four thousand dollars
> of depth, measured per token, every time.

---

## 1:34 — 2:06 · A real swap, from this phone

**Screen:** the swap sheet, five dollars of USDC into TSLAx. Seed Vault opens, fingerprint, the
sheet returns. The receipt: `4.995 USDC · to 0.013629 TSLAx · all-in cost 0.32%` and the signature
in mono. Tap the signature; it copies.

> Swapping is the easy part and the wallet already does it. This does it without leaving the
> reading, through Mobile Wallet Adapter and the Seed Vault.
>
> Four dollars and ninety-nine cents of USDC, into thirteen thousandths of a Tesla xStock, all-in
> cost a third of a percent. That is a signature on mainnet, and the app keeps the receipt.

**Record this live on the day.** A swap from the demo wallet, not a replay. The signature has to be
one a judge can paste into an explorer while the video is still open.

---

## 2:06 — 2:38 · What staked SKR decides

**Screen:** back to the List, scroll past Analyzed into Without analysis. Six hundred and
seventy-two rows with a price and nothing else. Tap Vote on one. The sheet shows the staked SKR the
vote carries, the signature fee, and the sentence about large stakes. Sign. The vote lands.

> Six hundred and seventy-two of those tokens have no analysis at all, because each one costs money
> to produce. Nobody decides which comes next.
>
> Staked SKR decides. You vote for the one you want read, weighted by what you have staked, and the
> vote is a transaction carrying a memo, so the wallet that signs it is the voter and anyone can
> count the votes off the chain the same way we do.
>
> A vote weighted by stake is decided by the largest stake. The app says that on the screen where
> you cast it.

**This segment cannot be recorded until the server half ships.** See the blocker below.

---

## 2:38 — 3:00 · What it will not tell you

**Screen:** the Method section on Detail. Then airplane mode, and Detail again:
`Nothing below this line is read from the chain`, every trust row Unknown with its own reason.

> One thing this app never does is tell you what to do. No buy, no sell, no hold. That is not
> modesty. We backtested our own method, found no detectable edge, and published the dataset, so
> what you get is a classification and not a forecast.
>
> And when it cannot read something, it says which. Not a blank, not a stale number. Which.

**Last frame:** the wordmark and the two-corner mark, held for two seconds, silent.

---

## The blocker that decides whether SKR is in the video at all

The vote segment, 2:06 to 2:38, needs `POST /api/v1/vote/build` to exist. Today it answers 404 and
the app correctly says voting is not open. Recording is 2 October, so the server half has
seventeen days, and it is blocked on four things only the founder can do: the trigger to touch the
web repository, migration 0023 applied to production, a collector address that is not the founder's
own wallet, and whether the weight is linear, square-rooted or capped.

**If it does not ship, cut 2:06 to 2:38 entirely** and give the thirty-two seconds back to the
trust layer and the swap. Do not film the vote reaching the wallet and stopping: a demo that shows
a feature failing is worse than a demo that does not mention it, and the SKR prize is judged from
this video.

## What to cut first if it runs long

In this order, and no further than the third without re-timing the whole thing.

1. **1:16 to 1:34, the Netflix counter-example.** Eighteen seconds. The contrast is nice and the
   thin-pool segment already carries the argument alone.
2. **The airplane-mode half of the close**, six seconds. Keep the sentence about no verdict; that
   one is the product.
3. **The custodian figure** in the trust layer, four seconds. Permanent delegate and pausable
   transfers are the two that matter.

Never cut: the empty gauge slot, the permanent delegate line, the signature on screen.

## What was deliberately left out, because a judge would not care

The cold-start time of 2.95 seconds, the 718 unit tests, ten cold starts with no crash, the
coverage-health script, the history rewrite, the design system, the redaction guard, and the
liquidity measurement methodology. Every one of those is real and several were hard, and all of
them belong in the README and the deck where somebody is choosing to read. In a three-minute video
they are thirty seconds that could have been the mint.
