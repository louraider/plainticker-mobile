<!-- Adopted verbatim from PlainTicker (louraider/investor24-analyst, DESIGN.md) on 2026-09-10.
     The mobile app inherits this design system by decision (docs/plan-2026-09-10.md §2).
     Web-only sections (Tailwind, next/font loading, CSS variables) map to Compose equivalents:
     Geist Sans/Mono bundled as font resources; tokens as Color/Typography objects; dynamicColor = false.
     Keep this file in sync with the source; do not fork tokens here. -->

<!-- ============================================================================
     MOBILE DELTAS — decided in /plan-design-review 2026-09-10 (docs/plan-2026-09-10.md §13).
     Everything below this block is the web source of truth; these are the only places
     the Android app (Compose, ~412dp-wide Seeker, portrait) deliberately differs.

     THEME   One theme: warm paper (light) forced. Dark deferred (TODOS.md) behind an a11y pass.
     TYPE    Hero ticker 56sp Mono 500 (web 96–220) · Hero price 28 Mono tnum (48) · Section title 22 Sans 500 (28)
             Section num/meta 11 Mono uppercase +0.08em · Metric value 22 Mono tnum (28) · F-Score numeral 56 Mono (96)
             Strata value 18 Mono (28) · Body 16 Sans lh 1.5 · Small 14 · Wordmark 11 Mono uppercase tracked
             Honor system font scale to 1.3×; numbers never wrap.
     SPACING Page side 16dp · section 32 top / 24 bottom (web 48/48) · row vertical 12 (16) · interactive rows ≥56dp
             static rows ≥44dp · header 56dp + status inset · hairline 1dp --rule · button 56dp high, 4dp radius · no max width
     LAYOUT  Rows and rules, never cards (no Card, elevation 0). Top text tabs (Mono uppercase, 2dp ink indicator), no bottom
             bar, no sticky TopNav (header scrolls away). Detail hero is ONE row: ticker left, price right.
     COMPONENTS (Compose) WordmarkMeta · TopTabs · SectionHead · MetricRow · StrataRow (label above a 2dp track + 2dp marker)
             SignalList · LiveSignature (6dp positive pulse — the ONLY pulse on a screen; the web Hero asOf pulse is not ported)
             StateBanner (one slot: offline > stale > hours > device) · DatelineStripe · Skeleton (never a spinner)
             SwapSheet (ModalBottomSheet, 4dp corners, 2dp rule as drag handle — no M3 pill) · Receipt · InkButton
     SECTIONS (English) Detail 01 — TRUST · 02 — QUALITY · 03 — VALUATION · 04 — MOMENTUM · 05 — F-SCORE · 06 — METHOD
             List 01 — ANALYZED · 02 — WITHOUT ANALYSIS · Portfolio 01 — HOLDINGS · Watchlist 01 — WATCHED
     MOTION  skeleton→content 200ms ease-out · strata marker 400ms cubic-bezier(.2,.8,.2,1) · pulse 2s · sheet default slide,
             no bounce · reduced motion → pulse off, marker instant · no entrance choreography, no parallax
     TOUCH   hover bg-elev → pressed bg-elev + ripple ink @10% · targets ≥48dp · list rows 56dp
     M3      Every colorScheme slot set from tokens (primary=ink, surface=paper, error=danger, outline=rule; checkbox/ripple/
             cursor/selection=ink). Shapes 4dp. No Snackbar. Unit test: no scheme color equals M3 baseline #6750A4.
     COPY    Sentence case (mono meta uppercase); verb+object buttons; banned: seamless, powerful, unlock, empower, journey,
             insights, supercharge, effortless, all-in-one, welcome to; no "!", no emoji; em dash only inside "NN — LABEL";
             no BUY/SELL/HOLD on analysis surfaces or notifications; "Swap" is the sheet verb. Enforced by a strings lint.
     TONE    Descriptive values neutral ink; semantic tone only on explicit risk lines (permanentDelegate, pausable,
             |premium| > 1%, reference unavailable). priceChange24h is never rendered.
     ============================================================================ -->

# Design System — PlainTicker

Created: 2026-05-02 by `/design-consultation`
Calibrated against: `/plan-design-review` 2026-05-02 (13 design decisions locked)
v3 «Стратиграфія» migration: 2026-05-05 — see `pre-build/redesign-v3-spec.md` for source of truth.
Status: ACTIVE — v3 redesign shipped 2026-05-05.

## Product Context

- **What this is:** Ukrainian-language ticker→1-pager analysis tool for US/UK stocks. Rendered Ukrainian fundamentals analysis з sector-relative context, не investment advice.
- **Who it's for:** Андрій (28-40, IT/middle-management, IBKR/Freedom24 retail, $5K-50K portfolio). Reads Ukrainian/Russian, не вільний з англомовним фін-текстом. Хоче 30-секундну орієнтацію перед buy/sell decision.
- **Space/industry:** Ukrainian/CEE retail finance education + analytics (NOT US fin-tools competing).
- **Project type:** Web app (RSC ticker pages) + marketing-adjacent (homepage, methodology page).
- **Brand:** `PlainTicker` (нейтральний, rebrand 2026-07-06; історичні згадки нижче — архівні).
- **URL:** `analyse.investor24.com.ua/uk/[TICKER]` — i18n-ready.

## Memorable Thing

**«Це серйозний інструмент для думливих інвесторів».** Trust-first analytical gravitas, but read like geological strata — three independent layers (Якість / Оцінка / Момент) standing visible at once, не collapsed into one verdict number. Editorial rhythm із numbered SectionHead dividers replaces dashboard chrome.

Every design decision should serve this memorable thing. If a choice would push the page toward "playful experiment" (sibling /buffett/ territory) або "feature-bloated dashboard" (stockanalysis.com territory) — reject it.

## Aesthetic Direction

- **Direction:** Editorial × Industrial. Stratigraphic — layered horizontal bands (Perspective), numbered chapter dividers (SectionHead), large mono numerals (96-220px Geist Mono). Reading-tool feel, not trading-dashboard.
- **Decoration level:** minimal-intentional. Color appears у three semantic tones (positive/caution/danger) on Perspective markers, F-Score signal cards, MetricRow context, EarningsHighlight beat/miss. Charts allowed (sparklines + composite-bars). Soft OKLCH semantics. (PeerTable — toneless since 2026-06-09 neutral rebuild.)
- **Mood:** calm, authoritative, paper-like. Editorial restraint with confident large-mono headers.
- **Reference sites (for visual calibration):**
  - investor24.com.ua (parent brand — inherit nav structure, footer patterns)
  - investor24.com.ua/buffett/ (sibling sub-brand — learn divergence point)
  - ft.com (article density + restraint, numbered chapters)
  - stripe.com docs (large mono numerals, generous whitespace)
  - **NOT to emulate:** Robinhood, Yahoo Finance, stockanalysis.com (feature-bloated), Simply Wall St (chart-heavy)

## Typography

- **Display + body:** **Geist Sans** — Ukrainian-Cyrillic native, free, modern editorial weight. Loaded via `geist/font` next/font integration (self-hosted WOFF2, subset latin + cyrillic). Replaced IBM Plex Sans 2026-05-05.
- **Numbers + tickers + tabular:** **Geist Mono** — `font-feature-settings: "tnum" "ss01"` for scannable metric rows + ticker SS01 single-storey "a". Replaced IBM Plex Mono.
- **NEVER as primary:** Inter, Roboto, Arial, Helvetica, Open Sans, system-ui, Space Grotesk, IBM Plex (legacy).

### Loading strategy

```ts
// app/layout.tsx
import { GeistSans } from "geist/font/sans";
import { GeistMono } from "geist/font/mono";

export default function RootLayout({ children }) {
  return (
    <html lang="uk" className={`${GeistSans.variable} ${GeistMono.variable}`}>
      ...
    </html>
  );
}
```

Tailwind v4 maps `--font-sans` / `--font-mono` to these CSS variables — components reference `font-sans` / `font-mono` utilities.

### Type scale

| Role | Size | Line | Weight | Tracking | Notes |
|------|------|------|--------|----------|-------|
| **Hero ticker (v3)** | clamp(96px, 18vw, 220px) | 0.9 | 500 | -0.04em | Geist Mono ss01 + tnum |
| Hero price (v3) | 48px | 1 | 500 | -0.02em | Geist Mono tnum |
| H2 SectionHead | 28px | 1.2 | 500 | -0.02em | Geist Sans |
| FScoreColumn numeral | 96px | 1 | 500 | -0.04em | Geist Mono tnum |
| MetricRow value | 28px | 1 | 500 | -0.02em | Geist Mono tnum |
| EarningsHighlight value | 28px | 1 | 500 | -0.02em | Geist Mono tnum |
| Perspective stratum | 28px | 1 | 400 | 0 | Geist Mono tnum |
| Body | 16px | 1.6 | 400 | 0 | Geist Sans |
| Body-narrative (drop-cap) | 16px / drop 64px | 1.65 | 400 | 0 | Geist Sans |
| Small | 14px | 1.5 | 400 | 0 | Geist Sans |
| Mono-meta (timestamp) | 11px | 1.5 | 400 | 0.06em / 0.08em | Geist Mono uppercase |
| Section-num | 11px | 1.4 | 400 | 0.08em | Geist Mono uppercase |

**Editorial rhythm rule:** numbered SectionHead (`01 — ПЕРСПЕКТИВА`, `02 — ЯКІСТЬ`, …) anchors each major block. Numerals always Geist Mono, uppercase, `tracking-[0.08em]`.

### Hard rules

- Body ≥16px (no exceptions)
- Line-height ≥1.5 for body, ≥1.2 for display
- Numbers in metric tables MUST use `tabular-nums` for column alignment
- Ticker symbols wrapped `<span lang="en">NVDA</span>` for screen readers
- Drop-cap у NarrativeBlock applied via `narrative-prose` class only (CSS attaches `:first-of-type::first-letter`).

## Color (v3 «Стратиграфія» tokens)

- **Approach:** light-primary paper neutrals + 3 semantic tones (positive/caution/danger) у OKLCH for perceptual uniformity. Tones appear на Perspective markers, F-Score signal borders, MetricRow context, EarningsHighlight beat/miss. (PeerTable — toneless since 2026-06-09 neutral rebuild.)
- **Strategy:** warm paper #F5F2EA primary (replaces v2 #FAFAF7); ink stack scaled (#18181B / #3F3F46 / #5F5F66); rule scaled (#D4D0C5 / #A8A29E). Dark mode swap-in via `[data-theme="dark"]`.

### Light mode tokens (v3 default)

```css
:root {
  --bg:           #F5F2EA;  /* warm paper — primary surface */
  --bg-elev:      #EDE8DA;  /* elevated surface — Perspective card, peer-table target highlight, hover */
  --ink:          #18181B;  /* primary text + numerals */
  --ink-2:        #3F3F46;  /* secondary text — section titles bridge */
  --ink-muted:    #5F5F66;  /* meta, sub-labels, timestamps — bumped from #71717A для WCAG AA pass на --bg (5.3:1) */
  --rule:         #D4D0C5;  /* rules, dividers, table borders */
  --rule-strong:  #A8A29E;  /* trend-arrow color, dot accents */
  --accent:       #18181B;  /* inverse-fill buttons (dark on paper) */
  --accent-fg:    #F5F2EA;
  /* Tone luminance dropped 0.55-0.62 → 0.45-0.48 для WCAG AA pass на body
     text (originals were 3.36-3.96:1 на --bg; new values 5.4-6.5:1).
     *-soft tints unchanged (used as backgrounds, not text). */
  --positive:     oklch(0.45 0.15 155);
  --positive-soft:oklch(0.85 0.08 155);
  --caution:      oklch(0.48 0.16 70);
  --caution-soft: oklch(0.88 0.07 70);
  --danger:       oklch(0.45 0.18 25);
  --danger-soft:  oklch(0.88 0.06 25);
}
```

### Dark mode tokens

```css
[data-theme="dark"] {
  --bg:           #14171A;
  --bg-elev:      #1B1F23;
  --ink:          #F1EDE3;
  --ink-2:        #C7C2B5;
  --ink-muted:    #8A8F94;
  --rule:         #2A2F34;
  --rule-strong:  #4A5058;
  --accent:       #F1EDE3;
  --accent-fg:    #14171A;
  --positive:     oklch(0.65 0.16 155);
  --caution:      oklch(0.72 0.16 70);
  --danger:       oklch(0.62 0.20 25);
}
```

Theme toggle: client-side `ThemeToggle` у TopNav, persists localStorage, defaults to `prefers-color-scheme` system pref.

### NEVER

- Purple/violet/indigo as accent or gradient (default AI-slop signal)
- Blue→purple gradients
- Neon, hot pink, electric saturation
- Pure red (#FF0000) — use OKLCH `--danger` instead

### Contrast verification (WCAG 2.1 AA)

Verified via `@axe-core/playwright` automated suite (`npm run test:a11y`).

| Pair | Ratio | Required | Status |
|------|-------|----------|--------|
| `--ink` on `--bg` | ~16:1 | ≥4.5:1 (body) | ✓ |
| `--ink-muted` on `--bg` | 5.3:1 | ≥4.5:1 (body) | ✓ |
| `--accent` on `--bg` | ~16:1 | ≥4.5:1 (body) | ✓ |
| `--positive` on `--bg` | 6.5:1 | ≥4.5:1 | ✓ |
| `--caution` on `--bg` | 5.4:1 | ≥4.5:1 | ✓ |
| `--danger` on `--bg` | 6.5:1 | ≥4.5:1 | ✓ |
| `--ink` on `--bg-elev` | ~13:1 | ≥4.5:1 | ✓ |

**axe-core verification 2026-05-05 (post-v3.1):** all 4 key pages (`/uk`, `/uk/NVDA`, `/uk/methodology`, `/maintenance`) zero violations. ink-muted bumped #71717A → #5F5F66 + tone luminance dropped 0.55-0.62 → 0.45-0.48 closed the only outstanding violations.

Dark mode tokens still need axe verification (deferred — light mode is v0 default).

## Spacing

- **Base unit:** 4px
- **Density:** comfortable (16px body padding edge-to-edge mobile, 24px desktop)
- **Scale:**

```css
--space-2xs: 2px;
--space-xs:  4px;
--space-sm:  8px;
--space-md:  16px;
--space-lg:  24px;
--space-xl:  32px;
--space-2xl: 48px;
--space-3xl: 64px;
--max:       1100px;  /* content max-width */
```

### Per-context guidance

- Section padding (between major blocks): `48px` top + `48px` bottom
- Hero padding: `64px` top / `48px` bottom desktop; `32px` / `24px` mobile
- MetricRow vertical padding: `16px`
- Page padding: `16px` mobile / `24px` desktop
- Disclaimer block padding: `16px` all sides
- Perspective card: `32px` desktop / `20px` mobile

## Layout

- **Approach:** grid-disciplined, content max 1100px desktop.
- **Section frame:** `border-t border-rule pt-[48px] pb-[48px]` per major block.
- **Hero split:** 2-col grid `[1fr_auto]` з vertical rule between (`md:[&>*+*]:border-l md:[&>*+*]:pl-[32px]`).
- **Perspective:** 3 stratum rows of `[140px_1fr_100px]` desktop, stacks single column mobile.
- **MetricRow:** `[200px_140px_1fr_200px]` desktop (label / value / context / sparkline), stacks mobile.
- **Border radius:** hierarchical scale

```css
--radius-sm: 4px;   /* buttons, inputs, banners, cards */
--radius-md: 8px;   /* Perspective card, EarningsHighlight */
--radius-lg: 12px;  /* maintenance/404 page hero cards */
```

NO `border-radius: 9999px` (full pill) on anything except status dots.

### Breakpoints

```css
@media (max-width: 600px)   { /* compact mobile, perspective grid stacks */ }
@media (max-width: 767px)   { /* default mobile */ }
@media (min-width: 768px)   { /* tablet/desktop layouts engage */ }
@media (min-width: 1024px)  { /* desktop max */ }
```

## Motion

- **Approach:** functional. Transitions on Perspective track-fill width + marker position (400ms cubic-bezier ease-out), TopNav theme toggle (instant swap), Hero asOf-dot pulse (2s infinite).
- **Easing:**
  - enter: `ease-out` (200ms skeleton → content fade-in)
  - focus: `ease-in-out` (100ms focus ring transitions)
  - perspective track: `cubic-bezier(0.2, 0.8, 0.2, 1)` 400ms
- **Duration:**
  - micro: 100ms (focus, hover bg-elev)
  - short: 200ms (skeleton fade-in, banner mount)
  - medium: 400ms (perspective marker)
- **`prefers-reduced-motion`:** all animations disabled (Hero pulse uses media query attribute selector to halt animation, Perspective transitions short-circuit instant).

### NEVER

- Scroll-driven animations (parallax, etc.)
- Entrance choreography (fade-in-up, slide-in)
- Decorative motion (floating elements, animated gradients)
- Spinner-style loaders (skeleton fade-in instead)

## Component Vocabulary (v3)

Reference these by name. Do not reinvent.

| Component | Purpose | Key constraints |
|-----------|---------|-----------------|
| `TopNav` (з `ThemeToggle`) | Sticky brand + theme toggle + ticker context | Height ~64px, `bg/95 backdrop-blur` border-b. ThemeToggle icon button right side. |
| `SearchInput` (з `TickerSuggestions`) | Homepage + sticky nav ticker entry | Auto-uppercase, mono font, 48px mobile / 40px desktop. Compact variant у TopNav. **Панель підказок має чотири стани реєстру** (`useTickerRegistry`: idle / loading / ready / error) і ніколи їх не змішує: «Не знайдено» рендериться ТІЛЬКИ у `ready` (поки індекс вантажиться, сервіс не бреше про власне покриття), рядок очікування зʼявляється з затримкою 350 мс (теплий чанк не блимає), а рядок помилки чесно каже, що індекс не завантажився, і залишає Enter на введеному тікері робочим. При відкритті нічого не обрано — порожній Enter відправляє введений текст; назва компанії резолвиться в тікер через індекс (`resolveTypedTicker`). |
| `Hero` | Ticker page identity (split 2-col, 220px ticker) | Geist Mono `clamp(96px,18vw,220px)` ticker LEFT, 48px price + 16px change RIGHT, vertical rule between desktop. Hero-meta row: pulsing positive dot + asOf timestamp + sector UA. |
| `Perspective` (v2 «direct verdict») | **THE v3 KEY FEATURE** — 3 strata + convergence + Setup checklist | Card з 3 horizontal track rows (Якість / Оцінка / Момент), each `[140px_1fr_100px]`. Marker = 2px vertical line, color = tone. **Convergence label** descriptive (state, not action) — 5 states per v3.2 Amendment / audit Finding #11: «Сигнали збігаються (позитивно)» / «Якість + премія» / «Знижка + слабкі фундаменталі» / «Слабкі сигнали по трьох вимірах» / «Сигнали неоднозначні». **Setup section (Stage 5 orthogonal criteria):** score 0-5 + tier («Сильний setup» / «Частковий збіг» / «Setup слабкий») + 5 ✓/✗ criteria (Якість бізнесу / Зростає / Дешевша за сектор / Сильний грошовий потік / 52w позиція). NO buy/sell verbs. Tolerates null momentum (placeholder stratum). |
| `FScoreColumn` | Piotroski 9-signal stack (replaces FScoreBadge) | Permanent 2-col layout: 96px Geist Mono numeral (monochrome `text-ink` — quality grade carries no tone) + 3-cell category breakdown LEFT, 9 stacked signal cards RIGHT з ✓/·/— glyph (пройдено / не пройдено / недоступно — required data missing, e.g. a bank's gross margin) + tone border-l (✓ only). Failed/unknown signals use explicit muted colors (text-ink-2 + bg-bg-elev/60) — no opacity-60 cascade (a11y). When `unknown>0` an aggregate «N показник… недоступн…» note renders under the numeral. |
| `SectionHead` | Editorial chapter divider (replaces SectionMarker) | `[80px_1fr]` grid: section-num "01 — ПЕРСПЕКТИВА" Geist Mono 11px tracking-[0.08em] LEFT, h2 28px title + optional kicker (max 60ch ink-muted) + optional aside (right-aligned mono meta) RIGHT. |
| `MetricRow` з sparkline | Single metric з 4-point trend | `[200px_auto_1fr_200px]` grid: label + sub / value 28px mono nowrap / context tone-colored 13px / sparkline 100×24 + "За 4 роки: …" chain. Context phrase stripped of redundant label/value prefix («на N% краще медіани (X)»). Value column auto-width to fit "$X,XX трлн" без overflow. Sparkline tone matches MetricRow tone. |
| `NarrativeBlock` з drop-cap | LLM-generated prose | Max 70ch line desktop, `narrative-prose` class triggers `:first-of-type::first-letter` 64px Geist drop-cap. Cite from JSON, no buy/sell verbs. |
| `EarningsHighlight` | 3-cell editorial grid | Last report EPS / surprise % (tone-colored beat/miss) / next earnings date. Card з `bg-bg-elev` `border-rule` `rounded-md`. Mobile stacks vertically, desktop horizontal. |
| `PeerTable` (neutral) | Компанії того ж сектору — нейтральна вибірка + target highlight | Desktop 6-column table: Тікер / Назва / F-Score / ROE / Виручка р/р / Опер. маржа. Вся вибірка сектору за алфавітом — без рангу, без композиту, без tone-кольорів (2026-06-09 neutral rebuild). ROE/Опер. маржа — рівневі, без «+». Target row `bg-bg-elev font-medium` + non-link ticker. sr-only `<caption>`. Mobile horizontal scroll (min-width 560px). |
| `NextSteps` (numbered) | 3-step deep-dive prompts | Numbered `01 / 02 / 03` Geist Mono 24px LEFT, title (16px medium ink) + body (14px ink-muted) RIGHT. Border-b rule per step. LLM-generated через `nextSteps` payload field; static stub fallback. |
| `Footer` (NEW v3) | 4-column footer | Desktop `[2fr_1fr_1fr_1fr]`: Аналізатор (disclaimer copy) / Сервіси (parent investor24 links) / Джерела (SEC EDGAR · Massive · AlphaVantage) / Контакти. Meta-stripe bottom з asOf + delay note. |
| `StateBanner` | Above hero, all banner states | 5 variants: loading / stale / partial / degraded / warning. Full-width, `--radius-sm`. `role="status"` (info) or `role="alert"` (rare). |
| `SourceAttribution` / `MethodologyLink` / `DisclaimerBlock` | Footer-adjacent trust signals | Inherited unchanged from v1; rendered before global Footer. |
| `admin-tg-link` | Co-founder helper UI | Single labeled input + button + copy-to-clipboard. `/api/admin` scope. Magic-link auth (per Pass 7B). Inherited unchanged. |

## Trust Signals (3 mandatory per ticker page)

Every ticker page MUST display all three within 1 scroll on mobile:

1. **Last-updated timestamp** — у Hero meta-stripe, mono uppercase. Format: "Дані оновлено DD MMM YYYY HH:MM UTC".
2. **Source attribution** — bottom of content (above disclaimer), mono small. Three lines: SEC EDGAR / Massive Free / AlphaVantage labels + delay disclosure.
3. **Methodology link** — between source and disclaimer. Inline accent-styled link "Як ми рахуємо →".

If any trust signal is missing, the page fails the trust-first principle.

## Narrative Voice (LLM prompt spec)

Locked principles for `lib/prompts/narrative.v*.ts` (15 principles total — see CEO plan Pass 3 for full list):

**Tone & content:** calm-factual analyst tone, never conclusionary, never promotional, never hedging-empty, cite from JSON, sector-relative phrasing, glossary-strict, 120-180 words. Drop-cap aware (first paragraph leads з a single content character — no quote, no question).

**Anti-AI rhythm + structure:** sentence rhythm variance (no metronome), no anaphora stacks, no negative parallelism ("Це не X. Це Y."), no magic adverbs, no invented concept labels, no "Here's the thing" transitions, no stand-alone pivot quotes.

**Convergence-aware:** narrative must reference the Perspective convergence label (e.g. «Якість + премія» → narrative explains the premium given strong fundamentals). Convergence labels are **descriptive (state, not action)** per audit Finding #11 (v3.2 Amendment) — никогда не директивні.

**Composite weighting (30/30/20/20 research-shop convention):** `lib/scoring/percentile.ts` weights valuation 30 / growth 30 / quality 20 / health 20. Sensitivity analysis on the v0 universe (5 mega-caps) shows rank order stable across variants 25/25/25/25, 30/30/20/20, 35/15/30/20 — material тільки до композитного значення, не rank order. Re-audit 2026-05-06 dropped a previous false "Morningstar Style Box" citation (real Morningstar uses different structure: 50% forward + 50% historical × 4 metrics × 12.5%).

## Accessibility (WCAG 2.1 AA spec)

- Body text ≥16px, line-height ≥1.5, contrast ≥4.5:1 (verified post-v3.1 — all v3 tones pass з tightened OKLCH luminance)
- All interactive ≥44×44px tap targets
- Focus ring: `2px solid var(--ink)` outline + `2px` offset, NEVER removed
- Skip-link "Перейти до основного" top of body, visible on focus
- `<main role="main">`, `<nav role="navigation" aria-label="Основна">`, `<footer>` for source/disclaimer
- `aria-live="polite"` wraps streaming sections (announces "Готуємо аналіз")
- Пошук тікерів (`SearchInput` / `HeroSearch`): поле = `role="combobox"` + `aria-autocomplete="list"` + `aria-expanded` + `aria-controls` + `aria-activedescendant`; панель = `<ul role="listbox" aria-label="Підказки тікерів">` з `role="option"` + `aria-selected` на рядках; sr-only `role="status" aria-live="polite"` оголошує кількість підказок. `listboxId` унікальний per input (`useId`) — на головній два поля живуть на одній сторінці.
- Perspective: each stratum track is `role="meter"` з `aria-valuenow/min/max` + `aria-label`. Convergence label rendered у visible mono footer of card.
- FScoreColumn: 9-signal `<ul aria-label="Дев'ять сигналів Piotroski F-Score">`. ✓/· glyphs `aria-hidden="true"`; sr-only text "пройдено / не пройдено" per signal.
- MetricRow trend: `aria-label="За 4 роки: 2022 рік +X%, 2023 рік +Y%, ..."` for screen readers, sparkline `aria-hidden`.
- PeerTable: `<table>` з proper thead/tbody + sr-only `<caption>`. Target row uses `font-medium`, no link element.
- State-banners: `role="status"` (info) or `role="alert"` (rare).
- `prefers-reduced-motion`: instant content swap, Hero pulse halted.
- `<html lang="uk">` on all укр pages; ticker symbols `<span lang="en">NVDA</span>`.
- No-CSS fallback: logical document order matches visual order.

## Brand Inheritance (АРХІВ — знято ребрендом 2026-07-06; лого-lockup = «PlainTicker»)

The Аналізатор is a sub-brand of Investor24 (parent at investor24.com.ua). Inherit:

- **Top nav structure** (link to investor24.com.ua main as "Investor24 ↗")
- **Footer cross-links** — Сервіси column links до parent + sibling /buffett/.
- **Brand mark** — "Investor24 · Аналізатор" lockup. Investor24 in regular weight, dot, sub-brand name in regular weight. NO icon/logo in v0.
- **Cross-link** — link Аналізатор у parent's "Сервіси" footer list and main nav (after launch, coordinate з co-founder).

### Diverge from parent:

- **Less emoji** — parent uses 📊 📈 🧮 🏦 in nav heavily. Аналізатор uses emoji ONLY in TG link copy (📊 Розбір цифр →). Nav stays text-only for analytical gravitas.
- **Different page-content tone** — parent uses warmth + character. Аналізатор uses restraint + factual prose з editorial rhythm.
- **Different color emphasis** — parent has more visual decoration. Аналізатор v3 leans editorial-restrained but allows tone-coloring on data signals.

This intentional divergence positions Аналізатор as the analytical sibling to /buffett/'s humorous experiment.

## Anti-Slop Hard Rules (v3)

### Allowed (new у v3, not slop)

- ✅ Charts (sparklines, composite-bars, Perspective tracks) — small, semantic, tone-colored
- ✅ Soft gradients у positive-soft/caution-soft/danger-soft as fill backgrounds
- ✅ Color presence у data signals (positive/caution/danger tones)
- ✅ Animation beyond focus/hover (Hero asOf pulse, Perspective track-fill 400ms)
- ✅ Geist Sans + Geist Mono (replaces IBM Plex)
- ✅ Theme toggle (light primary + dark secondary)

### Still forbidden

- 🚫 Inter / Roboto / Arial / system-ui / IBM Plex (legacy) as primary font
- 🚫 Icons in colored circles
- 🚫 Purple/violet/indigo gradients or accent
- 🚫 "Welcome to NVDA" generic hero copy — data IS the headline
- 🚫 Emoji у primary copy (TG link OK)
- 🚫 3-column metrics card grid (use vertical MetricRow rows)
- 🚫 Centered-everything layouts (default left-align Ukrainian body)
- 🚫 Uniform bubbly border-radius
- 🚫 Buy/sell verbs у LLM-наративі чи convergence label. ✅ Дозволено (2026-05-29 reframe): дія-класифікація-ЛЕЙБЛ (Кандидат на придбання / Тримати / Уникати) з обов'язковим чесним кваліфікатором («за правилом методу, не прогноз ціни і не порада»). Наратив-проза лишається без buy/sell-дієслів. Спец: `docs/superpowers/specs/2026-05-29-honest-verdict-reframe-design.md`
- 🚫 Decorative blobs, wavy SVG dividers

## Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-05-02 | Created DESIGN.md | Founded by `/design-consultation` after `/plan-design-review` locked 13 core decisions. |
| 2026-05-02 | IBM Plex Sans/Mono locked as primary | Ukrainian-Cyrillic native, free, fin-gravitas. |
| 2026-05-02 | Warm off-white #FAFAF7 + deep ink #1A1D1F | Paper-like reading feel, FT-adjacent. |
| 2026-05-02 | Sub-brand naming: "Investor24 · Аналізатор" | Mirrors sibling /buffett/ pattern. |
| 2026-05-02 | NO chart in v0, narrative paragraph PRIMARY | Restraint differentiates from stockanalysis.com. |
| 2026-05-02 | 13 component slots formalized | Locked vocabulary prevents implementer reinvention. |
| 2026-05-02 | E5 locked: Approach C (Hybrid F-Score + percentile) | User chose hybrid over pure percentile (B). |
| 2026-05-02 | F-Score badge VISIBLE on ticker page | Transparency over black-box. |
| 2026-05-05 | Type scale wired як Tailwind v4 `@utility` classes | Phase 2 redesign foundation, semantic utility names. |
| **2026-05-05** | **v3 «Стратиграфія» migration shipped** | Full font swap (Geist replaces IBM Plex), light-primary OKLCH semantic tokens, Perspective replaces single-axis hybrid gauge з 3 strata + convergence verdict, FScoreColumn replaces expandable FScoreBadge, SectionHead replaces SectionMarker, EarningsHighlight + Footer added. PAYLOAD_SCHEMA_VERSION 4→5 з momentum field. Source of truth: `pre-build/redesign-v3-spec.md`. |

## Implementation Notes

- **Tech stack:** Next.js 16 App Router + React 19 + TypeScript strict + Tailwind v4 (CSS variables defined here as Tailwind theme tokens)
- **CSS variables → Tailwind:** in `app/globals.css` `@theme` block, map `--bg`, `--ink`, `--positive`, etc. Components reference `bg-bg`, `text-ink`, `text-positive` etc.
- **Font loading:** `geist/font/sans` + `geist/font/mono` next/font integration (self-hosted WOFF2 subset).
- **Theme persistence:** `data-theme="dark"` on `<html>`, persisted to localStorage, defaults to `prefers-color-scheme` system pref. Toggle лежить у TopNav.
- **Component locations (current):**
  - `components/ticker/Hero.tsx`
  - `components/ticker/Perspective.tsx`
  - `components/ticker/FScoreColumn.tsx`
  - `components/ticker/SectionHead.tsx`
  - `components/ticker/MetricRow.tsx`
  - `components/ticker/NarrativeBlock.tsx`
  - `components/ticker/EarningsHighlight.tsx`
  - `components/ticker/PeerTable.tsx`
  - `components/ticker/NextSteps.tsx`
  - `components/ticker/SourceAttribution.tsx`
  - `components/ticker/MethodologyLink.tsx`
  - `components/ticker/DisclaimerBlock.tsx`
  - `components/ticker/ContextBlock.tsx` (homepage + dev preview only — not on ticker page у v3)
  - `components/footer/Footer.tsx`
  - `components/nav/TopNav.tsx` + `components/nav/ThemeToggle.tsx`
  - `components/nav/SearchInput.tsx` + `components/nav/TickerSuggestions.tsx` + `components/nav/useTickerRegistry.ts`
  - `components/home/HeroSearch.tsx`
  - `components/state/StateBanner.tsx`
  - `components/state/Skeleton.tsx`
  - `components/admin/TgLinkGenerator.tsx`
  - `app/uk/[ticker]/page.tsx`
  - `app/uk/methodology/page.tsx`
  - `app/uk/page.tsx` (homepage)
  - `app/_not-found/page.tsx`
  - `app/_maintenance/page.tsx`

## References

- v3 source of truth: `pre-build/redesign-v3-spec.md` + `pre-build/redesign-v3-plan.md`
- `/plan-design-review` 2026-05-02 (13 decisions locked) — see CEO plan §"Locked Design"
- Sibling tool