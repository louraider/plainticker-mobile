# Design System: PlainTicker Mobile, "Instrument"

Decided 2026-09-11 after the taste-skill audit of the first canvas (which inherited PlainTicker web's warm paper, Geist and numbered section labels and read as the web product). The mobile app now has its own visual language. PlainTicker web's `DESIGN.md` is no longer the source of truth for this repo; product rules that are shared (no verdict words, trust first, neutral descriptive values) are restated here. Canvas of record: design/canvas/instrument.py (regenerate the artboards from it). Published canvas: https://claude.ai/code/artifact/e58d956f-3e18-4ce0-90c6-415f796c3bbd

## 1. Visual theme and atmosphere

A reading instrument, not a trading terminal and not a website. Cold near-black canvas as the default (the Seeker is an OLED phone opened from a dark wallet), quiet secondary text, one blue accent that means "interactive or live", and every number in a monospace face. Density is that of a daily app (Level 5): a first viewport shows one idea and its evidence, never a dashboard. Layout variance is moderate (Level 5): left-aligned stacks, an asymmetric hero (giant ticker, price below), blueprint grids for facts. Motion is minimal (Level 3): the only continuous motion on a screen is the live bar, and it stops when the data is not live.

The memorable things: the tracking gauge (a hairline with the NYSE close tick and the token tick), the breathing live bar beside "Live from the mint", the 64px monospace ticker, and the 1px blueprint grid that holds the token's facts.

### 1.1 The liquidity floor

A tracking figure is drawn only where the pool behind it can carry one. Measured live on 2026-09-12 (docs/data-map.md): of the 157 analyzed xStocks on the list Jupiter priced 55; the 13 pools at or above $100k all tracked the NYSE close within 0.8 percent; the 6 between $10k and $100k deviated plausibly (NFLXx -2.34 percent on $12.5k, XOMx -1.58 percent on $18.8k); below $10k the quoted premium was arithmetic off a dead pool (UBERx +152.13 percent on a pool of $80, APPx +89.34 percent on $34). The floor is **$10,000**, and it lives in exactly one place, `TrackingQuality` in the data layer, so the list row and the gauge can never disagree about it.

Above the floor nothing changes: the row keeps the signed premium against the NYSE close, Detail draws the gauge. Below it neither is drawn and the surface states the pool instead, in one short sentence a person can act on: "Pool holds $34, too thin to track" on the row's single meta line, "Pool holds $34, too thin to track the NYSE close" on Detail. When Jupiter prices a token without reporting any depth, the honest reading is unknown rather than deep, so the premium is withheld there too and the line reads "Pool depth not reported" (on Detail, "Pool depth not reported, tracking cannot be checked"). Money in these sentences is `Fmt.compactMoney`: "$34", "$12.5k", "$1.3M".

This is disclosure, not curation. Nothing is filtered out, no section is added and the sort is unchanged. The sentence is Muted on the row and Ink on Detail, never Caution: a shallow pool is a fact about the token, and section 2 keeps Caution for explicit issuer-control risk. The rule sits here rather than in section 7 because it decides whether the signature element of the product is drawn at all; section 7 governs how a sentence is worded, this governs whether a number exists on the screen.

**Withholding a number is a property of the whole screen, not of the number.** Read on the device on 2026-09-13, the shipped v0.2.0 Detail for APPx withheld the premium correctly and then printed the token at 40sp Ink directly above the NYSE close at 20sp Ink 2, with the pool sentence at 13sp between them, smaller than either figure. A reader subtracts $611.56 and $323.00 and arrives at the +89.34 percent unaided. Three things follow, and they are the rule on Detail below the floor:

- **The sentence is read first.** It stands above the pair, at body 15 in Ink, not under it in a caption. A caveat that arrives after the number it qualifies is not a caveat.
- **Neither figure leads.** Both are set at the reference's 20 mono, the token in Ink and the close in Ink 2, so colour separates them and size no longer stages a comparison. The 40sp hero price is for a quote the pool can carry.
- **The token's figure is named for what it is.** "Pool quote", not "Token price". By the floor's own argument, a reading off a $34 pool is not a price of the company; the label says so in the one place a reader is looking.

The gauge slot is then empty, and the screen is shorter. That is correct: there is nothing to draw there, and the same sentence in two places is how it drifts back under the pair.

### 1.2 The app's own record, where the chain has said nothing

The wallet session does not survive process death: the MWA auth token lives in the adapter's memory and the authorized account with it, so every cold open finds no wallet and never asks the chain. Read on the device on 2026-09-13, that left the Portfolio, the screen carrying the one piece of evidence this product actually owns, showing "Connect your wallet to see the xStocks in it." under its "Holdings" heading, while the real holding bought through the app, 0.01362917 TSLAx, sat 500dp further down filed under "Recent swaps" as a transaction.

What the app still knows is what it did. It writes a receipt the moment a swap lands, and those receipts net out to a quantity. So **where the chain has told the screen nothing, the app's own record stands in the holdings slot**, under four rules:

- **It is drawn only where nothing was read.** A connected wallet with positions draws the positions. A connected wallet the chain answered for and found empty draws the empty sentence, because the chain is the authority on what a wallet holds and a receipt is history, not a contradiction of it. What is left is the two states where no read happened at all: no wallet session, and a wallet whose chain read failed.
- **The sentence is read first**, above the figures, at body 15 in Ink: "What this app recorded when its own swaps landed. The wallet itself has not been read." That is section 1.1's rule, applied to a different screen for the same reason.
- **It carries a quantity and never a value.** No price is applied to it and it is not summed into the total. A recorded quantity multiplied by a live quote is half a chain read wearing the other half's clothes.
- **It never claims a confirmation.** A fill the execute answer did not report adds nothing rather than the quote it was estimated at, and the unreported fill is disclosed once, on the swap row under "Recent swaps", never twice.

Persisting the session was the alternative and was not taken, for two reasons rather than one. The auth token is a bearer grant for signing authority, and writing it to disk to make a screen look better is a security decision taken for a cosmetic reason. Persisting only the account address and reading the chain for it silently would show real data, but it would read a wallet that has not authorized this launch and leave the screen unable to say whether it is connected. The record needs no network, no consent and no wallet, and it is the only thing on the screen the app can vouch for itself.

## 2. Color palette and roles

- **Canvas** `#0B0F14`: page background. Cool near-black, never pure black.
- **Elevated** `#121820`: sheets, the onboarding panel, the digest panel. The only second surface.
- **Ink** `#E8ECF1`: primary text and numerals.
- **Ink 2** `#B4BCC8`: secondary text, labels of facts, body copy.
- **Muted** `#7F8A99`: metadata, placeholders, disabled text, inactive tabs.
- **Line** `rgba(232,236,241,0.10)`: hairlines, grid gaps, row dividers.
- **Line strong** `rgba(232,236,241,0.22)`: field underlines, sheet top edge, secondary button border, gauge track.
- **Accent** `#5AA9E6`: every interactive text action, the active tab indicator, the primary button fill, the live bar, the token tick on the gauge. Nothing else.
- **Caution** `#D9A441`: only the value of an explicit issuer-control risk (permanent delegate present, transfers pausable). Never on prices, premiums, scores or list rows.

Rules: exactly one accent. No green or red for price direction anywhere; direction is a signed monospace number in Ink. No gradients, no shadows, no glass. Contrast: Ink on Canvas 15.9:1, Ink 2 on Canvas 9.6:1, Muted on Canvas 5.1:1, Canvas text on Accent 7.9:1.

A light variant is deferred (TODOS.md). If it is ever built it must keep the hierarchy above, not invert it: the accent stays `#5AA9E6`-adjacent, the canvas becomes a cool off-white, never cream.

## 3. Typography

- **UI face:** Outfit (400, 500, 600). Words, labels, buttons, headings.
- **Numeral face:** JetBrains Mono (400, 500) with tabular numerals. Every number, every ticker symbol, every wallet or signature fragment, every timestamp. Numbers never appear in Outfit.
- Scale (sp): hero ticker 64 mono 500 tracking -0.035em · hero price 40 mono 500 -0.03em · big value (total, received) 40 mono 500 · section heading 20 Outfit 600 -0.01em · fact value 22 to 32 mono 500 · track value 20 mono 500 · list ticker 18 mono 500 · body 15 Outfit 400 line-height 23 · label 13 Outfit 500 Muted · meta 12 mono 400 Muted · button 16 Outfit 600 · text action 14 Outfit 600 Accent.
- No uppercase transforms, no positive letter-spacing labels, no section numbers, no em or en dashes in any visible string (use a period, comma, colon or hyphen). Sentence case everywhere. Font scale honored to 1.3x; numerals never wrap (single line, autosize down on the hero row only).

Fonts are bundled as resources on Android (Outfit and JetBrains Mono, SIL Open Font License). On the canvas they load from Google Fonts. Bundled versions, upstream sources and license files are listed in docs/fonts.md; the OFL texts ship in app/src/main/assets/licenses/.

## 4. Components (Compose names)

- **TopBar** 56dp after the status inset: "PlainTicker" 15/600 left; one text action right (Watch, wallet fragment). Scrolls away, never sticky.
- **TopScrim** the only thing over a scrolling surface that does not scroll, and it is not content: full Canvas across the status bar inset, then a 16dp fall to nothing. It draws no text, takes no touch and has no semantics, so the header still scrolls away and nothing is sticky. It is Canvas over Canvas, so at rest it is invisible; it appears only when something that is not the page background passes under the clock. Read on the device on 2026-09-13, the 64sp Ink hero and the white system clock were drawing in the same pixels, "NVDAx" over "11:40", because the canvas artboards are 890dp content frames with no system bars in them and the state was never looked at. One scrim per scrolling surface: the three tabs share it through their host, Detail owns its own.
- **TopTabs** List · Portfolio · Watchlist, 14 Outfit, active Ink with a 2dp Accent underline, inactive Muted, hairline below.
- **TodayStrip** one line of Outfit 13/500 Ink 2 under the tabs ("Today: 3 watched, next report TSLAx on Oct 22"), hidden when nothing is watched.
- **Gauge** hairline track between two 1dp Line strong end stops, 1dp Muted tick at 50% for the reference, 2dp Accent tick for the token on a stated scale; caption left in Outfit 13, value right in mono 13 Accent. The default scale is **plus or minus 2.5%**, which is the spread the tracked set actually produced (section 1.1 and `TrackingQuality.TRACKED_SPREAD_PCT`), not a figure chosen on the canvas: at 0.5% the shipped build pinned NVDAx, the deepest pool in the catalogue, against the left end on an ordinary day. A premium past the scale stands its tick 6dp clear of the end of the track and the caption reads "past the 2.5% scale". The gauge never rests a tick on an end: a gauge that saturates silently states a wrong number confidently, which is worse than no gauge. Below the liquidity floor the gauge draws nothing at all; the price block above it has already stated the pool.
- **LiveBar** 2dp Accent vertical bar, breathing 2.4 s while live, static when landed or stale; label 14/600 Accent, meta 12 mono Muted.
- **FactGrid** two columns, 1dp Line gaps and border, cells on Canvas: label 13 Muted, value mono, sub line 13 Ink 2 (mono 12 when it carries numbers); the first cell may span both columns. Exactly as many cells as facts.
- **Track** label 15 Ink 2, value mono 20 and state word 13 Muted right, hairline track with a 2dp Ink marker. Never a filled bar.
- **SignalRow** 44dp, name 15 Ink 2 left, "yes" or "no" in mono 14 right.
- **ListRow** 64dp, ticker mono 18 + company 13 left with a mono 12 meta line, value mono 18 right; one Line divider between rows; text action trailing when needed. The value and the state word are two columns, not one right-aligned group: the word is set at the start of a 40dp column (the widest of "strong", "weak" and "fair" is 37.3dp, measured on the device) and grows with the reader's font scale, so the number's right edge is the same on every row. Right-aligned as a group, the word's width decided where the number began: "84" ended at x1004, "79" at x1053 and "72" at x1024 on the Seeker, a 16dp jog down the one column a reader scans. A section where some rows carry no word keeps the column open anyway (`reserveValueSub`), so the odd row lines up with its neighbours.
- **Field** label above (13 Muted), value mono 36 or Outfit 16, 1dp Line strong underline, Accent underline on focus, one text action right (Max, Clear). No placeholder-as-label.
- **PrimaryButton** 56dp, Accent fill, Canvas text 16/600, radius 0. **SecondaryButton** Line strong border, Ink text. **DisabledButton** Line border, Muted text.
- **Sheet** Elevated surface, 1dp Line strong top edge, 28x2dp Line strong handle, radius 0.
- **Panel** Elevated with a Line border (digest, onboarding). The only card-like container; used for one grouped message, never for lists.
- **Skeleton** Elevated bars with a 200 ms fade to content. Never a spinner.
- **Banner** one slot under the TopBar, Elevated, Outfit 13/500; priority offline > stale > hours > device. The hours tier is drawn on the List as well as on Detail, out of the same four strings and decided from the same `MarketHours`: the List prints a premium "vs NYSE close" on every tracked row, so it owes the same caveat the screen one tap away pays, and until 2026-09-13 it paid nothing. The List reads the venue from the first catalog asset carrying a `trading` block, since every block describes the one exchange, and clears the halt flag on the way: a halt is one issuer stopping one token and is Detail's to state, never a sentence about 157 rows.

Shape lock: radius 0 on everything. Touch targets 48dp minimum; list rows 64dp; text actions get 14dp vertical padding.

## 5. Layout principles

- Portrait, single column, side padding 20dp, content width 360dp on the Seeker. The device was measured on 2026-09-12: 1200x2670 physical at 480dpi, which is 400dp wide and 890dp tall. Everything here was drawn against a 412dp frame before that, so a layout tuned to the old 372dp content width has 12dp less room than its mockup. Previews at 360 and 412 bracket the real width.
- Sections are separated by headings and space (32dp above, 14dp below), not by rules; hairlines live inside grids and lists only.
- Detail order is fixed: header, hero (ticker, company), price row with the NYSE close, gauge, live bar, Backing and controls (FactGrid), Against the sector (three Tracks + FactGrid), F-Score (numeral + SignalRows), Method (body), then the single Swap button with a mono cost line. Nothing is sticky.
- First viewport of Detail ends inside "Against the sector" so the frame is full; never a blank bottom on a phone-height frame.
- One layout family per section: hero, gauge, grid, tracks, signal list, prose. No section repeats its neighbour's layout.

## 6. Motion and interaction

- LiveBar breathes (opacity 1 to 0.45, 2.4 s ease-in-out) only while data is live; the Receipt's bar is static.
- Skeleton to content 200 ms ease-out; Track marker settles 400 ms cubic-bezier(.2,.8,.2,1); sheet uses the default slide, no bounce.
- Pressed state: Elevated background plus an Accent ripple at 10%. Focus (keyboard or switch access): 2dp Accent outline, never removed.
- Reduced motion (animator scale 0): breathing off, marker instant.
- One haptic (Confirm) when a swap lands. No sound.

## 7. Copy and content rules

- Sentence case; buttons are verb plus object ("Swap USDC to TSLAx", "Read the list", "View in Portfolio").
- Never BUY, SELL, HOLD or AVOID as words on any surface, including the swap sheet: the direction flip is "TSLAx to USDC". "Swap" is the only trading verb.
- Descriptive values stay in Ink; Caution only on explicit issuer-control risk. The premium is a signed mono number, never colored.
- At most one middle dot per line; prefer commas, periods and line breaks. No exclamation marks, no emoji, no icons drawn by hand; if an icon is ever needed it comes from one library (Phosphor) at one stroke width.
- Numbers formatted by one Fmt object (en-US): prices 2dp (4dp under $1), percents signed 2dp, counts with commas, token amounts up to 6dp trimmed, absolute times in UTC, relative times "2 s ago", "3 h ago", "2 d old".
- Banned words: seamless, powerful, unlock, empower, journey, insights, supercharge, effortless, all-in-one, welcome to.

## 8. Anti-patterns (never)

Cream or paper backgrounds; Geist or Inter; numbered section labels; uppercase tracked eyebrows; em dashes; cards for lists; three equal tiles; filled progress bars; red and green price blocks; colored dots as decoration; gradients, shadows, glass, purple; spinners; snackbars; bottom navigation bars; sticky headers; hover-only states; text-only pages when a real number could be shown.

## 9. Brand mark

The mark is the tracking gauge of section 1, not a letter. A letter in a rounded tile is the default shape of every indie app icon and carries no memory of this product; the gauge is the one mark in the app nobody else has, and it says what the app does. Three rectangles in the 108 adaptive icon viewport: the **track** 56 wide and 8 high across the middle in Ink at y 54 to 62, the **reference graduation** 8 by 14 in Ink hanging under its centre where the NYSE close sits, and the **token tick** 10 by 36 in Accent at 64 to 74, off that centre and standing over the track, crossing it. The two ticks point opposite ways deliberately, for the reason in the third paragraph. Radius 0 everywhere; the launcher applies its own mask. Nothing else, and never a second accent.

Two rules decide the drawing, and both were learned by getting it wrong. The first is size, not composition. A launcher shows the central 72 of the 108 viewport, so one viewport unit is 0.667dp at 48dp, where an icon is actually read. The mark shipped in DT3, a JetBrains Mono "P" with a 2 unit Accent tick under it, put its only distinguishing detail at 1.3dp and it was invisible on a launcher grid; it was judged on the App info screen, where the icon is large. So no shape is thinner than 6 units, which is 4dp at 48dp, and the block stays inside the 66 safe circle (corner radius 29.73 of 33) and centred, because the mask can be a circle, a squircle or a rounded square.

The second rule is that a mark may not mirror itself top to bottom. The gauge as first drawn on 2026-09-13 centred all three rectangles on y 54: track 50 to 58, reference 44 to 64, token 32 to 76. In colour that reads as the gauge, because the Accent tick pulls away from the Ink ones. The monochrome layer and the 24 notification icon have no colour to pull with, and both of them read as a plus sign, which is the most overloaded glyph in a launcher and means "add" everywhere else on the phone. Colour can separate two shapes; a silhouette can only be separated by where its shapes point. So the token crosses the track and the reference only hangs under it, and `Mark.mirrors_itself` refuses any construction that is symmetric about its own middle. The rejected drawing is frozen at `design/brand/candidates/crossed_*.xml` and drawn in the comparison sheet, so the difference can be looked at rather than read about.

The monochrome layer is the same three rectangles in one colour: a themed icon has no colour to lean on, so the reference and the token have to differ by height, by position and by direction, which they do. The 24 notification icon is those rectangles scaled to the 20dp live area rather than a redrawn simplification, so the silhouette cannot drift from the launcher art.

Implementation: `design/brand/marks.py` holds the geometry and its assertions (the safe circle, the 4dp floor, one accent, and the mirror rule), `design/brand/glyph.py` writes `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` and `ic_stat_plainticker.xml` from the mark named by `marks.CHOSEN`, and `design/brand/render_icons.py` draws every candidate at 48dp, 72dp and 108dp under both masks on both grounds into `design/brand/icon-candidates.png`. `res/mipmap-anydpi-v26/` holds the two adaptive icons over `@color/ic_launcher_background`, an alias of Canvas; no bitmap icons ship. The splash is `Theme.PlainTicker.Starting` (core-splashscreen): Canvas background, the same foreground vector as the icon, no branding image, then `Theme.PlainTicker`. `BrandAssetsTest` checks all of this from disk, including the 4dp floor and the mirror rule, the latter on all three layers.
