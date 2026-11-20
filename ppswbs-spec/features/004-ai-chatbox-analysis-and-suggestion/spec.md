# Feature Specification: AI Chatbox Analysis and Suggestion

**Date created**: 2026-10-21
**Date updated**: 2026-11-16
**Author**: Ngô Tiến Đạt
**Last updated by**: Phan Sơn

**Feature**: `004-ai-chatbox-analysis-and-suggestion`
**Status**: Independent frontend behavior synchronized (updated 2026-11-16 for Section 3.1 Unauthenticated Access & Session Guard at /design-requests/new); backend/API integration gates remain open
**Source alignment**: Feature-004 design, current React/Vite slice, Report 1 FE-04/FE-05 and Report 3

## 1. Scope and independent boundary

Feature-004 owns:

1. Basic AI Support for approved public information.
2. Independent Design Request creation with optional description and/or reference image.
3. AI image analysis and safe result display.
4. Staff/Manager human review integration when the backend returns a review state.
5. Manager-only advisory price suggestion for new workshop packages or retail products.

The Design Request is independent from Workshop Booking. It MUST NOT require or
mutate package, location, session, booking, payment, deposit, ticket, capacity,
settlement or custom-manufacturing state. An opaque catalogue reference may be sent
only when the backend contract explicitly permits it.

## 2. Actors and permissions

### Route masking synchronization (2026-11-20)

`/design-review-requests*` and `/price-proposals/:proposalId/ai-suggestions` are internal reviewer routes with browser allowlist `STAFF` and `MANAGER`; a signed-in `MEMBER`, `ADMIN`, or `CARRIER` is replaced to `/404` before page render/request. This does not convert the documented `AI_ACTOR_NOT_ALLOWED` API `403` for Basic AI Support into `404`: that endpoint's typed actor policy remains a server-side contract. Public/Member design-request routes retain their current feature rules.

| Actor | Allowed capability |
| --- | --- |
| Guest | Basic AI Support in public scope only (Tối đa 3 prompt/ngày lịch Việt Nam `Asia/Ho_Chi_Minh`, reset lúc 00:00; quản lý qua `X-Guest-AI-Session`). Không được sử dụng dữ liệu Member hoặc operational data. Cannot create or upload a Design Request. |
| Member | Basic AI Support in public scope (Tối đa 10 prompt/ngày lịch Việt Nam `Asia/Ho_Chi_Minh`, reset lúc 00:00; gắn với active Firebase Member account); create and view own Independent Design Requests. |
| Staff | Review requests only within server-authorized scope. Không thuộc nhóm sử dụng Basic AI Support (trả `403 AI_ACTOR_NOT_ALLOWED`). |
| Manager | Review requests requiring Manager authority; request and decide advisory catalogue price suggestions. Không thuộc nhóm sử dụng Basic AI Support (trả `403 AI_ACTOR_NOT_ALLOWED`). |
| Admin | Technical configuration and approved technical/audit projection only; no business review or price decision. Không thuộc nhóm sử dụng Basic AI Support (trả `403 AI_ACTOR_NOT_ALLOWED`). |
| AI provider | Returns validated safe output only; cannot mutate Mland state. |
| Cloud storage | Temporary image storage/deletion only. |

The frontend route guard is a navigation aid. Firebase identity, Mland role/status,
ownership, reviewer authority and resource scope are enforced by the backend.
Client-provided role, owner, reviewer, price, status or decision is untrusted.

### 2.1 Demo AI Chat Quota and Session Rules
1. **Quota Boundary**:
   - Guest: 3 prompts per calendar day (`Asia/Ho_Chi_Minh`), reset at 00:00.
   - Active Member: 10 prompts per calendar day (`Asia/Ho_Chi_Minh`), reset at 00:00.
   - Staff/Manager/Admin: Gated with HTTP 403 `AI_ACTOR_NOT_ALLOWED`.
2. **Session and Accounting Lifecycle**:
   - Guest requests use an opaque session token sent via `X-Guest-AI-Session`. If absent, backend generates a new 24h TTL session and returns it in `X-Guest-AI-Session` header.
   - If `X-Guest-AI-Session` is expired or invalid, backend returns HTTP 401 `AI_GUEST_SESSION_INVALID`.
   - Quota usage is consumed atomically by backend authority upon passing initial length/non-empty validation. The quota write commits independently before downstream screening/provider work, so prompt injection, forbidden context, unsafe output and provider errors consume quota once validated and cannot roll it back.
   - Quota metadata is returned in response headers (`X-AI-Quota-Limit`, `X-AI-Quota-Remaining`, `X-AI-Quota-Reset-At`) and response body (`quota` object).
   - When quota is exhausted, backend returns HTTP 429 `AI_DAILY_QUOTA_EXCEEDED` with `Retry-After: <seconds-to-midnight>` and quota metadata.

## 3. Independent Design Request behavior

The Member opens `/design-requests/new` and may submit:

- description only;
- reference image only; or
- description and reference image together.

Description is limited to 4,000 characters. An image requires purpose-specific
consent before storage or provider processing. The image is validated for supported
MIME/signature, size, dimensions, malware policy and the Member image rate limit.
The request contains no booking fields.

When an image is supplied, the Member must submit the current consent version and
purpose. When no image is supplied, image consent fields are omitted and no image
storage/provider image call occurs.

### 3.1 Unauthenticated access and session guard
When an unauthenticated guest or user with expired session accesses `/design-requests/new`:
1. The route MUST NOT render a blank/empty container or hang silently.
2. It MUST render an inline empty-state notice (`<RequireSession requiredText="...">`) stating: "Vui lòng đăng nhập để tạo yêu cầu thiết kế tùy chỉnh".
3. It MUST provide an accessible "Đăng nhập" action button (`<SignInLink>`) directly routing to `/login`.

### 3.2 Removal of catalogue reference field
To streamline customer workflow, the input field for catalogue reference (`catalogueReference`) has been removed from the Custom Design screen (`/design-requests/new`). Independent custom designs focus purely on customer descriptions and reference imagery without requiring pre-association with catalogue products.

### 3.3 Pre-submission image validation and error clarity
Before dispatching a design request:
1. The client performs strict pre-submission image validation:
   - File format check: allowed MIME types `image/jpeg`, `image/png`, `image/webp`. Rejects invalid formats with explicit notice.
   - Non-empty check: rejects 0-byte or corrupted image files.
   - Size limit: enforces maximum 10 MB limit with human-readable file size reporting.
   - Resolution check: validates that dimensions do not exceed 4096×4096px.
2. Clear error messaging: All client validation and server error codes (e.g. rate limit `429 IMAGE_RATE_LIMITED`, consent requirement, descriptive limits) are translated into explicit Vietnamese messages detailing the specific issue and recovery steps.

### 3.4 Submission feedback
Upon successful submission, the system returns and displays the confirmation notice:
> *"Yêu cầu đã được gửi thành công, đang đợi phản hồi của nhân viên"*
The customer is transitioned to the Design Request detail view (`/design-requests/:designRequestId`) with the active success banner.

### 3.5 Guest and Member request tracking
1. **Member Tracking**: Authenticated Members can view the full history and real-time status of their custom design requests via `/my/designs?tab=custom`, displaying request references, dates, AI analysis status, and staff review decisions (`NEED_REVIEW`, `ACCEPTED`, `REJECTED`, `CLARIFICATION_REQUIRED`).
2. **Guest Lookup & Review Visibility**: Guests possessing their opaque request reference or direct link can view their design request details and staff review feedback directly at `/design-requests/:designRequestId` without session gating.
3. **Staff & Manager Review**: Staff and Manager actors review custom design requests through the standard review queue (`/design-review-requests`) and detail decision screen (`/design-review-requests/:reviewRequestId`), mirroring the evaluation workflow of blank ring designs.

## 4. Basic AI Support

```text
Receive request
→ Resolve Firebase Member or Guest AI session
→ Validate actor/status (Staff/Manager/Admin -> 403 AI_ACTOR_NOT_ALLOWED)
→ Normalize prompt
→ Validate non-empty and max 2,000 characters (invalid -> 400 AI_INPUT_INVALID)
→ Atomically consume daily quota (exceeded -> 429 AI_DAILY_QUOTA_EXCEEDED with Retry-After)
→ Screen scope and prompt injection (violation -> 422 AI_PROMPT_INJECTION_DETECTED / AI_CONTEXT_NOT_ALLOWED)
→ Build fixed system prompt with allowlisted public context
→ Call Gemini adapter
→ Validate provider output
→ Persist safe interaction/audit with 30-day retention
→ Return response and quota metadata
```

The assistant may answer catalogue, workshop package/session, store-information and
approved FAQ questions. It cannot confirm booking/payment, publish a price, decide
feasibility, accept/reject a custom order or expose private/operational data.

Error codes and status mapping:
- `AI_INPUT_INVALID`: 400 (Bad Request - empty prompt or > 2,000 chars)
- `AI_GUEST_SESSION_INVALID`: 401 (Unauthorized - missing/expired/revoked session)
- `AI_ACTOR_NOT_ALLOWED`: 403 (Forbidden - internal staff/manager/admin roles)
- `AI_PROMPT_INJECTION_DETECTED`: 422 (Unprocessable - prompt injection)
- `AI_CONTEXT_NOT_ALLOWED`: 422 (Unprocessable - forbidden private/payment contexts)
- `AI_OUTPUT_POLICY_VIOLATION`: 422 (Unprocessable - unsafe provider output)
- `AI_DAILY_QUOTA_EXCEEDED`: 429 (Too Many Requests - daily quota exhausted, includes `Retry-After`)
- `AI_UNAVAILABLE`: 503 (Service Unavailable - provider timeout or offline)

## 5. Analysis and review states

Technical analysis and business review are separate:

```text
analysis: PENDING → PROCESSING → SUCCEEDED / FAILED
review:   NEED_REVIEW → ACCEPTED / REJECTED / CLARIFICATION_REQUIRED
```

The frontend displays the actual backend state returned by the response. It does not
assume polling, asynchronous processing or automatic acceptance. Safe results may
contain candidate features, image quality, confidence/uncertainty and explanation;
raw provider payload and raw temporary image data are never displayed.

Every valid image request follows the backend review policy. AI cannot create a
terminal review decision. A stale version or conflict requires reload/reconciliation;
the frontend must not retry with a new command key automatically.

## 6. Price suggestion

Only Manager-authorized users may view commercial price suggestions and record a
decision. Admin may receive a technical/audit projection without commercial detail.
Guest, Member and Staff do not see internal price suggestions.

The suggestion is advisory and applies only to a new workshop package or retail
product proposal. It cannot automatically update or publish a catalogue price,
change a payment amount, price an individual Member quotation or price a
custom-manufacturing order.

## 7. Security, validation and retention invariants

- Validate transport, Firebase identity, Mland role/status, ownership/scope, rate limit/idempotency and input schema before provider calls.
- Prompt input is untrusted data; system policy and user content are separated.
- Reject prompt injection before provider invocation with `AI_PROMPT_INJECTION_DETECTED`.
- Do not send tokens, credentials, payment data, raw operational data, unnecessary PII or unnormalized request bodies to the provider.
- Member image submissions are limited to three attempts in a rolling five-minute window. The fourth attempt returns `429 IMAGE_RATE_LIMITED` and `Retry-After`.
- Duplicate retries with the same idempotency key do not consume another image quota slot.
- Temporary images are deleted after processing on success and failure. The current frontend never caches raw image data beyond the active form interaction.
- Text prompt/response retention is at most 30 days according to the shared retention policy.
- Frontend must preserve recoverable form data on validation, network, `409`, `429` and `503` failures.

## 8. Frontend routes and API baseline

```text
/ai-support
/design-requests/new
/design-requests/:designRequestId
/design-review-requests
/design-review-requests/:reviewRequestId
/price-proposals/:proposalId/ai-suggestions
```

The current backend compatibility shape is:

```text
POST /api/v1/ai-support/messages
POST /api/v1/design-requests
GET  /api/v1/design-requests/{id}
GET  /api/v1/design-review-requests
POST /api/v1/design-review-requests/{id}/decisions
POST /api/v1/price-proposals/{id}/ai-suggestions
GET  /api/v1/price-proposals/{id}/ai-suggestions
POST /api/v1/price-proposals/{id}/ai-suggestions/decisions
```

The current implementation returns `message` rather than the drafted `answer`,
uses `designVersion` for review commands, accepts price `bodyContext` as raw body
with `targetType` query parameter, and returns `200` for price suggestion. These
are documented compatibility facts; they must be synchronized with OpenAPI before
the integration contract is called final.

## 9. Non-goals

- Workshop booking, package/location/session selection, payment, ticketing or settlement.
- Catalogue price publication from an AI response.
- Final design, feasibility, custom-manufacturing acceptance or final custom-order price.
- Guest Design Request creation or Guest image upload.
- Raw provider/image exposure.
- New global frontend state, UI kit or form library.

## 10. Acceptance criteria

- Guest can use Basic AI Support but receives no Design Request mutation path.
- Member can submit description-only, image-only or combined requests.
- Image consent and validation happen before image submission.
- Invalid image, prompt injection, rate limit and forbidden responses do not call the provider or mutate business state.
- Owner result screens display only safe backend projections.
- Staff/Manager/Admin screens respect backend authorization and projection boundaries.
- No frontend route or API call introduces booking/payment prerequisites.
- UI tests cover loading, empty, validation, success, forbidden, failure, retry, rate-limit and conflict states.
