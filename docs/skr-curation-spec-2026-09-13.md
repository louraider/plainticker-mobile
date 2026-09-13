# SKR-weighted coverage curation — specification

The SKR integration the founder chose on 2026-09-13, after rejecting stake-to-unlock as too weak on
its own. Layer C of `docs/skr-plan-2026-09-13.md`, promoted to the only layer that ships.

## What it is, in one paragraph

The app covers 160 of the 832 tokenized stocks on Solana. 672 have no analysis at all, and the list
already draws them in their own section, where a row offers a price and nothing else. Producing an
analysis costs money and takes a cron cycle, so coverage is a finite budget that today nobody
allocates. **Staked SKR allocates it.** A Seeker owner votes with the weight of their stake for
which uncovered xStock is analyzed next, the leaders are visible in the app, and the prewarm run
covers the winner. Vote, then the analysis appears.

## Why this and not stake-to-unlock

Stake-to-unlock gates something that already exists, and the only mobile section plan section 7
calls Pro is "Against the sector", which ships free today. Gating what people already see is a
downgrade. Curation gates nothing: it decides what gets built next, which is the one genuinely
scarce thing this product has.

It also answers the question the submission form actually asks. "DOES YOUR APPLICATION HAVE AN SKR
INTEGRATION? IF SO, HOW?" has a one-sentence answer here: staked SKR decides where a finite analyst
budget points.

## The measurements it rests on, all 2026-09-13

| | |
|---|---|
| xStocks with a Solana mint | 832 |
| of those, an analysis exists | 160 |
| **no analysis at all** | **672** |
| of the 160, six days old | 152 |
| stake accounts in the SKR staking program | 47,965 |
| with a non-zero principal | 46,436 |
| median stake | 6,719 SKR |
| one account carrying an impossible principal | 1.145 × 10^13 SKR |
| cost of one analysis | about $0.025 of LLM |

## The identity problem, and the recommended answer

A vote weighted by someone's stake is worthless unless the voter is proved to control that wallet.
Otherwise anyone posts a whale's address and votes with a stake that is not theirs.

Two ways to prove it. **The second is recommended.**

### Option 1, the nonce flow written in TODOS.md

`POST /api/v1/auth/nonce` → the app signs it with MWA `signMessages` → `POST /api/v1/auth/verify`
checks ed25519 → a short-lived token → the vote is posted with that token.

Costs: a replay-safe nonce store, a new ed25519 dependency and new crypto code in a production
repository that today contains **no signature verification anywhere**, and a token lifetime to get
right. All of it in the repository whose `.env.local` is its production environment.

### Option 2, the vote is a transaction — recommended

The app builds a transaction carrying a memo, `PT-VOTE:<TICKER>`, the wallet signs and sends it
exactly as it already signs and sends a swap, and the server reads votes by walking the signatures
of one collector address and reading their memos.

Why this is better here:

- **It adds no crypto and no auth surface to the production web app.** The signer is the voter by
  construction. There is no nonce, no token, no replay window, and nothing to get wrong.
- **The vote is public and anyone can verify it**, which is the same stance as the published
  backtest and the tamper-evident method portfolio. A vote nobody can audit would be the one
  unverifiable thing in a product built on verifiability.
- **It is an interaction with Solana**, which the judging brief names in as many words.
- The app already signs and submits transactions, so the client work is a variation on a path that
  has landed real money on mainnet.

Costs: the voter pays a signature fee, about 5,000 lamports, a tenth of a cent. The server polls
`getSignaturesForAddress` on the collector and reads memos, which is read-only RPC on the same
bounded forwarder path the app already uses.

**The weight is read on-chain, not sent by the client.** The server reads the voter's staked
principal itself with the pinned `getProgramAccounts`, so a client cannot inflate its own weight
even if it wanted to.

## Known weakness, to be stated and not hidden

A balance-weighted vote is gameable by a whale: 4,674 wallets hold more than 31,210 SKR and one of
them outweighs thousands of small stakers. This is not fixable by tuning and it goes in the README
next to the sentence about the liquidity floor, in the same voice. Two mitigations are worth
considering and neither is free: a square-root weight, which flattens whales without silencing
them, and a per-wallet cap at the median.

**The impossible principal found on 2026-09-13 is a second, sharper case.** One account in the
program decodes to 1.145 × 10^13 SKR, which is not a stake. If that wallet ever voted it would
outweigh the entire rest of the program combined. The weight must be bounded by the real staked
supply, about 4.39 billion SKR, and a vote that exceeds it is refused rather than counted.

## Server work, in the web repository

**None of this may be written without the founder's trigger, and the migration is the reason.**
`.env.local` equals `.env.production.local` there: a single Neon database, so a migration applied
locally hits production. Migrations in that repository are applied by the operator, never by an
agent. The standing instruction of 2026-05-18 is still live in `CLAUDE.md`.

| step | what |
|---|---|
| migration 0023 | `coverage_request` gains `voter` (text, the wallet) and `weight` (numeric), plus a unique index on (ticker, voter) so one wallet counts once per ticker |
| vote reader | a cron or route that walks the collector's signatures, parses `PT-VOTE:<TICKER>` memos, reads each voter's staked principal, bounds it, and upserts |
| next-up query | the uncovered tickers ordered by summed weight |
| `recordCoverageRequest` | gains `kind = "skr-vote"`; the existing per-IP token bucket does not apply to a path whose identity is a signature |

The gate that repository holds itself to: vitest, `tsc` 0, `eslint` 0, `next build` 0, and a live
canary before it is called shipped.

## App work, in this repository

| step | what |
|---|---|
| 1 | a "Vote to cover next" action on a row in the "Without analysis" section, and on that ticker's Detail |
| 2 | building and sending the memo transaction through the existing wallet session |
| 3 | a "Next up" strip showing the leaders and their weight, so the loop is visible before anyone votes |
| 4 | the states: no wallet, no stake, already voted, the vote landed, the vote failed |
| 5 | copy in `strings.xml`, subject to `CopyLintTest` |

## What needs the founder

1. **The trigger** for touching the web repository at all, given the standing instruction.
2. **Applying migration 0023 to production**, which only the operator does.
3. **The collector address.** A new address the votes are sent to, which must not be the founder's
   own wallet, because the repository must never carry it.
4. Whether the weight is linear, square-rooted or capped. Linear is simplest and most gameable.
