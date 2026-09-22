#!/usr/bin/env bash
# device-smoke.sh — walk PlainTicker Mobile on a real Android phone and assert what it drew.
#
# Why it exists
#   On release day the whole app is walked on the phone before the APK is cut and again before the
#   video is recorded. Doing that by hand each time is how a regression reaches a judge. Everything
#   a machine can assert is asserted here, so the human pass (docs/qa-checklist.md) is spent only
#   on what a machine cannot do, which is the wallet.
#
# What it does
#   Installs the debug APK if told to, clears app state so the walk is reproducible, then walks:
#   launch and onboarding, Today (the new home, docs/design-research-2026-09-21.md section 3) and
#   Stocks, one Detail for a token with a deep pool and one for a token under the $10,000 liquidity
#   floor, watching a ticker and firing the daily check from Today (Watchlist folded into Today's
#   Yours block, so it is no longer a destination of its own), and the swap button as far as a
#   debug build honestly goes (the wallet handoff; nothing is signed and no money moves,
#   BuildConfig.SUBMIT_SWAPS is false in debug). Then a second pass at font scale 1.3 and a third
#   with the animator scale at 0.
#   It asserts rather than screenshots: a failed assertion names the screen, what it expected and
#   what it found, and the script exits non-zero.
#
# Usage
#   scripts/device-smoke.sh [options]
#     --install [APK]   install first (default app/build/outputs/apk/debug/app-debug.apk)
#     --deep TICKER     underlying of the token with a deep pool          (default NVDA)
#     --thin TICKER     underlying of the token under the liquidity floor (default APP)
#     --keep            keep the dumps in the work directory and print its path
#     -h, --help        this text
#
# Which phone
#   $ANDROID_SERIAL if it is set, otherwise the single attached device. Two devices without
#   $ANDROID_SERIAL is an error: this repository is public and no serial belonging to a person is
#   written down here. adb is $ADB, or adb on PATH, or the Windows SDK default.
#
# Exit codes
#   0  every assertion held
#   1  an assertion failed (screen, expectation and finding are printed)
#   2  the run could not start: no adb, no device or an ambiguous one, app not installed, bad usage
#   3  the walk lost its way: something it had to tap never appeared. A harness problem, not a
#      verdict on the product.
#
# Cost
#   One uiautomator dump costs about 2.4 s on this phone and one screencap about 0.41 s, so a
#   script that dumps in a loop measures itself. Nothing here dumps in a loop: state changes are
#   polled through `dumpsys window` (~0.1 s), motion through `dumpsys gfxinfo` (~0.1 s), and one
#   dump is taken per assertion point. The dump count and the wall clock are printed at the end.
#
# Portable to bash 3.2: no associative arrays, no mapfile, no process substitution in a pipeline.
# Everything the walk writes lives in a host temp directory; nothing is written to the phone.
set -euo pipefail

# MSYS/Git Bash rewrites arguments that look like paths ("/dev/tty", "/sdcard") before adb sees
# them. Every adb call in this script goes through adbx(), which turns that off.
export MSYS_NO_PATHCONV=1

# ---- what the walk expects, and why ---------------------------------------------------------

# `am start -W` TotalTime is launch request to the activity's first frame. Time to first content
# was measured at 2.75 s on this phone (docs/data-map.md, "Two point seven seconds to first
# content"), of which 2.35 s is process start, splash and first composition. TotalTime is a strict
# part of that, so a TotalTime above the whole measured figure means launch alone has eaten the
# entire budget. Observed here: 1.16 s to 1.33 s, so this ceiling leaves about 2x of headroom.
LAUNCH_CEILING_MS=2750

# The live bar breathes at 1 to 0.45 over 2.4 s and is the only continuous motion on a screen
# (DESIGN.md sections 1 and 6). Frames rendered over a 2 s window on a Detail with the bar on
# screen: 482 and 718/3s with animators on, 4 with animator_duration_scale 0. Two orders of
# magnitude apart, so the thresholds are nowhere near either measurement.
MOTION_WINDOW_S=2
MOTION_MIN_FRAMES=100
STILL_MAX_FRAMES=20

# The content gutter is 20dp (DESIGN.md section 5). What that is in pixels follows the phone's
# density, which is read off the phone rather than assumed: 60px on this Seeker at 480dpi, and
# something else on the second Android phone step 5.10 of the checklist reaches for.
GUTTER_DP=20

# A screen that drew nodes but no readable string is not a screen that passed the copy rules, it is
# a screen with nothing on it. The thinnest real screen the walk meets is onboarding at 7 strings.
MIN_STRINGS_PER_SCREEN=5

# The second pass runs at font scale 1.3 (plan section 13, Pass 6: every screen is tested at 1.3x).
# A numeral that kept to one line grows to about the scale; one that took a second line grows to
# about twice the scale. The bound sits halfway between, so it follows the scale rather than being
# a number tuned to one setting, and it is not close to either case.
FONT_SCALE_PCT=130
WRAP_RATIO_MAX=$((FONT_SCALE_PCT * 145 / 100))
SCALE_TOOK_MIN=$((FONT_SCALE_PCT * 80 / 100))

DEEP_TICKER=NVDA
THIN_TICKER=APP
DO_INSTALL=0
APK=app/build/outputs/apk/debug/app-debug.apk
KEEP=0
PKG=com.plainticker.mobile
ACTIVITY=.MainActivity

usage() {
  cat <<'USAGE'
usage: scripts/device-smoke.sh [--install [APK]] [--deep TICKER] [--thin TICKER] [--keep] [-h]

  --install [APK]   install first (default app/build/outputs/apk/debug/app-debug.apk)
  --deep TICKER     underlying of the token with a deep pool          (default NVDA)
  --thin TICKER     underlying of the token under the liquidity floor (default APP)
  --keep            keep the dumps in the work directory and print its path

The phone is $ANDROID_SERIAL, or the single attached device. adb is $ADB, or adb on PATH.
Exit: 0 every assertion held, 1 an assertion failed, 2 the run could not start,
      3 the walk lost its way (a harness problem, not a verdict on the product).
USAGE
}

# ---- arguments ------------------------------------------------------------------------------

while [ $# -gt 0 ]; do
  case "$1" in
    --install)
      DO_INSTALL=1
      case "${2:-}" in -*|"") ;; *) APK="$2"; shift ;; esac
      ;;
    --deep)
      [ $# -ge 2 ] || { echo "device-smoke: --deep needs a ticker" >&2; usage >&2; exit 2; }
      DEEP_TICKER="$2"; shift ;;
    --thin)
      [ $# -ge 2 ] || { echo "device-smoke: --thin needs a ticker" >&2; usage >&2; exit 2; }
      THIN_TICKER="$2"; shift ;;
    --keep) KEEP=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "device-smoke: unknown argument '$1'" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

for t in "$DEEP_TICKER" "$THIN_TICKER"; do
  case "$t" in
    *[!A-Z0-9.]*|"") echo "device-smoke: '$t' is not a ticker (A-Z, digits and dots)" >&2; exit 2 ;;
  esac
done
DEEP_X="${DEEP_TICKER}x"
THIN_X="${THIN_TICKER}x"

# ---- adb and the phone ----------------------------------------------------------------------

if [ -n "${ADB:-}" ] && [ -x "$ADB" ]; then
  :
elif command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
elif [ -n "${LOCALAPPDATA:-}" ] && [ -x "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]; then
  ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
elif [ -x "$HOME/Android/Sdk/platform-tools/adb" ]; then
  ADB="$HOME/Android/Sdk/platform-tools/adb"
else
  echo "device-smoke: no adb. Put it on PATH or set \$ADB." >&2
  exit 2
fi

SERIAL="${ANDROID_SERIAL:-}"
if [ -z "$SERIAL" ]; then
  attached="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  count="$(printf '%s\n' "$attached" | grep -c . || true)"
  if [ "$count" -eq 0 ]; then
    echo "device-smoke: no device is attached and authorized (adb devices)." >&2
    exit 2
  fi
  if [ "$count" -gt 1 ]; then
    echo "device-smoke: $count devices attached. Name one in \$ANDROID_SERIAL:" >&2
    printf '%s\n' "$attached" | sed 's/^/    /' >&2
    exit 2
  fi
  SERIAL="$attached"
fi

adbx() { "$ADB" -s "$SERIAL" "$@"; }
sh_()  { adbx shell "$@" 2>/dev/null; }

adbx get-state >/dev/null 2>&1 || { echo "device-smoke: $SERIAL is not reachable." >&2; exit 2; }

if [ "$DO_INSTALL" -eq 1 ]; then
  [ -f "$APK" ] || { echo "device-smoke: no APK at $APK. Build one with ./gradlew :app:assembleDebug." >&2; exit 2; }
  echo "device-smoke: installing $APK"
  adbx install -r -d "$APK" >/dev/null || { echo "device-smoke: install failed." >&2; exit 2; }
fi

sh_ pm path "$PKG" | grep -q . || {
  echo "device-smoke: $PKG is not installed on $SERIAL. Re-run with --install." >&2
  exit 2
}

SCREEN_W="$(sh_ wm size | awk -F'[ x]' '/Physical size/ { print $(NF-1) }')"
SCREEN_H="$(sh_ wm size | awk -F'[ x]' '/Physical size/ { print $NF }' | tr -d '\r')"
case "$SCREEN_W$SCREEN_H" in *[!0-9]*|"") echo "device-smoke: could not read the screen size." >&2; exit 2 ;; esac

DENSITY="$(sh_ wm density | awk -F': *' '/Physical density/ { print $2 + 0; exit }' | tr -d '\r')"
case "$DENSITY" in ''|*[!0-9]*|0) echo "device-smoke: could not read the screen density." >&2; exit 2 ;; esac
GUTTER_PX=$((GUTTER_DP * DENSITY / 160))
CONTENT_RIGHT=$((SCREEN_W - GUTTER_PX))

WORK="$(mktemp -d 2>/dev/null || mktemp -d -t devicesmoke)"
DUMPS=0
START_TS=$SECONDS

# ---- leave the phone as we found it ----------------------------------------------------------

SAVED_FONT_SCALE="$(sh_ settings get system font_scale | tr -d '\r')"
SAVED_ANIMATOR="$(sh_ settings get global animator_duration_scale | tr -d '\r')"
SAVED_NOTIF=denied
if sh_ dumpsys package "$PKG" | grep -q "POST_NOTIFICATIONS: granted=true"; then SAVED_NOTIF=granted; fi

restore_setting() { # namespace key saved-value
  if [ "$3" = "null" ] || [ -z "$3" ]; then
    sh_ settings delete "$1" "$2" >/dev/null || true
  else
    sh_ settings put "$1" "$2" "$3" >/dev/null || true
  fi
}

cleanup() {
  code=$?
  restore_setting system font_scale "$SAVED_FONT_SCALE"
  restore_setting global animator_duration_scale "$SAVED_ANIMATOR"
  if [ "$SAVED_NOTIF" = granted ]; then
    sh_ pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  else
    sh_ pm revoke "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  fi
  if [ "$KEEP" -eq 1 ]; then
    echo "device-smoke: dumps kept in $WORK"
  else
    rm -rf "$WORK"
  fi
  exit "$code"
}
trap cleanup EXIT

# ---- reporting ------------------------------------------------------------------------------

SCREEN="startup"
CHECKS=0

note()  { printf '  %s\n' "$1"; }
step()  { SCREEN="$1"; printf '\n== %s ==\n' "$1"; }

pass() { CHECKS=$((CHECKS + 1)); printf '  ok   %s\n' "$1"; }

fail() { # what-was-expected what-was-found
  printf '\ndevice-smoke: FAIL on screen "%s"\n' "$SCREEN" >&2
  printf '  expected: %s\n' "$1" >&2
  printf '  found:    %s\n' "$2" >&2
  printf '  dumps taken: %s, elapsed: %ss\n' "$DUMPS" "$((SECONDS - START_TS))" >&2
  [ "$KEEP" -eq 1 ] || printf '  re-run with --keep to hold on to the dumps\n' >&2
  exit 1
}

lost() { # what the walk could not reach
  printf '\ndevice-smoke: the walk lost its way on screen "%s"\n' "$SCREEN" >&2
  printf '  %s\n' "$1" >&2
  exit 3
}

# ---- the accessibility tree ------------------------------------------------------------------

# One dump becomes one tab-separated table, one line per node, in document order:
#   1 depth  2 left  3 top  4 right  5 bottom  6 clickable  7 enabled  8 checked
#   9 package  10 class  11 text  12 content-desc
# shellcheck disable=SC2016  # an awk program: $0 and $1 belong to awk, not to the shell
NODES_AWK='
function attr(s, name,   v, na, pc, qi) {
  if (match(s, name "=\"[^\"]*\"")) {
    v = substr(s, RSTART + length(name) + 2, RLENGTH - length(name) - 3)
    gsub(/&#10;/, " ", v); gsub(/&quot;/, "\"", v); gsub(/&apos;/, "\047", v)
    gsub(/&lt;/, "<", v); gsub(/&gt;/, ">", v)
    na = split(v, pc, /&amp;/); v = pc[1]
    for (qi = 2; qi <= na; qi++) v = v sprintf("%c", 38) pc[qi]
    gsub(/[\t\r]/, " ", v)
    return v
  }
  return ""
}
BEGIN { depth = 0 }
/^\/node/ { if (depth > 0) depth--; next }
/^node / {
  b = attr($0, "bounds"); n = 0
  while (match(b, /-?[0-9]+/)) { n++; num[n] = substr(b, RSTART, RLENGTH) + 0; b = substr(b, RSTART + RLENGTH) }
  if (n >= 4)
    print depth, num[1], num[2], num[3], num[4], attr($0, "clickable"), attr($0, "enabled"), \
          attr($0, "checked"), attr($0, "package"), attr($0, "class"), attr($0, "text"), attr($0, "content-desc")
  if (substr($0, length($0) - 1) != "/>") depth++
  next
}
'

# dump_screen NAME -> $WORK/NAME.tsv, restricted to the app under test.
dump_screen() {
  _name="$1"; _try=0
  while [ "$_try" -lt 3 ]; do
    _try=$((_try + 1))
    DUMPS=$((DUMPS + 1))
    adbx exec-out uiautomator dump /dev/tty > "$WORK/$_name.xml" 2>/dev/null || true
    tr '<' '\n' < "$WORK/$_name.xml" \
      | awk -v OFS='\t' "$NODES_AWK" \
      | awk -F'\t' -v OFS='\t' -v pkg="$PKG" '$9 == pkg' > "$WORK/$_name.tsv" || true
    [ -s "$WORK/$_name.tsv" ] && return 0
    sleep 1
  done
  lost "uiautomator returned no node of $PKG for '$_name' in three attempts"
}

# dump_until NAME PATTERN -> dump, and if PATTERN is not on the screen yet, wait and dump again.
# Bounded at three dumps: this is the one place the walk is allowed to pay for a second dump, and
# it exists because the first launch after `pm clear` settles at about 12 s (docs/data-map.md).
dump_until() {
  _name="$1"; _pattern="$2"; _n=0
  while [ "$_n" -lt 3 ]; do
    _n=$((_n + 1))
    dump_screen "$_name"
    if screen_has "$_name" "$_pattern"; then return 0; fi
    sleep 4
  done
  return 1
}

# Every text and content-desc on a screen, one per line.
screen_text() { cut -f11,12 < "$WORK/$1.tsv" | tr '\t' '\n' | grep -v '^$' || true; }

screen_has() { screen_text "$1" | grep -qE "$2"; }

# node_center NAME FIELD VALUE -> "x y" for the first node whose FIELD equals VALUE exactly.
node_center() {
  awk -F'\t' -v f="$2" -v want="$3" '$f == want { print int(($2 + $4) / 2), int(($3 + $5) / 2); exit }' \
    "$WORK/$1.tsv"
}

# tap_center NAME FIELD VALUE: tap the nearest clickable ancestor of that node, or the node itself.
tap_center() {
  _xy="$(awk -F'\t' -v f="$2" -v want="$3" '
    { stack[$1] = $0 }
    $f == want {
      for (i = $1; i >= 0; i--) {
        split(stack[i], n, "\t")
        if (n[6] == "true") { print int((n[2] + n[4]) / 2), int((n[3] + n[5]) / 2); exit }
      }
      print int(($2 + $4) / 2), int(($3 + $5) / 2); exit
    }' "$WORK/$1.tsv")"
  [ -n "$_xy" ] || lost "nothing on the screen has $2 = '$3', so there was nothing to tap"
  # shellcheck disable=SC2086  # two integers, deliberately word-split into adb's two arguments
  sh_ input tap $_xy
}

focused_pkg() {
  sh_ dumpsys window \
    | grep -o 'mCurrentFocus=Window{[^}]*}' \
    | tail -1 \
    | sed -e 's/.*Window{[^ ]* [^ ]* //' -e 's|/.*||' -e 's/}.*//' | tr -d '\r'
}

# Cheap polling: dumpsys window costs about 0.1 s, a dump costs 2.4 s.
wait_focus() { # want-pkg-or-"not:PKG" timeout-seconds
  _end=$((SECONDS + $2))
  while [ "$SECONDS" -lt "$_end" ]; do
    _f="$(focused_pkg)"
    case "$1" in
      not:*)
        if [ -n "$_f" ] && [ "$_f" != "${1#not:}" ]; then printf '%s' "$_f"; return 0; fi
        ;;
      *)
        if [ "$_f" = "$1" ]; then printf '%s' "$_f"; return 0; fi
        ;;
    esac
    sleep 0.3
  done
  printf '%s' "$(focused_pkg)"
  return 1
}

frames_over_window() { # -> frames rendered by the app over MOTION_WINDOW_S seconds
  sh_ dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1 || true
  sleep "$MOTION_WINDOW_S"
  sh_ dumpsys gfxinfo "$PKG" | awk -F': *' '/Total frames rendered/ { print $2 + 0; exit }'
}

# ---- the product rules, asserted on every screen ---------------------------------------------

# The four verdict words. PlainTicker classifies, it never advises: "Swap" is the only trading verb
# and the direction flip is "TSLAx to USDC" (DESIGN.md section 7). Word boundaries matter, so
# "Holdings" and "Pool holds $34" are not findings.
VERDICT_WORDS='buy|sell|hold|avoid'
# The server's `headline` field is Ukrainian and must never be rendered. UTF-8 lead bytes D0 to D3
# are exactly U+0400 to U+04FF, so this finds Cyrillic without needing a UTF-8 locale.
CYRILLIC=$'[\xd0-\xd3]'
EM_DASH=$'\xe2\x80\x94'
ISO_STAMP='[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}'

# Task A1: the List carries analyzed rows only, chaptered by sector, so "Analyzed" as one heading
# over every row is gone. The eleven GICS sectors /summary classifies against (verified live in
# app/src/test/resources/plainticker/summary.json, e.g. "Financials", "Information Technology"),
# plus the trailing chapter for a row /summary sent no sector for.
SECTOR_HEADINGS='Energy|Materials|Industrials|Consumer Discretionary|Consumer Staples|Health Care|Financials|Information Technology|Communication Services|Utilities|Real Estate|No sector'

assert_lint() {
  _n="$1"
  # Every copy rule below is an absence, and an absence is free on a screen that drew nothing. This
  # is the one place the walk checks that there was something to read in the first place, and every
  # assert_labels and assert_geometry call in the walk is preceded by an assert_lint on that dump.
  _strings="$(screen_text "$_n" | grep -c . || true)"
  [ "$_strings" -ge "$MIN_STRINGS_PER_SCREEN" ] || fail \
    "a screen with something on it: at least $MIN_STRINGS_PER_SCREEN readable strings, so that the \
copy rules below are absences on a drawn screen rather than on an empty one" \
    "$_strings string(s); this dump has nodes of $PKG but next to nothing a reader could see"

  _hit="$(screen_text "$_n" | grep -Eiw "$VERDICT_WORDS" | head -3 || true)"
  [ -z "$_hit" ] || fail "no verdict word (${VERDICT_WORDS//|/, }) anywhere on the screen" \
                         "$(printf '%s' "$_hit" | tr '\n' '/')"

  _hit="$(screen_text "$_n" | LC_ALL=C grep -m3 "$CYRILLIC" || true)"
  [ -z "$_hit" ] || fail "no Cyrillic on the screen: the server's headline field is Ukrainian" \
                         "$(printf '%s' "$_hit" | tr '\n' '/')"

  _hit="$(screen_text "$_n" | LC_ALL=C grep -m3 -F "$EM_DASH" || true)"
  [ -z "$_hit" ] || fail "no em dash (DESIGN.md section 8)" "$(printf '%s' "$_hit" | tr '\n' '/')"

  _hit="$(screen_text "$_n" | grep -Em3 "$ISO_STAMP" || true)"
  [ -z "$_hit" ] || fail "no raw ISO timestamp: times go through Fmt" \
                         "$(printf '%s' "$_hit" | tr '\n' '/')"

  _hit="$(screen_text "$_n" | grep -Em3 '[0-9]\.[0-9]{3,}' || true)"
  [ -z "$_hit" ] || fail "no unformatted float: every number goes through Fmt, which is 2dp for \
prices and percents and 6dp trimmed for token amounts" "$(printf '%s' "$_hit" | tr '\n' '/')"

  _hit="$(awk -F'\t' '$11 ~ /^-?[0-9]+\.[0-9]+$/ { print $11; c++ } c == 3 { exit }' "$WORK/$_n.tsv")"
  [ -z "$_hit" ] || fail "no bare decimal drawn as a value: the composite is an integer percentile" \
                         "$(printf '%s' "$_hit" | tr '\n' '/')"
  pass "copy rules hold over $_strings strings (no verdict word, no Cyrillic, no em dash, no ISO stamp, no raw float)"
}

# Pass 6 of the plan: every clickable speaks. A clickable with neither its own label nor a labelled
# descendant is silent under TalkBack.
assert_labels() {
  _hit="$(awk -F'\t' '
    { d[NR] = $1; clk[NR] = $6; l[NR] = $2; t[NR] = $3; r[NR] = $4; b[NR] = $5
      tx[NR] = $11; ds[NR] = $12; n = NR }
    END {
      for (i = 1; i <= n; i++) {
        if (clk[i] != "true") continue
        lab = tx[i] ds[i]
        for (j = i + 1; j <= n && d[j] > d[i]; j++) lab = lab tx[j] ds[j]
        gsub(/[ \t]/, "", lab)
        if (lab == "") { print "[" l[i] "," t[i] "][" r[i] "," b[i] "]"; c++ }
        if (c == 3) exit
      }
    }' "$WORK/$1.tsv")"
  [ -z "$_hit" ] || fail "every clickable carries a label, its own or a descendant's" \
                         "silent clickable at $(printf '%s' "$_hit" | tr '\n' ' ')"
  pass "every clickable carries a label"
}

# Nothing clipped: a text node stays on the screen, inside the 20dp gutters, inside whatever
# clickable contains it, and has a size.
assert_geometry() {
  _hit="$(awk -F'\t' -v W="$SCREEN_W" -v H="$SCREEN_H" -v gut="$GUTTER_PX" -v right="$CONTENT_RIGHT" '
    { d[NR] = $1; clk[NR] = $6; l[NR] = $2; t[NR] = $3; r[NR] = $4; b[NR] = $5; tx[NR] = $11; n = NR }
    END {
      for (i = 1; i <= n; i++) {
        if (tx[i] == "") continue
        box = "\"" substr(tx[i], 1, 28) "\" at [" l[i] "," t[i] "][" r[i] "," b[i] "]"
        if (r[i] <= l[i] || b[i] <= t[i]) { print "collapsed to nothing: " box; c++ }
        else if (l[i] < 0 || r[i] > W || t[i] < 0 || b[i] > H) { print "off the " W "x" H " screen: " box; c++ }
        else if (l[i] < gut || r[i] > right) { print "outside the " gut "px gutters: " box; c++ }
        else {
          cur = d[i]
          for (k = i - 1; k >= 1; k--) {
            if (d[k] >= cur) continue
            cur = d[k]
            if (clk[k] != "true") continue
            if (l[i] < l[k] || r[i] > r[k] || t[i] < t[k] || b[i] > b[k])
              { print "outside the control that holds it [" l[k] "," t[k] "][" r[k] "," b[k] "]: " box; c++ }
            break
          }
        }
        if (c == 3) exit
      }
    }' "$WORK/$1.tsv")"
  [ -z "$_hit" ] || fail "every text stays on the screen, inside the 20dp gutters and inside its control" \
                         "$(printf '%s' "$_hit" | tr '\n' '; ')"
  pass "nothing clipped: text is on screen, inside the gutters and inside its control"
}

# Every analyzed row is one ticker, one integer composite and one state word. The composite drawn
# as a raw float (83.80406) is a regression that already happened once, on 2026-09-12.
assert_row_shape() {
  _hit="$(awk -F'\t' -v H="$SCREEN_H" '
    { d[NR] = $1; clk[NR] = $6; t[NR] = $3; b[NR] = $5; tx[NR] = $11; n = NR
      if ($11 == "Without analysis") floor_y = $3 }
    END {
      if (floor_y == "") floor_y = 1e9
      for (i = 1; i <= n; i++) {
        # A row the screen edge cuts in half has drawn only part of itself, which is the scroll
        # position speaking and not the product.
        if (clk[i] != "true" || t[i] >= floor_y || b[i] >= H) continue
        ticker = ""; ints = 0; states = 0; floats = ""; seen = ""
        for (j = i + 1; j <= n && d[j] > d[i]; j++) {
          v = tx[j]
          if (v == "") continue
          seen = seen " " v
          if (v ~ /^[A-Z][A-Z0-9.]*x$/ && ticker == "") ticker = v
          else if (v ~ /^[0-9]{1,3}$/ && v + 0 <= 100) ints++
          else if (v == "strong" || v == "fair" || v == "weak") states++
          else if (v ~ /^-?[0-9]+\.[0-9]+$/) floats = floats " " v
        }
        if (ticker == "") continue
        rows++
        if (ints != 1) { print ticker ": " ints " integer composites in" seen; c++ }
        else if (states != 1) { print ticker ": " states " state words in" seen; c++ }
        else if (floats != "") { print ticker ": a bare decimal" floats; c++ }
        if (c == 3) exit
      }
      if (rows < 3 && c == 0) print "only " rows " analyzed rows were drawn"
    }' "$WORK/$1.tsv")"
  [ -z "$_hit" ] || fail "each analyzed row draws one ticker, one integer composite (0-100) and one state word" \
                         "$(printf '%s' "$_hit" | tr '\n' ';')"
  pass "every analyzed row is ticker plus integer composite plus state word"
}

# Task A1: the List's analyzed rows are chaptered by sector rather than drawn under one "Analyzed"
# heading. At least two distinct sector headings prove the list is actually chaptered rather than
# one heading renamed, and each carries a plain integer count as its mono meta (Heading, DESIGN.md
# section 4). This does not require a scroll: production runs about eleven sectors over 157 rows,
# so more than one heading is on the first screenful.
assert_sector_chapters() {
  _n="$1"
  _headings="$(screen_text "$_n" | grep -E "^($SECTOR_HEADINGS)\$" | sort -u || true)"
  _count="$(printf '%s\n' "$_headings" | grep -c . || true)"
  [ "$_count" -ge 2 ] || fail \
    "at least two distinct sector chapter headings (one of: ${SECTOR_HEADINGS//|/, })" \
    "$_count found$([ "$_count" -eq 0 ] || printf ': %s' "$(printf '%s' "$_headings" | tr '\n' ';')")"
  ! screen_has "$_n" '^Analyzed$' || fail \
    "no single 'Analyzed' heading over every row: task A1 chaptered the list by sector" \
    "'Analyzed' is still drawn"
  pass "chaptered by sector: $_count headings ($(printf '%s' "$_headings" | tr '\n' ';'))"
}

# Detail's section order is fixed (DESIGN.md section 5): the trust layer leads, the method closes.
DETAIL_ORDER='Live from the mint|Backing and controls|Against the sector|F-Score|Method'

headings_of() { awk -F'\t' -v re="^($DETAIL_ORDER)$" '$11 ~ re { print $11 }' "$WORK/$1.tsv"; }

# Dumps overlap, so a heading is recorded the first time it is seen and never again; what is left
# is the order the reader meets the sections in, top to bottom.
assert_detail_order() { # every Detail dump name, top to bottom
  _seen="|"
  for _n in "$@"; do
    while IFS= read -r _h; do
      [ -n "$_h" ] || continue
      case "$_seen" in
        *"|$_h|"*) ;;
        *) _seen="$_seen$_h|" ;;
      esac
    done <<EOF
$(headings_of "$_n")
EOF
  done
  _want="|Live from the mint|Backing and controls|Against the sector|F-Score|Method|"
  [ "$_seen" = "$_want" ] || fail "Detail headings in the order fixed by DESIGN.md section 5: $_want" \
                                  "$_seen"
  pass "Detail headings in the fixed order: $_want"
}

# The trust layer. A dump carries no colour, so what is asserted here is the wording that is drawn
# in Caution: "Issuer can move tokens" and "issuer can pause" belong to an issuer control the
# issuer actually holds, and to nothing else on the screen. The colour itself is pinned without a
# device by DetailModelTest.
DELEGATE_CAUTION='Issuer can move tokens'
PAUSABLE_CAUTION='issuer can pause'

assert_trust_rows() {
  _n="$1"
  for _label in 'Proof of reserves' 'Permanent delegate' 'Transfers pausable' 'Split multiplier' 'Transfer hook'; do
    screen_has "$_n" "^$_label: " || fail "the trust grid carries the row \"$_label\"" \
      "$(screen_text "$_n" | grep -E '^[A-Z][a-z].*: ' | tr '\n' ';' || echo 'no fact cell at all')"
  done

  _delegate="$(screen_text "$_n" | grep -E '^Permanent delegate: ' | head -1)"
  _pausable="$(screen_text "$_n" | grep -E '^Transfers pausable: ' | head -1)"

  case "$_delegate" in
    "Permanent delegate: Yes"*)
      case "$_delegate" in
        *"$DELEGATE_CAUTION"*) ;;
        *) fail "a permanent delegate the issuer holds says so: \"$DELEGATE_CAUTION\"" "$_delegate" ;;
      esac ;;
    *)
      case "$_delegate" in
        *"$DELEGATE_CAUTION"*) fail "no issuer-control caution where the issuer holds no delegate" "$_delegate" ;;
      esac ;;
  esac
  case "$_pausable" in
    "Transfers pausable: Yes"*)
      case "$_pausable" in
        *"$PAUSABLE_CAUTION"*) ;;
        *) fail "transfers the issuer can pause say so: \"$PAUSABLE_CAUTION\"" "$_pausable" ;;
      esac ;;
    *)
      case "$_pausable" in
        *"$PAUSABLE_CAUTION"*) fail "no issuer-control caution where transfers cannot be paused" "$_pausable" ;;
      esac ;;
  esac

  _strays="$(screen_text "$_n" \
    | grep -F -e "$DELEGATE_CAUTION" -e "$PAUSABLE_CAUTION" \
    | grep -vE '^(Permanent delegate|Transfers pausable): ' || true)"
  [ -z "$_strays" ] || fail "the issuer-control caution wording appears on those two values and nowhere else" \
                            "$(printf '%s' "$_strays" | tr '\n' ';')"
  pass "trust grid complete; caution wording on the issuer controls only ($_delegate / $_pausable)"
}

# The live bar's meta is "slot 446,603,268 · 2 s ago". Either half moving proves the clock is
# running: the age ticks, and if the mint was read again the slot changes too. A frozen meta is
# the regression this project has already shipped once.
live_meta() { awk -F'\t' '$11 ~ /^slot / { print $11; exit }' "$WORK/$1.tsv"; }

assert_live_bar_ticks() { # earlier-dump later-dump
  _a="$(live_meta "$1")"; _b="$(live_meta "$2")"
  [ -n "$_a" ] || fail "a live bar meta line (\"slot N, M s ago\") on the first read" "nothing starting with 'slot '"
  [ -n "$_b" ] || fail "a live bar meta line on the second read" "nothing starting with 'slot '"
  [ "$_a" != "$_b" ] || fail "the live bar's age to move between two reads" "both reads said \"$_a\""
  pass "live bar moved: \"$_a\" then \"$_b\""
}

# One numeral per distinct value, from the first node that draws it.
numerals_of() {
  awk -F'\t' '$11 ~ /^[^ ]*[0-9][^ ]*$/ && !seen[$11]++ { print $11 "\t" ($4 - $2) "\t" ($5 - $3) }' \
    "$WORK/$1.tsv"
}

# A numeral that kept to one line grows with the font scale. One that wrapped roughly doubles, and
# one that was squeezed gets narrower instead of wider.
assert_numerals_scaled() { # dump-at-1.0 dump-at-the-larger-scale
  numerals_of "$1" > "$WORK/num-base.tsv"
  numerals_of "$2" > "$WORK/num-scaled.tsv"
  _report="$(awk -F'\t' -v maxr="$WRAP_RATIO_MAX" -v tookr="$SCALE_TOOK_MIN" '
    NR == FNR { w[$1] = $2; h[$1] = $3; next }
    ($1 in w) && w[$1] > 0 && h[$1] > 0 {
      matched++
      rw = int($2 * 100 / w[$1]); rh = int($3 * 100 / h[$1])
      if (rh > took) took = rh
      if (rh > maxr) { bad = bad "\"" $1 "\" is " rh "% of its height at 1.0 (" h[$1] "px to " $3 "px); "; c++ }
      else if (rw < 100) { bad = bad "\"" $1 "\" is narrower at the larger scale (" w[$1] "px to " $2 "px); "; c++ }
    }
    END {
      if (matched < 3) { print "TOOFEW " matched; exit }
      if (c > 0) { print "WRAPPED " bad; exit }
      if (took < tookr) { print "NOSCALE " took; exit }
      print "OK " matched " numerals, tallest grew to " took "%"
    }' "$WORK/num-base.tsv" "$WORK/num-scaled.tsv")"
  case "$_report" in
    OK*)      pass "font scale ${FONT_SCALE_PCT}%: ${_report#OK }" ;;
    TOOFEW*)  fail "the same numerals drawn at both scales, at least three of them" \
                   "only ${_report#TOOFEW } numerals were drawn in both passes" ;;
    WRAPPED*) fail "every numeral on one line at font scale ${FONT_SCALE_PCT}% (height under ${WRAP_RATIO_MAX}% of its 1.0 height)" \
                   "${_report#WRAPPED }" ;;
    NOSCALE*) fail "font scale ${FONT_SCALE_PCT}% to change the layout (a numeral at least ${SCALE_TOOK_MIN}% of its 1.0 height)" \
                   "the tallest numeral reached only ${_report#NOSCALE }%, so the scale never took" ;;
    *)        fail "a verdict from the numeral comparison" "$_report" ;;
  esac
}

assert_motion() { # "breathing" | "static"
  _frames="$(frames_over_window)"
  case "$_frames" in ''|*[!0-9]*) fail "a frame count from dumpsys gfxinfo" "'$_frames'" ;; esac
  if [ "$1" = breathing ]; then
    [ "$_frames" -ge "$MOTION_MIN_FRAMES" ] || fail \
      "the live bar to breathe: at least $MOTION_MIN_FRAMES frames in ${MOTION_WINDOW_S}s" \
      "$_frames frames, so nothing on the screen is moving"
    pass "live bar breathing: $_frames frames in ${MOTION_WINDOW_S}s"
  else
    [ "$_frames" -le "$STILL_MAX_FRAMES" ] || fail \
      "the live bar static at animator scale 0: at most $STILL_MAX_FRAMES frames in ${MOTION_WINDOW_S}s" \
      "$_frames frames, so something is still animating"
    pass "live bar static at animator scale 0: $_frames frames in ${MOTION_WINDOW_S}s"
  fi
}

# ---- navigation -------------------------------------------------------------------------------

launch_measured() { # -> TotalTime in ms
  sh_ am start -W -n "$PKG/$ACTIVITY" | awk -F': *' '/^TotalTime/ { print $2 + 0; exit }' | tr -d '\r'
}

open_list_search() { # NAME-of-a-list-dump TICKER
  tap_center "$1" 12 Search
  sleep 0.6
  sh_ input text "$2"
  sleep 1.5
}

clear_list_search() { # NAME-of-a-dump-that-shows-Clear
  tap_center "$1" 11 Clear
  sleep 1.2
}

# The list has nothing that moves, so "no frames for a second" means it has stopped changing.
# This costs about 0.1 s a poll against 2.4 s for a dump, and it is what keeps the walk from
# tapping a row that the snapshot banner is about to slide out from under it.
wait_quiet() { # max-seconds
  _end=$((SECONDS + $1))
  while [ "$SECONDS" -lt "$_end" ]; do
    sh_ dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1 || true
    sleep 1
    _f="$(sh_ dumpsys gfxinfo "$PKG" | awk -F': *' '/Total frames rendered/ { print $2 + 0; exit }')"
    case "$_f" in ''|*[!0-9]*) _f=999 ;; esac
    # "No frames" means settled only once the app owns the screen. Between `am start` and the first
    # frame, gfxinfo answers a literal 0 for a few tenths of a second, and reading that as quiet is
    # exactly how a walk ends up taking coordinates off a screen that is still moving.
    [ "$(focused_pkg)" = "$PKG" ] || continue
    if [ "$_f" -le 2 ]; then return 0; fi
  done
  return 0   # a screen that never settles is the screen's business; the walk carries on
}

# LIST-DUMP is a dump of the Stocks screen as it stands (still named "list" internally, per
# ui/stocks/StocksScreen.kt's own doc comment: "the 160-row list, moved here rather than
# rewritten"; there is no tab bar to name a tab on any more, docs/design-research-2026-09-21.md
# section 3); TICKERX is the row to open; PREFIX names the dumps this makes, and PREFIX-top is left
# holding the top of Detail. Search rather than scroll: the list is sorted by composite, so where a
# row sits is data, and a walk that depends on data is a walk that fails on a Tuesday.
open_detail() { # LIST-DUMP TICKERX PREFIX
  open_list_search "$1" "${2%x}"
  sh_ input keyevent KEYCODE_BACK   # close the keyboard, not the screen
  sleep 0.6
  wait_quiet 20
  _attempt=0
  while [ "$_attempt" -lt 2 ]; do
    _attempt=$((_attempt + 1))
    dump_screen "$3-found"
    screen_has "$3-found" "^$2$" || lost "no row for $2 after searching '${2%x}' on the list"
    tap_center "$3-found" 11 "$2"
    sleep 2.5
    if dump_until "$3-top" '^Backing and controls$'; then return 0; fi
  done
  lost "tapping the $2 row never opened its Detail"
}

scroll_down() {
  sh_ input swipe "$((SCREEN_W / 2))" "$((SCREEN_H * 79 / 100))" "$((SCREEN_W / 2))" "$((SCREEN_H * 34 / 100))" 700
  sleep 0.7
}

# Three flings, not three drags: this is used where only the end of the screen matters, and
# overshooting the end of a scroll costs nothing.
scroll_to_bottom() {
  _i=0
  while [ "$_i" -lt 3 ]; do
    _i=$((_i + 1))
    sh_ input swipe "$((SCREEN_W / 2))" "$((SCREEN_H * 86 / 100))" "$((SCREEN_W / 2))" "$((SCREEN_H * 14 / 100))" 250
  done
  sleep 1.2
}

scroll_to_top() {
  _i=0
  while [ "$_i" -lt 6 ]; do
    _i=$((_i + 1))
    sh_ input swipe "$((SCREEN_W / 2))" "$((SCREEN_H * 24 / 100))" "$((SCREEN_W / 2))" "$((SCREEN_H * 86 / 100))" 300
  done
  sleep 1
}

# Only the registered-job headers count. Everything else dumpsys says about the package is
# history, quota bookkeeping and recently completed work, and none of that is a schedule.
jobs_for_app() { sh_ dumpsys jobscheduler | grep -cE "^  JOB .*$PKG" || true; }

# ---- pass 1: the walk at the phone's own settings ----------------------------------------------

printf 'device-smoke: %s on %s, %sx%s\n' "$PKG" "$SERIAL" "$SCREEN_W" "$SCREEN_H"
printf 'device-smoke: deep pool %s, under the liquidity floor %s\n' "$DEEP_X" "$THIN_X"

step "launch, cold after pm clear"
sh_ am force-stop "$PKG" >/dev/null || true
sh_ pm clear "$PKG" >/dev/null || lost "pm clear was refused"
TOTAL_MS="$(launch_measured)"
case "$TOTAL_MS" in ''|*[!0-9]*) fail "a TotalTime from 'am start -W'" "'$TOTAL_MS'" ;; esac
[ "$TOTAL_MS" -le "$LAUNCH_CEILING_MS" ] || fail \
  "launch at or under ${LAUNCH_CEILING_MS}ms (the whole measured time to first content, of which \
this is only the part before the first frame)" "TotalTime ${TOTAL_MS}ms"
pass "launch TotalTime ${TOTAL_MS}ms, ceiling ${LAUNCH_CEILING_MS}ms"

step "onboarding"
dump_until onboarding '^Read the list$' || lost "onboarding never drew 'Read the list'"
assert_lint onboarding
assert_labels onboarding
assert_geometry onboarding
screen_has onboarding '^Tokenized stocks, read before you swap\.$' \
  || fail "the onboarding headline" "$(screen_text onboarding | head -3 | tr '\n' ';')"
screen_has onboarding '^I am not a US person,' \
  || fail "the self-certification sentence" "$(screen_text onboarding | tr '\n' ';')"
# The same ancestor walk tap_center uses: a depth stack, so what is read is the control that holds
# "Read the list" and not whichever earlier node happened to be both shallower and clickable.
_gate="$(awk -F'\t' '
  { stack[$1] = $0 }
  $11 == "Read the list" {
    for (i = $1; i >= 0; i--) {
      split(stack[i], n, "\t")
      if (n[6] == "true") { print n[7]; exit }
    }
    exit
  }' "$WORK/onboarding.tsv")"
[ "$_gate" = false ] || fail "\"Read the list\" disabled until the certification is checked" \
                             "its control reports enabled=$_gate"
pass "the way in is closed until the reader certifies"

tap_center onboarding 11 'I am not a US person, and I understand xStocks are tokenized tracker instruments issued by a third party, not shares.'
sleep 0.5
tap_center onboarding 11 'Read the list'
sleep 2

step "today"
# Today is the new home (docs/design-research-2026-09-21.md section 3), replacing the List tab as
# the app's first screen. "Daily digest" is an unconditional heading in Today's Yours block (folded
# in from the old Watchlist screen), so it is on screen whether or not anything is watched yet: the
# same landmark the pre-rewrite watchlist step used, now met on first launch instead of on a tab.
dump_until today '^Daily digest$' || lost "the app never landed on Today after onboarding"
wait_quiet 20
dump_screen today
assert_lint today
assert_labels today
assert_geometry today
for _tab in Today Stocks Vote Portfolio You; do
  screen_has today "^$_tab\$" || fail "the five bar destinations" "no destination named $_tab"
done
pass "the five bar destinations are drawn"
screen_has today '^Watched$' \
  || fail "the Yours block, folded in from the watchlist" "$(screen_text today | head -8 | tr '\n' ';')"

step "stocks"
tap_center today 11 Stocks
sleep 1
dump_until stocks "^($SECTOR_HEADINGS)\$" || lost "Stocks never drew a sector chapter heading"
wait_quiet 20
dump_screen stocks
assert_lint stocks
assert_labels stocks
assert_geometry stocks
assert_row_shape stocks
assert_sector_chapters stocks

step "detail, deep pool ($DEEP_X)"
open_detail stocks "$DEEP_X" deep
assert_lint deep-top
assert_labels deep-top
assert_geometry deep-top
screen_has deep-top "^$DEEP_X\$" || fail "the ticker $DEEP_X in the hero" "$(screen_text deep-top | head -4 | tr '\n' ';')"
screen_has deep-top '^Token price$' || fail "the price row" "no 'Token price' label"
screen_has deep-top '^NYSE close$|^NYSE price$' || fail "the NYSE reference label" "neither 'NYSE close' nor 'NYSE price'"
screen_has deep-top 'vs NYSE (close|price): (plus|minus) [0-9]' \
  || fail "a gauge reading the token against the NYSE, for a pool above the \$10,000 floor" \
          "$(screen_text deep-top | grep -i 'vs NYSE' | tr '\n' ';' || echo 'no gauge reading at all')"
assert_trust_rows deep-top
assert_motion breathing

scroll_down
dump_screen deep-sector
assert_lint deep-sector
assert_geometry deep-sector
screen_has deep-sector '^composite [0-9]{1,3}$' \
  || fail "the composite in the 'Against the sector' heading, as an integer percentile" \
          "$(screen_text deep-sector | grep -i composite | tr '\n' ';' || echo 'no composite at all')"
for _track in Quality Valuation Momentum; do
  screen_has deep-sector "^$_track: " || fail "the three tracks" "no track named $_track"
done
pass "composite is an integer and the three tracks are drawn"

scroll_down
dump_screen deep-fscore
assert_lint deep-fscore
assert_geometry deep-fscore
screen_has deep-fscore '^F-Score [0-9] of 9$' \
  || fail "the F-Score read as 'N of 9'" "$(screen_text deep-fscore | grep -i 'f-score' | tr '\n' ';')"
_signals="$(screen_text deep-fscore | grep -cE ': (yes|no)$' || true)"
[ "$_signals" -eq 9 ] || fail "nine F-Score signals" "$_signals signals"
pass "F-Score with its nine signals"

scroll_down
dump_screen deep-foot
assert_lint deep-foot
assert_labels deep-foot
assert_geometry deep-foot
screen_has deep-foot '^Rule-based classification of fundamentals' \
  || fail "the method statement" "$(screen_text deep-foot | tail -4 | tr '\n' ';')"
screen_has deep-foot "^Swap USDC to $DEEP_X\$" \
  || fail "one button reading \"Swap USDC to $DEEP_X\"" "$(screen_text deep-foot | tail -3 | tr '\n' ';')"
pass "method statement and the single Swap button"

assert_detail_order deep-top deep-sector deep-fscore deep-foot

step "swap, as far as a debug build honestly goes"
# BuildConfig.SUBMIT_SWAPS is false in debug, and the sheet's first act is to ask a wallet who it
# is. That handoff is the honest end of a machine walk: approving it needs a person, and the rest
# of the swap is docs/qa-checklist.md. Nothing here signs anything and no money moves.
tap_center deep-foot 11 "Swap USDC to $DEEP_X"
_holder="$(wait_focus "not:$PKG" 8 || true)"
if [ -n "$_holder" ] && [ "$_holder" != "$PKG" ]; then
  pass "the Swap button hands off to a wallet ($_holder); nothing signed, nothing submitted"
  sh_ input keyevent KEYCODE_BACK
  sleep 1.5
  _back="$(wait_focus "$PKG" 8 || true)"
  [ "$_back" = "$PKG" ] || lost "the wallet kept the focus after BACK (now $_back)"
  pass "declining the wallet returns to Detail"
else
  dump_screen deep-nowallet
  assert_lint deep-nowallet
  screen_has deep-nowallet '^No wallet on this device answers the app$' \
    || fail "either a wallet taking the handoff, or the sheet saying no wallet answered" \
            "focus stayed on $PKG and the sheet said $(screen_text deep-nowallet | tail -3 | tr '\n' ';')"
  pass "no wallet on this device, and the sheet says so"
fi

step "the live bar's clock"
scroll_to_top
dump_screen deep-top-again
assert_lint deep-top-again
assert_live_bar_ticks deep-top deep-top-again

step "detail, under the liquidity floor ($THIN_X)"
sh_ input keyevent KEYCODE_BACK
sleep 1.5
dump_screen thin-stocks
clear_list_search thin-stocks
open_detail thin-stocks "$THIN_X" thin
assert_lint thin-top
assert_labels thin-top
assert_geometry thin-top
# Fixed 2026-09-22: this checked wording from before detail_gauge_thin's current copy ("Pool holds
# $34, too thin to track the NYSE close") and would have failed on every real run since, because
# that sentence was never shipped; the copy that is shipped is detail_gauge_thin's own fixed
# fragment below (the formatted "$34 behind this price" half of that string is not stable, which is
# why the match is a substring rather than the whole sentence).
screen_has thin-top 'too little for the token to follow the NYSE close' \
  || fail "the pool sentence where the gauge would be, because $THIN_X sits under the \$10,000 floor \
(pass another ticker with --thin if this pool has since grown)" \
          "$(screen_text thin-top | grep -iE 'pool|vs NYSE' | tr '\n' ';' || echo 'neither a pool sentence nor a gauge')"
if screen_has thin-top 'vs NYSE (close|price): (plus|minus)'; then
  fail "no premium for a token under the liquidity floor: the pool sentence stands instead" \
       "$(screen_text thin-top | grep -i 'vs NYSE' | tr '\n' ';')"
fi
assert_trust_rows thin-top
pass "the pool sentence stands where the gauge would be, and no premium is drawn"

step "today, watching a ticker"
_jobs_before="$(jobs_for_app)"
tap_center thin-top 11 Watch
sleep 1.2
# The first watch is the one moment the app asks for POST_NOTIFICATIONS (plan section 13, Pass 2).
# The dialog belongs to the permission controller; the walk dismisses it rather than answering for
# a person, and the digest lands on the screen either way.
_dialog="$(wait_focus "not:$PKG" 3 || true)"
if [ -n "$_dialog" ] && [ "$_dialog" != "$PKG" ]; then
  pass "the first watch asks for notifications ($_dialog), and nothing else does"
  sh_ input keyevent KEYCODE_BACK
  sleep 1
  _back="$(wait_focus "$PKG" 6 || true)"
  [ "$_back" = "$PKG" ] || lost "the permission dialog kept the focus (now $_back)"
fi
dump_screen watched-detail
assert_lint watched-detail
screen_has watched-detail '^Watching$' \
  || fail "the header action to read \"Watching\" after the tap" \
          "$(screen_text watched-detail | grep -E '^Watch' | tr '\n' ';' || echo 'no watch action at all')"
pass "$THIN_X is watched"

sh_ input keyevent KEYCODE_BACK
sleep 1.5
# Watchlist is no longer a destination of its own (docs/design-research-2026-09-21.md section 3):
# it folded into Today's Yours block, so the way back to it is the bottom bar's Today item, not a
# tab named "Watchlist". thin-stocks is reused for its coordinates the same way the pre-rewrite
# script reused a pre-watch dump: the bar's position does not depend on the search state on screen.
tap_center thin-stocks 11 Today
sleep 1.5
dump_until today-watched '^Daily digest$' || lost "Today never drew its digest panel"
assert_lint today-watched
assert_labels today-watched
assert_geometry today-watched
screen_has today-watched '^Watched$' || fail "the 'Watched' heading" "$(screen_text today-watched | head -6 | tr '\n' ';')"
screen_has today-watched "^$THIN_X\$" || fail "a row for the ticker just watched" "$(screen_text today-watched | tr '\n' ';')"
screen_has today-watched '^Unwatch$' || fail "an Unwatch action on the row" "$(screen_text today-watched | tr '\n' ';')"
screen_has today-watched '^No digest yet\.' \
  || fail "the digest panel to say there is no digest yet" "$(screen_text today-watched | grep -i digest | tr '\n' ';')"
screen_has today-watched '^Run the check now$' \
  || fail "the debug action that fires the daily check" "$(screen_text today-watched | tail -4 | tr '\n' ';')"
_jobs_watched="$(jobs_for_app)"
[ "$_jobs_watched" -gt "$_jobs_before" ] \
  || fail "watching a ticker to schedule the daily check with the job scheduler" \
          "dumpsys jobscheduler mentions $PKG $_jobs_watched times, $_jobs_before before the watch"
pass "the daily check is scheduled"

tap_center today-watched 11 'Run the check now'
sleep 3
dump_until digest '^Checked ' || lost "the digest panel never reported a check"
assert_lint digest
assert_geometry digest
screen_has digest '[0-9]+ watched\.' \
  || fail "a digest naming how many tickers are watched" "$(screen_text digest | grep -i watched | tr '\n' ';')"
screen_has digest '^Checked [0-9]' || fail "a 'Checked ... ago.' line" "$(screen_text digest | tail -4 | tr '\n' ';')"
pass "the daily check produced a digest on the screen"

tap_center digest 11 Unwatch
sleep 1.5
dump_until unwatched '^Nothing watched yet\.' || lost "unwatching did not return the empty state"
assert_lint unwatched
assert_geometry unwatched
screen_has unwatched '^Browse analyzed stocks$' \
  || fail "the empty watchlist to offer the list" "$(screen_text unwatched | tr '\n' ';')"
_jobs_after="$(jobs_for_app)"
[ "$_jobs_after" -le "$_jobs_before" ] \
  || fail "the daily check cancelled once nothing is watched" \
          "dumpsys jobscheduler still mentions $PKG $_jobs_after times, $_jobs_before before the walk"
pass "unwatching emptied the list and cancelled the daily check"

# ---- pass 2: font scale 1.3 --------------------------------------------------------------------

step "font scale $((FONT_SCALE_PCT / 100)).$((FONT_SCALE_PCT % 100))"
sh_ settings put system font_scale "$(awk -v p="$FONT_SCALE_PCT" 'BEGIN { printf "%.2f", p / 100 }')" >/dev/null
sleep 2
sh_ am force-stop "$PKG" >/dev/null || true
# -W, not a bare start and a fixed sleep: it returns on the first frame, so what follows is never
# measuring a process that has not drawn yet.
sh_ am start -W -n "$PKG/$ACTIVITY" >/dev/null
sleep 2
wait_quiet 20
dump_until big-today '^Daily digest$' || lost "Today never came back at font scale ${FONT_SCALE_PCT}%"
assert_lint big-today
assert_labels big-today
assert_geometry big-today

tap_center big-today 11 Stocks
sleep 1
dump_until big-stocks "^($SECTOR_HEADINGS)\$" || lost "Stocks never came back at font scale ${FONT_SCALE_PCT}%"
assert_lint big-stocks
assert_labels big-stocks
assert_geometry big-stocks
assert_row_shape big-stocks
assert_numerals_scaled stocks big-stocks

open_detail big-stocks "$DEEP_X" big
assert_lint big-top
assert_labels big-top
assert_geometry big-top
# At this scale the trust grid no longer fits the first viewport, which is the font scale doing its job
# and not a defect, so the grid is read one screen further down.
scroll_down
dump_screen big-trust
assert_lint big-trust
assert_geometry big-trust
assert_trust_rows big-trust
scroll_to_bottom
dump_screen big-foot
assert_lint big-foot
assert_labels big-foot
assert_geometry big-foot
screen_has big-foot "^Swap USDC to $DEEP_X\$" \
  || fail "the Swap button label whole and on one line at font scale ${FONT_SCALE_PCT}%" \
          "$(screen_text big-foot | tail -3 | tr '\n' ';')"
pass "the Swap button survives font scale ${FONT_SCALE_PCT}%"
restore_setting system font_scale "$SAVED_FONT_SCALE"
sleep 2

# ---- pass 3: animator scale 0 ------------------------------------------------------------------

step "animator scale 0"
# rememberMotionEnabled() reads Settings.Global.ANIMATOR_DURATION_SCALE once per composition, so
# the app has to be started again for the setting to reach the live bar.
sh_ settings put global animator_duration_scale 0 >/dev/null
sh_ am force-stop "$PKG" >/dev/null || true
sh_ am start -W -n "$PKG/$ACTIVITY" >/dev/null
sleep 2
wait_quiet 20
dump_until still-today '^Daily digest$' || lost "Today never came back at animator scale 0"
tap_center still-today 11 Stocks
sleep 1
dump_until still-stocks "^($SECTOR_HEADINGS)\$" || lost "Stocks never came back at animator scale 0"
open_detail still-stocks "$DEEP_X" still
assert_lint still-top
assert_geometry still-top
screen_has still-top '^Live from the mint$'   || fail "the live bar on the screen, or 'static' means nothing"           "$(screen_text still-top | head -6 | tr '\n' ';')"
assert_motion static
restore_setting global animator_duration_scale "$SAVED_ANIMATOR"

# ---- done ---------------------------------------------------------------------------------------

printf '\ndevice-smoke: OK - %s assertions held over %s dumps in %ss on %s (%s, %sx%s)\n' \
  "$CHECKS" "$DUMPS" "$((SECONDS - START_TS))" "$SERIAL" "$PKG" "$SCREEN_W" "$SCREEN_H"
