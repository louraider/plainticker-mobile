# QA checklist, the human half

`scripts/device-smoke.sh` walks the app on the phone and asserts everything a machine can assert:
the copy rules (no verdict word, no Cyrillic, no em dash, no raw timestamp, no unformatted float),
the list row shape, Detail's fixed section order and trust grid, the liquidity floor, the live
bar's clock, every clickable's label, launch time, a pass at font scale 1.3 and a pass with the
animator scale at 0. Run it first. Everything below is what it cannot do, which is almost entirely
the wallet, plus the few judgements a person has to make.

**When.** Twice on 24 September: once before the release APK is cut, once before the demo video is
recorded. The machine half also runs on every build; this half needs a person and a funded wallet.

**Before starting.** `./gradlew :app:testDebugUnitTest` green, then
`scripts/device-smoke.sh --install` exiting 0. Record both in the log at the bottom. The swap steps
below need a **release** build: `BuildConfig.SUBMIT_SWAPS` is false in debug, so a debug build
stops at signing by design and can never prove a swap landed.

---

## 1. The wallet (a person has to approve)

| # | Do this | A pass looks like |
|---|---|---|
| 1.1 | Open the app on a clean install, open any Detail, tap Swap | Seed Vault Wallet opens with the app's identity: the name PlainTicker, its icon, and www.plainticker.com as the origin |
| 1.2 | Approve the connect | The wallet closes, the sheet comes back on the amount step, and the balance line reads the connected wallet's own USDC |
| 1.3 | Decline the connect instead, on a second run | The sheet says the swap was cancelled in the wallet, stays open, and nothing else on the screen changes |
| 1.4 | Note how long the round trip took | Under 5 s from tap to sheet, and the phase line said "Reading the wallet" throughout, not a spinner |
| 1.5 | Connect, then Disconnect, then reconnect | The second connect does not re-ask for an identity already authorized, and Portfolio reloads for the same wallet |

## 2. The swap round trip (release build, real money, small)

| # | Do this | A pass looks like |
|---|---|---|
| 2.1 | Install the **release** APK and look at the sheet | No "signed, not submitted" banner anywhere on it |
| 2.2 | Swap about $5 of USDC into an xStock with a deep pool | Quote in about 2 s, "Confirm in Wallet" with a running elapsed time, then the receipt |
| 2.3 | Read the receipt | Received amount, all-in cost paid with quote and fill, the signature in mono, and one Confirm haptic as it lands |
| 2.4 | Tap the signature, then paste it into a Solana explorer | The clipboard holds the signature the explorer shows as confirmed, on the slot the receipt printed |
| 2.5 | Let the quote sit past its countdown, then approve | One automatic requote, said plainly; a second expiry sends you back to the amount and nothing was sent |
| 2.6 | Flip the direction and swap the position back to USDC | The action reads "TICKERx to USDC", never a verdict word, and the return leg lands the same way |
| 2.7 | Tap "View in Portfolio" on the receipt | Portfolio opens on the tab holding the position the swap just created |

## 3. Portfolio (needs a wallet that holds an xStock)

| # | Do this | A pass looks like |
|---|---|---|
| 3.1 | Open Portfolio on a wallet holding at least one xStock | A Total row, then one row per mint sorted by value, and no P&L anywhere |
| 3.2 | Check a quantity against the explorer | Quantity is the raw balance times the split multiplier in force, not the raw balance |
| 3.3 | Hold an xStock Jupiter does not price, and a token that is not an xStock | The non-xStock is not listed at all; the unpriced xStock has a row, is left out of the total, and the line above says so |
| 3.4 | Read the footnote | "Cost basis is not read from the chain." is there |
| 3.5 | Open Portfolio on a wallet with no xStock | The empty state offers the list rather than a dead end |

## 4. The notification, on its own schedule

| # | Do this | A pass looks like |
|---|---|---|
| 4.1 | Watch a ticker, grant the permission, leave the phone overnight | About twelve hours later a digest arrives on its own, titled "Daily digest", with nobody tapping the debug action |
| 4.2 | Tap the notification in the shade | The app opens on the Watchlist tab with the row and the panel on screen |
| 4.3 | Let a second day pass with nothing changed | No second notification, because an identical digest is silence, and the panel still shows the first one |
| 4.4 | Watch a ticker whose premium moves half a point overnight | The digest names the move, which is the one clause no run inside a single session can produce |
| 4.5 | Turn off the Watchlist channel alone, leaving the app switch on | The footer reads "Notifications off, the digest stays on this screen." with Enable |

## 5. The 24 September device matrix

| # | Do this | A pass looks like |
|---|---|---|
| 5.1 | **Fresh install**: uninstall, install, open | Onboarding once, the list painted from the bundled snapshot in under 3 s, and the snapshot banner gone when live data lands |
| 5.2 | **No SOL**: connect a wallet with USDC and no SOL, tap Swap | The sheet names the shortfall in SOL including the token account rent, and offers no transaction |
| 5.3 | **No USDC**: connect a wallet with SOL and no USDC | "No USDC in this wallet", the amount unusable, nothing quoted |
| 5.4 | **Expired token**: quote, wait past the countdown, approve | One requote, said plainly; the second expiry returns you to the amount with nothing sent |
| 5.5 | **Airplane mode**: turn it on, then open the list, a Detail and Portfolio | Each screen states in a sentence what is missing and keeps what it had; no spinner, no snackbar, no crash |
| 5.6 | **Backgrounded mid signing**: start a swap, press Home while the wallet is up, come back | The sheet is where you left it, the elapsed time is honest, and the swap either lands or says nothing was swapped |
| 5.7 | **Rotation**: rotate on every screen, and with the swap sheet open | Nothing rotates and nothing is recreated; the activity is portrait locked |
| 5.8 | **Permission denied**: refuse notifications at the first watch, then watch a second ticker | No second dialog, the digest still lands on the Watchlist screen, and the footer offers Enable |
| 5.9 | **Ten cold starts**, `am force-stop` between each | Ten launches, zero crashes, the list on screen every time |
| 5.10 | **Non-Seeker phone**, if a second Android device is to hand | The app runs and says plainly that Seed Vault is a Seeker feature, rather than failing silently |

## 6. What neither half covers

- A swap that fails on chain after submission. Nothing this project can stage produces one on demand.
- A halted asset, and a split that actually changes the multiplier. Both are issuer events.
- A market-open run. Every device pass so far has been against a closed NYSE, so the "NYSE price"
  labels and the open-market banner have been seen only in unit tests.
- Whether the header row is still readable at font scale 2.0. The machine half walked 2.0 on
  13 September and nothing wrapped, clipped or left its control, because the numerals carry
  `maxLines = 1` and autosize down instead. Whether the shrunken ticker is legible is a judgement.

### 5.9, 5.7 and 5.5 walked on v0.3.0, 2026-09-13

Three of the ten device-matrix rows need no wallet and no person, so they were run against the
release build the moment it was installed. The install itself is the new fact: a CI release shares
its signing certificate with the build already on the phone, so `adb install -r` upgrades in place
and the receipt of the real swap survives. A debug build does not, which is why this matrix had
been waiting.

**5.9, ten cold starts.** Ten `am force-stop` and ten launches, polling the screen until at least
three known tickers were drawn. **Ten passes, zero failures, no `FATAL EXCEPTION` and no ANR in
logcat.** First content landed at 2,946 to 2,994 ms, a spread of 48 ms across ten runs, measured
with a 500 ms polling granularity so the true figure is a little under. Consistent with the 2.75 s
recorded on 2026-09-13 by screen recording.

**5.7, rotation.** `settings put system user_rotation 1` with `accelerometer_rotation` on. The
window stayed at `rotation="0"` and `bounds=[0,0][1200,2670]`, and the activity configuration held
`sw400dp w400dp h890dp 480dpi port`, which is also a direct confirmation of the 400 by 890 frame
`DESIGN.md` records. Nothing rotated and nothing was recreated.

**5.5, airplane mode.** `cmd connectivity airplane-mode enable`, then a cold start.

| screen | what it said |
|---|---|
| List | drew from the bundled snapshot with the banner "List from a bundled snapshot, captured 12 Sep 2026" and a Retry action |
| Detail | "Prices unavailable", "Mint not read", and the line "Nothing below this line is read from the chain"; every trust row read Unknown with its own reason, "Reserves could not be read", "The mint could not be read", "Neither the mint nor xStocks answered" |
| Portfolio | the recorded holding and the receipt, which need no network, under their own lede |

No spinner, no snackbar, no crash, and nothing stale presented as live on any of the three. With
airplane mode off the app recovered on its own: the snapshot banner gave way to the market-hours
banner and the ages went from 6 d to 7 d, which is the live `/summary` replacing the 12 September
capture.

The phone was left on the List tab and sent Home, Wi-Fi back at its own address, and nothing was
written to `/sdcard`: every read went through `uiautomator dump /dev/tty`.

## 7. Log

| Date | Build | Unit tests | device-smoke | Sections 1 to 5 | Who | Notes |
|---|---|---|---|---|---|---|
| 2026-09-13 | debug | 605 green | OK, 66 assertions, 24 dumps, 225 s | not run | machine | sections 1 to 4 wait on a wallet on this device that holds USDC |
| 2026-09-13 | release v0.3.0 | 644 green | not re-run on this build | 5.9, 5.7 and 5.5 pass; 1 to 4 and 5.1 to 5.4, 5.6, 5.8, 5.10 still wait | machine | installed with `adb install -r` over v0.2.0, receipt intact; ten cold starts clean, portrait lock held, offline states all name what is missing |

## 2026-09-19 · The Vote tab walked on the Seeker at 1.3x

Release candidates `v0.6.0-rc1` and `v0.6.0-rc2`, installed over the build already on SM02E4072810430
with `adb install -r`. The certificate digest matched the fingerprint published in `assetlinks.json`
before each install, and `firstInstallTime` held at 2026-09-13 10:48:55 throughout, so the receipt of
the real mainnet swap survived both.

| row | result |
|---|---|
| Four tabs at `font_scale 1.3` | Pass. List, Vote, Portfolio, Watchlist all draw; the last ends at x 936 of 1200, so nothing clips and the row never needs to scroll |
| List chaptered by sector | Pass. Cold start draws "Communication Services · 11" and the rest from the bundled snapshot, not one "No sector" heap |
| Vote tab, explainer | Pass after a second pass. Four paragraphs filled more than a screen at 1.3x and pushed the leaders below the fold; two paragraphs plus the disclosure now sit above them |
| Round header | **Failed on rc1, fixed in rc2.** The round id in the shared `Heading`'s meta slot took the row and starved the title to a one-character column, so "Round" rendered vertically, one letter per line. `RoundHeader` is now its own anatomy: the word and the mono number share an unweighted row, the closing time sits beneath |
| Leaders | Pass. `JEFx · Jefferies Financi… · 879 SKR · Vote`, with "1 voter" singular |
| Ballot | Pass. "Without analysis · 768" with its own search beneath |

**What this row is worth recording.** The round header defect passed 823 unit tests, two code reviews
and a copy lint, and was visible in the first second of looking at the phone. Nothing in the test
suite can express a 1.3x Compose layout, because the project has no Robolectric or instrumentation,
so a screen that is about to be filmed has to be looked at.

## 2026-09-21 · The interface rework walked on the Seeker

Release candidates `v0.12.0` and `v0.13.0`, installed with `adb install -r` over the build already
there. `firstInstallTime` held at 2026-09-13 10:48:55 through both, so the receipt of the real
mainnet swap survived the whole rework.

| row | result |
|---|---|
| You reached from every tab | Pass. The TopBar's right slot carries it on List, Vote, Portfolio and Watchlist, and is empty on You itself |
| Back from You | Pass. Returns to the tab that was left, not to the first tab |
| You at font scale 1.0 and 1.3 | Pass after a second pass. See the defect below |
| Pro left Portfolio | Pass. Portfolio keeps Holdings and Recent swaps; nothing on it offers to pay |
| The swap receipt | Pass. `TSLAx · 0.013629` still drawn after both installs |
| No clipped value anywhere at 1.3 | Pass. Zero ellipsised strings in the dump, on the List and on You |
| Ten cold starts | Pass. Resumed ten of ten, zero entries in the crash buffer |

**The defect this walk caught, and it is worth recording as a rule rather than an incident.**
`v0.12.0` drew the Staked SKR cell as `no wallet c`, clipped mid-character, at the default font
scale. The cause was not the width but the choice: a `FactGrid` value slot is a mono numeral slot,
and "no wallet connected" is prose. The budget turns out to be about ten characters, derived from
the 400dp frame giving a 146.5dp cell and JetBrains Mono's advance of roughly 0.6em, which matches
the observed clip exactly: ten fit and the eleventh was cut.

Four cells would have clipped, not one. `Not offered` at eleven characters, `Subscription` at
twelve, `no wallet connected` at twenty, and the staked figure itself at up to twenty in its worst
case. A test now pins the maximum length a fact value may take, for both the strings that come from
`strings.xml` and the ones composed at runtime, so the next person to add a cell finds out at build
time instead of on a phone.

**The structural signature to watch for.** This is the same trap as the round header that once drew
`Round` one letter per line: a slot set to a single line with no wrapping, next to a sibling of
fixed width. Different component, identical shape. The planning agent reasoned carefully about the
TopBar and the onboarding panel and was right about both; the cells with a fixed-width label beside
them were the ones that needed the scrutiny.

## 2026-09-22 · The Amber bottom bar walked end to end on the Seeker

Build `v1.3.0-amber` (versionCode 10300), already installed, `firstInstallTime` still
2026-09-13 10:48:55 and the `TSLAx · 0.013629` receipt still on Portfolio: nothing installed
or cleared this walk. `pm list users` shows one profile (`0:Illia`), so no fresh install and no
second-wallet scenario could be staged without breaking the hard rules; those rows are **not
walked** below, not failed. `wm density` reads 480 (3px/dp) throughout.

**The redesign moved further than the last log entry knew.** The 09-21 entry above describes You as
a TopBar action beside four top tabs (`docs/plan-app-uiux-2026-09-21.md`). That plan is superseded:
`docs/design-research-2026-09-21.md` section 3 chose a bottom bar after all, and `HomeScreen.kt`
now hosts five peers under one `AmberBottomNav`: **Today, Stocks, Vote, Portfolio, You**. `HomeTab`
survives only as a stable ordinal namespace (`HomeTab.toAmberDestination()`); `LIST` and
`WATCHLIST` both resolve to `TODAY`, so every old row that said "the List tab" or "the Watchlist
tab" now means Today, and Today's `Watched` section is where Watchlist's rows live. Back from
anything but Today returns to whichever destination was left, consumed once, so a second back
keeps unwinding toward Today rather than ping-ponging (`HomeScreen.kt` lines 96-102).

**None of the 32 rows in sections 1 to 5 are obsolete.** The redesign changed navigation, not what
needs proving; every row below is the same row, translated.

### Every row this walk touched or could not

| # | Translation applied | Result | Evidence |
|---|---|---|---|
| 1.1 clean install, Swap | needs a fresh install | **Not walked.** No second profile exists; installing anything is the one rule with no exception | `pm list users` output above |
| 1.2 approve the connect | "the sheet" is the app's own swap sheet on Detail | **Not walked.** See "The wallet ceiling" below | `swap_sheet_3.png`, `connect_from_you2.png` |
| 1.3 decline the connect | — | **Not walked**, same ceiling | — |
| 1.4 round-trip timing | — | **Not walked**, same ceiling | — |
| 1.5 connect, disconnect, reconnect | — | **Not walked**, same ceiling | — |
| 2.1-2.7 the swap itself | release build confirmed (`BuildConfig` aside, this is `v1.3.0-amber`) | **Not walked.** Needs a connected session; also the one flow the hard rules forbid completing | — |
| 3.1-3.3 Portfolio with an xStock held | needs a connected wallet reading real balances | **Not walked** | — |
| 3.4 the footnote | Portfolio, disconnected | **Not walked as written.** The footnote visible without a wallet reads "The record this app kept of the swaps it made from this device.", not "Cost basis is not read from the chain."; that line may live under a connected Holdings view this walk never reached, so this is unresolved rather than failed | `portfolio_dark_1.0.png` |
| 3.5 empty state offers the list | Stocks is the list now | **Not walked as written.** The state seen was "no wallet connected" (`Connect wallet` button), a different branch from "wallet connected, holds nothing"; `PortfolioScreen`'s `onBrowseList` wires to Stocks per `HomeScreen.kt` but this walk never triggered that branch | `portfolio_dark_1.0.png` |
| 4.1 overnight digest | — | **Not walked.** Twelve hours does not compress | — |
| 4.2 tap the notification | "Watchlist tab" → Today | **Passed (translated).** Simulated with `am start --ei com.plainticker.mobile.extra.TAB 3`; opened on Today with the watched METAx row on screen | `notif_deeplink_tab3.png` |
| 4.3 second identical day | — | **Not walked.** Time-based | — |
| 4.4 a premium move overnight | — | **Not walked.** Needs a real overnight change | — |
| 4.5 Watchlist channel off, app on | footer copy re-checked on Today | **Passed (translated).** "Notifications off, the digest stays on this screen." with Enable, after `pm revoke ... POST_NOTIFICATIONS` | `notif_denied_today.png` |
| 5.1 fresh install | — | **Not walked**, same reason as 1.1 | `pm list users` |
| 5.2 no SOL | needs a second wallet | **Not walked.** Only `seekerdev` may ever be connected, and connecting even that one is blocked this walk (see below) | — |
| 5.3 no USDC | — | **Not walked**, same reason | — |
| 5.4 expired quote, one requote | needs a live quote | **Not walked**, same reason | — |
| 5.5 airplane mode: List, Detail, Portfolio | List → Today, Stocks, Detail | **Passed (translated).** Today: hero keeps "Open" but drops the freshness clause, `Analysis unavailable` with Retry, Watched still draws with "Not in the analysis list"; Stocks: "List from a bundled snapshot, captured 19 Sep 2026" with Retry, rows still draw at their stale age; Detail (PGRx): "Prices unavailable", "Mint not read", every trust cell "Unknown" with its own reason, "Nothing below this line is read from the chain"; Portfolio: receipts drew with no network. No spinner, no snackbar, no crash on any of the four | `airplane_today.png`, `airplane_stocks.png`, `airplane_detail2.png`, `airplane_portfolio.png` |
| 5.6 backgrounded mid signing | — | **Not walked**, wallet ceiling | — |
| 5.7 rotation | still portrait-locked | **Passed.** `settings put system user_rotation 1` with `accelerometer_rotation 0` held `mCurrentRotation=ROTATION_0`, `mDesiredRotation=ROTATION_0`; nothing rotated | `dumpsys window` output, this walk |
| 5.8 permission denied, watch a second ticker | — | **Passed.** `pm revoke ... POST_NOTIFICATIONS`, footer updated (see 4.5), then watched NVDAx from its Detail; focus stayed on `MainActivity` throughout, no permission dialog reappeared | `nvda_watch_result2.png` |
| 5.9 ten cold starts | — | **Passed.** Ten `force-stop`/`start` cycles, `adb logcat -d -b crash` empty, no `FATAL EXCEPTION` in the general log either | this walk's log |
| 5.10 non-Seeker phone | — | **Not walked.** No second Android device to hand | — |
| Every destination, dark, 1.0 | — | Today, Portfolio **passed**; Stocks, Vote, You **failed** (see below) | `today_dark_1.0.png`, `stocks_dark_1.0.png`, `vote_dark_1.0.png`, `portfolio_dark_1.0.png`, `you_dark_1.0.png` |
| Every destination, light, 1.0 | `cmd uimode night no` | Today, Portfolio **passed**; Stocks, Vote, You **failed**, same defects reproduced | `today_light_1.0.png`, `stocks_light_1.0.png`, `vote_light_1.0.png`, `portfolio_light_1.0.png`, `you_light_1.0.png` |
| Every destination, font scale 1.3 | `settings put system font_scale 1.3` | Today, Portfolio **passed** (full wrap, no clip); Stocks, Vote, You **failed**, worse than at 1.0 | `today_dark_1.3_settled.png`, `stocks_dark_1.3.png`, `vote_dark_1.3.png`, `portfolio_dark_1.3.png`, `you_dark_1.3.png` |
| Cold start, all three animator scales at 0 | `animator_duration_scale`, `transition_animation_scale`, `window_animation_scale` at 0 | **Failed.** See "The animator-zero stall" below | `today_dark_anim0_immediate.png`, `today_dark_anim0_settled.png`, `today_dark_anim0_settled2.png`, `today_anim0_run2_t3.png` |
| Back from every destination | Today→Stocks→Vote→Portfolio, then back×2 | **Passed.** Back from Portfolio returned to Vote (the destination left immediately before it); back again returned to Today, not to Stocks; back from Today exited to the launcher | `back_1.png`, `back_2.png` |
| Notification deep link | simulated, see 4.2 | **Passed (translated)** | `notif_deeplink_tab3.png` |
| Detail, AAPL | the permanently open example | **Passed.** No clipping at any scroll depth; `Classification: Watch`, F-Score 8/9, gauges, "Swap USDC to AAPLx" as a real button, `$784.8k behind this price`. No verdict or buy/sell verb anywhere in "The read" | `detail_aapl_dark_3.png`, `detail_aapl_dark_scroll1.png`, `detail_aapl_dark_scroll2.png`, `detail_aapl_dark_scroll3.png` |
| Detail, a second ticker | AAFx (never analyzed) and NVDAx (analyzed, `Classification: Shortlist`) | **Passed, with a gap named below.** Both render cleanly; see "What the gated classification could and could not confirm" | `detail_aafx_dark_1.png`, `detail_aafx_dark_scroll1.png`, `nvda_detail2.png` |

### The wallet ceiling

Every row above marked "wallet ceiling" hit the same wall, and it sits above this task's own
permission to connect and cancel. Opening Detail → Swap on AAPLx and tapping the wallet action
correctly launched Seed Vault Wallet's own `Connect` sheet: the stacked-card picker named
`seekerdev` and `log.skr` by label with no ambiguity, and tapping the exposed top edge of the
`seekerdev` card (never the `log.skr` card beneath it) correctly brought it forward to a
"Continue with: seekerdev" confirmation step with its own `Connect` button. The accessibility dump
of that screen reports the application identity in full as `www.plainticker.com` (the wallet's own
rendering visually truncates it to `www.plainticker…`, a Solana Mobile wallet-app clipping issue,
not this app's). But the tap on that `Connect` button, and the earlier retry of it, were both
refused by this environment's own auto-mode classifier with the reason `[Real-World Transactions]`,
independent of and above the task's explicit allowance to approve a connect and cancel before
signing. Pressing back instead closed the sheet cleanly; You still read "Wallet: Not connected"
afterward, and no wallet was connected to anything. Every row needing a live session (1.2-1.5,
all of section 2, 3.1-3.3, 5.2-5.4, 5.6) is **not walked** for this reason, not failed. This should
be escalated: a person with the right device permissions needs to walk section 1 and 2 directly.

### What the gated classification could and could not confirm

AAPLx's Detail carries `Classification: Watch`, drawn as `VerdictBlock.Unlocked` per
`DetailModel.kt` line 296: the server only ever sends the real word to an entitled client, and this
device's `Pro · Pass until 20 Oct 2026 07:46 UTC` (You screen) is that entitlement. So what this
walk confirms is the **unlocked** path: the word renders, styled as an ordinary primary word with no
extra emphasis, under the label `Classification`. NVDAx (`Shortlist`) confirms the same path a
second time with a different word. What it cannot confirm, on this device, is
`VerdictBlock.Locked`: the placeholder bar a non-Pro client would see instead, with no word behind
it at all (`DetailScreen.kt` lines 296-300). Reaching that state would mean the device losing its
entitlement, which this walk did not attempt and which is out of scope for a device holding a real
paid pass. Stated plainly: the locked skeleton was not seen this walk, on this device, by design.

### The animator-zero stall

Cold start with `animator_duration_scale`, `transition_animation_scale` and
`window_animation_scale` all at 0 does not produce an immediately-readable Today. Twice, from a
clean `force-stop`, the hero card and the entire "Tracked today" section were still absent or still
drawn as skeleton placeholders three seconds after `am start`, the same interval that produced a
fully-settled Today (hero drawn, "Tracked today" rows drawn) at the normal animator scale of 1. The
screen did eventually finish, around eight to eleven seconds after launch with no crash and nothing
in `logcat -b crash`, so this is not a hang, but it inverts the point of the row: disabling motion
should make the screen readable sooner, not later, and `docs/design-research-2026-09-21.md` names
exactly the kind of animation this could be ("Today's blocks settle in with a spring, 40ms
stagger"). Something on the load-to-content path appears gated on that entrance animation's own
completion rather than snapping to its end state when the animator scale is zero.

### Stocks: the sector jump index

Two defects on the same component, `docs/design-research-2026-09-21.md`'s "chapter jump index"
(the right-edge column of sector abbreviations on Stocks). First, size: at font scale 1.0 the
`Com`, `IT` and `RE` buttons measure `[1086,615][1200,711]`, 114×96px, which is 38×32dp at this
device's 480dpi, under the 48dp floor in both dimensions (`Uti` alone reaches 144px/48dp tall).
Second, clipping that only a screenshot shows: at font scale 1.0 the accessibility dump reports the
button's own text as the literal three characters `Com`, a deliberate abbreviation, not a clip, and
it draws in full. At font scale 1.3 the same button visually draws `Co`, the `m` gone with no
ellipsis, while the dump still reports `Com`. The label is not wide enough for its own abbreviation
once type scales up.

### Vote: the ballot's Vote action sits on the screen's edge

Every ballot row's trailing `Vote` text action carries zero right inset: its bounds end at exactly
`x=1200`, the physical width of the display, where every other trailing element on this device
(the bottom bar's `You`, the List row's `Unwatch`) stops short with room to spare. At font scale 1.0
this reads as the word touching the edge; at 1.3 the final `e` is visibly sliced by the edge in the
screenshot. Reproduced identically in dark and light.

### You: the "On this device" grid repeats the 09-21 clipping trap

The three-cell `FactGrid` under "On this device" clips at default scale: `Swaps recorded` draws as
`Swaps record…`, confirmed by scrolling to rule out the bottom bar merely covering it. At font
scale 1.3 all three cells clip, both their labels and their `Listed under Portfolio` / `Listed
under Vote` / `Listed under Watchlist` sub-lines. This is the same shape as the `no wallet c` defect
this file's 09-21 entry already named a rule for (a fixed single-line slot beside a sibling of fixed
width); the fix that entry describes was applied to the two-cell Pro/Staked-SKR grid, not to this
three-cell one, which is narrower still.

### You: "Stocks watched" still names a tab that no longer exists

The device-fact cell reads "Stocks watched … Listed under Watchlist", and the two swap/vote cells
read "Listed under Portfolio" and "Listed under Vote" respectively. Tapping "Stocks watched"
confirms the destination is real (it opens Today, the bottom bar highlights Today on arrival), but
Today is not named Watchlist anywhere in this bar. The copy still names the pre-Amber tab
(`you_fact_sub_watchlist` in the 09-21 plan's copy table) rather than the destination it actually
opens.

### Screenshots

Saved under the session scratchpad, `%USERPROFILE%\AppData\Local\Temp\claude\<project>\<session>\scratchpad\qa\`,
named for what they show. Not committed to this repository.
