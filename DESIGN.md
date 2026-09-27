# Design System: PlainTicker Mobile, "Amber"

Decided 2026-09-22. The founder rejected "Instrument" (this document's previous revision)
outright after reading it against the shipped build and four new proposals, and picked **Amber**
out of the four: `docs/design-research-2026-09-21.md`, section 5.3, is its source of truth, and
this document defers to it on every number. The research's own author recommended a different
direction ("Ink"); the founder's pick overrides that recommendation, and Amber is what this
document and this codebase now build toward. Full scope, nothing cut, including the light theme
Instrument had deferred.

Instrument is withdrawn as a visual authority the same way it withdrew PlainTicker web's system
before it. What follows is a full replacement, except for one kind of rule: content and legal
rules that came from the founder's own backtests and from legal exposure, not from taste. Those
are restated here in the same sections they held before, because `CopyLintTest` enforces some of
them directly from disk and a renumbering would only cost the reader something for no reason.
Section 1 (the liquidity floor and the app's own record) and section 7 (copy and content rules)
are that content. Sections 2 and 3 (colour, typography) are Amber's, built in the token-and-type
foundation pass. Sections 4, 5, 6 and 9 (components, layout, motion, the brand mark) were marked
**not yet restyled** in that pass, on purpose, because the composables behind them still ran
Instrument's anatomy and building the token and type foundation first is what let the restyle
passes that followed work against one set of names instead of guessing. Those passes have since
run: five of them (branches `design/amber-heads`, `design/amber-chrome`, `design/amber-rows` and
`design/amber-leftovers`, merged onto this branch, plus the screen-by-screen passes before them)
restyled or retired nearly every component `ui/components/` held, and this revision rewrites
sections 4, 5, 6 and 9 against what is actually built rather than against the plan for building
it. Section 8 (anti-patterns) is rewritten, because Amber's own research overturns two or three of
Instrument's specific bans by name.

A handful of pieces under `ui/components/` are still Instrument's own anatomy, unmoved, either on
purpose or because no pass's file set reached them; section 4 names each one and says which.
Read `Tokens.kt`, `Type.kt` and `Theme.kt` for the palette and type scale this document describes;
section 4 is now the reference for the components themselves, not a placeholder for it.

## 1. The liquidity floor and the app's own record

Unchanged in substance from Instrument. This is product and data-disclosure logic, not taste, and
nothing about the redesign touches it.

### 1.1 The liquidity floor

A tracking figure is drawn only where the pool behind it can carry one. Measured live on
2026-09-12 (`docs/data-map.md`): of the 157 analyzed xStocks on the list Jupiter priced 55; the 13
pools at or above $100k all tracked the NYSE close within 0.8 percent; the 6 between $10k and
$100k deviated plausibly; below $10k the quoted premium was arithmetic off a dead pool. That first
pass set the floor at $10,000. Re-measured the next day, 2026-09-13, at four candidate floors
(`docs/data-map.md`, "Decided 2026-09-13: the floor drops to $4,000"): $10,000 kept a premium on 19
rows, $4,000 on 22, $2,500 on 25 and $1,000 on 29, and the widest premium in the tracked set did
not move between any of the top three, because it already belonged to INTCx at $26,205, far above
all of them. **$4,000 buys three readable rows for nothing**: NFLXx at $9,370 reads -1.95 percent,
PEPx at $5,351 reads +0.11, ORCLx at $4,444 reads -0.78. One step further down is where it breaks:
Vx at $2,513 prints +6.64 and JPMx at $2,002 prints +37.98, pool arithmetic and not a price. The
floor is **$4,000**, and it lives in exactly one place, `TrackingQuality.MIN_POOL_USD` in the data
layer, so the list row and the gauge can never disagree about it. The same measurement widened the
gauge's own scale, `TrackingQuality.TRACKED_SPREAD_PCT`, from 2.5 to **4.5** percent: three of the
22 tracked rows (INTCx, HOODx, XOMx) had already run past the narrower scale one day after it was
set.

Above the floor nothing changes: the row keeps the signed premium against the NYSE close, Detail
draws the gauge. Below it neither is drawn and the surface states the pool instead, in one short
sentence a person can act on: "Pool holds $34, too thin to track" on the row's single meta line,
"Pool holds $34, too thin to track the NYSE close" on Detail. When Jupiter prices a token without
reporting any depth, the honest reading is unknown rather than deep, so the premium is withheld
there too and the line reads "Pool depth not reported." Money in these sentences is
`Fmt.compactMoney`.

This is disclosure, not curation. Nothing is filtered out, no section is added and the sort is
unchanged. The sentence is drawn in a descriptive text role, never in the caution role: a shallow
pool is a fact about the token, not an issuer-control risk. On Detail below the floor, three rules
hold: the sentence is read first, above the figures, not under them in a caption; neither figure
leads (both are set at the same reference weight so colour separates them and size no longer
stages a comparison); the token's own figure is named for what it is ("Pool quote", not "Token
price"), because a reading off a thin pool is not a price of the company.

### 1.2 The app's own record, where the chain has said nothing

The wallet session survives process death (since 2026-09-26): the MWA auth token the wallet
issued and the account's public key and label are kept in `files/wallet_session.bin`, sealed with
an AES-256-GCM key that lives in the Android Keystore, and excluded from cloud backup and device
transfer. A cold open restores the account for display and chain reads without opening the wallet;
the first action that needs the wallet reauthorizes with the saved token, and falls back to a
fresh authorize when the wallet no longer honours it. Disconnect, or a wallet refusing the
authorization, clears the file. Where the chain has still told the screen nothing (no wallet was
ever connected, or its read failed), the app's own record stands in the holdings slot, under four
rules:

- **It is drawn only where nothing was read.** A connected wallet the chain answered for, empty
  or not, draws what the chain said; the chain is the authority on what a wallet holds. What is
  left is the two states where no read happened: no wallet session, and a wallet whose chain read
  failed.
- **The sentence is read first**, above the figures: "What this app recorded when its own swaps
  landed. The wallet itself has not been read."
- **It carries a quantity and never a value.** No price is applied to it and it is not summed into
  the total. A recorded quantity multiplied by a live quote is half a chain read wearing the other
  half's clothes.
- **It never claims a confirmation.** A fill the execute answer did not report adds nothing rather
  than the quote it was estimated at, and the unreported fill is disclosed once, on the swap row,
  never twice.

Persisting the session was first declined, on the grounds that the auth token is a bearer grant
and writing it to disk to make a screen look better is a security decision taken for a cosmetic
reason. The judges' review (Beeman, 2026-09-26) showed the cost was not cosmetic: every launch
asked to connect again, before anything the reader came for. What decided it is what the token
can and cannot do. It lets this app ask the same wallet to reauthorize without a fresh consent
prompt; it signs nothing, and every transaction still opens the wallet for the person to approve.
So it is kept, and kept the way a grant should be: encrypted with a key that cannot leave the
phone, out of every backup, and gone on Disconnect. You says so in one line: the phone keeps the
session token the wallet issued, encrypted, and never a key. The app's own record still needs no
network, no consent and no wallet, and it is still what a screen shows where the chain has said
nothing.

## 2. Colour: Amber's tokens

Two tiers now, where Instrument had one flat list of nine. The research's diagnosis
(`docs/design-research-2026-09-21.md` section 2) is exactly why: nine primitives with no semantic
tier is how Instrument's `Accent` came to mean five different roles — tab indicator, text action,
button fill, live bar, gauge tick — at once, with nothing in the type system to stop a sixth.

**Primitives** (`Tokens.kt`, `AmberPrimitive`, private): the raw hex from section 5.3's Dark and
Light columns, transcribed and cross-checked on 2026-09-22 against `gen.py`'s own `"amber"` dict
and against `contrast.py`'s own `"Amber"` dict (the mockup generator and the research's contrast
calculator). All three agree on every value; there was nothing to reconcile.

**Semantic roles** (`Tokens.kt`, `AmberColors`, one instance per theme): named for what a thing
is, never for its colour, so a surface cannot be reached for as if it were an accent.

| Role | Dark | Light | What it is |
|---|---|---|---|
| `surfaceGround` | `#16130D` | `#FFFBF2` | Page background |
| `surfaceRaised` | `#221E15` | `#FFFFFF` | Cards, tonal containers, the status card |
| `surfaceHigh` | `#2E281C` | `#F3EBD6` | The highest surface: a sheet, a pressed row |
| `textPrimary` | `#F5EEDD` (16.0:1) | `#1F1A0E` (16.8:1) | Primary text and figures |
| `textSecondary` | `#C6BCA4` (9.8:1) | `#5A5240` (7.5:1) | Secondary text, context lines |
| `textTertiary(on)` | `#948B74` (5.5:1) | `#7A7059` (4.7:1) | Metadata; see the rule below |
| `border` | `#3A3324` | `#E2D9C2` | Hairlines, an active chip's border |
| `actionFill` / `actionOnFill` | `#FFC247` / `#3B2800` (8.8:1) | `#7A5600` / `#FFFFFF` (6.65:1) | Primary button fill and its text; a selected chip |
| `actionText`, `stateLive` | `#FFC247` (11.5:1) | `#7A5600` (6.4:1) | Text actions, the active tab, the live bar |
| `stateCaution` | `#FF6B57` (6.6:1) | `#B4220C` (6.4:1) | The only red-orange anywhere; never on a number |

Ratios are WCAG against `surfaceGround` (against `actionFill` for `actionOnFill`), pinned by
`AmberContrastTest` against the same relative-luminance formula `contrast.py` uses. `actionText`
and `stateLive` share one value in every set the research drew; that is stated, not an oversight.

**One disagreement between section 5.3's prose table and its own calculator, found running
`contrast.py` on 2026-09-22**: the light `action.fill` / `actionOnFill` pair (`#7A5600` on
`#FFFFFF`) prints as 6.7:1 in the prose table and as 6.65:1 out of `contrast.py` itself. Every
other ratio in both tables agrees with the calculator to within rounding. Per instruction, the
calculator is the source of truth here; the table above and `AmberContrastTest` both use 6.65:1.

**The rule the research measured and every direction had to obey**: text.tertiary never sits on
surface.high. It measures 4.32:1 dark and 4.12:1 light there, both inside the "4.1 to 4.4:1"
section 4 states and both under the 4.5:1 AA floor for normal text. `AmberColors.textTertiary(on:
AmberSurface)` is the only way to read that colour out of the class, and it promotes to
`textSecondary` when `on == AmberSurface.HIGH`, so the violation cannot happen by forgetting a
rule; it can only happen by not calling the function at all, and every colour-scheme slot and
every future component is expected to.

Instrument's nine tokens (`Canvas`, `Elevated`, `Ink`, `Ink2`, `Muted`, `Line`, `LineStrong`,
`Accent`, `Caution`) are untouched: `BrandAssetsTest` pins the launcher icon to them and every
existing composable still reads them. Retiring them is restyle work, one composable at a time, not
a global repaint done once here.

## 3. Typography: Amber's pairing

Bricolage Grotesque, one real variable font (`res/font/bricolage_grotesque.ttf`, the actual
variable instance from `google/fonts`, not the static Regular-weight file the mockup canvas
originally used to draw the approved artboards). Confirmed 2026-09-22 with fontTools against the
bundled file itself, not against the mockup's file: `fvar` axes `opsz` 12 to 96, `wght` 200 to 800,
`wdth` 75 to 100; GSUB carries `tnum`. JetBrains Mono stays bundled for on-chain identifiers only
(section 4's foundation rule); Amber's numbers stay in Bricolage.

`AmberType` (`Type.kt`) builds one `FontVariation.Settings` per style rather than a handful of
fixed static weights, because optical size doing real work between 34sp and 14sp is the actual
reason to bundle a variable font at all. Width is held at 100 everywhere; nothing in the research
calls for a width change. Weight runs 400, 600 or 700.

`tnum` is a font feature, not a variation axis, and this face widens the comma and the period
under it (confirmed on the mockup canvas with fontTools). So it is set on number styles only —
`figureLarge`, `figureRow`, `figureInline` — and never on a word style; `AmberThemeTest` pins that
split for every style `AmberType` exposes.

| Style | Size / weight | tnum | What it is |
|---|---|---|---|
| `sectionHead` | 22/700 | no | Section head |
| `rowTicker` | 16/600 | no | A ticker row's ticker |
| `rowCompany` | 14/400 | no | A ticker row's company name |
| `context` | 14/400 | no | A figure's supporting line |
| `body` | 15/400 | no | Prose: Method, disclaimers |
| `button` | 16/600 | no | Primary action label |
| `meta` | 12/400 | no | Smallest supporting text |
| `figureLarge` | 34/700 | **yes** | A card's headline figure |
| `figureRow` | 18/600 | **yes** | A ticker row's right-hand figure |
| `figureInline` | 14/400 | **yes** | A numeral inside a sentence |
| `textAction` | 14/600 | no | `TextAction`'s label (off Outfit since 2026-09-26) |
| `label` | 13/500 | no | A field's label, a banner's sentence |
| `fieldText` | 16/400 | no | A field's value when it is words |
| `screenTitle` | 26/700 | no | The onboarding headline |
| `wordmark` | 15/700 | no | "PlainTicker" in the top bar and on the onboarding panel |

`body`, `button` and `meta` are not individually sized by the research; they hold the sizes every
direction in section 5.5 converges on. The last five rows are the word styles Instrument's
components used to borrow from Outfit, at the same sizes and weights (2026-09-26); with them, every
word the product draws is Bricolage. Outfit is still bundled, for three things only: the debug
gallery's Instrument comparison components (`ListRow`, `TopTabs`, `TodayStrip`, `GalleryScreen`),
and `Typography.kt`, the Material typography fallback a `Text` with no explicit style would reach
(no product composable relies on it). Every other size and weight is transcribed from section 5.5
directly (the ticker row, a number with its context, the section head).

## 4. Components

`ui/components/` went through five further passes since sections 2 and 3 were written (branches
`design/amber-heads`, `design/amber-chrome`, `design/amber-rows` and `design/amber-leftovers`, all
merged onto this branch, on top of the screen-by-screen passes before them): every product-facing
composable that used to draw Instrument's anatomy now draws Amber's, or has been retired in favour
of one that does. A handful of pieces stayed on Instrument's own anatomy, on purpose or because no
pass's file set reached them; both kinds are recorded in 4.7 rather than left for the next reader
to guess at.

**The rule every budget below exists to enforce**, stated once because it caused four separate
clipping incidents on this branch before anyone wrote it down: a slot that draws on one line with
no wrapping, beside a sibling of fixed or content-derived width, clips the moment real content is
longer than whatever sat in the preview. A `Row` measures its unweighted children first and hands
weighted ones whatever is left, so a "meta" column that looks unweighted in a screenshot can still
starve a weighted name beside it: `AmberTickerRow`'s own anatomy below did exactly this, twice,
before the fix that held. The durable fix is either to give every competing slot in a row a real
`weight`, or to put the slots on their own lines so neither one's worst case has to share a budget
with the other's. **A layout is verified by measurement, never by reading an accessibility tree**:
the tree reports the semantic string a screen reader would hear, not the rendered one, and the
second of the four clipping fixes on this branch was signed off exactly that way and still shipped
broken. Every budget in this section was instead measured with fontTools against the real bundled
font (`res/font/bricolage_grotesque.ttf`, or `jetbrains_mono_medium.ttf` for an identifier) at the
exact `wght`/`wdth`/`opsz` the style in question draws, against real strings the catalog and
`strings.xml` can actually produce.

### 4.1 Rows

**`AmberTickerRow`** (`AmberTickerRow.kt`) is the one row every screen with a list of tickers now
draws: Stocks, Today (Yours, Reports this week, Next up), Portfolio, Vote, and the onboarding
backdrop. 64dp minimum, grown by content. Two lines, each given the row's *full* content width rather than
splitting it with the other: a name line (`ticker`, unweighted and non-wrapping, beside `company`,
`weight(1f, fill = false)`, one line, ellipsis) and a meta line (`context`, `weight(1f, fill =
false)`, up to two lines, beside `figure`, unweighted and non-wrapping). An optional
`trailingAction` (Vote, Unwatch) shares the meta line only, never the name line, through
`TextAction`. States: with or without `company`; with or without `figure`/`context`; with or
without `trailingAction`; pressed (`surfaceRaised` steps to `surfaceHigh`, the same tonal move that
surface means everywhere else).

This anatomy clipped twice before the two-line fix. The first pass gave the name group and the
meta group a weighted split of one `Row` (3-to-2); the device still drew "META x  Meta Pl…",
because the pair that actually starved was the *outer* one: a `Row` measures the meta group's own
content, up to a full 32-character disclosure clause, before it ever divides space among weighted
siblings, so the meta group acted as a fixed-width sibling in everything but name. The fix puts
`ticker`/`company` and `context`/`figure` on two separate lines, each owning the row's whole width,
so the two groups never compete for the same budget at all.

Measured budgets (400dp frame, 336dp of content width once `AmberTickerRowGroup`'s 16dp side
padding and the row's own 16dp are taken out):

| Slot | Style | Available | Worst real content | Margin (1.3x) | Past the budget |
|---|---|---|---|---|---|
| `company`, no trailing action | `rowCompany` 14/400 | 250.70dp | "Meta Platforms, Inc." (20 chars), 129.07dp | 121.64dp (59.73dp) | ellipsis, 1 line; the catalog's one 54-char outlier (4.7% of 928 names) still ellipsizes, by design |
| `context`, no figure/action | `context` 14/400 | 214.78dp | worst `list_row_meta_join` (32 chars), 192.44dp | 22.34dp | wraps to 2 lines, then ellipsis |
| `context`, Vote leader row (figure + trailingAction) | `context` 14/400 | 167.92dp of 289.14dp total | "9,999 voters", 83.16dp | 84.76dp (16.59dp) | not reached at realistic content |
| `context`, Watchlist row (trailingAction, no figure) | `context` 14/400 | 263.19dp | realistic join (39 chars), 246.57dp | 16.62dp at 1.0x, wraps at 1.3x | wraps to 2 lines |

**The Pro-numbers lock's own figure marker, added 2026-09-23.** Stocks' `AnalyzedRow` draws
`pro_locked_value` ("Pro") in the `figure` slot in place of the composite the server withheld from
a free, non-AAPL caller. Measured the same way, fontTools against `res/font/bricolage_grotesque.ttf`
at `figureRow`'s exact instantiation (18sp, `wght` 600, `wdth` 100, `opsz` 18): "Pro" is 29.862dp,
shorter than every figure this row's own budget table above is already proven against (the shortest
being "9,999 voters" at 83.16dp of *context*, beside a figure), so it can only widen the context
budget it competes with, never narrow it, at 1.0x or at 1.3x (38.82dp). `AmberTickerRowTest` pins
this arithmetic.

`AmberTickerRowGroup` is the 16dp tonal container a run of rows sits inside (`surfaceGround` behind
a 1dp seam, each row's own `surfaceRaised`). It is a non-lazy `Column`, so it fits a small, fixed
run (Today's "Next up," the onboarding backdrop's four sample rows) and not a list that must stay a
`LazyColumn` for recycling. Stocks (~830 rows under sticky sector chapters) and Portfolio (three
separate `itemsIndexed` lists) rebuild the same visual container per row instead:
`ListScreen.kt`'s `groupedRowModifier`/`groupEdge` (left/right edges on every row, top only on the
first, bottom only on the last, inset by the 16dp corner so the line stops short of the curve
rather than crossing it as a straight chord) and `PortfolioScreen.kt`'s `AmberRowFrame` (only the
run's first and last row round their outer corners, a 1dp `surfaceGround` seam under every other
row). Vote's ballot and Watchlist's rows use a third, plainer variant, `AmberRowDivider`, a bare
`HorizontalDivider` between lazy items with no rounded container at all, the same trade for the
same reason: an unbounded list cannot be one non-lazy `Column`.

**Light theme's exception**, stated once because all four containers share it: `surfaceRaised`
over `surfaceGround` measures about 1.03:1 in light (`#FFFFFF` on `#FFFBF2`) against a healthy
1.12:1 in dark, so a run of rows reads as one faint smear rather than a grouped block.
`AmberTickerRowGroup`, `groupedRowModifier`/`groupEdge`, `AmberChip` and `SkeletonBar` each draw one 1dp
`border` edge, gated on `colors === AmberLightColors`, outlining the group once rather than framing
every row (the anti-pattern section 8 still bans). Dark is untouched, byte-identical.

**`ListRow`** (`ListRow.kt`), Instrument's own row, is not retired. See 4.7.

### 4.2 Figures and section heads

**`AmberFigure`** (`AmberFigure.kt`) is the number with its context: an optional `label` above,
`figure` (34/700, tabular, always `actionText` amber, win or lose) and an optional `context`
sentence below, in an optional 28dp card (`card = false` draws it bare, Detail's plain composite
cell). States: with or without `label`; with or without `context`; `card` true/false; `tone`
(`Neutral` or `Caution`, and `Caution` colours only `context`, never `figure`, "never staged as a
grade," section 7). No measured budget is needed here, on purpose: `figure` is short, tabular,
single-line content with nothing beside it to starve it; `context` carries no `maxLines` at all, so
a full sentence (Today's own lede, "22 of 160 analyzed can be tracked today") simply wraps; the
card fills whatever width it is given rather than a literal dp. This is the shape every budget
elsewhere in this section is measured against: no fixed-width sibling, so nothing to clip against.

**`AmberSectionHead`** (`AmberSectionHead.kt`) is the one heading component Amber draws anywhere,
replacing the retired `Heading` (4.7). `title` (22/700, up to two lines, carries the row's heading
semantics), an optional short `meta` count on the same line, an optional full-width `lede` below.
`background` defaults to the page ground but takes a caller-supplied colour for a `stickyHeader`
chapter (Stocks' sector chapters) that must stay opaque while pinned over scrolling content.
`Heading`'s own defect, and the reason this component exists, was a `weight(1f)` title against an
unweighted meta that could starve to one letter when the meta ran long (`VoteScreen`'s
`RoundHeader` hit this before Amber). `AmberSectionHead` inverts which side is allowed to grow:
`meta` stays a short count and never wraps, `title` is allowed two lines because it is not always
short. Measured: the GICS sector names Stocks groups xStocks into run up to 22 characters, three
tied for the longest (`LongestSectorName`, "Communication Services," pinned against
`app/src/main/assets/snapshot/summary.json`). No dp budget is stated because `title` has nowhere it
can clip: it owns a `weight(1f)` column and wraps rather than squeezing.

### 4.3 Actions

**`AmberPrimaryAction`** / **`AmberDisabledAction`** / **`AmberSecondaryAction`**
(`AmberPrimaryAction.kt`) are the 56dp, 16dp-radius button family: filled amber with dark text
(primary, enabled); transparent with a 1dp border and tertiary text (disabled, no focus ring: a
disabled `Button` takes none); transparent with a 1dp border and primary text (secondary, retiring
Instrument's `SecondaryButton` and a private copy `YouScreen.kt` used to carry). All three share
`AmberActionFrame`, a 2dp focus ring along the button's own 16dp radius. `label` is always
`maxLines = 1` with ellipsis; no character budget is pinned, because every caller supplies a short,
translator-controlled string rather than variable-length data. The rule this family holds,
unchanged since before Amber: no state's only forward action is a text link. `AmberDisabledAction`
is what keeps a disabled state a real button rather than letting it collapse toward that shape.

**`TextAction`** (`TextAction.kt`) is the only secondary-action primitive in the app. Since
2026-09-26 it draws in `AmberType.textAction` (Bricolage 600, opsz 14, 14sp), with a
caller-supplied `color`; every Amber caller passes `colors.actionText`. Bricolage runs 1 to 9
percent wider than the Outfit SemiBold it replaced, so every one-line slot a text action shares was
re-measured with fontTools at that instance, at 1.0x and 1.3x (the notable margins: the Watchlist
row with "Unwatch", 60.648dp, keeps 259.352dp of context at 1.0x; the Vote leader row keeps
124.101dp of context at 1.3x beside "38,406.2 SKR"; the top bar keeps 151.639dp at 1.3x beside
"Watching"; You's "Read license" beside a wallet key keeps 103.844dp at 1.3x). Vertically, its 20sp
line box clears the "g" by 2.855sp (typo ascent 930, descent 270, "g" 180 units below the
baseline), more than Outfit's 1.824sp, which closes the "Sian out" clipping class at the component
level.

**`AmberChip`** (`AmberChip.kt`) is Amber's filter chip: 32dp, `surfaceRaised`/`textPrimary`
unselected, `actionFill`/`actionOnFill` selected (device QA of 1.3.17: `surfaceHigh` with a hairline
was one tonal step from its neighbours and did not read as selected), `minimumInteractiveComponentSize()`
reserving the 48dp touch target without growing the visual chip. The corner radius morphs 8dp to
full (16dp) with a spring on selection (section 6). `label` is `maxLines = 1` with ellipsis; every
caller (a sector name, a deep-pool/watched count) is short, bounded content, so no arithmetic budget
is pinned the way the ticker row's is. Light theme draws a 1dp border ring on an unselected chip,
for the reason given above.

### 4.4 Sheets and grids

**`AmberSheet`** / **`AmberSheetSurface`** / **`AmberSheetHandle`** (`AmberSheet.kt`) are swap,
vote and pass's shared surface: `surfaceHigh` (the highest tonal step, section 2), 28dp top radius,
an amber handle rather than a neutral line. `AmberSheet` wraps `ModalBottomSheet`;
`AmberSheetSurface` is the same chrome without the modal, for a surface that is always on screen (a
landed swap receipt, the onboarding gate) rather than a bottom sheet. `handle = false` draws a 1dp
border top edge instead, the seam the onboarding panel needs sitting over its own decorative 25
percent backdrop. The handle's own touch target is 48dp by construction (23dp padding each side of
a 2dp bar), stated because it replaced a 22dp-total handle that measured under the floor. No
content slot here has its own measured budget: a sheet's content is built from other components
(`AmberPrimaryAction`, `FactGrid`), each measured on its own terms.

**`FactGrid`** / **`FactCell`** (`FactGrid.kt`) is the two-column blueprint grid Detail's
fundamentals, and the swap/pass/vote sheets' fact cells, all draw through: `label` (meta), `value`
(`factValueAt`, Bricolage proportional, or JetBrains Mono when `FactCell.valueMono`, for an
on-chain identifier only), an optional `sub` line, `span` 1 (half-width, pairs up) or 2 (full-width,
alone in its row). `tone = Caution` colours `value` itself, not just a sub-line, for the one case
section 1.2 names, an explicit issuer-control fact stated as a plain word ("Yes"), not a price or
score; this is not the rule `AmberFigure` enforces (which never colours a live figure), because a
boolean fact word is not the kind of number that rule protects.

**This is the one anatomy in the app that still clips rather than wraps**, and its own doc comment
says so plainly: `value` draws `maxLines = 1, softWrap = false` beside a same-width sibling (a
half-width cell) or alone (a full-width one), so content past the budget clips mid-character rather
than falling back to a second line. Every budget below was re-derived against the bundled font at
`FactGrid`'s exact size after its label/value/sub faces moved off Instrument's monospace onto
Amber's proportional one, exactly the kind of anatomy change that silently voids a
previously-measured budget, which is why these are stated here rather than only in the test file.

Geometry on a 400dp frame: the grid's own 20dp side padding plus its 1dp border trick leaves 358dp
for a row. A half-width cell (two per row, less the 1dp gap and 32dp of the cell's own padding)
gets **146.5dp**; a full-width (`span = 2`) cell gets **326dp**.

| Caller | Value size | Available | Character budget | Worst real value | Headroom |
|---|---|---|---|---|---|
| Pass/Vote sheet, signature fee | 18sp | 146.5dp | **13 characters** | "0.123456 SOL" (12 chars, `LAMPORT_DISPLAY_DECIMALS = 6`) | 1 character |
| SwapSheet, `SheetCellSize.Normal` | 22sp | 146.5dp | **10 characters** | "0.002039285" (11 chars is the largest still measured clear; the budget keeps one under it) | -- |
| Detail, `CellValueSize` | 24sp | 146.5dp | **8 characters** | "Unknown" (7 chars) | 35.55dp |
| Full-width (`span = 2`) cells | 28sp | 326dp | **20 characters** | "4,389,047,809.999999" (20 chars) | 28.22dp |

**The swap sheet left this grid on 2026-09-24.** Its cost block and its receipt now draw through
**`AmberFactRows`** / **`AmberFact`** (`AmberFactRow.kt`): one fact per row inside
`AmberTickerRowGroup`'s own tonal container, the label weighted and wrapping on the left, the
value unweighted and one line on the right in `figureRow` (Bricolage, `tnum`), an optional sub line
under both, and the whole row one 56dp tap target when it copies something (the signature, the one
value set in JetBrains Mono because it is an on-chain key). Measured the same way: 328dp of content
on a 400dp frame; the widest real value, "123.456789 AUTO.GBx", is 194.184dp (252.439dp at 1.3x),
so the value never clips and the label wraps first (`SwapResultFitTest`). The "SwapSheet" row in
the table above is kept as history; nothing draws at that size any more. Pass and Vote still use
`FactGrid`.

The 18sp row is the one that actually clipped in production: the signature fee used to format at
lamports' own 9-decimal precision ("0.123456789 SOL," 15 characters against a 13-character budget)
until it was narrowed to a 6-decimal display precision, the honest precision for a quantity worth
about $0.0000002 at the seventh decimal. A future cell drawn at any of these three sizes must stay
inside its budget or accept the clip; there is no wrap fallback here, unlike every row and figure
above it.

### 4.5 Detail's own drawn elements

**`Gauge`** (`Gauge.kt`) is the tracking gauge, the one piece of this restyle the founder's own
approved mockup actually draws: a full-width `surfaceHigh` capsule (4dp thick, fully rounded), a
1dp tertiary reference tick at the midpoint, a 2dp amber token tick standing proud on both sides,
`caption` left and the signed premium right. States: on-scale; off-scale (the tick stands off the
capsule in the open canvas rather than resting against it, so a saturated reading is never mistaken
for a position on the stated scale); undrawn entirely below the liquidity floor or when Jupiter
reports no reference price. Instrument's separate end-stop ticks are gone; the capsule's own
rounded ends already read as the scale's boundary. Measured: `caption` (no `maxLines`, wraps
freely) has a 285.686dp budget against the longest real off-scale caption at 268.660dp, a 17.026dp
margin that wraps to two lines past 1.3x scale rather than clipping.

**`Track`** (`Track.kt`) is a position, never a filled bar: `label` (secondary, left), `value`
(primary, tabular) and `state` (tertiary, a word) right, over the same `surfaceHigh` capsule
`Gauge` draws, with a 2dp `textPrimary` marker, never `actionText` amber, because this states a
classification against the sector, not a live reading, and colour stays reserved for the figures it
already means something on. The marker settles with a 400ms cubic-bezier tween, unchanged since
before this restyle. Measured: `label`'s budget is 197.234dp against the longest real `state` word
("near 52-week high," 103.812dp) paired with the widest `value`, a 116.279dp margin (46.76dp at
1.3x).

**`LiveBar`** (`LiveBar.kt`) is the swap and pass sheets' phase indicator: a 2dp bar that breathes
(opacity 1 to 0.45, 2.4s ease-in-out) only while `live` is true, static when landed or stale. This
is the one continuous motion in the app; section 6 names it the one piece this restyle must not
touch. `label` (context, wraps freely) and `meta` (single line, now `TextOverflow.Ellipsis`) sit in
a column that used to carry no width modifier at all beside the bar's fixed 2dp, exactly this
section's opening trap, and now carries `weight(1f, fill = false)`. Measured: `meta`'s budget is
344dp against the longest real meta (46 characters, 255.624dp), an 88.376dp margin (11.689dp at
1.3x).

**`SignalRow`** (`SignalRow.kt`) is one F-Score signal: `name` (secondary unless passed, then
primary-weighted by colour, not size) left, `word` ("yes"/"no"/"n/a," never tabular: these are
words, not numerals) right. A null answer (a filing the server could not evaluate) reads "n/a" in
tertiary, never "no": a check nobody could evaluate is not a check the company failed. Measured:
`name`'s budget is 324.494dp against the longest real signal name (29 characters, "Operating cash
flow positive," 200.295dp), a 124.199dp margin (57.058dp at 1.3x).

### 4.6 Chrome

**`AmberBottomNav`** (`AmberBottomNav.kt`) is the five-destination bar (Today, Stocks, Vote,
Portfolio, You) that replaced the four-tab-plus-TopBar-action shell: `ShortNavigationBar`,
material3 1.4.0's stable component (section 10), 64dp on `surfaceRaised`, labels always shown, a
`surfaceHigh` pill behind the selected item. No variable-length slot: every label is a short, fixed
string, so no character budget applies. The pill's width-morph (research 5.3) is not built; what
plays is `NavigationItem`'s own built-in selection transition.

**`TopBar`** (`TopBar.kt`) draws the lockup, the two-corners mark before the wordmark (2026-09-24):
`ic_brand_mark_tight`, the launcher's four rectangles cropped to their own 46-unit block by
`design/brand/glyph.py` (drawn from the 108 launcher grid the glyph would fill only 46 units of its
box, the web TopNav's lesson), sized to the wordmark's cap height (Bricolage 700 at 15sp,
`sCapHeight` 660/1000, so 9.9sp, in sp so it scales with the wordmark), tinted `actionText`,
`contentDescription` null, 6dp before the word. `TopBarTest` proves mark, gap, wordmark and the
widest real trailing action ("Watching") fit the 360dp row: 200.94dp to spare at 1.0x, 155.02dp at
1.3x. The bar draws `AmberType.wordmark` and `AmberType.textAction` (the action moved off
Outfit on 2026-09-26: "Watching" is 64.372dp, leaving 198.338dp at 1.0x and 151.639dp at 1.3x),
keeps JetBrains Mono only for a wallet's short key, and resolves `defaultAmberColors()` for its
three colours. The wordmark itself moved on 2026-09-24 (section 9, "Two corners, refit"):
Bricolage 700 replacing Outfit SemiBold 15sp, closing the one place the app and the web drew
"PlainTicker" in two different faces (`TopBarTest` proves the wider glyphs still clear the bar's own
one-line budget). One slot on the right: a real action (`TextAction`), a picture of an action with
no handler (no current caller; the onboarding backdrop's "You" left with the old shell), or a short
meta fragment (a wallet's short key). Never sticky; scrolls away with the content, covered when needed by
`TopScrim` (`Insets.kt`), which paints nothing and holds no semantics of its own.

**`Banner`** (`Banner.kt`) is the one state slot under the header: `surfaceRaised`, a wrapping
`text` (no `maxLines`, so no clip risk) in `AmberType.label` (Bricolage 13/500, off Outfit since
2026-09-26) and an optional `TextAction`. Resolves `defaultAmberColors()`.

**`Field`** (`Field.kt`) is the only text field (the swap amount, Stocks' search), resolved to
`defaultAmberColors()`. Since 2026-09-26 its type is Bricolage throughout: the label in
`AmberType.label`, a number value in `AmberType.figureLarge` (34/700, tabular; an amount is a number,
not an identifier, so JetBrains Mono 36 left), a words value in `AmberType.fieldText`, the unit in
`AmberType.context`. The value is a single-line input that scrolls rather than clips, so it carries
no one-line budget, and "1,234.567891" is narrower now (222.12dp) than it was in mono (259.2dp);
the unit beside it ("AUTO.GBx", 65.46dp, 85.10dp at 1.3x) and the Max action (28.71dp) are the
slots measured.

**`Skeleton`** (`Skeleton.kt`, `SkeletonBar`/`SkeletonRows`/`SkeletonSwitch`) is the only loading
treatment anywhere in the app; there is no spinner. Resolved to `defaultAmberColors()`; carries the
same light-only 1dp border ring described above, since a skeleton fill is exactly the kind of flat
`surfaceRaised` block that nearly disappears on white. `SkeletonSwitch` is also what Detail's
`Hero` reuses for the company name's single cold-open reveal (section 6).

### 4.7 Retirements and deliberate exceptions

| Component | Status | Where it went, or why it stayed |
|---|---|---|
| `Heading` | Retired | Replaced everywhere by `AmberSectionHead`, which closes the exact starved-title trap `Heading`'s weighted-title-against-unweighted-meta anatomy caused. |
| `PrimaryButton`, `DisabledButton` | Retired | Replaced by `AmberPrimaryAction`/`AmberDisabledAction`. |
| `SecondaryButton` (shared, and a private `YouScreen.kt` copy) | Retired | Both replaced by one shared `AmberSecondaryAction`. |
| `Sheet`, `SheetSurface` | Retired | Replaced by `AmberSheet`/`AmberSheetSurface` (28dp top radius, `surfaceHigh`, amber handle, versus Instrument's square, neutral one). |
| `ListRow` | **Kept, one caller** | `GalleryScreen.kt` only (debug builds), to stay field-for-field comparable with `design/canvas/instrument.py`'s own artboards. Every product screen moved to `AmberTickerRow`. Do not add a second caller. |
| `TopTabs`, `TodayStrip` | **Kept, Instrument anatomy, Amber colour (2026-09-22)** | Both now resolve `defaultAmberColors()` (`colors: AmberColors` parameter, same shape as `Field`/`Skeleton`/`Banner` below), closing a real, live leak on Stocks at the time. Since 2026-09-26 no product screen draws `TodayStrip` at all: Stocks dropped it (it repeated Today's own screen) and so did the onboarding backdrop (see below), so it survives for the gallery only. `GalleryScreen.kt` (debug builds) passes the fixed `AmberDarkColors` explicitly to both, matching `TopBar`/`Banner` in that same file, so it stays a static comparison against `design/canvas/instrument.py` rather than following the live system setting. The stale picture this row used to flag is gone: since 2026-09-26 the onboarding backdrop is a picture of Today drawn with `TopBar`, `AmberSectionHead`, `AmberTickerRow` in its group and the real `AmberBottomNav`, so neither `TopTabs` nor `TodayStrip` has a product caller left. |
| `Panel` | **Kept, Instrument anatomy, Amber colour (2026-09-22)** | `GalleryScreen.kt` (canvas validation, unchanged) and, separately, `WatchlistScreen.kt`'s `Digest`, which is live: `WatchlistContent` is Today's own Yours block now, so the digest panel, its `Footer` (delivery/checked lines) and its `EmptyLine` (the first-run "nothing watched" sentence) drew Instrument's fixed `Ink`/`Ink2`/`Muted` directly, unlike every row on the same screen (`Watched`, fully on `AmberTickerRow`). No restyle pass's file set had reached `WatchlistScreen.kt` for this. Fixed: `Digest`, `Footer` and `EmptyLine` now resolve `defaultAmberColors()`, and `Panel` itself (its `surfaceRaised` background and `border`, previously Instrument's fixed `Elevated`/`Line`) takes a `colors: AmberColors` parameter the same way; `PlainTickerType` is unchanged on all three, the same "type stays, colour resolves" pattern the row below uses. Since Today direction A (2026-09-24) Today no longer draws `WatchlistContent`: the digest lives on its own screen under You (`ui/you/DigestScreen.kt`), whose `Panel` resolves the same colours. |
| `Field`, `Skeleton`, `Banner`, `TopBar`, `TextAction` | **Kept anatomy, Amber type and colour (2026-09-26)** | Each resolves `defaultAmberColors()`, and since the pre-freeze pass each draws Bricolage (`AmberType.label`, `textAction`, `fieldText`, `figureLarge`, `wordmark`) instead of Outfit; the pass sheet's title and sentences moved the same day (`sectionHead`, `body`). JetBrains Mono stays only where an on-chain identifier is drawn: a wallet's short key in `TopBar`, a signature or address in a `FactCell`/`AmberFact` with `valueMono`. |

## 5. Layout

The shared information architecture (Today, Stocks, Vote, Portfolio, You under `AmberBottomNav`,
replacing the old List/Vote/Portfolio/Watchlist tab row and a TopBar "You" action; chapters
replacing infinite scroll on Stocks) is `docs/design-research-2026-09-21.md` section 3, and it
shipped as drawn: `HomeScreen` hosts five peer destinations, Watchlist folded into Today's own
"Yours" block, Stocks gained sticky sector chapters, a jump index and a wrapping filter row (both reshaped in the judges' round 2, below).
Detail's own layout order does not change in any direction the research drew, Amber included; only
the tokens and components under it do.

### 5.1 The surface ladder

Three tiers, `AmberSurface.GROUND` / `RAISED` / `HIGH`, and a rule for reading tertiary text on any
of them:

| Surface | Used for |
|---|---|
| `surfaceGround` | The page background; the seam between rows inside a tonal group; a `stickyHeader`'s own default background. |
| `surfaceRaised` | A ticker row's own fill; the venue/total card body; `AmberChip` and `AmberBottomNav`'s container unselected; `FactGrid`'s default cell surface; `SkeletonBar`'s fill. |
| `surfaceHigh` | The highest surface: `AmberSheet`/`AmberSheetSurface`; the pressed state of a ticker row; the active bottom-nav pill; the `Gauge`/`Track` capsule fill (drawn directly on the page, not inside a card); `FactGrid`'s surface on the swap and pass sheets. |

**Tertiary text never sits on `surfaceHigh`**, and it is enforced in code, not by convention:
`AmberColors.textTertiary(on: AmberSurface)` is the only way to read that colour out of the class,
and it promotes to `textSecondary` the moment `on == AmberSurface.HIGH`. The reason is the light
theme's own thin margin: the pair measures 4.32:1 in dark and 4.12:1 in light on `surfaceHigh`,
both inside the "4.1 to 4.4:1" band the research measured and both under the 4.5:1 AA floor for
normal text, so a caller cannot pass `surfaceHigh` and get a failing colour back even by mistake.
Every call site in `ui/components/` and every screen that draws its own text on a resolved surface
(`AmberSectionHead`'s chapter head, `FactGrid`'s cell label) goes through this function rather than
reading `textTertiary` as a field.

A related but separate fact about the same thin margin: `surfaceRaised` over `surfaceGround`
measures about 1.03:1 in light against 1.12:1 in dark, so a plain tonal fill (a ticker row, an
unselected chip, a skeleton bar) reads as nearly invisible on white *before* any text is even
drawn on it. This is not the tertiary-on-high rule, it is a step lower on the ladder, and it is
why `AmberTickerRowGroup`, the grouped-row modifiers, `AmberChip` and `SkeletonBar` each draw the
light-only 1dp border ring documented in section 4 and pinned in section 8's anti-patterns entry.

### 5.2 Spacing and shape

No single spacing-scale token exists (`Tokens.kt` has no `AmberSpacing`); the rhythm below is what
every component in `ui/components/` and every screen actually uses, read off the source rather than
declared once and possibly drifted from:

| Value | Where |
|---|---|
| 1dp | The seam between rows in a tonal group; grid and gauge/track hairline elements. |
| 4dp to 6dp | A slot's own internal line gaps (`AmberFigure`'s label/figure/context, `AmberSectionHead`'s row-to-lede gap). |
| 8dp to 10dp | Gaps between a fixed label and its paired value (`Track`'s value/state, `AmberTickerRow`'s ticker/company and context/figure pairs). |
| 12dp | Gaps between two independent groups sharing a row (`Gauge`/`Track`/`LiveBar`'s caption-and-value row, the chip row, `AmberSectionHead`'s title/meta gap). |
| 16dp | A ticker row's own side padding and `AmberTickerRowGroup`'s outer side padding; `AmberChip`'s horizontal padding; a list container's shape radius. |
| 20dp | The near-universal screen-edge and card side inset: `AmberFigure`, `FactGrid`, `Gauge`, `Track`, `LiveBar`, `SignalRow`, `Field`, sheet content. |
| 24dp | Above an `AmberSectionHead`. |
| 28dp | A status card's or a sheet's top radius. |

Shape follows a three-step hierarchy by role, not by screen (`AmberShapes` in `Theme.kt`): **8dp**
for a chip's resting corner, **16dp** for a list container or a button, **28dp** for a status card
or a sheet's top radius. A chip's selected radius is a fourth, dynamic value
(full/pill, `ChipHeight / 2`) reached by animating the 8dp value rather than switching shapes (section 6).

### 5.3 Screen by screen

**Today** is direction A, "One line, then yours" (the founder's pick of three, 2026-09-24,
after a tester called the previous layout cluttered, unclear, stale and dominated by a huge
banner). One `LazyColumn` on `surfaceGround`, 16dp side inset, 8dp above each section on top of
`AmberSectionHead`'s own 24dp, so the screen breathes:

1. **The status line**: the venue in one wrapping line in the reader's own time ("NYSE open.
   Closes at 23:00 your time"), led by a 3dp bar lit in `actionText` only during the exchange's
   own session, the state phrase before the first period in weight 600. It replaced a 120dp
   `AmberFigure` card whose 34sp "Open"/"Closed" was the loudest thing on the screen and often
   wrong. Pre-market and after hours are their own sentences and say tokens still trade on thinner
   pools; a holiday and a 13:00 early close say so. It owns its row and carries no `maxLines`, so
   it wraps rather than clips (5.4). A tap opens the market hours sheet (`AmberSheet`): what the
   hours mean for a token, the analysis and price ages, and, only when the calendar is answering,
   why. The status is a clock (`MarketClock`): recomputed on every resume and at every open or
   close while the screen is resumed, stopped on pause; the venue's trading block is believed only
   until its own `nextChangeAt`, then the bundled NYSE calendar (`NyseCalendar`, 2026 and 2027
   holidays and early closes) answers until a one-asset refresh lands. Stocks' hours banner reads
   the same clock.
2. **Watched**: `AmberSectionHead` (count as meta, the figure's meaning as the lede, said once:
   against the share price while trading, against the last close otherwise), then the reader's
   rows in `AmberTickerRowGroup`, one figure each. The figure comes from Today's one price read,
   so no ticker is ever priced twice on the screen. A pool under the liquidity floor, or one with
   no depth reported, states itself beside the report date instead of a figure (1.1). No Unwatch
   here; it is on the stock's own page. Under the rows, the digest as one line in the reader's
   time with "Read it", which opens the digest screen under You (its own route, `digest`); the
   weighted sentence and the short fixed action never share a budget they could lose (5.4).
3. **Reports this week** (the founder's pick of option B off the designer's page, 2026-09-26,
   replacing "Tracked today": a beginner reading NVDAx at +0.21% had no way to tell what "tracked"
   meant or why the list was there, and a report date is a plain fact rather than a number that
   reads like a tip): up to five covered companies whose next report falls from today through the
   coming Sunday in the reader's own local week, soonest first, a watched ticker marked in the
   figure slot rather than left out, then a plain pointer into Stocks (which cannot sort or filter
   by report date, so the link never claims a filtered count). The report date is a US Eastern
   calendar day and is never re-zoned into the reader's own time; only the reader's own "today" and
   week end are read in their zone. Skeleton only while the fast half of Today's join is still out
   (`next_report_date`/`next_report_confirmed` ride `/summary`, not a price, so this needs nothing
   the retired block did). Undrawn entirely, header included, before the server has sent a report
   date for anyone at all: a block that cannot tell a quiet week from an undeployed field says
   nothing rather than risk a false one. A quiet week's own sentence replaces the section's lede in
   place of the usual explainer, naming the next known report after this week when there is one.
4. **Next up for analysis**: one row, the round's close in the reader's time, opening Vote.

No footer count: Stocks' own segments carry it. **First open** (nothing watched) swaps Watched and
the digest for a start block, a sentence of what watching gives and one `AmberPrimaryAction`,
"Find a stock", and gives each report row a Watch trailing action (the figure-plus-action pairing
`AmberTickerRow`'s budget proves; `TodayModelTest` re-proves it with fontTools numbers) rather than
the watched marker, since a first open's watched set is always empty. No digest line, no
notifications line and no Next up on a first open. The notification permission is asked once,
right after the first watch, from Today or Detail alike; the setting lives in You and on the digest
screen, beside the digest it delivers.

**Stocks** (`ListScreen.kt`, mounted by `StocksScreen`) replaced an infinite scroll with search at
the top, sticky sector chapters (`stickyHeader` items painted opaque so pinned content never shows
scrolling rows through it) and one horizontally scrolling filter row (Deep pool, Watched, then every
sector). Judges' round 2 (2026-09-27) removed the chapter jump rail that sat beside the list (its
"Com", "Dis", "Sta" codes clipped and meant nothing to a beginner, and it narrowed every line beside
it, the hours banner included) and replaced the wrapping `FlowRow` of chips, which grew to four rows
and pushed the first stock below the fold. One filter active at a time: a sector chip holds one
sector still rather than stacking with Deep pool/Watched. The Deep pool chip is drawn only from five
deep rows up (`DEEP_POOL_CHIP_MIN`), or while it is the active filter.

Plain copy on Stocks (audit 2026-09-26): the first chip reads "Deep pool 21", not "Tracked 21",
and while it is selected one line under the chips says what it means once ("Deep pool means at
least $4k sits in the token's trading pool, so its price follows the share closely", the floor
formatted from `TrackingQuality.MIN_POOL_USD`). An analyzed row's figure reads "62 of 100" (judges'
round 2; it read "score 66" before, and a bare "66" before that), and one line above the rows says
what the scale ranks against ("Each number ranks the stock against the others in its sector, from 0
to 100."). The widest real value, "100 of 100", is 92.538dp at `figureRow`, narrower than the
"$12,345.67" price figure and the "38,406.2 SKR" figure (113.220dp) every row budget in 4.1 is
already proven against. The analysis age reads "2 days old" (counted copy, `list_row_age_days`),
not "2 d old". The "Today: 1 stock watched" strip that used to sit above Stocks' banner is gone: it
repeated Today's own screen.

**Vote** lays out a header, an explainer, the round header, Leaders, Your votes, Last round, a
search field, then the ballot, all in one `LazyColumn` with no sticky header at all. Last round
reads the server's `previous.status`: the live word is `closed`, drawn as the neutral "Round 1
closed. JEF had the most stake."; `pending`, `published` and `uncoverable` keep their coverage
sentences; any other word degrades to the same neutral line rather than hiding the section (it
vanished for every reader until 2026-09-26 because `closed` was unknown). This was measured, not
assumed: the live catalog ran to 1,124 Solana symbols on 2026-09-26 and the ballot to 898 rows
at 64dp each, tens of thousands of display points below where the screen starts. The ballot holds
US-listed underlyings only (`XStockAsset.isUsUnderlying`, read from `underlying.listingCountry`,
then the underlying ISIN, then the venue's MIC), because the server refuses a vote for any other
listing after the wallet has connected; the filter took 174 London, Hong Kong, Madrid and
Frankfurt rows out of 1,072. Stocks' search and Detail drop the Vote action for the same rows. What that costs is
specific, not a vague "it's long": the header carries nothing a voter needs mid-ballot (switching
destinations is `AmberBottomNav`'s job, not this screen's, since the bar is a sibling of the
scrolling content, not a child of it) and every ballot row draws its own inline "Vote," so a voter
never scrolls back up to cast one. What is genuinely lost is `BallotSearchField`: it is a plain,
non-sticky `LazyColumn` item like every section above it, so re-reaching it once scrolled past
costs the same climb the descent down did. This is recorded in `VoteScreen.kt`'s own class doc
rather than fixed; if this layout is ever revisited, that field, not the header, is where the real
cost sits.

**Portfolio** uses a hero `AmberFigure` (the total) followed by `AmberRowFrame`-grouped rows; see
section 4.1 for why it rebuilds the grouping per row rather than using `AmberTickerRowGroup`.

**You** is the account cabinet (2026-09-25, matching plainticker.com's own cabinet, web PR #145,
at the founder's request). It replaced a long stack with no single headline (a wallet block, Pro
and Staked SKR fact cards, a full-width Connect wallet button, an Account section, a three-card
device grid, a notifications line, the footer, three license blocks). One `LazyColumn`, top to
bottom:

1. **The hero card**: a 28dp `surfaceRaised` card at the 16dp group inset (a 1dp light-only
   border, the same exception as the row groups). Identity first: the Google account's email when
   signed in, else the connected wallet's short key in JetBrains Mono, else "Not signed in". Then
   the plan as the headline, 28sp Bricolage 700 (`figureLarge`'s own opsz 34 instance, tabular
   figures off): "Pro until 20 Oct 2026", "Pro while staked", "Free", "Plan not read". Then a
   line or two: days left ("26 days left.", counted copy), what Free opens (formatted from
   `OPEN_EXAMPLE_TICKER`, "AAPL is open to everyone as a full example", never a preset count), a
   pending payment. Then at most one action, the only amber fill on the screen (`youHero`,
   `YouModel.kt`): no identity offers Sign in with Google with Connect wallet as a centred text
   action under it (nothing while the stored account is still read; a disabled "Signing in" while
   one is in flight); otherwise Free offers Get Pro, a pass offers Extend Pro, and a stake, a web
   subscription or a still-loading plan offer none. Pay is offered only where `payOffered` allows.
   Headline dates are UTC, the same clock as the Plan group's full "Valid until" timestamp.
2. **Plan**: source, valid until (with days left), how to extend, the staked SKR figure, a pending
   payment. The pass flow appears here as a Get Pro or Extend Pro text action whenever the hero
   does not carry it, so it is always one tap away and never drawn twice; Refresh sits where the
   old wallet block offered it (a connected wallet) and on a failed read.
3. **Sign-in methods** (`AccountSection.kt`), the one-line pitch as the lede: Google (the email
   with Sign out, or Sign in), the Solana wallet (the short key with Copy and Disconnect, or
   Connect; "Stays connected between launches. This phone keeps the session token your wallet
   issued, encrypted, and never a key. Disconnect forgets it."), and the wallets the
   server returned as linked to the Google account, only if there are any. Sign out is a two-step
   inline confirm: the first tap asks in the row, Sign out or Cancel answers it. Every sign-in
   message is drawn under the Google row, or beside the hero's button when the hero offers Sign in.
4. **On this device**: swaps recorded, votes cast, stocks watched, each a row whose whole width
   opens its tab, the count as an amber `figureRow` on the right.
5. **Notifications**: the Watchlist's delivery line with Enable while off, and the daily digest.
6. **About**: version, the disclaimer, and Fonts and licenses as one row whose Show opens the three
   bundled fonts in place, each still able to read its shipped OFL text.

Every group is a run of `CabinetRow`s (`YouScreen.kt`) in `AmberTickerRowGroup`'s container: a
56dp minimum, an optional meta label, the value and an optional sub in one weighted column that
wraps, then either a figure or one `TextAction` on the right; two actions move to their own
right-aligned line under the text. There is no card grid on You any more, so the grid's clip
history (five fixes) has no slot left to recur in. The only one-line slots are measured in
`CabinetFitTest` with fontTools against the font each is drawn in (400dp frame; 288dp of hero
button content; 336dp of row content):

| Slot | Font and instance | Worst real content, 1.0x / 1.3x | Budget | Margin at 1.3x |
|---|---|---|---|---|
| Hero button label | Bricolage 600 opsz 16, 16sp | "Sign in with Google", 146.96 / 191.05dp | 288dp | 96.95dp |
| Row text action (beside the key) | Bricolage 600 opsz 14, 14sp | "Read license", 85.27 / 110.86dp, plus 16dp inset | 336dp less the key | 103.84dp |
| Two actions on their own line | Bricolage 600 opsz 14, 14sp | "Copied" + "Disconnect", 192.41dp with insets at 1.3x | 336dp | 143.59dp |
| Wallet short key | JetBrains Mono Regular 15sp | nine characters, 81.0 / 105.3dp | column beside the widest action | 103.84dp |
| Device figure | Bricolage 600 opsz 18 tnum, 18sp | "999,999", 69.44 / 90.28dp | 336dp less 12dp gap and the widest label word (83.58dp) | 150.14dp |
| Hero headline (wraps, not clipped) | Bricolage 700 opsz 34 at 28sp | "Pro until 30 May 2030", 292.99 / 380.89dp | 328dp | one line at 1.0x (35.01dp), two at 1.3x |

**Onboarding** (`OnboardingScreen.kt`, rewritten 2026-09-26, when the audit found it still
describing a List to start on, four text tabs, a Watchlist that sent the digest and You "top
right"): a picture of Today at 25 percent (the top bar, the venue line, "Reports this week" with
four sample rows, one marked Watched, and `AmberBottomNav` with Today selected), cleared from the
semantics tree and blind to touch (every pointer event is consumed on the initial pass, because a
real `AmberBottomNav` cannot be built without a select handler). Over it, the panel, all Bricolage:
the wordmark, the headline (`AmberType.screenTitle`, wraps), one sentence on what a stock page
reads, the map (one sentence per destination, each opening with its own bottom-bar label in weight
600, one wrapping `Text` so no label column can starve a sentence column), the disclaimer, the
self-certification and "Open Today" (`AmberPrimaryAction`, 91.74dp at 1.0x and 119.27dp at 1.3x of
a 320dp button). The map teaches what the brief named: Today's contents, what a stock page reads,
Swap in both directions, the SKR vote and Pro.

**Detail** keeps its pre-Amber section order (hero, verdict, price, gauge, fundamentals, method,
what to check next); only the components under each section moved. The hero sits directly on
`surfaceGround`; the gauge and track capsules are the one place `surfaceHigh` is used for a drawn
element rather than a container.

### 5.4 The clipping rule, as a layout fact

Section 4 states the rule at the component level (a slot with no wrapping beside a sibling of fixed
or content-derived width clips). At the layout level it shows up one step higher: Stocks' own
company-name clip was not a bug in `AmberTickerRow` itself but in the outer `Row` around it, which
gave its meta column no `weight` at all, so Compose measured that column's full disclosure clause
before it ever divided space among the weighted name group beside it, a column with no explicit
width acting exactly like a fixed one. The fix (`AnalyzedRow`, `ListScreen.kt`) gives both outer
groups a weight (name 3, meta 2) rather than leaving either one unweighted. The general form: any
time two variable-length groups share a row, both need a `weight`, or neither should share the row
at all.

## 6. Motion

**Where motion is applied**, exhaustively, because a reader building a new screen needs to know
this is the whole list, not a sample of it:

| Where | Spec | Gate | Snaps to |
|---|---|---|---|
| `AmberChip`'s corner radius, selecting/deselecting | `spring(dampingRatio = NoBouncy, stiffness = MediumLow)` on the radius `Dp`, 8dp to full | `rememberMotionEnabled()` | The selected or unselected end shape |
| `TodayScreen`'s block entrance (the status line, Reports this week, Next up; the reader's own rows and the start block stay still) | The same no-bounce, medium-low spring, on alpha and an 8dp rise, staggered 40ms per block, once, the first time a block has something to draw | `rememberMotionEnabled()` | Alpha 1, no translation |
| Portfolio's Total card; You's hero card | `tween(150ms, LinearOutSlowInEasing)`, the research's "quick" token, once on first composition | `rememberMotionEnabled()` | Alpha 1 |
| `SkeletonSwitch` (Detail's `Hero` company name; every other skeleton-to-content switch in the app) | `tween(200ms, EaseOut)` alpha fade, never a spinner | `rememberMotionEnabled()` | Alpha 1 |
| `Track`'s position marker | `tween(400ms, CubicBezierEasing(.2, .8, .2, 1))`, unchanged since before this restyle | `rememberMotionEnabled()` | The target position |
| `LiveBar`'s breathing bar | `infiniteRepeatable(tween(1200ms, EaseInOut), reverse)`, alpha 1 to 0.45, while `live` is true | `rememberMotionEnabled()` | Alpha 1 (stops breathing, does not disappear) |
| The swap result's mark and hero figure (`SwapSheet.kt`'s `ResultBlock`, 2026-09-24) | The ring's sweep and its settling fill on the no-bounce, medium-low spring; the hero figure on the quick 150ms tween. Once per result. Landed only moves; a failure's open caution ring and a pending broken amber ring are static | `rememberMotionEnabled()` | The settled frame is the first frame: the state starts settled when motion is off, so nothing snaps a frame later |

Two spring families do the orchestrated work (`AmberChip`'s morph and `TodayScreen`'s stagger), both
`Spring.DampingRatioNoBouncy` at `Spring.StiffnessMediumLow`: a settle, never a bounce, because a
bouncy alpha can overshoot past fully opaque and read as a flicker on a small block, and a bouncy
shape morph would read as wobble on a 32dp chip. A single "quick" 150ms linear-ease tween is the
one-shot reveal token (Portfolio's Total, You's hero card), distinct from the spring family and
from `Skeleton`'s own, older 200ms ease-out fade and `Track`'s 400ms cubic-bezier settle, both
predating Amber's motion pass and left untouched because nothing about this restyle changed what
either one settles.

**Where motion is deliberately absent.** `Gauge`, `FactGrid`, `AmberSectionHead`, `AmberFigure`,
`AmberTickerRow`, `SignalRow` and `AmberBottomNav`'s own selection indicator carry none: `Gauge`'s
own doc comment states it outright ("Motion. None: this canvas answers to `positionPct`-style
continuous change nowhere the research draws"), and the same is true of every static value the
others draw.
Two pieces the research's own anatomy calls for are still not built, for the reason they never
were: they are motion-token work that belongs with the component that will use it, not a global
pass. `AmberSheet`'s "spring entry" is not built; what plays is `ModalBottomSheet`'s own default
slide, unmodified. `AmberBottomNav`'s pill width-morph is not built; what plays is `NavigationItem`'s
own built-in selection transition, unmodified. Neither omission is silent: both are named at their
own call site's doc comment, not left for a reader to discover by their absence.

**The rule every entry above already satisfies**: nothing may need motion to be legible, because
the smoke script (`scripts/device-smoke.sh`) runs with the system animator duration scale at 0, and
`rememberMotionEnabled()` returns false under exactly that condition. Every gated animation above
replaces its spec with `snap()` at that scale, landing on its settled value on the next frame
rather than partway through a transition; `LiveBar` simply stops breathing and holds at full
opacity, which is also its fully legible state, because `label` itself already says "Live from the
mint" or "Landed" in words, not only through whether the bar is moving. A new component that needs
its animation to finish before a reader can tell what it says has not met this bar, no matter how
good the animation looks with motion on.

## 7. Copy and content rules

Unchanged in substance from Instrument, restated here because `CopyLintTest` reads
`app/src/main/res/values/strings.xml` and every Kotlin source under `ui` (the verdict-word rule
reads every Kotlin source under `src/main` and `src/test`) straight from disk on every test run,
and because these came from the founder's own backtests and from legal exposure, never from taste.

- **No buy, sell, hold or avoid**, as words, on any surface, including the swap sheet: the
  direction flip is "TSLAx to USDC". "Swap" is the only trading verb.
- **No verdict words.** The product classifies against a fixed rule; it does not tell a reader
  what to do. A composite score or a sector rank is a fact stated once, never staged as a grade.
- **No emoji, no pictographs** standing in for words: no arrows, dingbats, check marks or dots
  drawn instead of the word they mean.
- **No intensifiers.** Banned outright: seamless, powerful, unlock, empower, journey, insights,
  supercharge, effortless, all-in-one, welcome to.
- **No exclamation marks**, anywhere in `strings.xml`.
- **No em or en dash**, anywhere in `strings.xml` or a UI string literal; use a period, comma,
  colon or hyphen.
- **At most one middle dot per line.**
- **One short date format, day first**: "27 Oct" (`Fmt.dayMonth`), "Monday 28 Sep", "12 Sep 2026".
  The month-first "Oct 27" (`Fmt.monthDay`) was retired on 2026-09-26, after Today drew "Reports
  Oct 27" beside "Monday 28 Sep". A count with a unit is a word, not a letter: "2 days old", never
  "2 d old".
- **Sentence case.** No word of four or more capitals outside a short initialism list (NYSE,
  NASDAQ, USDC, EDGAR, XBRL), no uppercase transform, no small-caps font feature.
- **A count of one is phrased as one.** A summary sentence spells out the word ("One held, one
  watched," `docs/design-research-2026-09-21.md` section 3's Today draft), not the numeral; a
  count with its own unit stays numeric ("1 voter," "22 of 160").
- **Every visible string lives in `strings.xml`.** Nothing user-facing is a Kotlin literal under
  `ui`; `KotlinScan` (`CopyLintTest`) reads every literal under `ui` specifically because a string
  that lives there instead has skipped every rule above it.
- **The product refuses to be a tipster, on purpose.** The founder's own backtests are what
  Detail's classification rule is built from, and a system that backtested well is exactly the one
  most tempting to state as a forecast; stating it as a forecast is also the one framing that
  creates real legal exposure (a securities-adjacent surface reviewed under Solana dApp Store
  policy, `TODOS.md`'s review-survival kit). So the product states a classification and stops:
  it never tells a reader what to do with it, and it never states a probability of being right.
- **The classification is a rule, not a prediction.** `PlainTickerModels.kt`'s
  `Method.isPrediction` is hardcoded false and `PlainTickerApiTest` pins it; the method statement
  itself says so ("We classify the company against its sector by a fixed rule. This is not a price
  forecast or investment advice.," `detail_method_body`). Rewording that sentence to sound more
  confident, or dropping it from a new screen, is a legal regression, not a copy edit.

## 8. Anti-patterns

Rewritten. Two of Instrument's specific bans are what Amber's own research overturns by name, and
carrying them forward unexamined would put a false rule in a document meant to be trusted.

**Overturned by name**, so they are not banned any more:
- *Bottom navigation bars.* Instrument banned them; the research's own first finding is that the
  founder's instinct for a bottom bar was right, and all four directions, Amber included, specify
  `ShortNavigationBar`.
- *Sticky headers.* Instrument banned them; the shared information architecture specifically uses
  a `stickyHeader` sector chapter on Stocks so chapters can replace infinite scroll.
- *Cream or paper backgrounds.* Instrument's own light variant would have stayed a cool off-white
  "never cream"; Amber's light ground (`#FFFBF2`) is warm by design, the same way its dark ground
  is warm, and that warmth is the point of the direction, not an accident to correct.
- *Cards for lists*, loosely: Amber's own anatomy sets a ticker row inside a 16dp tonal container,
  which reads as a grouped surface even though it is not a Material `Card`. The rule that survives
  is Instrument's original reason for banning cards (no shadow, no elevation, no border-as-frame
  around every row) rather than the flat "never a container" reading.

  **One narrow, light-only exception, added 2026-09-22.** `surfaceRaised` over `surfaceGround`
  measures about 1.03:1 in the light set (`#FFFFFF` on `#FFFBF2`) against a healthy 1.12:1 in
  dark, so a light grouped row, an unselected chip and a skeleton bar's fill were all reading as
  nearly invisible on white: the tonal step Amber's own structure depends on simply is not there
  in light. `AmberChip`, `SkeletonBar`, `AmberTickerRowGroup` and `ListScreen.kt`'s own lazy
  chapters now draw one 1dp `border` edge around each of those containers, gated on the light
  palette. The rule above still holds for what it actually bans: this edge outlines a group once
  (or a single chip, or a single skeleton bar), never a frame around every row inside a group, and
  dark is untouched, byte-identical, still the founder's approved palette. `AmberChipTest`,
  `SkeletonTest`, `AmberTickerRowTest` and `ListScreenTest` each pin that their own component's
  edge exists in light and is absent in dark; `AmberContrastTest` pins the measured contrast
  (light `border` over `surfaceRaised` and over `surfaceGround`) and dark's own untouched
  primitives, so a future palette edit cannot silently reintroduce the flat look.

**Still banned, repo-wide, unrelated to which of the four directions had won**: purple, and
frosted glass or glow ("Purple is banned by repo convention; frosted glass and glow are on the
slop list," `docs/design-research-2026-09-21.md` section 2). Gradients and drop shadows are not
called for by any of the four directions' shape language and stay off by the same convention.
Spinners (skeletons instead), snackbars, hover-only states (there is no hover on a phone, but a
future two-pane layout should not add one as its only affordance), and a text-only surface where a
real number could be shown instead, all stay banned; none of the four directions asked for any of
them and the research's own account of why the current build reads as generated does not implicate
them.

**What "looks generated" named, so a restyle does not walk back into it** (research section 1):
broadsheet hairlines with nothing else to organise a screen; a single undifferentiated column
where a caveat, a heading and a number all carry the same visual weight; one interactive colour
asked to mean five different roles because there is no semantic tier to stop it; middle-dot-joined
meta strings past the one-per-line limit above. Amber's whole point — varied surfaces, a real
shape hierarchy, a semantic token tier, a warm identity instead of a tinted near-black — is a
direct answer to that list, and a future edit that quietly re-flattens it back toward one of these
markers is the regression this section exists to name.

## 9. Brand mark

**What it is.** "Two corners" (`design/brand/marks.py`, `CHOSEN = "two-corners"`), the founder's
own pick from a selection gallery on 2026-09-15, over a week before Amber was chosen. Two L-shaped
registration corners on the adaptive icon's 108-unit viewport, top-left and bottom-right, on
opposite diagonals, with an empty centre between them: "the place the app keeps around a figure it
has not printed," the mark's own docstring says, echoing section 1.1's liquidity floor before
either Amber or this document's own restyle existed. Four axis-aligned rectangles, Amber's own
dark-set ground (`#16130D`, `AmberDarkColors.surfaceGround`) on Amber's own dark-set accent as the
tile (`#FFC247`, `AmberDarkColors.actionFill`, `amber_action` — `#F5EEDD`/`amber_ink` from
2026-09-15 through 2026-09-24, see "Two corners, refit" below); `BrandAssetsTest` pins both the
geometry (rotationally symmetric about the centre, never mirror-symmetric top to bottom, so a
flattened, one-colour silhouette still reads as itself rather than collapsing into a plus sign,
and every corner inside the 36-unit circle a launcher actually cuts) and the two colours directly
against `AmberDarkColors`. The same rectangles reappear as the splash icon (in
Amber's own ink, over Amber's own ground as the window background) and the notification icon (in
white, fitted to the 24dp status-bar viewport).

**Judgement, looked at on Amber's ground rather than transcribed, and since fixed (2026-09-22).**
The arrangement survives: two registration corners around a deliberately empty centre is a shape,
not a palette, and the idea it carries, a kept place where a figure is not printed rather than one
filled with a guess, still reads as this product's argument regardless of which colour system sits
behind it. What did not survive was the colour execution, and it did not survive for a reason more
specific than "it looks different now."

`Ink` and `Canvas`, the pair this section used to name here, are Instrument's own tokens, and were,
as of this restyle, colours that appeared nowhere else the app drew: Amber's actual grounds are
warm, `#16130D` dark and `#FFFBF2` light, against Instrument's cool near-black `#0B0F14` and cool
near-white `#E8ECF1`. The choice of `Ink` over `Canvas` was argued carefully at the time, but the
argument was made entirely inside Instrument's old, flat nine-token section 2: four ground
treatments were composited into a real drawer screenshot and measured across the tile edge
(`Canvas` 1.04:1, an accent field 7.23:1, a cool off-white field 16.79:1, `Ink` 15.48:1), and the
off-white option, the one closest in spirit to Amber's own warm light ground, was rejected
specifically because it "costs two colours that are not in DESIGN.md section 2," a constraint that
no longer described the palette this document now governs.

**The fix.** The mark itself was not touched, only which two Kotlin colours the same two roles
(mark fill, tile ground) read: Amber's own dark-set `surfaceGround` (`#16130D`) replaces `Canvas`,
and Amber's own dark-set `textPrimary` (`#F5EEDD`) replaces `Ink`, in exactly the pairing the 2026-
09-15 drawer measurement picked (a near-black figure on a near-white tile), now in Amber's own warm
hex rather than Instrument's cool one. `res/values/colors.xml` carries them as `amber_ground` and
`amber_ink` (`design/brand/marks.py`'s `AMBER_GROUND`/`AMBER_INK`, the same names `glyph.py`
regenerates `ic_launcher_foreground.xml`, `ic_brand_mark.xml` and `ic_launcher_background.xml`
from), and `Theme.PlainTicker`/`Theme.PlainTicker.Starting` in `themes.xml` now paint
`@color/amber_ground` instead of `@color/canvas` for the plain window background and the splash,
so the launcher tile, the splash and the first Compose frame all agree on one pair rather than the
splash and the plain window background staying on Instrument's cool near-black while the live
`AmberTheme` content painted a warm one a moment later. That was the cool-to-warm flash in light
mode section 9 used to describe here: `AmberTheme`'s light `surfaceGround` is a warm cream, and a
splash window still painted in Instrument's cool near-black flashed into it rather than resolving
with it. `BrandAssetsTest` now pins the launcher, the splash and both starting-theme colour items
against `AmberDarkColors.surfaceGround`/`textPrimary` instead of the retired `Canvas`/`Ink` Kotlin
tokens, so a future edit to either can no longer leave the icon behind the way this one did.

The new pair's own contrast, computed the same WCAG relative-luminance way `AmberContrastTest`
computes every other ratio in this document, is 16.0:1 (`AmberDarkColors.textPrimary` over
`AmberDarkColors.surfaceGround`, the same figure that test already pins independently for that
exact pair) — not a fresh photograph of the real Seeker drawer the way the 2026-09-15 figures
above were, since this fix had no device to retake one with, but the same formula every other
number in this section already answers to.

**One more fact, unrelated to colour but worth stating beside it, as of this fix (2026-09-22):**
the mark's own corners sit 39.60 units from centre, well past the 33-unit circle every earlier
mark was checked against, and clear the real superellipse mask a launcher actually cuts by only
0.81 of a unit (two device pixels on the Seeker). This is a property of the arrangement, not the
colour, and nothing above changes it, but it means the mark is already at the edge of its own safe
zone, which narrows how much room a future revision has to move these corners at all, whatever it
does with colour. (2026-09-24: a future revision did move them, closing exactly this; see "Two
corners, refit" below.)

**What this is not.** Not a recommendation to redraw it: that is a judgement about identity and
cost the founder should make deliberately, against a concrete replacement, the same way section 10
asks for `1.5.0-alpha` to be decided against a concrete component rather than reached for by
default. What changed here is narrower: the arrangement still works and now the colour pair does
too, on Amber's own values instead of Instrument's retired ones, closing the mismatch that used to
be visible at the launcher and at cold launch in light mode.

**2026-09-24: "Two corners, refit."** An audit of both repos (a comparison page built against the
live SVG geometry, not a mockup) found the mark's own drawing still fine but two things still
wrong with it, both from 2026-09-22's colour-only fix: the launcher tile carried Amber's colours
without any amber in it (`amber_ink`, a cream near-white, so on a Seeker drawer the app was one
more white tile among Google's, holding what reads as a crop button), and the "one more fact"
noted just above — the corners sitting 39.60 units out, inside the superellipse a real launcher
cuts but past the plain 36-unit circle (`VISIBLE / 2`) a "Pixel-style" one cuts — was still true, a
cost accepted rather than closed. Three directions were drawn against that finding: keeping this
mark and fixing both (direction A, "Two corners, refit"), a departures-board split-flap "P"
(direction B), and two rectangles beside each other reading as sector-vs-stock (direction C, which
an earlier round had already lost to reading as a split-screen toggle button). The founder picked
A: the mark the app already shipped, the one the audit found nothing wrong with as a drawing, is
still this product's own argument (a kept, empty place where a figure is not printed) regardless
of which two numbers move under it.

**The fix, in `design/brand/marks.py`'s `TWO_CORNERS`.** Two numbers, not a redraw. The four
rectangles pulled in from a 26-to-82 block with a 14-unit arm to a 31-to-77 block with a 12-unit
arm, which moves the furthest corner from 39.60 units out to 32.53: inside not only the
superellipse `MASK_EXPONENT` already cleared but now also that plain 36-unit circle, and even the
deprecated 33-unit `SAFE_RADIUS` this file used to report clearing by nothing at all. And
`Mark.ground` moved a second time, off `AMBER_INK` (`amber_ink`, `AmberDarkColors.textPrimary`)
onto `AMBER_ACTION` (`amber_action`, `#FFC247`, `AmberDarkColors.actionFill`, Tokens.kt
`AmberPrimitive.fillDark`): the corners themselves stay `AMBER_GROUND`
(`AmberDarkColors.surfaceGround`), the same near-black fill they always carried. Contrast against
the new tile is 11.5:1 by the same WCAG formula `AmberContrastTest` uses for every other ratio in
this document — `AmberContrastTest`'s own pinned `actionText`-over-`surfaceGround` figure for this
exact pair, read either direction. `glyph.py` regenerates `ic_launcher_foreground.xml`,
`ic_launcher_monochrome.xml`, `ic_brand_mark.xml` (the splash, unaffected by the tile move: it
paints Amber's own ink over Amber's own ground, never the launcher tile) and
`ic_stat_plainticker.xml` from the same geometry, and `res/values/colors.xml`/
`ic_launcher_background.xml` from the same `Mark.ground`, so none of the five can drift from the
other four. `BrandAssetsTest` pins the new tile against `AmberDarkColors.actionFill` and asserts
every corner of the shipped drawable sits inside the 36-unit circle, not only the superellipse.

**The store images and the app's own wordmark, in `design/brand/render_icons.py` and
`ui/theme/Type.kt`.** `store/icon-512.png` and `store/banner-1024x500.png` were still Instrument's
retired cool `#E8ECF1`/`#0B0F14` and the Outfit wordmark, rendered before even the 2026-09-22
colour fix and never re-run; `render_icons.py` now reads the shipped drawable (so it cannot drift
from it either) and sets the banner's own wordmark and line in the bundled Bricolage Grotesque,
instantiated at each size's own `wght`/`wdth`/`opsz` the way `Type.kt`'s `bricolage()` already
does for every `AmberType` style. And the app's own `TopBar` wordmark, `PlainTickerType.wordmark`,
moved off Outfit SemiBold 15sp onto Bricolage 700: the audit's own finding was that plain Bricolage
was already right and only the app had not moved onto it, since the web's TopNav lockup and both
OG images already set "PlainTicker" in Bricolage Bold. Same 15sp point size, no manual tracking
(Bricolage's own spacing, the same choice every `AmberType` style already makes). `TopBarTest`
proves the wider glyphs still clear the bar's own one-line clipping budget against the widest real
action label beside them ("Watching"), measured with fontTools against the bundled variable
Bricolage at this exact instance, at font scale 1.0 and 1.3 — the same method `AmberTickerRowTest`
uses, never a uiautomator dump.

**What this still is not.** Still not a redraw, and still not the founder's call to make by
default: directions B and C above are recorded (their own SVGs sit beside A's in the audit) for if
a real rebrand is ever decided against a concrete alternative, the same standard section 10 holds
`1.5.0-alpha` to. This fix is narrower again: the arrangement, unchanged since 2026-09-15, now
also clears the mask a real launcher cuts with room to spare, and the tile, the store images and
the app's own wordmark all read as the one product the web already does.

## 10. Material3 Expressive: what 1.4.0 actually has

Checked directly against `material3-android-1.4.0.aar` in this machine's Gradle cache (unpacked
and read as class listings, not trusted from a web search or from the research doc's own account
of it), because the research doc's finding here was itself a correction of web summaries that had
placed these APIs in alpha.

**Present in 1.4.0, no experimental opt-in annotation on any of them**: `ShortNavigationBar` and
its item and defaults classes; `MaterialExpressiveTheme`; `expressiveLightColorScheme()`.
`AmberDarkColorScheme` and `AmberLightColorScheme` (`Theme.kt`) do not use `MaterialExpressiveTheme`
or the expressive colour-scheme function, for a reason the research did not have occasion to check:
`expressiveLightColorScheme()` takes no parameters at all, so it cannot carry Amber's own palette,
and there is no `expressiveDarkColorScheme()` in this jar at any visibility. Amber's colour schemes
are built the same way `PlainTickerColorScheme` already is, with the stable `darkColorScheme` and
`lightColorScheme` constructors and every slot mapped by hand. This costs no new dependency and
uses nothing experimental.

**Absent from 1.4.0, confirmed by class listing**: `ButtonGroup`, `LoadingIndicator`,
`FlexibleBottomAppBar`, and any shape-morphing class. These exist only in `1.5.0-alpha` (alpha28,
2026-09-09).

**Nothing in this foundation pass needed any of the absent APIs.** If a later restyle pass wants a
chip that morphs shape on selection, or `LoadingIndicator`, or `ButtonGroup`, that is the moment to
decide about `1.5.0-alpha`, not now: this is a minified release build seven days from a feature
freeze, and an alpha dependency there is a real gamble that should be a founder decision made
against a concrete component, not a default reached for because it was convenient. Recorded here,
not acted on.

## 11. Status, as of this document

Built in this pass, both themes: `AmberDarkColors` / `AmberLightColors` (semantic colour tokens),
`AmberType` (the type scale), `AmberDarkColorScheme` / `AmberLightColorScheme` / `AmberShapes` /
`AmberTheme` (the M3 wiring). `AmberContrastTest` pins every ratio section 2 states and the
tertiary-on-high rule; `AmberThemeTest` pins the colour-scheme mapping and the type scale.

**Not built in this pass, on purpose**: `AmberTheme` was not yet the app's active theme
(`MainActivity` still called `PlainTickerTheme`); motion tokens; the restyle of any existing
component or screen; the brand mark. Each was later-phase work, once this foundation existed for
it to build on.

**Since this paragraph was first written**, every item above but one has been built: `AmberTheme`
is now the app's live theme, wired to the system light/dark setting (`MainActivity`); the
components, layout and motion the paragraph deferred are what sections 4, 5 and 6 now describe, in
full, against the shipped code rather than a plan for it. The brand mark was the one holdout:
section 9 recorded why its arrangement still worked and why its colour pair, as shipped, did not,
and on 2026-09-22 the colour pair was moved onto Amber's own ground values (the shape is still the
founder's, untouched) so the launcher, the splash and the first Compose frame agree.
