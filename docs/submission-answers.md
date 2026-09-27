# Submission answers for the Align form

Refreshed 2026-09-26 against build 1.3.13 and the live API; claims re-checked 2026-09-27 for
build 1.3.16. It replaces the 18 September draft,
which predated the vote going live, Pro, sign-in, the Amber redesign and the 7,500 SKR threshold.
Every question below is verbatim from `docs/hackathon-rules-2026-09-10.md`, section 1, row
"Submission form fields (Align)". The long-text fields are textareas with `maxLength` 5,000. Each
answer is fenced and ready to paste. The count above it is the number of characters between the
fences.

Two rules this text keeps, because the platform checks them:

- **Nothing is addressed to whoever reads it.** The Align coach flags text written to the reviewer
  as a red flag. The answers describe the product and stop.
- **No secrets, no private data.** The only wallet named is the public demo wallet. The founder's
  own wallet, email and key paths appear nowhere.

Submissions close 2026-10-08 23:59 US Pacific (2026-10-09 06:59 UTC). A draft can be edited, a
final submission cannot, so the agreement is completed on 7 October and the confirmation saved.

## The one number set

Every answer, the README, the deck, the video script and the store listing use these figures. If
one changes, it changes everywhere.

| Figure | Value | Source (26 Sep 2026) |
|---|---|---|
| Companies analysed | 57 (the 56-row ranked list plus JEF) | `GET /api/v1/summary`, `GET /api/v1/JEF` |
| xStocks with a Solana mint | 1,124 | live xStocks catalog, commit `22222ea` |
| Ballot | 898 US-listed xStocks without an analysis (server universe: 916 US tickers) | commit `22222ea`; `GET /api/v1/vote/universe` |
| Commits | 327 since 10 September | `git rev-list --count HEAD` |
| Pull requests | 51 opened, 50 merged | `gh pr list --state all` |
| Unit tests | about 1,600 (1,587 passing at 1.3.13) | commit `1af00bc` |
| Mainnet transactions from the app | 5: three swaps (13 and 24 Sep, one of them Swap to USDC), one vote, one pass | README, "Proof on mainnet" |
| Round 1 | JEF, 878.98 SKR from one voter; analysed 26 Sep | `GET /api/v1/vote/next-up` (`previous`) |
| Pro | 12 USDC for 30 days, or 7,500 SKR staked | web billing constant `STAKE_ENTITLEMENT_THRESHOLD_SKR` |
| SKR stakers | 46,436; median stake about 6,719 SKR; 36.6% stake more than 10,000 | measured from the SKR staking program, September 2026 |

## Short fields

| Field | What goes in it | State |
|---|---|---|
| PROJECT TITLE | `PlainTicker Mobile` | Final. |
| HAS THE TEAM AND/OR THE PROJECT RECEIVED ANY PRIOR FUNDING FROM A VENTURE CAPITAL FIRM OR ANGEL INVESTOR? | **No** | Solo founder. No equity round, SAFE, SAFT, convertible or token sale. The founder confirms it before the agreement. |
| HAS YOUR PROJECT BEEN BUILT IN THE LAST 3 MONTHS? | **Yes** | First commit `2a92e98` on 10 September 2026; 327 commits by 26 September. The analysis engine is older, which the porting answer states. |
| DECK URL | `DECK_URL` | Source: `docs/deck.md`. The founder exports it and fills in the link. |
| DEMO VIDEO URL | `VIDEO_URL` | Script: `docs/video-script-2026-09-27.md`. Upload the caption track with the video: the platform reads the transcript, not the picture. |
| REPOSITORY URL | `https://github.com/louraider/plainticker-mobile` | Read access granted through the Align GitHub connection before submitting, or made public after `docs/public-flip-checklist.md`. History stays unsquashed. |
| ANDROID APK URL | `https://github.com/louraider/plainticker-mobile/releases/latest/download/plainticker.apk` | The signed release APK of the latest release, signed with the key `assetlinks.json` names. The link is stable: every release attaches its APK as `plainticker.apk` and is marked Latest. |

## HAVE YOU WON A PREVIOUS HACKATHON WITH THIS PROJECT? IF YES WHICH ONE, IF NO PUT N/A.

**139 characters.**

```text
N/A. This project has not been entered in a previous hackathon and has not won one. Neither has PlainTicker, the analysis engine behind it.
```

## IF PORTING AN EXISTING APPLICATION OVER TO MOBILE, WHAT MAJOR FEATURES OR NEW SIGNIFICANT MOBILE DEVELOPMENT HAVE YOU DONE?

**3,587 characters.** This is the field that applies, and the one the entry turns on.

```text
PlainTicker's analysis engine existed on the web before this hackathon. The Android app did not. Every line of it was written from 10 September 2026: 327 commits and 51 pull requests in the linked repository.

What was reused. www.plainticker.com classifies US companies from their SEC EDGAR filings against their sector by a fixed rule: quality, valuation, momentum and the F-Score. That engine, its database and its cron are the backend. No screen and no line of web UI was ported. The web product has no chain read, no swap and no notification.

What is new, all of it mobile:

1. A native Kotlin and Jetpack Compose app for the Seeker with five tabs: Today, Stocks, Vote, Portfolio and You. Dark and light themes, about 1,600 unit tests, version 1.3.16.

2. Mobile Wallet Adapter 2.2 and the Seed Vault for every signature: swaps, votes and the Pro pass. Every way the wallet round trip can end is handled as its own state. The release certificate is published in plainticker.com's assetlinks.json, so the Seed Vault Wallet names the app www.plainticker.com before anything is approved.

3. Every transaction is decoded on the phone before the wallet opens. TransactionGuard checks programs, amounts, destinations and token authorities against what the screen shows and against addresses pinned in the app, and refuses anything else. For a swap it reads the Jupiter instruction's own bytes and requires that the tokens leave from and land in the wallet's own accounts. What it cannot see, Jupiter's internal routing, is stated in the README.

4. Token-2022 read on the device. Each time a stock page opens, the app reads proof of reserves, the permanent delegate, pausable transfers, the split multiplier and the transfer hook from the mint, and shows the slot and its age. Balances are read with the Token-2022 program id and scaled by the Scaled UI Amount multiplier, without which a holder sees the wrong quantity.

5. Jupiter swaps in both directions. The receipt reports the cost actually paid, not the quote. Swap to USDC in Portfolio is cross-checked against a second public node before it is offered.

6. A daily habit. Today opens on the NYSE state in the reader's own time, the watched stocks, and the reports due this week or next. One WorkManager digest a day covers the watched stocks, and no other notification is sent.

7. The SKR vote (see the SKR answer) and Pro: a 12 USDC 30-day pass paid from the wallet, or 7,500 SKR staked, or a promo code. Sign in with Google; the wallet connects through Mobile Wallet Adapter to sign, and wallets are linked to the account on plainticker.com. One account and one Pro plan are shared with it.

8. Server endpoints built for the phone, not ported: /summary, /rpc (a read-only JSON-RPC forwarder with a five-method allowlist, so no key ships in the APK), vote build and tally, pass build and confirm, promo redemption, and Google sign-in with a server-issued nonce.

Proof on mainnet, all made by the app on a Seeker from the public demo wallet 9g3mxMEfDhkX1VuNUgmuZFRj4RDiRt6CTvGUPPumUFoQ:
- 13 Sep, 5 USDC into TSLAx: 5pyP9e1w...awAXHK3F
- 19 Sep, the vote for JEF that won round 1: 3RZhaQtAy...PmV19yVG
- 20 Sep, a 12 USDC Pro pass: 5Do5uru...x4v5mWN
- 24 Sep, 1 USDC into TSLAx: 4TmGDAjA...ujzjwksZo
- 24 Sep, Swap to USDC of the whole TSLAx position: 3pwPVFXG...BuT6xkm
The full signatures are in the README.

What is not finished. Every transaction so far is the founder's own testing. Coverage is 57 of 1,124 xStocks. The app is not yet listed on the dApp Store; the publisher account, KYC and the signing chain are done.
```

## IF NO, WHAT MAJOR FEATURES OR NEW SIGNIFICANT MOBILE DEVELOPMENT HAVE YOU DONE?

**2,421 characters.** The porting field above is the one that applies. This field carries
the product side, which has no field of its own: access, the business model and go-to-market.
Counted with the placeholder `PROMO_CODES` in place; replace it with the codes, comma-separated
(each `PT-XXXX-XXXX-XXXX` is 17 characters), which leaves the answer far inside the cap.

```text
This entry is the porting case, answered in the previous field. This field adds the product side: access, the business model and go-to-market.

Access. The signed APK is at the APK link. Each of these promo codes adds 30 days of Pro, entered under You, then Plan, then "Have a code?": PROMO_CODES. AAPL is fully open without a code.

Who it is for. Seeker owners who hold, or are about to hold, xStocks. Their wallet shows a ticker and a price. PlainTicker adds what the mint says about the token, where the company stands against its sector in its SEC filings, and whether the price has enough depth behind it to follow the share.

Business model. Free: every stock page, the chain facts, Today, the digest and the vote, and AAPL in full. Pro opens every figure on every covered stock, for 12 USDC per 30 days paid from the wallet, or for as long as 7,500 SKR stays staked. The pass has already been paid once on mainnet (20 September). Accounts are shared with plainticker.com: the app signs in with Google, the web also with a wallet, and Pro bought on either counts on both for the same account.

Go-to-market. The wedge is Seeker owners holding xStocks: people who already have the phone, the wallet and USDC, and no analysis for the tokenized stocks they can already swap. Three channels:
1. The Solana dApp Store, where tokenized-stock research is a new category. The publisher account and KYC are cleared and the listing copy is written.
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

**3,471 characters.** The SKR bonus prize is judged from this field and the video.

```text
Yes. Staked SKR decides which tokenized stock PlainTicker analyses next, and staked SKR can unlock Pro. The first vote round closed its loop on mainnet.

Why coverage is worth deciding. 1,124 xStocks have a Solana mint and 57 have an analysis. Each analysis costs model spend and a place in a cron cycle, so coverage is a budget. Staked SKR allocates it. Every week a Seeker owner can vote for one of the 898 US-listed xStocks without an analysis, weighted by what their wallet has staked, and the winner is analysed. The vote gates nothing that already exists. It decides what gets made next.

The loop, closed once. Round 1 ran 14 to 21 September. A vote for JEF was built, signed in the Seed Vault and landed on mainnet (signature 3RZhaQtAy...PmV19yVG, slot 448,374,697, memo PT-VOTE:JEF). The tally recorded it, JEF won with 878.98 SKR from one voter, and Jefferies Financial Group was analysed on 26 September. The Vote tab shows the result under Last round, and JEFx's page carries the analysis. That one voter was the founder's demo wallet: the mechanism is proven, participation is not yet.

The vote is a transaction, not a login. POST /api/v1/vote/build returns an unsigned v0 transaction: a 0-lamport transfer to the collector 2KyWZetthwBAXji88M2Fz5ob4RPbYxCCBBKXueSoS7tJ, so every vote can be found on chain, and a memo PT-VOTE:<TICKER>. The wallet signs and sends it through Mobile Wallet Adapter. The signer is the voter by construction, so there is no session and no token, and a vote costs one signature fee. Before the wallet opens, the app decodes the transaction and refuses it unless it carries exactly that transfer to the collector pinned in the app and exactly that memo for the ticker the person chose.

The weight is read on chain, never sent by the app. The server runs one getProgramAccounts on the SKR staking program (SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ) with a memcmp at byte 41, because the struct is packed, and reads the principal as a u64 at byte 105. The value is bounded at both ends, because one account in that program decodes to more than the whole staked supply. A wallet counts once per ticker in a round, and a second vote is refused before a transaction is built, so it costs no fee. A cron reads the memos every ten minutes and GET /api/v1/vote/next-up sums them. Every vote and its memo can be found on chain; the weight is the stake the server read when it counted the vote.

SKR also unlocks Pro. 7,500 SKR staked opens Pro for as long as the stake stays in place, beside a 12 USDC 30-day pass. The threshold was set against measured staker data: of 46,436 SKR stakers the median stake is about 6,719 SKR and 36.6% stake more than 10,000, so the threshold sits just above the median. The app reads the stake through the same bounded read.

The weakness, stated where the vote is cast. A vote weighted by stake is decided by the largest stake, and the Vote tab says so in those words. The weight is stored raw and linear, so a cap, a square root, or one Seeker one voice through the Seeker Genesis Token can be applied in the tally alone and reversed. SGT weighting and a weight snapshot at round open are the next two changes.

No custody anywhere. The app holds no key, the server holds none that signs for a user, and the collector exists to be read, not spent from.

Specification: docs/skr-curation-spec-2026-09-13.md in the app repository. Endpoint contract: server/vote/README.md in the PlainTicker repository.
```
