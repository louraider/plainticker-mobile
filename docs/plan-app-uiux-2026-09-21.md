# The app's shape: a cabinet, real controls, and what is where

Written 2026-09-21, eight days before the feature freeze (end of 29 September), eleven before the video (2 October). `DESIGN.md` is binding; nothing below amends it except filling the TopBar slot section 4 already reserves. Read against `v0.9.0`: today's four screenshots, every screen and component under `ui/`, the source-scanning screen tests, `CopyLintTest`, `scripts/device-smoke.sh`, `docs/qa-checklist.md` and the video script. The device was offline while this was written, so the tasks say what to measure.

## 0. What is true today

- Four text tabs inside the scroll, checked at font scale 1.3 on 19 September (the last label ends at x936 of 1200). The TopBar's right slot is empty on all four.
- Identity is three things, none a login: the wallet session in memory (gone at process death by decision, `DESIGN.md` 1.2), the device code in `DevicePassStore` (a bearer credential; only its hash leaves the device), and this device's receipts on disk (swap, vote, pass).
- Everything identity-shaped sits at the bottom of Portfolio: `ProBlock` after "Recent swaps", two sentences set whole in JetBrains Mono, "Pay for Pro" as a right-aligned 14sp text action (`PortfolioScreen.kt` 234 to 239).
- The entitlement is answered for the device code, yet the sentence says "This wallet is Pro" one line above "Connect a wallet".
- Touch targets are already 48dp: `TextAction` (`defaultMinSize(48, 48)`), `TopTabs`, `ListRow` (64dp), `TodayStrip`, the consent row. The brief's third observation is right about weight and placement, wrong about size.
- The List clips a numeral at default scale: "$651 behind this price, too thin · 1 …" on CMCSAx (`ListRow`, `maxLines = 1`, a 276dp column).
- Onboarding's backdrop draws three tabs and the pre-chapter "Analyzed" heading. The panel says what a stock page contains, nothing about where things are.
- Vote with an open, empty round draws the round header, then the ballot; nothing says the round is empty.
- Section gaps differ by screen: 30/28, 30/30, against `DESIGN.md` 5's 32/14.
- 943 unit tests, none of which lays out Compose text: every changed screen gets looked at on the phone.

## 1. Decisions

### 1.1 Navigation: the four top tabs stay; the cabinet enters from the TopBar's right slot

Material 3 puts three to five top-level destinations in a navigation bar and uses tabs for siblings inside one destination; the design database agrees (`nav-hierarchy`, MD: "Primary nav (tabs/bottom bar) vs secondary nav (drawer/settings) must be clearly separated"; `bottom-nav-limit`: "max 5 items; use labels with icons"). `DESIGN.md` 8 bans bottom navigation bars and sticky headers, 4 specifies `TopTabs`, and 7 allows icons only from Phosphor, which a labelled bar needs and this app does not ship. A bottom bar is a change to the binding document, a new dependency, an inset change on four `LazyColumn`s and every sheet, a new `TopScrim` story and a rewritten backdrop and smoke script, eight days out. Not taken.

Where both documents agree is the account entry: the top app bar's trailing slot in Material, "one text action right (Watch, wallet fragment)" in `DESIGN.md` 4. The cabinet is that action, labelled "You", on all four tabs. You is a fifth `HomeTab` outside the tab row, not a nav route, so every ViewModel keeps its home scope (a payment in flight survives a tab change as today) and `home?tab=4` works for free. Back from You returns to the previous tab (`BackHandler`; database `Back Button`, Mobile, High: "Preserve navigation history"). `HomeTab.WATCHLIST.ordinal` stays 3; the digest notification carries it.

### 1.2 The cabinet: the wallet, the pass, the stake and this device's record

"You" means what the app can vouch for about the reader: a wallet session if one is open, the entitlement the server answers for this device's code, the stake read for the connected wallet, and what this device has done. It is not a profile, and it never shows the device code, because the code is the pass and the video is a screen recording.

| Section | Draws | Component |
|---|---|---|
| Wallet | short key in mono or "Not connected"; one sentence on why the session is asked for each launch | label + value; Refresh, Disconnect as text when connected |
| Pro | "Pro" as a fact (Pass, Stake, Subscription, No, Not read, Not offered) with "until …" as a mono sub line; "Staked SKR" as a figure or the reason there is none | `FactGrid`, two cells |
| Action | exactly one 56dp button per state, below | `PrimaryButton`, `SecondaryButton` |
| On this device | Swaps recorded, Votes cast, Stocks watched, each a numeral, each cell opening its tab | `FactGrid` with `onTap` |
| Notifications | the Watchlist footer's line and its Enable action, from the same model | text + `TextAction` |
| Footer | version in mono; the disclaimer sentence | meta, small |

Button rule, checkable. No wallet, not Pro: Primary "Connect wallet", Secondary "Pay for Pro" when `payOffered`. No wallet, Pro: Primary "Connect wallet". Wallet, not Pro: Primary "Pay for Pro" when `payOffered`, Disconnect as text. Wallet, Pro: no button. Never two Accent fills on one screen; "Pay for Pro" is never a text action again. `ProModel.payOffered` and `PassViewModel` do not change; `PassSheet` moves to You as a sibling `Box`, the pattern every sheet host uses.

Leaves Portfolio: the "Pro" heading, `ProBlock`, `PassSheet`, `passViewModel`. Stays: Holdings with the recorded lede and Connect (the state `DESIGN.md` 1.2 owns), Recent swaps and its footnote, because the receipts carry Holdings when no wallet is read; You shows their count and opens Portfolio. Votes stay on Vote, round-scoped; You shows the count and opens Vote.

### 1.3 Orientation: the first frame is the map, and every empty state names its tab's job

The onboarding screen already shows the List behind the panel at 25 percent. That backdrop becomes accurate (four tabs, the You action, a sector heading), and the panel gains one sentence naming the four tabs and You, before the disclaimer. No carousel, no coach marks, no checklist: "do, don't show" and "empty states are onboarding opportunities, not dead ends" are already this app's pattern (Watchlist: "Nothing watched yet. Watch a stock from its page…"). Missing: one such line on Vote when the round is empty, and a persistent map, which "On this device" is: three numerals, each naming its tab. Rule applied: `progressive-disclosure` (Apple HIG, Medium): "Reveal complex options progressively; don't overwhelm users upfront".

### 1.4 The raw look: what reads unfinished, and when it is done

1. **Forward actions drawn as links.** "Connect wallet", "Pay for Pro", "Browse analyzed stocks": 14sp Accent text alone on a line, right-aligned. Rule: a `TextAction` qualifies a row or a sentence (Unwatch, Retry, Clear, Max, Vote, Refresh, Disconnect); the action a screen state exists to offer is a 56dp button at 20dp side padding. Done when no state's only forward action is a `TextAction` and no screen carries two Accent fills.
2. **Prose in the numeral face.** The Pro block's sentences are paragraphs in mono because they carry a date and an amount. `DESIGN.md` 3 keeps numbers out of Outfit; its own answer is label plus mono value (`FactGrid`, `Track`). Done when no mono run on You or Portfolio is longer than a value and its sub line.
3. **A truncated numeral on the signature line.** "1 d old" ends in an ellipsis on thin rows at scale 1.0. Done when, at 400dp and 1.0, no row meta ends in an ellipsis for the premium, thin and depth-unknown forms with an age; at 1.3 the meta may wrap once but never clips.
4. **A heading over nothing.** Vote with an open, empty round. Done when every heading that can have no rows draws one line saying so (`Empty States`, Feedback, Medium: "Show helpful message and action, not blank empty screens").
5. **Three spacing rhythms.** Done when `Heading` alone owns the gap and the per-screen constants are gone.
6. **A first frame that lies.** Done when the backdrop draws `HomeTab` labels and the You action.
7. **At 1.3x.** Every changed screen photographed at `font_scale 1.3` on the Seeker, nothing clipped, every button label on one line.

Grouping and cards are not the problem: `DESIGN.md` 5 separates sections by headings and space, and `Panel` is used once, correctly, for the digest. Nothing here adds a card.

### 1.5 What not to do

- No bottom navigation bar, icons in tabs, Material `TabRow` or sticky anything (`DESIGN.md` 8 and 4; `HomeScreen.kt` records why the Material row was rejected).
- No coach marks or tour: `TapTargetView`, `Spotlight`, `ShowcaseView`, `MaterialTapTargetPrompt` in the curated list are View-system libraries, the tempting shortcut and the wrong one.
- No new dependency. Of the eight Compose entries in `awesome-android-ui`, Landscapist is image loading, Flinger a fling curve, Orchestra pickers, compose-backstack predates navigation-compose, three are samples and Neumorphism is shadows (banned). The other 400 lines are View-system. Material 3 and foundation cover every control here, and the release is minified with rules that were empty a week ago.
- Do not persist the wallet session to make You look signed in (`DESIGN.md` 1.2, a security reason).
- Do not draw the device code on any screen.
- Do not touch Detail, the swap sheet, the vote sheet, `data/receipts/*`, `AppContainer`, `proguard-rules.pro` or the tab order. The video is blocked on the Vote tab as it is; Detail is the strongest screen.
- Do not change `list_row_meta_thin` without every anchor in U4, and do not shorten Detail's sentence, which the floor's argument depends on.
- Do not redesign the Watchlist's empty bottom now. Weakest screen since 13 September, not filmed, and a numbers-first Watchlist is a design task rather than an eight-day one.
- No tab transitions, pull to refresh, snackbars or spinners (sections 6 and 8).

## 2. What the two resources gave

Database rules used, with severity: Touch Target Size High (met), Touch Spacing Medium (met: `TextAction` has 16dp start padding), Back Button High, Deep Linking Medium, Empty States Medium, Loading Indicators High (skeletons already), `nav-hierarchy` and `bottom-nav-top-level` (section 9, High), `progressive-disclosure` Medium. Compose stack file: "Prefer LazyColumn over Column scroll" High and "Use key in Lazy" High, which the screen tests already pin. No match exists in the stack file for navigation destinations, touch targets or empty states; the general rules stand in. Nothing was persisted with `--design-system`: `DESIGN.md` is the design system. The library list is answered in 1.5: adopt none.

## 3. Tasks

Ids `U`; hours are agent hours. Land by 26 September; device pass 27 and 28; freeze 29. Only U4 touches a shot in the video, so nothing blocks the recording.

### Before the freeze

| Id | Goal | Files | Acceptance | Needs | h, P |
|---|---|---|---|---|---|
| U1 | You: `HomeTab.YOU` appended after `WATCHLIST`, outside the tab row; TopBar action "You" on the four tabs, empty on You; `BackHandler` to the previous tab; `YouScreen.kt` and `YouModel.kt` per 1.2; `ProBlock`, `PassSheet`, `passViewModel` leave Portfolio; entitlement sentences say "This device" | `ui/home/HomeScreen.kt`, `ui/components/TopBar.kt`, `ui/you/*` (new), `ui/portfolio/PortfolioScreen.kt`, `ui/portfolio/ProModel.kt`, `strings.xml`, `YouScreenTest.kt` (house style: one `LazyColumn`, keyed items, section order), a test pinning `HomeTab` ordinals | You one tap from every tab; back returns to the tab left; `WATCHLIST.ordinal == 3`; `PassViewModelTest`, `ProModelTest`, `FreeStaysFreeTest`, `PortfolioScreenTest`, `CopyLintTest`, `CountCopyTest` green; `grep -r "\.code()" .../ui` returns nothing | none | 6, P1 |
| U2 | Forward actions become 56dp buttons: Portfolio Connect wallet as `SecondaryButton` under the lede; the two "Browse analyzed stocks" empties as `SecondaryButton`; Refresh, Disconnect, Unwatch, Retry stay text | `ui/portfolio/PortfolioScreen.kt`, `ui/watchlist/WatchlistScreen.kt` | `PortfolioScreenTest` markers present; no state's only forward action is a `TextAction`; screenshots at 1.0 and 1.3 | U1 (same file) | 2, P1 |
| U3 | Onboarding tells the truth: backdrop draws `HomeTab` labels, a sector heading and the You action as a picture; one sentence in the panel names the four tabs and You | `ui/onboarding/OnboardingScreen.kt`, `strings.xml`, `OnboardingScreenTest` | the button stays on screen at 360dp and 1.3x; `CopyLintTest` green; fresh-install row in U7 | U1 | 2, P1 |
| U4 | The row meta never clips a numeral: `list_row_meta_thin` becomes "%1$s behind, too thin"; `ListRow` meta `maxLines = 2`; anchors updated: `CopyLintTest` line 447, `README.md` line 33, video script line 37 | `ui/components/ListRow.kt`, `strings.xml`, `lint/CopyLintTest.kt`, `README.md`, `docs/video-script-2026-09-15.md` | at 400dp and 1.0 every row is one meta line, no ellipsis, all three forms with age; at 1.3 no ellipsis anywhere; `detail_gauge_thin` unchanged | none | 1.5, P1 |
| U5 | Vote's empty round says so: one line under the round header when a round is present and `leaders` is empty | `ui/vote/VoteScreen.kt`, `strings.xml`, `VoteScreenTest`, `VoteTabModelTest` | drawn in that state only; never when `notOpen`; `CopyLintTest` green | none | 1, P2 |
| U6 | One rhythm: `Heading` defaults 32/14 per `DESIGN.md` 5; per-screen gap constants deleted | `ui/components/Heading.kt`, the four screens, `YouScreen.kt` | `grep -rn "SectionTopGap\|HeadingTopGap" ui/` matches only `Heading.kt`; screenshots | U1 | 1, P2 |
| U7 | Device pass on the release candidate: `adb install -r` over `v0.9.0`; `firstInstallTime` still 2026-09-13 10:48:55 and the TSLAx receipt drawn; every changed screen at 1.0 and 1.3; You in all four button states; back from You; notification tap opens Watchlist; ten cold starts; `device-smoke.sh` exit 0; fresh install on a second profile for U3 | `docs/qa-checklist.md` | zero crashes; no ellipsis on a numeral; no ten-character `CODE_ALPHABET` string in any dump; log row written | U1 to U6 | 3, P1 |
| U8 | Record it: `DESIGN.md` 4 gains You ("the TopBar right slot on the home tabs is the You action; You is the one home destination outside the tab row") and the button rule from 1.4; qa-checklist rows for You; README's surface list names You | `DESIGN.md`, `docs/qa-checklist.md`, `README.md` | the next agent cannot move You back into Portfolio without contradicting the design system | U1 | 1, P1 |

Total 17.5 hours. Sequence: U1 on 22 and 23; U2 and U4 on 24 (the checkpoint day, so its screenshots feed the review); U3, U5, U6 on 25; U8 and the release candidate on 26; U7 on 27 and 28. Anything not green on the phone by the evening of 28 September is reverted, not patched.

### After the hackathon (from 9 October)

| Id | Goal | h, P |
|---|---|---|
| U9 | Wallet fragment as the TopBar action when a session is open (mono Accent `TextAction`), "You" otherwise | 2, P2 |
| U10 | Watchlist with numbers when little is watched: the report countdown as a `Track` (design review, 13 September) | 4, P2 |
| U11 | Licenses and fonts under You (the OFL texts ship in `assets/licenses/`) | 2, P3 |
| U12 | `device-smoke.sh` refreshed: the three-tab loop (line 820) and the pre-rewrite Detail assertion (line 911) are stale | 2, P2 |
| U13 | The scroll-away header's cost deep in the ballot: measure with readers before proposing any change to `DESIGN.md` 4 | 0, P3 |

### Minimum credible slice

If everything slips: U1 with only Wallet, Pro and Action, then U4, U7, U8. Identity leaves Portfolio's basement, the two money actions get a shape, and the clipped numeral leaves the video's opening shot. Eleven hours.

## 4. Copy

Every string through `strings.xml`: sentence case, no verdict words, no dashes, no exclamation marks, no emoji; counts here are numerals beside noun labels, so no new `<plurals>`. Drafts; the lint has the final word.

| Name | Draft |
|---|---|
| `you_action`, `you_heading` | You |
| `you_wallet_label`, `you_wallet_none` | Wallet; Not connected |
| `you_wallet_note` | The wallet is asked to connect on each launch. No key or session is kept. |
| `you_pro_label`; `you_pro_pass`, `_stake`, `_subscription`, `_none`, `_unread`, `_disabled` | Pro; Pass, Stake, Subscription, No, Not read, Not offered |
| `you_pro_until` | until %1$s |
| `you_stake_label`, `you_stake_none_wallet` | Staked SKR; no wallet connected |
| `you_heading_device` | On this device |
| `you_fact_swaps`, `you_fact_votes`, `you_fact_watched` | Swaps recorded, Votes cast, Stocks watched |
| `you_fact_sub_portfolio`, `_vote`, `_watchlist` | Listed under Portfolio, Listed under Vote, Listed under Watchlist |
| `you_open_tab`, `you_version` | Open %1$s; Version %1$s |
| `pro_entitlement_pass_until` and siblings | This device is Pro through a pass, until %1$s. (spoken description only) |
| `onboarding_body_map` | Start on the List. Vote picks what is analyzed next, Portfolio reads your wallet, Watchlist sends one digest a day. Your wallet, your pass and this device's record are under You, top right. |
| `vote_tab_no_votes_yet` | No votes in this round yet. The first vote leads. |
| `list_row_meta_thin` | %1$s behind, too thin |

## 5. Risks to what works

- **The swap receipt on the device.** It has survived every `adb install -r` since 13 September because neither the certificate nor the file changed. No task touches `data/receipts/*`, `AppContainer`'s store wiring or `proguard-rules.pro`; U7 checks `firstInstallTime` and the row first.
- **The vote flow.** `VoteViewModel` and `VoteSheet` are untouched; U5 adds one item. U1 must not change the owner of any `viewModel(factory)` call in `HomeScreen`: You is selected by the same `rememberSaveable` index, so a vote mid-flight still returns to its sheet.
- **The Pro entitlement.** `PassViewModel` keeps its home scope and `payOffered` its logic; only the composable moves. `PassSheet` refuses dismissal while a payment is in flight and must keep doing so on You. `FreeStaysFreeTest` pins that nothing free becomes locked.
- **The release build.** No new dependency, no new `@Serializable`, no reflection. `assembleRelease` runs `auditReleaseSerializers`; U7 installs that artefact, because a debug install would not keep the receipt.
- **The digest notification.** `EXTRA_TAB` is an ordinal; `YOU` is appended last and a test pins `WATCHLIST.ordinal == 3`.
- **The device code.** A screen that drew it would put a bearer credential into a three-minute video. U1's `grep` and U7's dump check are the guards.
- **The anchors.** `CopyLintTest` pins `list_row_meta_thin` verbatim; `PortfolioScreenTest` pins section order and the connect markers; the video script quotes the thin line. U1 and U4 name each; a pull request that changes copy without its anchor is not merged.
- **The phone.** No test lays out Compose text at 1.3x. Every screen this plan changes is looked at on the Seeker before the freeze, in U7's order, and the screenshots go in the checklist log.
