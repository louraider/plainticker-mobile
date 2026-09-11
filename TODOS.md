# TODOS

## Backend (investor24-analyst)

### Wallet-bound Pro entitlement

**What:** Map a Solana wallet to a next-auth user (`account.provider = "solana-wallet"`, `providerAccountId = pubkey`) via a server nonce signed with `signMessages`; grant `userSubscription` rows with `paymentProvider` `"skr-stake"` or `"usdc-transfer"`, which requires widening the `user_subscription_provider_check` CHECK constraint (today: `wayforpay`, `manual`, `stripe`).

**Why:** It is the monetization mechanism and the stake-to-unlock SKR feature in one. Without it, web subscribers and mobile users are separate populations and any mobile Pro gate is a client-side soft lock.

**Context:** Schema in `lib/db/schema.ts`: `account` (~lines 277–306, PK on provider + providerAccountId), `userSubscription` (~lines 400–447, `tierCheck` in `free/pro/pro_plus`, `providerCheck`). Stake read is one `getProgramAccounts` on `SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ` with `memcmp` at offset 41 and `dataSlice` offset 105 length 8. Sign-In With Solana is absent on Seed Vault Wallet, so the nonce flow is custom: `POST /api/v1/auth/nonce` → app signs → `POST /api/v1/auth/verify` (ed25519) → short-lived token; nonce store must be replay-safe. Start with the migration.

**Effort:** M
**Priority:** P2
**Depends on:** hackathon submission (2026-10-08); a decision on the N SKR threshold.

### IFRS / 20-F tag mapping for foreign filers

**What:** Extend the EDGAR XBRL extraction to read IFRS tags from 20-F filings so TSM, ASML, ARM and similar return non-null `forward.raw.epsCagr3y`, `forward.raw.yearsSpanned`, `forward.score.realityPct`, `forward.score.secRealizedGrowthPct`.

**Why:** Verified 2026-09-10: `/api/v1/TSM` returns 200 with exactly those four fields null while every Finnhub-derived field populates. The "is the growth real per filings" angle — the most differentiated part of the analysis — is missing for some of the most-traded xStocks (TSMx, ASMLx, ARMx).

**Context:** IFRS and us-gaap tag vocabularies differ materially and multi-year series shapes differ. Regression risk on the 169 domestic filers, so it needs its own canary. Until it lands, the mobile detail screen renders those nulls as "not available for this filer", never as zero. Start in `lib/data/edgar/` with a tag-alias layer keyed by filing form type.

**Effort:** L
**Priority:** P3
**Depends on:** hackathon submission; which foreign tickers matter most (phase-3 reserves list).

## Mobile (plainticker-mobile)

### Solana dApp Store listing

**What:** Publish the release APK to the Solana dApp Store: App NFT and release NFT under the Publisher Portal account (KYC/KYB) set up in Week 1; there is no publisher NFT mint anymore, see docs/dapp-store-publishing.md, listing copy carrying "Not available to US persons" and the not-investment-advice line, screenshots, and a dry run of the `dapp-store` CLI against the tagged `v1.0.0-hackathon` build.

**Why:** The hackathon announcement: "Winners must publish their app on the Solana dApp Store to claim their prize." A win without a listing forfeits the prize.

**Context:** Publisher policy: legal.solanamobile.com/publisher-policy-web. Terms of Use (legal.solanamobile.com/dapp-store-tos) bar "financial activities subject to registration or licensing, including… securities"; Jupiter Mobile and Phantom are listed and trade xStocks, so it is not a hard ban, but Guardian review of a surface that says "swap USDC → TSLAx" is a real uncertainty. Review-survival kit = the wording rules already adopted: no buy/sell verbs, tracker-instrument disclosure, attribution in text not logos, `method.statement_en` disclaimer. A "reasonable timeframe" is granted after results (early November 2026).

**Effort:** M
**Priority:** P2
**Depends on:** hackathon results; Publisher Portal account, KYC/KYB and App NFT from Week 1; publication within 30 calendar days of the winner announcement (Terms 9.3).

### Jupiter Developer Platform key + firewall (contingency)

**What:** If the keyless 0.5 RPS bucket on `api.jup.ag` produces 429s in real use (swap-sheet `/order` at one call per tap; batched `/price/v3` every 30 s), register at developers.jup.ag/portal, take the free key (1 RPS), pass it via `x-api-key`, and lock it with Developer Platform firewall rules (path allowlist `/swap/v2`, `/price/v3`; per-IP rate rule) because it would ship inside a public APK.

**Why:** The review chose fully keyless so nothing secret ships in the APK. This is the documented escape hatch if that assumption fails under load — a plan, not a panic.

**Context:** Docs index: developers.jup.ag/docs/llms.txt (Rate Limits, Firewall, API Keys pages). Trade-off recorded: any key in a public APK is extractable, and a key-level 429 takes every user down at once whereas keyless 429s are per-IP. Trigger = a measured 429 during the Sep 24 device QA, not a hunch.

**Effort:** S
**Priority:** P3
**Depends on:** the keyless decision failing in practice.

### Light theme (behind an accessibility pass)

**What:** Add a light variant of the Instrument tokens (cool off-white canvas, never cream; same single blue accent; same hierarchy) to the Compose theme, honoring the system setting, after a contrast pass on the mobile type scale (13sp labels, 12sp mono meta) and a second full `/qa` run.

**Why:** The hackathon build is dark-first by decision (plan §13 Pass 8): the Seeker is an OLED phone opened from a dark wallet, and one theme is all the schedule can QA. Daylight reading is the most likely first request after launch; shipping light as a token swap without the contrast pass would put unreadable muted text in front of users.

**Context:** `DESIGN.md` section 2 states the constraints for a light variant. Start in `ui/theme/` by adding the light `colorScheme` behind a flag, then a contrast table, then QA.

**Effort:** M
**Priority:** P3
**Depends on:** hackathon submission; first post-hackathon release or a user request.

### Large screens: landscape, tablets, two-pane

**What:** Unlock orientation, add `WindowSizeClass`-driven layouts (two-pane list | detail on expanded width), and make the swap sheet's state survive configuration changes (Activity-scoped ViewModel, quote persisted across recreation).

**Why:** The hackathon build is portrait-locked for the Seeker (plan §13 Pass 6, D18). A Play or dApp Store listing beyond Seeker will flag a portrait-only app on tablets and foldables, and rotation mid-signing is currently avoided by the lock rather than handled.

**Context:** Everything is a single scrolling column, so the phone layout degrades fine in multi-window; the work is the two-pane composition and the rotation-safe swap state machine. Attaches to the dApp Store listing TODO.

**Effort:** M
**Priority:** P3
**Depends on:** a listing beyond the Seeker.

### Profit / loss from the app's own swap receipts

**What:** Compute per-position P&L in Portfolio from receipts the app itself recorded (T10 persists signature, mints, amounts, cost, timestamp locally from day one); positions acquired elsewhere stay without P&L and say so.

**Why:** The chain carries no cost basis, so the hackathon Portfolio shows none (plan §13 Pass 7, D20) rather than an invented number in front of security-researcher judges. The app's own receipts are the one honest source, and P&L on owned positions is a return-visit reason the monetization plan wants.

**Context:** Receipts table lands in T10 (`data/receipts/`). P&L = value − Σ cost for lots bought in-app; sells reduce lots FIFO. Neutral ink, no red/green, per the design rules. Weakness to state in the UI: partial coverage when tokens were bought elsewhere.

**Effort:** S
**Priority:** P3
**Depends on:** T10 receipts persisted; monetization phase.

### Swap sheet power controls

**What:** A slippage ceiling behind a text disclosure and size presets (25 / 50 / 100%) in the swap sheet.

**Why:** The hackathon sheet is one field, Max, and Jupiter's dynamic slippage (plan §13 Pass 7, D21) so it reads as a reading tool's checkout, not a DEX panel. Larger traders will ask for a ceiling; a swap failing on slippage in the wild is the trigger, not a hunch.

**Context:** Jupiter Swap v2 `/order` accepts `slippageBps`; the sheet already shows the all-in cost line. Add the disclosure as a `HairlineField` row beneath the amount, hidden by default; presets as mono text actions. Keep the no-DEX-chrome rule: nothing appears until the disclosure is opened.

**Effort:** S
**Priority:** P3
**Depends on:** a user request or an observed slippage failure.

## SKR track

### SKR-weighted coverage curation (second layer)

**What:** On an unserved xStock's detail page, "Vote to cover next": the app signs a server nonce with `signMessages`; the server verifies ed25519, reads the voter's staked SKR principal through the RPC forwarder, and inserts `coverage_request` with `kind = "skr-vote"` and a new `weight` column; a "Next up" strip shows the top-weighted tickers; the prewarm run resolves the winner's CIK from the SEC ticker file and covers it.

**Why:** Closes a real loop (vote → analysis appears), matches Solana Mobile's own "SKR powers curation" language, and gives users a reason to return. Rated the stronger X-factor story of the two SKR options.

**Context:** Plan section 8 sets the Sep 24 decision rule: stake-to-unlock first, curation second if time remains, skip the track if the core is not done. `recordCoverageRequest` in `lib/coverage/record.ts` writes `kind = "organic"` under a 10-per-10-min per-IP limit; the vote path needs the `weight` column and a signed-identity key instead of IP. Known weakness to state in the README: balance-weighted votes are gameable by a whale. About 4 half-days (2 server, 2 app).

**Effort:** M
**Priority:** P3
**Depends on:** stake-to-unlock shipped; Sep 24 checkpoint green; the RPC forwarder.

## Completed
