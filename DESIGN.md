# Design System: PlainTicker Mobile, "Instrument"

Decided 2026-09-11 after the taste-skill audit of the first canvas (which inherited PlainTicker web's warm paper, Geist and numbered section labels and read as the web product). The mobile app now has its own visual language. PlainTicker web's `DESIGN.md` is no longer the source of truth for this repo; product rules that are shared (no verdict words, trust first, neutral descriptive values) are restated here. Canvas of record: design/canvas/instrument.py (regenerate the artboards from it). Published canvas: https://claude.ai/code/artifact/e58d956f-3e18-4ce0-90c6-415f796c3bbd

## 1. Visual theme and atmosphere

A reading instrument, not a trading terminal and not a website. Cold near-black canvas as the default (the Seeker is an OLED phone opened from a dark wallet), quiet secondary text, one blue accent that means "interactive or live", and every number in a monospace face. Density is that of a daily app (Level 5): a first viewport shows one idea and its evidence, never a dashboard. Layout variance is moderate (Level 5): left-aligned stacks, an asymmetric hero (giant ticker, price below), blueprint grids for facts. Motion is minimal (Level 3): the only continuous motion on a screen is the live bar, and it stops when the data is not live.

The memorable things: the tracking gauge (a hairline with the NYSE close tick and the token tick), the breathing live bar beside "Live from the mint", the 64px monospace ticker, and the 1px blueprint grid that holds the token's facts.

### 1.1 The liquidity floor

A tracking figure is drawn only where the pool behind it can carry one. Measured live on 2026-09-12 (docs/data-map.md): of the 157 analyzed xStocks on the list Jupiter priced 55; the 13 pools at or above $100k all tracked the NYSE close within 0.8 percent; the 6 between $10k and $100k deviated plausibly (NFLXx -2.34 percent on $12.5k, XOMx -1.58 percent on $18.8k); below $10k the quoted premium was arithmetic off a dead pool (UBERx +152.13 percent on a pool of $80, APPx +89.34 percent on $34). The floor is **$10,000**, and it lives in exactly one place, `TrackingQuality` in the data layer, so the list row and the gauge can never disagree about it.

Above the floor nothing changes: the row keeps the signed premium against the NYSE close, Detail draws the gauge. Below it neither is drawn and the surface states the pool instead, in one short sentence a person can act on: "Pool holds $34, too thin to track" on the row's single meta line, "Pool holds $34, too thin to track the NYSE close" where the gauge would be. When Jupiter prices a token without reporting any depth, the honest reading is unknown rather than deep, so the premium is withheld there too and the line reads "Pool depth not reported" (on the gauge, "Pool depth not reported, tracking cannot be checked"). Money in these sentences is `Fmt.compactMoney`: "$34", "$12.5k", "$1.3M".

This is disclosure, not curation. Nothing is filtered out, no section is added and the sort is unchanged. The sentence is Muted on the row and Ink 2 under the price, never Caution: a shallow pool is a fact about the token, and section 2 keeps Caution for explicit issuer-control risk. The rule sits here rather than in section 7 because it decides whether the signature element of the product is drawn at all; section 7 governs how a sentence is worded, this governs whether a number exists on the screen.

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
- **TopTabs** List · Portfolio · Watchlist, 14 Outfit, active Ink with a 2dp Accent underline, inactive Muted, hairline below.
- **TodayStrip** one line of Outfit 13/500 Ink 2 under the tabs ("Today: 3 watched, next report TSLAx on Oct 22"), hidden when nothing is watched.
- **Gauge** hairline track, 1dp Muted tick at 50% for the reference, 2dp Accent tick for the token on a stated scale (0.5% full width by default); caption left in Outfit 13, value right in mono 13 Accent.
- **LiveBar** 2dp Accent vertical bar, breathing 2.4 s while live, static when landed or stale; label 14/600 Accent, meta 12 mono Muted.
- **FactGrid** two columns, 1dp Line gaps and border, cells on Canvas: label 13 Muted, value mono, sub line 13 Ink 2 (mono 12 when it carries numbers); the first cell may span both columns. Exactly as many cells as facts.
- **Track** label 15 Ink 2, value mono 20 and state word 13 Muted right, hairline track with a 2dp Ink marker. Never a filled bar.
- **SignalRow** 44dp, name 15 Ink 2 left, "yes" or "no" in mono 14 right.
- **ListRow** 64dp, ticker mono 18 + company 13 left with a mono 12 meta line, value mono 18 right; one Line divider between rows; text action trailing when needed.
- **Field** label above (13 Muted), value mono 36 or Outfit 16, 1dp Line strong underline, Accent underline on focus, one text action right (Max, Clear). No placeholder-as-label.
- **PrimaryButton** 56dp, Accent fill, Canvas text 16/600, radius 0. **SecondaryButton** Line strong border, Ink text. **DisabledButton** Line border, Muted text.
- **Sheet** Elevated surface, 1dp Line strong top edge, 28x2dp Line strong handle, radius 0.
- **Panel** Elevated with a Line border (digest, onboarding). The only card-like container; used for one grouped message, never for lists.
- **Skeleton** Elevated bars with a 200 ms fade to content. Never a spinner.
- **Banner** one slot under the TopBar, Elevated, Outfit 13/500; priority offline > stale > hours > device.

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

The mark is **Two corners**: two registration corners on the 108 adaptive icon viewport, top-left and bottom-right, and an empty centre between them. Four rectangles, radius 0, no accent. `x=26 y=26 w=34 h=14` and `x=26 y=26 w=14 h=34` for the top-left corner; `x=48 y=68 w=34 h=14` and `x=68 y=48 w=14 h=34` for the bottom-right. It is the founder's own choice out of their selection gallery, cell 4a, and the geometry is transcribed rather than derived. What it says is the thing the product does that nothing else in the drawer does: the place is kept and the figure is not printed, which is section 1.1's liquidity floor as a picture. It replaces the tracking gauge, which replaced the JetBrains Mono "P" shipped in DT3.

The tile is **Ink and the mark on it is Canvas**, which is the part four rejected icons got wrong. Every one of them put a Canvas tile on the Seeker's near-black drawer wallpaper, where it has no boundary at all: three pixels inside the launcher's own mask against three pixels outside it measures **1.03 to 1**, and a tile at 1.03 is not an object, it is a mark floating on the wallpaper. The same four rectangles were built on four grounds and every one composited into the real drawer and measured by `design/brand/attempt-five/composite5.py`: Canvas **1.04**, an Accent field with the mark knocked out **7.23**, a cool off-white field **16.79**, and this one **15.48**, against Photos at **18.06** for the top of the scale. The off-white reads a point and a third higher and costs two colours section 2 does not have; the Ink tile is the Ink-and-Canvas pair section 2 already rates at 15.9 to 1 with the tile taking the Ink side, so `BrandAssetsTest` can pin the whole icon to the Kotlin tokens. Inverting the accent buys an edge and pays for it by flattening the mark into a silhouette, and it was measured rather than argued about.

Three rules decide the drawing and all three were learned by getting it wrong. The first is size, not composition. A launcher shows the central 72 of the 108 viewport, so one viewport unit is 0.667dp at 48dp, where an icon is actually read; on the Seeker's real 60.7dp tile it is 0.84dp. The mark shipped in DT3 put its only distinguishing detail at 1.3dp and it was invisible on a launcher grid. So no shape is thinner than 6 units, which is 4dp at 48dp; the thinnest arm here is 14 units, 11.8dp on the Seeker's tile.

The second rule is that a mark may not mirror itself top to bottom. The gauge as first drawn on 2026-09-13 centred all three of its rectangles on y 54, and in colour that read as a gauge because the Accent tick pulled away from the Ink ones. The monochrome layer and the 24 notification icon have no colour to pull with, and both read as a plus sign, which is the most overloaded glyph on a phone. Colour can separate two shapes; a silhouette can only be separated by where its shapes point. Two corners passes that rule by being symmetric the other way: turned 180 degrees about the centre it is itself, flipped top to bottom it is not. The rejected drawing is frozen at `design/brand/candidates/crossed_*.xml`.

The third rule is the mask, and it changed for this mark. Every earlier mark was asserted inside the central 66 circle, radius 33, on the grounds that a launcher might cut a circle. These corners sit **39.60** units out, so that rule would refuse the founder's choice. The circle was always a proxy: the mask on the phone this ships to was lifted off a neighbouring tile in a drawer screenshot and fits a superellipse of exponent **3.05**, and the assertion is now that superellipse rounded down to 3.0, because the smaller exponent is the tighter shape. The corners clear it by **0.81 of a unit**, two device pixels and two thirds of a dp on the Seeker's real tile, so this mark sits at the edge of the mask and must not be pushed out any further. A launcher that cuts a true circle clips about **2%** of the mark and takes the outer right-angle point off both corners, leaving two bevelled chevrons; no Pixel-family launcher cuts one, the cost is accepted rather than discovered, and it is drawn at `design/brand/two-corners/gallery.html`.

The monochrome layer is the same four rectangles in one colour and never the field they sit on: a themed icon is one colour on a plate the launcher supplies, so the only thing that can carry the mark is the mark. The 24 notification icon is those rectangles scaled to the 20dp live area rather than a redrawn simplification, so the silhouette cannot drift from the launcher art. The splash needs a third layer, `ic_brand_mark`, because the foreground is drawn in Canvas for a light tile and Canvas on the Canvas splash background is nothing; it is the same paths in Ink, written by the same generator.

Implementation: `design/brand/marks.py` holds the geometry and its assertions (the mask, the 4dp floor, at most one accent, the mirror rule, and the ground the tile carries), `design/brand/glyph.py` writes `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_brand_mark.xml`, `ic_stat_plainticker.xml` and `res/values/ic_launcher_background.xml` from the mark named by `marks.CHOSEN`, and `design/brand/two-corners/measure.py` puts every ground in the real drawer and measures it into `design/brand/two-corners/gallery.html`. `res/mipmap-anydpi-v26/` holds the two adaptive icons over `@color/ic_launcher_background`, an alias of Ink; no bitmap icons ship. The splash is `Theme.PlainTicker.Starting` (core-splashscreen): Canvas background, `ic_brand_mark`, no branding image, then `Theme.PlainTicker`. `BrandAssetsTest` checks all of this from disk, including the 4dp floor, the mask, the mirror rule on every layer, and that the mark's fill differs from the ground it is drawn on.