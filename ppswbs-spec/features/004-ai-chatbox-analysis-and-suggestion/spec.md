# Feature Specification: AI Chatbox Analysis and Suggestion

**Date created**: 2026-10-21  
**Date updated**: 2026-10-28  
**Author**: Ngô Tiến Đạt  
**Last updated by**: Ngô Tiến Đạt  

**Feature**: `004-ai-chatbox-analysis-and-suggestion`  
**Status**: Independent-design rebase; implementation blocked by the ownership decisions listed below  
**Source alignment**: `FE-004-DESIGN-INDEPENDENT-HANDOFF.md`, Report 1 FE-04/FE-05, Report 3

## 1. Scope and independent boundary

Feature-04 owns three bounded capabilities:

1. Basic AI Support for approved public information.
2. Independent Design Request creation, optional image analysis and human review.
3. Manager-only advisory price suggestions for new workshop packages or retail products.

An Independent Design Request is not a workshop booking-design request. It MUST NOT
create, read, update or depend on workshop booking, booking code/status, package
selection, location, session, payment, deposit, VNPay, capacity hold, ticket,
check-in or booking eligibility. It may retain an opaque catalogue/product reference
only when the catalogue facade is explicitly needed for analysis context.

The owning booking, payment, catalogue, fulfilment and custom-manufacturing modules
remain responsible for their own state. FE-003 MUST NOT call FE-004 as a booking
precondition and MUST NOT store a design approval reference.

## 2. Actors and unresolved ownership gate

| Actor | Allowed capability |
| --- | --- |
| Guest | Basic AI Support. Independent Design Request access is **TBD** because the handoff has not approved a secure guest ownership proof. |
| Member | Basic AI Support and independent Design Request access for the Member's own data, subject to the final ownership decision. |
| Staff | Review independent Design Requests within the approved delegated scope. |
| Manager | Review requests requiring Manager authority and request/decide advisory price suggestions. |
| Admin | Technical configuration and approved technical/audit evidence only; no business review or price decision. |
| AI provider | Returns validated candidate features, image quality or advisory price suggestion; never mutates Mland state. |
| Cloud storage | Temporary image storage and deletion only. |

The following decisions are not silently inferred from the previous booking flow and
MUST be resolved before production implementation:

- whether Guest may create an Independent Design Request;
- the ownership proof and retrieval mechanism if Guest is allowed;
- whether image-less text-only Design Requests are allowed;
- whether every request requires human review or only requests with image analysis;
- whether a Member may withdraw, revise or create a new request after rejection;
- whether an approval expires.

The current backend's Member-only booking assumption is therefore not an approved
final contract after this rebase.

## 3. Permission rules

| Action | Guest | Member | Staff | Manager | Admin |
| --- | --- | --- | --- | --- | --- |
| Basic AI Support | Allow public scope | Allow public scope | Deny by default | Deny by default | Deny by default |
| Create independent Design Request | TBD ownership proof | Allow own data | Deny | Deny | Deny |
| Upload reference image | Same as Design Request decision | Allow with consent | Deny | Deny | Deny |
| View own request/result | TBD ownership proof | Own data only | Deny | Deny | Deny |
| View scoped request queue | Deny | Deny | Allow approved scope | Allow approved authority | Technical/audit only if approved |
| Record human decision | Deny | Deny | Allow approved scope | Allow approved authority | Deny |
| Request/view price suggestion | Deny | Deny | Deny | Allow proposal scope | Technical/audit projection only |
| Final price decision/publication | Deny | Deny | Deny | Through catalogue workflow | Deny through FE-004 |
| Booking/payment/custom-order mutation | Deny through FE-004 | Deny through FE-004 | Deny through FE-004 | Deny through FE-004 | Deny through FE-004 |

All authorization is server-side. Client-provided role, owner, reviewer, price,
status, estimate or decision is untrusted. A reviewer cannot review a request they
created or a request with a prohibited conflict of interest.

## 4. Independent Design Request flow

```text
Guest/Member identity and ownership proof
→ Design workspace
→ Optional design description and/or reference image
→ Image consent when an image is supplied
→ Validate owner, input, image, rate limit and idempotency
→ Temporary storage only for an accepted image
→ Create independent design request/version
→ AI candidate analysis when requested
→ Delete temporary image after processing
→ Persist safe result/failure evidence
→ Staff/Manager review when the approved review policy requires it
→ Owner views safe result and may revise, withdraw or finish according to the resolved policy
```

The flow has no booking step before or after it. A successful result does not confirm
a booking, payment, final design, custom-manufacturing order or catalogue price.

## 5. Validation and security invariants

All requests use this order:

```text
Transport → Firebase identity → Mland role/status → ownership/scope
→ rate limit/idempotency → input schema → independent business context
→ prompt/image security screening → provider call
→ provider output validation → persistence/state transition
```

- Basic AI prompt limit is 2,000 characters; provider timeout is 30 seconds.
- Image limit is 10 MB and 4096×4096 pixels; MIME, signature, dimensions and malware policy are checked before provider processing.
- A Member may submit at most three image attempts in a rolling five-minute window. The fourth attempt returns `429 IMAGE_RATE_LIMITED` and `Retry-After`; duplicate idempotent retries do not consume another slot.
- Consent is purpose-specific, versioned and recorded before image storage or provider processing.
- Provider input is normalized from an allowlisted command model; raw request bodies, tokens, credentials, payment data, private customer data and operational data are never forwarded.
- Prompt injection and instruction-like text embedded in images are untrusted data. Detected injection returns `AI_PROMPT_INJECTION_DETECTED` without a provider call or business mutation.
- Provider output is schema- and policy-validated. Final approval, price publication, booking mutation, payment mutation and custom-order decisions are rejected.
- Booking-design retention rules do not apply. Independent Design Request image retention is **TBD** and must be explicitly decided; temporary processing storage remains the default until then.
- Text interaction retention remains no more than 30 days.

## 6. Data and state model

The persisted Design Request owns independent design data only:

- request identifier and version;
- owner account reference or approved guest ownership proof;
- optional opaque catalogue/product reference;
- description and optional image reference;
- consent evidence when an image is supplied;
- analysis status/result schema version;
- review status/decision and reviewer audit evidence;
- revision, withdrawal, reason and timestamps.

It MUST NOT contain `bookingId`, `bookingCode`, `bookingStatus`, `sessionId`,
`locationId`, `holdId`, `ticketId`, `paymentId` or `depositId`.

Technical analysis state and business review state remain separate:

```text
analysis: PENDING → PROCESSING → SUCCEEDED / FAILED
review:   NEED_REVIEW → ACCEPTED / REJECTED / CLARIFICATION_REQUIRED
request:  ACTIVE → WITHDRAWN / COMPLETED (exact transitions require ownership decision)
```

## 7. API contract baseline

The independent API surface is:

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

`POST /design-requests` accepts an independent description and/or image plus
consent/idempotency data when applicable. It does not accept booking or payment
fields. Exact Guest ownership, image-only/text-only and review-policy fields remain
blocked until the ownership gate is resolved.

## 8. Non-goals

- Workshop booking creation, lookup, eligibility, capacity, location/session and ticketing.
- Payment, deposit, VNPay, invoice and settlement.
- Configured ring-design choices owned by another contract.
- Custom-manufacturing acceptance or final custom-order pricing.
- Catalogue price publication from an AI response.
- Permanent storage of reference images without an approved retention decision.

## 9. Acceptance criteria for the independent rebase

- No Feature-004 production class imports `edu.fpt.sep490_g22.ppswbs_backend.workshopbooking` or a workshop booking facade.
- No Feature-004 request, response, database table or audit record contains booking/payment/location/session fields.
- Creating, analyzing, reviewing, revising or withdrawing a Design Request does not create or mutate booking, capacity, payment, ticket or custom-order state.
- Cross-owner reads and mutations are denied without revealing whether another owner's request exists.
- Prompt/image validation, consent, rate limiting, provider-boundary and retention policies are tested.
- The OpenAPI contract, data model, design flow, tasks and backend implementation use the same independent boundary.
- All unresolved ownership/review decisions are resolved before the implementation gate is marked complete.
