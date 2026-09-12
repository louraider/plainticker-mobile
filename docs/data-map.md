# Data map for the Week 2 screens

Which field feeds which cell, from the live sources as of 2026-09-11. Screens and cells are named as in `DESIGN.md` and `design/canvas/instrument.py`. Every number goes through `Fmt`. Kotlin models: `com.myapp.data.plainticker.PlainTickerModels`, `data.xstocks.XStocksModels`, `data.jupiter.JupiterModels`, `data.rpc.RpcModels`.

## Sources

| Source | Call | Cache | Notes |
|---|---|---|---|
| PlainTicker summary | `GET /api/v1/summary` | edge 5 min, app 5 min | 178 rows today; `stale`, `age_days`, `company` present; no verdict label |
| PlainTicker detail | `GET /api/v1/{TICKER}` | app 5 min per ticker | v1.1 payload, shape below |
| xStocks catalog | `XStocksApi.catalog()` | 6 h | ~830 assets over pages; filter to xStocks with a `trading` block; gives mint, symbol, name, underlying ticker |
| xStocks proof of reserves | `XStocksApi.proofOfReserves(symbol)` | 30 min | may be JSON `null` for a symbol (render "unavailable") |
| xStocks multiplier | `XStocksApi.multiplier(symbol)` | 30 min | scaled UI amount, activation time as number |
| Jupiter Price v3 | `prices(mints)` batched, 50 mints per call | 30 s | `usdPrice`, `blockId`, `stockData?.price` (reference), `priceChange24h` (never rendered) |
| Solana mint (forwarder) | `getAccountInfo(mint, jsonParsed)` | 60 s server-side | extensions list below |
| Token accounts (forwarder) | `getTokenAccountsByOwner(owner, programId = Token-2022 and classic)` | per open | raw amount, decimals |
| SKR stake (forwarder) | pinned `getProgramAccounts` | per open | principal u64 LE at slice 105/8 |

## Detail payload v1.1 (live shape, AAPL)

```
ticker, company, sector, as_of
verdict            { code, label_uk, label_en, tone }        NEVER rendered, never parsed into a label
axes.quality       { value 0-9, scale, position 0-1, state, label_uk, label_en, tone }
axes.valuation     { value 0-100, scale, position, state, label_en, tone }
axes.momentum      { value 0-1, scale, position, state, label_en, tone }
fscore             { score, scale "0-9", breakdown { profitability, leverageLiquidity, efficiency }, signals[9] boolean|null }
composite_percentile   integer 0-100
setup              { score 0-5, scale, criteria[5] { key, passed } }
forward.raw        { asOf, provider, epsCagr3y, forwardPE, forwardPEG, trailingPE, beatHistory[], analystCount, yearsSpanned,
                     beatRateDecay8q, epsGrowthTTMYoy, beatRateRawCount, nextEarningsDate, sentimentTrend3m, revenueGrowthTTMYoy,
                     beatRateAvgMagnitude, nextEarningsEstimate, recommendationsLatest, sectorEpsCagr3yMedian, impliedPerpetualGrowth,
                     epsCagr3yReason, epsTrajectory, epsGrowthShort }   any field may be null; raw is null only when the provider call itself failed
forward.score      { missing, realityPct, compositePct, consensusPct, pegBurdenPct, valuationPct, confidencePct, beatRateDecayPct, sentimentTrendPct, secRealizedGrowthPct }
method             { is_prediction false, kind "classification", statement_uk, statement_en, schema_version "v1.1" }
```

English text sources: `axes.*.label_en` (state words), `method.statement_en` (disclaimer). `headline` in `/summary` is Ukrainian and is not rendered in the app. `label_uk` fields are ignored.

## Screen cells

### List (T8)

| Cell | Field | Rule |
|---|---|---|
| Today strip | Watchlist store + `forward.raw.nextEarningsDate` of watched tickers | hidden when nothing is watched; "next report {TICKER} on {date}" |
| Row ticker | catalog `symbol` (e.g. TSLAx) | mono 18 |
| Row company | `/summary.company`, fallback catalog `name` | 13, ellipsis |
| Row value right | `/summary.composite` as `Fmt.count`-style 2 decimals of 0-1? No: composite is a percentile 0-100 on the web; on mobile show `composite_percentile`-style integer from `/summary.composite` | show as integer `71`, not `0.71` (fix the canvas sample later) |
| Row state word | derived from `/summary.tone` and `setup_score`: positive -> "strong", caution -> "fair", danger -> "weak", null -> no word | never the verdict |
| Row meta | Price v3 premium vs `stockData.price` as `Fmt.percent` + " vs NYSE close" + " · " + `Fmt.ageOld(age_days)` | one middle dot |
| Section "Without analysis" | catalog xStocks whose underlying ticker is not in `/summary` | price only, muted |
| Stale row | `/summary.stale = true` | row stays, meta says "analysis {n} d old"; banner only if everything is stale |
| Broken row | absent from `/summary` | hidden from Analyzed, may appear in Without analysis |

#### Measured on the Seeker, 2026-09-12

The placeholder list was run against production on the device. It loaded 179 analyzed rows, so the join works, and it exposed four things T8 has to fix and one data-layer bug.

- **Prices came back empty and the row meta read "Prices unavailable".** Reproduced from a laptop with the app's own call pattern: the keyless Jupiter bucket serves about five rapid calls and then answers `429 {"code":429,"message":"[API Gateway] Too many requests"}`. `JupiterPriceApi.prices` loops chunks of 50 with `bodyOrThrow()` and no pacing, so the first 429 throws out of the whole function and discards the chunks that already succeeded, and `ListViewModel` turns that into no prices at all. Three fixes, none of which is an API key: keep the chunks that succeeded, pace the calls to the documented 0.5 per second with one backoff on 429, and price the visible rows first rather than all 149 mints at once.
- **The Ukrainian headline is rendered.** Rows showed "Сигнали збігаються: дешева якість проти сектора". `headline` in `/summary` is Ukrainian and this document already says it is not rendered in the app; the placeholder renders it anyway.
- **Composite is a raw float**, shown as `83.80406` instead of the integer percentile.
- **Rows with no xStock are mixed into Analyzed** (BKNG appeared with no `x` suffix). They belong under "Without analysis" or nowhere.
- The home tab row is still the template Material `TabRow`, so inactive tabs are blue instead of Muted, and the placeholder rows have no side padding. `TopTabs` exists and is used in the gallery.

#### Seventeen seconds to first content, measured on the Seeker 2026-09-13

Walked the merged build on the real phone. The list is correct and the liquidity floor works in the wild: APPx shows "Pool holds $34, too thin to track" where a naive screen would have drawn +89 percent, NVDAx shows "+0.12% vs NYSE close · 6 d old", composites render as integers, no Cyrillic reaches the dump, and ABNBx correctly shows no age at all because its analysis is under a day old. Detail is right too: the pool sentence stands where the gauge would be, the live bar's clock ticks, proof of reserves names Alpaca as custodian, and permanent delegate and pausable transfers are the only two values in Caution.

**The problem is the wait.** From launch to the first row: skeletons at 6 s, skeletons at 11 s, rows at 17 s. Timed from a desktop connection, the cause is not the analysis:

| call | time | size |
|---|---|---|
| `/api/v1/summary` | 378 ms | 44 KB |
| xStocks catalog, 8 sequential pages | 7,457 ms | 4.31 MB |

The catalog is the whole cost, and it is paid before a single row can draw because rows need the symbol and the mint. On a phone the parse of 4.3 MB adds to it. Nothing renders until it finishes.

This matters more than its size suggests: the list is the first thing a judge opens, and the demo video opens on it. Three fixes, all client side and none needing an API key: paint the bundled snapshot immediately and let the network refresh over it, which T8 already built for the outage path and which turns the wait into nothing; render after page 0 and keep paging in the background, so rows appear at about a second; and cache the catalog on disk so the second launch pays nothing. A slim server-side catalog carrying only symbol, underlying, mint, name and the trading block would cut 4.31 MB to tens of kilobytes, but that is PlainTicker server work and the client fixes land sooner.

#### The liquidity floor, measured 2026-09-12

Joined live: `/api/v1/summary` (179 tickers) against the xStocks Solana catalog, then every matched mint priced through Jupiter Price v3 in paced chunks.

| | count |
|---|---|
| analyzed xStocks reaching the Analyzed section | 157 |
| of those, Jupiter returns a `usdPrice` | 55 |
| of those, liquidity at or above $100k | 13 |
| liquidity $10k to $100k | 6 |
| liquidity $1k to $10k | 10 |
| liquidity below $1k | 22 |

The thirteen deepest pools all track the NYSE close within 0.8 percent: NVDAx, TSLAx, AAPLx, MSTRx, HOODx, MSFTx, COINx, GOOGLx, MCDx, METAx, AMZNx, PLTRx, KOx. Below roughly $10k the quote stops meaning anything: UBERx reads +152.13 percent on a pool holding $80, APPx +89.34 percent on $34, CRWDx -42.15 percent on $48, ASMLx +30.42 percent on $61.

The premium against the NYSE close is one of the two things a row exists to show and the tracking gauge is the signature element of the Detail screen, yet for most rows the premium is either absent or arithmetic noise off a dead pool. Presenting a $34 pool's quote as a tracking figure contradicts the trust-first stance the whole product is built on.

**Decided 2026-09-12: show the pool depth and withhold the noise.** The rule is `TrackingQuality` (`data/jupiter/TrackingQuality.kt`), pure and unit tested, and it is the only place the floor exists. It reads `liquidity` off the Price v3 entry, which is already parsed and already in the production response, and answers with one of three cases:

| Case | When | What the surface draws |
|---|---|---|
| `Tracked` | pool at or above `MIN_POOL_USD` | the signed premium against the NYSE close, and the gauge on Detail. Unchanged from before |
| `Thin` | pool below the floor | no premium and no gauge. The row states the pool instead |
| `Untracked` | Jupiter priced the token but sent no `liquidity` | no premium and no gauge, and no pool number to state |

`MIN_POOL_USD` is **$10,000**, from the counts above: at or above $100k the 13 deepest tracked within 0.8 percent, the $10k to $100k band deviated by about 2 percent (a real spread on a shallow venue, still a description of the market), and under $10k the number stopped describing anything (UBERx +152.13 percent on $80, APPx +89.34 percent on $34, CRWDx -42.15 percent on $48, ASMLx +30.42 percent on $61). Thirty-two of the 55 priced tokens sit under the floor, four report no depth at all. The floor is not $100k because that would take the premium off six readable rows for a precision the row never claims.

The copy, all of it in `strings.xml` and gated by `CopyLintTest`, with money through `Fmt.compactMoney` ("$34", "$12.5k", "$1.3M"):

| Where | `Thin` | `Untracked` |
|---|---|---|
| List row meta | `Pool holds $34, too thin to track` | `Pool depth not reported` |
| In place of the gauge | `Pool holds $34, too thin to track the NYSE close` | `Pool depth not reported, tracking cannot be checked` |

The row's meta line keeps its shape: the sentence takes the premium's half, the analysis age keeps the half after the middle dot, and the row stays one line and 64dp. The sentence is Muted on the row and Ink 2 under the price, never Caution: a shallow pool is a fact about the token, not an issuer-control risk (DESIGN.md section 2). Sorting and sections are untouched and nothing is filtered out: the choice was disclosure, not curation. Written up as DESIGN.md section 1.1.

### Detail (T9)

| Cell | Field | Rule |
|---|---|---|
| Hero ticker | catalog `symbol` | 64 mono |
| Company | `company` | 16 Ink 2 |
| Token price | Price v3 `usdPrice` | `Fmt.price` |
| NYSE close | Price v3 `stockData.price` | `Fmt.price`; the label is "NYSE close" while the exchange is shut and "NYSE price" during its session (`MarketStatus.priceLabel`); null -> "Reference price unavailable" in the gauge's own slot, in Ink 2 and never Caution, gauge hidden |
| Gauge | `TrackingQuality.of(entry)`, never arithmetic in a composable | scale 0.5 percent; caption "Token vs NYSE close, scale 0.5%", or "Token vs NYSE price" during the exchange session; value `Fmt.percent`; below the liquidity floor the pool sentence takes the whole slot |
| Live bar meta | `getAccountInfo` context `slot` and fetch time | "slot {Fmt.slot} · {Fmt.relativeAgo}"; hollow (not live) when the RPC call failed |
| Proof of reserves | xStocks PoR `sharesHeld`, `tokensInCirculation` | ratio `Fmt.percent(unsigned)` value; sub "{shares} shares held for {tokens} tokens" mono |
| Permanent delegate | mint extension `permanentDelegate.delegate` | present -> "Yes" Caution + "Issuer can move tokens"; absent -> "None" |
| Transfers pausable | mint extension `pausableConfig.paused` | present -> "Yes" Caution + "Not paused now, issuer can pause" or "Paused" Caution; absent -> "None" |
| Split multiplier | mint extension `scaledUiAmountConfig.multiplier` (fallback xStocks multiplier) | `1.00`; sub "No pending split" or "Changes to {new} on {date}" |
| Transfer hook | mint extension `transferHook.programId` | null -> "None", else short key |
| Track Quality | `axes.quality.value`/`scale` | value "8/9", state `label_en` lowercased, marker `position` |
| Track Valuation | `axes.valuation.value` rounded | value "51", state `label_en` lowercased ("moderate" -> show "fair"? no: show `label_en` as is) |
| Track Momentum | `axes.momentum.value` | 2 decimals, state `label_en` |
| Heading right | `composite_percentile` | "composite {n}" |
| Fact grid (Against the sector) | not in the v1.1 payload | **Decided for T9: there is none.** v1.1 exposes no ROIC, margins, P/E or 52-week facts, so the section is the composite in the heading and the three tracks; the canvas cells are placeholders and `DetailScreenTest` fails if one of their string ids reaches the screen. An additive `facts` block server-side would revive it. |
| F-Score numeral | `fscore.score` | "8" + "of 9 signals" |
| Signal rows | `fscore.signals[9]` | names in this fixed order: Return on assets positive; Operating cash flow positive; Return on assets improving; Cash flow exceeds earnings; Leverage falling; Liquidity improving; No new shares issued; Gross margin improving; Asset turnover improving. null -> "n/a" Muted, never "no" (`SignalRow` takes a nullable) |
| Method body | `method.statement_en` | 15 Ink 2 |
| Method sources | static string | "Filings from SEC EDGAR XBRL. Prices from Jupiter. Reference from the NYSE close." |
| Analysis age | `as_of` | "Analysis from {Fmt.relativeAgo}" at the top of Method, only when > 24 h |
| Not served | detail 404 `unsupported_ticker` or `not_available` | trust layer still renders from chain + xStocks; fundamentals replaced by one row "Analysis not yet available" |
| forward.* | all fields | not rendered in the hackathon build (design decision); may feed a later "Expectations" section |

#### The mint, read live 2026-09-12 (T9 data half)

`getAccountInfo(mint, jsonParsed)` through the production forwarder, captured verbatim into
`app/src/test/resources/rpc/mint-tslax.json` (TSLAx, `XsDoVfqeBukxuZHWhdvWHBhgEHjGNst4MLodqsJHzoB`,
slot 446,503,662). Two hand-built siblings sit beside it: `mint-no-extensions.json`, a Token-2022
mint with no extension list at all, and `mint-pending-split.json`, a scheduled 1 to 4 change with
paused transfers, a live hook and a frozen default account state.

| Fact | TSLAx, live | Read as |
|---|---|---|
| owner | `TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb` | Token-2022, never the classic program |
| decimals | 8 | every xStock |
| `permanentDelegate.delegate` | `5aMNNLQJ…HFvEq` | present, so the issuer can move tokens |
| `pausableConfig.paused` | `false` | present and not paused right now |
| `scaledUiAmountConfig` | multiplier `"1"`, newMultiplier `"1"`, effective `0` | quoted decimal strings, the timestamp a bare number in unix seconds |
| `transferHook.programId` | JSON `null` | the extension is configured and no program runs |
| `defaultAccountState.accountState` | `"initialized"` | a fresh account is not frozen |
| supply | `"22963733950050"` | 229,637.3395005 tokens at 8 decimals |

`MintFacts.from` (`data/rpc/MintFacts.kt`) is the only reader. Each extension fact is nullable and
null means exactly one thing, that the extension is not on the mint; a present extension that is
quiet carries a value instead. An account owned by the classic program, an account that is not a
mint, a base64 read or a payload with no decimals or no supply all read as null, which every
surface renders as unknown and never as no risk. The trust rows therefore have three renderings,
not two: the fact, its absence, and a mint that could not be read.

Three more decisions the T9 data half fixes:

- **The split multiplier prefers the chain.** `SplitMultiplier` (`data/SplitMultiplier.kt`) reads
  the mint's `scaledUiAmountConfig` first and falls back to `XStocksApi.multiplier` only when the
  mint could not be read or carries no scaled amount, and the answer names its `source`, so an
  issuer's description of a split is never drawn as an on-chain fact. The xStocks
  `activationDateTime` is typed as a plain number upstream and normalized to unix seconds.
- **Proof of reserves.** A JSON `null` answer is an absence, rendered as unavailable and never as a
  coverage of zero; the `Cached` entry for it is kept as an answer so it is not re-asked per open.
  `Reserves` carries `sharesHeld`, `tokensInCirculation` and the custodians named in `holdings`.
- **Trading hours.** `MarketHours` (`data/xstocks/MarketHours.kt`) is one pure function over a
  clock. The asset's own `trading` block decides whenever it is there, including against the
  calendar; the fifteen-line Monday to Friday 09:30 to 16:00 America/New_York schedule is reached
  only by an asset with no block, and the answer says which source spoke. The schedule knows no
  holidays, so a session only it claims is drawn as its claim: the banner reads "The NYSE is open
  by the local schedule, the venue did not answer" rather than nothing at all. The price row reads
  `MarketStatus.priceLabel`: within a percentage during the exchange session, against the NYSE
  close in every other period, which includes extended, overnight and a halt.

#### The composition, built 2026-09-12 (T9 UI half, DT6)

`DetailScreen.kt` places; `DetailModel.kt` decides. Every sentence and numeral the screen draws is
a pure function over `DetailUiState` (`DetailModelTest`, 47 cases), so each Pass 2 state is a unit
test rather than a device run, and `DetailScreenTest` reads the composition from source to pin the
section order, the absence of arithmetic in it, and the fact that no word is chosen there.

Five decisions the composition fixes:

- **The reference is a close only while the exchange is shut.** The right label of the price row and
  the gauge caption both come from `MarketStatus.priceLabel`: "NYSE close" and "Token vs NYSE close"
  out of session, "NYSE price" and "Token vs NYSE price" in it. The two arg-taking strings
  `detail_price_tracking_within` and `detail_price_vs_close` are gone: the gauge already prints the
  signed premium on its right, so a caption repeating it was the same number twice.
- **Five cells under "Backing and controls"**, which packs the two-column grid exactly: proof of
  reserves spanning the first row, then permanent delegate, transfers pausable, split multiplier and
  transfer hook. `defaultAccountState` and the on-chain supply are read by `MintFacts` and not drawn;
  their strings are in `strings.xml` for whoever adds a second grid.
- **Caution reaches exactly two values**, the permanent delegate and pausable transfers, and only
  where the mint actually carries the extension. A revoked delegate is "None" in Ink. A mint that
  could not be read is "Unknown" with "The mint could not be read" under it, in Ink: an absence of
  facts is never drawn as an absence of risk, and never as a warning either.
- **The live bar breathes for one minute**, the forwarder's own cache window, then goes static while
  the meta line keeps counting. Both need a clock that moves, so `DetailViewModel` re-reads the wall
  clock once a second while the state is collected; taken once at the refresh it is older than the
  read it is compared against and the age is stuck at "0 s ago" for ever. It is announced by its slot, not by the ticking age, so a polite
  live region does not interrupt a reader every second.
- **The Swap button keeps the placeholder's wiring** (T10 owns the sheet). Its mono line states the
  pool until an order exists, then "est. all-in cost 0.09% · liquidity $1.3M".
### Swap sheet (T10)

| Cell | Field |
|---|---|
| Header | "USDC to {symbol}"; the direction text action beneath it only when the wallet's token balance is above zero |
| Amount | user input, `Fmt.tokenAmount` for the balance line from the USDC token account |
| You receive | `/order` `outAmount` / 10^decimals, `Fmt.tokenAmount`; sub line is `otherAmountThreshold` as the worst case |
| All-in cost | `Order.allInCostPct` (fee bps + platform fee bps + price impact); sub line is the route |
| Route | `Order.router` ("metis" -> "Metis") |
| SOL needed | `signatureFeeLamports + rentFeeLamports + prioritizationFeeLamports`, nine decimals; sub names the rent when there is one |
| Countdown | `Order.secondsLeft` when `hasExpiry` (RFQ only); nothing at all otherwise |
| Receipt | `/execute` signature, slot, `outputAmountResult` as the fill; "quote {x}%, fill {y}%" from quoted vs actual |

Dropped from the canvas: the **rate line** (`usdPrice` and `stockData.price` are a Detail reading and
the sheet does not fetch Price v3) and the **Liquidity** cell (no field in `/order`, and the sheet
makes no second call to find one). Both are recorded under the sheet's own block below.

#### The order, verified live 2026-09-12 (T10)

One `GET /swap/v2/order` for 5 USDC into TSLAx with the demo wallet as taker, 200 in 310 ms. Every cost is its own field with its own payer, so the sheet infers nothing:

| field | this quote | payer |
|---|---|---|
| `signatureFeeLamports` | 5,000 | taker |
| `rentFeeLamports` | 1,488,440 | taker |
| `prioritizationFeeLamports` | 1,450 | taker |

**This corrects task T10.** The plan says the SOL shortfall check adds ATA rent as the constant 2,039,280 lamports. That is the rent-exempt minimum of a 165-byte classic token account and it is not what this quote charges. Read `rentFeeLamports` from the order: it is per quote, it already knows whether the destination account exists, and it names its payer. The constant overstates the requirement by about 0.00055 SOL and drifts the moment account sizes or rent change. The shortfall is the three fields summed, against the wallet's lamports.

The demo wallet holds no Token-2022 account today, so its first buy does pay this rent.

Also on the quote: `router: "metis"`, `swapType: "aggregator"`, `mode: "ultra"`, `gasless: false`, so the taker pays and "no SOL required" is never the pitch. **No `expireAt` field at all**: bounded by `lastValidBlockHeight` and slippage, so the countdown belongs only to quotes that carry `expireAt` and its absence means no expiry, never expired. `slippageBps: 100` with `otherAmountThreshold` 1,346,933 against `outAmount` 1,360,437, so the worst case can be stated honestly beside the estimate. `feeBps: 10` plus a 10 bps platform fee on the input mint. All-in from the dollar values, `1 - outUsdValue / inUsdValue`, was 0.586 percent on the deepest xStock; the same pair measured 0.08 percent and then 1.44 percent fifteen minutes apart on 2026-09-10, so all-in cost belongs on the screen per quote, never in a fixed disclaimer. `requestId` is what `POST /swap/v2/execute` needs beside the signed transaction.

#### The machine behind the sheet (T10, built 2026-09-12)

The flow is a sealed state machine in `ui/swap/SwapState.kt`, with the transitions drawn at the top of that file: `Closed -> Opening -> Amount -> Quoting -> {Shortfall | AwaitingWallet} -> {Landing -> {Landed | Failed} | Signed}`, plus `edit()` back to `Amount` from `Shortfall` and `Failed`, and a cancelled approval back to `Amount` with the typed amount intact. `SwapTiming` measures each phase, so the sheet states the wallet round trip rather than guessing it.

What is decided away from the composition, and belongs to no other layer:

| Rule | Where | What it reads |
|---|---|---|
| The amount | `SwapAmount.parse` | typed text against the USDC balance from `getTokenAccountsByOwner`; BigDecimal only, never a float; refuses empty, non-number, too many decimals, zero or less, over balance, all before any request |
| The SOL check | `SolCost.isCoveredBy` | `signatureFeeLamports + rentFeeLamports + prioritizationFeeLamports` from the order, against `getBalance`; runs after the quote and **before** the wallet opens |
| One requote | `SwapError.requotable` | -1003, -2003, -2004 from `/execute`, once; the retry reaches `AwaitingWallet(requote = true)` because fresh bytes need a fresh approval |
| Sanitized errors | `SwapFailure` | a `@StringRes` per failure; upstream text goes to `SwapDebugLog` and can reach no state |
| The fill | `SwapFill` | `/execute` `outputAmountResult`, not the quote's `outAmount`; `allInCostPaidPct` is the quote's cost corrected by that ratio |
| The flip | `SwapLeg.flipped()` | the same machine with the mints exchanged, offered only when the wallet's token balance is above zero |

#### The sheet itself (T10, DT7, built 2026-09-12)

`ui/swap/SwapSheetModel.kt` turns a `SwapState` and the wall clock into a `SheetContent`; the
composition in `SwapSheet.kt` draws that and computes nothing. Every state of plan section 13
Pass 2 is a unit test over the model, so what the sheet says is checked without a device.

| Slot | What fills it |
|---|---|
| Header | the pair, either way round; the direction action only when `funds.tokenRaw > 0` |
| Field | the amount step only, with the typed text untouched and the balance beneath |
| Phase | the live bar: "Getting quote", "Confirm in Wallet", "Landing", each with its own elapsed seconds; breathing only while a call is in flight |
| Cost block | from `Quoting` onward, three cells carrying the five facts; a skeleton while the order is in flight; one line on the amount step saying the cost is quoted at the tap |
| Notice | one slot, in priority order: no USDC at all, a cancelled approval, an amount problem, a shortfall, a failure |
| Debug band | every state, whenever `SUBMIT_SWAPS` is false |
| Receipt | replaces the whole anatomy on `Landed` |
| Buttons | at most two, and none at all while `/execute` is in flight |

**Three places the canvas was not followed, and why.**

1. **No cost cells on the amount step.** The canvas draws "You receive" and "All-in cost" there.
   `GET /order` shares a 0.5 rps bucket with Price v3 and is spent at the tap, so a preview would
   either be stale by the time it is acted on or would consume the quote the swap itself needs.
   The block says so in words instead of leaving a gap: "The cost is quoted when you tap Swap, not
   before."
2. **No Liquidity cell and no rate line.** Neither has a field in `/order`, and the sheet makes no
   second call. The cell is dropped rather than left blank; Detail already carries the pool size on
   its cost line, where a Price v3 read has been made.
3. **The direction action sits beneath the header, not beside it.** "USDC to TSLAx" at mono 22 plus
   "TSLAx to USDC" at Outfit 14 fits a 400dp frame at a 1.0 font scale and clips at 1.3. Beneath, it
   also gets a clean 48dp target of its own.

Two smaller calls: the receipt's headline sets the numeral at 40sp and the ticker beside it at 20,
because a 14-character pair at 40sp leaves the screen at a 1.3 font scale; and SOL figures keep all
nine decimals where every other token quantity is trimmed to six, since a 6,450 lamport fee trimmed
to six reads "0.000006", which is not what is charged.

**A submission in flight cannot be dismissed.** `close()` cancels the job carrying `POST /execute`,
so the sheet refuses its own dismiss while the state is `Landing`, and offers no button there
either. Every other state dismisses normally.

**The debug build never draws a receipt.** `SUBMIT_SWAPS` is false in debug, the machine stops at
`Signed`, and the sheet's terminal there reads "Signed, not submitted" with the wallet round trip
beside it, over a band that says the same thing. There is no signature, no slot and no fill on that
surface because there is nothing to put in them.

#### Receipts, local (T10) -> read by Portfolio (T11)

The chain carries no cost basis, so the app writes its own record when a swap lands: `data/receipts/`, a serialized JSON file behind `ReceiptStore` (not Room: append-only, read whole, a few hundred rows, and ksp is not worth a module's build budget). Newest first, capped at 200, the signature is the row identity so one landing is one row.

| Field | From |
|---|---|
| `signature` | `/execute` `signature` |
| `inputMint`, `inputSymbol`, `inputDecimals` | the leg being spent |
| `inputAmountRaw` | `/execute` `inputAmountResult`, falling back to the quote's `inAmount` |
| `outputMint`, `outputSymbol`, `outputDecimals` | the leg being received |
| `outputAmountRaw` | `/execute` `outputAmountResult`, never `outAmount` |
| `allInCostPct` | the cost actually paid, the quote's all-in corrected by the fill ratio |
| `route` | `Order.router`, as a name ("Metis") |
| `landedAtMillis`, `slot` | wall clock at the landing; `/execute` `slot` |

### Portfolio (T11)

| Cell | Field |
|---|---|
| Total | sum of quantity × `usdPrice` |
| Row | token account amount × multiplier as quantity; `Fmt.tokenAmount`; value `Fmt.price`; premium from Price v3 |
| Footnote | static "Cost basis is not read from the chain." |
| Recent swaps | local receipts table (T10) |

### Watchlist (T12)

| Cell | Field |
|---|---|
| Row sub | "Reports {date}" from `forward.raw.nextEarningsDate` (or "No report date") + premium |
| Digest panel | last WorkManager digest text, stored locally |

## Known gaps to decide before T9

1. `/api/v1/{TICKER}` has no leaf fundamentals (ROIC, margins, P/E, EV/S, 52-week position). Either drop the fact grid under the tracks or add an additive `facts` block server-side. Recommendation: add `facts` server-side (Week 2 server lane, small) so Detail has the evidence cells the canvas shows.
2. `composite` on mobile: the canvas shows `0.71`; the API gives a percentile `51` for AAPL. Use the integer percentile and update the canvas sample.
3. `axes.valuation.label_en` is "Moderate" where the canvas says "fair"; use `label_en` as delivered.
4. Ten new tickers: `forward` was partly null; closed server-side and live in production on 2026-09-12 (PR #109). Still not rendered in the hackathon build, so this is a decision, not a blocker. See the block below.

## Forward gaps, closed server-side 2026-09-12 (PR #109)

Three additive fields explain, rather than invent, what a 3Y EPS CAGR cannot say about a young or loss-making filer. Verified live on all ten phase-3 tickers after the targeted re-extract:

| Field | Type | What it says |
|---|---|---|
| `epsCagr3yReason` | `negative_eps_base` / `negative_eps_endpoint` / `insufficient_history` / `missing_eps` / null | why `epsCagr3y` is null; null whenever the CAGR is a number |
| `epsTrajectory` | `loss_to_profit` / `profit_to_loss` / `profit` / `loss` / `insufficient_history` / null | the sign pattern over the same 4-FY window, a category and never a number |
| `epsGrowthShort` | `{ pct, yearsSpanned }` or null | labeled short-window growth, filled ONLY where `epsCagr3y` is null; both endpoints positive; never ranked, never persisted to `eps_cagr_3y` |

`sectorEpsCagr3yMedian` is no longer null: it is the same-sector median with self excluded, above a floor of ten rows.

Live values on 2026-09-12: eight of the ten read `negative_eps_base`; MSTR and CRWD are `loss` at both ends so they carry no short window; ABNB reads `missing_eps` (a real EDGAR tag gap worth its own TODO); MU turned out to have a genuine `epsCagr3y` of -0.70 and was never a gap. MSTR alone has `score.missing = true`: its raw now survives without a forward P/E, but the blends are withheld so it does not vote.

Two rendering rules if these ever reach a screen:
- `yearsSpanned` is fractional (2.0014, not 2). Round it through `Fmt` and label the window ("2 y window"), never print the raw double.
- `epsGrowthShort.pct` off a near-zero base is huge (DASH read +635%). It is arithmetically true and rhetorically misleading, so it needs the window label beside it or it should stay off the screen.

Open decision for T9: the payload has no leaf fundamentals (gap 1 above), and these three fields are the only SEC-derived evidence the Detail screen could show under the tracks. Either keep `forward.*` off the hackathon build as decided, or surface one honest line ("Earnings turned positive; 2 y window, no 3 y rate"). Founder's call; the default remains tracks only.
