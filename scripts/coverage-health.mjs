#!/usr/bin/env node
// coverage-health.mjs — are the five upstreams the demo depends on answering, today?
//
// Run it on day one, on the morning the video is recorded, and on the morning of the
// submission (docs/plan-2026-09-10.md section 6, task T14). It is the guard against the
// first failure mode in section 10: a demo ticker going stale or 503 on judging day, which
// no unit test can catch because it is a fact about the world and not about the code.
//
//   node scripts/coverage-health.mjs              a table, and an exit code
//   node scripts/coverage-health.mjs --json       the same run as one JSON object
//   node scripts/coverage-health.mjs --quiet      only the failures and the verdict
//
// Exit codes, chosen so a cron or a pre-record check can branch on them:
//   0  every demo-critical check passed
//   1  something the demo depends on is broken: record nothing, fix it first
//   2  degraded but survivable: the app has a stated fallback for every failure here
//
// The one rule this script exists to assert, in the plan's own words: "stale rows show age
// rather than vanish". A ticker that answers `stale: true` and no `age_days` is a failure,
// not a warning, because the screen would then print a claim it cannot date.

import process from "node:process";

const args = new Set(process.argv.slice(2));
const AS_JSON = args.has("--json");
const QUIET = args.has("--quiet");

const PLAINTICKER = "https://www.plainticker.com/api/v1";
const RPC = "https://www.plainticker.com/api/v1/rpc";
const JUPITER = "https://api.jup.ag/price/v3";
const XSTOCKS = "https://api.xstocks.fi/api/v2/public";

// The ten of plan section 4, which are the tickers PlainTicker was extended to cover for this
// app. If one of these is missing the app has a hole where its own reason for existing is.
const PHASE_3 = ["PLTR", "COIN", "HOOD", "MSTR", "UBER", "APP", "CRWD", "MU", "ABNB", "DASH"];

// What a demo actually puts on screen. The first thirteen are the deep pools measured on
// 2026-09-13, the ones whose premium the app is willing to draw; the last three are the
// opposite and are just as load-bearing, because the liquidity floor is what the video shows
// and it needs a token with nothing behind it to show the floor working.
const DEMO_DEEP = ["NVDA", "TSLA", "AAPL", "MSTR", "HOOD", "MSFT", "COIN", "GOOGL", "MCD", "META", "AMZN", "PLTR", "KO"];
const DEMO_THIN = ["UBER", "APP", "CRWD"];
const DEMO = [...new Set([...DEMO_DEEP, ...DEMO_THIN])];

const TICKERS = [...new Set([...PHASE_3, ...DEMO])];

// PlainTicker allows 60 requests a minute per IP; Jupiter's keyless bucket is about 0.5 a
// second. Neither is worth arguing with for a script that runs three times in a month.
const PACE_MS = 350;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const UA = { "User-Agent": "plainticker-coverage-health/1.0" };

async function get(url, { headers = {}, timeoutMs = 20000 } = {}) {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), timeoutMs);
  const started = Date.now();
  try {
    const res = await fetch(url, { headers: { ...UA, ...headers }, signal: ac.signal });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* a gateway page, not JSON */ }
    return { status: res.status, ms: Date.now() - started, json, text };
  } catch (e) {
    return { status: 0, ms: Date.now() - started, json: null, text: String(e?.message ?? e) };
  } finally {
    clearTimeout(timer);
  }
}

async function post(url, body) {
  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), 20000);
  const started = Date.now();
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { ...UA, "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: ac.signal,
    });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* ditto */ }
    return { status: res.status, ms: Date.now() - started, json, text };
  } catch (e) {
    return { status: 0, ms: Date.now() - started, json: null, text: String(e?.message ?? e) };
  } finally {
    clearTimeout(timer);
  }
}

const findings = [];
/** @param {"fail"|"degraded"} level */
const note = (level, what, detail) => findings.push({ level, what, detail });

// ---- 1. /summary, which is the list ---------------------------------------------------------

async function checkSummary() {
  const r = await get(`${PLAINTICKER}/summary`);
  const rows = Array.isArray(r.json?.rows) ? r.json.rows : [];
  const out = { status: r.status, ms: r.ms, rows: rows.length, withAge: 0, tone: {} };
  for (const row of rows) {
    if (Number.isFinite(row?.age_days)) out.withAge += 1;
    out.tone[row?.tone ?? "null"] = (out.tone[row?.tone ?? "null"] ?? 0) + 1;
  }
  if (r.status !== 200) note("fail", "/summary", `HTTP ${r.status || "no answer"}: the list has no analyzed section at all`);
  else if (rows.length === 0) note("fail", "/summary", "200 with no rows: the screen would draw a token directory");
  else if (rows.length < 100) note("degraded", "/summary", `only ${rows.length} rows, expected about 179`);
  return out;
}

// ---- 2. every ticker the demo names ---------------------------------------------------------

// The fields Detail renders. A null here is a hole on screen, not a crash, so it is degraded
// unless the ticker is one the demo actually opens.
const RENDERED = ["axes", "fscore", "composite_percentile", "method"];

/**
 * The judgement, with no network in it, so it can be proved to fail as well as to pass
 * (`--self-test`). Returns the row and the findings it earned rather than recording them,
 * because a rule that can only be exercised against production is a rule nobody has checked.
 */
export function judgeTicker(ticker, status, json) {
  const j = json ?? {};
  const demo = DEMO.includes(ticker);
  const row = {
    ticker,
    status,
    stale: j.stale === true,
    age_days: Number.isFinite(j.age_days) ? j.age_days : null,
    nulls: status === 200 ? RENDERED.filter((k) => j[k] == null) : [],
    code: j.code ?? null,
    demo,
    phase3: PHASE_3.includes(ticker),
  };
  const out = [];

  if (status !== 200) {
    out.push([demo ? "fail" : "degraded", ticker, `HTTP ${status || "no answer"}${row.code ? ` (${row.code})` : ""}`]);
    return { row, findings: out };
  }
  // The plan's rule, and the reason this script exists at all.
  if (row.stale && row.age_days == null) {
    out.push(["fail", ticker, "stale: true with no age_days, so the screen would print an undated claim"]);
  }
  if (row.nulls.length) {
    out.push([demo ? "fail" : "degraded", ticker, `null where Detail draws: ${row.nulls.join(", ")}`]);
  }
  if (row.age_days != null && row.age_days > 14) {
    out.push(["degraded", ticker, `analysis is ${row.age_days} d old, the screen will say so`]);
  }
  return { row, findings: out };
}

async function checkTicker(ticker) {
  const r = await get(`${PLAINTICKER}/${ticker}`);
  const { row, findings: earned } = judgeTicker(ticker, r.status, r.json);
  row.ms = r.ms;
  for (const [level, what, detail] of earned) note(level, what, detail);
  return row;
}

// ---- self-test: the judgement, proved to fail as well as to pass -----------------------------

function selfTest() {
  const full = { axes: {}, fscore: {}, composite_percentile: 42, method: {} };
  const cases = [
    ["a healthy demo ticker earns nothing", judgeTicker("NVDA", 200, full), 0, null],
    ["a stale ticker that dates itself is fine", judgeTicker("NVDA", 200, { ...full, stale: true, age_days: 7 }), 0, null],
    ["a stale ticker with no age is a failure", judgeTicker("NVDA", 200, { ...full, stale: true }), 1, "fail"],
    ["a null Detail field on a demo ticker is a failure", judgeTicker("NVDA", 200, { ...full, fscore: null }), 1, "fail"],
    ["the same null off the demo path is degraded", judgeTicker("DASH", 200, { ...full, fscore: null }), 1, "degraded"],
    ["a 404 on a demo ticker is a failure", judgeTicker("TSLA", 404, { code: "unsupported_ticker" }), 1, "fail"],
    ["a 404 off the demo path is degraded", judgeTicker("MU", 404, { code: "not_available" }), 1, "degraded"],
    ["no answer at all is judged like a bad status", judgeTicker("TSLA", 0, null), 1, "fail"],
    ["an analysis older than a fortnight is degraded", judgeTicker("NVDA", 200, { ...full, age_days: 21 }), 1, "degraded"],
  ];
  let bad = 0;
  for (const [name, got, count, level] of cases) {
    const ok = got.findings.length === count && (level == null || got.findings[0][0] === level);
    if (!ok) { bad += 1; console.log(`  FAIL  ${name}: got ${JSON.stringify(got.findings)}`); }
    else console.log(`  ok    ${name}`);
  }
  console.log("");
  console.log(bad ? `coverage-health --self-test: ${bad} of ${cases.length} FAILED` : `coverage-health --self-test: ${cases.length} cases, all as expected`);
  return bad ? 1 : 0;
}

if (args.has("--self-test")) process.exit(selfTest());

// ---- 3. the forwarder, which is the only way the app reaches the chain -----------------------

async function checkForwarder() {
  // The forwarder allows five read-only methods and nothing else (server/rpc-proxy/forwarder.ts).
  // Liveness is a real read the app itself makes: the USDC mint, which always exists.
  const USDC = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
  const r = await post(RPC, {
    jsonrpc: "2.0", id: 1, method: "getAccountInfo",
    params: [USDC, { encoding: "jsonParsed" }],
  });
  const value = r.json?.result?.value;
  if (r.status !== 200 || value == null) {
    note("fail", "rpc forwarder", `HTTP ${r.status || "no answer"}: the trust layer cannot read a mint`);
  }

  // A method that is deliberately not on the allowlist. If this answers, the bound is gone, and
  // the bound is the whole reason the app talks to a forwarder instead of to an RPC directly.
  const denied = await post(RPC, { jsonrpc: "2.0", id: 2, method: "getSlot", params: [] });
  const refused = denied.status !== 200 || denied.json?.error != null;
  if (!refused) note("fail", "rpc forwarder", "getSlot was not refused: the method allowlist is open");

  return {
    status: r.status,
    ms: r.ms,
    read: value != null,
    owner: value?.owner ?? null,
    refusesUnlisted: refused,
  };
}

// ---- 4. Jupiter, which is every price on every screen ----------------------------------------

async function checkJupiter(mints) {
  const ids = [...mints.values()].join(",");
  const r = await get(`${JUPITER}?ids=${ids}`);
  const entries = r.json && typeof r.json === "object" ? r.json : {};
  const priced = [];
  const unpriced = [];
  let withDepth = 0;
  for (const [ticker, mint] of mints) {
    const e = entries[mint];
    if (e?.usdPrice != null) { priced.push(ticker); if (Number.isFinite(e.liquidity)) withDepth += 1; }
    else unpriced.push(ticker);
  }
  if (r.status === 429) note("degraded", "jupiter price v3", "429: the app pages and retries, but a demo will paint slowly");
  else if (r.status !== 200) note("fail", "jupiter price v3", `HTTP ${r.status || "no answer"}: no price on any screen`);
  const deepUnpriced = unpriced.filter((t) => DEMO_DEEP.includes(t));
  if (deepUnpriced.length) note("fail", "jupiter price v3", `no price for ${deepUnpriced.join(", ")}, which the demo opens`);
  return { status: r.status, ms: r.ms, asked: mints.size, priced: priced.length, withDepth, unpriced };
}

// ---- 5. the xStocks catalog, which maps a ticker to a mint -----------------------------------

async function checkXStocks(wanted) {
  // 832 assets over 9 pages, alphabetical, and the array is `nodes` rather than `assets`
  // (XStocksModels.kt). Page until every ticker the demo names is found or the pages run out,
  // because a demo ticker whose mint is on page 6 is not a ticker page 0 can vouch for.
  const byTicker = new Map();
  let pages = 0;
  let total = 0;
  let status = 0;
  let ms = 0;
  for (let page = 0; page < 12; page += 1) {
    const r = await get(`${XSTOCKS}/assets?network=Solana&page=${page}&pageSize=100`, {
      // The public API answers 403 without a browser identity. The app sends one too.
      headers: { "User-Agent": "Mozilla/5.0 (plainticker-coverage-health)" },
    });
    status = r.status;
    ms += r.ms;
    pages += 1;
    if (r.status !== 200) {
      note("degraded", "xstocks catalog", `HTTP ${r.status || "no answer"} on page ${page}: the app falls back to its bundled snapshot`);
      break;
    }
    const nodes = Array.isArray(r.json?.nodes) ? r.json.nodes : [];
    total += nodes.length;
    for (const a of nodes) {
      // Resolve exactly as XStockAsset does (XStocksModels.kt), because a script that resolves
      // differently proves nothing about the app: the ticker is `underlying.symbol` with two
      // fallbacks, and the mint is the address of the Solana deployment, never a top-level field.
      const ticker = a?.underlying?.symbol?.trim() || a?.underlyingSymbol?.trim() || String(a?.symbol ?? "").replace(/x$/, "");
      const mint = (a?.deployments ?? []).find((d) => String(d?.network ?? "").toLowerCase() === "solana")?.address;
      if (ticker && mint && !byTicker.has(ticker)) byTicker.set(ticker, mint);
    }
    if (wanted.every((t) => byTicker.has(t))) break;
    if (r.json?.page?.hasNextPage !== true) break;
    await sleep(PACE_MS);
  }
  if (status === 200 && total === 0) note("degraded", "xstocks catalog", "200 with no assets");
  return { status, ms, assets: total, pages, byTicker };
}

// ---- run -------------------------------------------------------------------------------------

const stamp = new Date().toISOString();
const summary = await checkSummary();
await sleep(PACE_MS);

const tickers = [];
for (const t of TICKERS) {
  tickers.push(await checkTicker(t));
  await sleep(PACE_MS);
}

const forwarder = await checkForwarder();
await sleep(PACE_MS);

// The catalog is paged; one page of 100 covers every ticker this script names, but confirm it
// rather than assume it, because a missing mint is a row the demo cannot open.
const xstocks = await checkXStocks(DEMO);
const demoMints = new Map();
for (const t of DEMO) {
  const mint = xstocks.byTicker.get(t);
  if (mint) demoMints.set(t, mint);
  else note(DEMO_DEEP.includes(t) ? "fail" : "degraded", "xstocks catalog", `no mint for ${t} in ${xstocks.pages} page(s)`);
}
await sleep(PACE_MS);

const jupiter = demoMints.size ? await checkJupiter(demoMints) : { status: 0, asked: 0, priced: 0, withDepth: 0, unpriced: [] };

const fails = findings.filter((f) => f.level === "fail");
const degraded = findings.filter((f) => f.level === "degraded");
const exit = fails.length ? 1 : degraded.length ? 2 : 0;

const report = {
  stamp,
  verdict: exit === 0 ? "ok" : exit === 1 ? "broken" : "degraded",
  summary,
  forwarder,
  jupiter: { ...jupiter, unpriced: jupiter.unpriced },
  xstocks: { status: xstocks.status, ms: xstocks.ms, assets: xstocks.assets },
  tickers,
  findings,
};

if (AS_JSON) {
  console.log(JSON.stringify(report, null, 1));
} else {
  if (!QUIET) {
    console.log(`coverage-health ${stamp}`);
    console.log("");
    console.log(`  /summary            ${summary.status}  ${summary.rows} rows, ${summary.withAge} carry an age  (${summary.ms} ms)`);
    console.log(`  rpc forwarder       ${forwarder.status}  mint read: ${forwarder.read}, refuses unlisted methods: ${forwarder.refusesUnlisted}  (${forwarder.ms} ms)`);
    console.log(`  xstocks catalog     ${xstocks.status}  ${xstocks.assets} assets over ${xstocks.pages} page(s)  (${xstocks.ms} ms)`);
    console.log(`  jupiter price v3    ${jupiter.status}  ${jupiter.priced} of ${jupiter.asked} priced, ${jupiter.withDepth} with a depth  (${jupiter.ms} ms)`);
    console.log("");
    console.log("  ticker  http  stale  age  nulls");
    for (const t of tickers) {
      const mark = t.status === 200 && !t.nulls.length ? " " : "!";
      console.log(
        `  ${mark} ${t.ticker.padEnd(6)} ${String(t.status).padStart(4)}  ${(t.stale ? "yes" : "no").padEnd(5)}  ${String(t.age_days ?? "-").padStart(3)}  ${t.nulls.join(",") || "-"}`,
      );
    }
    console.log("");
  }
  for (const f of fails) console.log(`FAIL      ${f.what}: ${f.detail}`);
  for (const f of degraded) console.log(`degraded  ${f.what}: ${f.detail}`);
  console.log("");
  console.log(
    exit === 0
      ? "coverage-health: OK - every upstream the demo depends on answered"
      : exit === 1
        ? `coverage-health: BROKEN - ${fails.length} demo-critical failure(s); do not record`
        : `coverage-health: DEGRADED - ${degraded.length} finding(s), each with a stated fallback on screen`,
  );
}

process.exit(exit);
