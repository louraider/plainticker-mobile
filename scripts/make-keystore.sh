#!/usr/bin/env bash
# make-keystore.sh — create the release signing keystore OUTSIDE this repository.
#
# Usage
#   scripts/make-keystore.sh [OUT_DIR]        default OUT_DIR: $HOME/keystores
#   KEY_ALIAS=<alias> scripts/make-keystore.sh    default alias: plainticker-release
#
# keytool asks for the password interactively; nothing is passed on the command line, so
# nothing lands in shell history. The keystore type is PKCS12, which has ONE password for the
# store and the key: in CI, KEYSTORE_PASSWORD and KEY_PASSWORD are the same string.
#
# The script refuses to write inside the git repository and refuses to overwrite an existing
# keystore. Losing this file means a new app identity (new certificate fingerprint in
# /.well-known/assetlinks.json and every installed copy has to be reinstalled): back it up
# off this machine the same day. See docs/release-signing.md.
#
# Git Bash on Windows converts /c/Users/... to C:\Users\... for keytool on its own.
set -euo pipefail

OUT_DIR="${1:-$HOME/keystores}"
ALIAS="${KEY_ALIAS:-plainticker-release}"
KS_NAME="plainticker-release.jks"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool not found. Install a JDK 17+ or add \$JAVA_HOME/bin to PATH." >&2
  exit 2
fi

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
mkdir -p "$OUT_DIR"
ABS_OUT="$(cd "$OUT_DIR" && pwd -P)"
case "$ABS_OUT/" in
  "$REPO_ROOT"/*)
    echo "refusing: $ABS_OUT is inside the repository ($REPO_ROOT). Pick a directory outside it." >&2
    exit 2 ;;
esac

KS="$ABS_OUT/$KS_NAME"
if [ -e "$KS" ]; then
  echo "refusing: $KS already exists. Delete it deliberately first if you really want a new identity." >&2
  exit 2
fi

echo "Creating $KS (alias '$ALIAS', RSA 4096, valid 10000 days). keytool will ask for a password:"
keytool -genkeypair -v \
  -keystore "$KS" -storetype PKCS12 \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity 10000 \
  -dname "CN=PlainTicker Release, OU=Mobile, O=PlainTicker"

chmod 600 "$KS" 2>/dev/null || true

cat <<MSG

Done: $KS

1. Back it up OFF this machine today (password-manager attachment + a second location), together
   with the password. Never commit it (.gitignore has *.jks and *.keystore; the redaction guard
   fails the build if one is tracked).

2. Certificate SHA-256 for /.well-known/assetlinks.json (the MWA identity check):

     keytool -list -v -keystore "$KS" -alias "$ALIAS"

   Copy the 'SHA256:' line (colon-separated hex) into sha256_cert_fingerprints.

3. GitHub Actions secrets for .github/workflows/release.yml:

     base64 -w0 "$KS" | gh secret set KEYSTORE_BASE64        # macOS: base64 -i "$KS"
     gh secret set KEYSTORE_PASSWORD                         # paste when prompted
     printf %s "$ALIAS" | gh secret set KEY_ALIAS
     gh secret set KEY_PASSWORD                              # same value as KEYSTORE_PASSWORD (PKCS12)
MSG
