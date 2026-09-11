# Public-flip checklist

Run top to bottom before `Settings -> Change visibility -> Public`. Every command runs from
the repo root on a clean checkout of `main`. Nothing here is optional.

## 1. Denylist is complete

`scripts/redaction-denylist.sha256` already has the founder wallet hashed. Fill in the two
commented placeholders (transaction signature of the Sep 10 spike swap, founder SKR stake
account) with their SHA-256 hashes, never the identifiers themselves:

```bash
printf %s '<identifier>' | sha256sum | cut -d' ' -f1
```

## 2. Redaction guard passes on the tree and on history

```bash
scripts/redaction-guard.sh
scripts/redaction-guard.sh --history
```

Both must print `redaction-guard: OK`. The second scans every commit on every ref; if it
fails, the identifier is in history and step 3 applies. Also grep for anything that is not
base58 shaped and was ever secret: RPC URLs with keys, key prefixes, `.env` names.

```bash
git log --all -p | grep -nE 'api-key=|apiKey|helius|sk_live|sk_test' | head
```

## 3. History ever contained a secret: rewrite it before the flip

`git filter-repo` (not `filter-branch`), on a fresh mirror clone, then re-push:

```bash
git clone --mirror git@github.com:<org>/<repo>.git repo-rewrite && cd repo-rewrite
printf '%s==>[redacted]\n' '<secret>' > /tmp/replacements.txt   # one secret per line; delete the file afterwards
git filter-repo --replace-text /tmp/replacements.txt
git push --force --mirror
```

The repo is still private at this point, so the cheapest way to drop the unreachable objects
GitHub keeps is to delete the GitHub repo and recreate it from the rewritten mirror (or ask
GitHub support to run gc). Every worktree and clone must be re-cloned; tags are rewritten
too. Re-run step 2 on the rewritten history. Then **rotate that secret** (Helius key: new key
in the server environment; keystore: `docs/release-signing.md` section 7).

## 4. Rotate nothing that was never committed

The Helius key lives only in the server's environment, the keystore only in GitHub secrets and
the off-machine backup, Jupiter is keyless. If steps 2 and 3 found nothing, there is nothing
to rotate. Do not create churn.

## 5. No keystore anywhere

```bash
git ls-files | grep -iE '\.(jks|keystore)$'                                                  # must be empty
git log --all --diff-filter=A --name-only --pretty=format: | grep -iE '\.(jks|keystore)$'   # must be empty
```

## 6. `SUBMIT_SWAPS` is true only in release

```bash
grep -n 'SUBMIT_SWAPS' app/build.gradle.kts
```

Expected: exactly one `"false"` under `debug {` and one `"true"` under `release {`. Nothing
else may define it. Unit tests run against debug, so they never reach `/execute`.

## 7. No verdict verbs in code, fixtures or comments

DESIGN.md's anti-slop rule: the three trading-verdict verbs never appear in the mobile repo.
The pattern is assembled at run time so this file does not contain them either:

```bash
git grep -nwiE "$(printf 'b%sy|s%sll|h%sld' u e o)" -- app/src
```

Must be empty once the spike code is deleted (T10 replaces it with the swap sheet, verb
"Swap"). Until then the only hits are in `app/src/main/java/com/myapp/MainViewModel.kt` and
`app/src/main/java/com/myapp/ui/spike/`.

## 8. Local and environment files are untracked

```bash
git ls-files | grep -iE '(^|/)(local\.properties|\.env[^/]*|resp\.json)$'   # must be empty
```

## 9. Docs the public expects

- `README.md` with the Security & threat model section and the integrator section (T15).
- `server/rpc-proxy/` mirrors the forwarder source (plan section 5).
- `docs/release-signing.md`, `docs/build-notes.md`, this file.

## 10. CI is green on `main`

Both jobs of `.github/workflows/ci.yml` (unit tests; redaction guard including `--history`)
green on the commit about to be exposed. The four release secrets are set (`gh secret list`).

## 11. Flip, then verify

1. Settings -> Change visibility -> Public.
2. Re-run CI on `main`; confirm the Actions log shows no secret value (GitHub masks them, the
   workflows never echo them).
3. Tag the submission release (`docs/release-signing.md` section 5) and check that the attached
   APK's certificate digest in the job summary matches `assetlinks.json`.
