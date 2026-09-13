# Product Marketing Context

**Document version:** v1
**Last updated:** 2026-09-13

> This is the public half. The fuller version, with pricing, monetization, revenue thinking and named competitors, lives in the private PlainTicker web repository at `.agents/product-marketing.md`. Work from that one where you have it; this file alone is not the whole picture.

Every marketing skill reads this before it writes. Claims here carry their source: the file they came from and, where a number was measured, the date it was measured on. A gap is written as a gap. If what you need is missing, it is either in the private document or in the open questions at the bottom, which means nobody has decided it yet. Do not close a gap by inventing an answer.

## Product Overview

**One-liner:** A reading tool for tokenized US stocks (xStocks) on Solana, built for the Seeker phone. Before you swap, it shows what the token itself says, live from the mint, and then what the company's filings say against its sector. One Swap button, after you have read. (`README.md`)

**In-app one-liner**, the onboarding headline and the shortest true version of the product: "Tokenized stocks, read before you swap." (`app/src/main/res/values/strings.xml`, `onboarding_headline`)

**What it does:** Every xStock page opens with what the token itself says: proof of reserves and the named custodian, the issuer controls (permanent delegate, pausable transfers, transfer hook, new account state) and the split multiplier, read live from the Token-2022 mint on every open, with the slot and the age of the read on screen. Under that it places the company against its sector: quality, valuation and momentum as marker tracks, the Piotroski F-Score with its nine signals, and the sector-relative composite. The swap sits at the bottom of that page, after the reading, as one amount field with the all-in cost and the route shown per quote.

**Product category:** A reading tool for tokenized stocks, not a trading dashboard. The plan states it in those words and adds: "The buy button is a convenience; the analysis and the trust layer are the product." The written pitch line is "the layer under your Stocks tab." (`docs/plan-2026-09-10.md` section 1)

**Product type:** Native Android. Kotlin, Jetpack Compose, Mobile Wallet Adapter 2.2, portrait only, Seeker first, package `com.plainticker.mobile`. It is a client of the PlainTicker JSON API (`/api/v1/summary`, `/api/v1/{TICKER}`, and a bounded `/api/v1/rpc` forwarder), the keyless xStocks and Jupiter public APIs, and Solana mainnet. All Solana parsing happens on the device. No API key ships in the APK. (`README.md`)

**Business model:** The hackathon build is free, by a decision recorded on 2026-09-10: "No criterion rewards a paywall; a wall hurts Completion." Nothing in the app is paid, there are no ads, no referral or per-swap fees, no analytics SDK and no tracking beyond the server's own request log. What happens after the hackathon is designed but not decided, and it sits in the private document, not here. Do not write copy that implies a price, a tier or a trial.

## Target Audience

**Target companies:** Not applicable. This is a consumer app.

**Decision-makers:** Not applicable.

**Who it reaches today:** an owner of a Solana Seeker. The phone ships a wallet whose Stocks tab already lists and sells xStocks, so the reader arrives already holding the thing the app reads. That is the distribution assumption the whole plan rests on, and it is the only audience fact written down anywhere in this repository. A profile of the end user, who they are and what they came for, has never been written. See the open questions.

**Primary use case:** Deciding whether to swap into a given xStock, by reading the token first and the company second, on the phone, in the minute before the swap.

**Jobs to be done:**
- Find out whether this token is actually backed, at what premium to the NYSE close, and what the issuer can do to it while you hold it.
- Find out how the underlying company reads against its own sector, from filings rather than from a price.
- Come back tomorrow without meaning to: a watched ticker, a daily digest notification that deep links to what it named, and a Today stripe that says when it last checked.

**Use cases:**
- A Seeker owner sees TSLAx in the wallet's Stocks tab, opens it here, and reads the mint before deciding.
- A holder checks a position's premium against the NYSE close and finds the pool is too thin for the premium to mean anything, which the app says in a sentence instead of drawing a number.
- A watched company reports in three days and the notification opens that ticker.

## Personas

Business to consumer. The skill asks for personas only where several people are involved in a purchase. Nobody buys this on behalf of anyone else, and there is no purchase, so the table stays empty on purpose rather than filled with invention.

| Persona | Cares about | Challenge | Value we promise |
|---------|-------------|-----------|------------------|
| Not applicable (B2C) | | | |

## Problems & Pain Points

**Core problem:** Every wallet that lists xStocks shows a ticker and a price and nothing else. Nobody shows whether the token is really backed, whether it is trading at the NYSE close, and what the issuer is able to do to it. (`docs/plan-2026-09-10.md` section 1)

**Why alternatives fall short:**
- A price alone cannot tell you that a token is a third party's tracker instrument rather than a share, that the issuer holds a permanent delegate over your balance, or that transfers can be paused.
- A quoted premium off a dead pool is worse than no premium. Measured 2026-09-12: UBERx read +152.13 percent on a pool holding $80, APPx +89.34 percent on $34, CRWDx -42.15 percent on $48. A surface that prints those as tracking figures is describing nothing. (`docs/data-map.md`)
- Fundamentals presented without a sector to compare against do not read. The composite exists because a ratio on its own means little until it is placed against the companies it belongs next to.

**What it costs them:** A swap made on a price alone, into an instrument whose backing, issuer powers and tracking quality were all knowable in the ten seconds before the tap.

**Emotional tension:** Not established from users. Nothing in this repository records how a user felt about any of this, because nobody has been asked. The plan's storyboard names what the design intends the reader to feel at each step ("this knows something", then "it is reading the chain right now", then "informed, not pushed"), which is a design intention and must never be quoted as a customer's words. (`docs/plan-2026-09-10.md` section 13, Pass 3)

## Competitive Landscape

**Direct:** The Seeker's own wallet, whose Stocks tab already lists and sells xStocks. It falls short in one specific way, and only this one is written down: it shows a ticker and a price and nothing else. (`docs/plan-2026-09-10.md` section 1)

**Secondary and indirect:** Not covered in this file. Competitor analysis, including named companies and how each falls short, is in the private document. No comparison of this app against any other xStocks tool exists in this repository, so there is nothing public to cite and nothing to infer. Do not write a comparison from this file.

## Differentiation

**Key differentiators:**
- **The trust layer leads.** The detail screen opens on what the mint says, not on the price: proof of reserves with the named custodian, permanent delegate, pausable transfers, split multiplier, transfer hook, new account state, supply. It is read live through `getAccountInfo` with `jsonParsed` on every open, and a bar showing the slot and the age of the read breathes while it is live. (`DESIGN.md` section 1.1, `docs/data-map.md`)
- **The liquidity floor is disclosure, not curation.** Below $10,000 of pool depth the premium and the tracking gauge are not drawn, and the row states the pool instead: "Pool holds $34, too thin to track". Nothing is filtered out, no section is added, the sort is unchanged. The floor lives in exactly one place in the code, `TrackingQuality` in the data layer, and the figure comes from a measurement rather than a preference. (`docs/data-map.md`, 2026-09-12)
- **It refuses to tell you what to do.** The analysis is a rule-based classification of fundamentals against the sector. The engine's own payload says so about itself (`is_prediction: false`, `kind: "classification"`), and the app renders that statement verbatim as its Method body. There are no BUY, SELL or HOLD words anywhere in this codebase and a lint test keeps it that way; the swap sheet flips direction with the text action "TSLAx to USDC" rather than a sell verb, and "Swap" is the only trading verb on any surface. (`README.md`, `CopyLintTest`)

**How we do it differently:** The token is read before the company, and the company is read against its sector rather than against a target price. Both readings are shown with their age. Where a number would not mean anything, the app says so in a sentence instead of drawing the number.

**Why that is better:** Every fact on the screen is checkable by the reader, from the mint or from a filing, and the app never asks to be believed on anything it has not shown.

**Why customers choose us:** Not established. No user has said why, because no user has been asked. See the open questions.

## Objections

| Objection | Response |
|-----------|----------|
| "It will not tell me whether to buy it." | Correct, and on purpose. It is a rule-based classification of fundamentals against the sector, not a price forecast and not investment advice. The engine's own backtest of its method found no detectable edge after correction, and it is published, so the copy never re-promises one. What the app does instead is show the nine F-Score signals, the three sector-relative tracks and the composite, and let you read them. |
| "Most of these tokens barely trade." | True, and this is the surface that says so. Measured 2026-09-12: of the 157 analyzed xStocks that reach the Analyzed section, Jupiter returns a price for 55, and only 19 of those sit at or above $10,000 of pool depth. Below that floor the app withholds the premium and states the pool instead. Nothing is hidden and nothing is filtered out. |
| "Why trust an analysis whose working I cannot see?" | Do not trust it, check it. Every fundamental traces to an SEC EDGAR filing, the classification rule is published, the token facts are read live from the mint with the slot and the age on screen, and the RPC forwarder's source is mirrored into this repository for auditors. |

**Anti-persona:**
- **US persons.** The app is not available to them. It asks for a one-time self-certification at onboarding: "I am not a US person, and I understand xStocks are tokenized tracker instruments issued by a third party, not shares." Note for copy: the app discloses and asks, it does not geo-block. Never write that it is geo-restricted.
- **Anyone who wants a trading panel.** Slippage controls, size presets and price charts are deliberately absent. The recorded reasons: DEX chrome contradicts the reading tool stance, and a price chart invites the trading read. (`docs/plan-2026-09-10.md` section 13)
- **Non-Seeker devices.** Seeker only by decision, with no wallet picker.

## Switching Dynamics

**Push:** Not established.
**Pull:** Not established.
**Habit:** Not established.
**Anxiety:** Not established.

The four forces are not written down anywhere in either repository, in any language, and no user has been interviewed. Every sentence that could go here would be invention, and this document is read by the skills that write the copy, so an invention here becomes a claim on a page. Leave it empty until there is research behind it.

## Customer Language

**How they describe the problem:** Nothing verbatim exists. There are no interview transcripts, no survey, no support tickets, no reviews and no testimonials in this repository, and the app ships no analytics, so there is no record of behaviour either.

**How they describe us:** Nothing verbatim exists.

Copy skills: do not put quotation marks around a sentence no user said. If a piece needs customer language, that is a reason to go and collect some.

**Words to use:** Sentence case throughout. Buttons are a verb plus an object: "Swap USDC to TSLAx", "Read the list", "View in Portfolio", "Connect wallet", "Watch AAPLx". The onboarding button is "Read the list", never "Get started" and never a bare "Continue". Every state gets one utility sentence and an action, never a bare "Error" or "No items found". Numbers are formatted by one `Fmt` object in en-US: prices to 2dp (4dp under $1), percents signed to 2dp, absolute times in UTC, relative times as "2 s ago", "3 h ago", "2 d old".

**Words to avoid:**
- BUY, SELL, HOLD and AVOID as words on any surface, including the swap sheet. The direction flip is "TSLAx to USDC". "Swap" is the only trading verb.
- seamless, powerful, unlock, empower, journey, insights, supercharge, effortless, all-in-one, welcome to.
- Exclamation marks, emoji, em dashes and en dashes in any visible string, more than one middle dot in a line, uppercase transforms.
- Paraphrases of the honesty sentence. "Not a price forecast and not investment advice." is reused byte for byte, with that casing and that final period. "This is not financial advice", "for informational purposes only" and "does not constitute advice" are banned.
- The visual equivalents, which read as brand rules too: cream or paper backgrounds, Geist or Inter, red and green price blocks, gradients, shadows, glass, purple, spinners, bottom navigation bars.

The first two groups are enforced by `CopyLintTest` over `strings.xml` and the UI sources, so a violation fails the unit test gate before it reaches a device. (`DESIGN.md` sections 7 and 8)

**Glossary:**

| Term | Meaning |
|------|---------|
| xStocks | Tokenized tracker instruments issued by a third party (Backed Finance), not shares. Always say tracker instrument, never share. |
| NAV premium | `(Jupiter usdPrice - stockData.price) / stockData.price`. Labelled "vs NYSE close" when the exchange is shut and "vs NYSE price" during the session. |
| Tracking gauge | The hairline with the NYSE close tick and the token tick, on a 0.5 percent full-width scale. The signature element of the Detail screen, and the brand mark. |
| TrackingQuality | Tracked, Thin or Untracked. The one place the $10,000 liquidity floor lives. |
| Proof of reserves | Shares held against tokens in circulation, with the custodian named (Alpaca on the rows observed). |
| Permanent delegate | A Token-2022 extension: the issuer can move tokens. Shown as a risk line, in Caution. |
| Pausable | A Token-2022 extension: the issuer can pause transfers. Shown as a risk line, in Caution. |
| Split multiplier | Token-2022 `scaledUiAmountConfig`. 1 today, changes on splits, always applied to displayed balances. |
| F-Score | Piotroski. Nine accounting signals, 0 to 9. |
| Composite | Sector-relative percentile, 0 to 100, weighted 30/30/20/20 across valuation, growth, quality and health. |
| Axes | Quality, valuation and momentum. Each has a value, a position against the sector, and a state word. |
| Live bar | The 2dp accent bar that breathes while a chain read is live and goes static on a receipt. |

## Brand Voice

**Tone:** A reading instrument. Not a trading terminal and not a website. Calm and exact, and it does not raise its voice for anything, including its own good news.

**Style:** Direct and declarative. Facts carry the weight, so the number is the argument and no intensifier is added to it. Where something is unknown or unmeasurable, the surface says which, in a sentence. A claim arrives with the thing that supports it: the slot and age beside a chain read, the pool size beside a withheld premium, the analysis age beside every row.

**Personality:** calm, exact, disclosing, unhurried, plain.

**What the voice looks like in the design**, because the visual system is part of the register: cold near-black canvas `#0B0F14`, one blue accent `#5AA9E6` meaning interactive or live, Caution `#D9A441` reserved for explicit issuer-control risk and never used on prices, premiums, scores or list rows. Outfit for words, JetBrains Mono for every number, ticker, hash and timestamp. Radius 0 everywhere. No green or red for price direction anywhere: direction is a signed monospace number in Ink. The memorable elements are named in `DESIGN.md`: the tracking gauge, the breathing live bar, the 64px monospace ticker, the 1px blueprint grid. The brand mark is the tracking gauge itself rather than a letter, because it is the one mark nobody else has and it says what the app does.

**Note for anyone writing across both surfaces:** this system is the mobile app's own, adopted 2026-09-11 after the first canvas was rejected for reading like the web product. `DESIGN.md` in this repository is the source of truth here, and the web design system is not. Any visual or voice claim must say which surface it describes.

## Proof Points

Every number below is a measurement with a date, taken against the live market or on the device. Cite them with the date. Do not round them into adjectives.

**Metrics:**
- **Liquidity, measured 2026-09-12** by joining `/api/v1/summary` against the xStocks Solana catalog and pricing every matched mint through Jupiter Price v3: 157 analyzed xStocks reach the Analyzed section; Jupiter returns a price for 55; of those, 13 pools are at or above $100k, 6 between $10k and $100k, 10 between $1k and $10k, and 22 below $1k. The 13 deepest all track the NYSE close within 0.8 percent (NVDAx, TSLAx, AAPLx, MSTRx, HOODx, MSFTx, COINx, GOOGLx, MCDx, METAx, AMZNx, PLTRx, KOx).
- **First content, measured on the Seeker 2026-09-13** by screen recording anchored to the system's own `ActivityTaskManager: Displayed` frame: first row 11.7 s before the work and 2.75 s after, and 3.5 s to a settled screen on a warm launch. The catalog download behind the old number was 4.31 MB over 8 sequential pages in 7,457 ms, against a 378 ms and 44 KB `/summary` call.
- **A real mainnet swap has landed from the Seeker**, at slot 445899686: 5 USDC to 0.01366647 TSLAx, `/order` 593 ms, wallet round trip 14.4 s, `/execute` 1.0 s, 16.1 s in total. Verified 2026-09-10 through the pre-app spike, with the signature held privately and shown in the video from the demo wallet. **Careful:** that swap was the spike, not the shipped swap sheet. As of 2026-09-13 the sheet itself has been walked on hardware only as far as the amount step, because the connected wallet held no USDC, and no transaction was signed. Do not write that the shipped sheet has landed a swap until it has. (`docs/plan-2026-09-10.md` section 3, `docs/data-map.md`)
- **Quality gates, 2026-09-13:** 605 unit tests green; `scripts/device-smoke.sh` ran 66 assertions over 24 dumps in 225 s and 223 s on the Seeker, exit 0 both times, and it has been shown to fail as well as pass. The pulse is measured rather than eyeballed through `dumpsys gfxinfo`: 484 to 488 frames with animators on, 4 with the animator scale at 0. Font scale 1.3 and 2.0 both walked with nothing wrapping. (`docs/qa-checklist.md` section 7)
- **Cost per quote, measured:** the all-in cost on the same pair moved from 0.08 percent to 1.44 percent within 15 minutes in extended hours, which is why cost is shown per quote and never as a fixed line.

**Customers:** None to name. There are no logos, no named users and no case studies. The only third-party names that may appear are attributions, and they are attributions in text and never logos: "Analysis by PlainTicker. Token catalog and proof of reserves by xStocks (Backed Finance). Prices and routing by Jupiter. Filings from SEC EDGAR. Not affiliated with any of them."

**Testimonials:** None exist. Do not write one.

**Value themes:**

| Theme | Proof |
|-------|-------|
| It reads the token, not just the price | Proof of reserves, permanent delegate, pausable, transfer hook, new account state and the split multiplier, read live from the Token-2022 mint on every Detail open, with the slot and the age on screen |
| It says when a number means nothing | The $10,000 floor, set from the 2026-09-12 measurement, with the pool stated in place of the premium |
| It classifies and does not advise | `is_prediction: false`, `kind: "classification"` in the payload, asserted by a test; no BUY, SELL or HOLD words in the codebase, held by `CopyLintTest` and asserted again on device by `scripts/device-smoke.sh` |
| It is fast enough to open before a swap | 2.75 s to the first row on the Seeker, measured 2026-09-13 |
| It keeps no secrets in the APK | No API key ships; RPC goes through a bounded forwarder whose source is mirrored in this repository; Jupiter is used keyless |

## Goals

**Business goal:** Ship to the Solana Mobile CLOCK IN hackathon. Submissions close 2026-10-08. Judging is four criteria at 25 percent each: stickiness and product-market fit with the Seeker community, user experience, innovation against existing products, and presentation and demo quality. Completion is judged from the demo video and technical depth from the commit history of this repository, which is why the Solana work is parsed in Kotlin on the device where it can be read.

**Conversion action:** Not decided. Install, a first swap, a first watched ticker and a visit to plainticker.com are all plausible and the repository names none of them as the one. See the open questions.

**Current metrics:** None. The app ships no analytics SDK and no tracking by design, so there is no usage number to report and none can be quoted. On the server there is only the API request log.

**Definition of done for 2026-10-08** is a nine-item checklist in `docs/plan-2026-09-10.md` section 11, including a signed release APK from tag `v1.0.0-hackathon` that installs on a wiped Seeker, a same-day device pass with ten cold starts and zero crashes, no verdict words on any analysis surface or notification, and a submission confirmation saved at least 24 hours before the deadline.

## Open questions (unanswered as of 2026-09-13)

Product questions only. The commercial ones are in the private document. Nobody has answered these, so no skill should write as though they had.

1. Who is the end user of the mobile app, in one sentence? No profile of them exists anywhere in this repository.
2. What is the single conversion action for the mobile audience: install the APK, complete one swap, watch a ticker, or land on plainticker.com?
3. What is the app's public name in the dApp Store listing, "PlainTicker" or "PlainTicker Mobile", and what is the 30-character short description?
4. Is the app's total refusal of a decisive label the direction both surfaces are heading, or does it stay a mobile-only rule? The web engine still emits one, under a qualifier. Until this is answered, no copy for the app may quote that label.
5. Is there any customer research at all that a marketing document should be quoting verbatim? At the moment there is none in this repository, which is why Switching Dynamics and Customer Language are empty.

## Changelog

*Newest first. One line per revision: what changed and why.*
- v1 (2026-09-13): Initial context for the mobile app, drafted from README, DESIGN.md, the plan, the data map, TODOS and strings.xml. Public half of a two-file split: product, audience, differentiation, voice and measured proof here; pricing, monetization, revenue thinking and named competitors in the private web repository.
