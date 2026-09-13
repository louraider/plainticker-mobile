# Design review, 2026-09-13

The signed release v0.2.0 on the Seeker (SM02E4072810430, 1200x2670 at 480dpi, 400dp by 890dp),
walked screen by screen on the device rather than in previews. Read against DESIGN.md and
design/canvas/instrument.py. Nothing in the app was changed and the wallet was not touched.

## Screenshots

Captured to the session scratchpad, `shots/`. All are the release build on the real device.

| File | What it is |
| --- | --- |
| `01-list-settled.png` | List, settled, first viewport |
| `02-list-first-paint.png` | List about 0.7 s after a cold launch |
| `03-list-without-analysis.png` | The untracked tail of the List |
| `04-list-section-boundary.png` | Where Analyzed ends and Without analysis begins |
| `05-detail-deep-top.png` | Detail, NVDAx, pool $1.9M, first viewport |
| `06-detail-deep-sector.png` | Detail, NVDAx, scrolled under the status bar |
| `07-detail-deep-fscore-method-swap.png` | Detail, NVDAx, F-Score, Method, Swap |
| `09-detail-belowfloor-top.png` | Detail, APPx, pool $34, below the liquidity floor |
| `10-swap-wallet-connect.png` | What the Swap button actually opens with no wallet session |
| `11-portfolio.png` | Portfolio, wallet not connected, the first swap's local receipt |
| `12-watchlist.png` | Watchlist as shipped, nothing watched |
| `13-list-after-11min.png` | List again after eleven minutes of the app being open |

Not captured, and why: the swap sheet at its amount step and Portfolio showing the real 0.01362917
TSLAx holding are both behind a wallet authorization. With no session, "Swap USDC to NVDAx" opens
the wallet's own Connect sheet (`10`) and the app's amount step is never drawn; Portfolio states
"Connect your wallet to see the xStocks in it." The component gallery is registered in debug builds
only (`ui/nav/Routes.kt`), so this build has no other way in. Onboarding is behind the app and was
not re-run, because clearing data would destroy the first swap's local receipt.

## Strongest and weakest

**Strongest: Detail for a token above the floor, first viewport (`05`).** It is the only screen
where the whole system fires at once and no two elements do the same job. The 64sp mono ticker, an
asymmetric price row that sets the token at 40sp Ink and the reference at 20sp Ink 2 so you read
which is which without being told, the gauge, the live bar as the single moving thing on the
screen, then a blueprint grid that spends Caution exactly twice, on the only two facts that are
issuer control. Nothing on it is decoration and no section repeats its neighbour's layout.

**Weakest: Watchlist as shipped (`12`).** With nothing watched it is three sentences and two text
actions, one of the sentences boxed in a Panel that groups a single sentence, and 55 percent of the
frame empty. DESIGN.md section 8 bans "text-only pages when a real number could be shown", and this
app knows 157 tokens. It is also the screen a judge opening the app for the first time will land on
if they tap the third tab, because nothing is watched by default.

## Findings

### 1. The liquidity floor is undone by the price row above it (`09`)

APPx, pool $34. The floor correctly withholds the premium and the gauge. Directly above the
withholding sentence the screen prints **Token price $611.56** at 40sp Ink and **NYSE close
$323.00** at 20sp Ink 2, the two largest numbers on the screen, and between them
"Pool holds $34, too thin to track the NYSE close" at 13sp Ink 2. The reader computes plus 89
percent unaided, which is the exact figure docs/data-map.md records for APPx (+89.34 percent on
$34). The floor suppresses the derived number and then hands over both operands at maximum size,
with the sentence that says the number means nothing set smaller than either of them.

This is the review's central finding, and it exists because the floor was reviewed as a rule in
`TrackingQuality`, then on the row, then on the gauge, and never as a finished Detail screen read
top to bottom on a phone. Below the floor the token price is not a price either, by the floor's own
argument. Demote it to the reference's size and weight, or set both at one size and put the pool
sentence above them rather than below.

Second-order: the gauge is a horizontal band about 60dp tall. Below the floor it is replaced by one
13sp line, so the screen that carries the more urgent message is visibly the emptier one, and the
one thing still moving on it is a pulsing accent bar that says "Live from the mint".

### 2. The tracking gauge saturates on the tokens it was built for (`05`)

`Gauge.kt` fixes `scalePct` at 0.5 and `gaugePosition` clamps to 0 to 1 with no off-scale mark.
NVDAx read -1.01 percent today, so the accent tick sits hard against the left end of the track
while the caption still reads "Token vs NYSE close, scale 0.5%". docs/data-map.md says the 13
deepest pools track "within 0.8 percent" and the $10k to $100k band "deviated by about 2 percent"
(NFLXx -2.34, XOMx -1.58). Every token in the middle band pins, and NVDAx proves some of the
deepest ones do too. `instrument.py` only ever drew +0.09 percent, so nobody saw a pinned tick.
Either widen the default scale to the measured spread, or keep 0.5 percent and draw an explicit
end cap so a pinned tick cannot be read as a value.

### 3. The F-Score dims the failures (`07`)

`SignalRow` draws a passing signal in Ink 2 with its value in Ink, and a failing one in Muted with
its value in Muted. NVDAx scores 3 of 9, so six of nine rows recede and the section reads half
loaded. On a weak company the failures are what the reader came for, and they are the faintest text
on the screen. The canvas only ever drew 8 of 9, where one dimmed row reads as a single exception.
Give all nine names one weight and let the yes and no column carry the difference.

### 4. List and Detail disagree about the same token

List row: NVDAx, "79", "fair", meta "Analysis 7 d old". One tap later, Detail: "composite 77" and
"Analysis from 2 d ago". Both screens draw their source correctly. `/api/v1/summary` returns
`composite` 79.072655 and `age_days` 7; `/api/v1/NVDA` returns `composite_percentile` 77 and
`as_of` 2026-09-11. Two numbers for one fact, one tap apart, in the product whose case is that
claims carry their evidence. The repair is upstream, but no screen should print a composite and an
analysis age from two pipelines without naming which.

### 5. The score column does not line up (`01`)

`ListRow` right-aligns `valueRight` and `valueSub` as one group, so the width of the state word
decides where the number starts. Measured from the accessibility dump: "84" begins at x940, "72" at
x960, "79" at x989. That is a 49px, 16dp jog down a column of tabular numerals, in the app whose
stated identity is that every number is in a monospace face. The one column a reader scans is the
one the type system was chosen for. Give the state word a fixed width, or give the number its own
right-aligned column.

### 6. The tail of the List is rows of nothing (`03`, `04`)

"Without analysis" carries no count, and almost every row in it is a ticker, a company and an empty
right column, because Jupiter prices 55 of 157 (docs/data-map.md). The company name also changes
convention at the heading: "Intel Corporation" and "Sempra" above it, "Alcoa xStock" and "Analog
Devices xStock" below, with "xStock" repeated on every row of a list in which every row is an
xStock. That suffix is the most repeated string in the app and it carries no information. Put a
count on the heading, drop the suffix, and either give the row something or let the section
collapse.

### 7. Content scrolls under the status bar with nothing between them (`06`)

`Insets.kt` states the decision: the content scrolls under a transparent status bar with light
icons and nothing is sticky. On the device that puts the 64sp Ink hero and the white system clock
in the same pixels, "NVDAx" over "11:40". Every canvas artboard is an 890dp frame with no system
bars drawn, so this state was never looked at. A short scrim or a top fade on the scrolling
surfaces fixes it without making anything sticky.

### 8. The market-hours disclosure appears on one screen and not the other

Detail carries "The NYSE is closed, the reference is the last close". The List prints
"-0.91% vs NYSE close" on tracked rows against the same last close and carries no banner;
`ListViewModel` says so in a comment, "The List has no market-hours banner yet". The same caveat is
owed on both surfaces or on neither.

### 9. Portfolio and Watchlist are mostly empty and mostly sentences (`11`, `12`)

Portfolio's content ends at 489dp of an 890dp frame. The one real number the product owns, the
first swap, is a 64dp row with its amounts at 18sp mono and everything else Muted, under a
"Holdings" heading whose designed 40sp total never appears when no wallet is connected. When the
receipt list has exactly one item and the screen has 400dp doing nothing, give that item the
receipt's own hierarchy. Watchlist is covered above.

Related: the wallet session does not survive, so a cold open lands on an empty Portfolio. For a
demo that is a real risk, and it is the reason two of the screens in this review could not be shot.

### 10. The empty state squeezes its own sentence (`11`, `12`)

`EmptyLine` puts the paragraph at `weight(1f)` with the text action beside it. On Watchlist
"Nothing watched yet..." wraps to four lines in 58 percent of the width while "Browse analyzed
stocks" takes 37 percent. On Portfolio "Connect wallet" pushes its sentence to two lines and then
centres itself between them, so its baseline lands in the gap. Put the action on its own line under
the sentence. The component is also copied privately into three files (`ListScreen`,
`PortfolioScreen`, `WatchlistScreen`), which is how they drift.

### 11. A row with nothing to say is shorter than its neighbours (`01`)

ABNBx has no premium and an analysis from today, so `rowMeta` returns null and the row falls to the
64dp minimum while every neighbour stands at 69dp. From the dump: NEMx content spans y917 to 1052,
ABNBx y1991 to 2063. Small, but the list is sold as an instrument and its pitch breaks once in the
first viewport.

### 12. The section gap is not one number

DESIGN.md section 5 says 32dp above a heading. `Heading` defaults to 32dp and no shipped screen
uses the default: `ListScreen` and `PortfolioScreen` each define `HeadingTopGap` 30dp and
`SectionTopGap` 28dp, `WatchlistScreen` defines the same two names as 30dp and 30dp, and
`DetailScreen` defines `BackingGap` 28dp. Four private constants in four files for one role, and
Watchlist's second section sits 2dp lower than the same section on List and Portfolio.

### 13. Under the Swap button the app states depth where the canvas stated cost (`07`)

The device draws "liquidity $1.9M". `strings.xml` has `detail_cost_line`, "est. all-in cost %1$s
· liquidity %2$s", but the screen falls back to `detail_liquidity_line` until a quote exists.
The line under a Swap button is the one place a reader looks for what it will cost, and it answers
a different question.

## Where the built screen is right and the canvas is wrong

- **Row value scale.** `instrument.py` draws the List row value as "0.71" on a 0 to 1 scale. The
  device draws "84" on 0 to 100, which is what `/summary` serves and what the Detail heading
  repeats as "composite 77". The device is right; the canvas should be regenerated.
- **System bars.** The canvas draws none, so every artboard is an 890dp content frame and the app
  is not. The device is right; the canvas needs both bands.
- **Where the first viewport ends.** DESIGN.md section 5 says Detail's first viewport ends inside
  "Against the sector". With the market-hours banner drawn it ends inside "Backing and controls",
  one section short. The banner has to be there, so the device is right and the claim in DESIGN.md
  should be restated against the real 400dp by 890dp frame or dropped.

## One thing that is not a finding

The first paint is as advertised. A cold launch reported `TotalTime: 243` and the first frame this
session could capture, about 0.7 s in, was already byte-identical to the settled screen except for
the status bar clock (diff bounding box 162,38 to 184,70). There is no visible snapshot to live
transition to review because there is nothing to see.
