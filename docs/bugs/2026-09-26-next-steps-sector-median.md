# Bug note: "What to check next" states the sector median as the company's own figure, and gives ROIC the wrong unit

**Found:** QA pass 2026-09-26, v1.3.8, mobile. D1 (P2).
**Where:** AAPL detail, "What to check next" (server-rendered content, read-only from the mobile
app). Screenshot: `22_aapl_detail_bottom2.png`.
**Owning repo:** `C:/claudeworkfolder/FinanceAnalyst`, `origin/main` at `fb20f39`. Investigated
read-only from the mobile repo; **not edited** — Codex owns methodology and analysis there, and
that repo's own working tree is mid-edit on `codex/methodology-wip` (uncommitted `CLAUDE.md`
changes) while this note was written, so everything below was read via `git show origin/main:<path>`
rather than the checked-out tree.

## What the app shows

The read section above "What to check next" states AAPL's own figures correctly (same screen,
same payload):

> P/B of 68.1× runs +567% above the sector median of 10.2×
> ...comes in -40% below the sector median of 0.9× [Debt/EBITDA]

"What to check next," two paragraphs later, states:

> Apple's P/B of 10.2× is 6.7× above the sector median
> Apple's ROIC of 20.4× and Debt/EBITDA of 0.90× give you a concrete baseline

Both numbers in the second quote are the **sector medians** (10.2× is the P/B median already
named above as the median, not AAPL's 68.1×; 20.4 and 0.90 are presumably the ROIC and
Debt/EBITDA medians for the same reason, though only the P/B one is directly checkable against
this screen). And ROIC, a percentage, is printed with the multiplier unit ("×") that P/B and
Debt/EBITDA correctly use.

## Root cause

Two compounding gaps in `lib/prompts/next-steps.v1.ts` / `next-steps.en.v1.ts`, neither of which is
in the LLM's output — the input the LLM is given cannot support the sentence its own system prompt
asks for.

**1. The company's own metric value never reaches the next-steps prompt.**

`lib/scoring/percentile.ts:571-606`, `sectorComparatorLabel(value, median, unit, locale)`, is the
function that produces every string in `topSignals`/`bottomSignals`. Its return statement, line
605:

```ts
return `${comparator} (${formattedM})`;
```

`comparator` is a *relative* phrase ("6.7× above the median" / "20% below the median"); `formattedM`
is `formatValue(median, ...)` — **the median**, formatted with its unit. `value` (the company's own
number) is used only to compute `diff` and `comparator`'s direction/magnitude — it is never
formatted into the returned string. So a `topSignals` entry for P/B reads like `"P/B: 6.7× above the
median (10.2×)"`: the only absolute, quotable number in it is the median. This is deliberate for the
string's original purpose — its own doc comment (line 562-569) says the label is "POSITIONAL only"
for the §03 metric row UI, decided 2026-06-16 — but the same string is reused as the numeric input
for next-steps, a purpose that decision didn't anticipate.

`scoreMetric` (line 552) bakes this into the stored UA `context` at scoring time. The EN track
recomputes the same string at render time through `sectorComparatorContext` (line 628-636, calling
the same `sectorComparatorLabel` at line 635) and `lib/llm/client.ts`'s `buildEnSignals`/`toLine`
(line 433-436: `` `${METRIC_LABEL_EN[r.key]}: ${ctx}` ``), then threads it into
`NextStepsInputContext.topSignals`/`bottomSignals` (`lib/llm/client.ts:520-523`). Nothing else in
`NextStepsInputContext` (`lib/prompts/next-steps.v1.ts:156-169`, `next-steps.en.v1.ts:192-207`)
carries a raw metric value — no fundamentals block, nothing — so this comparator string is the LLM's
only numeric source for these metrics.

**2. Next-steps has no substitution safety net for the numbers it does get, unlike the narrative.**

The narrative prompt (`lib/prompts/narrative.v1.ts`) never lets the LLM type a number that reaches
the reader: it writes `{{value:metric_name}}` placeholders, and "the LLM is NOT source of numeric
truth — render layer substitutes" (file header comment, line ~20). That's why the read section above
"What to check next" — same payload, same run — states AAPL's real P/B (68.1×) and the median
(10.2×) both correctly: neither number was typed by the model.

Next-steps opted out of that mechanism on purpose: `lib/prompts/next-steps.v1.ts:12-13`, "No
placeholder syntax (steps are directive не data-display) — numeric mentions encouraged inline,
validator-lite filters forbidden phrases." The Zod validator (`stepSchema`, same file) checks word
count, sentence count, forbidden buy/sell verbs, markdown, anglicisms — nothing checks that a number
the model wrote matches anything in its input.

Put together: the system prompt tells the model to "lean on concrete numbers from the input, e.g.
'P/E of 35× against the sector median of 28×'" (`next-steps.en.v1.ts:221`, the UA twin is
`next-steps.v1.ts:183`) — an instruction that assumes the input carries the company's own figure the
way that example sentence does. The actual input line for P/B is `"P/B: 6.7× above the median
(10.2×)"` — no 68.1× anywhere in it. Asked to produce a sentence shaped like the example, the model
had exactly one absolute number available (the median) and used it as if it were the company's own,
and — for ROIC specifically, appearing beside Debt/EBITDA (a genuine "×" metric) in the same
next-step body — carried that neighboring unit onto ROIC's number too.

## Suggested fix

Give next-steps' `topSignals`/`bottomSignals` entries the company's own value the way the system
prompt's example already assumes, without touching `sectorComparatorLabel`'s existing return shape
(the §03 metric row's "positional only" design, decided 2026-06-16, may depend on the current
string; grep its other call sites before changing it in place). Two reasonable shapes, either
addressed to `lib/llm/client.ts`'s `buildContext`/`buildEnSignals` and `lib/scoring/percentile.ts`'s
UA equivalent (`computePercentile`'s `topSignals`/`bottomSignals` construction, line 239-249):

- Build a second, next-steps-specific line per metric that states both numbers with correct units,
  e.g. `` `${METRIC_LABEL_EN[key]} ${formatValue(value, unit)} vs sector median ${formatValue(median, unit)}` ``
  (reusing the already-unit-correct `formatValue`, `lib/scoring/percentile.ts:638-646`, which already
  handles ROIC as `%` and P/B/Debt-EBITDA as `x` correctly — the unit bug is a symptom of the missing
  value, not a bug in `formatValue` itself), and pass that to `NextStepsInputContext` instead of (or
  alongside) the current comparator-only string.
- Or, more robustly, adopt the narrative's own placeholder mechanism for next-steps' numeric
  mentions, so a number reaching the reader is substituted from `payload`/`percentile.metrics`
  rather than typed by the model — closing this whole class of bug rather than just this instance,
  at the cost of the flexibility the "no placeholder syntax" decision was made for
  (`next-steps.v1.ts:12-13`).

Either way, add a value present-in-input check to `stepSchema`'s `superRefine` (or a post-generation
check) so a step body citing a metric's number can be checked against `NextStepsInputContext`, the
same spirit as the existing buy/sell and anglicism gates.

## What was and wasn't done here

No code in `C:/claudeworkfolder/FinanceAnalyst` was changed — read-only investigation only, per
instruction (Codex owns methodology and analysis there). Nothing in the mobile app mislabels this
content either: the mobile client renders `stepsUk`/`stepsEn` from `ReadPayloadV1.nextSteps` as
opaque server-authored prose (`PlainTickerModels.kt`/the detail screen's own "What to check next"
section draws `title`/`body` strings verbatim), so there is no mobile-side fix to make for this
finding.
