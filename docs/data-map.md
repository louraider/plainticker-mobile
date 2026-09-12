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

### Detail (T9)

| Cell | Field | Rule |
|---|---|---|
| Hero ticker | catalog `symbol` | 64 mono |
| Company | `company` | 16 Ink 2 |
| Token price | Price v3 `usdPrice` | `Fmt.price` |
| NYSE close | Price v3 `stockData.price` | `Fmt.price`; null -> "Reference price unavailable" caution line, gauge hidden |
| Gauge | `(usdPrice / stockData.price - 1)` | scale 0.5 percent; caption "Token vs NYSE close, scale 0.5%"; value `Fmt.percent`; when market open per catalog `trading` calendar the caption says "Tracking within {abs}% of the NYSE close" |
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
| Fact grid (Against the sector) | not in the v1.1 payload | v1.1 exposes no ROIC, margins, P/E or 52-week facts; the canvas sample cells are placeholders. Decision for T9: render the three tracks and the composite only, OR request an additive `facts` block from the server (leaf metrics with sector medians). Default: tracks only until the server adds facts. |
| F-Score numeral | `fscore.score` | "8" + "of 9 signals" |
| Signal rows | `fscore.signals[9]` | names in this fixed order: Return on assets positive; Operating cash flow positive; Return on assets improving; Cash flow exceeds earnings; Leverage falling; Liquidity improving; No new shares issued; Gross margin improving; Asset turnover improving. null -> "n/a" muted |
| Method body | `method.statement_en` | 15 Ink 2 |
| Method sources | static string | "Filings from SEC EDGAR XBRL. Prices from Jupiter. Reference from the NYSE close." |
| Analysis age | `as_of` | header meta "Analysis from {Fmt.ageOld}" only when > 24 h |
| Not served | detail 404 `unsupported_ticker` or `not_available` | trust layer still renders from chain + xStocks; fundamentals replaced by one row "Analysis not yet available" |
| forward.* | all fields | not rendered in the hackathon build (design decision); may feed a later "Expectations" section |

### Swap sheet (T10)

| Cell | Field |
|---|---|
| Header | "USDC to {symbol}"; flip only when the wallet holds the token |
| Amount | user input, `Fmt.tokenAmount` for the balance line from USDC token account |
| You receive | `/order` `outAmount` / 10^decimals, `Fmt.tokenAmount` |
| Rate line | `usdPrice` and `stockData.price` |
| All-in cost | `Order.allInCostPct` (fee bps + platform fee bps + price impact) |
| Route | `Order.router` ("metis" -> "Metis") |
| Liquidity | not in `/order`; use Price v3 liquidity if exposed, else omit the cell |
| Countdown | `Order.secondsLeft` when `hasExpiry` (RFQ only) |
| Receipt | `/execute` signature, slot, `outAmount` actual; "quote {x}% · fill {y}%" from quoted vs actual |

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
