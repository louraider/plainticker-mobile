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
are that content. Sections 2 and 3 (colour, typography) are Amber's, built in this pass. Sections
4, 5, 6 and 9 (components, layout, motion, the brand mark) are **not yet restyled**: the
composables that implement them today still run Instrument's anatomy, on purpose, because
building the token and type foundation first is what lets the next pass restyle every component
against one set of names instead of guessing. Section 8 (anti-patterns) is rewritten, because
Amber's own research overturns two or three of Instrument's specific bans by name.

Where this document says a thing is "not yet restyled," treat the components as they are: correct
for what they draw today, not a template for new work. Read `Tokens.kt`, `Type.kt` and `Theme.kt`
for what is actually built; this document explains why they look the way they do.

## 1. The liquidity floor and the app's own record

Unchanged in substance from Instrument. This is product and data-disclosure logic, not taste, and
nothing about the redesign touches it.

### 1.1 The liquidity floor

A tracking figure is drawn only where the pool behind it can carry one. Measured live on
2026-09-12 (`docs/data-map.md`): of the 157 analyzed xStocks on the list Jupiter priced 55; the 13
pools at or above $100k all tracked the NYSE close within 0.8 percent; the 6 between $10k and
$100k deviated plausibly; below $10k the quoted premium was arithmetic off a dead pool. The floor
is **$10,000**, and it lives in exactly one place, `TrackingQuality` in the data layer, so the
list row and the gauge can never disagree about it.

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

The wallet session does not survive process death: the MWA auth token lives in the adapter's
memory, so every cold open finds no wallet and never asks the chain. Where the chain has told the
screen nothing, the app's own record stands in the holdings slot, under four rules:

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

Persisting the session was the alternative and was not taken: the auth token is a bearer grant for
signing authority, and writing it to disk to make a screen look better is a security decision
taken for a cosmetic reason. The app's own record needs no network, no consent and no wallet, and
it is the only thing on a cold-open screen the app can vouch for itself.

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
| `surfaceHigh` | `#2E281C` | `#F3EBD6` | The highest surface: a selected chip, a sheet |
| `textPrimary` | `#F5EEDD` (16.0:1) | `#1F1A0E` (16.8:1) | Primary text and figures |
| `textSecondary` | `#C6BCA4` (9.8:1) | `#5A5240` (7.5:1) | Secondary text, context lines |
| `textTertiary(on)` | `#948B74` (5.5:1) | `#7A7059` (4.7:1) | Metadata; see the rule below |
| `border` | `#3A3324` | `#E2D9C2` | Hairlines, an active chip's border |
| `actionFill` / `actionOnFill` | `#FFC247` / `#3B2800` (8.8:1) | `#7A5600` / `#FFFFFF` (6.65:1) | Primary button fill and its text |
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

`body`, `button` and `meta` are not individually sized by the research; they hold the sizes every
direction in section 5.5 converges on. Every other size and weight is transcribed from section 5.5
directly (the ticker row, a number with its context, the section head).

## 4. Components — not yet restyled

Instrument's anatomy (ListRow, FactGrid, Track, Gauge, Sheet, the buttons) is what `ui/components`
still draws. Its radius-0 shape lock and its "every number in mono" rule are Instrument's, not
Amber's; do not carry them into new work. Amber's own shape and component language is in
`docs/design-research-2026-09-21.md` sections 5.3 and 5.5 (radii by hierarchy: 8dp chips, 16dp
list containers, 28dp for a status card or a sheet's top radius; `AmberShapes` in `Theme.kt`
already carries these). Restyling each component to read `AmberColors` and `AmberType` instead of
Instrument's tokens is the next phase's work.

## 5. Layout — not yet restyled

The shared information architecture (Today, Stocks, Vote, Portfolio, You; the liquidity floor's
`Tracked today` block; chapters replacing infinite scroll) is `docs/design-research-2026-09-21.md`
section 3. Detail's own layout order does not change in any direction the research drew, Amber
included; only the tokens and components under it do.

## 6. Motion — the two pieces the calendar cut, now built

Section 7's calendar named exactly two pieces of Amber's motion that would not land in the
research's eight-day slice: chip morphing and staggered entry. Both are now built, once the
founder decided the calendar had room after all; everything else section 5.3 draws under Motion
(the bar pill morphing width, a spring sheet entry) is still not built, for the same reason it
never was: it is components-and-motion-token work that belongs with the component that will use
it, and neither the bar nor the sheet was touched in this pass.

**Chip morphing** (`AmberChip.kt`, `shapeFor`). The corner radius animates from 8dp to full (16dp,
the same radius `CircleShape` draws at the chip's fixed 32dp height) with a spring
(`Spring.StiffnessMediumLow`, no bounce), gated by `rememberMotionEnabled()` the same way the two
screens below already gate theirs; `snap()` replaces the spring at animator scale 0. Built without
`1.5.0-alpha`: that library exists to morph shapes whose vertex topology disagrees (a star into a
circle), and this chip's two states are the same rectangle disagreeing on one corner value, so a
plain `animateDpAsState` over that one `Dp` reads identically on a phone at this size. Section
5.3's own risk line names the alpha as the exact temptation to refuse here; this is that refusal,
not an oversight. See `AmberChip.kt`'s doc on `shapeFor` for the full reasoning, and the report for
what would actually justify the dependency later.

**Staggered entry** (`TodayScreen.kt`, `amberBlockEntrance`). Today's blocks 1, 3, 4 and 5 (venue,
tracked, next up, footer) fade in and rise 8dp into place once, the first time each has something
to draw, 40ms apart, with the same no-bounce spring and the same `rememberMotionEnabled()` gate
YouScreen's identity reveal and PortfolioScreen's Total already use. Block 2, Yours, is
deliberately left still: it is `WatchlistContent`'s shared, per-ticker list, the shape of thing a
stagger reads wrong on even at a handful of rows, and the same composable a 830-row list elsewhere
in the app would generalize from if a per-item stagger habit started here. Nothing on Today depends
on motion to be legible: every block's un-animated state is already its settled one.

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

## 9. Brand mark — not yet restyled

Unchanged. The launcher and notification icons (`design/brand/marks.py`, `design/brand/glyph.py`)
are pinned to Instrument's `Ink` and `Canvas` tokens by `BrandAssetsTest`, and this pass does not
touch them: repainting the icon is a decision for whoever owns it next, not a side effect of a
token foundation. See `docs/fonts.md`'s Brand mark section for how the icon is generated.

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

**Not built in this pass, on purpose**: `AmberTheme` is not yet the app's active theme
(`MainActivity` still calls `PlainTickerTheme`); motion tokens; the restyle of any existing
component or screen; the brand mark. Each is a later phase's work, once this foundation exists for
it to build on.
