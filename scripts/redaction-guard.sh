#!/usr/bin/env bash
# redaction-guard.sh — fail if a denylisted on-chain identifier is present in this repository.
#
# What it does
#   1. Lists tracked files (git ls-files), skipping binaries, this script and the denylist.
#   2. Extracts every maximal base58 run of 32+ characters. A run with the length of a Solana
#      public key (32-44) or of a transaction signature (86-88) is a candidate as it stands; a
#      longer run is also cut into every 32-44 (and, past 88, every 86-88) character window, so
#      an identifier glued to other base58 characters is still found.
#   3. Hashes each distinct candidate with SHA-256 and compares it with every active line of
#      scripts/redaction-denylist.sha256. Any hit -> exit 1.
#   Tokens are never printed. A hit reports file:line, the first 12 hex chars of the hash and
#   the first 4 chars of the token, so a public CI log cannot leak what the guard protects.
#
# Usage
#   scripts/redaction-guard.sh              # working tree of tracked files (what CI runs)
#   scripts/redaction-guard.sh --history    # every commit reachable from any ref (run before going public)
#
# Also fatal: a tracked *.jks / *.keystore; a denylist line that is not a hex digest
# (someone pasted the identifier instead of its hash); an empty denylist.
#
# Portable to bash 3.2 (macOS): no associative arrays, no mapfile. Hashing goes through one
# perl process (Digest::SHA is core perl and ships with git) and falls back to sha256sum/shasum
# per token when perl is missing.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SELF="scripts/redaction-guard.sh"
DENYLIST="scripts/redaction-denylist.sha256"
B58='[1-9A-HJ-NP-Za-km-z]{32,}'
MODE="${1:-}"

case "$MODE" in
  ""|--history) ;;
  *) echo "usage: $SELF [--history]" >&2; exit 2 ;;
esac

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf %s "$1" | sha256sum | cut -d' ' -f1
  else
    printf %s "$1" | shasum -a 256 | cut -d' ' -f1
  fi
}

# stdin: one token per line -> stdout: "<sha256> <token>" per line
hash_all() {
  if perl -MDigest::SHA=sha256_hex -e 1 >/dev/null 2>&1; then
    perl -MDigest::SHA=sha256_hex -ne 'chomp; next unless length; print sha256_hex($_), " ", $_, "\n"'
  else
    while IFS= read -r t; do
      [ -n "$t" ] || continue
      printf '%s %s\n' "$(sha256 "$t")" "$t"
    done
  fi
}

fail=0

# --- 1. denylist sanity ---------------------------------------------------------------------
if [ ! -f "$DENYLIST" ]; then
  echo "redaction-guard: FAIL - missing $DENYLIST" >&2
  exit 2
fi
active_lines="$(grep -vE '^[[:space:]]*(#|$)' "$DENYLIST" || true)"
if [ -z "$active_lines" ]; then
  echo "redaction-guard: FAIL - $DENYLIST has no active entries" >&2
  exit 2
fi
if printf '%s\n' "$active_lines" | grep -qvE '^[0-9a-fA-F]{64}([[:space:]].*)?$'; then
  echo "redaction-guard: FAIL - $DENYLIST has a line that is not a SHA-256 hex digest." >&2
  echo "  Only hashes belong there:  printf %s '<identifier>' | sha256sum | cut -d' ' -f1" >&2
  fail=1
fi
DENY="$(printf '%s\n' "$active_lines" | awk '{print tolower($1)}')"

# --- 2. no keystore may be tracked ----------------------------------------------------------
if git ls-files | grep -qiE '\.(jks|keystore)$'; then
  echo "redaction-guard: FAIL - a keystore is tracked:" >&2
  git ls-files | grep -iE '\.(jks|keystore)$' | sed 's/^/  /' >&2
  fail=1
fi

# --- 3. collect runs as  <location>:<run>  (location = path:line or rev:path:line) ----------
TMP="$(mktemp)"
HASHES="$(mktemp)"
trap 'rm -f "$TMP" "$HASHES"' EXIT

if [ "$MODE" = "--history" ]; then
  git rev-list --all | while read -r rev; do
    git grep -I -n -o -E "$B58" "$rev" -- . ":(exclude)$SELF" ":(exclude)$DENYLIST" 2>/dev/null || true
  done > "$TMP"
else
  git ls-files -z -- . ":(exclude)$SELF" ":(exclude)$DENYLIST" \
    | xargs -0 grep -I -H -n -o -s -E "$B58" > "$TMP" || true
fi

# --- 4. candidates: whole runs of key/signature length + every such window of longer runs ----
# Runs are made unique before they are cut into windows. In --history mode the same run sits in
# hundreds of revisions, and cutting every copy of a multi-megabyte base64 run into windows
# before deduplicating wrote tens of gigabytes of sort spill: the CI runner ran out of disk
# ("sort: write failed ... No space left on device", runs 35718927166 and 35778241319). The set
# of candidates is identical either way; only the copies are gone. LC_ALL=C makes both sorts
# byte-exact and faster.
awk -F: '{ print $NF }' "$TMP" | LC_ALL=C sort -u | awk '{
  t = $0; n = length(t)
  if ((n >= 32 && n <= 44) || (n >= 86 && n <= 88)) print t
  if (n > 44) for (L = 32; L <= 44; L++) for (i = 1; i + L - 1 <= n; i++) print substr(t, i, L)
  if (n > 88) for (L = 86; L <= 88; L++) for (i = 1; i + L - 1 <= n; i++) print substr(t, i, L)
}' | LC_ALL=C sort -u | hash_all > "$HASHES"
scanned=$(wc -l < "$HASHES" | tr -d ' ')

# --- 5. compare hashes with the denylist and report every hit -------------------------------
hits=0
while IFS=' ' read -r h tok; do
  [ -n "$tok" ] || continue
  hits=$((hits + 1))
  fail=1
  echo "redaction-guard: FAIL - denylisted identifier (sha256 ${h:0:12}..., starts '${tok:0:4}') at:" >&2
  grep -F -- "$tok" "$TMP" | awk -F: '{ NF--; print }' OFS=: | sort -u | sed 's/^/    /' >&2
done < <(awk 'NR == FNR { deny[$1] = 1; next } ($1 in deny) { print }' <(printf '%s\n' "$DENY") "$HASHES")

if [ "$fail" -ne 0 ]; then
  echo "redaction-guard: FAILED ($hits denylisted identifier(s); $scanned distinct candidates scanned; mode: ${MODE:-working-tree})" >&2
  exit 1
fi
echo "redaction-guard: OK - $scanned distinct base58 candidates scanned, none denylisted (mode: ${MODE:-working-tree})"
