#!/usr/bin/env node
// Captures the bundled outage snapshot the List screen falls back to (task T8, rule 5).
//
// Run it by hand whenever the snapshot should be refreshed:
//
//     node scripts/capture-list-snapshot.mjs
//
// It writes two assets, each stamped with the day it was captured:
//
//     app/src/main/assets/snapshot/summary.json   PlainTicker /api/v1/summary, trimmed
//     app/src/main/assets/snapshot/xstocks.json   the xStocks Solana catalog, trimmed
//
// Only the fields the List draws are kept, so the assets stay a few tens of kilobytes and
// the app never ships a copy of a payload it does not render. `headline` is dropped on
// purpose: it is Ukrainian and docs/data-map.md says it is never rendered in the app.
//
// Nothing secret goes in: both routes are public and keyless, and no wallet, signature or
// key is read or written here.

import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "app", "src", "main", "assets", "snapshot");

const SUMMARY_URL = "https://www.plainticker.com/api/v1/summary";
const CATALOG_URL = "https://api.xstocks.fi/api/v2/public/assets";
// The xStocks API answers 403 to a plain script agent; the app sends the same browser identity
// (see HttpClientFactory.BROWSER_USER_AGENT).
const USER_AGENT =
  "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";

const capturedAt = new Date().toISOString().slice(0, 10);

async function getJson(url, headers = {}) {
  const response = await fetch(url, { headers });
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} from ${url}`);
  return response.json();
}

/** Two decimals at most, and no "-0". */
function round(value) {
  if (typeof value !== "number" || !Number.isFinite(value)) return null;
  return Math.round(value * 100) / 100 + 0;
}

async function captureSummary() {
  const payload = await getJson(SUMMARY_URL);
  const rows = (payload.rows ?? [])
    .filter((row) => typeof row.ticker === "string" && row.ticker.length > 0)
    .map((row) => {
      const out = { ticker: row.ticker };
      if (row.company) out.company = row.company;
      if (row.sector) out.sector = row.sector;
      const composite = round(row.composite);
      if (composite !== null) out.composite = composite;
      if (row.tone) out.tone = row.tone;
      if (row.stale === true) out.stale = true;
      if (Number.isInteger(row.age_days)) out.age_days = row.age_days;
      return out;
    });
  return { captured_at: capturedAt, source: SUMMARY_URL, generated_at: payload.generated_at ?? null, rows };
}

async function captureCatalog() {
  const assets = [];
  for (let page = 0; page < 50; page++) {
    const body = await getJson(
      `${CATALOG_URL}?network=Solana&page=${page}&pageSize=100`,
      { "User-Agent": USER_AGENT },
    );
    for (const node of body.nodes ?? []) {
      const deployment = (node.deployments ?? []).find(
        (d) => typeof d.network === "string" && d.network.toLowerCase() === "solana",
      );
      const ticker = node.underlying?.symbol || node.underlyingSymbol || (node.symbol ?? "").replace(/x$/, "");
      if (!deployment?.address || !node.symbol || !ticker) continue;
      assets.push({ symbol: node.symbol, ticker, name: node.name ?? "", mint: deployment.address });
    }
    if (body.page?.hasNextPage !== true || (body.nodes ?? []).length === 0) break;
  }
  assets.sort((a, b) => a.symbol.localeCompare(b.symbol));
  return { captured_at: capturedAt, source: `${CATALOG_URL}?network=Solana`, assets };
}

async function main() {
  const [summary, catalog] = [await captureSummary(), await captureCatalog()];
  if (summary.rows.length === 0) throw new Error("summary came back with no rows, refusing to write it");
  if (catalog.assets.length === 0) throw new Error("catalog came back with no assets, refusing to write it");

  await mkdir(OUT, { recursive: true });
  const write = async (name, value) => {
    const path = join(OUT, name);
    const text = JSON.stringify(value, null, 1) + "\n";
    await writeFile(path, text, "utf8");
    console.log(`${path}  ${(text.length / 1024).toFixed(1)} KB`);
  };
  await write("summary.json", summary);
  await write("xstocks.json", catalog);
  console.log(`captured ${summary.rows.length} analyzed rows and ${catalog.assets.length} xStocks on ${capturedAt}`);
}

main().catch((error) => {
  console.error(String(error && error.message ? error.message : error));
  process.exit(1);
});
