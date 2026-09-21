# Design research and four directions, 2026-09-21

Research and direction step for the full visual redesign of PlainTicker Mobile. Another agent draws the mockups, a third implements. Feature freeze end of 29 September, video 2 October, submission 6 October. `DESIGN.md` ("Instrument") is withdrawn as a visual authority; its content rules survive because `CopyLintTest` enforces them: no buy or sell verbs, no verdict words, no emoji, no intensifiers, no exclamation marks, a count of one phrased as one, every string in `strings.xml`. Type, colour, spacing, shape, motion and component anatomy are open.

## 1. Why the current build reads as bad

Read against the six screenshots (`List`, `Vote`, `Portfolio`, `Watchlist`, `you`, `you13`), `DESIGN.md`, `Tokens.kt`, `Type.kt`.

1. **Restraint was confused with absence.** The system removed every device that organises a phone screen (surfaces, shape, weight contrast, colour, icons) and kept hairlines and two typefaces. Every screen is one undifferentiated column, so the caveat sentence, the heading and the number all carry the same visual weight. Blueprint was the metaphor; at 400dp a blueprint is a wall.
2. **The two faces fight.** JetBrains Mono for every number and ticker says terminal; Outfit, a rounded geometric, says consumer startup. Neither says financial instrument, and the rule "all values in mono" was applied to words: `Pass` and `No wallet` at 40sp in code font on the You screen is the tell of a rule run past its purpose.
3. **The blue is a link colour asked to be a brand.** `#5AA9E6` is pale and desaturated; on `#0B0F14` it reads as a default hyperlink. It is the only chroma on screen and is asked to be tab indicator, text action, button fill, live bar and gauge tick at once. A pale tint used as a fill (the `Connect wallet` rectangle) has no depth, so it reads as a placeholder.
4. **The home screen has no top.** It opens on a search field and the alphabetically first sector. The reader's actual questions (what is mine, what changed, what can be tracked today) live on three other tabs. 160 rows sorted by sector serve nobody's question; the row that varies most (the meta line) is the smallest text on it.
5. **Five destinations drawn as a text menu.** Four top tabs plus a `You` action is five top-level destinations without wayfinding, which is why it feels like a web page and why the founder's instinct for a bottom bar is right.
6. **It is a known generated look.** The `frontend-design` skill lists "broadsheet hairlines, zero border-radius, tinted near-black, a monospace face for small data labels, meta strings joined with middle dots" as the third of three defaults AI-built interfaces fall into. The current build matches it point for point. "Looks bad" is the plain word for "looks generated".

A diagnosis the redesign must not repeat: the composite drawn as `66 fair` gives a classification word the visual weight of a grade. The product refuses verdicts; its rows should not look like report cards.

## 2. What the research says

| Finding | Source | Weight |
|---|---|---|
| Navigation bar holds 3 to 5 destinations, labels always shown, one active indicator; fewer than 3 use tabs, more than 5 a drawer or rail. | [M3 navigation bar guidelines](https://m3.material.io/components/navigation-bar/guidelines), [Smashing](https://www.smashingmagazine.com/2016/11/the-golden-rules-of-mobile-navigation-design/); database `Bottom Tabs`, Medium | Load-bearing |
| M3 Expressive (I/O 2025, Pixel from Sept 2025) replaces the 80dp bar with a shorter flexible bar, deprecates drawers, adds 35 shapes, shape morphing, spring motion replacing duration-plus-easing, button groups, split button, FAB menu, loading indicator, docked and floating toolbars. Google cites 46 studies and 18,000 participants but publishes no numbers. | [9to5google](https://9to5google.com/2025/05/14/material-3-expressive-navigation/), [supercharge.design](https://supercharge.design/blog/material-3-expressive), [Wikipedia](https://en.wikipedia.org/wiki/Pixel_10a) | Load-bearing for the bar height; the research claim is a vendor claim |
| In Compose, `material3:1.4.0` is the latest stable and is what this app compiles against. Its jar, unpacked from the Gradle cache on this machine, holds `ShortNavigationBar` with its `Defaults` and `Arrangement`, `NavigationItemIconPosition`, `MaterialExpressiveTheme` and `expressiveLightColorScheme`, none carrying the `ExperimentalMaterial3ExpressiveApi` or `ExperimentalMaterial3Api` annotation. Absent from it: `FlexibleBottomAppBar`, `ButtonGroup`, `LoadingIndicator`, shape morphing; those live only in `1.5.0-alpha` (alpha28, 9 Sept 2026). The web summaries placing the theme in alpha were wrong; the jar wins. | [Compose Material 3 releases](https://developer.android.com/jetpack/androidx/releases/compose-material3), local Gradle cache | Load-bearing: a bottom bar and the expressive colour scheme cost no new dependency; the expressive components cost an alpha eight days before freeze |
| Tokens come in three tiers, primitive to semantic to component, named for purpose not value; a component never reads a primitive. The settled 2026 toolchain is Figma Variables, Style Dictionary, W3C DTCG format. | [themasterly](https://www.themasterly.com/blog/design-tokens), [alwaystwisted](https://www.alwaystwisted.com/articles/design-token-naming-conventions), [uxpin](https://www.uxpin.com/studio/blog/what-are-design-tokens/) | Load-bearing. `Tokens.kt` has nine primitives and no semantic tier, which is how `Accent` became five things |
| A small team ships a system by starting with tokens and five components, not a component library; systems die of overambition, no owner, or hardcoded values. | [getly](https://www.getly.store/blog/how-to-build-a-figma-design-system-from-scratch-in-2026-with-tokens), [muz.li](https://muz.li/blog/how-to-build-a-design-system-in-figma-a-practical-guide-2026/) | Load-bearing for the calendar |
| Alternatives to infinite scroll: load more, pagination, filtering and sorting; for structured data, sectioned navigation. | [NN/g](https://www.nngroup.com/videos/alternatives-to-infinite-scrolling/), [Lollypop](https://lollypop.design/blog/2026/march/infinite-scroll-design-definition-alternatives-tips/) | Load-bearing |
| Dark palettes use dark greys (`#121212`, `#0D1117`) or true black with tonal surfaces for elevation; preserve luminance contrast and colour semantics; if gain and loss are coloured, add a second cue. | [colorarchive](https://colorarchive.org/guides/fintech-dark-mode-colors/), [atmos](https://atmos.style/blog/dark-mode-ui-best-practices), [Chameleon, arXiv](https://arxiv.org/html/2512.00516v1); database `Dark Mode (OLED)` style, `Light/Dark Contrast` High, `Color Only` High | Load-bearing |
| Lining plus tabular figures (`tnum`, `lnum`) is the standard for money; Inter, IBM Plex, Roboto, Source Sans and Geist carry `tnum` (Geist digits are 600 units wide). Compose already sets `fontFeatureSettings = "tnum"`, so any sans with the feature can carry numbers. | [Medium, fintech typography](https://medium.com/design-bootcamp/the-elements-of-fintech-typography-part-1-readable-money-b6c1226acbde), [dev.to](https://dev.to/alanwest/tabular-numbers-in-css-font-variant-numeric-vs-monospace-hacks-25cn), [Jukebox Print](https://www.jukeboxprint.com/fonts/font-pairing/geist-and-geist-mono) | Load-bearing: "every number in mono" was a choice, not a necessity |
| Robinhood's redesign (Porto Rocha) built a system to make dense financial information legible, mostly black and white with content carrying the colour; Revolut is dark-first with clear hierarchy; 2026 fintech guides converge on dark-first, one accent per product area, tabular numerals. | [Corporate Insight](https://corporateinsight.com/robinhood-2-0-how-the-commission-free-pioneer-is-rewriting-the-rules-again/), [Gummble](https://gummble.com/blog/best-finance-app-designs-2026), [saasfactor](https://www.saasfactor.co/blogs/fintech-mobile-app-design) | Half load-bearing: the monochrome-plus-data-colour pattern is a real trend with a reason; the rest is fashion |
| Database rules taken, with severity: `Touch Target Size` 48dp High; `Back Button` High; `Excessive Motion` "animate 1 to 2 key elements per view" High; `Duration Timing` "shared motion tokens, no universal cutoff" Medium; `Reduced Motion` High; `Empty States` Medium; `Chip Collection Reflow` "wrap or +n disclosure" High; Compose stack `Design system: Material3 tokens, not hardcoded` High, `Use key in Lazy` High. | `ui-ux-pro-max` | Load-bearing |
| Database fashion, rejected: `--design-system` for "fintech crypto dark" returned Orbitron plus JetBrains Mono cyberpunk and a "gold trust + purple tech" palette; the product file recommends Glassmorphism plus OLED for Fintech/Crypto. Purple is banned by repo convention; frosted glass and glow are on the slop list. | `ui-ux-pro-max` | Fashion |

What changed my mind: the tabular-numeral finding. The mono-for-numbers rule was load-bearing in the old system and is not in the new one, which frees every direction to run on one or two faces.

## 3. The home screen as information architecture

The reader owns or is weighing a position. Their questions, in order: what is mine and what changed; what can this app stand behind today; is the venue open and how old is the data; then browse. Today's List answers the fourth question first and the others never.

**Home becomes Today.** Five blocks, one and a half viewports at 890dp:

1. Venue and data age, one line of facts.
2. **Yours**: holdings (receipts, or the chain when read) and watched stocks, each with its one relevant fact (premium if tracked, days to report, quantity). Empty state: one sentence and a `Search stocks` action.
3. **Tracked today**: the tokens above the $4,000 floor (22 on 13 Sept), premium and a compact gauge each, sorted by depth. Lede: "22 of 160 analyzed can be tracked today". This puts the product's thesis on the first screen.
4. **Next up**: the vote leader, one row, opens Vote.
5. A footer count opening Stocks.

**The list becomes Stocks**: search at the top, a wrapping filter row (Tracked, Watched, sector), sector chapters as `stickyHeader` items with counts and a chapter jump index, and `Without analysis` as a second segment of the same screen. Chapters replace the infinite scroll; nothing is filtered out.

**Five destinations**: Today, Stocks, Vote, Portfolio, You. Watchlist folds into Today (Yours) and into a Watched filter on Stocks. `HomeTab.WATCHLIST` stays in the enum so the digest notification's `EXTRA_TAB` still resolves; it is translated to Today scrolled to Yours. Vote stays a destination because the video is built on it.

## 4. Foundation every direction inherits

- **Token tiers.** Primitives (`grey.900`), semantic roles (`surface.ground`, `surface.raised`, `surface.high`, `text.primary/secondary/tertiary`, `border`, `action.fill`, `action.onFill`, `action.text`, `state.live`, `state.caution`), component tokens (`row.height`, `bar.indicator`). Composables read semantic or component tokens only. One rule the measurements below force: `text.tertiary` never sits on `surface.high` (it measures 4.1 to 4.4:1 there in every set); tertiary text on the highest surface promotes to secondary.
- **Numbers** in the UI face with `tnum`; JetBrains Mono (already bundled) only for on-chain identifiers. Number and unit are one string; the state word or comparison is a second line in `text.secondary`, never a colour.
- **Bar.** `ShortNavigationBar` from 1.4.0, five items, labels always, 24dp glyphs. Icons are five `VectorDrawable` XMLs copied from one set; no icon library.
- **Motion tokens**, three only: `quick` 150ms fade, `settle` 250ms decelerate, `spring` (medium-low stiffness) for the bar indicator and sheets. Animator scale 0 turns everything off.
- **Detail's layout does not change** in any direction; tokens and components change under it. That is what keeps eight days possible.
- Contrast below is WCAG, computed (`scratchpad/design/contrast.py`): text roles against `surface.ground`, `action.onFill` against `action.fill`, the rest against ground.

## 5. Four directions

### 5.1 Ink

Colour appears only where the chain says something is risky; everything else is black, white and weight. This is the product's refusal to tip, drawn.

**Type**: Schibsted Grotesk (OFL, confirmed) for words and numbers, `tnum` to be confirmed with `otfinfo -f` before bundling; JetBrains Mono for identifiers. Fallback: Source Sans 3 (OFL, `tnum` confirmed for the Source Sans family). Scale: 34/28/20/16/14/12 sp, weights 500 and 400 only.

| Role | Dark | Light |
|---|---|---|
| surface.ground | `#000000` | `#FFFFFF` |
| surface.raised / high | `#121212` / `#1C1C1C` | `#F4F4F4` / `#E9E9E9` |
| text.primary | `#F2F2F2` 18.8 | `#0A0A0A` 19.8 |
| text.secondary | `#A6A6A6` 8.6 | `#5A5A5A` 6.9 |
| text.tertiary | `#7C7C7C` 5.0 | `#6E6E6E` 5.1 |
| border | `#2A2A2A` | `#DADADA` |
| action.fill / onFill | `#F2F2F2` / `#000000` 18.8 | `#0A0A0A` / `#FFFFFF` 19.8 |
| action.text, state.live | `#F2F2F2` 18.8 | `#0A0A0A` 19.8 |
| state.caution | `#F5B942` 11.9 | `#8A5A00` 5.9 |

**Shape and elevation**: 16dp containers, full-radius 52dp buttons, 8dp chips; depth by tone only, no strokes except a 1dp border on inputs. **Motion**: functional only; the live bar keeps breathing, in white. **Home**: Today as grouped tonal containers on true black, one per block, numbers right-aligned. **Bar**: 64dp on ground, 1dp top border, Material Symbols Rounded (`home`, `search`, `how_to_vote`, `work`, `person`), active glyph filled in `text.primary` over a `surface.raised` pill, inactive in `text.tertiary`.

Risk: reads austere if size contrast is timid. The remedy is scale, not colour.

### 5.2 Prospectus

A filing is a document; read it like one, in daylight, with a serif for names and a working sans for figures. Light-first, because the use scene is reading, not trading.

**Type**: Instrument Serif (OFL, confirmed; upright only, 28sp and above, never italic) for company names and the lead; Source Sans 3 for UI, body and numbers (`tnum` confirmed); JetBrains Mono for identifiers. Fallback display: Literata (OFL) if Instrument Serif's single weight is thin on OLED.

| Role | Light | Dark |
|---|---|---|
| surface.ground | `#F4F3EF` | `#15171A` |
| surface.raised / high | `#FFFFFF` / `#ECEAE4` | `#1E2126` / `#272B31` |
| text.primary | `#14161A` 16.3 | `#ECEBE6` 15.0 |
| text.secondary | `#4A4F57` 7.4 | `#B4B3AD` 8.5 |
| text.tertiary | `#62676F` 5.1 | `#8A8A85` 5.2 |
| border | `#D8D6CF` | `#30343B` |
| action.fill / onFill | `#7A2E2E` / `#FFFFFF` 9.3 | `#7A2E2E` / `#FFFFFF` 9.3 |
| action.text | `#7A2E2E` 8.4 | `#E3A9A9` 9.0 |
| state.live | text.primary | text.primary |
| state.caution | `#8A5A00` 5.3 | `#F2B75C` 10.0 |

Oxblood is the ledger's red rule; it never touches a number, so it cannot read as loss. **Shape**: 4dp everywhere; one 1dp rule per section, never per row; white sheets on paper for the lead and the bottom sheet. **Motion**: stillness; sheet 250ms, tab change instant, only the live bar moves. **Home**: a front page: a serif lead (the largest change among Yours), a rule, Yours and Tracked as tables with a sentence-case header row and a gauge column. **Bar**: 56dp plus inset, same paper ground, 1dp top rule, Phosphor Regular (`house`, `magnifying-glass`, `check-square`, `briefcase`, `user-circle`), active in oxblood, no pill (indicator colour transparent).

Risk: a light app opened from a dark wallet is a jolt on the Seeker; the dark set is the fallback, not the intent.

### 5.3 Amber

The departures board: warm dark ground, amber figures, and Material 3 Expressive's shape and spring language done with a custom seed so it is Android-native without being Google.

**Type**: Bricolage Grotesque (free and open source, variable `wght`, `wdth`, `opsz`; licence and `tnum` to confirm in the repo before bundling) for display and UI, optical size doing the work at 34sp versus 14sp; numbers in Bricolage `tnum` if present, else JetBrains Mono. Fallback: Manrope (OFL). Compose variable fonts via `FontVariation.Settings`, API 26 and up, which `minSdk 26` allows.

| Role | Dark | Light |
|---|---|---|
| surface.ground | `#16130D` | `#FFFBF2` |
| surface.raised / high | `#221E15` / `#2E281C` | `#FFFFFF` / `#F3EBD6` |
| text.primary | `#F5EEDD` 16.0 | `#1F1A0E` 16.8 |
| text.secondary | `#C6BCA4` 9.8 | `#5A5240` 7.5 |
| text.tertiary | `#948B74` 5.5 | `#7A7059` 4.7 |
| border | `#3A3324` | `#E2D9C2` |
| action.fill / onFill | `#FFC247` / `#3B2800` 8.8 | `#7A5600` / `#FFFFFF` 6.7 |
| action.text, state.live | `#FFC247` 11.5 | `#7A5600` 6.4 |
| state.caution | `#FF6B57` 6.6 | `#B4220C` 6.4 |

Caution is the only red-orange anywhere and never on a number. **Shape**: varied radii by hierarchy, 28dp status card, 16dp list containers, 8dp chips morphing to full when selected; tonal depth, a 1dp border only on the active chip. **Motion**: one orchestrated moment per cold start (Today's blocks settle in with a spring, 40ms stagger), the bar pill morphs width, the sheet springs; nothing else moves. **Home**: Today as tonal cards, the status line set in amber `tnum` figures like a board, the tracked gauge as a full-width amber tick on a `surface.high` track. **Bar**: 64dp on `surface.raised`, Material Symbols Rounded, active glyph filled with the label in amber over a pill in `#5C4300`.

Risk: the light variant tends to yellow; this direction is dark-first and light is derived. It is also where an implementer reaches for `1.5.0-alpha`, which must be refused.

### 5.4 Bureau

The bank-grade answer: one committed hue owns the chrome, content sits on white, and everything is where a finance app user expects it.

**Type**: Public Sans (OFL, US Web Design System) for everything, `tnum` to confirm; JetBrains Mono for identifiers. Fallback: Source Sans 3.

| Role | Light | Dark |
|---|---|---|
| surface.ground | `#FFFFFF` | `#0E1414` |
| surface.raised / high | `#F5F7F7` / `#E9EEEE` | `#172020` / `#202B2B` |
| text.primary | `#0F1A1A` 17.8 | `#E8EFEF` 16.0 |
| text.secondary | `#4C5C5C` 7.0 | `#A9B8B8` 9.1 |
| text.tertiary | `#5F7070` 5.2 | `#7E8F8F` 5.5 |
| border | `#D5DDDD` | `#2B3838` |
| chrome / onChrome | `#0B3B3A` / `#FFFFFF` 12.4 | `#0B3B3A` / `#FFFFFF` 12.4 |
| action.fill / onFill | `#0B3B3A` / `#FFFFFF` 12.4 | `#2F9C96` / `#04201F` 5.1 |
| action.text, state.live | `#0B5F5C` 7.5 | `#6FCFC9` 10.1 |
| state.caution | `#A14A00` 6.0 | `#F2A64B` 9.2 |

**Shape**: 12dp cards with a 1dp border in light and tone in dark, 8dp 48dp buttons, chrome blocks square and full-bleed. **Motion**: Material defaults, nothing bespoke. **Home**: a teal header block carrying the venue line in white, then Yours as a card with a summary line, Tracked as rows, Next up as a row. **Bar**: 64dp filled teal, white glyphs and labels, active as a white pill with a teal glyph (Lucide `house`, `search`, `vote`, `briefcase`, `circle-user`).

Risk: least distinctive of the four; it looks like a real finance app because it looks like every real finance app.

### 5.5 Component anatomy

| Component | Ink | Prospectus | Amber | Bureau |
|---|---|---|---|---|
| Ticker row, 64dp | Ticker 16/500, company 14 secondary; right: figure 16 `tnum`, context 12 secondary below; rows flush in a 16dp container, no dividers | Ticker 16/600, company 18 Instrument Serif; figure in a fixed column; one rule under the section | Ticker 16/600 at `opsz` 16, company 14; figure 18 amber `tnum`; rows in a 16dp tonal container, 1dp gap | Ticker 16/600, company 14; figure 16, context 12; 1dp border rows inside a 12dp card |
| Number with context | Figure 28 `tnum` primary, one line of context 14 secondary, scale stated ("66 of 100, sector median 58") | Figure 32 Source Sans `tnum`, context 15 as a sentence, serif only for names | Figure 34 amber, context 14 secondary, in a 28dp card | Figure 28, label 13 above, context 13 below |
| Section head | 20/500, count right in tertiary, 28dp above, 12dp below | 24 Instrument Serif upright, 1dp rule below, 32dp above | 22/700 `opsz` 22, 24dp above, sits on the card edge | 18/600, 24dp above; may sit in the teal header |
| Primary action | 52dp pill, white fill, black 16/500 text | 48dp 4dp-radius oxblood fill, white 16/600 | 56dp 16dp-radius amber fill, dark text; springs on press | 48dp 8dp-radius teal fill, white 16/600 |
| Sheet | `surface.raised`, 28dp top radius, 32x4 handle, title 20 | `surface.raised` white on paper, 4dp radius, 1dp top rule, serif title 24 | `surface.high`, 28dp top radius, spring entry, handle in amber | `surface.raised`, 16dp top radius, title 20 in `text.primary` |

## 6. The pick, and the risk

**Pick Ink.** It answers the three complaints directly (the blue is gone, the bar exists, the home has a top), it is thematically exact for a product whose whole position is that it adds no verdict, it films best on the Seeker's OLED, and it is the only direction whose every part is a restyle of components that already exist plus one font family. Amber is the stronger identity and the one I would build with three weeks.

**Riskiest to build in eight days: Amber.** A variable font with three axes, four new component shapes, choreographed motion, and the constant temptation of `1.5.0-alpha` for the pieces (button groups, morphing chips) that Expressive makes look easy.

## 7. Calendar

| Work | Ink | Prospectus | Amber | Bureau |
|---|---|---|---|---|
| Token tiers, `Theme.kt`, one new family with `tnum`, both palettes | 1 day | 1 day (two families) | 1.5 days (variable font) | 1 day |
| `ShortNavigationBar`, five destinations, Watchlist fold, `EXTRA_TAB` translation, five vector XMLs | 1 day, all directions | | | |
| Today screen from existing models (`WatchlistModel`, receipts, tracked filter on `ListViewModel`, `NextUpModel`) | 1.5 days | 2 days (front-page lead logic) | 2 days (cards, one motion moment) | 1.5 days |
| Stocks: chapter `stickyHeader`, jump index, wrapping filter row | 1 day, all directions | | | |
| Restyle `ListRow`, `Heading`, `Buttons`, `Sheet`, `FactGrid`, `Track`, `Gauge` | 1 day | 1.5 days | 2 days | 1 day |
| Device pass at 1.0 and 1.3 on the Seeker, dark only | 1 day, all directions | | | |
| **Lands by 29 Sept** | Everything above | Everything but the light-at-1.3 pass | Tokens, bar, Today, Stocks; motion beyond the bar and sheet does not | Everything above |
| **Does not land** | Light theme QA; icon polish past five glyphs | Serif hyphenation at 1.3x on long names | Chip morphing, staggered entry, light variant | Nothing structural |

**Minimum credible slice, any direction**: tokens and type, the bar, Today, the restyled row, button and sheet, dark only. About three agent days. It changes every frame of the video and leaves Detail, the swap sheet and the vote sheet untouched. Anything not green on the phone by the evening of 28 September is reverted, not patched.
