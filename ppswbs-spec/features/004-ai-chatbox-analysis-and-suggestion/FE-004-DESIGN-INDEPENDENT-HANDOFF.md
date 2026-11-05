# FE-004 — Independent Design Capability Handoff

**Date created**: 2026-10-24
**Date updated**: 2026-11-05
**Author**: Ngô Tiến Đạt
**Last updated by**: Ngô Tiến Đạt

## 1. Confirmed boundary

Feature-004 is independent from Workshop Booking. It does not create, read, update
or depend on booking code/status, package, location, session, capacity hold, ticket,
check-in, payment, deposit, VNPay or settlement state. Feature-003 does not call
Feature-004 as a booking precondition.

## 2. Confirmed actor and request policy

- Guest may use Basic AI Support only.
- Only an authenticated Member may create or upload an Independent Design Request.
- A request may contain description only, image only, or both.
- Image consent is required before image storage or provider processing.
- Member ownership is derived from Firebase identity and verified server-side.
- Member may view only the safe projection of the Member's own request.
- Staff/Manager review and Manager price suggestion authority remain backend decisions.
- AI never accepts/rejects a request, publishes a price or mutates booking/payment state.

## 3. Frontend routes and screens

```text
/ai-support
/design-requests/new
/design-requests/:designRequestId
/design-review-requests
/design-review-requests/:reviewRequestId
/price-proposals/:proposalId/ai-suggestions
```

The frontend uses React/Vite, shared HTTP/auth/i18n/UI modules and real router
paths. It has no booking prerequisite and does not add a hidden booking context.

## 4. Data and API boundary

Design Request data may include Member ownership, description, opaque catalogue
reference, consent evidence, temporary image lifecycle, safe analysis result,
review evidence and audit timestamps. It must not contain booking, session, location,
payment, ticket, deposit or capacity fields.

Current frontend compatibility fields and statuses are documented in `spec.md` and
`design.md`. OpenAPI/controller synchronization remains a backend task; the
frontend must not silently translate a mismatched payload.

## 5. Required validation and recovery

- Prompt maximum: 2,000 characters.
- Description maximum: 4,000 characters.
- Image maximum: 10 MB and 4096×4096; supported JPEG/PNG/WEBP.
- Member image quota: three attempts per rolling five minutes; duplicate idempotent retry does not consume another slot.
- Keep form values on recoverable validation/network/conflict failures.
- Use one idempotency key per write action and reuse it on retry.
- Show `401`, `403`, `409`, `429` and provider failure as typed UI states.
- Never expose raw image/provider payload, credential, token or unauthorized price data.

## 6. Implementation and verification gate

The implementation must prove that no Feature-004 route or API call mutates booking,
payment, ticket, capacity or custom-manufacturing state. Frontend unit/component
tests and `npm test`/`npm run build` are required for frontend handoff. Live backend,
OpenAPI, migration, provider and E2E evidence must be recorded separately and is
not implied by this document.
