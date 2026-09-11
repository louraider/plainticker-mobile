# Design System: PlainTicker Mobile, "Instrument"

Decided 2026-09-11 after the taste-skill audit of the first canvas (which inherited PlainTicker web's warm paper, Geist and numbered section labels and read as the web product). The mobile app now has its own visual language. PlainTicker web's `DESIGN.md` is no longer the source of truth for this repo; product rules that are shared (no verdict words, trust first, neutral descriptive values) are restated here. Canvas of record: design/canvas/instrument.py (regenerate the artboards from it). Published canvas: https://claude.ai/code/artifact/e58d956f-3e18-4ce0-90c6-415f796c3bbd

## 1. Visual theme and atmosphere

A reading instrument, not a trading terminal and not a website. Cold near-black canvas as the default (the Seeker is an OLED phone opened from a dark wallet), quiet secondary text, one blue accent that means "interactive or live", and every number in a monospace face. Density is that of a daily app (Level 5): a first viewport shows one idea and its evidence, never a dashboard. Layout variance is moderate (Level 5): left-aligned stacks, an asymmetric hero (giant ticker, price below), blueprint grids for facts. Motion is minimal (Level 3): the only continuous motion on a screen is the live bar, and it stops when the data is not live.

The memorable things: the tracking gauge (a hairline with the NYSE close tick and the token tick), the breathing live bar beside "Live from the mint", the 64px monospace ticker, and the 1px blueprint grid that holds the token's facts.

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

Fonts are bundled as resources on Android (Outfit and JetBrains Mono, SIL Open Font License). On the canvas they load from Google Fonts.

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

- Portrait, single column, side padding 20dp, content width 372dp on the Seeker (412dp).
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

## 9. Brand glyph

Adaptive launcher icon: Canvas background, JetBrains Mono "P" in Ink with a 2dp Accent tick beneath it (the gauge tick), exported as vector paths; monochrome layer for themed icons; splash background Canvas with no branding image; notification small icon is the same glyph in monochrome.
