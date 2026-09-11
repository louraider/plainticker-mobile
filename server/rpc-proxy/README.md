# `/api/v1/rpc` — bounded Solana JSON-RPC forwarder

Contract for the PlainTicker mobile app and for anyone auditing what the
endpoint can and cannot do. Source of truth is the code; this page restates it.

| What | Where |
|---|---|
| Route (transport shell) | `app/api/v1/rpc/route.ts` |
| Rules, validation, cache, upstream call | `lib/rpc/forwarder.ts` |
| Tests | `lib/rpc/__tests__/forwarder.test.ts`, `app/api/v1/rpc/__tests__/route.test.ts` |

## Why it exists

The mobile app never ships an RPC key. It POSTs one JSON-RPC 2.0 request to
`https://www.plainticker.com/api/v1/rpc`; the server forwards it to the RPC
provider using the URL in the `HELIUS_RPC_URL` environment variable (a full
URL that embeds the key) and returns only the JSON-RPC `result` or `error`.

The endpoint is deliberately narrow: read-only methods, one request per call,
a pinned `getProgramAccounts`, small bodies, per-IP and global budgets, and a
short cache. It cannot be used as a general-purpose RPC.

## Request

```
POST /api/v1/rpc
Content-Type: application/json

{ "jsonrpc": "2.0", "id": <string | number>, "method": "<allowlisted>", "params": [ ... ] }
```

- Exactly one request object. Arrays (batches) are rejected.
- `id` must be a string (≤ 64 chars) or a finite number; it is echoed back.
- Only the keys `jsonrpc`, `id`, `method`, `params` are accepted.
- `params` must be a positional array.
- Body cap: **16 KiB** (16 384 bytes, counted as UTF-8 bytes). A larger
  `Content-Length` is rejected before the body is read; a chunked body is
  rejected the moment the running total crosses the cap.
- CORS: `Access-Control-Allow-Origin: *`, methods `POST, OPTIONS`.

## Method allowlist

| Method | Params accepted |
|---|---|
| `getAccountInfo` | `[pubkey, config?]` |
| `getMultipleAccounts` | `[[pubkey, …] (1–20), config?]` |
| `getTokenAccountsByOwner` | `[owner, { mint } \| { programId }, config?]` (exactly one filter key) |
| `getBalance` | `[pubkey, config?]` (config: `commitment`, `minContextSlot` only) |
| `getProgramAccounts` | **pinned**, see below |

`pubkey` = base58 string of 32–44 characters (`[1-9A-HJ-NP-Za-km-z]`).

`config` (where allowed) is a plain object whose keys are limited to
`encoding` (`base58` · `base64` · `base64+zstd` · `jsonParsed`),
`commitment` (`processed` · `confirmed` · `finalized`),
`dataSlice` (`{ offset, length }`, non-negative integers) and
`minContextSlot` (non-negative integer). Any other key is a `bad_request`.

Anything not in the table — `sendTransaction`, `simulateTransaction`,
`getSignaturesForAddress`, `getTransaction`, `requestAirdrop`, and so on —
returns `400 method_not_allowed`. Method names are case-sensitive.

## The `getProgramAccounts` pin

`getProgramAccounts` exists for one purpose: reading a staker's SKR balance.
The request must be exactly this shape (extra `dataSize` / `memcmp` filters
and a `commitment` / `minContextSlot` are allowed because they only narrow
the result):

```json
{
  "jsonrpc": "2.0", "id": 1, "method": "getProgramAccounts",
  "params": [
    "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ",
    {
      "encoding": "base64",
      "filters": [ { "memcmp": { "offset": 41, "bytes": "<staker pubkey, base58>" } } ],
      "dataSlice": { "offset": 105, "length": 8 }
    }
  ]
}
```

| Rule | Reason |
|---|---|
| `params[0]` must be the SKR staking program above | no other program is readable this way |
| at least one `memcmp` at **offset 41** with a base58 pubkey in `bytes` | offset of the staker pubkey in the packed stake struct (not 40) |
| `dataSlice` exactly `{ "offset": 105, "length": 8 }` | the staked amount is a little-endian `u64` at byte 105; only those 8 bytes leave the provider |
| `encoding` exactly `"base64"` | fixed so the response is a predictable 8-byte payload |
| at most 4 filters; only `memcmp` (offset/bytes/optional `encoding: "base58"`) and `dataSize` | nothing that widens the query |
| exactly two positional params, no other config keys | `withContext`, `jsonParsed`, etc. are rejected |

Any deviation returns `400 gpa_not_pinned`. The pin means the endpoint cannot
be used to dump a program's accounts, even for the SKR program.

## Limits

| Limit | Value | Key / source |
|---|---|---|
| Per client IP | 60 requests / 60 s (token bucket) | `apiv1rpc:ip:<ip>` in the shared `ratelimits` table; IP from `x-vercel-forwarded-for` → `x-real-ip` → leftmost `x-forwarded-for` (`lib/client-ip.ts`) |
| Global upstream ceiling | `HELIUS_DAILY_BUDGET` calls / 24 h (default **50 000**) | `apiv1rpc:global`; only upstream calls consume it — cache hits and rejected requests do not |
| Upstream timeout | 10 s (`AbortController`), covering headers **and** body | after which the call is a `502` — an upstream that sends headers and then drips the body is cut off the same way |

When the global bucket drops **below 30 % of the budget**, every request that
consumes a token also writes one greppable line to the server log:

```
RPC_BUDGET_70PCT remaining=<n>
```

That line is the alert; there is no other notification channel. When the
bucket is empty the endpoint returns `429 budget_exhausted` until the window
refills.

## Cache

Successful results are cached **60 s** per `(method, canonical params)` —
canonical means object keys are sorted recursively, so two clients sending the
same config in a different key order share one entry. The `id` is not part of
the key; the response carries the caller's `id`.

- A hit skips the upstream call and the global counter. The per-IP bucket is
  still consumed.
- JSON-RPC **errors are not cached** (a transient node error must not stick).
- Results larger than **256 KiB** of JSON are returned to the caller but not
  stored, so 500 requests for multi-megabyte accounts cannot fill an
  instance's memory through the cache.
- The cache is an in-process `Map` with a 500-entry cap (oldest evicted). It
  lives in the lambda instance's module scope, so it is **best effort per
  instance**: several instances each keep their own map and a cold start
  begins empty. The `ratelimits` table, not this map, is the authoritative
  budget.
- Responses carry `X-Rpc-Cache: HIT | MISS` and `Cache-Control: no-store`.

## Responses

Success (HTTP 200):

```json
{ "jsonrpc": "2.0", "id": <echoed>, "result": <provider result> }
```

Provider-side JSON-RPC error (still HTTP 200, per JSON-RPC):

```json
{ "jsonrpc": "2.0", "id": <echoed>, "error": { "code": <number>, "message": "<string>" } }
```

Only `code` and `message` are forwarded (never `data`); the message is
scrubbed of the upstream URL, its host, any userinfo (`user:password@`), every
query-string value and every long path segment — each in both its raw
percent-encoded and decoded form — before it leaves, and truncated to 512
characters.

Endpoint-side rejections are flat JSON, status in both places:

| HTTP | `error` | When |
|---|---|---|
| 400 | `bad_request` | invalid JSON, batch/array, wrong JSON-RPC shape, params that fail the per-method rules |
| 400 | `method_not_allowed` | method not in the allowlist |
| 400 | `gpa_not_pinned` | `getProgramAccounts` that deviates from the pin |
| 413 | `payload_too_large` | body over 16 KiB |
| 429 | `rate_limited` | per-IP bucket empty (`Retry-After` seconds, `X-RateLimit-Remaining: 0`) |
| 429 | `budget_exhausted` | global daily ceiling reached |
| 502 | `upstream_unavailable` | provider unreachable, timed out, non-2xx, or returned something that is not a JSON-RPC response |
| 503 | `rpc_not_configured` | `HELIUS_RPC_URL` missing or not an `https:` URL |
| 500 | `internal` | anything else (e.g. the ratelimit table unreachable); always JSON, never a framework HTML page |

Successful and rate-limited responses also carry `X-RateLimit-Remaining` for
the per-IP bucket.

Processing order: size cap → parse + validate (no I/O) → per-IP bucket → cache
→ configuration → global budget → upstream. A malformed request never touches
the database; a cache hit never touches the provider.

## What the server sends upstream

The forwarded body is **re-serialised from the validated fields**
(`jsonrpc`, `id`, `method`, `params`) — the raw client text is never
forwarded, so nothing outside the validated shape can reach the provider.
Headers sent: `content-type: application/json`, `accept: application/json`.
No client header (IP, user agent, cookies) is forwarded.

## What is logged

- **Never the key or the provider URL.** `HELIUS_RPC_URL` is read with
  `process.env` at call time, handed to `fetch`, and appears nowhere else.
  Errors thrown by `fetch` embed the URL in their message, so they are
  caught, mapped to `502` and **not** logged or rethrown.
- **Never the request or response body**, never the pubkeys inside it.
- Two single-line markers only, both greppable:
  - `RPC_BUDGET_70PCT remaining=<n>` — budget alert (see Limits).
  - `RPC_UPSTREAM_FAIL kind=<timeout|network|status:<n>|bad_json|bad_shape> method=<method>` — upstream failure class.
- No per-request row is written to `api_requests` for this route. The only
  persistent state is the two token buckets in `ratelimits` (per-IP key
  includes the client IP; the global key does not).
- Vercel's own request log (path, status, duration) applies as for any route.

## Environment

| Variable | Required | Meaning |
|---|---|---|
| `HELIUS_RPC_URL` | yes, in production | full `https:` provider URL including the key. No default. Missing → `503 rpc_not_configured`. |
| `HELIUS_DAILY_BUDGET` | no | integer, upstream calls per 24 h; default `50000`; non-numeric or ≤ 0 falls back to the default. |

## Threat notes for reviewers

- **Key extraction:** the key exists only inside the `fetch` call. Tests
  drive every failure path with a sentinel key in the URL and assert it never
  appears in a body, header or log line.
- **Abuse as a free RPC:** five read-only methods, no batches, pinned gPA,
  20-key cap on `getMultipleAccounts`, 16 KiB bodies, 60/min per IP, daily
  global ceiling. `getProgramAccounts` cannot enumerate accounts.
- **Cost amplification:** the global bucket is consumed only on an actual
  upstream call, so a burst of invalid requests or cache hits cannot drain the
  daily budget; `getProgramAccounts` is bounded to an 8-byte slice.
- **Memory exhaustion through the cache:** entries over 256 KiB of JSON are
  never stored, and the map is capped at 500 entries, so the worst case per
  instance is bounded (~128 MB) rather than 500 × the largest account on chain.
- **Cache poisoning across clients:** the key is `(method, canonical
  params)` from validated input; only successful `result` payloads are stored;
  a client cannot choose the key of another client's entry except by sending
  the same public query, which returns the same public data.
- **IP spoofing of the per-IP bucket:** uses the shared trusted-IP resolver
  (`x-vercel-forwarded-for` first) — same rule as the rest of the public API.
