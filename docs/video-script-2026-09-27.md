# Demo video: script of record

Written 2026-09-27, updated 2026-09-28 for build 1.3.21 and 2026-10-02 for build 1.3.27, on main and the latest release. It replaces
`docs/video-script-2026-09-15.md`, which was written before the redesign, before the vote went live and before Pro existed.

**One person's day on the current app,** filmed on a Seeker, screen capture with voice-over.
Target 2:30 to 3:00; the Brief caps the demo at three minutes.

## Why the spoken track carries everything

The Align AI coach and the judges read a **transcript** of the video (the uploaded captions first,
then YouTube's automatic ones), not the picture. So:

- Every claim that matters is **said out loud**: Mobile Wallet Adapter, Seed Vault, "verified as
  www.plainticker.com", the check the phone makes before the wallet opens and what stays outside
  it, and one mainnet signature, read briefly.
- Text burned into the picture is not read. Use none that carries an argument.
- **Upload the caption track** with the video: the "Caption text" block at the end, split to the
  timings you actually cut. Spell the product the way the captions should read it:
  "PlainTicker", "Seed Vault", "www.plainticker.com", "SKR".
- Nothing in the voice-over talks to the viewer about scoring, judging or prizes. The platform
  flags text addressed to reviewers.

**Voice:** calm and exact. The number is the argument. No "revolutionary", "seamless", "unlock",
no exclamation, no buy or sell verbs. The classification is a classification by a fixed rule, and
is never called advice or a recommendation.

**Timing:** the spoken lines below run to **401 words**, about **172 seconds** at 140 words
a minute. That leaves about 6 seconds of screen time without speech inside a 2:58 cut, which is
tight: the Brief caps the demo at three minutes, so if a take runs over, cut in the order at the
end of this document.

## Before filming

- **Film on a demo Google account, never the founder's personal Gmail,** and never connect the
  founder's private wallet. The You screen prints the signed-in email in its hero card, and
  Portfolio and You can show a wallet address. Use the public demo wallet
  (`9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ`), which holds USDC, SOL and staked SKR.
- Check before the first take: the demo wallet holds at least 2 USDC and 0.01 SOL, it still has
  SKR staked, and You shows Pro (the pass runs to 20 October; otherwise redeem a promo code).
  Since 1.3.27 Pro belongs to the Google account: signing in moves the phone's pass or promo days
  to the demo account, and signing out removes Pro from the phone, so do not sign out between
  takes.
- Watch one stock at least a day ahead, so a real digest notification exists and Today has a
  Watched row. Pick a watched stock with a report inside the next two weeks if possible.
- Film on or after **28 September**, when round 3 opens. Since round 2 closed on 28 September,
  *Last round* shows round 2 (AAL) rather than JEF. Keep the same spoken line and take the JEF
  shot from JEFx's own stock page (shot 5b), which carries its research either way.
- Release build only. A debug build signs and never submits, and its key does not verify as
  www.plainticker.com.
- Notifications from other apps off, Do Not Disturb on except for PlainTicker, a clean status bar.

## Shot list

| # | Time | What must be on screen |
|---|---|---|
| 1 | 0:00–0:10 | Lock screen; the PlainTicker "Daily digest" notification; tap into the app |
| 2 | 0:10–0:27 | Today: the status line, Watched with at least one row, Reports next week (or this week) |
| 3 | 0:27–0:56 | Stocks → AAPLx: the classification, the token price beside *Last US price*, *Live from the mint* with its slot and age, then scroll *Backing and controls* (Minted on chain, the proof of reserves xStocks reports, Permanent delegate Yes) |
| 4 | 0:56–1:32 | Swap 1 USDC into AAPLx: the sheet with the estimated all-in cost, the Review step and **Continue to wallet**, the Seed Vault Wallet connect sheet showing www.plainticker.com, approval, *Swap landed*, the receipt with its signature |
| 5 | 1:32–1:44 | Portfolio: the holding and **Swap to USDC**, run to its result |
| 5b | 1:44–2:16 | Vote tab: the explainer, *Last round* (round 2, AAL), then JEFx's stock page with its research (classification *Unavailable*, sector model pending); back to Vote, the current round, one vote cast, the wallet approval, the landed state |
| 6 | 2:16–2:30 | You: the Pro hero card, Plan, Sign-in methods (Google), then the Wallet group below it, which signs and is not a sign-in. The demo account's email only |
| 7 | 2:30–2:41 | Airplane mode on: Today's offline state, then a stock page reading *Mint not read* |
| 8 | 2:41–2:58 | Airplane mode off; Today refreshes, then the Vote tab's current round. End there, no title card |

## The script

### 1 · 0:00–0:10 · The morning notification

**Screen:** lock screen, the digest notification, a tap into the app.

> PlainTicker, on a Solana Seeker, reads a tokenized stock before you hold it. My day starts with
> its one daily digest.

### 2 · 0:10–0:27 · Today

**Screen:** Today. Hold on the status line, then Watched, then Reports next week.

> Today shows the market in my own time zone: New York is closed and opens on Monday. Then the
> stocks I watch, each token's gap to the last US price of its share, and the companies reporting
> next week.

### 3 · 0:27–0:56 · A stock page, read live from the mint

**Screen:** Stocks, search AAPL, open AAPLx. Pause on *Live from the mint*, then scroll into
*Backing and controls* and stop on the permanent delegate.

> This is Apple's token. The classification comes from Apple's SEC filings, set against its sector
> by a fixed rule. It is not a forecast and not advice.
>
> Live from the mint: this phone just read the Token-2022 mint, at the slot on screen, seconds
> ago. The issuer holds a permanent delegate, so it can move this token without my signature. The
> app says so in plain words.

### 4 · 0:56–1:32 · A swap, and the receipt

**Screen:** Swap on the stock page, 1 USDC, the estimated all-in cost, the Review step and
**Continue to wallet**, the wallet sheet naming www.plainticker.com, approval, *Swap landed*, the receipt.
Hold on the signature for a beat.

> Now a one-dollar swap, routed by Jupiter. Before your wallet opens, this phone checks the
> transaction's amounts and accounts against the request; Jupiter's internal routing and the
> issuer's power to move tokens stay outside those checks. Review shows what the phone checked; I
> continue to the wallet.
>
> Mobile Wallet Adapter hands it to the Seed Vault, and the wallet shows this app verified as
> www.plainticker.com. I approve. Swap landed. The receipt keeps the executed fill plus estimated
> network costs, and the signature.

### 5 · 1:32–1:44 · Swap to USDC

**Screen:** Portfolio, the holding, Swap to USDC, the result.

> Portfolio sends it back with Swap to USDC. Swaps like these landed on mainnet from this app on
> the thirteenth and the twenty-fourth of September.

### 5b · 1:44–2:16 · Vote

**Screen:** Vote tab explainer, *Last round*, JEFx's stock page with its research, back to the
current round, one vote, the wallet approval, the landed state.

> Most tokenized stocks have no analysis yet, and each one costs money to make.
>
> Staked SKR chose Jefferies in round one; its research is published, and its classification waits
> for a sector model for banks. The vote landed on mainnet: its signature starts 3RZh and ends
> 9yVG.
>
> Now round three. My vote is a transaction, weighted by the SKR I have staked, and the app says
> plainly that the largest stake decides.

### 6 · 2:16–2:30 · You, with Pro

**Screen:** You, the Pro hero card, Plan, Sign-in methods, and the Wallet group below it.

> Pro opens every figure on every covered stock: twelve USDC for thirty days, or seven thousand
> five hundred SKR staked. I signed in with Google, and the account is shared with
> plainticker.com.

### 7 · 2:30–2:41 · Airplane mode

**Screen:** airplane mode on, Today's offline state, a stock page reading *Mint not read*.

> In airplane mode the app says what it is showing and from when. A stock page says the mint was
> not read, rather than guess.

### 8 · 2:41–2:58 · Close

**Screen:** airplane mode off, Today refreshing, then the Vote tab's current round. End there.

> PlainTicker. The analysis engine existed before CLOCK IN; this native Android app was built for
> it during the hackathon. Round one had one voter, me. Round three is open until 5 October: [N]
> wallets have voted so far.

## Caption text

Upload this as the caption track, split to the cut. It is the spoken lines above, word for word.

```text
PlainTicker, on a Solana Seeker, reads a tokenized stock before you hold it. My day starts with its one daily digest.

Today shows the market in my own time zone: New York is closed and opens on Monday. Then the stocks I watch, each token's gap to the last US price of its share, and the companies reporting next week.

This is Apple's token. The classification comes from Apple's SEC filings, set against its sector by a fixed rule. It is not a forecast and not advice.

Live from the mint: this phone just read the Token-2022 mint, at the slot on screen, seconds ago. The issuer holds a permanent delegate, so it can move this token without my signature. The app says so in plain words.

Now a one-dollar swap, routed by Jupiter. Before your wallet opens, this phone checks the transaction's amounts and accounts against the request; Jupiter's internal routing and the issuer's power to move tokens stay outside those checks. Review shows what the phone checked; I continue to the wallet.

Mobile Wallet Adapter hands it to the Seed Vault, and the wallet shows this app verified as www.plainticker.com. I approve. Swap landed. The receipt keeps the executed fill plus estimated network costs, and the signature.

Portfolio sends it back with Swap to USDC. Swaps like these landed on mainnet from this app on the thirteenth and the twenty-fourth of September.

Most tokenized stocks have no analysis yet, and each one costs money to make.

Staked SKR chose Jefferies in round one; its research is published, and its classification waits for a sector model for banks. The vote landed on mainnet: its signature starts 3RZh and ends 9yVG.

Now round three. My vote is a transaction, weighted by the SKR I have staked, and the app says plainly that the largest stake decides.

Pro opens every figure on every covered stock: twelve USDC for thirty days, or seven thousand five hundred SKR staked. I signed in with Google, and the account is shared with plainticker.com.

In airplane mode the app says what it is showing and from when. A stock page says the mint was not read, rather than guess.

PlainTicker. The analysis engine existed before CLOCK IN; this native Android app was built for it during the hackathon. Round one had one voter, me. Round three is open until 5 October: [N] wallets have voted so far.
```

## Lines that must match the screen on the day

Re-read these against the app before recording, and change the line rather than the facts:

- **Shot 2:** "closed and opens on Monday" is true on a weekend. On a weekday say what the status
  line says ("open, and closes at ten my time", or "opens at half past four my time").
- **Shot 3:** proof of reserves is on screen (100.8% for AAPLx on 25 September) and is not
  spoken, so the line stays true whatever the figure reads.
- **Shot 3, the price:** while the NYSE is shut the stock page labels the reference *Last US
  price* (during the session it reads *NYSE price*): the share's latest US trade from Jupiter,
  which moves before the open and after the close. It is not the NYSE
  close, so no line calls it the close.
- **Shot 4, the Review step:** Review and **Continue to wallet** are in 1.3.27, and the spoken
  line names them. Hold Review long enough to read the network fee and the "Up to" deposit.
- **Shot 5b:** the Jefferies line is true of round 1 whatever *Last round* shows, because it names
  the round. "Waits for a sector model" is true while JEFx's page reads *Unavailable* with the
  sector-model reason; if a classification has appeared by the day, say what the page says.
- **Shot 8, `[N]`:** a placeholder. Fill it from `GET /api/v1/vote/rounds/3/ledger` on the day:
  the number of distinct `voter` wallets in `votes`. Say "one wallet has voted" for 1, and "no
  wallet has voted yet" for 0. Round 3 opens 28 September 00:00 UTC and closes 5 October 00:00
  UTC, so film after it opens. "Round one had one voter, me" is true of round 1 (the round-1
  ledger lists one vote).
- If a digest, a label or a screen changes after 1.3.27 before filming, the spoken line follows the
  build that is filmed.

## If it runs long, cut in this order

1. Shot 6's last clause ("and the account is shared with plainticker.com").
2. Shot 5's second sentence.
3. Shot 1's second sentence.

Never cut: the permanent delegate line, the check before the wallet opens and what stays outside
it, "verified as www.plainticker.com", the signature read-out, "the largest stake decides", or
the close on round three's voters.
