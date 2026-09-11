#!/usr/bin/env bash
# redaction-guard.sh — fail if a denylisted on-chain identifier is present in this repository.
#
# What it does
#   1. Lists tracked files (git ls-files), skipping binaries, this script and the denylist.
#   2. Extracts every base58-looking run with the length of a Solana public key (32-44 chars)
#      or of a transaction signature (86-88 chars).
#   3. Hashes each distinct token with SHA-256 and compares it with every active line of
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
# Portable to bash 3.2 (macOS): no associative arrays, no mapfile.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SELF="scripts/redaction-guard.sh"
DENYLIST="scripts/redaction-denylist.sha256"
B58='[1-9A-HJ-NP-Za-km-z]{32,88}'
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

# --- 3. collect candidates as  <location>:<token>  (location = path:line or rev:path:line) ----
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

if [ "$MODE" = "--history" ]; then
  git rev-list --all | while read -r rev; do
    git grep -I -n -o -E "$B58" "$rev" -- . ":(exclude)$SELF" ":(exclude)$DENYLIST" 2>/dev/null || true
  done > "$TMP"
else
  git ls-files -z \
    | grep -z -v -x -F -e "$SELF" -e "$DENYLIST" \
    | xargs -0 grep -I -H -n -o -s -E "$B58" > "$TMP" || true
fi

# --- 4. hash every distinct token of key/signature length and compare ------------------------
hits=0
scanned=0
while IFS= read -r tok; do
  [ -n "$tok" ] || continue
  scanned=$((scanned + 1))
  h="$(sha256 "$tok")"
  if printf '%s\n' "$DENY" | grep -qxF "$h"; then
    hits=$((hits + 1))
    fail=1
    echo "redaction-guard: FAIL - denylisted identifier (sha256 ${h:0:12}..., starts '${tok:0:4}') at:" >&2
    grep -F -- ":$tok" "$TMP" | sed "s/:$tok\$//" | sort -u | sed 's/^/    /' >&2
  fi
done < <(awk -F: '{ t=$NF; n=length(t); if ((n>=32 && n<=44) || (n>=86 && n<=88)) print t }' "$TMP" | sort -u)

if [ "$fail" -ne 0 ]; then
  echo "redaction-guard: FAILED ($hits denylisted identifier(s); $scanned distinct tokens scanned; mode: ${MODE:-working-tree})" >&2
  exit 1
fi
echo "redaction-guard: OK - $scanned distinct base58 tokens scanned, none denylisted (mode: ${MODE:-working-tree})"
