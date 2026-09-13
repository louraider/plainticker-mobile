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

**State on 2026-09-13: the tree is clean and history is not.**

| run | result |
|---|---|
| working tree | OK, 241,715 distinct base58 candidates, no hit |
| `--history` | FAILED, 403,493 candidates, one hit |

The hit is the Sep 10 spike signature at `aeac9bb:docs/plan-2026-09-10.md:32`, the first plan
commit. Exactly one commit carries it, and the line has since been rewritten in the working tree.
That signature resolves on any block explorer to the founder's own wallet, which is the Seed Vault
wallet of the phone and holds the SKR stake and the Genesis token, so this is not a cosmetic leak
and step 3 applies before the flip.

**CI on `main` is red because of this, on purpose.** `.github/workflows/ci.yml` runs the
`--history` guard as its own job, and it began failing the moment the digest went into the
denylist. That is the guard doing its job: it goes green again when the rewrite lands, and
silencing it would be silencing the only thing standing between this repository and a doxxed
wallet.

Also grep for anything that is not base58 shaped and was ever secret: RPC URLs with keys, key
prefixes, `.env` names.

```bash
git log --all -p | grep -nE 'api-key=|apiKey|helius|sk_live|sk_test' | head
```

## 3. History ever contained a secret: rewrite it before the flip

**This step is live.** `git filter-repo` (not `filter-branch`), on a fresh mirror clone, then
re-push. The replacements file is built from the offending commit itself, so the signature is
never displayed, pasted or held in the shell history:

```bash
git clone --mirror git@github.com:<org>/<repo>.git repo-rewrite && cd repo-rewrite

git show aeac9bb:docs/plan-2026-09-10.md \
  | grep -oE '[1-9A-HJ-NP-Za-km-z]{80,90}' | sort -u \
  | sed 's/$/==>[redacted]/' > replacements.txt

# Confirm it caught the right thing and nothing else: one line, and its digest is the
# denylisted one. Do not proceed if this prints anything but a single matching digest.
cut -d'=' -f1 replacements.txt | while read -r s; do printf %s "$s" | sha256sum; done
# expect: 44ccc5ca952d8ba1b8ea09760dd5be61ac2cd219c40f5942ddf0812a349f55f5

git filter-repo --replace-text replacements.txt
rm -f replacements.txt
bash ../scripts/redaction-guard.sh --history     # must print OK before anything is pushed
git push --force --mirror
```

Verified 2026-09-13 that the extraction returns exactly one 87-character string and that its
digest matches the denylist entry.

**What the rewrite costs, so it is chosen rather than discovered.** `aeac9bb` is the root of every
branch and of the `v0.2.0` tag, so every commit hash in the repository changes. Commit messages,
authors, dates and order survive, which is what the judges read (plan section 0: technical depth
is scored from commits). The `v0.2.0` tag moves to a new hash; the signed APK CI already built
from it is a finished artifact and is not affected, but a later `docs/release-signing.md` run
should re-tag. Every clone and worktree on this machine must be deleted and re-cloned, or they
will fight the new history forever.

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
