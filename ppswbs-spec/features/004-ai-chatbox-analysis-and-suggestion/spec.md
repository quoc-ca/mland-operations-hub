# Feature Specification: AI Chatbox Analysis and Suggestion

**Date created**: 2026-10-21
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

**Feature**: `004-ai-chatbox-analysis-and-suggestion`
**Status**: Independent frontend behavior synchronized; backend/API integration gates remain open
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

| Actor | Allowed capability |
| --- | --- |
| Guest | Basic AI Support in public scope only. Cannot create or upload a Design Request. |
| Member | Basic AI Support; create and view own Independent Design Requests. |
| Staff | Review requests only within server-authorized scope. |
| Manager | Review requests requiring Manager authority; request and decide advisory catalogue price suggestions. |
| Admin | Technical configuration and approved technical/audit projection only; no business review or price decision. |
| AI provider | Returns validated safe output only; cannot mutate Mland state. |
| Cloud storage | Temporary image storage/deletion only. |

The frontend route guard is a navigation aid. Firebase identity, Mland role/status,
ownership, reviewer authority and resource scope are enforced by the backend.
Client-provided role, owner, reviewer, price, status or decision is untrusted.

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

## 4. Basic AI Support

```text
Guest/Member opens AI Chat
→ validates non-empty prompt and 2,000-character limit
→ screens scope and prompt-injection patterns
→ sends allowlisted public context to the provider
→ validates safe response
→ displays advisory response and scope
```

The assistant may answer catalogue, workshop package/session, store-information and
approved FAQ questions. It cannot confirm booking/payment, publish a price, decide
feasibility, accept/reject a custom order or expose private/operational data.

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
