# Morfoboard Backend Translate/Fix Audit

Date: 2026-07-21
Scope: Android client contract, deployed API at `43.156.68.104:8080`, and backend source currently present in this repository.

> This report intentionally omits credential values and raw authentication tokens.

## Executive summary

The Android keyboard **does call the deployed online server** at `http://43.156.68.104:8080/api/v1/ai/process`. Network reachability is good, but the AI dependency reported by production health is disconnected and the observed translate/fix outputs are not acceptable.

Live observations:

| Probe | Result |
|---|---|
| `GET /api/v1/health` | HTTP 200 in ~62 ms |
| Health body | `status=ok`, version `0.1.0`, `ai_backend=disconnected` |
| Translate without bearer token | HTTP 401 with structured JSON |
| Fix without bearer token | HTTP 401 with structured JSON |
| Translate through deployed process route | HTTP 200, ~3.9 s model time, semantically incomplete output |
| Fix through deployed process route | HTTP 200, ~8.8 s model time, changed Indonesian into broken English |

The success-path probes exposed a **critical authentication flaw**: the backend accepts an unsigned, fabricated JWT because it only base64-decodes claims and does not verify signature, issuer, audience, or expiry. The test account used an invalid reserved domain and no real user credential.

## Client/server contract

### Android request

Source: `android/app/src/main/kotlin/com/morfoboard/app/ai/AIClient.kt`

- URL: `POST {baseUrl}/api/v1/ai/process`
- Production base URL: `http://43.156.68.104:8080`
- Content type: `application/json`
- Optional header: `Authorization: Bearer <Google ID token>`
- Request fields:
  - `action`: `translate` or `fix_text`
  - `text`
  - `source_language`: default `auto`
  - `target_language`: default `en`
  - `tone`: default `natural`
- Read timeout: 35 seconds

### Expected success response

```json
{
  "success": true,
  "original": "...",
  "result": "...",
  "action": "translate",
  "model_used": "...",
  "processing_time_ms": 1234
}
```

### Expected error response

```json
{
  "success": false,
  "error": "error_code",
  "message": "human-readable message"
}
```

The deployed route and Android models currently agree on this JSON shape.

## Live response quality

### Translate

Request intent:

- Action: translate
- Source: auto
- Target: English
- Tone: natural
- Input: Indonesian greeting plus a question asking how the recipient is

Observed result: only a short equivalent of “Morning.” The second clause/question was dropped.

**Impact:** meaning is lost even though the API returns `success=true`.

Expected behavior:

- Preserve every semantic clause.
- Preserve punctuation and question intent.
- Return only the translated text.
- Never summarize unless explicitly requested.

### Fix text

Request intent:

- Action: fix text
- Input: Indonesian sentence containing informal spelling and tense/flow issues

Observed result: broken English, despite `fix_text` being expected to preserve the source language.

**Impact:** the action does not satisfy its core contract and may destructively replace user text with a different language.

Expected behavior:

- Detect and preserve source language.
- Correct `tau`/`tahu`, casing, punctuation, grammar, and flow without translating.
- Preserve meaning and tone.
- Return the original unchanged if no correction is needed.

## Prioritized findings for the backend agent

### P0 — Authentication is not verified

Source: `backend/internal/auth/verifier.go`

`Verifier.Verify` only splits the JWT and decodes its payload. It does not verify:

- cryptographic signature;
- Google issuer;
- OAuth client audience;
- expiration/not-before time;
- subject/email validity.

A fabricated unsigned JWT reached the AI endpoint successfully.

**Required fix:** use Google token verification/JWKS with strict issuer, audience (`GOOGLE_CLIENT_ID`), expiry, and signature validation. Reject empty subject/email. Add tests for unsigned, wrong audience, expired, malformed, and valid tokens.

### P0 — Production AI dependency reports disconnected

Source: deployed `GET /api/v1/health` and `backend/internal/health/handler.go`.

Health returns top-level `status: ok` while `ai_backend: disconnected`.

Potential causes:

1. The configured upstream URL is unavailable from the server.
2. `/v1/models` requires authorization, but health sends no API key.
3. The health probe path does not match the upstream.
4. Production configuration differs from the repository.

Note that `NineRouterClient.IsAvailable()` does send authorization, while the health handler does not. These probes can therefore disagree.

**Required fix:** create one shared upstream health method using the same URL/auth rules as inference. Return degraded/unhealthy status when AI is unavailable, or clearly distinguish liveness from readiness (`/health/live` and `/health/ready`).

### P0 — Translate/fix quality violates product behavior

Sources:

- `backend/internal/proxy/prompts.go`
- `backend/internal/proxy/client.go`
- deployed outputs

Current prompts are only one short sentence each. They do not strongly enforce full semantic preservation, source-language preservation for correction, punctuation, formatting, URLs, names, numbers, emoji, or no-explanation behavior.

The observed model (`mimo-v2.5-free`) returned incomplete translation and broken cross-language correction.

**Required fix:** strengthen prompts, use a model that reliably follows multilingual editing instructions, and add deterministic quality fixtures before rollout.

Suggested translation constraints:

1. Translate every clause; never omit, summarize, answer, or explain.
2. Preserve names, mentions, URLs, email addresses, numbers, line breaks, and emoji.
3. Preserve punctuation intent.
4. Apply requested tone without changing meaning.
5. Detect source when `auto`.
6. Return only final translated text.

Suggested fix-text constraints:

1. Preserve detected source language; never translate.
2. Correct spelling, grammar, capitalization, punctuation, and awkward flow.
3. Preserve meaning, tone, names, mentions, URLs, numbers, line breaks, and emoji.
4. Do not invent details.
5. Return unchanged text when already correct.
6. Return only final corrected text.

### P1 — Extra upstream availability call adds latency and failure surface

Source: `backend/internal/proxy/handler.go` and `backend/internal/proxy/client.go`.

Every process request first calls `GET /v1/models` through `IsAvailable()`, then calls chat completions. This doubles network work, can consume most of the same 30-second timeout, and can reject healthy inference when only the models endpoint is unavailable.

**Required fix:** remove the per-request preflight. Call inference directly. Use a short cached readiness probe/circuit breaker outside the request hot path.

### P1 — Server timeout budget conflicts

Sources:

- Go HTTP server `WriteTimeout`: 35 seconds
- Upstream HTTP client timeout: 30 seconds
- Android read timeout: 35 seconds
- Plus an upstream availability request before inference

This leaves little/no budget for auth, decoding, preflight, queueing, JSON work, and network variance.

**Required fix:** define one end-to-end SLO and derive nested deadlines. Example: client 45 s, server request context 40 s, inference 35 s, health preflight removed. Prefer lower p95 model latency.

### P1 — Validation is incomplete

Source: `backend/internal/proxy/handler.go`

Missing strict validation for:

- supported target/source languages;
- supported tones;
- whitespace-only input;
- unknown JSON fields/trailing JSON;
- UTF-8 validity;
- size measured as bytes versus characters.

**Required fix:** trim only for validation (do not mutate user formatting), enforce allowlists, reject unknown fields, enforce one JSON object, and define limits consistently in Unicode code points or bytes.

### P1 — Plain HTTP exposes private text and tokens

Sources:

- Android production URL uses `http://`.
- Network security config explicitly permits cleartext to the production IP.

**Impact:** typed content and bearer tokens can be intercepted or modified on hostile networks.

**Required fix:** serve the API via HTTPS with a hostname and valid certificate, migrate Android to `https://`, then remove the production cleartext exception.

### P1 — Sensitive text is logged by Android

Sources:

- `AIClient.kt` logs the first portion of user text.
- `MorfoboardIME.kt` logs portions of translate/fix input.

This is client-side rather than BE, but it affects the online flow.

**Required fix:** remove content logging from release builds. Log only action, request ID, duration, status, and safe size metadata.

### P2 — Error mapping loses backend detail

Source: `AIClient.kt`

The Android client does not distinguish 400, 403, or 429 and discards structured backend `error/message` for most statuses.

**Backend recommendation:** keep stable machine-readable error codes and `Retry-After` for rate limits. Coordinate with FE to map them.

### P2 — No rate limiting or request correlation

The public AI endpoint should have per-user/IP rate limits, request IDs, and metrics for p50/p95/p99 latency, upstream errors, output emptiness, and action/model quality.

### P2 — Weak response validation

Source: `backend/internal/proxy/client.go`

The upstream parser accepts the first SSE chunk if streaming is forced; this can return partial content. It also does not reject empty results.

**Required fix:** disable streaming reliably or aggregate all SSE deltas until `[DONE]`; reject empty/partial results; cap upstream response size; preserve request context.

## Suggested backend acceptance tests

### Translation fixtures

- Indonesian → English, multi-clause sentence and question.
- English → Indonesian, formal and casual tones.
- Indonesian ↔ Javanese and Spanish as required by the PRD.
- Mixed Indonesian-English text.
- Names, `@mentions`, URL, email, currency, dates, numbers.
- Multiple paragraphs, bullet points, emoji, smart punctuation.
- Already-target-language input.
- Empty, whitespace-only, over-limit, unsupported target language/tone.

Assertions:

- No clause loss.
- No answers/explanations.
- No invented facts.
- Protected entities unchanged.
- Output language and requested tone are correct.

### Fix-text fixtures

- Indonesian informal spelling (`tau` → `tahu`) without translation.
- Indonesian formal/business message.
- English grammar correction without Indonesian translation.
- Mixed-language text where code-switching is intentional.
- Perfect text returned unchanged.
- URLs, mentions, numbers, formatting, line breaks, and emoji preserved.

### Auth/security fixtures

- Valid Google token accepted.
- Unsigned token rejected.
- Invalid signature rejected.
- Wrong audience/issuer rejected.
- Expired/not-yet-valid token rejected.
- Empty email/subject rejected.
- Blacklisted valid user rejected.
- Rate limit returns 429 and `Retry-After`.

## Recommended response contract improvements

Keep backward compatibility while adding:

```json
{
  "success": true,
  "request_id": "...",
  "original": "...",
  "result": "...",
  "action": "fix_text",
  "detected_language": "id",
  "target_language": null,
  "model_used": "...",
  "processing_time_ms": 1234
}
```

For errors:

```json
{
  "success": false,
  "request_id": "...",
  "error": "ai_unavailable",
  "message": "AI service is temporarily unavailable",
  "retryable": true
}
```

## Definition of done for BE repair

- Cryptographically verified Google ID tokens.
- Production readiness reports AI connected.
- No per-request `/v1/models` preflight.
- HTTPS only.
- Translate fixtures preserve all meaning and entities.
- Fix-text fixtures never change source language.
- Invalid input, rate limit, timeout, and upstream failures return stable structured errors.
- p95 latency measured and inside agreed SLO.
- `go test ./...`, `go vet ./...`, and `govulncheck ./...` pass.
