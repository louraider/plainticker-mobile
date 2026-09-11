// Mirror of PlainTicker server lib/rpc/forwarder.ts (louraider/investor24-analyst, merged in PR #108) for auditors of the mobile app.
// The server file is the source of truth; this copy is refreshed when the server changes. Route shell: app/api/v1/rpc/route.ts there.
import { consumeRateLimitToken } from "@/lib/ratelimit";

/**
 * Bounded Solana JSON-RPC forwarder — pure logic for `POST /api/v1/rpc`.
 *
 * The mobile app ships no RPC key. It POSTs one JSON-RPC 2.0 request here and
 * the server forwards it to the provider URL held in `HELIUS_RPC_URL` (a full
 * URL that embeds the key). Everything in this module is designed so that the
 * URL/key can never reach a response body, a thrown error, or a log line:
 *
 *   - the env var is read with `process.env` at call time and never echoed;
 *   - the upstream request body is RE-SERIALIZED from validated fields, so
 *     unknown client input never reaches the provider;
 *   - every upstream failure is collapsed to a fixed `upstream_unavailable`
 *     shape — fetch's own error messages embed the URL (`Failed to parse URL
 *     from …`) and are therefore never rethrown, never logged;
 *   - only the JSON-RPC `result` or a `{ code, message }` error object is
 *     forwarded back, and the message is scrubbed for the URL/host/key.
 *
 * Bounds (see server/rpc-proxy/README.md for the public contract):
 *   - one request object per call: no batches, no arrays;
 *   - body cap 16 KiB;
 *   - method allowlist (5 read-only methods);
 *   - getMultipleAccounts capped at 20 pubkeys;
 *   - getProgramAccounts PINNED to the SKR staking program with the exact
 *     memcmp@41 + dataSlice{105,8} shape used to read a staker's balance;
 *   - per-IP token bucket 60/min;
 *   - global daily ceiling `HELIUS_DAILY_BUDGET` (default 50 000) with a
 *     greppable `RPC_BUDGET_70PCT` warning once <30% remains;
 *   - 60 s in-process cache per (method, canonical params), 500 entries,
 *     results over 256 KiB of JSON are served but not stored.
 *
 * The cache lives in the lambda's module scope: it is BEST EFFORT per instance
 * (Vercel may run many instances, each with its own map; a cold start begins
 * empty). It exists to absorb bursts from one instance's clients — the
 * ratelimit table, not this map, is the authoritative budget.
 */

// ---------------------------------------------------------------------------
// Constants (exported so the README/tests cannot drift from the code)
// ---------------------------------------------------------------------------

export const RPC_MAX_BODY_BYTES = 16 * 1024;
export const RPC_ALLOWED_METHODS = [
  "getAccountInfo",
  "getMultipleAccounts",
  "getTokenAccountsByOwner",
  "getBalance",
  "getProgramAccounts",
] as const;
export type RpcAllowedMethod = (typeof RPC_ALLOWED_METHODS)[number];

export const RPC_MAX_MULTIPLE_ACCOUNTS = 20;

/** SKR staking program — the ONLY program getProgramAccounts may target. */
export const SKR_STAKING_PROGRAM_ID =
  "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ";
/** Byte offset of the staker pubkey inside the stake account (packed struct). */
export const SKR_GPA_MEMCMP_OFFSET = 41;
/** Byte range of the staked amount (u64 LE) — the only bytes we let through. */
export const SKR_GPA_DATA_SLICE = { offset: 105, length: 8 } as const;

export const RPC_PER_IP_CAPACITY = 60;
export const RPC_PER_IP_WINDOW_MS = 60_000;
export const RPC_GLOBAL_WINDOW_MS = 86_400_000;
export const RPC_DEFAULT_DAILY_BUDGET = 50_000;
/** Warn once remaining budget drops below this fraction. */
export const RPC_BUDGET_WARN_FRACTION = 0.3;
export const RPC_BUDGET_WARN_MARKER = "RPC_BUDGET_70PCT";

export const RPC_CACHE_TTL_MS = 60_000;
export const RPC_CACHE_MAX_ENTRIES = 500;
/**
 * Largest result (JSON characters, ≈ bytes for the ASCII/base64 payloads RPC
 * returns) that is stored in the cache. Bigger results are still returned to
 * the caller; they just do not occupy the per-instance map — 500 multi-MB
 * account dumps would otherwise be a cheap way to exhaust an instance's memory.
 */
export const RPC_CACHE_MAX_ENTRY_BYTES = 256 * 1024;
export const RPC_UPSTREAM_TIMEOUT_MS = 10_000;

export const RPC_PER_IP_KEY_PREFIX = "apiv1rpc:ip:";
export const RPC_GLOBAL_KEY = "apiv1rpc:global";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type Json =
  | string
  | number
  | boolean
  | null
  | Json[]
  | { [key: string]: Json };

export interface RpcRequest {
  jsonrpc: "2.0";
  id: string | number;
  method: RpcAllowedMethod;
  params: Json[];
}

export type RpcRejectCode =
  | "bad_request"
  | "method_not_allowed"
  | "gpa_not_pinned";

export type ValidationResult =
  | { ok: true; request: RpcRequest }
  | { ok: false; error: RpcRejectCode };

/** Transport-neutral result the route turns into a Response. */
export interface RpcHttpResult {
  status: number;
  body: Json;
  headers: Record<string, string>;
}

// ---------------------------------------------------------------------------
// Env
// ---------------------------------------------------------------------------

/**
 * Daily upstream budget. `parseInt(process.env.HELIUS_DAILY_BUDGET ?? '50000')`
 * with a guard: a non-numeric or non-positive value falls back to the default
 * instead of producing a zero-capacity bucket that would 429 every request.
 */
export function readDailyBudget(): number {
  const parsed = parseInt(
    process.env.HELIUS_DAILY_BUDGET ?? String(RPC_DEFAULT_DAILY_BUDGET),
    10,
  );
  return Number.isFinite(parsed) && parsed > 0
    ? parsed
    : RPC_DEFAULT_DAILY_BUDGET;
}

/** Read the provider URL. Never returned to callers other than `fetch`. */
function readUpstreamUrl(): string | null {
  const raw = process.env.HELIUS_RPC_URL?.trim();
  if (!raw) return null;
  try {
    const u = new URL(raw);
    if (u.protocol !== "https:") return null;
    return raw;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Body reading (size cap)
// ---------------------------------------------------------------------------

export type BodyReadResult =
  | { ok: true; text: string }
  | { ok: false; reason: "too_large" | "unreadable" };

/**
 * Read a request body with a hard byte cap. Rejects early on a Content-Length
 * over the cap and, for chunked bodies, stops reading the moment the running
 * total crosses it — the full oversized body is never buffered.
 */
export async function readBodyCapped(
  req: Request,
  maxBytes: number = RPC_MAX_BODY_BYTES,
): Promise<BodyReadResult> {
  const declared = req.headers.get("content-length");
  if (declared !== null) {
    const n = Number(declared);
    if (Number.isFinite(n) && n > maxBytes) return { ok: false, reason: "too_large" };
  }
  if (!req.body) return { ok: true, text: "" };

  const reader = req.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!value) continue;
      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel().catch(() => undefined);
        return { ok: false, reason: "too_large" };
      }
      chunks.push(value);
    }
  } catch {
    return { ok: false, reason: "unreadable" };
  }
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    merged.set(c, offset);
    offset += c.byteLength;
  }
  return { ok: true, text: new TextDecoder().decode(merged) };
}

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

const BASE58_PUBKEY_RE = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;
const COMMITMENTS = new Set(["processed", "confirmed", "finalized"]);
const ENCODINGS = new Set(["base58", "base64", "base64+zstd", "jsonParsed"]);
const REQUEST_KEYS = new Set(["jsonrpc", "id", "method", "params"]);

function isPlainObject(v: unknown): v is Record<string, Json> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

function isPubkey(v: unknown): v is string {
  return typeof v === "string" && BASE58_PUBKEY_RE.test(v);
}

function isNonNegInt(v: unknown): v is number {
  return typeof v === "number" && Number.isInteger(v) && v >= 0;
}

function isValidDataSlice(v: unknown): boolean {
  if (!isPlainObject(v)) return false;
  const keys = Object.keys(v);
  return (
    keys.length === 2 &&
    isNonNegInt(v.offset) &&
    isNonNegInt(v.length)
  );
}

/**
 * Optional trailing config object shared by the account-read methods.
 * `allowed` lists the keys a method accepts; anything else is a bad request.
 */
function isValidConfig(v: unknown, allowed: ReadonlySet<string>): boolean {
  if (!isPlainObject(v)) return false;
  for (const [k, val] of Object.entries(v)) {
    if (!allowed.has(k)) return false;
    switch (k) {
      case "encoding":
        if (typeof val !== "string" || !ENCODINGS.has(val)) return false;
        break;
      case "commitment":
        if (typeof val !== "string" || !COMMITMENTS.has(val)) return false;
        break;
      case "dataSlice":
        if (!isValidDataSlice(val)) return false;
        break;
      case "minContextSlot":
        if (!isNonNegInt(val)) return false;
        break;
      default:
        return false;
    }
  }
  return true;
}

const ACCOUNT_CONFIG_KEYS = new Set([
  "encoding",
  "commitment",
  "dataSlice",
  "minContextSlot",
]);
const BALANCE_CONFIG_KEYS = new Set(["commitment", "minContextSlot"]);

function validateAccountInfoParams(p: Json[]): boolean {
  if (p.length < 1 || p.length > 2) return false;
  if (!isPubkey(p[0])) return false;
  return p.length === 1 || isValidConfig(p[1], ACCOUNT_CONFIG_KEYS);
}

function validateMultipleAccountsParams(p: Json[]): boolean {
  if (p.length < 1 || p.length > 2) return false;
  const keys = p[0];
  if (!Array.isArray(keys)) return false;
  if (keys.length < 1 || keys.length > RPC_MAX_MULTIPLE_ACCOUNTS) return false;
  if (!keys.every(isPubkey)) return false;
  return p.length === 1 || isValidConfig(p[1], ACCOUNT_CONFIG_KEYS);
}

function validateTokenAccountsByOwnerParams(p: Json[]): boolean {
  if (p.length < 2 || p.length > 3) return false;
  if (!isPubkey(p[0])) return false;
  const filter = p[1];
  if (!isPlainObject(filter)) return false;
  const fkeys = Object.keys(filter);
  if (fkeys.length !== 1) return false;
  if (fkeys[0] !== "mint" && fkeys[0] !== "programId") return false;
  if (!isPubkey(filter[fkeys[0]])) return false;
  return p.length === 2 || isValidConfig(p[2], ACCOUNT_CONFIG_KEYS);
}

function validateBalanceParams(p: Json[]): boolean {
  if (p.length < 1 || p.length > 2) return false;
  if (!isPubkey(p[0])) return false;
  return p.length === 1 || isValidConfig(p[1], BALANCE_CONFIG_KEYS);
}

const GPA_CONFIG_KEYS = new Set([
  "filters",
  "dataSlice",
  "encoding",
  "commitment",
  "minContextSlot",
]);
const GPA_MAX_FILTERS = 4;

/**
 * The pin. params[0] is the SKR staking program; params[1] carries a memcmp
 * on the staker pubkey at byte 41 and slices exactly the 8-byte amount at
 * byte 105, base64-encoded. Extra filters are limited to `dataSize` and
 * further memcmps (they only NARROW the result set); anything else fails.
 */
function isPinnedGpa(p: Json[]): boolean {
  if (p.length !== 2) return false;
  if (p[0] !== SKR_STAKING_PROGRAM_ID) return false;
  const cfg = p[1];
  if (!isPlainObject(cfg)) return false;
  for (const k of Object.keys(cfg)) if (!GPA_CONFIG_KEYS.has(k)) return false;

  if (cfg.encoding !== "base64") return false;
  const slice = cfg.dataSlice;
  if (
    !isPlainObject(slice) ||
    Object.keys(slice).length !== 2 ||
    slice.offset !== SKR_GPA_DATA_SLICE.offset ||
    slice.length !== SKR_GPA_DATA_SLICE.length
  ) {
    return false;
  }
  if ("commitment" in cfg) {
    if (typeof cfg.commitment !== "string" || !COMMITMENTS.has(cfg.commitment)) {
      return false;
    }
  }
  if ("minContextSlot" in cfg && !isNonNegInt(cfg.minContextSlot)) return false;

  const filters = cfg.filters;
  if (!Array.isArray(filters)) return false;
  if (filters.length < 1 || filters.length > GPA_MAX_FILTERS) return false;
  let stakerMemcmp = false;
  for (const f of filters) {
    if (!isPlainObject(f)) return false;
    const fk = Object.keys(f);
    if (fk.length !== 1) return false;
    if (fk[0] === "dataSize") {
      if (!isNonNegInt(f.dataSize)) return false;
      continue;
    }
    if (fk[0] !== "memcmp") return false;
    const m = f.memcmp;
    if (!isPlainObject(m)) return false;
    for (const mk of Object.keys(m)) {
      if (mk !== "offset" && mk !== "bytes" && mk !== "encoding") return false;
    }
    if (!isNonNegInt(m.offset)) return false;
    if (!isPubkey(m.bytes)) return false;
    if ("encoding" in m && m.encoding !== "base58") return false;
    if (m.offset === SKR_GPA_MEMCMP_OFFSET) stakerMemcmp = true;
  }
  return stakerMemcmp;
}

/**
 * Validate an already-parsed JSON value as exactly one allowlisted JSON-RPC
 * 2.0 request. Order of rejection: shape → method → per-method params.
 */
export function validateRpcRequest(raw: unknown): ValidationResult {
  if (!isPlainObject(raw)) return { ok: false, error: "bad_request" };
  for (const k of Object.keys(raw)) {
    if (!REQUEST_KEYS.has(k)) return { ok: false, error: "bad_request" };
  }
  if (raw.jsonrpc !== "2.0") return { ok: false, error: "bad_request" };
  const id = raw.id;
  if (typeof id !== "string" && typeof id !== "number") {
    return { ok: false, error: "bad_request" };
  }
  if (typeof id === "string" && id.length > 64) {
    return { ok: false, error: "bad_request" };
  }
  if (typeof id === "number" && !Number.isFinite(id)) {
    return { ok: false, error: "bad_request" };
  }
  const method = raw.method;
  if (typeof method !== "string") return { ok: false, error: "bad_request" };
  if (!(RPC_ALLOWED_METHODS as readonly string[]).includes(method)) {
    return { ok: false, error: "method_not_allowed" };
  }
  const params = raw.params;
  if (!Array.isArray(params)) return { ok: false, error: "bad_request" };

  const m = method as RpcAllowedMethod;
  let paramsOk: boolean;
  switch (m) {
    case "getAccountInfo":
      paramsOk = validateAccountInfoParams(params);
      break;
    case "getMultipleAccounts":
      paramsOk = validateMultipleAccountsParams(params);
      break;
    case "getTokenAccountsByOwner":
      paramsOk = validateTokenAccountsByOwnerParams(params);
      break;
    case "getBalance":
      paramsOk = validateBalanceParams(params);
      break;
    case "getProgramAccounts":
      if (!isPinnedGpa(params)) return { ok: false, error: "gpa_not_pinned" };
      paramsOk = true;
      break;
  }
  if (!paramsOk) return { ok: false, error: "bad_request" };

  return { ok: true, request: { jsonrpc: "2.0", id, method: m, params } };
}

/** Parse the raw body text and validate. Invalid JSON is a bad request. */
export function parseRpcBody(text: string): ValidationResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, error: "bad_request" };
  }
  return validateRpcRequest(parsed);
}

// ---------------------------------------------------------------------------
// Canonical params + cache
// ---------------------------------------------------------------------------

/** Deterministic JSON: object keys sorted recursively, arrays kept in order. */
export function canonicalJson(v: Json): string {
  if (Array.isArray(v)) return `[${v.map(canonicalJson).join(",")}]`;
  if (isPlainObject(v)) {
    const keys = Object.keys(v).sort();
    return `{${keys
      .map((k) => `${JSON.stringify(k)}:${canonicalJson(v[k])}`)
      .join(",")}}`;
  }
  return JSON.stringify(v);
}

export function cacheKeyFor(req: Pick<RpcRequest, "method" | "params">): string {
  return `${req.method}:${canonicalJson(req.params)}`;
}

interface CacheEntry {
  expiresAt: number;
  result: Json;
}

/**
 * In-process TTL cache with an insertion-order cap. Module-scoped on purpose:
 * one map per lambda instance, best effort, never authoritative (see header).
 */
const cache = new Map<string, CacheEntry>();

function cacheGet(key: string, now: number): Json | undefined {
  const hit = cache.get(key);
  if (!hit) return undefined;
  if (hit.expiresAt <= now) {
    cache.delete(key);
    return undefined;
  }
  return hit.result;
}

/** Store a result unless it is over the per-entry size cap. */
function cacheSet(key: string, result: Json, now: number): void {
  if (JSON.stringify(result).length > RPC_CACHE_MAX_ENTRY_BYTES) return;
  cache.delete(key);
  cache.set(key, { expiresAt: now + RPC_CACHE_TTL_MS, result });
  while (cache.size > RPC_CACHE_MAX_ENTRIES) {
    const oldest = cache.keys().next().value;
    if (oldest === undefined) break;
    cache.delete(oldest);
  }
}

/** Test-only: empty the cache. */
export function __resetRpcCacheForTests(): void {
  cache.clear();
}

/** Test-only: current entry count. */
export function __rpcCacheSizeForTests(): number {
  return cache.size;
}

// ---------------------------------------------------------------------------
// Upstream call
// ---------------------------------------------------------------------------

interface UpstreamOk {
  ok: true;
  /** Exactly one of result / error is present after normalisation. */
  result?: Json;
  error?: { code: number; message: string };
}
interface UpstreamFail {
  ok: false;
  kind: "timeout" | "network" | `status:${number}` | "bad_json" | "bad_shape";
}
type UpstreamOutcome = UpstreamOk | UpstreamFail;

/**
 * Remove anything that could identify the upstream from a provider-supplied
 * string: the full URL, its host, and every query-string value (the key).
 * Applied to JSON-RPC error messages before they are forwarded.
 */
function scrubUpstreamText(text: string, upstreamUrl: string): string {
  let out = text;
  const scrub = (needle: string, minLen: number, marker: string): void => {
    if (needle.length >= minLen && out.includes(needle)) {
      out = out.split(needle).join(marker);
    }
  };
  const decoded = (s: string): string => {
    try {
      return decodeURIComponent(s);
    } catch {
      return s;
    }
  };

  scrub(upstreamUrl, 1, "[upstream]");
  try {
    const u = new URL(upstreamUrl);
    scrub(u.host, 1, "[upstream]");
    // Userinfo (user and password before the host) — raw and percent-decoded forms.
    for (const part of [u.username, u.password]) {
      scrub(part, 4, "[redacted]");
      scrub(decoded(part), 4, "[redacted]");
    }
    // Query values: `searchParams` yields the DECODED form; also scrub the
    // raw percent-encoded form exactly as it sits in the URL string.
    for (const [, val] of u.searchParams) scrub(val, 8, "[redacted]");
    for (const pair of u.search.replace(/^\?/, "").split("&")) {
      const eq = pair.indexOf("=");
      scrub(eq === -1 ? pair : pair.slice(eq + 1), 8, "[redacted]");
    }
    for (const seg of u.pathname.split("/")) scrub(seg, 16, "[redacted]");
  } catch {
    /* URL already validated at read time; nothing more to scrub */
  }
  return out.slice(0, 512);
}

function isAbortError(controller: AbortController, e: unknown): boolean {
  return (
    controller.signal.aborted ||
    (e instanceof Error && (e.name === "AbortError" || e.name === "TimeoutError"))
  );
}

async function callUpstream(
  upstreamUrl: string,
  request: RpcRequest,
  fetchImpl: typeof fetch,
): Promise<UpstreamOutcome> {
  // ONE timer covers the whole exchange — headers AND body. Clearing it as
  // soon as headers arrive would leave `res.json()` unbounded against an
  // upstream that drips its body; the abort tears the body stream down too.
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), RPC_UPSTREAM_TIMEOUT_MS);
  try {
    let res: Response;
    try {
      res = await fetchImpl(upstreamUrl, {
        method: "POST",
        headers: { "content-type": "application/json", accept: "application/json" },
        // Re-serialised from validated fields — never the raw client text.
        body: JSON.stringify({
          jsonrpc: "2.0",
          id: request.id,
          method: request.method,
          params: request.params,
        }),
        signal: controller.signal,
      });
    } catch (e) {
      // Never rethrow, never log the message: undici embeds the URL in it.
      return { ok: false, kind: isAbortError(controller, e) ? "timeout" : "network" };
    }

    if (!res.ok) {
      // Drain without reading: the body may echo the request URL.
      await res.body?.cancel().catch(() => undefined);
      return { ok: false, kind: `status:${res.status}` };
    }

    let parsed: unknown;
    try {
      parsed = await res.json();
    } catch (e) {
      return { ok: false, kind: isAbortError(controller, e) ? "timeout" : "bad_json" };
    }
    if (!isPlainObject(parsed)) return { ok: false, kind: "bad_shape" };

    if ("error" in parsed && parsed.error !== undefined && parsed.error !== null) {
      const err = parsed.error;
      if (!isPlainObject(err) || typeof err.code !== "number") {
        return { ok: false, kind: "bad_shape" };
      }
      const message =
        typeof err.message === "string"
          ? scrubUpstreamText(err.message, upstreamUrl)
          : "upstream error";
      return { ok: true, error: { code: err.code, message } };
    }
    if (!("result" in parsed)) return { ok: false, kind: "bad_shape" };
    return { ok: true, result: parsed.result as Json };
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// Pipeline
// ---------------------------------------------------------------------------

export interface HandleRpcInput {
  /** Raw body text (already size-capped by the caller). */
  bodyText: string;
  /** Trusted client IP for the per-IP bucket. */
  ip: string;
}

export interface HandleRpcDeps {
  fetch?: typeof fetch;
  now?: () => number;
  warn?: (line: string) => void;
}

const NO_STORE = { "Cache-Control": "no-store" } as const;

function reject(status: number, error: string): RpcHttpResult {
  return { status, body: { error, code: status }, headers: { ...NO_STORE } };
}

/**
 * The full request pipeline, transport-neutral. Never throws: an unexpected
 * internal failure (e.g. the ratelimit table unreachable) returns a
 * structured 500, matching the ticker route's contract.
 *
 * Order: parse+validate (cheap, no I/O) → per-IP bucket → cache → config →
 * global budget → upstream. Cache hits never touch the global counter.
 */
export async function handleRpc(
  input: HandleRpcInput,
  deps: HandleRpcDeps = {},
): Promise<RpcHttpResult> {
  try {
    return await handleRpcInner(input, deps);
  } catch {
    return reject(500, "internal");
  }
}

async function handleRpcInner(
  input: HandleRpcInput,
  deps: HandleRpcDeps,
): Promise<RpcHttpResult> {
  const fetchImpl = deps.fetch ?? globalThis.fetch;
  const now = deps.now ?? Date.now;
  const warn = deps.warn ?? ((line: string) => console.warn(line));

  const validated = parseRpcBody(input.bodyText);
  if (!validated.ok) return reject(400, validated.error);
  const request = validated.request;

  const perIp = await consumeRateLimitToken(
    `${RPC_PER_IP_KEY_PREFIX}${input.ip}`,
    RPC_PER_IP_CAPACITY,
    RPC_PER_IP_WINDOW_MS,
  );
  if (!perIp.allowed) {
    return {
      status: 429,
      body: { error: "rate_limited", code: 429 },
      headers: {
        ...NO_STORE,
        "Retry-After": String(Math.ceil(perIp.resetMs / 1000)),
        "X-RateLimit-Remaining": "0",
      },
    };
  }
  const rlHeaders = {
    ...NO_STORE,
    "X-RateLimit-Remaining": String(perIp.remaining),
  };

  const key = cacheKeyFor(request);
  const t = now();
  const hit = cacheGet(key, t);
  if (hit !== undefined) {
    return {
      status: 200,
      body: { jsonrpc: "2.0", id: request.id, result: hit },
      headers: { ...rlHeaders, "X-Rpc-Cache": "HIT" },
    };
  }

  const upstreamUrl = readUpstreamUrl();
  if (!upstreamUrl) return reject(503, "rpc_not_configured");

  const budget = readDailyBudget();
  const global = await consumeRateLimitToken(
    RPC_GLOBAL_KEY,
    budget,
    RPC_GLOBAL_WINDOW_MS,
  );
  if (!global.allowed) return reject(429, "budget_exhausted");
  if (global.remaining < budget * RPC_BUDGET_WARN_FRACTION) {
    warn(`${RPC_BUDGET_WARN_MARKER} remaining=${global.remaining}`);
  }

  const outcome = await callUpstream(upstreamUrl, request, fetchImpl);
  if (!outcome.ok) {
    warn(`RPC_UPSTREAM_FAIL kind=${outcome.kind} method=${request.method}`);
    return reject(502, "upstream_unavailable");
  }
  if (outcome.error) {
    return {
      status: 200,
      body: { jsonrpc: "2.0", id: request.id, error: outcome.error },
      headers: { ...rlHeaders, "X-Rpc-Cache": "MISS" },
    };
  }
  const result = outcome.result ?? null;
  cacheSet(key, result, t);
  return {
    status: 200,
    body: { jsonrpc: "2.0", id: request.id, result },
    headers: { ...rlHeaders, "X-Rpc-Cache": "MISS" },
  };
}
