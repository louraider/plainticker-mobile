# PlainTicker Mobile

A native Android app for the Solana Seeker that reads a tokenized US stock (an xStock) before you
hold it: what the Token-2022 mint says about reserves and issuer controls, read live on the phone,
and how the company's SEC filings place it against its sector. Swaps go through Jupiter and are
signed in the Seed Vault through Mobile Wallet Adapter. Staked SKR decides which stock gets
analysed next.

Kotlin and Jetpack Compose, package `com.plainticker.mobile`, built from 10 September 2026 for
CLOCK IN, the Solana Mobile hackathon. Version 1.3.13 on `main`.

The analysis is a classification by a fixed rule against the sector. It is not a price forecast
and not investment advice. xStocks are tokenized tracker instruments issued by a third party and
are not available to US persons; the app asks for that self-certification on its first screen.

## Try it in two minutes

1. **Install the signed APK:** `https://github.com/louraider/plainticker-mobile/releases/download/v1.3.15-amber/plainticker-1.3.15-amber.apk`. It is signed with the one release key that
   `https://www.plainticker.com/.well-known/assetlinks.json` names, so the Seed Vault Wallet shows
   the app as `www.plainticker.com`. A Seeker is the target; any Android 8+ phone with a Mobile
   Wallet Adapter wallet runs it.
2. **Turn on Pro with a promo code.** Open **You → Plan → Have a code?**, enter the code and tap
   Apply. The codes are listed in the submission form. Each one adds 30 days of Pro, which opens
   every figure on every covered stock. No wallet and no sign-in are needed for this step.
3. **Try three things:**
   - **A stock page's trust card.** Stocks → search `AAPL` → open AAPLx. Under the price, *Live
     from the mint* names the slot it read and how many seconds ago. *Backing and controls* shows
     proof of reserves, the permanent delegate, pausable transfers, the split multiplier and the
     transfer hook, all read from the Token-2022 mint on this phone each time the page opens.
   - **A swap and its receipt.** On the same page, swap 1 USDC into the token. The wallet sheet
     names `www.plainticker.com`. The app decodes the transaction before the wallet opens. The
     result reads *Swap landed*, and the receipt keeps the amount that reached the route, the
     all-in cost actually paid and the signature. Portfolio then offers **Swap to USDC** for the
     way back. This needs a wallet holding a few USDC and about 0.01 SOL.
   - **The Vote tab.** *Last round* shows round 1's result: JEF had the most stake, and JEF is
     now analysed (open JEFx from Stocks). Cast a vote for any uncovered US stock in the current
     round. A vote is a transaction you sign, weighted by the SKR your wallet has staked. A
     wallet with no stake is told its vote would carry no weight before anything is signed.

| Today | Stocks | A stock page |
|---|---|---|
| ![Today: market status, Watched, Reports next week](docs/img/01-today.png) | ![Stocks: search, Deep pool and sector filters, scores](docs/img/02-stocks.png) | ![AAPLx: classification, price against the NYSE close, live from the mint, backing and controls](docs/img/03-stock-page-aapl.png) |

| The daily digest | Swaps this app made on mainnet |
|---|---|
| ![Daily digest: one short check a day](docs/img/04-daily-digest.png) | ![Recent swaps: three real swaps with their all-in cost](docs/img/05-recent-swaps.png) |

All five screenshots were captured on a Seeker (1200 × 2670) from builds 1.3.8 to 1.3.12. The last
one is cropped from Portfolio and lists the three swaps in the proof table below.

## What it is and who it is for

**The person:** someone with a Seeker and some USDC who has seen tokenized stocks in their wallet
and wants to know what a token is before it is theirs. A swap screen gives them a ticker and a
price. PlainTicker gives them three things that screen does not.

- **What the token is.** The chain facts, read from the mint on the phone. Some of these can matter
  more than any fundamental: an issuer holding a permanent delegate can move the token out of a
  wallet without the owner's signature. The app says so in plain words ("Issuer can move tokens").
  A mint that could not be read is drawn as unknown, never as no risk.
- **What the company is.** PlainTicker's classification of the company's SEC EDGAR filings
  against its sector: quality, valuation and momentum, and the F-Score with its nine signals. The
  age of the analysis is shown on every row. Every number is a position against the sector. None
  of them is a forecast.
- **What the price is worth.** The token price against the NYSE close. It is drawn only where
  Jupiter reports at least $4,000 of depth behind the price. Below that floor the app states the
  depth instead ("$34 behind, too thin") and draws no premium. The rule lives in one function,
  `data/jupiter/TrackingQuality.kt`, which every screen reads.

**The daily habit** is Today and the digest. Today opens on the market's state in your own time
zone ("NYSE closed. Opens Monday at 16:30 your time"), then your watched stocks and how far each
token sits from its share, then *Reports this week* (or *next week* at the weekend), because
prices often move around a company's report. One digest a day arrives as a notification about the
stocks you watch. No other notification is sent.

**Five tabs:** Today, Stocks, Vote, Portfolio, You. Amber visual system, Bricolage Grotesque, dark
and light themes, portrait. `DESIGN.md` is the design system.

## Why it lives on the Seeker

A web page cannot do most of this. The website shares only the analysis engine.

- **Mobile Wallet Adapter 2.2 and the Seed Vault.** Every signature (swap, vote, Pro pass) goes
  through MWA to the Seeker's Seed Vault Wallet. The app never holds a key and never asks for a
  seed phrase.
- **A verified app identity.** The release certificate's SHA-256 is published in
  `https://www.plainticker.com/.well-known/assetlinks.json` and returned by Google's Digital Asset
  Links API. The wallet's connect sheet therefore names the app `www.plainticker.com` before
  anyone approves anything. A debug build uses another key and does not verify, which is the
  point of the chain (`docs/release-signing.md`).
- **Token-2022 read on the device.** xStocks are Token-2022 mints with the Scaled UI Amount
  extension. The app parses the mint extensions in Kotlin, reads token accounts with the
  Token-2022 program id, and multiplies raw balances by the split multiplier. Without that step a
  holder would see the wrong quantity.
- **SKR decides coverage, and the first loop has closed.** Each analysis costs money to make, and
  most xStocks have none. Staked SKR decides which one is made next. Round 1 ran 14 to 21
  September: JEF won with 878.98 SKR from one voter, the vote landed on mainnet, and JEF
  (Jefferies Financial Group) was analysed on 26 September. Rounds are weekly, closing Monday
  00:00 UTC.
- **Phone-native work:** a WorkManager digest with the notification permission asked at the first
  watch, a bundled snapshot painted before the network answers, and an offline state that says
  what it is showing and from when.

## How the vote works

- `POST /api/v1/vote/build` returns an unsigned v0 transaction with two instructions: a
  0-lamport transfer to the collector `2KyWZetthwBAXji88M2Fz5ob4RPbYxCCBBKXueSoS7tJ`, so every vote
  can be found on chain, and a memo `PT-VOTE:<TICKER>`. The wallet signs and sends it. The signer
  is the voter by construction, so no login is involved, and a vote costs one signature fee.
- The weight is the voter's staked SKR principal, read on chain by the server and never sent by
  the app. The server runs one `getProgramAccounts` on the SKR staking program with a `memcmp` at
  byte 41 (the struct is packed), reads the principal as a u64 at byte 105, and bounds it at both
  ends.
- A cron reads the memos every ten minutes and `GET /api/v1/vote/next-up` sums them. Anyone can
  recount the same signatures and get the same result.
- The ballot holds the 898 US-listed xStocks without an analysis. PlainTicker reads SEC filings, so
  the server accepts only US underlyings (916 in its universe file).
- The sheet states the weakness before the wallet opens: a vote weighted by stake is decided by
  the largest stake. The stored weight is raw and linear, so a cap or a one-Seeker-one-voice rule
  can be applied in the tally alone (see the roadmap).

## Pro

Pro opens every figure on every covered stock. AAPL stays fully open to everyone as the worked
example, and every stock page stays readable on the free plan, with the figures that feed the
classification locked. Pro comes from any one of these:

- a 30-day pass paid from the wallet, **12 USDC**, as a transfer the app decodes before signing;
- **7,500 SKR staked**, for as long as the stake stays in place;
- a subscription on plainticker.com, since the account is shared;
- a promo code (You → Plan → Have a code?).

Sign-in is **Google** (Credential Manager, with a nonce the app checks) or a **Solana wallet**
through MWA. One account and one Pro plan are shared with www.plainticker.com.

## Architecture

```
Seeker: Kotlin 2.4, Jetpack Compose, Mobile Wallet Adapter 2.2, Ktor, WorkManager, Credential Manager
|   every chain read parsed on the device; every transaction decoded before the wallet opens
|
|-- PlainTicker API  https://www.plainticker.com/api/v1
|     /summary            the ranked list in one edge-cached call
|     /{TICKER}           the classification payload
|     /rpc                bounded JSON-RPC forwarder; the RPC key stays on the server
|     /vote/*             build, next-up, universe
|     /pass/*, /promo/*   Pro: pass build and confirm, promo redeem, entitlement
|     /auth/google        Google sign-in, with /nonce
|-- xStocks public API    catalog (mints, trading calendar), proof of reserves, split multiplier
|-- Jupiter               Price v3 (reference price and depth), Swap order and execute, keyless
`-- Solana mainnet        Token-2022 mints and token accounts through /rpc; a second public node
                          cross-checks decimals, multiplier and balance before Swap to USDC
```

The analysis engine, its database and its cron existed before the hackathon and are the backend.
No web UI was ported. The web product has no wallet, no chain read, no swap and no notification.
Everything in this repository was written after 10 September 2026. The server work built for the
phone (`/summary`, `/rpc`, the vote, the pass, promo codes, Google sign-in) landed as pull
requests in the PlainTicker repository. `server/rpc-proxy/` mirrors the forwarder's source and
contract here for audit.

## Security

- **No keys anywhere.** The app holds none, and the server holds none that can sign for a user.
  The server builds vote and pass transactions it cannot sign. The treasury's and the collector's
  keys are held by the founder, outside every repository and every server.
- **No secrets in the APK.** RPC goes through `/api/v1/rpc`. It accepts one request at a time
  (never a batch) from a five-method read-only allowlist: `getAccountInfo`, `getMultipleAccounts`,
  `getTokenAccountsByOwner`, `getBalance`, and `getProgramAccounts` pinned to one program and one
  filter shape. It caps the body at 16 KiB, rate-limits per IP and per day, and never echoes the
  upstream URL. `sendTransaction` is not on the list. Jupiter is used without a key.
- **Every transaction is decoded on the phone before the wallet opens**
  (`wallet/TransactionGuard.kt`). The screen's figures come from JSON sent beside the bytes, so a
  compromised server or API could show "12 USDC to the treasury" over a transaction that drains
  an account. The guard compares the instructions with the figures shown and with addresses the
  app pins itself (`data/PinnedAddresses.kt`, `data/KnownPrograms.kt`). Changing a pin needs a
  release, not a server setting. A transaction the guard cannot parse is refused, and the refusal
  costs a retry, never money.
- **Money moves only in a release build.** Jupiter `/execute` sits behind `BuildConfig.SUBMIT_SWAPS`:
  false in debug, true in release. The unit tests run against debug.
- **Repository hygiene.** `scripts/redaction-guard.sh` hashes every base58 candidate in the tree and
  in the full history against a denylist of identifiers that must never appear. The denylist
  holds digests only. The demo wallet and the signatures below are public on purpose, as evidence.
- **Two permissions:** internet, and notifications, asked when the first stock is watched. No
  analytics SDK, no advertising, no tracking.

### What `TransactionGuard` checks, and what it cannot see

| Flow | What is checked before the wallet opens |
|---|---|
| Every server-built transaction (vote, pass) | The fee payer is the connected wallet. The wallet is the only signer. No address lookup tables. Every program is on the flow's allowlist. The fee cannot exceed the one displayed. |
| Vote | Only ComputeBudget, System and Memo. Exactly one 0-lamport transfer, to the pinned collector. Exactly one memo, `PT-VOTE:` plus the ticker the person chose, naming no account but the wallet. |
| Pro pass | Exactly one SPL Token transfer of exactly the displayed amount, from the wallet's own USDC or USDT account to the pinned treasury's account. Exactly one `PT-PASS` memo for this device. Token accounts are created only for the wallet or the treasury. Any Approve, SetAuthority or CloseAccount is refused. |
| Swap, both directions | The order's mints, amount and taker match what was asked for, and the wallet is a required signer. Exactly one Jupiter instruction, and only `route_v2` or JupiterZ `fill`, whose layouts are known. The amount is read from the instruction bytes. The account spent from and the account paid into must be the wallet's own for the input and output mints. No top-level transfer or burn. Approve, SetAuthority and CloseAccount may name only the wallet. Token accounts are created only for the wallet. No lamports leave the wallet through System. The priority fee cannot exceed the order's. |

| What it cannot see | Why, and what stands in for it |
|---|---|
| What Jupiter does inside its own program: the cross-program invocations to each venue on the route | The bytes show the top-level instructions only. The defence is that Jupiter's two programs are the only swap programs allowed, that the source and destination accounts are pinned to the wallet's own, and that the chain returns the fill, which the receipt reports as the cost actually paid. |
| Whether the quote is a good price | Not a safety property. The sheet shows the all-in cost before the swap, and the receipt shows what was paid. |
| Accounts a swap loads from an address lookup table | They cannot be resolved offline. The authority and both token accounts must be static keys, and a static mint slot must name the requested mint. |
| What the issuer can do to the token after the swap | That is the mint's permanent delegate and pause authority, which the stock page shows before the swap. |

Known gap, stated: the Google sign-in endpoint does not yet require the nonce, because builds older
than 1.3.8 do not send one. The app has sent it and checked it since 1.3.8, and the server will
require it once those builds are gone.

## Proof on mainnet

Every transaction below was made by this app on a Seeker, from the public demo wallet
`9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ`.

| When (UTC) | What | Signature | Slot |
|---|---|---|---|
| 13 Sep 08:08 | Swap: 5 USDC into 0.01362917 TSLAx. The receipt reads 4.995 USDC to the route, all-in cost 0.32% | [`5pyP9e1w…awAXHK3F`](https://solscan.io/tx/5pyP9e1wHCGmatBKM8wtzTW33AjtBR7hMv5Xf4RF2fYhYGyioQov76L29ToZaXtTVF1WbsnqJM2zvRu7awAXHK3F) | 446,653,478 |
| 19 Sep 10:02 | Vote canary, memo `PT-VOTE:JEF`, which won round 1 | [`3RZhaQtAy…PmV19yVG`](https://solscan.io/tx/3RZhaQtAySu4CNLdPQjfEKm7y7NZqcukEFLAFJzZLUsLuPpsPadbTb6ZFYmSRXTg2SsqhhHuN1EUjsUnPmV19yVG) | 448,374,697 |
| 20 Sep 07:46 | Pro pass, 12 USDC to the treasury, memo `PT-PASS:…`. The app then read Pro until 20 Oct | [`5Do5uru…x4v5mWN`](https://solscan.io/tx/5Do5ururZ2JVt4Gqu8wdaug1Dd3b9xVrqbUgctwehQaKY2PCFKh19pu9zC196LNnoTxNd12unoHYxApu3v4x5mWN) | 448,668,140 |
| 24 Sep 15:24 | Swap: 1 USDC into TSLAx, all-in cost 0.17% | [`4TmGDAjA…ujzjwksZo`](https://solscan.io/tx/4TmGDAjA3V6BF7TdQyUqfkcAg6rfb3s23jhMx1XkqZtVj8i1enfpWZWGoCQShHcawoms7Jja5ZVaRk1ujzjwksZo) | 450,068,679 |
| 24 Sep 19:17 | Swap to USDC: the whole 0.016275 TSLAx position back into 6.16 USDC, all-in cost 0.38% | [`3pwPVFXG…BuT6xkm`](https://solscan.io/tx/3pwPVFXGxoGcQCNbN1oLxSJuqFS4yHFhsW2522eu76gQ8D5fXtwauBFGUsurhYdJDxFx9QvQUruFQTnBBsuT6xkm) | 450,121,017 |

## The numbers

Measured on 26 September 2026 unless the row says otherwise.

| What | Number | Source |
|---|---|---|
| Companies analysed | **57**: the 56 rows of the ranked list plus JEF, the round-1 winner, analysed on 26 September | `GET /api/v1/summary` (56 rows); `GET /api/v1/JEF` (served) |
| xStocks with a Solana mint | 1,124 | The live xStocks catalog (commit `22222ea`) |
| On the ballot | 898 US-listed xStocks without an analysis | The app's US filter over the live catalog (commit `22222ea`); the server's universe file accepts 916 US tickers (`GET /api/v1/vote/universe`) |
| Commits | 327 since 10 September (272 without merges) | `git rev-list --count HEAD` |
| Pull requests | 51 opened, 50 merged | `gh pr list --state all` |
| Unit tests | about 1,600 (1,587 passing at 1.3.13) | commit `1af00bc`; 1,588 `@Test` annotations in the tree |
| Mainnet transactions from the app | 5: three swaps (one of them Swap to USDC), one vote, one pass | The table above |
| Pro | 12 USDC for 30 days, or 7,500 SKR staked | plainticker.com's billing constant `STAKE_ENTITLEMENT_THRESHOLD_SKR` |

## Honest limits

- **All usage so far is the founder's.** Every transaction above came from the demo wallet. Round 1
  had one voter, and round 2 had none when this was written. Nothing here claims users the app
  does not have.
- **Coverage is 57 of 1,124 xStocks.** The vote exists because of that gap, and it closes it one
  company a week.
- **The vote is decided by the largest stake.** The app says so where the vote is cast. A
  one-Seeker-one-voice weight is on the roadmap.
- **Not on the Solana dApp Store yet.** The publisher account and KYC are done, the signing chain
  is live, and the listing copy is written (`docs/dapp-store-publishing.md`). The app record waits
  for the submission build.
- **CI is blocked by a GitHub billing failure** at the time of writing, so recent release APKs
  were built and signed locally with the same release key, and the unit suite runs locally.
- **Portfolio shows no profit and loss**, because the chain carries no cost basis.
- **Some figures read "not available for this filer"** for young or loss-making companies, rather
  than being estimated.
- **Portrait only.**

## Run it

Android Studio with the API 37 SDK (`compileSdk 37`, `minSdk 26`), JDK 17 or 21, and a phone with a
Mobile Wallet Adapter wallet.

```
./gradlew :app:testDebugUnitTest    # about 1,600 unit tests
./gradlew :app:assembleDebug        # debug: signs a swap, never submits it
./gradlew :app:assembleRelease      # release: env-driven signing, docs/release-signing.md
scripts/device-smoke.sh             # assertions against a connected phone
scripts/redaction-guard.sh          # the tree and the full history against the denylist
```

## Where things are

`app/src/main/java/com/plainticker/mobile/`: `ui/` holds the screens (today, stocks, detail, swap,
portfolio, vote, you, pass), `wallet/` the MWA session and `TransactionGuard`, `data/` the API,
Jupiter, xStocks and RPC clients, and `watchlist/` the digest worker.

`DESIGN.md` is the design system, `docs/skr-curation-spec-2026-09-13.md` the vote's
specification, `docs/release-signing.md` the signing chain, `docs/qa-checklist.md` the device walk,
`docs/dapp-store-publishing.md` the publishing route and listing copy, `docs/deck.md` the deck, and
`docs/video-script-2026-09-27.md` the demo script.

## Attribution

Analysis by PlainTicker. Token catalog and proof of reserves by xStocks (Backed Finance). Prices and
routing by Jupiter. Filings from SEC EDGAR. Not affiliated with any of them.
