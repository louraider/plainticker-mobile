# Submission answers for the Align form

Written 2026-09-18. Every question below is verbatim from `docs/hackathon-rules-2026-09-10.md`,
section 1, row "Submission form fields (Align)". The long-text fields are textareas with
`maxLength` 5,000. Each answer is fenced, ready to paste, and the count stated above it is the
text between the fences, characters rather than words.

Submissions close 2026-10-08 23:59 US Pacific, which is 2026-10-09 06:59 UTC. A submission can be
edited while it is a draft and not after the final submission agreement is completed, so the
agreement is completed on 7 October and the confirmation is saved at least 24 hours before the
deadline.

## Short fields

| Field | What goes in it | State |
|---|---|---|
| PROJECT TITLE | `PlainTicker Mobile` | Known. The same name as the repository, the README and the launcher label. |
| HAS THE TEAM AND/OR THE PROJECT RECEIVED ANY PRIOR FUNDING FROM A VENTURE CAPITAL FIRM OR ANGEL INVESTOR? | **No** | Solo founder, and no equity round, SAFE, SAFT, convertible instrument or token sale. Eligibility for a USDC prize rests on this line, so the founder confirms it before the agreement is completed. |
| HAS YOUR PROJECT BEEN BUILT IN THE LAST 3 MONTHS? | **Yes** | First commit `2a92e98` on 10 September 2026, 171 commits by 18 September, against a window that opens about 8 June 2026. The analysis engine behind the app is older, which the porting answer states rather than leaving to be found. |
| DECK URL | blank | Not written. |
| DEMO VIDEO URL | blank | The script of record is `docs/video-script-2026-09-15.md`, and the recording date and the cut order live there. The SKR segment turns on the go or no-go at the end of Saturday 26 September; if that does not clear, the segment is cut entirely rather than filmed reaching the wallet and stopping. |
| REPOSITORY URL | `https://github.com/louraider/plainticker-mobile` | Known, and private today. `docs/public-flip-checklist.md` runs before it goes public, or the judges are added to it. Commit history stays unsquashed. |
| ANDROID APK URL | blank | The submission tag is not cut. The link will be the release asset that CI builds from that `v` tag, signed with the one keystore the assetlinks chain names. |

Nothing else on the form is unanswered. The four blanks above are blank because the artefact does
not exist yet, not because the answer is undecided.

## HAVE YOU WON A PREVIOUS HACKATHON WITH THIS PROJECT? IF YES WHICH ONE, IF NO PUT N/A.

**139 characters**, against the 5,000 the field allows. The form asks for N/A and gets it, with the one sentence that keeps the engine from being read as a prior entry.

```text
N/A. This project has not been entered in a previous hackathon and has not won one. Neither has PlainTicker, the analysis engine behind it.
```

## IF PORTING AN EXISTING APPLICATION OVER TO MOBILE, WHAT MAJOR FEATURES OR NEW SIGNIFICANT MOBILE DEVELOPMENT HAVE YOU DONE?

**4622 characters**, against the 5,000 the field allows. This is the question this entry turns on. The hint on the form reads ANSWER THIS OR THE NEXT QUESTION, WHICHEVER APPLIES, and this is the one that applies.

```text
N/A is not the honest answer here. PlainTicker's analysis engine existed on the web before this hackathon. The Android app did not: every line of it was written after 10 September 2026, and its history is 171 commits in the repository the judges get.

What was reused, precisely. www.plainticker.com classifies US filers from SEC EDGAR XBRL filings against their sector: quality, valuation, momentum, and the F-Score with its nine signals. That engine, its database and its cron are the backend and they predate the hackathon. Nothing else was reused. No screen, no component, no line of web UI was ported. The web product has no wallet, no chain read, no swap and no notification, and never had.

What is new, all of it mobile, all of it in the repository:

1. The app. Kotlin and Compose for the Seeker: onboarding, the list, the stock page, the swap sheet, Portfolio, and a watchlist with a daily digest. 111 source files, 760 unit tests, a 2.6 MB signed release against the debug build's 16.7 MB.

2. Mobile Wallet Adapter 2.2 and the Seed Vault, with every way the round trip can end typed rather than assumed. A $5 swap from the finished app landed on mainnet on 13 September: 5 USDC into 0.01362917 TSLAx, signature 5pyP9e1wHCGmatBKM8wtzTW33AjtBR7hMv5Xf4RF2fYhYGyioQov76L29ToZaXtTVF1WbsnqJM2zvRu7awAXHK3F, slot 446,653,478, confirmed in 0.8 seconds, all-in cost 0.32 percent against a 0.42 percent quote. The receipt reports the cost actually paid rather than the quote it was offered.

3. App identity, proved end to end. The release certificate's SHA-256 is named by https://www.plainticker.com/.well-known/assetlinks.json and returned by Google's Digital Asset Links API, so the Seed Vault Wallet's own sheet names the app www.plainticker.com with a verification mark before anyone approves anything. New signing chain, new well-known file, new release workflow.

4. Every Solana read parsed in Kotlin on the device. Token-2022 mint extensions, permanent delegate, pausable transfers, default account state, transfer hook and the split multiplier, are read on every stock page open and drawn with the slot they came from and its age. xStocks are Token-2022 and carry the Scaled UI Amount extension, so token accounts must be read with the Token-2022 program id and a displayed balance is the raw amount times the multiplier. Getting that wrong shows the wrong quantity to whoever owns it.

5. Jupiter, keyless. Price v3 for the reference price and the pool depth, Swap v2 order, signTransactions, execute, one automatic requote when a quote expires, and the rent a new token account needs read from the order and shown before the wallet opens.

6. The liquidity floor, which is the product's argument rather than a feature. The tracking gauge against the NYSE close is drawn only where more than $4,000 of pool stands behind the price. Below that the app states the pool instead, "Pool holds $34, too thin to track", and draws nothing. Of the analyzed xStocks Jupiter priced 55, and 22 of those cleared $4,000. Nothing is filtered out: disclosure, not curation.

7. Things a web page cannot do. A WorkManager digest on the watchlist with the notification permission asked at the first watch, and a bundled snapshot painted before the network answers, which took the first row from 11.7 s to 2.75 s and the spread across runs from 1.6 s to 66 ms. The 4.31 MB catalog crosses the wire at 0.34 MB and is cached for a day.

8. Two server endpoints built for the phone rather than ported: /api/v1/summary, the whole ranked list in one edge-cached call, and /api/v1/rpc, a bounded JSON-RPC forwarder with a five-method allowlist and a getProgramAccounts pinned to one program and one filter shape, so no key ships in the APK. Its source is mirrored in this repository for audit.

9. The SKR vote, new on both sides, described in the SKR answer.

10. Release engineering. Signed releases only from a v tag in CI, never from a laptop; a device smoke walk of 66 assertions, proved to fail as well as to pass; and a guard that hashes every base58 candidate in the tree and in the whole history against a denylist.

What is not finished, stated rather than implied. The vote endpoint's pull requests are open on both sides and wait on four gates, decided on 26 September: migration 0023 applied by hand, the collector address set on Vercel, both pull requests merged with the server first, and a canary vote signed on the Seeker. That canary has not run, so today the route answers 404 and the app says voting is not open yet. The human half of the QA walk is 24 September. The app has never been seen against an open NYSE.
```

## IF NO, WHAT MAJOR FEATURES OR NEW SIGNIFICANT MOBILE DEVELOPMENT HAVE YOU DONE?

**264 characters**, against the 5,000 the field allows. Left short on purpose: answering it twice reads as padding, and the previous field carries the inventory.

```text
This entry is the porting case, so the previous question is the one that applies and is answered there: PlainTicker's analysis engine existed on the web before the hackathon, the Android app did not, and the answer above separates what was reused from what is new.
```

## DOES YOUR APPLICATION HAVE AN SKR INTEGRATION? IF SO, HOW?

**4264 characters**, against the 5,000 the field allows. The bonus track is judged from this field and the video.

```text
Yes. Staked SKR decides which tokenized stock PlainTicker analyses next.

Why that is the thing worth deciding. 832 tokenized stocks have a Solana mint. 160 of them have an analysis and 672 have none, and the app already draws those 672 in their own section of the list, where a row carries a price and nothing else. One analysis costs about $0.025 of model spend and a place in a cron cycle, so coverage is a finite budget, and until now nobody allocated it. Staked SKR allocates it. A Seeker owner votes for the uncovered xStock they want read, weighted by what they have staked, and the prewarm run covers the winner. Vote, and the analysis appears. It gates nothing that already exists; it decides what gets made next, which is the one genuinely scarce thing this product has.

The vote is a transaction, not a login. POST /api/v1/vote/build returns an unsigned v0 transaction carrying two instructions: a 0-lamport transfer from the voter to one collector address, 2KyWZetthwBAXji88M2Fz5ob4RPbYxCCBBKXueSoS7tJ, which puts the collector in the account keys so every vote can be found from the chain, and a memo reading PT-VOTE:<TICKER>. The wallet signs and sends it through Mobile Wallet Adapter, the same path the swap already uses. The signer is the voter by construction, so there is no nonce, no session, no token, and no authentication surface added to a production web application that verifies no signatures anywhere today. The voter pays one signature, 5,000 lamports. The vote is public, and anyone can recount it.

The weight is read on-chain by the server and never sent by the client. One getProgramAccounts on the SKR staking program SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ, memcmp at byte 41 because the struct is packed rather than aligned, the principal a little-endian u64 at byte 105, six decimals. A client cannot inflate its own weight. The weight is linear in the staked principal and bounded at both ends, 0 to 4,389,047,810,000,000 base units, the staked supply measured on 13 September. Both ends matter: one account in that program decodes to 11,452,317,590,968,446,072 raw, and read into a signed 64-bit integer it arrives as a negative number, so a bound written only as "refuse more than the supply" would let it through untouched. One wallet counts once per ticker, and a second vote is refused with 409 before a transaction is built, so it never costs a fee.

Counting. A cron every ten minutes walks getSignaturesForAddress on the collector, parses the memos, re-reads each voter's stake under the same bound, and upserts on (ticker, voter), keeping a cursor so every run makes progress. GET /api/v1/vote/next-up sums the weight per ticker and drops anything already covered. A vote is only accepted for a US-listed underlying, 720 of the 894 xStocks assets on 18 September, because PlainTicker reads SEC filings; the universe file fails closed, so an unknown ticker is refused rather than recorded.

No custody anywhere. The app holds no key, and the server holds none either: it builds a transaction it cannot sign, and the collector address exists to be read rather than spent from.

The weakness, stated where the vote is cast rather than in a footnote. A stake-weighted vote is decided by the largest stake: 4,674 wallets stake more than 31,210 SKR, and one of them outweighs thousands of smaller stakers. The sheet says exactly that before the wallet opens. The stored weight is kept raw and linear so that a square root or a per-wallet cap can be applied in the next-up query alone, and reversed.

Where it stands on 18 September. Both halves are built and reviewed, and both are open pull requests: the server side with 2345 tests, the app side with 760. Four gates are left, none of them code, and they are decided at the end of Saturday 26 September: migration 0023 applied by hand, VOTE_COLLECTOR_PUBKEY set on Vercel for production and preview, both pull requests merged with the server first, and then a canary vote signed on the Seeker. That canary has not run. Until it does the route answers 404 and the app says, in those words, that voting is not open yet.

The specification is docs/skr-curation-spec-2026-09-13.md in the app repository, and the endpoint contract is server/vote/README.md beside the code.
```
