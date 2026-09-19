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
