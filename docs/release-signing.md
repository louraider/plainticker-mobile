# Release signing

The release certificate is the app's identity. Mobile Wallet Adapter verifies the app against
`https://www.plainticker.com/.well-known/assetlinks.json`, which lists the certificate's
SHA-256. Change the certificate and every wallet stops recognising the app, and every installed
copy has to be reinstalled. There is exactly one keystore, created once, kept forever.

Rules, in order of how much they hurt when broken:

1. **The keystore never enters the repository.** `.gitignore` has `*.jks` and `*.keystore`;
   `scripts/redaction-guard.sh` fails CI if one is tracked. It also never goes into
   `gradle.properties`, `local.properties` or any other file, only into environment variables.
2. **It is backed up off this machine the same day it is created.** The dev machine has a
   failing CPU. Password-manager attachment plus one more location (encrypted USB, second
   machine), together with the password.
3. **Local release builds do not happen on this machine** (`assembleRelease` under full load is
   what freezes it). Releases come from the tag workflow.

## Status, 2026-09-13

Created. `~/keystores/plainticker-release.jks`, alias `plainticker-release`, RSA 4096, SHA256withRSA, valid until 29 January 2054. Certificate SHA-256, public by design because it is served at the URL below:

```
66:CE:92:EA:FA:2F:81:9B:9A:2F:6E:4E:ED:DC:33:AC:51:BE:46:64:4C:29:5A:02:75:02:31:B1:22:9A:49:AF
```

It is already published at `https://www.plainticker.com/.well-known/assetlinks.json` and verified through Google's Digital Asset Links API.

**The chain is proven end to end, 2026-09-13.** Tag `v0.2.0` built a signed release APK in CI from the four repository secrets, and `apksigner` reported the certificate as `66ce92eafa2f819b9a2f6e4eeddc33ac51be46644c295a02750231b1229a49af`, which is the same certificate the published file names. The keystore password was changed once between creation and the build, which did not touch the certificate, exactly as expected: the password protects the file, the certificate is the identity.

Seen on the phone: the Seed Vault Wallet's Connect sheet now names the application as `www.plainticker.com` rather than the template domain it would have shown before the rename and the file. That line is what a person reads before approving a swap.

The release APK is 2.6 MB against the debug build's 16.7 MB, and it carries no debuggable flag, which is also what makes it the only build that can submit a swap.

## 1. Create it (once)

```bash
scripts/make-keystore.sh                # -> ~/keystores/plainticker-release.jks
scripts/make-keystore.sh /d/secure      # any directory outside the repo
```

The script refuses to write inside the repo and refuses to overwrite. `keytool` prompts for
the password; pick a long random one from the password manager. The store is PKCS12, which has
a single password for store and key, so `KEYSTORE_PASSWORD == KEY_PASSWORD`. Default alias is
`plainticker-release` (`KEY_ALIAS=<other> scripts/make-keystore.sh` to change it).

## 2. Back it up (tonight)

- Attach `plainticker-release.jks` and the password to the password-manager entry
  "PlainTicker release keystore".
- Copy to a second location that is not this machine.
- Verify one backup by running the fingerprint command in step 3 against the copy.

## 3. Certificate fingerprint for `assetlinks.json`

```bash
keytool -list -v -keystore ~/keystores/plainticker-release.jks -alias plainticker-release
```

Copy the `SHA256:` line (colon-separated hex) into `sha256_cert_fingerprints` of
`/.well-known/assetlinks.json` on the PlainTicker server (task T3 owns that file). The release
workflow also prints the digest of the APK it actually signed in the job summary; that is the
value to compare against.

## 4. GitHub Actions secrets

`.github/workflows/release.yml` reads four repository secrets:

| secret              | value                                  |
|---------------------|----------------------------------------|
| `KEYSTORE_BASE64`   | the `.jks` file, base64, one line      |
| `KEYSTORE_PASSWORD` | the store password                     |
| `KEY_ALIAS`         | `plainticker-release`                  |
| `KEY_PASSWORD`      | same as `KEYSTORE_PASSWORD` (PKCS12)   |

```bash
base64 -w0 ~/keystores/plainticker-release.jks | gh secret set KEYSTORE_BASE64   # macOS: base64 -i <file>
gh secret set KEYSTORE_PASSWORD        # paste when prompted; nothing in shell history
printf %s plainticker-release | gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
```

The workflow decodes the keystore to `$RUNNER_TEMP`, exports the four variables only for the
`assembleRelease` step, deletes the file afterwards, and fails early with a clear message if
any secret is missing. GitHub masks secret values in logs; the workflow never echoes them.

## 5. Cut a release

```bash
git tag v0.1.0
git push origin v0.1.0
```

`v<major>.<minor>.<patch>` becomes `versionName`; `versionCode = major*10000 + minor*100 +
patch` (`v0.1.0` -> 100, `v1.2.3` -> 10203). A `-suffix` (`v0.2.0-rc1`) is published as a
pre-release with the same numeric code, except `-amber`, the design line every shipped build
carries, which is a normal release marked Latest. The signed `app-release.apk` is uploaded as a
workflow artifact and attached to the GitHub Release twice, as `plainticker-<versionName>.apk`
and as `plainticker.apk`, so
`https://github.com/louraider/plainticker-mobile/releases/latest/download/plainticker.apk` always
serves the latest release.

## 6. How the build reads it

`app/build.gradle.kts` reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
from the environment. Only when all four are present does the release build type get a
signing config; otherwise release stays unsigned (`app-release-unsigned.apk`) and the build
prints one `WARNING: release signing not configured ...` line when a release-ish task runs.
Debug builds are untouched by any of this.

If you ever must build a signed release by hand (not on this machine):

```bash
export KEYSTORE_PATH=~/keystores/plainticker-release.jks KEY_ALIAS=plainticker-release
read -rs KEYSTORE_PASSWORD; export KEYSTORE_PASSWORD KEY_PASSWORD="$KEYSTORE_PASSWORD"
./gradlew :app:assembleRelease -PversionCode=10316 -PversionName=1.3.16
```

## 7. If the keystore leaks

There is no rotation that keeps the identity. Create a new keystore, update
`assetlinks.json`, ship a new release; existing installs must be reinstalled. Revoke the four
GitHub secrets first so no further release can be signed with the old key.
