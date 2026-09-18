# Demo video — script of record

Three minutes, the cap the Brief sets. Recorded 2 October on the release build, on the Seeker,
screen capture with voice-over. Completion is judged from this file's result, so it is the one
deliverable that has to be finished rather than merely started.

**Voice:** the app's own. Calm and exact, a reading instrument rather than a trading terminal. The
number is the argument and no intensifier is added to it. Nothing here says revolutionary, seamless,
powerful or unlock, and nothing raises its voice, including about its own good news.

**Timing, counted rather than estimated.** The voice-over below is **155 seconds of speech at 140
words a minute**, inside a 180-second cap, leaving **25 seconds** for the screen to breathe. The
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
*70 words · 30 s speech · 32 s slot*

**Screen:** back to the List, scroll past Analyzed into Without analysis. Over that section the
strip: `Next up, by staked SKR`, and under it three leaders, each carrying its token and company,
the voters on the meta line, and the staked SKR behind it as the value. The leaders are lifted out
of the rows below, so no ticker is drawn twice. Tap Vote on the first of them. The sheet states the
staked SKR this vote carries, the signature fee in SOL, the address it is sent to, and the sentence
about large stakes. Seed Vault opens, fingerprint, and what it signs is a memo transaction rather
than a swap. The sheet returns as a receipt: `Vote sent`, the staked SKR behind that ticker, and
the signature in mono. One cutaway to that ticker's Detail, where the standing line reads
`Next up: <place> of <tickers with votes>, by staked SKR` with the weight and the voters under it.

> Six hundred and seventy-two of these have no analysis, because each one costs money to produce.
> Nobody decides which comes next.
>
> Staked SKR does. These three are what stake asked for. You vote for the one you want read, and
> the vote is a transaction, so the signer is the voter.
>
> A vote weighted by stake is decided by the largest stake. The app says so where you cast it.

**The strip afterwards is a second take, and the script may not pretend otherwise.** The tally is a
ten-minute cron that re-reads each voter's staked principal from the chain, so the strip does not
move on the signature. Film the List again once the tally has cycled and the leader's weight has
risen, keep the two shots visibly separate, and let the voice-over say nothing that implies one
continuous take. The vote landing is the shot; the strip rising is the loop closing, and it is
worth the wait rather than worth a cut that claims it happened instantly.

**This segment exists only if the three gates below are closed**, and the section under it says
who closes them.

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

## The three gates that decide whether SKR is in the video at all

Rewritten 2026-09-18, the day the server half stopped being hypothetical. Both halves are built and
both are reviewed with their findings fixed, and both sit in open pull requests. Server,
`louraider/investor24-analyst#112`: `POST /api/v1/vote/build` returns an unsigned v0 transaction
carrying a `PT-VOTE:<TICKER>` memo and a zero-lamport transfer to the collector, a ten-minute cron
re-reads each voter's staked principal with the pinned `getProgramAccounts` and upserts the weight,
`GET /api/v1/vote/next-up` sums it, migration 0023 carries the columns, 2,345 tests pass. App,
`louraider/plainticker-mobile#17`: the Next up strip over the uncovered section, the standing line
on Detail, the sheet that knows `already_voted`, `vote_not_configured` and `expiresAt`, 760 unit
tests.

It is still not live. `/vote/build` answers 503, the app says voting is not open yet, and that
sentence is the honest screen rather than a bug. Three things stand between it and a recordable
segment, and every one of them is the founder's:

1. **Migration 0023 applied to the production database by hand.** In that repository `.env.local`
   is the production environment, one Neon database, and a migration there is applied by the
   operator and by nobody else.
2. **`VOTE_COLLECTOR_PUBKEY` set on Vercel, for production and for preview.** Unset is what the 503
   reports. The collector is `2KyWZetthwBAXji88M2Fz5ob4RPbYxCCBBKXueSoS7tJ`.
3. **Both pull requests merged**, the server one first, because the app's strip reads a route that
   has to answer before it can draw anything.

Then a canary from the Seeker, which is the only evidence this file accepts: one vote signed on
mainnet, its memo readable in an explorer, and the strip carrying that weight after the next
ten-minute tally.

**Go or no-go: the end of Saturday 26 September**, decided on what has landed rather than on what
is about to. That date sits where it does for three reasons. The three gates have to close by 25
September for a canary to have a day to run and a tally to have cycled, so 26 September is the
first honest reading. The feature freeze is the end of 29 September, which leaves three clear days
to re-time this file and rehearse the shorter cut before anything is frozen. And recording is 2
October, six days out, so the shorter cut gets walked twice rather than discovered on the day.

**If it is not live by then, cut 2:00 to 2:32 entirely.** Give twenty of those seconds to the trust
layer and twelve to the swap, and re-time the rest from this file's own counts: the five remaining
segments are 292 words, 125 seconds of speech, so the three minutes hold them with 55 seconds of
air instead of 25. Do not film the vote reaching the wallet and stopping, and do not film the sheet
saying voting is not open: a demo that shows a feature failing is worse than a demo that does not
mention it, and the SKR prize is judged from this video.

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
