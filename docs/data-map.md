# Data map for the Week 2 screens

Which field feeds which cell, from the live sources as of 2026-09-11. Screens and cells are named as in `DESIGN.md` and `design/canvas/instrument.py`. Every number goes through `Fmt`. Kotlin models: `com.plainticker.mobile.data.plainticker.PlainTickerModels`, `data.xstocks.XStocksModels`, `data.jupiter.JupiterModels`, `data.rpc.RpcModels`.

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
| Today strip | Watchlist store + the nearest report the last daily check stored (`DigestRecord.nextReport`) | hidden when nothing is watched; "Today: 3 watched, next report TSLAx on Oct 22", and the count alone until a check has run or when the named ticker is no longer watched |
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

This matters more than its size suggests: the list is the first thing a judge opens, and the demo video opens on it. Three fixes, all client side and none needing an API key: paint the bundled snapshot immediately and let the network refresh over it, which T8 already built for the outage path and which turns the wait into nothing; render after page 0 and keep paging in the background, so rows appear at about a second; and cache the catalog on disk so the second launch pays nothing. A slim server-side catalog carrying only symbol, underlying, mint, name and the trading block would cut 4.31 MB to tens of kilobytes, but that is PlainTicker server work and the client fixes land sooner. **All three client fixes are in; the section below is the measurement after them.**

#### Two point seven seconds to first content, measured on the Seeker 2026-09-13

The three client fixes above are in. Measured on the same phone, the same evening, same Wi-Fi.

**Method.** Debug build installed over adb, `am force-stop`, `am start -n com.plainticker.mobile/.MainActivity`,
and the launch recorded with `adb shell screenrecord` at the display's own rate. The recording is
anchored to the system's own launch measurement: `ActivityTaskManager: Displayed` gives the
milliseconds from the launch request to the activity's first frame, and that frame is found in the
recording, so every later frame can be stated relative to the launch. "First row" is the first
frame in which row text is drawn in the list band. "Settled" is the first frame identical to the
final one, which is the frame where the snapshot banner has gone and nothing further changes.
Three runs each. The earlier `uiautomator dump` poll was run too, as the like-for-like check
against the 2026-09-12 numbers, but one dump costs about 2.3 s on this phone, so it can only say
"at or under" once the answer is small: it reported 13.3, 13.5 and 13.7 s before and 3.1, 3.7 and
3.8 s after, with rows already present in the very first sample of every run after.

| | skeletons | first row | settled |
|---|---|---|---|
| before | 2.31 s | **11.7 s** (10.8 to 12.5) | 11.8 s |
| after, first ever launch, nothing cached | 2.35 s | **2.75 s** (2.70 to 2.76) | 12.4 s |
| after, every later launch | 2.37 s | **2.78 s** (2.74 to 2.78) | **3.5 s** |

**Read it as two numbers, not one.** Of the 2.75 s, 2.35 s is process start, the splash and the
first composition, and that number is the same in both builds (2.31 against 2.35): the splash has
no `setKeepOnScreenCondition`, so it leaves on the first Compose frame and no data is waiting
behind it. The part this work owns is the gap between the first Compose frame and the first row,
and that went from about 9.4 s to about 0.40 s. The 0.40 s is reading and parsing the two bundled
assets and joining 179 rows against 832 assets. Getting under 2.35 s to first content is a startup
question, not a data one, and it is not what was fixed here.

The spread is the other half of the result. Before, first row ranged over 1.6 s across three runs
because it was a 4.31 MB download; after, it ranges over 66 ms, because the first paint touches no
network at all.

**The second launch.** The catalog is written to `cacheDir/xstocks-catalog.json`, 1.12 MB, trimmed
to each asset's Solana deployment from the 4.31 MB the API serves across ten networks. A launch
inside the 24 h window reads it and asks the network nothing: the file's timestamp is unchanged
after a launch, and the list settles at 3.5 s rather than 12.4 s, which is less time than the eight
catalog pages take to arrive.

**What it costs.** On the first ever launch the list now settles about 0.6 s later than it used to
(12.4 s against 11.8 s): that launch does everything it used to and also parses the snapshot and
writes 1.12 MB to disk. It buys a usable list 9 s sooner on that launch and a settled one 9 s
sooner on every launch after it, so it is the right trade, but it is a real number and it is here.

**What the reader sees.** At 2.75 s the rows carry their analysis and no premium, because the
snapshot carries no price, and the one banner slot reads "List from a bundled snapshot of 12 Sep
2026, refreshing now". Live prices arrive and fill the meta lines in place. The banner goes when
nothing on screen comes from the snapshot any more, which is when `/summary` has answered and the
catalog is whole. The frame before the rows shows skeletons and the next frame shows rows: the
replacement is a data swap and nothing about it is animated, so no row ever moves under a thumb.

The server-side slim catalog is still the real fix for the 4.31 MB and is still PlainTicker server
work. It would take the first ever launch's settle from 12.4 s down with it; it is no longer on the
path to first content.

#### What a refresh does, and what it may not do (2026-09-13)

The three caches that make the first paint fast each had a cost the review named, and each is paid
here rather than by the reader.

**The catalog fetch no longer runs inside the shared lock.** `CachedCatalogRepository` took its
mutex for the whole of `catalog()`, and `multiplierRecord` and `proofOfReserves` take the same
lock, so a Detail screen opened while the catalog was being fetched waited behind seven seconds of
paging for two small requests that were not related to it. The lock is now taken for the cache
reads and the cache writes only, which is the shape `CachedPriceRepository` settled on first. What
the lock also used to prevent was two screens fetching the same 4.31 MB; a ticket does that
instead, and unlike the lock it spans both entry points, so a Detail screen opening during the
List's paging run joins that run rather than starting a second one. The ticket is handed back
under `NonCancellable`, so a caller that walked away never strands the screens waiting on it. The
per-symbol reads carry no ticket: two screens asking for the same multiplier at the same instant
cost two small requests, which is the trade the price layer already made.

**A refresh keeps what is on screen until something better arrives.** `refresh()` used to forget
every source and republish the bundled snapshot, so a retry walked a live list back to the capture
of an older day, took away any token listed since it, and grew back as the pages landed. A run now
forgets only what it is about to ask again (whether each source has settled, and which mints have
been priced). A source that fails leaves the last good answer alone; a catalog that is still paging
lands on top of the whole one keyed by token symbol, and whole is sticky, so page zero never
replaces a catalog the reader has already been shown; and a retry over a drawn list does not set
`isLoading`, because skeletons over a correct list is the same walk backwards in another shape.

**The snapshot line waits 1.5 s before it may be drawn.** On a warm launch the review measured it
up at 3.53 s and gone at 4.20 s: 0.70 s in which the whole list moved down by the height of a
banner and back. The line is not a lie and it is not removed. It is now held back by
`ListViewModel.SNAPSHOT_BANNER_GRACE_MS`, and a refresh that settles inside that grace never draws
it at all. 1.5 s is twice the 0.72 s the whole warm window takes (first row 2.78 s, settled 3.5 s),
so nothing that behaves like a warm launch can reach it; the first ever launch settles at 12.4 s,
so the honest case still carries the line for about eleven seconds. During the grace the slot stays
empty rather than falling through to a lower tier, which would only be a different sentence
arriving and leaving inside the same second.

**An explicit action reaches the network.** Both catalog paths answered from the file for its whole
24 h window, `refresh()` among them, so a token listed this morning could not be seen until
tomorrow whatever the reader did. `catalogUpdates(userAsked = true)` steps over both caches and
pages the network; the file still paints first, so the list never goes back to skeletons for it.
`ListViewModel.refresh()` is the only caller that passes it, and the `init` load is not, so the
second launch of the day still pays nothing. The Retry on every banner reaches it, and so does one
new text action: a search that misses now carries "Check for new tokens" beside its sentence, which
is the only affordance a settled live list has, and the reader who searched for a ticker and found
nothing is exactly the reader who wants it.

#### Rows moving under the reader: what was fixed and what was left (2026-09-13)

The review flagged two ways a row can move while the catalog is arriving. One was real and is
fixed; the other is handled by the list itself, and is written down here so the next reader of this
file does not go looking for it again.

**Fixed: a ticker drawn, taken away, then re-added.** The one path that did this was the retry
above, and it is gone with it. On the first load the flicker does not happen and could not: only a
whole catalog may be used to decide that a ticker has no xStock, the bundled snapshot is one, so a
ticker listed after the capture is *withheld* until its page lands rather than drawn and dropped.
That is a row appearing once, which is the behaviour this design chose deliberately over drawing
every classified company and then removing the ones with no token (the BKNG bug of 2026-09-12).
`ListViewModelTest` carries both: the retry case, which failed before the fix, and a guard over the
first load that asserts no symbol ever leaves the list once it is on it.

**Left alone: alphabetical insertion into "Without analysis".** The section is sorted by symbol, so
a page that lands with a symbol earlier in the alphabet does insert above rows already drawn. It
does not move them under the reader, because `LazyColumn` is keyed (`"a:" + ticker` and
`"p:" + ticker`, `ListScreen.kt`) and Compose re-anchors the first visible item by its key on every
measure: `LazyListScrollPosition.updateScrollPositionIfTheFirstItemWasMoved`, called from
`LazyList.kt`'s measure policy in foundation 1.12.0, which this build uses, keeps the item first
"even given that its index has been changed". Rows inserted above the viewport therefore change
indices and not pixels. It is also the smaller case than it looks: the snapshot and the file both
carry very nearly the live catalog (832 assets against 832), so the only symbols a page inserts are
the ones listed since the capture. The fix that would remove it altogether is to publish the
section only from a whole catalog, and that would cost the first paint of a build whose snapshot is
missing the whole 7.5 s of paging, which is a worse trade than the thing it buys.

#### Walked on the Seeker after the four fixes, 2026-09-13

Same phone, same evening, same Wi-Fi as the measurements above. Debug build over adb, `am
force-stop` between runs, the catalog file removed with `run-as` for the cold ones.

**The banner does not flash any more, and it still appears when it should.** The line occupies a
band the rest of the screen sits under: with it, the "Analyzed" heading is at y 1031; without it,
y 905. That 126 px, 42dp, is the jump. Two runs:

| run | how it was read | the snapshot line |
|---|---|---|
| warm, catalog on disk | 14.4 s of `screenrecord` resampled to 5 fps, 73 frames, counting lit pixels in the band the line occupies (x 400 to 1140, y 600 to 665) | **never drawn, 0 in every frame** |
| cold, catalog file removed | 14 `screencap` samples from `am start`, the same band | dark at 2378 ms, lit from 2776 ms |

`Displayed` for those launches was +1s159ms and +1s173ms, so the first Compose frame, which is
where the load starts, is about 1.17 s after the request. The cold run therefore put the line up
between +1.21 s and +1.61 s after the load started, which brackets the 1.5 s grace, and it stayed
up until the catalog was whole. The warm run settles inside the grace and draws nothing.

**A reader who asks reaches the network inside the 24 h window.** Searching "zzzz" draws
`No xStock matches 'zzzz'` at [60,977][684,1046] with `Check for new tokens` at [732,982][1140,1042],
one line, inside the 20dp gutters. Tapping it rewrote `cacheDir/xstocks-catalog.json` (03:25:05 to
03:25:28) with the file still hours inside its window, which is the whole point: before this, a
token listed today was unreachable until tomorrow.

**The retry does not walk the list back.** With that refresh out, at +5 s, the list is the same
list: same first rows (NEMx, Newmont Corp., "Analysis 6 d old", 84, strong, NVDAx), and no snapshot
banner anywhere in the dump. Before the fix the same tap republished the bundled snapshot.

**The catalog is fetched once for two screens.** Received bytes for the app's uid, read from
`dumpsys netstats` with a forced poll on either side of each run:

| cold run | on the wire |
|---|---|
| List alone | 0.34 MB |
| List, plus a Detail opened at +3 s while the catalog was still paging | 0.346 MB |

The 6 KB difference is the Detail's own calls (mint, multiplier, reserves, quote). The Detail did
open and did load (NEMx, live bar ticking on a fresh slot). Worth writing down while the numbers
are here: the catalog is 4.31 MB of JSON but about **0.34 MB on the wire**, because it is gzipped
and it repeats itself. The size that hurts on a phone is the parse, not the transfer.

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

#### The floor re-measured against independent sources, 2026-09-13

The founder asked how liquidity is checked, on the reasonable ground that the whole world trades
these names and "no liquidity" sounds wrong. Checked `liquidity` from Price v3 against DexScreener
(sum of every indexed pair) and GeckoTerminal (`total_reserve_in_usd`), then against the only
measure that settles it, a real order.

**Jupiter's `liquidity` is not the pool.** It runs 2x to 5x under the sum of indexed pairs, and the
ratio is not constant, so it cannot be scaled into one:

| | Price v3 `liquidity` | DexScreener, all pairs | ratio |
|---|---|---|---|
| SPYx | $4,275,435 | $7,954,274 | 0.54 |
| TSLAx | $1,343,499 | $2,908,641 | 0.46 |
| COINx | $350,489 | $1,283,821 | 0.27 |
| KOx | $99,672 | $274,189 | 0.36 |
| INTCx | $26,846 | $88,450 | 0.30 |
| ORCLx | $4,450 | $21,494 | 0.21 |

So `detail_gauge_thin` and `list_row_meta_thin`, which say "Pool holds $34", state something the
field does not mean. The number is a routable depth of some kind, not the pool, and the copy claims
the pool.

**The thin tail is real anyway, and worse than the figure suggested.** The 24 analyzed tokens Price
v3 puts under $1k have zero indexed pairs on DexScreener and zero 24-hour volume, and GeckoTerminal
agrees ($0 for CRWDx, $711 for APPx). Their premiums are noise off nothing, which is what the floor
was built to withhold. A $100 order proves it outright: UBERx quotes at **-64.2% price impact**,
APPx at **-49.96%**. Metis will not route either at all (`NO_ROUTES_FOUND`, `TOKEN_NOT_TRADABLE`);
only Manifest answers, at those prices.

**Three tokens are misclassified by the current rule.** ORCLx, NFLXx and PEPx are `Thin` on the
Price v3 figure and above the floor on every other source. All three quote on Raydium CLMM at $100
with small impact (NFLXx -0.141%, PEPx -0.471%, ORCLx -1.555%), which is a working market the app
currently declines to describe.

**The measure that survives all three checks is price impact, not depth.** It is what the person
actually pays, it comes back on the order the swap sheet already fetches, and it separates the
cases the depth figure blurs: AMDx sits at $13,399 by Price v3 and moves **2.35% on a $1,000 order**,
while TSLAx moves 0.08% on the same size. One number, second person, no jargon.

#### Swap v2 refuses tradable tokens, found 2026-09-13

`GET /swap/v2/order` with no router argument returns **400 `"Quote not available from market maker"`**
for NFLXx, ORCLx, PEPx, UBERx and APPx at every size down to $100. The endpoint asks the RFQ router
and fails hard rather than falling back to the aggregator. Adding `excludeRouters=jupiterz` to the
same request returns a route immediately, on Raydium CLMM, at the impacts above.

This is a shipped defect, not an API quirk: the swap sheet reads `/swap/v2/order`, so the app tells
the holder no quote exists for a token that trades fine. It hits every xStock no market maker
covers, which on this sample is at least five of the twelve that quote at all.

The fix is a fallback, not a flag. RFQ is worth asking first (TSLAx at $1,000 routes through
JupiterZ at a price improvement, -0.091% all-in), so the order call keeps its default, and on
`"Quote not available from market maker"` retries once with `excludeRouters=jupiterz`. A token that
fails both is genuinely unbuyable and the sheet says so.


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
| You receive | `/order` `outAmount` / 10^decimals, `Fmt.tokenAmount`; sub line is `otherAmountThreshold` as the worst case, or the estimate less `slippageBps` when the order names no threshold |
| All-in cost | `Order.allInCostPct` (fee bps + platform fee bps + price impact), or the missing value when the order priced neither side in dollars; sub line is the route |
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

#### What the review changed, 2026-09-13 (T10)

A cross-model review of the branch found six places where the screen could have said something
it did not know. All six are fixed and each has a test that names the transition.

| What it said | What it says now |
|---|---|
| An order-stage 502 or 429 read "This pair cannot be quoted at this size right now" | A non-2xx with no structured body is `QUOTE_UNAVAILABLE`; only a structured refusal is a verdict on the pair |
| `POST /execute` answered `Success` with no signature: "The swap did not land. Nothing was swapped." | `SUBMIT_UNAVAILABLE`, which claims neither. The answer went missing, not the swap |
| No `outputAmountResult`: the receipt drew the quote's `outAmount` as the fill | The fill is null and the receipt draws the missing value. `SwapFill.outAmountRaw`, `SwapReceipt.outputAmountRaw` and the paid cost are nullable, because an estimate wearing a receipt's label is the one lie this screen cannot tell |
| No `otherAmountThreshold`: the worst case borrowed the estimate, so the sheet promised "at least {the estimate}" | The floor is the estimate less the order's own `slippageBps`, which is how an exact-in threshold is computed |
| No `inUsdValue`: the all-in cost cell read "0.00%", a free swap | `allInCostPct` is null and the cell draws the missing value |
| The base64 transaction was decoded inside the wallet round-trip, so our own unreadable bytes came back as "The wallet did not return a signature" | Decoded before the wallet opens; an undecodable payload is `NO_TRANSACTION` and costs no approval |

And one state-machine hole. A modal bottom sheet hides **first** and calls `onDismissRequest`
afterwards, so refusing the request was not enough: a swipe or a back press during `POST /execute`
left the sheet hidden with the machine still in `Landing`, and `open()` refuses to reopen a busy
machine, so the receipt for a swap that did land could never be reached. The drag itself is now
refused, through `confirmValueChange` on the sheet state, and only while landing.

#### On hardware, 2026-09-13 (T10, DT7)

The Seeker enumerated for the first time since the sheet was written, and the debug build was
installed and walked. What was seen: Detail live from the chain, the Swap button with its mono
pool line, the MWA `Connect` sheet in Seed Vault Wallet with the identity verified against
`assetlinks.json`, and then the sheet itself, composed on the device at 1200x2670 / 480 dpi.
It drew the debug band from the first frame, the mono title, the amount field with Max, the
balance line, "No USDC in this wallet", "The cost is quoted when you tap Swap, not before", the
disabled primary and the footnote. No direction action, correctly: that wallet holds no TSLAx.
Typing an amount against a zero balance kept the button disabled and made no request. A
swipe-down dismissed the sheet cleanly and Detail came back whole.

**Still not exercised on hardware:** the signing leg. The connected wallet holds no USDC, so the
machine cannot leave the amount step, and nothing was funded to make it. `Quoting`, the SOL
check, `AwaitingWallet`, the wallet round-trip timing, the requote and the receipt remain covered
by unit tests only. No transaction was signed and no money moved.

*Superseded later the same day:* the signing leg was exercised for real against the release build,
and it found two things. See "What the first real swap taught us, 2026-09-13" at the end of this
file.

#### Receipts, local (T10) -> read by Portfolio (T11)

The chain carries no cost basis, so the app writes its own record when a swap lands: `data/receipts/`, a serialized JSON file behind `ReceiptStore` (not Room: append-only, read whole, a few hundred rows, and ksp is not worth a module's build budget). Newest first, capped at 200, the signature is the row identity so one landing is one row.

| Field | From |
|---|---|
| `signature` | `/execute` `signature` |
| `inputMint`, `inputSymbol`, `inputDecimals` | the leg being spent |
| `inputAmountRaw` | `/execute` `inputAmountResult`, falling back to the quote's `inAmount` |
| `outputMint`, `outputSymbol`, `outputDecimals` | the leg being received |
| `outputAmountRaw` | `/execute` `outputAmountResult`, never `outAmount`; null when the answer reported none |
| `allInCostPct` | the cost actually paid, the quote's all-in corrected by the fill ratio; null when the fill or the order's dollar values are missing |
| `route` | `Order.router`, as a name ("Metis") |
| `landedAtMillis`, `slot` | wall clock at the landing; `/execute` `slot` |

### Portfolio (T11)

| Cell | Field | Rule |
|---|---|---|
| Total | sum of the positions that carry a value | `Fmt.price`; null when none of them do, and the slot then takes the neutral "-" rather than a zero |
| Total sub | how many positions the total covers | all of them "3 xStocks, priced by Jupiter"; some "1 of 3 xStocks priced by Jupiter, the total covers what is priced"; none "3 xStocks, none of them priced by Jupiter" |
| Row ticker | catalog `symbol` | mono 18 |
| Row company | catalog `name` | 13 Ink 2 |
| Row quantity | token account `amount` over the **mint's** `decimals`, times the mint's `scaledUiAmountConfig.multiplier` | `Fmt.tokenAmount`; the first half of the meta line ("2.01364 TSLAx") |
| Row tracking | `TrackingQuality.of(entry)`, the list's own rule and the list's own strings | the signed premium above the floor, "Pool holds $34, too thin to track" below it, "Pool depth not reported" with no depth; absent when Jupiter did not price the token |
| Row value | quantity × Price v3 `usdPrice` | `Fmt.price`; absent for a position with no quantity or no price |
| Footnote | static "Cost basis is not read from the chain." | |
| Recent swaps | local receipts table (T10) | paid "5 USDC", received "to 0.01364 TSLAx" (mono: it is an amount and a ticker), meta "all-in cost 0.09% · 10 Sep 2026 12:57 UTC" |
| Swaps footnote | static "The record this app kept of the swaps it made from this device." | |

**The mint decides the quantity, and an unread mint decides nothing.** The multiplier comes from
the Token-2022 `scaledUiAmountConfig` on the mint account, through `MintRepository`, and so do the
decimals. A mint that carries no such extension is a multiplier of one, which is a fact read off
the chain. A mint that could not be read is neither: the row says "Mint not read, quantity
unknown", shows no value, is left out of the total and withholds its tracking half as well, since
a premium printed beside a missing quantity invites a reader to multiply two numbers, one of which
this app does not have. Falling back to a multiplier of one there would be a wrong share count
that reads as a measured one, and two tests fail the moment it is put back.

**The multiplier in force, not the one the mint has stored (review, 2026-09-13).** Token-2022 keeps
a scheduled change beside the old value and switches at its timestamp by itself:
`ScaledUiAmountConfig::total_multiplier` answers `newMultiplier` from
`newMultiplierEffectiveTimestamp` onwards, and the stored `multiplier` field is only rewritten the
next time the authority updates the extension. Between a split taking effect and that next write,
the stored value is last week's number. `SplitMultiplier.effectiveAt(nowMillis)` is therefore what
scales a balance, asked at the clock of the mint read itself, and `current` stays what the mint has
stored. The Detail screen still draws `current` as today's multiplier, which is the same gap on a
fact line rather than in a share count; it is a small fix and it is not in this branch.

**One row per mint, not per token account (review, 2026-09-13).** `getTokenAccountsByOwner` answers
one entry per token *account*, and one owner may keep several for the same mint (the associated
account plus any auxiliary one). The balances are folded by mint and their raw amounts summed
before anything is drawn. Without the fold one holding was drawn as two rows each carrying part of
the quantity, and since the rows are keyed by mint the second one threw the `LazyColumn` duplicate
key rather than merely reading oddly: the same crash the list found in a duplicated ticker.

**One `getAccountInfo(mint, jsonParsed)` per held mint, in order.** `getMultipleAccounts` would
read them all in one call and is not on the forwarder's list (see Sources above), so batching is a
server change rather than a client one. A wallet holds a handful of xStocks and the forwarder
caches each mint 60 s server-side, so a Detail screen opened afterwards pays nothing for the same
read.

**States.** Not connected (one sentence and "Connect wallet"), connecting, connected and empty
("No xStocks in this wallet yet. A swap from any stock page puts the token here." with the way
back to the list), the chain out, the catalog out, prices wholly or partly missing, and a refresh.
The banner order is what could not be read at all, then what is partly missing, then the note from
the last wallet round-trip; a wallet note carries no Retry because the connect action beside it is
the answer to it. A load that fails leaves the drawn rows where they are and raises a banner: the
"never walk backwards" rule the list learned the same week. An empty wallet and a chain that could
not be read are different states and never share a sentence. A banner names the source that is
out now and not the one that was out last time: a run whose chain read answered clears the chain
sentence even when the catalog is what failed after it.

**No cost basis, and nothing derived from one.** There is no profit, no loss and no change-since
figure in the state, the model or the composition, and `PortfolioScreenTest` fails the build if one
appears. The chain does not carry a cost basis and the footnote says so rather than leaving a
reader to assume the app forgot.

#### Portfolio on the Seeker, 2026-09-13

Walked on SM02E4072810430 (Android 16, 1200x2670 at 480 dpi, nothing overridden) against a debug
build with `SUBMIT_SWAPS` false. Nothing was signed and no money moved.

**The wallet the device can authorize holds no xStock.** Seed Vault offers two entries and both
authorize the same key, the founder's, and that wallet holds no xStock and no USDC (the swap sheet
said as much on 2026-09-12). The public demo wallet is not in this device's Seed Vault, so the
holdings rows, the total and the tracking sentences are **not yet verified on hardware**; they are
covered by `PortfolioModelTest`, `PortfolioViewModelTest` and the four previews at 360, 412 and
412 at 1.3x. What the walk did prove:

| What | Evidence |
|---|---|
| Not connected | "Connect your wallet to see the xStocks in it." at [60,869][808,1007] with "Connect wallet" at [856,908][1140,968], inside the 20 dp gutters |
| The MWA round trip | the Seed Vault sheet with the identity verified against `assetlinks.json`, then the wallet fragment in the Holdings heading at [933,679][1140,733] |
| Connected and empty | "No xStocks in this wallet yet. A swap from any stock page puts the token here." with "Browse analyzed stocks", which switches the host to the List tab |
| The chain is really read | with the network up, a refresh raises no banner, which only happens when `tokenBalances` and the catalog both answered |
| The chain out | with the radios off, Refresh draws "On-chain data unavailable" with Retry (hit box [988,569][1140,713], 144 px = 48 dp), the empty sentence correctly disappears, and the receipts stay |
| Retry | with the network back, one tap clears the banner and the empty state returns |
| Receipts with no chain read | both rows drew while the wallet was disconnected and again while the chain was out |
| One spoken sentence per row | `content-desc="5 USDC, to 0.01364 TSLAx, all-in cost 0.09 percent, 10 Sep 2026 12:57 UTC"`, so the numerals are spoken and the cells are not read out one by one |
| An unreported fill | `content-desc="20 USDC, to NVDAx, amount not reported, all-in cost not reported, 3 Sep 2026 12:57 UTC"` |
| Targets | Refresh [682,1166][879,1310] and Disconnect [879,1166][1140,1310], both 144 px tall, which is 48 dp |

The two receipt rows were a **synthetic fixture** written into `files/swap-receipts.json` with
`run-as` and deleted afterwards: no swap had landed on this device yet, the signing leg was still
unexercised, and the only honest way to see the section on hardware was to seed the record the app
would have written. The signatures in it were obviously fake and nothing was sent anywhere.

*Superseded later the same day:* a real swap landed and wrote a real row, and the total above it
read `1 xStocks, priced by Jupiter`. See "What the first real swap taught us, 2026-09-13" at the
end of this file.

The walk found one real defect: the received amount and its ticker were drawn in Outfit, because
the row's second cell is the company slot and the canvas helper draws it in the UI face. DESIGN.md
section 3 gives every numeral and every ticker to JetBrains Mono, so `ListRow` gained `companyMono`
and the swap row passes it; the cell went from 585 px to 650 px wide on the device, which is how
the fix was confirmed.

### Watchlist (T12), as built

| Cell | Field | Rule |
|---|---|---|
| Row ticker | catalog `symbol`, else the underlying ticker | mono 18; the row opens Detail |
| Row company | `/summary.company`, else payload `company`, else catalog `name` | 13, ellipsis |
| Row meta, first half | `Fmt.monthDay(forward.raw.nextEarningsDate)` as "Reports Oct 28" | "No report date" when the payload carries none |
| Row meta, first half, dropped ticker | "Not in the analysis list" | when `/summary` no longer carries the ticker; no analysis is then fetched for it |
| Row meta, second half | `TrackingQuality` exactly as the List row: premium, or the pool sentence below $10k, or "Pool depth not reported" | the two halves join with `list_row_meta_join`, one middle dot |
| Trailing action | "Unwatch", 48dp, its own target; no swipe | the row itself opens Detail |
| Empty | "Nothing watched yet. Watch a stock from its page to see its next report date here, and get one digest a day." + "Browse analyzed stocks" | the common first state |
| Digest panel | `DigestRecord.text` as stored, with `Fmt.utc(producedAtMillis)` above it | "No digest yet. The first one lands about twelve hours after you watch a stock." when there is none |
| Digest footer | "Notifications on, one check a day." / "Notifications off, the digest stays on this screen." + "Enable" | plus "Checked 3 h ago." once a check has run |

**The digest (`com.plainticker.mobile.watchlist.digest`) is a pure function of its inputs.** Three clauses in a
fixed order: the count watched, then up to two premiums that moved half a percentage point or more
against the NYSE close since the previous check, largest first, then the nearest report ahead. Every
list it builds is sorted by something the input carries, so the same rows on the same day produce
the same string. The copy is in strings.xml (`digest_*`) and resolved through one seam, so the
notification and the Panel cannot word it differently.

Half a point is the threshold because DESIGN.md section 1.1 measured every pool at or above $100k
tracking within 0.8 percent: a token can sit anywhere in that band on any day without anything
happening to it. A premium the liquidity floor withheld is not a baseline either, so a dead pool
can never "move".

**What the check does with what it found**

| Outcome | Stored | Notification |
|---|---|---|
| News, and not yesterday's | text, `producedAtMillis`, premiums, next report | posted |
| Identical to the last digest | premiums, next report, `lastCheckedAtMillis` | none: the reader has already been told |
| Nothing worth a sentence | the same, and the old digest is left alone | none |
| `/summary` did not answer | nothing at all | none; the worker asks for a retry |

**The schedule.** One unique periodic `WatchlistWorker`, network constrained, enqueued with KEEP
the moment the watchlist stops being empty, initial delay twelve hours, period one day; cancelled
when the last ticker comes off. Watching a second stock therefore does not push the first digest
another half day out. `POST_NOTIFICATIONS` is requested on the tap that puts the first ticker on an
empty watchlist, once ever (`notifications_asked` in the same preferences), and a refusal leaves the
digest on the screen with an Enable action beside it.

#### On the Seeker, 2026-09-13

Walked on SM02E4072810430 (Android 16, 1200x2670 at 480 dpi, nothing overridden), a clean install
of the debug build, against production. No money moved and nothing was signed.

| What | Evidence |
|---|---|
| Not asked at launch | after install and after onboarding, `POST_NOTIFICATIONS: granted=false` with no dialog in the dump |
| Asked at the first watch | the tap on "Watch" in the NVDAx header raised `com.android.permissioncontroller` with "Allow PlainTicker to send you notifications?"; the action then read "Watching" |
| The twelve hours are real | `dumpsys jobscheduler`: "Minimum latency: +11h59m59s985ms", "Network type: ... INTERNET", required constraints TIMING_DELAY and CONNECTIVITY |
| The row | "NVDAx  NVIDIA Corporation" with "Reports Nov 17 · -0.19% vs NYSE close" and "Unwatch" in a 144 px box, which is 48 dp |
| One spoken sentence | `content-desc="NVDAx, NVIDIA Corporation, Reports Nov 17, minus 0.19 percent vs NYSE close"` |
| Before any check | the Panel read "No digest yet. The first one lands about twelve hours after you watch a stock." and there was no "Checked" line |
| The check, fired by hand | the debug action enqueued the worker; the notification read **title "Daily digest", text "1 watched. NVDAx reports in 65 days."**, channel `watchlist-digest`, BigTextStyle, small icon `ic_stat_plainticker` |
| The Panel says what was sent | "13 Sep 2026 02:16 UTC" over "1 watched. NVDAx reports in 65 days.", then "Notifications on, one check a day." and "Checked 0 s ago." |
| The same digest twice is silent | a second run left the notification's `when=1789265818824` untouched and the Panel's produced time at 02:16, and only "Checked" moved |
| The notification opens what it named | tapping it in the shade resumed `com.plainticker.mobile/.MainActivity` on the Watchlist tab with the row and the Panel on screen |
| A refused permission degrades | with the permission revoked the footer read "Notifications off, the digest stays on this screen." with "Enable"; watching AAPLx raised **no** second dialog; the check then produced a new digest ("2 watched. AAPLx reports in 45 days.", 02:20 UTC) and posted nothing |
| The Today strip | "Today: 1 watched, next report AAPLx on Oct 28" on the List, and the count alone when the named ticker is no longer watched |
| Unwatching the last ticker | the empty sentence with "Browse analyzed stocks" returned, and the WorkManager job was gone from `dumpsys jobscheduler` |

Restored afterwards: nothing watched, no notification pending, `POST_NOTIFICATIONS` revoked (its
state before the walk), and no file of this walk left on /sdcard.

**Not seen on hardware:** a premium that actually moved half a point between two runs, since both
runs were minutes apart on a closed market, so the "moved from x to y" clause is covered by
`DigestTest` and `WatchlistCheckTest` only; and a real overnight run of the periodic job, which the
job scheduler dump shows scheduled but which nothing can observe inside one session.

#### What the cross-model review changed (`fix(T12): review findings`)

| What | Why it mattered |
|---|---|
| `WatchlistNotifications.enabled()` reads the channel as well as the permission and the app-wide switch | A reader who turns off the "Watchlist" category alone leaves both of the other two on, so the footer went on reading "Notifications on, one check a day." beside a digest that could never arrive, and hid the one Enable action that undoes it. Walked on the Seeker: with `POST_NOTIFICATIONS` still granted and only the channel blocked (`mImportance=0`), the footer now reads "Notifications off, the digest stays on this screen." with Enable |
| `WatchlistWorker.decide` is the worker's whole decision, and `WatchlistWorkerTest` covers it | The shell was the one part of the check with no test at all: a `doWork` that answered `success` to a crash would cost the reader a day in silence, and nothing would have failed |
| `AppNavHost` no longer re-navigates on the notification's own cold start | The guard compared a filled route ("home?tab=2") against the pattern a destination is registered under ("home?tab={tab}"), so it never matched: the tab the graph had just opened was popped and built again, and every call its screens make was paid for twice |

Three findings from the outside model were checked and not taken. Dropping `cancel()` from the
schedule would leave a daily job running against an empty watchlist forever. `WatchedTicker.premiumPct`
cannot leak a sub-floor premium, because `TrackingQuality.Thin.premiumPct` is null by construction.
`Fmt.MONTHS` is the app's date format rather than copy, is unchanged by this task, and the copy lint
passes over it.

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

## The device smoke walk (T13, DT9), measured 2026-09-13

`scripts/device-smoke.sh` walks the app on SM02E4072810430 (Android 16, 1200x2670 at 480 dpi,
nothing overridden) and asserts rather than screenshots. Two clean runs the same evening: **66
assertions over 24 dumps, 225 s and 223 s**, exit 0 both times. The numbers the script's own
constants are cut from, all read on this phone:

| What | Measured | What it decides |
|---|---|---|
| one `uiautomator dump` over `exec-out` | 2.2 to 2.6 s | one dump per assertion point, never a dump in a loop |
| one `dumpsys window` focus read | about 0.1 s | how the walk waits for the wallet, the permission dialog and the way back |
| one `dumpsys gfxinfo` read | about 0.1 s | how the walk waits for the list to stop changing, and how it measures motion |
| `am start -W` TotalTime, cold after `pm clear` | 1131, 1142, 1155, 1160, 1165, 1233 ms | ceiling 2750 ms, the whole measured time to first content, of which TotalTime is only the part before the first frame |
| frames rendered over 2 s on Detail, live bar on screen | 484 to 488 | breathing needs at least 100 |
| the same, with `animator_duration_scale 0` | 4 | static allows at most 20 |
| tallest numeral at font scale 1.3 against itself at 1.0 | 119% | a wrap would be about 260%; the bound is 145% of the scale |

**Three things the walk found that are worth keeping.**

1. **The snapshot banner moves the screen under a tap.** The banner occupies a 126 px band, so a
   dump taken while it is still up gives coordinates that are wrong by 42 dp the moment the catalog
   is whole. The walk now waits for the list to stop rendering (`dumpsys gfxinfo` reporting no
   frames for a second) before it reads coordinates, and `open_detail` re-taps once if Detail did
   not open. This is a harness lesson, not a product bug, but any future device automation will
   meet it.
2. **Font scale 2.0 does not break the layout.** Probed by running the scale pass at 2.0: nothing
   wrapped, nothing left its control, nothing left the 20 dp gutters, and the tallest numeral grew
   to 166% rather than 200%, because Android 14's non-linear font scaling plus `maxLines = 1` and
   autosize shrink the glyph instead of taking a second line. Whether the shrunken header ticker is
   still *legible* at 2.0 is a judgement and sits in docs/qa-checklist.md, not in the script.
3. **A debug build's swap ends at the wallet handoff, and that is the honest boundary.** Tapping
   Swap raises `com.solanamobile.wallet` before the app's own sheet is ever composed, so there is
   no machine assertion to make about the sheet on this device. The walk asserts the handoff
   happened, presses back, and asserts Detail returns. Nothing is signed and no money moves.

**The jobscheduler count has to be the registered jobs and nothing else.** `dumpsys jobscheduler`
mentions the package 18 times after a walk, almost all of it history, quota bookkeeping and
recently completed work. Only lines matching `^  JOB .*com.plainticker.mobile` are a schedule: 0
before the first watch, 1 while a ticker is watched, 0 again after unwatching.

**Caution has no colour in a dump.** What the script can read is the wording that is drawn in
Caution, so it asserts that "Issuer can move tokens" and "issuer can pause" appear on the two
issuer-control cells whose value is Yes and on no other cell on the screen. The colour itself stays
pinned by `DetailModelTest` without a device.

**What the cross-model review changed, 2026-09-13.** Three corrections after the walk was first
written, each proved on this phone:

1. `wait_quiet` read "no frames" as "settled". Between `am start` and the first frame `dumpsys
   gfxinfo` answers a literal `0` for a few tenths of a second (sampled at 0.2 s intervals after a
   bare `am start`: none, 0, 5, 9, 13, ...), so a walk that starts the app and polls immediately
   can call a screen settled before it has drawn anything, which is the snapshot-banner trap in
   point 1 above wearing a different hat. The scale and animator passes now start the app with
   `am start -W`, which returns on the first frame in about 1.2 s, and `wait_quiet` only accepts a
   quiet reading once `dumpsys window` says the app owns the focus.
2. Every copy rule is an absence, and an absence is free on a screen that drew nothing. A dump with
   nodes but no text used to pass `assert_lint`, `assert_labels` and `assert_geometry` in silence.
   `assert_lint` now fails below five readable strings; the thinnest real screen in the walk is
   onboarding at 7, and each pass line prints the count it checked, so the transcript carries its
   own evidence against vacuity.
3. The 20 dp gutter was 60 px hard-coded. It is now `wm density` divided into 20 dp, so the walk
   means the same thing on the second Android phone step 5.10 of the checklist reaches for
   (52 px at 420 dpi, 70 px at 560 dpi).

The walk was also proved to fail a third way, independently of the two seeded during development:
with `animator_duration_scale` already 0 before the run, the live bar physically cannot breathe and
pass 1 exits 1 in 49 s on "the live bar to breathe: at least 100 frames in 2s / found: 4 frames".

## What the first real swap taught us, 2026-09-13

The first swap this product has ever made with real money: **5 USDC into TSLAx** from the public
demo wallet on SM02E4072810430, signature
`5pyP9e1wHCGmatBKM8wtzTW33AjtBR7hMv5Xf4RF2fYhYGyioQov76L29ToZaXtTVF1WbsnqJM2zvRu7awAXHK3F`,
**slot 446,653,478**, confirmed in 0.8 s, against the signed release build v0.2.0. Everything
above this heading was measured against a debug build that stops after signing; this is the first
entry written from a leg that actually landed. It left the wallet holding 0.01362917 TSLAx and
15.216083 USDC, and it exposed two copy defects, one on each side of the swap.

**1. The plural. Portfolio, under the total.**

| | |
|---|---|
| Seen | `1 xStocks, priced by Jupiter`, under `$4.99`, read off the device with `uiautomator dump` |
| Why | the sentence was a `<string>` with the plural spelled once, so a count of one printed a plural noun |
| Now | `1 xStock, priced by Jupiter`, and `4 xStocks, priced by Jupiter` |

It was not one string. Every sentence in the app that said a whole number out loud had the same
shape, so all nine are `<plurals>` now and the platform picks the form:

| Resource | one | other |
|---|---|---|
| `portfolio_priced_by` | `1 xStock, priced by Jupiter` | `4 xStocks, priced by Jupiter` |
| `portfolio_priced_partial` | `1 of 1 xStock priced by Jupiter, the total covers what is priced` | `1 of 3 xStocks priced by Jupiter, the total covers what is priced` |
| `portfolio_priced_none` | `1 xStock, not priced by Jupiter` | `3 xStocks, none of them priced by Jupiter` |
| `list_today_watched` | `Today: 1 stock watched` | `Today: 12 stocks watched` |
| `list_today` | `Today: 1 stock watched, next report TSLAx on Oct 22` | `Today: 12 stocks watched, next report TSLAx on Oct 22` |
| `digest_watched` | `1 stock watched.` | `4 stocks watched.` |
| `digest_reports_in_days` | `TSLAx reports in 1 day.` | `TSLAx reports in 4 days.` |
| `detail_fscore_of` | `of 1 signal` | `of 9 signals` |
| `detail_fscore_a11y` | `F-Score 1 of 1 signal` | `F-Score 8 of 9 signals` |
| `swap_amount_too_precise` | `CENTx counts 1 decimal` | `USDC counts 6 decimals` |

Two of those had never been reachable at one (`digest_reports_in_days` has its own sentences for
today and tomorrow; the F-Score scale is nine), which is exactly why the class needed a guard
rather than nine judgements. `CountCopyTest` renders each of them out of the shipped strings.xml at
one and at many, asks every model that counts twice, and lints `src/main` for a `Fmt.count(...)`
handed to `stringResource`, `words` or `getString`. That last rule is the bug's own shape: it
printed the six real call sites the moment it was written.

Review then found the class once more, inside one of the new plurals. `portfolio_priced_partial`
says **two** numbers out loud and a plurals agrees with only one of them, which has to be the one
that governs "xStocks". The clause after it read "the total covers those", so a wallet holding one
priced token beside two unpriced ones drew `1 of 3 xStocks priced by Jupiter, the total covers
those`: a plural over a count of one again, in the sentence next to the one that started this.
The clause is now number-neutral, "the total covers what is priced", and `CountCopyTest` refuses
a plural pronoun in it.

**2. The wrong truth. Swap sheet, after the wallet closes.**

| | |
|---|---|
| Seen | `The swap did not land. Nothing was swapped.` with a Close button, after the wallet sheet was closed without approving |
| Why | every way the approval round trip could end without a signature landed in the terminal failure branch |
| Now | `No signature came back, so nothing was sent. The amount is still here.` on the amount step, with what was typed still in the field |

True on chain and the wrong sentence: nothing failed, nobody approved. What the app can actually
know is in the device log, which is the reason the new sentence is worded the way it is. The
attempt that was not approved:

```
11:01:04.248  Encrypted session established
11:01:42.627  mobile-wallet-adapter session closed
```

and the one that landed at slot 446,653,478:

```
11:08:09.943  Encrypted session established
11:08:19.761  mobile-wallet-adapter session closed
```

The session closes either way. **Mobile Wallet Adapter does not tell the app why it ended**, only
whether a signature came back with it, so a sentence naming a decline would be a guess and a
sentence naming a failure is worse than a guess. What is certain is the same in all three cases
(declined, the sheet went away, the session dropped): nothing was signed, `/execute` was never
called, no money moved. So the machine treats all three as a cancellation, which is a state it
already had, and the sheet returns to the amount step with the amount intact instead of a dead end.

Two failure reasons went with it. `NOTHING_SIGNED` ("The wallet returned no signed transaction") is
unreachable now and is gone. `WALLET_REFUSED` ("The wallet did not return a signature") only ever
described a failed *connect* once the approval path stopped using it, so it is `CONNECT_REFUSED`
and says `The wallet did not answer, so nothing was connected`. `The swap did not land. Nothing was
swapped.` stays, and now belongs to exactly one thing: an `/execute` that refused.
