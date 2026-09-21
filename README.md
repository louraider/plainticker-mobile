# PlainTicker Mobile

An Android app for reading a tokenized US stock (an xStock) on Solana before swapping into it: what
the mint itself says about reserves, issuer controls and splits, and what the company's SEC filings
say about it against its sector. It is built for the Solana Seeker, for someone who already holds
USDC in the phone's wallet and wants to know what a token is before it is in theirs.

Package `com.plainticker.mobile`, for the Solana Mobile hackathon CLOCK IN, September to October
2026. Not a price forecast. Not investment advice. xStocks are tokenized tracker instruments issued
by a third party and are not available to US persons; the app asks for that self-certification on
its first screen.

## What it does that a swap screen does not

**It reads the token before it swaps it.** Every stock page opens on the chain, not on a chart.
Proof of reserves, permanent delegate, pausable transfers, the split multiplier and the transfer
hook are read live from the Token-2022 mint on every open, with the slot they came from and its age
beside them. An extension the mint does not carry renders as absent; a mint that could not be read
renders as unknown, never as no risk.

**It withholds the number it cannot stand behind.** The signature element is the tracking gauge, the
token price against the NYSE close, and it is drawn only where there is enough depth behind the
price to carry one. The floor is **$4,000**, held in one pure function
(`data/jupiter/TrackingQuality.kt`) that the list row, the stock page, Portfolio and the daily
digest all read, so they cannot disagree.

That floor is a fact about the market, not about this app. Joined live on 2026-09-12
(`docs/data-map.md`): of the **157 analyzed xStocks** Jupiter priced 55, and out in the tail the
quote stopped describing anything, APPx reading +89.34 percent on $34. The floor was then measured
at four candidates on 2026-09-13: **$4,000 leaves 22 rows carrying a premium**, against 19 at
$10,000, and it buys NFLXx at $9,370, PEPx at $5,351 and ORCLx at $4,444 without widening the
widest premium the tracked set holds. One step lower is where it breaks, Vx printing +6.64 percent
on $2,513. So the app states the figure instead, "$34 behind, too thin", and leaves the
gauge out. That figure is Jupiter's own `liquidity` field, which runs 0.21 to 0.54 of what two
independent aggregators count, so the app reports what Jupiter reports rather than calling it the
pool. Nothing is filtered: disclosure, not curation.

**It adds no verdict of its own.** The analysis is PlainTicker (SEC EDGAR XBRL filings classified
against the sector), the engine behind www.plainticker.com. The app renders quality, valuation,
momentum and the F-Score with its nine signals, the age of the analysis on every row, and no word of
judgement: `CopyLintTest` reads `strings.xml` and every UI source from disk and fails the build if
one of the four trading verdict words reaches a surface.

## A real swap landed on 2026-09-13

Signature
[`5pyP9e1wHCGmatBKM8wtzTW33AjtBR7hMv5Xf4RF2fYhYGyioQov76L29ToZaXtTVF1WbsnqJM2zvRu7awAXHK3F`](https://solscan.io/tx/5pyP9e1wHCGmatBKM8wtzTW33AjtBR7hMv5Xf4RF2fYhYGyioQov76L29ToZaXtTVF1WbsnqJM2zvRu7awAXHK3F),
slot 446,653,478, 2026-09-13 08:08:19 UTC, no error. The demo wallet
`9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ` was debited 5.000000 USDC and credited **0.01362917
TSLAx**; 4.995 of that entered the route and 0.005 went to the fee account as the 10 bps platform
fee on the input mint, both in the transaction's own token balances.

The app's receipt, still on the phone, reads `4.995 USDC`, `to 0.013629 TSLAx`, `all-in cost 0.32%`,
`13 Sep 2026 08:08 UTC`: the amount that reached the route rather than the amount the wallet was
asked for, and the cost **actually paid**, which is the quote's all-in corrected by the fill the
chain returned. Where `/execute` reports no fill, the receipt draws a missing value rather than
redrawing the estimate.

## What staked SKR decides

**Coverage is the scarce thing in this product, and staked SKR is what allocates it.** 832
tokenized stocks have a Solana mint. 160 of them have an analysis, which leaves 672 the list can
only draw as a name and a price, in their own section. One analysis costs about $0.025 of model
spend and a place in a cron cycle, so the order they get produced in is a budget, and until now
nobody allocated it. A Seeker owner votes for the uncovered xStock they want read, weighted by what
they have staked, and the prewarm run covers the winner.

**The vote is a transaction rather than a login.** `POST /api/v1/vote/build` returns an unsigned v0
transaction: a 0-lamport transfer to one collector address, which puts the collector in the account
keys so the votes can be found, and a memo reading `PT-VOTE:<TICKER>`. The wallet signs and sends it
through Mobile Wallet Adapter on the same path as a swap, so the signer is the voter by
construction and there is no nonce, no session and no token anywhere. The weight is the voter's
staked principal, read on-chain by the server and never sent by the client: one `getProgramAccounts`
on the SKR staking program, `memcmp` at byte 41 because the struct is packed, the principal a u64
at byte 105, bounded at both ends because one account in that program decodes to more than
everything staked. One wallet counts once per ticker, and a second vote is refused before a
transaction is built, so it never costs a fee. A cron every ten minutes reads the memos and
`/api/v1/vote/next-up` sums them, and anyone can count the same signatures and get the same figure.
The weakness goes on the sheet where the vote is cast rather than in this file: a stake-weighted
vote is decided by the largest stake, and 4,674 wallets stake more than 31,210 SKR.

**No part of this takes custody.** The app holds no key, the server holds none and never signs, and
the collector address exists to be read rather than spent from. A vote costs the voter one
signature, 5,000 lamports.

**It is not live yet.** Both halves are built and reviewed and both are open pull requests. Four
gates stand between that and a working vote, and the go or no-go on them is the end of Saturday 26
September: migration 0023 applied by hand, `VOTE_COLLECTOR_PUBKEY` set on Vercel for production and
preview, both pull requests merged with the server first, and then a canary vote signed on the
Seeker. That canary has not run. Until it does the route answers 404 and the app says, in those
words, that voting is not open yet. `docs/skr-curation-spec-2026-09-13.md` is the specification.

## Run it

Android Studio with the API 37 SDK (`compileSdk 37`, `minSdk 26`), JDK 17 or 21, and a phone with a
Mobile Wallet Adapter wallet. Developed and walked on a Solana Seeker, Android 16, 1200x2670 at 480
dpi.

```
./gradlew :app:testDebugUnitTest    # 760 unit tests
./gradlew :app:assembleDebug        # debug: signs a swap, never submits it
./gradlew :app:assembleRelease      # release: env-driven signing, docs/release-signing.md
scripts/device-smoke.sh             # 66 assertions against a connected phone
```

`BuildConfig.SUBMIT_SWAPS` is false in debug and true in release, so a debug build stops at the
signature by design and cannot move money. Signed releases come from a `v*` tag through
`.github/workflows/release.yml`, never from a laptop. CI on every push: unit tests, `shellcheck`,
and `scripts/redaction-guard.sh` on the tree and on every commit of every ref.

## What it is made of

```
Seeker (Kotlin 2.4, Jetpack Compose, Mobile Wallet Adapter 2.2, Ktor, WorkManager)   all parsing on device
|
|-- PlainTicker API   https://www.plainticker.com/api/v1
|     /summary        every ranked ticker in one edge-cached call (5 min)
|     /{TICKER}       classification payload v1.1
|     /rpc            bounded JSON-RPC forwarder; the RPC key never leaves the server
|-- xStocks public API   catalog (mints, trading calendar), proof of reserves, split multiplier
|-- Jupiter              Price v3 (the NYSE reference price and the depth it reports), Swap v2 order and execute
`-- Solana mainnet       Token-2022 mint extensions, token accounts and balances, through the forwarder
```

Seven surfaces are built: onboarding, the list, the stock page, the swap sheet, Portfolio, the
watchlist with its daily digest, and the vote sheet. Each data screen went through a cross-model
review before it merged, which is the `fix(T8)` through `fix(T12): review findings` commits and the
table of what each one changed in `docs/data-map.md`. No API key ships in the APK and Jupiter is
used keyless. `server/rpc-proxy/README.md` is the forwarder's contract for anyone
integrating against it or auditing it, with a mirror of its source beside it. `DESIGN.md` is the
design system: near-black canvas, one accent, Outfit for words and JetBrains Mono for every number,
a launcher icon that is two registration corners around an empty centre rather than a letter.

## Measured, and when

Method for each of these is in `docs/data-map.md`.

| What | Number | When |
|---|---|---|
| First row of the list | **2.75 s**, from 11.7 s, by painting a bundled snapshot before the network answers. Spread across runs fell from 1.6 s to 66 ms | Seeker, 2026-09-13, three runs each, `screenrecord` anchored to the system's `Displayed` time |
| xStocks catalog | 4.31 MB of JSON but **0.34 MB on the wire**, gzipped. Cached on disk for 24 h, and a launch inside that window settles at 3.5 s rather than 12.4 s | `dumpsys netstats` either side of a cold run, 2026-09-13 |
| Unit tests | **760**, green | `:app:testDebugUnitTest`, 2026-09-18 |
| Device smoke walk | **66 assertions**, 225 s, exit 0 twice, and proved to fail as well as to pass | `scripts/device-smoke.sh` on the Seeker, 2026-09-13 |
| Release APK | 2.6 MB, against the debug build's 16.7 MB | tag `v0.2.0`, built and signed in CI, 2026-09-13 |
| xStocks the gauge can describe | **22** at the $4,000 floor, against 19 at $10,000 and 29 at $1,000, where JPMx prints +37.98 percent. The market, not this app | four floors measured, 2026-09-13 |

## Security and threat model

- **Money moves in exactly one place.** Jupiter `/execute` sits behind `BuildConfig.SUBMIT_SWAPS`:
  false in debug, true in release. Unit tests run against debug, so nothing in the suite reaches it.
- **The wallet is the system wallet.** Signing goes through Mobile Wallet Adapter to the Seeker's
  Seed Vault wallet. The app never holds a key, never asks for a seed, and decodes the transaction
  bytes before the wallet opens, so an unreadable payload costs no approval.
- **The identity a person approves is provable, and was proved end to end on 2026-09-13.** Four
  links, each of which anyone can check:
  1. The release APK on the phone (v0.2.0, `versionCode` 200) carries one certificate:
     `apksigner verify --print-certs` reports SHA-256
     `66ce92eafa2f819b9a2f6e4eeddc33ac51be46644c295a02750231b1229a49af`.
  2. `https://www.plainticker.com/.well-known/assetlinks.json` returns 200 and names package
     `com.plainticker.mobile` with that same fingerprint, and nothing else.
  3. Google's Digital Asset Links API, the service Android's own verifier uses, returns that one
     statement for the site (`statements:list?source.web.site=https://www.plainticker.com`).
  4. The Seed Vault Wallet's Connect sheet therefore names the app as `www.plainticker.com`, which
     is the line a person reads before approving a swap.

  A debug build is signed with the debug key and will not verify, which is the point of the chain.
  There is one keystore, outside the repository and off the build machine, and it never changes:
  rotating it would mean a new app identity (`docs/release-signing.md`).
- **No secrets in the APK.** RPC traffic goes through `/api/v1/rpc`, which takes one JSON-RPC request
  (never a batch) from a five-method allowlist (`getAccountInfo`, `getMultipleAccounts`,
  `getTokenAccountsByOwner`, `getBalance`, and `getProgramAccounts` pinned to one program with a
  fixed filter shape), caps the body at 16 KiB, limits per IP and per day, caches 60 s, and never
  echoes the upstream URL or key. `sendTransaction` and `simulateTransaction` are not on that list:
  the forwarder cannot be used to move anything.
- **Issuer controls are shown, not buried.** Permanent delegate and pausable transfers are read from
  the mint on every stock page open and are the only two values the design system allows to be drawn
  in Caution. A shallow pool is drawn as a plain fact, because that is what it is.
- **Repository hygiene.** `scripts/redaction-guard.sh` hashes every base58 candidate in the tree and
  in the history against a denylist of identifiers that must never appear, and CI fails on a hit. It
  is green on both. The founder's wallet and stake account are in neither the tree nor the history;
  the demo wallet and the signature above are public by design and are cited as evidence.
  `docs/public-flip-checklist.md` runs before the repository goes public.
- **What this app does not do.** No custody, no price predictions, no leverage, no US persons, no
  tracking SDKs, no analytics beyond the server's request log, and two permissions in the manifest:
  internet, and notifications, asked once at the moment the first ticker is watched.

Threats considered: a mint impersonating a listed xStock (the catalog is the only source of mints,
and prices are requested by mint); a hostile or partial RPC answer (typed parsing, unknown keys
ignored, on-chain rows falling back to "unavailable" rather than to a default); a rate limit losing
every price (one 429 cost the whole list its prices on 2026-09-12, so the client now keeps the
chunks that succeeded, paces itself to 0.5 requests per second, and prices visible rows first);
quote expiry (one automatic requote, and a second expiry returns to the amount with nothing sent); a
wallet without the SOL a token account needs (read from the order's own `rentFeeLamports` and shown
before the wallet opens); a swipe during submission (the sheet refuses its own dismissal while
`/execute` is in flight, so a landed swap's receipt cannot be stranded); and a stale analysis (rows
stay on screen carrying their age, and only broken rows are dropped).

## What is not there

- **No profit and loss in Portfolio**, because the chain carries no cost basis. The footnote says so.
- **The human half of the QA walk.** One real swap has landed, above; the return leg, ten cold
  starts, the no-SOL, no-USDC, airplane-mode and permission-denied paths and the overnight
  notification are walked by a person on 24 September (`docs/qa-checklist.md`).
- **Not on the Solana dApp Store.** Portal account and KYC are cleared and the signing chain is
  done; the app record and its App NFT wait for the device walkthrough
  (`docs/dapp-store-publishing.md`).
- **No fact grid under the analysis tracks**, because the v1.1 payload carries no leaf fundamentals.
  That section is the composite and the three tracks rather than placeholder cells.
- **The open market has never been seen on a device.** Every device pass so far ran against a closed
  NYSE, so the "NYSE price" labels and the open-session banner exist only in unit tests.
- **Ten of the analyzed tickers were added to PlainTicker for this hackathon**, and for young or
  loss-making filers some forward metrics read "not available for this filer" rather than estimated.
  Portrait and dark only; light theme and large screens are deferred (`TODOS.md`).
- **No screenshots in this file.** Four belong under "What it does that a swap screen does not": the
  list with a tracked row and a sub-floor row together, the backing and controls grid with the live
  bar running, the swap sheet at the confirm step, and a receipt. None has been captured from the
  release build, and an invented one is the thing this README argues against.

## Attribution

Analysis by PlainTicker. Token catalog and proof of reserves by xStocks (Backed Finance). Prices and
routing by Jupiter. Filings from SEC EDGAR. Not affiliated with any of them.

`docs/plan-2026-09-10.md` is the plan of record, `docs/release-signing.md` the signing chain,
`docs/qa-checklist.md` the human QA walk, `docs/dapp-store-publishing.md` the publishing route,
`TODOS.md` the deferred work with its triggers.
