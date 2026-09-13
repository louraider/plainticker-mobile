# Public-flip checklist

Run top to bottom before `Settings -> Change visibility -> Public`. Every command runs from
the repo root on a clean checkout of `main`. Nothing here is optional.

## 1. Denylist is complete

**Done 2026-09-13.** Both placeholders are filled. Neither identifier was ever typed or stored:
the spike signature came back from `getSignaturesForAddress` on the founder wallet, matched on
slot 445,899,686 and verified against plan section 3 (one transaction, -5.00000000 USDC and
+0.01366647 TSLAx, no error); the stake account was re-read through the SKR staking program with
the memcmp at offset 41, still 31,209.870777 SKR of principal. `scripts/redaction-denylist.sha256`
holds three digests and no identifiers.

If a fourth is ever added, hash it the same way and never paste the value into a file:

```bash
printf %s '<identifier>' | sha256sum | cut -d' ' -f1
```

## 2. Redaction guard passes on the tree and on history

```bash
scripts/redaction-guard.sh
scripts/redaction-guard.sh --history
```

**Both pass as of 2026-09-13, after the rewrite in step 3.**

| run | result |
|---|---|
| working tree | OK, 241,715 distinct base58 candidates, no hit |
| `--history` | OK, 2,393,234 distinct candidates across every commit on every ref, no hit |

Before the rewrite `--history` failed on one hit: the Sep 10 spike signature at
`aeac9bb:docs/plan-2026-09-10.md:32`, the first plan commit. The line had been corrected in the
working tree long before, which is exactly the trap: deleting text from a file does not delete it
from history. That signature resolves on any block explorer to the founder's own wallet, which is
the Seed Vault wallet of the phone and holds the SKR stake and the Genesis token.

Also grep for anything that is not base58 shaped and was ever secret. Run 2026-09-13 over every
commit on every ref: the only matches were this checklist quoting its own command, two Helius
documentation links, and `apiKey: config.apiKey` in a code sample, which is a field name and not a
value. No keystore, `.p12` or `.pem` was ever added in any commit.

```bash
git log --all -p | grep -nE 'api-key=|apiKey|helius|sk_live|sk_test' | head
git log --all --diff-filter=A --name-only --pretty=format: | grep -iE '\.(jks|keystore|p12|pem)$'
```

## 3. History ever contained a secret: rewrite it before the flip

**Done 2026-09-13, authorised by the founder.** `git filter-repo` 2.47 over a `--no-local --mirror`
clone, one replacement, then `git push --force --mirror`. The replacements file was built by
extracting the base58 run straight out of the offending commit, so the signature was never
displayed, pasted or left in a shell history, and its digest was checked against the denylist
before the rewrite ran.

```bash
git clone --no-local --mirror <repo> repo-rewrite && cd repo-rewrite   # --no-local or filter-repo refuses: hardlinked clone

git show aeac9bb:docs/plan-2026-09-10.md \
  | grep -oE '[1-9A-HJ-NP-Za-km-z]{80,90}' | sort -u \
  | sed 's/$/==>[redacted: signature of the Sep 10 spike swap]/' > ../replacements.txt

cut -d'=' -f1 ../replacements.txt | while read -r s; do printf %s "$s" | sha256sum; done
# must print exactly 44ccc5ca952d8ba1b8ea09760dd5be61ac2cd219c40f5942ddf0812a349f55f5 and nothing else

git filter-repo --replace-text ../replacements.txt && rm -f ../replacements.txt
git rev-list --all | while read r; do git grep -qI -F '<needle>' "$r" && echo "FOUND $r"; done   # must print nothing
git remote add origin <url> && git push --force --mirror
```

What it cost, measured rather than predicted:

| | before | after |
|---|---|---|
| commits on all refs | 268 | 268 |
| the first plan commit | `aeac9bb` | `c400b23` |
| author, date and message of that commit | louraider, Thu Sep 10 17:32:35 2026 +0300 | unchanged |
| the line itself | the signature | `[redacted: signature of the Sep 10 spike swap]` |
| `v0.2.0` | `c1b70ac` | `1190ddd` |

Nothing was lost. Every hash changed, because `aeac9bb` is the root of every branch, and commit
messages, authors, dates and order survived, which is the half the judges read (plan section 0:
technical depth is scored from commits). The signed APK CI already built from `v0.2.0` is a
finished artifact and is unaffected; a submission tag should be cut fresh from the rewritten head.

Afterwards every local ref was moved onto the rewritten history, reflogs expired and `git gc
--prune=now` run, so `aeac9bb` is no longer reachable or present locally, and the four worktrees
were removed and re-created at the same paths.

**One loose end, deliberately.** A full mirror of the pre-rewrite history is kept at
`C:\Users\dubys\AndroidStudioProjects\myapp-backup-before-rewrite.git` (23 MB, 39 refs). It is the
only rollback and it still contains the signature. Delete it once the rewritten repository has been
used for a day and nothing is missing, and never push from it.

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

Must be empty. It is empty today (checked 2026-09-11 after T5); T10's swap sheet uses the
verb "Swap". Run it again on the final commit, and once without `-w` to catch the verbs
hidden inside identifiers.

## 8. Local and environment files are untracked

```bash
git ls-files | grep -iE '(^|/)(local\.properties|\.env[^/]*|resp\.json)$'   # must be empty
```

## 9. Vendored and IDE files: decide what goes public

These are tracked today and will be visible the moment the flag flips:

```bash
git ls-files -- .agents .claude skills-lock.json .idea | cut -d/ -f1-2 | sort -u
```

`.agents/skills/` and `.claude/skills/` are third-party skill docs (check their licence
allows redistribution or drop them from the repo before the flip); `.idea/` files carry local
paths and run configurations. Keep, relicense or `git rm --cached` each one deliberately.

## 10. Docs the public expects

- `README.md` with the Security & threat model section and the integrator section (T15).
- `server/rpc-proxy/` mirrors the forwarder source (plan section 5).
- `docs/release-signing.md`, `docs/build-notes.md`, this file.

## 11. CI is green on `main`

Both jobs of `.github/workflows/ci.yml` (unit tests; redaction guard including `--history`)
green on the commit about to be exposed. The four release secrets are set (`gh secret list`).

## 12. Flip, then verify

1. Settings -> Change visibility -> Public.
2. Re-run CI on `main`; confirm the Actions log shows no secret value (GitHub masks them, the
   workflows never echo them).
3. Tag the submission release (`docs/release-signing.md` section 5) and check that the attached
   APK's certificate digest in the job summary matches `assetlinks.json`.
