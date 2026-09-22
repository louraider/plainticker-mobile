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

### ABNB reads missing_eps: a diluted-EPS tag gap in EDGAR extraction

**What:** Find why `extractAnnualSeries` finds no `us-gaap:EarningsPerShareDiluted` at an endpoint year for ABNB, and widen the tag or unit matching if the fact is present under another tag (for example `EarningsPerShareBasicAndDiluted`) or another unit shape.

**Why:** After PR #109 every phase-3 ticker explains its null CAGR. Eight read `negative_eps_base`, which is the honest answer for a company that lost money. ABNB reads `missing_eps`, which is not a fact about the company but a fact about our extraction: Airbnb has reported diluted EPS for years. One ticker out of ten mis-attributing a data gap to the filer is exactly the kind of small dishonesty the trust-first pitch cannot afford if a judge checks.

**Context:** Reason published by `computeEpsCagr3y` in `lib/methodology/sec-realized-growth.ts`; the series comes from `extractAnnualSeries` in `lib/data/edgar/extract.ts`, which reads only `us-gaap:EarningsPerShareDiluted` and is anchored on the net-income concept, so a year without the pinned fact drops out entirely. Verified live 2026-09-12: ABNB `epsCagr3yReason = "missing_eps"`, `epsTrajectory = null`. MU, the other unexplained null, turned out to have a real CAGR of -0.70 and was never a gap. Related to the IFRS mapping TODO above: both are tag-vocabulary problems in the same extractor.

**Effort:** S
**Priority:** P3
**Depends on:** hackathon submission; nothing renders `forward` in the hackathon build.

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

**The trigger fired early, on 2026-09-12**, during the first device pass: the list showed no prices at all. Measured with the app's own call pattern: about five rapid calls succeed, then every further call returns 429. But the first fix is ours, not Jupiter's. `JupiterPriceApi.prices` loops chunks of 50 with no pacing and throws on the first failure, discarding chunks that already succeeded, so one 429 costs every price on the screen. Pace the calls, keep partial results, and price the visible rows first; only if that still 429s does the free 1 RPS key (twice the budget, extractable from the APK) become worth its cost.

**Effort:** S
**Priority:** P3
**Depends on:** the keyless decision failing in practice.

### Light theme (behind a device QA pass)

**What:** Instrument is retired (`DESIGN.md`, 2026-09-22); the redesign is Amber, and its light
colour scheme, type and Material wiring already exist as of that date: `AmberLightColors` and
`AmberLightColorScheme` in `ui/theme/`, pinned by `AmberContrastTest`. What is still open is wiring
`AmberTheme` in as the app's active theme (behind the system setting or a flag; `MainActivity`
still calls `PlainTickerTheme`), restyling every screen's components to read `AmberColors` /
`AmberType` instead of Instrument's tokens, and a device QA pass on the mobile type scale in both
themes.

**Why:** The hackathon build is dark-first by decision (plan §13 Pass 8): the Seeker is an OLED
phone opened from a dark wallet, and the restyle calendar (`docs/design-research-2026-09-21.md`
section 7) does not land a light-theme device pass by the 29 Sept freeze either. Daylight reading
is the most likely first request after launch; shipping light without the device pass would put
unreadable text in front of users the same way it would have under Instrument.

**Context:** `DESIGN.md` section 2 states Amber's light values and their measured contrast ratios.
Start in `ui/theme/` from what already exists (`AmberLightColorScheme`), restyle one screen's
components onto it, then QA.

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
**Depends on:** a founder trigger for the web repository (its 2026-05-18 instruction is still live), migration 0023 applied to production by the operator (one Neon, so a migration reaches prod directly), and a collector address that is not the founder's own wallet.

**Superseded 2026-09-13.** This is no longer the second layer and no longer waits on stake-to-unlock, which the founder rejected as too weak on its own. Curation is the SKR integration. The identity proof changed too: a memo transaction rather than a signed nonce, because the production web app contains no signature verification anywhere and making the signer the voter by construction removes the nonce store, the token lifetime and the replay window. Full specification in the mobile repository, `docs/skr-curation-spec-2026-09-13.md`.

## Completed

### The app icon, attempt three

**The founder saw the new mark on the phone and said it is not much better. That is the verdict.** It ships as it is only until someone tries again, and what follows is why the first two attempts failed, so the third does not repeat them.

Both were argued for from first principles and both are defensible on paper. The letter was the product's initial; the gauge is the product's own instrument; each survives 48dp; each has a working monochrome layer. Neither looks good. A mark that wins every argument and still looks weak has lost the only argument that counts.

What the two failures share is more useful than either alone.

- **Both were built from the design system's smallest parts**, a traced glyph and a hairline with two ticks. Those parts exist to stay quiet inside a screen someone reads for a minute, which is the opposite of the job a tile has in a launcher grid. An icon competes on silhouette and mass at a glance, and three thin rectangles are a diagram rather than a form. Diagrams explain. Icons are recognised.
- **The tile is near-black in a drawer of saturated colour.** The last review noticed that its edge is invisible and the mark floats on the wallpaper, and accepted it. Next to Phantom and Jupiter it reads as a hole. Inverting it, an accent field carrying a canvas-coloured mark, has never been tried and costs one render to judge.
- **The constraint set was inherited from the interface without anyone asking whether it belongs here.** Radius zero, one accent, no gradient, no mass. Those rules earn their place on a reading screen. An icon is seen for half a second beside thirty others and may deserve rules of its own, written down deliberately rather than assumed.

For the third attempt: start from mass and silhouette rather than from a concept, render candidates into a screenshot of the real drawer beside the real neighbours rather than onto a neutral sheet, and judge them there before anyone explains what they mean. A mark that needs its explanation does not work. `design/brand/render_icons.py` and the frozen candidates under `design/brand/candidates/` make another round cheap, and `BrandAssetsTest` plus `Mark.mirrors_itself` move with whatever wins.

**What shipped 2026-09-13** on `design/app-icon`. The mark is the tracking gauge, not a letter: an Ink scale with the reference graduation hanging under its centre and the token tick standing over it in Accent, drawn at a weight that survives 48dp (no shape thinner than 4dp there, against the 1.3dp tick that made the old mark look weak). Three candidates were drawn as real drawables and rendered at 48dp, 72dp and 108dp under a circle and a squircle mask on both grounds: `design/brand/icon-candidates.png`, from `design/brand/render_icons.py`. Geometry lives in `design/brand/marks.py`, `BrandAssetsTest` pins it and the 4dp floor, DESIGN.md section 9 is rewritten.

Review changed the construction, not the direction. The gauge was first drawn with the track, the reference tick and the token tick all centred on y 54, which is a cross: in colour the Accent tick separated and it read as the gauge, but the monochrome layer and the 24dp notification silhouette have no colour to separate with and both read as a plus sign. The two layers the constraints call non-negotiable were the two that failed, and they had only been judged in the render script. The ticks now point opposite ways, `Mark.mirrors_itself` and `no layer of the mark is a plus sign once the color is gone` refuse any symmetric construction, and the rejected drawing is frozen at `design/brand/candidates/crossed_*.xml` and kept as a row of the comparison sheet.


**What shipped 2026-09-15** on `design/icon-two-corners`, after two more rejections and a selection gallery the founder drew on themselves. The mark is **Two corners**, cell 4a of that gallery: two registration corners with an empty centre between them, transcribed rather than argued for. The part this round decided is the one three bullets above got right and nobody acted on — the tile is near-black in a drawer of saturated colour and its edge is invisible. That is now a number rather than an observation. The same four rectangles were built on four grounds and composited into the real drawer: Canvas 1.04 to 1, an Accent field 7.23, a cool off-white 16.79, an Ink tile with the mark in Canvas 15.48, against Photos at 18.06. The Ink tile ships, because it reads within a point and a third of the off-white and is made of two tokens section 2 already has. The 33-unit safe circle is retired in favour of the superellipse actually measured off the phone, since the chosen corners sit at 39.60 and clear the real mask by 0.81 of a unit. `design/brand/two-corners/gallery.html` has all four with the numbers under them.
