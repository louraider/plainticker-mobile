# PlainTicker Mobile

A reading tool for tokenized US stocks (xStocks) on Solana, built for the Seeker phone. Before you swap, it shows what the token itself says, live from the mint, and then what the company's filings say against its sector. One Swap button, after you have read.

Solana Mobile hackathon CLOCK IN, September to October 2026. Not a price forecast. Not investment advice. xStocks are tokenized tracker instruments issued by a third party and are not available to US persons.

## What a judge sees in three minutes

1. **Onboarding**: one screen, three sentences, a self-certification checkbox, "Read the list".
2. **List**: every xStock with a PlainTicker analysis first, sorted by composite; price-only tokens below.
3. **Detail, trust first**: token price against the NYSE close on a tracking gauge, a live signature read from the Token-2022 mint (slot and age), then "Backing and controls": proof of reserves, permanent delegate, pausable transfers, split multiplier, transfer hook.
4. **Detail, fundamentals**: quality, valuation and momentum against the sector as marker tracks, the F-Score with its nine signals, and the method statement.
5. **Swap**: a bottom sheet with one amount field, the all-in cost and the route; signing happens in the Seed Vault wallet; the sheet becomes a receipt when the transaction lands.
6. **Portfolio and Watchlist**: holdings priced by Jupiter with the per-token premium, no invented profit and loss; watched stocks with a daily digest notification.

## Architecture

```
Seeker (Kotlin, Jetpack Compose, Mobile Wallet Adapter 2.2)          all parsing on device
|
|-- PlainTicker API   https://www.plainticker.com/api/v1
|     /summary        every ranked ticker in one edge-cached call (5 min)
|     /{TICKER}       full classification payload v1.1
|     /rpc            bounded JSON-RPC forwarder to the RPC provider (key stays on the server)
|                     allowlist, pinned getProgramAccounts, body cap, per-IP and daily budgets, 60 s cache
|-- xStocks public API   catalog (mints, trading calendar), proof of reserves, split multiplier
|-- Jupiter              Price v3 (batched, reference price for the premium), Swap v2 order and execute
`-- Solana mainnet       Token-2022 mint extensions read live through the forwarder
```

No API key ships in the APK. Jupiter is used keyless. The forwarder contract and a mirror of its source live in `server/rpc-proxy/` for auditors; the server code itself is in the PlainTicker repository.

The analysis engine is PlainTicker (SEC EDGAR XBRL filings plus market data), the same engine behind www.plainticker.com. The app renders its classification payload and never adds a verdict of its own: there are no BUY, SELL or HOLD words anywhere in this codebase, and a lint test keeps it that way.

## Security and threat model

- **Money moves only in one place.** The single money-moving call (Jupiter `/execute`) is behind `BuildConfig.SUBMIT_SWAPS`: false in debug builds (transactions are signed but never submitted), true in release builds.
- **The wallet is the system wallet.** Signing goes through Mobile Wallet Adapter to the Seeker's Seed Vault wallet; the app never holds a key and never asks for a seed.
- **No secrets in the APK.** RPC traffic goes through `/api/v1/rpc`, which accepts one JSON-RPC request from an allowlist (`getAccountInfo`, `getMultipleAccounts`, `getTokenAccountsByOwner`, `getBalance`, and `getProgramAccounts` pinned to the SKR staking program with a fixed filter shape), caps the body at 16 KiB, rate-limits per IP and per day, and never echoes the upstream URL or key.
- **Issuer controls are shown, not hidden.** Permanent delegate and pausable transfers are read from the mint on every Detail open and rendered as risk lines.
- **Repository hygiene.** `scripts/redaction-guard.sh` scans tracked files for denylisted identifiers (as SHA-256 hashes) in CI; the founder wallet and test transaction signatures never appear in the tree. `docs/public-flip-checklist.md` is run before the repository goes public.
- **What we do not do.** No custody, no price predictions, no leverage, no US persons (self-certification at onboarding), no tracking SDKs, no analytics beyond the server's request log.

Threats considered: a malicious xStock mint impersonating a listed one (the catalog is the source of mints, prices are batched by mint), a hostile RPC response (typed parsing with unknown keys ignored, on-chain rows fall back to "unavailable"), quote expiry and slippage (`-2003`/`-2004` trigger one requote), a user without SOL for the token account (the shortfall is shown before signing), and a stale analysis (rows stay visible with their age; only broken rows are hidden).

## Build and run

Requirements: Android Studio with the Android 15 SDK (compileSdk 37), JDK 17 or 21, a device or emulator with a Mobile Wallet Adapter wallet. Tested on a Solana Seeker (Seed Vault Wallet).

```
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # debug APK: signs swaps but never submits them
./gradlew :app:assembleRelease        # release: env-driven signing, see docs/release-signing.md
```

CI (`.github/workflows/ci.yml`) runs the unit tests and the redaction guard on every push; `release.yml` builds a signed APK on `v*` tags.

## Design

`DESIGN.md` is the design system ("Instrument"): cold near-black canvas, one blue accent for interaction and live state, Outfit for words and JetBrains Mono for every number, sharp corners, a tracking gauge and blueprint fact grids. The approved screens are generated from `design/canvas/instrument.py`.

## Limitations

- Ten of the xStocks the app analyses were added to PlainTicker for this hackathon; for young or loss-making companies some forward-looking filing metrics are shown as "not available for this filer" rather than estimated.
- The analysis is a rule-based classification of fundamentals against the sector, refreshed by a weekly job; the age of each analysis is shown on every row.
- Portfolio shows no profit and loss because cost basis is not readable from the chain; swap receipts made in the app are kept locally for a later, honest P&L.
- Portrait only, Seeker-first; a light theme and large-screen layouts are deferred.

## Attribution

Analysis by PlainTicker. Token catalog and proof of reserves by xStocks (Backed Finance). Prices and routing by Jupiter. Filings from SEC EDGAR. Not affiliated with any of them.

## Plan and decisions

`docs/plan-2026-09-10.md` is the plan of record (architecture, decision log, design decisions, task list). `TODOS.md` holds deferred work with its triggers. `docs/hackathon-rules-2026-09-10.md` records the hackathon rules as read from the official sources.
