# Public-flip checklist

Run top to bottom before `Settings -> Change visibility -> Public`. Every command runs from
the repo root on a clean checkout of `main`. Nothing here is optional.

**Walked end to end 2026-09-27 on `3c2fab8` (`origin/main`), every ref included**, and before
that on 2026-09-18 on `6bb2a81`. The 2026-09-27 walk is summarised in the next section; the steps
below keep the 2026-09-18 evidence and say where it has since changed. The steps that remain open
are open because they are the founder's, not because nobody looked. The flip was brought forward
from 5 October: going public also unblocks GitHub Actions billing.

## Walk of 2026-09-27

Scope: everything the flip publishes. 459 commits on 61 remote branches and 35 tags, 56 pull
requests (all closed or merged, none open), 0 issues, 1 pull request comment, 0 review comments
and 0 review bodies, 21 releases with one APK each, 314 Actions runs (41 logs read: every
successful Release run, the latest green CI runs and the failures), the repository description.

| step | result on 2026-09-27 |
|---|---|
| 1 denylist | Three digests, unchanged. The founder wallet's first four characters appear nowhere in any commit, PR, release note or log, and no full identifier matches a digest. |
| 2 guard, tree | OK, 2,509,351 distinct candidates, 114 s locally. |
| 2 guard, history | **Was failing in CI, for a reason this list had not seen.** Every History step from 2026-09-22 10:59 UTC died with `sort: write failed: /tmp/...: No space left on device` (runs `35718927166`, `35778241319`, and each one after them until billing stopped the runs), after `timeout-minutes: 20` had already landed in `1f4a533`. The old script cut every copy of every long run into windows before deduplicating; across 459 revisions that is tens of gigabytes. A local run of the old script was stopped at 1.4 GB of runs and 4 GB of sort spill and still growing. Fixed on `chore/public-ready`: runs are made unique before they are windowed, the candidate set is the same. Patched `--history`: **OK, 2,509,353 candidates, 123 s, peak temp 1.7 GB.** |
| 2 other secrets | `git log --all -p` grep: 22 lines, all the same four families as on 2026-09-18 (this list quoting itself, two Helius documentation links, `apiKey: config.apiKey` in a code sample, the fake `?api-key=SECRET`). No JSON keypair byte array, no PEM block, no seed phrase, no `Bearer` value, no `HELIUS_*=` value, no promo code other than the placeholders `PT-XXXX-XXXX-XXXX`, `PT-0000-0000-0000` and `PT-AAAA-BBBB-CCCC`. 1,166 paths ever added; none is a keystore, `.p12`, `.pem`, `.pfx`, environment file or keypair file. |
| 3 rewrite | Nothing new to rewrite for a secret. The pre-rewrite backup mirror is still on this machine; deleting it is still the founder's call. |
| 5 keystore | Both commands empty. |
| 6 `SUBMIT_SWAPS` | One `"false"` under debug, one `"true"` under release (`app/build.gradle.kts` lines 90 and 93). |
| 7 verdict verbs | Empty over Kotlin and XML. |
| 8 local files | Empty. `.gitignore` now also refuses `/secrets/`, `*keypair*.json` and every `.env*`, and `docs/dapp-store-publishing.md` keeps the publisher keypair under `~/.config/solana/`, never in the checkout. |
| 9 vendored and IDE files | **Removed from the tree** on `chore/public-ready` (`.agents/`, `.claude/`, `skills-lock.json`, `.idea/`) and ignored. Nothing in CI, scripts or Gradle read them. History keeps them; nothing in them is a secret (step 9 below). |
| 10 docs | Present. **The `server/rpc-proxy/` mirror is stale**: `#112` merged, the server's `lib/rpc/forwarder.ts` is now split with `lib/rpc/upstream.ts`, and the mirror still has the pre-split file. Refresh it after the pending `X-Rpc-Age` change lands, or the README sends an auditor to a file the server no longer has. |
| 11 CI | Last green run on `main`: `35647502431` on `ac02884`, 2026-09-21. Then the History disk failure above (Unit tests and Shell scripts green in those runs), then from 2026-09-23 no job starts at all: "recent account payments have failed or your spending limit needs to be increased". With the guard fix and billing unblocked, re-run CI on the flip-day commit before ticking this. The four release secrets are set. |
| 13 identity | 418 commits authored with the founder's work address, 40 with the GitHub `noreply` address, 1 by `bot <bot@example.com>`. The founder accepted this on 2026-09-18; it is unchanged. The address itself is no longer written out in this file. |
| 14 PRs | None open. PR bodies and titles read in full: no secret, no email, no promo code, no text addressed to an automated reviewer. PR `#54`'s title and body name real people and a real company as if they were judges; the review was simulated. Edit it before the flip. |

**One new finding, and it is the founder's decision.** The denylist keeps the founder's wallet
out of the text, but the text still leads to it. Step 1's slot and swap amounts identify the
Sep 10 spike swap on any explorer, and its signer is the founder wallet. The exact stake principal
quoted in step 1 is also written in about twenty files, source and tests included, as the
measured example; a `getProgramAccounts` dump of the SKR staking program finds the one stake
account holding exactly that principal, and its owner. Both are in history from the first plan
commit onward. Removing them from the tree alone does not help, because history stays public.
Either accept it (the wallet is only linked, not exposed as a key), or rewrite history with
`git filter-repo --replace-text` over those values the way step 3 did, which changes every hash
again, or move what matters out of that wallet.

## 1. Denylist is complete

**Done 2026-09-13.** Both placeholders are filled. Neither identifier was ever typed or stored:
the spike signature came back from `getSignaturesForAddress` on the founder wallet, matched on
slot 445,899,686 and verified against plan section 3 (one transaction, -5.00000000 USDC and
+0.01366647 TSLAx, no error); the stake account was re-read through the SKR staking program with
the memcmp at offset 41, still 31,209.870777 SKR of principal. `scripts/redaction-denylist.sha256`
holds three digests and no identifiers.

**Re-checked 2026-09-18.** Three active lines, each 64 hex characters, each labelled: founder
wallet public key, signature of the Sep 10 spike swap, founder SKR stake account. The guard reads
the file and rejects a line that is not a digest, so a pasted identifier fails the run rather than
sitting there unnoticed.

If a fourth is ever added, hash it the same way and never paste the value into a file:

```bash
printf %s '<identifier>' | sha256sum | cut -d' ' -f1
```

**What is public by design and must never be added here.** The denylist is not a list of every
on-chain identifier in the repository; it is the list of the founder's own. Three identifiers are
in the tree on purpose and are the evidence a judge can recount for themselves:

| identifier | where it lives | sha256, so a later reader can check it is absent above |
|---|---|---|
| vote collector address | plan section 6 T16, `docs/submission-answers.md`, `docs/video-script-2026-09-15.md` | `44b148f8f98412562fdedaa3dc8211268c0d3b501be23212e92c59f1be4e4021` |
| demo wallet | `README.md`, "A real swap landed on 2026-09-13" | `42b3e875194cb222f0a2da5a2701485ed1a0e25a1f7f6c4d25c6b8c7b69be282` |
| Sep 13 demo swap signature | `README.md`, `docs/data-map.md`, `docs/plan-2026-09-10.md`, `docs/submission-answers.md` | linked to solscan in the README, so it is already public |

Checked 2026-09-18: none of those three digests appears in the denylist. The demo wallet is a
different wallet from the founder's, `42b3e875...` against `2246dcff...`, which is the reason a
dedicated one was funded in week 1. Denylisting any of the three would turn the guard red against
the README that proves the app works, which is the opposite of what the guard is for.

## 2. Redaction guard passes on the tree and on history

```bash
scripts/redaction-guard.sh
scripts/redaction-guard.sh --history
```

**Both pass as of 2026-09-18, on `6bb2a81`.**

| run | result | evidence |
|---|---|---|
| working tree | OK, 2,499,305 distinct base58 candidates, no hit | run locally 2026-09-18, 1 min 28 s |
| `--history` | OK, every commit on every ref, no hit | CI run `35393228344` on `6bb2a81`, job "Redaction guard", steps "Working tree" and "History" both green, 2026-09-18 20:46 UTC |

The candidate count moved from 241,715 on 2026-09-13 to 2,499,305 today. Nothing broke: the design
branches merged since carry `design/brand/*/gallery.html`, up to 3.9 MB each of base64 image data,
and every long base64 run is cut into every 32-44 character window before hashing. It is noise the
guard is built to chew through, and it does.

**Closed 2026-09-27.** The cap has been 20 minutes since `1f4a533`, and the disk failure that
followed it is fixed on `chore/public-ready` (see the walk above). The 2026-09-18 record follows.

**The job now ran out of time, and this was the one open blocker on this list.** The "Redaction
guard" job took 4 min 34 s of its `timeout-minutes: 5` on `6bb2a81`. On the next commit, `92a8f7e`,
it ran out: run `35394936181` was killed inside the "History" step at 5 min 17 s and the log ends
`##[error]The operation was canceled` with `awk` terminated as an orphan process. Unit tests and
shell scripts passed in the same run. Nothing was found; the guard was stopped before it could
finish looking, which is the worse of the two ways for this job to be red.

It grows with the repository, so it does not recover on its own, and step 15 re-runs CI after the
flip. Two ways out, and the choice is the founder's because one of them changes what the guard
covers:

- `timeout-minutes: 20` on the `redaction-guard` job in `.github/workflows/ci.yml`. One line, keeps
  every byte in scope, and 20 leaves room as the design branches grow. This is the safe one.
- Exclude `design/brand/**/*.html` from the scan. Faster, and it narrows what the guard reads,
  which is a decision about coverage rather than a repair.

Until one of them lands, `main` is red on the guard and step 11 cannot be ticked. Two minutes.

Also grep for anything that is not base58 shaped and was ever secret. Re-run 2026-09-18 over every
commit on every ref: 12 matches, and all of them are the same four things found on 2026-09-13.
This checklist quoting its own command, two Helius documentation links, and `apiKey: config.apiKey`
in a code sample, which is a field name and not a value. No keystore, `.p12` or `.pem` was ever added in
any commit, and neither was an environment file: 983 distinct paths have been added across all
history and not one of them is credential shaped.

```bash
git log --all -p | grep -nE 'api-key=|apiKey|helius|sk_live|sk_test' | head
git log --all --diff-filter=A --name-only --pretty=format: | grep -iE '\.(jks|keystore|p12|pem)$'
git log --all --diff-filter=A --name-only --pretty=format: | grep -iE '(^|/)\.env'
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

**One loose end, deliberately, and it is now overdue.** A full mirror of the pre-rewrite history is
kept at `C:\Users\dubys\AndroidStudioProjects\myapp-backup-before-rewrite.git`. Checked 2026-09-18:
still there, 32 MB, 268 commits, and it still contains the signature. The note written on
2026-09-13 said to delete it once the rewritten repository had been used for a day and nothing was
missing. It has now been used for five days across four worktrees, a dozen merges to `main` and two
CI release builds, and nothing has been missing. Deleting it is the founder's call because it is
the only rollback. Two minutes.

## 4. Rotate nothing that was never committed

The Helius key lives only in the server's environment, the keystore only in GitHub secrets and
the off-machine backup, Jupiter is keyless. If steps 2 and 3 found nothing, there is nothing
to rotate. Do not create churn.

**Checked 2026-09-18: steps 2 and 3 found nothing, so nothing is rotated.**

## 5. No keystore anywhere

```bash
git ls-files | grep -iE '\.(jks|keystore)$'                                                  # must be empty
git log --all --diff-filter=A --name-only --pretty=format: | grep -iE '\.(jks|keystore)$'   # must be empty
```

**Both empty 2026-09-18**, and widened to `.p12`, `.pem` and `.pfx` with the same result. The guard
fails on a tracked keystore on its own, before it reads the denylist, so this is checked twice on
every push.

**The passwords are not in the repository either, checked 2026-09-18.** `docs/release-signing.md`
quotes the keystore path, the alias, the four secret *names* and the certificate SHA-256, and the
certificate is public by design: it is served at
`https://www.plainticker.com/.well-known/assetlinks.json`. No password value appears in the file,
and every step that needs one reads it from a prompt (`gh secret set` with no argument, `read -rs`)
so it never reaches a shell history either.

## 6. `SUBMIT_SWAPS` is true only in release

```bash
grep -n 'SUBMIT_SWAPS' app/build.gradle.kts
```

Expected: exactly one `"false"` under `debug {` and one `"true"` under `release {`. Nothing
else may define it. Unit tests run against debug, so they never reach `/execute`.

**Checked 2026-09-18.** `app/build.gradle.kts` lines 79 and 82, one each, plus the comment above
them. Nine other files name it; all nine read `BuildConfig.SUBMIT_SWAPS` and none defines it.

## 7. No verdict verbs in code, fixtures or comments

DESIGN.md's anti-slop rule: the three trading-verdict verbs never appear in the mobile repo.
The pattern is assembled at run time so this file does not contain them either:

```bash
git grep -nwiE "$(printf 'b%sy|s%sll|h%sld' u e o)" -- 'app/src/**/*.kt' 'app/src/**/*.xml'
```

Must be empty. **Empty 2026-09-18** over Kotlin and XML, and `CopyLintTest` fails the build on a
verdict word in `strings.xml` or any UI source, so the rule is enforced on every push rather than
only here.

The command used to read `-- app/src`, which is wider than the rule and has not been empty since
the fonts were bundled. Run against the whole of `app/src` today it returns three lines, all of
them data rather than copy, and all three expected:

| hit | why it is there |
|---|---|
| `app/src/main/assets/licenses/jetbrains_mono_ofl.txt:50` | the SIL Open Font Licence text, which ships verbatim or not at all |
| `app/src/main/assets/licenses/outfit_ofl.txt:50` | the same licence for the second font |
| `app/src/main/assets/snapshot/xstocks.json:506` | the xStocks catalog's own name for one asset, as the issuer publishes it |

Run it once without `-w` as well, to catch a verb hidden inside an identifier. Done 2026-09-18:
every hit is an ordinary English word in a comment or an identifier (`WalletSessionHolder`,
`otherAmountThreshold`, "the custodian holds", `Booking Holdings Inc.` in the bundled snapshot),
and none of them is a verdict about a stock. T10's swap sheet uses the verb "Swap".

## 8. Local and environment files are untracked

```bash
git ls-files | grep -iE '(^|/)(local\.properties|\.env[^/]*|resp\.json|keystore\.properties|signing\.properties)$'   # must be empty
```

**Empty 2026-09-18.** `local.properties` and `resp.json` exist on this machine, are ignored, and
have never been added in any commit.

**Gap closed 2026-09-18.** `.gitignore` covered `*.jks`, `*.keystore` and `local.properties`
(twice) but named no environment file at all. Nothing was ever tracked, so this cost nothing to
correct, but the redaction guard can only see what git already stored: a first `.env` would have
been caught by a person or not at all. `.env`, `.env.*`, `keystore.properties`,
`signing.properties`, `*.p12` and `*.pem` are ignored from today.

## 9. Vendored and IDE files: decide what goes public

These are tracked today and will be visible the moment the flag flips:

```bash
git ls-files -- .agents .claude skills-lock.json .idea | cut -d/ -f1-2 | sort -u
```

**Checked 2026-09-18**, and the licence half is closed:

| what | files | state |
|---|---|---|
| `.agents/skills/solana-dev`, `.claude/skills/solana-dev` | 33 and 32 | Upstream is `solana-foundation/solana-dev-skill`, **MIT**, read from the GitHub licence API. MIT permits redistribution provided the notice travels with the copy, and neither tree carried one, so the upstream `LICENSE` is now vendored into both. Keeping them is licensed as of today; `git rm --cached` on both trees is still an option and is the only choice left open here. |
| `.agents/product-marketing.md` | 1 | Written as the public half on purpose, and says so in its own second paragraph. Keep. |
| `skills-lock.json` | 1 | A source repository, a path and a hash. Nothing local in it. Keep. |
| `.idea/` | 8 | Read file by file: no absolute path, no user name, no device serial, no token. `runConfigurations.xml` carries a list of ignored IntelliJ producers and `deploymentTargetSelector.xml` a dropdown mode. The machine-specific files (`workspace.xml`, `deviceManager.xml`, `caches/`) are ignored and untracked. Keep. |

**Superseded 2026-09-27.** All four are removed from the tree on `chore/public-ready` and ignored
from then on; decided on 2026-09-27 before the flip. History keeps them. Read again for
that reason: the two skill trees are the MIT upstream plus its licence; `skills-lock.json` is a
repository, a path and a hash; the `.idea/` files carry no path, name, serial or token; and
`.agents/product-marketing.md` is the public half by its own words, though it names the private web
repository and quotes the measured spike swap (see the walk's finding). None of it needs a rewrite.
Removing `.claude/skills/solana-dev` also removes it from every local checkout on the next pull;
reinstall it per machine from `solana-foundation/solana-dev-skill` if an agent still wants it.

## 10. Docs the public expects

- `README.md` with the Security & threat model section and the integrator section (T15).
- `server/rpc-proxy/` mirrors the forwarder source (plan section 5).
- `docs/release-signing.md`, `docs/build-notes.md`, this file.

**All present 2026-09-18.** README carries "Security and threat model" and points integrators at
`server/rpc-proxy/README.md` with a mirror of the source beside it; both docs exist.

**One thing to redo, and only if a merge happens first.** `server/rpc-proxy/forwarder.ts` mirrors
the server's `lib/rpc/forwarder.ts` as merged in `louraider/investor24-analyst#108`, and its first
line says so. `#112` splits that file: the SKR staking constants and the upstream call move to a
new `lib/rpc/upstream.ts` and are re-exported from the old path. If `#112` merges before the flip,
refresh the mirror and add `upstream.ts` beside it, or the README sends an auditor to a file the
server no longer has. If `#112` does not merge, the mirror is current.

## 11. CI is green on `main`

All **three** jobs of `.github/workflows/ci.yml` green on the commit about to be exposed: unit
tests; shell scripts (parse, shellcheck, device-smoke usage, coverage-health self-test); redaction
guard including `--history`. The four release secrets are set (`gh secret list`).

**Checked 2026-09-18, and this step is open.** Run `35393228344` on `6bb2a81`: all three jobs
success. Run `35394936181` on `92a8f7e`, the next commit: unit tests and shell scripts success, the
redaction guard killed by its own five-minute cap (step 2). `main` is red on that job until the cap
is raised, so this step stays unticked. `gh secret list` returns `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`, all set 2026-09-13.

Run this again on the actual flip-day commit; a green run on an earlier commit proves the pipeline,
not the commit being exposed.

## 12. A licence for this repository

There is none, and the rules do not ask for one. `docs/hackathon-rules-2026-09-10.md` requires a
repository "accessible to the judges so they can review your code and commit history" and one
"someone else can clone and run"; the only licence the rules mention is the one a participant
grants the organizer under Terms 7.1, which is not a repository licence. Checked 2026-09-18:
`gh repo view` reports `licenseInfo: null` and no `LICENSE` file is tracked.

A public repository with no licence is all-rights-reserved, which is enough for judging and is a
defensible default for a product with a paid tier ahead of it (plan section 7). Adding one is a
founder decision rather than a checklist item, and takes a minute if the answer is yes.

## 13. Author identity in the public history

The commit history is unsquashed and stays that way: 291 commits on all refs, 176 on `main`, and
plan section 0 says technical depth is scored from them. The flip publishes every author line with
them. Read exactly what is there before it is public:

```bash
git log --all --format='%an <%ae>' | sort | uniq -c
git log --all --format='%cn <%ce>' | sort | uniq -c
```

**Run 2026-09-18:**

| role | identity | commits |
|---|---|---|
| author | `louraider <founder's work address>` | 290 |
| author | `bot <bot@example.com>` | 1 |
| committer | `louraider <founder's work address>` | 178 |
| committer | `louraider <37688796+louraider@users.noreply.github.com>` | 112 |
| committer | `bot <bot@example.com>` | 1 |

Two things for the founder to look at, both of them decisions rather than defects:

- **A real address goes public.** The founder's work address is on 290 of the 291 commits. GitHub's
  `noreply` address is on the 112 web merges, so the history already carries both. Changing it
  means another `--mirror` rewrite of every hash, which step 3 has already paid for once; doing it
  again a fortnight before the deadline costs more than the address does.
- **One commit is authored by `bot <bot@example.com>`:** `2a92e98`, "chore: initial commit",
  2026-09-10 13:00, the scaffolding commit at the root of every branch. It is a template artifact
  rather than a contributor. Same trade as above: correcting it rewrites every hash.

## 14. Open pull requests

A branch does not have to be merged to be public. Every open pull request is visible the moment the
flag flips, with its diff and its title.

**Checked 2026-09-18.** Two are open here, not one:

| pull request | branch | candidates scanned against the denylist | result |
|---|---|---|---|
| `#17` feat(next-up) | `feat/next-up` | 1 base58 candidate in the whole diff | no hit |
| `#14` The README describes the product | `docs/readme` | 782 | no hit |
| `louraider/investor24-analyst#112` | `feat/skr-vote` | 886 | no hit |

Beyond the guard, which only knows the three denylisted values, the diffs were read for what it
cannot see:

- Test fixtures carry no real wallet. `app/src/test/resources/rpc/gpa-skr-stake.json` and
  `token-accounts-by-owner.json` use synthetic pubkeys (`Stake1Account111...`,
  `TestWa11etPubkey111...`) and each one says so in a `_fixture_note` on its first line.
- `#112` adds `VOTE_COLLECTOR_PUBKEY=` to `.env.example` with an empty value and a comment saying
  the repository never carries the value. The credential-shaped strings in its tests are field
  names, mocked values, and one deliberately fake `?api-key=SECRET` inside the test that proves the
  forwarder redacts it.
- `#17` and `#112` both carry Ukrainian titles, which is the one thing in either that reads
  differently from the rest of the repository. Rename them or leave them; it is cosmetic.

`#112` is in the other repository, which stays private. Only `server/rpc-proxy/` is mirrored here
(step 10).

## 15. Flip, then verify

1. Settings -> Change visibility -> Public.
2. Re-run CI on `main`; confirm the Actions log shows no secret value (GitHub masks them, the
   workflows never echo them).
3. Tag the submission release (`docs/release-signing.md` section 5) and check that the attached
   APK's certificate digest in the job summary matches `assetlinks.json`.
