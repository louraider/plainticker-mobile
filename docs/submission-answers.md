# Submission answers for the Align form

Final state for the submission of 7 October 2026, checked against version 1.3.30 (main `9c92c53`)
and the live API that day. It follows the refreshes of 26 September (1.3.13), 28 September (1.3.21)
and 2 October (1.3.27), and replaces the 18 September draft.
Every question below is verbatim from `docs/hackathon-rules-2026-09-10.md`, section 1, row
"Submission form fields (Align)". The long-text fields are textareas with `maxLength` 5,000. Each
answer is fenced and ready to paste. The count above it is the number of characters between the
fences.

Two rules this text keeps, because the platform checks them:

- **Nothing is addressed to whoever reads it.** The Align coach flags text written to the reviewer
  as a red flag. The answers describe the product and stop.
- **No secrets, no private data.** The only wallet named is the public demo wallet. The founder's
  own wallet, email and key paths appear nowhere, and neither do the promo codes.

Submissions close 2026-10-08 23:59 US Pacific (2026-10-09 06:59 UTC). A draft can be edited, a
final submission cannot, so the agreement is completed on 7 October and the confirmation saved.

Three placeholders stay in this file on purpose and are filled only in the founder's local copy:

- `PROMO_CODES`: the judge codes, comma separated. They never enter this repository.
- `DECK_URL` and `VIDEO_URL`: the links, once uploaded.
- `[STORE_STATUS]`: one sentence on the dApp Store review the founder confirms on the day, for
  example "1.3.30 was resubmitted for review on 7 October." Counts below include the placeholder
  as written, so a sentence of that length adds 33 characters.

## The one number set

Every answer, the README, the deck, the video script and the store listing use these figures. If
one changes, it changes everywhere.

| Figure | Value | Source (7 Oct 2026 unless marked) |
|---|---|---|
| Version | 1.3.30, tag `v1.3.30-amber` on main `9c92c53` | the release workflow, built and signed from the tag |
| Companies covered | 57 companies analysed on plainticker.com, 56 of them with an xStock. Each has its own full page on the web. The app lists the 56: xStocks issues no BABA token, so BABA is covered on the web only. 51 of the 56 carry a class. ABBV, CMCSA and NKE read Not classified (too few companies in their sector to compare fairly). GS and JEF are read by the descriptive model for investment banks, which gives no class | the server's `covered` list (57 tickers); `GET /api/v1/summary` (53 rows: 51 `classified`, GS and JEF `descriptive-only`); `GET /api/v1/{ticker}` (`thin-cohort` for ABBV, CMCSA, NKE) |
| Sector models | JPM and BAC (deposit banks) and PGR (a property and casualty insurer) are classified by their own sector models; GS and JEF are descriptive | methodology v0b.7 to v0b.11, 4 to 6 October; app 1.3.29 and 1.3.30 |
| xStocks with a Solana mint | 1,124 | live xStocks catalog, commit `22222ea` |
| Ballot | 898 US-listed xStocks without an analysis (server universe: 916 US tickers) | commit `22222ea`; `GET /api/v1/vote/universe` |
| Commits | 519 on main since 10 September | `git rev-list --count origin/main` at `9c92c53` |
| Pull requests | 86 opened, 85 merged, 1 closed unmerged | `gh pr list --state all`, by the 1.3.30 release |
| Unit tests | 2,182 at 1.3.30 | `@Test` count on `9c92c53`; CI green on main |
| Mainnet transactions from the app | 5 in the proof list: three swaps (13 and 24 Sep, one of them Swap to USDC), one vote, one pass. The demo wallet has made more since: a METAx swap on 29 Sep and the votes of rounds 2 and 3 | README, "Proof on mainnet"; the round ledgers |
| Round 1 | JEF, 878.98 SKR from one voter; its page served from 26 Sep, read descriptively | `GET /api/v1/vote/rounds/1/ledger` |
| Round 2 | AAL, 878.98 SKR from one voter, the demo wallet; closed 28 Sep 00:00 UTC; not analysed yet | `GET /api/v1/vote/rounds/2/ledger`; `GET /api/v1/AAL` answers 404 |
| Round 3 | ABNB won; 2 votes from 1 voter, the demo wallet (ABNB and AFRM); closed 5 Oct; not analysed yet | `GET /api/v1/vote/rounds/3/ledger`; `GET /api/v1/ABNB` answers 404 |
| Round 4 | open, 5 to 12 October | `GET /api/v1/vote/next-up` |
| Pro | 12 USDC for 30 days, or 7,500 SKR staked | web billing constant `STAKE_ENTITLEMENT_THRESHOLD_SKR` |
| SKR stakers | 46,436; median stake about 6,719 SKR; 36.6% stake more than 10,000 | measured from the SKR staking program, September 2026 |
| dApp Store | first submission reviewed and refused: `USE_BIOMETRIC` and `USE_FINGERPRINT` merged in by a library with no declared use (PER-001). Fixed in 1.3.28: a real optional fingerprint app lock, and the release pipeline refuses any permission off its allowlist. Status today: `[STORE_STATUS]` | pull requests #84 and #85; the founder confirms the status |

## Short fields

| Field | What goes in it | State |
|---|---|---|
| PROJECT TITLE | `PlainTicker Mobile` | Final. |
| HAS THE TEAM AND/OR THE PROJECT RECEIVED ANY PRIOR FUNDING FROM A VENTURE CAPITAL FIRM OR ANGEL INVESTOR? | **No** | Solo founder. No equity round, SAFE, SAFT, convertible or token sale. The founder confirms it before the agreement. |
| HAS YOUR PROJECT BEEN BUILT IN THE LAST 3 MONTHS? | **Yes** | First commit `2a92e98` on 10 September 2026; 519 commits on main by 1.3.30. The analysis engine is older, which the porting answer states. |
| DECK URL | `DECK_URL` | Source: `docs/deck.md`. The founder exports it and fills in the link. |
| DEMO VIDEO URL | `VIDEO_URL` | The video is final. Its transcript is `captions.srt`, uploaded with it: the platform reads the transcript, not the picture. |
| REPOSITORY URL | `https://github.com/louraider/plainticker-mobile` | Public. History stays unsquashed. |
| ANDROID APK URL | `https://github.com/louraider/plainticker-mobile/releases/download/v1.3.30-amber/plainticker-1.3.30-amber.apk` | The signed release APK of 1.3.30, signed with the key `assetlinks.json` names. Alternative, stable across releases: `https://github.com/louraider/plainticker-mobile/releases/latest/download/plainticker.apk` (every release attaches its APK as `plainticker.apk` and is marked Latest). Releases are built and signed by GitHub Actions from a `v*` tag whose commit is on `main`, only after the founder approves the run in the protected `release` environment. |

## HAVE YOU WON A PREVIOUS HACKATHON WITH THIS PROJECT? IF YES WHICH ONE, IF NO PUT N/A.

**139 characters.**

```text
N/A. This project has not been entered in a previous hackathon and has not won one. Neither has PlainTicker, the analysis engine behind it.
```

## IF PORTING AN EXISTING APPLICATION OVER TO MOBILE, WHAT MAJOR FEATURES OR NEW SIGNIFICANT MOBILE DEVELOPMENT HAVE YOU DONE?

**4,870 characters**, with `[STORE_STATUS]` as written, so its sentence may run to 144
characters. This is the field that applies, and the one the entry turns on.

```text
PlainTicker's analysis engine existed on the web before this hackathon. The Android app did not. Every line of it was written from 10 September 2026: 519 commits and 86 pull requests in the linked repository by version 1.3.30.

What was reused. www.plainticker.com classifies US companies from their SEC EDGAR filings against their sector by a fixed rule: quality, valuation, momentum and the F-Score. That engine, its database and its cron are the backend. No screen and no line of web UI was ported. The web product has no chain read, no swap and no notification.

What is new, all of it mobile:

1. A native Kotlin and Jetpack Compose app for the Seeker with five tabs: Today, Stocks, Vote, Portfolio and You. Dark and light themes, 2,182 unit tests.

2. Mobile Wallet Adapter 2.2 and the Seed Vault for every signature: swaps, votes and the Pro pass. Every way the wallet round trip can end is its own state. The release certificate is published in plainticker.com's assetlinks.json, so the Seed Vault Wallet names the app www.plainticker.com before anything is approved.

3. Every transaction is decoded on the phone before the wallet opens. TransactionGuard checks programs, amounts, destinations and token authorities against what the screen shows and against addresses pinned in the app, and refuses anything more, or anything it cannot parse. For a swap it reads the Jupiter instruction's own bytes, requires that the tokens leave from and land in the wallet's own accounts, and refuses slippage above 300 bps or a platform fee above 400 bps. The network fee is read from the bytes and capped at 0.01 SOL. When the wallet hands back the signed transaction, the phone decodes it again and runs every rule before it is sent; the wallet may change only the priority fee, within that cap. A pass can never exceed 12 USDC. The guard reads the top level of the message only, and the README says what stands in for the rest.

4. Token-2022 read on the device. Each stock page reads from the mint the supply, the permanent delegate, pausable transfers, the split multiplier and the transfer hook, with the slot and its age, and Backing and controls explains each row in plain words. Proof of reserves is reported by xStocks and shown beside them. Balances are scaled by the Scaled UI Amount multiplier, without which a holder sees the wrong quantity.

5. Jupiter swaps in both directions. The receipt reports the executed fill plus estimated network costs beside the quote. Swap to USDC is cross-checked against a second public node before it is offered.

6. A daily habit. Today opens on the NYSE state in the reader's own time, the watched stocks with each token's gap to the last US price of the share, and the reports due this week or next. One WorkManager digest a day covers the watched stocks. A stock, a vote or a swap can be shared as an image drawn on the phone.

7. The class, read honestly. Methodology v0b.7 to v0b.11 (4 to 6 October) gave banks and insurers their own sector models: JPM, BAC and PGR are classified by them, GS and JEF are read descriptively. Each row in Stocks now shows its class state (1.3.30), and a stock page names the reason a class is absent and draws an empty axis as Not available, never as a zero (1.3.29).

8. The SKR vote (see the SKR answer) and Pro: a 12 USDC 30-day pass paid from the wallet, 7,500 SKR staked, or a promo code. Pro belongs to the Google account, not the phone: signing in moves it there, signing out leaves it on the account. Wallets are linked on plainticker.com, which shares the account and Pro. An optional fingerprint app lock (1.3.28) is off by default. The device code is sealed with an Android Keystore key.

9. Server endpoints built for the phone: /summary, /rpc (a read-only forwarder with a five-method allowlist, so no key ships in the APK), vote build and tally, a public vote ledger, pass build and confirm, promo redemption, and Google sign-in with a server-issued nonce.

Proof on mainnet, made by the app on a Seeker from the public demo wallet 9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ:
13 Sep, 5 USDC into TSLAx: 5pyP9e1w...awAXHK3F
19 Sep, the vote for JEF that won round 1: 3RZhaQtAy...PmV19yVG
20 Sep, a 12 USDC Pro pass: 5Do5uru...x4v5mWN
24 Sep, 1 USDC into TSLAx: 4TmGDAjA...ujzjwksZo
24 Sep, Swap to USDC of the whole TSLAx position: 3pwPVFXG...BuT6xkm
The full signatures are in the README.

What is not finished. Every transaction so far is the founder's own testing. Coverage is 57 companies analysed on plainticker.com, 56 of them with an xStock, against 1,124 xStocks; five of the 56 have no class. The first dApp Store submission was reviewed and refused for two biometric permissions a library had merged in with no declared use. 1.3.28 fixed it: they now serve a real optional app lock, and the release pipeline refuses any permission off its allowlist. [STORE_STATUS]
```

## IF NO, WHAT MAJOR FEATURES OR NEW SIGNIFICANT MOBILE DEVELOPMENT HAVE YOU DONE?

**2,713 characters**, with the placeholders `PROMO_CODES` and `[STORE_STATUS]` as written. The
porting field above is the one that applies. This field carries the product side, which has no
field of its own: access, the business model and go-to-market. Replace `PROMO_CODES` with the
codes, comma separated (each `PT-XXXX-XXXX-XXXX` is 17 characters; ten codes add 177
characters), which leaves the answer far inside the cap.

```text
This entry is the porting case, answered in the previous field. This field adds the product side: access, the business model and go-to-market.

Access. The signed APK is at the APK link. Each of these promo codes adds 30 days of Pro, entered under You, then Plan, then "Have a code?": PROMO_CODES. AAPL is fully open without a code.

Who it is for. Seeker owners who hold, or are about to hold, xStocks. Their wallet shows a ticker and a price. PlainTicker adds what the mint says about the token, where the company stands against its sector in its SEC filings, and whether the price has enough depth behind it to follow the share.

Business model. Free: every stock page, the chain facts, Today, the digest and the vote, and AAPL in full. Pro opens every figure on every covered stock, for 12 USDC per 30 days paid from the wallet, or for as long as 7,500 SKR stays staked. The pass has already been paid once on mainnet (20 September). Accounts are shared with plainticker.com: the app signs in with Google, the web also with a wallet, and Pro paid on either counts on both for the same account. Pro belongs to the account, so it follows the reader to a new phone.

Go-to-market. The wedge is Seeker owners holding xStocks: people who already have the phone, the wallet and USDC, and no analysis for the tokenized stocks they can already swap. Three channels:
1. The Solana dApp Store, where tokenized-stock research is a new category. The publisher account and KYC are cleared and the listing copy is written. The first submission was refused for two biometric permissions a library had merged in; 1.3.28 gave them a real use, an optional app lock, and the release pipeline now refuses any permission off its allowlist. [STORE_STATUS]
2. SKR stakers as the vote community. 46,436 wallets stake SKR. Each weekly round gives them a reason to open the app and ends in a public result: the winning stock is analysed and announced.
3. plainticker.com, which serves the same engine on the web and shares the account and Pro.

The first 30 days after launch.
Week 1: publish on the dApp Store. Every Monday, when a round closes, post the winner and its analysis page.
Week 2: weigh one Seeker as one voice through the Seeker Genesis Token beside stake, and snapshot vote weights when a round opens.
Week 3: Ukrainian in the app (the engine already writes Ukrainian), and more coverage led by the vote.
Week 4: publish the first usage figures: installs, weekly readers, votes per round and Pro passes, counted from the server's request log and from on-chain memos, with no tracking SDK.

Where it stands today: all usage so far is the founder's own testing, and the numbers above are the product's, not its audience's.
```

## DOES YOUR APPLICATION HAVE AN SKR INTEGRATION? IF SO, HOW?

**4,081 characters.** The SKR bonus prize is judged from this field and the video.

```text
Yes. Staked SKR decides which tokenized stock PlainTicker analyses next, and staked SKR can unlock Pro. The first vote round closed its loop on mainnet.

Why coverage is worth deciding. 1,124 xStocks have a Solana mint, and PlainTicker has analysed 57 companies, 56 of them with an xStock. Each analysis costs model spend and a place in a cron cycle, so coverage is a budget. Staked SKR allocates it. Every week a Seeker owner can vote for one of the 898 US-listed xStocks without an analysis, weighted by what their wallet has staked, and the winner is analysed. The vote gates nothing that already exists. It decides what gets made next.

The loop, closed once. Round 1 ran 14 to 21 September. A vote for JEF was built, signed in the Seed Vault and landed on mainnet (signature 3RZhaQtAy...PmV19yVG, slot 448,374,697, memo PT-VOTE:JEF). The tally recorded it, JEF won with 878.98 SKR from one voter, and Jefferies Financial Group's research was published on 26 September. The Vote tab shows the latest result under Last round, and JEFx's page carries the research, read by the descriptive model for investment banks. That one voter was the founder's demo wallet: the mechanism is proven, participation is not yet. Round 2 closed on 28 September with AAL on top, and round 3 on 5 October with ABNB on top, from two votes cast by the same single voter. AAL and ABNB are not analysed yet. Round 4 is open until 12 October.

The vote is a transaction, not a login. POST /api/v1/vote/build returns an unsigned v0 transaction: a 0-lamport transfer to the collector 2KyWZetthwBAXji88M2Fz5ob4RPbYxCCBBKXueSoS7tJ, so every vote can be found on chain, and a memo PT-VOTE:<TICKER>. The wallet signs and sends it through Mobile Wallet Adapter. The signer is the voter by construction, so there is no session and no token, and a vote costs one signature fee. Before the wallet opens, the app decodes the transaction and refuses it unless it carries exactly that transfer to the collector pinned in the app and exactly that memo for the ticker the person chose.

The weight is read on chain, never sent by the app. The server runs one getProgramAccounts on the SKR staking program (SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ) with a memcmp at byte 41, because the struct is packed, and reads the principal as a u64 at byte 105. The value is bounded at both ends, because one account in that program decodes to more than the whole staked supply. A wallet counts once per ticker in a round, and a second vote is refused before a transaction is built, so it costs no fee. A cron reads the memos every ten minutes and GET /api/v1/vote/next-up sums them. Every vote and its memo can be found on chain; the weight is the stake the server read at tally time. GET /api/v1/vote/rounds/{id}/ledger publishes every counted vote of a round with its weight and, for votes counted since the ledger shipped on 27 September, the time and RPC slot of that stake read. Standard RPC cannot re-read a balance at a past slot, so the weight is published, not re-derivable later.

SKR also unlocks Pro. 7,500 SKR staked opens Pro for as long as the stake stays in place, beside a 12 USDC 30-day pass. The threshold was set against measured staker data: of 46,436 SKR stakers the median stake is about 6,719 SKR and 36.6% stake more than 10,000, so the threshold sits just above the median. The app reads the stake through the same bounded read.

The weakness, stated where the vote is cast. A vote weighted by stake is decided by the largest stake, and the Vote tab says so in those words. The weight is stored raw and linear, so a cap, a square root, or one Seeker one voice through the Seeker Genesis Token can be applied in the tally alone and reversed. SGT weighting and a weight snapshot at round open are the next two changes.

No custody anywhere. The app and server never hold a user's wallet signing keys, and the collector exists to be read, not spent from.

Specification: docs/skr-curation-spec-2026-09-13.md in the app repository. Endpoint contract: server/vote/README.md in the PlainTicker repository.
```
